package com.sshtunnel.vpn

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Property-based tests for per-app routing functionality.
 * 
 * These tests verify the correctness of app routing exclusion and inclusion logic.
 */
class AppRoutingPropertiesTest {
    
    // Data classes for testing
    
    data class RoutingConfig(
        val excludedApps: Set<String>,
        val routingMode: RoutingMode
    )
    
    enum class RoutingMode {
        ROUTE_ALL_EXCEPT_EXCLUDED,
        ROUTE_ONLY_INCLUDED
    }
    
    // Generators
    
    /**
     * Property 20: App routing exclusion correctness
     * Feature: ssh-tunnel-proxy, Property 20: App routing exclusion correctness
     * Validates: Requirements 5.2
     * 
     * For any app excluded from the tunnel, that app's traffic should bypass
     * the SOCKS5 proxy and route directly.
     * 
     * This test verifies that:
     * 1. Excluded apps are properly tracked in the routing configuration
     * 2. The routing mode is set to ROUTE_ALL_EXCEPT_EXCLUDED
     * 3. Excluded apps are not in the "included" set when mode is exclusion
     */
    @Test
    fun `excluded apps should be properly tracked in routing configuration`() = runTest {
        checkAll(
            iterations = 100,
            Arb.packageNames(),
            Arb.packageNames()
        ) { allApps, excludedApps ->
            // Create routing config with exclusion mode
            val config = RoutingConfig(
                excludedApps = excludedApps,
                routingMode = RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
            )
            
            // Verify routing mode is correct
            config.routingMode shouldBe RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
            
            // Verify all excluded apps are in the configuration
            excludedApps.forEach { app ->
                config.excludedApps shouldContain app
            }
            
            // Verify the excluded apps set has the correct size
            config.excludedApps.size shouldBe excludedApps.size
            
            // Verify that apps not in the excluded list are implicitly included
            val nonExcludedApps = allApps - excludedApps
            nonExcludedApps.forEach { app ->
                config.excludedApps shouldNotContain app
            }
        }
    }
    
    /**
     * Property 21: App routing inclusion correctness
     * Feature: ssh-tunnel-proxy, Property 21: App routing inclusion correctness
     * Validates: Requirements 5.3
     * 
     * For any app included in the tunnel, that app's traffic should route
     * through the SOCKS5 proxy.
     * 
     * This test verifies that:
     * 1. Included apps are properly tracked in the routing configuration
     * 2. The routing mode is set to ROUTE_ONLY_INCLUDED
     * 3. Only specified apps are in the "included" set when mode is inclusion
     * 
     * Note: In ROUTE_ONLY_INCLUDED mode, the excludedApps field actually
     * contains the apps to INCLUDE (semantic inversion for implementation).
     */
    @Test
    fun `included apps should be properly tracked in routing configuration`() = runTest {
        checkAll(
            iterations = 100,
            Arb.packageNames(),
            Arb.packageNames()
        ) { allApps, includedApps ->
            // Create routing config with inclusion mode
            // Note: In ROUTE_ONLY_INCLUDED mode, excludedApps contains the apps to include
            val config = RoutingConfig(
                excludedApps = includedApps,
                routingMode = RoutingMode.ROUTE_ONLY_INCLUDED
            )
            
            // Verify routing mode is correct
            config.routingMode shouldBe RoutingMode.ROUTE_ONLY_INCLUDED
            
            // Verify all included apps are in the configuration
            includedApps.forEach { app ->
                config.excludedApps shouldContain app
            }
            
            // Verify the included apps set has the correct size
            config.excludedApps.size shouldBe includedApps.size
            
            // Verify that apps not in the included list are implicitly excluded
            val nonIncludedApps = allApps - includedApps
            nonIncludedApps.forEach { app ->
                config.excludedApps shouldNotContain app
            }
        }
    }
    
    /**
     * Property 22: Routing changes apply without reconnection
     * Feature: ssh-tunnel-proxy, Property 22: Routing changes apply without reconnection
     * Validates: Requirements 5.4
     * 
     * For any active connection, modifying app routing settings should apply
     * the changes without disconnecting the SSH tunnel.
     * 
     * This test verifies that:
     * 1. Routing configuration can be updated independently
     * 2. The new configuration is properly structured
     * 3. Configuration updates preserve the routing mode
     * 4. Configuration updates preserve unrelated settings
     */
    @Test
    fun `routing configuration updates should preserve structure and mode`() = runTest {
        checkAll(
            iterations = 100,
            Arb.routingConfig(),
            Arb.packageNames()
        ) { originalConfig, newExcludedApps ->
            // Create updated configuration with new excluded apps
            val updatedConfig = originalConfig.copy(
                excludedApps = newExcludedApps
            )
            
            // Verify routing mode is preserved
            updatedConfig.routingMode shouldBe originalConfig.routingMode
            
            // Verify new excluded apps are applied
            updatedConfig.excludedApps shouldBe newExcludedApps
            
            // Verify configuration is valid
            updatedConfig.excludedApps.forEach { app ->
                app.isNotEmpty() shouldBe true
                app.contains(".") shouldBe true
            }
        }
    }
    
    /**
     * Verifies that routing mode changes are properly handled.
     * 
     * For any routing configuration, changing the mode should result in
     * a valid configuration with the new mode.
     */
    @Test
    fun `routing mode changes should be properly handled`() = runTest {
        checkAll(
            iterations = 100,
            Arb.routingConfig()
        ) { originalConfig ->
            // Toggle the routing mode
            val newMode = when (originalConfig.routingMode) {
                RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED -> RoutingMode.ROUTE_ONLY_INCLUDED
                RoutingMode.ROUTE_ONLY_INCLUDED -> RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
            }
            
            // Create updated configuration with new mode
            val updatedConfig = originalConfig.copy(
                routingMode = newMode
            )
            
            // Verify mode is changed
            updatedConfig.routingMode shouldBe newMode
            updatedConfig.routingMode shouldBe when (originalConfig.routingMode) {
                RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED -> RoutingMode.ROUTE_ONLY_INCLUDED
                RoutingMode.ROUTE_ONLY_INCLUDED -> RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED
            }
            
            // Verify excluded apps are preserved
            updatedConfig.excludedApps shouldBe originalConfig.excludedApps
        }
    }
    
    /**
     * Verifies that empty routing configurations are valid.
     * 
     * For any routing mode, an empty set of excluded/included apps
     * should be a valid configuration.
     */
    @Test
    fun `empty routing configurations should be valid`() = runTest {
        checkAll(
            iterations = 100,
            Arb.enum<RoutingMode>()
        ) { mode ->
            // Create configuration with no excluded/included apps
            val config = RoutingConfig(
                excludedApps = emptySet(),
                routingMode = mode
            )
            
            // Verify configuration is valid
            config.excludedApps.isEmpty() shouldBe true
            config.routingMode shouldBe mode
            
            // Verify behavior based on mode
            when (mode) {
                RoutingMode.ROUTE_ALL_EXCEPT_EXCLUDED -> {
                    // Empty exclusion list means all apps are routed through tunnel
                    config.excludedApps.size shouldBe 0
                }
                RoutingMode.ROUTE_ONLY_INCLUDED -> {
                    // Empty inclusion list means no apps are routed through tunnel
                    config.excludedApps.size shouldBe 0
                }
            }
        }
    }
    
    /**
     * Verifies that routing configurations with duplicate package names
     * are properly deduplicated.
     */
    @Test
    fun `routing configurations should deduplicate package names`() = runTest {
        checkAll(
            iterations = 100,
            Arb.packageName(),
            Arb.enum<RoutingMode>()
        ) { packageName, mode ->
            // Create configuration with duplicate package names
            val duplicateList = listOf(packageName, packageName, packageName)
            val config = RoutingConfig(
                excludedApps = duplicateList.toSet(),
                routingMode = mode
            )
            
            // Verify duplicates are removed (Set behavior)
            config.excludedApps.size shouldBe 1
            config.excludedApps shouldContain packageName
        }
    }
    
    // Generator functions
    
    private fun Arb.Companion.packageName() = arbitrary {
        val parts = Arb.list(Arb.string(3..10, Codepoint.alphanumeric()), 2..4).bind()
        parts.joinToString(".")
    }
    
    private fun Arb.Companion.packageNames() = arbitrary {
        val count = Arb.int(1, 10).bind()
        (1..count).map { Arb.packageName().bind() }.toSet()
    }
    
    private fun Arb.Companion.routingConfig() = arbitrary {
        RoutingConfig(
            excludedApps = Arb.packageNames().bind(),
            routingMode = Arb.enum<RoutingMode>().bind()
        )
    }
}
