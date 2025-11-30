package com.sshtunnel.android.vpn

import kotlinx.coroutines.Job
import java.net.DatagramSocket
import java.net.Socket

/**
 * Represents a SOCKS5 UDP ASSOCIATE connection.
 * 
 * UDP ASSOCIATE is a SOCKS5 command that establishes a UDP relay connection.
 * Unlike regular UDP connections, UDP ASSOCIATE requires:
 * - A TCP control socket to maintain the association
 * - A UDP relay socket for sending/receiving datagrams
 * - A relay endpoint (address and port) where datagrams should be sent
 * - A reader coroutine to process incoming datagrams
 * 
 * Requirements: 1.3, 2.1
 */
data class UdpAssociateConnection(
    val key: ConnectionKey,
    val controlSocket: Socket,          // TCP connection for SOCKS5 control
    val relaySocket: DatagramSocket,    // UDP socket for relay communication
    val relayEndpoint: UdpRelayEndpoint, // Where to send encapsulated datagrams
    val createdAt: Long,
    var lastActivityAt: Long,
    var bytesSent: Long,
    var bytesReceived: Long,
    val readerJob: Job                  // Coroutine reading from relay socket
)
