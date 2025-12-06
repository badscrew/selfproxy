package com.sshtunnel.vpn

import com.sshtunnel.data.RoutingMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for VPN tunnel providers.
 * 
 * Platform-specific implementations handle VPN tunnel creation and packet routing
 * (Android VpnService, iOS NetworkExtension).
 */
interface VpnTunnelProvider {
    /**
     * Starts the VPN tunnel with the specified configuration.
     * 
     * @param config The tunnel configuration
     * @return Result indicating success or failure
     */
    suspend fun startTunnel(config: TunnelConfig): Result<Unit>
    
    /**
     * Stops the VPN tunnel and cleans up resources.
     * 
     * @return Result indicating success or failure
     */
    suspend fun stopTunnel(): Result<Unit>
    
    /**
     * Updates the routing configuration without restarting the tunnel.
     * 
     * @param config The new routing configuration
     * @return Result indicating success or failure
     */
    suspend fun updateRouting(config: RoutingConfig): Result<Unit>
    
    /**
     * Observes the current tunnel state.
     * 
     * @return StateFlow emitting tunnel state changes
     */
    fun observeTunnelState(): StateFlow<TunnelState>
    
    /**
     * Gets the current tunnel state.
     * 
     * @return The current tunnel state
     */
    fun getCurrentState(): TunnelState
}

/**
 * Configuration for the VPN tunnel.
 * 
 * @property socksPort Local port where SOCKS5 proxy is listening
 * @property dnsServers List of DNS servers to use (empty for system default)
 * @property routingConfig Configuration for app-specific routing
 * @property mtu Maximum transmission unit for the tunnel (default 1500)
 * @property sessionName Display name for the VPN session
 */
data class TunnelConfig(
    val socksPort: Int,
    val dnsServers: List<String> = listOf("8.8.8.8", "8.8.4.4"),
    val routingConfig: RoutingConfig = RoutingConfig(),
    val mtu: Int = 1500,
    val sessionName: String = "SSH Tunnel Proxy"
)

/**
 * Configuration for app-specific routing.
 * 
 * @property excludedApps Set of package names to exclude from the tunnel
 * @property routingMode The routing mode (exclude or include)
 */
data class RoutingConfig(
    val excludedApps: Set<String> = emptySet(),
    val routingMode: RoutingMode = RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
)

/**
 * Represents the current state of the VPN tunnel.
 */
sealed class TunnelState {
    /**
     * Tunnel is not active.
     */
    data object Inactive : TunnelState()
    
    /**
     * Tunnel is being started.
     */
    data object Starting : TunnelState()
    
    /**
     * Tunnel is active and routing traffic.
     */
    data object Active : TunnelState()
    
    /**
     * Tunnel is being stopped.
     */
    data object Stopping : TunnelState()
    
    /**
     * Tunnel encountered an error.
     * 
     * @property error The error message
     * @property cause The underlying cause
     */
    data class Error(val error: String, val cause: Throwable? = null) : TunnelState()
}

/**
 * VPN tunnel errors.
 */
sealed class VpnError : Exception() {
    data class PermissionDenied(override val message: String) : VpnError()
    data class TunnelCreationFailed(override val message: String, override val cause: Throwable? = null) : VpnError()
    data class RoutingFailed(override val message: String, override val cause: Throwable? = null) : VpnError()
    data class AlreadyActive(override val message: String) : VpnError()
    data class NotActive(override val message: String) : VpnError()
    data class Unknown(override val message: String, override val cause: Throwable? = null) : VpnError()
}
