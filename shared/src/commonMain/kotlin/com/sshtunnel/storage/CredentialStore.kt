package com.sshtunnel.storage

import com.sshtunnel.data.KeyType

/**
 * Interface for securely storing and retrieving SSH private keys.
 * 
 * Platform-specific implementations handle encryption and secure storage
 * using platform-native security features (Android Keystore, iOS Keychain).
 */
interface CredentialStore {
    /**
     * Stores an SSH private key securely with encryption.
     * 
     * @param profileId The unique identifier of the profile this key belongs to
     * @param privateKey The raw private key bytes to store
     * @param passphrase Optional passphrase for passphrase-protected keys
     * @return Result indicating success or failure of the storage operation
     */
    suspend fun storeKey(
        profileId: Long,
        privateKey: ByteArray,
        passphrase: String? = null
    ): Result<Unit>
    
    /**
     * Retrieves and decrypts an SSH private key.
     * 
     * @param profileId The unique identifier of the profile to retrieve the key for
     * @param passphrase Optional passphrase for passphrase-protected keys
     * @return Result containing the decrypted PrivateKey on success, or an error on failure
     */
    suspend fun retrieveKey(
        profileId: Long,
        passphrase: String? = null
    ): Result<PrivateKey>
    
    /**
     * Deletes a stored SSH private key.
     * 
     * @param profileId The unique identifier of the profile whose key should be deleted
     * @return Result indicating success or failure of the delete operation
     */
    suspend fun deleteKey(profileId: Long): Result<Unit>
}

/**
 * Represents a decrypted SSH private key with its type information.
 * 
 * @property keyData The raw private key bytes
 * @property keyType The type of SSH key (RSA, ECDSA, ED25519)
 */
data class PrivateKey(
    val keyData: ByteArray,
    val keyType: KeyType
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PrivateKey

        if (!keyData.contentEquals(other.keyData)) return false
        if (keyType != other.keyType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyData.contentHashCode()
        result = 31 * result + keyType.hashCode()
        return result
    }
}
