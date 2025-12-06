package com.sshtunnel.ssh

import android.content.Context
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.Logger
import com.sshtunnel.storage.PrivateKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.time.Duration

/**
 * Native SSH client implementation using OpenSSH binary.
 * 
 * This implementation uses the native OpenSSH binary bundled with the app
 * to provide reliable SSH connections with dynamic port forwarding (SOCKS5).
 * 
 * Unlike the sshj-based implementation, this uses the battle-tested OpenSSH
 * implementation that achieves 100% connection success on desktop systems.
 */
class AndroidNativeSSHClient(
    private val context: Context,
    private val binaryManager: BinaryManager,
    private val privateKeyManager: PrivateKeyManager,
    private val commandBuilder: SSHCommandBuilder,
    private val processManager: ProcessManager,
    private val connectionMonitor: ConnectionMonitor,
    private val logger: Logger
) : SSHClient {
    
    companion object {
        private const val TAG = "AndroidNativeSSHClient"
    }
    
    // Track active sessions
    private val activeSessions = mutableMapOf<String, NativeSSHSession>()
    
    // Process output flow
    private val _processOutput = MutableStateFlow<String>("")
    
    override suspend fun connect(
        profile: ServerProfile,
        privateKey: PrivateKey,
        passphrase: String?,
        connectionTimeout: Duration,
        enableCompression: Boolean,
        strictHostKeyChecking: Boolean
    ): Result<SSHSession> = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Starting native SSH connection to ${profile.hostname}:${profile.port} as ${profile.username}")
            
            // Detect device architecture
            val architecture = binaryManager.detectArchitecture()
            logger.verbose(TAG, "Detected device architecture: ${architecture.abiName}")
            
            // Extract or get cached binary
            val binaryPath = binaryManager.getCachedBinary(architecture)
                ?: binaryManager.extractBinary(architecture).getOrElse { error ->
                    logger.error(TAG, "Failed to extract SSH binary: ${error.message}", error)
                    return@withContext Result.failure(
                        SSHError.Unknown("Failed to extract SSH binary: ${error.message}", error)
                    )
                }
            
            logger.info(TAG, "Using SSH binary at: $binaryPath")
            
            // Verify binary is valid
            if (!binaryManager.verifyBinary(binaryPath)) {
                logger.error(TAG, "Binary verification failed")
                return@withContext Result.failure(
                    SSHError.Unknown("SSH binary verification failed")
                )
            }
            
            logger.verbose(TAG, "Binary verification successful")
            
            // Write private key to file
            val keyPath = privateKeyManager.writePrivateKey(profile.id, privateKey.keyData).getOrElse { error ->
                logger.error(TAG, "Failed to write private key: ${error.message}", error)
                return@withContext Result.failure(
                    SSHError.InvalidKey("Failed to write private key: ${error.message}")
                )
            }
            
            logger.verbose(TAG, "Private key written to: $keyPath")
            
            // Generate unique session ID
            val sessionId = UUID.randomUUID().toString()
            logger.verbose(TAG, "Generated session ID: $sessionId")
            
            // Create session object (without process yet)
            val session = NativeSSHSession(
                sessionId = sessionId,
                profile = profile,
                keyPath = keyPath,
                binaryPath = binaryPath,
                process = null,
                socksPort = 0
            )
            
            // Track session
            activeSessions[sessionId] = session
            
            // Return SSH session
            val sshSession = SSHSession(
                sessionId = sessionId,
                serverAddress = profile.hostname,
                serverPort = profile.port,
                username = profile.username,
                socksPort = 0,
                nativeSession = session
            )
            
            logger.info(TAG, "Native SSH session created successfully")
            Result.success(sshSession)
            
        } catch (e: Exception) {
            logger.error(TAG, "Unexpected error during native SSH connection", e)
            Result.failure(SSHError.Unknown("Unexpected error during connection", e))
        }
    }
    
    override suspend fun createPortForwarding(
        session: SSHSession,
        localPort: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Creating port forwarding on local port $localPort")
            
            val nativeSession = session.nativeSession as? NativeSSHSession
                ?: return@withContext Result.failure(
                    SSHError.SessionClosed("Invalid or closed session").also {
                        logger.error(TAG, "Invalid session object when creating port forwarding")
                    }
                )
            
            // Build SSH command with dynamic port forwarding
            val command = commandBuilder.buildCommand(
                binaryPath = nativeSession.binaryPath,
                profile = nativeSession.profile,
                privateKeyPath = nativeSession.keyPath,
                localPort = localPort
            )
            
            logger.verbose(TAG, "SSH command: ${command.joinToString(" ")}")
            
            // Start SSH process
            val process = processManager.startProcess(command).getOrElse { error ->
                logger.error(TAG, "Failed to start SSH process: ${error.message}", error)
                // Clean up private key on failure
                privateKeyManager.deletePrivateKey(nativeSession.profile.id)
                activeSessions.remove(session.sessionId)
                return@withContext Result.failure(
                    SSHError.Unknown("Failed to start SSH process: ${error.message}", error)
                )
            }
            
            logger.info(TAG, "SSH process started successfully")
            
            // Update session with process
            val updatedSession = nativeSession.copy(
                process = process,
                socksPort = localPort
            )
            activeSessions[session.sessionId] = updatedSession
            
            // Start monitoring process output
            monitorProcessOutput(process)
            
            // Start monitoring connection health
            monitorConnectionHealth(session.sessionId, process, localPort)
            
            logger.info(TAG, "Port forwarding created on port $localPort")
            Result.success(localPort)
            
        } catch (e: Exception) {
            logger.error(TAG, "Unexpected error creating port forwarding", e)
            Result.failure(SSHError.Unknown("Failed to create port forwarding", e))
        }
    }
    
    override suspend fun sendKeepAlive(session: SSHSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nativeSession = session.nativeSession as? NativeSSHSession
                ?: return@withContext Result.failure(
                    SSHError.SessionClosed("Invalid or closed session").also {
                        logger.warn(TAG, "Invalid session when sending keep-alive")
                    }
                )
            
            val process = nativeSession.process
                ?: return@withContext Result.failure(
                    SSHError.SessionClosed("Process not started").also {
                        logger.warn(TAG, "Process not started when sending keep-alive")
                    }
                )
            
            // Check if process is still alive
            if (!processManager.isProcessAlive(process)) {
                logger.warn(TAG, "Process is not alive during keep-alive check")
                return@withContext Result.failure(
                    SSHError.SessionClosed("Process is not alive")
                )
            }
            
            // Native SSH handles keep-alive automatically via ServerAliveInterval
            // We just verify the process is still running
            logger.verbose(TAG, "Keep-alive check successful - process is alive")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to send keep-alive", e)
            Result.failure(SSHError.Unknown("Failed to send keep-alive", e))
        }
    }
    
    override suspend fun disconnect(session: SSHSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Disconnecting native SSH session ${session.sessionId}")
            
            val nativeSession = activeSessions[session.sessionId]
                ?: return@withContext Result.success(Unit).also {
                    logger.verbose(TAG, "Session already disconnected or not found")
                }
            
            // Stop SSH process if running
            nativeSession.process?.let { process ->
                logger.verbose(TAG, "Stopping SSH process")
                processManager.stopProcess(process, timeoutSeconds = 5)
                logger.info(TAG, "SSH process stopped")
            }
            
            // Delete private key file
            logger.verbose(TAG, "Deleting private key file")
            privateKeyManager.deletePrivateKey(nativeSession.profile.id).getOrElse { error ->
                logger.warn(TAG, "Failed to delete private key: ${error.message}")
            }
            
            // Remove session from tracking
            activeSessions.remove(session.sessionId)
            
            logger.info(TAG, "Native SSH session disconnected successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error during disconnect", e)
            Result.failure(SSHError.Unknown("Error during disconnect", e))
        }
    }
    
    override fun isConnected(session: SSHSession): Boolean {
        val nativeSession = activeSessions[session.sessionId] ?: return false
        val process = nativeSession.process ?: return false
        return processManager.isProcessAlive(process)
    }
    
    override fun isSessionAlive(session: SSHSession): Boolean {
        // For native SSH, check if the process is still alive
        val nativeSession = activeSessions[session.sessionId] ?: return false
        val process = nativeSession.process ?: return false
        return processManager.isProcessAlive(process)
    }
    
    /**
     * Get the SSH process output stream.
     * 
     * @return Flow of log lines from SSH process
     */
    fun observeProcessOutput(): Flow<String> = _processOutput.asStateFlow()
    
    /**
     * Monitor process output and emit to flow.
     */
    private fun monitorProcessOutput(process: Process) {
        CoroutineScope(Dispatchers.IO).launch {
            processManager.monitorOutput(process).collect { line ->
                logger.verbose(TAG, "SSH output: $line")
                _processOutput.value = line
            }
        }
    }
    
    /**
     * Monitor connection health and handle disconnections.
     */
    private fun monitorConnectionHealth(sessionId: String, process: Process, socksPort: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            connectionMonitor.monitorConnection(process, socksPort).collect { state ->
                when (state) {
                    is ConnectionHealthState.Healthy -> {
                        logger.verbose(TAG, "Connection health: Healthy")
                    }
                    is ConnectionHealthState.Disconnected -> {
                        logger.warn(TAG, "Connection health: Disconnected")
                        // Clean up session
                        activeSessions[sessionId]?.let { session ->
                            privateKeyManager.deletePrivateKey(session.profile.id)
                            activeSessions.remove(sessionId)
                        }
                    }
                    is ConnectionHealthState.Unhealthy -> {
                        logger.warn(TAG, "Connection health: Unhealthy - ${state.message}")
                    }
                }
            }
        }
    }
}

/**
 * Internal representation of a native SSH session.
 */
private data class NativeSSHSession(
    val sessionId: String,
    val profile: ServerProfile,
    val keyPath: String,
    val binaryPath: String,
    val process: Process?,
    val socksPort: Int
)
