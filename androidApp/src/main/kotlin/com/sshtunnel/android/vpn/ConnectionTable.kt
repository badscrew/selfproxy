package com.sshtunnel.android.vpn

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe connection table for tracking active TCP and UDP connections
 * 
 * Manages connection lifecycle, cleanup, and statistics tracking.
 * Uses ConcurrentHashMap for concurrent access and Mutex for critical sections.
 * 
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 13.1, 13.2, 13.3, 13.4, 13.5
 */
class ConnectionTable {
    private val tcpConnections = ConcurrentHashMap<ConnectionKey, TcpConnection>()
    private val udpConnections = ConcurrentHashMap<ConnectionKey, UdpConnection>()
    private val lock = Mutex()
    
    // Statistics tracking
    private var totalTcpConnectionsCreated = 0
    private var totalUdpConnectionsCreated = 0
    
    /**
     * Add a TCP connection to the table
     * 
     * @param connection The TCP connection to add
     * Requirements: 9.1
     */
    suspend fun addTcpConnection(connection: TcpConnection) {
        lock.withLock {
            tcpConnections[connection.key] = connection
            totalTcpConnectionsCreated++
        }
    }
    
    /**
     * Retrieve a TCP connection by its key
     * 
     * @param key The connection key (5-tuple)
     * @return The TCP connection if found, null otherwise
     * Requirements: 9.1
     */
    suspend fun getTcpConnection(key: ConnectionKey): TcpConnection? {
        return tcpConnections[key]
    }
    
    /**
     * Remove a TCP connection from the table
     * 
     * @param key The connection key to remove
     * @return The removed TCP connection if found, null otherwise
     * Requirements: 9.2
     */
    suspend fun removeTcpConnection(key: ConnectionKey): TcpConnection? {
        return lock.withLock {
            tcpConnections.remove(key)
        }
    }
    
    /**
     * Get all active TCP connections
     * 
     * @return List of all TCP connections
     */
    suspend fun getAllTcpConnections(): List<TcpConnection> {
        return tcpConnections.values.toList()
    }
    
    /**
     * Add a UDP connection to the table
     * 
     * @param connection The UDP connection to add
     * Requirements: 9.1
     */
    suspend fun addUdpConnection(connection: UdpConnection) {
        lock.withLock {
            udpConnections[connection.key] = connection
            totalUdpConnectionsCreated++
        }
    }
    
    /**
     * Retrieve a UDP connection by its key
     * 
     * @param key The connection key (5-tuple)
     * @return The UDP connection if found, null otherwise
     * Requirements: 9.1
     */
    suspend fun getUdpConnection(key: ConnectionKey): UdpConnection? {
        return udpConnections[key]
    }
    
    /**
     * Remove a UDP connection from the table
     * 
     * @param key The connection key to remove
     * @return The removed UDP connection if found, null otherwise
     * Requirements: 9.2
     */
    suspend fun removeUdpConnection(key: ConnectionKey): UdpConnection? {
        return lock.withLock {
            udpConnections.remove(key)
        }
    }
    
    /**
     * Clean up idle connections that have exceeded the timeout
     * 
     * Closes connections that have been idle for longer than the specified timeout.
     * Default timeout is 2 minutes (120,000 ms).
     * 
     * @param idleTimeoutMs Idle timeout in milliseconds (default: 120,000 = 2 minutes)
     * Requirements: 9.3, 9.5
     */
    suspend fun cleanupIdleConnections(idleTimeoutMs: Long = 120_000) {
        val now = System.currentTimeMillis()
        val connectionsToRemove = mutableListOf<ConnectionKey>()
        
        lock.withLock {
            // Find idle TCP connections
            tcpConnections.forEach { (key, connection) ->
                if (now - connection.lastActivityAt > idleTimeoutMs) {
                    connectionsToRemove.add(key)
                }
            }
            
            // Remove idle TCP connections and close sockets
            connectionsToRemove.forEach { key ->
                tcpConnections.remove(key)?.let { connection ->
                    try {
                        connection.readerJob.cancel()
                        connection.socksSocket.close()
                    } catch (e: Exception) {
                        // Ignore errors during cleanup
                    }
                }
            }
            
            connectionsToRemove.clear()
            
            // Find idle UDP connections
            udpConnections.forEach { (key, connection) ->
                if (now - connection.lastActivityAt > idleTimeoutMs) {
                    connectionsToRemove.add(key)
                }
            }
            
            // Remove idle UDP connections and close sockets
            connectionsToRemove.forEach { key ->
                udpConnections.remove(key)?.let { connection ->
                    try {
                        connection.socksSocket?.close()
                    } catch (e: Exception) {
                        // Ignore errors during cleanup
                    }
                }
            }
        }
    }
    
    /**
     * Close all connections and clear the table
     * 
     * Used when stopping the packet router or VPN service.
     * 
     * Requirements: 9.4
     */
    suspend fun closeAllConnections() {
        lock.withLock {
            // Close all TCP connections
            tcpConnections.values.forEach { connection ->
                try {
                    connection.readerJob.cancel()
                    connection.socksSocket.close()
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
            }
            tcpConnections.clear()
            
            // Close all UDP connections
            udpConnections.values.forEach { connection ->
                try {
                    connection.socksSocket?.close()
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
            }
            udpConnections.clear()
        }
    }
    
    /**
     * Get connection statistics
     * 
     * Provides metrics about active connections and data transfer.
     * Thread-safe access without locking for performance.
     * 
     * @return ConnectionStatistics with current metrics
     * Requirements: 13.1, 13.2, 13.3, 13.4, 13.5
     */
    fun getStatistics(): ConnectionStatistics {
        val tcpConns = tcpConnections.values.toList()
        val udpConns = udpConnections.values.toList()
        
        val totalBytesSent = tcpConns.sumOf { it.bytesSent } + udpConns.sumOf { it.bytesSent }
        val totalBytesReceived = tcpConns.sumOf { it.bytesReceived } + udpConns.sumOf { it.bytesReceived }
        
        return ConnectionStatistics(
            totalTcpConnections = totalTcpConnectionsCreated,
            activeTcpConnections = tcpConns.size,
            totalUdpConnections = totalUdpConnectionsCreated,
            activeUdpConnections = udpConns.size,
            totalBytesSent = totalBytesSent,
            totalBytesReceived = totalBytesReceived
        )
    }
}

/**
 * Connection statistics data class
 * 
 * Tracks metrics about connection usage and data transfer.
 */
data class ConnectionStatistics(
    val totalTcpConnections: Int,
    val activeTcpConnections: Int,
    val totalUdpConnections: Int,
    val activeUdpConnections: Int,
    val totalBytesSent: Long,
    val totalBytesReceived: Long
)

/**
 * Type alias for router statistics (same as connection statistics)
 */
typealias RouterStatistics = ConnectionStatistics
