package com.sshtunnel.ssh

import android.content.Context
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of PrivateKeyManager.
 * 
 * Manages private SSH key files in the app's private directory with
 * secure permissions (owner read/write only).
 */
class AndroidPrivateKeyManager(
    private val context: Context,
    private val logger: Logger
) : PrivateKeyManager {
    
    private val keysDir: File = File(context.filesDir, "ssh-keys")
    
    companion object {
        private const val TAG = "PrivateKeyManager"
        private const val KEY_FILE_PREFIX = "key_"
    }
    
    init {
        // Ensure keys directory exists with secure permissions
        if (!keysDir.exists()) {
            keysDir.mkdirs()
            // Set directory permissions to owner-only access
            keysDir.setReadable(false, false)
            keysDir.setReadable(true, true)
            keysDir.setWritable(false, false)
            keysDir.setWritable(true, true)
            keysDir.setExecutable(false, false)
            keysDir.setExecutable(true, true)
        }
    }
    
    override suspend fun writePrivateKey(profileId: Long, keyData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Writing private key for profile $profileId")
            
            // Validate key data
            if (keyData.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Private key data cannot be empty")
                )
            }
            
            // Get key file path
            val keyFile = File(keysDir, "$KEY_FILE_PREFIX$profileId")
            
            // Delete existing key file if present
            if (keyFile.exists()) {
                logger.debug(TAG, "Deleting existing key file for profile $profileId")
                keyFile.delete()
            }
            
            // Write key data to file
            keyFile.writeBytes(keyData)
            
            // Set secure permissions (owner read/write only)
            val permissionsResult = setSecurePermissions(keyFile.absolutePath)
            if (permissionsResult.isFailure) {
                // Clean up on permission failure
                keyFile.delete()
                return@withContext Result.failure(
                    permissionsResult.exceptionOrNull() ?: Exception("Failed to set secure permissions")
                )
            }
            
            logger.info(TAG, "Successfully wrote private key to ${keyFile.absolutePath}")
            Result.success(keyFile.absolutePath)
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to write private key for profile $profileId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deletePrivateKey(profileId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Deleting private key for profile $profileId")
            
            val keyFile = File(keysDir, "$KEY_FILE_PREFIX$profileId")
            
            if (!keyFile.exists()) {
                logger.debug(TAG, "Key file does not exist for profile $profileId")
                return@withContext Result.success(Unit)
            }
            
            // Delete the key file
            val deleted = keyFile.delete()
            
            if (!deleted) {
                logger.warn(TAG, "Failed to delete key file for profile $profileId")
                return@withContext Result.failure(
                    Exception("Failed to delete key file")
                )
            }
            
            logger.info(TAG, "Successfully deleted private key for profile $profileId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error deleting private key for profile $profileId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun setSecurePermissions(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            
            if (!file.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("File does not exist: $filePath")
                )
            }
            
            // Set permissions to owner read/write only (600 in Unix terms)
            // First, remove all permissions
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            
            // Then, set owner-only read/write
            val readableSet = file.setReadable(true, true)
            val writableSet = file.setWritable(true, true)
            
            if (!readableSet || !writableSet) {
                logger.warn(TAG, "Failed to set secure permissions on $filePath")
                return@withContext Result.failure(
                    Exception("Failed to set secure permissions")
                )
            }
            
            logger.debug(TAG, "Set secure permissions on $filePath")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error setting secure permissions on $filePath", e)
            Result.failure(e)
        }
    }
    
    override fun getKeyFilePath(profileId: Long): String {
        return File(keysDir, "$KEY_FILE_PREFIX$profileId").absolutePath
    }
    
    override suspend fun keyFileExists(profileId: Long): Boolean = withContext(Dispatchers.IO) {
        val keyFile = File(keysDir, "$KEY_FILE_PREFIX$profileId")
        keyFile.exists()
    }
    
    override suspend fun deleteAllKeys(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Deleting all private keys")
            
            if (!keysDir.exists()) {
                logger.debug(TAG, "Keys directory does not exist")
                return@withContext Result.success(Unit)
            }
            
            // Get all key files
            val keyFiles = keysDir.listFiles { file ->
                file.isFile && file.name.startsWith(KEY_FILE_PREFIX)
            } ?: emptyArray()
            
            var failedCount = 0
            keyFiles.forEach { file ->
                if (!file.delete()) {
                    logger.warn(TAG, "Failed to delete key file: ${file.name}")
                    failedCount++
                }
            }
            
            if (failedCount > 0) {
                logger.warn(TAG, "Failed to delete $failedCount key files")
                return@withContext Result.failure(
                    Exception("Failed to delete $failedCount key files")
                )
            }
            
            logger.info(TAG, "Successfully deleted ${keyFiles.size} private keys")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error deleting all private keys", e)
            Result.failure(e)
        }
    }
}
