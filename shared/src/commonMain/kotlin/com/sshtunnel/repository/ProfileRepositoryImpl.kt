package com.sshtunnel.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.sshtunnel.data.CipherMethod
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.db.SSHTunnelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Implementation of ProfileRepository using SQLDelight for cross-platform database access.
 * 
 * This implementation provides thread-safe CRUD operations for Shadowsocks server profiles
 * with proper error handling using Result types.
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
                serverHost = profile.serverHost,
                serverPort = profile.serverPort.toLong(),
                cipher = profile.cipher.name,
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
                    ServerProfile(
                        id = dbProfile.id,
                        name = dbProfile.name,
                        serverHost = dbProfile.serverHost,
                        serverPort = dbProfile.serverPort.toInt(),
                        cipher = CipherMethod.valueOf(dbProfile.cipher),
                        createdAt = dbProfile.createdAt,
                        lastUsed = dbProfile.lastUsed
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
                    ServerProfile(
                        id = dbProfile.id,
                        name = dbProfile.name,
                        serverHost = dbProfile.serverHost,
                        serverPort = dbProfile.serverPort.toInt(),
                        cipher = CipherMethod.valueOf(dbProfile.cipher),
                        createdAt = dbProfile.createdAt,
                        lastUsed = dbProfile.lastUsed
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
                serverHost = profile.serverHost,
                serverPort = profile.serverPort.toLong(),
                cipher = profile.cipher.name,
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
