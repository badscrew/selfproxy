package com.sshtunnel.mocks

import com.sshtunnel.data.KeyType
import com.sshtunnel.storage.CredentialStore
import com.sshtunnel.storage.CredentialStoreException
import com.sshtunnel.storage.PrivateKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock implementation of CredentialStore for testing.
 * 
 * This mock stores keys in memory without encryption, suitable for unit tests.
 * It simulates the behavior of AndroidCredentialStore without requiring
 * Android Keystore or EncryptedSharedPreferences.
 */
class MockCredentialStore : CredentialStore {
    
    private val keys = ConcurrentHashMap<Long, StoredKey>()
    
    data class StoredKey(
        val keyData: ByteArray,
        val keyType: KeyType,
        val passphrase: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StoredKey) return false
            
            if (!keyData.contentEquals(other.keyData)) return false
            if (keyType != other.keyType) return false
            if (passphrase != other.passphrase) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = keyData.contentHashCode()
            result = 31 * result + keyType.hashCode()
            result = 31 * result + (passphrase?.hashCode() ?: 0)
            return result
        }
    }
    
    override suspend fun storeKey(
        profileId: Long,
        privateKey: ByteArray,
        passphrase: String?
    ): Result<Unit> {
        return try {
            val keyType = detectKeyType(privateKey)
            keys[profileId] = StoredKey(privateKey.copyOf(), keyType, passphrase)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(CredentialStoreException("Failed to store key", e))
        }
    }
    
    override suspend fun retrieveKey(
        profileId: Long,
        passphrase: String?
    ): Result<PrivateKey> {
        val stored = keys[profileId]
            ?: return Result.failure(
                CredentialStoreException("No key found for profile $profileId")
            )
        
        // Verify passphrase if key was stored with one
        if (stored.passphrase != null && stored.passphrase != passphrase) {
            return Result.failure(
                CredentialStoreException("Invalid passphrase")
            )
        }
        
        return Result.success(
            PrivateKey(stored.keyData.copyOf(), stored.keyType)
        )
    }
    
    override suspend fun deleteKey(profileId: Long): Result<Unit> {
        keys.remove(profileId)
        return Result.success(Unit)
    }
    
    /**
     * Detects the SSH key type from the key data.
     */
    private fun detectKeyType(keyData: ByteArray): KeyType {
        val keyString = String(keyData)
        
        return when {
            keyString.contains("ssh-ed25519") || 
            keyString.contains("ED25519") -> KeyType.ED25519
            keyString.contains("ecdsa") || 
            keyString.contains("EC PRIVATE KEY") -> KeyType.ECDSA
            keyString.contains("RSA") || 
            keyString.contains("BEGIN PRIVATE KEY") -> KeyType.RSA
            else -> KeyType.RSA // Default
        }
    }
    
    /**
     * Clears all stored keys (useful for test cleanup).
     */
    fun clear() {
        keys.clear()
    }
    
    /**
     * Returns the number of stored keys (useful for test assertions).
     */
    fun size(): Int = keys.size
}
