package com.sshtunnel.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshtunnel.data.serverProfile
import com.sshtunnel.db.SSHTunnelDatabase
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Property-based tests for ProfileRepository.
 * 
 * Tests Properties 7, 8, 9, and 10 from the design document.
 */
class ProfileRepositoryPropertiesTest {
    
    private lateinit var database: SSHTunnelDatabase
    private lateinit var repository: ProfileRepository
    
    @BeforeTest
    fun setup() {
        // Create in-memory SQLite database for testing
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SSHTunnelDatabase.Schema.create(driver)
        database = SSHTunnelDatabase(driver)
        repository = ProfileRepositoryImpl(database)
    }
    
    @AfterTest
    fun teardown() {
        // Clean up database driver
        database.databaseQueries.transaction {
            database.databaseQueries.selectAllProfiles().executeAsList().forEach {
                database.databaseQueries.deleteProfile(it.id)
            }
        }
    }
    
    @Test
    fun `getAllProfiles should return all saved profiles with correct names and addresses`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 1: Profile CRUD operations maintain data integrity
        // Validates: Requirements 1.1, 1.2, 1.3
        checkAll(
            iterations = 100,
            Arb.list(Arb.serverProfile(), 0..10)
        ) { profiles ->
            // Clear database before each iteration
            database.databaseQueries.transaction {
                database.databaseQueries.selectAllProfiles().executeAsList().forEach {
                    database.databaseQueries.deleteProfile(it.id)
                }
            }
            
            // Create all profiles
            val createdIds = mutableListOf<Long>()
            profiles.forEach { profile ->
                val result = repository.createProfile(profile)
                result.isSuccess shouldBe true
                createdIds.add(result.getOrThrow())
            }
            
            // Retrieve all profiles
            val retrievedProfiles = repository.getAllProfiles()
            
            // Verify count matches
            retrievedProfiles.size shouldBe profiles.size
            
            // Verify all profiles are present with correct names and server hosts
            val expectedNamesAndHosts = profiles.map { it.name to it.serverHost }
            val actualNamesAndHosts = retrievedProfiles.map { it.name to it.serverHost }
            
            actualNamesAndHosts shouldContainExactlyInAnyOrder expectedNamesAndHosts
            
            // Verify all other fields are preserved correctly
            profiles.forEachIndexed { _, originalProfile ->
                val retrievedProfile = retrievedProfiles.find { 
                    it.name == originalProfile.name && it.serverHost == originalProfile.serverHost 
                }
                
                retrievedProfile shouldBe retrievedProfile // Should not be null
                retrievedProfile?.let {
                    it.serverPort shouldBe originalProfile.serverPort
                    it.cipher shouldBe originalProfile.cipher
                    it.createdAt shouldBe originalProfile.createdAt
                    it.lastUsed shouldBe originalProfile.lastUsed
                }
            }
        }
    }
    
    @Test
    fun `getProfile should load correct profile details for any saved profile`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 1: Profile CRUD operations maintain data integrity
        // Validates: Requirements 1.1, 1.2, 1.3
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            // Clear database before each iteration
            database.databaseQueries.transaction {
                database.databaseQueries.selectAllProfiles().executeAsList().forEach {
                    database.databaseQueries.deleteProfile(it.id)
                }
            }
            
            // Create the profile
            val result = repository.createProfile(profile)
            result.isSuccess shouldBe true
            val profileId = result.getOrThrow()
            
            // Retrieve the profile by ID
            val retrievedProfile = repository.getProfile(profileId)
            
            // Verify profile was retrieved
            retrievedProfile shouldBe retrievedProfile // Should not be null
            
            // Verify all fields match exactly
            retrievedProfile?.let {
                it.id shouldBe profileId
                it.name shouldBe profile.name
                it.serverHost shouldBe profile.serverHost
                it.serverPort shouldBe profile.serverPort
                it.cipher shouldBe profile.cipher
                it.createdAt shouldBe profile.createdAt
                it.lastUsed shouldBe profile.lastUsed
            }
        }
    }
    
    @Test
    fun `deleteProfile should remove profile from storage`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 3: Profile deletion removes all associated data
        // Validates: Requirements 1.4, 2.3
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            // Clear database before each iteration
            database.databaseQueries.transaction {
                database.databaseQueries.selectAllProfiles().executeAsList().forEach {
                    database.databaseQueries.deleteProfile(it.id)
                }
            }
            
            // Create the profile
            val createResult = repository.createProfile(profile)
            createResult.isSuccess shouldBe true
            val profileId = createResult.getOrThrow()
            
            // Verify profile exists
            val beforeDelete = repository.getProfile(profileId)
            beforeDelete shouldBe beforeDelete // Should not be null
            
            // Delete the profile
            val deleteResult = repository.deleteProfile(profileId)
            deleteResult.isSuccess shouldBe true
            
            // Verify profile no longer exists
            val afterDelete = repository.getProfile(profileId)
            afterDelete shouldBe null
            
            // Verify it's not in the list of all profiles
            val allProfiles = repository.getAllProfiles()
            allProfiles.none { it.id == profileId } shouldBe true
        }
    }
    
    @Test
    fun `updateProfile should persist all changes to profile data`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 1: Profile CRUD operations maintain data integrity
        // Validates: Requirements 1.1, 1.2, 1.3
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.serverProfile()
        ) { originalProfile, updatedData ->
            // Clear database before each iteration
            database.databaseQueries.transaction {
                database.databaseQueries.selectAllProfiles().executeAsList().forEach {
                    database.databaseQueries.deleteProfile(it.id)
                }
            }
            
            // Create the original profile
            val createResult = repository.createProfile(originalProfile)
            createResult.isSuccess shouldBe true
            val profileId = createResult.getOrThrow()
            
            // Create updated profile with same ID but new data
            val updatedProfile = updatedData.copy(id = profileId)
            
            // Update the profile
            val updateResult = repository.updateProfile(updatedProfile)
            updateResult.isSuccess shouldBe true
            
            // Retrieve the updated profile
            val retrievedProfile = repository.getProfile(profileId)
            
            // Verify all updatable fields were updated
            retrievedProfile shouldBe retrievedProfile // Should not be null
            retrievedProfile?.let {
                it.id shouldBe profileId
                it.name shouldBe updatedProfile.name
                it.serverHost shouldBe updatedProfile.serverHost
                it.serverPort shouldBe updatedProfile.serverPort
                it.cipher shouldBe updatedProfile.cipher
                // createdAt should remain unchanged from original
                it.createdAt shouldBe originalProfile.createdAt
                it.lastUsed shouldBe updatedProfile.lastUsed
            }
        }
    }
}
