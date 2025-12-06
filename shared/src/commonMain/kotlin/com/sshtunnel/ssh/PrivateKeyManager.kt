package com.sshtunnel.ssh

/**
 * Manages private SSH key files for native SSH client.
 * 
 * Handles secure creation, storage, and cleanup of private key files
 * required by the native SSH binary.
 */
interface PrivateKeyManager {
    /**
     * Write private key to a secure file in the app's private directory.
     * 
     * @param profileId Profile identifier for the key
     * @param keyData Private key bytes
     * @return Result containing path to key file, or error on failure
     */
    suspend fun writePrivateKey(profileId: Long, keyData: ByteArray): Result<String>
    
    /**
     * Delete private key file for a profile.
     * 
     * @param profileId Profile identifier
     * @return Result indicating success or failure
     */
    suspend fun deletePrivateKey(profileId: Long): Result<Unit>
    
    /**
     * Set secure file permissions on a key file (owner read/write only).
     * 
     * @param filePath Path to key file
     * @return Result indicating success or failure
     */
    suspend fun setSecurePermissions(filePath: String): Result<Unit>
    
    /**
     * Get the path where a private key would be stored for a profile.
     * 
     * @param profileId Profile identifier
     * @return Path to key file (may not exist yet)
     */
    fun getKeyFilePath(profileId: Long): String
    
    /**
     * Check if a private key file exists for a profile.
     * 
     * @param profileId Profile identifier
     * @return true if key file exists
     */
    suspend fun keyFileExists(profileId: Long): Boolean
    
    /**
     * Delete all private key files (cleanup on app uninstall or error).
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteAllKeys(): Result<Unit>
}
