package com.sshtunnel.shadowsocks

import com.sshtunnel.data.ShadowsocksConfig
import kotlinx.coroutines.flow.Flow

/**
 * Interface for Shadowsocks client implementation.
 * Manages the local SOCKS5 proxy that connects to a Shadowsocks server.
 */
interface ShadowsocksClient {
    /**
     * Start the Shadowsocks local SOCKS5 proxy.
     * 
     * @param config Configuration for the Shadowsocks connection
     * @return Result containing the local SOCKS5 port on success, or error on failure
     */
    suspend fun start(config: ShadowsocksConfig): Result<Int>
    
    /**
     * Stop the Shadowsocks proxy.
     * Terminates the local SOCKS5 proxy and closes the connection to the server.
     */
    suspend fun stop()
    
    /**
     * Test connection to the Shadowsocks server.
     * Validates that the server is reachable and credentials are correct.
     * 
     * @param config Configuration to test
     * @return Result containing connection test result with latency and status
     */
    suspend fun testConnection(config: ShadowsocksConfig): Result<ConnectionTestResult>
    
    /**
     * Observe the current state of the Shadowsocks client.
     * 
     * @return Flow emitting state changes
     */
    fun observeState(): Flow<ShadowsocksState>
}

/**
 * Result of a connection test.
 * 
 * @property success Whether the connection test succeeded
 * @property latencyMs Round-trip latency in milliseconds (null if test failed)
 * @property errorMessage Error message if test failed (null if successful)
 */
data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long?,
    val errorMessage: String?
)

/**
 * Represents the current state of the Shadowsocks client.
 */
sealed class ShadowsocksState {
    /**
     * Client is idle, not running.
     */
    data object Idle : ShadowsocksState()
    
    /**
     * Client is starting up.
     */
    data object Starting : ShadowsocksState()
    
    /**
     * Client is running with local SOCKS5 proxy active.
     * 
     * @property localPort Port number of the local SOCKS5 proxy
     */
    data class Running(val localPort: Int) : ShadowsocksState()
    
    /**
     * Client encountered an error.
     * 
     * @property message Human-readable error message
     * @property cause Original exception that caused the error (if available)
     */
    data class Error(val message: String, val cause: Throwable?) : ShadowsocksState()
}
