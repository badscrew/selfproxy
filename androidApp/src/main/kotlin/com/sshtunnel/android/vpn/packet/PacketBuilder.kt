package com.sshtunnel.android.vpn.packet

import com.sshtunnel.android.vpn.TcpFlags
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Builds IP, TCP, and UDP packets for writing to the TUN interface.
 * 
 * This class provides utilities for constructing properly formatted network packets
 * with correct checksums for routing responses back through the VPN tunnel.
 */
class PacketBuilder {
    
    /**
     * Builds a complete IPv4 packet with the given parameters.
     * 
     * @param sourceIp Source IP address (e.g., "10.0.0.2")
     * @param destIp Destination IP address
     * @param protocol Protocol type (TCP, UDP, etc.)
     * @param payload Protocol-specific payload
     * @param identification Packet identification (default: random)
     * @param ttl Time to live (default: 64)
     * @return Complete IPv4 packet as byte array
     */
    fun buildIPv4Packet(
        sourceIp: String,
        destIp: String,
        protocol: Protocol,
        payload: ByteArray,
        identification: Int = Random.nextInt(65536),
        ttl: Int = 64
    ): ByteArray {
        val headerLength = 20 // No options
        val totalLength = headerLength + payload.size
        
        val buffer = ByteBuffer.allocate(totalLength)
        
        // Version (4) and IHL (5 = 20 bytes / 4)
        buffer.put((0x45).toByte())
        
        // Type of Service (0)
        buffer.put(0)
        
        // Total Length
        buffer.putShort(totalLength.toShort())
        
        // Identification
        buffer.putShort(identification.toShort())
        
        // Flags (0) and Fragment Offset (0)
        buffer.putShort(0)
        
        // TTL
        buffer.put(ttl.toByte())
        
        // Protocol
        buffer.put(protocol.value.toByte())
        
        // Checksum (placeholder, will be calculated)
        val checksumPosition = buffer.position()
        buffer.putShort(0)
        
        // Source IP
        buffer.put(InetAddress.getByName(sourceIp).address)
        
        // Destination IP
        buffer.put(InetAddress.getByName(destIp).address)
        
        // Calculate and set checksum
        val packet = buffer.array()
        val checksum = calculateIPChecksum(packet.copyOfRange(0, headerLength))
        packet[checksumPosition] = (checksum shr 8).toByte()
        packet[checksumPosition + 1] = (checksum and 0xFF).toByte()
        
        // Add payload
        System.arraycopy(payload, 0, packet, headerLength, payload.size)
        
        return packet
    }
    
    /**
     * Builds a TCP packet with the given parameters.
     * 
     * @param sourceIp Source IP address
     * @param sourcePort Source port
     * @param destIp Destination IP address
     * @param destPort Destination port
     * @param sequenceNumber TCP sequence number
     * @param acknowledgmentNumber TCP acknowledgment number
     * @param flags TCP flags
     * @param windowSize TCP window size (default: 65535)
     * @param payload TCP payload data (default: empty)
     * @return Complete TCP packet (IP + TCP headers + payload)
     */
    fun buildTcpPacket(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        sequenceNumber: Long,
        acknowledgmentNumber: Long,
        flags: TcpFlags,
        windowSize: Int = 65535,
        payload: ByteArray = byteArrayOf()
    ): ByteArray {
        val tcpHeaderLength = 20 // No options
        val tcpSegmentLength = tcpHeaderLength + payload.size
        
        val tcpBuffer = ByteBuffer.allocate(tcpSegmentLength)
        
        // Source Port
        tcpBuffer.putShort(sourcePort.toShort())
        
        // Destination Port
        tcpBuffer.putShort(destPort.toShort())
        
        // Sequence Number
        tcpBuffer.putInt(sequenceNumber.toInt())
        
        // Acknowledgment Number
        tcpBuffer.putInt(acknowledgmentNumber.toInt())
        
        // Data Offset (5 = 20 bytes / 4) and Reserved (0)
        tcpBuffer.put((0x50).toByte())
        
        // Flags
        tcpBuffer.put(flags.toByte().toByte())
        
        // Window Size
        tcpBuffer.putShort(windowSize.toShort())
        
        // Checksum (placeholder, will be calculated)
        val checksumPosition = tcpBuffer.position()
        tcpBuffer.putShort(0)
        
        // Urgent Pointer
        tcpBuffer.putShort(0)
        
        // Payload
        tcpBuffer.put(payload)
        
        val tcpSegment = tcpBuffer.array()
        
        // Calculate TCP checksum with pseudo-header
        val checksum = calculateTcpChecksum(sourceIp, destIp, tcpSegment)
        tcpSegment[checksumPosition] = (checksum shr 8).toByte()
        tcpSegment[checksumPosition + 1] = (checksum and 0xFF).toByte()
        
        // Build complete IP packet with TCP segment as payload
        return buildIPv4Packet(
            sourceIp = sourceIp,
            destIp = destIp,
            protocol = Protocol.TCP,
            payload = tcpSegment
        )
    }
    
    /**
     * Builds a UDP packet with the given parameters.
     * 
     * @param sourceIp Source IP address
     * @param sourcePort Source port
     * @param destIp Destination IP address
     * @param destPort Destination port
     * @param payload UDP payload data
     * @return Complete UDP packet (IP + UDP headers + payload)
     */
    fun buildUdpPacket(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpHeaderLength = 8
        val udpDatagramLength = udpHeaderLength + payload.size
        
        val udpBuffer = ByteBuffer.allocate(udpDatagramLength)
        
        // Source Port
        udpBuffer.putShort(sourcePort.toShort())
        
        // Destination Port
        udpBuffer.putShort(destPort.toShort())
        
        // Length
        udpBuffer.putShort(udpDatagramLength.toShort())
        
        // Checksum (placeholder, will be calculated)
        val checksumPosition = udpBuffer.position()
        udpBuffer.putShort(0)
        
        // Payload
        udpBuffer.put(payload)
        
        val udpDatagram = udpBuffer.array()
        
        // Calculate UDP checksum with pseudo-header
        val checksum = calculateUdpChecksum(sourceIp, destIp, udpDatagram)
        udpDatagram[checksumPosition] = (checksum shr 8).toByte()
        udpDatagram[checksumPosition + 1] = (checksum and 0xFF).toByte()
        
        // Build complete IP packet with UDP datagram as payload
        return buildIPv4Packet(
            sourceIp = sourceIp,
            destIp = destIp,
            protocol = Protocol.UDP,
            payload = udpDatagram
        )
    }
    
    /**
     * Calculates the IP header checksum using the Internet Checksum algorithm (RFC 1071).
     * 
     * @param header IP header bytes (typically 20 bytes)
     * @return 16-bit checksum value
     */
    fun calculateIPChecksum(header: ByteArray): Int {
        return calculateChecksum(header, 0, header.size)
    }
    
    /**
     * Calculates the TCP checksum including the pseudo-header.
     * 
     * The pseudo-header consists of:
     * - Source IP (4 bytes)
     * - Destination IP (4 bytes)
     * - Zero (1 byte)
     * - Protocol (1 byte = 6 for TCP)
     * - TCP Length (2 bytes)
     * 
     * @param sourceIp Source IP address
     * @param destIp Destination IP address
     * @param tcpSegment Complete TCP segment (header + payload)
     * @return 16-bit checksum value
     */
    fun calculateTcpChecksum(
        sourceIp: String,
        destIp: String,
        tcpSegment: ByteArray
    ): Int {
        val pseudoHeader = ByteBuffer.allocate(12)
        
        // Source IP
        pseudoHeader.put(InetAddress.getByName(sourceIp).address)
        
        // Destination IP
        pseudoHeader.put(InetAddress.getByName(destIp).address)
        
        // Zero
        pseudoHeader.put(0)
        
        // Protocol (TCP = 6)
        pseudoHeader.put(Protocol.TCP.value.toByte())
        
        // TCP Length
        pseudoHeader.putShort(tcpSegment.size.toShort())
        
        // Combine pseudo-header and TCP segment
        val combined = pseudoHeader.array() + tcpSegment
        
        return calculateChecksum(combined, 0, combined.size)
    }
    
    /**
     * Calculates the UDP checksum including the pseudo-header.
     * 
     * The pseudo-header consists of:
     * - Source IP (4 bytes)
     * - Destination IP (4 bytes)
     * - Zero (1 byte)
     * - Protocol (1 byte = 17 for UDP)
     * - UDP Length (2 bytes)
     * 
     * @param sourceIp Source IP address
     * @param destIp Destination IP address
     * @param udpDatagram Complete UDP datagram (header + payload)
     * @return 16-bit checksum value
     */
    fun calculateUdpChecksum(
        sourceIp: String,
        destIp: String,
        udpDatagram: ByteArray
    ): Int {
        val pseudoHeader = ByteBuffer.allocate(12)
        
        // Source IP
        pseudoHeader.put(InetAddress.getByName(sourceIp).address)
        
        // Destination IP
        pseudoHeader.put(InetAddress.getByName(destIp).address)
        
        // Zero
        pseudoHeader.put(0)
        
        // Protocol (UDP = 17)
        pseudoHeader.put(Protocol.UDP.value.toByte())
        
        // UDP Length
        pseudoHeader.putShort(udpDatagram.size.toShort())
        
        // Combine pseudo-header and UDP datagram
        val combined = pseudoHeader.array() + udpDatagram
        
        return calculateChecksum(combined, 0, combined.size)
    }
    
    /**
     * Calculates the Internet Checksum (RFC 1071) for the given data.
     * 
     * This is the one's complement of the one's complement sum of all 16-bit words.
     * Handles odd-length data by padding with a zero byte.
     * 
     * @param data Data to checksum
     * @param offset Starting offset in data
     * @param length Number of bytes to checksum
     * @return 16-bit checksum value
     */
    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        
        // Sum all 16-bit words
        while (i < offset + length - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        
        // Add remaining byte if odd length (pad with zero)
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        
        // Fold 32-bit sum to 16 bits
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        
        // One's complement
        return (sum.inv() and 0xFFFF).toInt()
    }
}


