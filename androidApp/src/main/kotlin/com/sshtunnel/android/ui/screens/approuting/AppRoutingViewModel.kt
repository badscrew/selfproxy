package com.sshtunnel.android.ui.screens.approuting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshtunnel.data.AppRoutingConfig
import com.sshtunnel.data.RoutingMode
import com.sshtunnel.repository.AppRoutingRepository
import com.sshtunnel.vpn.AndroidVpnTunnelProvider
import com.sshtunnel.vpn.InstalledAppsProvider
import com.sshtunnel.vpn.RoutingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the App Routing screen.
 * 
 * Manages the state of app routing configuration, including:
 * - Loading installed apps
 * - Managing excluded/included apps
 * - Switching routing modes
 * - Applying routing changes dynamically
 */
@HiltViewModel
class AppRoutingViewModel @Inject constructor(
    application: Application,
    private val appRoutingRepository: AppRoutingRepository,
    private val vpnTunnelProvider: AndroidVpnTunnelProvider
) : AndroidViewModel(application) {
    
    private val installedAppsProvider = InstalledAppsProvider(application)
    
    private val _uiState = MutableStateFlow(AppRoutingUiState())
    val uiState: StateFlow<AppRoutingUiState> = _uiState.asStateFlow()
    
    /**
     * Loads the routing configuration and installed apps for a profile.
     */
    fun loadRoutingConfig(profileId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Load routing configuration
                val config = appRoutingRepository.getRoutingConfig(profileId)
                    ?: AppRoutingConfig(
                        profileId = profileId,
                        excludedPackages = emptySet(),
                        routingMode = RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
                    )
                
                // Load installed apps
                val includeSystemApps = _uiState.value.showSystemApps
                val apps = installedAppsProvider.getInstalledApps(includeSystemApps)
                
                _uiState.update {
                    it.copy(
                        profileId = profileId,
                        routingMode = config.routingMode,
                        selectedPackages = config.excludedPackages,
                        installedApps = apps,
                        filteredApps = apps,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load routing configuration: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Toggles the selection of an app.
     */
    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedPackages.contains(packageName)) {
                state.selectedPackages - packageName
            } else {
                state.selectedPackages + packageName
            }
            
            state.copy(
                selectedPackages = newSelection,
                hasUnsavedChanges = true
            )
        }
    }
    
    /**
     * Switches the routing mode.
     */
    fun setRoutingMode(mode: RoutingMode) {
        _uiState.update {
            it.copy(
                routingMode = mode,
                hasUnsavedChanges = true
            )
        }
    }
    
    /**
     * Filters the app list based on search query.
     */
    fun filterApps(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.installedApps
            } else {
                state.installedApps.filter { app ->
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
                }
            }
            
            state.copy(
                searchQuery = query,
                filteredApps = filtered
            )
        }
    }
    
    /**
     * Toggles the display of system apps.
     */
    fun toggleShowSystemApps() {
        viewModelScope.launch {
            val newValue = !_uiState.value.showSystemApps
            _uiState.update { it.copy(showSystemApps = newValue, isLoading = true) }
            
            try {
                val apps = installedAppsProvider.getInstalledApps(newValue)
                
                _uiState.update { state ->
                    val filtered = if (state.searchQuery.isBlank()) {
                        apps
                    } else {
                        apps.filter { app ->
                            app.appName.contains(state.searchQuery, ignoreCase = true) ||
                            app.packageName.contains(state.searchQuery, ignoreCase = true)
                        }
                    }
                    
                    state.copy(
                        installedApps = apps,
                        filteredApps = filtered,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load apps: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Saves the routing configuration and applies it dynamically if VPN is active.
     */
    fun saveRoutingConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            
            try {
                val state = _uiState.value
                val config = AppRoutingConfig(
                    profileId = state.profileId,
                    excludedPackages = state.selectedPackages,
                    routingMode = state.routingMode
                )
                
                // Save to repository
                appRoutingRepository.saveRoutingConfig(config).getOrThrow()
                
                // Apply dynamically if VPN is active
                val vpnState = vpnTunnelProvider.getCurrentState()
                if (vpnState is com.sshtunnel.vpn.TunnelState.Active) {
                    val routingConfig = RoutingConfig(
                        excludedApps = state.selectedPackages,
                        routingMode = state.routingMode.toVpnRoutingMode()
                    )
                    vpnTunnelProvider.updateRouting(routingConfig).getOrThrow()
                }
                
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasUnsavedChanges = false,
                        showSaveSuccess = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to save routing configuration: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Dismisses the save success message.
     */
    fun dismissSaveSuccess() {
        _uiState.update { it.copy(showSaveSuccess = false) }
    }
    
    /**
     * Clears the error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

/**
 * UI state for the App Routing screen.
 */
data class AppRoutingUiState(
    val profileId: Long = -1L,
    val routingMode: RoutingMode = RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED,
    val selectedPackages: Set<String> = emptySet(),
    val installedApps: List<InstalledAppsProvider.InstalledApp> = emptyList(),
    val filteredApps: List<InstalledAppsProvider.InstalledApp> = emptyList(),
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val showSaveSuccess: Boolean = false,
    val error: String? = null
)

/**
 * Converts data layer RoutingMode to VPN layer RoutingMode.
 */
private fun RoutingMode.toVpnRoutingMode(): com.sshtunnel.vpn.RoutingMode {
    return when (this) {
        RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED -> com.sshtunnel.vpn.RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
        RoutingMode.ROUTE_ONLY_INCLUDED -> com.sshtunnel.vpn.RoutingMode.ROUTE_ONLY_INCLUDED
    }
}
