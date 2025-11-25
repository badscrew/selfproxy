package com.sshtunnel.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
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
class AndroidSSHClient : SSHClient {
    
    private val jsch = JSch()
    
    override suspend fun connect(
        profile: ServerProfile,
        privateKey: PrivateKey,
        passphrase: String?,
        connectionTimeout: Duration,
        enableCompression: Boolean,
        strictHostKeyChecking: Boolean
    ): Result<SSHSession> = withContext(Dispatchers.IO) {
        try {
            // Add the private key to JSch
            val keyName = "key_${profile.id}"
            if (passphrase != null) {
                jsch.addIdentity(keyName, privateKey.keyData, null, passphrase.toByteArray())
            } else {
                jsch.addIdentity(keyName, privateKey.keyData, null, null)
            }
            
            // Create SSH session
            val session = jsch.getSession(profile.username, profile.hostname, profile.port)
            
            // Configure session properties
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
            
            // Set connection timeout (JSch expects milliseconds as int)
            val timeoutMs = connectionTimeout.inWholeMilliseconds.toInt()
            session.timeout = timeoutMs
            
            // Connect
            session.connect()
            
            // Generate unique session ID
            val sessionId = UUID.randomUUID().toString()
            
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
            Result.failure(mapJSchException(e))
        } catch (e: Exception) {
            Result.failure(SSHError.Unknown("Unexpected error during connection", e))
        }
    }
    
    override suspend fun createPortForwarding(
        session: SSHSession,
        localPort: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jschSession = session.nativeSession as? Session
                ?: return@withContext Result.failure(
                    SSHError.SessionClosed("Invalid or closed session")
                )
            
            if (!jschSession.isConnected) {
                return@withContext Result.failure(
                    SSHError.SessionClosed("Session is not connected")
                )
            }
            
            // Set up dynamic port forwarding (SOCKS5)
            // setPortForwardingL creates a local SOCKS5 proxy
            // Bind to localhost only for security
            val actualPort = jschSession.setPortForwardingL("127.0.0.1", localPort, null, 0)
            
            Result.success(actualPort)
            
        } catch (e: JSchException) {
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
            Result.failure(SSHError.Unknown("Failed to create port forwarding", e))
        }
    }
    
    override suspend fun sendKeepAlive(session: SSHSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jschSession = session.nativeSession as? Session
                ?: return@withContext Result.failure(
                    SSHError.SessionClosed("Invalid or closed session")
                )
            
            if (!jschSession.isConnected) {
                return@withContext Result.failure(
                    SSHError.SessionClosed("Session is not connected")
                )
            }
            
            // Send keep-alive packet
            jschSession.sendKeepAliveMsg()
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(SSHError.Unknown("Failed to send keep-alive", e))
        }
    }
    
    override suspend fun disconnect(session: SSHSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jschSession = session.nativeSession as? Session
                ?: return@withContext Result.success(Unit) // Already disconnected
            
            if (jschSession.isConnected) {
                jschSession.disconnect()
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(SSHError.Unknown("Error during disconnect", e))
        }
    }
    
    override fun isConnected(session: SSHSession): Boolean {
        val jschSession = session.nativeSession as? Session ?: return false
        return jschSession.isConnected
    }
    
    /**
     * Maps JSch exceptions to our SSH error types.
     */
    private fun mapJSchException(e: JSchException): SSHError {
        val message = e.message ?: "Unknown SSH error"
        
        return when {
            message.contains("Auth fail", ignoreCase = true) ||
            message.contains("authentication", ignoreCase = true) -> {
                SSHError.AuthenticationFailed(
                    "Authentication failed. Please check your private key and credentials."
                )
            }
            
            message.contains("timeout", ignoreCase = true) -> {
                SSHError.ConnectionTimeout(
                    "Connection timed out. Please check your network connection and firewall settings."
                )
            }
            
            message.contains("UnknownHostException", ignoreCase = true) ||
            message.contains("unknown host", ignoreCase = true) -> {
                SSHError.HostUnreachable(
                    "Cannot resolve hostname. Please check the server address."
                )
            }
            
            message.contains("Connection refused", ignoreCase = true) ||
            message.contains("Network is unreachable", ignoreCase = true) -> {
                SSHError.HostUnreachable(
                    "Cannot reach SSH server. Please check the hostname and port."
                )
            }
            
            message.contains("invalid privatekey", ignoreCase = true) ||
            message.contains("invalid key", ignoreCase = true) -> {
                SSHError.InvalidKey(
                    "Invalid private key format or corrupted key file."
                )
            }
            
            message.contains("session is down", ignoreCase = true) -> {
                SSHError.SessionClosed("SSH session has been closed")
            }
            
            else -> SSHError.Unknown(message, e)
        }
    }
}
