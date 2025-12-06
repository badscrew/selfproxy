package com.sshtunnel.repository

/**
 * Repository interface for managing app routing configurations.
 * 
 * Handles persistence of per-app VPN routing settings, allowing users to
 * exclude specific apps from the VPN tunnel.
 */
interface AppRoutingRepository {
    /**
     * Gets the set of excluded app package names.
     * 
     * Apps in this set will bypass the VPN tunnel and use direct network access.
     * 
     * @return Set of package names that are excluded from the VPN
     */
    suspend fun getExcludedApps(): Set<String>
    
    /**
     * Sets the excluded app package names.
     * 
     * Replaces the current exclusion list with the provided set.
     * Package names are validated before persistence.
     * 
     * @param packageNames Set of package names to exclude from the VPN
     * @return Result indicating success or failure
     */
    suspend fun setExcludedApps(packageNames: Set<String>): Result<Unit>
    
    /**
     * Checks if a specific app is excluded from the VPN.
     * 
     * Provides quick lookup without loading the entire exclusion set.
     * 
     * @param packageName The package name to check
     * @return true if the app is excluded, false otherwise
     */
    suspend fun isAppExcluded(packageName: String): Boolean
}
