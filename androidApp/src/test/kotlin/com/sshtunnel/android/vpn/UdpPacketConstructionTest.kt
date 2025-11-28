package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPPacketParser
import com.sshtunnel.android.vpn.packet.PacketBuilder
import com.sshtunnel.android.vpn.packet.Protocol
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Unit tests for UDP packet construction.
 * 
 * Tests UDP response packet structure, checksum calculation, and DNS response packets.
 * 
 * Requirements: 6.5, 8.3
 */
class UdpPacketConstructionTest {
    
    private lateinit var packetBuilder: PacketBuilder
    
    @Before
    fun setup() {
        packetBuilder = PacketBuilder()
    }
    
    // ========== UDP Response Packet Structure Tests ==========
    
    @Test
    fun `build UDP packet with correct structure`() {
        val sourceIp = "8.8.8.8"
        val sourcePort = 53
        val destIp = "10.0.0.2"
        val destPort = 54321
        val payload = "DNS response data".toByteArray()
        
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = sourceIp,
            sourcePort = sourcePort,
            destIp = destIp,
            destPort = destPort,
            payload = payload
        )
        
        // Verify packet size: IP header (20) + UDP header (8) + payload
        val expectedSize = 20 + 8 + payload.size
        assertEquals("Packet size should be IP + UDP headers + payload", expectedSize, packet.size)
        
        // Parse IP header
        val ipHeader = IPPacketParser.parseIPv4Header(packet)
        assertNotNull("IP header should be valid", ipHeader)
        assertEquals("IP version should be 4", 4, ipHeader!!.version)
        assertEquals("Protocol should be UDP", Protocol.UDP, ipHeader.protocol)
        assertEquals("Source IP should match", sourceIp, ipHeader.sourceIP)
        assertEquals("Destination IP should match", destIp, ipHeader.destIP)
        assertEquals("Total length should match", expectedSize, ipHeader.totalLength)
        
        // Parse UDP header (starts at byte 20)
        val udpStart = 20
        val actualSourcePort = ((packet[udpStart].toInt() and 0xFF) shl 8) or
                              (packet[udpStart + 1].toInt() and 0xFF)
        val actualDestPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                            (packet[udpStart + 3].toInt() and 0xFF)
        val udpLength = ((packet[udpStart + 4].toInt() and 0xFF) shl 8) or
                       (packet[udpStart + 5].toInt() and 0xFF)
        
        assertEquals("UDP source port should match", sourcePort, actualSourcePort)
        assertEquals("UDP destination port should match", destPort, actualDestPort)
        assertEquals("UDP length should be header + payload", 8 + payload.size, udpLength)
        
        // Verify payload
        val actualPayload = packet.copyOfRange(28, packet.size)
        assertArrayEquals("Payload should match", payload, actualPayload)
    }
    
    @Test
    fun `build UDP packet with swapped source and destination`() {
        // Simulate response packet where source and dest are swapped
        val originalSourceIp = "10.0.0.2"
        val originalSourcePort = 54321
        val originalDestIp = "8.8.8.8"
        val originalDestPort = 53
        
        // For response, swap source and dest
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = originalDestIp,      // DNS server becomes source
            sourcePort = originalDestPort,  // Port 53
            destIp = originalSourceIp,      // Original source becomes dest
            destPort = originalSourcePort,  // Original source port
            payload = "response".toByteArray()
        )
        
        val ipHeader = IPPacketParser.parseIPv4Header(packet)
        assertNotNull(ipHeader)
        
        // Verify IPs are swapped correctly
        assertEquals("Response source should be original dest", originalDestIp, ipHeader!!.sourceIP)
        assertEquals("Response dest should be original source", originalSourceIp, ipHeader.destIP)
        
        // Verify ports are swapped correctly
        val udpStart = 20
        val actualSourcePort = ((packet[udpStart].toInt() and 0xFF) shl 8) or
                              (packet[udpStart + 1].toInt() and 0xFF)
        val actualDestPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                            (packet[udpStart + 3].toInt() and 0xFF)
        
        assertEquals("Response source port should be original dest port", originalDestPort, actualSourcePort)
        assertEquals("Response dest port should be original source port", originalSourcePort, actualDestPort)
    }
    
    @Test
    fun `build UDP packet with empty payload`() {
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 53,
            destIp = "10.0.0.2",
            destPort = 54321,
            payload = ByteArray(0)
        )
        
        // Verify packet size: IP header (20) + UDP header (8) + no payload
        assertEquals("Packet size should be IP + UDP headers only", 28, packet.size)
        
        // Parse UDP header
        val udpStart = 20
        val udpLength = ((packet[udpStart + 4].toInt() and 0xFF) shl 8) or
                       (packet[udpStart + 5].toInt() and 0xFF)
        
        assertEquals("UDP length should be header only", 8, udpLength)
    }
    
    @Test
    fun `build UDP packet with large payload`() {
        val largePayload = ByteArray(1400) { (it % 256).toByte() }
        
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 53,
            destIp = "10.0.0.2",
            destPort = 54321,
            payload = largePayload
        )
        
        // Verify packet size
        val expectedSize = 20 + 8 + 1400
        assertEquals("Packet size should accommodate large payload", expectedSize, packet.size)
        
        // Verify payload integrity
        val actualPayload = packet.copyOfRange(28, packet.size)
        assertArrayEquals("Large payload should be intact", largePayload, actualPayload)
    }
    
    @Test
    fun `build UDP packet with binary payload`() {
        val binaryPayload = byteArrayOf(
            0x00, 0xFF.toByte(), 0x7F, 0x80.toByte(),
            0x01, 0xFE.toByte(), 0xAA.toByte(), 0x55
        )
        
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 53,
            destIp = "10.0.0.2",
            destPort = 54321,
            payload = binaryPayload
        )
        
        // Verify binary payload is preserved
        val actualPayload = packet.copyOfRange(28, packet.size)
        assertArrayEquals("Binary payload should be preserved", binaryPayload, actualPayload)
    }
    
    @Test
    fun `build UDP packet with maximum port numbers`() {
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 65535,
            destIp = "10.0.0.2",
            destPort = 65535,
            payload = "test".toByteArray()
        )
        
        val udpStart = 20
        val actualSourcePort = ((packet[udpStart].toInt() and 0xFF) shl 8) or
                              (packet[udpStart + 1].toInt() and 0xFF)
        val actualDestPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                            (packet[udpStart + 3].toInt() and 0xFF)
        
        assertEquals("Maximum source port should be preserved", 65535, actualSourcePort)
        assertEquals("Maximum dest port should be preserved", 65535, actualDestPort)
    }
    
    @Test
    fun `build UDP packet with minimum port numbers`() {
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 0,
            destIp = "10.0.0.2",
            destPort = 0,
            payload = "test".toByteArray()
        )
        
        val udpStart = 20
        val actualSourcePort = ((packet[udpStart].toInt() and 0xFF) shl 8) or
                              (packet[udpStart + 1].toInt() and 0xFF)
        val actualDestPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                            (packet[udpStart + 3].toInt() and 0xFF)
        
        assertEquals("Minimum source port should be preserved", 0, actualSourcePort)
        assertEquals("Minimum dest port should be preserved", 0, actualDestPort)
    }
    
    // ========== Checksum Calculation Tests ==========
    
    @Test
    fun `UDP checksum is non-zero`() {
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 53,
            destIp = "10.0.0.2",
            destPort = 54321,
            payload = "test data".toByteArray()
        )
        
        // Extract checksum from UDP header
        val udpStart = 20
        val checksum = ((packet[udpStart + 6].toInt() and 0xFF) shl 8) or
                      (packet[udpStart + 7].toInt() and 0xFF)
        
        assertNotEquals("UDP checksum should be calculated (non-zero)", 0, checksum)
    }
    
    @Test
    fun `UDP checksum calculation is consistent`() {
        val sourceIp = "8.8.8.8"
        val sourcePort = 53
        val destIp = "10.0.0.2"
        val destPort = 54321
        val payload = "consistent test".toByteArray()
        
        // Build same packet twice
        val packet1 = packetBuilder.buildUdpPacket(sourceIp, sourcePort, destIp, destPort, payload)
        val packet2 = packetBuilder.buildUdpPacket(sourceIp, sourcePort, destIp, destPort, payload)
        
        // Extract checksums
        val udpStart = 20
        val checksum1 = ((packet1[udpStart + 6].toInt() and 0xFF) shl 8) or
                       (packet1[udpStart + 7].toInt() and 0xFF)
        val checksum2 = ((packet2[udpStart + 6].toInt() and 0xFF) shl 8) or
                       (packet2[udpStart + 7].toInt() and 0xFF)
        
        assertEquals("Checksum should be consistent for same inputs", checksum1, checksum2)
    }
    
    @Test
    fun `UDP checksum changes with different payload`() {
        val sourceIp = "8.8.8.8"
        val sourcePort = 53
        val destIp = "10.0.0.2"
        val destPort = 54321
        
        val packet1 = packetBuilder.buildUdpPacket(sourceIp, sourcePort, destIp, destPort, "payload1".toByteArray())
        val packet2 = packetBuilder.buildUdpPacket(sourceIp, sourcePort, destIp, destPort, "payload2".toByteArray())
        
        // Extract checksums
        val udpStart = 20
        val checksum1 = ((packet1[udpStart + 6].toInt() and 0xFF) shl 8) or
                       (packet1[udpStart + 7].toInt() and 0xFF)
        val checksum2 = ((packet2[udpStart + 6].toInt() and 0xFF) shl 8) or
                       (packet2[udpStart + 7].toInt() and 0xFF)
        
        assertNotEquals("Checksum should differ for different payloads", checksum1, checksum2)
    }
    
    @Test
    fun `UDP checksum changes with different source IP`() {
        val destIp = "10.0.0.2"
        val destPort = 54321
        val payload = "test".toByteArray()
        
        val packet1 = packetBuilder.buildUdpPacket("8.8.8.8", 53, destIp, destPort, payload)
        val packet2 = packetBuilder.buildUdpPacket("1.1.1.1", 53, destIp, destPort, payload)
        
        // Extract checksums
        val udpStart = 20
        val checksum1 = ((packet1[udpStart + 6].toInt() and 0xFF) shl 8) or
                       (packet1[udpStart + 7].toInt() and 0xFF)
        val checksum2 = ((packet2[udpStart + 6].toInt() and 0xFF) shl 8) or
                       (packet2[udpStart + 7].toInt() and 0xFF)
        
        assertNotEquals("Checksum should differ for different source IPs", checksum1, checksum2)
    }
    
    @Test
    fun `UDP checksum handles odd-length payload`() {
        // Odd-length payload should be handled correctly in checksum calculation
        val oddPayload = "odd".toByteArray() // 3 bytes
        
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 53,
            destIp = "10.0.0.2",
            destPort = 54321,
            payload = oddPayload
        )
        
        // Extract checksum
        val udpStart = 20
        val checksum = ((packet[udpStart + 6].toInt() and 0xFF) shl 8) or
                      (packet[udpStart + 7].toInt() and 0xFF)
        
        assertNotEquals("Checksum should be calculated for odd-length payload", 0, checksum)
    }
    
    // ========== DNS Response Packet Tests ==========
    
    @Test
    fun `build DNS response packet`() {
        // Simulate a DNS response packet
        val dnsResponse = buildDnsResponse("example.com", "93.184.216.34")
        
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",      // DNS server
            sourcePort = 53,            // DNS port
            destIp = "10.0.0.2",        // Client
            destPort = 54321,           // Client port
            payload = dnsResponse
        )
        
        // Verify it's a valid UDP packet
        val ipHeader = IPPacketParser.parseIPv4Header(packet)
        assertNotNull("DNS response should have valid IP header", ipHeader)
        assertEquals("Protocol should be UDP", Protocol.UDP, ipHeader!!.protocol)
        
        // Verify UDP header
        val udpStart = 20
        val sourcePort = ((packet[udpStart].toInt() and 0xFF) shl 8) or
                        (packet[udpStart + 1].toInt() and 0xFF)
        val destPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                      (packet[udpStart + 3].toInt() and 0xFF)
        
        assertEquals("DNS response should come from port 53", 53, sourcePort)
        assertEquals("DNS response should go to client port", 54321, destPort)
        
        // Verify DNS payload is intact
        val actualPayload = packet.copyOfRange(28, packet.size)
        assertArrayEquals("DNS response payload should be intact", dnsResponse, actualPayload)
    }
    
    @Test
    fun `build DNS response packet with multiple answers`() {
        // Simulate DNS response with multiple A records
        val dnsResponse = buildDnsResponseMultiple("example.com", listOf("93.184.216.34", "93.184.216.35"))
        
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 53,
            destIp = "10.0.0.2",
            destPort = 54321,
            payload = dnsResponse
        )
        
        // Verify packet structure
        assertTrue("DNS response with multiple answers should be valid", packet.size > 28)
        
        val actualPayload = packet.copyOfRange(28, packet.size)
        assertArrayEquals("DNS response with multiple answers should be intact", dnsResponse, actualPayload)
    }
    
    @Test
    fun `build DNS response packet with NXDOMAIN`() {
        // Simulate DNS NXDOMAIN response (domain not found)
        val dnsResponse = buildDnsNxDomain("nonexistent.example.com")
        
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 53,
            destIp = "10.0.0.2",
            destPort = 54321,
            payload = dnsResponse
        )
        
        // Verify packet is valid
        val ipHeader = IPPacketParser.parseIPv4Header(packet)
        assertNotNull("NXDOMAIN response should have valid IP header", ipHeader)
        
        val actualPayload = packet.copyOfRange(28, packet.size)
        assertArrayEquals("NXDOMAIN response should be intact", dnsResponse, actualPayload)
    }
    
    @Test
    fun `build large DNS response packet`() {
        // Simulate large DNS response (e.g., with many TXT records)
        val largeDnsResponse = ByteArray(512) { (it % 256).toByte() }
        
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "8.8.8.8",
            sourcePort = 53,
            destIp = "10.0.0.2",
            destPort = 54321,
            payload = largeDnsResponse
        )
        
        // Verify packet can handle large DNS responses
        val expectedSize = 20 + 8 + 512
        assertEquals("Large DNS response should fit in packet", expectedSize, packet.size)
        
        val actualPayload = packet.copyOfRange(28, packet.size)
        assertArrayEquals("Large DNS response should be intact", largeDnsResponse, actualPayload)
    }
    
    // ========== Helper Functions ==========
    
    /**
     * Helper function to build a simplified DNS response for testing.
     */
    private fun buildDnsResponse(domain: String, ipAddress: String): ByteArray {
        val buffer = ByteBuffer.allocate(512)
        
        // DNS header (12 bytes)
        buffer.putShort(0x1234) // Transaction ID
        buffer.putShort(0x8180.toShort()) // Flags: response, no error
        buffer.putShort(1) // Questions: 1
        buffer.putShort(1) // Answer RRs: 1
        buffer.putShort(0) // Authority RRs: 0
        buffer.putShort(0) // Additional RRs: 0
        
        // Question section
        domain.split('.').forEach { label ->
            buffer.put(label.length.toByte())
            buffer.put(label.toByteArray())
        }
        buffer.put(0) // End of domain name
        buffer.putShort(1) // Type: A record
        buffer.putShort(1) // Class: IN
        
        // Answer section
        domain.split('.').forEach { label ->
            buffer.put(label.length.toByte())
            buffer.put(label.toByteArray())
        }
        buffer.put(0) // End of domain name
        buffer.putShort(1) // Type: A record
        buffer.putShort(1) // Class: IN
        buffer.putInt(300) // TTL: 300 seconds
        buffer.putShort(4) // Data length: 4 bytes (IPv4)
        
        // IP address
        ipAddress.split('.').forEach { octet ->
            buffer.put(octet.toInt().toByte())
        }
        
        return buffer.array().copyOf(buffer.position())
    }
    
    /**
     * Helper function to build a DNS response with multiple A records.
     */
    private fun buildDnsResponseMultiple(domain: String, ipAddresses: List<String>): ByteArray {
        val buffer = ByteBuffer.allocate(512)
        
        // DNS header
        buffer.putShort(0x1234) // Transaction ID
        buffer.putShort(0x8180.toShort()) // Flags: response, no error
        buffer.putShort(1) // Questions: 1
        buffer.putShort(ipAddresses.size.toShort()) // Answer RRs: multiple
        buffer.putShort(0) // Authority RRs: 0
        buffer.putShort(0) // Additional RRs: 0
        
        // Question section
        domain.split('.').forEach { label ->
            buffer.put(label.length.toByte())
            buffer.put(label.toByteArray())
        }
        buffer.put(0)
        buffer.putShort(1) // Type: A
        buffer.putShort(1) // Class: IN
        
        // Answer sections (one for each IP)
        ipAddresses.forEach { ipAddress ->
            domain.split('.').forEach { label ->
                buffer.put(label.length.toByte())
                buffer.put(label.toByteArray())
            }
            buffer.put(0)
            buffer.putShort(1) // Type: A
            buffer.putShort(1) // Class: IN
            buffer.putInt(300) // TTL
            buffer.putShort(4) // Data length
            
            ipAddress.split('.').forEach { octet ->
                buffer.put(octet.toInt().toByte())
            }
        }
        
        return buffer.array().copyOf(buffer.position())
    }
    
    /**
     * Helper function to build a DNS NXDOMAIN response.
     */
    private fun buildDnsNxDomain(domain: String): ByteArray {
        val buffer = ByteBuffer.allocate(512)
        
        // DNS header
        buffer.putShort(0x1234) // Transaction ID
        buffer.putShort(0x8183.toShort()) // Flags: response, NXDOMAIN (rcode=3)
        buffer.putShort(1) // Questions: 1
        buffer.putShort(0) // Answer RRs: 0
        buffer.putShort(0) // Authority RRs: 0
        buffer.putShort(0) // Additional RRs: 0
        
        // Question section
        domain.split('.').forEach { label ->
            buffer.put(label.length.toByte())
            buffer.put(label.toByteArray())
        }
        buffer.put(0)
        buffer.putShort(1) // Type: A
        buffer.putShort(1) // Class: IN
        
        return buffer.array().copyOf(buffer.position())
    }
}
