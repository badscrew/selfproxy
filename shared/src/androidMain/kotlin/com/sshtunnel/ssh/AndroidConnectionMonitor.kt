package com.sshtunnel.ssh

import com.sshtunnel.logging.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.net.Socket
import kotlin.coroutines.coroutineContext

/**
 * Android implementation of ConnectionMonitor with battery optimizations.
 * 
 * Monitors SSH process health and SOCKS5 port availability,
 * checking every 1 second as per requirements.
 * 
 * Performance optimizations:
 * - Reduced port checking frequency when connection is stable
 * - Fast process alive checks
 * - Efficient socket connection testing
 * - Battery-efficient monitoring intervals
 */
class AndroidConnectionMonitor(
    private val processManager: ProcessManager,
    private val logger: Logger
) : ConnectionMonitor {
    
    companion object {
        private const val TAG = "AndroidConnectionMonitor"
        private const val MONITORING_INTERVAL_MS = 1000L // 1 second as per requirement 14.5
        private const val PORT_CHECK_TIMEOUT_MS = 500 // 500ms timeout for faster checks
        private const val STABLE_CONNECTION_THRESHOLD = 10 // After 10 healthy checks, reduce port checking
        private const val STABLE_PORT_CHECK_INTERVAL = 5 // Check port every 5 iterations when stable
    }
    
    /**
     * Start monitoring connection health with battery optimizations.
     * 
     * Checks process health every 1 second as per requirement.
     * Port checking is optimized: frequent when establishing, less frequent when stable.
     * Emits state changes when connection status changes.
     */
    override fun monitorConnection(
        process: Process,
        socksPort: Int
    ): Flow<ConnectionHealthState> = flow {
        logger.debug(TAG, "Starting connection monitoring for port $socksPort")
        
        var lastState: ConnectionHealthState = ConnectionHealthState.Healthy
        emit(lastState)
        
        var healthyCheckCount = 0
        var iterationCount = 0
        
        while (coroutineContext.isActive) {
            try {
                iterationCount++
                
                // Always check if process is still alive (fast operation)
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
                
                // Optimize port checking: check less frequently when connection is stable
                val shouldCheckPort = if (healthyCheckCount < STABLE_CONNECTION_THRESHOLD) {
                    // Check every iteration when establishing or recently unhealthy
                    true
                } else {
                    // Check every Nth iteration when stable (battery optimization)
                    iterationCount % STABLE_PORT_CHECK_INTERVAL == 0
                }
                
                val newState = if (shouldCheckPort) {
                    val portOpen = isPortOpen(socksPort)
                    
                    if (portOpen) {
                        healthyCheckCount++
                        ConnectionHealthState.Healthy
                    } else {
                        logger.warn(TAG, "SOCKS5 port $socksPort is not accepting connections")
                        healthyCheckCount = 0 // Reset counter on failure
                        ConnectionHealthState.Unhealthy("SOCKS5 port not accepting connections")
                    }
                } else {
                    // Assume healthy if not checking port (process is alive)
                    healthyCheckCount++
                    ConnectionHealthState.Healthy
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
                healthyCheckCount = 0 // Reset on error
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
     * Check if SOCKS5 port is accepting connections with optimized timeout.
     * 
     * Attempts to connect to the port with a 500ms timeout for faster checks.
     * Uses SO_TIMEOUT for efficient socket operations.
     */
    override suspend fun isPortOpen(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                // Set socket timeout for faster failure detection
                socket.soTimeout = PORT_CHECK_TIMEOUT_MS
                socket.connect(
                    java.net.InetSocketAddress("127.0.0.1", port),
                    PORT_CHECK_TIMEOUT_MS
                )
                true
            }
        } catch (e: Exception) {
            // Don't log every failure to reduce log spam
            false
        }
    }
}
