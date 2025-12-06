package com.sshtunnel.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sshtunnel.data.ConnectionSettings
import com.sshtunnel.data.DnsMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Repository for persisting connection settings using DataStore.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    
    companion object {
        private val CONNECTION_TIMEOUT = intPreferencesKey("connection_timeout")
        private val KEEP_ALIVE_INTERVAL = intPreferencesKey("keep_alive_interval")
        private val CUSTOM_SOCKS_PORT = intPreferencesKey("custom_socks_port")
        private val DNS_MODE = stringPreferencesKey("dns_mode")
        private val VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
    }
    
    /**
     * Observe connection settings as a Flow.
     */
    val settingsFlow: Flow<ConnectionSettings> = context.dataStore.data.map { preferences ->
        ConnectionSettings(
            connectionTimeout = (preferences[CONNECTION_TIMEOUT] ?: 30).seconds,
            keepAliveInterval = (preferences[KEEP_ALIVE_INTERVAL] ?: 60).seconds,
            customSocksPort = preferences[CUSTOM_SOCKS_PORT],
            dnsMode = preferences[DNS_MODE]?.let { 
                try {
                    DnsMode.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    DnsMode.THROUGH_TUNNEL
                }
            } ?: DnsMode.THROUGH_TUNNEL,
            verboseLogging = preferences[VERBOSE_LOGGING] ?: false
        )
    }
    
    /**
     * Update connection timeout in seconds.
     */
    suspend fun updateConnectionTimeout(timeoutSeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[CONNECTION_TIMEOUT] = timeoutSeconds
        }
    }
    
    /**
     * Update keep-alive interval in seconds.
     */
    suspend fun updateKeepAliveInterval(intervalSeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEEP_ALIVE_INTERVAL] = intervalSeconds
        }
    }
    
    /**
     * Update custom SOCKS5 port.
     */
    suspend fun updateCustomSocksPort(port: Int?) {
        context.dataStore.edit { preferences ->
            if (port != null) {
                preferences[CUSTOM_SOCKS_PORT] = port
            } else {
                preferences.remove(CUSTOM_SOCKS_PORT)
            }
        }
    }
    
    /**
     * Update DNS mode.
     */
    suspend fun updateDnsMode(mode: DnsMode) {
        context.dataStore.edit { preferences ->
            preferences[DNS_MODE] = mode.name
        }
    }
    
    /**
     * Update verbose logging.
     */
    suspend fun updateVerboseLogging(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VERBOSE_LOGGING] = enabled
        }
    }
    
    /**
     * Reset all settings to defaults.
     */
    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
