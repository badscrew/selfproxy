package com.sshtunnel.repository

import com.sshtunnel.data.AppRoutingConfig

/**
 * Repository interface for managing app routing configurations.
 * 
 * Handles persistence of per-app VPN routing settings, allowing users to
 * exclude specific apps from the VPN tunnel.
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
     * Saves the routing configuration for a profile.
     * 
     * @param config The routing configuration to save
     * @return Result indicating success or failure
     */
    suspend fun saveRoutingConfig(config: AppRoutingConfig): Result<Unit>
    
    /**
     * Gets the set of excluded app package names for a profile.
     * 
     * Apps in this set will bypass the VPN tunnel and use direct network access.
     * 
     * @param profileId The profile ID
     * @return Set of package names that are excluded from the VPN
     */
    suspend fun getExcludedApps(profileId: Long): Set<String>
    
    /**
     * Gets the set of excluded app package names (global, for backward compatibility).
     * 
     * @return Set of package names that are excluded from the VPN
     */
    suspend fun getExcludedApps(): Set<String>
    
    /**
     * Sets the excluded app package names for a profile.
     * 
     * Replaces the current exclusion list with the provided set.
     * Package names are validated before persistence.
     * 
     * @param profileId The profile ID
     * @param packageNames Set of package names to exclude from the VPN
     * @return Result indicating success or failure
     */
    suspend fun setExcludedApps(profileId: Long, packageNames: Set<String>): Result<Unit>
    
    /**
     * Sets the excluded app package names (global, for backward compatibility).
     * 
     * @param packageNames Set of package names to exclude from the VPN
     * @return Result indicating success or failure
     */
    suspend fun setExcludedApps(packageNames: Set<String>): Result<Unit>
    
    /**
     * Checks if a specific app is excluded from the VPN for a profile.
     * 
     * Provides quick lookup without loading the entire exclusion set.
     * 
     * @param profileId The profile ID
     * @param packageName The package name to check
     * @return true if the app is excluded, false otherwise
     */
    suspend fun isAppExcluded(profileId: Long, packageName: String): Boolean
    
    /**
     * Checks if a specific app is excluded from the VPN (global, for backward compatibility).
     * 
     * @param packageName The package name to check
     * @return true if the app is excluded, false otherwise
     */
    suspend fun isAppExcluded(packageName: String): Boolean
}
