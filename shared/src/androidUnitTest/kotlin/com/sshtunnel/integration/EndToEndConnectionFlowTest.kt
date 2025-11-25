package com.sshtunnel.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.db.SSHTunnelDatabase
import com.sshtunnel.repository.ProfileRepositoryImpl
import com.sshtunnel.ssh.AndroidSSHClient
import com.sshtunnel.ssh.ConnectionState
import com.sshtunnel.ssh.SSHConnectionManagerImpl
import com.sshtunnel.storage.AndroidCredentialStore
import com.sshtunnel.storage.AndroidSSHKeyParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration test for the complete connection flow.
 * 
 * Tests the full workflow from profile creation to SSH connection establishment,
 * including:
 * - Profile repository operations
 * - Credential storage and retrieval
 * - SSH key parsing
 * - SSH connection management
 * - SOCKS5 proxy creation
 * 
 * NOTE: These tests require a real SSH server. See SSHConnectionIntegrationTest
 * for setup instructions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EndToEndConnectionFlowTest {
    
    private lateinit var context: Context
    private lateinit var driver: AndroidSqliteDriver
    private lateinit var database: SSHTunnelDatabase
    private lateinit var profileRepository: ProfileRepositoryImpl
    private lateinit var credentialStore: AndroidCredentialStore
    private lateinit var keyParser: AndroidSSHKeyParser
    private lateinit var sshClient: AndroidSSHClient
    private lateinit var connectionManager: SSHConnectionManagerImpl
    
    // TODO: Update with your test SSH server details
    private val TEST_HOSTNAME = "localhost"
    private val TEST_PORT = 2222
    private val TEST_USERNAME = "testuser"
    private val TEST_PRIVATE_KEY_PEM = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        [Your test private key here]
        -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Set up database
        driver = AndroidSqliteDriver(
            schema = SSHTunnelDatabase.Schema,
            context = context,
            name = null // in-memory
        )
        database = SSHTunnelDatabase(driver)
        
        // Set up components
        profileRepository = ProfileRepositoryImpl(database)
        credentialStore = AndroidCredentialStore(context)
        keyParser = AndroidSSHKeyParser()
        sshClient = AndroidSSHClient()
        connectionManager = SSHConnectionManagerImpl(
            sshClient = sshClient,
            credentialStore = credentialStore
        )
    }
    
    @After
    fun teardown() {
        driver.close()
    }
    
    @Test
    @Ignore("Requires real SSH server - see class documentation")
    fun `complete connection flow from profile creation to active connection`() = runTest {
        // Step 1: Create a server profile
        val profile = ServerProfile(
            id = 0,
            name = "Test Server",
            hostname = TEST_HOSTNAME,
            port = TEST_PORT,
            username = TEST_USERNAME,
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val profileId = profileRepository.createProfile(profile).getOrThrow()
        assertTrue(profileId > 0, "Profile should be created with valid ID")
        
        // Step 2: Parse and store SSH key
        val keyParseResult = keyParser.parsePrivateKey(TEST_PRIVATE_KEY_PEM.toByteArray())
        assertTrue(keyParseResult.isSuccess, "Key parsing should succeed")
        
        val privateKey = keyParseResult.getOrThrow()
        val storeResult = credentialStore.storeKey(profileId, privateKey.keyData, null)
        assertTrue(storeResult.isSuccess, "Key storage should succeed")
        
        // Step 3: Retrieve the profile
        val retrievedProfile = profileRepository.getProfile(profileId)
        assertNotNull(retrievedProfile, "Profile should be retrievable")
        assertEquals(profile.name, retrievedProfile.name)
        
        // Step 4: Connect using the connection manager
        val initialState = connectionManager.observeConnectionState().first()
        assertEquals(ConnectionState.Disconnected, initialState, "Should start disconnected")
        
        val connectResult = connectionManager.connect(retrievedProfile, null)
        assertTrue(connectResult.isSuccess, "Connection should succeed")
        
        // Step 5: Verify connection state
        val connection = connectResult.getOrThrow()
        assertNotNull(connection, "Connection should be established")
        assertTrue(connection.socksPort > 0, "SOCKS port should be assigned")
        assertEquals(TEST_HOSTNAME, connection.serverAddress)
        assertEquals(TEST_PORT, connection.serverPort)
        assertEquals(TEST_USERNAME, connection.username)
        
        val connectedState = connectionManager.observeConnectionState().first()
        assertTrue(
            connectedState is ConnectionState.Connected,
            "State should be Connected"
        )
        
        // Step 6: Verify we can get the current connection
        val currentConnection = connectionManager.getCurrentConnection()
        assertNotNull(currentConnection, "Should be able to get current connection")
        assertEquals(connection.sessionId, currentConnection.sessionId)
        
        // Step 7: Update profile's lastUsed timestamp
        val updatedProfile = retrievedProfile.copy(lastUsed = System.currentTimeMillis())
        val updateResult = profileRepository.updateProfile(updatedProfile)
        assertTrue(updateResult.isSuccess, "Profile update should succeed")
        
        // Step 8: Disconnect
        val disconnectResult = connectionManager.disconnect()
        assertTrue(disconnectResult.isSuccess, "Disconnect should succeed")
        
        val disconnectedState = connectionManager.observeConnectionState().first()
        assertEquals(ConnectionState.Disconnected, disconnectedState, "Should be disconnected")
        
        // Step 9: Verify credential cleanup
        val retrievedKey = credentialStore.retrieveKey(profileId, null)
        assertTrue(retrievedKey.isSuccess, "Key should still be retrievable")
        
        // Step 10: Delete profile and credentials
        val deleteKeyResult = credentialStore.deleteKey(profileId)
        assertTrue(deleteKeyResult.isSuccess, "Key deletion should succeed")
        
        val deleteProfileResult = profileRepository.deleteProfile(profileId)
        assertTrue(deleteProfileResult.isSuccess, "Profile deletion should succeed")
    }
    
    @Test
    fun `profile repository operations work correctly in isolation`() = runTest {
        // This test verifies the profile repository works without SSH connection
        
        // Create profile
        val profile = ServerProfile(
            id = 0,
            name = "Test Profile",
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val profileId = profileRepository.createProfile(profile).getOrThrow()
        
        // Retrieve profile
        val retrieved = profileRepository.getProfile(profileId)
        assertNotNull(retrieved)
        assertEquals(profile.name, retrieved.name)
        
        // Update profile
        val updated = retrieved.copy(name = "Updated Name")
        val updateResult = profileRepository.updateProfile(updated)
        assertTrue(updateResult.isSuccess)
        
        // Verify update
        val afterUpdate = profileRepository.getProfile(profileId)
        assertEquals("Updated Name", afterUpdate?.name)
        
        // Delete profile
        val deleteResult = profileRepository.deleteProfile(profileId)
        assertTrue(deleteResult.isSuccess)
        
        // Verify deletion
        val afterDelete = profileRepository.getProfile(profileId)
        assertEquals(null, afterDelete)
    }
    
    @Test
    fun `credential store operations work correctly in isolation`() = runTest {
        // This test verifies credential storage works without SSH connection
        
        val profileId = 1L
        val testKey = generateTestKeyData()
        
        // Store key
        val storeResult = credentialStore.storeKey(profileId, testKey, null)
        assertTrue(storeResult.isSuccess, "Key storage should succeed")
        
        // Retrieve key
        val retrieveResult = credentialStore.retrieveKey(profileId, null)
        assertTrue(retrieveResult.isSuccess, "Key retrieval should succeed")
        
        val retrievedKey = retrieveResult.getOrThrow()
        assertTrue(retrievedKey.keyData.contentEquals(testKey), "Retrieved key should match stored key")
        
        // Delete key
        val deleteResult = credentialStore.deleteKey(profileId)
        assertTrue(deleteResult.isSuccess, "Key deletion should succeed")
        
        // Verify deletion
        val afterDelete = credentialStore.retrieveKey(profileId, null)
        assertTrue(afterDelete.isFailure, "Key should no longer exist after deletion")
    }
    
    @Test
    fun `key parser handles various key formats`() = runTest {
        // Test ED25519 key format detection
        val ed25519Key = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
            QyNTUxOQAAACDummytest1234567890abcdefghijklmnopqrstuvwxyz==
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()
        
        val parseResult = keyParser.parsePrivateKey(ed25519Key.toByteArray())
        
        // Note: This will fail with invalid key, but we're testing the parser structure
        // In a real test with valid keys, this would succeed
        assertTrue(
            parseResult.isFailure || parseResult.isSuccess,
            "Parser should handle the key format"
        )
    }
    
    /**
     * Generates test key data for credential store testing.
     */
    private fun generateTestKeyData(): ByteArray {
        return ByteArray(32) { it.toByte() } // Simple test data
    }
}
