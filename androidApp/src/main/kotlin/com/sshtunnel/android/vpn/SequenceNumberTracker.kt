package com.sshtunnel.android.vpn

import kotlin.random.Random

/**
 * Manages TCP sequence and acknowledgment numbers for a connection
 * 
 * TCP requires tracking:
 * - Sequence number: Identifies the byte position in the data stream
 * - Acknowledgment number: Next expected byte from the other side
 * 
 * Sequence numbers are 32-bit values that wrap around at 2^32.
 */
class SequenceNumberTracker {
    private var nextSeqNum: Long = Random.nextLong(0, 0xFFFFFFFF)
    private var nextAckNum: Long = 0
    
    /**
     * Get the next sequence number to use
     */
    fun getNextSeq(): Long = nextSeqNum
    
    /**
     * Get the next acknowledgment number to use
     */
    fun getNextAck(): Long = nextAckNum
    
    /**
     * Advance the sequence number by the given number of bytes
     * Handles 32-bit wraparound
     */
    fun advanceSeq(bytes: Int) {
        nextSeqNum = (nextSeqNum + bytes) and 0xFFFFFFFF
    }
    
    /**
     * Update the acknowledgment number based on received data
     */
    fun updateAck(ackNum: Long) {
        nextAckNum = ackNum and 0xFFFFFFFF
    }
}
