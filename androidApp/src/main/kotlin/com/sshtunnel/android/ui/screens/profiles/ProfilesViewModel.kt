package com.sshtunnel.android.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.repository.ProfileRepository
import com.sshtunnel.storage.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing SSH server profiles.
 * 
 * Handles profile CRUD operations and maintains UI state for the profiles screen.
 */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val credentialStore: CredentialStore
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ProfilesUiState>(ProfilesUiState.Loading)
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()
    
    private val _showProfileDialog = MutableStateFlow(false)
    val showProfileDialog: StateFlow<Boolean> = _showProfileDialog.asStateFlow()
    
    private val _editingProfile = MutableStateFlow<ServerProfile?>(null)
    val editingProfile: StateFlow<ServerProfile?> = _editingProfile.asStateFlow()
    
    private val _showDeleteDialog = MutableStateFlow<ServerProfile?>(null)
    val showDeleteDialog: StateFlow<ServerProfile?> = _showDeleteDialog.asStateFlow()
    
    init {
        loadProfiles()
    }
    
    /**
     * Loads all saved profiles from the repository.
     */
    fun loadProfiles() {
        viewModelScope.launch {
            _uiState.value = ProfilesUiState.Loading
            try {
                val profiles = profileRepository.getAllProfiles()
                _uiState.value = if (profiles.isEmpty()) {
                    ProfilesUiState.Empty
                } else {
                    ProfilesUiState.Success(profiles)
                }
            } catch (e: Exception) {
                _uiState.value = ProfilesUiState.Error(e.message ?: "Failed to load profiles")
            }
        }
    }
    
    /**
     * Shows the profile creation/edit dialog.
     * 
     * @param profile Optional profile to edit, null for creating new profile
     */
    fun showProfileDialog(profile: ServerProfile? = null) {
        _editingProfile.value = profile
        _showProfileDialog.value = true
    }
    
    /**
     * Hides the profile creation/edit dialog.
     */
    fun hideProfileDialog() {
        _showProfileDialog.value = false
        _editingProfile.value = null
    }
    
    /**
     * Creates a new profile with the provided details.
     * 
     * @param name Profile name
     * @param hostname SSH server hostname
     * @param port SSH server port
     * @param username SSH username
     * @param keyType SSH key type
     * @param keyData SSH private key bytes
     */
    fun createProfile(
        name: String,
        hostname: String,
        port: Int,
        username: String,
        keyType: KeyType,
        keyData: ByteArray
    ) {
        viewModelScope.launch {
            try {
                val profile = ServerProfile(
                    name = name,
                    hostname = hostname,
                    port = port,
                    username = username,
                    keyType = keyType
                )
                
                val result = profileRepository.createProfile(profile)
                result.onSuccess { profileId ->
                    // Store the SSH key
                    credentialStore.storeKey(profileId, keyData)
                    hideProfileDialog()
                    loadProfiles()
                }.onFailure { error ->
                    _uiState.value = ProfilesUiState.Error(
                        error.message ?: "Failed to create profile"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ProfilesUiState.Error(
                    e.message ?: "Failed to create profile"
                )
            }
        }
    }
    
    /**
     * Updates an existing profile with new details.
     * 
     * @param profile Updated profile
     * @param keyData Optional new SSH private key bytes
     */
    fun updateProfile(profile: ServerProfile, keyData: ByteArray? = null) {
        viewModelScope.launch {
            try {
                val result = profileRepository.updateProfile(profile)
                result.onSuccess {
                    // Update the SSH key if provided
                    keyData?.let {
                        credentialStore.storeKey(profile.id, it)
                    }
                    hideProfileDialog()
                    loadProfiles()
                }.onFailure { error ->
                    _uiState.value = ProfilesUiState.Error(
                        error.message ?: "Failed to update profile"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ProfilesUiState.Error(
                    e.message ?: "Failed to update profile"
                )
            }
        }
    }
    
    /**
     * Shows the delete confirmation dialog for a profile.
     * 
     * @param profile Profile to delete
     */
    fun showDeleteDialog(profile: ServerProfile) {
        _showDeleteDialog.value = profile
    }
    
    /**
     * Hides the delete confirmation dialog.
     */
    fun hideDeleteDialog() {
        _showDeleteDialog.value = null
    }
    
    /**
     * Deletes a profile and its associated SSH key.
     * 
     * @param profile Profile to delete
     */
    fun deleteProfile(profile: ServerProfile) {
        viewModelScope.launch {
            try {
                // Delete the SSH key first
                credentialStore.deleteKey(profile.id)
                
                // Then delete the profile
                val result = profileRepository.deleteProfile(profile.id)
                result.onSuccess {
                    hideDeleteDialog()
                    loadProfiles()
                }.onFailure { error ->
                    _uiState.value = ProfilesUiState.Error(
                        error.message ?: "Failed to delete profile"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ProfilesUiState.Error(
                    e.message ?: "Failed to delete profile"
                )
            }
        }
    }
    
    /**
     * Clears any error state.
     */
    fun clearError() {
        if (_uiState.value is ProfilesUiState.Error) {
            loadProfiles()
        }
    }
}

/**
 * UI state for the profiles screen.
 */
sealed class ProfilesUiState {
    object Loading : ProfilesUiState()
    object Empty : ProfilesUiState()
    data class Success(val profiles: List<ServerProfile>) : ProfilesUiState()
    data class Error(val message: String) : ProfilesUiState()
}
