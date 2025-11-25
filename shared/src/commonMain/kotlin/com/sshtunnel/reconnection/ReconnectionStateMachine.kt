package com.sshtunnel.reconnection

import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * State machine for managing SSH connection reconnection attempts.
 * 
 * Implements exponential backoff strategy with a maximum retry interval
 * to handle connection drops and network changes gracefully.
 */
class ReconnectionStateMachine {
    private var currentAttempt: Int = 0
    private val maxRetryInterval: Duration = 60.seconds
    
    /**
     * Calculates the backoff duration for the current reconnection attempt.
     * 
     * Uses exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s, 60s (max)
     * Formula: min(2^attemptNumber, 60) seconds
     * 
     * @param attemptNumber The current attempt number (0-indexed)
     * @return Duration to wait before the next reconnection attempt
     */
    fun calculateBackoff(attemptNumber: Int): Duration {
        require(attemptNumber >= 0) { "Attempt number must be non-negative" }
        
        val seconds = min(2.0.pow(attemptNumber).toInt(), maxRetryInterval.inWholeSeconds.toInt())
        return seconds.seconds
    }
    
    /**
     * Records a reconnection attempt and returns the backoff duration.
     * 
     * @return Duration to wait before the next attempt
     */
    fun recordAttempt(): Duration {
        val backoff = calculateBackoff(currentAttempt)
        currentAttempt++
        return backoff
    }
    
    /**
     * Resets the state machine after a successful connection.
     * 
     * This should be called when a connection is successfully established
     * to reset the attempt counter for future reconnection scenarios.
     */
    fun reset() {
        currentAttempt = 0
    }
    
    /**
     * Gets the current attempt number.
     * 
     * @return The current attempt number (0-indexed)
     */
    fun getCurrentAttempt(): Int = currentAttempt
    
    /**
     * Gets the maximum retry interval.
     * 
     * @return The maximum duration between retry attempts
     */
    fun getMaxRetryInterval(): Duration = maxRetryInterval
}

/**
 * Represents the reason for a disconnection.
 */
sealed class DisconnectReason {
    /**
     * Connection was lost unexpectedly (e.g., network issue, server timeout).
     */
    data object ConnectionLost : DisconnectReason()
    
    /**
     * Network changed (e.g., WiFi to mobile data).
     */
    data object NetworkChanged : DisconnectReason()
    
    /**
     * Keep-alive packet failed.
     */
    data object KeepAliveFailed : DisconnectReason()
    
    /**
     * User manually disconnected.
     */
    data object UserDisconnected : DisconnectReason()
    
    /**
     * Authentication failed during reconnection.
     */
    data object AuthenticationFailed : DisconnectReason()
    
    /**
     * Unknown reason.
     */
    data class Unknown(val message: String) : DisconnectReason()
}

/**
 * Represents a reconnection attempt.
 * 
 * @property attemptNumber The attempt number (0-indexed)
 * @property nextRetryIn Duration until the next retry attempt
 * @property reason The reason for the disconnection
 */
data class ReconnectAttempt(
    val attemptNumber: Int,
    val nextRetryIn: Duration,
    val reason: DisconnectReason
)
