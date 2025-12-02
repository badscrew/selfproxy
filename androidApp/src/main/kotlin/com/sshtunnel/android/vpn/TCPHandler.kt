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
     * Wraps all packet processing in error handling to ensure one bad packet
     * doesn't crash the router.
     * 
     * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5
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
        try {
            // Parse TCP header
            val tcpHeader = parseTcpHeader(packet, ipHeader.headerLength) ?: run {
                logger.verbose(TAG, "Failed to parse TCP header, dropping packet")
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
            
            // Get existing connection if any
            val existingConnection = connectionTable.getTcpConnection(key)
            
            // Handle based on flags and current state
            when {
                tcpHeader.flags.syn && !tcpHeader.flags.ack -> {
                    // SYN packet - new connection
                    if (existingConnection == null || existingConnection.state == TcpState.CLOSED) {
                        handleSyn(key, tcpHeader, tunOutputStream)
                    } else {
                        logger.verbose(TAG, "Received SYN for existing connection in state ${existingConnection.state}, ignoring")
                    }
                }
                tcpHeader.flags.rst -> {
                    // RST packet - close connection immediately (valid in any state)
                    handleRst(key, tunOutputStream)
                }
                tcpHeader.flags.fin -> {
                    // FIN packet - graceful connection close
                    if (existingConnection != null && canHandleFin(existingConnection.state)) {
                        handleFin(key, tcpHeader, tunOutputStream)
                    } else {
                        logger.verbose(TAG, "Received FIN for connection in invalid state: ${existingConnection?.state}")
                    }
                }
                else -> {
                    // Data or ACK packet
                    val payload = extractTcpPayload(packet, ipHeader.headerLength, tcpHeader)
                    if (payload.isNotEmpty()) {
                        if (existingConnection != null && existingConnection.state == TcpState.ESTABLISHED) {
                            handleData(key, tcpHeader, payload)
                        } else {
                            logger.verbose(TAG, "Received data for connection in invalid state: ${existingConnection?.state}")
                        }
                    } else {
                        // Pure ACK packet
                        if (existingConnection != null) {
                            handleAck(key, tcpHeader, existingConnection)
                        } else {
                            logger.verbose(TAG, "ACK packet received for unknown connection: $key")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            // Log error with context and continue processing
            // Requirements: 11.1, 11.2, 11.5
            logger.error(
                TAG,
                "Error handling TCP packet from ${ipHeader.sourceIP}:${ipHeader.destIP}: ${e.message}",
                e
            )
            // Continue processing - don't let one bad packet crash the router
        }
    }
    
    /**
     * Checks if a FIN packet can be handled in the current state.
     * 
     * @param state The current TCP state
     * @return true if FIN can be handled, false otherwise
     */
    private fun canHandleFin(state: TcpState): Boolean {
        return state == TcpState.ESTABLISHED || state == TcpState.FIN_WAIT_1 || state == TcpState.FIN_WAIT_2
    }
    
    /**
     * Handles a pure ACK packet (no data).
     * 
     * @param key The connection key
     * @param tcpHeader The parsed TCP header
     * @param connection The existing connection
     */
    private suspend fun handleAck(
        key: ConnectionKey,
        tcpHeader: TcpHeader,
        connection: TcpConnection
    ) {
        logger.verbose(TAG, "ACK packet received for $key in state ${connection.state}")
        
        // Update last activity time
        val updatedConnection = connection.copy(
            lastActivityAt = System.currentTimeMillis()
        )
        connectionTable.addTcpConnection(updatedConnection)
        
        // Handle state-specific ACK processing
        when (connection.state) {
            TcpState.FIN_WAIT_1 -> {
                // ACK for our FIN, transition to FIN_WAIT_2
                val transitioned = connection.copy(
                    state = TcpState.FIN_WAIT_2,
                    lastActivityAt = System.currentTimeMillis()
                )
                connectionTable.addTcpConnection(transitioned)
                logger.debug(TAG, "Connection transitioned to FIN_WAIT_2: $key")
            }
            TcpState.CLOSING -> {
                // ACK for our FIN in simultaneous close, transition to TIME_WAIT
                val transitioned = connection.copy(
                    state = TcpState.TIME_WAIT,
                    lastActivityAt = System.currentTimeMillis()
                )
                connectionTable.addTcpConnection(transitioned)
                logger.debug(TAG, "Connection transitioned to TIME_WAIT: $key")
            }
            else -> {
                // Normal ACK in other states, just update activity time
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
     * 4. Adds the connection to the ConnectionTable with SYN_SENT state
     * 5. Starts a connection reader coroutine
     * 6. Sends a SYN-ACK packet back to TUN
     * 7. Transitions to ESTABLISHED state
     * 
     * On any error, sends RST packet to client and cleans up.
     * 
     * Requirements: 3.1, 3.2, 4.1, 4.2, 4.3, 4.4, 4.5, 11.2, 12.2, 12.4
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
        try {
            logger.info(
                TAG,
                "Establishing TCP connection: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort}"
            )
            
            // Check if connection already exists
            val existing = connectionTable.getTcpConnection(key)
            if (existing != null && existing.state != TcpState.CLOSED) {
                logger.verbose(TAG, "Connection already exists for $key in state ${existing.state}, ignoring SYN")
                return
            }
            
            // Initialize sequence numbers
            // Our initial sequence number (random)
            val ourSeqNum = kotlin.random.Random.nextLong(0, 0xFFFFFFFF)
            // Acknowledge their sequence number + 1
            val theirSeqNum = tcpHeader.sequenceNumber
            val ourAckNum = (theirSeqNum + 1) and 0xFFFFFFFF
            
            // Create a placeholder connection in SYN_SENT state
            // This is needed because SOCKS5 connection might take time
            val now = System.currentTimeMillis()
            val placeholderConnection = TcpConnection(
                key = key,
                socksSocket = Socket(), // Placeholder, will be replaced
                state = TcpState.SYN_SENT,
                sequenceNumber = ourSeqNum,
                acknowledgmentNumber = ourAckNum,
                createdAt = now,
                lastActivityAt = now,
                bytesSent = 0,
                bytesReceived = 0,
                readerJob = handlerScope.launch { } // Placeholder job
            )
            connectionTable.addTcpConnection(placeholderConnection)
            
            logger.debug(TAG, "Connection transitioned to SYN_SENT: $key")
            
            // Establish SOCKS5 connection
            val socksSocket = establishSocks5Connection(key)
            if (socksSocket == null) {
                // SOCKS5 connection failed - send RST and clean up
                // Requirements: 4.5, 11.2
                logger.error(TAG, "Failed to establish SOCKS5 connection for $key, sending RST")
                connectionTable.removeTcpConnection(key)
                sendTcpRst(tunOutputStream, key)
                return
            }
            
            logger.debug(TAG, "SOCKS5 connection established for $key")
            
            // Start connection reader
            val readerJob = startConnectionReader(key, socksSocket, tunOutputStream, ourSeqNum, ourAckNum)
            
            // Update connection to ESTABLISHED state
            val connection = TcpConnection(
                key = key,
                socksSocket = socksSocket,
                state = TcpState.ESTABLISHED,
                sequenceNumber = ourSeqNum + 1, // +1 for SYN
                acknowledgmentNumber = ourAckNum,
                createdAt = now,
                lastActivityAt = System.currentTimeMillis(),
                bytesSent = 0,
                bytesReceived = 0,
                readerJob = readerJob
            )
            
            // Add to connection table
            connectionTable.addTcpConnection(connection)
            
            logger.info(
                TAG,
                "TCP connection established: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort} " +
                "(seq=$ourSeqNum, ack=$ourAckNum, state=ESTABLISHED)"
            )
            
            // Send SYN-ACK back to TUN
            sendTcpSynAck(tunOutputStream, key, ourSeqNum, ourAckNum)
            
        } catch (e: Exception) {
            // Handle any unexpected errors during connection establishment
            // Requirements: 11.1, 11.2, 11.5
            logger.error(TAG, "Error establishing TCP connection for $key: ${e.message}", e)
            
            // Clean up and send RST
            connectionTable.removeTcpConnection(key)
            try {
                sendTcpRst(tunOutputStream, key)
            } catch (rstError: Exception) {
                logger.error(TAG, "Failed to send RST for $key: ${rstError.message}", rstError)
            }
        }
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
     * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 13.1, 12.5
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
        
        // Log data forwarding (NEVER log payload content for privacy - only size)
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
     * Handles a TCP FIN packet for graceful connection termination.
     * 
     * This method implements proper TCP state transitions:
     * - ESTABLISHED -> FIN_WAIT_1 (we send FIN-ACK)
     * - FIN_WAIT_1 -> CLOSING (simultaneous close)
     * - FIN_WAIT_2 -> TIME_WAIT (normal close)
     * 
     * Requirements: 3.3, 3.4, 9.2
     * 
     * @param key The connection key (5-tuple)
     * @param tcpHeader The parsed TCP header from the FIN packet
     * @param tunOutputStream Output stream to write response packets
     */
    internal suspend fun handleFin(
        key: ConnectionKey,
        tcpHeader: TcpHeader,
        tunOutputStream: FileOutputStream
    ) {
        logger.debug(TAG, "Handling FIN for connection: $key")
        
        // Look up connection in ConnectionTable
        val connection = connectionTable.getTcpConnection(key)
        if (connection == null) {
            logger.verbose(TAG, "Received FIN for unknown connection: $key, ignoring")
            return
        }
        
        // Calculate acknowledgment number (acknowledge their FIN)
        val ackNum = (tcpHeader.sequenceNumber + 1) and 0xFFFFFFFF
        
        // Handle based on current state
        when (connection.state) {
            TcpState.ESTABLISHED -> {
                // Normal close: ESTABLISHED -> FIN_WAIT_1
                logger.debug(TAG, "Connection $key: ESTABLISHED -> FIN_WAIT_1")
                
                try {
                    // Send FIN to SOCKS5 socket (shutdown output, half-close)
                    withContext(Dispatchers.IO) {
                        try {
                            connection.socksSocket.shutdownOutput()
                            logger.verbose(TAG, "Sent FIN to SOCKS5 for $key")
                        } catch (e: Exception) {
                            logger.verbose(TAG, "Failed to shutdown SOCKS5 output for $key: ${e.message}")
                        }
                    }
                    
                    // Update TCP state to FIN_WAIT_1
                    val updatedConnection = connection.copy(
                        state = TcpState.FIN_WAIT_1,
                        acknowledgmentNumber = ackNum,
                        lastActivityAt = System.currentTimeMillis()
                    )
                    connectionTable.addTcpConnection(updatedConnection)
                    
                    // Send FIN-ACK back to TUN
                    sendTcpFinAck(
                        tunOutputStream,
                        key,
                        connection.sequenceNumber,
                        ackNum
                    )
                    
                    logger.info(TAG, "Sent FIN-ACK for connection: $key, state=FIN_WAIT_1")
                    
                } catch (e: Exception) {
                    logger.error(TAG, "Error handling FIN for $key: ${e.message}", e)
                    // Clean up on error
                    cleanupConnection(key, connection, "error")
                }
            }
            
            TcpState.FIN_WAIT_1 -> {
                // Simultaneous close: FIN_WAIT_1 -> CLOSING
                logger.debug(TAG, "Connection $key: FIN_WAIT_1 -> CLOSING (simultaneous close)")
                
                val updatedConnection = connection.copy(
                    state = TcpState.CLOSING,
                    acknowledgmentNumber = ackNum,
                    lastActivityAt = System.currentTimeMillis()
                )
                connectionTable.addTcpConnection(updatedConnection)
                
                // Send ACK for their FIN
                sendTcpAck(tunOutputStream, key, connection.sequenceNumber, ackNum)
                
                logger.info(TAG, "Sent ACK for simultaneous close: $key, state=CLOSING")
            }
            
            TcpState.FIN_WAIT_2 -> {
                // Normal close completion: FIN_WAIT_2 -> TIME_WAIT
                logger.debug(TAG, "Connection $key: FIN_WAIT_2 -> TIME_WAIT")
                
                val updatedConnection = connection.copy(
                    state = TcpState.TIME_WAIT,
                    acknowledgmentNumber = ackNum,
                    lastActivityAt = System.currentTimeMillis()
                )
                connectionTable.addTcpConnection(updatedConnection)
                
                // Send ACK for their FIN
                sendTcpAck(tunOutputStream, key, connection.sequenceNumber, ackNum)
                
                logger.info(TAG, "Sent ACK for FIN: $key, state=TIME_WAIT")
                
                // Clean up after a short delay (simplified TIME_WAIT)
                handlerScope.launch {
                    kotlinx.coroutines.delay(1000) // 1 second instead of 2*MSL
                    cleanupConnection(key, connection, "FIN")
                }
            }
            
            else -> {
                logger.verbose(TAG, "Received FIN in unexpected state ${connection.state} for $key")
            }
        }
    }
    
    /**
     * Cleans up a connection by removing it from the table and closing resources.
     * 
     * Requirements: 12.3
     * 
     * @param key The connection key
     * @param connection The connection to clean up
     * @param reason The reason for closure (e.g., "FIN", "RST", "timeout", "error")
     */
    private suspend fun cleanupConnection(
        key: ConnectionKey,
        connection: TcpConnection,
        reason: String = "normal"
    ) {
        connectionTable.removeTcpConnection(key)
        
        try {
            connection.readerJob.cancel()
            connection.socksSocket.close()
        } catch (e: Exception) {
            logger.verbose(TAG, "Error closing connection resources for $key: ${e.message}")
        }
        
        // Calculate connection duration
        val duration = System.currentTimeMillis() - connection.createdAt
        val durationSeconds = duration / 1000.0
        
        logger.info(
            TAG,
            "TCP connection closed: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort} " +
            "(reason=$reason, duration=${String.format("%.2f", durationSeconds)}s, " +
            "sent=${connection.bytesSent} bytes, received=${connection.bytesReceived} bytes)"
        )
    }
    
    /**
     * Handles a TCP RST packet for immediate connection termination.
     * 
     * This method:
     * 1. Looks up the connection in ConnectionTable
     * 2. Closes SOCKS5 socket immediately
     * 3. Removes connection from ConnectionTable
     * 4. Cancels connection reader coroutine
     * 
     * No response packet is sent for RST (RST is not acknowledged).
     * 
     * Requirements: 3.4, 9.2, 12.3
     * 
     * @param key The connection key (5-tuple)
     * @param tunOutputStream Output stream (not used for RST, but kept for consistency)
     */
    internal suspend fun handleRst(
        key: ConnectionKey,
        tunOutputStream: FileOutputStream
    ) {
        logger.debug(TAG, "Handling RST for connection: $key")
        
        // Look up connection in ConnectionTable
        val connection = connectionTable.removeTcpConnection(key)
        if (connection == null) {
            logger.verbose(TAG, "Received RST for unknown connection: $key, ignoring")
            return
        }
        
        // Calculate connection duration
        val duration = System.currentTimeMillis() - connection.createdAt
        val durationSeconds = duration / 1000.0
        
        // Close SOCKS5 socket immediately
        try {
            connection.readerJob.cancel()
            connection.socksSocket.close()
        } catch (e: Exception) {
            logger.verbose(TAG, "Error closing connection for RST $key: ${e.message}")
        }
        
        logger.info(
            TAG,
            "TCP connection reset: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort} " +
            "(reason=RST, duration=${String.format("%.2f", durationSeconds)}s, " +
            "sent=${connection.bytesSent} bytes, received=${connection.bytesReceived} bytes)"
        )
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
     * Requirements: 12.5
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
                    
                    // Copy data (NEVER log payload content for privacy - only size)
                    val data = buffer.copyOf(bytesRead)
                    logger.verbose(TAG, "Received $bytesRead bytes from SOCKS5 for $key")
                    
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
                val connection = connectionTable.removeTcpConnection(key)
                try {
                    socket.close()
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Log connection closure with duration
                if (connection != null) {
                    val duration = System.currentTimeMillis() - connection.createdAt
                    val durationSeconds = duration / 1000.0
                    logger.info(
                        TAG,
                        "TCP connection closed: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort} " +
                        "(reason=remote_close, duration=${String.format("%.2f", durationSeconds)}s, " +
                        "sent=${connection.bytesSent} bytes, received=${connection.bytesReceived} bytes)"
                    )
                } else {
                    logger.debug(TAG, "Connection reader stopped for $key")
                }
            }
        }
    }
    
    /**
     * Sends a TCP SYN-ACK packet to the TUN interface.
     * 
     * Handles TUN interface write errors gracefully.
     * 
     * Requirements: 11.3, 11.4
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
        try {
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
            
        } catch (e: IOException) {
            // TUN interface write error - log and close connection
            // Requirements: 11.4
            logger.error(TAG, "Failed to write SYN-ACK to TUN for $key: ${e.message}", e)
            // Connection will be cleaned up by caller
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "Unexpected error sending SYN-ACK for $key: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Sends a TCP data packet to the TUN interface.
     * 
     * Handles TUN interface write errors gracefully.
     * 
     * Requirements: 11.3, 11.4
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
        try {
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
            
        } catch (e: IOException) {
            // TUN interface write error - log and propagate
            // Requirements: 11.4
            logger.error(TAG, "Failed to write data packet to TUN for $key: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "Unexpected error sending data packet for $key: ${e.message}", e)
            throw e
        }
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
     * Sends a TCP FIN-ACK packet to the TUN interface.
     * 
     * @param tunOutputStream Output stream to write the packet
     * @param key The connection key
     * @param seqNum Our sequence number
     * @param ackNum Our acknowledgment number
     */
    private suspend fun sendTcpFinAck(
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
                fin = true,
                syn = false,
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
        
        logger.verbose(TAG, "Sent FIN-ACK: $key seq=$seqNum ack=$ackNum")
    }
    
    /**
     * Sends a pure TCP ACK packet to the TUN interface.
     * 
     * @param tunOutputStream Output stream to write the packet
     * @param key The connection key
     * @param seqNum Our sequence number
     * @param ackNum Our acknowledgment number
     */
    private suspend fun sendTcpAck(
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
                syn = false,
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
        
        logger.verbose(TAG, "Sent ACK: $key seq=$seqNum ack=$ackNum")
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
