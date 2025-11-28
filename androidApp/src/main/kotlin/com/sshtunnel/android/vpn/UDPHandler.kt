package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPv4Header
import com.sshtunnel.android.vpn.packet.PacketBuilder
import com.sshtunnel.android.vpn.packet.Protocol
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

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
    private val packetBuilder = PacketBuilder()
    
    companion object {
        private const val TAG = "UDPHandler"
        private const val DNS_PORT = 53
        private const val UDP_HEADER_SIZE = 8
        private const val DNS_TIMEOUT_MS = 5000
        private const val MAX_DNS_RESPONSE_SIZE = 4096
    }
    
    /**
     * Handles a UDP packet from the TUN interface.
     * 
     * Wraps all packet processing in error handling to ensure one bad packet
     * doesn't crash the router.
     * 
     * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5
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
            // Log error with context and continue processing
            // Requirements: 11.1, 11.2, 11.5
            logger.error(
                TAG,
                "Error handling UDP packet from ${ipHeader.sourceIP} to ${ipHeader.destIP}: ${e.message}",
                e
            )
            // Continue processing - don't let one bad packet crash the router
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
     * This method:
     * - Connects to DNS server through SOCKS5
     * - Sends DNS query using DNS-over-TCP
     * - Receives DNS response
     * - Constructs UDP response packet
     * - Writes response back to TUN interface
     * 
     * @param sourceIp Source IP address from the original packet
     * @param sourcePort Source port from the original packet
     * @param destIp Destination IP address (DNS server)
     * @param destPort Destination port (53)
     * @param dnsPayload The DNS query payload
     * @param tunOutputStream Output stream to write response packets
     * 
     * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 12.1, 12.5
     */
    private suspend fun handleDnsQuery(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        dnsPayload: ByteArray,
        tunOutputStream: FileOutputStream
    ) {
        try {
            // Log DNS query (NEVER log payload content for privacy - only size)
            logger.info(
                TAG,
                "DNS query: $sourceIp:$sourcePort -> $destIp:$destPort (query size: ${dnsPayload.size} bytes)"
            )
            
            // Query DNS through SOCKS5 using DNS-over-TCP
            val dnsResponse = queryDnsThroughSocks5(destIp, destPort, dnsPayload)
            
            if (dnsResponse != null) {
                logger.info(TAG, "DNS query successful: response size ${dnsResponse.size} bytes")
                
                // Send UDP response packet back to TUN interface
                // Note: source and dest are swapped for the response
                sendUdpPacket(
                    tunOutputStream = tunOutputStream,
                    sourceIp = destIp,        // DNS server becomes source
                    sourcePort = destPort,    // Port 53
                    destIp = sourceIp,        // Original source becomes dest
                    destPort = sourcePort,    // Original source port
                    payload = dnsResponse
                )
            } else {
                logger.warn(TAG, "DNS query failed or timed out for $destIp:$destPort")
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Error handling DNS query: ${e.message}", e)
        }
    }
    
    /**
     * Queries a DNS server through SOCKS5 using DNS-over-TCP.
     * 
     * DNS-over-TCP format:
     * - 2-byte length prefix (big-endian)
     * - DNS query payload
     * 
     * Response format:
     * - 2-byte length prefix (big-endian)
     * - DNS response payload
     * 
     * @param dnsServer DNS server IP address
     * @param dnsPort DNS server port (typically 53)
     * @param query DNS query payload
     * @return DNS response payload, or null if query failed or timed out
     * 
     * Requirements: 7.2, 7.3, 7.4, 7.5
     */
    private suspend fun queryDnsThroughSocks5(
        dnsServer: String,
        dnsPort: Int,
        query: ByteArray
    ): ByteArray? = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            logger.debug(TAG, "Connecting to DNS server $dnsServer:$dnsPort through SOCKS5")
            
            // Connect to SOCKS5 proxy
            socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", socksPort), DNS_TIMEOUT_MS)
            socket.soTimeout = DNS_TIMEOUT_MS
            
            // Perform SOCKS5 handshake
            if (!performSocks5Handshake(socket, dnsServer, dnsPort)) {
                logger.error(TAG, "SOCKS5 handshake failed for DNS query")
                return@withContext null
            }
            
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            
            // Send DNS query with 2-byte length prefix (DNS-over-TCP format)
            val queryLength = ByteBuffer.allocate(2).putShort(query.size.toShort()).array()
            output.write(queryLength)
            output.write(query)
            output.flush()
            
            logger.debug(TAG, "Sent DNS query: ${query.size} bytes")
            
            // Read response length (2 bytes)
            val responseLengthBytes = ByteArray(2)
            val lengthRead = input.read(responseLengthBytes)
            if (lengthRead != 2) {
                logger.error(TAG, "Failed to read DNS response length")
                return@withContext null
            }
            
            val responseLength = ByteBuffer.wrap(responseLengthBytes).short.toInt() and 0xFFFF
            
            // Validate response length
            if (responseLength <= 0) {
                logger.error(TAG, "Invalid DNS response length: $responseLength")
                return@withContext null
            }
            
            if (responseLength > MAX_DNS_RESPONSE_SIZE) {
                logger.error(TAG, "DNS response too large: $responseLength bytes (max: $MAX_DNS_RESPONSE_SIZE)")
                return@withContext null
            }
            
            logger.debug(TAG, "Reading DNS response: $responseLength bytes")
            
            // Read response data
            val response = ByteArray(responseLength)
            var totalRead = 0
            while (totalRead < responseLength) {
                val read = input.read(response, totalRead, responseLength - totalRead)
                if (read <= 0) {
                    logger.error(TAG, "Connection closed while reading DNS response")
                    break
                }
                totalRead += read
            }
            
            if (totalRead == responseLength) {
                logger.debug(TAG, "DNS query successful: received $totalRead bytes")
                response
            } else {
                logger.error(TAG, "Incomplete DNS response: expected $responseLength, got $totalRead")
                null
            }
            
        } catch (e: java.net.SocketTimeoutException) {
            logger.warn(TAG, "DNS query timed out after ${DNS_TIMEOUT_MS}ms")
            null
        } catch (e: Exception) {
            logger.error(TAG, "DNS query failed: ${e.message}", e)
            null
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                logger.verbose(TAG, "Error closing DNS socket: ${e.message}")
            }
        }
    }
    
    /**
     * Performs SOCKS5 handshake for connecting to a destination through the proxy.
     * 
     * SOCKS5 handshake steps:
     * 1. Send greeting: [VER=5, NMETHODS=1, METHOD=0 (no auth)]
     * 2. Receive method selection: [VER=5, METHOD=0]
     * 3. Send connect request: [VER=5, CMD=1 (CONNECT), RSV=0, ATYP=1 (IPv4), DST.ADDR, DST.PORT]
     * 4. Receive response: [VER=5, REP=0 (success), RSV=0, ATYP, BND.ADDR, BND.PORT]
     * 
     * @param socket Connected socket to SOCKS5 proxy
     * @param destIp Destination IP address
     * @param destPort Destination port
     * @return true if handshake successful, false otherwise
     * 
     * Requirements: 7.2
     */
    private fun performSocks5Handshake(
        socket: Socket,
        destIp: String,
        destPort: Int
    ): Boolean {
        try {
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            
            // Step 1: Send greeting
            // [VER=5, NMETHODS=1, METHOD=0 (no authentication)]
            output.write(byteArrayOf(0x05, 0x01, 0x00))
            output.flush()
            
            // Step 2: Read method selection
            val greeting = ByteArray(2)
            if (input.read(greeting) != 2) {
                logger.error(TAG, "Failed to read SOCKS5 greeting response")
                return false
            }
            
            if (greeting[0] != 0x05.toByte()) {
                logger.error(TAG, "Invalid SOCKS5 version: ${greeting[0]}")
                return false
            }
            
            if (greeting[1] != 0x00.toByte()) {
                logger.error(TAG, "SOCKS5 authentication method not accepted: ${greeting[1]}")
                return false
            }
            
            // Step 3: Send connect request
            // [VER=5, CMD=1 (CONNECT), RSV=0, ATYP=1 (IPv4), DST.ADDR (4 bytes), DST.PORT (2 bytes)]
            val ipBytes = java.net.InetAddress.getByName(destIp).address
            val portBytes = ByteBuffer.allocate(2).putShort(destPort.toShort()).array()
            
            val request = byteArrayOf(
                0x05,  // Version 5
                0x01,  // CMD: CONNECT
                0x00,  // Reserved
                0x01   // ATYP: IPv4
            ) + ipBytes + portBytes
            
            output.write(request)
            output.flush()
            
            // Step 4: Read response
            // [VER, REP, RSV, ATYP, BND.ADDR, BND.PORT]
            // Minimum response size: 10 bytes (for IPv4)
            val response = ByteArray(10)
            val read = input.read(response)
            
            if (read < 10) {
                logger.error(TAG, "Incomplete SOCKS5 response: $read bytes")
                return false
            }
            
            if (response[0] != 0x05.toByte()) {
                logger.error(TAG, "Invalid SOCKS5 response version: ${response[0]}")
                return false
            }
            
            if (response[1] != 0x00.toByte()) {
                val errorCode = response[1].toInt() and 0xFF
                logger.error(TAG, "SOCKS5 connect failed with error code: $errorCode")
                return false
            }
            
            logger.debug(TAG, "SOCKS5 handshake successful: $destIp:$destPort")
            return true
            
        } catch (e: Exception) {
            logger.error(TAG, "SOCKS5 handshake error: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Sends a UDP packet back to the TUN interface.
     * 
     * This method:
     * - Builds IP header with swapped source/dest
     * - Builds UDP header with correct length
     * - Calculates UDP checksum
     * - Writes packet to TUN interface
     * 
     * Handles TUN interface write errors gracefully.
     * 
     * Requirements: 6.5, 8.3, 11.3, 11.4
     * 
     * @param tunOutputStream Output stream to write the packet
     * @param sourceIp Source IP address for the response
     * @param sourcePort Source port for the response
     * @param destIp Destination IP address for the response
     * @param destPort Destination port for the response
     * @param payload UDP payload data
     */
    private suspend fun sendUdpPacket(
        tunOutputStream: FileOutputStream,
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        payload: ByteArray
    ) = withContext(Dispatchers.IO) {
        try {
            logger.verbose(
                TAG,
                "Sending UDP packet: $sourceIp:$sourcePort -> $destIp:$destPort, payload size: ${payload.size}"
            )
            
            // Build complete UDP packet (IP + UDP headers + payload)
            val packet = packetBuilder.buildUdpPacket(
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                destIp = destIp,
                destPort = destPort,
                payload = payload
            )
            
            // Write packet to TUN interface
            tunOutputStream.write(packet)
            tunOutputStream.flush()
            
            logger.verbose(TAG, "UDP packet sent successfully: ${packet.size} bytes")
            
        } catch (e: IOException) {
            // TUN interface write error - log and continue
            // Requirements: 11.4
            logger.error(TAG, "Failed to write UDP packet to TUN: ${e.message}", e)
            // Don't throw - UDP is best-effort, continue processing
        } catch (e: Exception) {
            // Unexpected error - log and continue
            // Requirements: 11.1, 11.5
            logger.error(TAG, "Unexpected error sending UDP packet: ${e.message}", e)
            // Don't throw - continue processing other packets
        }
    }
}
