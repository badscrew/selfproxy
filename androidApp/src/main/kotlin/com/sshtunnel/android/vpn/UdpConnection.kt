package com.sshtunnel.android.vpn

import java.net.Socket

/**
 * Represents an active UDP connection
 * 
 * UDP is connectionless, but we track "connections" for:
 * - DNS queries (port 53)
 * - SOCKS5 UDP ASSOCIATE (future enhancement)
 */
data class UdpConnection(
    val key: ConnectionKey,
    val socksSocket: Socket?,
    val createdAt: Long,
    val lastActivityAt: Long,
    val bytesSent: Long,
    val bytesReceived: Long
)
