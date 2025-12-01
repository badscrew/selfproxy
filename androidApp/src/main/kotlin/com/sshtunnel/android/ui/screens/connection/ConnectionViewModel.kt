package com.sshtunnel.android.ui.screens.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.repository.ProfileRepository
import com.sshtunnel.ssh.Connection
import com.sshtunnel.ssh.ConnectionError
import com.sshtunnel.ssh.ConnectionState
import com.sshtunnel.ssh.SSHConnectionManager
import com.sshtunnel.testing.ConnectionTestResult
import com.sshtunnel.testing.ConnectionTestService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing SSH connection state and operations.
 * 
 * Handles connection lifecycle, state observation, and connection testing.
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val connectionManager: SSHConnectionManager,
    private val connectionTestService: ConnectionTestService,
    private val profileRepository: ProfileRepository,
    private val logger: com.sshtunnel.logging.Logger,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Disconnected)
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    
    private val _testResult = MutableStateFlow<TestResultState>(TestResultState.None)
    val testResult: StateFlow<TestResultState> = _testResult.asStateFlow()
    
    private val _vpnPermissionNeeded = MutableStateFlow(false)
    val vpnPermissionNeeded: StateFlow<Boolean> = _vpnPermissionNeeded.asStateFlow()
    
    private var currentProfile: ServerProfile? = null
    private var isVpnActive = false
    private var pendingConnection: Connection? = null
    
    companion object {
        private const val TAG = "ConnectionViewModel"
    }
    
    /**
     * Called when VPN permission is granted by the user.
     * Auto-retries VPN start if there's a pending connection.
     */
    fun onVpnPermissionGranted() {
        logger.info(TAG, "VPN permission granted")
        _vpnPermissionNeeded.value = false
        
        // Auto-retry: If we have a pending connection waiting for VPN, retry VPN start
        pendingConnection?.let { connection ->
            logger.info(TAG, "Auto-retrying VPN start after permission granted")
            // Update UI to show we're waiting for VPN
            _uiState.value = ConnectionUiState.WaitingForVpn(connection, currentProfile)
            // Get VpnController from Application and retry starting VPN
            val app = context.applicationContext as com.sshtunnel.android.SSHTunnelProxyApp
            app.vpnController.retryVpnStart()
        }
    }
    
    /**
     * Called when VPN permission is denied by the user.
     */
    fun onVpnPermissionDenied() {
        logger.info(TAG, "VPN permission denied")
        _vpnPermissionNeeded.value = false
        
        // Disconnect SSH since VPN won't work without permission
        pendingConnection?.let {
            logger.warn(TAG, "VPN permission denied, disconnecting SSH")
            disconnect()
        }
    }
    
    /**
     * Called when VPN service starts successfully.
     */
    fun onVpnStarted() {
        logger.info(TAG, "VPN started successfully")
        isVpnActive = true
        
        // Update UI to show fully connected state
        pendingConnection?.let { connection ->
            _uiState.value = ConnectionUiState.Connected(connection, currentProfile)
            pendingConnection = null
        }
    }
    
    /**
     * Called when VPN service stops.
     */
    fun onVpnStopped() {
        logger.info(TAG, "VPN stopped")
        isVpnActive = false
    }
    
    /**
     * Called when VPN encounters an error.
     */
    fun onVpnError(errorMessage: String) {
        logger.error(TAG, "VPN error: $errorMessage")
        isVpnActive = false
        
        // Show error and disconnect
        _uiState.value = ConnectionUiState.Error(
            "VPN failed: $errorMessage",
            null
        )
    }
    
    init {
        logger.info(TAG, "ConnectionViewModel initialized")
        // Observe connection state changes
        viewModelScope.launch {
            connectionManager.observeConnectionState().collect { state ->
                updateUiState(state)
            }
        }
        
        // Observe VPN state changes
        viewModelScope.launch {
            val app = context.applicationContext as com.sshtunnel.android.SSHTunnelProxyApp
            app.vpnController.vpnActiveState.collect { isActive ->
                logger.info(TAG, "VPN state changed: ${if (isActive) "Active" else "Inactive"}")
                if (isActive) {
                    onVpnStarted()
                } else {
                    onVpnStopped()
                }
            }
        }
    }
    
    /**
     * Sets the profile to connect to.
     * 
     * @param profileId ID of the profile to use for connection
     */
    fun setProfile(profileId: Long) {
        viewModelScope.launch {
            logger.info(TAG, "Setting profile ID: $profileId")
            val profile = profileRepository.getProfile(profileId)
            currentProfile = profile
            logger.verbose(TAG, "Profile set: ${profile?.name}")
            
            // If already connected to a different profile, show profile info
            if (_uiState.value is ConnectionUiState.Connected) {
                val connectedState = _uiState.value as ConnectionUiState.Connected
                if (connectedState.connection.profileId != profileId) {
                    // Different profile, disconnect first
                    logger.info(TAG, "Different profile selected, disconnecting current connection")
                    disconnect()
                }
            }
        }
    }
    
    /**
     * Connects to the SSH server using the current profile.
     */
    fun connect() {
        val profile = currentProfile
        if (profile == null) {
            logger.error(TAG, "Connect called but no profile selected")
            _uiState.value = ConnectionUiState.Error(
                "No profile selected",
                null
            )
            return
        }
        
        logger.info(TAG, "User initiated connection to profile: ${profile.name}")
        
        // Request VPN permission first
        _vpnPermissionNeeded.value = true
        
        viewModelScope.launch {
            // Clear any previous test results
            _testResult.value = TestResultState.None
            
            val result = connectionManager.connect(profile)
            result.onFailure { error ->
                logger.error(TAG, "Connection failed in ViewModel: ${error.message}", error)
                _uiState.value = ConnectionUiState.Error(
                    error.message ?: "Connection failed",
                    null
                )
            }
            // Success is handled by the state flow observer
        }
    }
    
    /**
     * Disconnects the current SSH connection.
     */
    fun disconnect() {
        logger.info(TAG, "User initiated disconnect")
        viewModelScope.launch {
            // Clear test results
            _testResult.value = TestResultState.None
            
            val result = connectionManager.disconnect()
            result.onFailure { error ->
                logger.error(TAG, "Disconnect failed: ${error.message}", error)
                // Even if disconnect fails, we should show disconnected state
                _uiState.value = ConnectionUiState.Disconnected
            }
            // Success is handled by the state flow observer
        }
    }
    
    /**
     * Tests the current connection by checking external IP.
     */
    fun testConnection() {
        viewModelScope.launch {
            _testResult.value = TestResultState.Testing
            
            val result = connectionTestService.testConnection()
            result.onSuccess { testResult ->
                _testResult.value = TestResultState.Success(testResult)
            }.onFailure { error ->
                _testResult.value = TestResultState.Error(
                    error.message ?: "Connection test failed"
                )
            }
        }
    }
    
    /**
     * Clears the connection test result.
     */
    fun clearTestResult() {
        _testResult.value = TestResultState.None
    }
    
    /**
     * Clears error state and returns to disconnected.
     */
    fun clearError() {
        if (_uiState.value is ConnectionUiState.Error) {
            _uiState.value = ConnectionUiState.Disconnected
        }
    }
    
    /**
     * Updates UI state based on connection manager state.
     * Only shows "Connected" when both SSH and VPN are active.
     */
    private fun updateUiState(state: ConnectionState) {
        logger.verbose(TAG, "Connection state changed: ${state::class.simpleName}")
        _uiState.value = when (state) {
            is ConnectionState.Disconnected -> {
                logger.info(TAG, "UI state updated to Disconnected")
                isVpnActive = false
                pendingConnection = null
                ConnectionUiState.Disconnected
            }
            is ConnectionState.Connecting -> {
                logger.info(TAG, "UI state updated to Connecting")
                ConnectionUiState.Connecting(currentProfile)
            }
            is ConnectionState.Connected -> {
                logger.info(TAG, "SSH connected (SOCKS port: ${state.connection.socksPort}), waiting for VPN")
                // SSH is connected, but we need to wait for VPN to start
                pendingConnection = state.connection
                
                // Don't show "Connected" yet - wait for VPN to start
                ConnectionUiState.WaitingForVpn(
                    connection = state.connection,
                    profile = currentProfile
                )
            }
            is ConnectionState.Error -> {
                logger.error(TAG, "UI state updated to Error: ${state.error.message}")
                isVpnActive = false
                pendingConnection = null
                ConnectionUiState.Error(
                    message = state.error.message,
                    error = state.error
                )
            }
        }
    }
    
    /**
     * Formats a connection error into a user-friendly message with suggestions.
     */
    fun getErrorDetails(error: ConnectionError?): ErrorDetails {
        return when (error) {
            is ConnectionError.AuthenticationFailed -> ErrorDetails(
                title = "Authentication Failed",
                message = error.message,
                suggestions = listOf(
                    "Verify your username is correct",
                    "Ensure your SSH key is authorized on the server (check ~/.ssh/authorized_keys)",
                    "Check that the key format matches what the server expects",
                    "Verify the key file is not corrupted"
                )
            )
            is ConnectionError.ConnectionTimeout -> ErrorDetails(
                title = "Connection Timeout",
                message = error.message,
                suggestions = listOf(
                    "Check your internet connection (WiFi or mobile data)",
                    "Verify the server is online and accessible",
                    "Check if a firewall is blocking SSH connections (port 22 or custom port)",
                    "Try increasing the connection timeout in settings",
                    "Verify the server address and port are correct"
                )
            )
            is ConnectionError.HostUnreachable -> ErrorDetails(
                title = "Host Unreachable",
                message = error.message,
                suggestions = listOf(
                    "Verify the hostname or IP address is correct",
                    "Check your internet connection",
                    "Ensure the server is online and accessible",
                    "Verify the port number (default SSH port is 22)",
                    "Check if a firewall is blocking the connection"
                )
            )
            is ConnectionError.PortForwardingDisabled -> ErrorDetails(
                title = "Port Forwarding Disabled",
                message = error.message,
                suggestions = listOf(
                    "Contact your server administrator to enable port forwarding",
                    "Ask them to add 'AllowTcpForwarding yes' to /etc/ssh/sshd_config",
                    "After changes, the SSH service needs to be restarted",
                    "Try a different SSH server that supports port forwarding"
                )
            )
            is ConnectionError.InvalidKey -> ErrorDetails(
                title = "Invalid SSH Key",
                message = error.message,
                suggestions = listOf(
                    "Verify the key file is not corrupted",
                    "Ensure the key is in a supported format (RSA, ECDSA, or Ed25519)",
                    "If passphrase-protected, verify the passphrase is correct",
                    "Try regenerating the SSH key pair",
                    "Ensure the key has proper permissions (600 for private key)"
                )
            )
            is ConnectionError.UnknownHost -> ErrorDetails(
                title = "Unknown Host",
                message = error.message,
                suggestions = listOf(
                    "Check the hostname spelling carefully",
                    "Verify your DNS is working (try opening a website)",
                    "Try using an IP address instead of hostname",
                    "Check if you need to be on a specific network (VPN, corporate network)"
                )
            )
            is ConnectionError.NetworkUnavailable -> ErrorDetails(
                title = "Network Unavailable",
                message = error.message,
                suggestions = listOf(
                    "Check your WiFi or mobile data connection",
                    "Try switching between WiFi and mobile data",
                    "Verify you have internet access (try opening a website)",
                    "If on a restricted network, it may be blocking SSH connections",
                    "Check if airplane mode is enabled"
                )
            )
            is ConnectionError.CredentialError -> ErrorDetails(
                title = "Credential Error",
                message = error.message,
                suggestions = listOf(
                    "Try editing the profile and re-selecting the SSH key",
                    "Check that the key file still exists on your device",
                    "Verify the app has permission to access the key file",
                    "Try creating a new profile with the same settings"
                )
            )
            is ConnectionError.Unknown, null -> ErrorDetails(
                title = "Connection Error",
                message = error?.message ?: "An unknown error occurred while connecting to the SSH server.",
                suggestions = listOf(
                    "Check your internet connection",
                    "Verify all profile settings are correct",
                    "Try disconnecting and reconnecting",
                    "Enable verbose logging in settings for more details",
                    "Check the diagnostic logs for technical information"
                )
            )
        }
    }
}

/**
 * UI state for the connection screen.
 */
sealed class ConnectionUiState {
    /**
     * No active connection.
     */
    data object Disconnected : ConnectionUiState()
    
    /**
     * Connection is being established.
     * 
     * @property profile The profile being connected to
     */
    data class Connecting(val profile: ServerProfile?) : ConnectionUiState()
    
    /**
     * SSH tunnel established, waiting for VPN to start.
     * 
     * @property connection The SSH connection details
     * @property profile The profile used for this connection
     */
    data class WaitingForVpn(
        val connection: Connection,
        val profile: ServerProfile?
    ) : ConnectionUiState()
    
    /**
     * Connection is fully active (both SSH and VPN).
     * 
     * @property connection The active connection details
     * @property profile The profile used for this connection
     */
    data class Connected(
        val connection: Connection,
        val profile: ServerProfile?
    ) : ConnectionUiState()
    
    /**
     * Connection failed or encountered an error.
     * 
     * @property message User-friendly error message
     * @property error The detailed error object
     */
    data class Error(
        val message: String,
        val error: ConnectionError?
    ) : ConnectionUiState()
}

/**
 * State for connection test results.
 */
sealed class TestResultState {
    /**
     * No test has been run.
     */
    data object None : TestResultState()
    
    /**
     * Test is currently running.
     */
    data object Testing : TestResultState()
    
    /**
     * Test completed successfully.
     * 
     * @property result The test result
     */
    data class Success(val result: ConnectionTestResult) : TestResultState()
    
    /**
     * Test failed.
     * 
     * @property message Error message
     */
    data class Error(val message: String) : TestResultState()
}

/**
 * Detailed error information with suggestions.
 * 
 * @property title Short error title
 * @property message Detailed error message
 * @property suggestions List of suggestions to fix the error
 */
data class ErrorDetails(
    val title: String,
    val message: String,
    val suggestions: List<String>
)
