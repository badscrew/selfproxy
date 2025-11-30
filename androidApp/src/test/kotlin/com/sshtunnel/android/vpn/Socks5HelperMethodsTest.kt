package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Unit tests for SOCKS5 helper methods in UDPHandler.
 * 
 * Tests:
 * - SOCKS5 message construction
 * - Address encoding (IPv4)
 * - Port encoding (big-endian)
 * - Header parsing
 * - Error code mapping
 * 
 * Requirements: 12.1, 12.2
 */
class Socks5HelperMethodsTest {
    
    private lateinit var udpHandler: UDPHandler
    private lateinit var connectionTable: ConnectionTable
    private lateinit var logger: Logger
    
    @Before
    fun setup() {
        connectionTable = ConnectionTable()
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
            connectionTable = connectionTable,
            logger = logger
        )
    }
    
    // ========== SOCKS5 UDP Encapsulation Tests ==========
    
    @Test
    fun `encapsulate UDP packet with IPv4 address`() {
        val destIp = "8.8.8.8"
        val destPort = 53
        val payload = "test payload".toByteArray()
        
        val encapsulated = udpHandler.encapsulateUdpPacket(destIp, destPort, payload)
        
        // Verify header structure
        // RSV (2 bytes) + FRAG (1 byte) + ATYP (1 byte) + IPv4 (4 bytes) + PORT (2 bytes) = 10 bytes
        assertTrue("Encapsulated packet should be at least 10 bytes", encapsulated.size >= 10)
        
        // Verify RSV = 0x0000
        assertEquals("RSV high byte should be 0x00", 0x00, encapsulated[0].toInt() and 0xFF)
        assertEquals("RSV low byte should be 0x00", 0x00, encapsulated[1].toInt() and 0xFF)
        
        // Verify FRAG = 0x00
        assertEquals("FRAG should be 0x00", 0x00, encapsulated[2].toInt() and 0xFF)
        
        // Verify ATYP = 0x01 (IPv4)
        assertEquals("ATYP should be 0x01 for IPv4", 0x01, encapsulated[3].toInt() and 0xFF)
        
        // Verify IPv4 address (8.8.8.8 = 0x08080808)
        assertEquals("First octet should be 8", 0x08, encapsulated[4].toInt() and 0xFF)
        assertEquals("Second octet should be 8", 0x08, encapsulated[5].toInt() and 0xFF)
        assertEquals("Third octet should be 8", 0x08, encapsulated[6].toInt() and 0xFF)
        assertEquals("Fourth octet should be 8", 0x08, encapsulated[7].toInt() and 0xFF)
        
        // Verify port (big-endian)
        val actualPort = ((encapsulated[8].toInt() and 0xFF) shl 8) or (encapsulated[9].toInt() and 0xFF)
        assertEquals("Port should be encoded correctly", destPort, actualPort)
        
        // Verify payload is appended
        val actualPayload = encapsulated.copyOfRange(10, encapsulated.size)
        assertArrayEquals("Payload should be preserved", payload, actualPayload)
    }
    
    @Test
    fun `encapsulate UDP packet with different IPv4 addresses`() {
        val testCases = listOf(
            Triple("192.168.1.1", byteArrayOf(0xC0.toByte(), 0xA8.toByte(), 0x01, 0x01), "192.168.1.1"),
            Triple("10.0.0.1", byteArrayOf(0x0A, 0x00, 0x00, 0x01), "10.0.0.1"),
            Triple("172.16.0.1", byteArrayOf(0xAC.toByte(), 0x10, 0x00, 0x01), "172.16.0.1"),
            Triple("255.255.255.255", byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), "255.255.255.255")
        )
        
        testCases.forEach { (ip, expectedBytes, description) ->
            val encapsulated = udpHandler.encapsulateUdpPacket(ip, 80, ByteArray(0))
            
            // Extract IP address bytes (offset 4-7)
            val actualBytes = encapsulated.copyOfRange(4, 8)
            assertArrayEquals("IP address $description should be encoded correctly", expectedBytes, actualBytes)
        }
    }
    
    @Test
    fun `encapsulate UDP packet with various port numbers`() {
        val testPorts = listOf(
            0,      // Minimum port
            53,     // DNS
            80,     // HTTP
            443,    // HTTPS
            1080,   // SOCKS
            8080,   // Alt HTTP
            32768,  // Mid-range
            65535   // Maximum port
        )
        
        testPorts.forEach { port ->
            val encapsulated = udpHandler.encapsulateUdpPacket("8.8.8.8", port, ByteArray(0))
            
            // Extract port (offset 8-9, big-endian)
            val actualPort = ((encapsulated[8].toInt() and 0xFF) shl 8) or (encapsulated[9].toInt() and 0xFF)
            assertEquals("Port $port should be encoded correctly in big-endian", port, actualPort)
        }
    }
    
    @Test
    fun `encapsulate UDP packet with empty payload`() {
        val encapsulated = udpHandler.encapsulateUdpPacket("8.8.8.8", 53, ByteArray(0))
        
        // Should have exactly 10 bytes (header only, no payload)
        assertEquals("Encapsulated packet with empty payload should be 10 bytes", 10, encapsulated.size)
    }
    
    @Test
    fun `encapsulate UDP packet with large payload`() {
        val largePayload = ByteArray(1400) { (it % 256).toByte() }
        val encapsulated = udpHandler.encapsulateUdpPacket("8.8.8.8", 53, largePayload)
        
        // Should have 10 bytes header + 1400 bytes payload
        assertEquals("Encapsulated packet size should be header + payload", 10 + 1400, encapsulated.size)
        
        // Verify payload is preserved
        val actualPayload = encapsulated.copyOfRange(10, encapsulated.size)
        assertArrayEquals("Large payload should be preserved", largePayload, actualPayload)
    }
    
    @Test
    fun `encapsulate UDP packet preserves binary payload`() {
        val binaryPayload = byteArrayOf(
            0x00, 0xFF.toByte(), 0x7F, 0x80.toByte(),
            0x01, 0xFE.toByte(), 0xAA.toByte(), 0x55
        )
        val encapsulated = udpHandler.encapsulateUdpPacket("8.8.8.8", 53, binaryPayload)
        
        val actualPayload = encapsulated.copyOfRange(10, encapsulated.size)
        assertArrayEquals("Binary payload should be preserved exactly", binaryPayload, actualPayload)
    }
    
    // ========== SOCKS5 UDP Decapsulation Tests ==========
    
    @Test
    fun `decapsulate valid SOCKS5 UDP packet with IPv4`() {
        val sourceIp = "8.8.8.8"
        val sourcePort = 53
        val payload = "DNS response".toByteArray()
        
        // Build SOCKS5 UDP response packet
        val socks5Packet = buildSocks5UdpPacket(sourceIp, sourcePort, payload)
        
        val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
        
        assertNotNull("Decapsulation should succeed", decapsulated)
        assertEquals("Source IP should match", sourceIp, decapsulated!!.sourceIp)
        assertEquals("Source port should match", sourcePort, decapsulated.sourcePort)
        assertArrayEquals("Payload should match", payload, decapsulated.payload)
    }
    
    @Test
    fun `decapsulate SOCKS5 UDP packet with different IPv4 addresses`() {
        val testAddresses = listOf(
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1",
            "255.255.255.255",
            "1.1.1.1"
        )
        
        testAddresses.forEach { ip ->
            val socks5Packet = buildSocks5UdpPacket(ip, 80, "test".toByteArray())
            val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
            
            assertNotNull("Decapsulation should succeed for $ip", decapsulated)
            assertEquals("Source IP should match for $ip", ip, decapsulated!!.sourceIp)
        }
    }
    
    @Test
    fun `decapsulate SOCKS5 UDP packet with various ports`() {
        val testPorts = listOf(0, 53, 80, 443, 1080, 8080, 32768, 65535)
        
        testPorts.forEach { port ->
            val socks5Packet = buildSocks5UdpPacket("8.8.8.8", port, "test".toByteArray())
            val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
            
            assertNotNull("Decapsulation should succeed for port $port", decapsulated)
            assertEquals("Source port should match for port $port", port, decapsulated!!.sourcePort)
        }
    }
    
    @Test
    fun `decapsulate SOCKS5 UDP packet with empty payload`() {
        val socks5Packet = buildSocks5UdpPacket("8.8.8.8", 53, ByteArray(0))
        val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
        
        assertNotNull("Decapsulation should succeed with empty payload", decapsulated)
        assertEquals("Payload should be empty", 0, decapsulated!!.payload.size)
    }
    
    @Test
    fun `decapsulate SOCKS5 UDP packet with large payload`() {
        val largePayload = ByteArray(1400) { (it % 256).toByte() }
        val socks5Packet = buildSocks5UdpPacket("8.8.8.8", 53, largePayload)
        val decapsulated = udpHandler.decapsulateUdpPacket(socks5Packet)
        
        assertNotNull("Decapsulation should succeed with large payload", decapsulated)
        assertArrayEquals("Large payload should be preserved", largePayload, decapsulated!!.payload)
    }
    
    @Test
    fun `return null for packet too short`() {
        // Packet with only 5 bytes (minimum is 10 for IPv4)
        val shortPacket = ByteArray(5)
        val decapsulated = udpHandler.decapsulateUdpPacket(shortPacket)
        
        assertNull("Decapsulation should fail for packet too short", decapsulated)
    }
    
    @Test
    fun `return null for packet with invalid RSV field`() {
        // Build packet with invalid RSV (should be 0x0000)
        val packet = ByteBuffer.allocate(20).apply {
            put(0x01) // RSV high byte (invalid, should be 0x00)
            put(0x00) // RSV low byte
            put(0x00) // FRAG
            put(0x01) // ATYP (IPv4)
            put(byteArrayOf(8, 8, 8, 8)) // IP address
            putShort(53) // Port
            put("test".toByteArray()) // Payload
        }.array()
        
        val decapsulated = udpHandler.decapsulateUdpPacket(packet)
        
        assertNull("Decapsulation should fail for invalid RSV", decapsulated)
    }
    
    @Test
    fun `return null for packet with non-zero FRAG field`() {
        // Build packet with FRAG != 0x00 (fragmentation not supported)
        val packet = ByteBuffer.allocate(20).apply {
            put(0x00) // RSV high byte
            put(0x00) // RSV low byte
            put(0x01) // FRAG (invalid, should be 0x00)
            put(0x01) // ATYP (IPv4)
            put(byteArrayOf(8, 8, 8, 8)) // IP address
            putShort(53) // Port
            put("test".toByteArray()) // Payload
        }.array()
        
        val decapsulated = udpHandler.decapsulateUdpPacket(packet)
        
        assertNull("Decapsulation should fail for non-zero FRAG", decapsulated)
    }
    
    @Test
    fun `return null for packet with unknown address type`() {
        // Build packet with unknown ATYP
        val packet = ByteBuffer.allocate(20).apply {
            put(0x00) // RSV high byte
            put(0x00) // RSV low byte
            put(0x00) // FRAG
            put(0x99.toByte()) // ATYP (invalid, unknown type)
            put(byteArrayOf(8, 8, 8, 8)) // IP address
            putShort(53) // Port
            put("test".toByteArray()) // Payload
        }.array()
        
        val decapsulated = udpHandler.decapsulateUdpPacket(packet)
        
        assertNull("Decapsulation should fail for unknown address type", decapsulated)
    }
    
    @Test
    fun `return null for IPv4 packet too short for address`() {
        // Packet with ATYP=IPv4 but not enough bytes for 4-byte address
        val packet = ByteBuffer.allocate(8).apply {
            put(0x00) // RSV high byte
            put(0x00) // RSV low byte
            put(0x00) // FRAG
            put(0x01) // ATYP (IPv4)
            put(byteArrayOf(8, 8)) // Only 2 bytes instead of 4
        }.array()
        
        val decapsulated = udpHandler.decapsulateUdpPacket(packet)
        
        assertNull("Decapsulation should fail for truncated IPv4 address", decapsulated)
    }
    
    @Test
    fun `return null for packet too short for port`() {
        // Packet with address but no port
        val packet = ByteBuffer.allocate(8).apply {
            put(0x00) // RSV high byte
            put(0x00) // RSV low byte
            put(0x00) // FRAG
            put(0x01) // ATYP (IPv4)
            put(byteArrayOf(8, 8, 8, 8)) // IP address (4 bytes)
            // Missing port (2 bytes)
        }.array()
        
        val decapsulated = udpHandler.decapsulateUdpPacket(packet)
        
        assertNull("Decapsulation should fail for missing port", decapsulated)
    }
    
    // ========== Port Encoding Tests (Big-Endian) ==========
    
    @Test
    fun `port encoding is big-endian in encapsulation`() {
        // Test that port 0x1234 (4660) is encoded as [0x12, 0x34]
        val port = 0x1234
        val encapsulated = udpHandler.encapsulateUdpPacket("8.8.8.8", port, ByteArray(0))
        
        // Port is at offset 8-9
        assertEquals("Port high byte should be 0x12", 0x12, encapsulated[8].toInt() and 0xFF)
        assertEquals("Port low byte should be 0x34", 0x34, encapsulated[9].toInt() and 0xFF)
    }
    
    @Test
    fun `port decoding is big-endian in decapsulation`() {
        // Build packet with port 0x5678 (22136) encoded as [0x56, 0x78]
        val packet = ByteBuffer.allocate(10).apply {
            put(0x00) // RSV high byte
            put(0x00) // RSV low byte
            put(0x00) // FRAG
            put(0x01) // ATYP (IPv4)
            put(byteArrayOf(8, 8, 8, 8)) // IP address
            put(0x56) // Port high byte
            put(0x78) // Port low byte
        }.array()
        
        val decapsulated = udpHandler.decapsulateUdpPacket(packet)
        
        assertNotNull("Decapsulation should succeed", decapsulated)
        assertEquals("Port should be decoded as 0x5678", 0x5678, decapsulated!!.sourcePort)
    }
    
    @Test
    fun `port encoding handles edge cases`() {
        // Test port 0 (encoded as [0x00, 0x00])
        val encapsulated0 = udpHandler.encapsulateUdpPacket("8.8.8.8", 0, ByteArray(0))
        assertEquals("Port 0 high byte", 0x00, encapsulated0[8].toInt() and 0xFF)
        assertEquals("Port 0 low byte", 0x00, encapsulated0[9].toInt() and 0xFF)
        
        // Test port 255 (encoded as [0x00, 0xFF])
        val encapsulated255 = udpHandler.encapsulateUdpPacket("8.8.8.8", 255, ByteArray(0))
        assertEquals("Port 255 high byte", 0x00, encapsulated255[8].toInt() and 0xFF)
        assertEquals("Port 255 low byte", 0xFF, encapsulated255[9].toInt() and 0xFF)
        
        // Test port 256 (encoded as [0x01, 0x00])
        val encapsulated256 = udpHandler.encapsulateUdpPacket("8.8.8.8", 256, ByteArray(0))
        assertEquals("Port 256 high byte", 0x01, encapsulated256[8].toInt() and 0xFF)
        assertEquals("Port 256 low byte", 0x00, encapsulated256[9].toInt() and 0xFF)
        
        // Test port 65535 (encoded as [0xFF, 0xFF])
        val encapsulated65535 = udpHandler.encapsulateUdpPacket("8.8.8.8", 65535, ByteArray(0))
        assertEquals("Port 65535 high byte", 0xFF, encapsulated65535[8].toInt() and 0xFF)
        assertEquals("Port 65535 low byte", 0xFF, encapsulated65535[9].toInt() and 0xFF)
    }
    
    // ========== Address Encoding Tests (IPv4) ==========
    
    @Test
    fun `IPv4 address encoding is correct`() {
        // Test that 192.168.1.1 is encoded as [0xC0, 0xA8, 0x01, 0x01]
        val encapsulated = udpHandler.encapsulateUdpPacket("192.168.1.1", 80, ByteArray(0))
        
        // IPv4 address is at offset 4-7
        assertEquals("First octet should be 192 (0xC0)", 0xC0, encapsulated[4].toInt() and 0xFF)
        assertEquals("Second octet should be 168 (0xA8)", 0xA8, encapsulated[5].toInt() and 0xFF)
        assertEquals("Third octet should be 1", 0x01, encapsulated[6].toInt() and 0xFF)
        assertEquals("Fourth octet should be 1", 0x01, encapsulated[7].toInt() and 0xFF)
    }
    
    @Test
    fun `IPv4 address encoding handles all octets correctly`() {
        // Test address with all different octets
        val encapsulated = udpHandler.encapsulateUdpPacket("10.20.30.40", 80, ByteArray(0))
        
        assertEquals("First octet should be 10", 10, encapsulated[4].toInt() and 0xFF)
        assertEquals("Second octet should be 20", 20, encapsulated[5].toInt() and 0xFF)
        assertEquals("Third octet should be 30", 30, encapsulated[6].toInt() and 0xFF)
        assertEquals("Fourth octet should be 40", 40, encapsulated[7].toInt() and 0xFF)
    }
    
    @Test
    fun `IPv4 address encoding handles edge values`() {
        // Test 0.0.0.0
        val encapsulated0 = udpHandler.encapsulateUdpPacket("0.0.0.0", 80, ByteArray(0))
        assertEquals("0.0.0.0 first octet", 0, encapsulated0[4].toInt() and 0xFF)
        assertEquals("0.0.0.0 second octet", 0, encapsulated0[5].toInt() and 0xFF)
        assertEquals("0.0.0.0 third octet", 0, encapsulated0[6].toInt() and 0xFF)
        assertEquals("0.0.0.0 fourth octet", 0, encapsulated0[7].toInt() and 0xFF)
        
        // Test 255.255.255.255
        val encapsulated255 = udpHandler.encapsulateUdpPacket("255.255.255.255", 80, ByteArray(0))
        assertEquals("255.255.255.255 first octet", 255, encapsulated255[4].toInt() and 0xFF)
        assertEquals("255.255.255.255 second octet", 255, encapsulated255[5].toInt() and 0xFF)
        assertEquals("255.255.255.255 third octet", 255, encapsulated255[6].toInt() and 0xFF)
        assertEquals("255.255.255.255 fourth octet", 255, encapsulated255[7].toInt() and 0xFF)
    }
    
    @Test
    fun `IPv4 ATYP field is set correctly`() {
        val encapsulated = udpHandler.encapsulateUdpPacket("8.8.8.8", 53, ByteArray(0))
        
        // ATYP is at offset 3
        assertEquals("ATYP should be 0x01 for IPv4", 0x01, encapsulated[3].toInt() and 0xFF)
    }
    
    // ========== Round-Trip Tests ==========
    
    @Test
    fun `encapsulation and decapsulation round-trip preserves data`() {
        val originalIp = "192.168.1.100"
        val originalPort = 12345
        val originalPayload = "Hello, SOCKS5!".toByteArray()
        
        // Encapsulate
        val encapsulated = udpHandler.encapsulateUdpPacket(originalIp, originalPort, originalPayload)
        
        // Decapsulate
        val decapsulated = udpHandler.decapsulateUdpPacket(encapsulated)
        
        assertNotNull("Decapsulation should succeed", decapsulated)
        assertEquals("IP should match after round-trip", originalIp, decapsulated!!.sourceIp)
        assertEquals("Port should match after round-trip", originalPort, decapsulated.sourcePort)
        assertArrayEquals("Payload should match after round-trip", originalPayload, decapsulated.payload)
    }
    
    @Test
    fun `round-trip with various data combinations`() {
        val testCases = listOf(
            Triple("8.8.8.8", 53, "DNS query".toByteArray()),
            Triple("1.1.1.1", 443, "HTTPS data".toByteArray()),
            Triple("192.168.0.1", 80, ByteArray(0)),
            Triple("10.0.0.1", 65535, ByteArray(1000) { it.toByte() })
        )
        
        testCases.forEach { (ip, port, payload) ->
            val encapsulated = udpHandler.encapsulateUdpPacket(ip, port, payload)
            val decapsulated = udpHandler.decapsulateUdpPacket(encapsulated)
            
            assertNotNull("Round-trip should succeed for $ip:$port", decapsulated)
            assertEquals("IP should match for $ip:$port", ip, decapsulated!!.sourceIp)
            assertEquals("Port should match for $ip:$port", port, decapsulated.sourcePort)
            assertArrayEquals("Payload should match for $ip:$port", payload, decapsulated.payload)
        }
    }
    
    // ========== Helper Functions ==========
    
    /**
     * Helper function to build a SOCKS5 UDP packet for testing.
     * 
     * SOCKS5 UDP packet format:
     * +----+------+------+----------+----------+----------+
     * |RSV | FRAG | ATYP | SRC.ADDR | SRC.PORT |   DATA   |
     * +----+------+------+----------+----------+----------+
     * | 2  |  1   |  1   | Variable |    2     | Variable |
     * +----+------+------+----------+----------+----------+
     */
    private fun buildSocks5UdpPacket(
        sourceIp: String,
        sourcePort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipBytes = java.net.InetAddress.getByName(sourceIp).address
        
        return ByteBuffer.allocate(4 + ipBytes.size + 2 + payload.size).apply {
            // RSV: Reserved (2 bytes, must be 0x0000)
            put(0x00)
            put(0x00)
            
            // FRAG: Fragment number (1 byte, 0x00 = no fragmentation)
            put(0x00)
            
            // ATYP: Address type (1 byte, 0x01 = IPv4)
            put(0x01)
            
            // SRC.ADDR: Source address (4 bytes for IPv4)
            put(ipBytes)
            
            // SRC.PORT: Source port (2 bytes, big-endian)
            putShort(sourcePort.toShort())
            
            // DATA: Payload
            put(payload)
        }.array()
    }
}
