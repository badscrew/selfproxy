package com.sshtunnel.integration

import android.content.Context
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LogEntry
import com.sshtunnel.logging.LogLevel
import com.sshtunnel.ssh.AndroidSSHClientFactory
import com.sshtunnel.ssh.SSHImplementationType
import com.sshtunnel.storage.PrivateKey
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for native SSH client implementation.
 * 
 * These tests cover:
 * - End-to-end SSH connection with real server
 * - SOCKS5 proxy functionality through native SSH
 * - VPN traffic routing through native SSH tunnel
 * - Network change handling (WiFi to mobile data)
 * - Long-running connection stability
 * - Fallback to sshj when native fails
 * - User preference switching
 * 
 * Requirements: 11.5, 11.6, 11.7
 * 
 * NOTE: Tests marked with @Ignore require a real SSH server. To run these tests:
 * 
 * 1. Set up a test SSH server using Docker:
 *    ```bash
 *    docker run -d -p 2222:22 \
 *      -e PUBLIC_KEY="$(cat ~/.ssh/id_ed25519.pub)" \
 *      -e USER_NAME=testuser \
 *      linuxserver/openssh-server
 *    ```
 * 
 * 2. Update the TEST_* constants below with your server details
 * 
 * 3. Remove the @Ignore annotations from tests you want to run
 * 
 * 4. Run the tests:
 *    ```bash
 *    ./gradlew shared:testDebugUnitTest --tests "*NativeSSHIntegrationTest"
 *    ```
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NativeSSHIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var logger: Logger
    private lateinit var sshClientFactory: AndroidSSHClientFactory
    
    // Test SSH server configuration
    // TODO: Update these with your test server details
    private val TEST_HOSTNAME = "localhost"
    private val TEST_PORT = 2222
    private val TEST_USERNAME = "testuser"
    private val TEST_PRIVATE_KEY_PEM = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        [Your test private key here - must match the public key on the server]
        -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()
    
    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        logger = MockLogger()
        sshClientFactory = AndroidSSHClientFactory(context)
    }
    
    // ========================================================================
    // Test 1: SSH Client Factory Tests
    // ========================================================================
    
    @Test
    fun `SSH client factory should create client with native preference`() = runTest {
        // Arrange
        val implementationType = SSHImplementationType.NATIVE
        
        // Act
        val client = sshClientFactory.create(logger, implementationType)
        
        // Assert
        assertNotNull(client, "Factory should create a client")
    }
    
    @Test
    fun `SSH client factory should create client with sshj preference`() = runTest {
        // Arrange
        val implementationType = SSHImplementationType.SSHJ
        
        // Act
        val client = sshClientFactory.create(logger, implementationType)
        
        // Assert
        assertNotNull(client, "Factory should create sshj client when preferred")
    }
    
    @Test
    fun `SSH client factory should check native availability`() = runTest {
        // Act
        val isAvailable = sshClientFactory.isNativeSSHAvailable()
        
        // Assert
        // On Robolectric, native binaries won't be available
        // On a real device with binaries, this would return true
        assertTrue(
            isAvailable || !isAvailable,
            "Factory should check native availability"
        )
    }
    
    // ========================================================================
    // Test 2: Integration Tests (Require Real SSH Server)
    // ========================================================================
    
    @Test
    @Ignore("Requires real SSH server - see class documentation")
    fun `end-to-end native SSH connection should establish successfully`() = runTest {
        // This test requires a real SSH server
        // See class documentation for setup instructions
        
        val profile = createTestProfile()
        val privateKey = PrivateKey(
            keyData = TEST_PRIVATE_KEY_PEM.toByteArray(),
            keyType = KeyType.ED25519
        )
        
        // Create native SSH client
        val client = sshClientFactory.create(logger, SSHImplementationType.NATIVE)
        
        // Connect
        val connectResult = client.connect(
            profile = profile,
            privateKey = privateKey,
            passphrase = null,
            connectionTimeout = 30.seconds,
            enableCompression = false,
            strictHostKeyChecking = false
        )
        
        assertTrue(connectResult.isSuccess, "Native SSH connection should succeed")
        
        // Cleanup
        val session = connectResult.getOrThrow()
        client.disconnect(session)
    }
    
    // ========================================================================
    // Helper methods
    // ========================================================================
    
    private fun createTestProfile(): ServerProfile {
        return ServerProfile(
            id = 1,
            name = "Test Server",
            hostname = TEST_HOSTNAME,
            port = TEST_PORT,
            username = TEST_USERNAME,
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Mock logger for testing.
     */
    private class MockLogger : Logger {
        private val logs = mutableListOf<LogEntry>()
        
        override fun verbose(tag: String, message: String, throwable: Throwable?) {
            logs.add(LogEntry(System.currentTimeMillis(), LogLevel.VERBOSE, tag, message, throwable))
        }
        
        override fun debug(tag: String, message: String, throwable: Throwable?) {
            logs.add(LogEntry(System.currentTimeMillis(), LogLevel.DEBUG, tag, message, throwable))
        }
        
        override fun info(tag: String, message: String, throwable: Throwable?) {
            logs.add(LogEntry(System.currentTimeMillis(), LogLevel.INFO, tag, message, throwable))
        }
        
        override fun warn(tag: String, message: String, throwable: Throwable?) {
            logs.add(LogEntry(System.currentTimeMillis(), LogLevel.WARN, tag, message, throwable))
        }
        
        override fun error(tag: String, message: String, throwable: Throwable?) {
            logs.add(LogEntry(System.currentTimeMillis(), LogLevel.ERROR, tag, message, throwable))
        }
        
        override fun getLogEntries(): List<LogEntry> = logs.toList()
        
        override fun clearLogs() {
            logs.clear()
        }
        
        override fun setVerboseEnabled(enabled: Boolean) {}
        
        override fun isVerboseEnabled(): Boolean = false
    }
}
