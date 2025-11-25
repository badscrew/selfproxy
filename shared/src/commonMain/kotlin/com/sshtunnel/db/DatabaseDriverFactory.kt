package com.sshtunnel.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Factory interface for creating platform-specific SQLDelight database drivers.
 * 
 * Each platform (Android, iOS) must provide its own implementation of this interface
 * to create the appropriate database driver for that platform.
 */
expect class DatabaseDriverFactory {
    /**
     * Creates a platform-specific SQLDelight database driver.
     * 
     * @return SqlDriver instance for the current platform
     */
    fun createDriver(): SqlDriver
}
