package com.sshtunnel.repository

import com.sshtunnel.data.ServerProfile

/**
 * Repository interface for managing SSH server profiles.
 * 
 * Provides CRUD operations for server profiles with error handling using Result types.
 * All operations are suspend functions to support asynchronous database access.
 */
interface ProfileRepository {
    /**
     * Creates a new server profile in persistent storage.
     * 
     * @param profile The server profile to create (id will be auto-generated)
     * @return Result containing the generated profile ID on success, or an error on failure
     */
    suspend fun createProfile(profile: ServerProfile): Result<Long>
    
    /**
     * Retrieves a server profile by its unique identifier.
     * 
     * @param id The unique identifier of the profile to retrieve
     * @return The server profile if found, null otherwise
     */
    suspend fun getProfile(id: Long): ServerProfile?
    
    /**
     * Retrieves all saved server profiles.
     * 
     * @return List of all server profiles, empty list if none exist
     */
    suspend fun getAllProfiles(): List<ServerProfile>
    
    /**
     * Updates an existing server profile with new details.
     * 
     * @param profile The server profile with updated details (must have valid id)
     * @return Result indicating success or failure of the update operation
     */
    suspend fun updateProfile(profile: ServerProfile): Result<Unit>
    
    /**
     * Deletes a server profile from persistent storage.
     * 
     * @param id The unique identifier of the profile to delete
     * @return Result indicating success or failure of the delete operation
     */
    suspend fun deleteProfile(id: Long): Result<Unit>
}
