package com.sshtunnel.android.vpn

import kotlinx.coroutines.Job
import java.net.Socket

/**
 * Represents an active TCP connection with state tracking
 */
data class TcpConnection(
    val key: ConnectionKey,
    val socksSocket: Socket,
    val state: TcpState,
    val sequenceNumber: Long,
    val acknowledgmentNumber: Long,
    val createdAt: Long,
    val lastActivityAt: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val readerJob: Job
)

/**
 * TCP connection states following the TCP state machine
 * 
 * Simplified implementation for MVP:
 * - CLOSED: No connection exists
 * - SYN_SENT: SYN packet received, SOCKS5 connection being established
 * - ESTABLISHED: Connection active, data can flow
 * - FIN_WAIT_1: FIN sent, waiting for ACK
 * - FIN_WAIT_2: FIN ACK received, waiting for remote FIN
 * - CLOSING: Both sides closing simultaneously
 * - TIME_WAIT: Connection closed, waiting for delayed packets
 */
enum class TcpState {
    CLOSED,
    SYN_SENT,
    ESTABLISHED,
    FIN_WAIT_1,
    FIN_WAIT_2,
    CLOSING,
    TIME_WAIT
}
