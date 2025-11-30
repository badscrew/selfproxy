package com.sshtunnel.android.vpn

/**
 * SOCKS5 UDP relay endpoint (BND.ADDR and BND.PORT from server response).
 * 
 * When establishing a UDP ASSOCIATE connection, the SOCKS5 server responds with
 * the address and port where UDP datagrams should be sent for relay.
 * 
 * Requirements: 1.3
 */
data class UdpRelayEndpoint(
    val address: String,
    val port: Int
)
