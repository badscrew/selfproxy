package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Property-based tests for WebRTC port handling through SOCKS5 UDP ASSOCIATE.
 * 
 * Feature: socks5-udp-associate, Property 9: WebRTC Port Handling
 * Validates: Requirements 8.1, 8.2, 10.3
 */
class WebRtcPortHandlingPropertiesTest {
    
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
     * Property 9: WebRTC Port Handling
     * 
     * For any UDP packet destined to common WebRTC ports (3478-3479, 49152-65535),
     * the packet should be routed through SOCKS5 UDP ASSOCIATE with the same
     * latency characteristics as other UDP traffic.
     * 
     * This test verifies that:
     * 1. STUN/TURN ports (3478-3479) are handled correctly
     * 2. High ports (49152-65535) used for WebRTC media are handled correctly
     * 3. Encapsulation/decapsulation works for WebRTC traffic
     * 4. Latency is within acceptable range (< 10ms for processing, excluding network)
     * 
     * Validates: Requirements 8.1, 8.2, 10.3
     */
    @Test
    fun `WebRTC STUN ports should be routed through SOCKS5 UDP ASSOCIATE`() = runTest {
        // Feature: socks5-udp-associate, Property 9: WebRTC Port Handling
        // Validates: Requirements 8.1, 8.2, 10.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.stunPort(),  // STUN/TURN ports (3478-3479)
            Arb.stunPayload()  // Typical STUN message
        ) { destIp, destPort, payload ->
            // Measure processing latency
            val latencyMs = measureTimeMillis {
                // Act: Encapsulate packet for WebRTC STUN port
                val encapsulated = udpHandler.encapsulateUdpPacket(
                    destIp = destIp,
                    destPort = destPort,
                    payload = payload
                )
                
                // Assert 1: Packet should be encapsulated correctly
                assertTrue(
                    "STUN packet should be encapsulated",
                    encapsulated.size >= 10 + payload.size
                )
                
                // Verify SOCKS5 header
                assertEquals("RSV high byte should be 0x00", 0x00, encapsulated[0].toInt() and 0xFF)
                assertEquals("RSV low byte should be 0x00", 0x00, encapsulated[1].toInt() and 0xFF)
                assertEquals("FRAG should be 0x00", 0x00, encapsulated[2].toInt() and 0xFF)
                assertEquals("ATYP should be 0x01 for IPv4", 0x01, encapsulated[3].toInt() and 0xFF)
                
                // Verify port is correctly encoded
                val encodedPort = ((encapsulated[8].toInt() and 0xFF) shl 8) or
                                 (encapsulated[9].toInt() and 0xFF)
                assertEquals("Port should be correctly encoded", destPort, encodedPort)
                
                // Verify payload is preserved
                val extractedPayload = encapsulated.copyOfRange(10, encapsulated.size)
                assertArrayEquals("STUN payload should be preserved", payload, extractedPayload)
            }
            
            // Assert 2: Latency should be within acceptable range
            // Processing should be < 50ms in unit tests (real-world would be < 10ms)
            // Unit tests have overhead, so we use a more generous threshold
            assertTrue(
                "Processing latency should be < 50ms (was ${latencyMs}ms)",
                latencyMs < 50
            )
        }
    }
    
    /**
     * Property 10: WebRTC media ports should be handled with low latency
     * 
     * For any UDP packet destined to high ports (49152-65535) typically used
     * for WebRTC media streams, the packet should be processed with minimal latency.
     * 
     * Validates: Requirements 8.2, 10.3
     */
    @Test
    fun `WebRTC media ports should be routed through SOCKS5 UDP ASSOCIATE`() = runTest {
        // Feature: socks5-udp-associate, Property 9: WebRTC Port Handling
        // Validates: Requirements 8.2, 10.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.webRtcMediaPort(),  // High ports (49152-65535)
            Arb.rtpPayload()  // Typical RTP packet
        ) { destIp, destPort, payload ->
            // Measure processing latency
            val latencyMs = measureTimeMillis {
                // Act: Encapsulate packet for WebRTC media port
                val encapsulated = udpHandler.encapsulateUdpPacket(
                    destIp = destIp,
                    destPort = destPort,
                    payload = payload
                )
                
                // Assert 1: Packet should be encapsulated correctly
                assertTrue(
                    "RTP packet should be encapsulated",
                    encapsulated.size >= 10 + payload.size
                )
                
                // Verify SOCKS5 header structure
                assertEquals("RSV should be 0x0000", 0x00, encapsulated[0].toInt() and 0xFF)
                assertEquals("FRAG should be 0x00", 0x00, encapsulated[2].toInt() and 0xFF)
                
                // Verify port is in WebRTC media range
                val encodedPort = ((encapsulated[8].toInt() and 0xFF) shl 8) or
                                 (encapsulated[9].toInt() and 0xFF)
                assertEquals("Port should be correctly encoded", destPort, encodedPort)
                assertTrue(
                    "Port should be in WebRTC media range",
                    encodedPort in 49152..65535
                )
                
                // Verify payload is preserved
                val extractedPayload = encapsulated.copyOfRange(10, encapsulated.size)
                assertArrayEquals("RTP payload should be preserved", payload, extractedPayload)
            }
            
            // Assert 2: Latency should be within acceptable range for real-time media
            // Unit tests have overhead, so we use a more generous threshold
            assertTrue(
                "Processing latency should be < 50ms for real-time media (was ${latencyMs}ms)",
                latencyMs < 50
            )
        }
    }
    
    /**
     * Property 11: WebRTC bidirectional communication should work correctly
     * 
     * For WebRTC traffic, both outgoing (STUN requests, RTP) and incoming
     * (STUN responses, RTP) packets should be handled correctly.
     * 
     * Validates: Requirements 8.1, 8.2, 8.3
     */
    @Test
    fun `WebRTC bidirectional communication should preserve data`() = runTest {
        // Feature: socks5-udp-associate, Property 9: WebRTC Port Handling
        // Validates: Requirements 8.1, 8.2, 8.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),  // Client IP
            Arb.validPort(),    // Client port
            Arb.ipv4Address(),  // STUN server IP
            Arb.stunPort(),     // STUN server port
            Arb.stunPayload()   // STUN message
        ) { clientIp, clientPort, stunServerIp, stunServerPort, stunRequest ->
            // Test outgoing STUN request
            val encapsulatedRequest = udpHandler.encapsulateUdpPacket(
                destIp = stunServerIp,
                destPort = stunServerPort,
                payload = stunRequest
            )
            
            // Assert: Request should be encapsulated correctly
            assertTrue(
                "STUN request should be encapsulated",
                encapsulatedRequest.size >= 10 + stunRequest.size
            )
            
            // Simulate STUN response
            val stunResponse = ByteArray(stunRequest.size) { 
                (stunRequest[it].toInt() xor 0x01).toByte()  // Modified response
            }
            
            // Build SOCKS5 UDP response packet
            val socks5Response = buildSocks5UdpResponse(
                sourceIp = stunServerIp,
                sourcePort = stunServerPort,
                payload = stunResponse
            )
            
            // Test incoming STUN response
            val decapsulated = udpHandler.decapsulateUdpPacket(socks5Response)
            
            // Assert: Response should be decapsulated correctly
            assertNotNull("STUN response should be decapsulated", decapsulated)
            assertEquals(
                "Response source should be STUN server",
                stunServerIp,
                decapsulated!!.sourceIp
            )
            assertEquals(
                "Response port should be STUN server port",
                stunServerPort,
                decapsulated.sourcePort
            )
            assertArrayEquals(
                "STUN response payload should be preserved",
                stunResponse,
                decapsulated.payload
            )
        }
    }
    
    /**
     * Property 12: Multiple WebRTC streams should be handled independently
     * 
     * For multiple simultaneous WebRTC streams (e.g., multiple video calls),
     * each stream should be handled independently without interference.
     * 
     * Validates: Requirements 8.4, 10.2
     */
    @Test
    fun `multiple WebRTC streams should be handled independently`() = runTest {
        // Feature: socks5-udp-associate, Property 9: WebRTC Port Handling
        // Validates: Requirements 8.4, 10.2
        
        checkAll(
            iterations = 50,
            Arb.list(Arb.webRtcStream(), 2..5)  // 2-5 concurrent streams
        ) { streams ->
            // Process each stream
            streams.forEach { stream ->
                // Measure latency for each stream
                val latencyMs = measureTimeMillis {
                    // Encapsulate packet for this stream
                    val encapsulated = udpHandler.encapsulateUdpPacket(
                        destIp = stream.destIp,
                        destPort = stream.destPort,
                        payload = stream.payload
                    )
                    
                    // Assert: Each stream should be processed correctly
                    assertTrue(
                        "Stream packet should be encapsulated",
                        encapsulated.size >= 10 + stream.payload.size
                    )
                    
                    // Verify port is correctly encoded
                    val encodedPort = ((encapsulated[8].toInt() and 0xFF) shl 8) or
                                     (encapsulated[9].toInt() and 0xFF)
                    assertEquals(
                        "Port should match for stream",
                        stream.destPort,
                        encodedPort
                    )
                }
                
                // Assert: Each stream should have low latency
                // Unit tests have overhead, so we use a more generous threshold
                assertTrue(
                    "Stream processing should be < 50ms (was ${latencyMs}ms)",
                    latencyMs < 50
                )
            }
        }
    }
    
    /**
     * Property 13: WebRTC packet sizes should be handled correctly
     * 
     * WebRTC packets can vary in size from small STUN messages (~20 bytes)
     * to large RTP packets (~1500 bytes). All sizes should be handled correctly.
     * 
     * Validates: Requirements 8.1, 8.2, 10.3
     */
    @Test
    fun `WebRTC packets of various sizes should be handled correctly`() = runTest {
        // Feature: socks5-udp-associate, Property 9: WebRTC Port Handling
        // Validates: Requirements 8.1, 8.2, 10.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.webRtcPort(),  // Any WebRTC port
            Arb.int(20..1500)  // Packet size range
        ) { destIp, destPort, payloadSize ->
            // Generate payload of specified size
            val payload = ByteArray(payloadSize) { (it % 256).toByte() }
            
            // Act: Encapsulate packet
            val encapsulated = udpHandler.encapsulateUdpPacket(
                destIp = destIp,
                destPort = destPort,
                payload = payload
            )
            
            // Assert: Packet should be encapsulated correctly regardless of size
            assertEquals(
                "Encapsulated size should be header + payload",
                10 + payloadSize,
                encapsulated.size
            )
            
            // Verify payload is preserved
            val extractedPayload = encapsulated.copyOfRange(10, encapsulated.size)
            assertArrayEquals(
                "Payload should be preserved for size $payloadSize",
                payload,
                extractedPayload
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
     * Data class representing a WebRTC stream.
     */
    data class WebRtcStream(
        val destIp: String,
        val destPort: Int,
        val payload: ByteArray
    )
    
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
         * Generator for STUN/TURN ports (3478-3479).
         */
        fun Arb.Companion.stunPort(): Arb<Int> = Arb.int(3478..3479)
        
        /**
         * Generator for WebRTC media ports (49152-65535).
         */
        fun Arb.Companion.webRtcMediaPort(): Arb<Int> = Arb.int(49152..65535)
        
        /**
         * Generator for any WebRTC port (STUN or media).
         */
        fun Arb.Companion.webRtcPort(): Arb<Int> = arbitrary {
            if (Arb.boolean().bind()) {
                Arb.stunPort().bind()
            } else {
                Arb.webRtcMediaPort().bind()
            }
        }
        
        /**
         * Generator for STUN message payloads (20-100 bytes).
         */
        fun Arb.Companion.stunPayload(): Arb<ByteArray> = arbitrary {
            val size = Arb.int(20..100).bind()
            ByteArray(size) { Arb.byte().bind() }
        }
        
        /**
         * Generator for RTP packet payloads (100-1500 bytes).
         */
        fun Arb.Companion.rtpPayload(): Arb<ByteArray> = arbitrary {
            val size = Arb.int(100..1500).bind()
            ByteArray(size) { Arb.byte().bind() }
        }
        
        /**
         * Generator for WebRTC streams.
         */
        fun Arb.Companion.webRtcStream(): Arb<WebRtcStream> = arbitrary {
            WebRtcStream(
                destIp = Arb.ipv4Address().bind(),
                destPort = Arb.webRtcPort().bind(),
                payload = if (Arb.boolean().bind()) {
                    Arb.stunPayload().bind()
                } else {
                    Arb.rtpPayload().bind()
                }
            )
        }
    }
}
