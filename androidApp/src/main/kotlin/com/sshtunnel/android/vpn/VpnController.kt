package com.sshtunnel.android.vpn

import android.content.Context
import android.content.Intent
import com.sshtunnel.ssh.ConnectionState
import com.sshtunnel.ssh.SSHConnectionManager
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
 * Controller that manages VPN service lifecycle based on SSH connection state.
 * 
 * This controller observes the SSH connection state and automatically starts/stops
 * the VPN service when connections are established or terminated.
 * 
 * Integration features:
 * - Starts VPN when SSH connection is established
 * - Stops VPN when SSH connection is terminated
 * - Passes SOCKS5 port to VPN service
 * - Handles VPN errors and reports to Connection Manager
 * - Ensures VPN is stopped on SSH disconnection
 */
@Singleton
class VpnController @Inject constructor(
    private val context: Context,
    private val connectionManager: SSHConnectionManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isVpnActive = false
    private var currentSocksPort: Int = 0
    
    companion object {
        private const val TAG = "VpnController"
        private const val VPN_START_RETRY_DELAY_MS = 1000L
        private const val MAX_VPN_START_RETRIES = 3
    }
    
    private val vpnStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                TunnelVpnService.ACTION_VPN_ERROR -> {
                    android.util.Log.w(TAG, "VPN error received, marking as inactive")
                    isVpnActive = false
                    currentSocksPort = 0
                }
                TunnelVpnService.ACTION_VPN_STOPPED -> {
                    android.util.Log.i(TAG, "VPN stopped received, marking as inactive")
                    isVpnActive = false
                    currentSocksPort = 0
                }
                TunnelVpnService.ACTION_VPN_STARTED -> {
                    android.util.Log.i(TAG, "VPN started successfully")
                    isVpnActive = true
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
                        is ConnectionState.Connected -> state.connection
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collect { connection ->
                    if (connection != null) {
                        startVpn(connection.socksPort, connection.serverAddress)
                    } else {
                        stopVpn()
                    }
                }
        }
    }
    
    private fun startVpn(socksPort: Int, serverAddress: String) {
        if (isVpnActive && currentSocksPort == socksPort) {
            android.util.Log.d(TAG, "VPN already active with same SOCKS port, skipping start")
            return
        }
        
        // If VPN is active but with different SOCKS port, stop it first
        if (isVpnActive && currentSocksPort != socksPort) {
            android.util.Log.i(TAG, "VPN active with different SOCKS port, restarting")
            stopVpn()
        }
        
        android.util.Log.i(TAG, "Starting VPN service (SOCKS port: $socksPort, server: $serverAddress)")
        
        val intent = Intent(context, TunnelVpnService::class.java).apply {
            action = TunnelVpnService.ACTION_START
            putExtra(TunnelVpnService.EXTRA_SOCKS_PORT, socksPort)
            putExtra(TunnelVpnService.EXTRA_SERVER_ADDRESS, serverAddress)
        }
        
        try {
            context.startService(intent)
            isVpnActive = true
            currentSocksPort = socksPort
            android.util.Log.i(TAG, "VPN service start requested successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start VPN service: ${e.message}", e)
            isVpnActive = false
            currentSocksPort = 0
            
            // Report VPN error - this could trigger SSH disconnection if needed
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
            currentSocksPort = 0
            android.util.Log.i(TAG, "VPN service stop requested successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to stop VPN service: ${e.message}", e)
            // Even if stop fails, mark as inactive
            isVpnActive = false
            currentSocksPort = 0
        }
    }
    
    /**
     * Handles VPN errors by logging and potentially triggering SSH disconnection.
     * 
     * @param errorMessage The error message describing what went wrong
     */
    private fun handleVpnError(errorMessage: String) {
        android.util.Log.e(TAG, "VPN error: $errorMessage")
        
        // For now, we just log the error
        // In the future, we could:
        // - Trigger SSH disconnection if VPN fails critically
        // - Show user notification about VPN failure
        // - Attempt to restart VPN service
        
        // Optionally disconnect SSH if VPN fails
        scope.launch {
            android.util.Log.w(TAG, "VPN failed, disconnecting SSH connection")
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
     * Gets the current SOCKS port being used by VPN.
     * 
     * @return SOCKS port number, or 0 if VPN is not active
     */
    fun getCurrentSocksPort(): Int = currentSocksPort
    
    /**
     * Manually retries starting the VPN with the current connection.
     * This is useful when VPN permission is granted after initial failure.
     */
    fun retryVpnStart() {
        scope.launch {
            val state = connectionManager.observeConnectionState().value
            if (state is ConnectionState.Connected) {
                android.util.Log.i(TAG, "Manually retrying VPN start")
                startVpn(state.connection.socksPort, state.connection.serverAddress)
            } else {
                android.util.Log.w(TAG, "Cannot retry VPN start - no active SSH connection")
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
