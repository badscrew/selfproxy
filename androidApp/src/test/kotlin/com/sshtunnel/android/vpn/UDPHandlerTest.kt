package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Unit tests for UDPHandler UDP packet parsing.
 * 
 * Tests UDP header parsing, payload extraction, and DNS query detection.
 */
class UDPHandlerTest {
    
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
    
    // ========== UDP Header Parsing Tests ==========
    
    @Test
    fun `parse valid UDP header`() {
        // Build a UDP packet
        val packet = buildUdpPacket(
            sourcePort = 12345,
            destPort = 80,
            payload = "Hello, World!".toByteArray()
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        
        assertNotNull(udpHeader)
        assertEquals(12345, udpHeader!!.sourcePort)
        assertEquals(80, udpHeader.destPort)
        assertEquals(8 + 13, udpHeader.length) // Header (8) + payload (13)
        // Checksum is not validated in this test
    }
    
    @Test
    fun `parse UDP header with zero-length payload`() {
        val packet = buildUdpPacket(
            sourcePort = 54321,
            destPort = 53,
            payload = ByteArray(0)
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        
        assertNotNull(udpHeader)
        assertEquals(54321, udpHeader!!.sourcePort)
        assertEquals(53, udpHeader.destPort)
        assertEquals(8, udpHeader.length) // Only header, no payload
    }
    
    @Test
    fun `parse UDP header with large payload`() {
        val largePayload = ByteArray(1400) { it.toByte() }
        val packet = buildUdpPacket(
            sourcePort = 5000,
            destPort = 8080,
            payload = largePayload
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        
        assertNotNull(udpHeader)
        assertEquals(5000, udpHeader!!.sourcePort)
        assertEquals(8080, udpHeader.destPort)
        assertEquals(8 + 1400, udpHeader.length)
    }
    
    @Test
    fun `parse UDP header with maximum port numbers`() {
        val packet = buildUdpPacket(
            sourcePort = 65535,
            destPort = 65535,
            payload = "test".toByteArray()
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        
        assertNotNull(udpHeader)
        assertEquals(65535, udpHeader!!.sourcePort)
        assertEquals(65535, udpHeader.destPort)
    }
    
    @Test
    fun `parse UDP header with minimum port numbers`() {
        val packet = buildUdpPacket(
            sourcePort = 0,
            destPort = 0,
            payload = "test".toByteArray()
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        
        assertNotNull(udpHeader)
        assertEquals(0, udpHeader!!.sourcePort)
        assertEquals(0, udpHeader.destPort)
    }
    
    @Test
    fun `return null for packet too short for UDP header`() {
        // Packet with only IP header (20 bytes), no UDP header
        val packet = ByteArray(20)
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        
        assertNull(udpHeader)
    }
    
    @Test
    fun `return null for packet with incomplete UDP header`() {
        // Packet with IP header + partial UDP header (only 5 bytes instead of 8)
        val packet = ByteArray(25)
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        
        assertNull(udpHeader)
    }
    
    @Test
    fun `return null for packet with invalid UDP length`() {
        // Build packet with invalid length field (less than 8)
        val packet = ByteArray(40)
        val udpStart = 20
        
        // Set source port
        packet[udpStart] = 0x30
        packet[udpStart + 1] = 0x39
        
        // Set dest port
        packet[udpStart + 2] = 0x00
        packet[udpStart + 3] = 0x50
        
        // Set invalid length (7, which is less than minimum 8)
        packet[udpStart + 4] = 0x00
        packet[udpStart + 5] = 0x07
        
        // Set checksum
        packet[udpStart + 6] = 0x00
        packet[udpStart + 7] = 0x00
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        
        assertNull(udpHeader)
    }
    
    @Test
    fun `return null for truncated UDP packet`() {
        // Build packet where length field says 100 bytes but packet is only 50 bytes
        val packet = ByteArray(50)
        val udpStart = 20
        
        // Set source port
        packet[udpStart] = 0x30
        packet[udpStart + 1] = 0x39
        
        // Set dest port
        packet[udpStart + 2] = 0x00
        packet[udpStart + 3] = 0x50
        
        // Set length to 100 (but packet is only 50 bytes total)
        packet[udpStart + 4] = 0x00
        packet[udpStart + 5] = 0x64 // 100 in hex
        
        // Set checksum
        packet[udpStart + 6] = 0x00
        packet[udpStart + 7] = 0x00
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        
        assertNull(udpHeader)
    }
    
    // ========== Payload Extraction Tests ==========
    
    @Test
    fun `extract UDP payload from packet with data`() {
        val expectedPayload = "Hello, UDP!".toByteArray()
        val packet = buildUdpPacket(
            sourcePort = 12345,
            destPort = 80,
            payload = expectedPayload
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        assertNotNull(udpHeader)
        
        val actualPayload = udpHandler.extractUdpPayload(packet, 20, udpHeader!!)
        
        assertArrayEquals(expectedPayload, actualPayload)
    }
    
    @Test
    fun `extract empty payload from packet without data`() {
        val packet = buildUdpPacket(
            sourcePort = 12345,
            destPort = 80,
            payload = ByteArray(0)
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        assertNotNull(udpHeader)
        
        val payload = udpHandler.extractUdpPayload(packet, 20, udpHeader!!)
        
        assertEquals(0, payload.size)
    }
    
    @Test
    fun `extract large payload from packet`() {
        val expectedPayload = ByteArray(1000) { (it % 256).toByte() }
        val packet = buildUdpPacket(
            sourcePort = 5000,
            destPort = 8080,
            payload = expectedPayload
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        assertNotNull(udpHeader)
        
        val actualPayload = udpHandler.extractUdpPayload(packet, 20, udpHeader!!)
        
        assertArrayEquals(expectedPayload, actualPayload)
    }
    
    @Test
    fun `extract payload with binary data`() {
        val expectedPayload = byteArrayOf(0x00, 0xFF.toByte(), 0x7F, 0x80.toByte(), 0x01, 0xFE.toByte())
        val packet = buildUdpPacket(
            sourcePort = 12345,
            destPort = 80,
            payload = expectedPayload
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        assertNotNull(udpHeader)
        
        val actualPayload = udpHandler.extractUdpPayload(packet, 20, udpHeader!!)
        
        assertArrayEquals(expectedPayload, actualPayload)
    }
    
    // ========== DNS Query Detection Tests ==========
    
    @Test
    fun `detect DNS query on port 53`() {
        val packet = buildUdpPacket(
            sourcePort = 54321,
            destPort = 53, // DNS port
            payload = buildDnsQuery("example.com")
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        assertNotNull(udpHeader)
        
        val isDns = udpHandler.isDnsQuery(udpHeader!!)
        
        assertTrue("Should detect DNS query on port 53", isDns)
    }
    
    @Test
    fun `do not detect DNS query on non-53 port`() {
        val packet = buildUdpPacket(
            sourcePort = 54321,
            destPort = 80, // HTTP port, not DNS
            payload = "GET / HTTP/1.1".toByteArray()
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        assertNotNull(udpHeader)
        
        val isDns = udpHandler.isDnsQuery(udpHeader!!)
        
        assertFalse("Should not detect DNS query on port 80", isDns)
    }
    
    @Test
    fun `detect DNS query regardless of source port`() {
        // DNS queries can come from any source port
        val sourcePorts = listOf(1024, 5353, 12345, 54321, 65535)
        
        sourcePorts.forEach { sourcePort ->
            val packet = buildUdpPacket(
                sourcePort = sourcePort,
                destPort = 53,
                payload = buildDnsQuery("test.com")
            )
            
            val udpHeader = udpHandler.parseUdpHeader(packet, 20)
            assertNotNull(udpHeader)
            
            val isDns = udpHandler.isDnsQuery(udpHeader!!)
            
            assertTrue("Should detect DNS query from source port $sourcePort", isDns)
        }
    }
    
    @Test
    fun `detect DNS query with empty payload`() {
        // Even with empty payload, if dest port is 53, it's a DNS query
        val packet = buildUdpPacket(
            sourcePort = 54321,
            destPort = 53,
            payload = ByteArray(0)
        )
        
        val udpHeader = udpHandler.parseUdpHeader(packet, 20)
        assertNotNull(udpHeader)
        
        val isDns = udpHandler.isDnsQuery(udpHeader!!)
        
        assertTrue("Should detect DNS query even with empty payload", isDns)
    }
    
    @Test
    fun `do not detect DNS query on common non-DNS ports`() {
        val nonDnsPorts = listOf(80, 443, 22, 21, 25, 110, 143, 3306, 5432, 6379, 8080)
        
        nonDnsPorts.forEach { port ->
            val packet = buildUdpPacket(
                sourcePort = 12345,
                destPort = port,
                payload = "test data".toByteArray()
            )
            
            val udpHeader = udpHandler.parseUdpHeader(packet, 20)
            assertNotNull(udpHeader)
            
            val isDns = udpHandler.isDnsQuery(udpHeader!!)
            
            assertFalse("Should not detect DNS query on port $port", isDns)
        }
    }
    
    // ========== Helper Functions ==========
    
    /**
     * Helper function to build a UDP packet for testing.
     * Creates an IP header + UDP header + payload.
     */
    private fun buildUdpPacket(
        sourcePort: Int,
        destPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpHeaderSize = 8
        val ipHeaderSize = 20
        val udpLength = udpHeaderSize + payload.size
        val totalSize = ipHeaderSize + udpLength
        
        val buffer = ByteBuffer.allocate(totalSize)
        
        // Build minimal IP header (20 bytes)
        buffer.put((0x45).toByte()) // Version 4, IHL 5 (20 bytes)
        buffer.put(0) // TOS
        buffer.putShort(totalSize.toShort()) // Total length
        buffer.putShort(0) // Identification
        buffer.putShort(0) // Flags and fragment offset
        buffer.put(64) // TTL
        buffer.put(17) // Protocol (UDP)
        buffer.putShort(0) // Checksum (not validated in this test)
        buffer.putInt(0x0A000002) // Source IP: 10.0.0.2
        buffer.putInt(0x08080808) // Dest IP: 8.8.8.8
        
        // Build UDP header (8 bytes)
        buffer.putShort(sourcePort.toShort()) // Source port
        buffer.putShort(destPort.toShort()) // Dest port
        buffer.putShort(udpLength.toShort()) // Length (header + payload)
        buffer.putShort(0) // Checksum (not validated in this test)
        
        // Add payload
        buffer.put(payload)
        
        return buffer.array()
    }
    
    /**
     * Helper function to build a minimal DNS query for testing.
     * This creates a simplified DNS query structure.
     */
    private fun buildDnsQuery(domain: String): ByteArray {
        // Simplified DNS query structure
        // In reality, DNS queries are more complex, but this is sufficient for testing
        val buffer = ByteBuffer.allocate(512)
        
        // DNS header (12 bytes)
        buffer.putShort(0x1234) // Transaction ID
        buffer.putShort(0x0100) // Flags: standard query
        buffer.putShort(1) // Questions: 1
        buffer.putShort(0) // Answer RRs: 0
        buffer.putShort(0) // Authority RRs: 0
        buffer.putShort(0) // Additional RRs: 0
        
        // Question section
        // Domain name (encoded as labels)
        domain.split('.').forEach { label ->
            buffer.put(label.length.toByte())
            buffer.put(label.toByteArray())
        }
        buffer.put(0) // End of domain name
        
        buffer.putShort(1) // Type: A record
        buffer.putShort(1) // Class: IN (Internet)
        
        return buffer.array().copyOf(buffer.position())
    }
}
