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
 * Property-based tests for SOCKS5 UDP packet decapsulation.
 * 
 * Feature: socks5-udp-associate, Property 3: UDP Decapsulation Correctness
 * Validates: Requirements 4.1, 4.2, 4.3
 */
class UdpDecapsulationPropertiesTest {
    
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
     * Property 3: UDP Decapsulation Correctness
     * 
     * For any SOCKS5 UDP response packet, decapsulation should correctly extract
     * the source address, source port, and payload without data corruption.
     * 
     * Validates: Requirements 4.1, 4.2, 4.3
     */
    @Test
    fun `UDP decapsulation should correctly extract source address port and payload for IPv4`() = runTest {
        // Feature: socks5-udp-associate, Property 3: UDP Decapsulation Correctness
        // Validates: Requirements 4.1, 4.2, 4.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort(),
            Arb.udpPayload()
        ) { sourceIp, sourcePort, payload ->
            // Arrange: Build a valid SOCKS5 UDP packet manually
            val socks5Packet = buildSocks5UdpPacket(sourceIp, sourcePort, payload)
            
            // Act: Decapsulate the packet
            val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
            
            // Assert: Verify extraction is correct
            assertNotNull("Decapsulation should succeed for valid packet", decapsulated)
            assertEquals("Source IP should match", sourceIp, decapsulated!!.sourceIp)
            assertEquals("Source port should match", sourcePort, decapsulated.sourcePort)
            assertArrayEquals("Payload should be preserved exactly", payload, decapsulated.payload)
        }
    }
    
    /**
     * Property 4: Round-trip consistency
     * 
     * For any UDP packet, encapsulating then decapsulating should preserve
     * the destination address, port, and payload.
     * 
     * Validates: Requirements 3.1, 3.2, 4.1, 4.2, 4.3
     */
    @Test
    fun `encapsulate then decapsulate should preserve data for IPv4`() = runTest {
        // Feature: socks5-udp-associate, Property 3: UDP Decapsulation Correctness
        // Validates: Requirements 3.1, 3.2, 4.1, 4.2, 4.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort(),
            Arb.udpPayload()
        ) { address, port, payload ->
            // Act: Encapsulate then decapsulate
            val encapsulated = udpHandler.encapsulateUdpPacket(address, port, payload)
            val decapsulated = udpHandler.decapsulateUdpPacket(encapsulated)
            
            // Assert: Data should be preserved
            assertNotNull("Decapsulation should succeed", decapsulated)
            assertEquals("Address should be preserved", address, decapsulated!!.sourceIp)
            assertEquals("Port should be preserved", port, decapsulated.sourcePort)
            assertArrayEquals("Payload should be preserved", payload, decapsulated.payload)
        }
    }
    
    /**
     * Property 5: UDP decapsulation should handle IPv6 addresses
     * 
     * For any IPv6 SOCKS5 UDP packet, decapsulation should correctly extract
     * the source address, port, and payload.
     * 
     * Validates: Requirements 4.1, 4.2, 4.3
     */
    @Test
    fun `UDP decapsulation should correctly extract source address port and payload for IPv6`() = runTest {
        // Feature: socks5-udp-associate, Property 3: UDP Decapsulation Correctness
        // Validates: Requirements 4.1, 4.2, 4.3
        
        checkAll(
            iterations = 100,
            Arb.ipv6Address(),
            Arb.validPort(),
            Arb.udpPayload()
        ) { sourceIp, sourcePort, payload ->
            // Arrange: Build a valid SOCKS5 UDP packet manually
            val socks5Packet = buildSocks5UdpPacket(sourceIp, sourcePort, payload)
            
            // Act: Decapsulate the packet
            val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
            
            // Assert: Verify extraction is correct
            assertNotNull("Decapsulation should succeed for valid packet", decapsulated)
            // Normalize IPv6 addresses for comparison (Java removes leading zeros)
            val normalizedSourceIp = normalizeIpv6(sourceIp)
            val normalizedDecapsulatedIp = normalizeIpv6(decapsulated!!.sourceIp)
            assertEquals("Source IP should match", normalizedSourceIp, normalizedDecapsulatedIp)
            assertEquals("Source port should match", sourcePort, decapsulated.sourcePort)
            assertArrayEquals("Payload should be preserved exactly", payload, decapsulated.payload)
        }
    }
    
    /**
     * Property 6: Round-trip consistency for IPv6
     * 
     * For any IPv6 UDP packet, encapsulating then decapsulating should preserve
     * the destination address, port, and payload.
     * 
     * Validates: Requirements 3.1, 3.3, 4.1, 4.2, 4.3
     */
    @Test
    fun `encapsulate then decapsulate should preserve data for IPv6`() = runTest {
        // Feature: socks5-udp-associate, Property 3: UDP Decapsulation Correctness
        // Validates: Requirements 3.1, 3.3, 4.1, 4.2, 4.3
        
        checkAll(
            iterations = 100,
            Arb.ipv6Address(),
            Arb.validPort(),
            Arb.udpPayload()
        ) { address, port, payload ->
            // Act: Encapsulate then decapsulate
            val encapsulated = udpHandler.encapsulateUdpPacket(address, port, payload)
            val decapsulated = udpHandler.decapsulateUdpPacket(encapsulated)
            
            // Assert: Data should be preserved
            assertNotNull("Decapsulation should succeed", decapsulated)
            // Normalize IPv6 addresses for comparison (Java removes leading zeros)
            val normalizedAddress = normalizeIpv6(address)
            val normalizedDecapsulatedIp = normalizeIpv6(decapsulated!!.sourceIp)
            assertEquals("Address should be preserved", normalizedAddress, normalizedDecapsulatedIp)
            assertEquals("Port should be preserved", port, decapsulated.sourcePort)
            assertArrayEquals("Payload should be preserved", payload, decapsulated.payload)
        }
    }
    
    /**
     * Property 7: UDP decapsulation should reject invalid RSV field
     * 
     * For any SOCKS5 UDP packet with RSV != 0x0000, decapsulation should fail.
     * 
     * Validates: Requirements 4.2, 7.3
     */
    @Test
    fun `UDP decapsulation should reject packets with invalid RSV field`() = runTest {
        // Feature: socks5-udp-associate, Property 3: UDP Decapsulation Correctness
        // Validates: Requirements 4.2, 7.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort(),
            Arb.udpPayload(),
            Arb.invalidRsv()
        ) { sourceIp, sourcePort, payload, invalidRsv ->
            // Arrange: Build a SOCKS5 UDP packet with invalid RSV
            val socks5Packet = buildSocks5UdpPacketWithRsv(sourceIp, sourcePort, payload, invalidRsv)
            
            // Act: Attempt to decapsulate
            val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
            
            // Assert: Should fail
            assertNull("Decapsulation should fail for invalid RSV", decapsulated)
        }
    }
    
    /**
     * Property 8: UDP decapsulation should reject invalid FRAG field
     * 
     * For any SOCKS5 UDP packet with FRAG != 0x00, decapsulation should fail
     * (fragmentation not supported).
     * 
     * Validates: Requirements 4.2, 7.3
     */
    @Test
    fun `UDP decapsulation should reject packets with non-zero FRAG field`() = runTest {
        // Feature: socks5-udp-associate, Property 3: UDP Decapsulation Correctness
        // Validates: Requirements 4.2, 7.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort(),
            Arb.udpPayload(),
            Arb.nonZeroFrag()
        ) { sourceIp, sourcePort, payload, frag ->
            // Arrange: Build a SOCKS5 UDP packet with non-zero FRAG
            val socks5Packet = buildSocks5UdpPacketWithFrag(sourceIp, sourcePort, payload, frag)
            
            // Act: Attempt to decapsulate
            val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
            
            // Assert: Should fail
            assertNull("Decapsulation should fail for non-zero FRAG", decapsulated)
        }
    }
    
    /**
     * Property 9: UDP decapsulation should handle empty payloads
     * 
     * For any SOCKS5 UDP packet with an empty payload, decapsulation should
     * succeed and return an empty payload.
     * 
     * Validates: Requirements 4.1, 4.2, 4.3
     */
    @Test
    fun `UDP decapsulation should handle empty payloads`() = runTest {
        // Feature: socks5-udp-associate, Property 3: UDP Decapsulation Correctness
        // Validates: Requirements 4.1, 4.2, 4.3
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort()
        ) { sourceIp, sourcePort ->
            // Arrange: Build a SOCKS5 UDP packet with empty payload
            val payload = ByteArray(0)
            val socks5Packet = buildSocks5UdpPacket(sourceIp, sourcePort, payload)
            
            // Act: Decapsulate
            val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
            
            // Assert: Should succeed with empty payload
            assertNotNull("Decapsulation should succeed", decapsulated)
            assertEquals("Source IP should match", sourceIp, decapsulated!!.sourceIp)
            assertEquals("Source port should match", sourcePort, decapsulated.sourcePort)
            assertEquals("Payload should be empty", 0, decapsulated.payload.size)
        }
    }
    
    /**
     * Property 10: UDP decapsulation should reject truncated packets
     * 
     * For any SOCKS5 UDP packet that is too short to contain a complete header,
     * decapsulation should fail gracefully.
     * 
     * Validates: Requirements 4.2, 7.3
     */
    @Test
    fun `UDP decapsulation should reject truncated packets`() = runTest {
        // Feature: socks5-udp-associate, Property 3: UDP Decapsulation Correctness
        // Validates: Requirements 4.2, 7.3
        
        checkAll(
            iterations = 100,
            Arb.int(0..9)  // Less than minimum header size (10 bytes)
        ) { size ->
            // Arrange: Create a truncated packet
            val truncatedPacket = ByteArray(size) { 0x00 }
            
            // Act: Attempt to decapsulate
            val decapsulated = udpHandler.decapsulateUdpPacket(truncatedPacket)
            
            // Assert: Should fail
            assertNull("Decapsulation should fail for truncated packet", decapsulated)
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Normalizes an IPv6 address by converting it through InetAddress.
     * This ensures consistent formatting (removes leading zeros, etc.)
     */
    private fun normalizeIpv6(address: String): String {
        return try {
            java.net.InetAddress.getByName(address).hostAddress ?: address
        } catch (e: Exception) {
            address
        }
    }
    
    /**
     * Builds a valid SOCKS5 UDP packet for testing.
     */
    private fun buildSocks5UdpPacket(
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
        
        return ByteBuffer.allocate(4 + ipBytes.size + 2 + payload.size).apply {
            put(0x00)  // RSV high byte
            put(0x00)  // RSV low byte
            put(0x00)  // FRAG
            put(atyp)  // ATYP
            put(ipBytes)  // Source address
            putShort(sourcePort.toShort())  // Source port
            put(payload)  // Payload
        }.array()
    }
    
    /**
     * Builds a SOCKS5 UDP packet with custom RSV field.
     */
    private fun buildSocks5UdpPacketWithRsv(
        sourceIp: String,
        sourcePort: Int,
        payload: ByteArray,
        rsv: Short
    ): ByteArray {
        val ipBytes = java.net.InetAddress.getByName(sourceIp).address
        val atyp: Byte = when (ipBytes.size) {
            4 -> 0x01  // IPv4
            16 -> 0x04 // IPv6
            else -> throw IllegalArgumentException("Invalid IP address")
        }
        
        return ByteBuffer.allocate(4 + ipBytes.size + 2 + payload.size).apply {
            putShort(rsv)  // Custom RSV
            put(0x00)  // FRAG
            put(atyp)  // ATYP
            put(ipBytes)  // Source address
            putShort(sourcePort.toShort())  // Source port
            put(payload)  // Payload
        }.array()
    }
    
    /**
     * Builds a SOCKS5 UDP packet with custom FRAG field.
     */
    private fun buildSocks5UdpPacketWithFrag(
        sourceIp: String,
        sourcePort: Int,
        payload: ByteArray,
        frag: Byte
    ): ByteArray {
        val ipBytes = java.net.InetAddress.getByName(sourceIp).address
        val atyp: Byte = when (ipBytes.size) {
            4 -> 0x01  // IPv4
            16 -> 0x04 // IPv6
            else -> throw IllegalArgumentException("Invalid IP address")
        }
        
        return ByteBuffer.allocate(4 + ipBytes.size + 2 + payload.size).apply {
            put(0x00)  // RSV high byte
            put(0x00)  // RSV low byte
            put(frag)  // Custom FRAG
            put(atyp)  // ATYP
            put(ipBytes)  // Source address
            putShort(sourcePort.toShort())  // Source port
            put(payload)  // Payload
        }.array()
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
        
        /**
         * Generator for invalid RSV values (anything except 0x0000).
         */
        fun Arb.Companion.invalidRsv(): Arb<Short> = Arb.short().filter { it != 0.toShort() }
        
        /**
         * Generator for non-zero FRAG values.
         */
        fun Arb.Companion.nonZeroFrag(): Arb<Byte> = Arb.byte().filter { it != 0.toByte() }
    }
}
