package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPv4Header
import com.sshtunnel.android.vpn.packet.PacketBuilder
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
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
        private const val SOCKS5_CONNECT_TIMEOUT_MS = 5000
    }
    
    private val packetBuilder = PacketBuilder()
    private val handlerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
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
        
        // Create connection key
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = ipHeader.sourceIP,
            sourcePort = tcpHeader.sourcePort,
            destIp = ipHeader.destIP,
            destPort = tcpHeader.destPort
        )
        
        // Handle based on flags
        when {
            tcpHeader.flags.syn && !tcpHeader.flags.ack -> {
                // SYN packet - new connection
                handleSyn(key, tcpHeader, tunOutputStream)
            }
            tcpHeader.flags.rst -> {
                // RST packet - close connection
                // TODO: Implement in task 9
                logger.verbose(TAG, "RST packet received for $key")
            }
            tcpHeader.flags.fin -> {
                // FIN packet - close connection
                // TODO: Implement in task 9
                logger.verbose(TAG, "FIN packet received for $key")
            }
            else -> {
                // Data or ACK packet
                val payload = extractTcpPayload(packet, ipHeader.headerLength, tcpHeader)
                if (payload.isNotEmpty()) {
                    handleData(key, tcpHeader, payload)
                } else {
                    // Pure ACK packet, no data to forward
                    logger.verbose(TAG, "ACK packet received for $key")
                }
            }
        }
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
     * Handles a TCP SYN packet to establish a new connection.
     * 
     * This method:
     * 1. Creates a ConnectionKey from the packet
     * 2. Establishes a SOCKS5 connection to the destination
     * 3. Performs SOCKS5 handshake
     * 4. Adds the connection to the ConnectionTable
     * 5. Starts a connection reader coroutine
     * 6. Sends a SYN-ACK packet back to TUN
     * 
     * Requirements: 3.1, 3.2, 4.1, 4.2, 4.3, 4.4
     * 
     * @param key The connection key (5-tuple)
     * @param tcpHeader The parsed TCP header from the SYN packet
     * @param tunOutputStream Output stream to write response packets
     */
    private suspend fun handleSyn(
        key: ConnectionKey,
        tcpHeader: TcpHeader,
        tunOutputStream: FileOutputStream
    ) {
        logger.debug(TAG, "Handling SYN for new connection: $key")
        
        // Check if connection already exists
        val existing = connectionTable.getTcpConnection(key)
        if (existing != null) {
            logger.verbose(TAG, "Connection already exists for $key, ignoring SYN")
            return
        }
        
        // Establish SOCKS5 connection
        val socksSocket = establishSocks5Connection(key) ?: run {
            logger.error(TAG, "Failed to establish SOCKS5 connection for $key")
            sendTcpRst(tunOutputStream, key)
            return
        }
        
        logger.debug(TAG, "SOCKS5 connection established for $key")
        
        // Initialize sequence numbers
        // Our initial sequence number (random)
        val ourSeqNum = kotlin.random.Random.nextLong(0, 0xFFFFFFFF)
        // Acknowledge their sequence number + 1
        val theirSeqNum = tcpHeader.sequenceNumber
        val ourAckNum = (theirSeqNum + 1) and 0xFFFFFFFF
        
        // Create connection object
        val now = System.currentTimeMillis()
        val readerJob = startConnectionReader(key, socksSocket, tunOutputStream, ourSeqNum, ourAckNum)
        
        val connection = TcpConnection(
            key = key,
            socksSocket = socksSocket,
            state = TcpState.ESTABLISHED,
            sequenceNumber = ourSeqNum + 1, // +1 for SYN
            acknowledgmentNumber = ourAckNum,
            createdAt = now,
            lastActivityAt = now,
            bytesSent = 0,
            bytesReceived = 0,
            readerJob = readerJob
        )
        
        // Add to connection table
        connectionTable.addTcpConnection(connection)
        
        logger.info(TAG, "TCP connection established: $key")
        
        // Send SYN-ACK back to TUN
        sendTcpSynAck(tunOutputStream, key, ourSeqNum, ourAckNum)
    }
    
    /**
     * Handles TCP data packets by forwarding payload to SOCKS5.
     * 
     * This method:
     * 1. Extracts TCP payload from the packet
     * 2. Looks up the connection in ConnectionTable
     * 3. Writes payload to SOCKS5 socket
     * 4. Updates sequence numbers
     * 5. Updates statistics (bytes sent)
     * 
     * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 13.1
     * 
     * @param key The connection key (5-tuple)
     * @param tcpHeader The parsed TCP header
     * @param payload The TCP payload data
     */
    private suspend fun handleData(
        key: ConnectionKey,
        tcpHeader: TcpHeader,
        payload: ByteArray
    ) {
        // Look up connection in ConnectionTable
        val connection = connectionTable.getTcpConnection(key)
        if (connection == null) {
            logger.verbose(TAG, "Received data for unknown connection: $key, dropping")
            return
        }
        
        if (connection.state != TcpState.ESTABLISHED) {
            logger.verbose(TAG, "Received data for non-established connection: $key (state=${connection.state}), dropping")
            return
        }
        
        logger.verbose(TAG, "Forwarding ${payload.size} bytes from TUN to SOCKS5: $key")
        
        try {
            // Write payload to SOCKS5 socket
            withContext(Dispatchers.IO) {
                connection.socksSocket.getOutputStream().write(payload)
                connection.socksSocket.getOutputStream().flush()
            }
            
            // Update acknowledgment number (acknowledge their data)
            val newAckNum = (tcpHeader.sequenceNumber + payload.size) and 0xFFFFFFFF
            
            // Update connection with new acknowledgment number and statistics
            val updatedConnection = connection.copy(
                acknowledgmentNumber = newAckNum,
                lastActivityAt = System.currentTimeMillis(),
                bytesSent = connection.bytesSent + payload.size
            )
            connectionTable.addTcpConnection(updatedConnection)
            
            logger.verbose(TAG, "Data forwarded successfully: $key, ack=$newAckNum")
            
        } catch (e: IOException) {
            logger.error(TAG, "Failed to forward data to SOCKS5 for $key: ${e.message}", e)
            // Connection is broken, remove it
            connectionTable.removeTcpConnection(key)
            try {
                connection.readerJob.cancel()
                connection.socksSocket.close()
            } catch (ex: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * Establishes a SOCKS5 connection to the destination.
     * 
     * @param key The connection key containing destination IP and port
     * @return Connected socket or null if failed
     */
    private suspend fun establishSocks5Connection(key: ConnectionKey): Socket? {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(
                    InetSocketAddress("127.0.0.1", socksPort),
                    SOCKS5_CONNECT_TIMEOUT_MS
                )
                socket.soTimeout = 30000 // 30 second read timeout
                
                // Perform SOCKS5 handshake
                if (performSocks5Handshake(socket, key.destIp, key.destPort)) {
                    socket
                } else {
                    socket.close()
                    null
                }
            } catch (e: Exception) {
                logger.error(TAG, "Failed to establish SOCKS5 connection: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Starts a coroutine to read data from the SOCKS5 socket and forward to TUN.
     * 
     * This coroutine runs for the lifetime of the connection, reading data from
     * the SOCKS5 proxy and constructing TCP packets to send back through the TUN interface.
     * 
     * @param key The connection key
     * @param socket The SOCKS5 socket
     * @param tunOutputStream Output stream to write response packets
     * @param initialSeqNum Initial sequence number for this connection
     * @param initialAckNum Initial acknowledgment number for this connection
     * @return Job for the reader coroutine
     */
    private fun startConnectionReader(
        key: ConnectionKey,
        socket: Socket,
        tunOutputStream: FileOutputStream,
        initialSeqNum: Long,
        initialAckNum: Long
    ): Job {
        return handlerScope.launch {
            var seqNum = initialSeqNum + 1 // +1 for SYN
            val ackNum = initialAckNum
            
            try {
                val inputStream = socket.getInputStream()
                val buffer = ByteArray(8192)
                
                while (true) {
                    val bytesRead = withContext(Dispatchers.IO) {
                        inputStream.read(buffer)
                    }
                    
                    if (bytesRead <= 0) {
                        logger.verbose(TAG, "Connection closed by remote: $key")
                        break
                    }
                    
                    val data = buffer.copyOf(bytesRead)
                    
                    // Send data packet to TUN
                    sendTcpDataPacket(
                        tunOutputStream,
                        key,
                        seqNum,
                        ackNum,
                        data
                    )
                    
                    // Update sequence number
                    seqNum = (seqNum + bytesRead) and 0xFFFFFFFF
                    
                    // Update connection statistics
                    connectionTable.getTcpConnection(key)?.let { conn ->
                        val updated = conn.copy(
                            sequenceNumber = seqNum,
                            lastActivityAt = System.currentTimeMillis(),
                            bytesReceived = conn.bytesReceived + bytesRead
                        )
                        connectionTable.addTcpConnection(updated)
                    }
                }
            } catch (e: IOException) {
                logger.verbose(TAG, "Connection reader error for $key: ${e.message}")
            } finally {
                // Clean up connection
                connectionTable.removeTcpConnection(key)
                try {
                    socket.close()
                } catch (e: Exception) {
                    // Ignore
                }
                logger.debug(TAG, "Connection reader stopped for $key")
            }
        }
    }
    
    /**
     * Sends a TCP SYN-ACK packet to the TUN interface.
     * 
     * @param tunOutputStream Output stream to write the packet
     * @param key The connection key
     * @param seqNum Our sequence number
     * @param ackNum Our acknowledgment number
     */
    private suspend fun sendTcpSynAck(
        tunOutputStream: FileOutputStream,
        key: ConnectionKey,
        seqNum: Long,
        ackNum: Long
    ) {
        val packet = packetBuilder.buildTcpPacket(
            sourceIp = key.destIp,
            sourcePort = key.destPort,
            destIp = key.sourceIp,
            destPort = key.sourcePort,
            sequenceNumber = seqNum,
            acknowledgmentNumber = ackNum,
            flags = TcpFlags(
                fin = false,
                syn = true,
                rst = false,
                psh = false,
                ack = true,
                urg = false
            ),
            windowSize = 65535,
            payload = byteArrayOf()
        )
        
        withContext(Dispatchers.IO) {
            tunOutputStream.write(packet)
            tunOutputStream.flush()
        }
        
        logger.verbose(TAG, "Sent SYN-ACK: $key seq=$seqNum ack=$ackNum")
    }
    
    /**
     * Sends a TCP data packet to the TUN interface.
     * 
     * @param tunOutputStream Output stream to write the packet
     * @param key The connection key
     * @param seqNum Our sequence number
     * @param ackNum Our acknowledgment number
     * @param data The payload data
     */
    private suspend fun sendTcpDataPacket(
        tunOutputStream: FileOutputStream,
        key: ConnectionKey,
        seqNum: Long,
        ackNum: Long,
        data: ByteArray
    ) {
        val packet = packetBuilder.buildTcpPacket(
            sourceIp = key.destIp,
            sourcePort = key.destPort,
            destIp = key.sourceIp,
            destPort = key.sourcePort,
            sequenceNumber = seqNum,
            acknowledgmentNumber = ackNum,
            flags = TcpFlags(
                fin = false,
                syn = false,
                rst = false,
                psh = true,
                ack = true,
                urg = false
            ),
            windowSize = 65535,
            payload = data
        )
        
        withContext(Dispatchers.IO) {
            tunOutputStream.write(packet)
            tunOutputStream.flush()
        }
        
        logger.verbose(TAG, "Sent data packet: $key seq=$seqNum ack=$ackNum len=${data.size}")
    }
    
    /**
     * Sends a TCP RST packet to the TUN interface.
     * 
     * @param tunOutputStream Output stream to write the packet
     * @param key The connection key
     */
    private suspend fun sendTcpRst(
        tunOutputStream: FileOutputStream,
        key: ConnectionKey
    ) {
        val packet = packetBuilder.buildTcpPacket(
            sourceIp = key.destIp,
            sourcePort = key.destPort,
            destIp = key.sourceIp,
            destPort = key.sourcePort,
            sequenceNumber = 0,
            acknowledgmentNumber = 0,
            flags = TcpFlags(
                fin = false,
                syn = false,
                rst = true,
                psh = false,
                ack = false,
                urg = false
            ),
            windowSize = 0,
            payload = byteArrayOf()
        )
        
        withContext(Dispatchers.IO) {
            tunOutputStream.write(packet)
            tunOutputStream.flush()
        }
        
        logger.verbose(TAG, "Sent RST: $key")
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
