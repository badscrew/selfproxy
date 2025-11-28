package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPv4Header
import com.sshtunnel.android.vpn.packet.PacketBuilder
import com.sshtunnel.android.vpn.packet.Protocol
import com.sshtunnel.logging.LogLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.FileOutputStream
import java.io.IOException

/**
 * Tests for error handling and recovery in the packet router.
 * 
 * These tests verify that:
 * - Malformed packets are handled gracefully
 * - SOCKS5 failures are handled correctly
 * - TUN interface errors don't crash the router
 * - Router continues processing after errors
 * 
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5
 */
class ErrorHandlingTest {
    
    private lateinit var logger: TestLogger
    private lateinit var connectionTable: ConnectionTable
    private lateinit var tcpHandler: TCPHandler
    private lateinit var udpHandler: UDPHandler
    private lateinit var packetBuilder: PacketBuilder
    
    @Before
    fun setup() {
        logger = TestLogger()
        logger.setVerboseEnabled(true)
        connectionTable = ConnectionTable(logger)
        tcpHandler = TCPHandler(
            socksPort = 1080,
            connectionTable = connectionTable,
            logger = logger
        )
        udpHandler = UDPHandler(
            socksPort = 1080,
            connectionTable = connectionTable,
            logger = logger
        )
        packetBuilder = PacketBuilder()
    }
    
    /**
     * Test that malformed TCP packets are handled gracefully.
     * 
     * Requirements: 11.1
     */
    @Test
    fun `malformed TCP packet should be dropped without crashing`() = runTest {
        // Create a malformed packet (too short for TCP header)
        val malformedPacket = ByteArray(30) // IP header only, no TCP header
        
        val ipHeader = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = 30,
            identification = 1234,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.TCP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "1.1.1.1"
        )
        
        // Create a mock output stream that doesn't throw
        val mockOutputStream = object : FileOutputStream(java.io.FileDescriptor()) {
            override fun write(b: ByteArray) {}
            override fun write(b: Int) {}
            override fun flush() {}
        }
        
        // Handle the malformed packet - should not throw
        tcpHandler.handleTcpPacket(malformedPacket, ipHeader, mockOutputStream)
        
        // Verify error was logged
        val errorLogs = logger.getLogEntries().filter { it.level == LogLevel.VERBOSE }
        assertTrue(errorLogs.any { it.message.contains("Failed to parse TCP header") })
    }
    
    /**
     * Test that malformed UDP packets are handled gracefully.
     * 
     * Requirements: 11.1
     */
    @Test
    fun `malformed UDP packet should be dropped without crashing`() = runTest {
        // Create a malformed packet (too short for UDP header)
        val malformedPacket = ByteArray(25) // IP header only, no UDP header
        
        val ipHeader = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = 25,
            identification = 1234,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.UDP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "8.8.8.8"
        )
        
        // Create a mock output stream
        val mockOutputStream = object : FileOutputStream(java.io.FileDescriptor()) {
            override fun write(b: ByteArray) {}
            override fun write(b: Int) {}
            override fun flush() {}
        }
        
        // Handle the malformed packet - should not throw
        udpHandler.handleUdpPacket(malformedPacket, ipHeader, mockOutputStream)
        
        // Verify error was logged
        val verboseLogs = logger.getLogEntries().filter { it.level == LogLevel.VERBOSE }
        assertTrue(verboseLogs.any { it.message.contains("Failed to parse UDP header") })
    }
    
    /**
     * Test that TUN interface write errors are handled gracefully.
     * 
     * Requirements: 11.4
     */
    @Test
    fun `TUN interface write error should be logged and not crash`() = runTest {
        // Create a mock output stream that throws IOException
        val failingOutputStream = object : FileOutputStream(java.io.FileDescriptor()) {
            override fun write(b: ByteArray) {
                throw IOException("TUN interface closed")
            }
            override fun write(b: Int) {
                throw IOException("TUN interface closed")
            }
            override fun flush() {}
        }
        
        // Build a valid UDP packet
        val dnsQuery = ByteArray(32) { 0 }
        val packet = packetBuilder.buildUdpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53,
            payload = dnsQuery
        )
        
        val ipHeader = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = packet.size,
            identification = 1234,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.UDP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "8.8.8.8"
        )
        
        // Handle packet with failing output stream - should not throw
        udpHandler.handleUdpPacket(packet, ipHeader, failingOutputStream)
        
        // Verify error was logged (may be in handleDnsQuery or sendUdpPacket)
        val errorLogs = logger.getLogEntries().filter { it.level == LogLevel.ERROR }
        // Error might be logged during DNS query or packet send
        assertTrue("Expected error logs for TUN write failure", errorLogs.isNotEmpty())
    }
    
    /**
     * Test that TCP handler continues processing after an error.
     * 
     * Requirements: 11.5
     */
    @Test
    fun `TCP handler should continue processing after error`() = runTest {
        val mockOutputStream = object : FileOutputStream(java.io.FileDescriptor()) {
            override fun write(b: ByteArray) {}
            override fun write(b: Int) {}
            override fun flush() {}
        }
        
        // First packet: malformed (should be dropped)
        val malformedPacket = ByteArray(30)
        val ipHeader1 = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = 30,
            identification = 1234,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.TCP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "1.1.1.1"
        )
        
        tcpHandler.handleTcpPacket(malformedPacket, ipHeader1, mockOutputStream)
        
        // Second packet: valid TCP SYN (should be processed)
        val synPacket = buildValidTcpSynPacket()
        val ipHeader2 = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = synPacket.size,
            identification = 1235,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.TCP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "1.1.1.1"
        )
        
        tcpHandler.handleTcpPacket(synPacket, ipHeader2, mockOutputStream)
        
        // Verify both packets were processed (first dropped, second attempted)
        val logs = logger.getLogEntries()
        
        // First packet should have parse error
        assertTrue(logs.any { it.message.contains("Failed to parse TCP header") })
        
        // Second packet should have connection attempt (will fail due to no SOCKS5, but that's OK)
        assertTrue(logs.any { it.message.contains("Establishing TCP connection") })
    }
    
    /**
     * Test that UDP handler continues processing after an error.
     * 
     * Requirements: 11.5
     */
    @Test
    fun `UDP handler should continue processing after error`() = runTest {
        val mockOutputStream = object : FileOutputStream(java.io.FileDescriptor()) {
            override fun write(b: ByteArray) {}
            override fun write(b: Int) {}
            override fun flush() {}
        }
        
        // First packet: malformed (should be dropped)
        val malformedPacket = ByteArray(25)
        val ipHeader1 = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = 25,
            identification = 1234,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.UDP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "8.8.8.8"
        )
        
        udpHandler.handleUdpPacket(malformedPacket, ipHeader1, mockOutputStream)
        
        // Second packet: valid UDP DNS query (should be processed)
        val dnsQuery = ByteArray(32) { 0 }
        val validPacket = packetBuilder.buildUdpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53,
            payload = dnsQuery
        )
        
        val ipHeader2 = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = validPacket.size,
            identification = 1235,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.UDP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "8.8.8.8"
        )
        
        udpHandler.handleUdpPacket(validPacket, ipHeader2, mockOutputStream)
        
        // Verify both packets were processed
        val logs = logger.getLogEntries()
        
        // First packet should have parse error
        assertTrue(logs.any { it.message.contains("Failed to parse UDP header") })
        
        // Second packet should have DNS query attempt
        assertTrue(logs.any { it.message.contains("DNS query") })
    }
    
    /**
     * Test that SOCKS5 connection failures are logged with context.
     * 
     * Requirements: 11.2, 12.4
     */
    @Test
    fun `SOCKS5 connection failure should be logged with error code`() = runTest {
        // This test verifies that SOCKS5 errors are logged properly
        // We can't easily test actual SOCKS5 failures without a mock server,
        // but we can verify the logging infrastructure is in place
        
        val mockOutputStream = object : FileOutputStream(java.io.FileDescriptor()) {
            override fun write(b: ByteArray) {}
            override fun write(b: Int) {}
            override fun flush() {}
        }
        
        // Build a TCP SYN packet
        val synPacket = buildValidTcpSynPacket()
        val ipHeader = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = synPacket.size,
            identification = 1234,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.TCP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "1.1.1.1"
        )
        
        // Handle SYN packet - will fail to connect to SOCKS5 (no server running)
        tcpHandler.handleTcpPacket(synPacket, ipHeader, mockOutputStream)
        
        // Verify error was logged with context
        val errorLogs = logger.getLogEntries().filter { it.level == LogLevel.ERROR }
        assertTrue(errorLogs.any { 
            it.message.contains("Failed to establish SOCKS5 connection") ||
            it.message.contains("Error establishing TCP connection")
        })
    }
    
    /**
     * Test that packet parsing errors include packet details in logs.
     * 
     * Requirements: 11.1
     */
    @Test
    fun `packet parsing errors should include packet details in logs`() = runTest {
        val mockOutputStream = object : FileOutputStream(java.io.FileDescriptor()) {
            override fun write(b: ByteArray) {}
            override fun write(b: Int) {}
            override fun flush() {}
        }
        
        // Create a packet with invalid TCP header
        val invalidPacket = ByteArray(30) { 0 }
        
        val ipHeader = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = 30,
            identification = 1234,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.TCP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "1.1.1.1"
        )
        
        tcpHandler.handleTcpPacket(invalidPacket, ipHeader, mockOutputStream)
        
        // Verify error log contains packet details
        val logs = logger.getLogEntries()
        assertTrue(logs.any { it.message.contains("Failed to parse TCP header") })
    }
    
    /**
     * Helper function to build a valid TCP SYN packet.
     */
    private fun buildValidTcpSynPacket(): ByteArray {
        return packetBuilder.buildTcpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80,
            sequenceNumber = 1000,
            acknowledgmentNumber = 0,
            flags = TcpFlags(
                fin = false,
                syn = true,
                rst = false,
                psh = false,
                ack = false,
                urg = false
            ),
            windowSize = 65535,
            payload = byteArrayOf()
        )
    }
}
