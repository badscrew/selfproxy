package com.sshtunnel.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sshtunnel.logging.Logger
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
 * - Encrypts passwords using AES-256-GCM
 * - Stores encrypted passwords in EncryptedSharedPreferences
 * - Sanitizes passwords from logs to prevent exposure
 * - Securely erases passwords from memory after use
 * 
 * @property context Android application context
 */
class AndroidCredentialStore(
    private val context: Context,
    private val logger: Logger
) : CredentialStore {
    
    companion object {
        private const val KEYSTORE_ALIAS = "shadowsocks_password_encryption_key"
        private const val PREFS_NAME = "shadowsocks_credentials"
        private const val PASSWORD_PREFIX = "password_"
        private const val IV_PREFIX = "iv_"
        private const val GCM_TAG_LENGTH = 128
        private const val TAG = "AndroidCredentialStore"
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
    
    override suspend fun storePassword(
        profileId: Long,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Storing password for profile $profileId")
            
            // Get or create encryption key
            val secretKey = getOrCreateEncryptionKey()
            
            // Convert password to bytes
            val passwordBytes = password.toByteArray(Charsets.UTF_8)
            
            // Encrypt the password
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedPassword = cipher.doFinal(passwordBytes)
            
            // Clear password bytes from memory
            passwordBytes.fill(0)
            
            // Store encrypted password and IV in EncryptedSharedPreferences
            encryptedPrefs.edit().apply {
                putString(PASSWORD_PREFIX + profileId, Base64.encodeToString(encryptedPassword, Base64.NO_WRAP))
                putString(IV_PREFIX + profileId, Base64.encodeToString(iv, Base64.NO_WRAP))
                apply()
            }
            
            logger.debug(TAG, "Password stored successfully for profile $profileId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to store password for profile $profileId", e)
            Result.failure(CredentialStoreException("Failed to store password for profile $profileId", e))
        }
    }
    
    override suspend fun retrievePassword(
        profileId: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Retrieving password for profile $profileId")
            
            // Retrieve encrypted password and IV
            val encryptedPasswordBase64 = encryptedPrefs.getString(PASSWORD_PREFIX + profileId, null)
                ?: return@withContext Result.failure(
                    CredentialStoreException("No password found for profile $profileId")
                )
            
            val ivBase64 = encryptedPrefs.getString(IV_PREFIX + profileId, null)
                ?: return@withContext Result.failure(
                    CredentialStoreException("No IV found for profile $profileId")
                )
            
            val encryptedPassword = Base64.decode(encryptedPasswordBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            
            // Get encryption key
            val secretKey = getOrCreateEncryptionKey()
            
            // Decrypt the password
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decryptedPasswordBytes = cipher.doFinal(encryptedPassword)
            
            // Convert bytes to string
            val password = String(decryptedPasswordBytes, Charsets.UTF_8)
            
            // Clear decrypted bytes from memory
            decryptedPasswordBytes.fill(0)
            
            logger.debug(TAG, "Password retrieved successfully for profile $profileId")
            Result.success(password)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to retrieve password for profile $profileId", e)
            Result.failure(CredentialStoreException("Failed to retrieve password for profile $profileId", e))
        }
    }
    
    override suspend fun deletePassword(profileId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Deleting password for profile $profileId")
            
            encryptedPrefs.edit().apply {
                remove(PASSWORD_PREFIX + profileId)
                remove(IV_PREFIX + profileId)
                apply()
            }
            
            logger.debug(TAG, "Password deleted successfully for profile $profileId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to delete password for profile $profileId", e)
            Result.failure(CredentialStoreException("Failed to delete password for profile $profileId", e))
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
    
}

/**
 * Exception thrown when credential store operations fail.
 */
class CredentialStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
