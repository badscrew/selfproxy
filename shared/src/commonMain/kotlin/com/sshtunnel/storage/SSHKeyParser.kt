package com.sshtunnel.storage

import com.sshtunnel.data.KeyType

/**
 * Interface for parsing and validating SSH private keys.
 * 
 * Supports RSA, ECDSA, and Ed25519 key formats, including passphrase-protected keys.
 */
interface SSHKeyParser {
    /**
     * Parses an SSH private key from raw bytes and validates its format.
     * 
     * @param keyData The raw private key bytes (typically from a file)
     * @param passphrase Optional passphrase for encrypted keys
     * @return Result containing ParsedKey on success, or an error on failure
     */
    suspend fun parseKey(keyData: ByteArray, passphrase: String? = null): Result<ParsedKey>
    
    /**
     * Detects the type of SSH key from the key data without full parsing.
     * 
     * @param keyData The raw private key bytes
     * @return Result containing the detected KeyType, or an error if unable to detect
     */
    fun detectKeyType(keyData: ByteArray): Result<KeyType>
    
    /**
     * Validates that a key is in a supported format and can be parsed.
     * 
     * @param keyData The raw private key bytes
     * @return Result indicating success if valid, or an error describing the validation failure
     */
    fun validateKeyFormat(keyData: ByteArray): Result<Unit>
    
    /**
     * Checks if a key is passphrase-protected (encrypted).
     * 
     * @param keyData The raw private key bytes
     * @return true if the key requires a passphrase, false otherwise
     */
    fun isPassphraseProtected(keyData: ByteArray): Boolean
}

/**
 * Represents a successfully parsed SSH private key.
 * 
 * @property keyData The raw private key bytes
 * @property keyType The detected SSH key type
 * @property isEncrypted Whether the key was passphrase-protected
 * @property fingerprint Optional key fingerprint for identification
 */
data class ParsedKey(
    val keyData: ByteArray,
    val keyType: KeyType,
    val isEncrypted: Boolean,
    val fingerprint: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ParsedKey

        if (!keyData.contentEquals(other.keyData)) return false
        if (keyType != other.keyType) return false
        if (isEncrypted != other.isEncrypted) return false
        if (fingerprint != other.fingerprint) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyData.contentHashCode()
        result = 31 * result + keyType.hashCode()
        result = 31 * result + isEncrypted.hashCode()
        result = 31 * result + (fingerprint?.hashCode() ?: 0)
        return result
    }
}

/**
 * Exception thrown when SSH key parsing or validation fails.
 */
sealed class SSHKeyException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * The key format is invalid or corrupted.
     */
    class InvalidFormatException(message: String, cause: Throwable? = null) : 
        SSHKeyException(message, cause)
    
    /**
     * The key type is not supported.
     */
    class UnsupportedKeyTypeException(message: String) : 
        SSHKeyException(message)
    
    /**
     * The key is passphrase-protected but no passphrase was provided.
     */
    class PassphraseRequiredException(message: String = "Key is passphrase-protected but no passphrase was provided") : 
        SSHKeyException(message)
    
    /**
     * The provided passphrase is incorrect.
     */
    class IncorrectPassphraseException(message: String = "Incorrect passphrase for encrypted key") : 
        SSHKeyException(message)
    
    /**
     * General parsing error.
     */
    class ParsingException(message: String, cause: Throwable? = null) : 
        SSHKeyException(message, cause)
}
