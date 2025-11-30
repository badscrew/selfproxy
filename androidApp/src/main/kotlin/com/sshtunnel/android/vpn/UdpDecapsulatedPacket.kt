package com.sshtunnel.android.vpn

/**
 * Decapsulated UDP packet from SOCKS5 response.
 * 
 * When receiving UDP datagrams from the SOCKS5 relay, they are encapsulated
 * with a SOCKS5 UDP header. This class represents the extracted information
 * after parsing and removing the SOCKS5 header.
 * 
 * Requirements: 1.3
 */
data class UdpDecapsulatedPacket(
    val sourceIp: String,
    val sourcePort: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UdpDecapsulatedPacket

        if (sourceIp != other.sourceIp) return false
        if (sourcePort != other.sourcePort) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourceIp.hashCode()
        result = 31 * result + sourcePort
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
