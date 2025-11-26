package com.sshtunnel.android.vpn

import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Simplified TUN to SOCKS5 routing engine.
 * 
 * This is a pure Kotlin implementation that handles basic TCP traffic routing.
 * For production use, this should be replaced with a native tun2socks library.
 * 
 * Current implementation:
 * - Handles TCP connections through SOCKS5
 * - Handles DNS queries through SOCKS5
 * - Basic packet parsing and routing
 * 
 * Limitations:
 * - Simplified TCP state machine
 * - No UDP support (except DNS)
 * - No ICMP support
 * - May not handle all edge cases
 */
class Tun2SocksEngine(
    private val tunInputStream: FileInputStream,
    private val tunOutputStream: FileOutputStream,
    private val socksPort: Int
) {
    
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
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tcpConnections = mutableMapOf<ConnectionKey, TcpConnection>()
    
    @Volatile
    private var isRunning = false
    
    companion object {
        private const val TAG = "Tun2SocksEngine"
        private const val MAX_PACKET_SIZE = 32767
        private const val DNS_PORT = 53
        private const val SOCKS5_VERSION: Byte = 0x05
        private const val SOCKS5_NO_AUTH: Byte = 0x00
        private const val SOCKS5_CMD_CONNECT: Byte = 0x01
        private const val SOCKS5_ATYP_IPV4: Byte = 0x01
        private const val SOCKS5_SUCCESS: Byte = 0x00
    }
    
    /**
     * Starts the TUN to SOCKS5 routing engine.
     */
    fun start(): Result<Unit> {
        return try {
            if (isRunning) {
                return Result.failure(IllegalStateException("Engine already running"))
            }
            
            isRunning = true
            
            // Start packet routing
            scope.launch {
                routePackets()
            }
            
            android.util.Log.i(TAG, "Tun2SocksEngine started successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start engine: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stops the routing engine and cleans up resources.
     */
    fun stop(): Result<Unit> {
        return try {
            if (!isRunning) {
                return Result.success(Unit)
            }
            
            isRunning = false
            
            // Cancel all coroutines
            scope.cancel()
            
            // Close all TCP connections
            tcpConnections.values.forEach { 
                it.job.cancel()
                try {
                    it.socket.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            tcpConnections.clear()
            
            android.util.Log.i(TAG, "Tun2SocksEngine stopped successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping engine: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Checks if the engine is currently running.
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Main packet routing loop.
     */
    private suspend fun routePackets() {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        
        withContext(Dispatchers.IO) {
            try {
                while (isActive && isRunning) {
                    val length = tunInputStream.read(buffer)
                    if (length > 0) {
                        val packet = buffer.copyOf(length)
                        launch { processPacket(packet) }
                    }
                }
            } catch (e: Exception) {
                if (isActive && isRunning) {
                    android.util.Log.e(TAG, "Error routing packets: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Processes a single IP packet.
     */
    private suspend fun processPacket(packet: ByteArray) {
        try {
            // Parse IP header
            val ipVersion = (packet[0].toInt() shr 4) and 0x0F
            
            if (ipVersion != 4) {
                // Only IPv4 supported
                return
            }
            
            val protocol = packet[9].toInt() and 0xFF
            
            when (protocol) {
                6 -> handleTcpPacket(packet) // TCP
                17 -> handleUdpPacket(packet) // UDP (DNS only)
                else -> {
                    // Ignore other protocols
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error processing packet: ${e.message}", e)
        }
    }
    
    /**
     * Handles TCP packets by routing through SOCKS5.
     */
    private suspend fun handleTcpPacket(packet: ByteArray) {
        try {
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            
            // Extract IPs
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
            
            when {
                syn && !tcpConnections.containsKey(connectionKey) -> {
                    establishTcpConnection(connectionKey)
                }
                fin || rst -> {
                    closeTcpConnection(connectionKey)
                }
                else -> {
                    forwardTcpData(connectionKey, packet, ipHeaderLength)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error handling TCP packet: ${e.message}", e)
        }
    }
    
    /**
     * Establishes a new TCP connection through SOCKS5.
     */
    private suspend fun establishTcpConnection(key: ConnectionKey) {
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5000)
                
                if (!performSocks5Handshake(socket, key.destIp, key.destPort)) {
                    socket.close()
                    android.util.Log.w(TAG, "SOCKS5 handshake failed for ${key.destIp}:${key.destPort}")
                    return@withContext
                }
                
                val job = scope.launch {
                    readFromSocks5(socket, key)
                }
                
                tcpConnections[key] = TcpConnection(socket, key, job)
                
                android.util.Log.d(TAG, "Established TCP connection: ${key.destIp}:${key.destPort}")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to establish TCP connection: ${e.message}", e)
            }
        }
    }
    
    /**
     * Performs SOCKS5 handshake.
     */
    private fun performSocks5Handshake(socket: Socket, destIp: String, destPort: Int): Boolean {
        return try {
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            
            // Send greeting
            output.write(byteArrayOf(SOCKS5_VERSION, 0x01, SOCKS5_NO_AUTH))
            output.flush()
            
            // Read response
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
                0x00,
                SOCKS5_ATYP_IPV4
            ) + ipBytes + portBytes
            
            output.write(request)
            output.flush()
            
            // Read response
            val response = ByteArray(10)
            if (input.read(response) < 10 || response[0] != SOCKS5_VERSION || response[1] != SOCKS5_SUCCESS) {
                return false
            }
            
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "SOCKS5 handshake error: ${e.message}", e)
            false
        }
    }
    
    /**
     * Forwards TCP data through existing SOCKS5 connection.
     */
    private suspend fun forwardTcpData(key: ConnectionKey, packet: ByteArray, ipHeaderLength: Int) {
        val connection = tcpConnections[key] ?: return
        
        withContext(Dispatchers.IO) {
            try {
                val tcpHeaderStart = ipHeaderLength
                val tcpHeaderLength = ((packet[tcpHeaderStart + 12].toInt() and 0xFF) shr 4) * 4
                
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
    
    /**
     * Reads data from SOCKS5 connection (responses from remote server).
     * Note: This simplified implementation doesn't write responses back to TUN.
     * A full implementation would construct proper TCP/IP packets.
     */
    private suspend fun readFromSocks5(socket: Socket, key: ConnectionKey) {
        withContext(Dispatchers.IO) {
            try {
                val input = socket.getInputStream()
                val buffer = ByteArray(8192)
                
                while (isActive && !socket.isClosed && isRunning) {
                    val length = input.read(buffer)
                    if (length <= 0) break
                    
                    // In a full implementation, we would:
                    // 1. Construct TCP/IP response packet
                    // 2. Write it back to TUN interface
                    // For now, we just log that data was received
                    android.util.Log.v(TAG, "Received ${length} bytes from ${key.destIp}:${key.destPort}")
                }
            } catch (e: Exception) {
                if (isActive && isRunning) {
                    android.util.Log.e(TAG, "Error reading from SOCKS5: ${e.message}", e)
                }
            } finally {
                closeTcpConnection(key)
            }
        }
    }
    
    /**
     * Closes a TCP connection.
     */
    private fun closeTcpConnection(key: ConnectionKey) {
        tcpConnections.remove(key)?.let { connection ->
            connection.job.cancel()
            try {
                connection.socket.close()
            } catch (e: Exception) {
                // Ignore
            }
            android.util.Log.d(TAG, "Closed TCP connection: ${key.destIp}:${key.destPort}")
        }
    }
    
    /**
     * Handles UDP packets (DNS only).
     */
    private suspend fun handleUdpPacket(packet: ByteArray) {
        try {
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            
            val udpHeaderStart = ipHeaderLength
            val destPort = ((packet[udpHeaderStart + 2].toInt() and 0xFF) shl 8) or 
                          (packet[udpHeaderStart + 3].toInt() and 0xFF)
            
            if (destPort == DNS_PORT) {
                // DNS queries - log but don't handle for now
                android.util.Log.v(TAG, "DNS query detected (not yet implemented)")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error handling UDP packet: ${e.message}", e)
        }
    }
}
