package com.sshtunnel.android.vpn.packet

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

/**
 * Unit tests for IPPacketParser.
 * 
 * Tests IP packet parsing functionality including:
 * - Valid IPv4 header parsing
 * - Invalid IP version rejection
 * - Protocol extraction
 * - IP address extraction
 * - Checksum validation
 */
class IPPacketParserTest {
    
    @Test
    fun `parseIPv4Header should parse valid IPv4 packet`() {
        // Create a minimal valid IPv4 packet
        val packet = buildValidIPv4Packet(
            sourceIp = "192.168.1.1",
            destIp = "8.8.8.8",
            protocol = Protocol.TCP
        )
        
        val header = IPPacketParser.parseIPv4Header(packet)
        
        assertNotNull(header)
        assertEquals(4, header!!.version)
        assertEquals(20, header.headerLength)
        assertEquals(Protocol.TCP, header.protocol)
        assertEquals("192.168.1.1", header.sourceIP)
        assertEquals("8.8.8.8", header.destIP)
    }
    
    @Test
    fun `parseIPv4Header should reject IPv6 packet`() {
        // Create a packet with version 6
        val packet = ByteArray(20)
        packet[0] = 0x65.toByte() // Version 6, IHL 5
        
        val header = IPPacketParser.parseIPv4Header(packet)
        
        assertNull(header)
    }
    
    @Test
    fun `parseIPv4Header should reject packet that is too short`() {
        val packet = ByteArray(10) // Less than minimum 20 bytes
        
        val header = IPPacketParser.parseIPv4Header(packet)
        
        assertNull(header)
    }
    
    @Test
    fun `parseIPv4Header should reject packet with invalid header length`() {
        val packet = ByteArray(20)
        packet[0] = 0x43.toByte() // Version 4, IHL 3 (12 bytes - too short)
        
        val header = IPPacketParser.parseIPv4Header(packet)
        
        assertNull(header)
    }
    
    @Test
    fun `extractProtocol should extract TCP protocol`() {
        val packet = buildValidIPv4Packet(
            sourceIp = "10.0.0.1",
            destIp = "10.0.0.2",
            protocol = Protocol.TCP
        )
        
        val protocol = IPPacketParser.extractProtocol(packet)
        
        assertEquals(Protocol.TCP, protocol)
    }
    
    @Test
    fun `extractProtocol should extract UDP protocol`() {
        val packet = buildValidIPv4Packet(
            sourceIp = "10.0.0.1",
            destIp = "10.0.0.2",
            protocol = Protocol.UDP
        )
        
        val protocol = IPPacketParser.extractProtocol(packet)
        
        assertEquals(Protocol.UDP, protocol)
    }
    
    @Test
    fun `extractProtocol should extract ICMP protocol`() {
        val packet = buildValidIPv4Packet(
            sourceIp = "10.0.0.1",
            destIp = "10.0.0.2",
            protocol = Protocol.ICMP
        )
        
        val protocol = IPPacketParser.extractProtocol(packet)
        
        assertEquals(Protocol.ICMP, protocol)
    }
    
    @Test
    fun `extractProtocol should return UNKNOWN for unsupported protocol`() {
        val packet = ByteArray(20)
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[9] = 99.toByte() // Unknown protocol
        
        val protocol = IPPacketParser.extractProtocol(packet)
        
        assertEquals(Protocol.UNKNOWN, protocol)
    }
    
    @Test
    fun `extractProtocol should return UNKNOWN for short packet`() {
        val packet = ByteArray(5)
        
        val protocol = IPPacketParser.extractProtocol(packet)
        
        assertEquals(Protocol.UNKNOWN, protocol)
    }
    
    @Test
    fun `extractSourceIP should extract correct IP address`() {
        val packet = buildValidIPv4Packet(
            sourceIp = "192.168.1.100",
            destIp = "8.8.8.8",
            protocol = Protocol.TCP
        )
        
        val sourceIp = IPPacketParser.extractSourceIP(packet)
        
        assertEquals("192.168.1.100", sourceIp)
    }
    
    @Test
    fun `extractSourceIP should return empty string for short packet`() {
        val packet = ByteArray(10)
        
        val sourceIp = IPPacketParser.extractSourceIP(packet)
        
        assertEquals("", sourceIp)
    }
    
    @Test
    fun `extractDestIP should extract correct IP address`() {
        val packet = buildValidIPv4Packet(
            sourceIp = "10.0.0.1",
            destIp = "1.1.1.1",
            protocol = Protocol.UDP
        )
        
        val destIp = IPPacketParser.extractDestIP(packet)
        
        assertEquals("1.1.1.1", destIp)
    }
    
    @Test
    fun `extractDestIP should return empty string for short packet`() {
        val packet = ByteArray(15)
        
        val destIp = IPPacketParser.extractDestIP(packet)
        
        assertEquals("", destIp)
    }
    
    @Test
    fun `getHeaderLength should return correct length for standard header`() {
        val packet = ByteArray(20)
        packet[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
        
        val headerLength = IPPacketParser.getHeaderLength(packet)
        
        assertEquals(20, headerLength)
    }
    
    @Test
    fun `getHeaderLength should return correct length for header with options`() {
        val packet = ByteArray(40)
        packet[0] = 0x4A.toByte() // Version 4, IHL 10 (40 bytes)
        
        val headerLength = IPPacketParser.getHeaderLength(packet)
        
        assertEquals(40, headerLength)
    }
    
    @Test
    fun `getHeaderLength should return 0 for empty packet`() {
        val packet = ByteArray(0)
        
        val headerLength = IPPacketParser.getHeaderLength(packet)
        
        assertEquals(0, headerLength)
    }
    
    @Test
    fun `validateChecksum should return true for valid checksum`() {
        val packet = buildValidIPv4Packet(
            sourceIp = "192.168.1.1",
            destIp = "8.8.8.8",
            protocol = Protocol.TCP
        )
        
        val isValid = IPPacketParser.validateChecksum(packet)
        
        assertTrue(isValid)
    }
    
    @Test
    fun `validateChecksum should return false for invalid checksum`() {
        val packet = buildValidIPv4Packet(
            sourceIp = "192.168.1.1",
            destIp = "8.8.8.8",
            protocol = Protocol.TCP
        )
        
        // Corrupt the checksum
        packet[10] = 0xFF.toByte()
        packet[11] = 0xFF.toByte()
        
        val isValid = IPPacketParser.validateChecksum(packet)
        
        assertFalse(isValid)
    }
    
    @Test
    fun `validateChecksum should return false for short packet`() {
        val packet = ByteArray(10)
        
        val isValid = IPPacketParser.validateChecksum(packet)
        
        assertFalse(isValid)
    }
    
    @Test
    fun `Protocol fromValue should return correct protocol`() {
        assertEquals(Protocol.TCP, Protocol.fromValue(6))
        assertEquals(Protocol.UDP, Protocol.fromValue(17))
        assertEquals(Protocol.ICMP, Protocol.fromValue(1))
        assertEquals(Protocol.UNKNOWN, Protocol.fromValue(99))
    }
    
    // Helper function to build a valid IPv4 packet with correct checksum
    private fun buildValidIPv4Packet(
        sourceIp: String,
        destIp: String,
        protocol: Protocol,
        totalLength: Int = 20
    ): ByteArray {
        val packet = ByteArray(totalLength)
        
        // Version 4, IHL 5 (20 bytes)
        packet[0] = 0x45.toByte()
        
        // Type of Service
        packet[1] = 0x00
        
        // Total Length
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        
        // Identification
        packet[4] = 0x00
        packet[5] = 0x01
        
        // Flags and Fragment Offset
        packet[6] = 0x40.toByte() // Don't Fragment flag
        packet[7] = 0x00
        
        // TTL
        packet[8] = 64.toByte()
        
        // Protocol
        packet[9] = protocol.value.toByte()
        
        // Checksum (will be calculated)
        packet[10] = 0x00
        packet[11] = 0x00
        
        // Source IP
        val sourceIpBytes = InetAddress.getByName(sourceIp).address
        System.arraycopy(sourceIpBytes, 0, packet, 12, 4)
        
        // Destination IP
        val destIpBytes = InetAddress.getByName(destIp).address
        System.arraycopy(destIpBytes, 0, packet, 16, 4)
        
        // Calculate and set checksum
        val checksum = calculateIPChecksum(packet)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()
        
        return packet
    }
    
    private fun calculateIPChecksum(packet: ByteArray): Int {
        var sum = 0L
        var i = 0
        
        // Sum all 16-bit words in the header, skipping checksum field
        while (i < 20) {
            if (i != 10) { // Skip checksum field (bytes 10-11)
                val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
                sum += word
            }
            i += 2
        }
        
        // Fold 32-bit sum to 16 bits
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        
        // One's complement
        return (sum.inv() and 0xFFFF).toInt()
    }
}
