package com.sshtunnel.ssh

/**
 * Manages native SSH binary extraction, caching, and verification.
 */
interface BinaryManager {
    /**
     * Extract SSH binary from APK to private directory.
     * 
     * @param architecture Target CPU architecture
     * @return Result containing path to extracted binary, or error on failure
     */
    suspend fun extractBinary(architecture: Architecture): Result<String>
    
    /**
     * Get cached binary path if available and valid.
     * 
     * @param architecture Target CPU architecture
     * @return Path to cached binary or null if not cached or invalid
     */
    suspend fun getCachedBinary(architecture: Architecture): String?
    
    /**
     * Verify binary integrity using checksum.
     * 
     * @param binaryPath Path to binary file
     * @return true if binary is valid and executable
     */
    suspend fun verifyBinary(binaryPath: String): Boolean
    
    /**
     * Detect device architecture.
     * 
     * @return Architecture enum value for the device
     */
    fun detectArchitecture(): Architecture
    
    /**
     * Get binary metadata for an architecture.
     * 
     * @param architecture Target CPU architecture
     * @return Binary metadata including version and checksum
     */
    fun getBinaryMetadata(architecture: Architecture): BinaryMetadata
}

/**
 * Supported CPU architectures for native SSH binaries.
 */
enum class Architecture(val abiName: String) {
    ARM64("arm64-v8a"),
    ARM32("armeabi-v7a"),
    X86_64("x86_64"),
    X86("x86");
    
    companion object {
        /**
         * Get Architecture from Android ABI name.
         */
        fun fromAbi(abi: String): Architecture {
            return values().find { it.abiName == abi } ?: ARM64
        }
    }
}

/**
 * Metadata about a native SSH binary.
 * 
 * @property architecture The CPU architecture
 * @property version OpenSSH version
 * @property checksum SHA-256 checksum of the binary
 * @property extractedPath Path where binary is extracted (empty if not extracted)
 * @property extractedAt Timestamp when binary was extracted (0 if not extracted)
 * @property appVersion App version when binary was extracted (empty if not extracted)
 */
data class BinaryMetadata(
    val architecture: Architecture,
    val version: String,
    val checksum: String,
    val extractedPath: String = "",
    val extractedAt: Long = 0,
    val appVersion: String = ""
)
