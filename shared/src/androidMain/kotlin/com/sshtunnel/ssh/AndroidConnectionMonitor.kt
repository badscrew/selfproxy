package com.sshtunnel.ssh

import com.sshtunnel.logging.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.net.Socket
import kotlin.coroutines.coroutineContext

/**
 * Android implementation of ConnectionMonitor.
 * 
 * Monitors SSH process health and SOCKS5 port availability,
 * checking every 1 second as per requirements.
 */
class AndroidConnectionMonitor(
    private val processManager: ProcessManager,
    private val logger: Logger
) : ConnectionMonitor {
    
    companion object {
        private const val TAG = "AndroidConnectionMonitor"
        private const val MONITORING_INTERVAL_MS = 1000L // 1 second as per requirement 14.5
        private const val PORT_CHECK_TIMEOUT_MS = 1000 // 1 second timeout for port checks
    }
    
    /**
     * Start monitoring connection health.
     * 
     * Checks process health and port availability every 1 second.
     * Emits state changes when connection status changes.
     */
    override fun monitorConnection(
        process: Process,
        socksPort: Int
    ): Flow<ConnectionHealthState> = flow {
        logger.debug(TAG, "Starting connection monitoring for port $socksPort")
        
        var lastState: ConnectionHealthState = ConnectionHealthState.Healthy
        emit(lastState)
        
        while (coroutineContext.isActive) {
            try {
                // Check if process is still alive
                val processAlive = processManager.isProcessAlive(process)
                
                if (!processAlive) {
                    logger.warn(TAG, "SSH process terminated")
                    val newState = ConnectionHealthState.Disconnected
                    if (newState != lastState) {
                        emit(newState)
                        lastState = newState
                    }
                    break
                }
                
                // Check if SOCKS5 port is still accepting connections
                val portOpen = isPortOpen(socksPort)
                
                val newState = if (portOpen) {
                    ConnectionHealthState.Healthy
                } else {
                    logger.warn(TAG, "SOCKS5 port $socksPort is not accepting connections")
                    ConnectionHealthState.Unhealthy("SOCKS5 port not accepting connections")
                }
                
                // Only emit if state changed
                if (newState != lastState) {
                    emit(newState)
                    lastState = newState
                }
                
                // Wait before next check (1 second interval as per requirement)
                delay(MONITORING_INTERVAL_MS)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error during connection monitoring", e)
                val errorState = ConnectionHealthState.Unhealthy("Monitoring error: ${e.message}")
                if (errorState != lastState) {
                    emit(errorState)
                    lastState = errorState
                }
                break
            }
        }
        
        logger.debug(TAG, "Connection monitoring stopped")
    }
    
    /**
     * Check if SOCKS5 port is accepting connections.
     * 
     * Attempts to connect to the port with a 1-second timeout.
     */
    override suspend fun isPortOpen(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(
                    java.net.InetSocketAddress("127.0.0.1", port),
                    PORT_CHECK_TIMEOUT_MS
                )
                true
            }
        } catch (e: Exception) {
            logger.debug(TAG, "Port $port check failed: ${e.message}")
            false
        }
    }
}
