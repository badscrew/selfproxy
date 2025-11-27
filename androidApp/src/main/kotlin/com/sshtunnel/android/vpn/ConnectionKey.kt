package com.sshtunnel.android.vpn

/**
 * Unique identifier for a network connection using the 5-tuple:
 * (protocol, source IP, source port, destination IP, destination port)
 */
data class ConnectionKey(
    val protocol: Protocol,
    val sourceIp: String,
    val sourcePort: Int,
    val destIp: String,
    val destPort: Int
)

/**
 * Network protocol types supported by the packet router
 */
enum class Protocol(val value: Int) {
    TCP(6),
    UDP(17),
    ICMP(1),
    UNKNOWN(-1);
    
    companion object {
        fun fromValue(value: Int): Protocol {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}
