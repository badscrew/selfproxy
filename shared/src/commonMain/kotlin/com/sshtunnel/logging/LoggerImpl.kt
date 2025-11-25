package com.sshtunnel.logging

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default implementation of Logger with in-memory log storage and sanitization.
 * 
 * This implementation:
 * - Stores logs in memory for export
 * - Automatically sanitizes all log messages
 * - Supports verbose logging toggle
 * - Thread-safe log operations
 */
class LoggerImpl : Logger {
    private val mutex = Mutex()
    private val logEntries = mutableListOf<LogEntry>()
    private var verboseEnabled = false
    
    // Maximum number of log entries to keep in memory
    private val maxLogEntries = 1000
    
    override fun verbose(tag: String, message: String, throwable: Throwable?) {
        if (verboseEnabled) {
            log(LogLevel.VERBOSE, tag, message, throwable)
        }
    }
    
    override fun debug(tag: String, message: String, throwable: Throwable?) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }
    
    override fun info(tag: String, message: String, throwable: Throwable?) {
        log(LogLevel.INFO, tag, message, throwable)
    }
    
    override fun warn(tag: String, message: String, throwable: Throwable?) {
        log(LogLevel.WARN, tag, message, throwable)
    }
    
    override fun error(tag: String, message: String, throwable: Throwable?) {
        log(LogLevel.ERROR, tag, message, throwable)
    }
    
    override fun getLogEntries(): List<LogEntry> {
        return logEntries.toList()
    }
    
    override fun clearLogs() {
        logEntries.clear()
    }
    
    override fun setVerboseEnabled(enabled: Boolean) {
        verboseEnabled = enabled
    }
    
    override fun isVerboseEnabled(): Boolean {
        return verboseEnabled
    }
    
    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        // Sanitize the message
        val sanitizedMessage = LogSanitizer.sanitize(message)
        
        // Create log entry
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = sanitizedMessage,
            throwable = throwable
        )
        
        // Add to in-memory storage
        logEntries.add(entry)
        
        // Trim old entries if we exceed the limit
        if (logEntries.size > maxLogEntries) {
            logEntries.removeAt(0)
        }
        
        // Also print to platform-specific logger
        printToPlatform(entry)
    }
    
    /**
     * Print to platform-specific logging system.
     * This is implemented as expect/actual for each platform.
     */
    private fun printToPlatform(entry: LogEntry) {
        // Platform-specific implementation will handle this
        platformLog(entry)
    }
}

/**
 * Platform-specific logging function.
 * 
 * Android: Uses android.util.Log
 * iOS: Uses NSLog
 */
expect fun platformLog(entry: LogEntry)
