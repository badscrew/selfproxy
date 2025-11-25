package com.sshtunnel.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Monitors battery state changes and provides a Flow of battery events.
 */
class BatteryMonitor(private val context: Context) {
    
    /**
     * Observes battery state changes.
     * Emits events when:
     * - Battery level changes
     * - Charging state changes
     * - Battery saver mode changes
     */
    fun observeBatteryChanges(): Flow<BatteryEvent> = callbackFlow {
        val batteryOptimizationManager = BatteryOptimizationManager(context)
        
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_LOW -> {
                        trySend(BatteryEvent.LowBattery)
                    }
                    Intent.ACTION_BATTERY_OKAY -> {
                        trySend(BatteryEvent.BatteryOkay)
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        trySend(BatteryEvent.PowerConnected)
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        trySend(BatteryEvent.PowerDisconnected)
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        val batterySaverEnabled = batteryOptimizationManager.isBatterySaverEnabled()
                        trySend(
                            if (batterySaverEnabled) {
                                BatteryEvent.BatterySaverEnabled
                            } else {
                                BatteryEvent.BatterySaverDisabled
                            }
                        )
                    }
                }
            }
        }
        
        // Register receivers
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
        }
        
        context.registerReceiver(batteryReceiver, filter)
        
        awaitClose {
            context.unregisterReceiver(batteryReceiver)
        }
    }
}

/**
 * Battery-related events.
 */
sealed class BatteryEvent {
    object LowBattery : BatteryEvent()
    object BatteryOkay : BatteryEvent()
    object PowerConnected : BatteryEvent()
    object PowerDisconnected : BatteryEvent()
    object BatterySaverEnabled : BatteryEvent()
    object BatterySaverDisabled : BatteryEvent()
}
