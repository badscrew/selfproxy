package com.sshtunnel.repository

import com.sshtunnel.db.SSHTunnelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of AppRoutingRepository using SQLDelight.
 * 
 * Stores excluded app package names in the database for VPN routing configuration.
 * Package names are validated to ensure they follow Android package naming conventions.
 */
class AppRoutingRepositoryImpl(
    private val database: SSHTunnelDatabase
) : AppRoutingRepository {
    
    companion object {
        // Android package name pattern: lowercase letters, numbers, underscores, and dots
        // Must contain at least one dot and not start/end with a dot
        private val PACKAGE_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    }
    
    override suspend fun getExcludedApps(): Set<String> {
        return withContext(Dispatchers.Default) {
            try {
                database.databaseQueries
                    .selectAllExcludedApps()
                    .executeAsList()
                    .toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }
    }
    
    override suspend fun setExcludedApps(packageNames: Set<String>): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                // Validate all package names first
                val invalidPackages = packageNames.filter { !isValidPackageName(it) }
                if (invalidPackages.isNotEmpty()) {
                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Invalid package names: ${invalidPackages.joinToString(", ")}"
                        )
                    )
                }
                
                // Use transaction to ensure atomicity
                database.databaseQueries.transaction {
                    // Clear existing excluded apps
                    database.databaseQueries.deleteAllExcludedApps()
                    
                    // Insert new excluded apps
                    packageNames.forEach { packageName ->
                        database.databaseQueries.insertExcludedApp(packageName)
                    }
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun isAppExcluded(packageName: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                database.databaseQueries
                    .isAppExcluded(packageName)
                    .executeAsOne()
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Validates that a package name follows Android package naming conventions.
     * 
     * Valid package names:
     * - Start with a lowercase letter
     * - Contain only lowercase letters, numbers, underscores, and dots
     * - Have at least one dot (e.g., com.example)
     * - Don't start or end with a dot
     * 
     * @param packageName The package name to validate
     * @return true if valid, false otherwise
     */
    private fun isValidPackageName(packageName: String): Boolean {
        return packageName.isNotEmpty() && 
               PACKAGE_NAME_PATTERN.matches(packageName)
    }
}
