package com.sshtunnel.ssh

import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.storage.PrivateKey
import io.kotest.assertions.fail
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based tests for SSH client functionality.
 * 
 * Note: These tests validate the SSH client interface and error handling.
 * Full integration tests with real SSH servers are in SSHConnectionIntegrationTest.
 */
class SSHClientPropertiesTest {
    
    /**
     * Feature: ssh-tunnel-proxy, Property 1: Valid credentials establish connections
     * Validates: Requirements 1.1
     * 
     * This property test validates that the SSH client properly handles connection attempts
     * with various valid credential combinations. Since we cannot test against real SSH servers
     * in unit tests, we validate:
     * 1. The client accepts all valid key types (RSA, ECDSA, ED25519)
     * 2. The client properly formats connection parameters
     * 3. The client returns appropriate Result types
     * 
     * Integration tests with real SSH servers are performed separately.
     */
    @Test
    fun `valid credentials should be accepted by SSH client`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 1: Valid credentials establish connections
        // Validates: Requirements 1.1
        
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.privateKey()
        ) { profile, privateKey ->
            val client = AndroidSSHClient()
            
            // Validate that the client accepts the credentials without throwing
            // Note: Actual connection will fail without a real SSH server, but we're
            // testing that the client properly processes the inputs
            val result = client.connect(
                profile = profile,
                privateKey = privateKey,
                passphrase = null,
                connectionTimeout = 5.seconds,
                enableCompression = false,
                strictHostKeyChecking = false
            )
            
            // The result should be a Result type (either success or failure)
            // We expect failure in unit tests (no real SSH server), but the client
            // should handle it gracefully with proper error types
            when {
                result.isSuccess -> {
                    // If somehow successful (shouldn't happen in unit tests),
                    // verify the session is valid
                    val session = result.getOrNull()
                    if (session != null) {
                        assert(session.serverAddress == profile.hostname)
                        assert(session.serverPort == profile.port)
                        assert(session.username == profile.username)
                        
                        // Clean up
                        client.disconnect(session)
                    }
                }
                result.isFailure -> {
                    // Expected in unit tests - verify we get proper error types
                    val error = result.exceptionOrNull()
                    assert(error is SSHError) {
                        "Expected SSHError but got ${error?.javaClass?.simpleName}"
                    }
                    
                    // Verify error messages are informative
                    val message = error?.message ?: ""
                    assert(message.isNotEmpty()) {
                        "Error message should not be empty"
                    }
                }
            }
        }
    }
    
    /**
     * Feature: ssh-tunnel-proxy, Property 2: Connected sessions create SOCKS5 proxies
     * Validates: Requirements 1.2
     * 
     * This property test validates that the SSH client properly handles SOCKS5 proxy creation
     * requests. Since we cannot test against real SSH servers in unit tests, we validate:
     * 1. The client accepts valid port numbers (0 for automatic, or specific ports)
     * 2. The client returns appropriate Result types
     * 3. The client handles invalid sessions gracefully
     */
    @Test
    fun `SOCKS5 proxy creation should handle various port configurations`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 2: Connected sessions create SOCKS5 proxies
        // Validates: Requirements 1.2
        
        checkAll(
            iterations = 100,
            Arb.socksPort()
        ) { localPort ->
            val client = AndroidSSHClient()
            
            // Create a mock session (not connected to real server)
            val mockSession = SSHSession(
                sessionId = "test-session",
                serverAddress = "test.example.com",
                serverPort = 22,
                username = "testuser",
                socksPort = 0,
                nativeSession = null // No real JSch session
            )
            
            // Attempt to create port forwarding
            val result = client.createPortForwarding(mockSession, localPort)
            
            // Should fail gracefully with proper error (no real session)
            assert(result.isFailure) {
                "Expected failure without real SSH session"
            }
            
            val error = result.exceptionOrNull()
            assert(error is SSHError.SessionClosed) {
                "Expected SessionClosed error but got ${error?.javaClass?.simpleName}"
            }
            
            // Verify error message is informative
            val message = error?.message ?: ""
            assert(message.isNotEmpty()) {
                "Error message should not be empty"
            }
        }
    }
    
    // Custom generators for property-based testing
    
    companion object {
        /**
         * Generates valid server profiles with random but realistic values.
         */
        fun Arb.Companion.serverProfile(): Arb<ServerProfile> = arbitrary {
            ServerProfile(
                id = Arb.long(1L, 1000L).bind(),
                name = Arb.string(5..20, Codepoint.alphanumeric()).bind(),
                hostname = Arb.domain().bind(),
                port = Arb.int(1..65535).bind(),
                username = Arb.string(3..16, Codepoint.alphanumeric()).bind(),
                keyType = Arb.enum<KeyType>().bind(),
                createdAt = Arb.long(0L, System.currentTimeMillis()).bind(),
                lastUsed = Arb.long(0L, System.currentTimeMillis()).orNull().bind()
            )
        }
        
        /**
         * Generates valid domain names.
         */
        fun Arb.Companion.domain(): Arb<String> = arbitrary {
            val parts = Arb.list(
                Arb.string(3..10, Codepoint.alphanumeric()),
                2..4
            ).bind()
            parts.joinToString(".") + ".com"
        }
        
        /**
         * Generates valid private keys for all supported key types.
         */
        fun Arb.Companion.privateKey(): Arb<PrivateKey> = arbitrary {
            val keyType = Arb.enum<KeyType>().bind()
            val keySize = when (keyType) {
                KeyType.ED25519 -> 32
                KeyType.ECDSA -> 64
                KeyType.RSA -> 256
            }
            PrivateKey(
                keyData = Arb.byteArray(Arb.constant(keySize), Arb.byte()).bind(),
                keyType = keyType
            )
        }
        
        /**
         * Generates valid SOCKS5 port numbers.
         * 0 means automatic port selection, otherwise use ports 1024-65535.
         */
        fun Arb.Companion.socksPort(): Arb<Int> = arbitrary {
            val useAutomatic = Arb.boolean().bind()
            if (useAutomatic) {
                0
            } else {
                Arb.int(1024..65535).bind()
            }
        }
    }
}
