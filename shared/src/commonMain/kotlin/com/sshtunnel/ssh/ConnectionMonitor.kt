package com.sshtunnel.ssh

import kotlinx.coroutines.flow.Flow

/**
 * Monitors SSH connection health and detects failures.
 * 
 * Responsibilities:
 * - Monitor SSH process health
 * - Check SOCKS5 port availability
 * - Detect connection state changes
 * - Emit connection state updates
 */
interface ConnectionMonitor {
    /**
     * Start monitoring connection health.
     * 
     * Monitors the SSH process and SOCKS5 port availability,
     * emitting connection state changes as they occur.
     * 
     * @param process SSH process to monitor
     * @param socksPort SOCKS5 port to check for availability
     * @return Flow of connection health states
     */
    fun monitorConnection(
        process: Process,
        socksPort: Int
    ): Flow<ConnectionHealthState>
    
    /**
     * Check if SOCKS5 port is accepting connections.
     * 
     * @param port Port to check
     * @return true if port is open and accepting connections
     */
    suspend fun isPortOpen(port: Int): Boolean
}

/**
 * Represents the health state of an SSH connection being monitored.
 */
sealed class ConnectionHealthState {
    /**
     * Connection is active and healthy.
     */
    object Healthy : ConnectionHealthState()
    
    /**
     * Connection has been disconnected.
     */
    object Disconnected : ConnectionHealthState()
    
    /**
     * Connection encountered an error.
     * 
     * @param message Error message describing what went wrong
     */
    data class Unhealthy(val message: String) : ConnectionHealthState()
}
