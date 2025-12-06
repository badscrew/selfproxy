package com.sshtunnel.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.db.SSHTunnelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Implementation of ProfileRepository using SQLDelight for cross-platform database access.
 * 
 * This implementation provides thread-safe CRUD operations for server profiles with
 * proper error handling using Result types.
 * 
 * @property database The SQLDelight database instance
 */
class ProfileRepositoryImpl(
    private val database: SSHTunnelDatabase
) : ProfileRepository {
    
    private val queries = database.databaseQueries
    
    override suspend fun createProfile(profile: ServerProfile): Result<Long> = withContext(Dispatchers.Default) {
        try {
            queries.insertProfile(
                name = profile.name,
                hostname = profile.hostname,
                port = profile.port.toLong(),
                username = profile.username,
                keyType = profile.keyType.name,
                createdAt = profile.createdAt,
                lastUsed = profile.lastUsed
            )
            
            // Get the last inserted row ID
            val lastInsertId = queries.transactionWithResult {
                database.databaseQueries.selectAllProfiles()
                    .executeAsList()
                    .maxByOrNull { it.id }
                    ?.id ?: 0L
            }
            
            Result.success(lastInsertId)
        } catch (e: Exception) {
            Result.failure(ProfileRepositoryException("Failed to create profile: ${e.message}", e))
        }
    }
    
    override suspend fun getProfile(id: Long): ServerProfile? = withContext(Dispatchers.Default) {
        try {
            queries.selectProfileById(id)
                .asFlow()
                .mapToOneOrNull(Dispatchers.Default)
                .first()
                ?.let { dbProfile ->
                    @Suppress("DEPRECATION")
                    ServerProfile(
                        id = dbProfile.id,
                        name = dbProfile.name,
                        serverHost = dbProfile.hostname,
                        serverPort = dbProfile.port.toInt(),
                        cipher = com.sshtunnel.data.CipherMethod.AES_256_GCM, // Default cipher for migration
                        createdAt = dbProfile.createdAt,
                        lastUsed = dbProfile.lastUsed,
                        hostname = dbProfile.hostname,
                        port = dbProfile.port.toInt(),
                        username = dbProfile.username,
                        keyType = KeyType.valueOf(dbProfile.keyType)
                    )
                }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getAllProfiles(): List<ServerProfile> = withContext(Dispatchers.Default) {
        try {
            queries.selectAllProfiles()
                .asFlow()
                .mapToList(Dispatchers.Default)
                .first()
                .map { dbProfile ->
                    @Suppress("DEPRECATION")
                    ServerProfile(
                        id = dbProfile.id,
                        name = dbProfile.name,
                        serverHost = dbProfile.hostname,
                        serverPort = dbProfile.port.toInt(),
                        cipher = com.sshtunnel.data.CipherMethod.AES_256_GCM, // Default cipher for migration
                        createdAt = dbProfile.createdAt,
                        lastUsed = dbProfile.lastUsed,
                        hostname = dbProfile.hostname,
                        port = dbProfile.port.toInt(),
                        username = dbProfile.username,
                        keyType = KeyType.valueOf(dbProfile.keyType)
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun updateProfile(profile: ServerProfile): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (profile.id == 0L) {
                return@withContext Result.failure(
                    ProfileRepositoryException("Cannot update profile with id 0")
                )
            }
            
            queries.updateProfile(
                name = profile.name,
                hostname = profile.hostname,
                port = profile.port.toLong(),
                username = profile.username,
                keyType = profile.keyType.name,
                lastUsed = profile.lastUsed,
                id = profile.id
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(ProfileRepositoryException("Failed to update profile: ${e.message}", e))
        }
    }
    
    override suspend fun deleteProfile(id: Long): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            queries.deleteProfile(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(ProfileRepositoryException("Failed to delete profile: ${e.message}", e))
        }
    }
}

/**
 * Exception thrown when a profile repository operation fails.
 */
class ProfileRepositoryException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
