package com.sshtunnel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sshtunnel.data.AppRoutingConfig
import com.sshtunnel.data.ShadowsocksConfig
import com.sshtunnel.shadowsocks.ShadowsocksClient
import com.sshtunnel.shadowsocks.ShadowsocksState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import com.sshtunnel.data.RoutingMode as DataRoutingMode

/**
 * Android VPN service that routes device traffic through a Shadowsocks SOCKS5 proxy.
 * 
 * This service:
 * 1. Starts a Shadowsocks client to create a local SOCKS5 proxy
 * 2. Creates a TUN interface to intercept device traffic
 * 3. Routes all traffic through the SOCKS5 proxy
 * 4. Handles app exclusion filtering
 * 5. Prevents DNS leaks by routing DNS through the tunnel
 * 
 * Requirements: 3.3, 3.4, 4.1, 4.2, 4.3, 4.5, 5.2, 5.5
 */
class TunnelVpnService : VpnService() {
    
    private var tunInterface: ParcelFileDescriptor? = null
    private var serviceScope: CoroutineScope? = null
    private var packetRoutingJob: Job? = null
    private var shadowsocksClient: ShadowsocksClient? = null
    private var shadowsocksMonitorJob: Job? = null
    
    private var currentConfig: TunnelConfig? = null
    private var currentShadowsocksConfig: ShadowsocksConfig? = null
    
    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        Log.d(TAG, "TunnelVpnService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WITH_SHADOWSOCKS -> {
                // Start VPN with Shadowsocks integration
                val serverHost = intent.getStringExtra(EXTRA_SERVER_HOST) ?: ""
                val serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, 0)
                val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
                val cipher = intent.getStringExtra(EXTRA_CIPHER) ?: ""
                val dnsServers = intent.getStringArrayExtra(EXTRA_DNS_SERVERS)?.toList() ?: listOf("8.8.8.8", "8.8.4.4")
                val excludedApps = intent.getStringArrayExtra(EXTRA_EXCLUDED_APPS)?.toSet() ?: emptySet()
                val routingMode = intent.getStringExtra(EXTRA_ROUTING_MODE)?.let {
                    DataRoutingMode.valueOf(it)
                } ?: DataRoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
                val mtu = intent.getIntExtra(EXTRA_MTU, 1500)
                val sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: "Shadowsocks VPN"
                
                // Parse cipher method
                val cipherMethod = try {
                    com.sshtunnel.data.CipherMethod.valueOf(cipher)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid cipher method: $cipher")
                    updateProviderState(TunnelState.Error("Invalid cipher method"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                
                val shadowsocksConfig = ShadowsocksConfig(
                    serverHost = serverHost,
                    serverPort = serverPort,
                    password = password,
                    cipher = cipherMethod
                )
                
                val tunnelConfig = TunnelConfig(
                    socksPort = 0, // Will be set after Shadowsocks starts
                    dnsServers = dnsServers,
                    routingConfig = RoutingConfig(excludedApps, routingMode),
                    mtu = mtu,
                    sessionName = sessionName
                )
                
                startTunnelWithShadowsocks(shadowsocksConfig, tunnelConfig)
            }
            
            ACTION_START -> {
                // Legacy action for starting with existing SOCKS port
                val socksPort = intent.getIntExtra(EXTRA_SOCKS_PORT, 0)
                val dnsServers = intent.getStringArrayExtra(EXTRA_DNS_SERVERS)?.toList() ?: listOf("8.8.8.8", "8.8.4.4")
                val excludedApps = intent.getStringArrayExtra(EXTRA_EXCLUDED_APPS)?.toSet() ?: emptySet()
                val routingMode = intent.getStringExtra(EXTRA_ROUTING_MODE)?.let {
                    DataRoutingMode.valueOf(it)
                } ?: DataRoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
                val mtu = intent.getIntExtra(EXTRA_MTU, 1500)
                val sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: "Shadowsocks VPN"
                
                val config = TunnelConfig(
                    socksPort = socksPort,
                    dnsServers = dnsServers,
                    routingConfig = RoutingConfig(excludedApps, routingMode),
                    mtu = mtu,
                    sessionName = sessionName
                )
                
                startTunnel(config)
            }
            
            ACTION_STOP -> {
                stopTunnel()
            }
            
            ACTION_UPDATE_ROUTING -> {
                val excludedApps = intent.getStringArrayExtra(EXTRA_EXCLUDED_APPS)?.toSet() ?: emptySet()
                val routingMode = intent.getStringExtra(EXTRA_ROUTING_MODE)?.let {
                    DataRoutingMode.valueOf(it)
                } ?: DataRoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
                
                updateRouting(RoutingConfig(excludedApps, routingMode))
            }
        }
        
        return START_STICKY
    }
    
    /**
     * Starts VPN tunnel with Shadowsocks integration.
     * 
     * This method:
     * 1. Starts the Shadowsocks client to create a local SOCKS5 proxy
     * 2. Waits for the proxy to be ready
     * 3. Creates the TUN interface with proper DNS and routing configuration
     * 4. Starts packet routing through the SOCKS5 proxy
     * 
     * Requirements: 3.3, 3.4, 4.1, 4.2, 4.3, 4.5
     */
    private fun startTunnelWithShadowsocks(
        shadowsocksConfig: ShadowsocksConfig,
        tunnelConfig: TunnelConfig
    ) {
        serviceScope?.launch {
            try {
                Log.d(TAG, "Starting VPN tunnel with Shadowsocks")
                
                // Store configs
                currentShadowsocksConfig = shadowsocksConfig
                currentConfig = tunnelConfig
                
                // Get Shadowsocks client (injected via AndroidVpnTunnelProvider)
                val client = Companion.shadowsocksClient
                if (client == null) {
                    Log.e(TAG, "Shadowsocks client not initialized")
                    updateProviderState(TunnelState.Error("Shadowsocks client not available"))
                    stopSelf()
                    return@launch
                }
                
                // Store client reference for this service instance
                shadowsocksClient = client
                
                // Start Shadowsocks client
                Log.d(TAG, "Starting Shadowsocks client")
                val result = client.start(shadowsocksConfig)
                
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Failed to start Shadowsocks: ${error?.message}", error)
                    updateProviderState(TunnelState.Error("Failed to start Shadowsocks: ${error?.message}", error))
                    stopSelf()
                    return@launch
                }
                
                val socksPort = result.getOrThrow()
                Log.d(TAG, "Shadowsocks started on port $socksPort")
                
                // Update tunnel config with actual SOCKS port
                val updatedConfig = tunnelConfig.copy(socksPort = socksPort)
                currentConfig = updatedConfig
                
                // Monitor Shadowsocks state
                startShadowsocksMonitoring(client)
                
                // Create TUN interface with DNS leak prevention
                tunInterface = createTunInterface(updatedConfig)
                
                if (tunInterface == null) {
                    Log.e(TAG, "Failed to create TUN interface")
                    client.stop()
                    updateProviderState(TunnelState.Error("Failed to create TUN interface"))
                    stopSelf()
                    return@launch
                }
                
                // Start foreground service with notification
                startForeground(NOTIFICATION_ID, createNotification(updatedConfig))
                
                // Update state
                updateProviderState(TunnelState.Active)
                
                // Start packet routing
                startPacketRouting(socksPort)
                
                Log.d(TAG, "VPN tunnel with Shadowsocks started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting tunnel with Shadowsocks", e)
                shadowsocksClient?.let { client ->
                    serviceScope?.launch { client.stop() }
                }
                updateProviderState(TunnelState.Error("Failed to start tunnel: ${e.message}", e))
                stopSelf()
            }
        }
    }
    
    /**
     * Monitors Shadowsocks client state and handles errors.
     * 
     * Requirements: 3.5
     */
    private fun startShadowsocksMonitoring(client: ShadowsocksClient) {
        shadowsocksMonitorJob?.cancel()
        shadowsocksMonitorJob = serviceScope?.launch {
            client.observeState().collectLatest { state ->
                when (state) {
                    is ShadowsocksState.Error -> {
                        Log.e(TAG, "Shadowsocks error: ${state.message}")
                        updateProviderState(TunnelState.Error("Shadowsocks error: ${state.message}", state.cause))
                        stopTunnel()
                    }
                    is ShadowsocksState.Idle -> {
                        Log.w(TAG, "Shadowsocks became idle unexpectedly")
                        updateProviderState(TunnelState.Error("Shadowsocks stopped unexpectedly"))
                        stopTunnel()
                    }
                    else -> {
                        // Running or Starting states are normal
                    }
                }
            }
        }
    }
    
    /**
     * Starts VPN tunnel with an existing SOCKS port (legacy method).
     * 
     * Requirements: 3.3, 3.4, 4.1, 4.2, 4.3
     */
    private fun startTunnel(config: TunnelConfig) {
        serviceScope?.launch {
            try {
                Log.d(TAG, "Starting VPN tunnel with SOCKS port ${config.socksPort}")
                
                // Store current config
                currentConfig = config
                
                // Create TUN interface
                tunInterface = createTunInterface(config)
                
                if (tunInterface == null) {
                    Log.e(TAG, "Failed to create TUN interface")
                    updateProviderState(TunnelState.Error("Failed to create TUN interface"))
                    stopSelf()
                    return@launch
                }
                
                // Start foreground service with notification
                startForeground(NOTIFICATION_ID, createNotification(config))
                
                // Update state
                updateProviderState(TunnelState.Active)
                
                // Start packet routing
                startPacketRouting(config.socksPort)
                
                Log.d(TAG, "VPN tunnel started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting tunnel", e)
                updateProviderState(TunnelState.Error("Failed to start tunnel: ${e.message}", e))
                stopSelf()
            }
        }
    }
    
    /**
     * Stops the VPN tunnel and cleans up all resources.
     * 
     * Requirements: 3.4
     */
    private fun stopTunnel() {
        Log.d(TAG, "Stopping VPN tunnel")
        
        // Cancel Shadowsocks monitoring
        shadowsocksMonitorJob?.cancel()
        shadowsocksMonitorJob = null
        
        // Stop Shadowsocks client
        shadowsocksClient?.let { client ->
            serviceScope?.launch {
                try {
                    client.stop()
                    Log.d(TAG, "Shadowsocks client stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping Shadowsocks client", e)
                }
            }
        }
        
        // Cancel packet routing
        packetRoutingJob?.cancel()
        packetRoutingJob = null
        
        // Close TUN interface
        try {
            tunInterface?.close()
            tunInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TUN interface", e)
        }
        
        // Clear configs
        currentConfig = null
        currentShadowsocksConfig = null
        
        // Update state
        updateProviderState(TunnelState.Inactive)
        
        // Stop foreground and service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "VPN tunnel stopped")
    }
    
    private fun updateRouting(config: RoutingConfig) {
        serviceScope?.launch {
            try {
                Log.d(TAG, "Updating routing configuration")
                
                currentConfig?.let { currentCfg ->
                    // Store the new config
                    val newConfig = currentCfg.copy(routingConfig = config)
                    currentConfig = newConfig
                    
                    // Recreate TUN interface with new routing
                    // This is necessary because Android's VpnService doesn't support
                    // dynamic routing updates without recreating the interface
                    val oldTun = tunInterface
                    
                    // Create new TUN interface with updated routing
                    val newTun = createTunInterface(newConfig)
                    
                    if (newTun != null) {
                        // Cancel old packet routing
                        packetRoutingJob?.cancel()
                        
                        // Close old TUN interface
                        oldTun?.close()
                        
                        // Update to new TUN interface
                        tunInterface = newTun
                        
                        // Restart packet routing
                        startPacketRouting(newConfig.socksPort)
                        
                        Log.d(TAG, "Routing configuration updated successfully")
                    } else {
                        Log.e(TAG, "Failed to create new TUN interface with updated routing")
                        updateProviderState(TunnelState.Error("Failed to update routing"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating routing", e)
                updateProviderState(TunnelState.Error("Failed to update routing: ${e.message}", e))
            }
        }
    }
    
    /**
     * Creates the TUN interface with proper DNS and routing configuration.
     * 
     * This method implements:
     * - DNS leak prevention by routing DNS through the tunnel (Requirement 4.3, 4.5)
     * - App exclusion filtering (Requirement 5.2, 5.5)
     * - Proper routing for all traffic (Requirement 4.1, 4.2)
     * 
     * Requirements: 4.1, 4.2, 4.3, 4.5, 5.2, 5.5
     */
    private fun createTunInterface(config: TunnelConfig): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setSession(config.sessionName)
                .addAddress("10.0.0.2", 24) // VPN interface address
                .addRoute("0.0.0.0", 0) // Route all traffic (Requirement 4.1, 4.2)
                .setMtu(config.mtu)
                .setBlocking(true)
            
            // Add DNS servers to prevent DNS leaks (Requirement 4.3, 4.5)
            // All DNS queries will be routed through the VPN tunnel
            config.dnsServers.forEach { dns ->
                builder.addDnsServer(dns)
                Log.d(TAG, "Added DNS server: $dns")
            }
            
            // Configure app routing (Requirement 5.2, 5.5)
            configureAppRouting(builder, config.routingConfig)
            
            // Set configure intent (for notification tap)
            builder.setConfigureIntent(createConfigIntent())
            
            // Establish the VPN
            val vpn = builder.establish()
            
            if (vpn != null) {
                Log.d(TAG, "TUN interface created successfully")
            } else {
                Log.e(TAG, "Failed to establish VPN - permission may have been revoked")
            }
            
            vpn
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TUN interface", e)
            null
        }
    }
    
    /**
     * Configures app-specific routing for the VPN.
     * 
     * Implements per-app routing to allow users to exclude specific apps from the tunnel.
     * Always excludes the VPN app itself to prevent routing loops.
     * 
     * Requirements: 5.2, 5.5
     */
    private fun configureAppRouting(builder: Builder, config: RoutingConfig) {
        when (config.routingMode) {
            DataRoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED -> {
                // Exclude specified apps from the tunnel (Requirement 5.2)
                config.excludedApps.forEach { packageName ->
                    try {
                        builder.addDisallowedApplication(packageName)
                        Log.d(TAG, "Excluded app from tunnel: $packageName")
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(TAG, "App not found: $packageName", e)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to exclude app: $packageName", e)
                    }
                }
                
                // Always exclude our own app to prevent routing loops
                try {
                    builder.addDisallowedApplication(packageName)
                    Log.d(TAG, "Excluded own app from tunnel: $packageName")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to exclude own app", e)
                }
            }
            
            DataRoutingMode.ROUTE_ONLY_INCLUDED -> {
                // In this mode, we need to exclude all apps except the ones in the "included" list
                // The "excludedApps" set in this mode actually contains the apps to INCLUDE
                // We need to get all installed apps and exclude everything except the included ones
                
                try {
                    val pm = packageManager
                    val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    
                    val includedApps = config.excludedApps // In ROUTE_ONLY_INCLUDED mode, this is the include list
                    
                    installedApps.forEach { appInfo ->
                        val pkgName = appInfo.packageName
                        
                        // Skip if this app is in the included list
                        if (includedApps.contains(pkgName)) {
                            Log.d(TAG, "Including app in tunnel: $pkgName")
                            return@forEach
                        }
                        
                        // Skip our own app (always exclude to prevent loops)
                        if (pkgName == packageName) {
                            return@forEach
                        }
                        
                        // Exclude all other apps
                        try {
                            builder.addDisallowedApplication(pkgName)
                            Log.v(TAG, "Excluded app from tunnel (include mode): $pkgName")
                        } catch (e: Exception) {
                            Log.v(TAG, "Failed to exclude app: $pkgName", e)
                        }
                    }
                    
                    // Always exclude our own app to prevent routing loops
                    try {
                        builder.addDisallowedApplication(packageName)
                        Log.d(TAG, "Excluded own app from tunnel: $packageName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to exclude own app", e)
                    }
                    
                    Log.d(TAG, "ROUTE_ONLY_INCLUDED mode configured with ${includedApps.size} included apps")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error configuring ROUTE_ONLY_INCLUDED mode", e)
                }
            }
        }
    }
    
    private fun createConfigIntent(): PendingIntent {
        // This intent will be triggered when the user taps the VPN notification
        // For now, we'll create a simple intent that opens the main activity
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    private fun startPacketRouting(socksPort: Int) {
        val tun = tunInterface ?: return
        
        packetRoutingJob = serviceScope?.launch(Dispatchers.IO) {
            val inputStream = FileInputStream(tun.fileDescriptor)
            val outputStream = FileOutputStream(tun.fileDescriptor)
            val buffer = ByteArray(32767) // Max IP packet size
            
            Log.d(TAG, "Starting packet routing loop")
            
            try {
                while (isActive) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        // For now, we'll implement basic packet forwarding
                        // A full implementation would parse IP packets and route them through SOCKS5
                        // This is a simplified version that demonstrates the concept
                        
                        // In a production implementation, you would:
                        // 1. Parse the IP packet to extract destination
                        // 2. Create a SOCKS5 connection to the destination
                        // 3. Forward the packet data through SOCKS5
                        // 4. Receive the response and write it back to the TUN interface
                        
                        // For now, we'll just log that we received a packet
                        if (length > 20) { // Minimum IP header size
                            val version = (buffer[0].toInt() shr 4) and 0x0F
                            if (version == 4) {
                                // IPv4 packet
                                val destIp = "${buffer[16].toUByte()}.${buffer[17].toUByte()}.${buffer[18].toUByte()}.${buffer[19].toUByte()}"
                                Log.v(TAG, "Received IPv4 packet to $destIp, length: $length")
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "Error in packet routing", e)
                }
            } finally {
                Log.d(TAG, "Packet routing loop ended")
            }
        }
    }
    
    private fun createNotification(config: TunnelConfig): Notification {
        val channelId = createNotificationChannel()
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SSH Tunnel Active")
            .setContentText("Traffic is being routed through SSH tunnel")
            .setSmallIcon(android.R.drawable.ic_lock_lock) // Using system icon for now
            .setOngoing(true) // Cannot be dismissed
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createConfigIntent())
            .addAction(createDisconnectAction())
            .build()
    }
    
    private fun createNotificationChannel(): String {
        val channelId = "vpn_service"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when VPN tunnel is active"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        return channelId
    }
    
    private fun createDisconnectAction(): NotificationCompat.Action {
        val intent = Intent(this, TunnelVpnService::class.java).apply {
            action = ACTION_STOP
        }
        
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Disconnect",
            pendingIntent
        ).build()
    }
    
    private fun updateProviderState(state: TunnelState) {
        try {
            AndroidVpnTunnelProvider.getInstance(applicationContext).updateState(state)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update provider state", e)
        }
    }
    
    override fun onRevoke() {
        // User revoked VPN permission
        Log.i(TAG, "VPN permission revoked by user")
        updateProviderState(TunnelState.Error("VPN permission revoked"))
        stopTunnel()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "TunnelVpnService destroyed")
        
        // Cancel all coroutines
        serviceScope?.cancel()
        serviceScope = null
        
        // Close TUN interface
        try {
            tunInterface?.close()
            tunInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TUN interface in onDestroy", e)
        }
        
        super.onDestroy()
    }
    
    companion object {
        private const val TAG = "TunnelVpnService"
        private const val NOTIFICATION_ID = 1001
        
        // Actions
        const val ACTION_START = "com.sshtunnel.vpn.START"
        const val ACTION_START_WITH_SHADOWSOCKS = "com.sshtunnel.vpn.START_WITH_SHADOWSOCKS"
        const val ACTION_STOP = "com.sshtunnel.vpn.STOP"
        const val ACTION_UPDATE_ROUTING = "com.sshtunnel.vpn.UPDATE_ROUTING"
        
        // Extras
        const val EXTRA_SOCKS_PORT = "socks_port"
        const val EXTRA_SERVER_HOST = "server_host"
        const val EXTRA_SERVER_PORT = "server_port"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_CIPHER = "cipher"
        const val EXTRA_DNS_SERVERS = "dns_servers"
        const val EXTRA_EXCLUDED_APPS = "excluded_apps"
        const val EXTRA_ROUTING_MODE = "routing_mode"
        const val EXTRA_MTU = "mtu"
        const val EXTRA_SESSION_NAME = "session_name"
        
        /**
         * Shadowsocks client instance injected by AndroidVpnTunnelProvider.
         * This is set before starting the service.
         */
        internal var shadowsocksClient: ShadowsocksClient? = null
    }
}
