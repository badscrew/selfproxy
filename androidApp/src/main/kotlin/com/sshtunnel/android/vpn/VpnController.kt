package com.sshtunnel.android.vpn

import android.content.Context
import android.content.Intent
import com.sshtunnel.connection.ConnectionManager
import com.sshtunnel.data.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller that manages VPN service lifecycle based on Shadowsocks connection state.
 * 
 * This controller observes the connection state and automatically starts/stops
 * the VPN service when connections are established or terminated.
 * 
 * Integration features:
 * - Starts VPN when Shadowsocks connection is established
 * - Stops VPN when connection is terminated
 * - Handles VPN errors and reports to Connection Manager
 * - Ensures VPN is stopped on disconnection
 */
@Singleton
class VpnController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isVpnActive = false
    
    private val _vpnActiveState = kotlinx.coroutines.flow.MutableStateFlow(false)
    val vpnActiveState: kotlinx.coroutines.flow.StateFlow<Boolean> = _vpnActiveState
    
    companion object {
        private const val TAG = "VpnController"
    }
    
    private val vpnStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                TunnelVpnService.ACTION_VPN_ERROR -> {
                    android.util.Log.w(TAG, "VPN error received, marking as inactive")
                    isVpnActive = false
                    _vpnActiveState.value = false
                }
                TunnelVpnService.ACTION_VPN_STOPPED -> {
                    android.util.Log.i(TAG, "VPN stopped received, marking as inactive")
                    isVpnActive = false
                    _vpnActiveState.value = false
                }
                TunnelVpnService.ACTION_VPN_STARTED -> {
                    android.util.Log.i(TAG, "VPN started successfully")
                    isVpnActive = true
                    _vpnActiveState.value = true
                }
            }
        }
    }
    
    init {
        observeConnectionState()
        registerVpnStateReceiver()
    }
    
    private fun observeConnectionState() {
        scope.launch {
            connectionManager.observeConnectionState()
                .map { state ->
                    when (state) {
                        is ConnectionState.Connected -> state.serverAddress
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collect { serverAddress ->
                    if (serverAddress != null) {
                        startVpn(serverAddress)
                    } else {
                        stopVpn()
                    }
                }
        }
    }
    
    private fun startVpn(serverAddress: String) {
        if (isVpnActive) {
            android.util.Log.d(TAG, "VPN already active, skipping start")
            return
        }
        
        android.util.Log.i(TAG, "Starting VPN service (server: $serverAddress)")
        
        val intent = Intent(context, TunnelVpnService::class.java).apply {
            action = TunnelVpnService.ACTION_START
            putExtra(TunnelVpnService.EXTRA_SERVER_ADDRESS, serverAddress)
        }
        
        try {
            context.startService(intent)
            isVpnActive = true
            android.util.Log.i(TAG, "VPN service start requested successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start VPN service: ${e.message}", e)
            isVpnActive = false
            
            // Report VPN error - this could trigger disconnection if needed
            handleVpnError("Failed to start VPN service: ${e.message}")
        }
    }
    
    private fun stopVpn() {
        if (!isVpnActive) {
            android.util.Log.d(TAG, "VPN not active, skipping stop")
            return
        }
        
        android.util.Log.i(TAG, "Stopping VPN service")
        
        val intent = Intent(context, TunnelVpnService::class.java).apply {
            action = TunnelVpnService.ACTION_STOP
        }
        
        try {
            context.startService(intent)
            isVpnActive = false
            android.util.Log.i(TAG, "VPN service stop requested successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to stop VPN service: ${e.message}", e)
            // Even if stop fails, mark as inactive
            isVpnActive = false
        }
    }
    
    /**
     * Handles VPN errors by logging and potentially triggering disconnection.
     * 
     * @param errorMessage The error message describing what went wrong
     */
    private fun handleVpnError(errorMessage: String) {
        android.util.Log.e(TAG, "VPN error: $errorMessage")
        
        // For now, we just log the error
        // In the future, we could:
        // - Trigger disconnection if VPN fails critically
        // - Show user notification about VPN failure
        // - Attempt to restart VPN service
        
        // Optionally disconnect if VPN fails
        scope.launch {
            android.util.Log.w(TAG, "VPN failed, disconnecting")
            connectionManager.disconnect()
        }
    }
    
    /**
     * Checks if VPN service is currently active.
     * 
     * @return true if VPN is active, false otherwise
     */
    fun isVpnActive(): Boolean = isVpnActive
    
    /**
     * Manually retries starting the VPN with the current connection.
     * This is useful when VPN permission is granted after initial failure.
     */
    fun retryVpnStart() {
        scope.launch {
            val state = connectionManager.getCurrentState()
            if (state is ConnectionState.Connected) {
                android.util.Log.i(TAG, "Manually retrying VPN start after permission granted")
                // Reset VPN state before retry to ensure clean start
                isVpnActive = false
                // Small delay to ensure previous service instance is fully stopped
                delay(500)
                startVpn(state.serverAddress)
            } else {
                android.util.Log.w(TAG, "Cannot retry VPN start - no active connection (state: $state)")
            }
        }
    }
    
    private fun registerVpnStateReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(TunnelVpnService.ACTION_VPN_ERROR)
            addAction(TunnelVpnService.ACTION_VPN_STOPPED)
            addAction(TunnelVpnService.ACTION_VPN_STARTED)
        }
        context.registerReceiver(vpnStateReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        android.util.Log.d(TAG, "VPN state receiver registered")
    }
}
