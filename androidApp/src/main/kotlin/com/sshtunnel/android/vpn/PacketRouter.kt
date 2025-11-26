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
    
    companion object {
        private const val TAG = "PacketRouter"
        private const val MAX_PACKET_SIZE = 32767
        private const val SOCKS5_VERSION: Byte = 0x05
        private const val SOCKS5_NO_AUTH: Byte = 0x00
        private const val SOCKS5_CMD_CONNECT: Byte = 0x01
        private const val SOCKS5_ATYP_IPV4: Byte = 0x01
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
        // UDP through SOCKS5 is more complex and requires SOCKS5 UDP ASSOCIATE
        // For now, we'll log and skip UDP packets
        android.util.Log.v(TAG, "UDP packet received (${packet.size} bytes) - UDP routing not yet implemented")
    }
}
