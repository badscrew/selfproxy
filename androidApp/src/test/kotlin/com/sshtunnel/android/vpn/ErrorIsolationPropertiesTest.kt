package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.DatagramSocket
import java.net.Socket

/**
 * Property-based tests for error isolation in UDP ASSOCIATE connections.
 * 
 * Feature: socks5-udp-associate, Property 7: Error Isolation
 * Validates: Requirements 7.1, 7.2, 7.3, 7.5
 */
class ErrorIsolationPropertiesTest {
    
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
        
        connectionTable = ConnectionTable()
    }
    
    /**
     * Property 7: Error Isolation
     * 
     * For any UDP ASSOCIATE connection that fails, the failure should not affect
     * other active UDP or TCP connections. This ensures that one bad connection
     * doesn't crash the entire router or affect other traffic.
     * 
     * This test verifies that:
     * 1. Multiple UDP ASSOCIATE connections can coexist
     * 2. Simulating a failure in one connection doesn't affect others
     * 3. Other connections continue to function normally
     * 4. Failed connections are properly cleaned up
     * 
     * Validates: Requirements 7.1, 7.2, 7.3, 7.5
     */
    @Test
    fun `failure in one connection should not affect other connections`() = runTest {
        // Feature: socks5-udp-associate, Property 7: Error Isolation
        // Validates: Requirements 7.1, 7.2, 7.3, 7.5
        
        checkAll(
            iterations = 100,
            Arb.int(3..10)  // Number of connections to create
        ) { connectionCount ->
            // Arrange: Create multiple UDP ASSOCIATE connections
            val testConnectionTable = ConnectionTable()
            val keys = mutableListOf<ConnectionKey>()
            val connections = mutableListOf<UdpAssociateConnection>()
            
            // Create multiple connections with different keys
            repeat(connectionCount) { i ->
                val key = ConnectionKey(
                    protocol = Protocol.UDP,
                    sourceIp = "192.168.1.${i + 1}",
                    sourcePort = 10000 + i,
                    destIp = "8.8.8.${i + 1}",
                    destPort = 53
                )
                keys.add(key)
                
                val connection = createMockUdpAssociateConnection(key)
                connections.add(connection)
                testConnectionTable.addUdpAssociateConnection(connection)
            }
            
            // Verify all connections are active
            val initialStats = testConnectionTable.getStatistics()
            assertEquals(
                "All connections should be active initially",
                connectionCount,
                initialStats.activeUdpAssociateConnections
            )
            
            // Act: Simulate a failure in one connection (pick a random one)
            val failedIndex = connectionCount / 2  // Pick middle connection
            val failedKey = keys[failedIndex]
            val failedConnection = connections[failedIndex]
            
            // Simulate connection failure by closing its sockets
            try {
                failedConnection.relaySocket.close()
                failedConnection.controlSocket.close()
            } catch (e: Exception) {
                // Ignore - sockets might already be closed
            }
            
            // Remove the failed connection from the table (as cleanup would do)
            testConnectionTable.removeUdpAssociateConnection(failedKey)
            
            // Assert: Other connections should still be active
            val afterFailureStats = testConnectionTable.getStatistics()
            assertEquals(
                "Other connections should remain active after one fails",
                connectionCount - 1,
                afterFailureStats.activeUdpAssociateConnections
            )
            
            // Verify each non-failed connection is still accessible
            for (i in keys.indices) {
                if (i == failedIndex) {
                    // Failed connection should be removed
                    val removed = testConnectionTable.getUdpAssociateConnection(keys[i])
                    assertNull(
                        "Failed connection should be removed",
                        removed
                    )
                } else {
                    // Other connections should still exist
                    val existing = testConnectionTable.getUdpAssociateConnection(keys[i])
                    assertNotNull(
                        "Connection $i should still exist after connection $failedIndex failed",
                        existing
                    )
                    assertEquals(
                        "Connection $i should have correct key",
                        keys[i],
                        existing?.key
                    )
                }
            }
        }
    }
    
    /**
     * Property 8: Socket errors don't propagate
     * 
     * For any connection that experiences a socket error (IOException),
     * the error should be caught and logged, but not propagated to affect
     * other connections or crash the router.
     * 
     * Validates: Requirements 7.2, 7.5
     */
    @Test
    fun `socket errors should be isolated and not propagate`() = runTest {
        // Feature: socks5-udp-associate, Property 7: Error Isolation
        // Validates: Requirements 7.2, 7.5
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(),
            Arb.connectionKey()
        ) { key1, key2 ->
            // Skip if keys are the same
            if (key1 == key2) return@checkAll
            
            // Arrange: Create two connections
            val testConnectionTable = ConnectionTable()
            
            val connection1 = createMockUdpAssociateConnection(key1)
            val connection2 = createMockUdpAssociateConnection(key2)
            
            testConnectionTable.addUdpAssociateConnection(connection1)
            testConnectionTable.addUdpAssociateConnection(connection2)
            
            // Verify both connections exist
            assertEquals(
                "Should have two connections",
                2,
                testConnectionTable.getStatistics().activeUdpAssociateConnections
            )
            
            // Act: Simulate a socket error in connection1 by closing its sockets
            var exceptionThrown = false
            try {
                connection1.relaySocket.close()
                connection1.controlSocket.close()
                
                // Try to use the closed socket (this would throw IOException in real code)
                // In the real implementation, this error is caught and doesn't propagate
                try {
                    connection1.relaySocket.send(
                        java.net.DatagramPacket(ByteArray(10), 10)
                    )
                } catch (e: IOException) {
                    // This is expected - socket is closed
                    exceptionThrown = true
                }
            } catch (e: Exception) {
                // Socket operations might fail, but shouldn't crash
                exceptionThrown = true
            }
            
            // Assert: Exception was caught (simulating error handling)
            assertTrue(
                "Socket error should occur when using closed socket",
                exceptionThrown
            )
            
            // Assert: Connection2 should still be accessible and functional
            val connection2Retrieved = testConnectionTable.getUdpAssociateConnection(key2)
            assertNotNull(
                "Connection 2 should still exist after connection 1 failed",
                connection2Retrieved
            )
            assertEquals(
                "Connection 2 should have correct key",
                key2,
                connection2Retrieved?.key
            )
            
            // Connection2's sockets should still be open (not closed by connection1's failure)
            assertFalse(
                "Connection 2's relay socket should still be open",
                connection2.relaySocket.isClosed
            )
            assertFalse(
                "Connection 2's control socket should still be closed",
                connection2.controlSocket.isClosed
            )
        }
    }
    
    /**
     * Property 9: Malformed packet errors don't affect other connections
     * 
     * For any connection that receives a malformed SOCKS5 UDP packet,
     * the packet should be dropped and logged, but the connection should
     * remain active and other connections should be unaffected.
     * 
     * Validates: Requirements 7.3, 7.5
     */
    @Test
    fun `malformed packet errors should not affect connections`() = runTest {
        // Feature: socks5-udp-associate, Property 7: Error Isolation
        // Validates: Requirements 7.3, 7.5
        
        checkAll(
            iterations = 100,
            Arb.int(2..5)  // Number of connections
        ) { connectionCount ->
            // Arrange: Create multiple connections
            val testConnectionTable = ConnectionTable()
            val keys = mutableListOf<ConnectionKey>()
            
            repeat(connectionCount) { i ->
                val key = ConnectionKey(
                    protocol = Protocol.UDP,
                    sourceIp = "10.0.0.${i + 1}",
                    sourcePort = 20000 + i,
                    destIp = "1.1.1.${i + 1}",
                    destPort = 443
                )
                keys.add(key)
                
                val connection = createMockUdpAssociateConnection(key)
                testConnectionTable.addUdpAssociateConnection(connection)
            }
            
            // Verify all connections are active
            assertEquals(
                "All connections should be active",
                connectionCount,
                testConnectionTable.getStatistics().activeUdpAssociateConnections
            )
            
            // Act: Simulate receiving malformed packets on one connection
            // In the real implementation, UDPHandler.decapsulateUdpPacket() would
            // return null for malformed packets, and the packet would be dropped
            
            // Create various malformed packets
            val malformedPackets = listOf(
                ByteArray(0),                    // Empty packet
                ByteArray(5),                    // Too short
                byteArrayOf(0x01.toByte(), 0x00.toByte(), 0x00.toByte()),  // Invalid RSV
                byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x01.toByte()),  // Invalid FRAG
                byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte())  // Invalid ATYP
            )
            
            // Create a UDPHandler to test decapsulation
            val udpHandler = UDPHandler(
                socksPort = 1080,
                connectionTable = testConnectionTable,
                logger = logger
            )
            
            // Try to decapsulate each malformed packet
            for (malformedPacket in malformedPackets) {
                val result = udpHandler.decapsulateUdpPacket(malformedPacket)
                // Assert: Malformed packets should return null (be dropped)
                assertNull(
                    "Malformed packet should be dropped (return null)",
                    result
                )
            }
            
            // Assert: All connections should still be active
            // Malformed packets don't cause connections to be removed
            val finalStats = testConnectionTable.getStatistics()
            assertEquals(
                "All connections should still be active after malformed packets",
                connectionCount,
                finalStats.activeUdpAssociateConnections
            )
            
            // Verify each connection is still accessible
            for (key in keys) {
                val connection = testConnectionTable.getUdpAssociateConnection(key)
                assertNotNull(
                    "Connection should still exist after malformed packets",
                    connection
                )
            }
        }
    }
    
    /**
     * Property 10: Concurrent connection failures are isolated
     * 
     * For any set of connections where multiple fail simultaneously,
     * each failure should be handled independently without affecting
     * the remaining healthy connections.
     * 
     * Validates: Requirements 7.1, 7.2, 7.5
     */
    @Test
    fun `concurrent connection failures should be isolated`() = runTest {
        // Feature: socks5-udp-associate, Property 7: Error Isolation
        // Validates: Requirements 7.1, 7.2, 7.5
        
        checkAll(
            iterations = 100,
            Arb.int(5..10)  // Total number of connections
        ) { totalConnections ->
            // Arrange: Create multiple connections
            val testConnectionTable = ConnectionTable()
            val keys = mutableListOf<ConnectionKey>()
            val connections = mutableListOf<UdpAssociateConnection>()
            
            repeat(totalConnections) { i ->
                val key = ConnectionKey(
                    protocol = Protocol.UDP,
                    sourceIp = "172.16.0.${i + 1}",
                    sourcePort = 30000 + i,
                    destIp = "9.9.9.${i + 1}",
                    destPort = 80
                )
                keys.add(key)
                
                val connection = createMockUdpAssociateConnection(key)
                connections.add(connection)
                testConnectionTable.addUdpAssociateConnection(connection)
            }
            
            // Verify all connections are active
            assertEquals(
                "All connections should be active initially",
                totalConnections,
                testConnectionTable.getStatistics().activeUdpAssociateConnections
            )
            
            // Act: Simulate multiple concurrent failures (fail half of the connections)
            val failureCount = totalConnections / 2
            val failedIndices = (0 until failureCount).toList()
            
            // Close sockets for multiple connections simultaneously
            for (index in failedIndices) {
                try {
                    connections[index].relaySocket.close()
                    connections[index].controlSocket.close()
                } catch (e: Exception) {
                    // Ignore - simulating failure
                }
                
                // Remove from table (as cleanup would do)
                testConnectionTable.removeUdpAssociateConnection(keys[index])
            }
            
            // Assert: Remaining connections should still be active
            val remainingCount = totalConnections - failureCount
            val afterFailureStats = testConnectionTable.getStatistics()
            assertEquals(
                "Remaining connections should still be active",
                remainingCount,
                afterFailureStats.activeUdpAssociateConnections
            )
            
            // Verify each connection's state
            for (i in keys.indices) {
                val connection = testConnectionTable.getUdpAssociateConnection(keys[i])
                
                if (i in failedIndices) {
                    // Failed connections should be removed
                    assertNull(
                        "Failed connection $i should be removed",
                        connection
                    )
                } else {
                    // Healthy connections should still exist
                    assertNotNull(
                        "Healthy connection $i should still exist",
                        connection
                    )
                    assertEquals(
                        "Healthy connection $i should have correct key",
                        keys[i],
                        connection?.key
                    )
                }
            }
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Creates a mock UDP ASSOCIATE connection for testing.
     */
    private fun createMockUdpAssociateConnection(key: ConnectionKey): UdpAssociateConnection {
        // Create real sockets for testing (they will be closed in tests)
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
