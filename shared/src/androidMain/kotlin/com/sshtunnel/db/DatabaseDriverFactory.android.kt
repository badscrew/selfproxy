package com.sshtunnel.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android implementation of DatabaseDriverFactory.
 * 
 * Creates an AndroidSqliteDriver for SQLDelight database access on Android.
 * Handles database schema migrations from SSH to Shadowsocks.
 * 
 * @property context Android application context
 */
actual class DatabaseDriverFactory(private val context: Context) {
    /**
     * Creates an Android SQLite database driver with migration support.
     * 
     * The migration from SSH schema to Shadowsocks schema is handled automatically
     * by SQLDelight. Existing SSH profiles will be dropped as they are incompatible
     * with Shadowsocks. Users will need to create new Shadowsocks profiles.
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
