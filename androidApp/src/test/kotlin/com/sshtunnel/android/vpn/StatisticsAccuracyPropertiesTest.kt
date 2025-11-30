package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.FileOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.DatagramSocket
import java.net.Socket

/**
 * Property-based tests for UDP ASSOCIATE statistics tracking accuracy.
 * 
 * Feature: socks5-udp-associate, Property 8: Statistics Accuracy
 * Validates: Requirements 9.1, 9.2, 9.3, 9.4
 */
class StatisticsAccuracyPropertiesTest {
    
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
     * Property 8: Statistics Accuracy - Bytes Sent
     * 
     * For any UDP ASSOCIATE connection, when we send a known number of bytes,
     * the bytesSent counter should match exactly.
     * 
     * Validates: Requirements 9.1, 9.2, 9.3, 9.4
     */
    @Test
    fun `bytesSent counter should accurately track sent bytes`() = runTest {
        // Feature: socks5-udp-associate, Property 8: Statistics Accuracy
        // Validates: Requirements 9.1, 9.2, 9.3, 9.4
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(),
            Arb.list(Arb.int(1..1500), 1..10)  // List of packet sizes
        ) { key, packetSizes ->
            // Arrange: Create a mock UDP ASSOCIATE connection
            val connection = createMockUdpAssociateConnection(key)
            connectionTable.addUdpAssociateConnection(connection)
            
            // Act: Simulate sending multiple packets
            var expectedBytesSent = 0L
            for (packetSize in packetSizes) {
                connectionTable.updateUdpAssociateStats(
                    key = key,
                    bytesSent = packetSize.toLong()
                )
                expectedBytesSent += packetSize
            }
            
            // Assert: Verify bytesSent counter matches
            val updatedConnection = connectionTable.getUdpAssociateConnection(key)
            assertNotNull("Connection should exist", updatedConnection)
            assertEquals(
                "bytesSent should match total bytes sent",
                expectedBytesSent,
                updatedConnection!!.bytesSent
            )
            
            // Cleanup
            connectionTable.removeUdpAssociateConnection(key)
        }
    }
    
    /**
     * Property 8: Statistics Accuracy - Bytes Received
     * 
     * For any UDP ASSOCIATE connection, when we receive a known number of bytes,
     * the bytesReceived counter should match exactly.
     * 
     * Validates: Requirements 9.1, 9.2, 9.3, 9.4
     */
    @Test
    fun `bytesReceived counter should accurately track received bytes`() = runTest {
        // Feature: socks5-udp-associate, Property 8: Statistics Accuracy
        // Validates: Requirements 9.1, 9.2, 9.3, 9.4
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(),
            Arb.list(Arb.int(1..1500), 1..10)  // List of packet sizes
        ) { key, packetSizes ->
            // Arrange: Create a mock UDP ASSOCIATE connection
            val connection = createMockUdpAssociateConnection(key)
            connectionTable.addUdpAssociateConnection(connection)
            
            // Act: Simulate receiving multiple packets
            var expectedBytesReceived = 0L
            for (packetSize in packetSizes) {
                connectionTable.updateUdpAssociateStats(
                    key = key,
                    bytesReceived = packetSize.toLong()
                )
                expectedBytesReceived += packetSize
            }
            
            // Assert: Verify bytesReceived counter matches
            val updatedConnection = connectionTable.getUdpAssociateConnection(key)
            assertNotNull("Connection should exist", updatedConnection)
            assertEquals(
                "bytesReceived should match total bytes received",
                expectedBytesReceived,
                updatedConnection!!.bytesReceived
            )
            
            // Cleanup
            connectionTable.removeUdpAssociateConnection(key)
        }
    }
    
    /**
     * Property 8: Statistics Accuracy - Bidirectional Traffic
     * 
     * For any UDP ASSOCIATE connection, when we send and receive bytes,
     * both counters should track independently and accurately.
     * 
     * Validates: Requirements 9.1, 9.2, 9.3, 9.4
     */
    @Test
    fun `bytesSent and bytesReceived should track independently`() = runTest {
        // Feature: socks5-udp-associate, Property 8: Statistics Accuracy
        // Validates: Requirements 9.1, 9.2, 9.3, 9.4
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(),
            Arb.list(Arb.pair(Arb.int(1..1500), Arb.int(1..1500)), 1..10)  // Pairs of (sent, received)
        ) { key, trafficPairs ->
            // Arrange: Create a mock UDP ASSOCIATE connection
            val connection = createMockUdpAssociateConnection(key)
            connectionTable.addUdpAssociateConnection(connection)
            
            // Act: Simulate bidirectional traffic
            var expectedBytesSent = 0L
            var expectedBytesReceived = 0L
            for ((sentSize, receivedSize) in trafficPairs) {
                connectionTable.updateUdpAssociateStats(
                    key = key,
                    bytesSent = sentSize.toLong(),
                    bytesReceived = receivedSize.toLong()
                )
                expectedBytesSent += sentSize
                expectedBytesReceived += receivedSize
            }
            
            // Assert: Verify both counters match
            val updatedConnection = connectionTable.getUdpAssociateConnection(key)
            assertNotNull("Connection should exist", updatedConnection)
            assertEquals(
                "bytesSent should match total bytes sent",
                expectedBytesSent,
                updatedConnection!!.bytesSent
            )
            assertEquals(
                "bytesReceived should match total bytes received",
                expectedBytesReceived,
                updatedConnection.bytesReceived
            )
            
            // Cleanup
            connectionTable.removeUdpAssociateConnection(key)
        }
    }
    
    /**
     * Property 8: Statistics Accuracy - Global Statistics
     * 
     * For any set of UDP ASSOCIATE connections, the global statistics
     * should accurately sum all individual connection statistics.
     * 
     * Validates: Requirements 9.1, 9.2, 9.3, 9.4
     */
    @Test
    fun `global statistics should accurately sum all connections`() = runTest {
        // Feature: socks5-udp-associate, Property 8: Statistics Accuracy
        // Validates: Requirements 9.1, 9.2, 9.3, 9.4
        
        checkAll(
            iterations = 50,
            Arb.list(
                Arb.bind(
                    Arb.connectionKey(),
                    Arb.long(0L..100000L),  // bytesSent
                    Arb.long(0L..100000L)   // bytesReceived
                ) { key, sent, received -> Triple(key, sent, received) },
                1..5  // Number of connections
            )
        ) { connectionData ->
            // Arrange: Create a fresh ConnectionTable for this iteration
            val freshConnectionTable = ConnectionTable(logger)
            
            // Create multiple UDP ASSOCIATE connections with different stats
            val connections = mutableListOf<UdpAssociateConnection>()
            var expectedTotalBytesSent = 0L
            var expectedTotalBytesReceived = 0L
            
            for ((key, bytesSent, bytesReceived) in connectionData) {
                val connection = createMockUdpAssociateConnection(key).copy(
                    bytesSent = bytesSent,
                    bytesReceived = bytesReceived
                )
                freshConnectionTable.addUdpAssociateConnection(connection)
                connections.add(connection)
                expectedTotalBytesSent += bytesSent
                expectedTotalBytesReceived += bytesReceived
            }
            
            // Act: Get global statistics
            val stats = freshConnectionTable.getStatistics()
            
            // Assert: Verify global statistics match sum of individual connections
            assertEquals(
                "Total UDP ASSOCIATE connections should match",
                connections.size,
                stats.totalUdpAssociateConnections
            )
            assertEquals(
                "Active UDP ASSOCIATE connections should match",
                connections.size,
                stats.activeUdpAssociateConnections
            )
            
            // Verify that the bytes sent/received match our expected amounts
            assertEquals(
                "Total bytes sent should match expected amount",
                expectedTotalBytesSent,
                stats.totalBytesSent
            )
            assertEquals(
                "Total bytes received should match expected amount",
                expectedTotalBytesReceived,
                stats.totalBytesReceived
            )
            
            // Cleanup
            for (connection in connections) {
                freshConnectionTable.removeUdpAssociateConnection(connection.key)
            }
        }
    }
    
    /**
     * Property 8: Statistics Accuracy - Last Activity Timestamp
     * 
     * For any UDP ASSOCIATE connection, updating statistics should also
     * update the lastActivityAt timestamp.
     * 
     * Validates: Requirements 9.1, 9.4
     */
    @Test
    fun `updating statistics should update lastActivityAt timestamp`() = runTest {
        // Feature: socks5-udp-associate, Property 8: Statistics Accuracy
        // Validates: Requirements 9.1, 9.4
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(),
            Arb.int(1..1500)
        ) { key, packetSize ->
            // Arrange: Create a mock UDP ASSOCIATE connection
            val connection = createMockUdpAssociateConnection(key)
            val initialTimestamp = connection.lastActivityAt
            connectionTable.addUdpAssociateConnection(connection)
            
            // Wait a bit to ensure timestamp changes
            Thread.sleep(10)
            
            // Act: Update statistics
            connectionTable.updateUdpAssociateStats(
                key = key,
                bytesSent = packetSize.toLong()
            )
            
            // Assert: Verify lastActivityAt was updated
            val updatedConnection = connectionTable.getUdpAssociateConnection(key)
            assertNotNull("Connection should exist", updatedConnection)
            assertTrue(
                "lastActivityAt should be updated",
                updatedConnection!!.lastActivityAt > initialTimestamp
            )
            
            // Cleanup
            connectionTable.removeUdpAssociateConnection(key)
        }
    }
    
    /**
     * Property 8: Statistics Accuracy - Zero Bytes
     * 
     * For any UDP ASSOCIATE connection, updating with zero bytes should
     * not change the counters but should still update lastActivityAt.
     * 
     * Validates: Requirements 9.1, 9.4
     */
    @Test
    fun `updating with zero bytes should not change counters`() = runTest {
        // Feature: socks5-udp-associate, Property 8: Statistics Accuracy
        // Validates: Requirements 9.1, 9.4
        
        checkAll(
            iterations = 100,
            Arb.connectionKey()
        ) { key ->
            // Arrange: Create a mock UDP ASSOCIATE connection
            val connection = createMockUdpAssociateConnection(key)
            connectionTable.addUdpAssociateConnection(connection)
            
            // Set initial values
            connectionTable.updateUdpAssociateStats(key, bytesSent = 1000, bytesReceived = 2000)
            val initialConnection = connectionTable.getUdpAssociateConnection(key)!!
            val initialBytesSent = initialConnection.bytesSent
            val initialBytesReceived = initialConnection.bytesReceived
            val initialTimestamp = initialConnection.lastActivityAt
            
            // Wait a bit to ensure timestamp changes
            Thread.sleep(10)
            
            // Act: Update with zero bytes
            connectionTable.updateUdpAssociateStats(key, bytesSent = 0, bytesReceived = 0)
            
            // Assert: Verify counters unchanged but timestamp updated
            val updatedConnection = connectionTable.getUdpAssociateConnection(key)
            assertNotNull("Connection should exist", updatedConnection)
            assertEquals(
                "bytesSent should be unchanged",
                initialBytesSent,
                updatedConnection!!.bytesSent
            )
            assertEquals(
                "bytesReceived should be unchanged",
                initialBytesReceived,
                updatedConnection.bytesReceived
            )
            assertTrue(
                "lastActivityAt should be updated",
                updatedConnection.lastActivityAt > initialTimestamp
            )
            
            // Cleanup
            connectionTable.removeUdpAssociateConnection(key)
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Creates a mock UDP ASSOCIATE connection for testing.
     */
    private fun createMockUdpAssociateConnection(key: ConnectionKey): UdpAssociateConnection {
        // Create mock sockets (they won't actually be used in these tests)
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
            bytesSent = 0,
            bytesReceived = 0,
            readerJob = kotlinx.coroutines.Job()
        )
    }
    
    // ========== Custom Generators ==========
    
    companion object {
        /**
         * Generator for ConnectionKey instances.
         */
        fun Arb.Companion.connectionKey(): Arb<ConnectionKey> = arbitrary {
            ConnectionKey(
                protocol = Protocol.UDP,
                sourceIp = ipv4Address().bind(),
                sourcePort = validPort().bind(),
                destIp = ipv4Address().bind(),
                destPort = validPort().bind()
            )
        }
        
        /**
         * Generator for valid IPv4 addresses.
         */
        fun Arb.Companion.ipv4Address(): Arb<String> = arbitrary {
            val octet1 = Arb.int(1..255).bind()
            val octet2 = Arb.int(0..255).bind()
            val octet3 = Arb.int(0..255).bind()
            val octet4 = Arb.int(1..255).bind()
            "$octet1.$octet2.$octet3.$octet4"
        }
        
        /**
         * Generator for valid port numbers (1-65535).
         */
        fun Arb.Companion.validPort(): Arb<Int> = Arb.int(1..65535)
    }
}
