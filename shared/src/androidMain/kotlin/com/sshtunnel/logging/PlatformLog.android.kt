package com.sshtunnel.logging

import android.util.Log

/**
 * Android implementation of platform logging.
 * Uses android.util.Log for output.
 */
actual fun platformLog(entry: LogEntry) {
    val message = if (entry.throwable != null) {
        "${entry.message}\n${LogSanitizer.sanitizeThrowable(entry.throwable)}"
    } else {
        entry.message
    }
    
    when (entry.level) {
        LogLevel.VERBOSE -> Log.v(entry.tag, message)
        LogLevel.DEBUG -> Log.d(entry.tag, message)
        LogLevel.INFO -> Log.i(entry.tag, message)
        LogLevel.WARN -> Log.w(entry.tag, message)
        LogLevel.ERROR -> Log.e(entry.tag, message)
    }
}
