package com.sshtunnel.repository

import com.sshtunnel.data.AppRoutingConfig
import com.sshtunnel.data.RoutingMode
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
        
        // Default profile ID for global routing config (backward compatibility)
        private const val DEFAULT_PROFILE_ID = -1L
    }
    
    override suspend fun getRoutingConfig(profileId: Long): AppRoutingConfig? {
        return withContext(Dispatchers.Default) {
            try {
                val excludedApps = database.databaseQueries
                    .selectAllExcludedApps(profileId)
                    .executeAsList()
                    .toSet()
                
                if (excludedApps.isEmpty()) {
                    return@withContext null
                }
                
                val routingModeStr = database.databaseQueries
                    .selectRoutingMode(profileId)
                    .executeAsOneOrNull()
                    ?: "ROUTE_ALL_EXCEPT_EXCLUDED"
                
                val routingMode = try {
                    RoutingMode.valueOf(routingModeStr)
                } catch (e: IllegalArgumentException) {
                    RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
                }
                
                AppRoutingConfig(
                    profileId = profileId,
                    excludedPackages = excludedApps,
                    routingMode = routingMode
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override suspend fun saveRoutingConfig(config: AppRoutingConfig): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                // Validate all package names first
                val invalidPackages = config.excludedPackages.filter { !isValidPackageName(it) }
                if (invalidPackages.isNotEmpty()) {
                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Invalid package names: ${invalidPackages.joinToString(", ")}"
                        )
                    )
                }
                
                // Use transaction to ensure atomicity
                database.databaseQueries.transaction {
                    // Clear existing excluded apps for this profile
                    database.databaseQueries.deleteAllExcludedAppsForProfile(config.profileId)
                    
                    // Insert new excluded apps with routing mode
                    val routingModeStr = config.routingMode.name
                    config.excludedPackages.forEach { packageName ->
                        database.databaseQueries.insertExcludedApp(
                            config.profileId,
                            packageName,
                            routingModeStr
                        )
                    }
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getExcludedApps(profileId: Long): Set<String> {
        return withContext(Dispatchers.Default) {
            try {
                database.databaseQueries
                    .selectAllExcludedApps(profileId)
                    .executeAsList()
                    .toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }
    }
    
    override suspend fun getExcludedApps(): Set<String> {
        return getExcludedApps(DEFAULT_PROFILE_ID)
    }
    
    override suspend fun setExcludedApps(profileId: Long, packageNames: Set<String>): Result<Unit> {
        val config = AppRoutingConfig(
            profileId = profileId,
            excludedPackages = packageNames,
            routingMode = RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
        )
        return saveRoutingConfig(config)
    }
    
    override suspend fun setExcludedApps(packageNames: Set<String>): Result<Unit> {
        return setExcludedApps(DEFAULT_PROFILE_ID, packageNames)
    }
    
    override suspend fun isAppExcluded(profileId: Long, packageName: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                database.databaseQueries
                    .isAppExcluded(profileId, packageName)
                    .executeAsOne()
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override suspend fun isAppExcluded(packageName: String): Boolean {
        return isAppExcluded(DEFAULT_PROFILE_ID, packageName)
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
