package com.sshtunnel.android.vpn

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LoggerImpl

/**
 * Thread-safe connection table for tracking active TCP and UDP connections
 * 
 * Manages connection lifecycle, cleanup, and statistics tracking.
 * Uses ConcurrentHashMap for concurrent access and Mutex for critical sections.
 * 
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 13.1, 13.2, 13.3, 13.4, 13.5
 */
class ConnectionTable(
    private val logger: Logger = LoggerImpl()
) {
    private val tcpConnections = ConcurrentHashMap<ConnectionKey, TcpConnection>()
    private val udpConnections = ConcurrentHashMap<ConnectionKey, UdpConnection>()
    private val udpAssociateConnections = ConcurrentHashMap<ConnectionKey, UdpAssociateConnection>()
    private val lock = Mutex()
    
    // Statistics tracking
    private var totalTcpConnectionsCreated = 0
    private var totalUdpConnectionsCreated = 0
    private var totalUdpAssociateConnectionsCreated = 0
    
    companion object {
        private const val TAG = "ConnectionTable"
    }
    
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
     * Add a UDP ASSOCIATE connection to the table
     * 
     * UDP ASSOCIATE connections are used for SOCKS5 UDP relay.
     * Each connection maintains a TCP control socket and a UDP relay socket.
     * 
     * @param connection The UDP ASSOCIATE connection to add
     * Requirements: 2.1, 9.1
     */
    suspend fun addUdpAssociateConnection(connection: UdpAssociateConnection) {
        lock.withLock {
            udpAssociateConnections[connection.key] = connection
            totalUdpAssociateConnectionsCreated++
        }
    }
    
    /**
     * Retrieve a UDP ASSOCIATE connection by its key
     * 
     * @param key The connection key (5-tuple)
     * @return The UDP ASSOCIATE connection if found, null otherwise
     * Requirements: 2.1, 6.1, 6.2
     */
    suspend fun getUdpAssociateConnection(key: ConnectionKey): UdpAssociateConnection? {
        return udpAssociateConnections[key]
    }
    
    /**
     * Remove a UDP ASSOCIATE connection from the table
     * 
     * @param key The connection key to remove
     * @return The removed UDP ASSOCIATE connection if found, null otherwise
     * Requirements: 2.2, 2.3
     */
    suspend fun removeUdpAssociateConnection(key: ConnectionKey): UdpAssociateConnection? {
        return lock.withLock {
            udpAssociateConnections.remove(key)
        }
    }
    
    /**
     * Update UDP ASSOCIATE connection statistics
     * 
     * Updates the bytes sent/received counters and last activity timestamp
     * for a UDP ASSOCIATE connection.
     * 
     * @param key The connection key
     * @param bytesSent Number of bytes sent (added to existing count)
     * @param bytesReceived Number of bytes received (added to existing count)
     * Requirements: 2.4, 9.1, 9.2, 9.4
     */
    suspend fun updateUdpAssociateStats(
        key: ConnectionKey,
        bytesSent: Long = 0,
        bytesReceived: Long = 0
    ) {
        udpAssociateConnections[key]?.let { connection ->
            connection.bytesSent += bytesSent
            connection.bytesReceived += bytesReceived
            connection.lastActivityAt = System.currentTimeMillis()
        }
    }
    
    /**
     * Clean up idle connections that have exceeded the timeout
     * 
     * Closes connections that have been idle for longer than the specified timeout.
     * Default timeout is 2 minutes (120,000 ms).
     * 
     * Also cleans up connections in TIME_WAIT state that have exceeded the TIME_WAIT timeout.
     * 
     * @param idleTimeoutMs Idle timeout in milliseconds (default: 120,000 = 2 minutes)
     * @param timeWaitTimeoutMs TIME_WAIT timeout in milliseconds (default: 30,000 = 30 seconds)
     * Requirements: 9.3, 9.5, 12.3
     */
    suspend fun cleanupIdleConnections(
        idleTimeoutMs: Long = 120_000,
        timeWaitTimeoutMs: Long = 30_000
    ) {
        val now = System.currentTimeMillis()
        val connectionsToRemove = mutableListOf<Pair<ConnectionKey, TcpConnection>>()
        val udpConnectionsToRemove = mutableListOf<Pair<ConnectionKey, UdpConnection>>()
        val udpAssociateConnectionsToRemove = mutableListOf<Pair<ConnectionKey, UdpAssociateConnection>>()
        
        lock.withLock {
            // Find idle TCP connections or connections in TIME_WAIT
            tcpConnections.forEach { (key, connection) ->
                val timeout = if (connection.state == TcpState.TIME_WAIT) {
                    timeWaitTimeoutMs
                } else {
                    idleTimeoutMs
                }
                
                if (now - connection.lastActivityAt > timeout) {
                    connectionsToRemove.add(key to connection)
                }
            }
            
            // Remove idle TCP connections and close sockets
            connectionsToRemove.forEach { (key, connection) ->
                tcpConnections.remove(key)
                try {
                    connection.readerJob.cancel()
                    connection.socksSocket.close()
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
                
                // Log connection closure with duration
                val duration = now - connection.createdAt
                val durationSeconds = duration / 1000.0
                val reason = if (connection.state == TcpState.TIME_WAIT) "TIME_WAIT_timeout" else "idle_timeout"
                logger.info(
                    TAG,
                    "TCP connection closed: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort} " +
                    "(reason=$reason, duration=${String.format("%.2f", durationSeconds)}s, " +
                    "sent=${connection.bytesSent} bytes, received=${connection.bytesReceived} bytes)"
                )
            }
            
            // Find idle UDP connections
            udpConnections.forEach { (key, connection) ->
                if (now - connection.lastActivityAt > idleTimeoutMs) {
                    udpConnectionsToRemove.add(key to connection)
                }
            }
            
            // Remove idle UDP connections and close sockets
            udpConnectionsToRemove.forEach { (key, connection) ->
                udpConnections.remove(key)
                try {
                    connection.socksSocket?.close()
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
                
                // Log UDP connection closure with duration
                val duration = now - connection.createdAt
                val durationSeconds = duration / 1000.0
                logger.info(
                    TAG,
                    "UDP connection closed: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort} " +
                    "(reason=idle_timeout, duration=${String.format("%.2f", durationSeconds)}s, " +
                    "sent=${connection.bytesSent} bytes, received=${connection.bytesReceived} bytes)"
                )
            }
            
            // Find idle UDP ASSOCIATE connections
            udpAssociateConnections.forEach { (key, connection) ->
                if (now - connection.lastActivityAt > idleTimeoutMs) {
                    udpAssociateConnectionsToRemove.add(key to connection)
                }
            }
            
            // Remove idle UDP ASSOCIATE connections and close sockets
            udpAssociateConnectionsToRemove.forEach { (key, connection) ->
                udpAssociateConnections.remove(key)
                try {
                    connection.readerJob.cancel()
                    connection.relaySocket.close()
                    connection.controlSocket.close()
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
                
                // Log UDP ASSOCIATE connection closure with duration and statistics
                val duration = now - connection.createdAt
                val durationSeconds = duration / 1000.0
                logger.info(
                    TAG,
                    "UDP ASSOCIATE connection closed: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort} " +
                    "(reason=idle_timeout, duration=${String.format("%.2f", durationSeconds)}s, " +
                    "sent=${connection.bytesSent} bytes, received=${connection.bytesReceived} bytes)"
                )
            }
            
            if (connectionsToRemove.isNotEmpty() || udpConnectionsToRemove.isNotEmpty() || udpAssociateConnectionsToRemove.isNotEmpty()) {
                logger.debug(
                    TAG,
                    "Cleaned up ${connectionsToRemove.size} idle TCP connections, " +
                    "${udpConnectionsToRemove.size} idle UDP connections, and " +
                    "${udpAssociateConnectionsToRemove.size} idle UDP ASSOCIATE connections"
                )
            }
        }
    }
    
    /**
     * Close all connections and clear the table
     * 
     * Used when stopping the packet router or VPN service.
     * 
     * Requirements: 2.3, 9.4
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
            
            // Close all UDP ASSOCIATE connections
            udpAssociateConnections.values.forEach { connection ->
                try {
                    connection.readerJob.cancel()
                    connection.relaySocket.close()
                    connection.controlSocket.close()
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
            }
            udpAssociateConnections.clear()
        }
    }
    
    /**
     * Get connection statistics
     * 
     * Provides metrics about active connections and data transfer.
     * Thread-safe access without locking for performance.
     * Includes UDP ASSOCIATE connection metrics.
     * 
     * @return ConnectionStatistics with current metrics
     * Requirements: 2.4, 9.3, 13.1, 13.2, 13.3, 13.4, 13.5
     */
    fun getStatistics(): ConnectionStatistics {
        val tcpConns = tcpConnections.values.toList()
        val udpConns = udpConnections.values.toList()
        val udpAssociateConns = udpAssociateConnections.values.toList()
        
        val totalBytesSent = tcpConns.sumOf { it.bytesSent } + 
                            udpConns.sumOf { it.bytesSent } + 
                            udpAssociateConns.sumOf { it.bytesSent }
        val totalBytesReceived = tcpConns.sumOf { it.bytesReceived } + 
                                udpConns.sumOf { it.bytesReceived } + 
                                udpAssociateConns.sumOf { it.bytesReceived }
        
        return ConnectionStatistics(
            totalTcpConnections = totalTcpConnectionsCreated,
            activeTcpConnections = tcpConns.size,
            totalUdpConnections = totalUdpConnectionsCreated,
            activeUdpConnections = udpConns.size,
            totalUdpAssociateConnections = totalUdpAssociateConnectionsCreated,
            activeUdpAssociateConnections = udpAssociateConns.size,
            totalBytesSent = totalBytesSent,
            totalBytesReceived = totalBytesReceived
        )
    }
}

/**
 * Connection statistics data class
 * 
 * Tracks metrics about connection usage and data transfer.
 * Includes separate counters for UDP ASSOCIATE connections.
 * 
 * Requirements: 9.3, 9.5
 */
data class ConnectionStatistics(
    val totalTcpConnections: Int,
    val activeTcpConnections: Int,
    val totalUdpConnections: Int,
    val activeUdpConnections: Int,
    val totalUdpAssociateConnections: Int,
    val activeUdpAssociateConnections: Int,
    val totalBytesSent: Long,
    val totalBytesReceived: Long
)

/**
 * Type alias for router statistics (same as connection statistics)
 */
typealias RouterStatistics = ConnectionStatistics
