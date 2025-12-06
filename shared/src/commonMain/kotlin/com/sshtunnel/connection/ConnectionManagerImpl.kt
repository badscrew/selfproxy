package com.sshtunnel.connection

import com.sshtunnel.data.ConnectionState
import com.sshtunnel.data.ErrorType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.data.ShadowsocksConfig
import com.sshtunnel.data.VpnStatistics
import com.sshtunnel.logging.Logger
import com.sshtunnel.network.NetworkMonitor
import com.sshtunnel.network.NetworkState
import com.sshtunnel.reconnection.ReconnectionPolicy
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
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
 * @property networkMonitor Monitor for network connectivity changes
 * @property scope Coroutine scope for background operations
 * @property logger Logger for debugging and monitoring
 * @property reconnectionPolicy Policy for automatic reconnection behavior
 */
class ConnectionManagerImpl(
    private val shadowsocksClient: ShadowsocksClient,
    private val vpnTunnelProvider: VpnTunnelProvider,
    private val credentialStore: CredentialStore,
    private val networkMonitor: NetworkMonitor,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val reconnectionPolicy: ReconnectionPolicy = ReconnectionPolicy.DEFAULT
) : ConnectionManager {
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _statistics = MutableStateFlow(VpnStatistics())
    
    private var currentProfile: ServerProfile? = null
    private var socksPort: Int? = null
    
    private var shadowsocksStateJob: Job? = null
    private var tunnelStateJob: Job? = null
    private var networkMonitorJob: Job? = null
    private var reconnectionJob: Job? = null
    
    // Reconnection state
    private var reconnectionAttempts = 0
    private var isReconnecting = false
    private var userDisconnected = false
    
    companion object {
        private const val TAG = "ConnectionManager"
        private const val MAX_FAILED_ATTEMPTS_BEFORE_NOTIFICATION = 5
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
        
        // Reset reconnection state for new connection
        userDisconnected = false
        reconnectionAttempts = 0
        isReconnecting = false
        
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
            
            // Step 6: Reset reconnection counter on successful connection
            reconnectionAttempts = 0
            isReconnecting = false
            
            // Step 7: Start monitoring state changes
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
        
        // Mark as user-initiated disconnect to prevent auto-reconnect
        userDisconnected = true
        
        // Cancel any ongoing reconnection attempts
        reconnectionJob?.cancel()
        reconnectionJob = null
        
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
            reconnectionAttempts = 0
            isReconnecting = false
            
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
            reconnectionAttempts = 0
            isReconnecting = false
            
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
        
        // Monitor network changes if reconnection policy allows
        if (reconnectionPolicy.enabled && reconnectionPolicy.reconnectOnNetworkChange) {
            networkMonitorJob = networkMonitor.observeNetworkChanges()
                .onEach { networkState ->
                    logger.debug(TAG, "Network state changed to $networkState")
                    handleNetworkChange(networkState)
                }
                .launchIn(scope)
        }
    }
    
    /**
     * Stops monitoring state changes.
     */
    private fun stopMonitoring() {
        logger.debug(TAG, "Stopping state monitoring")
        shadowsocksStateJob?.cancel()
        tunnelStateJob?.cancel()
        networkMonitorJob?.cancel()
        shadowsocksStateJob = null
        tunnelStateJob = null
        networkMonitorJob = null
    }
    
    /**
     * Handles Shadowsocks state changes.
     */
    private fun handleShadowsocksStateChange(state: ShadowsocksState) {
        when (state) {
            is ShadowsocksState.Error -> {
                logger.error(TAG, "Shadowsocks error: ${state.message}")
                
                // Only handle if we're currently connected or connecting
                if (_connectionState.value is ConnectionState.Connected ||
                    _connectionState.value is ConnectionState.Connecting) {
                    
                    // Trigger reconnection if enabled
                    if (reconnectionPolicy.enabled && !userDisconnected) {
                        logger.info(TAG, "Connection lost, attempting to reconnect")
                        scope.launch {
                            attemptReconnection()
                        }
                    } else {
                        scope.launch {
                            disconnect()
                        }
                        
                        _connectionState.value = ConnectionState.Error(
                            message = "Shadowsocks error: ${state.message}",
                            errorType = ErrorType.UNKNOWN
                        )
                    }
                }
            }
            is ShadowsocksState.Idle -> {
                // Shadowsocks stopped - if we're connected, this is unexpected
                if (_connectionState.value is ConnectionState.Connected && !userDisconnected) {
                    logger.warn(TAG, "Shadowsocks stopped unexpectedly")
                    
                    if (reconnectionPolicy.enabled) {
                        scope.launch {
                            attemptReconnection()
                        }
                    } else {
                        scope.launch {
                            disconnect()
                        }
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
                
                // Only handle if we're currently connected or connecting
                if (_connectionState.value is ConnectionState.Connected ||
                    _connectionState.value is ConnectionState.Connecting) {
                    
                    // Trigger reconnection if enabled
                    if (reconnectionPolicy.enabled && !userDisconnected) {
                        logger.info(TAG, "VPN tunnel error, attempting to reconnect")
                        scope.launch {
                            attemptReconnection()
                        }
                    } else {
                        scope.launch {
                            disconnect()
                        }
                        
                        _connectionState.value = ConnectionState.Error(
                            message = "VPN tunnel error: ${state.error}",
                            errorType = ErrorType.UNKNOWN
                        )
                    }
                }
            }
            is TunnelState.Inactive -> {
                // Tunnel stopped - if we're connected, this is unexpected
                if (_connectionState.value is ConnectionState.Connected && !userDisconnected) {
                    logger.warn(TAG, "VPN tunnel stopped unexpectedly")
                    
                    if (reconnectionPolicy.enabled) {
                        scope.launch {
                            attemptReconnection()
                        }
                    } else {
                        scope.launch {
                            disconnect()
                        }
                    }
                }
            }
            else -> {
                // Other states are normal during connection lifecycle
            }
        }
    }
    
    /**
     * Handles network connectivity changes.
     */
    private fun handleNetworkChange(networkState: NetworkState) {
        when (networkState) {
            is NetworkState.Changed -> {
                // Network type changed (e.g., WiFi to Mobile)
                if (_connectionState.value is ConnectionState.Connected && !userDisconnected) {
                    logger.info(TAG, "Network changed from ${networkState.fromType} to ${networkState.toType}, reconnecting")
                    scope.launch {
                        attemptReconnection()
                    }
                }
            }
            is NetworkState.Lost -> {
                // Network lost - wait for it to come back
                logger.warn(TAG, "Network connection lost")
            }
            is NetworkState.Available -> {
                // Network available - if we were disconnected due to network loss, reconnect
                if (_connectionState.value is ConnectionState.Reconnecting && !userDisconnected) {
                    logger.info(TAG, "Network available, continuing reconnection")
                }
            }
            else -> {
                // Other network states
            }
        }
    }
    
    /**
     * Attempts to reconnect with exponential backoff.
     */
    private suspend fun attemptReconnection() {
        // Prevent multiple concurrent reconnection attempts
        if (isReconnecting) {
            logger.debug(TAG, "Reconnection already in progress")
            return
        }
        
        isReconnecting = true
        
        // Cancel any existing reconnection job
        reconnectionJob?.cancel()
        
        reconnectionJob = scope.launch {
            val profile = currentProfile
            if (profile == null) {
                logger.error(TAG, "Cannot reconnect: no profile available")
                isReconnecting = false
                return@launch
            }
            
            // Check if we've exceeded max attempts
            if (reconnectionPolicy.maxAttempts > 0 && reconnectionAttempts >= reconnectionPolicy.maxAttempts) {
                logger.error(TAG, "Max reconnection attempts (${reconnectionPolicy.maxAttempts}) exceeded")
                _connectionState.value = ConnectionState.Error(
                    message = "Failed to reconnect after ${reconnectionAttempts} attempts",
                    errorType = ErrorType.UNKNOWN
                )
                isReconnecting = false
                return@launch
            }
            
            while (isReconnecting && !userDisconnected) {
                reconnectionAttempts++
                
                // Update state to Reconnecting
                _connectionState.value = ConnectionState.Reconnecting(attempt = reconnectionAttempts)
                
                // Calculate backoff delay
                val backoffDelay = calculateBackoff(reconnectionAttempts - 1)
                logger.info(TAG, "Reconnection attempt $reconnectionAttempts, waiting ${backoffDelay.inWholeSeconds}s")
                
                // Notify user after 5 failed attempts
                if (reconnectionAttempts == MAX_FAILED_ATTEMPTS_BEFORE_NOTIFICATION) {
                    logger.warn(TAG, "Reconnection failed $MAX_FAILED_ATTEMPTS_BEFORE_NOTIFICATION times, user should be notified")
                    // Note: Actual notification would be handled by UI layer observing connection state
                }
                
                // Wait before attempting reconnection
                delay(backoffDelay)
                
                // Check if we should still reconnect
                if (userDisconnected || !isReconnecting) {
                    logger.info(TAG, "Reconnection cancelled")
                    break
                }
                
                // Clean up current connection state
                try {
                    shadowsocksClient.stop()
                    vpnTunnelProvider.stopTunnel()
                } catch (e: Exception) {
                    logger.error(TAG, "Error cleaning up before reconnection", e)
                }
                
                // Attempt to reconnect
                logger.info(TAG, "Attempting to reconnect (attempt $reconnectionAttempts)")
                val result = connect(profile)
                
                if (result.isSuccess) {
                    logger.info(TAG, "Reconnection successful after $reconnectionAttempts attempts")
                    isReconnecting = false
                    break
                } else {
                    logger.warn(TAG, "Reconnection attempt $reconnectionAttempts failed: ${result.exceptionOrNull()?.message}")
                    
                    // Check if we've exceeded max attempts
                    if (reconnectionPolicy.maxAttempts > 0 && reconnectionAttempts >= reconnectionPolicy.maxAttempts) {
                        logger.error(TAG, "Max reconnection attempts (${reconnectionPolicy.maxAttempts}) exceeded")
                        _connectionState.value = ConnectionState.Error(
                            message = "Failed to reconnect after ${reconnectionAttempts} attempts",
                            errorType = ErrorType.UNKNOWN
                        )
                        isReconnecting = false
                        break
                    }
                }
            }
        }
    }
    
    /**
     * Calculates exponential backoff delay for reconnection attempts.
     * 
     * Uses formula: min(initialBackoff * (multiplier ^ attemptNumber), maxBackoff)
     * Default: 1s, 2s, 4s, 8s, 16s, 32s, 60s (max)
     * 
     * @param attemptNumber The attempt number (0-indexed)
     * @return Duration to wait before next attempt
     */
    private fun calculateBackoff(attemptNumber: Int): Duration {
        val backoffSeconds = reconnectionPolicy.initialBackoff.inWholeSeconds * 
            reconnectionPolicy.backoffMultiplier.pow(attemptNumber).toLong()
        
        val cappedSeconds = min(backoffSeconds, reconnectionPolicy.maxBackoff.inWholeSeconds)
        return cappedSeconds.seconds
    }
}
