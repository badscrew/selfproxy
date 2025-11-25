package com.sshtunnel.android.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sshtunnel.android.logging.LogExportService
import com.sshtunnel.data.DnsMode
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.launch

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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Battery Optimization Section
            BatteryOptimizationSection(viewModel = viewModel)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Diagnostics Section
            DiagnosticsSection(viewModel = viewModel)
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


@Composable
private fun BatteryOptimizationSection(viewModel: SettingsViewModel) {
    val batteryState by viewModel.batteryState.collectAsState()
    val isIgnoringOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    SettingsSection(title = "Battery Optimization") {
        // Battery Status
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Battery Status",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Level:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (batteryState.level >= 0) "${batteryState.level}%" else "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        batteryState.isLowBattery -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Charging:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (batteryState.isCharging) "Yes" else "No",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Battery Saver:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (batteryState.isBatterySaverEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (batteryState.isBatterySaverEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Battery Optimization Exemption
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Battery Optimization Exemption",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = if (isIgnoringOptimizations) {
                    "App is exempt from battery optimizations. The VPN tunnel will remain active in the background."
                } else {
                    "App is subject to battery optimizations. The VPN tunnel may be interrupted when the device enters doze mode."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (!isIgnoringOptimizations) {
                Button(
                    onClick = {
                        viewModel.getBatteryOptimizationExemptionIntent()?.let { intent ->
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request Exemption")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                        contentDescription = "Exempt",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Exemption granted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Low Battery Warning
        if (batteryState.isLowBattery) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Low Battery",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Battery level is low. Consider disconnecting the tunnel to conserve power.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        
        // Battery Saver Info
        if (batteryState.isBatterySaverEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Battery Saver Active",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Keep-alive intervals have been increased to reduce battery consumption.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    
    SettingsSection(title = "Diagnostics") {
        // Verbose Logging Toggle
        SwitchSettingItem(
            label = "Verbose Logging",
            checked = settings.verboseLogging,
            onCheckedChange = { viewModel.updateVerboseLogging(it) },
            description = "Enable detailed logging for debugging purposes"
        )
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Log Export
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Export Logs",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Export diagnostic logs for troubleshooting. All sensitive data is automatically removed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Logs")
                }
            }
            
            if (exportError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Error: $exportError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Privacy Notice",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Logs are sanitized to remove passwords, private keys, and other sensitive data before export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
    
    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Logs") },
            text = { Text("Choose export format:") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        scope.launch {
                            try {
                                // Export as text
                                val logExportService = LogExportService(
                                    context,
                                    (context.applicationContext as com.sshtunnel.android.SSHTunnelProxyApp)
                                        .logger
                                )
                                val result = logExportService.exportLogsAsText()
                                result.onSuccess { intent ->
                                    context.startActivity(Intent.createChooser(intent, "Share Logs"))
                                }.onFailure { error ->
                                    exportError = error.message
                                }
                            } catch (e: Exception) {
                                exportError = e.message
                            }
                        }
                    }
                ) {
                    Text("Text File")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            scope.launch {
                                try {
                                    // Export as JSON
                                    val logExportService = LogExportService(
                                        context,
                                        (context.applicationContext as com.sshtunnel.android.SSHTunnelProxyApp)
                                            .logger
                                    )
                                    val result = logExportService.exportLogsAsJson()
                                    result.onSuccess { intent ->
                                        context.startActivity(Intent.createChooser(intent, "Share Logs"))
                                    }.onFailure { error ->
                                        exportError = error.message
                                    }
                                } catch (e: Exception) {
                                    exportError = e.message
                                }
                            }
                        }
                    ) {
                        Text("JSON File")
                    }
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
