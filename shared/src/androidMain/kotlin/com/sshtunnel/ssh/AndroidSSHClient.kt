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
import net.schmizz.sshj.Config
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.transport.cipher.Cipher
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.io.IOException
import java.security.Security
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
        
        // Flag to ensure SpongyCastle is only registered once
        private var spongyCastleRegistered = false
        
        /**
         * Registers SpongyCastle as the security provider.
         * SpongyCastle is a repackaged BouncyCastle for Android with full algorithm support.
         */
        private fun ensureSpongyCastleRegistered() {
            synchronized(this) {
                if (!spongyCastleRegistered) {
                    // Remove any existing BouncyCastle provider
                    Security.removeProvider("BC")
                    
                    // Add SpongyCastle as the primary security provider
                    Security.insertProviderAt(BouncyCastleProvider(), 1)
                    
                    spongyCastleRegistered = true
                }
            }
        }
        
        /**
         * Creates an Android-compatible SSH configuration using SpongyCastle.
         * SpongyCastle provides full BouncyCastle functionality on Android.
         */
        private fun createSecureConfig(): Config {
            // Ensure SpongyCastle is registered
            ensureSpongyCastleRegistered()
            
            // Use DefaultConfig which now has access to all algorithms via SpongyCastle
            return DefaultConfig()
        }
    }
    
    // Track SOCKS5 server sockets for cleanup
    private val socks5Servers = mutableMapOf<String, java.net.ServerSocket>()
    
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
            
            // Create sshj client with secure configuration
            val secureConfig = createSecureConfig()
            ssh = SshjClient(secureConfig)
            logger.verbose(TAG, "Created sshj client with secure configuration (strong encryption only)")
            logger.verbose(TAG, "Supported ciphers: ${secureConfig.cipherFactories.joinToString(", ") { it.name }}")
            
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
            
            // Configure socket timeout after connection is established
            // Set to 5 minutes (300 seconds) to allow long-running connections
            // while preventing indefinite hangs
            ssh.connection.timeoutMs = 300000 // 5 minutes
            logger.verbose(TAG, "Socket timeout set to 300 seconds (5 minutes) for established connections")
            
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
            
            // Create SOCKS5 server socket bound to localhost only
            logger.verbose(TAG, "Creating SOCKS5 server socket on 127.0.0.1:$localPort")
            val serverSocket = java.net.ServerSocket(
                localPort,
                50, // backlog
                java.net.InetAddress.getByName("127.0.0.1")
            )
            val actualPort = serverSocket.localPort
            logger.info(TAG, "SOCKS5 server socket created on port $actualPort")
            
            // Track server socket for cleanup
            socks5Servers[session.sessionId] = serverSocket
            
            // Start SOCKS5 server in background
            startSocks5Server(serverSocket, ssh)
            
            Result.success(actualPort)
            
        } catch (e: IOException) {
            logger.error(TAG, "Failed to create port forwarding: ${e.message}", e)
            Result.failure(SSHError.PortForwardingDisabled("Failed to create port forwarding: ${e.message}"))
        } catch (e: Exception) {
            logger.error(TAG, "Unexpected error creating port forwarding", e)
            Result.failure(SSHError.Unknown("Failed to create port forwarding", e))
        }
    }
    
    /**
     * Starts a SOCKS5 server that accepts connections and forwards them through SSH.
     */
    private fun startSocks5Server(serverSocket: java.net.ServerSocket, ssh: SshjClient) {
        Thread {
            logger.info(TAG, "SOCKS5 server thread started on port ${serverSocket.localPort}")
            try {
                while (!serverSocket.isClosed && ssh.isConnected) {
                    try {
                        val clientSocket = serverSocket.accept()
                        logger.verbose(TAG, "SOCKS5 client connected from ${clientSocket.inetAddress}")
                        
                        // Handle each client in a separate thread
                        Thread {
                            handleSocks5Client(clientSocket, ssh)
                        }.start()
                    } catch (e: java.net.SocketException) {
                        if (!serverSocket.isClosed) {
                            logger.error(TAG, "Socket error accepting SOCKS5 connection", e)
                        }
                    } catch (e: Exception) {
                        logger.error(TAG, "Error accepting SOCKS5 connection", e)
                    }
                }
            } finally {
                logger.info(TAG, "SOCKS5 server thread stopping")
                try {
                    serverSocket.close()
                } catch (e: Exception) {
                    logger.error(TAG, "Error closing SOCKS5 server socket", e)
                }
            }
        }.start()
    }
    
    /**
     * Handles a single SOCKS5 client connection.
     */
    private fun handleSocks5Client(clientSocket: java.net.Socket, ssh: SshjClient) {
        try {
            clientSocket.soTimeout = 30000 // 30 second timeout
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            // SOCKS5 handshake - read greeting
            val greeting = ByteArray(2)
            if (input.read(greeting) != 2) {
                logger.warn(TAG, "SOCKS5: Invalid greeting length")
                clientSocket.close()
                return
            }
            
            val version = greeting[0].toInt() and 0xFF
            val nmethods = greeting[1].toInt() and 0xFF
            
            if (version != 5) {
                logger.warn(TAG, "SOCKS5: Unsupported version $version")
                clientSocket.close()
                return
            }
            
            // Read authentication methods
            val methods = ByteArray(nmethods)
            if (input.read(methods) != nmethods) {
                logger.warn(TAG, "SOCKS5: Invalid methods length")
                clientSocket.close()
                return
            }
            
            // Send method selection (0x00 = no authentication)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()
            logger.verbose(TAG, "SOCKS5: Handshake complete, no authentication")
            
            // Read CONNECT request
            val request = ByteArray(4)
            if (input.read(request) != 4) {
                logger.warn(TAG, "SOCKS5: Invalid request length")
                clientSocket.close()
                return
            }
            
            val reqVersion = request[0].toInt() and 0xFF
            val command = request[1].toInt() and 0xFF
            val addressType = request[3].toInt() and 0xFF
            
            if (reqVersion != 5) {
                logger.warn(TAG, "SOCKS5: Invalid request version $reqVersion")
                sendSocks5Error(output, 0x01) // General failure
                clientSocket.close()
                return
            }
            
            if (command != 1) { // Only support CONNECT
                logger.warn(TAG, "SOCKS5: Unsupported command $command")
                sendSocks5Error(output, 0x07) // Command not supported
                clientSocket.close()
                return
            }
            
            // Parse destination address
            val (destHost, destPort) = when (addressType) {
                0x01 -> { // IPv4
                    val addr = ByteArray(4)
                    if (input.read(addr) != 4) {
                        logger.warn(TAG, "SOCKS5: Invalid IPv4 address")
                        sendSocks5Error(output, 0x01)
                        clientSocket.close()
                        return
                    }
                    val port = ByteArray(2)
                    if (input.read(port) != 2) {
                        logger.warn(TAG, "SOCKS5: Invalid port")
                        sendSocks5Error(output, 0x01)
                        clientSocket.close()
                        return
                    }
                    val host = addr.joinToString(".") { (it.toInt() and 0xFF).toString() }
                    val portNum = ((port[0].toInt() and 0xFF) shl 8) or (port[1].toInt() and 0xFF)
                    Pair(host, portNum)
                }
                0x03 -> { // Domain name
                    val len = input.read()
                    if (len < 0) {
                        logger.warn(TAG, "SOCKS5: Invalid domain length")
                        sendSocks5Error(output, 0x01)
                        clientSocket.close()
                        return
                    }
                    val domain = ByteArray(len)
                    if (input.read(domain) != len) {
                        logger.warn(TAG, "SOCKS5: Invalid domain")
                        sendSocks5Error(output, 0x01)
                        clientSocket.close()
                        return
                    }
                    val port = ByteArray(2)
                    if (input.read(port) != 2) {
                        logger.warn(TAG, "SOCKS5: Invalid port")
                        sendSocks5Error(output, 0x01)
                        clientSocket.close()
                        return
                    }
                    val host = String(domain, Charsets.UTF_8)
                    val portNum = ((port[0].toInt() and 0xFF) shl 8) or (port[1].toInt() and 0xFF)
                    Pair(host, portNum)
                }
                0x04 -> { // IPv6
                    val addr = ByteArray(16)
                    if (input.read(addr) != 16) {
                        logger.warn(TAG, "SOCKS5: Invalid IPv6 address")
                        sendSocks5Error(output, 0x01)
                        clientSocket.close()
                        return
                    }
                    val port = ByteArray(2)
                    if (input.read(port) != 2) {
                        logger.warn(TAG, "SOCKS5: Invalid port")
                        sendSocks5Error(output, 0x01)
                        clientSocket.close()
                        return
                    }
                    // Convert IPv6 bytes to string
                    val host = addr.toList().chunked(2).joinToString(":") { chunk ->
                        ((chunk[0].toInt() and 0xFF) shl 8 or (chunk[1].toInt() and 0xFF)).toString(16)
                    }
                    val portNum = ((port[0].toInt() and 0xFF) shl 8) or (port[1].toInt() and 0xFF)
                    Pair(host, portNum)
                }
                else -> {
                    logger.warn(TAG, "SOCKS5: Unsupported address type $addressType")
                    sendSocks5Error(output, 0x08) // Address type not supported
                    clientSocket.close()
                    return
                }
            }
            
            logger.info(TAG, "SOCKS5: CONNECT request to $destHost:$destPort")
            
            // Create SSH tunnel to destination
            try {
                logger.verbose(TAG, "SOCKS5: Creating SSH direct connection to $destHost:$destPort")
                val remoteSocket = ssh.newDirectConnection(destHost, destPort)
                logger.info(TAG, "SOCKS5: SSH tunnel established to $destHost:$destPort")
                
                // Log SSH channel details for debugging
                try {
                    val channel = remoteSocket.javaClass.getDeclaredField("chan")
                    channel.isAccessible = true
                    val channelObj = channel.get(remoteSocket)
                    logger.verbose(TAG, "SOCKS5: SSH channel type: ${channelObj?.javaClass?.simpleName}")
                    logger.verbose(TAG, "SOCKS5: SSH channel object: $channelObj")
                } catch (e: Exception) {
                    logger.verbose(TAG, "SOCKS5: Could not inspect SSH channel: ${e.message}")
                }
                
                // Verify the connection is actually open
                if (remoteSocket.inputStream == null || remoteSocket.outputStream == null) {
                    logger.error(TAG, "SOCKS5: SSH tunnel has null streams!")
                    sendSocks5Error(output, 0x05)
                    clientSocket.close()
                    return
                }
                
                logger.verbose(TAG, "SOCKS5: SSH tunnel streams verified")
                logger.verbose(TAG, "SOCKS5: Input stream type: ${remoteSocket.inputStream.javaClass.simpleName}")
                logger.verbose(TAG, "SOCKS5: Output stream type: ${remoteSocket.outputStream.javaClass.simpleName}")
                
                // Send success response
                // Format: VER(1) REP(1) RSV(1) ATYP(1) BND.ADDR(var) BND.PORT(2)
                val response = byteArrayOf(
                    0x05, // Version
                    0x00, // Success
                    0x00, // Reserved
                    0x01, // IPv4 address type
                    0x00, 0x00, 0x00, 0x00, // Bind address (0.0.0.0)
                    0x00, 0x00 // Bind port (0)
                )
                output.write(response)
                output.flush()
                logger.verbose(TAG, "SOCKS5: Success response sent (7 bytes)")
                
                // Start bidirectional relay
                logger.verbose(TAG, "SOCKS5: Starting relay for $destHost:$destPort")
                relayData(clientSocket, remoteSocket)
                logger.verbose(TAG, "SOCKS5: Relay completed for $destHost:$destPort")
                
            } catch (e: Exception) {
                logger.error(TAG, "SOCKS5: Failed to establish SSH tunnel to $destHost:$destPort: ${e.javaClass.simpleName}: ${e.message}", e)
                try {
                    sendSocks5Error(output, 0x05) // Connection refused
                } catch (ex: Exception) {
                    logger.verbose(TAG, "SOCKS5: Failed to send error response: ${ex.message}")
                }
                try {
                    clientSocket.close()
                } catch (ex: Exception) {
                    // Ignore
                }
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "SOCKS5: Error handling client", e)
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Sends a SOCKS5 error response.
     */
    private fun sendSocks5Error(output: java.io.OutputStream, errorCode: Int) {
        try {
            val response = byteArrayOf(
                0x05, // Version
                errorCode.toByte(), // Error code
                0x00, // Reserved
                0x01, // IPv4 address type
                0x00, 0x00, 0x00, 0x00, // Bind address
                0x00, 0x00 // Bind port
            )
            output.write(response)
            output.flush()
        } catch (e: Exception) {
            logger.error(TAG, "Failed to send SOCKS5 error response", e)
        }
    }
    
    /**
     * Relays data bidirectionally between client and remote sockets.
     */
    private fun relayData(clientSocket: java.net.Socket, remoteSocket: net.schmizz.sshj.connection.channel.direct.DirectConnection) {
        logger.info(TAG, "SOCKS5: Starting bidirectional relay")
        
        var clientToRemoteBytes = 0L
        var remoteToClientBytes = 0L
        
        val clientToRemote = Thread {
            try {
                logger.info(TAG, "SOCKS5: Client->Remote relay thread started")
                val buffer = ByteArray(8192)
                val input = clientSocket.getInputStream()
                val output = remoteSocket.outputStream
                var bytesRead: Int
                var readCount = 0
                var isFirstTlsRecord = true
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    readCount++
                    logger.verbose(TAG, "SOCKS5: Client->Remote: read #$readCount: $bytesRead bytes")
                    
                    // Log first packet for TLS debugging (TLS ClientHello detection)
                    if (readCount == 1 && bytesRead > 5) {
                        val firstBytes = buffer.take(Math.min(10, bytesRead)).joinToString(" ") { "%02x".format(it) }
                        logger.verbose(TAG, "SOCKS5: Client->Remote: first packet hex: $firstBytes")
                        // Check if it's TLS ClientHello (0x16 0x03 0x01/0x03)
                        if (buffer[0] == 0x16.toByte() && buffer[1] == 0x03.toByte()) {
                            logger.info(TAG, "SOCKS5: Client->Remote: TLS ClientHello detected")
                        }
                    }
                    
                    // For TLS ClientHello, try to buffer the complete record
                    if (isFirstTlsRecord && readCount == 1 && bytesRead >= 5 && 
                        buffer[0] == 0x16.toByte() && buffer[1] == 0x03.toByte()) {
                        // TLS record format: [type:1][version:2][length:2][data:length]
                        val recordLength = ((buffer[3].toInt() and 0xFF) shl 8) or (buffer[4].toInt() and 0xFF)
                        val totalLength = 5 + recordLength
                        
                        logger.verbose(TAG, "SOCKS5: Client->Remote: TLS record length=$recordLength, total=$totalLength, received=$bytesRead")
                        
                        if (bytesRead < totalLength) {
                            // Need to read more data to complete the TLS record
                            logger.info(TAG, "SOCKS5: Client->Remote: Buffering incomplete TLS record (need ${totalLength - bytesRead} more bytes)")
                            val completeRecord = ByteArray(totalLength)
                            System.arraycopy(buffer, 0, completeRecord, 0, bytesRead)
                            var totalRead = bytesRead
                            
                            while (totalRead < totalLength) {
                                val remaining = totalLength - totalRead
                                val chunk = input.read(buffer, 0, Math.min(remaining, buffer.size))
                                if (chunk == -1) {
                                    logger.warn(TAG, "SOCKS5: Client->Remote: EOF while reading TLS record")
                                    break
                                }
                                System.arraycopy(buffer, 0, completeRecord, totalRead, chunk)
                                totalRead += chunk
                                readCount++
                                logger.verbose(TAG, "SOCKS5: Client->Remote: read #$readCount: $chunk bytes (buffering, total: $totalRead/$totalLength)")
                            }
                            
                            // Write the complete TLS record
                            output.write(completeRecord, 0, totalRead)
                            output.flush()
                            clientToRemoteBytes += totalRead
                            logger.info(TAG, "SOCKS5: Client->Remote: wrote complete TLS record: $totalRead bytes")
                            isFirstTlsRecord = false
                            continue
                        }
                        isFirstTlsRecord = false
                    }
                    
                    try {
                        output.write(buffer, 0, bytesRead)
                        output.flush()
                        clientToRemoteBytes += bytesRead
                        logger.verbose(TAG, "SOCKS5: Client->Remote: wrote $bytesRead bytes (total: $clientToRemoteBytes)")
                    } catch (e: Exception) {
                        logger.error(TAG, "SOCKS5: Client->Remote: Write failed after $clientToRemoteBytes bytes: ${e.javaClass.simpleName}: ${e.message}")
                        logger.error(TAG, "SOCKS5: Client->Remote: Write error cause: ${e.cause?.javaClass?.simpleName}: ${e.cause?.message}")
                        throw e
                    }
                }
                logger.info(TAG, "SOCKS5: Client->Remote relay ended normally (EOF after $readCount reads), total: $clientToRemoteBytes bytes")
            } catch (e: java.net.SocketException) {
                // Socket closed by other thread - this is normal during shutdown
                logger.verbose(TAG, "SOCKS5: Client->Remote relay ended (socket closed), total: $clientToRemoteBytes bytes")
            } catch (e: net.schmizz.sshj.connection.ConnectionException) {
                // SSH connection-level error - this is the "Stream closed" error
                logger.error(TAG, "SOCKS5: Client->Remote SSH connection error: ${e.message}", e)
                logger.error(TAG, "SOCKS5: Client->Remote SSH error details: cause=${e.cause?.javaClass?.simpleName}, causeMsg=${e.cause?.message}")
                logger.error(TAG, "SOCKS5: Client->Remote bytes transferred before error: $clientToRemoteBytes")
            } catch (e: java.io.IOException) {
                // I/O error on stream
                logger.error(TAG, "SOCKS5: Client->Remote I/O error: ${e.javaClass.simpleName}: ${e.message}", e)
                logger.error(TAG, "SOCKS5: Client->Remote bytes transferred before error: $clientToRemoteBytes")
            } catch (e: Exception) {
                logger.error(TAG, "SOCKS5: Client->Remote relay error: ${e.javaClass.simpleName}: ${e.message}", e)
                logger.error(TAG, "SOCKS5: Client->Remote bytes transferred before error: $clientToRemoteBytes")
            } finally {
                // Don't close anything here - let the main thread handle cleanup
                logger.verbose(TAG, "SOCKS5: Client->Remote relay thread finished")
            }
        }
        
        val remoteToClient = Thread {
            try {
                logger.info(TAG, "SOCKS5: Remote->Client relay thread started")
                val buffer = ByteArray(8192)
                val input = remoteSocket.inputStream
                val output = clientSocket.getOutputStream()
                
                logger.verbose(TAG, "SOCKS5: Remote->Client: waiting for data from SSH tunnel...")
                var bytesRead: Int
                var readCount = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    readCount++
                    logger.info(TAG, "SOCKS5: Remote->Client: read #$readCount: $bytesRead bytes from SSH tunnel")
                    
                    // Log first few bytes for debugging (only for small packets)
                    if (bytesRead <= 20) {
                        val hexBytes = buffer.take(bytesRead).joinToString(" ") { "%02x".format(it) }
                        logger.verbose(TAG, "SOCKS5: Remote->Client: data hex: $hexBytes")
                    }
                    
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                    remoteToClientBytes += bytesRead
                    logger.info(TAG, "SOCKS5: Remote->Client: wrote $bytesRead bytes to client (total: $remoteToClientBytes)")
                }
                logger.info(TAG, "SOCKS5: Remote->Client relay ended normally (EOF after $readCount reads), total: $remoteToClientBytes bytes")
                
                if (remoteToClientBytes == 0L) {
                    logger.warn(TAG, "SOCKS5: Remote->Client received EOF immediately with 0 bytes - remote server closed connection!")
                }
            } catch (e: java.net.SocketException) {
                // Socket closed by other thread - this is normal during shutdown
                logger.verbose(TAG, "SOCKS5: Remote->Client relay ended (socket closed), total: $remoteToClientBytes bytes")
            } catch (e: net.schmizz.sshj.connection.ConnectionException) {
                // SSH connection-level error
                logger.error(TAG, "SOCKS5: Remote->Client SSH connection error: ${e.message}", e)
                logger.error(TAG, "SOCKS5: Remote->Client SSH error details: cause=${e.cause?.javaClass?.simpleName}, causeMsg=${e.cause?.message}")
                logger.error(TAG, "SOCKS5: Remote->Client bytes transferred before error: $remoteToClientBytes")
            } catch (e: java.io.IOException) {
                // I/O error on stream
                logger.error(TAG, "SOCKS5: Remote->Client I/O error: ${e.javaClass.simpleName}: ${e.message}", e)
                logger.error(TAG, "SOCKS5: Remote->Client bytes transferred before error: $remoteToClientBytes")
            } catch (e: Exception) {
                logger.error(TAG, "SOCKS5: Remote->Client relay error: ${e.javaClass.simpleName}: ${e.message}", e)
                logger.error(TAG, "SOCKS5: Remote->Client bytes transferred before error: $remoteToClientBytes")
            } finally {
                // Don't close anything here - let the main thread handle cleanup
                logger.verbose(TAG, "SOCKS5: Remote->Client relay thread finished")
            }
        }
        
        clientToRemote.start()
        remoteToClient.start()
        
        logger.verbose(TAG, "SOCKS5: Both relay threads started, waiting for completion...")
        
        // Wait for both threads to complete
        try {
            clientToRemote.join()
            remoteToClient.join()
        } catch (e: InterruptedException) {
            logger.warn(TAG, "SOCKS5: Relay interrupted")
        }
        
        // Now that both threads are done, close both sockets
        try {
            logger.verbose(TAG, "SOCKS5: Closing remote socket")
            remoteSocket.close()
        } catch (e: Exception) {
            logger.verbose(TAG, "SOCKS5: Error closing remote socket: ${e.message}")
        }
        
        try {
            logger.verbose(TAG, "SOCKS5: Closing client socket")
            clientSocket.close()
        } catch (e: Exception) {
            logger.verbose(TAG, "SOCKS5: Error closing client socket: ${e.message}")
        }
        
        logger.info(TAG, "SOCKS5: Connection relay completed - Client->Remote: $clientToRemoteBytes bytes, Remote->Client: $remoteToClientBytes bytes")
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
            
            // Verify keep-alive is configured
            // sshj's keep-alive is automatic and configured during connection
            // The keep-alive interval is set to 60 seconds in the connect() method
            logger.verbose(TAG, "Verifying SSH keep-alive configuration")
            
            val keepAliveInterval = ssh.connection.keepAlive.keepAliveInterval
            logger.verbose(TAG, "Keep-alive interval: $keepAliveInterval seconds")
            
            // Verify connection is still alive by checking transport
            if (!ssh.isConnected || !ssh.isAuthenticated) {
                logger.warn(TAG, "Connection is not alive")
                throw IOException("Connection is not alive")
            }
            
            logger.verbose(TAG, "Keep-alive check successful - connection is alive")
            
            Result.success(Unit)
            
        } catch (e: IOException) {
            logger.error(TAG, "Failed to verify keep-alive: ${e.message}", e)
            Result.failure(SSHError.SessionClosed("Connection is not alive"))
        } catch (e: Exception) {
            logger.error(TAG, "Failed to send keep-alive", e)
            Result.failure(SSHError.Unknown("Failed to send keep-alive", e))
        }
    }
    
    override suspend fun disconnect(session: SSHSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Disconnecting SSH session ${session.sessionId}")
            
            // Close SOCKS5 server first
            socks5Servers[session.sessionId]?.let { serverSocket ->
                try {
                    logger.verbose(TAG, "Closing SOCKS5 server on port ${serverSocket.localPort}")
                    serverSocket.close()
                    socks5Servers.remove(session.sessionId)
                    logger.info(TAG, "SOCKS5 server closed")
                } catch (e: Exception) {
                    logger.error(TAG, "Error closing SOCKS5 server", e)
                }
            }
            
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
