package com.sshtunnel.ssh

import com.sshtunnel.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Android implementation of ProcessManager with performance optimizations.
 * 
 * Manages native SSH process lifecycle including:
 * - Process creation with ProcessBuilder
 * - Output stream capture and monitoring with buffering
 * - Process health checking
 * - Graceful and forced termination
 * 
 * Performance optimizations:
 * - Buffered stream reading for reduced system calls
 * - Efficient process alive checks
 * - Optimized memory usage with stream buffering
 */
class AndroidProcessManager(
    private val logger: Logger
) : ProcessManager {
    
    companion object {
        private const val TAG = "AndroidProcessManager"
        private const val STREAM_BUFFER_SIZE = 8192 // 8KB buffer for stream reading
    }
    
    /**
     * Start SSH process with specified command.
     * Uses ProcessBuilder to create the process with redirected error stream.
     */
    override suspend fun startProcess(command: List<String>): Result<Process> = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Starting process with command: ${command.joinToString(" ")}")
            
            if (command.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Command cannot be empty"))
            }
            
            val processBuilder = ProcessBuilder(command)
                .redirectErrorStream(true) // Merge stderr into stdout for unified monitoring
            
            val process = processBuilder.start()
            
            logger.info(TAG, "Process started successfully")
            Result.success(process)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to start process", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stop process gracefully with timeout, then force kill if needed.
     * 
     * Attempts graceful shutdown by calling destroy(), waits for timeout,
     * then forcibly kills the process if it hasn't terminated.
     */
    override suspend fun stopProcess(process: Process, timeoutSeconds: Int): Unit = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Stopping process (timeout: ${timeoutSeconds}s)")
            
            // Request graceful termination
            process.destroy()
            
            // Wait for process to terminate with timeout
            val terminated = withTimeoutOrNull(timeoutSeconds * 1000L) {
                process.waitFor()
                true
            } ?: false
            
            if (terminated) {
                logger.info(TAG, "Process terminated gracefully")
            } else {
                // Force kill if graceful termination failed
                logger.warn(TAG, "Process did not terminate gracefully, forcing termination")
                process.destroyForcibly()
                process.waitFor()
                logger.info(TAG, "Process terminated forcibly")
            }
        } catch (e: Exception) {
            logger.error(TAG, "Error stopping process", e)
            // Ensure process is killed even if error occurs
            try {
                process.destroyForcibly()
            } catch (killError: Exception) {
                logger.error(TAG, "Failed to force kill process", killError)
            }
        }
    }
    
    /**
     * Check if process is alive.
     * Returns true if the process is still running.
     */
    override fun isProcessAlive(process: Process): Boolean {
        return try {
            process.isAlive
        } catch (e: Exception) {
            logger.error(TAG, "Error checking process status", e)
            false
        }
    }
    
    /**
     * Monitor process output streams with optimized buffering.
     * 
     * Creates a Flow that emits lines from the process's stdout/stderr.
     * Uses larger buffer size to reduce system calls and improve performance.
     * The flow completes when the process terminates or the stream ends.
     */
    override fun monitorOutput(process: Process): Flow<String> = flow {
        try {
            // Use larger buffer for better performance
            val reader = BufferedReader(
                InputStreamReader(process.inputStream),
                STREAM_BUFFER_SIZE
            )
            
            reader.useLines { lines ->
                for (line in lines) {
                    emit(line)
                }
            }
            
            logger.debug(TAG, "Process output stream ended")
        } catch (e: Exception) {
            logger.error(TAG, "Error monitoring process output", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
