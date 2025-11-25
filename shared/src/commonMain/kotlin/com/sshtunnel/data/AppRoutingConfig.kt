package com.sshtunnel.data

/**
 * Configuration for per-app traffic routing.
 * 
 * @property profileId The server profile this routing configuration is associated with
 * @property excludedPackages Set of Android package names to exclude from the tunnel
 * @property routingMode The routing mode determining how apps are routed
 */
data class AppRoutingConfig(
    val profileId: Long,
    val excludedPackages: Set<String>,
    val routingMode: RoutingMode
)

/**
 * Routing mode for app traffic.
 */
enum class RoutingMode {
    /**
     * Route all apps through the tunnel except those in the excluded list.
     */
    ROUTE_ALL_EXCEPT_EXCLUDED,
    
    /**
     * Only route apps in the included list through the tunnel.
     */
    ROUTE_ONLY_INCLUDED
}
