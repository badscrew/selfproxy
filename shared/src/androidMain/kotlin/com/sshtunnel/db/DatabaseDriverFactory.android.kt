package com.sshtunnel.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android implementation of DatabaseDriverFactory.
 * 
 * Creates an AndroidSqliteDriver for SQLDelight database access on Android.
 * 
 * @property context Android application context
 */
actual class DatabaseDriverFactory(private val context: Context) {
    /**
     * Creates an Android SQLite database driver.
     * 
     * @return AndroidSqliteDriver instance
     */
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = SSHTunnelDatabase.Schema,
            context = context,
            name = "sshtunnel.db"
        )
    }
}
