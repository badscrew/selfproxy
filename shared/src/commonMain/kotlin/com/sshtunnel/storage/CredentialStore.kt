package com.sshtunnel.storage

/**
 * Interface for securely storing and retrieving Shadowsocks passwords.
 * 
 * Platform-specific implementations handle encryption and secure storage
 * using platform-native security features (Android Keystore, iOS Keychain).
 */
interface CredentialStore {
    /**
     * Stores a Shadowsocks password securely with encryption.
     * 
     * @param profileId The unique identifier of the profile this password belongs to
     * @param password The password to store
     * @return Result indicating success or failure of the storage operation
     */
    suspend fun storePassword(
        profileId: Long,
        password: String
    ): Result<Unit>
    
    /**
     * Retrieves and decrypts a Shadowsocks password.
     * 
     * @param profileId The unique identifier of the profile to retrieve the password for
     * @return Result containing the decrypted password on success, or an error on failure
     */
    suspend fun retrievePassword(
        profileId: Long
    ): Result<String>
    
    /**
     * Deletes a stored Shadowsocks password.
     * 
     * @param profileId The unique identifier of the profile whose password should be deleted
     * @return Result indicating success or failure of the delete operation
     */
    suspend fun deletePassword(profileId: Long): Result<Unit>
}
