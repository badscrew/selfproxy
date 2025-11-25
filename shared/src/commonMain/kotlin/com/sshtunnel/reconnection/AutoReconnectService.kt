package com.sshtunnel.reconnection

import com.sshtunnel.data.ServerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Service that monitors SSH connection health and automatically reconnects when needed.
 * 
 * This service:
 * - Detects SSH connection drops via keep-alive failures
 * - Triggers reconnection on network changes
 * - Uses exponential backoff for retry attempts
 * - Restores SOCKS5 proxy after successful reconnection
 */
interface AutoReconnectService {
    /**
     * Enables auto-reconnect for the specified profile.
     * 
     * @param profile The server profile to maintain connection for
     * @param passphrase Optional passphrase for the private key
     */
    suspend fun enable(profile: ServerProfile, passphrase: String? = null)
    
    /**
     * Disables auto-reconnect and stops monitoring.
     */
    suspend fun disable()
    
    /**
     * Observes reconnection attempts and their status.
     * 
     * @return Flow emitting reconnection attempt information with status
     */
    fun observeReconnectAttempts(): Flow<ReconnectAttemptWithStatus>
    
    /**
     * Checks if auto-reconnect is currently enabled.
     * 
     * @return true if auto-reconnect is enabled, false otherwise
     */
    fun isEnabled(): Boolean
}

/**
 * Information about a reconnection attempt with status.
 * 
 * @property attemptNumber The current attempt number (1-based)
 * @property reason The reason for the disconnection
 * @property status The status of this attempt
 */
data class ReconnectAttemptWithStatus(
    val attempt: ReconnectAttempt,
    val status: ReconnectStatus
)

/**
 * Status of a reconnection attempt.
 */
sealed class ReconnectStatus {
    /**
     * Attempting to reconnect.
     */
    data object Attempting : ReconnectStatus()
    
    /**
     * Reconnection succeeded.
     */
    data object Success : ReconnectStatus()
    
    /**
     * Reconnection failed, will retry.
     */
    data class Failed(val error: String) : ReconnectStatus()
    
    /**
     * Reconnection cancelled (auto-reconnect disabled).
     */
    data object Cancelled : ReconnectStatus()
}
