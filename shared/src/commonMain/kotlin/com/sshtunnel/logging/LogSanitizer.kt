package com.sshtunnel.logging

/**
 * Sanitizes log messages to remove sensitive data.
 * 
 * Removes or masks:
 * - Passwords
 * - Private keys
 * - Passphrases
 * - Authentication tokens
 * - IP addresses (optional)
 * - Hostnames (optional)
 */
object LogSanitizer {
    private val SENSITIVE_PATTERNS = listOf(
        // Password patterns
        Regex("password[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
        Regex("pwd[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
        Regex("pass[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
        
        // Key patterns
        Regex("key[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
        Regex("privatekey[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
        Regex("private_key[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
        
        // Passphrase patterns
        Regex("passphrase[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
        
        // Token patterns
        Regex("token[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
        Regex("auth[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
        
        // SSH key content (BEGIN/END blocks)
        Regex("-----BEGIN [A-Z ]+-----[\\s\\S]*?-----END [A-Z ]+-----"),
        
        // Base64 encoded keys (long strings that look like keys)
        Regex("[A-Za-z0-9+/]{64,}={0,2}")
    )
    
    private const val MASK = "***"
    
    /**
     * Sanitizes a log message by removing or masking sensitive data.
     * 
     * @param message The original log message
     * @return Sanitized message with sensitive data removed
     */
    fun sanitize(message: String): String {
        var sanitized = message
        
        // Replace all sensitive patterns with mask
        SENSITIVE_PATTERNS.forEach { pattern ->
            sanitized = pattern.replace(sanitized) { matchResult ->
                val key = matchResult.value.substringBefore('=', matchResult.value.substringBefore(':'))
                "$key=$MASK"
            }
        }
        
        return sanitized
    }
    
    /**
     * Sanitizes a throwable's message and stack trace.
     * 
     * @param throwable The throwable to sanitize
     * @return Sanitized error message
     */
    fun sanitizeThrowable(throwable: Throwable): String {
        val message = throwable.message ?: throwable.toString()
        val sanitizedMessage = sanitize(message)
        
        // Include exception type but sanitize the message
        return "${throwable::class.simpleName}: $sanitizedMessage"
    }
    
    /**
     * Sanitizes connection details for logging.
     * 
     * @param hostname Server hostname
     * @param port Server port
     * @param username Username
     * @return Sanitized connection string
     */
    fun sanitizeConnectionDetails(hostname: String, port: Int, username: String): String {
        // Mask the hostname but keep the domain
        val maskedHostname = if (hostname.contains('.')) {
            val parts = hostname.split('.')
            if (parts.size > 2) {
                "${parts.first().take(3)}***@${parts.takeLast(2).joinToString(".")}"
            } else {
                "${parts.first().take(3)}***@${parts.last()}"
            }
        } else {
            "${hostname.take(3)}***"
        }
        
        return "$username@$maskedHostname:$port"
    }
}
