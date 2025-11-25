package com.sshtunnel.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.ssh.AndroidSSHClient
import com.sshtunnel.ssh.SSHError
import com.sshtunnel.storage.PrivateKey
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration test for SSH connections.
 * 
 * NOTE: These tests require a real SSH server to run. They are marked as @Ignore
 * by default. To run these tests:
 * 
 * 1. Set up a test SSH server (e.g., using Docker):
 *    ```
 *    docker run -d -p 2222:22 \
 *      -e PUBLIC_KEY="$(cat ~/.ssh/id_ed25519.pub)" \
 *      linuxserver/openssh-server
 *    ```
 * 
 * 2. Update the TEST_* constants below with your server details
 * 
 * 3. Remove the @Ignore annotations
 * 
 * 4. Run the tests:
 *    ```
 *    ./gradlew shared:testDebugUnitTest --tests "*SSHConnectionIntegrationTest"
 *    ```
 * 
 * For CI/CD, consider using Testcontainers or a dedicated test SSH server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SSHConnectionIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var sshClient: AndroidSSHClient
    
    // TODO: Update these constants with your test SSH server details
    private val TEST_HOSTNAME = "localhost"
    private val TEST_PORT = 2222
    private val TEST_USERNAME = "testuser"
    private val TEST_PRIVATE_KEY = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        [Your test private key here]
        -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sshClient = AndroidSSHClient()
    }
    
    @Test
    @Ignore("Requires real SSH server - see class documentation")
    fun `connect to real SSH server should establish connection`() = runTest {
        // Arrange
        val profile = ServerProfile(
            id = 1,
            name = "Test Server",
            hostname = TEST_HOSTNAME,
            port = TEST_PORT,
            username = TEST_USERNAME,
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val privateKey = PrivateKey(
            keyData = TEST_PRIVATE_KEY.toByteArray(),
            keyType = KeyType.ED25519
        )
        
        // Act
        val result = sshClient.connect(
            profile = profile,
            privateKey = privateKey,
            passphrase = null,
            connectionTimeout = 10.seconds,
            enableCompression = false,
            strictHostKeyChecking = false
        )
        
        // Assert
        assertTrue(result.isSuccess, "Connection should succeed with valid credentials")
        
        val session = result.getOrThrow()
        assertTrue(sshClient.isConnected(session), "Session should be connected")
        
        // Cleanup
        sshClient.disconnect(session)
    }
    
    @Test
    @Ignore("Requires real SSH server - see class documentation")
    fun `create SOCKS5 proxy should succeed after connection`() = runTest {
        // Arrange
        val profile = ServerProfile(
            id = 1,
            name = "Test Server",
            hostname = TEST_HOSTNAME,
            port = TEST_PORT,
            username = TEST_USERNAME,
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val privateKey = PrivateKey(
            keyData = TEST_PRIVATE_KEY.toByteArray(),
            keyType = KeyType.ED25519
        )
        
        // Act - Connect
        val connectResult = sshClient.connect(
            profile = profile,
            privateKey = privateKey,
            passphrase = null,
            connectionTimeout = 10.seconds,
            enableCompression = false,
            strictHostKeyChecking = false
        )
        
        assertTrue(connectResult.isSuccess, "Connection should succeed")
        val session = connectResult.getOrThrow()
        
        // Act - Create port forwarding
        val portResult = sshClient.createPortForwarding(session, 0) // 0 = auto-assign port
        
        // Assert
        assertTrue(portResult.isSuccess, "Port forwarding should succeed")
        val socksPort = portResult.getOrThrow()
        assertTrue(socksPort > 0, "SOCKS port should be assigned")
        
        // Cleanup
        sshClient.disconnect(session)
    }
    
    @Test
    @Ignore("Requires real SSH server - see class documentation")
    fun `keep-alive should maintain idle connection`() = runTest {
        // Arrange
        val profile = ServerProfile(
            id = 1,
            name = "Test Server",
            hostname = TEST_HOSTNAME,
            port = TEST_PORT,
            username = TEST_USERNAME,
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val privateKey = PrivateKey(
            keyData = TEST_PRIVATE_KEY.toByteArray(),
            keyType = KeyType.ED25519
        )
        
        // Act - Connect
        val connectResult = sshClient.connect(
            profile = profile,
            privateKey = privateKey,
            passphrase = null,
            connectionTimeout = 10.seconds,
            enableCompression = false,
            strictHostKeyChecking = false
        )
        
        assertTrue(connectResult.isSuccess, "Connection should succeed")
        val session = connectResult.getOrThrow()
        
        // Act - Send keep-alive
        val keepAliveResult = sshClient.sendKeepAlive(session)
        
        // Assert
        assertTrue(keepAliveResult.isSuccess, "Keep-alive should succeed")
        assertTrue(sshClient.isConnected(session), "Session should still be connected")
        
        // Cleanup
        sshClient.disconnect(session)
    }
    
    @Test
    @Ignore("Requires real SSH server - see class documentation")
    fun `disconnect should cleanly close connection`() = runTest {
        // Arrange
        val profile = ServerProfile(
            id = 1,
            name = "Test Server",
            hostname = TEST_HOSTNAME,
            port = TEST_PORT,
            username = TEST_USERNAME,
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val privateKey = PrivateKey(
            keyData = TEST_PRIVATE_KEY.toByteArray(),
            keyType = KeyType.ED25519
        )
        
        // Act - Connect
        val connectResult = sshClient.connect(
            profile = profile,
            privateKey = privateKey,
            passphrase = null,
            connectionTimeout = 10.seconds,
            enableCompression = false,
            strictHostKeyChecking = false
        )
        
        assertTrue(connectResult.isSuccess, "Connection should succeed")
        val session = connectResult.getOrThrow()
        
        // Act - Disconnect
        val disconnectResult = sshClient.disconnect(session)
        
        // Assert
        assertTrue(disconnectResult.isSuccess, "Disconnect should succeed")
        assertFalse(sshClient.isConnected(session), "Session should be disconnected")
    }
    
    @Test
    fun `connect with invalid hostname should fail with UnknownHost error`() = runTest {
        // Arrange
        val profile = ServerProfile(
            id = 1,
            name = "Invalid Server",
            hostname = "invalid.nonexistent.hostname.test",
            port = 22,
            username = "testuser",
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val privateKey = PrivateKey(
            keyData = generateTestKey(),
            keyType = KeyType.ED25519
        )
        
        // Act
        val result = sshClient.connect(
            profile = profile,
            privateKey = privateKey,
            passphrase = null,
            connectionTimeout = 5.seconds,
            enableCompression = false,
            strictHostKeyChecking = false
        )
        
        // Assert
        assertTrue(result.isFailure, "Connection should fail with invalid hostname")
        val error = result.exceptionOrNull()
        assertTrue(error is SSHError.UnknownHost, "Error should be UnknownHost")
    }
    
    @Test
    fun `connect with unreachable host should fail with timeout or unreachable error`() = runTest {
        // Arrange - Use a non-routable IP address
        val profile = ServerProfile(
            id = 1,
            name = "Unreachable Server",
            hostname = "192.0.2.1", // TEST-NET-1, non-routable
            port = 22,
            username = "testuser",
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val privateKey = PrivateKey(
            keyData = generateTestKey(),
            keyType = KeyType.ED25519
        )
        
        // Act
        val result = sshClient.connect(
            profile = profile,
            privateKey = privateKey,
            passphrase = null,
            connectionTimeout = 2.seconds, // Short timeout for test
            enableCompression = false,
            strictHostKeyChecking = false
        )
        
        // Assert
        assertTrue(result.isFailure, "Connection should fail with unreachable host")
        val error = result.exceptionOrNull()
        assertTrue(
            error is SSHError.ConnectionTimeout || error is SSHError.HostUnreachable,
            "Error should be ConnectionTimeout or HostUnreachable"
        )
    }
    
    /**
     * Generates a dummy test key for testing error cases.
     * This is not a valid SSH key and will fail authentication.
     */
    private fun generateTestKey(): ByteArray {
        return """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
            QyNTUxOQAAACDummytest1234567890abcdefghijklmnopqrstuvwxyz==
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent().toByteArray()
    }
}
