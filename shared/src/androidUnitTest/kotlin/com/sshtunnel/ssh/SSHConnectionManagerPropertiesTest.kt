package com.sshtunnel.ssh

import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.storage.CredentialStore
import com.sshtunnel.storage.PrivateKey
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based tests for SSH Connection Manager functionality.
 */
class SSHConnectionManagerPropertiesTest {
    
    /**
     * Feature: ssh-tunnel-proxy, Property 4: Disconnection cleans up resources
     * Validates: Requirements 1.4
     * 
     * This property test validates that disconnecting an SSH connection properly cleans up
     * all resources, including:
     * 1. SSH session is terminated
     * 2. SOCKS5 proxy is stopped
     * 3. Connection state is updated to Disconnected
     * 4. No active connections remain
     * 
     * For any active SSH connection, disconnecting should terminate the SSH session
     * and stop the SOCKS5 proxy, leaving no active connections.
     */
    @Test
    fun `disconnection should clean up all resources`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 4: Disconnection cleans up resources
        // Validates: Requirements 1.4
        
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            // Create mock implementations
            val mockSSHClient = MockSSHClient()
            val mockCredentialStore = MockCredentialStore()
            
            // Create connection manager
            val connectionManager = SSHConnectionManagerImpl(
                sshClient = mockSSHClient,
                credentialStore = mockCredentialStore,
                logger = MockLogger(),
                connectionTimeout = 5.seconds
            )
            
            // Store a credential for the profile
            val privateKey = PrivateKey(
                keyData = ByteArray(32) { it.toByte() },
                keyType = KeyType.ED25519
            )
            mockCredentialStore.storeKey(profile.id, privateKey.keyData, null)
            
            // Attempt to connect (will succeed with mock)
            val connectResult = connectionManager.connect(profile, null)
            
            // Verify connection was established
            if (connectResult.isSuccess) {
                val connection = connectResult.getOrThrow()
                
                // Verify connection state is Connected
                val stateBeforeDisconnect = connectionManager.observeConnectionState().value
                assert(stateBeforeDisconnect is ConnectionState.Connected) {
                    "Expected Connected state but got ${stateBeforeDisconnect::class.simpleName}"
                }
                
                // Verify current connection exists
                val currentConnection = connectionManager.getCurrentConnection()
                assert(currentConnection != null) {
                    "Current connection should not be null"
                }
                assert(currentConnection?.sessionId == connection.sessionId) {
                    "Current connection should match the established connection"
                }
                
                // Disconnect
                val disconnectResult = connectionManager.disconnect()
                
                // Verify disconnect was successful
                assert(disconnectResult.isSuccess) {
                    "Disconnect should succeed"
                }
                
                // Verify connection state is Disconnected
                val stateAfterDisconnect = connectionManager.observeConnectionState().value
                stateAfterDisconnect shouldBe ConnectionState.Disconnected
                
                // Verify no current connection exists
                val connectionAfterDisconnect = connectionManager.getCurrentConnection()
                assert(connectionAfterDisconnect == null) {
                    "Current connection should be null after disconnect"
                }
                
                // Verify SSH session was disconnected
                assert(mockSSHClient.disconnectCalled) {
                    "SSH client disconnect should have been called"
                }
                
                // Verify the session is no longer connected
                val session = mockSSHClient.lastSession
                if (session != null) {
                    assert(!mockSSHClient.isConnected(session)) {
                        "Session should not be connected after disconnect"
                    }
                }
            }
        }
    }
    
    /**
     * Feature: ssh-tunnel-proxy, Property 5: Connection failures produce specific error messages
     * Validates: Requirements 1.5, 8.1, 8.2, 8.4
     * 
     * This property test validates that connection failures produce specific, actionable
     * error messages that help users troubleshoot issues.
     * 
     * For any connection failure (invalid credentials, unreachable server, timeout),
     * the error message should indicate the specific failure reason.
     */
    @Test
    fun `connection failures should produce specific error messages`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 5: Connection failures produce specific error messages
        // Validates: Requirements 1.5, 8.1, 8.2, 8.4
        
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.sshError()
        ) { profile, errorType ->
            // Create mock implementations that will fail with specific errors
            val mockSSHClient = MockSSHClient(failWith = errorType)
            val mockCredentialStore = MockCredentialStore()
            
            // Create connection manager
            val connectionManager = SSHConnectionManagerImpl(
                sshClient = mockSSHClient,
                credentialStore = mockCredentialStore,
                logger = MockLogger(),
                connectionTimeout = 5.seconds
            )
            
            // Store a credential for the profile
            val privateKey = PrivateKey(
                keyData = ByteArray(32) { it.toByte() },
                keyType = KeyType.ED25519
            )
            mockCredentialStore.storeKey(profile.id, privateKey.keyData, null)
            
            // Attempt to connect (should fail)
            val connectResult = connectionManager.connect(profile, null)
            
            // Verify connection failed
            assert(connectResult.isFailure) {
                "Connection should fail with mock error"
            }
            
            // Verify connection state is Error
            val state = connectionManager.observeConnectionState().value
            assert(state is ConnectionState.Error) {
                "Expected Error state but got ${state::class.simpleName}"
            }
            
            // Verify error message is specific and informative
            val error = (state as ConnectionState.Error).error
            val message = error.message
            
            // Error message should not be empty
            assert(message.isNotEmpty()) {
                "Error message should not be empty"
            }
            
            // Verify error type matches the failure type
            when (errorType) {
                is SSHError.AuthenticationFailed -> {
                    assert(error is ConnectionError.AuthenticationFailed) {
                        "Expected AuthenticationFailed error"
                    }
                    assert(message.contains("authentication", ignoreCase = true) ||
                           message.contains("credentials", ignoreCase = true) ||
                           message.contains("key", ignoreCase = true)) {
                        "Authentication error message should mention authentication/credentials/key"
                    }
                }
                is SSHError.ConnectionTimeout -> {
                    assert(error is ConnectionError.ConnectionTimeout) {
                        "Expected ConnectionTimeout error"
                    }
                    assert(message.contains("timeout", ignoreCase = true) ||
                           message.contains("timed out", ignoreCase = true)) {
                        "Timeout error message should mention timeout"
                    }
                }
                is SSHError.HostUnreachable -> {
                    assert(error is ConnectionError.HostUnreachable) {
                        "Expected HostUnreachable error"
                    }
                    assert(message.contains("reach", ignoreCase = true) ||
                           message.contains("connect", ignoreCase = true) ||
                           message.contains("network", ignoreCase = true)) {
                        "Unreachable error message should mention connectivity"
                    }
                }
                is SSHError.PortForwardingDisabled -> {
                    assert(error is ConnectionError.PortForwardingDisabled) {
                        "Expected PortForwardingDisabled error"
                    }
                    assert(message.contains("forwarding", ignoreCase = true) ||
                           message.contains("disabled", ignoreCase = true)) {
                        "Port forwarding error message should mention forwarding"
                    }
                }
                is SSHError.InvalidKey -> {
                    assert(error is ConnectionError.InvalidKey) {
                        "Expected InvalidKey error"
                    }
                    assert(message.contains("key", ignoreCase = true) ||
                           message.contains("invalid", ignoreCase = true)) {
                        "Invalid key error message should mention key"
                    }
                }
                is SSHError.UnknownHost -> {
                    assert(error is ConnectionError.UnknownHost) {
                        "Expected UnknownHost error"
                    }
                    assert(message.contains("host", ignoreCase = true) ||
                           message.contains("resolve", ignoreCase = true) ||
                           message.contains("DNS", ignoreCase = true)) {
                        "Unknown host error message should mention host/DNS"
                    }
                }
                else -> {
                    // For other errors, just verify message is not empty
                    assert(message.isNotEmpty()) {
                        "Error message should not be empty"
                    }
                }
            }
        }
    }
    
    // Mock implementations for testing
    
    /**
     * Mock SSH client for testing.
     */
    private class MockSSHClient(
        private val failWith: SSHError? = null
    ) : SSHClient {
        var disconnectCalled = false
        var lastSession: SSHSession? = null
        private val connectedSessions = mutableSetOf<String>()
        
        override suspend fun connect(
            profile: ServerProfile,
            privateKey: PrivateKey,
            passphrase: String?,
            connectionTimeout: kotlin.time.Duration,
            enableCompression: Boolean,
            strictHostKeyChecking: Boolean
        ): Result<SSHSession> {
            return if (failWith != null) {
                Result.failure(failWith)
            } else {
                val session = SSHSession(
                    sessionId = "mock-session-${System.currentTimeMillis()}",
                    serverAddress = profile.hostname,
                    serverPort = profile.port,
                    username = profile.username,
                    socksPort = 0
                )
                lastSession = session
                connectedSessions.add(session.sessionId)
                Result.success(session)
            }
        }
        
        override suspend fun createPortForwarding(
            session: SSHSession,
            localPort: Int
        ): Result<Int> {
            return if (failWith != null) {
                Result.failure(failWith)
            } else {
                val port = if (localPort == 0) 1080 else localPort
                Result.success(port)
            }
        }
        
        override suspend fun sendKeepAlive(session: SSHSession): Result<Unit> {
            return Result.success(Unit)
        }
        
        override suspend fun disconnect(session: SSHSession): Result<Unit> {
            disconnectCalled = true
            connectedSessions.remove(session.sessionId)
            return Result.success(Unit)
        }
        
        override fun isConnected(session: SSHSession): Boolean {
            return connectedSessions.contains(session.sessionId)
        }
    }
    
    /**
     * Mock credential store for testing.
     */
    private class MockCredentialStore : CredentialStore {
        private val keys = mutableMapOf<Long, PrivateKey>()
        
        override suspend fun storeKey(
            profileId: Long,
            privateKey: ByteArray,
            passphrase: String?
        ): Result<Unit> {
            keys[profileId] = PrivateKey(privateKey, KeyType.ED25519)
            return Result.success(Unit)
        }
        
        override suspend fun retrieveKey(
            profileId: Long,
            passphrase: String?
        ): Result<PrivateKey> {
            val key = keys[profileId]
            return if (key != null) {
                Result.success(key)
            } else {
                Result.failure(Exception("Key not found"))
            }
        }
        
        override suspend fun deleteKey(profileId: Long): Result<Unit> {
            keys.remove(profileId)
            return Result.success(Unit)
        }
    }
    
    /**
     * Mock logger for testing.
     */
    private class MockLogger : com.sshtunnel.logging.Logger {
        override fun verbose(tag: String, message: String, throwable: Throwable?) {}
        override fun debug(tag: String, message: String, throwable: Throwable?) {}
        override fun info(tag: String, message: String, throwable: Throwable?) {}
        override fun warn(tag: String, message: String, throwable: Throwable?) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun getLogEntries(): List<com.sshtunnel.logging.LogEntry> = emptyList()
        override fun clearLogs() {}
        override fun setVerboseEnabled(enabled: Boolean) {}
        override fun isVerboseEnabled(): Boolean = false
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
         * Generates various SSH error types for testing error handling.
         */
        fun Arb.Companion.sshError(): Arb<SSHError> = arbitrary {
            val errorTypes = listOf(
                SSHError.AuthenticationFailed("Authentication failed. Please check your private key and credentials."),
                SSHError.ConnectionTimeout("Connection timed out. Please check your network connection and firewall settings."),
                SSHError.HostUnreachable("Cannot reach SSH server. Please check the hostname and port."),
                SSHError.PortForwardingDisabled("Port forwarding is disabled on the SSH server."),
                SSHError.InvalidKey("Invalid private key format or corrupted key file."),
                SSHError.UnknownHost("Cannot resolve hostname. Please check the server address.")
            )
            Arb.of(errorTypes).bind()
        }
    }
}
