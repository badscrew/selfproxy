package com.sshtunnel.android.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshtunnel.android.data.SettingsRepository
import com.sshtunnel.battery.BatteryMonitor
import com.sshtunnel.battery.BatteryOptimizationManager
import com.sshtunnel.battery.BatteryState
import com.sshtunnel.data.ConnectionSettings
import com.sshtunnel.data.DnsMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen.
 * Includes battery optimization management.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {
    
    private val batteryOptimizationManager = BatteryOptimizationManager(application)
    private val batteryMonitor = BatteryMonitor(application)
    
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
     * Current battery state.
     */
    private val _batteryState = MutableStateFlow(batteryOptimizationManager.getBatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()
    
    /**
     * Whether the app is ignoring battery optimizations.
     */
    private val _isIgnoringBatteryOptimizations = MutableStateFlow(
        batteryOptimizationManager.isIgnoringBatteryOptimizations()
    )
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()
    
    init {
        monitorBatteryState()
    }
    
    private fun monitorBatteryState() {
        viewModelScope.launch {
            batteryMonitor.observeBatteryChanges().collect {
                // Update battery state when changes occur
                _batteryState.value = batteryOptimizationManager.getBatteryState()
                _isIgnoringBatteryOptimizations.value = 
                    batteryOptimizationManager.isIgnoringBatteryOptimizations()
            }
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
     * Update custom SOCKS5 port.
     */
    fun updateCustomSocksPort(port: Int?) {
        viewModelScope.launch {
            settingsRepository.updateCustomSocksPort(port)
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
     * Update verbose logging.
     */
    fun updateVerboseLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateVerboseLogging(enabled)
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
    
    /**
     * Get intent to request battery optimization exemption.
     */
    fun getBatteryOptimizationExemptionIntent() = 
        batteryOptimizationManager.createBatteryOptimizationExemptionIntent()
    
    /**
     * Refresh battery state.
     */
    fun refreshBatteryState() {
        _batteryState.value = batteryOptimizationManager.getBatteryState()
        _isIgnoringBatteryOptimizations.value = 
            batteryOptimizationManager.isIgnoringBatteryOptimizations()
    }
}
