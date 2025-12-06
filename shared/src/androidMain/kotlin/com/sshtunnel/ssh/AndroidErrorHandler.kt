package com.sshtunnel.ssh

import com.sshtunnel.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Android implementation of ErrorHandler for native SSH.
 * 
 * This implementation classifies errors, determines recovery actions,
 * and handles cleanup of resources after errors occur.
 */
class AndroidErrorHandler(
    private val privateKeyManager: PrivateKeyManager,
    private val logger: Logger
) : ErrorHandler {
    
    companion object {
        private const val TAG = "AndroidErrorHandler"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 30000L
    }
    
    // Track retry counts per session
    private val retryCount = mutableMapOf<String, Int>()
    
    // Track sessions that need cleanup
    private val sessionsToCleanup = mutableSetOf<String>()
    
    override suspend fun handleError(error: Throwable): RecoveryAction = withContext(Dispatchers.IO) {
        logger.info(TAG, "Handling error: ${error.javaClass.simpleName}: ${error.message}")
        
        val classifiedError = classifyError(error)
        logger.verbose(TAG, "Classified error as: ${classifiedError.javaClass.simpleName}")
        
        val action = when (classifiedError) {
            is NativeSSHError.BinaryExtractionFailed -> {
                // Binary extraction failed - fall back to sshj
                logger.warn(TAG, "Binary extraction failed, falling back to sshj")
                RecoveryAction.FallbackToSshj
            }
            
            is NativeSSHError.ProcessStartFailed -> {
                // Process start failed - retry once, then fall back
                val retries = retryCount.getOrDefault("process_start", 0)
                if (retries < 1) {
                    retryCount["process_start"] = retries + 1
                    logger.info(TAG, "Process start failed, retrying (attempt ${retries + 1})")
                    RecoveryAction.Retry(delayMs = 1000)
                } else {
                    logger.warn(TAG, "Process start failed after retries, falling back to sshj")
                    retryCount.remove("process_start")
                    RecoveryAction.FallbackToSshj
                }
            }
            
            is NativeSSHError.ConnectionFailed -> {
                // Connection failed - attempt reconnection with backoff
                val retries = retryCount.getOrDefault("connection", 0)
                if (retries < MAX_RETRIES) {
                    val backoff = calculateBackoff(retries)
                    retryCount["connection"] = retries + 1
                    logger.info(TAG, "Connection failed, reconnecting with ${backoff}ms backoff (attempt ${retries + 1})")
                    RecoveryAction.Reconnect(backoffMs = backoff)
                } else {
                    logger.error(TAG, "Connection failed after $MAX_RETRIES retries")
                    retryCount.remove("connection")
                    RecoveryAction.Fail("Connection failed after $MAX_RETRIES attempts")
                }
            }
            
            is NativeSSHError.ProcessCrashed -> {
                // Process crashed - clean up and reconnect
                logger.error(TAG, "SSH process crashed with exit code ${classifiedError.exitCode}")
                RecoveryAction.Reconnect(backoffMs = 2000)
            }
            
            is NativeSSHError.ProcessKilled -> {
                // Process was killed - clean up and reconnect
                logger.error(TAG, "SSH process was killed with signal ${classifiedError.signal}")
                RecoveryAction.Reconnect(backoffMs = 2000)
            }
            
            is NativeSSHError.PortUnavailable -> {
                // Port unavailable - fail immediately
                logger.error(TAG, "SOCKS5 port is unavailable")
                RecoveryAction.Fail("SOCKS5 port is unavailable")
            }
        }
        
        logger.info(TAG, "Recovery action: ${action.javaClass.simpleName}")
        action
    }
    
    override fun classifyError(error: Throwable): NativeSSHError {
        return when (error) {
            // Binary extraction errors
            is IOException -> {
                when {
                    error.message?.contains("extract", ignoreCase = true) == true ||
                    error.message?.contains("binary", ignoreCase = true) == true -> {
                        NativeSSHError.BinaryExtractionFailed(
                            "Failed to extract SSH binary: ${error.message}",
                            error
                        )
                    }
                    error.message?.contains("process", ignoreCase = true) == true ||
                    error.message?.contains("start", ignoreCase = true) == true -> {
                        NativeSSHError.ProcessStartFailed(
                            "Failed to start SSH process: ${error.message}",
                            error
                        )
                    }
                    error.message?.contains("connection", ignoreCase = true) == true ||
                    error.message?.contains("connect", ignoreCase = true) == true -> {
                        NativeSSHError.ConnectionFailed(
                            "SSH connection failed: ${error.message}",
                            error
                        )
                    }
                    error.message?.contains("port", ignoreCase = true) == true ||
                    error.message?.contains("bind", ignoreCase = true) == true -> {
                        NativeSSHError.PortUnavailable
                    }
                    else -> {
                        NativeSSHError.ConnectionFailed(
                            "SSH error: ${error.message}",
                            error
                        )
                    }
                }
            }
            
            // Process-related errors
            is IllegalStateException -> {
                when {
                    error.message?.contains("process", ignoreCase = true) == true -> {
                        NativeSSHError.ProcessStartFailed(
                            "Process error: ${error.message}",
                            error
                        )
                    }
                    else -> {
                        NativeSSHError.ConnectionFailed(
                            "SSH error: ${error.message}",
                            error
                        )
                    }
                }
            }
            
            // Already classified native SSH errors
            is NativeSSHError -> error
            
            // SSH errors from common interface
            is SSHError -> {
                when (error) {
                    is SSHError.AuthenticationFailed,
                    is SSHError.ConnectionTimeout,
                    is SSHError.HostUnreachable,
                    is SSHError.NetworkUnavailable,
                    is SSHError.UnknownHost -> {
                        NativeSSHError.ConnectionFailed(
                            error.message ?: "SSH connection failed",
                            error
                        )
                    }
                    is SSHError.SessionClosed -> {
                        NativeSSHError.ProcessCrashed(
                            exitCode = -1,
                            message = error.message ?: "SSH session closed"
                        )
                    }
                    is SSHError.PortForwardingDisabled -> {
                        NativeSSHError.PortUnavailable
                    }
                    is SSHError.InvalidKey -> {
                        NativeSSHError.ConnectionFailed(
                            "Invalid SSH key: ${error.message}",
                            error
                        )
                    }
                    is SSHError.Unknown -> {
                        NativeSSHError.ConnectionFailed(
                            error.message ?: "Unknown SSH error",
                            error
                        )
                    }
                }
            }
            
            // Unknown errors
            else -> {
                NativeSSHError.ConnectionFailed(
                    "Unexpected error: ${error.message}",
                    error
                )
            }
        }
    }
    
    override suspend fun cleanup(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Cleaning up resources for session $sessionId")
            
            // Mark session for cleanup
            sessionsToCleanup.add(sessionId)
            
            // Clean up private key files
            // Note: We don't have the profile ID here, so we rely on the caller
            // to handle private key cleanup. This method is for additional cleanup.
            
            // Reset retry counts for this session
            retryCount.remove(sessionId)
            
            logger.verbose(TAG, "Cleanup completed for session $sessionId")
            
        } catch (e: Exception) {
            logger.error(TAG, "Error during cleanup for session $sessionId", e)
        }
    }
    
    /**
     * Calculate exponential backoff with jitter.
     */
    private fun calculateBackoff(retryCount: Int): Long {
        val backoff = INITIAL_BACKOFF_MS * (1 shl retryCount) // 2^retryCount
        val jitter = (Math.random() * 1000).toLong() // 0-1000ms jitter
        return minOf(backoff + jitter, MAX_BACKOFF_MS)
    }
    
    /**
     * Reset retry count for a specific key.
     */
    fun resetRetryCount(key: String) {
        retryCount.remove(key)
    }
    
    /**
     * Check if a session needs cleanup.
     */
    fun needsCleanup(sessionId: String): Boolean {
        return sessionsToCleanup.contains(sessionId)
    }
    
    /**
     * Mark cleanup as complete for a session.
     */
    fun markCleanupComplete(sessionId: String) {
        sessionsToCleanup.remove(sessionId)
    }
}
