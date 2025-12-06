package com.sshtunnel.data

import kotlinx.serialization.Serializable

/**
 * Represents a saved Shadowsocks server profile with connection details.
 * 
 * @property id Unique identifier for the profile (0 for new profiles)
 * @property name User-friendly name for the profile
 * @property serverHost Shadowsocks server hostname or IP address
 * @property serverPort Shadowsocks server port (typically 8388)
 * @property cipher Encryption cipher method used by the server
 * @property createdAt Timestamp when the profile was created (milliseconds since epoch)
 * @property lastUsed Timestamp when the profile was last used (milliseconds since epoch, null if never used)
 */
@Serializable
data class ServerProfile(
    val id: Long = 0,
    val name: String,
    val serverHost: String,
    val serverPort: Int = 8388,
    val cipher: CipherMethod,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null,
    
    // Deprecated SSH fields - kept temporarily for backward compatibility
    // These will be removed when SSH code is fully removed in task 2
    @Deprecated("SSH support is being removed")
    val hostname: String = serverHost,
    @Deprecated("SSH support is being removed")
    val port: Int = serverPort,
    @Deprecated("SSH support is being removed")
    val username: String = "",
    @Deprecated("SSH support is being removed")
    val keyType: KeyType = KeyType.ED25519
)

/**
 * Supported Shadowsocks encryption cipher methods.
 * All ciphers use AEAD (Authenticated Encryption with Associated Data) mode.
 */
@Serializable
enum class CipherMethod {
    /**
     * AES-256-GCM: Advanced Encryption Standard with 256-bit key and Galois/Counter Mode.
     * Provides strong security with good performance.
     */
    AES_256_GCM,
    
    /**
     * ChaCha20-IETF-Poly1305: Modern stream cipher with Poly1305 MAC.
     * Excellent performance on devices without AES hardware acceleration.
     */
    CHACHA20_IETF_POLY1305,
    
    /**
     * AES-128-GCM: Advanced Encryption Standard with 128-bit key and Galois/Counter Mode.
     * Faster than AES-256-GCM with still strong security.
     */
    AES_128_GCM
}

/**
 * @deprecated This enum is for SSH and will be removed when SSH code is fully removed.
 * Temporarily kept for backward compatibility with existing code.
 */
@Deprecated("SSH support is being removed. Use CipherMethod for Shadowsocks instead.")
@Serializable
enum class KeyType {
    RSA,
    ECDSA,
    ED25519
}
