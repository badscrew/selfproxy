package com.sshtunnel.android.vpn

import android.content.Context
import android.content.Intent
import com.sshtunnel.ssh.ConnectionState
import com.sshtunnel.ssh.SSHConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 */
@Singleton
class VpnController @Inject constructor(
    private val context: Context,
    private val connectionManager: SSHConnectionManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isVpnActive = false
    
    companion object {
        private const val TAG = "VpnController"
    }
    
    init {
        observeConnectionState()
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
        if (isVpnActive) {
            android.util.Log.d(TAG, "VPN already active, skipping start")
            return
        }
        
        android.util.Log.i(TAG, "Starting VPN service (SOCKS port: $socksPort)")
        
        val intent = Intent(context, TunnelVpnService::class.java).apply {
            action = TunnelVpnService.ACTION_START
            putExtra(TunnelVpnService.EXTRA_SOCKS_PORT, socksPort)
            putExtra(TunnelVpnService.EXTRA_SERVER_ADDRESS, serverAddress)
        }
        
        try {
            context.startService(intent)
            isVpnActive = true
            android.util.Log.i(TAG, "VPN service start requested")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start VPN service: ${e.message}", e)
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
            android.util.Log.i(TAG, "VPN service stop requested")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to stop VPN service: ${e.message}", e)
        }
    }
}
