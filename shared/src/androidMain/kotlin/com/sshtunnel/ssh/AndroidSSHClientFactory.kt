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
     * 1. If preferNative is true and native SSH is available, use native implementation
     * 2. Otherwise, use sshj implementation
     * 
     * @param logger Logger for diagnostic output
     * @param preferNative Whether to prefer native implementation (default: true)
     * @return SSH client instance (native or sshj)
     */
    override fun create(
        logger: Logger,
        preferNative: Boolean
    ): SSHClient {
        logger.info(TAG, "Creating SSH client (preferNative=$preferNative)")
        
        // Check if native SSH is available
        val nativeAvailable = isNativeSSHAvailable()
        logger.info(TAG, "Native SSH available: $nativeAvailable")
        
        return if (preferNative && nativeAvailable) {
            logger.info(TAG, "Using native OpenSSH implementation")
            createNativeClient(logger)
        } else {
            logger.info(TAG, "Using sshj implementation")
            createSshjClient(logger)
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
        val commandBuilder = AndroidSSHCommandBuilder()
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

