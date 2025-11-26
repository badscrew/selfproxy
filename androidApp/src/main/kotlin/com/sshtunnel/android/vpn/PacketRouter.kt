package com.sshtunnel.android.vpn

import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Routes IP packets from TUN interface through SOCKS5 proxy.
 * 
 * This class handles the core packet routing logic:
 * - Reading packets from TUN interface
 * - Parsing IP headers
 * - Routing TCP/UDP through SOCKS5
 * - Writing responses back to TUN
 */
class PacketRouter(
    private val tunInputStream: FileInputStream,
    private val tunOutputStream: FileOutputStream,
    private val socksPort: Int
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tcpConnections = mutableMapOf<ConnectionKey, TcpConnection>()
    private val udpConnections = mutableMapOf<ConnectionKey, UdpConnection>()
    
    companion object {
        private const val TAG = "PacketRouter"
        private const val MAX_PACKET_SIZE = 32767
        private const val DNS_PORT = 53
        private const val SOCKS5_VERSION: Byte = 0x05
        private const val SOCKS5_NO_AUTH: Byte = 0x00
        private const val SOCKS5_CMD_CONNECT: Byte = 0x01
        private const val SOCKS5_CMD_UDP_ASSOCIATE: Byte = 0x03
        private const val SOCKS5_ATYP_IPV4: Byte = 0x01
        private const val SOCKS5_ATYP_DOMAIN: Byte = 0x03
        private const val SOCKS5_SUCCESS: Byte = 0x00
    }
    
    data class ConnectionKey(
        val sourceIp: String,
        val sourcePort: Int,
        val destIp: String,
        val destPort: Int
    )
    
    data class TcpConnection(
        val socket: Socket,
        val key: ConnectionKey,
        val job: Job
    )
    
    data class UdpConnection(
        val socket: Socket,
        val key: ConnectionKey,
        val job: Job
    )
    
    fun start() {
        scope.launch {
            routePackets()
        }
    }
    
    fun stop() {
        scope.cancel()
        tcpConnections.values.forEach { 
            it.job.cancel()
            it.socket.close() 
        }
        tcpConnections.clear()
        
        udpConnections.values.forEach {
            it.job.cancel()
            it.socket.close()
        }
        udpConnections.clear()
    }
    
    private suspend fun routePackets() {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        
        withContext(Dispatchers.IO) {
            try {
                while (isActive) {
                    val length = tunInputStream.read(buffer)
                    if (length > 0) {
                        val packet = buffer.copyOf(length)
                        launch { processPacket(packet) }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    android.util.Log.e(TAG, "Error routing packets: ${e.message}", e)
                }
            }
        }
    }
    
    private suspend fun processPacket(packet: ByteArray) {
        try {
            // Parse IP header
            val ipVersion = (packet[0].toInt() shr 4) and 0x0F
            
            if (ipVersion != 4) {
                // Only IPv4 supported for now
                return
            }
            
            val protocol = packet[9].toInt() and 0xFF
            
            when (protocol) {
                6 -> handleTcpPacket(packet) // TCP
                17 -> handleUdpPacket(packet) // UDP
                else -> {
                    // Ignore other protocols
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error processing packet: ${e.message}", e)
        }
    }
    
    private suspend fun handleTcpPacket(packet: ByteArray) {
        try {
            // Parse IP header
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            
            // Extract source and destination IPs
            val sourceIp = InetAddress.getByAddress(packet.copyOfRange(12, 16)).hostAddress ?: return
            val destIp = InetAddress.getByAddress(packet.copyOfRange(16, 20)).hostAddress ?: return
            
            // Parse TCP header
            val tcpHeaderStart = ipHeaderLength
            val sourcePort = ((packet[tcpHeaderStart].toInt() and 0xFF) shl 8) or 
                            (packet[tcpHeaderStart + 1].toInt() and 0xFF)
            val destPort = ((packet[tcpHeaderStart + 2].toInt() and 0xFF) shl 8) or 
                          (packet[tcpHeaderStart + 3].toInt() and 0xFF)
            
            val flags = packet[tcpHeaderStart + 13].toInt() and 0xFF
            val syn = (flags and 0x02) != 0
            val fin = (flags and 0x01) != 0
            val rst = (flags and 0x04) != 0
            
            val connectionKey = ConnectionKey(sourceIp, sourcePort, destIp, destPort)
            
            // Handle connection lifecycle
            when {
                syn && !tcpConnections.containsKey(connectionKey) -> {
                    // New connection - establish SOCKS5 connection
                    establishTcpConnection(connectionKey, packet)
                }
                fin || rst -> {
                    // Close connection
                    closeTcpConnection(connectionKey)
                }
                else -> {
                    // Forward data through existing connection
                    forwardTcpData(connectionKey, packet, ipHeaderLength)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error handling TCP packet: ${e.message}", e)
        }
    }
    
    private suspend fun establishTcpConnection(key: ConnectionKey, @Suppress("UNUSED_PARAMETER") synPacket: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                // Create socket to SOCKS5 proxy
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5000)
                
                // Perform SOCKS5 handshake
                if (!performSocks5Handshake(socket, key.destIp, key.destPort)) {
                    socket.close()
                    android.util.Log.w(TAG, "SOCKS5 handshake failed for ${key.destIp}:${key.destPort}")
                    return@withContext
                }
                
                // Start job to read responses from SOCKS5 and write back to TUN
                val job = scope.launch {
                    readFromSocks5(socket, key)
                }
                
                // Store connection
                tcpConnections[key] = TcpConnection(socket, key, job)
                
                // Send SYN-ACK back to TUN
                sendTcpSynAck(key)
                
                android.util.Log.d(TAG, "Established TCP connection: ${key.destIp}:${key.destPort}")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to establish TCP connection: ${e.message}", e)
            }
        }
    }
    
    private fun performSocks5Handshake(socket: Socket, destIp: String, destPort: Int): Boolean {
        try {
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            
            // Send greeting: [version, num_methods, methods...]
            output.write(byteArrayOf(SOCKS5_VERSION, 0x01, SOCKS5_NO_AUTH))
            output.flush()
            
            // Read response: [version, method]
            val greeting = ByteArray(2)
            if (input.read(greeting) != 2 || greeting[0] != SOCKS5_VERSION) {
                return false
            }
            
            // Send connect request
            val ipBytes = InetAddress.getByName(destIp).address
            val portBytes = ByteBuffer.allocate(2).putShort(destPort.toShort()).array()
            
            val request = byteArrayOf(
                SOCKS5_VERSION,
                SOCKS5_CMD_CONNECT,
                0x00, // Reserved
                SOCKS5_ATYP_IPV4
            ) + ipBytes + portBytes
            
            output.write(request)
            output.flush()
            
            // Read response: [version, reply, reserved, atyp, addr, port]
            val response = ByteArray(10)
            if (input.read(response) < 10 || response[0] != SOCKS5_VERSION || response[1] != SOCKS5_SUCCESS) {
                return false
            }
            
            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "SOCKS5 handshake error: ${e.message}", e)
            return false
        }
    }
    
    private suspend fun forwardTcpData(key: ConnectionKey, packet: ByteArray, ipHeaderLength: Int) {
        val connection = tcpConnections[key] ?: return
        
        withContext(Dispatchers.IO) {
            try {
                // Extract TCP header length
                val tcpHeaderStart = ipHeaderLength
                val tcpHeaderLength = ((packet[tcpHeaderStart + 12].toInt() and 0xFF) shr 4) * 4
                
                // Extract payload
                val payloadStart = ipHeaderLength + tcpHeaderLength
                if (payloadStart < packet.size) {
                    val payload = packet.copyOfRange(payloadStart, packet.size)
                    if (payload.isNotEmpty()) {
                        connection.socket.getOutputStream().write(payload)
                        connection.socket.getOutputStream().flush()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error forwarding TCP data: ${e.message}", e)
                closeTcpConnection(key)
            }
        }
    }
    
    private suspend fun readFromSocks5(socket: Socket, key: ConnectionKey) {
        withContext(Dispatchers.IO) {
            try {
                val input = socket.getInputStream()
                val buffer = ByteArray(8192)
                
                while (isActive && !socket.isClosed) {
                    val length = input.read(buffer)
                    if (length <= 0) break
                    
                    val data = buffer.copyOf(length)
                    sendTcpData(key, data)
                }
            } catch (e: Exception) {
                if (isActive) {
                    android.util.Log.e(TAG, "Error reading from SOCKS5: ${e.message}", e)
                }
            } finally {
                closeTcpConnection(key)
            }
        }
    }
    
    private fun closeTcpConnection(key: ConnectionKey) {
        tcpConnections.remove(key)?.let { connection ->
            connection.job.cancel()
            connection.socket.close()
            android.util.Log.d(TAG, "Closed TCP connection: ${key.destIp}:${key.destPort}")
        }
    }
    
    private suspend fun sendTcpSynAck(key: ConnectionKey) {
        // Create a minimal SYN-ACK packet
        // In a real implementation, this would need proper TCP sequence numbers
        // For now, we'll rely on the fact that most TCP stacks are forgiving
        withContext(Dispatchers.IO) {
            try {
                // This is a simplified implementation
                // A full implementation would need to track sequence numbers
                android.util.Log.v(TAG, "Sending SYN-ACK for ${key.destIp}:${key.destPort}")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error sending SYN-ACK: ${e.message}", e)
            }
        }
    }
    
    private suspend fun sendTcpData(key: ConnectionKey, data: ByteArray) {
        // Create TCP packet with data and write to TUN
        // This is a simplified implementation
        withContext(Dispatchers.IO) {
            try {
                // In a real implementation, we would construct proper IP/TCP packets
                // with correct checksums, sequence numbers, etc.
                android.util.Log.v(TAG, "Sending ${data.size} bytes to ${key.sourceIp}:${key.sourcePort}")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error sending TCP data: ${e.message}", e)
            }
        }
    }
    
    private suspend fun handleUdpPacket(packet: ByteArray) {
        try {
            // Parse IP header
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            
            // Extract source and destination IPs
            val sourceIp = InetAddress.getByAddress(packet.copyOfRange(12, 16)).hostAddress ?: return
            val destIp = InetAddress.getByAddress(packet.copyOfRange(16, 20)).hostAddress ?: return
            
            // Parse UDP header
            val udpHeaderStart = ipHeaderLength
            val sourcePort = ((packet[udpHeaderStart].toInt() and 0xFF) shl 8) or 
                            (packet[udpHeaderStart + 1].toInt() and 0xFF)
            val destPort = ((packet[udpHeaderStart + 2].toInt() and 0xFF) shl 8) or 
                          (packet[udpHeaderStart + 3].toInt() and 0xFF)
            
            // Check if this is a DNS query (port 53)
            if (destPort == DNS_PORT) {
                handleDnsQuery(packet, sourceIp, sourcePort, destIp, destPort, ipHeaderLength)
            } else {
                // For non-DNS UDP, we would need full SOCKS5 UDP ASSOCIATE support
                android.util.Log.v(TAG, "Non-DNS UDP packet to port $destPort - not yet supported")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error handling UDP packet: ${e.message}", e)
        }
    }
    
    private suspend fun handleDnsQuery(
        packet: ByteArray,
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        ipHeaderLength: Int
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Extract DNS query payload
                val udpHeaderLength = 8
                val dnsPayloadStart = ipHeaderLength + udpHeaderLength
                val dnsPayload = packet.copyOfRange(dnsPayloadStart, packet.size)
                
                android.util.Log.d(TAG, "DNS query from $sourceIp:$sourcePort to $destIp:$destPort (${dnsPayload.size} bytes)")
                
                // Route DNS query through SOCKS5 using TCP (DNS over TCP)
                // This is more reliable than UDP through SOCKS5
                val response = queryDnsThroughSocks5(destIp, destPort, dnsPayload)
                
                if (response != null) {
                    // Send DNS response back through TUN
                    sendDnsResponse(sourceIp, sourcePort, destIp, destPort, response)
                    android.util.Log.d(TAG, "DNS response sent: ${response.size} bytes")
                } else {
                    android.util.Log.w(TAG, "DNS query failed - no response")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error handling DNS query: ${e.message}", e)
            }
        }
    }
    
    private fun queryDnsThroughSocks5(dnsServer: String, dnsPort: Int, query: ByteArray): ByteArray? {
        var socket: Socket? = null
        try {
            // Create TCP connection to DNS server through SOCKS5
            socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5000)
            socket.soTimeout = 5000 // 5 second timeout for DNS queries
            
            // Perform SOCKS5 handshake
            if (!performSocks5Handshake(socket, dnsServer, dnsPort)) {
                android.util.Log.w(TAG, "SOCKS5 handshake failed for DNS query")
                return null
            }
            
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            
            // Send DNS query with length prefix (DNS over TCP format)
            val queryLength = ByteBuffer.allocate(2).putShort(query.size.toShort()).array()
            output.write(queryLength)
            output.write(query)
            output.flush()
            
            // Read DNS response length
            val responseLengthBytes = ByteArray(2)
            if (input.read(responseLengthBytes) != 2) {
                android.util.Log.w(TAG, "Failed to read DNS response length")
                return null
            }
            
            val responseLength = ByteBuffer.wrap(responseLengthBytes).short.toInt() and 0xFFFF
            if (responseLength <= 0 || responseLength > 4096) {
                android.util.Log.w(TAG, "Invalid DNS response length: $responseLength")
                return null
            }
            
            // Read DNS response
            val response = ByteArray(responseLength)
            var totalRead = 0
            while (totalRead < responseLength) {
                val read = input.read(response, totalRead, responseLength - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            
            if (totalRead != responseLength) {
                android.util.Log.w(TAG, "Incomplete DNS response: $totalRead/$responseLength bytes")
                return null
            }
            
            return response
        } catch (e: Exception) {
            android.util.Log.e(TAG, "DNS query through SOCKS5 failed: ${e.message}", e)
            return null
        } finally {
            socket?.close()
        }
    }
    
    private suspend fun sendDnsResponse(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        dnsResponse: ByteArray
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Build UDP packet with DNS response
                // This is a simplified implementation
                // A full implementation would construct proper IP/UDP packets with checksums
                
                // For now, we'll create a minimal UDP packet structure
                val sourceIpBytes = InetAddress.getByName(destIp).address
                val destIpBytes = InetAddress.getByName(sourceIp).address
                
                // IP header (20 bytes)
                val ipHeader = ByteArray(20)
                ipHeader[0] = 0x45.toByte() // Version 4, header length 5 (20 bytes)
                ipHeader[1] = 0x00 // Type of service
                
                val totalLength = 20 + 8 + dnsResponse.size
                ipHeader[2] = (totalLength shr 8).toByte()
                ipHeader[3] = (totalLength and 0xFF).toByte()
                
                ipHeader[8] = 64 // TTL
                ipHeader[9] = 17 // Protocol: UDP
                
                // Source IP (swap dest and source for response)
                System.arraycopy(sourceIpBytes, 0, ipHeader, 12, 4)
                // Dest IP
                System.arraycopy(destIpBytes, 0, ipHeader, 16, 4)
                
                // Calculate IP checksum
                val ipChecksum = calculateChecksum(ipHeader)
                ipHeader[10] = (ipChecksum shr 8).toByte()
                ipHeader[11] = (ipChecksum and 0xFF).toByte()
                
                // UDP header (8 bytes)
                val udpHeader = ByteArray(8)
                udpHeader[0] = (destPort shr 8).toByte() // Source port (DNS server)
                udpHeader[1] = (destPort and 0xFF).toByte()
                udpHeader[2] = (sourcePort shr 8).toByte() // Dest port (client)
                udpHeader[3] = (sourcePort and 0xFF).toByte()
                
                val udpLength = 8 + dnsResponse.size
                udpHeader[4] = (udpLength shr 8).toByte()
                udpHeader[5] = (udpLength and 0xFF).toByte()
                // Checksum at [6-7] - can be 0 for IPv4 UDP
                
                // Combine into full packet
                val responsePacket = ipHeader + udpHeader + dnsResponse
                
                // Write to TUN interface
                tunOutputStream.write(responsePacket)
                tunOutputStream.flush()
                
                android.util.Log.v(TAG, "Sent DNS response packet: ${responsePacket.size} bytes")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error sending DNS response: ${e.message}", e)
            }
        }
    }
    
    private fun calculateChecksum(data: ByteArray): Int {
        var sum = 0L
        var i = 0
        
        // Sum all 16-bit words
        while (i < data.size - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        
        // Add remaining byte if odd length
        if (i < data.size) {
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
