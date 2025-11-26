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
    private var packetRouter: PacketRouter? = null
    
    companion object {
        private const val TAG = "TunnelVpnService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_service_channel"
        
        const val ACTION_START = "com.sshtunnel.android.vpn.START"
        const val ACTION_STOP = "com.sshtunnel.android.vpn.STOP"
        const val EXTRA_SOCKS_PORT = "socks_port"
        const val EXTRA_SERVER_ADDRESS = "server_address"
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
        try {
            // Create TUN interface
            vpnInterface = createTunInterface()
            
            if (vpnInterface == null) {
                android.util.Log.e(TAG, "Failed to create TUN interface")
                stopSelf()
                return
            }
            
            // Start foreground service with notification
            startForeground(NOTIFICATION_ID, createNotification(serverAddress))
            
            // Start packet routing
            val inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)
            
            packetRouter = PacketRouter(inputStream, outputStream, socksPort)
            packetRouter?.start()
            
            android.util.Log.i(TAG, "VPN service started successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start VPN: ${e.message}", e)
            stopSelf()
        }
    }
    
    private fun stopVpn() {
        android.util.Log.i(TAG, "Stopping VPN service")
        
        // Stop packet routing
        packetRouter?.stop()
        packetRouter = null
        
        // Close VPN interface
        vpnInterface?.close()
        vpnInterface = null
        
        // Stop foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
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
