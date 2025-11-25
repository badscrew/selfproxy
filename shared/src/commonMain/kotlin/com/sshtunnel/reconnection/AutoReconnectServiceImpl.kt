package com.sshtunnel.reconnection

import com.sshtunnel.data.ServerProfile
import com.sshtunnel.network.NetworkMonitor
import com.sshtunnel.network.NetworkState
import com.sshtunnel.ssh.ConnectionState
import com.sshtunnel.ssh.SSHConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of AutoReconnectService.
 * 
 * This service monitors the SSH connection and network state, automatically
 * reconnecting when the connection drops or the network changes.
 * 
 * @property connectionManager SSH connection manager
 * @property networkMonitor Platform-specific network monitor
 * @property reconnectionStateMachine State machine for calculating backoff
 * @property keepAliveInterval Interval for sending keep-alive packets
 * @property scope Coroutine scope for background work
 */
class AutoReconnectServiceImpl(
    private val connectionManager: SSHConnectionManager,
    private val networkMonitor: NetworkMonitor,
    private val reconnectionStateMachine: ReconnectionStateMachine,
    private val keepAliveInterval: Duration = 60.seconds,
    private val scope: CoroutineScope
) : AutoReconnectService {
    
    private val _reconnectAttempts = MutableSharedFlow<ReconnectAttemptWithStatus>()
    override fun observeReconnectAttempts(): Flow<ReconnectAttemptWithStatus> = _reconnectAttempts.asSharedFlow()
    
    private var currentProfile: ServerProfile? = null
    private var currentPassphrase: String? = null
    private var enabled = false
    
    private var monitoringJob: Job? = null
    private var keepAliveJob: Job? = null
    private var reconnectJob: Job? = null
    
    private var attemptNumber = 0
    private var lastNetworkState: NetworkState? = null
    
    override suspend fun enable(profile: ServerProfile, passphrase: String?) {
        // Cancel any existing monitoring
        disable()
        
        currentProfile = profile
        currentPassphrase = passphrase
        enabled = true
        attemptNumber = 0
        
        // Start monitoring connection state
        monitoringJob = scope.launch {
            connectionManager.observeConnectionState().collect { state ->
                handleConnectionStateChange(state)
            }
        }
        
        // Start monitoring network changes
        scope.launch {
            networkMonitor.observeNetworkChanges().collect { networkState ->
                handleNetworkChange(networkState)
            }
        }
    }
    
    override suspend fun disable() {
        enabled = false
        
        // Cancel all monitoring jobs
        monitoringJob?.cancel()
        keepAliveJob?.cancel()
        reconnectJob?.cancel()
        
        monitoringJob = null
        keepAliveJob = null
        reconnectJob = null
        
        currentProfile = null
        currentPassphrase = null
        attemptNumber = 0
        lastNetworkState = null
    }
    
    override fun isEnabled(): Boolean = enabled
    
    /**
     * Handles connection state changes.
     */
    private suspend fun handleConnectionStateChange(state: ConnectionState) {
        when (state) {
            is ConnectionState.Connected -> {
                // Connection established, reset attempt counter and start keep-alive
                attemptNumber = 0
                startKeepAlive()
            }
            is ConnectionState.Disconnected -> {
                // Connection lost, trigger reconnection if enabled
                if (enabled && currentProfile != null) {
                    triggerReconnect(DisconnectReason.ConnectionLost)
                }
            }
            is ConnectionState.Error -> {
                // Connection error, trigger reconnection if enabled
                if (enabled && currentProfile != null) {
                    triggerReconnect(DisconnectReason.Unknown(state.error.message))
                }
            }
            is ConnectionState.Connecting -> {
                // Do nothing while connecting
            }
        }
    }
    
    /**
     * Handles network state changes.
     */
    private suspend fun handleNetworkChange(networkState: NetworkState) {
        val previousState = lastNetworkState
        lastNetworkState = networkState
        
        // Only trigger reconnect if we had a previous state and it changed
        if (previousState != null && previousState != networkState) {
            // Network changed, trigger reconnection if we're connected or trying to connect
            val currentState = connectionManager.observeConnectionState().value
            if (currentState is ConnectionState.Connected && enabled && currentProfile != null) {
                // Disconnect current connection and reconnect on new network
                connectionManager.disconnect()
                triggerReconnect(DisconnectReason.NetworkChanged)
            }
        }
    }
    
    /**
     * Starts keep-alive monitoring.
     */
    private fun startKeepAlive() {
        // Cancel any existing keep-alive job
        keepAliveJob?.cancel()
        
        keepAliveJob = scope.launch {
            while (isActive && enabled) {
                delay(keepAliveInterval)
                
                // Check if connection is still alive
                val currentState = connectionManager.observeConnectionState().value
                if (currentState !is ConnectionState.Connected) {
                    // Connection lost, trigger reconnection
                    if (enabled && currentProfile != null) {
                        triggerReconnect(DisconnectReason.KeepAliveFailed)
                    }
                    break
                }
                
                // In a real implementation, we would send a keep-alive packet here
                // For now, we just check the connection state
            }
        }
    }
    
    /**
     * Triggers a reconnection attempt with exponential backoff.
     */
    private suspend fun triggerReconnect(reason: DisconnectReason) {
        // Cancel any existing reconnect job
        reconnectJob?.cancel()
        
        reconnectJob = scope.launch {
            while (isActive && enabled && currentProfile != null) {
                attemptNumber++
                
                // Calculate backoff delay
                val backoffDelay = reconnectionStateMachine.calculateBackoff(attemptNumber)
                
                // Create attempt object
                val attempt = ReconnectAttempt(
                    attemptNumber = attemptNumber,
                    nextRetryIn = backoffDelay,
                    reason = reason
                )
                
                // Emit attempt information
                _reconnectAttempts.emit(
                    ReconnectAttemptWithStatus(
                        attempt = attempt,
                        status = ReconnectStatus.Attempting
                    )
                )
                
                // Wait before attempting reconnection
                if (attemptNumber > 1) {
                    delay(backoffDelay)
                }
                
                // Check if still enabled
                if (!enabled || currentProfile == null) {
                    _reconnectAttempts.emit(
                        ReconnectAttemptWithStatus(
                            attempt = attempt.copy(nextRetryIn = Duration.ZERO),
                            status = ReconnectStatus.Cancelled
                        )
                    )
                    break
                }
                
                // Attempt reconnection
                val result = connectionManager.connect(currentProfile!!, currentPassphrase)
                
                if (result.isSuccess) {
                    // Reconnection succeeded
                    _reconnectAttempts.emit(
                        ReconnectAttemptWithStatus(
                            attempt = attempt.copy(nextRetryIn = Duration.ZERO),
                            status = ReconnectStatus.Success
                        )
                    )
                    attemptNumber = 0
                    break
                } else {
                    // Reconnection failed, will retry
                    val nextBackoff = reconnectionStateMachine.calculateBackoff(attemptNumber + 1)
                    _reconnectAttempts.emit(
                        ReconnectAttemptWithStatus(
                            attempt = attempt.copy(nextRetryIn = nextBackoff),
                            status = ReconnectStatus.Failed(
                                result.exceptionOrNull()?.message ?: "Unknown error"
                            )
                        )
                    )
                }
            }
        }
    }
}
