package com.sshtunnel.logging

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Exports logs to various formats for troubleshooting.
 */
object LogExporter {
    /**
     * Exports logs as plain text.
     * 
     * @param entries Log entries to export
     * @return Formatted log text
     */
    fun exportAsText(entries: List<LogEntry>): String {
        val builder = StringBuilder()
        builder.appendLine("SSH Tunnel Proxy - Diagnostic Logs")
        builder.appendLine("Generated: ${formatTimestamp(System.currentTimeMillis())}")
        builder.appendLine("Total Entries: ${entries.size}")
        builder.appendLine("=" .repeat(80))
        builder.appendLine()
        
        entries.forEach { entry ->
            builder.appendLine(formatLogEntry(entry))
        }
        
        return builder.toString()
    }
    
    /**
     * Exports logs as JSON.
     * 
     * @param entries Log entries to export
     * @return JSON formatted logs
     */
    fun exportAsJson(entries: List<LogEntry>): String {
        val builder = StringBuilder()
        builder.appendLine("{")
        builder.appendLine("  \"generated\": \"${formatTimestamp(System.currentTimeMillis())}\",")
        builder.appendLine("  \"totalEntries\": ${entries.size},")
        builder.appendLine("  \"logs\": [")
        
        entries.forEachIndexed { index, entry ->
            builder.append("    ")
            builder.append(formatLogEntryAsJson(entry))
            if (index < entries.size - 1) {
                builder.appendLine(",")
            } else {
                builder.appendLine()
            }
        }
        
        builder.appendLine("  ]")
        builder.appendLine("}")
        
        return builder.toString()
    }
    
    /**
     * Formats a single log entry as text.
     */
    private fun formatLogEntry(entry: LogEntry): String {
        val timestamp = formatTimestamp(entry.timestamp)
        val level = entry.level.name.padEnd(7)
        val tag = entry.tag.padEnd(20)
        
        val builder = StringBuilder()
        builder.append("[$timestamp] $level $tag: ${entry.message}")
        
        if (entry.throwable != null) {
            builder.appendLine()
            builder.append("  Error: ${LogSanitizer.sanitizeThrowable(entry.throwable)}")
        }
        
        return builder.toString()
    }
    
    /**
     * Formats a single log entry as JSON.
     */
    private fun formatLogEntryAsJson(entry: LogEntry): String {
        val builder = StringBuilder()
        builder.append("{")
        builder.append("\"timestamp\": \"${formatTimestamp(entry.timestamp)}\", ")
        builder.append("\"level\": \"${entry.level.name}\", ")
        builder.append("\"tag\": \"${escapeJson(entry.tag)}\", ")
        builder.append("\"message\": \"${escapeJson(entry.message)}\"")
        
        if (entry.throwable != null) {
            builder.append(", \"error\": \"${escapeJson(LogSanitizer.sanitizeThrowable(entry.throwable))}\"")
        }
        
        builder.append("}")
        return builder.toString()
    }
    
    /**
     * Formats a timestamp as ISO 8601 string.
     */
    private fun formatTimestamp(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dateTime.year}-${dateTime.monthNumber.toString().padStart(2, '0')}-${dateTime.dayOfMonth.toString().padStart(2, '0')} " +
               "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}:${dateTime.second.toString().padStart(2, '0')}"
    }
    
    /**
     * Escapes special characters for JSON.
     */
    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
