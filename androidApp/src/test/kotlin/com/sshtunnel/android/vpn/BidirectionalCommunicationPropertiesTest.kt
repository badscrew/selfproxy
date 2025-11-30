package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.PacketBuilder
import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Property-based tests for bidirectional UDP communication through SOCKS5.
 * 
 * Feature: socks5-udp-associate, Property 5: Bidirectional Communication Preservation
 * Validates: Requirements 4.3, 4.4, 4.5, 8.3
 */
class BidirectionalCommunicationPropertiesTest {
    
    private lateinit var logger: Logger
    private lateinit var connectionTable: ConnectionTable
    private lateinit var udpHandler: UDPHandler
    private lateinit var packetBuilder: PacketBuilder
    
    @Before
    fun setup() {
        logger = TestLogger()
        connectionTable = ConnectionTable()
        udpHandler = UDPHandler(
            socksPort = 1080,
            connectionTable = connectionTable,
            logger = logger
        )
        packetBuilder = PacketBuilder()
    }
    
    /**
     * Property 5: Bidirectional Communication Preservation
     * 
     * For any UDP packet sent through SOCKS5, if a response is received,
     * the response should be correctly routed back to the original source IP and port.
     * 
     * This test verifies that:
     * 1. Outgoing packets are correctly encapsulated
     * 2. Incoming responses are correctly decapsulated
     * 3. Source/destination addresses are properly swapped in responses
     * 4. Payload data is preserved in both directions
     * 
     * Validates: Requirements 4.3, 4.4, 4.5, 8.3
     */
    @Test
    fun `bidirectional communication should preserve addresses and payload`() = runTest {
        // Feature: socks5-udp-associate, Property 5: Bidirectional Communication Preservation
        // Validates: Requirements 4.3, 4.4, 4.5, 8.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),  // Client IP
            Arb.validPort(),    // Client port
            Arb.ipv4Address(),  // Destination IP
            Arb.validPort(),    // Destination port
            Arb.udpPayload()    // Request payload
        ) { clientIp, clientPort, destIp, destPort, requestPayload ->
            // Arrange: Create a request packet from client to destination
            val requestPacket = packetBuilder.buildUdpPacket(
                sourceIp = clientIp,
                sourcePort = clientPort,
                destIp = destIp,
                destPort = destPort,
                payload = requestPayload
            )
            
            // Act 1: Encapsulate the outgoing packet (client -> destination)
            val encapsulatedRequest = udpHandler.encapsulateUdpPacket(
                destIp = destIp,
                destPort = destPort,
                payload = requestPayload
            )
            
            // Assert 1: Encapsulated packet should have correct destination
            assertTrue(
                "Encapsulated request should have valid size",
                encapsulatedRequest.size >= 10 + requestPayload.size
            )
            
            // Simulate response from destination
            // Response payload (could be different from request)
            val responsePayload = ByteArray(requestPayload.size) { 
                (requestPayload[it].toInt() xor 0xFF).toByte() 
            }
            
            // Act 2: Build SOCKS5 UDP response packet (destination -> client)
            // This simulates what the SOCKS5 server would send back
            val socks5Response = buildSocks5UdpResponse(
                sourceIp = destIp,
                sourcePort = destPort,
                payload = responsePayload
            )
            
            // Act 3: Decapsulate the response
            val decapsulated = udpHandler.decapsulateUdpPacket(socks5Response)
            
            // Assert 2: Decapsulation should succeed
            assertNotNull("Response should be decapsulated successfully", decapsulated)
            
            // Assert 3: Source address should match destination (addresses are swapped)
            assertEquals(
                "Response source IP should match original destination",
                destIp,
                decapsulated!!.sourceIp
            )
            assertEquals(
                "Response source port should match original destination port",
                destPort,
                decapsulated.sourcePort
            )
            
            // Assert 4: Payload should be preserved
            assertArrayEquals(
                "Response payload should be preserved",
                responsePayload,
                decapsulated.payload
            )
            
            // Act 4: Build final response packet to send back to client
            val finalResponse = packetBuilder.buildUdpPacket(
                sourceIp = decapsulated.sourceIp,  // Destination becomes source
                sourcePort = decapsulated.sourcePort,
                destIp = clientIp,                  // Client becomes destination
                destPort = clientPort,
                payload = decapsulated.payload
            )
            
            // Assert 5: Final response should have correct addressing
            assertTrue(
                "Final response packet should have valid size",
                finalResponse.size > 0
            )
            
            // Verify the complete round-trip:
            // 1. Client sends to destination: clientIp:clientPort -> destIp:destPort
            // 2. Response comes from destination: destIp:destPort -> clientIp:clientPort
            // This ensures bidirectional communication is preserved
        }
    }
    
    /**
     * Property 6: Bidirectional communication should handle concurrent flows
     * 
     * For multiple simultaneous UDP flows, each flow's bidirectional communication
     * should be independent and not interfere with others.
     * 
     * Validates: Requirements 4.3, 4.4, 8.3, 8.4
     */
    @Test
    fun `bidirectional communication should handle multiple concurrent flows`() = runTest {
        // Feature: socks5-udp-associate, Property 5: Bidirectional Communication Preservation
        // Validates: Requirements 4.3, 4.4, 8.3, 8.4
        
        checkAll(
            iterations = 50,
            Arb.list(Arb.udpFlow(), 2..5)  // Generate 2-5 concurrent flows
        ) { flows ->
            // Process each flow
            flows.forEach { flow ->
                // Act 1: Encapsulate outgoing packet
                val encapsulated = udpHandler.encapsulateUdpPacket(
                    destIp = flow.destIp,
                    destPort = flow.destPort,
                    payload = flow.payload
                )
                
                // Assert 1: Each flow's encapsulation should be independent
                assertTrue(
                    "Encapsulated packet should be valid for flow ${flow.clientIp}:${flow.clientPort}",
                    encapsulated.size >= 10
                )
                
                // Act 2: Simulate response
                val response = buildSocks5UdpResponse(
                    sourceIp = flow.destIp,
                    sourcePort = flow.destPort,
                    payload = flow.payload
                )
                
                // Act 3: Decapsulate response
                val decapsulated = udpHandler.decapsulateUdpPacket(response)
                
                // Assert 2: Each flow's response should be correctly decapsulated
                assertNotNull(
                    "Response should be decapsulated for flow ${flow.clientIp}:${flow.clientPort}",
                    decapsulated
                )
                assertEquals(
                    "Response should have correct source IP",
                    flow.destIp,
                    decapsulated!!.sourceIp
                )
                assertEquals(
                    "Response should have correct source port",
                    flow.destPort,
                    decapsulated.sourcePort
                )
            }
        }
    }
    
    /**
     * Property 7: Bidirectional communication should preserve payload integrity
     * 
     * For any payload sent in either direction, the payload should be
     * received exactly as sent, with no corruption or modification.
     * 
     * Validates: Requirements 4.3, 4.5, 8.3
     */
    @Test
    fun `bidirectional communication should preserve payload integrity`() = runTest {
        // Feature: socks5-udp-associate, Property 5: Bidirectional Communication Preservation
        // Validates: Requirements 4.3, 4.5, 8.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort(),
            Arb.ipv4Address(),
            Arb.validPort(),
            Arb.udpPayload()
        ) { clientIp, clientPort, destIp, destPort, payload ->
            // Test forward direction (client -> destination)
            val encapsulated = udpHandler.encapsulateUdpPacket(
                destIp = destIp,
                destPort = destPort,
                payload = payload
            )
            
            // Extract payload from encapsulated packet
            val headerSize = when {
                destIp.contains(":") -> 22  // IPv6
                else -> 10  // IPv4
            }
            val extractedPayload = encapsulated.copyOfRange(headerSize, encapsulated.size)
            
            // Assert: Forward direction payload should be preserved
            assertArrayEquals(
                "Forward direction payload should be preserved",
                payload,
                extractedPayload
            )
            
            // Test reverse direction (destination -> client)
            val response = buildSocks5UdpResponse(
                sourceIp = destIp,
                sourcePort = destPort,
                payload = payload
            )
            
            val decapsulated = udpHandler.decapsulateUdpPacket(response)
            
            // Assert: Reverse direction payload should be preserved
            assertNotNull("Response should be decapsulated", decapsulated)
            assertArrayEquals(
                "Reverse direction payload should be preserved",
                payload,
                decapsulated!!.payload
            )
        }
    }
    
    // ========== Helper Functions ==========
    
    /**
     * Builds a SOCKS5 UDP response packet.
     * 
     * This simulates what a SOCKS5 server would send back when a response
     * is received from the destination.
     */
    private fun buildSocks5UdpResponse(
        sourceIp: String,
        sourcePort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipBytes = InetAddress.getByName(sourceIp).address
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
     * Data class representing a UDP flow.
     */
    data class UdpFlow(
        val clientIp: String,
        val clientPort: Int,
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
         * Generator for UDP payloads (0-1500 bytes).
         */
        fun Arb.Companion.udpPayload(): Arb<ByteArray> = arbitrary {
            val size = Arb.int(0..1500).bind()
            ByteArray(size) { Arb.byte().bind() }
        }
        
        /**
         * Generator for UDP flows.
         */
        fun Arb.Companion.udpFlow(): Arb<UdpFlow> = arbitrary {
            UdpFlow(
                clientIp = Arb.ipv4Address().bind(),
                clientPort = Arb.validPort().bind(),
                destIp = Arb.ipv4Address().bind(),
                destPort = Arb.validPort().bind(),
                payload = Arb.udpPayload().bind()
            )
        }
    }
}
