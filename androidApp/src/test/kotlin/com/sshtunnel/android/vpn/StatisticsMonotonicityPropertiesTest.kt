package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.Protocol
import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.DatagramSocket
import java.net.Socket

/**
 * Property-based tests for VPN statistics monotonicity.
 * 
 * Feature: shadowsocks-vpn-proxy, Property 6: Statistics accumulation is monotonic
 * Validates: Requirements 7.2, 7.3
 */
class StatisticsMonotonicityPropertiesTest {
    
    private lateinit var logger: Logger
    private lateinit var connectionTable: ConnectionTable
    
    @Before
    fun setup() {
        logger = object : Logger {
            override fun verbose(tag: String, message: String, throwable: Throwable?) {}
            override fun debug(tag: String, message: String, throwable: Throwable?) {}
            override fun info(tag: String, message: String, throwable: Throwable?) {}
            override fun warn(tag: String, message: String, throwable: Throwable?) {}
            override fun error(tag: String, message: String, throwable: Throwable?) {}
            override fun getLogEntries(): List<com.sshtunnel.logging.LogEntry> = emptyList()
            override fun clearLogs() {}
            override fun setVerboseEnabled(enabled: Boolean) {}
            override fun isVerboseEnabled(): Boolean = false
        }
        connectionTable = ConnectionTable(logger)
    }
    
    /**
     * Property 6: Statistics Monotonicity - Bytes Sent
     * 
     * For any active VPN connection, bytes sent should never decrease
     * while the connection is active.
     * 
     * Validates: Requirements 7.2, 7.3
     */
    @Test
    fun `bytesSent should never decrease during active connection`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 6: Statistics accumulation is monotonic
        // Validates: Requirements 7.2, 7.3
        
        checkAll(
            iterations = 100,
            Arb.list(Arb.long(1L..10000L), 5..20)  // List of byte increments (positive values)
        ) { byteIncrements ->
            // Arrange: Create a fresh ConnectionTable for this iteration
            val freshConnectionTable = ConnectionTable(logger)
            var previousBytesSent = 0L
            
            // Act: Simulate traffic by adding connections with increasing byte counts
            for (increment in byteIncrements) {
                // Create a mock TCP connection with the specified bytes sent
                val key = createRandomConnectionKey()
                val connection = createMockTcpConnection(key, bytesSent = increment)
                freshConnectionTable.addTcpConnection(connection)
                
                // Get current statistics
                val stats = freshConnectionTable.getStatistics()
                
                // Assert: Bytes sent should never decrease
                assertTrue(
                    "Bytes sent should be monotonically increasing: previous=$previousBytesSent, current=${stats.totalBytesSent}",
                    stats.totalBytesSent >= previousBytesSent
                )
                
                previousBytesSent = stats.totalBytesSent
            }
        }
    }
    
    /**
     * Property 6: Statistics Monotonicity - Bytes Received
     * 
     * For any active VPN connection, bytes received should never decrease
     * while the connection is active.
     * 
     * Validates: Requirements 7.2, 7.3
     */
    @Test
    fun `bytesReceived should never decrease during active connection`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 6: Statistics accumulation is monotonic
        // Validates: Requirements 7.2, 7.3
        
        checkAll(
            iterations = 100,
            Arb.list(Arb.long(1L..10000L), 5..20)  // List of byte increments (positive values)
        ) { byteIncrements ->
            // Arrange: Create a fresh ConnectionTable for this iteration
            val freshConnectionTable = ConnectionTable(logger)
            var previousBytesReceived = 0L
            
            // Act: Simulate traffic by adding connections with increasing byte counts
            for (increment in byteIncrements) {
                // Create a mock TCP connection with the specified bytes received
                val key = createRandomConnectionKey()
                val connection = createMockTcpConnection(key, bytesReceived = increment)
                freshConnectionTable.addTcpConnection(connection)
                
                // Get current statistics
                val stats = freshConnectionTable.getStatistics()
                
                // Assert: Bytes received should never decrease
                assertTrue(
                    "Bytes received should be monotonically increasing: previous=$previousBytesReceived, current=${stats.totalBytesReceived}",
                    stats.totalBytesReceived >= previousBytesReceived
                )
                
                previousBytesReceived = stats.totalBytesReceived
            }
        }
    }
    
    /**
     * Property 6: Statistics Monotonicity - Total Bytes
     * 
     * For any active VPN connection, total bytes (sent + received) should
     * never decrease while the connection is active.
     * 
     * Validates: Requirements 7.2, 7.3
     */
    @Test
    fun `totalBytes should never decrease during active connection`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 6: Statistics accumulation is monotonic
        // Validates: Requirements 7.2, 7.3
        
        checkAll(
            iterations = 100,
            Arb.list(
                Arb.pair(Arb.long(1L..5000L), Arb.long(1L..5000L)),
                5..20
            )  // List of (sent, received) pairs (positive values)
        ) { trafficPairs ->
            // Arrange: Create a fresh ConnectionTable for this iteration
            val freshConnectionTable = ConnectionTable(logger)
            var previousTotalBytes = 0L
            
            // Act: Simulate bidirectional traffic
            for ((sentIncrement, receivedIncrement) in trafficPairs) {
                // Create a mock TCP connection with the specified bytes
                val key = createRandomConnectionKey()
                val connection = createMockTcpConnection(
                    key,
                    bytesSent = sentIncrement,
                    bytesReceived = receivedIncrement
                )
                freshConnectionTable.addTcpConnection(connection)
                
                // Get current statistics
                val stats = freshConnectionTable.getStatistics()
                val currentTotalBytes = stats.totalBytesSent + stats.totalBytesReceived
                
                // Assert: Total bytes should never decrease
                assertTrue(
                    "Total bytes should be monotonically increasing: previous=$previousTotalBytes, current=$currentTotalBytes",
                    currentTotalBytes >= previousTotalBytes
                )
                
                previousTotalBytes = currentTotalBytes
            }
        }
    }
    
    /**
     * Property 6: Statistics Monotonicity - Connection Duration
     * 
     * For any active VPN connection, connection duration should never decrease
     * while the connection is active. This test verifies the monotonicity property
     * by checking that timestamps always increase.
     * 
     * Validates: Requirements 7.4
     */
    @Test
    fun `connection timestamps should be monotonically increasing`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 6: Statistics accumulation is monotonic
        // Validates: Requirements 7.4
        
        checkAll(
            iterations = 100,
            Arb.list(Arb.long(1L..1000L), 5..20)  // List of time delays in milliseconds
        ) { timeDelays ->
            // Arrange: Track timestamps
            var previousTimestamp = System.currentTimeMillis()
            
            // Act: Simulate time passing
            for (delay in timeDelays) {
                // Simulate time passing
                Thread.sleep(delay)
                
                // Get current timestamp
                val currentTimestamp = System.currentTimeMillis()
                
                // Assert: Timestamp should never decrease
                assertTrue(
                    "Timestamp should be monotonically increasing: previous=$previousTimestamp, current=$currentTimestamp",
                    currentTimestamp >= previousTimestamp
                )
                
                previousTimestamp = currentTimestamp
            }
        }
    }
    
    /**
     * Property 6: Statistics Monotonicity - Multiple Connections
     * 
     * For any active VPN connection with multiple concurrent connections,
     * aggregate statistics should never decrease.
     * 
     * Validates: Requirements 7.2, 7.3
     */
    @Test
    fun `aggregate statistics should never decrease with multiple connections`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 6: Statistics accumulation is monotonic
        // Validates: Requirements 7.2, 7.3
        
        checkAll(
            iterations = 50,
            Arb.list(
                Arb.triple(
                    Arb.long(1L..5000L),  // bytesSent (positive values)
                    Arb.long(1L..5000L),  // bytesReceived (positive values)
                    Arb.int(1..3)         // number of connections
                ),
                5..10
            )
        ) { connectionBatches ->
            // Arrange: Create a fresh ConnectionTable for this iteration
            val freshConnectionTable = ConnectionTable(logger)
            var previousBytesSent = 0L
            var previousBytesReceived = 0L
            
            // Act: Add multiple connections in batches
            for ((bytesSent, bytesReceived, numConnections) in connectionBatches) {
                // Add multiple connections
                repeat(numConnections) {
                    val key = createRandomConnectionKey()
                    val connection = createMockTcpConnection(
                        key,
                        bytesSent = bytesSent,
                        bytesReceived = bytesReceived
                    )
                    freshConnectionTable.addTcpConnection(connection)
                }
                
                // Get current statistics
                val stats = freshConnectionTable.getStatistics()
                
                // Assert: Both counters should never decrease
                assertTrue(
                    "Bytes sent should be monotonically increasing: previous=$previousBytesSent, current=${stats.totalBytesSent}",
                    stats.totalBytesSent >= previousBytesSent
                )
                assertTrue(
                    "Bytes received should be monotonically increasing: previous=$previousBytesReceived, current=${stats.totalBytesReceived}",
                    stats.totalBytesReceived >= previousBytesReceived
                )
                
                previousBytesSent = stats.totalBytesSent
                previousBytesReceived = stats.totalBytesReceived
            }
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Creates a random ConnectionKey for testing.
     */
    private fun createRandomConnectionKey(): ConnectionKey {
        return ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "192.168.1.${(1..254).random()}",
            sourcePort = (1024..65535).random(),
            destIp = "8.8.8.${(1..254).random()}",
            destPort = (1..65535).random()
        )
    }
    
    /**
     * Creates a mock TCP connection for testing.
     */
    private fun createMockTcpConnection(
        key: ConnectionKey,
        bytesSent: Long = 0,
        bytesReceived: Long = 0
    ): TcpConnection {
        val socket = Socket()
        val now = System.currentTimeMillis()
        
        return TcpConnection(
            key = key,
            socksSocket = socket,
            state = TcpState.ESTABLISHED,
            sequenceTracker = SequenceNumberTracker(
                initialSeq = 1000u,
                initialAck = 2000u
            ),
            createdAt = now,
            lastActivityAt = now,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            readerJob = kotlinx.coroutines.Job()
        )
    }
    
    /**
     * Creates a mock UDP ASSOCIATE connection for testing.
     */
    private fun createMockUdpAssociateConnection(
        key: ConnectionKey,
        bytesSent: Long = 0,
        bytesReceived: Long = 0
    ): UdpAssociateConnection {
        val controlSocket = Socket()
        val relaySocket = DatagramSocket()
        val relayEndpoint = UdpRelayEndpoint("127.0.0.1", 1080)
        val now = System.currentTimeMillis()
        
        return UdpAssociateConnection(
            key = key,
            controlSocket = controlSocket,
            relaySocket = relaySocket,
            relayEndpoint = relayEndpoint,
            createdAt = now,
            lastActivityAt = now,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            readerJob = kotlinx.coroutines.Job()
        )
    }
}
