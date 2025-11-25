package com.sshtunnel.android.ui.screens.approuting

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.sshtunnel.data.RoutingMode
import com.sshtunnel.vpn.InstalledAppsProvider

/**
 * App Routing screen for configuring per-app traffic routing.
 * 
 * Allows users to:
 * - Select which apps should use the VPN tunnel
 * - Switch between exclude and include modes
 * - Search and filter apps
 * - Toggle system apps visibility
 * - Apply routing changes dynamically
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoutingScreen(
    profileId: Long,
    onNavigateBack: () -> Unit,
    viewModel: AppRoutingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Load routing config when screen is first displayed
    LaunchedEffect(profileId) {
        viewModel.loadRoutingConfig(profileId)
    }
    
    // Show save success snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.showSaveSuccess) {
        if (uiState.showSaveSuccess) {
            snackbarHostState.showSnackbar(
                message = "Routing configuration saved",
                duration = SnackbarDuration.Short
            )
            viewModel.dismissSaveSuccess()
        }
    }
    
    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Routing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.hasUnsavedChanges) {
                        IconButton(
                            onClick = { viewModel.saveRoutingConfig() },
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "Save")
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Routing mode selector
                RoutingModeSelector(
                    selectedMode = uiState.routingMode,
                    onModeSelected = { viewModel.setRoutingMode(it) },
                    modifier = Modifier.padding(16.dp)
                )
                
                // Search bar
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.filterApps(it) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                // System apps toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show system apps",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.showSystemApps,
                        onCheckedChange = { viewModel.toggleShowSystemApps() }
                    )
                }
                
                Divider()
                
                // App list
                if (uiState.filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) {
                                "No apps found"
                            } else {
                                "No apps available"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AppList(
                        apps = uiState.filteredApps,
                        selectedPackages = uiState.selectedPackages,
                        routingMode = uiState.routingMode,
                        onAppToggle = { viewModel.toggleAppSelection(it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingModeSelector(
    selectedMode: RoutingMode,
    onModeSelected: (RoutingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Routing Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedMode == RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED,
                onClick = { onModeSelected(RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED) },
                label = { Text("Exclude apps") },
                modifier = Modifier.weight(1f)
            )
            
            FilterChip(
                selected = selectedMode == RoutingMode.ROUTE_ONLY_INCLUDED,
                onClick = { onModeSelected(RoutingMode.ROUTE_ONLY_INCLUDED) },
                label = { Text("Include only") },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = when (selectedMode) {
                RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED ->
                    "All apps use the tunnel except selected apps"
                RoutingMode.ROUTE_ONLY_INCLUDED ->
                    "Only selected apps use the tunnel"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search apps...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        singleLine = true
    )
}

@Composable
private fun AppList(
    apps: List<InstalledAppsProvider.InstalledApp>,
    selectedPackages: Set<String>,
    routingMode: RoutingMode,
    onAppToggle: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppListItem(
                app = app,
                isSelected = selectedPackages.contains(app.packageName),
                routingMode = routingMode,
                onToggle = { onAppToggle(app.packageName) }
            )
        }
    }
}

@Composable
private fun AppListItem(
    app: InstalledAppsProvider.InstalledApp,
    isSelected: Boolean,
    routingMode: RoutingMode,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        app.icon?.let { drawable ->
            val bitmap = remember(drawable) {
                drawable.toBitmap().asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        } ?: Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // App name and package
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Show routing status
            if (isSelected) {
                Text(
                    text = when (routingMode) {
                        RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED -> "Excluded from tunnel"
                        RoutingMode.ROUTE_ONLY_INCLUDED -> "Uses tunnel"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}
