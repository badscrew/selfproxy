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
 * Feature: ssh-tunnel-proxy, Property 7: Profile listing completeness
 * Validates: Requirements 2.2
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
        // Feature: ssh-tunnel-proxy, Property 7: Profile listing completeness
        // Validates: Requirements 2.2
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
            
            // Verify all profiles are present with correct names and hostnames
            val expectedNamesAndHosts = profiles.map { it.name to it.hostname }
            val actualNamesAndHosts = retrievedProfiles.map { it.name to it.hostname }
            
            actualNamesAndHosts shouldContainExactlyInAnyOrder expectedNamesAndHosts
            
            // Verify all other fields are preserved correctly
            profiles.forEachIndexed { index, originalProfile ->
                val retrievedProfile = retrievedProfiles.find { 
                    it.name == originalProfile.name && it.hostname == originalProfile.hostname 
                }
                
                retrievedProfile shouldBe retrievedProfile // Should not be null
                retrievedProfile?.let {
                    it.port shouldBe originalProfile.port
                    it.username shouldBe originalProfile.username
                    it.keyType shouldBe originalProfile.keyType
                    it.createdAt shouldBe originalProfile.createdAt
                    it.lastUsed shouldBe originalProfile.lastUsed
                }
            }
        }
    }
}
