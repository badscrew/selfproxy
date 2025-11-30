package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Property-based tests for SOCKS5 UDP packet encapsulation.
 * 
 * Feature: socks5-udp-associate, Property 2: UDP Encapsulation Format Compliance
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 11.1, 11.2, 11.3
 */
class UdpEncapsulationPropertiesTest {
    
    private lateinit var logger: Logger
    private lateinit var udpHandler: UDPHandler
    
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
        
        udpHandler = UDPHandler(
            socksPort = 1080,
            connectionTable = ConnectionTable(),
            logger = logger
        )
    }
    
    /**
     * Property 2: UDP Encapsulation Format Compliance
     * 
     * For any UDP datagram, the encapsulated packet should have a valid SOCKS5 UDP header
     * with RSV=0x0000, FRAG=0x00, and correct ATYP/DST.ADDR/DST.PORT fields.
     * The payload should be preserved exactly.
     * 
     * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 11.1, 11.2, 11.3
     */
    @Test
    fun `UDP encapsulation should produce valid SOCKS5 headers for IPv4`() = runTest {
        // Feature: socks5-udp-associate, Property 2: UDP Encapsulation Format Compliance
        // Validates: Requirements 3.1, 3.2, 3.3, 3.4, 11.1, 11.2, 11.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort(),
            Arb.udpPayload()
        ) { destIp, destPort, payload ->
            // Act: Encapsulate the packet
            val encapsulated = udpHandler.encapsulateUdpPacket(destIp, destPort, payload)
            
            // Assert: Verify header structure
            assertTrue("Encapsulated packet should have minimum size", encapsulated.size >= 10)
            
            // Verify RSV (bytes 0-1) = 0x0000
            assertEquals("RSV high byte should be 0x00", 0x00, encapsulated[0].toInt() and 0xFF)
            assertEquals("RSV low byte should be 0x00", 0x00, encapsulated[1].toInt() and 0xFF)
            
            // Verify FRAG (byte 2) = 0x00
            assertEquals("FRAG should be 0x00", 0x00, encapsulated[2].toInt() and 0xFF)
            
            // Verify ATYP (byte 3) = 0x01 for IPv4
            assertEquals("ATYP should be 0x01 for IPv4", 0x01, encapsulated[3].toInt() and 0xFF)
            
            // Verify DST.ADDR (bytes 4-7) matches the IP address
            val ipBytes = java.net.InetAddress.getByName(destIp).address
            for (i in ipBytes.indices) {
                assertEquals(
                    "IP byte $i should match",
                    ipBytes[i].toInt() and 0xFF,
                    encapsulated[4 + i].toInt() and 0xFF
                )
            }
            
            // Verify DST.PORT (bytes 8-9) matches the port
            val portFromPacket = ((encapsulated[8].toInt() and 0xFF) shl 8) or
                                (encapsulated[9].toInt() and 0xFF)
            assertEquals("Port should match", destPort, portFromPacket)
            
            // Verify payload is preserved (starts at byte 10)
            val payloadFromPacket = encapsulated.copyOfRange(10, encapsulated.size)
            assertArrayEquals("Payload should be preserved exactly", payload, payloadFromPacket)
        }
    }
    
    /**
     * Property 3: UDP encapsulation should handle empty payloads
     * 
     * For any destination with an empty payload, the encapsulated packet should
     * still have a valid header with no payload data.
     * 
     * Validates: Requirements 3.1, 3.2, 11.3
     */
    @Test
    fun `UDP encapsulation should handle empty payloads`() = runTest {
        // Feature: socks5-udp-associate, Property 2: UDP Encapsulation Format Compliance
        // Validates: Requirements 3.1, 3.2, 11.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort()
        ) { destIp, destPort ->
            // Arrange: Empty payload
            val payload = ByteArray(0)
            
            // Act: Encapsulate the packet
            val encapsulated = udpHandler.encapsulateUdpPacket(destIp, destPort, payload)
            
            // Assert: Should have exactly header size (10 bytes for IPv4)
            assertEquals("Encapsulated packet should be exactly header size", 10, encapsulated.size)
            
            // Verify header is still valid
            assertEquals("RSV high byte should be 0x00", 0x00, encapsulated[0].toInt() and 0xFF)
            assertEquals("RSV low byte should be 0x00", 0x00, encapsulated[1].toInt() and 0xFF)
            assertEquals("FRAG should be 0x00", 0x00, encapsulated[2].toInt() and 0xFF)
            assertEquals("ATYP should be 0x01 for IPv4", 0x01, encapsulated[3].toInt() and 0xFF)
        }
    }
    
    /**
     * Property 4: UDP encapsulation should handle maximum size payloads
     * 
     * For any destination with a maximum UDP payload (65507 bytes), the encapsulation
     * should succeed and preserve the entire payload.
     * 
     * Validates: Requirements 3.1, 3.5, 11.3
     */
    @Test
    fun `UDP encapsulation should handle maximum size payloads`() = runTest {
        // Feature: socks5-udp-associate, Property 2: UDP Encapsulation Format Compliance
        // Validates: Requirements 3.1, 3.5, 11.3
        
        checkAll(
            iterations = 10,  // Fewer iterations for large payloads
            Arb.ipv4Address(),
            Arb.validPort()
        ) { destIp, destPort ->
            // Arrange: Maximum UDP payload size (65507 bytes)
            val maxPayloadSize = 65507
            val payload = ByteArray(maxPayloadSize) { (it % 256).toByte() }
            
            // Act: Encapsulate the packet
            val encapsulated = udpHandler.encapsulateUdpPacket(destIp, destPort, payload)
            
            // Assert: Total size should be header + payload
            assertEquals(
                "Encapsulated packet should be header + payload",
                10 + maxPayloadSize,
                encapsulated.size
            )
            
            // Verify payload is preserved
            val payloadFromPacket = encapsulated.copyOfRange(10, encapsulated.size)
            assertArrayEquals("Payload should be preserved exactly", payload, payloadFromPacket)
        }
    }
    
    /**
     * Property 5: UDP encapsulation should handle all valid ports
     * 
     * For any valid port number (1-65535), the encapsulation should correctly
     * encode the port in big-endian format.
     * 
     * Validates: Requirements 3.1, 3.2, 11.2
     */
    @Test
    fun `UDP encapsulation should correctly encode all valid ports`() = runTest {
        // Feature: socks5-udp-associate, Property 2: UDP Encapsulation Format Compliance
        // Validates: Requirements 3.1, 3.2, 11.2
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort(),
            Arb.udpPayload()
        ) { destIp, destPort, payload ->
            // Act: Encapsulate the packet
            val encapsulated = udpHandler.encapsulateUdpPacket(destIp, destPort, payload)
            
            // Assert: Verify port is correctly encoded in big-endian
            val portFromPacket = ((encapsulated[8].toInt() and 0xFF) shl 8) or
                                (encapsulated[9].toInt() and 0xFF)
            assertEquals("Port should be correctly encoded", destPort, portFromPacket)
            
            // Also verify using ByteBuffer
            val portFromBuffer = ByteBuffer.wrap(encapsulated, 8, 2).short.toInt() and 0xFFFF
            assertEquals("Port should match when decoded with ByteBuffer", destPort, portFromBuffer)
        }
    }
    
    /**
     * Property 6: UDP encapsulation should handle IPv6 addresses
     * 
     * For any IPv6 address, the encapsulation should use ATYP=0x04 and
     * include 16 bytes for the address.
     * 
     * Validates: Requirements 3.1, 3.3, 11.2
     */
    @Test
    fun `UDP encapsulation should produce valid SOCKS5 headers for IPv6`() = runTest {
        // Feature: socks5-udp-associate, Property 2: UDP Encapsulation Format Compliance
        // Validates: Requirements 3.1, 3.3, 11.2
        
        checkAll(
            iterations = 100,
            Arb.ipv6Address(),
            Arb.validPort(),
            Arb.udpPayload()
        ) { destIp, destPort, payload ->
            // Act: Encapsulate the packet
            val encapsulated = udpHandler.encapsulateUdpPacket(destIp, destPort, payload)
            
            // Assert: Verify header structure for IPv6
            assertTrue("Encapsulated packet should have minimum size", encapsulated.size >= 22)
            
            // Verify RSV (bytes 0-1) = 0x0000
            assertEquals("RSV high byte should be 0x00", 0x00, encapsulated[0].toInt() and 0xFF)
            assertEquals("RSV low byte should be 0x00", 0x00, encapsulated[1].toInt() and 0xFF)
            
            // Verify FRAG (byte 2) = 0x00
            assertEquals("FRAG should be 0x00", 0x00, encapsulated[2].toInt() and 0xFF)
            
            // Verify ATYP (byte 3) = 0x04 for IPv6
            assertEquals("ATYP should be 0x04 for IPv6", 0x04, encapsulated[3].toInt() and 0xFF)
            
            // Verify DST.ADDR (bytes 4-19) matches the IP address
            val ipBytes = java.net.InetAddress.getByName(destIp).address
            assertEquals("IPv6 address should be 16 bytes", 16, ipBytes.size)
            for (i in ipBytes.indices) {
                assertEquals(
                    "IP byte $i should match",
                    ipBytes[i].toInt() and 0xFF,
                    encapsulated[4 + i].toInt() and 0xFF
                )
            }
            
            // Verify DST.PORT (bytes 20-21) matches the port
            val portFromPacket = ((encapsulated[20].toInt() and 0xFF) shl 8) or
                                (encapsulated[21].toInt() and 0xFF)
            assertEquals("Port should match", destPort, portFromPacket)
            
            // Verify payload is preserved (starts at byte 22)
            val payloadFromPacket = encapsulated.copyOfRange(22, encapsulated.size)
            assertArrayEquals("Payload should be preserved exactly", payload, payloadFromPacket)
        }
    }
    
    // ========== Custom Generators ==========
    
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
         * Generator for valid IPv6 addresses.
         */
        fun Arb.Companion.ipv6Address(): Arb<String> = arbitrary {
            // Generate simplified IPv6 addresses (not all possible formats)
            val parts = List(8) {
                Arb.int(0..0xFFFF).bind().toString(16).padStart(4, '0')
            }
            parts.joinToString(":")
        }
        
        /**
         * Generator for valid port numbers (1-65535).
         */
        fun Arb.Companion.validPort(): Arb<Int> = Arb.int(1..65535)
        
        /**
         * Generator for UDP payloads (0-1500 bytes, typical MTU size).
         */
        fun Arb.Companion.udpPayload(): Arb<ByteArray> = arbitrary {
            val size = Arb.int(0..1500).bind()
            ByteArray(size) { Arb.byte().bind() }
        }
    }
}
