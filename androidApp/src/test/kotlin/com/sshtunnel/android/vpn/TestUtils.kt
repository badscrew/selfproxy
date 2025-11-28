package com.sshtunnel.android.vpn

import com.sshtunnel.logging.LogEntry
import com.sshtunnel.logging.LogLevel
import com.sshtunnel.logging.Logger

/**
 * Test implementation of Logger for testing purposes.
 * 
 * This logger stores all log entries in memory for verification in tests.
 * It does not use Android's Log class, making it suitable for unit tests.
 */
class TestLogger : Logger {
    private val logs = mutableListOf<LogEntry>()
    private var verboseEnabled = false
    
    override fun verbose(tag: String, message: String, throwable: Throwable?) {
        if (verboseEnabled) {
            logs.add(LogEntry(System.currentTimeMillis(), LogLevel.VERBOSE, tag, message, throwable))
        }
    }
    
    override fun debug(tag: String, message: String, throwable: Throwable?) {
        logs.add(LogEntry(System.currentTimeMillis(), LogLevel.DEBUG, tag, message, throwable))
    }
    
    override fun info(tag: String, message: String, throwable: Throwable?) {
        logs.add(LogEntry(System.currentTimeMillis(), LogLevel.INFO, tag, message, throwable))
    }
    
    override fun warn(tag: String, message: String, throwable: Throwable?) {
        logs.add(LogEntry(System.currentTimeMillis(), LogLevel.WARN, tag, message, throwable))
    }
    
    override fun error(tag: String, message: String, throwable: Throwable?) {
        logs.add(LogEntry(System.currentTimeMillis(), LogLevel.ERROR, tag, message, throwable))
    }
    
    override fun getLogEntries(): List<LogEntry> = logs.toList()
    
    override fun clearLogs() {
        logs.clear()
    }
    
    override fun setVerboseEnabled(enabled: Boolean) {
        verboseEnabled = enabled
    }
    
    override fun isVerboseEnabled(): Boolean = verboseEnabled
}
