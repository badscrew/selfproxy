package com.sshtunnel.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshtunnel.db.SSHTunnelDatabase
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Property-based tests for AppRoutingRepository.
 * 
 * Tests Property 5 from the design document: App routing configuration persists correctly.
 */
class AppRoutingRepositoryPropertiesTest {
    
    private lateinit var database: SSHTunnelDatabase
    private lateinit var repository: AppRoutingRepository
    
    @BeforeTest
    fun setup() {
        // Create in-memory SQLite database for testing
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SSHTunnelDatabase.Schema.create(driver)
        database = SSHTunnelDatabase(driver)
        repository = AppRoutingRepositoryImpl(database)
    }
    
    @AfterTest
    fun teardown() {
        // Clean up database
        database.databaseQueries.transaction {
            database.databaseQueries.deleteAllExcludedApps()
        }
    }
    
    @Test
    fun `setExcludedApps then getExcludedApps should return same set`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 5: App routing configuration persists correctly
        // Validates: Requirements 5.3, 5.4, 5.5
        checkAll(
            iterations = 100,
            Arb.set(Arb.validPackageName(), 0..20)
        ) { packageNames ->
            // Clear database before each iteration
            database.databaseQueries.deleteAllExcludedApps()
            
            // Set excluded apps
            val setResult = repository.setExcludedApps(packageNames)
            setResult.isSuccess shouldBe true
            
            // Get excluded apps
            val retrievedPackages = repository.getExcludedApps()
            
            // Verify the sets are identical
            retrievedPackages shouldContainExactlyInAnyOrder packageNames
        }
    }
    
    @Test
    fun `isAppExcluded should return true for excluded apps and false for others`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 5: App routing configuration persists correctly
        // Validates: Requirements 5.3, 5.4, 5.5
        checkAll(
            iterations = 100,
            Arb.set(Arb.validPackageName(), 1..10),
            Arb.validPackageName()
        ) { excludedApps, testPackage ->
            // Clear database before each iteration
            database.databaseQueries.deleteAllExcludedApps()
            
            // Set excluded apps
            val setResult = repository.setExcludedApps(excludedApps)
            setResult.isSuccess shouldBe true
            
            // Check if test package is excluded
            val isExcluded = repository.isAppExcluded(testPackage)
            
            // Verify result matches whether package is in the excluded set
            isExcluded shouldBe excludedApps.contains(testPackage)
        }
    }
    
    @Test
    fun `setExcludedApps should replace previous configuration`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 5: App routing configuration persists correctly
        // Validates: Requirements 5.3, 5.4, 5.5
        checkAll(
            iterations = 100,
            Arb.set(Arb.validPackageName(), 1..10),
            Arb.set(Arb.validPackageName(), 1..10)
        ) { firstSet, secondSet ->
            // Clear database before each iteration
            database.databaseQueries.deleteAllExcludedApps()
            
            // Set first configuration
            val firstResult = repository.setExcludedApps(firstSet)
            firstResult.isSuccess shouldBe true
            
            // Set second configuration (should replace first)
            val secondResult = repository.setExcludedApps(secondSet)
            secondResult.isSuccess shouldBe true
            
            // Get current configuration
            val currentConfig = repository.getExcludedApps()
            
            // Verify only second configuration is present
            currentConfig shouldContainExactlyInAnyOrder secondSet
            
            // Verify no packages from first set remain (unless they're also in second set)
            val onlyInFirst = firstSet - secondSet
            onlyInFirst.forEach { packageName ->
                repository.isAppExcluded(packageName) shouldBe false
            }
        }
    }
    
    @Test
    fun `setExcludedApps should reject invalid package names`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 5: App routing configuration persists correctly
        // Validates: Requirements 5.3, 5.4, 5.5
        checkAll(
            iterations = 100,
            Arb.set(Arb.invalidPackageName(), 1..5)
        ) { invalidPackages ->
            // Clear database before each iteration
            database.databaseQueries.deleteAllExcludedApps()
            
            // Try to set invalid package names
            val result = repository.setExcludedApps(invalidPackages)
            
            // Verify operation failed
            result.isFailure shouldBe true
            
            // Verify no packages were added to database
            val storedPackages = repository.getExcludedApps()
            storedPackages.isEmpty() shouldBe true
        }
    }
    
    @Test
    fun `empty set should clear all excluded apps`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 5: App routing configuration persists correctly
        // Validates: Requirements 5.3, 5.4, 5.5
        checkAll(
            iterations = 100,
            Arb.set(Arb.validPackageName(), 1..10)
        ) { initialPackages ->
            // Clear database before each iteration
            database.databaseQueries.deleteAllExcludedApps()
            
            // Set initial configuration
            val setResult = repository.setExcludedApps(initialPackages)
            setResult.isSuccess shouldBe true
            
            // Verify packages were added
            val beforeClear = repository.getExcludedApps()
            beforeClear shouldContainExactlyInAnyOrder initialPackages
            
            // Clear by setting empty set
            val clearResult = repository.setExcludedApps(emptySet())
            clearResult.isSuccess shouldBe true
            
            // Verify all packages were removed
            val afterClear = repository.getExcludedApps()
            afterClear.isEmpty() shouldBe true
            
            // Verify isAppExcluded returns false for all previously excluded apps
            initialPackages.forEach { packageName ->
                repository.isAppExcluded(packageName) shouldBe false
            }
        }
    }
}

/**
 * Generates valid Android package names.
 * 
 * Valid package names:
 * - Start with a lowercase letter
 * - Contain only lowercase letters, numbers, underscores, and dots
 * - Have at least one dot (e.g., com.example)
 * - Don't start or end with a dot
 */
fun Arb.Companion.validPackageName(): Arb<String> = arbitrary {
    val numParts = Arb.int(2..4).bind()
    val parts = List(numParts) {
        val length = Arb.int(3..10).bind()
        buildString {
            // First character must be a letter
            append(('a'..'z').random())
            // Rest can be letters, numbers, or underscores
            repeat(length - 1) {
                val chars = ('a'..'z') + ('0'..'9') + '_'
                append(chars.random())
            }
        }
    }
    parts.joinToString(".")
}

/**
 * Generates invalid Android package names for testing validation.
 * 
 * Invalid patterns:
 * - Starting with uppercase
 * - Starting with a number
 * - Containing spaces or special characters
 * - No dots (single segment)
 * - Starting or ending with a dot
 * - Empty strings
 */
fun Arb.Companion.invalidPackageName(): Arb<String> = arbitrary {
    when (Arb.int(0..5).bind()) {
        0 -> "COM.EXAMPLE.APP" // All uppercase
        1 -> "123.invalid.package" // Starts with number
        2 -> "com.example.with space" // Contains space
        3 -> "singlepart" // No dots
        4 -> ".com.example.app" // Starts with dot
        5 -> "com.example.app." // Ends with dot
        else -> "com.example.app" // Fallback (shouldn't happen)
    }
}
