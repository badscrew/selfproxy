package com.sshtunnel.connection

import com.sshtunnel.data.ConnectionState
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.data.VpnStatistics
import com.sshtunnel.shadowsocks.ConnectionTestResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the lifecycle of Shadowsocks connections and VPN tunnels.
 * 
 * Coordinates between the Shadowsocks client (SOCKS5 proxy) and the VPN tunnel provider
 * to establish secure connections and route device traffic through the Shadowsocks server.
 */
interface ConnectionManager {
    /**
     * Establishes a connection to the Shadowsocks server and starts the VPN tunnel.
     * 
     * This method:
     * 1. Retrieves the password from secure storage
     * 2. Starts the Shadowsocks client (local SOCKS5 proxy)
     * 3. Starts the VPN tunnel to route traffic through the proxy
     * 4. Updates the connection state throughout the process
     * 
     * @param profile The server profile containing connection details
     * @return Result indicating success or failure with error details
     */
    suspend fun connect(profile: ServerProfile): Result<Unit>
    
    /**
     * Disconnects from the Shadowsocks server and stops the VPN tunnel.
     * 
     * This method:
     * 1. Stops the VPN tunnel
     * 2. Stops the Shadowsocks client
     * 3. Updates the connection state to Disconnected
     * 
     * @return Result indicating success or failure with error details
     */
    suspend fun disconnect(): Result<Unit>
    
    /**
     * Tests the connection to a Shadowsocks server without establishing a full connection.
     * 
     * This method validates:
     * - Server is reachable
     * - Credentials are correct
     * - Cipher is supported
     * 
     * @param profile The server profile to test
     * @return Result containing test results with latency and status
     */
    suspend fun testConnection(profile: ServerProfile): Result<ConnectionTestResult>
    
    /**
     * Observes the current connection state.
     * 
     * Emits state changes as the connection progresses through:
     * Disconnected → Connecting → Connected → (Reconnecting) → Disconnected
     * 
     * @return StateFlow emitting connection state changes
     */
    fun observeConnectionState(): StateFlow<ConnectionState>
    
    /**
     * Observes real-time VPN statistics.
     * 
     * Emits statistics updates including:
     * - Bytes sent/received
     * - Upload/download speeds
     * - Connection duration
     * 
     * @return StateFlow emitting statistics updates
     */
    fun observeStatistics(): StateFlow<VpnStatistics>
    
    /**
     * Gets the current connection state.
     * 
     * @return The current connection state
     */
    fun getCurrentState(): ConnectionState
}
