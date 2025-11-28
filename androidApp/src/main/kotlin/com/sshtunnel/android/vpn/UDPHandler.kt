package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPv4Header
import com.sshtunnel.logging.Logger
import java.io.FileOutputStream

/**
 * Handles UDP packet processing and routing through SOCKS5.
 * 
 * This handler:
 * - Parses UDP headers from IP packets
 * - Detects DNS queries (port 53)
 * - Routes UDP traffic through SOCKS5
 * - Constructs UDP response packets
 * 
 * Requirements: 6.1, 6.2, 6.3, 6.4
 */
class UDPHandler(
    private val socksPort: Int,
    private val connectionTable: ConnectionTable,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "UDPHandler"
        private const val DNS_PORT = 53
        private const val UDP_HEADER_SIZE = 8
    }
    
    /**
     * Handles a UDP packet from the TUN interface.
     * 
     * @param packet The complete IP packet containing UDP data
     * @param ipHeader The parsed IP header
     * @param tunOutputStream Output stream to write response packets
     */
    suspend fun handleUdpPacket(
        packet: ByteArray,
        ipHeader: IPv4Header,
        tunOutputStream: FileOutputStream
    ) {
        try {
            // Parse UDP header
            val udpHeader = parseUdpHeader(packet, ipHeader.headerLength)
            if (udpHeader == null) {
                logger.verbose(TAG, "Failed to parse UDP header, dropping packet")
                return
            }
            
            logger.verbose(
                TAG,
                "UDP packet: ${ipHeader.sourceIP}:${udpHeader.sourcePort} -> " +
                "${ipHeader.destIP}:${udpHeader.destPort}, length=${udpHeader.length}"
            )
            
            // Extract UDP payload
            val payload = extractUdpPayload(packet, ipHeader.headerLength, udpHeader)
            
            // Check if this is a DNS query
            if (isDnsQuery(udpHeader)) {
                logger.debug(TAG, "DNS query detected, routing through SOCKS5")
                handleDnsQuery(
                    sourceIp = ipHeader.sourceIP,
                    sourcePort = udpHeader.sourcePort,
                    destIp = ipHeader.destIP,
                    destPort = udpHeader.destPort,
                    dnsPayload = payload,
                    tunOutputStream = tunOutputStream
                )
            } else {
                logger.verbose(TAG, "Non-DNS UDP traffic not yet supported, dropping")
                // TODO: Implement SOCKS5 UDP ASSOCIATE for non-DNS traffic (post-MVP)
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Error handling UDP packet: ${e.message}", e)
        }
    }
    
    /**
     * Parses a UDP header from an IP packet.
     * 
     * UDP header structure (8 bytes):
     * - Bytes 0-1: Source Port
     * - Bytes 2-3: Destination Port
     * - Bytes 4-5: Length (header + data)
     * - Bytes 6-7: Checksum
     * 
     * @param packet The complete IP packet
     * @param ipHeaderLength The length of the IP header in bytes
     * @return Parsed UdpHeader or null if invalid
     * 
     * Requirements: 6.1
     */
    fun parseUdpHeader(packet: ByteArray, ipHeaderLength: Int): UdpHeader? {
        val udpStart = ipHeaderLength
        
        // Check if packet has enough data for UDP header
        if (packet.size < udpStart + UDP_HEADER_SIZE) {
            logger.verbose(TAG, "Packet too short for UDP header")
            return null
        }
        
        try {
            // Extract source port (bytes 0-1)
            val sourcePort = ((packet[udpStart].toInt() and 0xFF) shl 8) or
                            (packet[udpStart + 1].toInt() and 0xFF)
            
            // Extract destination port (bytes 2-3)
            val destPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                          (packet[udpStart + 3].toInt() and 0xFF)
            
            // Extract length (bytes 4-5)
            val length = ((packet[udpStart + 4].toInt() and 0xFF) shl 8) or
                        (packet[udpStart + 5].toInt() and 0xFF)
            
            // Extract checksum (bytes 6-7)
            val checksum = ((packet[udpStart + 6].toInt() and 0xFF) shl 8) or
                          (packet[udpStart + 7].toInt() and 0xFF)
            
            // Validate length
            if (length < UDP_HEADER_SIZE) {
                logger.verbose(TAG, "Invalid UDP length: $length")
                return null
            }
            
            // Validate that packet has enough data for the specified length
            if (packet.size < udpStart + length) {
                logger.verbose(TAG, "Packet truncated: expected $length bytes, got ${packet.size - udpStart}")
                return null
            }
            
            return UdpHeader(
                sourcePort = sourcePort,
                destPort = destPort,
                length = length,
                checksum = checksum
            )
            
        } catch (e: Exception) {
            logger.error(TAG, "Error parsing UDP header: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Extracts the UDP payload from a packet.
     * 
     * @param packet The complete IP packet
     * @param ipHeaderLength The length of the IP header in bytes
     * @param udpHeader The parsed UDP header
     * @return UDP payload bytes
     * 
     * Requirements: 6.3
     */
    fun extractUdpPayload(
        packet: ByteArray,
        ipHeaderLength: Int,
        udpHeader: UdpHeader
    ): ByteArray {
        val udpStart = ipHeaderLength
        val payloadStart = udpStart + UDP_HEADER_SIZE
        val payloadLength = udpHeader.length - UDP_HEADER_SIZE
        
        if (payloadLength <= 0 || payloadStart + payloadLength > packet.size) {
            return ByteArray(0)
        }
        
        return packet.copyOfRange(payloadStart, payloadStart + payloadLength)
    }
    
    /**
     * Checks if a UDP packet is a DNS query.
     * 
     * DNS queries are sent to port 53.
     * 
     * @param udpHeader The parsed UDP header
     * @return true if this is a DNS query, false otherwise
     * 
     * Requirements: 6.2, 6.4
     */
    fun isDnsQuery(udpHeader: UdpHeader): Boolean {
        return udpHeader.destPort == DNS_PORT
    }
    
    /**
     * Handles a DNS query by routing it through SOCKS5.
     * 
     * This method will be implemented in a future task to:
     * - Connect to DNS server through SOCKS5
     * - Send DNS query using DNS-over-TCP
     * - Receive DNS response
     * - Construct UDP response packet
     * - Write response back to TUN interface
     * 
     * @param sourceIp Source IP address from the original packet
     * @param sourcePort Source port from the original packet
     * @param destIp Destination IP address (DNS server)
     * @param destPort Destination port (53)
     * @param dnsPayload The DNS query payload
     * @param tunOutputStream Output stream to write response packets
     * 
     * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
     */
    private suspend fun handleDnsQuery(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        dnsPayload: ByteArray,
        tunOutputStream: FileOutputStream
    ) {
        // TODO: Implement DNS query routing in task 12
        logger.debug(TAG, "DNS query handling not yet implemented")
    }
    
    /**
     * Sends a UDP packet back to the TUN interface.
     * 
     * This method will be implemented in a future task to:
     * - Build IP header with swapped source/dest
     * - Build UDP header with correct length
     * - Calculate UDP checksum (or set to 0)
     * - Write packet to TUN interface
     * 
     * @param tunOutputStream Output stream to write the packet
     * @param sourceIp Source IP address for the response
     * @param sourcePort Source port for the response
     * @param destIp Destination IP address for the response
     * @param destPort Destination port for the response
     * @param payload UDP payload data
     * 
     * Requirements: 6.5, 8.3
     */
    private suspend fun sendUdpPacket(
        tunOutputStream: FileOutputStream,
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        payload: ByteArray
    ) {
        // TODO: Implement UDP packet construction in task 13
        logger.debug(TAG, "UDP packet sending not yet implemented")
    }
}
