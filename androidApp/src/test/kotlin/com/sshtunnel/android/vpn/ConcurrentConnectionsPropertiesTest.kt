package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Property-based tests for concurrent UDP ASSOCIATE connections.
 * 
 * Feature: socks5-udp-associate, Property 10: Concurrent Connection Independence
 * Validates: Requirements 8.4, 10.2
 */
class ConcurrentConnectionsPropertiesTest {
    
    private lateinit var logger: Logger
    private lateinit var connectionTable: ConnectionTable
    private lateinit var udpHandler: UDPHandler
    
    @Before
    fun setup() {
        logger = TestLogger()
        connectionTable = ConnectionTable()
        udpHandler = UDPHandler(
            socksPort = 1080,
            connectionTable = connectionTable,
            logger = logger
        )
    }
    
    /**
     * Property 10: Concurrent Connection Independence
     * 
     * For any two simultaneous UDP ASSOCIATE connections, operations on one connection
     * (send, receive, close) should not interfere with operations on the other connection.
     * 
     * This test verifies that:
     * 1. Multiple connections can be created simultaneously
     * 2. Each connection maintains its own state
     * 3. Operations on one connection don't affect others
     * 4. Encapsulation/decapsulation is thread-safe
     * 
     * Validates: Requirements 8.4, 10.2
     */
    @Test
    fun `concurrent connections should operate independently`() = runTest {
        // Feature: socks5-udp-associate, Property 10: Concurrent Connection Independence
        // Validates: Requirements 8.4, 10.2
        
        checkAll(
            iterations = 50,
            Arb.list(Arb.connectionSpec(), 2..10)  // 2-10 concurrent connections
        ) { connections ->
            // Track results for each connection
            val results = ConcurrentHashMap<Int, Boolean>()
            
            // Process all connections concurrently
            val jobs = connections.mapIndexed { index, conn ->
                async {
                    try {
                        // Act 1: Encapsulate packet for this connection
                        val encapsulated = udpHandler.encapsulateUdpPacket(
                            destIp = conn.destIp,
                            destPort = conn.destPort,
                            payload = conn.payload
                        )
                        
                        // Verify encapsulation succeeded
                        val encapSuccess = encapsulated.size >= 10 + conn.payload.size
                        
                        // Act 2: Simulate response for this connection
                        val response = buildSocks5UdpResponse(
                            sourceIp = conn.destIp,
                            sourcePort = conn.destPort,
                            payload = conn.payload
                        )
                        
                        // Act 3: Decapsulate response
                        val decapsulated = udpHandler.decapsulateUdpPacket(response)
                        
                        // Verify decapsulation succeeded
                        val decapSuccess = decapsulated != null &&
                                          decapsulated.sourceIp == conn.destIp &&
                                          decapsulated.sourcePort == conn.destPort &&
                                          decapsulated.payload.contentEquals(conn.payload)
                        
                        // Store result
                        results[index] = encapSuccess && decapSuccess
                        
                    } catch (e: Exception) {
                        // Any exception means this connection failed
                        results[index] = false
                    }
                }
            }
            
            // Wait for all jobs to complete
            jobs.awaitAll()
            
            // Assert: All connections should have succeeded independently
            connections.indices.forEach { index ->
                assertTrue(
                    "Connection $index should have succeeded independently",
                    results[index] == true
                )
            }
        }
    }
    
    /**
     * Property 11: Concurrent encapsulation should be thread-safe
     * 
     * For any number of concurrent encapsulation operations, each should
     * produce correct results without data corruption or race conditions.
     * 
     * Validates: Requirements 8.4, 10.2
     */
    @Test
    fun `concurrent encapsulation should be thread-safe`() = runTest {
        // Feature: socks5-udp-associate, Property 10: Concurrent Connection Independence
        // Validates: Requirements 8.4, 10.2
        
        checkAll(
            iterations = 50,
            Arb.list(Arb.connectionSpec(), 5..20)  // 5-20 concurrent operations
        ) { connections ->
            // Perform all encapsulations concurrently
            val results = connections.map { conn ->
                async {
                    udpHandler.encapsulateUdpPacket(
                        destIp = conn.destIp,
                        destPort = conn.destPort,
                        payload = conn.payload
                    )
                }
            }.awaitAll()
            
            // Assert: Each result should be valid
            results.forEachIndexed { index, encapsulated ->
                val conn = connections[index]
                
                // Verify header structure
                assertTrue(
                    "Encapsulated packet $index should have valid size",
                    encapsulated.size >= 10 + conn.payload.size
                )
                
                // Verify RSV and FRAG
                assertEquals(
                    "RSV high byte should be 0x00 for packet $index",
                    0x00,
                    encapsulated[0].toInt() and 0xFF
                )
                assertEquals(
                    "FRAG should be 0x00 for packet $index",
                    0x00,
                    encapsulated[2].toInt() and 0xFF
                )
                
                // Verify payload is preserved
                val extractedPayload = encapsulated.copyOfRange(10, encapsulated.size)
                assertArrayEquals(
                    "Payload should be preserved for packet $index",
                    conn.payload,
                    extractedPayload
                )
            }
        }
    }
    
    /**
     * Property 12: Concurrent decapsulation should be thread-safe
     * 
     * For any number of concurrent decapsulation operations, each should
     * produce correct results without data corruption or race conditions.
     * 
     * Validates: Requirements 8.4, 10.2
     */
    @Test
    fun `concurrent decapsulation should be thread-safe`() = runTest {
        // Feature: socks5-udp-associate, Property 10: Concurrent Connection Independence
        // Validates: Requirements 8.4, 10.2
        
        checkAll(
            iterations = 50,
            Arb.list(Arb.connectionSpec(), 5..20)  // 5-20 concurrent operations
        ) { connections ->
            // Build SOCKS5 responses for all connections
            val socks5Responses = connections.map { conn ->
                buildSocks5UdpResponse(
                    sourceIp = conn.destIp,
                    sourcePort = conn.destPort,
                    payload = conn.payload
                )
            }
            
            // Perform all decapsulations concurrently
            val results = socks5Responses.map { response ->
                async {
                    udpHandler.decapsulateUdpPacket(response)
                }
            }.awaitAll()
            
            // Assert: Each result should be valid
            results.forEachIndexed { index, decapsulated ->
                val conn = connections[index]
                
                assertNotNull(
                    "Decapsulation should succeed for packet $index",
                    decapsulated
                )
                
                assertEquals(
                    "Source IP should match for packet $index",
                    conn.destIp,
                    decapsulated!!.sourceIp
                )
                
                assertEquals(
                    "Source port should match for packet $index",
                    conn.destPort,
                    decapsulated.sourcePort
                )
                
                assertArrayEquals(
                    "Payload should be preserved for packet $index",
                    conn.payload,
                    decapsulated.payload
                )
            }
        }
    }
    
    /**
     * Property 13: Connection state should remain isolated
     * 
     * For multiple connections with different parameters, each connection's
     * state (addresses, ports, payloads) should remain isolated and not
     * be affected by other connections.
     * 
     * Validates: Requirements 8.4, 10.2
     */
    @Test
    fun `connection state should remain isolated`() = runTest {
        // Feature: socks5-udp-associate, Property 10: Concurrent Connection Independence
        // Validates: Requirements 8.4, 10.2
        
        checkAll(
            iterations = 100,
            Arb.connectionSpec(),
            Arb.connectionSpec()
        ) { conn1, conn2 ->
            // Ensure connections are different
            if (conn1.destIp == conn2.destIp && conn1.destPort == conn2.destPort) {
                return@checkAll  // Skip if connections are identical
            }
            
            // Process both connections
            val encap1 = udpHandler.encapsulateUdpPacket(
                destIp = conn1.destIp,
                destPort = conn1.destPort,
                payload = conn1.payload
            )
            
            val encap2 = udpHandler.encapsulateUdpPacket(
                destIp = conn2.destIp,
                destPort = conn2.destPort,
                payload = conn2.payload
            )
            
            // Assert: Each connection should maintain its own state
            // Extract destination IP from encapsulated packets
            val ip1Bytes = encap1.copyOfRange(4, 8)
            val ip2Bytes = encap2.copyOfRange(4, 8)
            
            val ip1 = java.net.InetAddress.getByAddress(ip1Bytes).hostAddress
            val ip2 = java.net.InetAddress.getByAddress(ip2Bytes).hostAddress
            
            assertEquals("Connection 1 should have correct IP", conn1.destIp, ip1)
            assertEquals("Connection 2 should have correct IP", conn2.destIp, ip2)
            
            // Extract ports
            val port1 = ((encap1[8].toInt() and 0xFF) shl 8) or (encap1[9].toInt() and 0xFF)
            val port2 = ((encap2[8].toInt() and 0xFF) shl 8) or (encap2[9].toInt() and 0xFF)
            
            assertEquals("Connection 1 should have correct port", conn1.destPort, port1)
            assertEquals("Connection 2 should have correct port", conn2.destPort, port2)
            
            // Extract payloads
            val payload1 = encap1.copyOfRange(10, encap1.size)
            val payload2 = encap2.copyOfRange(10, encap2.size)
            
            assertArrayEquals("Connection 1 should have correct payload", conn1.payload, payload1)
            assertArrayEquals("Connection 2 should have correct payload", conn2.payload, payload2)
        }
    }
    
    /**
     * Property 14: High concurrency should not cause failures
     * 
     * For a large number of concurrent connections (stress test), all
     * operations should complete successfully without failures.
     * 
     * Validates: Requirements 8.4, 10.2
     */
    @Test
    fun `high concurrency should not cause failures`() = runTest {
        // Feature: socks5-udp-associate, Property 10: Concurrent Connection Independence
        // Validates: Requirements 8.4, 10.2
        
        checkAll(
            iterations = 10,  // Fewer iterations for stress test
            Arb.list(Arb.connectionSpec(), 20..50)  // 20-50 concurrent connections
        ) { connections ->
            val successCount = AtomicInteger(0)
            val failureCount = AtomicInteger(0)
            
            // Process all connections concurrently
            val jobs = connections.map { conn ->
                async {
                    try {
                        // Encapsulate
                        val encapsulated = udpHandler.encapsulateUdpPacket(
                            destIp = conn.destIp,
                            destPort = conn.destPort,
                            payload = conn.payload
                        )
                        
                        // Build response
                        val response = buildSocks5UdpResponse(
                            sourceIp = conn.destIp,
                            sourcePort = conn.destPort,
                            payload = conn.payload
                        )
                        
                        // Decapsulate
                        val decapsulated = udpHandler.decapsulateUdpPacket(response)
                        
                        // Verify
                        if (encapsulated.size >= 10 + conn.payload.size &&
                            decapsulated != null &&
                            decapsulated.sourceIp == conn.destIp &&
                            decapsulated.sourcePort == conn.destPort) {
                            successCount.incrementAndGet()
                        } else {
                            failureCount.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        failureCount.incrementAndGet()
                    }
                }
            }
            
            // Wait for all jobs to complete
            jobs.awaitAll()
            
            // Assert: All connections should have succeeded
            assertEquals(
                "All connections should succeed under high concurrency",
                connections.size,
                successCount.get()
            )
            assertEquals(
                "No connections should fail under high concurrency",
                0,
                failureCount.get()
            )
        }
    }
    
    // ========== Helper Functions ==========
    
    /**
     * Builds a SOCKS5 UDP response packet.
     */
    private fun buildSocks5UdpResponse(
        sourceIp: String,
        sourcePort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipBytes = java.net.InetAddress.getByName(sourceIp).address
        val atyp: Byte = when (ipBytes.size) {
            4 -> 0x01  // IPv4
            16 -> 0x04 // IPv6
            else -> throw IllegalArgumentException("Invalid IP address")
        }
        
        val header = ByteArray(4 + ipBytes.size + 2)
        var offset = 0
        
        // RSV (2 bytes) = 0x0000
        header[offset++] = 0x00
        header[offset++] = 0x00
        
        // FRAG (1 byte) = 0x00
        header[offset++] = 0x00
        
        // ATYP (1 byte)
        header[offset++] = atyp
        
        // SRC.ADDR
        System.arraycopy(ipBytes, 0, header, offset, ipBytes.size)
        offset += ipBytes.size
        
        // SRC.PORT (2 bytes, big-endian)
        header[offset++] = (sourcePort shr 8).toByte()
        header[offset] = (sourcePort and 0xFF).toByte()
        
        return header + payload
    }
    
    // ========== Custom Generators ==========
    
    /**
     * Data class representing a connection specification.
     */
    data class ConnectionSpec(
        val destIp: String,
        val destPort: Int,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ConnectionSpec
            
            if (destIp != other.destIp) return false
            if (destPort != other.destPort) return false
            if (!payload.contentEquals(other.payload)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = destIp.hashCode()
            result = 31 * result + destPort
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }
    
    companion object {
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
        
        /**
         * Generator for UDP payloads (10-500 bytes for faster tests).
         */
        fun Arb.Companion.udpPayload(): Arb<ByteArray> = arbitrary {
            val size = Arb.int(10..500).bind()
            ByteArray(size) { Arb.byte().bind() }
        }
        
        /**
         * Generator for connection specifications.
         */
        fun Arb.Companion.connectionSpec(): Arb<ConnectionSpec> = arbitrary {
            ConnectionSpec(
                destIp = Arb.ipv4Address().bind(),
                destPort = Arb.validPort().bind(),
                payload = Arb.udpPayload().bind()
            )
        }
    }
}
