package com.sshtunnel.repository

import com.sshtunnel.data.AppRoutingConfig
import com.sshtunnel.data.RoutingMode
import com.sshtunnel.db.SSHTunnelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Implementation of AppRoutingRepository using SQLDelight.
 * 
 * Stores app routing configurations in the database with JSON serialization
 * for the package name sets.
 */
class AppRoutingRepositoryImpl(
    private val database: SSHTunnelDatabase
) : AppRoutingRepository {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override suspend fun getRoutingConfig(profileId: Long): AppRoutingConfig? {
        return withContext(Dispatchers.Default) {
            try {
                val result = database.databaseQueries.selectRoutingConfig(profileId).executeAsOneOrNull()
                
                result?.let {
                    val excludedPackages = json.decodeFromString<Set<String>>(it.excludedPackages)
                    val routingMode = RoutingMode.valueOf(it.routingMode)
                    
                    AppRoutingConfig(
                        profileId = it.profileId,
                        excludedPackages = excludedPackages,
                        routingMode = routingMode
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override suspend fun saveRoutingConfig(config: AppRoutingConfig): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                val excludedPackagesJson = json.encodeToString(config.excludedPackages)
                
                database.databaseQueries.insertRoutingConfig(
                    profileId = config.profileId,
                    excludedPackages = excludedPackagesJson,
                    routingMode = config.routingMode.name
                )
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun deleteRoutingConfig(profileId: Long): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                database.databaseQueries.deleteRoutingConfig(profileId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
