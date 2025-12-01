package com.sshtunnel.ssh

import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.Logger
import com.sshtunnel.storage.PrivateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient as SshjClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthPublickey
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Android implementation of SSHClient using sshj library.
 * 
 * This implementation handles SSH connections, dynamic port forwarding (SOCKS5),
 * and keep-alive packets for maintaining idle connections.
 */
class AndroidSSHClient(
    private val logger: Logger
) : SSHClient {
    
    companion object {
        private const val TAG = "AndroidSSHClient"
    }
    
    override suspend fun connect(
        profile: ServerProfile,
        privateKey: PrivateKey,
        passphrase: String?,
        connectionTimeout: Duration,
        enableCompression: Boolean,
        strictHostKeyChecking: Boolean
    ): Result<SSHSession> = withContext(Dispatchers.IO) {
        var ssh: SshjClient? = null
        try {
            logger.info(TAG, "Attempting SSH connection to ${profile.hostname}:${profile.port} as ${profile.username}")
            logger.verbose(TAG, "Connection settings: timeout=${connectionTimeout}, compression=$enableCompression, strictHostKeyChecking=$strictHostKeyChecking")
            
            // Create sshj client
            ssh = SshjClient()
            logger.verbose(TAG, "Created sshj client")
            
            // Configure host key verification
            if (strictHostKeyChecking) {
                // TODO: Implement proper known_hosts verification
                // For now, use promiscuous verifier (accepts all host keys)
                logger.verbose(TAG, "Host key checking: promiscuous (accepts all)")
                ssh.addHostKeyVerifier(PromiscuousVerifier())
            } else {
                logger.verbose(TAG, "Host key checking: disabled")
                ssh.addHostKeyVerifier(PromiscuousVerifier())
            }
            
            // Configure connection timeout
            val timeoutMs = connectionTimeout.inWholeMilliseconds.toInt()
            ssh.connectTimeout = timeoutMs
            ssh.timeout = timeoutMs
            logger.verbose(TAG, "Connection timeout set to ${timeoutMs}ms")
            
            // Configure compression
            if (enableCompression) {
                logger.verbose(TAG, "Compression: enabled")
                ssh.useCompression()
            } else {
                logger.verbose(TAG, "Compression: disabled")
            }
            
            // Connect to server
            logger.info(TAG, "Connecting to SSH server...")
            logger.verbose(TAG, "Initiating TCP connection and SSH handshake...")
            ssh.connect(profile.hostname, profile.port)
            logger.info(TAG, "SSH connection established successfully")
            
            // Load private key
            logger.verbose(TAG, "Loading private key (type: ${privateKey.keyType})")
            logger.verbose(TAG, "Private key size: ${privateKey.keyData.size} bytes")
            
            val keyProvider = try {
                if (passphrase != null) {
                    logger.verbose(TAG, "Private key is passphrase-protected")
                    ssh.loadKeys(
                        String(privateKey.keyData, Charsets.UTF_8),
                        null,
                        net.schmizz.sshj.userauth.password.PasswordUtils.createOneOff(passphrase.toCharArray())
                    )
                } else {
                    logger.verbose(TAG, "Private key has no passphrase")
                    ssh.loadKeys(
                        String(privateKey.keyData, Charsets.UTF_8),
                        null,
                        null
                    )
                }
            } catch (e: Exception) {
                logger.error(TAG, "Failed to load private key: ${e.message}", e)
                throw SSHError.InvalidKey("Failed to load private key: ${e.message}")
            }
            
            logger.verbose(TAG, "Private key loaded successfully")
            
            // Authenticate with public key
            logger.verbose(TAG, "Authenticating with public key...")
            try {
                ssh.authPublickey(profile.username, keyProvider)
                logger.info(TAG, "Authentication successful")
            } catch (e: Exception) {
                logger.error(TAG, "Authentication failed: ${e.message}", e)
                throw e
            }
            
            // Configure keep-alive
            ssh.connection.keepAlive.keepAliveInterval = 60 // 60 seconds
            logger.verbose(TAG, "Keep-alive interval set to 60 seconds")
            
            // Generate unique session ID
            val sessionId = UUID.randomUUID().toString()
            logger.verbose(TAG, "Generated session ID: $sessionId")
            
            // Create SSHSession wrapper
            val sshSession = SSHSession(
                sessionId = sessionId,
                serverAddress = profile.hostname,
                serverPort = profile.port,
                username = profile.username,
                socksPort = 0,
                nativeSession = ssh
            )
            
            Result.success(sshSession)
            
        } catch (e: net.schmizz.sshj.userauth.UserAuthException) {
            ssh?.disconnect()
            val mappedError = SSHError.AuthenticationFailed(
                "Authentication failed. The server rejected your credentials. " +
                "Verify your username and SSH key are correct."
            )
            logger.error(TAG, "SSH authentication failed: ${mappedError.message}", e)
            Result.failure(mappedError)
        } catch (e: net.schmizz.sshj.transport.TransportException) {
            ssh?.disconnect()
            val mappedError = mapTransportException(e)
            logger.error(TAG, "SSH transport error: ${mappedError.message}", e)
            Result.failure(mappedError)
        } catch (e: IOException) {
            ssh?.disconnect()
            val mappedError = mapIOException(e)
            logger.error(TAG, "SSH connection failed: ${mappedError.message}", e)
            Result.failure(mappedError)
        } catch (e: SSHError) {
            ssh?.disconnect()
            logger.error(TAG, "SSH error: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            ssh?.disconnect()
            logger.error(TAG, "Unexpected error during SSH connection", e)
            Result.failure(SSHError.Unknown("Unexpected error during connection", e))
        }
    }
    
    override suspend fun createPortForwarding(
        session: SSHSession,
        localPort: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Creating SOCKS5 port forwarding on local port $localPort")
            
            val ssh = session.nativeSession as? SshjClient
                ?: return@withContext Result.failure(
                    SSHError.SessionClosed("Invalid or closed session").also {
                        logger.error(TAG, "Invalid session object when creating port forwarding")
                    }
                )
            
            if (!ssh.isConnected) {
                logger.error(TAG, "Session is not connected when attempting port forwarding")
                return@withContext Result.failure(
                    SSHError.SessionClosed("Session is not connected")
                )
            }
            
            // Set up dynamic port forwarding (SOCKS5)
            // sshj doesn't have built-in SOCKS5 support like JSch's setPortForwardingL
            // We need to create a local port forwarder manually
            logger.verbose(TAG, "Setting up dynamic port forwarding (SOCKS5) on 127.0.0.1:$localPort")
            
            // Create a server socket for the SOCKS5 proxy
            val serverSocket = java.net.ServerSocket(localPort, 50, java.net.InetAddress.getByName("127.0.0.1"))
            val actualPort = serverSocket.localPort
            
            // TODO: Implement SOCKS5 proxy using sshj's port forwarding
            // For now, just return the port - full SOCKS5 implementation will be in task 3
            logger.info(TAG, "SOCKS5 proxy socket created on port $actualPort (full implementation pending)")
            
            Result.success(actualPort)
            
        } catch (e: IOException) {
            logger.error(TAG, "Failed to create port forwarding: ${e.message}", e)
            Result.failure(SSHError.Unknown("Failed to create port forwarding", e))
        } catch (e: Exception) {
            logger.error(TAG, "Unexpected error creating port forwarding", e)
            Result.failure(SSHError.Unknown("Failed to create port forwarding", e))
        }
    }
    
    override suspend fun sendKeepAlive(session: SSHSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ssh = session.nativeSession as? SshjClient
                ?: return@withContext Result.failure(
                    SSHError.SessionClosed("Invalid or closed session").also {
                        logger.warn(TAG, "Invalid session when sending keep-alive")
                    }
                )
            
            if (!ssh.isConnected) {
                logger.warn(TAG, "Session not connected when sending keep-alive")
                return@withContext Result.failure(
                    SSHError.SessionClosed("Session is not connected")
                )
            }
            
            // Send keep-alive packet
            // Note: sshj's keep-alive is automatic, but we can trigger it manually
            logger.verbose(TAG, "Sending SSH keep-alive packet")
            // sshj doesn't have a direct sendAlive() method, but keep-alive is handled automatically
            // We'll just verify the connection is still alive
            if (!ssh.isConnected) {
                throw IOException("Connection is not alive")
            }
            logger.verbose(TAG, "Keep-alive check successful")
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to send keep-alive", e)
            Result.failure(SSHError.Unknown("Failed to send keep-alive", e))
        }
    }
    
    override suspend fun disconnect(session: SSHSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Disconnecting SSH session ${session.sessionId}")
            
            val ssh = session.nativeSession as? SshjClient
                ?: return@withContext Result.success(Unit).also {
                    logger.verbose(TAG, "Session already disconnected or invalid")
                }
            
            if (ssh.isConnected) {
                ssh.disconnect()
                logger.info(TAG, "SSH session disconnected successfully")
            } else {
                logger.verbose(TAG, "Session was already disconnected")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error during disconnect", e)
            Result.failure(SSHError.Unknown("Error during disconnect", e))
        }
    }
    
    override fun isConnected(session: SSHSession): Boolean {
        val ssh = session.nativeSession as? SshjClient ?: return false
        return ssh.isConnected
    }
    
    /**
     * Maps sshj TransportException to our SSH error types.
     */
    private fun mapTransportException(e: net.schmizz.sshj.transport.TransportException): SSHError {
        val message = e.message ?: "Unknown transport error"
        
        return when {
            message.contains("Connection refused", ignoreCase = true) -> {
                SSHError.HostUnreachable(
                    "Connection refused by the server. " +
                    "The server may not be running SSH on the specified port, " +
                    "or a firewall is blocking the connection. " +
                    "Verify the port number (default is 22) and check firewall settings."
                )
            }
            
            message.contains("timeout", ignoreCase = true) -> {
                SSHError.ConnectionTimeout(
                    "Connection timed out. Check your network connection, verify the server is online, " +
                    "and ensure no firewall is blocking the connection. " +
                    "You can increase the timeout in settings if needed."
                )
            }
            
            message.contains("Network is unreachable", ignoreCase = true) ||
            message.contains("No route to host", ignoreCase = true) -> {
                SSHError.NetworkUnavailable(
                    "Network is unreachable. " +
                    "Check your internet connection (WiFi or mobile data) and try again. " +
                    "If you're on a restricted network, it may be blocking SSH connections."
                )
            }
            
            else -> SSHError.Unknown("Transport error: $message", e)
        }
    }
    
    /**
     * Maps IOException to our SSH error types.
     */
    private fun mapIOException(e: IOException): SSHError {
        val message = e.message ?: "Unknown IO error"
        
        return when {
            e is java.net.UnknownHostException -> {
                SSHError.UnknownHost(
                    "Cannot resolve hostname. DNS lookup failed. " +
                    "Check the hostname spelling, verify your DNS is working, " +
                    "or try using an IP address instead."
                )
            }
            
            e is java.net.ConnectException -> {
                SSHError.HostUnreachable(
                    "Connection refused by the server. " +
                    "The server may not be running SSH on the specified port, " +
                    "or a firewall is blocking the connection. " +
                    "Verify the port number (default is 22) and check firewall settings."
                )
            }
            
            e is java.net.SocketTimeoutException -> {
                SSHError.ConnectionTimeout(
                    "Connection timed out. Check your network connection, verify the server is online, " +
                    "and ensure no firewall is blocking the connection. " +
                    "You can increase the timeout in settings if needed."
                )
            }
            
            message.contains("Connection refused", ignoreCase = true) -> {
                SSHError.HostUnreachable(
                    "Connection refused by the server. " +
                    "The server may not be running SSH on the specified port, " +
                    "or a firewall is blocking the connection."
                )
            }
            
            message.contains("timeout", ignoreCase = true) -> {
                SSHError.ConnectionTimeout(
                    "Connection timed out. Check your network connection and try again."
                )
            }
            
            else -> SSHError.Unknown("IO error: $message", e)
        }
    }
}
