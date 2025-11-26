package com.sshtunnel.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.Logger
import com.sshtunnel.storage.PrivateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import java.util.UUID
import kotlin.time.Duration

/**
 * Android implementation of SSHClient using JSch library.
 * 
 * This implementation handles SSH connections, dynamic port forwarding (SOCKS5),
 * and keep-alive packets for maintaining idle connections.
 */
class AndroidSSHClient(
    private val logger: Logger
) : SSHClient {
    
    private val jsch = JSch()
    
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
        try {
            logger.info(TAG, "Attempting SSH connection to ${profile.hostname}:${profile.port} as ${profile.username}")
            logger.verbose(TAG, "Connection settings: timeout=${connectionTimeout}, compression=$enableCompression, strictHostKeyChecking=$strictHostKeyChecking")
            
            // Add the private key to JSch
            val keyName = "key_${profile.id}"
            logger.verbose(TAG, "Adding private key (type: ${privateKey.keyType}) to JSch")
            if (passphrase != null) {
                jsch.addIdentity(keyName, privateKey.keyData, null, passphrase.toByteArray())
                logger.verbose(TAG, "Private key is passphrase-protected")
            } else {
                jsch.addIdentity(keyName, privateKey.keyData, null, null)
                logger.verbose(TAG, "Private key has no passphrase")
            }
            
            // Create SSH session
            logger.verbose(TAG, "Creating JSch session")
            val session = jsch.getSession(profile.username, profile.hostname, profile.port)
            
            // Configure session properties
            logger.verbose(TAG, "Configuring SSH session properties")
            val config = Properties().apply {
                // Host key checking
                setProperty("StrictHostKeyChecking", if (strictHostKeyChecking) "yes" else "no")
                
                // Compression
                if (enableCompression) {
                    setProperty("compression.s2c", "zlib@openssh.com,zlib,none")
                    setProperty("compression.c2s", "zlib@openssh.com,zlib,none")
                    setProperty("compression_level", "6")
                } else {
                    setProperty("compression.s2c", "none")
                    setProperty("compression.c2s", "none")
                }
                
                // Preferred authentication methods (public key only)
                setProperty("PreferredAuthentications", "publickey")
                
                // Key exchange algorithms (prefer modern algorithms)
                setProperty(
                    "kex",
                    "diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1"
                )
                
                // Server host key algorithms
                when (privateKey.keyType) {
                    KeyType.ED25519 -> setProperty("server_host_key", "ssh-ed25519")
                    KeyType.ECDSA -> setProperty("server_host_key", "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521")
                    KeyType.RSA -> setProperty("server_host_key", "ssh-rsa,rsa-sha2-256,rsa-sha2-512")
                }
            }
            session.setConfig(config)
            logger.verbose(TAG, "SSH config: kex=${config.getProperty("kex")}, server_host_key=${config.getProperty("server_host_key")}")
            
            // Set connection timeout (JSch expects milliseconds as int)
            val timeoutMs = connectionTimeout.inWholeMilliseconds.toInt()
            session.timeout = timeoutMs
            logger.verbose(TAG, "Connection timeout set to ${timeoutMs}ms")
            
            // Connect
            logger.info(TAG, "Connecting to SSH server...")
            session.connect()
            logger.info(TAG, "SSH connection established successfully")
            
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
                nativeSession = session
            )
            
            Result.success(sshSession)
            
        } catch (e: JSchException) {
            val mappedError = mapJSchException(e)
            logger.error(TAG, "SSH connection failed: ${mappedError.message}", e)
            logger.verbose(TAG, "JSch exception details: ${e.message}")
            Result.failure(mappedError)
        } catch (e: Exception) {
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
            
            val jschSession = session.nativeSession as? Session
                ?: return@withContext Result.failure(
                    SSHError.SessionClosed("Invalid or closed session").also {
                        logger.error(TAG, "Invalid session object when creating port forwarding")
                    }
                )
            
            if (!jschSession.isConnected) {
                logger.error(TAG, "Session is not connected when attempting port forwarding")
                return@withContext Result.failure(
                    SSHError.SessionClosed("Session is not connected")
                )
            }
            
            // Set up dynamic port forwarding (SOCKS5)
            // setPortForwardingL creates a local SOCKS5 proxy
            // Bind to localhost only for security
            logger.verbose(TAG, "Setting up dynamic port forwarding (SOCKS5) on 127.0.0.1:$localPort")
            val actualPort = jschSession.setPortForwardingL("127.0.0.1", localPort, null, 0)
            logger.info(TAG, "SOCKS5 proxy created successfully on port $actualPort")
            
            Result.success(actualPort)
            
        } catch (e: JSchException) {
            logger.error(TAG, "Failed to create port forwarding: ${e.message}", e)
            // Check if port forwarding is disabled on the server
            if (e.message?.contains("forwarding", ignoreCase = true) == true) {
                Result.failure(
                    SSHError.PortForwardingDisabled(
                        "Port forwarding is disabled on the SSH server. " +
                        "Please enable AllowTcpForwarding in sshd_config."
                    )
                )
            } else {
                Result.failure(mapJSchException(e))
            }
        } catch (e: Exception) {
            logger.error(TAG, "Unexpected error creating port forwarding", e)
            Result.failure(SSHError.Unknown("Failed to create port forwarding", e))
        }
    }
    
    override suspend fun sendKeepAlive(session: SSHSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jschSession = session.nativeSession as? Session
                ?: return@withContext Result.failure(
                    SSHError.SessionClosed("Invalid or closed session").also {
                        logger.warn(TAG, "Invalid session when sending keep-alive")
                    }
                )
            
            if (!jschSession.isConnected) {
                logger.warn(TAG, "Session not connected when sending keep-alive")
                return@withContext Result.failure(
                    SSHError.SessionClosed("Session is not connected")
                )
            }
            
            // Send keep-alive packet
            logger.verbose(TAG, "Sending SSH keep-alive packet")
            jschSession.sendKeepAliveMsg()
            logger.verbose(TAG, "Keep-alive packet sent successfully")
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to send keep-alive", e)
            Result.failure(SSHError.Unknown("Failed to send keep-alive", e))
        }
    }
    
    override suspend fun disconnect(session: SSHSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Disconnecting SSH session ${session.sessionId}")
            
            val jschSession = session.nativeSession as? Session
                ?: return@withContext Result.success(Unit).also {
                    logger.verbose(TAG, "Session already disconnected or invalid")
                }
            
            if (jschSession.isConnected) {
                jschSession.disconnect()
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
        val jschSession = session.nativeSession as? Session ?: return false
        return jschSession.isConnected
    }
    
    /**
     * Maps JSch exceptions to our SSH error types with detailed categorization.
     */
    private fun mapJSchException(e: JSchException): SSHError {
        val message = e.message ?: "Unknown SSH error"
        
        return when {
            // Authentication failures - categorize by specific reason
            message.contains("Auth fail", ignoreCase = true) -> {
                categorizeAuthenticationFailure(message)
            }
            
            message.contains("authentication", ignoreCase = true) -> {
                SSHError.AuthenticationFailed(
                    "Authentication failed. The server rejected your credentials. " +
                    "Verify your username and SSH key are correct."
                )
            }
            
            // Timeout errors - provide specific guidance
            message.contains("timeout", ignoreCase = true) -> {
                when {
                    message.contains("connect", ignoreCase = true) -> {
                        SSHError.ConnectionTimeout(
                            "Connection timed out while trying to reach the SSH server. " +
                            "This usually means the server is unreachable or a firewall is blocking the connection. " +
                            "Check your network connection and verify the server address and port are correct."
                        )
                    }
                    message.contains("read", ignoreCase = true) -> {
                        SSHError.ConnectionTimeout(
                            "Connection timed out while waiting for server response. " +
                            "The server may be overloaded or experiencing network issues. " +
                            "Try again in a few moments or increase the connection timeout in settings."
                        )
                    }
                    else -> {
                        SSHError.ConnectionTimeout(
                            "Connection timed out. Check your network connection, verify the server is online, " +
                            "and ensure no firewall is blocking the connection. " +
                            "You can increase the timeout in settings if needed."
                        )
                    }
                }
            }
            
            // Network connectivity errors
            message.contains("UnknownHostException", ignoreCase = true) ||
            message.contains("unknown host", ignoreCase = true) -> {
                SSHError.UnknownHost(
                    "Cannot resolve hostname. DNS lookup failed. " +
                    "Check the hostname spelling, verify your DNS is working, " +
                    "or try using an IP address instead."
                )
            }
            
            message.contains("Connection refused", ignoreCase = true) -> {
                SSHError.HostUnreachable(
                    "Connection refused by the server. " +
                    "The server may not be running SSH on the specified port, " +
                    "or a firewall is blocking the connection. " +
                    "Verify the port number (default is 22) and check firewall settings."
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
            
            // Key format errors
            message.contains("invalid privatekey", ignoreCase = true) ||
            message.contains("invalid key", ignoreCase = true) -> {
                SSHError.InvalidKey(
                    "Invalid private key format or corrupted key file. " +
                    "Ensure the key is in a supported format (RSA, ECDSA, or Ed25519) " +
                    "and is not corrupted. You may need to regenerate the key."
                )
            }
            
            message.contains("invalid passphrase", ignoreCase = true) ||
            message.contains("passphrase", ignoreCase = true) -> {
                SSHError.InvalidKey(
                    "Invalid passphrase for the private key. " +
                    "The passphrase you provided is incorrect. " +
                    "Please try again with the correct passphrase."
                )
            }
            
            // Port forwarding errors
            message.contains("forwarding", ignoreCase = true) ||
            message.contains("port forward", ignoreCase = true) -> {
                SSHError.PortForwardingDisabled(
                    "Port forwarding is disabled on the SSH server. " +
                    "The server administrator needs to enable 'AllowTcpForwarding yes' in sshd_config. " +
                    "Contact your server administrator or use a different server that supports port forwarding."
                )
            }
            
            // Session errors
            message.contains("session is down", ignoreCase = true) -> {
                SSHError.SessionClosed("SSH session has been closed unexpectedly")
            }
            
            // Permission errors
            message.contains("Permission denied", ignoreCase = true) -> {
                SSHError.AuthenticationFailed(
                    "Permission denied. The server rejected your authentication attempt. " +
                    "Verify your username is correct and your SSH key is authorized on the server " +
                    "(check ~/.ssh/authorized_keys on the server)."
                )
            }
            
            // Protocol errors
            message.contains("Protocol", ignoreCase = true) -> {
                SSHError.Unknown(
                    "SSH protocol error. The server may be using an incompatible SSH version " +
                    "or configuration. Ensure the server is running a standard SSH daemon (OpenSSH).",
                    e
                )
            }
            
            else -> SSHError.Unknown(message, e)
        }
    }
    
    /**
     * Categorizes authentication failures by specific reason.
     */
    private fun categorizeAuthenticationFailure(message: String): SSHError {
        return when {
            message.contains("publickey", ignoreCase = true) -> {
                SSHError.AuthenticationFailed(
                    "Public key authentication failed. " +
                    "The server rejected your SSH key. " +
                    "Verify the key is authorized on the server (check ~/.ssh/authorized_keys) " +
                    "and matches the key format the server expects."
                )
            }
            
            message.contains("key", ignoreCase = true) -> {
                SSHError.AuthenticationFailed(
                    "SSH key authentication failed. " +
                    "The key may not be authorized on the server or may be in the wrong format. " +
                    "Ensure your public key is added to ~/.ssh/authorized_keys on the server."
                )
            }
            
            message.contains("user", ignoreCase = true) || 
            message.contains("username", ignoreCase = true) -> {
                SSHError.AuthenticationFailed(
                    "Authentication failed - invalid username. " +
                    "The username you provided does not exist on the server. " +
                    "Verify the username is correct and the account exists."
                )
            }
            
            else -> {
                SSHError.AuthenticationFailed(
                    "Authentication failed. The server rejected your credentials. " +
                    "Verify your username and SSH key are correct, and ensure the key is authorized on the server."
                )
            }
        }
    }
}
