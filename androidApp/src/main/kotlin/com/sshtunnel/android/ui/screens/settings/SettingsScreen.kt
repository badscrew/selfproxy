package com.sshtunnel.android.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sshtunnel.data.DnsMode

/**
 * Settings screen - displays app configuration options.
 * Includes SSH settings, VPN settings, battery optimization, and diagnostics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetToDefaults() }) {
                        Text("Reset")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // SSH Connection Settings Section
            SettingsSection(title = "SSH Connection") {
                // SSH Port
                NumberSettingItem(
                    label = "SSH Port",
                    value = settings.sshPort,
                    onValueChange = { viewModel.updateSshPort(it) },
                    range = 1..65535,
                    description = "Port number for SSH server connection"
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Connection Timeout
                NumberSettingItem(
                    label = "Connection Timeout (seconds)",
                    value = settings.connectionTimeout.inWholeSeconds.toInt(),
                    onValueChange = { viewModel.updateConnectionTimeout(it) },
                    range = 5..300,
                    description = "Maximum time to wait for connection"
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Keep-Alive Interval
                NumberSettingItem(
                    label = "Keep-Alive Interval (seconds)",
                    value = settings.keepAliveInterval.inWholeSeconds.toInt(),
                    onValueChange = { viewModel.updateKeepAliveInterval(it) },
                    range = 10..300,
                    description = "Interval between keep-alive packets"
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Compression Toggle
                SwitchSettingItem(
                    label = "Enable Compression",
                    checked = settings.enableCompression,
                    onCheckedChange = { viewModel.updateEnableCompression(it) },
                    description = "Compress data over SSH connection"
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Strict Host Key Checking
                SwitchSettingItem(
                    label = "Strict Host Key Checking",
                    checked = settings.strictHostKeyChecking,
                    onCheckedChange = { viewModel.updateStrictHostKeyChecking(it) },
                    description = "Verify SSH server host keys"
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // VPN Settings Section
            SettingsSection(title = "VPN Configuration") {
                // Custom SOCKS5 Port
                OptionalNumberSettingItem(
                    label = "Custom SOCKS5 Port",
                    value = settings.customSocksPort,
                    onValueChange = { viewModel.updateCustomSocksPort(it) },
                    range = 1024..65535,
                    description = "Local port for SOCKS5 proxy (automatic if not set)"
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // DNS Mode
                DnsModeSettingItem(
                    label = "DNS Mode",
                    selectedMode = settings.dnsMode,
                    onModeSelected = { viewModel.updateDnsMode(it) },
                    description = "How DNS queries are handled"
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            content()
        }
    }
}

@Composable
private fun NumberSettingItem(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    description: String
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }
    var isError by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                val intValue = newValue.toIntOrNull()
                if (intValue != null && intValue in range) {
                    isError = false
                    onValueChange(intValue)
                } else {
                    isError = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = isError,
            supportingText = if (isError) {
                { Text("Value must be between ${range.first} and ${range.last}") }
            } else null,
            singleLine = true
        )
    }
}

@Composable
private fun OptionalNumberSettingItem(
    label: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    range: IntRange,
    description: String
) {
    var textValue by remember(value) { mutableStateOf(value?.toString() ?: "") }
    var isError by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                if (newValue.isEmpty()) {
                    isError = false
                    onValueChange(null)
                } else {
                    val intValue = newValue.toIntOrNull()
                    if (intValue != null && intValue in range) {
                        isError = false
                        onValueChange(intValue)
                    } else {
                        isError = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = isError,
            supportingText = if (isError) {
                { Text("Value must be between ${range.first} and ${range.last}") }
            } else null,
            placeholder = { Text("Automatic") },
            singleLine = true
        )
    }
}

@Composable
private fun SwitchSettingItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsModeSettingItem(
    label: String,
    selectedMode: DnsMode,
    onModeSelected: (DnsMode) -> Unit,
    description: String
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = formatDnsMode(selectedMode),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DnsMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(formatDnsMode(mode)) },
                        onClick = {
                            onModeSelected(mode)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun formatDnsMode(mode: DnsMode): String {
    return when (mode) {
        DnsMode.THROUGH_TUNNEL -> "Through Tunnel"
        DnsMode.CUSTOM_DNS -> "Custom DNS"
        DnsMode.SYSTEM_DEFAULT -> "System Default"
    }
}
