package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Unit tests for TCPHandler TCP packet parsing.
 * 
 * Tests TCP header parsing, flag extraction, and payload extraction
 * for various TCP packet types (SYN, ACK, FIN, RST).
 */
class TCPHandlerTest {
    
    private lateinit var tcpHandler: TCPHandler
    private lateinit var connectionTable: ConnectionTable
    private lateinit var logger: Logger
    
    @Before
    fun setup() {
        connectionTable = ConnectionTable()
        logger = object : Logger {
            override fun verbose(tag: String, message: String, throwable: Throwable?) {}
            override fun debug(tag: String, message: String, throwable: Throwable?) {}
            override fun info(tag: String, message: String, throwable: Throwable?) {}
            override fun warn(tag: String, message: String, throwable: Throwable?) {}
            override fun error(tag: String, message: String, throwable: Throwable?) {}
            override fun getLogEntries(): List<com.sshtunnel.logging.LogEntry> = emptyList()
            override fun clearLogs() {}
            override fun setVerboseEnabled(enabled: Boolean) {}
            override fun isVerboseEnabled(): Boolean = false
        }
        tcpHandler = TCPHandler(
            socksPort = 1080,
            connectionTable = connectionTable,
            logger = logger
        )
    }
    
    @Test
    fun `parse valid TCP SYN packet`() {
        // Build a TCP SYN packet
        val packet = buildTcpPacket(
            sourcePort = 12345,
            destPort = 80,
            sequenceNumber = 1000,
            acknowledgmentNumber = 0,
            flags = TcpFlags(syn = true, ack = false, fin = false, rst = false, psh = false, urg = false),
            windowSize = 65535,
            payload = ByteArray(0)
        )
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        
        assertNotNull(tcpHeader)
        assertEquals(12345, tcpHeader!!.sourcePort)
        assertEquals(80, tcpHeader.destPort)
        assertEquals(1000L, tcpHeader.sequenceNumber)
        assertEquals(0L, tcpHeader.acknowledgmentNumber)
        assertEquals(20, tcpHeader.dataOffset)
        assertTrue(tcpHeader.flags.syn)
        assertFalse(tcpHeader.flags.ack)
        assertFalse(tcpHeader.flags.fin)
        assertFalse(tcpHeader.flags.rst)
        assertEquals(65535, tcpHeader.windowSize)
    }
    
    @Test
    fun `parse valid TCP SYN-ACK packet`() {
        val packet = buildTcpPacket(
            sourcePort = 80,
            destPort = 12345,
            sequenceNumber = 2000,
            acknowledgmentNumber = 1001,
            flags = TcpFlags(syn = true, ack = true, fin = false, rst = false, psh = false, urg = false),
            windowSize = 65535,
            payload = ByteArray(0)
        )
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        
        assertNotNull(tcpHeader)
        assertEquals(80, tcpHeader!!.sourcePort)
        assertEquals(12345, tcpHeader.destPort)
        assertEquals(2000L, tcpHeader.sequenceNumber)
        assertEquals(1001L, tcpHeader.acknowledgmentNumber)
        assertTrue(tcpHeader.flags.syn)
        assertTrue(tcpHeader.flags.ack)
        assertFalse(tcpHeader.flags.fin)
        assertFalse(tcpHeader.flags.rst)
    }
    
    @Test
    fun `parse TCP ACK packet`() {
        val packet = buildTcpPacket(
            sourcePort = 12345,
            destPort = 80,
            sequenceNumber = 1001,
            acknowledgmentNumber = 2001,
            flags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false, urg = false),
            windowSize = 65535,
            payload = ByteArray(0)
        )
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        
        assertNotNull(tcpHeader)
        assertFalse(tcpHeader!!.flags.syn)
        assertTrue(tcpHeader.flags.ack)
        assertFalse(tcpHeader.flags.fin)
        assertFalse(tcpHeader.flags.rst)
    }
    
    @Test
    fun `parse TCP FIN packet`() {
        val packet = buildTcpPacket(
            sourcePort = 12345,
            destPort = 80,
            sequenceNumber = 5000,
            acknowledgmentNumber = 3000,
            flags = TcpFlags(syn = false, ack = true, fin = true, rst = false, psh = false, urg = false),
            windowSize = 65535,
            payload = ByteArray(0)
        )
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        
        assertNotNull(tcpHeader)
        assertTrue(tcpHeader!!.flags.fin)
        assertTrue(tcpHeader.flags.ack)
        assertFalse(tcpHeader.flags.syn)
        assertFalse(tcpHeader.flags.rst)
    }
    
    @Test
    fun `parse TCP RST packet`() {
        val packet = buildTcpPacket(
            sourcePort = 12345,
            destPort = 80,
            sequenceNumber = 1500,
            acknowledgmentNumber = 0,
            flags = TcpFlags(syn = false, ack = false, fin = false, rst = true, psh = false, urg = false),
            windowSize = 0,
            payload = ByteArray(0)
        )
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        
        assertNotNull(tcpHeader)
        assertTrue(tcpHeader!!.flags.rst)
        assertFalse(tcpHeader.flags.syn)
        assertFalse(tcpHeader.flags.ack)
        assertFalse(tcpHeader.flags.fin)
    }
    
    @Test
    fun `parse TCP PSH-ACK packet with data`() {
        val payload = "GET / HTTP/1.1\r\n\r\n".toByteArray()
        val packet = buildTcpPacket(
            sourcePort = 12345,
            destPort = 80,
            sequenceNumber = 1001,
            acknowledgmentNumber = 2001,
            flags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = true, urg = false),
            windowSize = 65535,
            payload = payload
        )
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        
        assertNotNull(tcpHeader)
        assertTrue(tcpHeader!!.flags.psh)
        assertTrue(tcpHeader.flags.ack)
        assertFalse(tcpHeader.flags.syn)
        assertFalse(tcpHeader.flags.fin)
    }
    
    @Test
    fun `extract TCP flags correctly`() {
        // Test all flags set
        val allFlagsPacket = buildTcpPacket(
            sourcePort = 1,
            destPort = 2,
            sequenceNumber = 0,
            acknowledgmentNumber = 0,
            flags = TcpFlags(syn = true, ack = true, fin = true, rst = true, psh = true, urg = true),
            windowSize = 0,
            payload = ByteArray(0)
        )
        
        val allFlagsHeader = tcpHandler.parseTcpHeader(allFlagsPacket, 20)
        assertNotNull(allFlagsHeader)
        assertTrue(allFlagsHeader!!.flags.syn)
        assertTrue(allFlagsHeader.flags.ack)
        assertTrue(allFlagsHeader.flags.fin)
        assertTrue(allFlagsHeader.flags.rst)
        assertTrue(allFlagsHeader.flags.psh)
        assertTrue(allFlagsHeader.flags.urg)
        
        // Test no flags set
        val noFlagsPacket = buildTcpPacket(
            sourcePort = 1,
            destPort = 2,
            sequenceNumber = 0,
            acknowledgmentNumber = 0,
            flags = TcpFlags(syn = false, ack = false, fin = false, rst = false, psh = false, urg = false),
            windowSize = 0,
            payload = ByteArray(0)
        )
        
        val noFlagsHeader = tcpHandler.parseTcpHeader(noFlagsPacket, 20)
        assertNotNull(noFlagsHeader)
        assertFalse(noFlagsHeader!!.flags.syn)
        assertFalse(noFlagsHeader.flags.ack)
        assertFalse(noFlagsHeader.flags.fin)
        assertFalse(noFlagsHeader.flags.rst)
        assertFalse(noFlagsHeader.flags.psh)
        assertFalse(noFlagsHeader.flags.urg)
    }
    
    @Test
    fun `extract TCP payload from packet with data`() {
        val expectedPayload = "Hello, World!".toByteArray()
        val packet = buildTcpPacket(
            sourcePort = 12345,
            destPort = 80,
            sequenceNumber = 1001,
            acknowledgmentNumber = 2001,
            flags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = true, urg = false),
            windowSize = 65535,
            payload = expectedPayload
        )
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        assertNotNull(tcpHeader)
        
        val actualPayload = tcpHandler.extractTcpPayload(packet, 20, tcpHeader!!)
        
        assertArrayEquals(expectedPayload, actualPayload)
    }
    
    @Test
    fun `extract empty payload from packet without data`() {
        val packet = buildTcpPacket(
            sourcePort = 12345,
            destPort = 80,
            sequenceNumber = 1000,
            acknowledgmentNumber = 0,
            flags = TcpFlags(syn = true, ack = false, fin = false, rst = false, psh = false, urg = false),
            windowSize = 65535,
            payload = ByteArray(0)
        )
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        assertNotNull(tcpHeader)
        
        val payload = tcpHandler.extractTcpPayload(packet, 20, tcpHeader!!)
        
        assertEquals(0, payload.size)
    }
    
    @Test
    fun `return null for packet too short for TCP header`() {
        // Packet with only IP header (20 bytes), no TCP header
        val packet = ByteArray(20)
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        
        assertNull(tcpHeader)
    }
    
    @Test
    fun `return null for packet with invalid data offset`() {
        // Build packet with invalid data offset (too large)
        val packet = ByteArray(60)
        // IP header (20 bytes) + TCP header start
        val tcpStart = 20
        
        // Set data offset to 15 (15 * 4 = 60 bytes), which would exceed packet size
        packet[tcpStart + 12] = (15 shl 4).toByte()
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        
        assertNull(tcpHeader)
    }
    
    @Test
    fun `parse TCP packet with large sequence numbers`() {
        val packet = buildTcpPacket(
            sourcePort = 12345,
            destPort = 80,
            sequenceNumber = 0xFFFFFFFFL, // Max 32-bit value
            acknowledgmentNumber = 0x80000000L, // High bit set
            flags = TcpFlags(syn = false, ack = true, fin = false, rst = false, psh = false, urg = false),
            windowSize = 65535,
            payload = ByteArray(0)
        )
        
        val tcpHeader = tcpHandler.parseTcpHeader(packet, 20)
        
        assertNotNull(tcpHeader)
        assertEquals(0xFFFFFFFFL, tcpHeader!!.sequenceNumber)
        assertEquals(0x80000000L, tcpHeader.acknowledgmentNumber)
    }
    
    // ========== SOCKS5 Handshake Tests ==========
    
    @Test
    fun `successful SOCKS5 handshake`() = runBlocking {
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x05, 0x00), // Version 5, No auth
            connectResponse = byteArrayOf(
                0x05, 0x00, 0x00, 0x01, // Version, Success, Reserved, IPv4
                0x7F, 0x00, 0x00, 0x01, // Bound address: 127.0.0.1
                0x04, 0x38 // Bound port: 1080
            )
        )
        
        mockServer.start()
        
        try {
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            
            val result = tcpHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            
            assertTrue("Handshake should succeed", result)
            
            // Verify the server received correct greeting
            val receivedGreeting = mockServer.receivedGreeting
            assertNotNull(receivedGreeting)
            assertArrayEquals(byteArrayOf(0x05, 0x01, 0x00), receivedGreeting)
            
            // Verify the server received correct CONNECT request
            val receivedConnect = mockServer.receivedConnect
            assertNotNull(receivedConnect)
            assertEquals(0x05, receivedConnect!![0].toInt() and 0xFF) // Version
            assertEquals(0x01, receivedConnect[1].toInt() and 0xFF) // CMD: CONNECT
            assertEquals(0x00, receivedConnect[2].toInt() and 0xFF) // Reserved
            assertEquals(0x01, receivedConnect[3].toInt() and 0xFF) // ATYP: IPv4
            
            socket.close()
        } finally {
            mockServer.stop()
        }
    }
    
    @Test
    fun `SOCKS5 handshake fails with invalid version in greeting response`() = runBlocking {
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x04, 0x00), // Wrong version (SOCKS4)
            connectResponse = byteArrayOf()
        )
        
        mockServer.start()
        
        try {
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            
            val result = tcpHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            
            assertFalse("Handshake should fail with invalid version", result)
            
            socket.close()
        } finally {
            mockServer.stop()
        }
    }
    
    @Test
    fun `SOCKS5 handshake fails when authentication is required`() = runBlocking {
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x05, 0x02), // Version 5, Username/password auth required
            connectResponse = byteArrayOf()
        )
        
        mockServer.start()
        
        try {
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            
            val result = tcpHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            
            assertFalse("Handshake should fail when auth is required", result)
            
            socket.close()
        } finally {
            mockServer.stop()
        }
    }
    
    @Test
    fun `SOCKS5 handshake fails with connection refused error`() = runBlocking {
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x05, 0x00),
            connectResponse = byteArrayOf(
                0x05, 0x05, 0x00, 0x01, // Version, Connection refused (0x05), Reserved, IPv4
                0x00, 0x00, 0x00, 0x00, // Bound address: 0.0.0.0
                0x00, 0x00 // Bound port: 0
            )
        )
        
        mockServer.start()
        
        try {
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            
            val result = tcpHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            
            assertFalse("Handshake should fail with connection refused", result)
            
            socket.close()
        } finally {
            mockServer.stop()
        }
    }
    
    @Test
    fun `SOCKS5 handshake fails with host unreachable error`() = runBlocking {
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x05, 0x00),
            connectResponse = byteArrayOf(
                0x05, 0x04, 0x00, 0x01, // Version, Host unreachable (0x04), Reserved, IPv4
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00
            )
        )
        
        mockServer.start()
        
        try {
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            
            val result = tcpHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            
            assertFalse("Handshake should fail with host unreachable", result)
            
            socket.close()
        } finally {
            mockServer.stop()
        }
    }
    
    @Test
    fun `SOCKS5 handshake handles timeout`() = runBlocking {
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x05, 0x00),
            connectResponse = byteArrayOf(), // Don't send connect response
            delayBeforeConnect = 2000 // Delay longer than socket timeout
        )
        
        mockServer.start()
        
        try {
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 500 // Short timeout
            
            val result = tcpHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            
            assertFalse("Handshake should fail on timeout", result)
            
            socket.close()
        } finally {
            mockServer.stop()
        }
    }
    
    @Test
    fun `SOCKS5 handshake fails with incomplete greeting response`() = runBlocking {
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x05), // Only 1 byte instead of 2
            connectResponse = byteArrayOf()
        )
        
        mockServer.start()
        
        try {
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            
            val result = tcpHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            
            assertFalse("Handshake should fail with incomplete greeting", result)
            
            socket.close()
        } finally {
            mockServer.stop()
        }
    }
    
    @Test
    fun `SOCKS5 handshake fails with incomplete connect response`() = runBlocking {
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x05, 0x00),
            connectResponse = byteArrayOf(0x05, 0x00, 0x00) // Only 3 bytes instead of 10
        )
        
        mockServer.start()
        
        try {
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            
            val result = tcpHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            
            assertFalse("Handshake should fail with incomplete connect response", result)
            
            socket.close()
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Helper function to build a TCP packet for testing.
     * Creates an IP header + TCP header + payload.
     */
    private fun buildTcpPacket(
        sourcePort: Int,
        destPort: Int,
        sequenceNumber: Long,
        acknowledgmentNumber: Long,
        flags: TcpFlags,
        windowSize: Int,
        payload: ByteArray
    ): ByteArray {
        val tcpHeaderSize = 20
        val ipHeaderSize = 20
        val totalSize = ipHeaderSize + tcpHeaderSize + payload.size
        
        val buffer = ByteBuffer.allocate(totalSize)
        
        // Build minimal IP header (20 bytes)
        buffer.put((0x45).toByte()) // Version 4, IHL 5 (20 bytes)
        buffer.put(0) // TOS
        buffer.putShort(totalSize.toShort()) // Total length
        buffer.putShort(0) // Identification
        buffer.putShort(0) // Flags and fragment offset
        buffer.put(64) // TTL
        buffer.put(6) // Protocol (TCP)
        buffer.putShort(0) // Checksum (not validated in this test)
        buffer.putInt(0x0A000002) // Source IP: 10.0.0.2
        buffer.putInt(0x01010101) // Dest IP: 1.1.1.1
        
        // Build TCP header (20 bytes)
        buffer.putShort(sourcePort.toShort()) // Source port
        buffer.putShort(destPort.toShort()) // Dest port
        buffer.putInt(sequenceNumber.toInt()) // Sequence number
        buffer.putInt(acknowledgmentNumber.toInt()) // Acknowledgment number
        buffer.put((5 shl 4).toByte()) // Data offset (5 * 4 = 20 bytes), reserved bits
        buffer.put(flags.toByte().toByte()) // Flags
        buffer.putShort(windowSize.toShort()) // Window size
        buffer.putShort(0) // Checksum (not validated in this test)
        buffer.putShort(0) // Urgent pointer
        
        // Add payload
        buffer.put(payload)
        
        return buffer.array()
    }
    
    /**
     * Mock SOCKS5 server for testing handshake.
     * Simulates a SOCKS5 proxy server with configurable responses.
     */
    private class MockSocks5Server(
        private val greetingResponse: ByteArray,
        private val connectResponse: ByteArray,
        private val delayBeforeConnect: Long = 0
    ) {
        private var serverSocket: ServerSocket? = null
        private var serverThread: Thread? = null
        var receivedGreeting: ByteArray? = null
        var receivedConnect: ByteArray? = null
        val port: Int
            get() = serverSocket?.localPort ?: 0
        
        fun start() {
            serverSocket = ServerSocket(0) // Use any available port
            serverThread = thread {
                try {
                    val clientSocket = serverSocket?.accept()
                    clientSocket?.use { socket ->
                        val input = socket.getInputStream()
                        val output = socket.getOutputStream()
                        
                        // Read greeting
                        receivedGreeting = ByteArray(3)
                        input.read(receivedGreeting)
                        
                        // Send greeting response
                        output.write(greetingResponse)
                        output.flush()
                        
                        // If greeting was successful, handle CONNECT
                        if (greetingResponse.size >= 2 && greetingResponse[1] == 0x00.toByte()) {
                            // Read CONNECT request
                            receivedConnect = ByteArray(10)
                            input.read(receivedConnect)
                            
                            // Delay if requested (for timeout testing)
                            if (delayBeforeConnect > 0) {
                                Thread.sleep(delayBeforeConnect)
                            }
                            
                            // Send CONNECT response
                            if (connectResponse.isNotEmpty()) {
                                output.write(connectResponse)
                                output.flush()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore exceptions in mock server
                }
            }
        }
        
        fun stop() {
            try {
                serverSocket?.close()
                serverThread?.interrupt()
                serverThread?.join(1000)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
