package com.sshtunnel.android.ui.screens.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.ssh.Connection
import com.sshtunnel.testing.ConnectionTestResult
import kotlin.time.Duration

/**
 * Connection screen - displays connection status and controls.
 * Shows current profile info, connection state, and connect/disconnect button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAppRouting: ((Long) -> Unit)? = null,
    profileId: Long? = null,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val vpnPermissionNeeded by viewModel.vpnPermissionNeeded.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // VPN permission launcher
    val vpnPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }
    
    // Request VPN permission when needed
    LaunchedEffect(vpnPermissionNeeded) {
        if (vpnPermissionNeeded) {
            val intent = android.net.VpnService.prepare(context)
            if (intent != null) {
                vpnPermissionLauncher.launch(intent)
            } else {
                // Permission already granted
                viewModel.onVpnPermissionGranted()
            }
        }
    }
    
    // Set profile if provided
    LaunchedEffect(profileId) {
        profileId?.let { viewModel.setProfile(it) }
    }
    
    // Listen for VPN state broadcasts
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    "com.sshtunnel.android.vpn.STARTED" -> {
                        viewModel.onVpnStarted()
                    }
                    "com.sshtunnel.android.vpn.STOPPED" -> {
                        viewModel.onVpnStopped()
                    }
                    "com.sshtunnel.android.vpn.ERROR" -> {
                        val errorMessage = intent.getStringExtra("error_message") ?: "Unknown VPN error"
                        viewModel.onVpnError(errorMessage)
                    }
                }
            }
        }
        
        val filter = android.content.IntentFilter().apply {
            addAction("com.sshtunnel.android.vpn.STARTED")
            addAction("com.sshtunnel.android.vpn.STOPPED")
            addAction("com.sshtunnel.android.vpn.ERROR")
        }
        
        context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = uiState) {
                is ConnectionUiState.Disconnected -> {
                    DisconnectedContent(
                        onConnect = { viewModel.connect() }
                    )
                }
                
                is ConnectionUiState.Connecting -> {
                    ConnectingContent(
                        profile = state.profile
                    )
                }
                
                is ConnectionUiState.WaitingForVpn -> {
                    WaitingForVpnContent(
                        connection = state.connection,
                        profile = state.profile,
                        onDisconnect = { viewModel.disconnect() }
                    )
                }
                
                is ConnectionUiState.Connected -> {
                    ConnectedContent(
                        connection = state.connection,
                        profile = state.profile,
                        testResult = testResult,
                        onDisconnect = { viewModel.disconnect() },
                        onTestConnection = { viewModel.testConnection() },
                        onClearTestResult = { viewModel.clearTestResult() },
                        onNavigateToAppRouting = onNavigateToAppRouting
                    )
                }
                
                is ConnectionUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        errorDetails = viewModel.getErrorDetails(state.error),
                        onRetry = { viewModel.connect() },
                        onBack = {
                            viewModel.clearError()
                            onNavigateBack()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Content shown when disconnected.
 */
@Composable
private fun DisconnectedContent(
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Disconnected",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Select a profile and connect to start tunneling",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Connect")
        }
    }
}

/**
 * Content shown while connecting.
 */
@Composable
private fun ConnectingContent(
    profile: ServerProfile?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp)
        )
        
        Text(
            text = "Connecting...",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        profile?.let {
            Text(
                text = "to ${it.username}@${it.hostname}:${it.port}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Content shown while waiting for VPN to start.
 */
@Composable
private fun WaitingForVpnContent(
    connection: Connection,
    profile: ServerProfile?,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp)
        )
        
        Text(
            text = "Starting VPN...",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "SSH tunnel established, activating VPN",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        profile?.let {
            Text(
                text = "${it.username}@${it.hostname}:${it.port}",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Content shown when connected.
 */
@Composable
private fun ConnectedContent(
    connection: Connection,
    profile: ServerProfile?,
    testResult: TestResultState,
    onDisconnect: () -> Unit,
    onTestConnection: () -> Unit,
    onClearTestResult: () -> Unit,
    onNavigateToAppRouting: ((Long) -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Connected",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        
        profile?.let {
            ProfileInfoCard(profile = it)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ProxyInfoCard(
            socksPort = connection.socksPort,
            serverAddress = "${connection.username}@${connection.serverAddress}:${connection.serverPort}"
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // SOCKS5 Test button
        var showSocksTest by remember { mutableStateOf(false) }
        
        OutlinedButton(
            onClick = { showSocksTest = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test SOCKS5 Proxy")
        }
        
        // Show test dialog
        if (showSocksTest) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showSocksTest = false }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 8.dp
                ) {
                    com.sshtunnel.android.testing.JSchSocksTestScreen(
                        socksPort = connection.socksPort,
                        onBack = { showSocksTest = false }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // App routing button
        profile?.let { prof ->
            onNavigateToAppRouting?.let { navigate ->
                OutlinedButton(
                    onClick = { navigate(prof.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Configure App Routing")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        // Connection test section
        ConnectionTestSection(
            testResult = testResult,
            onTestConnection = onTestConnection,
            onClearTestResult = onClearTestResult
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Disconnect")
        }
    }
}

/**
 * Content shown when there's an error.
 */
@Composable
private fun ErrorContent(
    message: String,
    errorDetails: ErrorDetails,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = errorDetails.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = errorDetails.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                if (errorDetails.suggestions.isNotEmpty()) {
                    Divider()
                    
                    Text(
                        text = "Suggestions:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    errorDetails.suggestions.forEach { suggestion ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retry")
            }
        }
    }
}

/**
 * Card displaying profile information.
 */
@Composable
private fun ProfileInfoCard(profile: ServerProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium
            )
            
            InfoRow(
                label = "Server",
                value = "${profile.username}@${profile.hostname}:${profile.port}"
            )
            
            InfoRow(
                label = "Key Type",
                value = profile.keyType.name
            )
        }
    }
}

/**
 * Displays SOCKS5 proxy information when connected.
 */
@Composable
private fun ProxyInfoCard(
    socksPort: Int,
    serverAddress: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SOCKS5 Proxy Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Divider()
            
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow(
                label = "Proxy Address",
                value = "127.0.0.1:$socksPort"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            InfoRow(
                label = "Server",
                value = serverAddress
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Configure apps to use this SOCKS5 proxy for manual routing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Connection test section with button and results.
 */
@Composable
private fun ConnectionTestSection(
    testResult: TestResultState,
    onTestConnection: () -> Unit,
    onClearTestResult: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onTestConnection,
            modifier = Modifier.fillMaxWidth(),
            enabled = testResult !is TestResultState.Testing
        ) {
            if (testResult is TestResultState.Testing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testing...")
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Connection")
            }
        }
        
        AnimatedVisibility(visible = testResult is TestResultState.Success) {
            (testResult as? TestResultState.Success)?.let { success ->
                ConnectionTestResultCard(
                    result = success.result,
                    onDismiss = onClearTestResult
                )
            }
        }
        
        AnimatedVisibility(visible = testResult is TestResultState.Error) {
            (testResult as? TestResultState.Error)?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Test Failed",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = error.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(onClick = onClearTestResult) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card displaying connection test results.
 */
@Composable
private fun ConnectionTestResultCard(
    result: ConnectionTestResult,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.isRoutingCorrectly) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (result.isRoutingCorrectly) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = if (result.isRoutingCorrectly) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connection Test",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Divider()
            
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow(
                label = "External IP",
                value = result.externalIp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            result.expectedServerIp?.let { expectedIp ->
                InfoRow(
                    label = "Expected IP",
                    value = expectedIp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            InfoRow(
                label = "Routing Status",
                value = if (result.isRoutingCorrectly) "Correct" else "Unknown"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            InfoRow(
                label = "Latency",
                value = formatDuration(result.latency)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            InfoRow(
                label = "Test Service",
                value = result.testServiceUsed
            )
            
            if (!result.isRoutingCorrectly && result.expectedServerIp != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Note: The external IP doesn't match the expected server IP. This may be normal if your server uses NAT or a proxy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/**
 * Generic info row for displaying label-value pairs.
 */
@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Formats a duration for display.
 */
private fun formatDuration(duration: Duration): String {
    return when {
        duration.inWholeMilliseconds < 1000 -> "${duration.inWholeMilliseconds}ms"
        duration.inWholeSeconds < 60 -> "${duration.inWholeSeconds}s"
        else -> "${duration.inWholeMinutes}m ${duration.inWholeSeconds % 60}s"
    }
}
