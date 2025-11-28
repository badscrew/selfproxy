package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPPacketParser
import com.sshtunnel.android.vpn.packet.Protocol
import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LoggerImpl
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Routes IP packets from TUN interface through SOCKS5 proxy.
 * 
 * This class handles the core packet routing logic:
 * - Reading packets from TUN interface using IPPacketParser
 * - Dispatching TCP packets to TCPHandler
 * - Dispatching UDP packets to UDPHandler
 * - Managing connections through ConnectionTable
 * - Writing responses back to TUN
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.5
 */
class PacketRouter(
    private val tunInputStream: FileInputStream,
    private val tunOutputStream: FileOutputStream,
    private val socksPort: Int,
    private val logger: Logger = LoggerImpl()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionTable = ConnectionTable(logger)
    private val tcpHandler = TCPHandler(socksPort, connectionTable, logger)
    private val udpHandler = UDPHandler(socksPort, connectionTable, logger)
    
    companion object {
        private const val TAG = "PacketRouter"
        private const val MAX_PACKET_SIZE = 32767
        private const val CLEANUP_INTERVAL_MS = 30_000L // 30 seconds
        private const val IDLE_TIMEOUT_MS = 120_000L // 2 minutes
    }
    
    private var cleanupJob: Job? = null
    
    /**
     * Starts the packet router.
     * 
     * Begins reading packets from TUN interface and starts periodic connection cleanup.
     * 
     * Requirements: 1.1
     */
    fun start() {
        logger.info(TAG, "Starting packet router with SOCKS port $socksPort")
        
        // Start packet routing
        scope.launch {
            routePackets()
        }
        
        // Start periodic connection cleanup
        cleanupJob = scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                try {
                    connectionTable.cleanupIdleConnections(IDLE_TIMEOUT_MS)
                    logger.verbose(TAG, "Connection cleanup completed")
                } catch (e: Exception) {
                    logger.error(TAG, "Error during connection cleanup: ${e.message}", e)
                }
            }
        }
        
        logger.info(TAG, "Packet router started successfully")
    }
    
    /**
     * Stops the packet router.
     * 
     * Cancels all coroutines and closes all connections.
     * 
     * Requirements: 9.4
     */
    fun stop() {
        logger.info(TAG, "Stopping packet router")
        
        // Cancel cleanup job
        cleanupJob?.cancel()
        cleanupJob = null
        
        // Close all connections
        scope.launch {
            try {
                connectionTable.closeAllConnections()
                logger.debug(TAG, "All connections closed")
            } catch (e: Exception) {
                logger.error(TAG, "Error closing connections: ${e.message}", e)
            }
        }
        
        // Cancel scope
        scope.cancel()
        
        logger.info(TAG, "Packet router stopped")
    }
    
    /**
     * Gets router statistics.
     * 
     * @return RouterStatistics with current metrics
     * Requirements: 13.1, 13.2, 13.3, 13.4, 13.5
     */
    fun getStatistics(): RouterStatistics {
        return connectionTable.getStatistics()
    }
    
    /**
     * Main packet routing loop.
     * 
     * Continuously reads packets from TUN interface and dispatches them to
     * appropriate protocol handlers.
     * 
     * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 11.1, 11.2, 11.3, 11.4, 11.5
     */
    private suspend fun routePackets() {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        
        withContext(Dispatchers.IO) {
            try {
                logger.debug(TAG, "Starting packet routing loop")
                
                while (isActive) {
                    try {
                        val length = tunInputStream.read(buffer)
                        
                        if (length > 0) {
                            val packet = buffer.copyOf(length)
                            // Process each packet in a separate coroutine for concurrency
                            launch { processPacket(packet) }
                        } else if (length < 0) {
                            logger.error(TAG, "TUN interface closed (read returned $length)")
                            break
                        }
                    } catch (e: java.io.IOException) {
                        if (isActive) {
                            logger.error(TAG, "Error reading from TUN interface: ${e.message}", e)
                            delay(100) // Brief pause before retry
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    logger.error(TAG, "Fatal error in packet routing: ${e.message}", e)
                }
            } finally {
                logger.info(TAG, "Packet routing loop stopped")
            }
        }
    }
    
    /**
     * Processes a single IP packet.
     * 
     * Parses the IP header and dispatches to the appropriate protocol handler.
     * Handles errors gracefully to prevent one bad packet from crashing the router.
     * 
     * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 11.1, 11.2, 11.3, 11.4, 11.5, 12.1, 12.5
     */
    private suspend fun processPacket(packet: ByteArray) {
        try {
            // Parse IP header using IPPacketParser
            val ipHeader = IPPacketParser.parseIPv4Header(packet)
            
            if (ipHeader == null) {
                logger.verbose(TAG, "Failed to parse IP header, dropping packet (size=${packet.size} bytes)")
                return
            }
            
            // Log packet reception in verbose mode (NEVER log payload data for privacy)
            logger.verbose(
                TAG,
                "Received IP packet: ${ipHeader.sourceIP} -> ${ipHeader.destIP}, " +
                "protocol=${ipHeader.protocol}, length=${ipHeader.totalLength} bytes, " +
                "ttl=${ipHeader.ttl}, id=${ipHeader.identification}"
            )
            
            // Dispatch to appropriate protocol handler
            when (ipHeader.protocol) {
                Protocol.TCP -> {
                    tcpHandler.handleTcpPacket(packet, ipHeader, tunOutputStream)
                }
                Protocol.UDP -> {
                    udpHandler.handleUdpPacket(packet, ipHeader, tunOutputStream)
                }
                Protocol.ICMP -> {
                    logger.verbose(TAG, "ICMP packet received, not supported (dropping)")
                }
                Protocol.UNKNOWN -> {
                    logger.verbose(TAG, "Unknown protocol packet received (dropping)")
                }
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Error processing packet: ${e.message}", e)
            // Continue processing next packet - don't let one bad packet crash the router
        }
    }
}
