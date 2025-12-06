package com.sshtunnel.android.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.sshtunnel.android.MainActivity
import com.sshtunnel.android.R
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.Logger
import com.sshtunnel.reconnection.AutoReconnectService
import com.sshtunnel.reconnection.DisconnectReason
import com.sshtunnel.reconnection.ReconnectStatus
// TODO: Remove SSH imports - this file needs to be updated for Shadowsocks
// import com.sshtunnel.ssh.*
import com.sshtunnel.storage.CredentialStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.seconds

/**
 * VPN service that routes device traffic through SSH tunnel via SOCKS5 proxy.
 * 
 * This service creates a TUN interface and routes all device traffic through
 * the SOCKS5 proxy created by the SSH connection. It integrates with the native
 * SSH client to manage the SSH process lifecycle and handle process termination.
 */
class TunnelVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var socksPort: Int = 0
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var packetRouter: PacketRouter? = null
    
    // Native SSH integration
    private var sshClient: SSHClient? = null
    private var sshSession: SSHSession? = null
    private var currentProfile: ServerProfile? = null
    private var privateKeyPath: String? = null
    private var processMonitorJob: Job? = null
    
    // Connection monitoring and reconnection
    private var connectionMonitor: ConnectionMonitor? = null
    private var autoReconnectService: AutoReconnectService? = null
    private var connectionMonitorJob: Job? = null
    private var reconnectMonitorJob: Job? = null
    private var isReconnecting: Boolean = false
    private var reconnectionPolicy: com.sshtunnel.reconnection.ReconnectionPolicy = 
        com.sshtunnel.reconnection.ReconnectionPolicy.DEFAULT
    
    companion object {
        private const val TAG = "TunnelVpnService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_service_channel"
        private const val PROCESS_MONITOR_INTERVAL_MS = 1000L // Check every 1 second
        
        const val ACTION_START = "com.sshtunnel.android.vpn.START"
        const val ACTION_START_WITH_SSH = "com.sshtunnel.android.vpn.START_WITH_SSH"
        const val ACTION_STOP = "com.sshtunnel.android.vpn.STOP"
        const val ACTION_VPN_ERROR = "com.sshtunnel.android.vpn.ERROR"
        const val ACTION_VPN_STARTED = "com.sshtunnel.android.vpn.STARTED"
        const val ACTION_VPN_STOPPED = "com.sshtunnel.android.vpn.STOPPED"
        const val ACTION_SSH_PROCESS_TERMINATED = "com.sshtunnel.android.vpn.SSH_PROCESS_TERMINATED"
        const val ACTION_CONNECTION_LOST = "com.sshtunnel.android.vpn.CONNECTION_LOST"
        const val ACTION_RECONNECTING = "com.sshtunnel.android.vpn.RECONNECTING"
        const val ACTION_RECONNECTED = "com.sshtunnel.android.vpn.RECONNECTED"
        
        const val EXTRA_SOCKS_PORT = "socks_port"
        const val EXTRA_SERVER_ADDRESS = "server_address"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PASSPHRASE = "passphrase"
        const val EXTRA_RECONNECT_ATTEMPT = "reconnect_attempt"
        const val EXTRA_DISCONNECT_REASON = "disconnect_reason"
        
        // Connection loss detection timeout (Requirement 8.3)
        private const val CONNECTION_LOSS_DETECTION_TIMEOUT_MS = 5000L
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                socksPort = intent.getIntExtra(EXTRA_SOCKS_PORT, 0)
                val serverAddress = intent.getStringExtra(EXTRA_SERVER_ADDRESS) ?: "Unknown"
                
                if (socksPort == 0) {
                    android.util.Log.e(TAG, "Invalid SOCKS port: $socksPort")
                    stopSelf()
                    return START_NOT_STICKY
                }
                
                startVpn(serverAddress)
            }
            ACTION_START_WITH_SSH -> {
                // Start VPN with native SSH integration
                val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
                val passphrase = intent.getStringExtra(EXTRA_PASSPHRASE)
                
                if (profileId == 0L) {
                    android.util.Log.e(TAG, "Invalid profile ID: $profileId")
                    stopSelf()
                    return START_NOT_STICKY
                }
                
                startVpnWithSSH(profileId, passphrase)
            }
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    private fun startVpn(serverAddress: String) {
        serviceScope.launch {
            try {
                android.util.Log.i(TAG, "Starting VPN with SOCKS port $socksPort")
                
                // Verify SOCKS proxy is reachable before starting VPN
                if (!verifySocksProxy()) {
                    android.util.Log.e(TAG, "SOCKS proxy not reachable on port $socksPort")
                    broadcastVpnError("SOCKS proxy not reachable")
                    stopSelf()
                    return@launch
                }
                
                // Create TUN interface
                vpnInterface = createTunInterface()
                
                if (vpnInterface == null) {
                    android.util.Log.e(TAG, "Failed to create TUN interface - VPN permission may be revoked")
                    broadcastVpnError("Failed to create VPN interface")
                    stopSelf()
                    return@launch
                }
                
                android.util.Log.i(TAG, "TUN interface created successfully")
                
                // Start foreground service with notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(serverAddress),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(serverAddress))
                }
                
                // Start packet routing
                val inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
                val outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)
                
                packetRouter = PacketRouter(inputStream, outputStream, socksPort)
                packetRouter?.start()
                
                android.util.Log.i(TAG, "VPN service started successfully - routing traffic through SOCKS port $socksPort")
                broadcastVpnStarted()
                
            } catch (e: SecurityException) {
                android.util.Log.e(TAG, "Security exception starting VPN - permission denied: ${e.message}", e)
                broadcastVpnError("VPN permission denied")
                stopSelf()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start VPN: ${e.message}", e)
                broadcastVpnError("Failed to start VPN: ${e.message}")
                stopSelf()
            }
        }
    }
    
    /**
     * Verifies that the SOCKS proxy is reachable before starting VPN.
     * This prevents starting VPN when SSH connection is not ready.
     */
    private suspend fun verifySocksProxy(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", socksPort), 2000)
                socket.close()
                android.util.Log.d(TAG, "SOCKS proxy verification successful")
                true
            } catch (e: Exception) {
                android.util.Log.w(TAG, "SOCKS proxy verification failed: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Broadcasts VPN error to interested components.
     */
    private fun broadcastVpnError(errorMessage: String) {
        android.util.Log.e(TAG, "Broadcasting VPN error: $errorMessage")
        val intent = Intent(ACTION_VPN_ERROR).apply {
            setPackage(packageName) // Make broadcast explicit for Android 8.0+
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Broadcasts VPN started event.
     */
    private fun broadcastVpnStarted() {
        android.util.Log.d(TAG, "Broadcasting VPN started")
        val intent = Intent(ACTION_VPN_STARTED).apply {
            setPackage(packageName) // Make broadcast explicit for Android 8.0+
        }
        sendBroadcast(intent)
    }
    
    /**
     * Broadcasts VPN stopped event.
     */
    private fun broadcastVpnStopped() {
        android.util.Log.d(TAG, "Broadcasting VPN stopped")
        val intent = Intent(ACTION_VPN_STOPPED).apply {
            setPackage(packageName) // Make broadcast explicit for Android 8.0+
        }
        sendBroadcast(intent)
    }
    
    /**
     * Starts VPN with native SSH integration.
     * This method handles the complete lifecycle:
     * 1. Load server profile
     * 2. Create SSH client using factory
     * 3. Write private key to disk
     * 4. Start SSH tunnel
     * 5. Start VPN with SOCKS5 proxy
     * 6. Monitor SSH process
     */
    private fun startVpnWithSSH(profileId: Long, passphrase: String?) {
        serviceScope.launch {
            try {
                android.util.Log.i(TAG, "Starting VPN with native SSH for profile $profileId")
                
                // TODO: Load profile from repository
                // For now, this is a placeholder - the actual implementation will need
                // to inject ProfileRepository and load the profile
                android.util.Log.w(TAG, "Profile loading not yet implemented - using legacy flow")
                stopSelf()
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start VPN with SSH: ${e.message}", e)
                broadcastVpnError("Failed to start VPN with SSH: ${e.message}")
                cleanupSSH()
                stopSelf()
            }
        }
    }
    
    /**
     * Starts connection monitoring using ConnectionMonitor.
     * This implements Requirements 8.3 and 8.4 for connection loss detection
     * and automatic reconnection.
     */
    private fun startConnectionMonitoring(session: SSHSession, monitor: ConnectionMonitor) {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = serviceScope.launch {
            try {
                android.util.Log.i(TAG, "Starting connection health monitoring")
                
                // Get the process from the session
                val session = sshSession
                if (session == null) {
                    android.util.Log.w(TAG, "Cannot monitor connection: no active session")
                    return@launch
                }
                
                val process = (sshClient as? AndroidNativeSSHClient)?.getProcess(session.sessionId)
                if (process == null) {
                    android.util.Log.w(TAG, "Cannot monitor connection: process not available (may be using sshj)")
                    return@launch
                }
                
                val startTime = System.currentTimeMillis()
                
                monitor.monitorConnection(process, socksPort).collectLatest { state ->
                    when (state) {
                        is ConnectionHealthState.Healthy -> {
                            android.util.Log.d(TAG, "Connection healthy")
                            // Reset reconnecting flag on healthy connection
                            if (isReconnecting) {
                                isReconnecting = false
                                broadcastReconnected()
                            }
                        }
                        is ConnectionHealthState.Unhealthy -> {
                            val elapsedTime = System.currentTimeMillis() - startTime
                            android.util.Log.w(TAG, "Connection unhealthy: ${state.message}")
                            
                            // Detect connection loss within 5 seconds (Requirement 8.3)
                            if (elapsedTime <= CONNECTION_LOSS_DETECTION_TIMEOUT_MS) {
                                android.util.Log.w(TAG, "Connection loss detected within 5 seconds")
                                broadcastConnectionLost(DisconnectReason.ConnectionLost)
                                
                                // Trigger automatic reconnection (Requirement 8.4)
                                if (!isReconnecting) {
                                    triggerReconnection(DisconnectReason.ConnectionLost)
                                }
                            }
                        }
                        is ConnectionHealthState.Disconnected -> {
                            android.util.Log.w(TAG, "Connection disconnected")
                            broadcastConnectionLost(DisconnectReason.ConnectionLost)
                            
                            // Trigger automatic reconnection
                            if (!isReconnecting) {
                                triggerReconnection(DisconnectReason.ConnectionLost)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                android.util.Log.d(TAG, "Connection monitoring cancelled")
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in connection monitoring: ${e.message}", e)
                broadcastVpnError("Connection monitoring failed: ${e.message}")
            }
        }
    }
    
    /**
     * Starts monitoring reconnection attempts from AutoReconnectService.
     */
    private fun startReconnectMonitoring() {
        reconnectMonitorJob?.cancel()
        reconnectMonitorJob = serviceScope.launch {
            try {
                android.util.Log.i(TAG, "Starting reconnection attempt monitoring")
                
                autoReconnectService?.observeReconnectAttempts()?.collectLatest { attemptWithStatus ->
                    val attempt = attemptWithStatus.attempt
                    val status = attemptWithStatus.status
                    
                    android.util.Log.i(TAG, "Reconnect attempt ${attempt.attemptNumber}: $status")
                    
                    when (status) {
                        is ReconnectStatus.Attempting -> {
                            android.util.Log.i(TAG, "Attempting reconnection (attempt ${attempt.attemptNumber})")
                            broadcastReconnecting(attempt.attemptNumber)
                        }
                        is ReconnectStatus.Success -> {
                            android.util.Log.i(TAG, "Reconnection successful")
                            isReconnecting = false
                            broadcastReconnected()
                        }
                        is ReconnectStatus.Failed -> {
                            android.util.Log.w(TAG, "Reconnection failed: ${status.error}")
                            // Will retry automatically with exponential backoff
                        }
                        is ReconnectStatus.Cancelled -> {
                            android.util.Log.i(TAG, "Reconnection cancelled")
                            isReconnecting = false
                        }
                    }
                }
            } catch (e: CancellationException) {
                android.util.Log.d(TAG, "Reconnect monitoring cancelled")
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in reconnect monitoring: ${e.message}", e)
            }
        }
    }
    
    /**
     * Triggers automatic reconnection with exponential backoff.
     * Implements Requirement 8.4.
     */
    private fun triggerReconnection(reason: DisconnectReason) {
        serviceScope.launch {
            try {
                // Check if reconnection is enabled in policy
                if (!reconnectionPolicy.enabled) {
                    android.util.Log.i(TAG, "Automatic reconnection is disabled by policy")
                    stopVpn()
                    return@launch
                }
                
                // Check if we should reconnect for this specific reason
                when (reason) {
                    DisconnectReason.NetworkChanged -> {
                        if (!reconnectionPolicy.reconnectOnNetworkChange) {
                            android.util.Log.i(TAG, "Reconnection on network change is disabled")
                            return@launch
                        }
                    }
                    DisconnectReason.KeepAliveFailed -> {
                        if (!reconnectionPolicy.reconnectOnKeepAliveFail) {
                            android.util.Log.i(TAG, "Reconnection on keep-alive failure is disabled")
                            return@launch
                        }
                    }
                    DisconnectReason.UserDisconnected -> {
                        android.util.Log.i(TAG, "User disconnected - not reconnecting")
                        return@launch
                    }
                    else -> {
                        // Reconnect for other reasons
                    }
                }
                
                isReconnecting = true
                android.util.Log.i(TAG, "Triggering automatic reconnection due to: $reason")
                
                val profile = currentProfile
                if (profile == null) {
                    android.util.Log.e(TAG, "Cannot reconnect - no profile available")
                    isReconnecting = false
                    stopVpn()
                    return@launch
                }
                
                // Enable auto-reconnect service if available
                autoReconnectService?.enable(profile, null)
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to trigger reconnection: ${e.message}", e)
                isReconnecting = false
                broadcastVpnError("Reconnection failed: ${e.message}")
            }
        }
    }
    
    /**
     * Monitors the SSH process and restarts it if it terminates unexpectedly.
     * This implements Requirements 14.2 and 14.3.
     */
    private fun startProcessMonitoring(session: SSHSession) {
        processMonitorJob?.cancel()
        processMonitorJob = serviceScope.launch {
            try {
                android.util.Log.i(TAG, "Starting SSH process monitoring")
                
                while (isActive) {
                    delay(PROCESS_MONITOR_INTERVAL_MS)
                    
                    // Check if SSH process is still alive
                    val isAlive = sshClient?.isSessionAlive(session) ?: false
                    
                    if (!isAlive) {
                        android.util.Log.w(TAG, "SSH process terminated unexpectedly")
                        broadcastProcessTerminated()
                        
                        // Trigger reconnection instead of direct restart
                        if (!isReconnecting) {
                            triggerReconnection(DisconnectReason.ConnectionLost)
                        }
                        break
                    }
                }
            } catch (e: CancellationException) {
                android.util.Log.d(TAG, "Process monitoring cancelled")
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in process monitoring: ${e.message}", e)
                broadcastVpnError("SSH process monitoring failed: ${e.message}")
            }
        }
    }
    
    /**
     * Restarts the SSH connection after process termination.
     */
    private suspend fun restartSSH() {
        try {
            val profile = currentProfile
            if (profile == null) {
                android.util.Log.e(TAG, "Cannot restart SSH - no profile available")
                stopVpn()
                return
            }
            
            android.util.Log.i(TAG, "Restarting SSH connection to ${profile.hostname}")
            
            // Clean up old SSH resources
            cleanupSSH()
            
            // TODO: Restart SSH connection
            // This will be implemented when we have the full SSH client integration
            android.util.Log.w(TAG, "SSH restart not yet fully implemented")
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to restart SSH: ${e.message}", e)
            broadcastVpnError("Failed to restart SSH: ${e.message}")
            stopVpn()
        }
    }
    
    /**
     * Cleans up SSH resources including private key files and SSH session.
     */
    private fun cleanupSSH() {
        android.util.Log.d(TAG, "Cleaning up SSH resources")
        
        try {
            // Stop all monitoring jobs
            processMonitorJob?.cancel()
            processMonitorJob = null
            
            connectionMonitorJob?.cancel()
            connectionMonitorJob = null
            
            reconnectMonitorJob?.cancel()
            reconnectMonitorJob = null
            
            // Disable auto-reconnect
            serviceScope.launch {
                autoReconnectService?.disable()
            }
            
            // Disconnect SSH session
            val session = sshSession
            if (session != null) {
                serviceScope.launch {
                    sshClient?.disconnect(session)
                }
                sshSession = null
                android.util.Log.d(TAG, "SSH session disconnected")
            }
            
            // Delete private key file
            val keyPath = privateKeyPath
            if (keyPath != null) {
                try {
                    val keyFile = java.io.File(keyPath)
                    if (keyFile.exists()) {
                        keyFile.delete()
                        android.util.Log.d(TAG, "Private key file deleted: $keyPath")
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to delete private key file: ${e.message}")
                }
                privateKeyPath = null
            }
            
            // Clear references
            sshClient = null
            currentProfile = null
            connectionMonitor = null
            autoReconnectService = null
            isReconnecting = false
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error during SSH cleanup: ${e.message}", e)
        }
    }
    
    /**
     * Broadcasts SSH process termination event.
     */
    private fun broadcastProcessTerminated() {
        android.util.Log.w(TAG, "Broadcasting SSH process terminated")
        val intent = Intent(ACTION_SSH_PROCESS_TERMINATED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Broadcasts connection lost event.
     */
    private fun broadcastConnectionLost(reason: DisconnectReason) {
        android.util.Log.w(TAG, "Broadcasting connection lost: $reason")
        val intent = Intent(ACTION_CONNECTION_LOST).apply {
            setPackage(packageName)
            putExtra(EXTRA_DISCONNECT_REASON, reason.toString())
        }
        sendBroadcast(intent)
    }
    
    /**
     * Broadcasts reconnecting event.
     */
    private fun broadcastReconnecting(attemptNumber: Int) {
        android.util.Log.i(TAG, "Broadcasting reconnecting (attempt $attemptNumber)")
        val intent = Intent(ACTION_RECONNECTING).apply {
            setPackage(packageName)
            putExtra(EXTRA_RECONNECT_ATTEMPT, attemptNumber)
        }
        sendBroadcast(intent)
        
        // Update notification to show reconnecting state
        currentProfile?.let { profile ->
            updateNotification(profile.hostname)
        }
    }
    
    /**
     * Broadcasts reconnected event.
     */
    private fun broadcastReconnected() {
        android.util.Log.i(TAG, "Broadcasting reconnected")
        val intent = Intent(ACTION_RECONNECTED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        
        // Update notification to show connected state
        currentProfile?.let { profile ->
            updateNotification(profile.hostname)
        }
    }
    
    private fun stopVpn() {
        android.util.Log.i(TAG, "Stopping VPN service")
        
        try {
            // Clean up SSH resources first
            cleanupSSH()
            
            // Stop packet routing
            packetRouter?.stop()
            packetRouter = null
            android.util.Log.d(TAG, "Packet router stopped")
            
            // Close VPN interface
            vpnInterface?.close()
            vpnInterface = null
            android.util.Log.d(TAG, "VPN interface closed")
            
            // Stop foreground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            
            android.util.Log.i(TAG, "VPN service stopped successfully")
            broadcastVpnStopped()
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping VPN service: ${e.message}", e)
            // Ensure cleanup even if errors occur
            cleanupSSH()
            packetRouter = null
            vpnInterface = null
        }
    }
    
    private fun createTunInterface(): ParcelFileDescriptor? {
        return Builder()
            .setSession("SSH Tunnel")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            .setMtu(1500)
            .setBlocking(true)
            .setConfigureIntent(createConfigIntent())
            .apply {
                // Exclude this app from VPN to prevent routing loops
                try {
                    addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to exclude own app: ${e.message}")
                }
            }
            .establish()
    }
    
    private fun createConfigIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when VPN tunnel is active"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(serverAddress: String): Notification {
        val disconnectIntent = Intent(this, TunnelVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            0,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val contentText = if (isReconnecting) {
            "Reconnecting to $serverAddress..."
        } else {
            "Connected to $serverAddress"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSH Tunnel Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_vpn)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createConfigIntent())
            .addAction(
                R.drawable.ic_close,
                "Disconnect",
                disconnectPendingIntent
            )
            .build()
    }
    
    /**
     * Updates the notification to reflect current connection state.
     */
    private fun updateNotification(serverAddress: String) {
        val notification = createNotification(serverAddress)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Configures the reconnection policy.
     * This allows customization of reconnection behavior.
     */
    fun setReconnectionPolicy(policy: com.sshtunnel.reconnection.ReconnectionPolicy) {
        reconnectionPolicy = policy
        android.util.Log.i(TAG, "Reconnection policy updated: enabled=${policy.enabled}, maxAttempts=${policy.maxAttempts}")
    }
    

    
    override fun onRevoke() {
        android.util.Log.i(TAG, "VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }
    
    override fun onDestroy() {
        android.util.Log.i(TAG, "VPN service destroyed")
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
}
