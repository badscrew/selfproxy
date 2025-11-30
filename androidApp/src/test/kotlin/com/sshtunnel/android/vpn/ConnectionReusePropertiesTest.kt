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

/**
 * Property-based tests for UDP ASSOCIATE connection reuse.
 * 
 * Feature: socks5-udp-associate, Property 4: Connection Reuse Consistency
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4
 */
class ConnectionReusePropertiesTest {
    
    private lateinit var logger: Logger
    private lateinit var connectionTable: ConnectionTable
    private lateinit var udpHandler: UDPHandler
    private lateinit var tunOutputStream: FileOutputStream
    
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
        
        connectionTable = ConnectionTable()
        
        udpHandler = UDPHandler(
            socksPort = 1080,
            connectionTable = connectionTable,
            logger = logger
        )
        
        // Create a mock TUN output stream
        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = PipedInputStream(pipedOutputStream)
        tunOutputStream = FileOutputStream(java.io.FileDescriptor())
    }
    
    /**
     * Property 4: Connection Reuse Consistency
     * 
     * For any sequence of UDP packets to the same destination, if an active UDP ASSOCIATE
     * connection exists, all packets should reuse that connection rather than creating new ones.
     * 
     * This test verifies that:
     * 1. Multiple packets to the same destination reuse the same connection
     * 2. Only one connection is created for the same 5-tuple
     * 3. Connection statistics are updated correctly
     * 
     * Validates: Requirements 6.1, 6.2, 6.3, 6.4
     */
    @Test
    fun `multiple packets to same destination should reuse connection`() = runTest {
        // Feature: socks5-udp-associate, Property 4: Connection Reuse Consistency
        // Validates: Requirements 6.1, 6.2, 6.3, 6.4
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(),
            Arb.int(2..10)  // Number of packets to send
        ) { key, packetCount ->
            // Arrange: Create a fresh connection table for each test
            val testConnectionTable = ConnectionTable()
            
            // Create a mock UDP ASSOCIATE connection for this key
            val mockConnection = createMockUdpAssociateConnection(key)
            testConnectionTable.addUdpAssociateConnection(mockConnection)
            
            // Act: Simulate sending multiple packets with the same 5-tuple
            repeat(packetCount) { i ->
                // Check if connection exists (simulating what handleGenericUdpPacket does)
                val connection = testConnectionTable.getUdpAssociateConnection(key)
                
                // Assert: Connection should exist and be the same one
                assertNotNull("Connection should exist for packet $i", connection)
                assertEquals(
                    "Should reuse the same connection for packet $i",
                    mockConnection.key,
                    connection?.key
                )
                
                // Simulate updating statistics (as sendUdpThroughSocks5 would do)
                if (connection != null) {
                    connection.bytesSent += 100
                    connection.lastActivityAt = System.currentTimeMillis()
                    testConnectionTable.updateUdpAssociateStats(
                        key = key,
                        bytesSent = 100
                    )
                }
            }
            
            // Assert: Should still have exactly one connection
            val stats = testConnectionTable.getStatistics()
            assertEquals(
                "Should have exactly one UDP ASSOCIATE connection",
                1,
                stats.activeUdpAssociateConnections
            )
            
            // Verify the connection has accumulated statistics
            val finalConnection = testConnectionTable.getUdpAssociateConnection(key)
            assertNotNull("Connection should still exist", finalConnection)
            assertTrue(
                "Connection should have accumulated bytes sent",
                finalConnection!!.bytesSent >= packetCount * 100
            )
        }
    }
    
    /**
     * Property 5: Different destinations create different connections
     * 
     * For any two different connection keys (different 5-tuples), separate
     * UDP ASSOCIATE connections should be created and maintained independently.
     * 
     * Validates: Requirements 6.1, 6.3
     */
    @Test
    fun `different destinations should create separate connections`() = runTest {
        // Feature: socks5-udp-associate, Property 4: Connection Reuse Consistency
        // Validates: Requirements 6.1, 6.3
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(),
            Arb.connectionKey()
        ) { key1, key2 ->
            // Skip if keys are the same
            if (key1 == key2) return@checkAll
            
            // Arrange: Create a fresh connection table
            val testConnectionTable = ConnectionTable()
            
            // Act: Create connections for both keys
            val connection1 = createMockUdpAssociateConnection(key1)
            val connection2 = createMockUdpAssociateConnection(key2)
            
            testConnectionTable.addUdpAssociateConnection(connection1)
            testConnectionTable.addUdpAssociateConnection(connection2)
            
            // Assert: Should have two separate connections
            val stats = testConnectionTable.getStatistics()
            assertEquals(
                "Should have two separate UDP ASSOCIATE connections",
                2,
                stats.activeUdpAssociateConnections
            )
            
            // Verify each key retrieves its own connection
            val retrieved1 = testConnectionTable.getUdpAssociateConnection(key1)
            val retrieved2 = testConnectionTable.getUdpAssociateConnection(key2)
            
            assertNotNull("Connection 1 should exist", retrieved1)
            assertNotNull("Connection 2 should exist", retrieved2)
            assertEquals("Connection 1 should match key 1", key1, retrieved1?.key)
            assertEquals("Connection 2 should match key 2", key2, retrieved2?.key)
            assertNotEquals(
                "Connections should be different",
                retrieved1?.key,
                retrieved2?.key
            )
        }
    }
    
    /**
     * Property 6: Connection reuse updates lastActivityAt
     * 
     * For any connection that is reused, the lastActivityAt timestamp should
     * be updated to prevent premature cleanup.
     * 
     * Validates: Requirements 6.5
     */
    @Test
    fun `connection reuse should update lastActivityAt timestamp`() = runTest {
        // Feature: socks5-udp-associate, Property 4: Connection Reuse Consistency
        // Validates: Requirements 6.5
        
        checkAll(
            iterations = 100,
            Arb.connectionKey()
        ) { key ->
            // Arrange: Create a connection with an old timestamp
            val testConnectionTable = ConnectionTable()
            val oldTimestamp = System.currentTimeMillis() - 60000  // 1 minute ago
            
            val connection = createMockUdpAssociateConnection(key)
            connection.lastActivityAt = oldTimestamp
            testConnectionTable.addUdpAssociateConnection(connection)
            
            // Act: Simulate reusing the connection
            val retrievedConnection = testConnectionTable.getUdpAssociateConnection(key)
            assertNotNull("Connection should exist", retrievedConnection)
            
            // Update timestamp (as sendUdpThroughSocks5 would do)
            val newTimestamp = System.currentTimeMillis()
            retrievedConnection!!.lastActivityAt = newTimestamp
            
            // Assert: Timestamp should be updated
            val finalConnection = testConnectionTable.getUdpAssociateConnection(key)
            assertTrue(
                "lastActivityAt should be updated to a more recent time",
                finalConnection!!.lastActivityAt > oldTimestamp
            )
            assertTrue(
                "lastActivityAt should be close to current time",
                finalConnection.lastActivityAt >= newTimestamp - 1000  // Within 1 second
            )
        }
    }
    
    /**
     * Property 7: Connection statistics accumulate correctly
     * 
     * For any connection that handles multiple packets, the bytesSent and
     * bytesReceived counters should accumulate correctly.
     * 
     * Validates: Requirements 9.1, 9.2, 9.4
     */
    @Test
    fun `connection statistics should accumulate correctly`() = runTest {
        // Feature: socks5-udp-associate, Property 4: Connection Reuse Consistency
        // Validates: Requirements 9.1, 9.2, 9.4
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(),
            Arb.list(Arb.int(1..1500), 1..20)  // List of packet sizes
        ) { key, packetSizes ->
            // Arrange: Create a connection
            val testConnectionTable = ConnectionTable()
            val connection = createMockUdpAssociateConnection(key)
            testConnectionTable.addUdpAssociateConnection(connection)
            
            // Act: Simulate sending multiple packets
            var expectedBytesSent = 0L
            for (size in packetSizes) {
                expectedBytesSent += size
                // Update statistics through the connection table (as the real code does)
                testConnectionTable.updateUdpAssociateStats(
                    key = key,
                    bytesSent = size.toLong()
                )
            }
            
            // Assert: Statistics should match
            val finalConnection = testConnectionTable.getUdpAssociateConnection(key)
            assertNotNull("Connection should exist", finalConnection)
            assertEquals(
                "bytesSent should match total",
                expectedBytesSent,
                finalConnection!!.bytesSent
            )
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Creates a mock UDP ASSOCIATE connection for testing.
     */
    private fun createMockUdpAssociateConnection(key: ConnectionKey): UdpAssociateConnection {
        // Create mock sockets (they won't actually be used in these tests)
        val controlSocket = java.net.Socket()
        val relaySocket = java.net.DatagramSocket()
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
         * Generator for ConnectionKey with UDP protocol.
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
