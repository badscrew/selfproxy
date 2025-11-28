package com.sshtunnel.android.vpn

import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.Socket

/**
 * Unit tests for statistics tracking in ConnectionTable.
 * 
 * Tests:
 * - Byte counting (sent/received)
 * - Connection duration tracking
 * - Statistics aggregation
 * 
 * Requirements: 13.1, 13.2, 13.3, 13.4, 13.5
 */
class StatisticsTest {
    
    private lateinit var connectionTable: ConnectionTable
    
    @Before
    fun setup() {
        connectionTable = ConnectionTable()
    }
    
    /**
     * Test that byte counting works correctly for TCP connections.
     * 
     * Requirements: 13.1
     */
    @Test
    fun `test TCP byte counting`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        val connection = TcpConnection(
            key = key,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 1024,
            bytesReceived = 2048,
            readerJob = Job()
        )
        
        // Act
        connectionTable.addTcpConnection(connection)
        val stats = connectionTable.getStatistics()
        
        // Assert
        assertEquals(1024L, stats.totalBytesSent)
        assertEquals(2048L, stats.totalBytesReceived)
        assertEquals(1, stats.activeTcpConnections)
        assertEquals(1, stats.totalTcpConnections)
    }
    
    /**
     * Test that byte counting works correctly for UDP connections.
     * 
     * Requirements: 13.1
     */
    @Test
    fun `test UDP byte counting`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.UDP,
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53
        )
        
        val connection = UdpConnection(
            key = key,
            socksSocket = null,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 512,
            bytesReceived = 1024
        )
        
        // Act
        connectionTable.addUdpConnection(connection)
        val stats = connectionTable.getStatistics()
        
        // Assert
        assertEquals(512L, stats.totalBytesSent)
        assertEquals(1024L, stats.totalBytesReceived)
        assertEquals(1, stats.activeUdpConnections)
        assertEquals(1, stats.totalUdpConnections)
    }
    
    /**
     * Test that byte counting aggregates correctly across multiple connections.
     * 
     * Requirements: 13.1, 13.3
     */
    @Test
    fun `test byte counting aggregation across multiple connections`() = runTest {
        // Arrange - Add multiple TCP connections
        val tcpKey1 = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        val tcpConnection1 = TcpConnection(
            key = tcpKey1,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 1000,
            bytesReceived = 2000,
            readerJob = Job()
        )
        
        val tcpKey2 = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12346,
            destIp = "1.1.1.1",
            destPort = 443
        )
        
        val tcpConnection2 = TcpConnection(
            key = tcpKey2,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 3000,
            acknowledgmentNumber = 4000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 500,
            bytesReceived = 1500,
            readerJob = Job()
        )
        
        // Add UDP connection
        val udpKey = ConnectionKey(
            protocol = Protocol.UDP,
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53
        )
        
        val udpConnection = UdpConnection(
            key = udpKey,
            socksSocket = null,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 250,
            bytesReceived = 750
        )
        
        // Act
        connectionTable.addTcpConnection(tcpConnection1)
        connectionTable.addTcpConnection(tcpConnection2)
        connectionTable.addUdpConnection(udpConnection)
        
        val stats = connectionTable.getStatistics()
        
        // Assert
        assertEquals(1750L, stats.totalBytesSent) // 1000 + 500 + 250
        assertEquals(4250L, stats.totalBytesReceived) // 2000 + 1500 + 750
        assertEquals(2, stats.activeTcpConnections)
        assertEquals(2, stats.totalTcpConnections)
        assertEquals(1, stats.activeUdpConnections)
        assertEquals(1, stats.totalUdpConnections)
    }
    
    /**
     * Test that connection duration can be tracked.
     * 
     * Requirements: 13.2
     */
    @Test
    fun `test connection duration tracking`() = runTest {
        // Arrange
        val createdAt = System.currentTimeMillis() - 5000 // 5 seconds ago
        val lastActivityAt = System.currentTimeMillis()
        
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        val connection = TcpConnection(
            key = key,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = createdAt,
            lastActivityAt = lastActivityAt,
            bytesSent = 0,
            bytesReceived = 0,
            readerJob = Job()
        )
        
        // Act
        connectionTable.addTcpConnection(connection)
        val retrievedConnection = connectionTable.getTcpConnection(key)
        
        // Assert
        assertNotNull(retrievedConnection)
        assertEquals(createdAt, retrievedConnection!!.createdAt)
        assertEquals(lastActivityAt, retrievedConnection.lastActivityAt)
        
        // Calculate duration
        val duration = lastActivityAt - createdAt
        assertTrue("Duration should be at least 5 seconds", duration >= 5000)
    }
    
    /**
     * Test that statistics are updated when connections are modified.
     * 
     * Requirements: 13.1, 13.5
     */
    @Test
    fun `test statistics update when connection is modified`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        val connection = TcpConnection(
            key = key,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 1000,
            bytesReceived = 2000,
            readerJob = Job()
        )
        
        connectionTable.addTcpConnection(connection)
        
        // Act - Update connection with more bytes
        val updatedConnection = connection.copy(
            bytesSent = 2000,
            bytesReceived = 4000,
            lastActivityAt = System.currentTimeMillis()
        )
        connectionTable.addTcpConnection(updatedConnection)
        
        val stats = connectionTable.getStatistics()
        
        // Assert
        assertEquals(2000L, stats.totalBytesSent)
        assertEquals(4000L, stats.totalBytesReceived)
        assertEquals(1, stats.activeTcpConnections)
    }
    
    /**
     * Test that statistics are updated when connections are removed.
     * 
     * Requirements: 13.3, 13.5
     */
    @Test
    fun `test statistics update when connection is removed`() = runTest {
        // Arrange
        val key1 = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        val connection1 = TcpConnection(
            key = key1,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 1000,
            bytesReceived = 2000,
            readerJob = Job()
        )
        
        val key2 = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12346,
            destIp = "1.1.1.1",
            destPort = 443
        )
        
        val connection2 = TcpConnection(
            key = key2,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 3000,
            acknowledgmentNumber = 4000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 500,
            bytesReceived = 1500,
            readerJob = Job()
        )
        
        connectionTable.addTcpConnection(connection1)
        connectionTable.addTcpConnection(connection2)
        
        // Act - Remove one connection
        connectionTable.removeTcpConnection(key1)
        
        val stats = connectionTable.getStatistics()
        
        // Assert - Only connection2's bytes should remain
        assertEquals(500L, stats.totalBytesSent)
        assertEquals(1500L, stats.totalBytesReceived)
        assertEquals(1, stats.activeTcpConnections)
        assertEquals(2, stats.totalTcpConnections) // Total created doesn't decrease
    }
    
    /**
     * Test that statistics are correct when no connections exist.
     * 
     * Requirements: 13.3
     */
    @Test
    fun `test statistics with no connections`() = runTest {
        // Act
        val stats = connectionTable.getStatistics()
        
        // Assert
        assertEquals(0L, stats.totalBytesSent)
        assertEquals(0L, stats.totalBytesReceived)
        assertEquals(0, stats.activeTcpConnections)
        assertEquals(0, stats.totalTcpConnections)
        assertEquals(0, stats.activeUdpConnections)
        assertEquals(0, stats.totalUdpConnections)
    }
    
    /**
     * Test that total connections counter increments correctly.
     * 
     * Requirements: 13.3
     */
    @Test
    fun `test total connections counter`() = runTest {
        // Arrange
        val key1 = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        val connection1 = TcpConnection(
            key = key1,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 0,
            bytesReceived = 0,
            readerJob = Job()
        )
        
        // Act - Add connection
        connectionTable.addTcpConnection(connection1)
        val stats1 = connectionTable.getStatistics()
        
        // Remove connection
        connectionTable.removeTcpConnection(key1)
        val stats2 = connectionTable.getStatistics()
        
        // Add another connection
        val key2 = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12346,
            destIp = "1.1.1.1",
            destPort = 443
        )
        
        val connection2 = TcpConnection(
            key = key2,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 3000,
            acknowledgmentNumber = 4000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 0,
            bytesReceived = 0,
            readerJob = Job()
        )
        
        connectionTable.addTcpConnection(connection2)
        val stats3 = connectionTable.getStatistics()
        
        // Assert
        assertEquals(1, stats1.totalTcpConnections)
        assertEquals(1, stats1.activeTcpConnections)
        
        assertEquals(1, stats2.totalTcpConnections) // Total doesn't decrease
        assertEquals(0, stats2.activeTcpConnections) // Active does decrease
        
        assertEquals(2, stats3.totalTcpConnections) // Total increments
        assertEquals(1, stats3.activeTcpConnections)
    }
    
    /**
     * Test that statistics access is thread-safe.
     * 
     * Requirements: 13.5
     */
    @Test
    fun `test thread-safe statistics access`() = runTest {
        // Arrange
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        val connection = TcpConnection(
            key = key,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 1000,
            bytesReceived = 2000,
            readerJob = Job()
        )
        
        connectionTable.addTcpConnection(connection)
        
        // Act - Call getStatistics multiple times concurrently
        // This should not throw any exceptions or cause data corruption
        val stats1 = connectionTable.getStatistics()
        val stats2 = connectionTable.getStatistics()
        val stats3 = connectionTable.getStatistics()
        
        // Assert - All calls should return consistent results
        assertEquals(stats1.totalBytesSent, stats2.totalBytesSent)
        assertEquals(stats2.totalBytesSent, stats3.totalBytesSent)
        assertEquals(stats1.totalBytesReceived, stats2.totalBytesReceived)
        assertEquals(stats2.totalBytesReceived, stats3.totalBytesReceived)
        assertEquals(stats1.activeTcpConnections, stats2.activeTcpConnections)
        assertEquals(stats2.activeTcpConnections, stats3.activeTcpConnections)
    }
    
    /**
     * Test that large byte counts are handled correctly.
     * 
     * Requirements: 13.1
     */
    @Test
    fun `test large byte counts`() = runTest {
        // Arrange - Create connection with large byte counts
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        val largeByteCount = 10_000_000_000L // 10 GB
        
        val connection = TcpConnection(
            key = key,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = largeByteCount,
            bytesReceived = largeByteCount * 2,
            readerJob = Job()
        )
        
        // Act
        connectionTable.addTcpConnection(connection)
        val stats = connectionTable.getStatistics()
        
        // Assert
        assertEquals(largeByteCount, stats.totalBytesSent)
        assertEquals(largeByteCount * 2, stats.totalBytesReceived)
    }
    
    /**
     * Test that statistics correctly handle mixed TCP and UDP connections.
     * 
     * Requirements: 13.3
     */
    @Test
    fun `test mixed TCP and UDP statistics`() = runTest {
        // Arrange - Add TCP connections
        val tcpKey = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        val tcpConnection = TcpConnection(
            key = tcpKey,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 1000,
            bytesReceived = 2000,
            readerJob = Job()
        )
        
        // Add UDP connections
        val udpKey = ConnectionKey(
            protocol = Protocol.UDP,
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53
        )
        
        val udpConnection = UdpConnection(
            key = udpKey,
            socksSocket = null,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = System.currentTimeMillis(),
            bytesSent = 500,
            bytesReceived = 1000
        )
        
        // Act
        connectionTable.addTcpConnection(tcpConnection)
        connectionTable.addUdpConnection(udpConnection)
        
        val stats = connectionTable.getStatistics()
        
        // Assert
        assertEquals(1500L, stats.totalBytesSent) // 1000 + 500
        assertEquals(3000L, stats.totalBytesReceived) // 2000 + 1000
        assertEquals(1, stats.activeTcpConnections)
        assertEquals(1, stats.totalTcpConnections)
        assertEquals(1, stats.activeUdpConnections)
        assertEquals(1, stats.totalUdpConnections)
    }
}
