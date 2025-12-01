package com.sshtunnel.storage

import com.sshtunnel.data.KeyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Android implementation of SSHKeyParser.
 * 
 * TODO: Update to use BouncyCastle instead of JSch for key parsing.
 * This is a temporary stub to allow compilation during the sshj migration.
 * 
 * This implementation:
 * - Supports RSA, ECDSA, and Ed25519 key formats
 * - Handles passphrase-protected keys
 * - Validates key format and structure
 * - Detects key types from key data
 */
class AndroidSSHKeyParser : SSHKeyParser {
    
    companion object {
        
        // Key format markers
        private const val OPENSSH_PRIVATE_KEY_HEADER = "-----BEGIN OPENSSH PRIVATE KEY-----"
        private const val RSA_PRIVATE_KEY_HEADER = "-----BEGIN RSA PRIVATE KEY-----"
        private const val EC_PRIVATE_KEY_HEADER = "-----BEGIN EC PRIVATE KEY-----"
        private const val PRIVATE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----"
        private const val ENCRYPTED_PRIVATE_KEY_HEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----"
        
        // Encryption markers
        private const val PROC_TYPE_ENCRYPTED = "Proc-Type: 4,ENCRYPTED"
        private const val DEK_INFO = "DEK-Info:"
    }
    
    override suspend fun parseKey(
        keyData: ByteArray,
        passphrase: String?
    ): Result<ParsedKey> = withContext(Dispatchers.IO) {
        try {
            // TODO: Implement proper key parsing with BouncyCastle
            // For now, just detect the key type from the PEM header
            val isEncrypted = isPassphraseProtected(keyData)
            
            if (isEncrypted && passphrase == null) {
                return@withContext Result.failure(
                    SSHKeyException.PassphraseRequiredException()
                )
            }
            
            // Detect key type from PEM headers
            val keyTypeResult = detectKeyType(keyData)
            if (keyTypeResult.isFailure) {
                return@withContext Result.failure(
                    keyTypeResult.exceptionOrNull() ?: SSHKeyException.ParsingException("Failed to detect key type")
                )
            }
            
            val keyType = keyTypeResult.getOrThrow()
            
            Result.success(
                ParsedKey(
                    keyData = keyData,
                    keyType = keyType,
                    isEncrypted = isEncrypted,
                    fingerprint = null // TODO: Generate fingerprint with BouncyCastle
                )
            )
        } catch (e: Exception) {
            Result.failure(
                SSHKeyException.ParsingException("Unexpected error parsing SSH key", e)
            )
        }
    }
    
    override fun detectKeyType(keyData: ByteArray): Result<KeyType> {
        return try {
            val keyString = String(keyData, Charsets.UTF_8)
            
            val keyType = when {
                // Check for OpenSSH format with key type indicator
                keyString.contains(OPENSSH_PRIVATE_KEY_HEADER) -> {
                    when {
                        keyString.contains("ssh-ed25519") -> KeyType.ED25519
                        keyString.contains("ecdsa-sha2-nistp") -> KeyType.ECDSA
                        keyString.contains("ssh-rsa") -> KeyType.RSA
                        else -> null
                    }
                }
                // Check for traditional PEM formats
                keyString.contains(RSA_PRIVATE_KEY_HEADER) -> KeyType.RSA
                keyString.contains(EC_PRIVATE_KEY_HEADER) -> KeyType.ECDSA
                keyString.contains(PRIVATE_KEY_HEADER) -> {
                    // PKCS#8 format - need to parse further or default to RSA
                    KeyType.RSA
                }
                else -> null
            }
            
            if (keyType != null) {
                Result.success(keyType)
            } else {
                Result.failure(
                    SSHKeyException.UnsupportedKeyTypeException("Unable to detect key type from key data")
                )
            }
        } catch (e: Exception) {
            Result.failure(
                SSHKeyException.ParsingException("Error detecting key type", e)
            )
        }
    }
    
    override fun validateKeyFormat(keyData: ByteArray): Result<Unit> {
        return try {
            val keyString = String(keyData, Charsets.UTF_8)
            
            // Check for valid PEM format
            val hasValidHeader = keyString.contains(OPENSSH_PRIVATE_KEY_HEADER) ||
                    keyString.contains(RSA_PRIVATE_KEY_HEADER) ||
                    keyString.contains(EC_PRIVATE_KEY_HEADER) ||
                    keyString.contains(PRIVATE_KEY_HEADER) ||
                    keyString.contains(ENCRYPTED_PRIVATE_KEY_HEADER)
            
            if (!hasValidHeader) {
                return Result.failure(
                    SSHKeyException.InvalidFormatException("Key does not have a valid PEM header")
                )
            }
            
            // Check for corresponding footer
            val hasValidFooter = keyString.contains("-----END") && keyString.contains("PRIVATE KEY-----")
            
            if (!hasValidFooter) {
                return Result.failure(
                    SSHKeyException.InvalidFormatException("Key does not have a valid PEM footer")
                )
            }
            
            // Check for DSA keys (not supported)
            if (keyString.contains("BEGIN DSA PRIVATE KEY") || keyString.contains("ssh-dss")) {
                return Result.failure(
                    SSHKeyException.UnsupportedKeyTypeException("DSA keys are not supported")
                )
            }
            
            // Basic structure validation passed
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                SSHKeyException.InvalidFormatException("Error validating key format", e)
            )
        }
    }
    
    override fun isPassphraseProtected(keyData: ByteArray): Boolean {
        return try {
            val keyString = String(keyData, Charsets.UTF_8)
            
            // Check for encryption markers
            keyString.contains(PROC_TYPE_ENCRYPTED) ||
                    keyString.contains(DEK_INFO) ||
                    keyString.contains(ENCRYPTED_PRIVATE_KEY_HEADER) ||
                    (keyString.contains(OPENSSH_PRIVATE_KEY_HEADER) && 
                     keyString.contains("aes", ignoreCase = true))
        } catch (e: Exception) {
            false
        }
    }
    
}
