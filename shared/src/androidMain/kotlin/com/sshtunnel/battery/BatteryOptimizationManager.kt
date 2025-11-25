package com.sshtunnel.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages battery optimization settings and monitors battery state.
 * 
 * This class handles:
 * - Requesting battery optimization exemption
 * - Monitoring battery saver mode
 * - Monitoring battery level
 * - Providing battery state information
 */
class BatteryOptimizationManager(private val context: Context) {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    private val _batteryState = MutableStateFlow(getBatteryState())
    val batteryState: Flow<BatteryState> = _batteryState.asStateFlow()
    
    /**
     * Checks if the app is ignoring battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Not applicable on older versions
        }
    }
    
    /**
     * Creates an intent to request battery optimization exemption.
     * The caller should launch this intent to show the system dialog.
     */
    fun createBatteryOptimizationExemptionIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null // Not applicable on older versions
        }
    }
    
    /**
     * Checks if battery saver mode is currently enabled.
     */
    fun isBatterySaverEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
    }
    
    /**
     * Gets the current battery state.
     */
    fun getBatteryState(): BatteryState {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
        
        val batterySaverEnabled = isBatterySaverEnabled()
        
        return BatteryState(
            level = batteryPct,
            isCharging = isCharging,
            isBatterySaverEnabled = batterySaverEnabled,
            isLowBattery = batteryPct in 1..15 && !isCharging
        )
    }
    
    /**
     * Updates the battery state. Should be called when battery state changes.
     */
    fun updateBatteryState() {
        _batteryState.value = getBatteryState()
    }
}

/**
 * Represents the current battery state.
 */
data class BatteryState(
    val level: Int,
    val isCharging: Boolean,
    val isBatterySaverEnabled: Boolean,
    val isLowBattery: Boolean
)
