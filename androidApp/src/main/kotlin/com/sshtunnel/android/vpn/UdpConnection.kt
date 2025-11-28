package com.sshtunnel.android.vpn

import java.net.Socket

/**
 * Represents an active UDP connection state.
 * 
 * UDP is connectionless, but we track "connections" for:
 * - DNS query/response matching
 * - Statistics tracking
 * - Resource management
 */
data class UdpConnection(
    val key: ConnectionKey,
    val socksSocket: Socket?,
    val createdAt: Long,
    val lastActivityAt: Long,
    val bytesSent: Long,
    val bytesReceived: Long
)
