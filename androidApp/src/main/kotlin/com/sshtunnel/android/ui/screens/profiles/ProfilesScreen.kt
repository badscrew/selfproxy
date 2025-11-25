package com.sshtunnel.android.ui.screens.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sshtunnel.data.ServerProfile
import java.text.SimpleDateFormat
import java.util.*

/**
 * Profiles screen - displays list of saved SSH server profiles.
 * Users can create, edit, delete, and select profiles for connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateToConnection: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showProfileDialog by viewModel.showProfileDialog.collectAsState()
    val editingProfile by viewModel.editingProfile.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH Profiles") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showProfileDialog() }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Profile"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ProfilesUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is ProfilesUiState.Empty -> {
                    Text(
                        text = "No profiles yet\nTap + to create your first profile",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is ProfilesUiState.Success -> {
                    ProfilesList(
                        profiles = state.profiles,
                        onProfileClick = { profile ->
                            // TODO: Navigate to connection with selected profile
                            onNavigateToConnection()
                        },
                        onEditClick = { profile ->
                            viewModel.showProfileDialog(profile)
                        },
                        onDeleteClick = { profile ->
                            viewModel.showDeleteDialog(profile)
                        }
                    )
                }
                
                is ProfilesUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.clearError() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
    
    // Profile form dialog
    if (showProfileDialog) {
        ProfileFormDialog(
            profile = editingProfile,
            onDismiss = { viewModel.hideProfileDialog() },
            onSave = { name, hostname, port, username, keyType, keyData ->
                if (editingProfile != null) {
                    viewModel.updateProfile(
                        editingProfile!!.copy(
                            name = name,
                            hostname = hostname,
                            port = port,
                            username = username,
                            keyType = keyType
                        ),
                        keyData
                    )
                } else {
                    if (keyData != null) {
                        viewModel.createProfile(name, hostname, port, username, keyType, keyData)
                    }
                }
            }
        )
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { profile ->
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Delete Profile") },
            text = {
                Text("Are you sure you want to delete \"${profile.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteProfile(profile) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * List of server profiles.
 */
@Composable
private fun ProfilesList(
    profiles: List<ServerProfile>,
    onProfileClick: (ServerProfile) -> Unit,
    onEditClick: (ServerProfile) -> Unit,
    onDeleteClick: (ServerProfile) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(profiles) { profile ->
            ProfileCard(
                profile = profile,
                onClick = { onProfileClick(profile) },
                onEditClick = { onEditClick(profile) },
                onDeleteClick = { onDeleteClick(profile) }
            )
        }
    }
}

/**
 * Card displaying a single server profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCard(
    profile: ServerProfile,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Profile name and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Server address
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Server:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${profile.username}@${profile.hostname}:${profile.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Key type
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Key Type:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = profile.keyType.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Last used
            profile.lastUsed?.let { timestamp ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Last used:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTimestamp(timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Formats a timestamp to a human-readable string.
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
