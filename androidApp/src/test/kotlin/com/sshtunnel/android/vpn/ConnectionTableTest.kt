package com.sshtunnel.android.vpn

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.Socket

/**
 * Unit tests for ConnectionTable
 * 
 * Tests connection management, cleanup, statistics, and thread safety.
 */
class ConnectionTableTest {
    
    private lateinit var connectionTable: ConnectionTable
    
    @Before
    fun setup() {
        connectionTable = ConnectionTable()
    }
    
    @Test
    fun `adding and retrieving TCP connection should work`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        val connection = createMockTcpConnection(key)
        
        // Act
        connectionTable.addTcpConnection(connection)
        val retrieved = connectionTable.getTcpConnection(key)
        
        // Assert
        assertNotNull(retrieved)
        assertEquals(key, retrieved?.key)
        assertEquals(TcpState.ESTABLISHED, retrieved?.state)
    }
    
    @Test
    fun `adding and retrieving UDP connection should work`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.UDP,
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53
        )
        val connection = createMockUdpConnection(key)
        
        // Act
        connectionTable.addUdpConnection(connection)
        val retrieved = connectionTable.getUdpConnection(key)
        
        // Assert
        assertNotNull(retrieved)
        assertEquals(key, retrieved?.key)
    }
    
    @Test
    fun `retrieving non-existent TCP connection should return null`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        // Act
        val retrieved = connectionTable.getTcpConnection(key)
        
        // Assert
        assertNull(retrieved)
    }
    
    @Test
    fun `removing TCP connection should work`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        val connection = createMockTcpConnection(key)
        connectionTable.addTcpConnection(connection)
        
        // Act
        val removed = connectionTable.removeTcpConnection(key)
        val retrieved = connectionTable.getTcpConnection(key)
        
        // Assert
        assertNotNull(removed)
        assertEquals(key, removed?.key)
        assertNull(retrieved)
    }
    
    @Test
    fun `removing UDP connection should work`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.UDP,
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53
        )
        val connection = createMockUdpConnection(key)
        connectionTable.addUdpConnection(connection)
        
        // Act
        val removed = connectionTable.removeUdpConnection(key)
        val retrieved = connectionTable.getUdpConnection(key)
        
        // Assert
        assertNotNull(removed)
        assertEquals(key, removed?.key)
        assertNull(retrieved)
    }
    
    @Test
    fun `idle connection cleanup should remove old connections`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val oldTime = now - 150_000 // 2.5 minutes ago (older than 2 minute timeout)
        val recentTime = now - 60_000 // 1 minute ago (within timeout)
        
        val oldKey = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        val oldConnection = createMockTcpConnection(oldKey, lastActivityAt = oldTime)
        
        val recentKey = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12346,
            destIp = "1.1.1.1",
            destPort = 443
        )
        val recentConnection = createMockTcpConnection(recentKey, lastActivityAt = recentTime)
        
        connectionTable.addTcpConnection(oldConnection)
        connectionTable.addTcpConnection(recentConnection)
        
        // Act
        connectionTable.cleanupIdleConnections(idleTimeoutMs = 120_000)
        
        // Assert
        val oldRetrieved = connectionTable.getTcpConnection(oldKey)
        val recentRetrieved = connectionTable.getTcpConnection(recentKey)
        
        assertNull(oldRetrieved) // Old connection should be removed
        assertNotNull(recentRetrieved) // Recent connection should remain
    }
    
    @Test
    fun `idle UDP connection cleanup should work`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val oldTime = now - 150_000 // 2.5 minutes ago
        
        val key = ConnectionKey(
            protocol = Protocol.UDP,
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53
        )
        val connection = createMockUdpConnection(key, lastActivityAt = oldTime)
        
        connectionTable.addUdpConnection(connection)
        
        // Act
        connectionTable.cleanupIdleConnections(idleTimeoutMs = 120_000)
        
        // Assert
        val retrieved = connectionTable.getUdpConnection(key)
        assertNull(retrieved)
    }
    
    @Test
    fun `closeAllConnections should remove all connections`() = runTest {
        // Arrange
        val tcpKey = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        val tcpConnection = createMockTcpConnection(tcpKey)
        
        val udpKey = ConnectionKey(
            protocol = Protocol.UDP,
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53
        )
        val udpConnection = createMockUdpConnection(udpKey)
        
        connectionTable.addTcpConnection(tcpConnection)
        connectionTable.addUdpConnection(udpConnection)
        
        // Act
        connectionTable.closeAllConnections()
        
        // Assert
        val tcpRetrieved = connectionTable.getTcpConnection(tcpKey)
        val udpRetrieved = connectionTable.getUdpConnection(udpKey)
        
        assertNull(tcpRetrieved)
        assertNull(udpRetrieved)
    }
    
    @Test
    fun `statistics should track connections correctly`() = runTest {
        // Arrange
        val tcpKey1 = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        val tcpConnection1 = createMockTcpConnection(
            tcpKey1,
            bytesSent = 1000,
            bytesReceived = 2000
        )
        
        val tcpKey2 = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12346,
            destIp = "1.1.1.1",
            destPort = 443
        )
        val tcpConnection2 = createMockTcpConnection(
            tcpKey2,
            bytesSent = 500,
            bytesReceived = 1500
        )
        
        val udpKey = ConnectionKey(
            protocol = Protocol.UDP,
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53
        )
        val udpConnection = createMockUdpConnection(
            udpKey,
            bytesSent = 100,
            bytesReceived = 200
        )
        
        // Act
        connectionTable.addTcpConnection(tcpConnection1)
        connectionTable.addTcpConnection(tcpConnection2)
        connectionTable.addUdpConnection(udpConnection)
        
        val stats = connectionTable.getStatistics()
        
        // Assert
        assertEquals(2, stats.totalTcpConnections)
        assertEquals(2, stats.activeTcpConnections)
        assertEquals(1, stats.totalUdpConnections)
        assertEquals(1, stats.activeUdpConnections)
        assertEquals(1600L, stats.totalBytesSent) // 1000 + 500 + 100
        assertEquals(3700L, stats.totalBytesReceived) // 2000 + 1500 + 200
    }
    
    @Test
    fun `statistics should reflect removed connections`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        val connection = createMockTcpConnection(key)
        
        connectionTable.addTcpConnection(connection)
        
        // Act
        val statsBefore = connectionTable.getStatistics()
        connectionTable.removeTcpConnection(key)
        val statsAfter = connectionTable.getStatistics()
        
        // Assert
        assertEquals(1, statsBefore.totalTcpConnections)
        assertEquals(1, statsBefore.activeTcpConnections)
        assertEquals(1, statsAfter.totalTcpConnections) // Total doesn't decrease
        assertEquals(0, statsAfter.activeTcpConnections) // Active decreases
    }
    
    @Test
    fun `concurrent access should be thread-safe`() = runTest {
        // Arrange
        val numConnections = 100
        
        // Act - Add connections concurrently
        val jobs = (1..numConnections).map { i ->
            launch {
                val key = ConnectionKey(
                    protocol = Protocol.TCP,
                    sourceIp = "10.0.0.2",
                    sourcePort = 10000 + i,
                    destIp = "1.1.1.1",
                    destPort = 80
                )
                val connection = createMockTcpConnection(key)
                connectionTable.addTcpConnection(connection)
            }
        }
        
        jobs.forEach { it.join() }
        
        // Assert
        val stats = connectionTable.getStatistics()
        assertEquals(numConnections, stats.activeTcpConnections)
    }
    
    @Test
    fun `concurrent cleanup should be thread-safe`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val oldTime = now - 150_000
        
        // Add some old connections
        repeat(10) { i ->
            val key = ConnectionKey(
                protocol = Protocol.TCP,
                sourceIp = "10.0.0.2",
                sourcePort = 10000 + i,
                destIp = "1.1.1.1",
                destPort = 80
            )
            val connection = createMockTcpConnection(key, lastActivityAt = oldTime)
            connectionTable.addTcpConnection(connection)
        }
        
        // Act - Run cleanup concurrently
        val jobs = (1..5).map {
            launch {
                connectionTable.cleanupIdleConnections(idleTimeoutMs = 120_000)
            }
        }
        
        jobs.forEach { it.join() }
        
        // Assert - All connections should be cleaned up
        val stats = connectionTable.getStatistics()
        assertEquals(0, stats.activeTcpConnections)
    }
    
    // Helper functions
    
    private fun createMockTcpConnection(
        key: ConnectionKey,
        state: TcpState = TcpState.ESTABLISHED,
        lastActivityAt: Long = System.currentTimeMillis(),
        bytesSent: Long = 0,
        bytesReceived: Long = 0
    ): TcpConnection {
        return TcpConnection(
            key = key,
            socksSocket = createMockSocket(),
            state = state,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = lastActivityAt,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            readerJob = Job()
        )
    }
    
    private fun createMockUdpConnection(
        key: ConnectionKey,
        lastActivityAt: Long = System.currentTimeMillis(),
        bytesSent: Long = 0,
        bytesReceived: Long = 0
    ): UdpConnection {
        return UdpConnection(
            key = key,
            socksSocket = null,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = lastActivityAt,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived
        )
    }
    
    private fun createMockSocket(): Socket {
        // Create a mock socket that doesn't actually connect
        // In a real test, you might use a mock framework like MockK
        return Socket()
    }
}
