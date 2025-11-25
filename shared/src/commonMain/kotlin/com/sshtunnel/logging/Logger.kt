package com.sshtunnel.logging

/**
 * Logging interface for the SSH Tunnel Proxy application.
 * 
 * Provides structured logging with different severity levels and automatic
 * sanitization of sensitive data.
 */
interface Logger {
    /**
     * Log a verbose message (only logged when verbose mode is enabled).
     */
    fun verbose(tag: String, message: String, throwable: Throwable? = null)
    
    /**
     * Log a debug message.
     */
    fun debug(tag: String, message: String, throwable: Throwable? = null)
    
    /**
     * Log an informational message.
     */
    fun info(tag: String, message: String, throwable: Throwable? = null)
    
    /**
     * Log a warning message.
     */
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    
    /**
     * Log an error message.
     */
    fun error(tag: String, message: String, throwable: Throwable? = null)
    
    /**
     * Get all logged messages for export.
     * 
     * @return List of log entries
     */
    fun getLogEntries(): List<LogEntry>
    
    /**
     * Clear all logged messages.
     */
    fun clearLogs()
    
    /**
     * Enable or disable verbose logging.
     */
    fun setVerboseEnabled(enabled: Boolean)
    
    /**
     * Check if verbose logging is enabled.
     */
    fun isVerboseEnabled(): Boolean
}

/**
 * Represents a single log entry.
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

/**
 * Log severity levels.
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}
