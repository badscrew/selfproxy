package com.sshtunnel.ssh

/**
 * Parses SSH process output to extract structured information.
 * 
 * Analyzes SSH debug output to identify:
 * - Connection events (connecting, connected, disconnected)
 * - Authentication events (key exchange, authentication success/failure)
 * - Error messages and their types
 * - Keep-alive messages
 * - Port forwarding status
 */
object SSHOutputParser {
    
    /**
     * Parse a line of SSH output and extract structured event information.
     * 
     * @param line Raw output line from SSH process
     * @return Parsed SSH event or null if line doesn't contain relevant information
     */
    fun parseLine(line: String): SSHEvent? {
        return when {
            // Connection events
            line.contains("Connecting to", ignoreCase = true) -> {
                SSHEvent.Connecting(extractHostPort(line))
            }
            line.contains("Connection established", ignoreCase = true) -> {
                SSHEvent.Connected
            }
            line.contains("Connection closed", ignoreCase = true) ||
            line.contains("Connection to .* closed", ignoreCase = true) -> {
                SSHEvent.Disconnected(extractReason(line))
            }
            
            // Authentication events
            line.contains("Authenticating", ignoreCase = true) -> {
                SSHEvent.Authenticating
            }
            line.contains("Authentication succeeded", ignoreCase = true) ||
            line.contains("Authenticated to", ignoreCase = true) -> {
                SSHEvent.AuthenticationSuccess
            }
            line.contains("Permission denied", ignoreCase = true) ||
            line.contains("Authentication failed", ignoreCase = true) -> {
                SSHEvent.AuthenticationFailure(extractAuthError(line))
            }
            
            // Key exchange
            line.contains("kex:", ignoreCase = true) ||
            line.contains("Key exchange", ignoreCase = true) -> {
                SSHEvent.KeyExchange(extractKeyExchangeInfo(line))
            }
            
            // Port forwarding
            line.contains("Local forwarding", ignoreCase = true) ||
            line.contains("dynamic forwarding", ignoreCase = true) -> {
                SSHEvent.PortForwardingEstablished(extractPort(line))
            }
            line.contains("forwarding failed", ignoreCase = true) -> {
                SSHEvent.PortForwardingFailed(extractError(line))
            }
            
            // Keep-alive
            line.contains("keep-alive", ignoreCase = true) ||
            line.contains("ServerAlive", ignoreCase = true) -> {
                SSHEvent.KeepAlive
            }
            
            // Errors
            line.contains("error:", ignoreCase = true) ||
            line.contains("fatal:", ignoreCase = true) -> {
                SSHEvent.Error(extractError(line))
            }
            line.contains("warning:", ignoreCase = true) -> {
                SSHEvent.Warning(extractWarning(line))
            }
            
            // Network errors
            line.contains("Connection refused", ignoreCase = true) -> {
                SSHEvent.Error("Connection refused - server may be down or port blocked")
            }
            line.contains("Connection timed out", ignoreCase = true) ||
            line.contains("Operation timed out", ignoreCase = true) -> {
                SSHEvent.Error("Connection timed out - check network connectivity")
            }
            line.contains("No route to host", ignoreCase = true) -> {
                SSHEvent.Error("No route to host - check network configuration")
            }
            line.contains("Network is unreachable", ignoreCase = true) -> {
                SSHEvent.Error("Network is unreachable - check internet connection")
            }
            
            // Host key verification
            line.contains("Host key verification failed", ignoreCase = true) -> {
                SSHEvent.Error("Host key verification failed - server identity changed")
            }
            line.contains("REMOTE HOST IDENTIFICATION HAS CHANGED", ignoreCase = true) -> {
                SSHEvent.Error("Remote host identification has changed - possible security issue")
            }
            
            else -> null
        }
    }
    
    /**
     * Extract host and port from connection message.
     */
    private fun extractHostPort(line: String): String {
        val regex = Regex("Connecting to ([^ ]+) port (\\d+)", RegexOption.IGNORE_CASE)
        val match = regex.find(line)
        return if (match != null) {
            "${match.groupValues[1]}:${match.groupValues[2]}"
        } else {
            "unknown"
        }
    }
    
    /**
     * Extract disconnection reason.
     */
    private fun extractReason(line: String): String {
        return when {
            line.contains("by remote host", ignoreCase = true) -> "closed by remote host"
            line.contains("by user", ignoreCase = true) -> "closed by user"
            else -> "connection closed"
        }
    }
    
    /**
     * Extract authentication error details.
     */
    private fun extractAuthError(line: String): String {
        return when {
            line.contains("publickey", ignoreCase = true) -> "public key authentication failed"
            line.contains("password", ignoreCase = true) -> "password authentication failed"
            line.contains("keyboard-interactive", ignoreCase = true) -> "interactive authentication failed"
            else -> "authentication failed"
        }
    }
    
    /**
     * Extract key exchange information.
     */
    private fun extractKeyExchangeInfo(line: String): String {
        val algorithms = listOf("curve25519", "ecdh", "diffie-hellman")
        algorithms.forEach { algo ->
            if (line.contains(algo, ignoreCase = true)) {
                return algo
            }
        }
        return "key exchange in progress"
    }
    
    /**
     * Extract port number from forwarding message.
     */
    private fun extractPort(line: String): Int {
        val regex = Regex("port (\\d+)")
        val match = regex.find(line)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
    
    /**
     * Extract error message.
     */
    private fun extractError(line: String): String {
        // Remove common prefixes
        var error = line
            .replace(Regex("^.*error:", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^.*fatal:", RegexOption.IGNORE_CASE), "")
            .trim()
        
        // If empty, return the whole line
        if (error.isEmpty()) {
            error = line
        }
        
        return error
    }
    
    /**
     * Extract warning message.
     */
    private fun extractWarning(line: String): String {
        return line
            .replace(Regex("^.*warning:", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { line }
    }
    
    /**
     * Categorize error severity based on message content.
     */
    fun categorizeError(error: String): ErrorSeverity {
        return when {
            error.contains("fatal", ignoreCase = true) -> ErrorSeverity.FATAL
            error.contains("refused", ignoreCase = true) -> ErrorSeverity.CRITICAL
            error.contains("timed out", ignoreCase = true) -> ErrorSeverity.CRITICAL
            error.contains("unreachable", ignoreCase = true) -> ErrorSeverity.CRITICAL
            error.contains("authentication", ignoreCase = true) -> ErrorSeverity.CRITICAL
            error.contains("permission denied", ignoreCase = true) -> ErrorSeverity.CRITICAL
            error.contains("host key", ignoreCase = true) -> ErrorSeverity.CRITICAL
            error.contains("warning", ignoreCase = true) -> ErrorSeverity.WARNING
            else -> ErrorSeverity.ERROR
        }
    }
}

/**
 * Represents a parsed SSH event from process output.
 */
sealed class SSHEvent {
    // Connection events
    data class Connecting(val target: String) : SSHEvent()
    object Connected : SSHEvent()
    data class Disconnected(val reason: String) : SSHEvent()
    
    // Authentication events
    object Authenticating : SSHEvent()
    object AuthenticationSuccess : SSHEvent()
    data class AuthenticationFailure(val reason: String) : SSHEvent()
    
    // Key exchange
    data class KeyExchange(val algorithm: String) : SSHEvent()
    
    // Port forwarding
    data class PortForwardingEstablished(val port: Int) : SSHEvent()
    data class PortForwardingFailed(val reason: String) : SSHEvent()
    
    // Keep-alive
    object KeepAlive : SSHEvent()
    
    // Errors and warnings
    data class Error(val message: String) : SSHEvent()
    data class Warning(val message: String) : SSHEvent()
}

/**
 * Error severity levels for categorization.
 */
enum class ErrorSeverity {
    WARNING,    // Non-critical issues
    ERROR,      // Standard errors
    CRITICAL,   // Critical errors that prevent connection
    FATAL       // Fatal errors that require immediate attention
}
