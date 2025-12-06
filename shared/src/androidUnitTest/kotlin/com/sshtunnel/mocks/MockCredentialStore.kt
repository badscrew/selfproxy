package com.sshtunnel.mocks

import com.sshtunnel.storage.CredentialStore
import com.sshtunnel.storage.CredentialStoreException
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock implementation of CredentialStore for testing.
 * 
 * This mock stores passwords in memory without encryption, suitable for unit tests.
 * It simulates the behavior of AndroidCredentialStore without requiring
 * Android Keystore or EncryptedSharedPreferences.
 */
class MockCredentialStore : CredentialStore {
    
    private val passwords = ConcurrentHashMap<Long, String>()
    
    override suspend fun storePassword(
        profileId: Long,
        password: String
    ): Result<Unit> {
        return try {
            passwords[profileId] = password
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(CredentialStoreException("Failed to store password", e))
        }
    }
    
    override suspend fun retrievePassword(
        profileId: Long
    ): Result<String> {
        val password = passwords[profileId]
            ?: return Result.failure(
                CredentialStoreException("No password found for profile $profileId")
            )
        
        return Result.success(password)
    }
    
    override suspend fun deletePassword(profileId: Long): Result<Unit> {
        passwords.remove(profileId)
        return Result.success(Unit)
    }
    
    /**
     * Clears all stored passwords (useful for test cleanup).
     */
    fun clear() {
        passwords.clear()
    }
    
    /**
     * Returns the number of stored passwords (useful for test assertions).
     */
    fun size(): Int = passwords.size
}
