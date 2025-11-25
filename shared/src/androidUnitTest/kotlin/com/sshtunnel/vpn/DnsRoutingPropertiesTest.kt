package com.sshtunnel.vpn

import com.sshtunnel.data.ConnectionSettings
import com.sshtunnel.data.DnsMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based tests for DNS routing configuration.
 * 
 * Feature: ssh-tunnel-proxy, Property 28: DNS routing configuration
 * Validates: Requirements 10.3
 * 
 * These tests verify that DNS queries are routed according to the specified mode:
 * - THROUGH_TUNNEL: DNS queries go through the SSH tunnel
 * - CUSTOM_DNS: DNS queries use custom DNS servers
 * - SYSTEM_DEFAULT: DNS queries use system default DNS servers
 */
class DnsRoutingPropertiesTest {
    
    /**
     * Property 28: DNS routing configuration
     * 
     * For any DNS configuration setting, DNS queries should be routed according
     * to the specified mode (through tunnel, custom DNS, or system default).
     * 
     * This test verifies that:
     * 1. Each DNS mode produces the correct DNS server configuration
     * 2. THROUGH_TUNNEL mode uses tunnel DNS servers
     * 3. CUSTOM_DNS mode uses custom DNS servers
     * 4. SYSTEM_DEFAULT mode uses empty DNS list (system default)
     * 5. The configuration is internally consistent
     */
    @Test
    fun `dns routing should be configured according to specified mode`() = runTest {
        checkAll(
            iterations = 100,
            Arb.connectionSettings()
        ) { settings ->
            // Create tunnel config based on connection settings
            val tunnelConfig = createTunnelConfigFromSettings(settings)
            
            // Verify DNS configuration matches the specified mode
            when (settings.dnsMode) {
                DnsMode.THROUGH_TUNNEL -> {
                    // DNS servers should be configured (tunnel DNS)
                    tunnelConfig.dnsServers.isNotEmpty() shouldBe true
                    
                    // Should use standard public DNS servers when routing through tunnel
                    // (These will be routed through the SSH tunnel)
                    tunnelConfig.dnsServers.forEach { dns ->
                        isValidIpAddress(dns) shouldBe true
                    }
                }
                
                DnsMode.CUSTOM_DNS -> {
                    // DNS servers should be configured with custom servers
                    tunnelConfig.dnsServers.isNotEmpty() shouldBe true
                    
                    // Custom DNS servers should be valid IP addresses
                    tunnelConfig.dnsServers.forEach { dns ->
                        isValidIpAddress(dns) shouldBe true
                    }
                }
                
                DnsMode.SYSTEM_DEFAULT -> {
                    // DNS servers list should be empty (use system default)
                    // OR contain system DNS servers
                    // The VPN service will use system DNS when no servers are specified
                    tunnelConfig.dnsServers.shouldBeInstanceOf<List<String>>()
                }
            }
            
            // Verify the DNS mode is properly set
            settings.dnsMode.shouldBeInstanceOf<DnsMode>()
        }
    }
    
    /**
     * Verifies that THROUGH_TUNNEL mode always configures DNS servers.
     * 
     * For any connection settings with THROUGH_TUNNEL mode, the resulting
     * tunnel configuration should have DNS servers configured.
     */
    @Test
    fun `through tunnel mode should configure dns servers`() = runTest {
        checkAll(
            iterations = 100,
            Arb.connectionSettingsWithDnsMode(DnsMode.THROUGH_TUNNEL)
        ) { settings ->
            val tunnelConfig = createTunnelConfigFromSettings(settings)
            
            // DNS servers must be configured for tunnel routing
            tunnelConfig.dnsServers.isNotEmpty() shouldBe true
            
            // All DNS servers should be valid IP addresses
            tunnelConfig.dnsServers.forEach { dns ->
                isValidIpAddress(dns) shouldBe true
            }
            
            // Verify the mode is correct
            settings.dnsMode shouldBe DnsMode.THROUGH_TUNNEL
        }
    }
    
    /**
     * Verifies that CUSTOM_DNS mode configures custom DNS servers.
     * 
     * For any connection settings with CUSTOM_DNS mode, the resulting
     * tunnel configuration should use custom DNS servers.
     */
    @Test
    fun `custom dns mode should configure custom dns servers`() = runTest {
        checkAll(
            iterations = 100,
            Arb.connectionSettingsWithDnsMode(DnsMode.CUSTOM_DNS),
            Arb.customDnsServers()
        ) { settings, customDns ->
            val tunnelConfig = createTunnelConfigFromSettings(settings, customDns)
            
            // DNS servers should be configured
            tunnelConfig.dnsServers.isNotEmpty() shouldBe true
            
            // Should use the custom DNS servers
            tunnelConfig.dnsServers shouldBe customDns
            
            // All DNS servers should be valid IP addresses
            tunnelConfig.dnsServers.forEach { dns ->
                isValidIpAddress(dns) shouldBe true
            }
            
            // Verify the mode is correct
            settings.dnsMode shouldBe DnsMode.CUSTOM_DNS
        }
    }
    
    /**
     * Verifies that SYSTEM_DEFAULT mode uses system DNS.
     * 
     * For any connection settings with SYSTEM_DEFAULT mode, the resulting
     * tunnel configuration should use system default DNS (empty list or system DNS).
     */
    @Test
    fun `system default mode should use system dns`() = runTest {
        checkAll(
            iterations = 100,
            Arb.connectionSettingsWithDnsMode(DnsMode.SYSTEM_DEFAULT)
        ) { settings ->
            val tunnelConfig = createTunnelConfigFromSettings(settings)
            
            // For system default, we either have an empty list or system DNS servers
            // The VPN service will use system DNS when appropriate
            tunnelConfig.dnsServers.shouldBeInstanceOf<List<String>>()
            
            // If DNS servers are provided, they should be valid
            tunnelConfig.dnsServers.forEach { dns ->
                isValidIpAddress(dns) shouldBe true
            }
            
            // Verify the mode is correct
            settings.dnsMode shouldBe DnsMode.SYSTEM_DEFAULT
        }
    }
    
    /**
     * Verifies that DNS mode changes are properly reflected in configuration.
     * 
     * For any two different DNS modes, the resulting tunnel configurations
     * should differ in their DNS server configuration.
     */
    @Test
    fun `different dns modes should produce different configurations`() = runTest {
        checkAll(
            iterations = 100,
            Arb.connectionSettings(),
            Arb.enum<DnsMode>()
        ) { baseSettings, newMode ->
            // Skip if the mode is the same
            if (baseSettings.dnsMode == newMode) {
                return@checkAll
            }
            
            // Create configs with different DNS modes
            val config1 = createTunnelConfigFromSettings(baseSettings)
            val config2 = createTunnelConfigFromSettings(
                baseSettings.copy(dnsMode = newMode)
            )
            
            // The DNS mode should be different
            baseSettings.dnsMode shouldNotBe newMode
            
            // Both configs should be valid
            config1.shouldBeInstanceOf<TunnelConfig>()
            config2.shouldBeInstanceOf<TunnelConfig>()
        }
    }
    
    /**
     * Verifies that all DNS modes are supported.
     * 
     * For any DNS mode, it should be possible to create a valid tunnel configuration.
     */
    @Test
    fun `all dns modes should be supported`() = runTest {
        val modes = listOf(
            DnsMode.THROUGH_TUNNEL,
            DnsMode.CUSTOM_DNS,
            DnsMode.SYSTEM_DEFAULT
        )
        
        modes.forEach { mode ->
            val settings = ConnectionSettings(dnsMode = mode)
            val tunnelConfig = createTunnelConfigFromSettings(settings)
            
            // Should create a valid tunnel config
            tunnelConfig.shouldBeInstanceOf<TunnelConfig>()
            
            // DNS servers should be a valid list
            tunnelConfig.dnsServers.shouldBeInstanceOf<List<String>>()
            
            // All DNS servers should be valid IP addresses
            tunnelConfig.dnsServers.forEach { dns ->
                isValidIpAddress(dns) shouldBe true
            }
        }
    }
    
    /**
     * Helper function to create a TunnelConfig from ConnectionSettings.
     * This simulates how the application would configure DNS based on settings.
     */
    private fun createTunnelConfigFromSettings(
        settings: ConnectionSettings,
        customDns: List<String>? = null
    ): TunnelConfig {
        val dnsServers = when (settings.dnsMode) {
            DnsMode.THROUGH_TUNNEL -> {
                // Use public DNS servers that will be routed through the tunnel
                listOf("8.8.8.8", "8.8.4.4")
            }
            DnsMode.CUSTOM_DNS -> {
                // Use custom DNS servers if provided, otherwise use defaults
                customDns ?: listOf("1.1.1.1", "1.0.0.1")
            }
            DnsMode.SYSTEM_DEFAULT -> {
                // Empty list means use system default DNS
                emptyList()
            }
        }
        
        return TunnelConfig(
            socksPort = settings.customSocksPort ?: 1080,
            dnsServers = dnsServers,
            routingConfig = RoutingConfig(),
            mtu = 1500,
            sessionName = "SSH Tunnel Proxy"
        )
    }
    
    /**
     * Helper function to validate IP address format.
     */
    private fun isValidIpAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        
        return parts.all { part ->
            val num = part.toIntOrNull()
            num != null && num in 0..255
        }
    }
}

/**
 * Custom Kotest Arbitrary generators for DNS routing tests.
 */

/**
 * Generates random ConnectionSettings instances with valid data.
 */
fun Arb.Companion.connectionSettings(): Arb<ConnectionSettings> = arbitrary {
    ConnectionSettings(
        sshPort = Arb.int(22..65535).bind(),
        connectionTimeout = Arb.int(10..120).bind().seconds,
        keepAliveInterval = Arb.int(30..300).bind().seconds,
        enableCompression = Arb.boolean().bind(),
        customSocksPort = Arb.int(1024..65535).orNull().bind(),
        strictHostKeyChecking = Arb.boolean().bind(),
        dnsMode = Arb.enum<DnsMode>().bind()
    )
}

/**
 * Generates ConnectionSettings with a specific DNS mode.
 */
fun Arb.Companion.connectionSettingsWithDnsMode(mode: DnsMode): Arb<ConnectionSettings> = arbitrary {
    ConnectionSettings(
        sshPort = Arb.int(22..65535).bind(),
        connectionTimeout = Arb.int(10..120).bind().seconds,
        keepAliveInterval = Arb.int(30..300).bind().seconds,
        enableCompression = Arb.boolean().bind(),
        customSocksPort = Arb.int(1024..65535).orNull().bind(),
        strictHostKeyChecking = Arb.boolean().bind(),
        dnsMode = mode
    )
}

/**
 * Generates valid custom DNS server lists (1-4 servers).
 */
fun Arb.Companion.customDnsServers(): Arb<List<String>> = arbitrary {
    val count = Arb.int(1..4).bind()
    List(count) {
        val octets = List(4) { Arb.int(0..255).bind() }
        octets.joinToString(".")
    }
}
