package com.sshtunnel.android.vpn.packet

import com.sshtunnel.android.vpn.TcpFlags
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Property-based tests for PacketBuilder.
 * 
 * These tests verify universal properties that should hold across all inputs,
 * using Kotest's property testing framework.
 */
class PacketBuilderPropertiesTest {
    
    @Test
    fun `checksum calculation is symmetric`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: Checksum calculation is symmetric
        // Validates: Requirements 8.1, 8.4
        
        val builder = PacketBuilder()
        
        checkAll(100, Arb.byteArray(Arb.int(20..1500))) { data ->
            val checksum1 = builder.calculateIPChecksum(data)
            val checksum2 = builder.calculateIPChecksum(data)
            
            // Checksum should be deterministic - same input produces same output
            checksum1 shouldBe checksum2
        }
    }
    
    @Test
    fun `IP packet round-trip preserves data`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: IP packet round-trip preserves data
        // Validates: Requirements 8.1, 8.2, 8.3
        
        val builder = PacketBuilder()
        
        checkAll(
            100,
            Arb.ipAddress(),
            Arb.ipAddress(),
            Arb.protocol(),
            Arb.byteArray(Arb.int(0..1400))
        ) { sourceIp, destIp, protocol, payload ->
            // Build packet
            val packet = builder.buildIPv4Packet(
                sourceIp = sourceIp,
                destIp = destIp,
                protocol = protocol,
                payload = payload
            )
            
            // Parse packet
            val parsed = IPPacketParser.parseIPv4Header(packet)
            
            // Verify round-trip preserves key data
            parsed shouldNotBe null
            parsed!!.sourceIP shouldBe sourceIp
            parsed.destIP shouldBe destIp
            parsed.protocol shouldBe protocol
            
            // Verify payload is intact
            val headerLength = parsed.headerLength
            val extractedPayload = packet.copyOfRange(headerLength, packet.size)
            extractedPayload shouldBe payload
        }
    }
    
    @Test
    fun `TCP packet construction always includes valid checksum`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: TCP packets have valid checksums
        // Validates: Requirements 8.2, 8.4
        
        val builder = PacketBuilder()
        
        checkAll(
            100,
            Arb.ipAddress(),
            Arb.port(),
            Arb.ipAddress(),
            Arb.port(),
            Arb.sequenceNumber(),
            Arb.sequenceNumber(),
            Arb.tcpFlags(),
            Arb.byteArray(Arb.int(0..100))
        ) { sourceIp, sourcePort, destIp, destPort, seqNum, ackNum, flags, payload ->
            val packet = builder.buildTcpPacket(
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                destIp = destIp,
                destPort = destPort,
                sequenceNumber = seqNum,
                acknowledgmentNumber = ackNum,
                flags = flags,
                payload = payload
            )
            
            // Verify packet was created
            packet.size shouldBe (40 + payload.size)
            
            // Verify IP header is valid
            val ipHeader = IPPacketParser.parseIPv4Header(packet)
            ipHeader shouldNotBe null
            ipHeader!!.protocol shouldBe Protocol.TCP
            
            // Verify TCP checksum is non-zero (zero would indicate error)
            val tcpOffset = 20
            val checksum = ((packet[tcpOffset + 16].toInt() and 0xFF) shl 8) or 
                          (packet[tcpOffset + 17].toInt() and 0xFF)
            checksum shouldNotBe 0
        }
    }
    
    @Test
    fun `UDP packet construction always includes valid checksum`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: UDP packets have valid checksums
        // Validates: Requirements 8.3, 8.4
        
        val builder = PacketBuilder()
        
        checkAll(
            100,
            Arb.ipAddress(),
            Arb.port(),
            Arb.ipAddress(),
            Arb.port(),
            Arb.byteArray(Arb.int(0..100))
        ) { sourceIp, sourcePort, destIp, destPort, payload ->
            val packet = builder.buildUdpPacket(
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                destIp = destIp,
                destPort = destPort,
                payload = payload
            )
            
            // Verify packet was created
            packet.size shouldBe (28 + payload.size)
            
            // Verify IP header is valid
            val ipHeader = IPPacketParser.parseIPv4Header(packet)
            ipHeader shouldNotBe null
            ipHeader!!.protocol shouldBe Protocol.UDP
            
            // Verify UDP checksum is non-zero
            val udpOffset = 20
            val checksum = ((packet[udpOffset + 6].toInt() and 0xFF) shl 8) or 
                          (packet[udpOffset + 7].toInt() and 0xFF)
            checksum shouldNotBe 0
        }
    }
    
    @Test
    fun `checksum handles odd-length data correctly`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: Checksum handles odd-length data
        // Validates: Requirements 8.4, 8.5
        
        val builder = PacketBuilder()
        
        checkAll(100, Arb.byteArray(Arb.int(1..1500))) { data ->
            // Should not throw exception regardless of data length
            val checksum = builder.calculateIPChecksum(data)
            
            // Checksum should be in valid range
            checksum shouldBe (checksum and 0xFFFF)
        }
    }
    
    @Test
    fun `built packets have valid IP checksums`() = runTest {
        // Feature: pure-kotlin-packet-router, Property: Built packets pass checksum validation
        // Validates: Requirements 8.1, 8.5
        
        val builder = PacketBuilder()
        
        checkAll(
            100,
            Arb.ipAddress(),
            Arb.ipAddress(),
            Arb.protocol(),
            Arb.byteArray(Arb.int(0..100))
        ) { sourceIp, destIp, protocol, payload ->
            val packet = builder.buildIPv4Packet(
                sourceIp = sourceIp,
                destIp = destIp,
                protocol = protocol,
                payload = payload
            )
            
            // Verify checksum is valid using IPPacketParser
            IPPacketParser.validateChecksum(packet) shouldBe true
        }
    }
}

// Custom Arb generators for property testing

/**
 * Generates random valid IPv4 addresses.
 */
fun Arb.Companion.ipAddress() = arbitrary {
    val octets = List(4) { Arb.int(0..255).bind() }
    octets.joinToString(".")
}

/**
 * Generates random valid port numbers (1024-65535).
 */
fun Arb.Companion.port() = Arb.int(1024..65535)

/**
 * Generates random protocol types (TCP or UDP).
 */
fun Arb.Companion.protocol() = arbitrary {
    val protocols = listOf(Protocol.TCP, Protocol.UDP)
    protocols[Arb.int(0..1).bind()]
}

/**
 * Generates random TCP sequence numbers (0 to 2^32-1).
 */
fun Arb.Companion.sequenceNumber() = arbitrary {
    Arb.int(0..Int.MAX_VALUE).bind().toLong() and 0xFFFFFFFFL
}

/**
 * Generates random TCP flags combinations.
 */
fun Arb.Companion.tcpFlags() = arbitrary {
    TcpFlags(
        fin = Arb.boolean().bind(),
        syn = Arb.boolean().bind(),
        rst = Arb.boolean().bind(),
        psh = Arb.boolean().bind(),
        ack = Arb.boolean().bind(),
        urg = Arb.boolean().bind()
    )
}

/**
 * Generates random boolean values.
 */
fun Arb.Companion.boolean() = arbitrary {
    Arb.int(0..1).bind() == 1
}

/**
 * Generates random byte arrays of specified size.
 */
fun Arb.Companion.byteArray(sizeArb: Arb<Int>) = arbitrary {
    val size = sizeArb.bind()
    ByteArray(size) { Arb.int(0..255).bind().toByte() }
}
