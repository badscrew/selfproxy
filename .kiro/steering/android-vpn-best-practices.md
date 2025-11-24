---
inclusion: always
---

# Android VPN Service Best Practices

## VPN Service Lifecycle

### Service Declaration

Always declare VPN service in AndroidManifest.xml:

```xml
<service
    android:name=".vpn.TunnelVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>
```

### Permission Handling

Request VPN permission before starting service:

```kotlin
class MainActivity : ComponentActivity() {
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            // User denied permission
            showPermissionDeniedDialog()
        }
    }
    
    fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            // Permission already granted
            startVpnService()
        }
    }
}
```

## VPN Configuration

### TUN Interface Setup

Configure the TUN interface properly:

```kotlin
class TunnelVpnService : VpnService() {
    private fun createTunInterface(socksPort: Int): ParcelFileDescriptor? {
        return Builder()
            .setSession("SSH Tunnel Proxy")
            .addAddress("10.0.0.2", 24) // VPN interface address
            .addRoute("0.0.0.0", 0) // Route all traffic
            .addDnsServer("8.8.8.8") // Primary DNS
            .addDnsServer("8.8.4.4") // Secondary DNS
            .setMtu(1500) // Standard MTU
            .setBlocking(true) // Blocking mode for packet reading
            .setConfigureIntent(createConfigIntent()) // Notification tap action
            .establish()
    }
    
    private fun createConfigIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
```

### App-Specific Routing

Implement per-app routing correctly:

```kotlin
fun configureAppRouting(
    builder: VpnService.Builder,
    excludedApps: Set<String>
) {
    excludedApps.forEach { packageName ->
        try {
            builder.addDisallowedApplication(packageName)
            Log.d(TAG, "Excluded app: $packageName")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "App not found: $packageName", e)
        }
    }
    
    // Always exclude our own app to prevent loops
    try {
        builder.addDisallowedApplication(packageName)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to exclude own app", e)
    }
}
```

## Foreground Service

### Notification Requirement

VPN services must run as foreground services with persistent notification:

```kotlin
class TunnelVpnService : VpnService() {
    private fun startForeground() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun createNotification(): Notification {
        val channelId = createNotificationChannel()
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SSH Tunnel Active")
            .setContentText("Connected to ${currentProfile.hostname}")
            .setSmallIcon(R.drawable.ic_vpn)
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
}
```

## Packet Routing

### Reading from TUN Interface

```kotlin
private suspend fun readPackets(tunInterface: ParcelFileDescriptor) {
    val inputStream = FileInputStream(tunInterface.fileDescriptor)
    val buffer = ByteArray(32767) // Max IP packet size
    
    withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                val length = inputStream.read(buffer)
                if (length > 0) {
                    val packet = buffer.copyOf(length)
                    routePacket(packet)
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "Error reading packet", e)
                }
                break
            }
        }
    }
}
```

### Writing to TUN Interface

```kotlin
private suspend fun writePacket(
    tunInterface: ParcelFileDescriptor,
    packet: ByteArray
) {
    val outputStream = FileOutputStream(tunInterface.fileDescriptor)
    withContext(Dispatchers.IO) {
        try {
            outputStream.write(packet)
        } catch (e: IOException) {
            Log.e(TAG, "Error writing packet", e)
        }
    }
}
```

## SOCKS5 Integration

### Routing Through SOCKS5

```kotlin
private suspend fun routePacket(packet: ByteArray) {
    // Parse IP packet
    val ipPacket = parseIPPacket(packet)
    
    // Route through SOCKS5 proxy
    val socksSocket = Socket()
    socksSocket.connect(
        InetSocketAddress("127.0.0.1", socksPort),
        5000 // 5 second timeout
    )
    
    // SOCKS5 handshake
    performSocks5Handshake(socksSocket, ipPacket.destination)
    
    // Forward packet data
    socksSocket.getOutputStream().write(ipPacket.payload)
    
    // Read response and write back to TUN
    val response = socksSocket.getInputStream().readBytes()
    writePacket(tunInterface, createResponsePacket(response))
}
```

## Battery Optimization

### Doze Mode Handling

Request battery optimization exemption:

```kotlin
fun requestBatteryOptimizationExemption(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val packageName = context.packageName
        
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            context.startActivity(intent)
        }
    }
}
```

### Efficient Keep-Alive

```kotlin
private fun scheduleKeepAlive() {
    keepAliveJob = serviceScope.launch {
        while (isActive) {
            delay(keepAliveInterval)
            sendKeepAlivePacket()
        }
    }
}

private suspend fun sendKeepAlivePacket() {
    try {
        sshSession.sendKeepAlive()
    } catch (e: Exception) {
        Log.w(TAG, "Keep-alive failed", e)
        triggerReconnect()
    }
}
```

## Network Change Handling

### Monitoring Network Changes

```kotlin
class NetworkMonitor(private val context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    
    fun observeNetworkChanges(): Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkState.Available(network))
            }
            
            override fun onLost(network: Network) {
                trySend(NetworkState.Lost)
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                trySend(NetworkState.Changed(isWifi, isCellular))
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
```

## Error Handling

### Graceful Shutdown

```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // Cancel all coroutines
    serviceScope.cancel()
    
    // Close TUN interface
    tunInterface?.close()
    
    // Disconnect SSH
    sshSession?.disconnect()
    
    // Stop foreground
    stopForeground(STOP_FOREGROUND_REMOVE)
}

override fun onRevoke() {
    // User revoked VPN permission
    Log.i(TAG, "VPN permission revoked")
    stopSelf()
}
```

## Testing VPN Service

### Manual Testing Checklist

- [ ] VPN connects successfully
- [ ] All traffic routes through tunnel
- [ ] DNS queries go through tunnel (check for leaks)
- [ ] App exclusions work correctly
- [ ] Network switches (WiFi ↔ Mobile) handled
- [ ] Notification shows correct status
- [ ] Disconnect works cleanly
- [ ] Battery usage is reasonable
- [ ] No memory leaks

### DNS Leak Testing

```kotlin
suspend fun testDnsLeak(): Boolean {
    val client = HttpClient()
    try {
        val response: String = client.get("https://dnsleaktest.com/").body()
        // Parse response to check if DNS matches VPN server
        return response.contains(expectedDnsServer)
    } finally {
        client.close()
    }
}
```

## Common Pitfalls

❌ **Don't**: Forget to exclude your own app
```kotlin
// This will cause a routing loop!
builder.establish()
```

✅ **Do**: Always exclude your own app
```kotlin
builder.addDisallowedApplication(packageName)
builder.establish()
```

❌ **Don't**: Block the main thread
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    connectToVpn() // Blocks main thread!
    return START_STICKY
}
```

✅ **Do**: Use coroutines for async work
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    serviceScope.launch {
        connectToVpn()
    }
    return START_STICKY
}
```

## Resources

- [Android VpnService Documentation](https://developer.android.com/reference/android/net/VpnService)
- [Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [Background Work Guide](https://developer.android.com/guide/background)
