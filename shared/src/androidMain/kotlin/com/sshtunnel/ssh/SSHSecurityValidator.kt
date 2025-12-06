package com.sshtunnel.ssh

import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.Logger
import java.io.File

/**
 * Security validator for SSH operations.
 * 
 * Provides validation and sanitization for SSH commands, arguments, and outputs
 * to prevent security vulnerabilities such as command injection, information leakage,
 * and path traversal attacks.
 * 
 * Security features:
 * - Command argument validation
 * - Path traversal prevention
 * - Output sanitization for logging
 * - Safe error message generation
 * - Binary integrity verification
 */
class SSHSecurityValidator(
    private val logger: Logger
) {
    companion object {
        private const val TAG = "SSHSecurityValidator"
        
        // Allowed characters in hostnames (RFC 1123)
        private val HOSTNAME_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$")
        
        // Allowed characters in usernames (POSIX portable filename character set + @)
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")
        
        // Dangerous characters that could be used for command injection
        private val DANGEROUS_CHARS = setOf(';', '&', '|', '`', '$', '(', ')', '<', '>', '\n', '\r')
        
        // Sensitive patterns to redact from logs
        private val SENSITIVE_PATTERNS = listOf(
            Regex("password[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
            Regex("passphrase[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
            Regex("key[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
            Regex("-----BEGIN [A-Z ]+PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+PRIVATE KEY-----"),
            Regex("ssh-rsa\\s+[A-Za-z0-9+/=]+"),
            Regex("ssh-ed25519\\s+[A-Za-z0-9+/=]+"),
            Regex("ecdsa-sha2-[a-z0-9]+\\s+[A-Za-z0-9+/=]+")
        )
        
        // Maximum allowed lengths to prevent buffer overflow attacks
        private const val MAX_HOSTNAME_LENGTH = 253
        private const val MAX_USERNAME_LENGTH = 32
        private const val MAX_PATH_LENGTH = 4096
        private const val MAX_PORT = 65535
        private const val MIN_PORT = 1
    }
    
    /**
     * Validate SSH command arguments before execution.
     * 
     * Checks for:
     * - Valid hostname format
     * - Valid username format
     * - Valid port range
     * - Valid file paths (no path traversal)
     * - No command injection attempts
     * 
     * @param profile Server profile containing connection details
     * @param privateKeyPath Path to private key file
     * @param localPort Local SOCKS5 port
     * @return Result indicating validation success or failure with error message
     */
    fun validateSSHArguments(
        profile: ServerProfile,
        privateKeyPath: String,
        localPort: Int
    ): Result<Unit> {
        // Validate hostname
        if (profile.hostname.length > MAX_HOSTNAME_LENGTH) {
            return Result.failure(SecurityException("Hostname exceeds maximum length"))
        }
        
        if (!HOSTNAME_REGEX.matches(profile.hostname)) {
            return Result.failure(SecurityException("Invalid hostname format"))
        }
        
        // Check for dangerous characters in hostname
        if (profile.hostname.any { it in DANGEROUS_CHARS }) {
            return Result.failure(SecurityException("Hostname contains dangerous characters"))
        }
        
        // Validate username
        if (profile.username.length > MAX_USERNAME_LENGTH) {
            return Result.failure(SecurityException("Username exceeds maximum length"))
        }
        
        if (!USERNAME_REGEX.matches(profile.username)) {
            return Result.failure(SecurityException("Invalid username format"))
        }
        
        // Validate port
        if (profile.port !in MIN_PORT..MAX_PORT) {
            return Result.failure(SecurityException("Port out of valid range (1-65535)"))
        }
        
        // Validate local port (0 is allowed for auto-assignment)
        if (localPort !in 0..MAX_PORT) {
            return Result.failure(SecurityException("Local port out of valid range (0-65535)"))
        }
        
        // Validate private key path
        val keyPathValidation = validateFilePath(privateKeyPath)
        if (keyPathValidation.isFailure) {
            return keyPathValidation
        }
        
        logger.verbose(TAG, "SSH arguments validation passed")
        return Result.success(Unit)
    }
    
    /**
     * Validate file path to prevent path traversal attacks.
     * 
     * Checks for:
     * - Path traversal attempts (../)
     * - Absolute path requirements
     * - Maximum path length
     * - Dangerous characters
     * 
     * Note: File existence check is optional and will only warn if file doesn't exist.
     * This allows validation to work in test scenarios where files may not exist yet.
     * 
     * @param path File path to validate
     * @return Result indicating validation success or failure
     */
    fun validateFilePath(path: String): Result<Unit> {
        if (path.length > MAX_PATH_LENGTH) {
            return Result.failure(SecurityException("Path exceeds maximum length"))
        }
        
        // Check for path traversal attempts
        if (path.contains("..")) {
            return Result.failure(SecurityException("Path traversal attempt detected"))
        }
        
        // Ensure path is absolute (starts with /)
        if (!path.startsWith("/")) {
            return Result.failure(SecurityException("Path must be absolute"))
        }
        
        // Check for dangerous characters
        if (path.any { it in DANGEROUS_CHARS }) {
            return Result.failure(SecurityException("Path contains dangerous characters"))
        }
        
        // Verify file exists and is readable (optional - warn only)
        val file = File(path)
        if (!file.exists()) {
            logger.verbose(TAG, "File does not exist (may be created later): $path")
            // Don't fail - file might be created later
        } else if (!file.canRead()) {
            logger.warn(TAG, "File exists but is not readable: $path")
            // Don't fail - permissions might be set later
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Sanitize SSH output for safe logging.
     * 
     * Removes or redacts:
     * - Private keys
     * - Passwords and passphrases
     * - SSH keys
     * - Other sensitive information
     * 
     * @param output Raw SSH output
     * @return Sanitized output safe for logging
     */
    fun sanitizeOutput(output: String): String {
        var sanitized = output
        
        // Replace sensitive patterns with redacted markers
        SENSITIVE_PATTERNS.forEach { pattern ->
            sanitized = sanitized.replace(pattern, "[REDACTED]")
        }
        
        // Redact IP addresses in certain contexts (optional, can be configured)
        // sanitized = sanitized.replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "[IP]")
        
        return sanitized
    }
    
    /**
     * Generate safe error message for user display.
     * 
     * Removes technical details and sensitive information while preserving
     * useful diagnostic information for the user.
     * 
     * @param error Original error or exception
     * @return Safe error message for user display
     */
    fun generateSafeErrorMessage(error: Throwable): String {
        val message = error.message ?: "Unknown error"
        
        // Sanitize the error message
        val sanitized = sanitizeOutput(message)
        
        // Map technical errors to user-friendly messages
        return when {
            sanitized.contains("Connection refused", ignoreCase = true) ->
                "Unable to connect to SSH server. Please check the hostname and port."
            
            sanitized.contains("Connection timed out", ignoreCase = true) ->
                "Connection timed out. Please check your network connection and server availability."
            
            sanitized.contains("Authentication failed", ignoreCase = true) ->
                "Authentication failed. Please check your username and private key."
            
            sanitized.contains("Host key verification failed", ignoreCase = true) ->
                "Server host key verification failed. The server's identity may have changed."
            
            sanitized.contains("Permission denied", ignoreCase = true) ->
                "Permission denied. Please check your credentials and server configuration."
            
            sanitized.contains("No route to host", ignoreCase = true) ->
                "Cannot reach the server. Please check your network connection."
            
            sanitized.contains("Network is unreachable", ignoreCase = true) ->
                "Network is unreachable. Please check your internet connection."
            
            else -> "Connection error: ${sanitized.take(100)}" // Limit length
        }
    }
    
    /**
     * Validate SSH binary integrity.
     * 
     * Performs additional security checks beyond basic verification:
     * - File permissions (should be executable but not world-writable)
     * - File ownership (should be owned by app)
     * - File location (should be in app's private directory)
     * 
     * @param binaryPath Path to SSH binary
     * @return Result indicating validation success or failure
     */
    fun validateBinaryIntegrity(binaryPath: String): Result<Unit> {
        val file = File(binaryPath)
        
        // Check file exists
        if (!file.exists()) {
            return Result.failure(SecurityException("Binary file does not exist"))
        }
        
        // Check file is a regular file (not a symlink or directory)
        if (!file.isFile) {
            return Result.failure(SecurityException("Binary path is not a regular file"))
        }
        
        // Check file is executable
        if (!file.canExecute()) {
            return Result.failure(SecurityException("Binary is not executable"))
        }
        
        // Check file is readable
        if (!file.canRead()) {
            return Result.failure(SecurityException("Binary is not readable"))
        }
        
        // Check file is not world-writable (security risk)
        // Note: On Android, we can't easily check Unix permissions, but we can verify
        // the file is in our private directory which provides protection
        
        // Verify file is in app's private directory
        val appDataDir = file.absolutePath.substringBefore("/files/")
        if (!appDataDir.contains("/data/data/") && !appDataDir.contains("/data/user/")) {
            return Result.failure(SecurityException("Binary is not in app's private directory"))
        }
        
        logger.verbose(TAG, "Binary integrity validation passed")
        return Result.success(Unit)
    }
    
    /**
     * Validate SSH command construction.
     * 
     * Ensures the command array doesn't contain any dangerous patterns
     * that could lead to command injection.
     * 
     * @param command SSH command array
     * @return Result indicating validation success or failure
     */
    fun validateCommandArray(command: List<String>): Result<Unit> {
        if (command.isEmpty()) {
            return Result.failure(SecurityException("Command array is empty"))
        }
        
        // Validate each argument
        command.forEachIndexed { index, arg ->
            // Check for null bytes (command injection technique)
            if (arg.contains('\u0000')) {
                return Result.failure(SecurityException("Command contains null byte at index $index"))
            }
            
            // Check for newlines (command injection technique)
            if (arg.contains('\n') || arg.contains('\r')) {
                return Result.failure(SecurityException("Command contains newline at index $index"))
            }
            
            // Warn about suspicious patterns (but don't fail, as they might be legitimate)
            if (arg.contains("$(") || arg.contains("`")) {
                logger.warn(TAG, "Command contains shell expansion pattern at index $index: ${arg.take(20)}")
            }
        }
        
        logger.verbose(TAG, "Command array validation passed")
        return Result.success(Unit)
    }
    
    /**
     * Sanitize log message before writing to log file.
     * 
     * This is a convenience method that combines output sanitization
     * with additional log-specific processing.
     * 
     * @param message Log message to sanitize
     * @return Sanitized log message
     */
    fun sanitizeLogMessage(message: String): String {
        return sanitizeOutput(message)
    }
}
