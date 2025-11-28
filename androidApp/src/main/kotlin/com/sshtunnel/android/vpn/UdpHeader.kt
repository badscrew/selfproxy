package com.sshtunnel.android.vpn

/**
 * Parsed UDP header information
 * 
 * UDP header structure (8 bytes):
 * - Source Port (2 bytes)
 * - Destination Port (2 bytes)
 * - Length (2 bytes) - includes header + data
 * - Checksum (2 bytes)
 */
data class UdpHeader(
    val sourcePort: Int,
    val destPort: Int,
    val length: Int,
    val checksum: Int
)
