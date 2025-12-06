package com.sshtunnel.shadowsocks

import com.sshtunnel.data.CipherMethod
import com.sshtunnel.data.ShadowsocksConfig
import com.sshtunnel.logging.Logger
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for AndroidShadowsocksClient.
 * Tests start/stop lifecycle, connection state transitions, and error handling.
 * 
 * Requirements: 3.1, 3.2, 3.5
 */
class AndroidShadowsocksClientTest {
    
    private val logger = object : Logger {
        override fun verbose(tag: String, message: String, throwable: Throwable?) {}
        override fun debug(tag: String, message: String, throwable: Throwable?) {}
        override fun info(tag: String, message: String, throwable: Throwable?) {}
        override fun warn(tag: String, message: String, throwable: Throwable?) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun getLogEntries() = emptyList<com.sshtunnel.logging.LogEntry>()
        override fun clearLogs() {}
        override fun setVerboseEnabled(enabled: Boolean) {}
        override fun isVerboseEnabled() = false
    }
    
    private fun createValidConfig() = ShadowsocksConfig(
        serverHost = "test.example.com",
        serverPort = 8388,
        password = "test-password",
        cipher = CipherMethod.AES_256_GCM,
        timeout = 5.seconds
    )
    
    /**
     * Test that initial state is Idle.
     */
    @Test
    fun `initial state should be Idle`() = runTest {
        val client = AndroidShadowsocksClient(logger)
        
        val state = client.observeState().first()
        
        state.shouldBeInstanceOf<ShadowsocksState.Idle>()
    }
    
    /**
     * Test that stop() transitions state to Idle.
     */
    @Test
    fun `stop should transition state to Idle`() = runTest {
        val client = AndroidShadowsocksClient(logger)
        
        client.stop()
        val state = client.observeState().first()
        
        state.shouldBeInstanceOf<ShadowsocksState.Idle>()
    }
    
    /**
     * Test that invalid configuration (empty host) is rejected.
     */
    @Test
    fun `start with empty server host should fail`() = runTest {
        val client = AndroidShadowsocksClient(logger)
        val config = createValidConfig().copy(serverHost = "")
        
        val result = client.start(config)
        
        result.isFailure shouldBe true
        val error = result.exceptionOrNull()
        error?.message shouldBe "Server host cannot be empty"
    }
    
    /**
     * Test that invalid configuration (invalid port) is rejected.
     */
    @Test
    fun `start with invalid port should fail`() = runTest {
        val client = AndroidShadowsocksClient(logger)
        val config = createValidConfig().copy(serverPort = 0)
        
        val result = client.start(config)
        
        result.isFailure shouldBe true
        val error = result.exceptionOrNull()
        error?.message shouldBe "Server port must be between 1 and 65535"
    }
    
    /**
     * Test that invalid configuration (port too high) is rejected.
     */
    @Test
    fun `start with port above 65535 should fail`() = runTest {
        val client = AndroidShadowsocksClient(logger)
        val config = createValidConfig().copy(serverPort = 70000)
        
        val result = client.start(config)
        
        result.isFailure shouldBe true
        val error = result.exceptionOrNull()
        error?.message shouldBe "Server port must be between 1 and 65535"
    }
    
    /**
     * Test that invalid configuration (empty password) is rejected.
     */
    @Test
    fun `start with empty password should fail`() = runTest {
        val client = AndroidShadowsocksClient(logger)
        val config = createValidConfig().copy(password = "")
        
        val result = client.start(config)
        
        result.isFailure shouldBe true
        val error = result.exceptionOrNull()
        error?.message shouldBe "Password cannot be empty"
    }
    
    /**
     * Test that all supported ciphers are accepted.
     */
    @Test
    fun `all supported ciphers should be accepted`() = runTest {
        val supportedCiphers = listOf(
            CipherMethod.AES_256_GCM,
            CipherMethod.CHACHA20_IETF_POLY1305,
            CipherMethod.AES_128_GCM
        )
        
        supportedCiphers.forEach { cipher ->
            val client = AndroidShadowsocksClient(logger)
            val config = createValidConfig().copy(cipher = cipher)
            
            // Start will fail due to network, but not due to cipher validation
            val result = client.start(config)
            
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                val errorMessage = error?.message ?: ""
                
                // Should not be a cipher error
                val isCipherError = errorMessage.contains("cipher", ignoreCase = true) &&
                                   errorMessage.contains("unsupported", ignoreCase = true)
                
                isCipherError shouldBe false
            }
            
            client.stop()
        }
    }
    
    /**
     * Test connection test with invalid configuration.
     */
    @Test
    fun `testConnection with empty host should return failure`() = runTest {
        val client = AndroidShadowsocksClient(logger)
        val config = createValidConfig().copy(serverHost = "")
        
        val result = client.testConnection(config)
        
        result.isSuccess shouldBe true
        val testResult = result.getOrNull()!!
        testResult.success shouldBe false
        testResult.errorMessage shouldBe "Invalid configuration: Server host cannot be empty"
    }
    
    /**
     * Test connection test with invalid port.
     */
    @Test
    fun `testConnection with invalid port should return failure`() = runTest {
        val client = AndroidShadowsocksClient(logger)
        val config = createValidConfig().copy(serverPort = -1)
        
        val result = client.testConnection(config)
        
        result.isSuccess shouldBe true
        val testResult = result.getOrNull()!!
        testResult.success shouldBe false
        testResult.errorMessage shouldBe "Invalid configuration: Server port must be between 1 and 65535"
    }
    
    /**
     * Test connection test with unreachable server.
     */
    @Test
    fun `testConnection with unreachable server should return failure`() = runTest {
        val client = AndroidShadowsocksClient(logger)
        val config = createValidConfig()
        
        val result = client.testConnection(config)
        
        result.isSuccess shouldBe true
        val testResult = result.getOrNull()!!
        testResult.success shouldBe false
        testResult.latencyMs shouldBe null
        // Error message should indicate connection failure (exact message may vary)
        testResult.errorMessage?.contains("Cannot reach server") shouldBe true
    }
}
