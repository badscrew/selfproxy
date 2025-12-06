package com.sshtunnel.ssh

import kotlinx.coroutines.flow.Flow

/**
 * Manages native process lifecycle for SSH binary execution.
 * 
 * Responsibilities:
 * - Start SSH process with specified command
 * - Monitor process output streams
 * - Check process alive status
 * - Terminate processes gracefully or forcibly
 */
interface ProcessManager {
    /**
     * Start SSH process with specified command
     * @param command List of command arguments (binary path and options)
     * @return Result containing the Process or error
     */
    suspend fun startProcess(command: List<String>): Result<Process>
    
    /**
     * Stop process gracefully with timeout
     * @param process Process to stop
     * @param timeoutSeconds Timeout for graceful shutdown (default 5 seconds)
     */
    suspend fun stopProcess(process: Process, timeoutSeconds: Int = 5)
    
    /**
     * Check if process is alive
     * @param process Process to check
     * @return true if process is running
     */
    fun isProcessAlive(process: Process): Boolean
    
    /**
     * Monitor process output
     * @param process Process to monitor
     * @return Flow of output lines from stdout and stderr
     */
    fun monitorOutput(process: Process): Flow<String>
}
