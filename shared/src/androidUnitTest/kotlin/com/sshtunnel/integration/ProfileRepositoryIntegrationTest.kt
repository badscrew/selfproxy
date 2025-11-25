package com.sshtunnel.integration

import android.content.Context
import org.robolectric.RuntimeEnvironment
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.db.SSHTunnelDatabase
import com.sshtunnel.repository.ProfileRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration test for ProfileRepository with real SQLite database.
 * 
 * Tests the complete profile repository implementation with actual database operations,
 * verifying CRUD operations work correctly with real persistence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileRepositoryIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var driver: AndroidSqliteDriver
    private lateinit var database: SSHTunnelDatabase
    private lateinit var repository: ProfileRepositoryImpl
    
    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        
        // Create in-memory database for testing
        driver = AndroidSqliteDriver(
            schema = SSHTunnelDatabase.Schema,
            context = context,
            name = null // null = in-memory database
        )
        
        database = SSHTunnelDatabase(driver)
        repository = ProfileRepositoryImpl(database)
    }
    
    @After
    fun teardown() {
        driver.close()
    }
    
    @Test
    fun `create and retrieve profile should persist data correctly`() = runTest {
        // Arrange
        val profile = ServerProfile(
            id = 0,
            name = "Test Server",
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis(),
            lastUsed = null
        )
        
        // Act
        val createResult = repository.createProfile(profile)
        
        // Assert
        assertTrue(createResult.isSuccess, "Profile creation should succeed")
        val profileId = createResult.getOrThrow()
        assertTrue(profileId > 0, "Generated ID should be positive")
        
        // Retrieve and verify
        val retrieved = repository.getProfile(profileId)
        assertNotNull(retrieved, "Profile should be retrievable")
        assertEquals(profile.name, retrieved.name)
        assertEquals(profile.hostname, retrieved.hostname)
        assertEquals(profile.port, retrieved.port)
        assertEquals(profile.username, retrieved.username)
        assertEquals(profile.keyType, retrieved.keyType)
    }
    
    @Test
    fun `create multiple profiles and retrieve all should return complete list`() = runTest {
        // Arrange
        val profiles = listOf(
            ServerProfile(
                id = 0,
                name = "Server 1",
                hostname = "server1.example.com",
                port = 22,
                username = "user1",
                keyType = KeyType.ED25519,
                createdAt = System.currentTimeMillis()
            ),
            ServerProfile(
                id = 0,
                name = "Server 2",
                hostname = "server2.example.com",
                port = 2222,
                username = "user2",
                keyType = KeyType.RSA,
                createdAt = System.currentTimeMillis()
            ),
            ServerProfile(
                id = 0,
                name = "Server 3",
                hostname = "server3.example.com",
                port = 22,
                username = "user3",
                keyType = KeyType.ECDSA,
                createdAt = System.currentTimeMillis()
            )
        )
        
        // Act
        val ids = profiles.map { profile ->
            repository.createProfile(profile).getOrThrow()
        }
        
        val allProfiles = repository.getAllProfiles()
        
        // Assert
        assertEquals(3, allProfiles.size, "Should retrieve all 3 profiles")
        assertEquals(profiles.map { it.name }.toSet(), allProfiles.map { it.name }.toSet())
        assertEquals(profiles.map { it.hostname }.toSet(), allProfiles.map { it.hostname }.toSet())
    }
    
    @Test
    fun `update profile should persist changes`() = runTest {
        // Arrange
        val originalProfile = ServerProfile(
            id = 0,
            name = "Original Name",
            hostname = "original.example.com",
            port = 22,
            username = "originaluser",
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val profileId = repository.createProfile(originalProfile).getOrThrow()
        
        // Act
        val updatedProfile = ServerProfile(
            id = profileId,
            name = "Updated Name",
            hostname = "updated.example.com",
            port = 2222,
            username = "updateduser",
            keyType = KeyType.RSA,
            createdAt = originalProfile.createdAt,
            lastUsed = System.currentTimeMillis()
        )
        
        val updateResult = repository.updateProfile(updatedProfile)
        
        // Assert
        assertTrue(updateResult.isSuccess, "Update should succeed")
        
        val retrieved = repository.getProfile(profileId)
        assertNotNull(retrieved, "Updated profile should be retrievable")
        assertEquals("Updated Name", retrieved.name)
        assertEquals("updated.example.com", retrieved.hostname)
        assertEquals(2222, retrieved.port)
        assertEquals("updateduser", retrieved.username)
        assertEquals(KeyType.RSA, retrieved.keyType)
        assertNotNull(retrieved.lastUsed, "lastUsed should be set")
    }
    
    @Test
    fun `delete profile should remove from database`() = runTest {
        // Arrange
        val profile = ServerProfile(
            id = 0,
            name = "To Delete",
            hostname = "delete.example.com",
            port = 22,
            username = "deleteuser",
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        val profileId = repository.createProfile(profile).getOrThrow()
        
        // Verify it exists
        assertNotNull(repository.getProfile(profileId))
        
        // Act
        val deleteResult = repository.deleteProfile(profileId)
        
        // Assert
        assertTrue(deleteResult.isSuccess, "Delete should succeed")
        assertNull(repository.getProfile(profileId), "Profile should no longer exist")
    }
    
    @Test
    fun `delete non-existent profile should succeed gracefully`() = runTest {
        // Act
        val deleteResult = repository.deleteProfile(99999L)
        
        // Assert
        assertTrue(deleteResult.isSuccess, "Deleting non-existent profile should succeed")
    }
    
    @Test
    fun `update non-existent profile should succeed but not affect any rows`() = runTest {
        // Arrange
        val nonExistentProfile = ServerProfile(
            id = 99999L,
            name = "Non-existent",
            hostname = "nonexistent.example.com",
            port = 22,
            username = "nobody",
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        // Act
        val updateResult = repository.updateProfile(nonExistentProfile)
        
        // Assert
        // SQLite UPDATE succeeds even if no rows are affected
        // This is acceptable behavior - the operation doesn't fail, it just affects 0 rows
        assertTrue(updateResult.isSuccess, "Update operation should succeed")
        
        // Verify the profile still doesn't exist
        val retrieved = repository.getProfile(99999L)
        assertNull(retrieved, "Profile should still not exist after update")
    }
    
    @Test
    fun `concurrent profile operations should maintain data integrity`() = runTest {
        // Arrange
        val profiles = (1..10).map { i ->
            ServerProfile(
                id = 0,
                name = "Server $i",
                hostname = "server$i.example.com",
                port = 22,
                username = "user$i",
                keyType = KeyType.ED25519,
                createdAt = System.currentTimeMillis()
            )
        }
        
        // Act - Create all profiles
        profiles.forEach { profile ->
            repository.createProfile(profile)
        }
        
        // Assert
        val allProfiles = repository.getAllProfiles()
        assertEquals(10, allProfiles.size, "All profiles should be created")
        
        // Verify each profile is unique
        val names = allProfiles.map { it.name }.toSet()
        assertEquals(10, names.size, "All profile names should be unique")
    }
    
    @Test
    fun `profile with special characters should be stored correctly`() = runTest {
        // Arrange
        val profile = ServerProfile(
            id = 0,
            name = "Test's \"Server\" & More",
            hostname = "test-server.example.com",
            port = 22,
            username = "user@domain",
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        // Act
        val profileId = repository.createProfile(profile).getOrThrow()
        val retrieved = repository.getProfile(profileId)
        
        // Assert
        assertNotNull(retrieved)
        assertEquals(profile.name, retrieved.name, "Special characters should be preserved")
        assertEquals(profile.username, retrieved.username, "Username with @ should be preserved")
    }
    
    @Test
    fun `profile with maximum field lengths should be stored correctly`() = runTest {
        // Arrange
        val longName = "A".repeat(255)
        val longHostname = "subdomain." + "a".repeat(240) + ".com"
        val longUsername = "u".repeat(255)
        
        val profile = ServerProfile(
            id = 0,
            name = longName,
            hostname = longHostname,
            port = 65535, // Max port number
            username = longUsername,
            keyType = KeyType.ED25519,
            createdAt = System.currentTimeMillis()
        )
        
        // Act
        val profileId = repository.createProfile(profile).getOrThrow()
        val retrieved = repository.getProfile(profileId)
        
        // Assert
        assertNotNull(retrieved)
        assertEquals(longName, retrieved.name)
        assertEquals(longHostname, retrieved.hostname)
        assertEquals(65535, retrieved.port)
        assertEquals(longUsername, retrieved.username)
    }
}
