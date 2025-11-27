package com.sshtunnel.android.vpn

/**
 * Parsed TCP header information
 */
data class TcpHeader(
    val sourcePort: Int,
    val destPort: Int,
    val sequenceNumber: Long,
    val acknowledgmentNumber: Long,
    val dataOffset: Int,
    val flags: TcpFlags,
    val windowSize: Int,
    val checksum: Int,
    val urgentPointer: Int
)

/**
 * TCP flags from the TCP header
 * 
 * Flags are stored in a single byte:
 * - FIN (0x01): Finish, no more data
 * - SYN (0x02): Synchronize, establish connection
 * - RST (0x04): Reset connection
 * - PSH (0x08): Push, deliver data immediately
 * - ACK (0x10): Acknowledgment
 * - URG (0x20): Urgent pointer field is significant
 */
data class TcpFlags(
    val fin: Boolean,
    val syn: Boolean,
    val rst: Boolean,
    val psh: Boolean,
    val ack: Boolean,
    val urg: Boolean
) {
    /**
     * Convert flags to byte representation
     */
    fun toByte(): Int {
        var flags = 0
        if (fin) flags = flags or 0x01
        if (syn) flags = flags or 0x02
        if (rst) flags = flags or 0x04
        if (psh) flags = flags or 0x08
        if (ack) flags = flags or 0x10
        if (urg) flags = flags or 0x20
        return flags
    }
    
    companion object {
        /**
         * Parse flags from byte representation
         */
        fun fromByte(byte: Int): TcpFlags {
            return TcpFlags(
                fin = (byte and 0x01) != 0,
                syn = (byte and 0x02) != 0,
                rst = (byte and 0x04) != 0,
                psh = (byte and 0x08) != 0,
                ack = (byte and 0x10) != 0,
                urg = (byte and 0x20) != 0
            )
        }
    }
}
