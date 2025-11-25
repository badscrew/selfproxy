package com.sshtunnel.battery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Manages low battery notifications.
 * 
 * Shows notifications when battery is low and the VPN tunnel is active,
 * offering the user the option to disconnect to save power.
 */
class LowBatteryNotificationManager(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "low_battery_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Creates the notification channel for low battery warnings.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Low Battery Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when battery is low while VPN is active"
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Shows a low battery notification.
     * 
     * @param batteryLevel Current battery level percentage
     * @param disconnectIntent Intent to disconnect the VPN tunnel
     */
    fun showLowBatteryNotification(batteryLevel: Int, disconnectIntent: Intent) {
        val disconnectPendingIntent = PendingIntent.getService(
            context,
            0,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Low Battery Warning")
            .setContentText("Battery at $batteryLevel%. Consider disconnecting VPN to save power.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPendingIntent
            )
            .build()
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Dismisses the low battery notification.
     */
    fun dismissNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
