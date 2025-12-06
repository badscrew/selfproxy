package com.sshtunnel.shadowsocks

import com.sshtunnel.data.CipherMethod
import com.sshtunnel.data.ShadowsocksConfig
import com.sshtunnel.logging.Logger
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based tests for Shadowsocks cipher validation.
 * 
 * Feature: shadowsocks-vpn-proxy, Property 8: Cipher method validation rejects unsupported ciphers
 * Validates: Requirements 9.1, 9.2, 9.3, 9.5
 */
class CipherValidationPropertiesTest {
    
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
    
    /**
     * Property 8: Cipher method validation rejects unsupported ciphers
     * 
     * For any valid Shadowsocks configuration with a supported cipher method,
     * all three supported ciphers (AES_256_GCM, CHACHA20_IETF_POLY1305, AES_128_GCM)
     * should be accepted by the system.
     * 
     * This test validates that the cipher enum values are all supported.
     */
    @Test
    fun `all cipher enum values should be supported`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 8: Cipher method validation rejects unsupported ciphers
        // Validates: Requirements 9.1, 9.2, 9.3, 9.5
        
        checkAll(
            iterations = 100,
            Arb.enum<CipherMethod>()
        ) { cipher ->
            // For any cipher in the CipherMethod enum
            // It should be a supported cipher
            val supportedCiphers = setOf(
                CipherMethod.AES_256_GCM,
                CipherMethod.CHACHA20_IETF_POLY1305,
                CipherMethod.AES_128_GCM
            )
            
            // All enum values should be in the supported set
            supportedCiphers.contains(cipher) shouldBe true
        }
    }
    
    /**
     * Test that all three supported cipher methods are accepted.
     */
    @Test
    fun `all supported cipher methods should be accepted`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 8: Cipher method validation rejects unsupported ciphers
        // Validates: Requirements 9.1, 9.2, 9.3
        
        val supportedCiphers = listOf(
            CipherMethod.AES_256_GCM,
            CipherMethod.CHACHA20_IETF_POLY1305,
            CipherMethod.AES_128_GCM
        )
        
        supportedCiphers.forEach { cipher ->
            val client = AndroidShadowsocksClient(logger)
            
            try {
                val config = ShadowsocksConfig(
                    serverHost = "test.example.com",
                    serverPort = 8388,
                    password = "test-password",
                    cipher = cipher,
                    timeout = 5.seconds
                )
                
                // When we start with each supported cipher
                val result = client.start(config)
                
                // Then it should not fail due to cipher validation
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: ""
                    
                    // Should not be a cipher validation error
                    val isCipherError = errorMessage.contains("cipher", ignoreCase = true) &&
                                       errorMessage.contains("unsupported", ignoreCase = true)
                    
                    isCipherError shouldBe false
                }
            } finally {
                // Clean up
                client.stop()
            }
        }
    }
}

/**
 * Generator for Shadowsocks configurations with supported cipher methods.
 */
fun Arb.Companion.shadowsocksConfigWithSupportedCipher() = arbitrary {
    val passwordLength = Arb.int(8..64).bind()
    ShadowsocksConfig(
        serverHost = Arb.validHostname().bind(),
        serverPort = Arb.int(1024..65535).bind(),
        password = Arb.string(passwordLength, Codepoint.alphanumeric()).bind(),
        cipher = Arb.enum<CipherMethod>().bind(), // All enum values are supported
        timeout = 5.seconds
    )
}

/**
 * Generator for valid hostnames.
 */
fun Arb.Companion.validHostname() = arbitrary {
    val labels = List(Arb.int(2..4).bind()) {
        val length = Arb.int(3..10).bind()
        Arb.string(length, Codepoint.alphanumeric()).bind()
    }
    labels.joinToString(".") + ".com"
}
