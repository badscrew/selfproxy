package com.sshtunnel.reconnection

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for automatic reconnection behavior.
 * 
 * This policy controls how the system handles connection losses and
 * attempts to reconnect with exponential backoff.
 */
data class ReconnectionPolicy(
    /**
     * Whether automatic reconnection is enabled.
     */
    val enabled: Boolean = true,
    
    /**
     * Maximum number of reconnection attempts before giving up.
     * Set to -1 for unlimited attempts.
     */
    val maxAttempts: Int = -1,
    
    /**
     * Initial backoff duration for the first reconnection attempt.
     */
    val initialBackoff: Duration = 1.seconds,
    
    /**
     * Maximum backoff duration between reconnection attempts.
     */
    val maxBackoff: Duration = 60.seconds,
    
    /**
     * Backoff multiplier for exponential backoff.
     * Each attempt waits: min(initialBackoff * (multiplier ^ attemptNumber), maxBackoff)
     */
    val backoffMultiplier: Double = 2.0,
    
    /**
     * Whether to reconnect on network changes (WiFi <-> Mobile data).
     */
    val reconnectOnNetworkChange: Boolean = true,
    
    /**
     * Whether to reconnect when keep-alive fails.
     */
    val reconnectOnKeepAliveFail: Boolean = true
) {
    companion object {
        /**
         * Default reconnection policy with standard settings.
         */
        val DEFAULT = ReconnectionPolicy()
        
        /**
         * Aggressive reconnection policy with shorter backoff times.
         */
        val AGGRESSIVE = ReconnectionPolicy(
            initialBackoff = 500.seconds,
            maxBackoff = 30.seconds
        )
        
        /**
         * Conservative reconnection policy with longer backoff times.
         */
        val CONSERVATIVE = ReconnectionPolicy(
            initialBackoff = 2.seconds,
            maxBackoff = 120.seconds
        )
        
        /**
         * Disabled reconnection policy.
         */
        val DISABLED = ReconnectionPolicy(
            enabled = false
        )
    }
}
