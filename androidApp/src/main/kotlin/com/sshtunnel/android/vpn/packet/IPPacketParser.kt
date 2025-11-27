package com.sshtunnel.android.vpn.packet

import java.net.InetAddress

/**
 * Parses IP packets from the TUN interface.
 * 
 * This object provides utilities for parsing IPv4 packet headers and extracting
 * protocol information, IP addresses, and validating packet structure.
 */
object IPPacketParser {
    
    /**
     * Parses an IPv4 packet header.
     * 
     * @param packet The raw packet bytes
     * @return IPv4Header if valid IPv4 packet, null otherwise
     */
    fun parseIPv4Header(packet: ByteArray): IPv4Header? {
        if (packet.size < 20) {
            return null // Minimum IPv4 header size
        }
        
        // Extract version (first 4 bits)
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) {
            return null // Not IPv4
        }
        
        // Extract header length (IHL - next 4 bits, in 32-bit words)
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || headerLength > packet.size) {
            return null // Invalid header length
        }
        
        // Extract total length (bytes 2-3)
        val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (totalLength > packet.size) {
            return null // Packet truncated
        }
        
        // Extract identification (bytes 4-5)
        val identification = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        
        // Extract flags and fragment offset (bytes 6-7)
        val flagsAndOffset = ((packet[6].toInt() and 0xFF) shl 8) or (packet[7].toInt() and 0xFF)
        val flags = (flagsAndOffset shr 13) and 0x07
        val fragmentOffset = flagsAndOffset and 0x1FFF
        
        // Extract TTL (byte 8)
        val ttl = packet[8].toInt() and 0xFF
        
        // Extract protocol (byte 9)
        val protocolValue = packet[9].toInt() and 0xFF
        val protocol = Protocol.fromValue(protocolValue)
        
        // Extract checksum (bytes 10-11)
        val checksum = ((packet[10].toInt() and 0xFF) shl 8) or (packet[11].toInt() and 0xFF)
        
        // Extract source IP (bytes 12-15)
        val sourceIP = extractSourceIP(packet)
        
        // Extract destination IP (bytes 16-19)
        val destIP = extractDestIP(packet)
        
        return IPv4Header(
            version = version,
            headerLength = headerLength,
            totalLength = totalLength,
            identification = identification,
            flags = flags,
            fragmentOffset = fragmentOffset,
            ttl = ttl,
            protocol = protocol,
            checksum = checksum,
            sourceIP = sourceIP,
            destIP = destIP
        )
    }
    
    /**
     * Extracts the protocol from an IP packet.
     * 
     * @param packet The raw packet bytes
     * @return Protocol enum value
     */
    fun extractProtocol(packet: ByteArray): Protocol {
        if (packet.size < 10) {
            return Protocol.UNKNOWN
        }
        
        val protocolValue = packet[9].toInt() and 0xFF
        return Protocol.fromValue(protocolValue)
    }
    
    /**
     * Extracts the source IP address from an IP packet.
     * 
     * @param packet The raw packet bytes
     * @return Source IP address as string, or empty string if invalid
     */
    fun extractSourceIP(packet: ByteArray): String {
        if (packet.size < 16) {
            return ""
        }
        
        return try {
            InetAddress.getByAddress(packet.copyOfRange(12, 16)).hostAddress ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Extracts the destination IP address from an IP packet.
     * 
     * @param packet The raw packet bytes
     * @return Destination IP address as string, or empty string if invalid
     */
    fun extractDestIP(packet: ByteArray): String {
        if (packet.size < 20) {
            return ""
        }
        
        return try {
            InetAddress.getByAddress(packet.copyOfRange(16, 20)).hostAddress ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Gets the IP header length from a packet.
     * 
     * @param packet The raw packet bytes
     * @return Header length in bytes, or 0 if invalid
     */
    fun getHeaderLength(packet: ByteArray): Int {
        if (packet.isEmpty()) {
            return 0
        }
        
        // IHL is in 32-bit words, so multiply by 4 to get bytes
        return (packet[0].toInt() and 0x0F) * 4
    }
    
    /**
     * Validates the IP header checksum.
     * 
     * @param packet The raw packet bytes
     * @return true if checksum is valid, false otherwise
     */
    fun validateChecksum(packet: ByteArray): Boolean {
        val headerLength = getHeaderLength(packet)
        if (headerLength < 20 || headerLength > packet.size) {
            return false
        }
        
        // Calculate checksum over the header INCLUDING the checksum field
        var sum = 0L
        var i = 0
        
        while (i < headerLength) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        
        // Fold 32-bit sum to 16 bits
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        
        // If checksum is valid, the sum should be 0xFFFF (all ones)
        return (sum and 0xFFFF) == 0xFFFFL
    }
}

/**
 * IP protocol types.
 */
enum class Protocol(val value: Int) {
    TCP(6),
    UDP(17),
    ICMP(1),
    UNKNOWN(-1);
    
    companion object {
        fun fromValue(value: Int): Protocol {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
}

/**
 * Parsed IPv4 packet header.
 */
data class IPv4Header(
    val version: Int,
    val headerLength: Int,
    val totalLength: Int,
    val identification: Int,
    val flags: Int,
    val fragmentOffset: Int,
    val ttl: Int,
    val protocol: Protocol,
    val checksum: Int,
    val sourceIP: String,
    val destIP: String
)
