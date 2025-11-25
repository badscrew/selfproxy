package com.sshtunnel.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of VpnTunnelProvider.
 * 
 * This class manages the lifecycle of the TunnelVpnService and provides
 * a bridge between the shared business logic and the Android VPN service.
 */
class AndroidVpnTunnelProvider(
    private val context: Context
) : VpnTunnelProvider {
    
    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Inactive)
    
    override suspend fun startTunnel(config: TunnelConfig): Result<Unit> {
        return try {
            // Check if VPN permission is granted
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                return Result.failure(
                    VpnError.PermissionDenied(
                        "VPN permission not granted. Please request permission first."
                    )
                )
            }
            
            // Check if tunnel is already active
            if (_tunnelState.value is TunnelState.Active) {
                return Result.failure(
                    VpnError.AlreadyActive("VPN tunnel is already active")
                )
            }
            
            // Update state
            _tunnelState.value = TunnelState.Starting
            
            // Start the VPN service
            val intent = Intent(context, TunnelVpnService::class.java).apply {
                action = TunnelVpnService.ACTION_START
                putExtra(TunnelVpnService.EXTRA_SOCKS_PORT, config.socksPort)
                putExtra(TunnelVpnService.EXTRA_DNS_SERVERS, config.dnsServers.toTypedArray())
                putExtra(TunnelVpnService.EXTRA_EXCLUDED_APPS, config.routingConfig.excludedApps.toTypedArray())
                putExtra(TunnelVpnService.EXTRA_ROUTING_MODE, config.routingConfig.routingMode.name)
                putExtra(TunnelVpnService.EXTRA_MTU, config.mtu)
                putExtra(TunnelVpnService.EXTRA_SESSION_NAME, config.sessionName)
            }
            
            context.startService(intent)
            
            // State will be updated by the service via callback
            Result.success(Unit)
            
        } catch (e: Exception) {
            _tunnelState.value = TunnelState.Error("Failed to start tunnel", e)
            Result.failure(VpnError.TunnelCreationFailed("Failed to start VPN tunnel", e))
        }
    }
    
    override suspend fun stopTunnel(): Result<Unit> {
        return try {
            if (_tunnelState.value is TunnelState.Inactive) {
                return Result.success(Unit) // Already stopped
            }
            
            _tunnelState.value = TunnelState.Stopping
            
            // Stop the VPN service
            val intent = Intent(context, TunnelVpnService::class.java).apply {
                action = TunnelVpnService.ACTION_STOP
            }
            
            context.startService(intent)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            _tunnelState.value = TunnelState.Error("Failed to stop tunnel", e)
            Result.failure(VpnError.Unknown("Failed to stop VPN tunnel", e))
        }
    }
    
    override suspend fun updateRouting(config: RoutingConfig): Result<Unit> {
        return try {
            if (_tunnelState.value !is TunnelState.Active) {
                return Result.failure(
                    VpnError.NotActive("Cannot update routing when tunnel is not active")
                )
            }
            
            // Update routing configuration
            val intent = Intent(context, TunnelVpnService::class.java).apply {
                action = TunnelVpnService.ACTION_UPDATE_ROUTING
                putExtra(TunnelVpnService.EXTRA_EXCLUDED_APPS, config.excludedApps.toTypedArray())
                putExtra(TunnelVpnService.EXTRA_ROUTING_MODE, config.routingMode.name)
            }
            
            context.startService(intent)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(VpnError.RoutingFailed("Failed to update routing", e))
        }
    }
    
    override fun observeTunnelState(): StateFlow<TunnelState> {
        return _tunnelState.asStateFlow()
    }
    
    override fun getCurrentState(): TunnelState {
        return _tunnelState.value
    }
    
    /**
     * Updates the tunnel state. Called by TunnelVpnService.
     */
    internal fun updateState(state: TunnelState) {
        _tunnelState.value = state
    }
    
    companion object {
        @Volatile
        private var instance: AndroidVpnTunnelProvider? = null
        
        /**
         * Gets the singleton instance of AndroidVpnTunnelProvider.
         */
        fun getInstance(context: Context): AndroidVpnTunnelProvider {
            return instance ?: synchronized(this) {
                instance ?: AndroidVpnTunnelProvider(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
