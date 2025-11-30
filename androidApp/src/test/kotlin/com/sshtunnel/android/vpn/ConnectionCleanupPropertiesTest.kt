package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.DatagramSocket
import java.net.Socket

/**
 * Property-based tests for UDP ASSOCIATE connection cleanup.
 * 
 * Feature: socks5-udp-associate, Property 6: Connection Cleanup Completeness
 * Validates: Requirements 2.2, 2.3, 5.4, 7.4
 */
class ConnectionCleanupPropertiesTest {
    
    private lateinit var logger: Logger
    
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
    }
    
    /**
     * Property 6: Connection Cleanup Completeness
     * 
     * For any set of connections with varying lastActivityAt timestamps, cleanup should:
     * 1. Remove all connections that exceed the idle timeout
     * 2. Preserve all connections within the idle timeout
     * 3. Properly close sockets and cancel jobs for removed connections
     * 
     * This test verifies that idle connections are removed while active connections
     * are preserved, ensuring proper resource management.
     * 
     * Validates: Requirements 2.2, 2.3, 5.4, 7.4
     */
    @Test
    fun `cleanup should remove idle connections and preserve active ones`() = runTest {
        // Feature: socks5-udp-associate, Property 6: Connection Cleanup Completeness
        // Validates: Requirements 2.2, 2.3, 5.4, 7.4
        
        checkAll(
            iterations = 100,
            Arb.int(1..5),  // Number of idle connections
            Arb.int(1..5)   // Number of active connections
        ) { idleCount, activeCount ->
            // Arrange: Create a fresh connection table
            val testConnectionTable = ConnectionTable(logger)
            val now = System.currentTimeMillis()
            val idleTimeoutMs = 120_000L  // 2 minutes
            
            // Create idle connections (older than timeout)
            val idleKeys = mutableListOf<ConnectionKey>()
            repeat(idleCount) { i ->
                val key = ConnectionKey(
                    protocol = Protocol.UDP,
                    sourceIp = "10.0.0.2",
                    sourcePort = 50000 + i,
                    destIp = "1.1.1.1",
                    destPort = 3478
                )
                idleKeys.add(key)
                
                // Create connection with old timestamp (exceeds timeout)
                val oldTimestamp = now - idleTimeoutMs - 30_000  // 30 seconds past timeout
                val connection = createMockUdpAssociateConnection(key, oldTimestamp)
                testConnectionTable.addUdpAssociateConnection(connection)
            }
            
            // Create active connections (within timeout)
            val activeKeys = mutableListOf<ConnectionKey>()
            repeat(activeCount) { i ->
                val key = ConnectionKey(
                    protocol = Protocol.UDP,
                    sourceIp = "10.0.0.2",
                    sourcePort = 60000 + i,
                    destIp = "1.1.1.1",
                    destPort = 3479
                )
                activeKeys.add(key)
                
                // Create connection with recent timestamp (within timeout)
                val recentTimestamp = now - 60_000  // 1 minute ago (within 2 minute timeout)
                val connection = createMockUdpAssociateConnection(key, recentTimestamp)
                testConnectionTable.addUdpAssociateConnection(connection)
            }
            
            // Verify initial state
            val statsBefore = testConnectionTable.getStatistics()
            assertEquals(
                "Should have all connections before cleanup",
                idleCount + activeCount,
                statsBefore.activeUdpAssociateConnections
            )
            
            // Act: Run cleanup
            testConnectionTable.cleanupIdleConnections(idleTimeoutMs = idleTimeoutMs)
            
            // Assert: Idle connections should be removed
            for (key in idleKeys) {
                val connection = testConnectionTable.getUdpAssociateConnection(key)
                assertNull(
                    "Idle connection should be removed: ${key.sourcePort}",
                    connection
                )
            }
            
            // Assert: Active connections should be preserved
            for (key in activeKeys) {
                val connection = testConnectionTable.getUdpAssociateConnection(key)
                assertNotNull(
                    "Active connection should be preserved: ${key.sourcePort}",
                    connection
                )
            }
            
            // Assert: Statistics should reflect cleanup
            val statsAfter = testConnectionTable.getStatistics()
            assertEquals(
                "Should have only active connections after cleanup",
                activeCount,
                statsAfter.activeUdpAssociateConnections
            )
        }
    }
    
    /**
     * Property 7: Cleanup with varying timeout values
     * 
     * For any idle timeout value, cleanup should correctly identify and remove
     * connections that exceed that specific timeout.
     * 
     * Validates: Requirements 2.2, 2.3, 7.4
     */
    @Test
    fun `cleanup should respect custom timeout values`() = runTest {
        // Feature: socks5-udp-associate, Property 6: Connection Cleanup Completeness
        // Validates: Requirements 2.2, 2.3, 7.4
        
        checkAll(
            iterations = 100,
            Arb.long(30_000L..300_000L)  // Timeout between 30 seconds and 5 minutes
        ) { customTimeout ->
            // Arrange: Create a fresh connection table
            val testConnectionTable = ConnectionTable(logger)
            val now = System.currentTimeMillis()
            
            // Create connection just past the timeout
            val justPastKey = ConnectionKey(
                protocol = Protocol.UDP,
                sourceIp = "10.0.0.2",
                sourcePort = 50001,
                destIp = "1.1.1.1",
                destPort = 3478
            )
            val justPastTimestamp = now - customTimeout - 5_000  // 5 seconds past timeout
            val justPastConnection = createMockUdpAssociateConnection(justPastKey, justPastTimestamp)
            testConnectionTable.addUdpAssociateConnection(justPastConnection)
            
            // Create connection just within the timeout
            val justWithinKey = ConnectionKey(
                protocol = Protocol.UDP,
                sourceIp = "10.0.0.2",
                sourcePort = 50002,
                destIp = "1.1.1.1",
                destPort = 3479
            )
            val justWithinTimestamp = now - customTimeout + 5_000  // 5 seconds before timeout
            val justWithinConnection = createMockUdpAssociateConnection(justWithinKey, justWithinTimestamp)
            testConnectionTable.addUdpAssociateConnection(justWithinConnection)
            
            // Act: Run cleanup with custom timeout
            testConnectionTable.cleanupIdleConnections(idleTimeoutMs = customTimeout)
            
            // Assert: Connection past timeout should be removed
            val pastConnection = testConnectionTable.getUdpAssociateConnection(justPastKey)
            assertNull(
                "Connection past timeout should be removed",
                pastConnection
            )
            
            // Assert: Connection within timeout should be preserved
            val withinConnection = testConnectionTable.getUdpAssociateConnection(justWithinKey)
            assertNotNull(
                "Connection within timeout should be preserved",
                withinConnection
            )
        }
    }
    
    /**
     * Property 8: Cleanup handles mixed connection types
     * 
     * For any combination of TCP, UDP, and UDP ASSOCIATE connections,
     * cleanup should correctly handle all types independently.
     * 
     * Validates: Requirements 2.2, 2.3, 7.4
     */
    @Test
    fun `cleanup should handle mixed TCP UDP and UDP ASSOCIATE connections`() = runTest {
        // Feature: socks5-udp-associate, Property 6: Connection Cleanup Completeness
        // Validates: Requirements 2.2, 2.3, 7.4
        
        checkAll(
            iterations = 100,
            Arb.int(0..3),  // Number of idle TCP connections
            Arb.int(0..3),  // Number of idle UDP connections
            Arb.int(0..3)   // Number of idle UDP ASSOCIATE connections
        ) { idleTcpCount, idleUdpCount, idleUdpAssociateCount ->
            // Arrange: Create a fresh connection table
            val testConnectionTable = ConnectionTable(logger)
            val now = System.currentTimeMillis()
            val idleTimeoutMs = 120_000L
            val oldTimestamp = now - idleTimeoutMs - 30_000
            
            // Create idle TCP connections
            repeat(idleTcpCount) { i ->
                val key = ConnectionKey(
                    protocol = Protocol.TCP,
                    sourceIp = "10.0.0.2",
                    sourcePort = 40000 + i,
                    destIp = "1.1.1.1",
                    destPort = 80
                )
                val connection = createMockTcpConnection(key, oldTimestamp)
                testConnectionTable.addTcpConnection(connection)
            }
            
            // Create idle UDP connections
            repeat(idleUdpCount) { i ->
                val key = ConnectionKey(
                    protocol = Protocol.UDP,
                    sourceIp = "10.0.0.2",
                    sourcePort = 50000 + i,
                    destIp = "8.8.8.8",
                    destPort = 53
                )
                val connection = createMockUdpConnection(key, oldTimestamp)
                testConnectionTable.addUdpConnection(connection)
            }
            
            // Create idle UDP ASSOCIATE connections
            repeat(idleUdpAssociateCount) { i ->
                val key = ConnectionKey(
                    protocol = Protocol.UDP,
                    sourceIp = "10.0.0.2",
                    sourcePort = 60000 + i,
                    destIp = "1.1.1.1",
                    destPort = 3478
                )
                val connection = createMockUdpAssociateConnection(key, oldTimestamp)
                testConnectionTable.addUdpAssociateConnection(connection)
            }
            
            // Verify initial state
            val statsBefore = testConnectionTable.getStatistics()
            assertEquals(idleTcpCount, statsBefore.activeTcpConnections)
            assertEquals(idleUdpCount, statsBefore.activeUdpConnections)
            assertEquals(idleUdpAssociateCount, statsBefore.activeUdpAssociateConnections)
            
            // Act: Run cleanup
            testConnectionTable.cleanupIdleConnections(idleTimeoutMs = idleTimeoutMs)
            
            // Assert: All idle connections should be removed
            val statsAfter = testConnectionTable.getStatistics()
            assertEquals(
                "All idle TCP connections should be removed",
                0,
                statsAfter.activeTcpConnections
            )
            assertEquals(
                "All idle UDP connections should be removed",
                0,
                statsAfter.activeUdpConnections
            )
            assertEquals(
                "All idle UDP ASSOCIATE connections should be removed",
                0,
                statsAfter.activeUdpAssociateConnections
            )
        }
    }
    
    /**
     * Property 9: Cleanup is idempotent
     * 
     * Running cleanup multiple times should not cause errors or unexpected behavior.
     * After the first cleanup removes idle connections, subsequent cleanups should
     * have no effect.
     * 
     * Validates: Requirements 2.2, 2.3, 7.4
     */
    @Test
    fun `cleanup should be idempotent`() = runTest {
        // Feature: socks5-udp-associate, Property 6: Connection Cleanup Completeness
        // Validates: Requirements 2.2, 2.3, 7.4
        
        checkAll(
            iterations = 100,
            Arb.int(1..5),  // Number of idle connections
            Arb.int(2..5)   // Number of cleanup runs
        ) { connectionCount, cleanupRuns ->
            // Arrange: Create a fresh connection table with idle connections
            val testConnectionTable = ConnectionTable(logger)
            val now = System.currentTimeMillis()
            val idleTimeoutMs = 120_000L
            val oldTimestamp = now - idleTimeoutMs - 30_000
            
            repeat(connectionCount) { i ->
                val key = ConnectionKey(
                    protocol = Protocol.UDP,
                    sourceIp = "10.0.0.2",
                    sourcePort = 50000 + i,
                    destIp = "1.1.1.1",
                    destPort = 3478
                )
                val connection = createMockUdpAssociateConnection(key, oldTimestamp)
                testConnectionTable.addUdpAssociateConnection(connection)
            }
            
            // Act: Run cleanup multiple times
            repeat(cleanupRuns) {
                testConnectionTable.cleanupIdleConnections(idleTimeoutMs = idleTimeoutMs)
            }
            
            // Assert: All connections should be removed (no errors from multiple cleanups)
            val stats = testConnectionTable.getStatistics()
            assertEquals(
                "All connections should be removed after cleanup",
                0,
                stats.activeUdpAssociateConnections
            )
        }
    }
    
    /**
     * Property 10: Empty table cleanup is safe
     * 
     * Running cleanup on an empty connection table should not cause errors.
     * 
     * Validates: Requirements 2.2, 2.3, 7.4
     */
    @Test
    fun `cleanup on empty table should not cause errors`() = runTest {
        // Feature: socks5-udp-associate, Property 6: Connection Cleanup Completeness
        // Validates: Requirements 2.2, 2.3, 7.4
        
        checkAll(
            iterations = 100,
            Arb.long(30_000L..300_000L)  // Various timeout values
        ) { timeout ->
            // Arrange: Create an empty connection table
            val testConnectionTable = ConnectionTable(logger)
            
            // Act: Run cleanup on empty table (should not throw)
            testConnectionTable.cleanupIdleConnections(idleTimeoutMs = timeout)
            
            // Assert: Table should still be empty
            val stats = testConnectionTable.getStatistics()
            assertEquals(0, stats.activeTcpConnections)
            assertEquals(0, stats.activeUdpConnections)
            assertEquals(0, stats.activeUdpAssociateConnections)
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Creates a mock UDP ASSOCIATE connection for testing.
     */
    private fun createMockUdpAssociateConnection(
        key: ConnectionKey,
        lastActivityAt: Long
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
            lastActivityAt = lastActivityAt,
            bytesSent = 0,
            bytesReceived = 0,
            readerJob = Job()
        )
    }
    
    /**
     * Creates a mock TCP connection for testing.
     */
    private fun createMockTcpConnection(
        key: ConnectionKey,
        lastActivityAt: Long
    ): TcpConnection {
        return TcpConnection(
            key = key,
            socksSocket = Socket(),
            state = TcpState.ESTABLISHED,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = lastActivityAt,
            bytesSent = 0,
            bytesReceived = 0,
            readerJob = Job()
        )
    }
    
    /**
     * Creates a mock UDP connection for testing.
     */
    private fun createMockUdpConnection(
        key: ConnectionKey,
        lastActivityAt: Long
    ): UdpConnection {
        return UdpConnection(
            key = key,
            socksSocket = null,
            createdAt = System.currentTimeMillis(),
            lastActivityAt = lastActivityAt,
            bytesSent = 0,
            bytesReceived = 0
        )
    }
}
