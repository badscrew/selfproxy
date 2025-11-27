package com.sshtunnel.android.vpn

/**
 * Parsed UDP header information
 * 
 * UDP header is simpler than TCP:
 * - Source port (2 bytes)
 * - Destination port (2 bytes)
 * - Length (2 bytes) - includes header + data
 * - Checksum (2 bytes) - optional for IPv4, can be 0
 */
data class UdpHeader(
    val sourcePort: Int,
    val destPort: Int,
    val length: Int,
    val checksum: Int
)
