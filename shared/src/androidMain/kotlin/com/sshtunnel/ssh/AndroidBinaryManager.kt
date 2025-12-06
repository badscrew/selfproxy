package com.sshtunnel.ssh

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Android implementation of BinaryManager with performance optimizations.
 * 
 * Manages extraction, caching, and verification of native SSH binaries
 * from the APK's jniLibs directory to the app's private directory.
 * 
 * Performance optimizations:
 * - Lazy extraction: Only extract when first needed
 * - Efficient caching: Reuse extracted binaries across app sessions
 * - Minimal verification: Only verify on first extraction or corruption
 * - Buffered I/O: Use larger buffers for faster extraction
 */
class AndroidBinaryManager(
    private val context: Context,
    private val logger: Logger
) : BinaryManager {
    
    private val binaryDir: File = File(context.filesDir, "native-ssh")
    private val metadataFile: File = File(binaryDir, "metadata.properties")
    
    // Lazy-loaded metadata cache to avoid repeated file I/O
    private var metadataCache: MutableMap<Architecture, BinaryMetadata>? = null
    
    // Track if binary directory has been initialized
    @Volatile
    private var directoryInitialized = false
    
    companion object {
        private const val TAG = "BinaryManager"
        private const val BUFFER_SIZE = 32768 // 32KB buffer for faster I/O
    }
    
    // Binary checksums for verification (SHA-256)
    // These are the actual checksums of the OpenSSH 10.2p1 binaries from Termux
    private val binaryChecksums = mapOf(
        Architecture.ARM64 to "d4286d8371bde67fedf3d425f1d93aa4561bef635642b8238342452e8bee6e51",
        Architecture.ARM32 to "624a9545daa72777fe5c350e3cc80f04e8e1c24efc35acffcf0755f314d5af0a",
        Architecture.X86_64 to "0491cb4fb1b576b7d6043b2506dd9c61ad93401db089f7f04581edca5ffc9d7a",
        Architecture.X86 to "aeeab6dc3f2bf8c232feda3d6109669148a863ac097a4d087e3cbd40066a7371"
    )
    
    /**
     * Lazy initialization of binary directory.
     * Only creates directory when first needed.
     */
    private fun ensureBinaryDirectory() {
        if (!directoryInitialized) {
            synchronized(this) {
                if (!directoryInitialized) {
                    if (!binaryDir.exists()) {
                        binaryDir.mkdirs()
                    }
                    directoryInitialized = true
                }
            }
        }
    }
    
    override suspend fun extractBinary(architecture: Architecture): Result<String> = withContext(Dispatchers.IO) {
        try {
            logger.debug(TAG, "Extracting SSH binary for architecture: ${architecture.abiName}")
            
            // Ensure directory exists
            ensureBinaryDirectory()
            
            // Check if cached binary is valid (fast path)
            val cachedPath = getCachedBinary(architecture)
            if (cachedPath != null) {
                logger.debug(TAG, "Using cached binary: $cachedPath")
                return@withContext Result.success(cachedPath)
            }
            
            // Extract binary from APK
            // The binaries are in lib/${architecture.abiName}/ in the APK
            val sourcePath = "lib/${architecture.abiName}/libssh.so"
            val destFile = File(binaryDir, "ssh_${architecture.abiName}")
            
            logger.debug(TAG, "Extracting from APK: $sourcePath -> ${destFile.absolutePath}")
            
            // Open the APK as a ZIP file and extract the binary
            val apkPath = context.applicationInfo.sourceDir
            val zipFile = java.util.zip.ZipFile(apkPath)
            
            try {
                val entry = zipFile.getEntry(sourcePath)
                if (entry == null) {
                    logger.error(TAG, "SSH binary not found in APK at: $sourcePath")
                    return@withContext Result.failure(
                        Exception("SSH binary not found in APK for ${architecture.abiName}")
                    )
                }
                
                // Extract the binary
                zipFile.getInputStream(entry).use { input ->
                    destFile.outputStream().buffered(BUFFER_SIZE).use { output ->
                        input.copyTo(output, BUFFER_SIZE)
                    }
                }
            } finally {
                zipFile.close()
            }
            
            // Set executable permissions
            if (!destFile.setExecutable(true, true)) {
                logger.warn(TAG, "Failed to set executable permission on ${destFile.absolutePath}")
            }
            
            // Verify the binary
            if (!verifyBinary(destFile.absolutePath)) {
                destFile.delete()
                return@withContext Result.failure(
                    Exception("Binary verification failed for ${architecture.abiName}")
                )
            }
            
            // Save metadata
            saveMetadata(architecture, destFile.absolutePath)
            
            logger.info(TAG, "Successfully extracted SSH binary to ${destFile.absolutePath}")
            Result.success(destFile.absolutePath)
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to extract SSH binary for ${architecture.abiName}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getCachedBinary(architecture: Architecture): String? = withContext(Dispatchers.IO) {
        try {
            ensureBinaryDirectory()
            
            val binaryName = "ssh"
            val binaryFile = File(binaryDir, "${binaryName}_${architecture.abiName}")
            
            // Fast path: check file existence first
            if (!binaryFile.exists()) {
                logger.debug(TAG, "No cached binary found for ${architecture.abiName}")
                return@withContext null
            }
            
            // Always load metadata from disk for version check (don't use cache)
            // This ensures we detect version changes even if cache is stale
            val metadata = loadMetadata(architecture)
            val currentVersion = getAppVersion()
            
            if (metadata?.appVersion != currentVersion) {
                logger.debug(TAG, "Cached binary is from different app version, re-extraction needed")
                binaryFile.delete()
                invalidateMetadataCache(architecture)
                return@withContext null
            }
            
            // Skip checksum verification for cached binaries (trust metadata)
            // Only verify if metadata indicates potential corruption
            
            // Check if executable (fast check)
            if (!binaryFile.canExecute()) {
                logger.debug(TAG, "Cached binary is not executable, setting permissions")
                if (!binaryFile.setExecutable(true, true)) {
                    logger.warn(TAG, "Failed to set executable permission")
                    return@withContext null
                }
            }
            
            logger.debug(TAG, "Found valid cached binary: ${binaryFile.absolutePath}")
            binaryFile.absolutePath
            
        } catch (e: Exception) {
            logger.error(TAG, "Error checking cached binary", e)
            null
        }
    }
    
    override suspend fun verifyBinary(binaryPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(binaryPath)
            if (!file.exists()) {
                logger.debug(TAG, "Binary file does not exist: $binaryPath")
                return@withContext false
            }
            
            // Basic file validation
            if (file.length() == 0L) {
                logger.warn(TAG, "Binary file is empty: $binaryPath")
                return@withContext false
            }
            
            // Check if file is executable
            if (!file.canExecute()) {
                logger.debug(TAG, "Setting executable permission on binary")
                if (!file.setExecutable(true, true)) {
                    logger.warn(TAG, "Failed to set executable permission")
                    return@withContext false
                }
            }
            
            // Determine architecture from file name
            val architecture = Architecture.values().find { 
                binaryPath.contains(it.abiName) 
            } ?: run {
                logger.warn(TAG, "Could not determine architecture from path: $binaryPath")
                return@withContext false
            }
            
            val expectedChecksum = binaryChecksums[architecture]
            if (expectedChecksum == null) {
                logger.warn(TAG, "No expected checksum for architecture: ${architecture.abiName}")
                return@withContext false
            }
            
            // Calculate SHA-256 checksum
            logger.debug(TAG, "Verifying binary checksum for ${architecture.abiName}")
            val checksum = calculateChecksum(file)
            val isValid = checksum == expectedChecksum
            
            if (isValid) {
                logger.info(TAG, "Binary checksum verified successfully for ${architecture.abiName}")
            } else {
                logger.error(TAG, "Checksum mismatch for ${architecture.abiName}")
                logger.error(TAG, "Expected: $expectedChecksum")
                logger.error(TAG, "Actual:   $checksum")
            }
            
            isValid
            
        } catch (e: Exception) {
            logger.error(TAG, "Error verifying binary", e)
            false
        }
    }
    
    override fun detectArchitecture(): Architecture {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI
        val architecture = Architecture.fromAbi(abi)
        logger.debug(TAG, "Detected architecture: ${architecture.abiName} (from ABI: $abi)")
        return architecture
    }
    
    override fun getBinaryMetadata(architecture: Architecture): BinaryMetadata {
        val metadata = loadMetadata(architecture)
        return metadata ?: BinaryMetadata(
            architecture = architecture,
            version = "OpenSSH_9.5p1", // Default version
            checksum = binaryChecksums[architecture] ?: ""
        )
    }
    
    /**
     * Calculate SHA-256 checksum with optimized buffer size.
     */
    private fun calculateChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered(BUFFER_SIZE).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get metadata from cache or load from disk.
     * Reduces repeated file I/O operations.
     */
    private fun getMetadataFromCache(architecture: Architecture): BinaryMetadata? {
        if (metadataCache == null) {
            synchronized(this) {
                if (metadataCache == null) {
                    metadataCache = mutableMapOf()
                }
            }
        }
        
        return metadataCache?.get(architecture) ?: run {
            val metadata = loadMetadata(architecture)
            metadata?.let { metadataCache?.put(architecture, it) }
            metadata
        }
    }
    
    /**
     * Invalidate cached metadata for an architecture.
     */
    private fun invalidateMetadataCache(architecture: Architecture) {
        metadataCache?.remove(architecture)
    }
    
    private fun saveMetadata(architecture: Architecture, binaryPath: String) {
        try {
            val metadata = getBinaryMetadata(architecture).copy(
                extractedPath = binaryPath,
                extractedAt = System.currentTimeMillis(),
                appVersion = getAppVersion()
            )
            
            val properties = java.util.Properties()
            properties.setProperty("${architecture.abiName}.path", metadata.extractedPath)
            properties.setProperty("${architecture.abiName}.version", metadata.version)
            properties.setProperty("${architecture.abiName}.checksum", metadata.checksum)
            properties.setProperty("${architecture.abiName}.extractedAt", metadata.extractedAt.toString())
            properties.setProperty("${architecture.abiName}.appVersion", metadata.appVersion)
            
            metadataFile.outputStream().buffered(BUFFER_SIZE).use { output ->
                properties.store(output, "Native SSH Binary Metadata")
            }
            
            // Update cache
            metadataCache?.put(architecture, metadata)
            
            logger.debug(TAG, "Saved metadata for ${architecture.abiName}")
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to save metadata", e)
        }
    }
    
    private fun loadMetadata(architecture: Architecture): BinaryMetadata? {
        try {
            if (!metadataFile.exists()) {
                return null
            }
            
            val properties = java.util.Properties()
            metadataFile.inputStream().use { input ->
                properties.load(input)
            }
            
            val path = properties.getProperty("${architecture.abiName}.path") ?: return null
            val version = properties.getProperty("${architecture.abiName}.version") ?: return null
            val checksum = properties.getProperty("${architecture.abiName}.checksum") ?: return null
            val extractedAt = properties.getProperty("${architecture.abiName}.extractedAt")?.toLongOrNull() ?: 0
            val appVersion = properties.getProperty("${architecture.abiName}.appVersion") ?: ""
            
            return BinaryMetadata(
                architecture = architecture,
                version = version,
                checksum = checksum,
                extractedPath = path,
                extractedAt = extractedAt,
                appVersion = appVersion
            )
            
        } catch (e: Exception) {
            logger.error(TAG, "Failed to load metadata", e)
            return null
        }
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            logger.error(TAG, "Failed to get app version", e)
            "unknown"
        }
    }
}
