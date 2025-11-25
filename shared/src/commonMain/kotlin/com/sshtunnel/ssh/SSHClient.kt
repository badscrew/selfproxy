package com.sshtunnel.ssh

import com.sshtunnel.data.ServerProfile
import com.sshtunnel.storage.PrivateKey
import kotlin.time.Duration

/**
 * Interface for SSH client operations.
 * 
 * Platform-specific implementations handle SSH connections using native libraries
 * (JSch for Android, NMSSH/Citadel for iOS).
 */
interface SSHClient {
    /**
     * Establishes an SSH connection to a remote server.
     * 
     * @param profile The server profile containing connection details
     * @param privateKey The private key for authentication
     * @param passphrase Optional passphrase for the private key
     * @param connectionTimeout Maximum time to wait for connection
     * @param enableCompression Whether to enable SSH compression
     * @param strictHostKeyChecking Whether to verify host keys
     * @return Result containing the SSHSession on success, or an error on failure
     */
    suspend fun connect(
        profile: ServerProfile,
        privateKey: PrivateKey,
        passphrase: String? = null,
        connectionTimeout: Duration,
        enableCompression: Boolean = false,
        strictHostKeyChecking: Boolean = false
    ): Result<SSHSession>
    
    /**
     * Creates dynamic port forwarding (SOCKS5 proxy) on the SSH session.
     * 
     * @param session The active SSH session
     * @param localPort The local port to bind the SOCKS5 proxy to (0 for automatic)
     * @return Result containing the actual local port used, or an error on failure
     */
    suspend fun createPortForwarding(
        session: SSHSession,
        localPort: Int = 0
    ): Result<Int>
    
    /**
     * Sends a keep-alive packet to maintain the SSH connection.
     * 
     * @param session The active SSH session
     * @return Result indicating success or failure
     */
    suspend fun sendKeepAlive(session: SSHSession): Result<Unit>
    
    /**
     * Disconnects an SSH session and cleans up resources.
     * 
     * @param session The SSH session to disconnect
     * @return Result indicating success or failure
     */
    suspend fun disconnect(session: SSHSession): Result<Unit>
    
    /**
     * Checks if an SSH session is still connected.
     * 
     * @param session The SSH session to check
     * @return true if the session is connected, false otherwise
     */
    fun isConnected(session: SSHSession): Boolean
}

/**
 * Represents an active SSH session.
 * 
 * @property sessionId Unique identifier for this session
 * @property serverAddress The SSH server address
 * @property serverPort The SSH server port
 * @property username The username used for authentication
 * @property socksPort The local port where SOCKS5 proxy is listening (0 if not created)
 */
data class SSHSession(
    val sessionId: String,
    val serverAddress: String,
    val serverPort: Int,
    val username: String,
    val socksPort: Int = 0,
    internal val nativeSession: Any? = null // Platform-specific session object
)

/**
 * SSH connection errors.
 */
sealed class SSHError : Exception() {
    data class AuthenticationFailed(override val message: String) : SSHError()
    data class ConnectionTimeout(override val message: String) : SSHError()
    data class HostUnreachable(override val message: String) : SSHError()
    data class PortForwardingDisabled(override val message: String) : SSHError()
    data class InvalidKey(override val message: String) : SSHError()
    data class UnknownHost(override val message: String) : SSHError()
    data class SessionClosed(override val message: String) : SSHError()
    data class Unknown(override val message: String, override val cause: Throwable? = null) : SSHError()
}
