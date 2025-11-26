package com.sshtunnel.ssh

import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.Logger
import com.sshtunnel.storage.CredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of SSHConnectionManager.
 * 
 * This class manages the lifecycle of SSH connections, including:
 * - Establishing connections with proper error handling
 * - Creating SOCKS5 proxies
 * - Managing connection state
 * - Cleaning up resources on disconnect
 * 
 * @property sshClient Platform-specific SSH client implementation
 * @property credentialStore Platform-specific credential storage
 * @property logger Logger for connection events and errors
 * @property connectionTimeout Timeout for establishing connections (default 30 seconds)
 * @property enableCompression Whether to enable SSH compression (default false)
 * @property strictHostKeyChecking Whether to verify host keys (default false)
 */
class SSHConnectionManagerImpl(
    private val sshClient: SSHClient,
    private val credentialStore: CredentialStore,
    private val logger: Logger,
    private val connectionTimeout: Duration = 30.seconds,
    private val enableCompression: Boolean = false,
    private val strictHostKeyChecking: Boolean = false
) : SSHConnectionManager {
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override fun observeConnectionState(): StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var currentSession: SSHSession? = null
    private var currentConnection: Connection? = null
    
    private val connectionMutex = Mutex()
    
    companion object {
        private const val TAG = "SSHConnectionManager"
    }
    
    override suspend fun connect(profile: ServerProfile, passphrase: String?): Result<Connection> {
        return connectionMutex.withLock {
            try {
                logger.info(TAG, "Connection requested for profile: ${profile.name} (${profile.hostname}:${profile.port})")
                
                // Check if already connected
                if (_connectionState.value is ConnectionState.Connected) {
                    logger.info(TAG, "Already connected, disconnecting existing connection first")
                    disconnect()
                }
                
                // Update state to connecting
                logger.info(TAG, "Updating state to Connecting")
                _connectionState.value = ConnectionState.Connecting
                
                // Retrieve private key from credential store
                logger.verbose(TAG, "Retrieving private key from credential store for profile ID: ${profile.id}")
                val privateKeyResult = credentialStore.retrieveKey(profile.id, passphrase)
                if (privateKeyResult.isFailure) {
                    val error = ConnectionError.CredentialError(
                        "Failed to retrieve private key: ${privateKeyResult.exceptionOrNull()?.message}",
                        privateKeyResult.exceptionOrNull()
                    )
                    logger.error(TAG, "Failed to retrieve private key: ${error.message}", error.cause)
                    _connectionState.value = ConnectionState.Error(error)
                    return Result.failure(Exception(error.message, error.cause))
                }
                
                val privateKey = privateKeyResult.getOrThrow()
                logger.verbose(TAG, "Private key retrieved successfully (type: ${privateKey.keyType})")
                
                // Establish SSH connection
                logger.info(TAG, "Establishing SSH connection...")
                val sessionResult = sshClient.connect(
                    profile = profile,
                    privateKey = privateKey,
                    passphrase = passphrase,
                    connectionTimeout = connectionTimeout,
                    enableCompression = enableCompression,
                    strictHostKeyChecking = strictHostKeyChecking
                )
                
                if (sessionResult.isFailure) {
                    val sshError = sessionResult.exceptionOrNull()
                    val connectionError = mapSSHErrorToConnectionError(sshError)
                    logger.error(TAG, "SSH connection failed: ${connectionError.message}", connectionError.cause)
                    _connectionState.value = ConnectionState.Error(connectionError)
                    return Result.failure(Exception(connectionError.message, connectionError.cause))
                }
                
                val session = sessionResult.getOrThrow()
                currentSession = session
                logger.info(TAG, "SSH session established (ID: ${session.sessionId})")
                
                // Create SOCKS5 proxy (dynamic port forwarding)
                logger.info(TAG, "Creating SOCKS5 proxy...")
                val portResult = sshClient.createPortForwarding(session, localPort = 0)
                if (portResult.isFailure) {
                    // Clean up the session
                    logger.warn(TAG, "Port forwarding failed, cleaning up SSH session")
                    sshClient.disconnect(session)
                    currentSession = null
                    
                    val sshError = portResult.exceptionOrNull()
                    val connectionError = mapSSHErrorToConnectionError(sshError)
                    logger.error(TAG, "Port forwarding failed: ${connectionError.message}", connectionError.cause)
                    _connectionState.value = ConnectionState.Error(connectionError)
                    return Result.failure(Exception(connectionError.message, connectionError.cause))
                }
                
                val socksPort = portResult.getOrThrow()
                logger.info(TAG, "SOCKS5 proxy created on port $socksPort")
                
                // Create connection object
                val connection = Connection(
                    sessionId = session.sessionId,
                    socksPort = socksPort,
                    serverAddress = profile.hostname,
                    serverPort = profile.port,
                    username = profile.username,
                    profileId = profile.id
                )
                
                currentConnection = connection
                _connectionState.value = ConnectionState.Connected(connection)
                logger.info(TAG, "Connection established successfully to ${profile.hostname}:${profile.port}")
                
                Result.success(connection)
                
            } catch (e: Exception) {
                val error = ConnectionError.Unknown(
                    "Unexpected error during connection: ${e.message}",
                    e
                )
                logger.error(TAG, "Unexpected error during connection", e)
                _connectionState.value = ConnectionState.Error(error)
                Result.failure(Exception(error.message, error.cause))
            }
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return connectionMutex.withLock {
            try {
                logger.info(TAG, "Disconnect requested")
                val session = currentSession
                if (session != null) {
                    logger.info(TAG, "Disconnecting SSH session ${session.sessionId}")
                    // Disconnect SSH session (this also stops port forwarding)
                    sshClient.disconnect(session)
                    currentSession = null
                    logger.info(TAG, "SSH session disconnected")
                } else {
                    logger.verbose(TAG, "No active session to disconnect")
                }
                
                currentConnection = null
                _connectionState.value = ConnectionState.Disconnected
                logger.info(TAG, "Connection state updated to Disconnected")
                
                Result.success(Unit)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error during disconnect, cleaning up state anyway", e)
                // Even if disconnect fails, we should clean up our state
                currentSession = null
                currentConnection = null
                _connectionState.value = ConnectionState.Disconnected
                
                Result.failure(e)
            }
        }
    }
    
    override fun getCurrentConnection(): Connection? {
        return currentConnection
    }
    
    /**
     * Maps SSH errors to connection errors with specific error messages.
     */
    private fun mapSSHErrorToConnectionError(error: Throwable?): ConnectionError {
        return when (error) {
            is SSHError.AuthenticationFailed -> ConnectionError.AuthenticationFailed(
                error.message,
                error
            )
            is SSHError.ConnectionTimeout -> ConnectionError.ConnectionTimeout(
                error.message,
                error
            )
            is SSHError.HostUnreachable -> ConnectionError.HostUnreachable(
                error.message,
                error
            )
            is SSHError.PortForwardingDisabled -> ConnectionError.PortForwardingDisabled(
                error.message,
                error
            )
            is SSHError.InvalidKey -> ConnectionError.InvalidKey(
                error.message,
                error
            )
            is SSHError.UnknownHost -> ConnectionError.UnknownHost(
                error.message,
                error
            )
            is SSHError.NetworkUnavailable -> ConnectionError.NetworkUnavailable(
                error.message,
                error
            )
            else -> ConnectionError.Unknown(
                error?.message ?: "Unknown error occurred",
                error
            )
        }
    }
}
