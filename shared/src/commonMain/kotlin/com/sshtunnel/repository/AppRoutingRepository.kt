package com.sshtunnel.repository

import com.sshtunnel.data.AppRoutingConfig
import com.sshtunnel.data.RoutingMode

/**
 * Repository interface for managing app routing configurations.
 * 
 * Handles persistence of per-app routing settings for each server profile.
 */
interface AppRoutingRepository {
    /**
     * Gets the routing configuration for a specific profile.
     * 
     * @param profileId The profile ID
     * @return The routing configuration, or null if not found
     */
    suspend fun getRoutingConfig(profileId: Long): AppRoutingConfig?
    
    /**
     * Saves or updates the routing configuration for a profile.
     * 
     * @param config The routing configuration to save
     * @return Result indicating success or failure
     */
    suspend fun saveRoutingConfig(config: AppRoutingConfig): Result<Unit>
    
    /**
     * Deletes the routing configuration for a profile.
     * 
     * @param profileId The profile ID
     * @return Result indicating success or failure
     */
    suspend fun deleteRoutingConfig(profileId: Long): Result<Unit>
}
