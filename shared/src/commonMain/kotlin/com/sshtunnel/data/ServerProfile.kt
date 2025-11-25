package com.sshtunnel.data

/**
 * Represents a saved SSH server profile with connection details.
 * 
 * @property id Unique identifier for the profile (0 for new profiles)
 * @property name User-friendly name for the profile
 * @property hostname SSH server hostname or IP address
 * @property port SSH server port (default 22)
 * @property username SSH username for authentication
 * @property keyType Type of SSH private key used for authentication
 * @property createdAt Timestamp when the profile was created (milliseconds since epoch)
 * @property lastUsed Timestamp when the profile was last used (milliseconds since epoch, null if never used)
 */
data class ServerProfile(
    val id: Long = 0,
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val keyType: KeyType,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null
)

/**
 * Supported SSH private key types.
 */
enum class KeyType {
    RSA,
    ECDSA,
    ED25519
}
