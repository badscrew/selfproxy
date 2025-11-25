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
        private val SSH_PORT = intPreferencesKey("ssh_port")
        private val CONNECTION_TIMEOUT = intPreferencesKey("connection_timeout")
        private val KEEP_ALIVE_INTERVAL = intPreferencesKey("keep_alive_interval")
        private val ENABLE_COMPRESSION = booleanPreferencesKey("enable_compression")
        private val CUSTOM_SOCKS_PORT = intPreferencesKey("custom_socks_port")
        private val STRICT_HOST_KEY_CHECKING = booleanPreferencesKey("strict_host_key_checking")
        private val DNS_MODE = stringPreferencesKey("dns_mode")
        private val VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
    }
    
    /**
     * Observe connection settings as a Flow.
     */
    val settingsFlow: Flow<ConnectionSettings> = context.dataStore.data.map { preferences ->
        ConnectionSettings(
            sshPort = preferences[SSH_PORT] ?: 22,
            connectionTimeout = (preferences[CONNECTION_TIMEOUT] ?: 30).seconds,
            keepAliveInterval = (preferences[KEEP_ALIVE_INTERVAL] ?: 60).seconds,
            enableCompression = preferences[ENABLE_COMPRESSION] ?: false,
            customSocksPort = preferences[CUSTOM_SOCKS_PORT],
            strictHostKeyChecking = preferences[STRICT_HOST_KEY_CHECKING] ?: false,
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
     * Update SSH port.
     */
    suspend fun updateSshPort(port: Int) {
        context.dataStore.edit { preferences ->
            preferences[SSH_PORT] = port
        }
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
     * Update compression setting.
     */
    suspend fun updateEnableCompression(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_COMPRESSION] = enabled
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
     * Update strict host key checking.
     */
    suspend fun updateStrictHostKeyChecking(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[STRICT_HOST_KEY_CHECKING] = enabled
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
