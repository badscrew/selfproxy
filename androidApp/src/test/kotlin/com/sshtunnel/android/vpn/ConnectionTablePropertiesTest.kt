package com.sshtunnel.android.vpn

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.Socket

/**
 * Property-based tests for ConnectionTable
 * 
 * Feature: pure-kotlin-packet-router, Property: TCP connection establishment always creates connection table entry
 * Validates: Requirements 9.1
 */
class ConnectionTablePropertiesTest {
    
    @Test
    fun `TCP connection establishment always creates connection table entry`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: TCP connection establishment always creates connection table entry
        // Validates: Requirements 9.1
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(Protocol.TCP)
        ) { key ->
            // Arrange
            val connectionTable = ConnectionTable()
            val connection = createMockTcpConnection(key)
            
            // Act
            connectionTable.addTcpConnection(connection)
            val retrieved = connectionTable.getTcpConnection(key)
            
            // Assert
            retrieved shouldNotBe null
            retrieved!!.key shouldBe key
            retrieved.key.protocol shouldBe Protocol.TCP
            retrieved.key.sourceIp shouldBe key.sourceIp
            retrieved.key.sourcePort shouldBe key.sourcePort
            retrieved.key.destIp shouldBe key.destIp
            retrieved.key.destPort shouldBe key.destPort
        }
    }
    
    @Test
    fun `UDP connection establishment always creates connection table entry`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: UDP connection establishment always creates connection table entry
        // Validates: Requirements 9.1
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(Protocol.UDP)
        ) { key ->
            // Arrange
            val connectionTable = ConnectionTable()
            val connection = createMockUdpConnection(key)
            
            // Act
            connectionTable.addUdpConnection(connection)
            val retrieved = connectionTable.getUdpConnection(key)
            
            // Assert
            retrieved shouldNotBe null
            retrieved!!.key shouldBe key
            retrieved.key.protocol shouldBe Protocol.UDP
        }
    }
    
    @Test
    fun `removing connection always removes it from table`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: Removing connection always removes it from table
        // Validates: Requirements 9.2
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(Protocol.TCP)
        ) { key ->
            // Arrange
            val connectionTable = ConnectionTable()
            val connection = createMockTcpConnection(key)
            connectionTable.addTcpConnection(connection)
            
            // Act
            val removed = connectionTable.removeTcpConnection(key)
            val retrieved = connectionTable.getTcpConnection(key)
            
            // Assert
            removed shouldNotBe null
            removed!!.key shouldBe key
            retrieved shouldBe null
        }
    }
    
    @Test
    fun `statistics always reflect current connection count`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: Statistics always reflect current connection count
        // Validates: Requirements 13.1, 13.2, 13.3
        
        checkAll(
            iterations = 100,
            Arb.int(1..50) // Number of connections to add
        ) { numConnections ->
            // Arrange
            val connectionTable = ConnectionTable()
            
            // Act - Add connections
            repeat(numConnections) { i ->
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
            
            val stats = connectionTable.getStatistics()
            
            // Assert
            stats.activeTcpConnections shouldBe numConnections
            stats.totalTcpConnections shouldBe numConnections
        }
    }
    
    @Test
    fun `statistics always sum bytes correctly`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: Statistics always sum bytes correctly
        // Validates: Requirements 13.4, 13.5
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(Protocol.TCP),
            Arb.long(0L..1_000_000L), // bytes sent
            Arb.long(0L..1_000_000L)  // bytes received
        ) { key, bytesSent, bytesReceived ->
            // Arrange
            val connectionTable = ConnectionTable()
            val connection = createMockTcpConnection(
                key,
                bytesSent = bytesSent,
                bytesReceived = bytesReceived
            )
            
            // Act
            connectionTable.addTcpConnection(connection)
            val stats = connectionTable.getStatistics()
            
            // Assert
            stats.totalBytesSent shouldBe bytesSent
            stats.totalBytesReceived shouldBe bytesReceived
        }
    }
    
    @Test
    fun `cleanup always removes connections older than timeout`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: Cleanup always removes connections older than timeout
        // Validates: Requirements 9.3, 9.5
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(Protocol.TCP),
            Arb.long(1_000L..300_000L) // timeout in ms (1 second to 5 minutes)
        ) { key, timeoutMs ->
            // Arrange
            val connectionTable = ConnectionTable()
            val now = System.currentTimeMillis()
            val oldTime = now - timeoutMs - 1000L // Older than timeout
            
            val connection = createMockTcpConnection(
                key,
                lastActivityAt = oldTime
            )
            connectionTable.addTcpConnection(connection)
            
            // Act
            connectionTable.cleanupIdleConnections(idleTimeoutMs = timeoutMs)
            val retrieved = connectionTable.getTcpConnection(key)
            
            // Assert
            retrieved shouldBe null // Connection should be removed
        }
    }
    
    @Test
    fun `cleanup never removes connections within timeout`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: Cleanup never removes connections within timeout
        // Validates: Requirements 9.3, 9.5
        
        checkAll(
            iterations = 100,
            Arb.connectionKey(Protocol.TCP),
            Arb.long(60_000L..300_000L) // timeout in ms (1 to 5 minutes)
        ) { key, timeoutMs ->
            // Arrange
            val connectionTable = ConnectionTable()
            val now = System.currentTimeMillis()
            val recentTime = now - (timeoutMs / 2) // Within timeout (half the timeout)
            
            val connection = createMockTcpConnection(
                key,
                lastActivityAt = recentTime
            )
            connectionTable.addTcpConnection(connection)
            
            // Act
            connectionTable.cleanupIdleConnections(idleTimeoutMs = timeoutMs)
            val retrieved = connectionTable.getTcpConnection(key)
            
            // Assert
            retrieved shouldNotBe null // Connection should still exist
        }
    }
    
    // Helper functions and generators
    
    private fun createMockTcpConnection(
        key: ConnectionKey,
        state: TcpState = TcpState.ESTABLISHED,
        lastActivityAt: Long = System.currentTimeMillis(),
        bytesSent: Long = 0,
        bytesReceived: Long = 0
    ): TcpConnection {
        return TcpConnection(
            key = key,
            socksSocket = Socket(),
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
}

/**
 * Custom Arb generator for ConnectionKey
 */
fun Arb.Companion.connectionKey(protocol: Protocol): Arb<ConnectionKey> = arbitrary {
    ConnectionKey(
        protocol = protocol,
        sourceIp = ipAddress().bind(),
        sourcePort = port().bind(),
        destIp = ipAddress().bind(),
        destPort = port().bind()
    )
}

/**
 * Custom Arb generator for IP addresses
 */
fun Arb.Companion.ipAddress(): Arb<String> = arbitrary {
    val octets = List(4) { Arb.int(0..255).bind() }
    octets.joinToString(".")
}

/**
 * Custom Arb generator for port numbers
 */
fun Arb.Companion.port(): Arb<Int> = Arb.int(1024..65535)
