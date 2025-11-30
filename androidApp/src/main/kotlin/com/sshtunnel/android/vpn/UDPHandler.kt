package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPv4Header
import com.sshtunnel.android.vpn.packet.PacketBuilder
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
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
                // Handle generic UDP traffic through SOCKS5 UDP ASSOCIATE
                logger.debug(TAG, "Generic UDP traffic detected, routing through SOCKS5 UDP ASSOCIATE")
                handleGenericUdpPacket(
                    sourceIp = ipHeader.sourceIP,
                    sourcePort = udpHeader.sourcePort,
                    destIp = ipHeader.destIP,
                    destPort = udpHeader.destPort,
                    payload = payload,
                    tunOutputStream = tunOutputStream
                )
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
     * Handles non-DNS UDP packets by routing through SOCKS5 UDP ASSOCIATE.
     * 
     * This method:
     * 1. Creates ConnectionKey from packet 5-tuple
     * 2. Checks if UDP ASSOCIATE connection exists for this key
     * 3. If exists: reuses connection, sends packet through it
     * 4. If not exists: establishes new UDP ASSOCIATE connection
     * 5. If establishment fails: logs error and drops packet
     * 6. Calls sendUdpThroughSocks5() to forward packet
     * 7. Starts UDP reader coroutine if this is a new connection
     * 
     * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.5
     * 
     * @param sourceIp Source IP address from the original packet
     * @param sourcePort Source port from the original packet
     * @param destIp Destination IP address
     * @param destPort Destination port
     * @param payload The UDP payload
     * @param tunOutputStream Output stream to write response packets
     */
    private suspend fun handleGenericUdpPacket(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        payload: ByteArray,
        tunOutputStream: FileOutputStream
    ) {
        try {
            // Step 1: Create ConnectionKey from packet 5-tuple
            val key = ConnectionKey(
                protocol = Protocol.UDP,
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                destIp = destIp,
                destPort = destPort
            )
            
            logger.verbose(
                TAG,
                "Handling generic UDP packet: $sourceIp:$sourcePort -> $destIp:$destPort, " +
                "payload size: ${payload.size}"
            )
            
            // Step 2: Check if UDP ASSOCIATE connection exists for this key
            var connection = connectionTable.getUdpAssociateConnection(key)
            
            if (connection != null) {
                // Step 3: If exists: reuse connection, send packet through it
                logger.verbose(
                    TAG,
                    "Reusing existing UDP ASSOCIATE connection for $sourceIp:$sourcePort -> $destIp:$destPort"
                )
                
                // Step 6: Call sendUdpThroughSocks5() to forward packet
                sendUdpThroughSocks5(connection, destIp, destPort, payload)
                
            } else {
                // Step 4: If not exists: establish new UDP ASSOCIATE connection
                logger.info(
                    TAG,
                    "Establishing new UDP ASSOCIATE connection for $sourceIp:$sourcePort -> $destIp:$destPort"
                )
                
                connection = establishUdpAssociate(key)
                
                if (connection == null) {
                    // Step 5: If establishment fails: log error and drop packet
                    logger.error(
                        TAG,
                        "Failed to establish UDP ASSOCIATE connection for $sourceIp:$sourcePort -> " +
                        "$destIp:$destPort, dropping packet"
                    )
                    return
                }
                
                logger.info(
                    TAG,
                    "Successfully established UDP ASSOCIATE connection for $sourceIp:$sourcePort -> " +
                    "$destIp:$destPort"
                )
                
                // Step 7: Start UDP reader coroutine for this new connection
                val readerJob = startUdpReader(key, connection, tunOutputStream)
                
                // Update the connection with the actual reader job
                val updatedConnection = connection.copy(readerJob = readerJob)
                
                // Replace the connection in the table with the updated one
                connectionTable.removeUdpAssociateConnection(key)
                connectionTable.addUdpAssociateConnection(updatedConnection)
                
                logger.debug(
                    TAG,
                    "Started UDP reader coroutine for $sourceIp:$sourcePort -> $destIp:$destPort"
                )
                
                // Step 6: Call sendUdpThroughSocks5() to forward packet
                sendUdpThroughSocks5(updatedConnection, destIp, destPort, payload)
            }
            
        } catch (e: Exception) {
            // Handle errors gracefully - don't let one bad packet crash the router
            // Requirements: 7.1, 7.2, 7.5
            logger.error(
                TAG,
                "Error handling generic UDP packet from $sourceIp:$sourcePort to $destIp:$destPort: ${e.message}",
                e
            )
            // Continue processing - UDP is best-effort
        }
    }
    
    /**
     * Queries DNS using Android's native DNS resolver instead of SOCKS5.
     * 
     * TEMPORARY IMPLEMENTATION:
     * This is a workaround because JSch's SOCKS5 proxy doesn't support UDP ASSOCIATE,
     * which is required for proper DNS tunneling. This implementation resolves DNS
     * queries locally, which means DNS queries are NOT tunneled through the SSH server.
     * 
     * TODO: Implement proper DNS tunneling via UDP ASSOCIATE when SOCKS5 server supports it.
     * See TODO.md #1 for details.
     * 
     * SECURITY NOTE:
     * This creates a DNS leak - DNS queries go directly to the system DNS servers
     * instead of through the tunnel. This is a known limitation of the current
     * implementation.
     * 
     * How it works:
     * 1. Parse DNS query to extract the domain name
     * 2. Use Android's InetAddress.getAllByName() to resolve
     * 3. Construct a DNS response packet with the resolved IP addresses
     * 4. Return the response to be sent back through the VPN
     * 
     * @param dnsServer DNS server IP address (ignored in this implementation)
     * @param dnsPort DNS server port (ignored in this implementation)
     * @param query DNS query payload
     * @return DNS response payload, or null if query failed
     * 
     * Requirements: 7.2, 7.3, 7.4, 7.5
     */
    private suspend fun queryDnsThroughSocks5(
        dnsServer: String,
        dnsPort: Int,
        query: ByteArray
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Resolving DNS query locally (SOCKS5 UDP not supported)")
            
            // Parse DNS query to extract domain name
            val domainName = parseDnsQuery(query)
            if (domainName == null) {
                logger.error(TAG, "Failed to parse DNS query")
                return@withContext null
            }
            
            logger.info(TAG, "DNS query for domain: $domainName")
            
            // Resolve using Android's native DNS resolver
            // This bypasses the tunnel but works with JSch's TCP-only SOCKS5
            val addresses = try {
                java.net.InetAddress.getAllByName(domainName)
            } catch (e: java.net.UnknownHostException) {
                logger.warn(TAG, "DNS resolution failed for $domainName: ${e.message}")
                return@withContext null
            } catch (e: Exception) {
                logger.error(TAG, "DNS resolution error for $domainName: ${e.message}", e)
                return@withContext null
            }
            
            if (addresses.isEmpty()) {
                logger.warn(TAG, "No addresses found for $domainName")
                return@withContext null
            }
            
            logger.info(TAG, "Resolved $domainName to ${addresses.size} address(es)")
            
            // Construct DNS response packet
            val response = constructDnsResponse(query, addresses)
            
            if (response != null) {
                logger.debug(TAG, "DNS response constructed: ${response.size} bytes")
            } else {
                logger.error(TAG, "Failed to construct DNS response")
            }
            
            response
            
        } catch (e: Exception) {
            logger.error(TAG, "DNS query failed: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parses a DNS query packet to extract the domain name.
     * 
     * DNS query format (simplified):
     * - 12 bytes: DNS header
     * - Variable: Question section with domain name
     * 
     * Domain name encoding:
     * - Length-prefixed labels (e.g., 3www6google3com0)
     * - Terminated with 0-length label
     * 
     * @param query DNS query packet
     * @return Domain name, or null if parsing failed
     */
    private fun parseDnsQuery(query: ByteArray): String? {
        try {
            if (query.size < 13) {
                return null
            }
            
            // Skip DNS header (12 bytes) and start reading question section
            var offset = 12
            val labels = mutableListOf<String>()
            
            while (offset < query.size) {
                val length = query[offset].toInt() and 0xFF
                
                if (length == 0) {
                    // End of domain name
                    break
                }
                
                if (length > 63 || offset + length >= query.size) {
                    // Invalid label length
                    return null
                }
                
                offset++
                val label = String(query, offset, length, Charsets.US_ASCII)
                labels.add(label)
                offset += length
            }
            
            if (labels.isEmpty()) {
                return null
            }
            
            return labels.joinToString(".")
            
        } catch (e: Exception) {
            logger.error(TAG, "Error parsing DNS query: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Constructs a DNS response packet from resolved IP addresses.
     * 
     * This creates a minimal DNS response with:
     * - Original query ID and flags
     * - Question section (copied from query)
     * - Answer section with A records (IPv4) or AAAA records (IPv6)
     * 
     * @param query Original DNS query packet
     * @param addresses Resolved IP addresses
     * @return DNS response packet, or null if construction failed
     */
    private fun constructDnsResponse(
        query: ByteArray,
        addresses: Array<java.net.InetAddress>
    ): ByteArray? {
        try {
            if (query.size < 12) {
                return null
            }
            
            val response = ByteBuffer.allocate(512) // Standard DNS packet size
            
            // Copy transaction ID (bytes 0-1)
            response.put(query[0])
            response.put(query[1])
            
            // Flags: Standard query response, no error
            response.put(0x81.toByte()) // QR=1 (response), Opcode=0, AA=0, TC=0, RD=1
            response.put(0x80.toByte()) // RA=1, Z=0, RCODE=0 (no error)
            
            // Question count (1)
            response.putShort(1)
            
            // Answer count (number of addresses)
            response.putShort(addresses.size.toShort())
            
            // Authority RR count (0)
            response.putShort(0)
            
            // Additional RR count (0)
            response.putShort(0)
            
            // Copy question section from query
            // Find the end of question section (after domain name and query type/class)
            var questionEnd = 12
            while (questionEnd < query.size) {
                val length = query[questionEnd].toInt() and 0xFF
                if (length == 0) {
                    questionEnd += 5 // 0-byte + 2 bytes type + 2 bytes class
                    break
                }
                questionEnd += length + 1
            }
            
            if (questionEnd > query.size) {
                return null
            }
            
            // Copy question section
            response.put(query, 12, questionEnd - 12)
            
            // Add answer records
            for (address in addresses) {
                val addressBytes = address.address
                
                // Name: pointer to question (0xC00C)
                response.putShort(0xC00C.toShort())
                
                // Type: A (1) for IPv4, AAAA (28) for IPv6
                val type = if (addressBytes.size == 4) 1 else 28
                response.putShort(type.toShort())
                
                // Class: IN (1)
                response.putShort(1)
                
                // TTL: 60 seconds
                response.putInt(60)
                
                // Data length
                response.putShort(addressBytes.size.toShort())
                
                // Address data
                response.put(addressBytes)
            }
            
            // Return the constructed response
            val responseSize = response.position()
            val responseBytes = ByteArray(responseSize)
            response.rewind()
            response.get(responseBytes)
            
            return responseBytes
            
        } catch (e: Exception) {
            logger.error(TAG, "Error constructing DNS response: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Decapsulates a SOCKS5 UDP response packet.
     * 
     * SOCKS5 UDP response header format:
     * +----+------+------+----------+----------+----------+
     * |RSV | FRAG | ATYP | SRC.ADDR | SRC.PORT |   DATA   |
     * +----+------+------+----------+----------+----------+
     * | 2  |  1   |  1   | Variable |    2     | Variable |
     * +----+------+------+----------+----------+----------+
     * 
     * - RSV: Reserved (must be 0x0000)
     * - FRAG: Fragment number (must be 0x00, fragmentation not supported)
     * - ATYP: Address type (0x01 = IPv4, 0x03 = Domain, 0x04 = IPv6)
     * - SRC.ADDR: Source address (4 bytes for IPv4, 16 bytes for IPv6)
     * - SRC.PORT: Source port (2 bytes, big-endian)
     * - DATA: UDP payload
     * 
     * @param socks5Packet Complete SOCKS5 UDP packet received from relay
     * @return UdpDecapsulatedPacket with source address, port, and payload, or null if invalid
     * 
     * Requirements: 4.1, 4.2, 7.3
     */
    fun decapsulateUdpPacket(socks5Packet: ByteArray): UdpDecapsulatedPacket? {
        try {
            // Minimum packet size: RSV(2) + FRAG(1) + ATYP(1) + ADDR(4 for IPv4) + PORT(2) = 10 bytes
            if (socks5Packet.size < 10) {
                logger.verbose(TAG, "SOCKS5 UDP packet too short: ${socks5Packet.size} bytes")
                return null
            }
            
            // Validate RSV (bytes 0-1) = 0x0000
            val rsvHigh = socks5Packet[0].toInt() and 0xFF
            val rsvLow = socks5Packet[1].toInt() and 0xFF
            if (rsvHigh != 0x00 || rsvLow != 0x00) {
                logger.verbose(TAG, "Invalid RSV field: 0x${rsvHigh.toString(16)}${rsvLow.toString(16)}")
                return null
            }
            
            // Validate FRAG (byte 2) = 0x00 (no fragmentation)
            val frag = socks5Packet[2].toInt() and 0xFF
            if (frag != 0x00) {
                logger.verbose(TAG, "Fragmentation not supported: FRAG=0x${frag.toString(16)}")
                return null
            }
            
            // Parse ATYP (byte 3)
            val atyp = socks5Packet[3].toInt() and 0xFF
            
            // Parse source address based on ATYP
            val (sourceIp, addressLength) = when (atyp) {
                0x01 -> {
                    // IPv4 address (4 bytes)
                    if (socks5Packet.size < 10) {
                        logger.verbose(TAG, "Packet too short for IPv4 address")
                        return null
                    }
                    val addr = socks5Packet.copyOfRange(4, 8)
                    val ip = java.net.InetAddress.getByAddress(addr).hostAddress
                    Pair(ip ?: "0.0.0.0", 4)
                }
                0x03 -> {
                    // Domain name (first byte is length)
                    if (socks5Packet.size < 5) {
                        logger.verbose(TAG, "Packet too short for domain name length")
                        return null
                    }
                    val domainLength = socks5Packet[4].toInt() and 0xFF
                    if (socks5Packet.size < 5 + domainLength + 2) {
                        logger.verbose(TAG, "Packet too short for domain name")
                        return null
                    }
                    val domainBytes = socks5Packet.copyOfRange(5, 5 + domainLength)
                    val domain = String(domainBytes, Charsets.US_ASCII)
                    Pair(domain, 1 + domainLength)
                }
                0x04 -> {
                    // IPv6 address (16 bytes)
                    if (socks5Packet.size < 22) {
                        logger.verbose(TAG, "Packet too short for IPv6 address")
                        return null
                    }
                    val addr = socks5Packet.copyOfRange(4, 20)
                    val ip = java.net.InetAddress.getByAddress(addr).hostAddress
                    Pair(ip ?: "::", 16)
                }
                else -> {
                    logger.verbose(TAG, "Unknown address type: 0x${atyp.toString(16)}")
                    return null
                }
            }
            
            // Parse source port (2 bytes, big-endian)
            val portOffset = 4 + addressLength
            if (socks5Packet.size < portOffset + 2) {
                logger.verbose(TAG, "Packet too short for port")
                return null
            }
            
            val sourcePort = ((socks5Packet[portOffset].toInt() and 0xFF) shl 8) or
                            (socks5Packet[portOffset + 1].toInt() and 0xFF)
            
            // Extract payload (remaining bytes)
            val payloadOffset = portOffset + 2
            val payload = if (payloadOffset < socks5Packet.size) {
                socks5Packet.copyOfRange(payloadOffset, socks5Packet.size)
            } else {
                ByteArray(0)
            }
            
            logger.verbose(
                TAG,
                "Decapsulated UDP packet: $sourceIp:$sourcePort, payload size: ${payload.size}"
            )
            
            return UdpDecapsulatedPacket(
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                payload = payload
            )
            
        } catch (e: Exception) {
            logger.error(TAG, "Error decapsulating UDP packet: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Encapsulates a UDP datagram with SOCKS5 UDP header.
     * 
     * SOCKS5 UDP request header format:
     * +----+------+------+----------+----------+----------+
     * |RSV | FRAG | ATYP | DST.ADDR | DST.PORT |   DATA   |
     * +----+------+------+----------+----------+----------+
     * | 2  |  1   |  1   | Variable |    2     | Variable |
     * +----+------+------+----------+----------+----------+
     * 
     * - RSV: Reserved (0x0000)
     * - FRAG: Fragment number (0x00 = no fragmentation)
     * - ATYP: Address type (0x01 = IPv4, 0x03 = Domain, 0x04 = IPv6)
     * - DST.ADDR: Destination address (4 bytes for IPv4)
     * - DST.PORT: Destination port (2 bytes, big-endian)
     * - DATA: Original UDP payload
     * 
     * @param destIp Destination IP address
     * @param destPort Destination port
     * @param payload Original UDP payload
     * @return Complete encapsulated datagram ready to send to relay endpoint
     * 
     * Requirements: 3.1, 3.2, 3.5, 11.2, 11.3
     */
    fun encapsulateUdpPacket(
        destIp: String,
        destPort: Int,
        payload: ByteArray
    ): ByteArray {
        try {
            // Parse destination IP address
            val ipBytes = java.net.InetAddress.getByName(destIp).address
            
            // Determine address type based on IP address length
            val atyp: Byte = when (ipBytes.size) {
                4 -> 0x01  // IPv4
                16 -> 0x04 // IPv6
                else -> throw IllegalArgumentException("Invalid IP address: $destIp")
            }
            
            // Build SOCKS5 UDP header
            val header = ByteBuffer.allocate(4 + ipBytes.size + 2).apply {
                // RSV: Reserved (2 bytes, must be 0x0000)
                put(0x00)
                put(0x00)
                
                // FRAG: Fragment number (1 byte, 0x00 = no fragmentation)
                put(0x00)
                
                // ATYP: Address type (1 byte)
                put(atyp)
                
                // DST.ADDR: Destination address (4 bytes for IPv4, 16 bytes for IPv6)
                put(ipBytes)
                
                // DST.PORT: Destination port (2 bytes, big-endian)
                putShort(destPort.toShort())
            }.array()
            
            // Combine header and payload
            val encapsulated = header + payload
            
            logger.verbose(
                TAG,
                "Encapsulated UDP packet: $destIp:$destPort, " +
                "header size: ${header.size}, payload size: ${payload.size}, " +
                "total size: ${encapsulated.size}"
            )
            
            return encapsulated
            
        } catch (e: Exception) {
            logger.error(TAG, "Error encapsulating UDP packet: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Performs SOCKS5 UDP ASSOCIATE handshake on the control connection.
     * 
     * UDP ASSOCIATE handshake steps:
     * 1. Send greeting: [VER=5, NMETHODS=1, METHOD=0 (no auth)]
     * 2. Receive method selection: [VER=5, METHOD=0]
     * 3. Send UDP ASSOCIATE request: [VER=5, CMD=3 (UDP ASSOCIATE), RSV=0, ATYP=1 (IPv4), DST.ADDR, DST.PORT]
     * 4. Receive response: [VER=5, REP=0 (success), RSV=0, ATYP, BND.ADDR, BND.PORT]
     * 
     * The BND.ADDR and BND.PORT in the response indicate the UDP relay endpoint
     * where encapsulated datagrams should be sent.
     * 
     * @param controlSocket Connected TCP socket to SOCKS5 proxy (must remain open)
     * @param clientAddress Client's address to bind (typically "0.0.0.0" for any)
     * @param clientPort Client's port to bind (typically 0 for any)
     * @return UdpRelayEndpoint with relay address and port, or null on failure
     * 
     * Requirements: 1.1, 1.2, 1.3, 7.1, 11.1, 11.2
     */
    private fun performUdpAssociateHandshake(
        controlSocket: Socket,
        clientAddress: String = "0.0.0.0",
        clientPort: Int = 0
    ): UdpRelayEndpoint? {
        try {
            val output = controlSocket.getOutputStream()
            val input = controlSocket.getInputStream()
            
            // Step 1: Send greeting
            // [VER=5, NMETHODS=1, METHOD=0 (no authentication)]
            output.write(byteArrayOf(0x05, 0x01, 0x00))
            output.flush()
            
            logger.debug(TAG, "Sent SOCKS5 greeting for UDP ASSOCIATE")
            
            // Step 2: Read method selection
            val greeting = ByteArray(2)
            if (input.read(greeting) != 2) {
                logger.error(TAG, "Failed to read SOCKS5 greeting response")
                return null
            }
            
            if (greeting[0] != 0x05.toByte()) {
                logger.error(TAG, "Invalid SOCKS5 version in greeting: ${greeting[0]}")
                return null
            }
            
            if (greeting[1] != 0x00.toByte()) {
                logger.error(TAG, "SOCKS5 authentication method not accepted: ${greeting[1]}")
                return null
            }
            
            logger.debug(TAG, "SOCKS5 greeting accepted")
            
            // Step 3: Send UDP ASSOCIATE request
            // [VER=5, CMD=3 (UDP ASSOCIATE), RSV=0, ATYP=1 (IPv4), DST.ADDR (4 bytes), DST.PORT (2 bytes)]
            val ipBytes = java.net.InetAddress.getByName(clientAddress).address
            val portBytes = ByteBuffer.allocate(2).putShort(clientPort.toShort()).array()
            
            val request = byteArrayOf(
                0x05,  // Version 5
                0x03,  // CMD: UDP ASSOCIATE
                0x00,  // Reserved
                0x01   // ATYP: IPv4
            ) + ipBytes + portBytes
            
            output.write(request)
            output.flush()
            
            logger.debug(TAG, "Sent UDP ASSOCIATE request: $clientAddress:$clientPort")
            
            // Step 4: Read response
            // [VER, REP, RSV, ATYP, BND.ADDR, BND.PORT]
            // Minimum response size: 10 bytes (for IPv4)
            val response = ByteArray(10)
            val read = input.read(response)
            
            if (read < 10) {
                logger.error(TAG, "Incomplete SOCKS5 UDP ASSOCIATE response: $read bytes")
                return null
            }
            
            if (response[0] != 0x05.toByte()) {
                logger.error(TAG, "Invalid SOCKS5 response version: ${response[0]}")
                return null
            }
            
            // Check reply code (REP field)
            val replyCode = response[1].toInt() and 0xFF
            if (replyCode != 0x00) {
                val errorMessage = when (replyCode) {
                    0x01 -> "General SOCKS server failure"
                    0x02 -> "Connection not allowed by ruleset"
                    0x03 -> "Network unreachable"
                    0x04 -> "Host unreachable"
                    0x05 -> "Connection refused"
                    0x06 -> "TTL expired"
                    0x07 -> "Command not supported (UDP ASSOCIATE not supported by server)"
                    0x08 -> "Address type not supported"
                    else -> "Unknown error code: $replyCode"
                }
                logger.error(TAG, "SOCKS5 UDP ASSOCIATE failed: $errorMessage (code: 0x${replyCode.toString(16)})")
                return null
            }
            
            // Parse ATYP (address type)
            val atyp = response[3].toInt() and 0xFF
            
            // Parse BND.ADDR based on ATYP
            val relayAddress = when (atyp) {
                0x01 -> {
                    // IPv4 address (4 bytes)
                    val addr = response.copyOfRange(4, 8)
                    java.net.InetAddress.getByAddress(addr).hostAddress
                }
                0x03 -> {
                    // Domain name (first byte is length)
                    logger.error(TAG, "Domain name address type not yet supported for UDP ASSOCIATE")
                    return null
                }
                0x04 -> {
                    // IPv6 address (16 bytes)
                    logger.error(TAG, "IPv6 address type not yet supported for UDP ASSOCIATE")
                    return null
                }
                else -> {
                    logger.error(TAG, "Unknown address type: $atyp")
                    return null
                }
            }
            
            // Parse BND.PORT (2 bytes, big-endian)
            val relayPort = ((response[8].toInt() and 0xFF) shl 8) or
                           (response[9].toInt() and 0xFF)
            
            if (relayPort == 0) {
                logger.error(TAG, "Invalid relay port: 0")
                return null
            }
            
            logger.debug(TAG, "UDP ASSOCIATE handshake successful: relay endpoint $relayAddress:$relayPort")
            
            return UdpRelayEndpoint(
                address = relayAddress ?: "0.0.0.0",
                port = relayPort
            )
            
        } catch (e: Exception) {
            logger.error(TAG, "UDP ASSOCIATE handshake error: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Establishes a SOCKS5 UDP ASSOCIATE connection.
     * 
     * This method:
     * 1. Creates TCP control socket to SOCKS5 proxy
     * 2. Performs initial SOCKS5 greeting (VER=0x05, NMETHODS=0x01, METHOD=0x00)
     * 3. Calls performUdpAssociateHandshake() to get relay endpoint
     * 4. Creates DatagramSocket for UDP relay communication
     * 5. Creates UdpAssociateConnection object
     * 6. Adds connection to ConnectionTable
     * 
     * The TCP control socket must remain open for the lifetime of the UDP association.
     * Closing the control socket will terminate the UDP relay.
     * 
     * @param key ConnectionKey identifying this UDP flow
     * @return UdpAssociateConnection or null on failure
     * 
     * Requirements: 1.1, 1.2, 1.3, 1.4, 2.1
     */
    private suspend fun establishUdpAssociate(
        key: ConnectionKey
    ): UdpAssociateConnection? = withContext(Dispatchers.IO) {
        var controlSocket: Socket? = null
        var relaySocket: DatagramSocket? = null
        
        try {
            logger.info(
                TAG,
                "Establishing UDP ASSOCIATE for ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort}"
            )
            
            // Step 1: Create TCP control socket to SOCKS5 proxy
            controlSocket = Socket()
            controlSocket.connect(InetSocketAddress("127.0.0.1", socksPort), DNS_TIMEOUT_MS)
            controlSocket.soTimeout = DNS_TIMEOUT_MS
            
            logger.debug(TAG, "Connected to SOCKS5 proxy at 127.0.0.1:$socksPort")
            
            // Step 2 & 3: Perform UDP ASSOCIATE handshake to get relay endpoint
            // This includes the initial SOCKS5 greeting (VER=0x05, NMETHODS=0x01, METHOD=0x00)
            val relayEndpoint = performUdpAssociateHandshake(controlSocket)
            
            if (relayEndpoint == null) {
                logger.error(TAG, "UDP ASSOCIATE handshake failed")
                controlSocket.close()
                return@withContext null
            }
            
            logger.debug(
                TAG,
                "UDP ASSOCIATE handshake successful: relay endpoint ${relayEndpoint.address}:${relayEndpoint.port}"
            )
            
            // Step 4: Create DatagramSocket for UDP relay communication
            relaySocket = DatagramSocket()
            relaySocket.soTimeout = DNS_TIMEOUT_MS
            
            logger.debug(TAG, "Created UDP relay socket on local port ${relaySocket.localPort}")
            
            // Step 5: Create UdpAssociateConnection object
            val now = System.currentTimeMillis()
            val connection = UdpAssociateConnection(
                key = key,
                controlSocket = controlSocket,
                relaySocket = relaySocket,
                relayEndpoint = relayEndpoint,
                createdAt = now,
                lastActivityAt = now,
                bytesSent = 0,
                bytesReceived = 0,
                readerJob = kotlinx.coroutines.Job() // Placeholder, will be replaced by startUdpReader
            )
            
            // Step 6: Add connection to ConnectionTable
            connectionTable.addUdpAssociateConnection(connection)
            
            logger.info(
                TAG,
                "UDP ASSOCIATE connection established: ${key.sourceIp}:${key.sourcePort} -> " +
                "${key.destIp}:${key.destPort} via relay ${relayEndpoint.address}:${relayEndpoint.port}"
            )
            
            return@withContext connection
            
        } catch (e: java.net.SocketTimeoutException) {
            logger.error(TAG, "UDP ASSOCIATE connection timed out: ${e.message}")
            controlSocket?.close()
            relaySocket?.close()
            return@withContext null
        } catch (e: Exception) {
            logger.error(TAG, "Failed to establish UDP ASSOCIATE: ${e.message}", e)
            controlSocket?.close()
            relaySocket?.close()
            return@withContext null
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
     * Sends a UDP datagram through the SOCKS5 relay.
     * 
     * This method:
     * 1. Encapsulates the UDP packet with SOCKS5 header
     * 2. Creates a DatagramPacket with the encapsulated data
     * 3. Sets destination to the relay endpoint (BND.ADDR:BND.PORT)
     * 4. Sends the datagram through the relay socket
     * 5. Updates connection statistics (bytesSent)
     * 6. Updates lastActivityAt timestamp
     * 
     * Handles IOException gracefully to ensure one failed send doesn't crash the router.
     * 
     * Requirements: 3.5, 5.1, 9.1, 9.4
     * 
     * @param connection The UDP ASSOCIATE connection to use
     * @param destIp Destination IP address
     * @param destPort Destination port
     * @param payload Original UDP payload
     */
    private suspend fun sendUdpThroughSocks5(
        connection: UdpAssociateConnection,
        destIp: String,
        destPort: Int,
        payload: ByteArray
    ) = withContext(Dispatchers.IO) {
        try {
            logger.verbose(
                TAG,
                "Sending UDP through SOCKS5: $destIp:$destPort, payload size: ${payload.size}"
            )
            
            // Step 1: Encapsulate UDP packet with SOCKS5 header
            val encapsulatedData = encapsulateUdpPacket(destIp, destPort, payload)
            
            logger.verbose(
                TAG,
                "Encapsulated packet size: ${encapsulatedData.size} bytes " +
                "(header: ${encapsulatedData.size - payload.size}, payload: ${payload.size})"
            )
            
            // Step 2 & 3: Create DatagramPacket with destination set to relay endpoint
            val relayAddress = java.net.InetAddress.getByName(connection.relayEndpoint.address)
            val datagramPacket = java.net.DatagramPacket(
                encapsulatedData,
                encapsulatedData.size,
                relayAddress,
                connection.relayEndpoint.port
            )
            
            // Step 4: Send datagram through relay socket
            connection.relaySocket.send(datagramPacket)
            
            logger.verbose(
                TAG,
                "Sent UDP datagram to relay endpoint ${connection.relayEndpoint.address}:${connection.relayEndpoint.port}"
            )
            
            // Step 5: Update connection statistics (bytesSent)
            // Count the encapsulated data size (includes SOCKS5 header + payload)
            connection.bytesSent += encapsulatedData.size
            
            // Step 6: Update lastActivityAt timestamp
            connection.lastActivityAt = System.currentTimeMillis()
            
            // Update statistics in ConnectionTable
            connectionTable.updateUdpAssociateStats(
                key = connection.key,
                bytesSent = encapsulatedData.size.toLong()
            )
            
            logger.debug(
                TAG,
                "UDP packet sent successfully: ${encapsulatedData.size} bytes, " +
                "total sent: ${connection.bytesSent} bytes"
            )
            
        } catch (e: IOException) {
            // Handle IOException gracefully - log and continue
            // Requirements: 3.5, 5.1
            logger.error(
                TAG,
                "Failed to send UDP datagram through SOCKS5 relay: ${e.message}",
                e
            )
            // Don't throw - UDP is best-effort, continue processing
            // The connection may be broken, but we'll let the cleanup mechanism handle it
        } catch (e: Exception) {
            // Handle any other unexpected errors
            logger.error(
                TAG,
                "Unexpected error sending UDP through SOCKS5: ${e.message}",
                e
            )
            // Don't throw - continue processing other packets
        }
    }
    
    /**
     * Starts a coroutine to read responses from the UDP relay socket.
     * 
     * This coroutine:
     * 1. Launches in IO dispatcher for non-blocking I/O
     * 2. Creates receive buffer (max UDP size: 65507 bytes)
     * 3. Continuously reads datagrams from relay socket with timeout
     * 4. Decapsulates received packets to extract source and payload
     * 5. Builds IP+UDP response packets with swapped addresses
     * 6. Writes response packets to TUN interface
     * 7. Updates connection statistics (bytesReceived)
     * 8. Handles timeout and IOException gracefully
     * 9. Cleans up connection on error or cancellation
     * 
     * The reader coroutine runs for the lifetime of the UDP ASSOCIATE connection.
     * It terminates when:
     * - The connection is explicitly closed
     * - A fatal error occurs
     * - The coroutine is cancelled
     * 
     * Requirements: 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 9.2
     * 
     * @param key ConnectionKey identifying this UDP flow
     * @param connection The UDP ASSOCIATE connection to read from
     * @param tunOutputStream Output stream to write response packets
     * @return Job representing the reader coroutine
     */
    private fun startUdpReader(
        key: ConnectionKey,
        connection: UdpAssociateConnection,
        tunOutputStream: FileOutputStream
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            // Step 2: Create receive buffer (max UDP size: 65507 bytes)
            // Max UDP datagram size = 65535 (max IP packet) - 20 (IP header) - 8 (UDP header)
            val maxUdpSize = 65507
            val receiveBuffer = ByteArray(maxUdpSize)
            val datagramPacket = java.net.DatagramPacket(receiveBuffer, receiveBuffer.size)
            
            logger.info(
                TAG,
                "Started UDP reader for ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort}"
            )
            
            try {
                // Step 3: Loop: receive datagram from relay socket with timeout
                while (isActive) {
                    try {
                        // Receive datagram from relay socket
                        // Socket timeout is already set in establishUdpAssociate()
                        connection.relaySocket.receive(datagramPacket)
                        
                        val receivedData = datagramPacket.data.copyOfRange(0, datagramPacket.length)
                        
                        logger.verbose(
                            TAG,
                            "Received UDP datagram from relay: ${receivedData.size} bytes"
                        )
                        
                        // Step 4: Decapsulate received packet to extract source and payload
                        val decapsulated = decapsulateUdpPacket(receivedData)
                        
                        if (decapsulated == null) {
                            logger.warn(TAG, "Failed to decapsulate UDP packet, dropping")
                            continue
                        }
                        
                        logger.verbose(
                            TAG,
                            "Decapsulated UDP packet: ${decapsulated.sourceIp}:${decapsulated.sourcePort}, " +
                            "payload size: ${decapsulated.payload.size}"
                        )
                        
                        // Step 5: Build IP+UDP response packet with swapped addresses
                        // Original packet: client -> destination
                        // Response packet: destination -> client
                        // So we swap: destination becomes source, client becomes destination
                        val responsePacket = packetBuilder.buildUdpPacket(
                            sourceIp = decapsulated.sourceIp,      // Destination becomes source
                            sourcePort = decapsulated.sourcePort,  // Destination port
                            destIp = key.sourceIp,                 // Client becomes destination
                            destPort = key.sourcePort,             // Client port
                            payload = decapsulated.payload
                        )
                        
                        logger.verbose(
                            TAG,
                            "Built response packet: ${decapsulated.sourceIp}:${decapsulated.sourcePort} -> " +
                            "${key.sourceIp}:${key.sourcePort}, size: ${responsePacket.size}"
                        )
                        
                        // Step 6: Write response packet to TUN interface
                        withContext(Dispatchers.IO) {
                            tunOutputStream.write(responsePacket)
                            tunOutputStream.flush()
                        }
                        
                        logger.verbose(
                            TAG,
                            "Wrote response packet to TUN interface: ${responsePacket.size} bytes"
                        )
                        
                        // Step 7: Update connection statistics (bytesReceived)
                        connection.bytesReceived += receivedData.size
                        connection.lastActivityAt = System.currentTimeMillis()
                        
                        // Update statistics in ConnectionTable
                        connectionTable.updateUdpAssociateStats(
                            key = key,
                            bytesReceived = receivedData.size.toLong()
                        )
                        
                        logger.debug(
                            TAG,
                            "UDP response processed: ${receivedData.size} bytes received, " +
                            "total received: ${connection.bytesReceived} bytes"
                        )
                        
                    } catch (e: java.net.SocketTimeoutException) {
                        // Step 8: Handle timeout
                        // Timeout is expected - just continue the loop
                        // This allows the coroutine to check isActive and handle cancellation
                        logger.verbose(TAG, "UDP relay socket timeout, continuing...")
                        continue
                        
                    } catch (e: IOException) {
                        // Step 8: Handle IOException
                        // Socket error - connection may be broken
                        if (isActive) {
                            logger.error(
                                TAG,
                                "IOException reading from UDP relay socket: ${e.message}",
                                e
                            )
                            // Break the loop - connection is broken
                            break
                        } else {
                            // Coroutine was cancelled - this is expected
                            logger.debug(TAG, "UDP reader cancelled, stopping")
                            break
                        }
                    }
                }
                
            } catch (e: Exception) {
                // Unexpected error - log and exit
                logger.error(
                    TAG,
                    "Unexpected error in UDP reader: ${e.message}",
                    e
                )
                
            } finally {
                // Step 9: Clean up connection on error or cancellation
                logger.info(
                    TAG,
                    "UDP reader stopping for ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort}, " +
                    "sent: ${connection.bytesSent} bytes, received: ${connection.bytesReceived} bytes"
                )
                
                // Close sockets
                try {
                    connection.relaySocket.close()
                    logger.debug(TAG, "Closed UDP relay socket")
                } catch (e: Exception) {
                    logger.verbose(TAG, "Error closing relay socket: ${e.message}")
                }
                
                try {
                    connection.controlSocket.close()
                    logger.debug(TAG, "Closed TCP control socket")
                } catch (e: Exception) {
                    logger.verbose(TAG, "Error closing control socket: ${e.message}")
                }
                
                // Remove connection from table
                connectionTable.removeUdpAssociateConnection(key)
                
                logger.info(
                    TAG,
                    "UDP ASSOCIATE connection cleaned up: ${key.sourceIp}:${key.sourcePort} -> " +
                    "${key.destIp}:${key.destPort}"
                )
            }
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
