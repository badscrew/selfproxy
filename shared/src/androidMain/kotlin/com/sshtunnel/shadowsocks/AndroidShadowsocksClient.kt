package com.sshtunnel.shadowsocks

import com.sshtunnel.data.CipherMethod
import com.sshtunnel.data.ShadowsocksConfig
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 * Android implementation of ShadowsocksClient.
 * 
 * This is a basic implementation that validates configuration and simulates
 * a local SOCKS5 proxy. In production, this would integrate with shadowsocks-android
 * library or shadowsocks-libev native binaries.
 * 
 * TODO: Integrate with actual Shadowsocks library (shadowsocks-android or shadowsocks-libev)
 */
class AndroidShadowsocksClient(
    private val logger: Logger
) : ShadowsocksClient {
    
    private val _state = MutableStateFlow<ShadowsocksState>(ShadowsocksState.Idle)
    
    private var currentConfig: ShadowsocksConfig? = null
    private var localPort: Int? = null
    private var proxyJob: Job? = null
    
    companion object {
        private const val TAG = "ShadowsocksClient"
    }
    
    override suspend fun start(config: ShadowsocksConfig): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Starting Shadowsocks client")
            
            // Validate configuration
            validateConfig(config).getOrElse { error ->
                logger.error(TAG, "Invalid configuration: ${error.message}", error)
                _state.value = ShadowsocksState.Error(error.message ?: "Invalid configuration", error)
                return@withContext Result.failure(error)
            }
            
            // Update state to starting
            _state.value = ShadowsocksState.Starting
            
            // Test connection to server first
            testServerConnection(config).getOrElse { error ->
                logger.error(TAG, "Failed to connect to server: ${error.message}", error)
                _state.value = ShadowsocksState.Error(
                    "Cannot reach Shadowsocks server: ${error.message}",
                    error
                )
                return@withContext Result.failure(error)
            }
            
            // Allocate a local port for SOCKS5 proxy
            // In production, this would start the actual Shadowsocks local proxy
            val port = allocateLocalPort()
            localPort = port
            currentConfig = config
            
            // Note: In production, we would start the actual proxy service here
            // For now, we just mark the state as running without a background job
            // This avoids issues with uncompleted coroutines in tests
            
            // Update state to running
            _state.value = ShadowsocksState.Running(port)
            logger.info(TAG, "Shadowsocks client started on port $port")
            
            Result.success(port)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to start Shadowsocks client", e)
            _state.value = ShadowsocksState.Error(e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }
    
    override suspend fun stop() = withContext(Dispatchers.IO) {
        try {
            logger.info(TAG, "Stopping Shadowsocks client")
            
            // In production, this would stop the actual Shadowsocks proxy service
            
            // Clear state
            localPort = null
            currentConfig = null
            
            // Update state
            _state.value = ShadowsocksState.Idle
            logger.info(TAG, "Shadowsocks client stopped")
        } catch (e: Exception) {
            logger.error(TAG, "Error stopping Shadowsocks client", e)
            _state.value = ShadowsocksState.Error(e.message ?: "Error stopping", e)
        }
    }
    
    override suspend fun testConnection(config: ShadowsocksConfig): Result<ConnectionTestResult> = 
        withContext(Dispatchers.IO) {
            try {
                logger.info(TAG, "Testing connection to ${config.serverHost}:${config.serverPort}")
                
                // Validate configuration first
                validateConfig(config).getOrElse { error ->
                    return@withContext Result.success(
                        ConnectionTestResult(
                            success = false,
                            latencyMs = null,
                            errorMessage = "Invalid configuration: ${error.message}"
                        )
                    )
                }
                
                // Test server connection
                val startTime = System.currentTimeMillis()
                testServerConnection(config).getOrElse { error ->
                    return@withContext Result.success(
                        ConnectionTestResult(
                            success = false,
                            latencyMs = null,
                            errorMessage = error.message ?: "Connection failed"
                        )
                    )
                }
                val latency = System.currentTimeMillis() - startTime
                
                logger.info(TAG, "Connection test successful, latency: ${latency}ms")
                Result.success(
                    ConnectionTestResult(
                        success = true,
                        latencyMs = latency,
                        errorMessage = null
                    )
                )
            } catch (e: Exception) {
                logger.error(TAG, "Connection test failed", e)
                Result.success(
                    ConnectionTestResult(
                        success = false,
                        latencyMs = null,
                        errorMessage = e.message ?: "Unknown error"
                    )
                )
            }
        }
    
    override fun observeState(): Flow<ShadowsocksState> = _state.asStateFlow()
    
    /**
     * Validate Shadowsocks configuration.
     */
    private fun validateConfig(config: ShadowsocksConfig): Result<Unit> {
        // Validate server host
        if (config.serverHost.isBlank()) {
            return Result.failure(IllegalArgumentException("Server host cannot be empty"))
        }
        
        // Validate port range
        if (config.serverPort !in 1..65535) {
            return Result.failure(
                IllegalArgumentException("Server port must be between 1 and 65535")
            )
        }
        
        // Validate password
        if (config.password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty"))
        }
        
        // Validate cipher method
        if (!isCipherSupported(config.cipher)) {
            return Result.failure(
                IllegalArgumentException("Unsupported cipher: ${config.cipher}")
            )
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Check if cipher method is supported.
     */
    private fun isCipherSupported(cipher: CipherMethod): Boolean {
        return when (cipher) {
            CipherMethod.AES_256_GCM,
            CipherMethod.CHACHA20_IETF_POLY1305,
            CipherMethod.AES_128_GCM -> true
        }
    }
    
    /**
     * Test connection to Shadowsocks server.
     * Attempts to establish a TCP connection to verify server is reachable.
     */
    private suspend fun testServerConnection(config: ShadowsocksConfig): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(
                    InetSocketAddress(config.serverHost, config.serverPort),
                    config.timeout.inWholeMilliseconds.toInt()
                )
                socket.close()
                Result.success(Unit)
            } catch (e: SocketTimeoutException) {
                Result.failure(IOException("Connection timeout: Server did not respond"))
            } catch (e: IOException) {
                Result.failure(IOException("Cannot reach server: ${e.message}"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    /**
     * Allocate a random local port for SOCKS5 proxy.
     * In production, this would be handled by the Shadowsocks library.
     */
    private fun allocateLocalPort(): Int {
        // Use a random port in the dynamic/private range (49152-65535)
        return Random.nextInt(49152, 65535)
    }
}
