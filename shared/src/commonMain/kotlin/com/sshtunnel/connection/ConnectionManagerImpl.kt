package com.sshtunnel.connection

import com.sshtunnel.data.ConnectionState
import com.sshtunnel.data.ErrorType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.data.ShadowsocksConfig
import com.sshtunnel.data.VpnStatistics
import com.sshtunnel.logging.Logger
import com.sshtunnel.shadowsocks.ConnectionTestResult
import com.sshtunnel.shadowsocks.ShadowsocksClient
import com.sshtunnel.shadowsocks.ShadowsocksState
import com.sshtunnel.storage.CredentialStore
import com.sshtunnel.vpn.RoutingConfig
import com.sshtunnel.vpn.TunnelConfig
import com.sshtunnel.vpn.TunnelState
import com.sshtunnel.vpn.VpnError
import com.sshtunnel.vpn.VpnTunnelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Default implementation of ConnectionManager.
 * 
 * Coordinates between Shadowsocks client and VPN tunnel provider to establish
 * and maintain secure connections.
 * 
 * @property shadowsocksClient Client for managing Shadowsocks connections
 * @property vpnTunnelProvider Provider for VPN tunnel management
 * @property credentialStore Secure storage for passwords
 * @property scope Coroutine scope for background operations
 * @property logger Logger for debugging and monitoring
 */
class ConnectionManagerImpl(
    private val shadowsocksClient: ShadowsocksClient,
    private val vpnTunnelProvider: VpnTunnelProvider,
    private val credentialStore: CredentialStore,
    private val scope: CoroutineScope,
    private val logger: Logger
) : ConnectionManager {
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _statistics = MutableStateFlow(VpnStatistics())
    
    private var currentProfile: ServerProfile? = null
    private var socksPort: Int? = null
    
    private var shadowsocksStateJob: Job? = null
    private var tunnelStateJob: Job? = null
    
    companion object {
        private const val TAG = "ConnectionManager"
    }
    
    override fun observeConnectionState(): StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    override fun observeStatistics(): StateFlow<VpnStatistics> = _statistics.asStateFlow()
    
    override fun getCurrentState(): ConnectionState = _connectionState.value
    
    override suspend fun connect(profile: ServerProfile): Result<Unit> {
        logger.info(TAG, "Starting connection to ${profile.name}")
        
        // Check if already connected
        if (_connectionState.value is ConnectionState.Connected) {
            logger.warn(TAG, "Already connected, disconnecting first")
            disconnect()
        }
        
        // Update state to Connecting
        _connectionState.value = ConnectionState.Connecting
        
        try {
            // Step 1: Retrieve password from secure storage
            logger.debug(TAG, "Retrieving password for profile ${profile.id}")
            val password = credentialStore.retrievePassword(profile.id).getOrElse { error ->
                logger.error(TAG, "Failed to retrieve password", error)
                _connectionState.value = ConnectionState.Error(
                    message = "Failed to retrieve password: ${error.message}",
                    errorType = ErrorType.AUTHENTICATION_FAILED
                )
                return Result.failure(error)
            }
            
            // Step 2: Create Shadowsocks configuration
            val config = ShadowsocksConfig(
                serverHost = profile.serverHost,
                serverPort = profile.serverPort,
                password = password,
                cipher = profile.cipher,
                timeout = 10.seconds
            )
            
            // Step 3: Start Shadowsocks client
            logger.debug(TAG, "Starting Shadowsocks client")
            val localPort = shadowsocksClient.start(config).getOrElse { error ->
                logger.error(TAG, "Failed to start Shadowsocks client", error)
                val errorType = when {
                    error.message?.contains("unreachable", ignoreCase = true) == true -> ErrorType.SERVER_UNREACHABLE
                    error.message?.contains("authentication", ignoreCase = true) == true -> ErrorType.AUTHENTICATION_FAILED
                    error.message?.contains("cipher", ignoreCase = true) == true -> ErrorType.UNSUPPORTED_CIPHER
                    error.message?.contains("timeout", ignoreCase = true) == true -> ErrorType.TIMEOUT
                    else -> ErrorType.UNKNOWN
                }
                _connectionState.value = ConnectionState.Error(
                    message = "Failed to start Shadowsocks: ${error.message}",
                    errorType = errorType
                )
                return Result.failure(error)
            }
            
            socksPort = localPort
            logger.info(TAG, "Shadowsocks client started on port $localPort")
            
            // Step 4: Start VPN tunnel
            logger.debug(TAG, "Starting VPN tunnel")
            val tunnelConfig = TunnelConfig(
                socksPort = localPort,
                sessionName = "Shadowsocks - ${profile.name}"
            )
            
            vpnTunnelProvider.startTunnel(tunnelConfig).getOrElse { error ->
                logger.error(TAG, "Failed to start VPN tunnel", error)
                
                // Clean up Shadowsocks client
                shadowsocksClient.stop()
                socksPort = null
                
                val errorType = when (error) {
                    is VpnError.PermissionDenied -> ErrorType.VPN_PERMISSION_DENIED
                    else -> ErrorType.UNKNOWN
                }
                
                _connectionState.value = ConnectionState.Error(
                    message = "Failed to start VPN tunnel: ${error.message}",
                    errorType = errorType
                )
                return Result.failure(error)
            }
            
            // Step 5: Update state to Connected
            currentProfile = profile
            _connectionState.value = ConnectionState.Connected(
                profileId = profile.id,
                serverAddress = profile.serverHost,
                connectedAt = System.currentTimeMillis()
            )
            
            // Step 6: Start monitoring state changes
            startMonitoring()
            
            logger.info(TAG, "Successfully connected to ${profile.name}")
            return Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Unexpected error during connection", e)
            _connectionState.value = ConnectionState.Error(
                message = "Unexpected error: ${e.message}",
                errorType = ErrorType.UNKNOWN
            )
            
            // Clean up
            try {
                shadowsocksClient.stop()
                vpnTunnelProvider.stopTunnel()
            } catch (cleanupError: Exception) {
                logger.error(TAG, "Error during cleanup", cleanupError)
            }
            
            socksPort = null
            currentProfile = null
            
            return Result.failure(e)
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        logger.info(TAG, "Disconnecting")
        
        // Stop monitoring
        stopMonitoring()
        
        try {
            // Step 1: Stop VPN tunnel
            logger.debug(TAG, "Stopping VPN tunnel")
            vpnTunnelProvider.stopTunnel().getOrElse { error ->
                logger.error(TAG, "Error stopping VPN tunnel", error)
                // Continue with cleanup even if VPN stop fails
            }
            
            // Step 2: Stop Shadowsocks client
            logger.debug(TAG, "Stopping Shadowsocks client")
            shadowsocksClient.stop()
            
            // Step 3: Clean up state
            socksPort = null
            currentProfile = null
            _statistics.value = VpnStatistics()
            
            // Step 4: Update state to Disconnected
            _connectionState.value = ConnectionState.Disconnected
            
            logger.info(TAG, "Successfully disconnected")
            return Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error during disconnect", e)
            
            // Force state to disconnected even on error
            _connectionState.value = ConnectionState.Disconnected
            socksPort = null
            currentProfile = null
            _statistics.value = VpnStatistics()
            
            return Result.failure(e)
        }
    }
    
    override suspend fun testConnection(profile: ServerProfile): Result<ConnectionTestResult> {
        logger.info(TAG, "Testing connection to ${profile.name}")
        
        try {
            // Retrieve password
            val password = credentialStore.retrievePassword(profile.id).getOrElse { error ->
                logger.error(TAG, "Failed to retrieve password for test", error)
                return Result.success(
                    ConnectionTestResult(
                        success = false,
                        latencyMs = null,
                        errorMessage = "Failed to retrieve password: ${error.message}"
                    )
                )
            }
            
            // Create configuration
            val config = ShadowsocksConfig(
                serverHost = profile.serverHost,
                serverPort = profile.serverPort,
                password = password,
                cipher = profile.cipher,
                timeout = 10.seconds
            )
            
            // Test connection
            return shadowsocksClient.testConnection(config)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error during connection test", e)
            return Result.success(
                ConnectionTestResult(
                    success = false,
                    latencyMs = null,
                    errorMessage = "Test failed: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Starts monitoring Shadowsocks and VPN tunnel state changes.
     */
    private fun startMonitoring() {
        logger.debug(TAG, "Starting state monitoring")
        
        // Monitor Shadowsocks state
        shadowsocksStateJob = shadowsocksClient.observeState()
            .onEach { state ->
                logger.debug(TAG, "Shadowsocks state changed to $state")
                handleShadowsocksStateChange(state)
            }
            .launchIn(scope)
        
        // Monitor VPN tunnel state
        tunnelStateJob = vpnTunnelProvider.observeTunnelState()
            .onEach { state ->
                logger.debug(TAG, "Tunnel state changed to $state")
                handleTunnelStateChange(state)
            }
            .launchIn(scope)
    }
    
    /**
     * Stops monitoring state changes.
     */
    private fun stopMonitoring() {
        logger.debug(TAG, "Stopping state monitoring")
        shadowsocksStateJob?.cancel()
        tunnelStateJob?.cancel()
        shadowsocksStateJob = null
        tunnelStateJob = null
    }
    
    /**
     * Handles Shadowsocks state changes.
     */
    private fun handleShadowsocksStateChange(state: ShadowsocksState) {
        when (state) {
            is ShadowsocksState.Error -> {
                logger.error(TAG, "Shadowsocks error: ${state.message}")
                
                // Only update to error if we're currently connected or connecting
                if (_connectionState.value is ConnectionState.Connected ||
                    _connectionState.value is ConnectionState.Connecting) {
                    
                    scope.launch {
                        // Try to disconnect cleanly
                        disconnect()
                    }
                    
                    _connectionState.value = ConnectionState.Error(
                        message = "Shadowsocks error: ${state.message}",
                        errorType = ErrorType.UNKNOWN
                    )
                }
            }
            is ShadowsocksState.Idle -> {
                // Shadowsocks stopped - if we're connected, this is unexpected
                if (_connectionState.value is ConnectionState.Connected) {
                    logger.warn(TAG, "Shadowsocks stopped unexpectedly")
                    scope.launch {
                        disconnect()
                    }
                }
            }
            else -> {
                // Other states are normal during connection lifecycle
            }
        }
    }
    
    /**
     * Handles VPN tunnel state changes.
     */
    private fun handleTunnelStateChange(state: TunnelState) {
        when (state) {
            is TunnelState.Error -> {
                logger.error(TAG, "VPN tunnel error: ${state.error}")
                
                // Only update to error if we're currently connected or connecting
                if (_connectionState.value is ConnectionState.Connected ||
                    _connectionState.value is ConnectionState.Connecting) {
                    
                    scope.launch {
                        // Try to disconnect cleanly
                        disconnect()
                    }
                    
                    _connectionState.value = ConnectionState.Error(
                        message = "VPN tunnel error: ${state.error}",
                        errorType = ErrorType.UNKNOWN
                    )
                }
            }
            is TunnelState.Inactive -> {
                // Tunnel stopped - if we're connected, this is unexpected
                if (_connectionState.value is ConnectionState.Connected) {
                    logger.warn(TAG, "VPN tunnel stopped unexpectedly")
                    scope.launch {
                        disconnect()
                    }
                }
            }
            else -> {
                // Other states are normal during connection lifecycle
            }
        }
    }
}
