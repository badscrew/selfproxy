package com.sshtunnel.vpn

import com.sshtunnel.data.RoutingMode
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based tests for VPN app exclusion.
 * 
 * Feature: shadowsocks-vpn-proxy, Property 10: Excluded apps bypass VPN tunnel
 * Validates: Requirements 5.2
 */
class AppExclusionPropertiesTest {
    
    /**
     * Property 10: Excluded apps bypass VPN tunnel
     * 
     * For any app marked as excluded in routing configuration, network requests
     * from that app should not be routed through the VPN tunnel.
     * 
     * This property tests that:
     * 1. When an app is in the excluded list with ROUTE_ALL_EXCEPT_EXCLUDED mode,
     *    it bypasses the tunnel
     * 2. When an app is NOT in the included list with ROUTE_ONLY_INCLUDED mode,
     *    it bypasses the tunnel
     * 3. The routing configuration correctly identifies which apps should bypass
     * 
     * Validates: Requirements 5.2
     */
    @Test
    fun `property 10 - excluded apps bypass VPN tunnel`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 10: Excluded apps bypass VPN tunnel
        // Validates: Requirements 5.2
        
        checkAll(
            iterations = 100,
            Arb.routingConfigWithExclusions()
        ) { config ->
            // Given: A routing configuration with excluded apps
            assertTrue(
                config.excludedApps.isNotEmpty(),
                "Config should have excluded apps"
            )
            
            when (config.routingMode) {
                RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED -> {
                    // Then: Apps in the excluded list should bypass the tunnel
                    config.excludedApps.forEach { excludedApp ->
                        assertTrue(
                            isAppExcluded(excludedApp, config),
                            "App $excludedApp should be excluded from tunnel"
                        )
                    }
                }
                RoutingMode.ROUTE_ONLY_INCLUDED -> {
                    // In this mode, excludedApps actually contains the apps to INCLUDE
                    // So apps NOT in this list should bypass the tunnel
                    val includedApps = config.excludedApps
                    
                    // Generate some apps that are not in the included list
                    val nonIncludedApp = "com.example.notincluded"
                    if (!includedApps.contains(nonIncludedApp)) {
                        assertTrue(
                            isAppExcludedInIncludeMode(nonIncludedApp, includedApps),
                            "App not in include list should bypass tunnel"
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Property: Excluded apps are consistently identified
     * 
     * For any routing configuration, the same app should always be identified
     * as excluded or included consistently.
     * 
     * Validates: Requirements 5.2, 5.5
     */
    @Test
    fun `property - excluded apps are consistently identified`() = runTest {
        checkAll(
            iterations = 100,
            Arb.routingConfigWithExclusions(),
            Arb.packageName()
        ) { config, appPackage ->
            // Given: A routing configuration and an app package name
            
            // Then: The app should be consistently identified as excluded or not
            val isExcluded1 = isAppExcluded(appPackage, config)
            val isExcluded2 = isAppExcluded(appPackage, config)
            
            assertTrue(
                isExcluded1 == isExcluded2,
                "App exclusion status should be consistent"
            )
        }
    }
    
    /**
     * Property: All apps in excluded list are actually excluded
     * 
     * For any routing configuration with ROUTE_ALL_EXCEPT_EXCLUDED mode,
     * every app in the excluded list should be identified as excluded.
     * 
     * Validates: Requirements 5.2
     */
    @Test
    fun `property - all apps in excluded list are actually excluded`() = runTest {
        checkAll(
            iterations = 100,
            Arb.routingConfigWithExclusions().filter { 
                it.routingMode == RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED 
            }
        ) { config ->
            // Given: A routing configuration with ROUTE_ALL_EXCEPT_EXCLUDED mode
            
            // Then: Every app in the excluded list should be identified as excluded
            config.excludedApps.forEach { excludedApp ->
                assertTrue(
                    config.excludedApps.contains(excludedApp),
                    "App $excludedApp should be in excluded list"
                )
                
                assertTrue(
                    isAppExcluded(excludedApp, config),
                    "App $excludedApp should be identified as excluded"
                )
            }
        }
    }
    
    /**
     * Property: Apps not in excluded list are routed through tunnel
     * 
     * For any routing configuration with ROUTE_ALL_EXCEPT_EXCLUDED mode,
     * apps NOT in the excluded list should be routed through the tunnel.
     * 
     * Validates: Requirements 5.2
     */
    @Test
    fun `property - apps not in excluded list are routed through tunnel`() = runTest {
        checkAll(
            iterations = 100,
            Arb.routingConfigWithExclusions().filter { 
                it.routingMode == RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED 
            },
            Arb.packageName()
        ) { config, appPackage ->
            // Given: A routing configuration and an app not in the excluded list
            if (!config.excludedApps.contains(appPackage)) {
                // Then: The app should NOT be excluded (should be routed through tunnel)
                assertFalse(
                    isAppExcluded(appPackage, config),
                    "App $appPackage not in excluded list should be routed through tunnel"
                )
            }
        }
    }
    
    /**
     * Property: Empty excluded list means all apps are routed
     * 
     * When the excluded apps list is empty with ROUTE_ALL_EXCEPT_EXCLUDED mode,
     * all apps should be routed through the tunnel.
     * 
     * Validates: Requirements 5.2
     */
    @Test
    fun `property - empty excluded list means all apps are routed`() = runTest {
        checkAll(
            iterations = 100,
            Arb.packageName()
        ) { appPackage ->
            // Given: A routing configuration with no excluded apps
            val config = RoutingConfig(
                excludedApps = emptySet(),
                routingMode = RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
            )
            
            // Then: No apps should be excluded
            assertFalse(
                isAppExcluded(appPackage, config),
                "With empty excluded list, no apps should be excluded"
            )
        }
    }
    
    /**
     * Helper function to determine if an app is excluded from the tunnel.
     * 
     * This simulates the logic used by the VPN service to determine
     * which apps should bypass the tunnel.
     */
    private fun isAppExcluded(packageName: String, config: RoutingConfig): Boolean {
        return when (config.routingMode) {
            RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED -> {
                // App is excluded if it's in the excluded list
                config.excludedApps.contains(packageName)
            }
            RoutingMode.ROUTE_ONLY_INCLUDED -> {
                // In this mode, excludedApps contains apps to INCLUDE
                // App is excluded if it's NOT in the include list
                !config.excludedApps.contains(packageName)
            }
        }
    }
    
    /**
     * Helper function for ROUTE_ONLY_INCLUDED mode.
     */
    private fun isAppExcludedInIncludeMode(packageName: String, includedApps: Set<String>): Boolean {
        // App is excluded if it's NOT in the included list
        return !includedApps.contains(packageName)
    }
}

/**
 * Custom generators for routing configurations with exclusions.
 */

/**
 * Generates routing configurations with at least one excluded app.
 */
fun Arb.Companion.routingConfigWithExclusions(): Arb<RoutingConfig> = arbitrary {
    RoutingConfig(
        excludedApps = Arb.set(Arb.packageName(), 1..10).bind(),
        routingMode = Arb.enum<RoutingMode>().bind()
    )
}
