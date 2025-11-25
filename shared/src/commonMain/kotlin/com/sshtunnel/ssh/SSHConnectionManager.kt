package com.sshtunnel.ssh

import com.sshtunnel.data.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages SSH connections and SOCKS5 proxy creation.
 * 
 * This interface provides connection lifecycle management, state observation,
 * and error handling for SSH tunnel connections.
 */
interface SSHConnectionManager {
    /**
     * Establishes an SSH connection and creates a SOCKS5 proxy.
     * 
     * @param profile The server profile to connect to
     * @param passphrase Optional passphrase for the private key
     * @return Result containing the Connection on success, or an error on failure
     */
    suspend fun connect(profile: ServerProfile, passphrase: String? = null): Result<Connection>
    
    /**
     * Disconnects the current SSH connection and stops the SOCKS5 proxy.
     * 
     * @return Result indicating success or failure
     */
    suspend fun disconnect(): Result<Unit>
    
    /**
     * Observes the current connection state.
     * 
     * @return Flow emitting connection state changes
     */
    fun observeConnectionState(): StateFlow<ConnectionState>
    
    /**
     * Gets the current connection if one exists.
     * 
     * @return The current Connection, or null if not connected
     */
    fun getCurrentConnection(): Connection?
}

/**
 * Represents an active SSH connection with SOCKS5 proxy.
 * 
 * @property sessionId Unique identifier for this connection
 * @property socksPort Local port where SOCKS5 proxy is listening
 * @property serverAddress SSH server address
 * @property serverPort SSH server port
 * @property username Username used for authentication
 * @property profileId ID of the server profile used
 */
data class Connection(
    val sessionId: String,
    val socksPort: Int,
    val serverAddress: String,
    val serverPort: Int,
    val username: String,
    val profileId: Long
)

/**
 * Represents the current state of the SSH connection.
 */
sealed class ConnectionState {
    /**
     * No active connection.
     */
    data object Disconnected : ConnectionState()
    
    /**
     * Connection is being established.
     */
    data object Connecting : ConnectionState()
    
    /**
     * Connection is active and SOCKS5 proxy is running.
     * 
     * @property connection The active connection details
     */
    data class Connected(val connection: Connection) : ConnectionState()
    
    /**
     * Connection failed or encountered an error.
     * 
     * @property error The error that occurred
     */
    data class Error(val error: ConnectionError) : ConnectionState()
}

/**
 * Connection errors with specific error messages.
 */
sealed class ConnectionError {
    abstract val message: String
    abstract val cause: Throwable?
    
    /**
     * Authentication failed due to invalid credentials or key.
     */
    data class AuthenticationFailed(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionError()
    
    /**
     * Connection timed out.
     */
    data class ConnectionTimeout(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionError()
    
    /**
     * SSH server is unreachable.
     */
    data class HostUnreachable(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionError()
    
    /**
     * Port forwarding is disabled on the SSH server.
     */
    data class PortForwardingDisabled(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionError()
    
    /**
     * Invalid private key format.
     */
    data class InvalidKey(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionError()
    
    /**
     * Unknown host or DNS resolution failure.
     */
    data class UnknownHost(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionError()
    
    /**
     * Credential retrieval failed.
     */
    data class CredentialError(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionError()
    
    /**
     * Unknown or unexpected error.
     */
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionError()
}
