package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPv4Header
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Handles TCP packet parsing and processing.
 * 
 * This class is responsible for:
 * - Parsing TCP headers from IP packets
 * - Extracting TCP flags, sequence numbers, and payload
 * - Managing TCP connections through SOCKS5
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
 */
class TCPHandler(
    private val socksPort: Int,
    private val connectionTable: ConnectionTable,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "TCPHandler"
    }
    
    /**
     * Handles a TCP packet from the TUN interface.
     * 
     * @param packet The raw IP packet bytes
     * @param ipHeader The parsed IP header
     * @param tunOutputStream Output stream to write response packets
     */
    suspend fun handleTcpPacket(
        packet: ByteArray,
        ipHeader: IPv4Header,
        tunOutputStream: FileOutputStream
    ) {
        // Parse TCP header
        val tcpHeader = parseTcpHeader(packet, ipHeader.headerLength) ?: run {
            logger.verbose(TAG, "Failed to parse TCP header")
            return
        }
        
        logger.verbose(
            TAG,
            "TCP packet: ${ipHeader.sourceIP}:${tcpHeader.sourcePort} -> " +
            "${ipHeader.destIP}:${tcpHeader.destPort} " +
            "flags=${formatFlags(tcpHeader.flags)} " +
            "seq=${tcpHeader.sequenceNumber} ack=${tcpHeader.acknowledgmentNumber}"
        )
        
        // TODO: Implement connection handling in future tasks
        // - handleSyn() for SYN packets
        // - handleData() for data packets
        // - handleFin() for FIN packets
        // - handleRst() for RST packets
    }
    
    /**
     * Parses a TCP header from an IP packet.
     * 
     * TCP Header Format (20 bytes minimum):
     * 0                   1                   2                   3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |          Source Port          |       Destination Port        |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                        Sequence Number                        |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                    Acknowledgment Number                      |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |  Data |           |U|A|P|R|S|F|                               |
     * | Offset| Reserved  |R|C|S|S|Y|I|            Window             |
     * |       |           |G|K|H|T|N|N|                               |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |           Checksum            |         Urgent Pointer        |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * 
     * @param packet The raw IP packet bytes
     * @param ipHeaderLength The length of the IP header in bytes
     * @return Parsed TcpHeader or null if invalid
     */
    fun parseTcpHeader(packet: ByteArray, ipHeaderLength: Int): TcpHeader? {
        val tcpStart = ipHeaderLength
        
        // Minimum TCP header is 20 bytes
        if (packet.size < tcpStart + 20) {
            return null
        }
        
        // Extract source port (bytes 0-1)
        val sourcePort = ((packet[tcpStart].toInt() and 0xFF) shl 8) or 
                        (packet[tcpStart + 1].toInt() and 0xFF)
        
        // Extract destination port (bytes 2-3)
        val destPort = ((packet[tcpStart + 2].toInt() and 0xFF) shl 8) or 
                      (packet[tcpStart + 3].toInt() and 0xFF)
        
        // Extract sequence number (bytes 4-7)
        val sequenceNumber = (
            ((packet[tcpStart + 4].toLong() and 0xFF) shl 24) or
            ((packet[tcpStart + 5].toLong() and 0xFF) shl 16) or
            ((packet[tcpStart + 6].toLong() and 0xFF) shl 8) or
            (packet[tcpStart + 7].toLong() and 0xFF)
        ) and 0xFFFFFFFFL
        
        // Extract acknowledgment number (bytes 8-11)
        val acknowledgmentNumber = (
            ((packet[tcpStart + 8].toLong() and 0xFF) shl 24) or
            ((packet[tcpStart + 9].toLong() and 0xFF) shl 16) or
            ((packet[tcpStart + 10].toLong() and 0xFF) shl 8) or
            (packet[tcpStart + 11].toLong() and 0xFF)
        ) and 0xFFFFFFFFL
        
        // Extract data offset (byte 12, upper 4 bits)
        // Data offset is in 32-bit words, so multiply by 4 to get bytes
        val dataOffset = ((packet[tcpStart + 12].toInt() shr 4) and 0x0F) * 4
        
        // Validate data offset
        if (dataOffset < 20 || tcpStart + dataOffset > packet.size) {
            return null
        }
        
        // Extract flags (byte 13, lower 6 bits)
        val flagsByte = packet[tcpStart + 13].toInt() and 0x3F
        val flags = TcpFlags.fromByte(flagsByte)
        
        // Extract window size (bytes 14-15)
        val windowSize = ((packet[tcpStart + 14].toInt() and 0xFF) shl 8) or 
                        (packet[tcpStart + 15].toInt() and 0xFF)
        
        // Extract checksum (bytes 16-17)
        val checksum = ((packet[tcpStart + 16].toInt() and 0xFF) shl 8) or 
                      (packet[tcpStart + 17].toInt() and 0xFF)
        
        // Extract urgent pointer (bytes 18-19)
        val urgentPointer = ((packet[tcpStart + 18].toInt() and 0xFF) shl 8) or 
                           (packet[tcpStart + 19].toInt() and 0xFF)
        
        return TcpHeader(
            sourcePort = sourcePort,
            destPort = destPort,
            sequenceNumber = sequenceNumber,
            acknowledgmentNumber = acknowledgmentNumber,
            dataOffset = dataOffset,
            flags = flags,
            windowSize = windowSize,
            checksum = checksum,
            urgentPointer = urgentPointer
        )
    }
    
    /**
     * Extracts the TCP payload from a packet.
     * 
     * @param packet The raw IP packet bytes
     * @param ipHeaderLength The length of the IP header in bytes
     * @param tcpHeader The parsed TCP header
     * @return TCP payload bytes, or empty array if no payload
     */
    fun extractTcpPayload(
        packet: ByteArray,
        ipHeaderLength: Int,
        tcpHeader: TcpHeader
    ): ByteArray {
        val payloadStart = ipHeaderLength + tcpHeader.dataOffset
        
        if (payloadStart >= packet.size) {
            return ByteArray(0)
        }
        
        return packet.copyOfRange(payloadStart, packet.size)
    }
    
    /**
     * Performs SOCKS5 handshake for a TCP connection.
     * 
     * SOCKS5 Protocol:
     * 1. Client sends greeting: [VER(0x05), NMETHODS(0x01), METHODS(0x00 = no auth)]
     * 2. Server responds: [VER(0x05), METHOD(0x00)]
     * 3. Client sends CONNECT request: [VER, CMD(0x01), RSV(0x00), ATYP(0x01), DST.ADDR, DST.PORT]
     * 4. Server responds: [VER, REP, RSV, ATYP, BND.ADDR, BND.PORT]
     *    REP values: 0x00 = success, 0x01 = general failure, 0x02 = not allowed,
     *                0x03 = network unreachable, 0x04 = host unreachable, 0x05 = connection refused,
     *                0x06 = TTL expired, 0x07 = command not supported, 0x08 = address type not supported
     * 
     * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5
     * 
     * @param socket The socket connected to the SOCKS5 proxy
     * @param destIp The destination IP address
     * @param destPort The destination port
     * @return true if handshake succeeded, false otherwise
     */
    suspend fun performSocks5Handshake(
        socket: Socket,
        destIp: String,
        destPort: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            
            // 1. Send greeting: [VER, NMETHODS, METHODS]
            logger.verbose(TAG, "SOCKS5: Sending greeting to proxy")
            output.write(byteArrayOf(0x05, 0x01, 0x00)) // Version 5, 1 method, No auth
            output.flush()
            
            // 2. Read method selection: [VER, METHOD]
            val greeting = ByteArray(2)
            val greetingRead = input.read(greeting)
            if (greetingRead != 2) {
                logger.error(TAG, "SOCKS5 greeting failed: expected 2 bytes, got $greetingRead")
                return@withContext false
            }
            
            if (greeting[0] != 0x05.toByte()) {
                logger.error(TAG, "SOCKS5 greeting failed: invalid version ${greeting[0]}")
                return@withContext false
            }
            
            if (greeting[1] != 0x00.toByte()) {
                logger.error(TAG, "SOCKS5 greeting failed: authentication required (method=${greeting[1]})")
                return@withContext false
            }
            
            logger.verbose(TAG, "SOCKS5: Greeting successful, sending CONNECT request")
            
            // 3. Send connect request: [VER, CMD, RSV, ATYP, DST.ADDR, DST.PORT]
            val ipBytes = InetAddress.getByName(destIp).address
            val portBytes = ByteBuffer.allocate(2).putShort(destPort.toShort()).array()
            
            val request = byteArrayOf(
                0x05,  // Version
                0x01,  // CMD: CONNECT
                0x00,  // Reserved
                0x01   // ATYP: IPv4
            ) + ipBytes + portBytes
            
            output.write(request)
            output.flush()
            
            // 4. Read response: [VER, REP, RSV, ATYP, BND.ADDR, BND.PORT]
            val response = ByteArray(10)
            val responseRead = input.read(response)
            
            if (responseRead < 10) {
                logger.error(TAG, "SOCKS5 connect failed: incomplete response (got $responseRead bytes)")
                return@withContext false
            }
            
            if (response[0] != 0x05.toByte()) {
                logger.error(TAG, "SOCKS5 connect failed: invalid version ${response[0]}")
                return@withContext false
            }
            
            val replyCode = response[1].toInt() and 0xFF
            if (replyCode != 0x00) {
                val errorMessage = when (replyCode) {
                    0x01 -> "general SOCKS server failure"
                    0x02 -> "connection not allowed by ruleset"
                    0x03 -> "network unreachable"
                    0x04 -> "host unreachable"
                    0x05 -> "connection refused"
                    0x06 -> "TTL expired"
                    0x07 -> "command not supported"
                    0x08 -> "address type not supported"
                    else -> "unknown error code $replyCode"
                }
                logger.error(TAG, "SOCKS5 connect failed: $errorMessage (code=0x${replyCode.toString(16)})")
                return@withContext false
            }
            
            logger.debug(TAG, "SOCKS5 handshake successful: $destIp:$destPort")
            true
        } catch (e: Exception) {
            logger.error(TAG, "SOCKS5 handshake error: ${e.message}", e)
            false
        }
    }
    
    /**
     * Formats TCP flags for logging.
     */
    private fun formatFlags(flags: TcpFlags): String {
        val parts = mutableListOf<String>()
        if (flags.syn) parts.add("SYN")
        if (flags.ack) parts.add("ACK")
        if (flags.fin) parts.add("FIN")
        if (flags.rst) parts.add("RST")
        if (flags.psh) parts.add("PSH")
        if (flags.urg) parts.add("URG")
        return parts.joinToString(",")
    }
}
