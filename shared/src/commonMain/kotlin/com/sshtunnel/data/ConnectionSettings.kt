package com.sshtunnel.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration settings for SSH connections and VPN behavior.
 * 
 * @property sshPort SSH server port (default 22)
 * @property connectionTimeout Maximum time to wait for connection establishment
 * @property keepAliveInterval Interval between keep-alive packets to maintain idle connections
 * @property enableCompression Whether to enable SSH compression
 * @property customSocksPort Custom local port for SOCKS5 proxy (null for automatic)
 * @property strictHostKeyChecking Whether to verify SSH server host keys
 * @property dnsMode How DNS queries should be handled
 * @property verboseLogging Whether to enable verbose logging for debugging
 */
data class ConnectionSettings(
    val sshPort: Int = 22,
    val connectionTimeout: Duration = 30.seconds,
    val keepAliveInterval: Duration = 60.seconds,
    val enableCompression: Boolean = false,
    val customSocksPort: Int? = null,
    val strictHostKeyChecking: Boolean = false,
    val dnsMode: DnsMode = DnsMode.THROUGH_TUNNEL,
    val verboseLogging: Boolean = false
)

/**
 * DNS routing mode.
 */
enum class DnsMode {
    /**
     * Route DNS queries through the SSH tunnel.
     */
    THROUGH_TUNNEL,
    
    /**
     * Use custom DNS servers.
     */
    CUSTOM_DNS,
    
    /**
     * Use system default DNS servers.
     */
    SYSTEM_DEFAULT
}
