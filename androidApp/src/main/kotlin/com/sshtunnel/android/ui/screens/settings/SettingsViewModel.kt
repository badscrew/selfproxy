package com.sshtunnel.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshtunnel.android.data.SettingsRepository
import com.sshtunnel.data.ConnectionSettings
import com.sshtunnel.data.DnsMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    /**
     * Current connection settings.
     */
    val settings: StateFlow<ConnectionSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionSettings()
        )
    
    /**
     * Update SSH port.
     */
    fun updateSshPort(port: Int) {
        viewModelScope.launch {
            settingsRepository.updateSshPort(port)
        }
    }
    
    /**
     * Update connection timeout.
     */
    fun updateConnectionTimeout(timeoutSeconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateConnectionTimeout(timeoutSeconds)
        }
    }
    
    /**
     * Update keep-alive interval.
     */
    fun updateKeepAliveInterval(intervalSeconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateKeepAliveInterval(intervalSeconds)
        }
    }
    
    /**
     * Update compression setting.
     */
    fun updateEnableCompression(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateEnableCompression(enabled)
        }
    }
    
    /**
     * Update custom SOCKS5 port.
     */
    fun updateCustomSocksPort(port: Int?) {
        viewModelScope.launch {
            settingsRepository.updateCustomSocksPort(port)
        }
    }
    
    /**
     * Update strict host key checking.
     */
    fun updateStrictHostKeyChecking(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateStrictHostKeyChecking(enabled)
        }
    }
    
    /**
     * Update DNS mode.
     */
    fun updateDnsMode(mode: DnsMode) {
        viewModelScope.launch {
            settingsRepository.updateDnsMode(mode)
        }
    }
    
    /**
     * Reset all settings to defaults.
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
        }
    }
}
