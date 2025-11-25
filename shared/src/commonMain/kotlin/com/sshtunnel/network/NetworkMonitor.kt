package com.sshtunnel.network

import kotlinx.coroutines.flow.Flow

/**
 * Interface for monitoring network connectivity changes.
 * 
 * Platform-specific implementations observe network state changes and emit
 * NetworkState updates through a Flow.
 */
interface NetworkMonitor {
    /**
     * Observes network connectivity changes.
     * 
     * Emits NetworkState updates when:
     * - Network becomes available
     * - Network is lost
     * - Network type changes (WiFi ↔ Mobile data)
     * - Network capabilities change
     * 
     * @return Flow of NetworkState updates
     */
    fun observeNetworkChanges(): Flow<NetworkState>
}

/**
 * Represents the current network connectivity state.
 */
sealed class NetworkState {
    /**
     * Network is available and connected.
     * 
     * @property networkType The type of network connection
     */
    data class Available(val networkType: NetworkType) : NetworkState()
    
    /**
     * Network connection was lost.
     */
    data object Lost : NetworkState()
    
    /**
     * Network type changed (e.g., WiFi to Mobile data).
     * 
     * @property fromType The previous network type
     * @property toType The new network type
     */
    data class Changed(val fromType: NetworkType, val toType: NetworkType) : NetworkState()
    
    /**
     * Network capabilities changed (e.g., metered status, bandwidth).
     * 
     * @property networkType The current network type
     * @property isMetered Whether the connection is metered
     */
    data class CapabilitiesChanged(
        val networkType: NetworkType,
        val isMetered: Boolean
    ) : NetworkState()
}

/**
 * Types of network connections.
 */
enum class NetworkType {
    /** WiFi connection */
    WIFI,
    
    /** Mobile data connection (cellular) */
    MOBILE,
    
    /** Ethernet connection */
    ETHERNET,
    
    /** VPN connection */
    VPN,
    
    /** Unknown or other connection type */
    UNKNOWN
}
