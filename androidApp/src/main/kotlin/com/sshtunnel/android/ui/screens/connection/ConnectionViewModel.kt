package com.sshtunnel.android.ui.screens.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshtunnel.connection.ConnectionManager
import com.sshtunnel.data.ConnectionState
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.data.VpnStatistics
import com.sshtunnel.repository.ProfileRepository
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
 * ViewModel for managing Shadowsocks connection state and operations.
 * 
 * Handles connection lifecycle, state observation, statistics, and connection testing.
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
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
    
    private val _statistics = MutableStateFlow(VpnStatistics())
    val statistics: StateFlow<VpnStatistics> = _statistics.asStateFlow()
    
    private var currentProfile: ServerProfile? = null
    
    companion object {
        private const val TAG = "ConnectionViewModel"
    }
    
    /**
     * Called when VPN permission is granted by the user.
     */
    fun onVpnPermissionGranted() {
        logger.info(TAG, "VPN permission granted")
        _vpnPermissionNeeded.value = false
        // Connection will proceed automatically
    }
    
    /**
     * Called when VPN permission is denied by the user.
     */
    fun onVpnPermissionDenied() {
        logger.info(TAG, "VPN permission denied")
        _vpnPermissionNeeded.value = false
        _uiState.value = ConnectionUiState.Error(
            "VPN permission is required to establish a secure tunnel",
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
        
        // Observe statistics updates
        viewModelScope.launch {
            connectionManager.observeStatistics().collect { stats ->
                _statistics.value = stats
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
     * Connects to the Shadowsocks server using the current profile.
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
            // Clear any previous test results and statistics
            _testResult.value = TestResultState.None
            _statistics.value = VpnStatistics()
            
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
     * Disconnects the current Shadowsocks connection.
     */
    fun disconnect() {
        logger.info(TAG, "User initiated disconnect")
        viewModelScope.launch {
            // Clear test results and statistics
            _testResult.value = TestResultState.None
            _statistics.value = VpnStatistics()
            
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
     */
    private fun updateUiState(state: ConnectionState) {
        logger.verbose(TAG, "Connection state changed: ${state::class.simpleName}")
        _uiState.value = when (state) {
            is ConnectionState.Disconnected -> {
                logger.info(TAG, "UI state updated to Disconnected")
                ConnectionUiState.Disconnected
            }
            is ConnectionState.Connecting -> {
                logger.info(TAG, "UI state updated to Connecting")
                ConnectionUiState.Connecting(currentProfile)
            }
            is ConnectionState.Connected -> {
                logger.info(TAG, "UI state updated to Connected (profile: ${state.profileId})")
                ConnectionUiState.Connected(
                    profileId = state.profileId,
                    serverAddress = state.serverAddress,
                    connectedAt = state.connectedAt,
                    profile = currentProfile
                )
            }
            is ConnectionState.Reconnecting -> {
                logger.info(TAG, "UI state updated to Reconnecting (attempt ${state.attempt})")
                ConnectionUiState.Reconnecting(
                    attempt = state.attempt,
                    profile = currentProfile
                )
            }
            is ConnectionState.Error -> {
                logger.error(TAG, "UI state updated to Error: ${state.message}")
                ConnectionUiState.Error(
                    message = state.message,
                    cause = state.cause
                )
            }
        }
    }
    
    /**
     * Formats a connection error into a user-friendly message with suggestions.
     */
    fun getErrorDetails(message: String, cause: Throwable?): ErrorDetails {
        // Analyze error message to provide specific suggestions
        val lowerMessage = message.lowercase()
        
        return when {
            "authentication" in lowerMessage || "password" in lowerMessage -> ErrorDetails(
                title = "Authentication Failed",
                message = message,
                suggestions = listOf(
                    "Verify your Shadowsocks password is correct",
                    "Check that the server is configured with the same password",
                    "Ensure the cipher method matches the server configuration",
                    "Try re-entering the password in the profile settings"
                )
            )
            "timeout" in lowerMessage -> ErrorDetails(
                title = "Connection Timeout",
                message = message,
                suggestions = listOf(
                    "Check your internet connection (WiFi or mobile data)",
                    "Verify the server is online and accessible",
                    "Check if a firewall is blocking the connection",
                    "Verify the server address and port are correct",
                    "Try increasing the connection timeout"
                )
            )
            "unreachable" in lowerMessage || "host" in lowerMessage -> ErrorDetails(
                title = "Server Unreachable",
                message = message,
                suggestions = listOf(
                    "Verify the hostname or IP address is correct",
                    "Check your internet connection",
                    "Ensure the server is online and accessible",
                    "Verify the port number is correct",
                    "Check if a firewall is blocking the connection"
                )
            )
            "cipher" in lowerMessage || "encryption" in lowerMessage -> ErrorDetails(
                title = "Encryption Error",
                message = message,
                suggestions = listOf(
                    "Verify the cipher method matches the server configuration",
                    "Try a different cipher method (aes-256-gcm, chacha20-ietf-poly1305)",
                    "Check that the server supports the selected cipher",
                    "Update the server to support modern AEAD ciphers"
                )
            )
            "network" in lowerMessage -> ErrorDetails(
                title = "Network Error",
                message = message,
                suggestions = listOf(
                    "Check your WiFi or mobile data connection",
                    "Try switching between WiFi and mobile data",
                    "Verify you have internet access",
                    "Check if airplane mode is enabled",
                    "If on a restricted network, it may be blocking connections"
                )
            )
            "vpn" in lowerMessage || "permission" in lowerMessage -> ErrorDetails(
                title = "VPN Error",
                message = message,
                suggestions = listOf(
                    "Grant VPN permission when prompted",
                    "Check that another VPN is not already active",
                    "Try restarting the app",
                    "Check Android VPN settings",
                    "Ensure the app has necessary permissions"
                )
            )
            else -> ErrorDetails(
                title = "Connection Error",
                message = message,
                suggestions = listOf(
                    "Check your internet connection",
                    "Verify all profile settings are correct",
                    "Try disconnecting and reconnecting",
                    "Check that the Shadowsocks server is running",
                    "Verify server address, port, password, and cipher are correct"
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
     * Connection is being re-established after a failure.
     * 
     * @property attempt The reconnection attempt number
     * @property profile The profile being reconnected to
     */
    data class Reconnecting(
        val attempt: Int,
        val profile: ServerProfile?
    ) : ConnectionUiState()
    
    /**
     * Connection is fully active.
     * 
     * @property profileId The ID of the connected profile
     * @property serverAddress The server address
     * @property connectedAt The connection timestamp
     * @property profile The profile used for this connection
     */
    data class Connected(
        val profileId: Long,
        val serverAddress: String,
        val connectedAt: kotlinx.datetime.Instant,
        val profile: ServerProfile?
    ) : ConnectionUiState()
    
    /**
     * Connection failed or encountered an error.
     * 
     * @property message User-friendly error message
     * @property cause The error cause
     */
    data class Error(
        val message: String,
        val cause: Throwable?
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
