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
    
    /**
     * Validates a server profile according to requirements.
     * 
     * Validation rules:
     * - Name must not be blank
     * - Server host must not be blank
     * - Port must be in valid range (1-65535)
     * - Cipher must be one of the supported methods
     * 
     * @param profile The profile to validate
     * @return Result indicating success or validation error
     */
    private fun validateProfile(profile: ServerProfile): Result<Unit> {
        // Validate name
        if (profile.name.isBlank()) {
            return Result.failure(
                ProfileRepositoryException("Profile name cannot be blank")
            )
        }
        
        // Validate server host
        if (profile.serverHost.isBlank()) {
            return Result.failure(
                ProfileRepositoryException("Server host cannot be blank")
            )
        }
        
        // Validate port range (1-65535)
        if (profile.serverPort !in 1..65535) {
            return Result.failure(
                ProfileRepositoryException("Server port must be between 1 and 65535, got ${profile.serverPort}")
            )
        }
        
        // Cipher validation is implicit through enum type
        // All CipherMethod enum values are valid
        
        return Result.success(Unit)
    }
    
    override suspend fun createProfile(profile: ServerProfile): Result<Long> = withContext(Dispatchers.Default) {
        try {
            // Validate profile before creating
            validateProfile(profile).getOrElse { return@withContext Result.failure(it) }
            
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
            val profile = queries.selectProfileById(id)
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
            
            // Update last_used timestamp when profile is accessed
            if (profile != null) {
                queries.updateLastUsed(
                    lastUsed = System.currentTimeMillis(),
                    id = id
                )
            }
            
            profile
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
            
            // Validate profile before updating
            validateProfile(profile).getOrElse { return@withContext Result.failure(it) }
            
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
