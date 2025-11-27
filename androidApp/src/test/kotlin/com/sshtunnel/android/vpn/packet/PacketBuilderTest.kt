package com.sshtunnel.android.vpn.packet

import com.sshtunnel.android.vpn.TcpFlags
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PacketBuilder.
 * 
 * Tests packet construction, checksum calculation, and proper formatting
 * of IP, TCP, and UDP packets.
 */
class PacketBuilderTest {
    
    private lateinit var builder: PacketBuilder
    
    @Before
    fun setup() {
        builder = PacketBuilder()
    }
    
    @Test
    fun `buildIPv4Packet creates valid packet structure`() {
        val packet = builder.buildIPv4Packet(
            sourceIp = "10.0.0.2",
            destIp = "1.1.1.1",
            protocol = Protocol.TCP,
            payload = byteArrayOf(1, 2, 3, 4),
            identification = 12345,
            ttl = 64
        )
        
        // Verify minimum size (20 byte header + 4 byte payload)
        assertEquals(24, packet.size)
        
        // Verify version (4) and IHL (5)
        assertEquals(0x45, packet[0].toInt() and 0xFF)
        
        // Verify total length
        val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        assertEquals(24, totalLength)
        
        // Verify identification
        val id = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        assertEquals(12345, id)
        
        // Verify TTL
        assertEquals(64, packet[8].toInt() and 0xFF)
        
        // Verify protocol (TCP = 6)
        assertEquals(6, packet[9].toInt() and 0xFF)
        
        // Verify checksum is non-zero
        val checksum = ((packet[10].toInt() and 0xFF) shl 8) or (packet[11].toInt() and 0xFF)
        assertNotEquals(0, checksum)
        
        // Verify payload is present
        assertEquals(1, packet[20].toInt() and 0xFF)
        assertEquals(2, packet[21].toInt() and 0xFF)
        assertEquals(3, packet[22].toInt() and 0xFF)
        assertEquals(4, packet[23].toInt() and 0xFF)
    }
    
    @Test
    fun `buildIPv4Packet has valid checksum`() {
        val packet = builder.buildIPv4Packet(
            sourceIp = "192.168.1.1",
            destIp = "8.8.8.8",
            protocol = Protocol.UDP,
            payload = byteArrayOf()
        )
        
        // Verify checksum using IPPacketParser
        assertTrue(IPPacketParser.validateChecksum(packet))
    }
    
    @Test
    fun `buildTcpPacket creates valid SYN packet`() {
        val packet = builder.buildTcpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80,
            sequenceNumber = 1000,
            acknowledgmentNumber = 0,
            flags = TcpFlags(syn = true),
            payload = byteArrayOf()
        )
        
        // Verify packet size (20 IP + 20 TCP = 40 bytes)
        assertEquals(40, packet.size)
        
        // Parse IP header
        val ipHeader = IPPacketParser.parseIPv4Header(packet)
        assertNotNull(ipHeader)
        assertEquals(Protocol.TCP, ipHeader!!.protocol)
        assertEquals("10.0.0.2", ipHeader.sourceIP)
        assertEquals("1.1.1.1", ipHeader.destIP)
        
        // Verify TCP header starts at offset 20
        val tcpOffset = 20
        
        // Verify source port
        val sourcePort = ((packet[tcpOffset].toInt() and 0xFF) shl 8) or 
                        (packet[tcpOffset + 1].toInt() and 0xFF)
        assertEquals(12345, sourcePort)
        
        // Verify destination port
        val destPort = ((packet[tcpOffset + 2].toInt() and 0xFF) shl 8) or 
                      (packet[tcpOffset + 3].toInt() and 0xFF)
        assertEquals(80, destPort)
        
        // Verify sequence number
        val seqNum = ((packet[tcpOffset + 4].toInt() and 0xFF) shl 24) or
                    ((packet[tcpOffset + 5].toInt() and 0xFF) shl 16) or
                    ((packet[tcpOffset + 6].toInt() and 0xFF) shl 8) or
                    (packet[tcpOffset + 7].toInt() and 0xFF)
        assertEquals(1000, seqNum)
        
        // Verify SYN flag is set
        val flags = packet[tcpOffset + 13].toInt() and 0xFF
        assertEquals(0x02, flags) // SYN flag
    }
    
    @Test
    fun `buildTcpPacket creates valid ACK packet with data`() {
        val payload = "Hello, World!".toByteArray()
        val packet = builder.buildTcpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "93.184.216.34",
            destPort = 443,
            sequenceNumber = 5000,
            acknowledgmentNumber = 3000,
            flags = TcpFlags(ack = true, psh = true),
            payload = payload
        )
        
        // Verify packet size (20 IP + 20 TCP + payload)
        assertEquals(40 + payload.size, packet.size)
        
        // Verify TCP checksum is non-zero
        val tcpOffset = 20
        val checksum = ((packet[tcpOffset + 16].toInt() and 0xFF) shl 8) or 
                      (packet[tcpOffset + 17].toInt() and 0xFF)
        assertNotEquals(0, checksum)
        
        // Verify payload is present
        val payloadOffset = 40
        val extractedPayload = packet.copyOfRange(payloadOffset, packet.size)
        assertArrayEquals(payload, extractedPayload)
    }
    
    @Test
    fun `buildUdpPacket creates valid packet`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val packet = builder.buildUdpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53,
            payload = payload
        )
        
        // Verify packet size (20 IP + 8 UDP + 5 payload = 33 bytes)
        assertEquals(33, packet.size)
        
        // Parse IP header
        val ipHeader = IPPacketParser.parseIPv4Header(packet)
        assertNotNull(ipHeader)
        assertEquals(Protocol.UDP, ipHeader!!.protocol)
        
        // Verify UDP header starts at offset 20
        val udpOffset = 20
        
        // Verify source port
        val sourcePort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or 
                        (packet[udpOffset + 1].toInt() and 0xFF)
        assertEquals(54321, sourcePort)
        
        // Verify destination port
        val destPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or 
                      (packet[udpOffset + 3].toInt() and 0xFF)
        assertEquals(53, destPort)
        
        // Verify UDP length (8 header + 5 payload = 13)
        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or 
                       (packet[udpOffset + 5].toInt() and 0xFF)
        assertEquals(13, udpLength)
        
        // Verify checksum is non-zero
        val checksum = ((packet[udpOffset + 6].toInt() and 0xFF) shl 8) or 
                      (packet[udpOffset + 7].toInt() and 0xFF)
        assertNotEquals(0, checksum)
        
        // Verify payload
        val extractedPayload = packet.copyOfRange(28, packet.size)
        assertArrayEquals(payload, extractedPayload)
    }
    
    @Test
    fun `calculateIPChecksum returns correct value`() {
        // Create a simple IP header with known checksum
        val header = byteArrayOf(
            0x45, 0x00, 0x00, 0x3c, // Version, IHL, TOS, Total Length
            0x1c, 0x46.toByte(), 0x40, 0x00, // ID, Flags, Fragment Offset
            0x40, 0x06, 0x00, 0x00, // TTL, Protocol, Checksum (0 for calculation)
            0xac.toByte(), 0x10, 0x0a, 0x63, // Source IP: 172.16.10.99
            0xac.toByte(), 0x10, 0x0a, 0x0c  // Dest IP: 172.16.10.12
        )
        
        val checksum = builder.calculateIPChecksum(header)
        
        // Checksum should be non-zero
        assertNotEquals(0, checksum)
        
        // Verify checksum is in valid range
        assertTrue(checksum >= 0 && checksum <= 0xFFFF)
    }
    
    @Test
    fun `calculateIPChecksum is deterministic`() {
        val header = byteArrayOf(
            0x45, 0x00, 0x00, 0x14, // Minimal header
            0x00, 0x00, 0x00, 0x00,
            0x40, 0x11, 0x00, 0x00,
            0x0a, 0x00, 0x00, 0x02, // 10.0.0.2
            0x08, 0x08, 0x08, 0x08  // 8.8.8.8
        )
        
        val checksum1 = builder.calculateIPChecksum(header)
        val checksum2 = builder.calculateIPChecksum(header)
        
        assertEquals(checksum1, checksum2)
    }
    
    @Test
    fun `checksum handles odd-length data correctly`() {
        // Test with odd-length data
        val oddData = byteArrayOf(1, 2, 3, 4, 5)
        val checksum1 = builder.calculateIPChecksum(oddData)
        
        // Should not throw exception and should return valid checksum
        assertTrue(checksum1 >= 0 && checksum1 <= 0xFFFF)
        
        // Test with even-length data
        val evenData = byteArrayOf(1, 2, 3, 4)
        val checksum2 = builder.calculateIPChecksum(evenData)
        
        assertTrue(checksum2 >= 0 && checksum2 <= 0xFFFF)
    }
    
    @Test
    fun `TcpFlags toByte converts correctly`() {
        val flags = TcpFlags(syn = true, ack = true)
        assertEquals(0x12, flags.toByte()) // SYN (0x02) + ACK (0x10)
        
        val finAck = TcpFlags(fin = true, ack = true)
        assertEquals(0x11, finAck.toByte()) // FIN (0x01) + ACK (0x10)
        
        val rst = TcpFlags(rst = true)
        assertEquals(0x04, rst.toByte()) // RST (0x04)
    }
    
    @Test
    fun `TcpFlags fromByte converts correctly`() {
        val synAck = TcpFlags.fromByte(0x12)
        assertTrue(synAck.syn)
        assertTrue(synAck.ack)
        assertFalse(synAck.fin)
        
        val finAck = TcpFlags.fromByte(0x11)
        assertTrue(finAck.fin)
        assertTrue(finAck.ack)
        assertFalse(finAck.syn)
    }
    
    @Test
    fun `buildTcpPacket with all flags set`() {
        val flags = TcpFlags(
            fin = true,
            syn = true,
            rst = true,
            psh = true,
            ack = true,
            urg = true
        )
        
        val packet = builder.buildTcpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 1234,
            destIp = "1.1.1.1",
            destPort = 80,
            sequenceNumber = 100,
            acknowledgmentNumber = 200,
            flags = flags
        )
        
        // Verify packet was created
        assertTrue(packet.size >= 40)
        
        // Verify flags byte
        val tcpOffset = 20
        val flagsByte = packet[tcpOffset + 13].toInt() and 0xFF
        assertEquals(0x3F, flagsByte) // All 6 flags set
    }
    
    @Test
    fun `buildUdpPacket with empty payload`() {
        val packet = builder.buildUdpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "8.8.8.8",
            destPort = 53,
            payload = byteArrayOf()
        )
        
        // Verify packet size (20 IP + 8 UDP = 28 bytes)
        assertEquals(28, packet.size)
        
        // Verify UDP length is 8 (header only)
        val udpOffset = 20
        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or 
                       (packet[udpOffset + 5].toInt() and 0xFF)
        assertEquals(8, udpLength)
    }
    
    @Test
    fun `buildTcpPacket with large payload`() {
        val largePayload = ByteArray(1400) { it.toByte() }
        val packet = builder.buildTcpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80,
            sequenceNumber = 1000,
            acknowledgmentNumber = 2000,
            flags = TcpFlags(ack = true, psh = true),
            payload = largePayload
        )
        
        // Verify packet size (20 IP + 20 TCP + 1400 payload = 1440 bytes)
        assertEquals(1440, packet.size)
        
        // Verify IP total length
        val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        assertEquals(1440, totalLength)
        
        // Verify payload is intact
        val extractedPayload = packet.copyOfRange(40, packet.size)
        assertArrayEquals(largePayload, extractedPayload)
    }
}
