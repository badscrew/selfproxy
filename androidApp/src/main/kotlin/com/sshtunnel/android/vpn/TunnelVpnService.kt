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
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * VPN service that routes device traffic through SSH tunnel via SOCKS5 proxy.
 * 
 * This service creates a TUN interface and routes all device traffic through
 * the SOCKS5 proxy created by the SSH connection.
 */
class TunnelVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var socksPort: Int = 0
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tun2socks: Tun2SocksEngine? = null
    
    companion object {
        private const val TAG = "TunnelVpnService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_service_channel"
        
        const val ACTION_START = "com.sshtunnel.android.vpn.START"
        const val ACTION_STOP = "com.sshtunnel.android.vpn.STOP"
        const val ACTION_VPN_ERROR = "com.sshtunnel.android.vpn.ERROR"
        const val ACTION_VPN_STARTED = "com.sshtunnel.android.vpn.STARTED"
        const val ACTION_VPN_STOPPED = "com.sshtunnel.android.vpn.STOPPED"
        
        const val EXTRA_SOCKS_PORT = "socks_port"
        const val EXTRA_SERVER_ADDRESS = "server_address"
        const val EXTRA_ERROR_MESSAGE = "error_message"
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
                startForeground(NOTIFICATION_ID, createNotification(serverAddress))
                
                // Start tun2socks engine
                val inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
                val outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)
                
                tun2socks = Tun2SocksEngine(inputStream, outputStream, socksPort)
                val startResult = tun2socks!!.start()
                
                if (startResult.isFailure) {
                    android.util.Log.e(TAG, "Failed to start tun2socks: ${startResult.exceptionOrNull()?.message}")
                    broadcastVpnError("Failed to start packet routing")
                    stopSelf()
                    return@launch
                }
                
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
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Broadcasts VPN started event.
     */
    private fun broadcastVpnStarted() {
        android.util.Log.d(TAG, "Broadcasting VPN started")
        val intent = Intent(ACTION_VPN_STARTED)
        sendBroadcast(intent)
    }
    
    /**
     * Broadcasts VPN stopped event.
     */
    private fun broadcastVpnStopped() {
        android.util.Log.d(TAG, "Broadcasting VPN stopped")
        val intent = Intent(ACTION_VPN_STOPPED)
        sendBroadcast(intent)
    }
    
    private fun stopVpn() {
        android.util.Log.i(TAG, "Stopping VPN service")
        
        try {
            // Stop tun2socks engine
            tun2socks?.stop()
            tun2socks = null
            android.util.Log.d(TAG, "Tun2socks engine stopped")
            
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
            tun2socks = null
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
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSH Tunnel Active")
            .setContentText("Connected to $serverAddress")
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
