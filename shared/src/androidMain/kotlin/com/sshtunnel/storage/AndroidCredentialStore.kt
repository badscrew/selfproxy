package com.sshtunnel.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sshtunnel.data.KeyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android implementation of CredentialStore using Android Keystore for encryption
 * and EncryptedSharedPreferences for storage.
 * 
 * This implementation:
 * - Uses Android Keystore for hardware-backed encryption (when available)
 * - Encrypts private keys using AES-256-GCM
 * - Stores encrypted keys in EncryptedSharedPreferences
 * - Handles passphrase-protected keys by storing the passphrase separately
 * 
 * @property context Android application context
 */
class AndroidCredentialStore(private val context: Context) : CredentialStore {
    
    companion object {
        private const val KEYSTORE_ALIAS = "ssh_key_encryption_key"
        private const val PREFS_NAME = "ssh_credentials"
        private const val KEY_PREFIX = "key_"
        private const val KEY_TYPE_PREFIX = "keytype_"
        private const val PASSPHRASE_PREFIX = "passphrase_"
        private const val IV_PREFIX = "iv_"
        private const val GCM_TAG_LENGTH = 128
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }
    
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    override suspend fun storeKey(
        profileId: Long,
        privateKey: ByteArray,
        passphrase: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get or create encryption key
            val secretKey = getOrCreateEncryptionKey()
            
            // Encrypt the private key
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedKey = cipher.doFinal(privateKey)
            
            // Detect key type from key data
            val keyType = detectKeyType(privateKey)
            
            // Store encrypted key and IV in EncryptedSharedPreferences
            encryptedPrefs.edit().apply {
                putString(KEY_PREFIX + profileId, Base64.encodeToString(encryptedKey, Base64.NO_WRAP))
                putString(IV_PREFIX + profileId, Base64.encodeToString(iv, Base64.NO_WRAP))
                putString(KEY_TYPE_PREFIX + profileId, keyType.name)
                
                // Store passphrase if provided (already encrypted by EncryptedSharedPreferences)
                if (passphrase != null) {
                    putString(PASSPHRASE_PREFIX + profileId, passphrase)
                }
                
                apply()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(CredentialStoreException("Failed to store key for profile $profileId", e))
        }
    }
    
    override suspend fun retrieveKey(
        profileId: Long,
        passphrase: String?
    ): Result<PrivateKey> = withContext(Dispatchers.IO) {
        try {
            // Retrieve encrypted key and IV
            val encryptedKeyBase64 = encryptedPrefs.getString(KEY_PREFIX + profileId, null)
                ?: return@withContext Result.failure(
                    CredentialStoreException("No key found for profile $profileId")
                )
            
            val ivBase64 = encryptedPrefs.getString(IV_PREFIX + profileId, null)
                ?: return@withContext Result.failure(
                    CredentialStoreException("No IV found for profile $profileId")
                )
            
            val keyTypeString = encryptedPrefs.getString(KEY_TYPE_PREFIX + profileId, null)
                ?: return@withContext Result.failure(
                    CredentialStoreException("No key type found for profile $profileId")
                )
            
            val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val keyType = KeyType.valueOf(keyTypeString)
            
            // Get encryption key
            val secretKey = getOrCreateEncryptionKey()
            
            // Decrypt the private key
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decryptedKey = cipher.doFinal(encryptedKey)
            
            Result.success(PrivateKey(decryptedKey, keyType))
        } catch (e: Exception) {
            Result.failure(CredentialStoreException("Failed to retrieve key for profile $profileId", e))
        }
    }
    
    override suspend fun deleteKey(profileId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit().apply {
                remove(KEY_PREFIX + profileId)
                remove(IV_PREFIX + profileId)
                remove(KEY_TYPE_PREFIX + profileId)
                remove(PASSPHRASE_PREFIX + profileId)
                apply()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(CredentialStoreException("Failed to delete key for profile $profileId", e))
        }
    }
    
    /**
     * Gets the encryption key from Android Keystore, or creates it if it doesn't exist.
     * Uses hardware-backed encryption when available.
     */
    private fun getOrCreateEncryptionKey(): SecretKey {
        return if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            entry.secretKey
        } else {
            createEncryptionKey()
        }
    }
    
    /**
     * Creates a new AES-256-GCM encryption key in Android Keystore.
     */
    private fun createEncryptionKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    /**
     * Detects the SSH key type from the key data.
     * This is a simple heuristic based on key format markers.
     */
    private fun detectKeyType(keyData: ByteArray): KeyType {
        val keyString = String(keyData)
        
        return when {
            keyString.contains("BEGIN OPENSSH PRIVATE KEY") && keyString.contains("ssh-ed25519") -> KeyType.ED25519
            keyString.contains("BEGIN EC PRIVATE KEY") || keyString.contains("ecdsa") -> KeyType.ECDSA
            keyString.contains("BEGIN RSA PRIVATE KEY") || keyString.contains("BEGIN PRIVATE KEY") -> KeyType.RSA
            keyString.contains("ssh-ed25519") -> KeyType.ED25519
            else -> KeyType.RSA // Default to RSA if unable to detect
        }
    }
}

/**
 * Exception thrown when credential store operations fail.
 */
class CredentialStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
