package com.sshtunnel.ssh

import android.content.Context
import com.sshtunnel.logging.LogEntry
import com.sshtunnel.logging.Logger

/**
 * Android implementation of SSHClientFactory.
 * 
 * Creates SSH client instances based on availability and preference.
 * Prefers native OpenSSH implementation when available, falls back to sshj.
 */
class AndroidSSHClientFactory(
    private val context: Context
) : SSHClientFactory {
    
    companion object {
        private const val TAG = "AndroidSSHClientFactory"
    }
    
    private val binaryManager: BinaryManager by lazy {
        AndroidBinaryManager(context, createLogger())
    }
    
    /**
     * Create a simple logger for internal use.
     */
    private fun createLogger(): Logger {
        return object : Logger {
            override fun verbose(tag: String, message: String, throwable: Throwable?) {}
            override fun debug(tag: String, message: String, throwable: Throwable?) {}
            override fun info(tag: String, message: String, throwable: Throwable?) {}
            override fun warn(tag: String, message: String, throwable: Throwable?) {}
            override fun error(tag: String, message: String, throwable: Throwable?) {}
            override fun getLogEntries(): List<LogEntry> = emptyList()
            override fun clearLogs() {}
            override fun setVerboseEnabled(enabled: Boolean) {}
            override fun isVerboseEnabled(): Boolean = false
        }
    }
    
    /**
     * Create an SSH client instance.
     * 
     * Selection logic:
     * 1. If implementationType is NATIVE and native SSH is available, use native implementation
     * 2. If implementationType is SSHJ, use sshj implementation
     * 3. If implementationType is AUTO:
     *    - Use native if available
     *    - Fall back to sshj if native is not available
     * 
     * @param logger Logger for diagnostic output
     * @param implementationType SSH implementation type preference
     * @return SSH client instance (native or sshj)
     */
    override fun create(
        logger: Logger,
        implementationType: SSHImplementationType
    ): SSHClient {
        logger.info(TAG, "Creating SSH client (implementationType=$implementationType)")
        
        // Check if native SSH is available
        val nativeAvailable = isNativeSSHAvailable()
        logger.info(TAG, "Native SSH available: $nativeAvailable")
        
        return when (implementationType) {
            SSHImplementationType.NATIVE -> {
                if (nativeAvailable) {
                    logger.info(TAG, "Using native OpenSSH implementation (user preference)")
                    createNativeClient(logger)
                } else {
                    logger.warn(TAG, "Native SSH requested but not available, falling back to sshj")
                    createSshjClient(logger)
                }
            }
            SSHImplementationType.SSHJ -> {
                logger.info(TAG, "Using sshj implementation (user preference)")
                createSshjClient(logger)
            }
            SSHImplementationType.AUTO -> {
                if (nativeAvailable) {
                    logger.info(TAG, "Using native OpenSSH implementation (auto-selected)")
                    createNativeClient(logger)
                } else {
                    logger.info(TAG, "Using sshj implementation (auto-selected, native not available)")
                    createSshjClient(logger)
                }
            }
        }
    }
    
    /**
     * Check if native SSH is available.
     * 
     * Native SSH is available if:
     * 1. The device architecture is supported
     * 2. The SSH binary can be extracted or is already cached
     * 
     * @return true if native SSH binary is available for this device architecture
     */
    override fun isNativeSSHAvailable(): Boolean {
        return try {
            // Detect device architecture
            val architecture = binaryManager.detectArchitecture()
            
            // Check if binary exists in APK for this architecture
            // We check if the binary resource exists
            val binaryPath = "lib/$architecture/libssh.so"
            val inputStream = context.assets.open(binaryPath)
            inputStream.close()
            
            true
        } catch (e: Exception) {
            // Binary not found or error accessing it
            false
        }
    }
    
    /**
     * Create a native SSH client instance.
     */
    private fun createNativeClient(logger: Logger): SSHClient {
        val privateKeyManager = AndroidPrivateKeyManager(context, logger)
        val commandBuilder = AndroidSSHCommandBuilder(logger)
        val processManager = AndroidProcessManager(logger)
        val connectionMonitor = AndroidConnectionMonitor(processManager, logger)
        
        return AndroidNativeSSHClient(
            context = context,
            binaryManager = AndroidBinaryManager(context, logger),
            privateKeyManager = privateKeyManager,
            commandBuilder = commandBuilder,
            processManager = processManager,
            connectionMonitor = connectionMonitor,
            logger = logger
        )
    }
    
    /**
     * Create an sshj SSH client instance.
     */
    private fun createSshjClient(logger: Logger): SSHClient {
        return AndroidSSHClient(logger)
    }
}

