package com.sshtunnel.storage

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import com.sshtunnel.data.KeyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Android implementation of SSHKeyParser using JSch library.
 * 
 * This implementation:
 * - Supports RSA, ECDSA, and Ed25519 key formats
 * - Handles passphrase-protected keys
 * - Validates key format and structure
 * - Detects key types from key data
 */
class AndroidSSHKeyParser : SSHKeyParser {
    
    companion object {
        // JSch key type constants
        private const val KEY_TYPE_RSA = KeyPair.RSA
        private const val KEY_TYPE_DSA = KeyPair.DSA
        private const val KEY_TYPE_ECDSA = KeyPair.ECDSA
        private const val KEY_TYPE_UNKNOWN = KeyPair.UNKNOWN
        private const val KEY_TYPE_ERROR = KeyPair.ERROR
        
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
    
    private val jsch = JSch()
    
    override suspend fun parseKey(
        keyData: ByteArray,
        passphrase: String?
    ): Result<ParsedKey> = withContext(Dispatchers.IO) {
        try {
            // Check if key is passphrase-protected
            val isEncrypted = isPassphraseProtected(keyData)
            
            if (isEncrypted && passphrase == null) {
                return@withContext Result.failure(
                    SSHKeyException.PassphraseRequiredException()
                )
            }
            
            // Load the key using JSch
            // JSch requires writing to a temp file, so we'll use a different approach
            val keyPair = try {
                loadKeyPairFromBytes(keyData, passphrase)
            } catch (e: JSchException) {
                return@withContext Result.failure(
                    when {
                        e.message?.contains("invalid privatekey", ignoreCase = true) == true ->
                            SSHKeyException.InvalidFormatException("Invalid private key format", e)
                        e.message?.contains("passphrase", ignoreCase = true) == true ->
                            SSHKeyException.IncorrectPassphraseException()
                        e.message?.contains("Auth fail", ignoreCase = true) == true ->
                            SSHKeyException.IncorrectPassphraseException()
                        else ->
                            SSHKeyException.ParsingException("Failed to parse SSH key: ${e.message}", e)
                    }
                )
            }
            
            // Detect key type
            val keyType = when (keyPair.keyType) {
                KEY_TYPE_RSA -> KeyType.RSA
                KEY_TYPE_ECDSA -> KeyType.ECDSA
                KEY_TYPE_DSA -> {
                    keyPair.dispose()
                    return@withContext Result.failure(
                        SSHKeyException.UnsupportedKeyTypeException("DSA keys are not supported")
                    )
                }
                else -> {
                    // Check for Ed25519 by examining the key data
                    val keyString = String(keyData, Charsets.UTF_8)
                    if (keyString.contains("ssh-ed25519") || keyString.contains("ED25519")) {
                        KeyType.ED25519
                    } else {
                        keyPair.dispose()
                        return@withContext Result.failure(
                            SSHKeyException.UnsupportedKeyTypeException("Unknown or unsupported key type")
                        )
                    }
                }
            }
            
            // Generate fingerprint
            val fingerprint = try {
                generateFingerprint(keyPair)
            } catch (e: Exception) {
                null // Fingerprint is optional
            }
            
            // Dispose of the key pair to free resources
            keyPair.dispose()
            
            Result.success(
                ParsedKey(
                    keyData = keyData,
                    keyType = keyType,
                    isEncrypted = isEncrypted,
                    fingerprint = fingerprint
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
    
    /**
     * Generates an MD5 fingerprint for the SSH key.
     */
    private fun generateFingerprint(keyPair: KeyPair): String {
        val publicKeyBlob = keyPair.publicKeyBlob
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(publicKeyBlob)
        
        return digest.joinToString(":") { byte ->
            "%02x".format(byte)
        }
    }
    
    /**
     * Loads a KeyPair from byte array.
     * JSch doesn't have a direct method to load from bytes, so we write to a temp file.
     */
    private fun loadKeyPairFromBytes(keyData: ByteArray, passphrase: String?): KeyPair {
        // Create a temporary file to hold the key
        val tempFile = java.io.File.createTempFile("ssh_key_", ".pem")
        try {
            tempFile.writeBytes(keyData)
            
            // Load the key pair
            val keyPair = KeyPair.load(jsch, tempFile.absolutePath)
            
            // If passphrase is provided, decrypt the key
            if (passphrase != null && keyPair.isEncrypted) {
                val decrypted = keyPair.decrypt(passphrase)
                if (!decrypted) {
                    throw JSchException("Failed to decrypt key with provided passphrase")
                }
            }
            
            return keyPair
        } finally {
            // Clean up temp file
            tempFile.delete()
        }
    }
}
