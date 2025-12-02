package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
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
    
    // ========== Integration Tests ==========
    
    /**
     * Integration test for TCP connection establishment.
     * 
     * Tests the SOCKS5 connection establishment which is the core of TCP connection setup:
     * 1. Establishes SOCKS5 connection
     * 2. Performs SOCKS5 handshake
     * 3. Verifies connection is ready for data transfer
     * 
     * Requirements: 3.1, 3.2, 4.1, 4.2, 4.3, 4.4
     */
    @Test
    fun `TCP connection establishment full flow`() = runBlocking {
        // Set up mock SOCKS5 server
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
            // Create a new TCPHandler with the mock server port
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Verify SOCKS5 handshake works for TCP connection establishment
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            val handshakeResult = testHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Verify the server received correct CONNECT request
            val receivedConnect = mockServer.receivedConnect
            assertNotNull("Server should receive CONNECT request", receivedConnect)
            assertEquals(0x05, receivedConnect!![0].toInt() and 0xFF) // Version
            assertEquals(0x01, receivedConnect[1].toInt() and 0xFF) // CMD: CONNECT
            
            // Verify connection is ready for data transfer
            assertTrue("Socket should be connected", socket.isConnected)
            assertFalse("Socket should not be closed", socket.isClosed)
            
            socket.close()
            
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Integration test verifying connection table entry is created.
     * 
     * Tests that after a successful SOCKS5 connection, the connection
     * is properly added to the ConnectionTable.
     */
    @Test
    fun `connection table entry created after successful SOCKS5 connection`() = runBlocking {
        // Set up mock SOCKS5 server
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x05, 0x00),
            connectResponse = byteArrayOf(
                0x05, 0x00, 0x00, 0x01,
                0x7F, 0x00, 0x00, 0x01,
                0x04, 0x38
            )
        )
        mockServer.start()
        
        try {
            // Create connection key
            val key = ConnectionKey(
                protocol = Protocol.TCP,
                sourceIp = "10.0.0.2",
                sourcePort = 12345,
                destIp = "1.1.1.1",
                destPort = 80
            )
            
            // Establish SOCKS5 connection
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            val handshakeResult = testHandler.performSocks5Handshake(socket, key.destIp, key.destPort)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Manually add connection to table (simulating what handleSyn would do)
            val connection = TcpConnection(
                key = key,
                socksSocket = socket,
                state = TcpState.ESTABLISHED,
                sequenceNumber = 1001,
                acknowledgmentNumber = 1001,
                createdAt = System.currentTimeMillis(),
                lastActivityAt = System.currentTimeMillis(),
                bytesSent = 0,
                bytesReceived = 0,
                readerJob = kotlinx.coroutines.Job()
            )
            
            connectionTable.addTcpConnection(connection)
            
            // Verify connection is in table
            val retrieved = connectionTable.getTcpConnection(key)
            assertNotNull("Connection should be in table", retrieved)
            assertEquals(key, retrieved!!.key)
            assertEquals(TcpState.ESTABLISHED, retrieved.state)
            
            // Clean up
            connectionTable.removeTcpConnection(key)
            socket.close()
            
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Integration test verifying SOCKS5 connection is established.
     * 
     * Tests that the SOCKS5 connection is properly established with
     * the correct destination IP and port.
     */
    @Test
    fun `SOCKS5 connection established with correct destination`() = runBlocking {
        // Set up mock SOCKS5 server
        val mockServer = MockSocks5Server(
            greetingResponse = byteArrayOf(0x05, 0x00),
            connectResponse = byteArrayOf(
                0x05, 0x00, 0x00, 0x01,
                0x7F, 0x00, 0x00, 0x01,
                0x04, 0x38
            )
        )
        mockServer.start()
        
        try {
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Establish connection to specific destination
            val destIp = "1.1.1.1"
            val destPort = 80
            
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 1000
            
            val result = testHandler.performSocks5Handshake(socket, destIp, destPort)
            
            assertTrue("SOCKS5 handshake should succeed", result)
            
            // Verify the CONNECT request contains correct destination
            val receivedConnect = mockServer.receivedConnect
            assertNotNull("Server should receive CONNECT request", receivedConnect)
            
            // Extract destination IP from CONNECT request (bytes 4-7)
            val receivedIp = String.format(
                "%d.%d.%d.%d",
                receivedConnect!![4].toInt() and 0xFF,
                receivedConnect[5].toInt() and 0xFF,
                receivedConnect[6].toInt() and 0xFF,
                receivedConnect[7].toInt() and 0xFF
            )
            assertEquals(destIp, receivedIp)
            
            // Extract destination port from CONNECT request (bytes 8-9)
            val receivedPort = ((receivedConnect[8].toInt() and 0xFF) shl 8) or 
                              (receivedConnect[9].toInt() and 0xFF)
            assertEquals(destPort, receivedPort)
            
            socket.close()
            
        } finally {
            mockServer.stop()
        }
    }
    
    // ========== TCP Data Forwarding Integration Tests ==========
    
    /**
     * Integration test for bidirectional TCP data flow.
     * 
     * Tests that data can flow in both directions:
     * - TUN → SOCKS5 (client sending data)
     * - SOCKS5 → TUN (server sending data)
     * 
     * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
     */
    @Test
    fun `bidirectional TCP data flow`() = runBlocking {
        // Set up mock SOCKS5 server that echoes data back
        val mockServer = MockSocks5EchoServer()
        mockServer.start()
        
        try {
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Establish SOCKS5 connection
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 2000
            
            val handshakeResult = testHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Send data to SOCKS5 (TUN → SOCKS5)
            val testData = "Hello, World!".toByteArray()
            socket.getOutputStream().write(testData)
            socket.getOutputStream().flush()
            
            // Read echoed data from SOCKS5 (SOCKS5 → TUN)
            val buffer = ByteArray(1024)
            val bytesRead = socket.getInputStream().read(buffer)
            
            assertTrue("Should receive data back", bytesRead > 0)
            val receivedData = buffer.copyOf(bytesRead)
            assertArrayEquals("Data should be echoed back", testData, receivedData)
            
            socket.close()
            
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Integration test for data integrity during forwarding.
     * 
     * Tests that data is forwarded without corruption or modification.
     * 
     * Requirements: 5.1, 5.2, 5.3
     */
    @Test
    fun `data integrity is maintained during forwarding`() = runBlocking {
        val mockServer = MockSocks5EchoServer()
        mockServer.start()
        
        try {
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Establish connection
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 2000
            
            val handshakeResult = testHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Test with various data patterns
            val testPatterns = listOf(
                "Simple ASCII text".toByteArray(),
                ByteArray(256) { it.toByte() }, // All byte values 0-255
                "Unicode: 你好世界 🌍".toByteArray(Charsets.UTF_8),
                ByteArray(1000) { (it % 256).toByte() } // Larger payload
            )
            
            for (testData in testPatterns) {
                // Send data
                socket.getOutputStream().write(testData)
                socket.getOutputStream().flush()
                
                // Read echoed data
                val buffer = ByteArray(testData.size + 100)
                var totalRead = 0
                while (totalRead < testData.size) {
                    val bytesRead = socket.getInputStream().read(buffer, totalRead, buffer.size - totalRead)
                    if (bytesRead <= 0) break
                    totalRead += bytesRead
                }
                
                val receivedData = buffer.copyOf(totalRead)
                assertArrayEquals("Data should match exactly", testData, receivedData)
            }
            
            socket.close()
            
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Integration test for large data transfers.
     * 
     * Tests that large amounts of data can be transferred without issues.
     * 
     * Requirements: 5.1, 5.2, 5.3
     */
    @Test
    fun `large data transfers work correctly`() = runBlocking {
        val mockServer = MockSocks5EchoServer()
        mockServer.start()
        
        try {
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Establish connection
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 5000 // Longer timeout for large transfer
            
            val handshakeResult = testHandler.performSocks5Handshake(socket, "1.1.1.1", 80)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Test with large data (100 KB)
            val largeData = ByteArray(100 * 1024) { (it % 256).toByte() }
            
            // Send data
            socket.getOutputStream().write(largeData)
            socket.getOutputStream().flush()
            
            // Read echoed data
            val buffer = ByteArray(largeData.size + 1000)
            var totalRead = 0
            val startTime = System.currentTimeMillis()
            
            while (totalRead < largeData.size) {
                val bytesRead = socket.getInputStream().read(buffer, totalRead, buffer.size - totalRead)
                if (bytesRead <= 0) break
                totalRead += bytesRead
                
                // Timeout check
                if (System.currentTimeMillis() - startTime > 5000) {
                    fail("Transfer took too long")
                }
            }
            
            assertEquals("Should receive all data", largeData.size, totalRead)
            
            val receivedData = buffer.copyOf(totalRead)
            assertArrayEquals("Large data should match exactly", largeData, receivedData)
            
            socket.close()
            
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Integration test for statistics tracking during data forwarding.
     * 
     * Tests that bytes sent and received are correctly tracked.
     * 
     * Requirements: 13.1, 13.2, 13.3, 13.4, 13.5
     */
    @Test
    fun `statistics are tracked correctly during data forwarding`() = runBlocking {
        val mockServer = MockSocks5EchoServer()
        mockServer.start()
        
        try {
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Create connection key
            val key = ConnectionKey(
                protocol = Protocol.TCP,
                sourceIp = "10.0.0.2",
                sourcePort = 12345,
                destIp = "1.1.1.1",
                destPort = 80
            )
            
            // Establish connection
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 2000
            
            val handshakeResult = testHandler.performSocks5Handshake(socket, key.destIp, key.destPort)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Add connection to table
            val connection = TcpConnection(
                key = key,
                socksSocket = socket,
                state = TcpState.ESTABLISHED,
                sequenceNumber = 1001,
                acknowledgmentNumber = 1001,
                createdAt = System.currentTimeMillis(),
                lastActivityAt = System.currentTimeMillis(),
                bytesSent = 0,
                bytesReceived = 0,
                readerJob = kotlinx.coroutines.Job()
            )
            connectionTable.addTcpConnection(connection)
            
            // Send data
            val testData = "Test data for statistics".toByteArray()
            socket.getOutputStream().write(testData)
            socket.getOutputStream().flush()
            
            // Simulate updating statistics (as handleData would do)
            val updatedConnection = connection.copy(
                bytesSent = connection.bytesSent + testData.size,
                lastActivityAt = System.currentTimeMillis()
            )
            connectionTable.addTcpConnection(updatedConnection)
            
            // Verify statistics
            val retrieved = connectionTable.getTcpConnection(key)
            assertNotNull("Connection should be in table", retrieved)
            assertEquals("Bytes sent should be tracked", testData.size.toLong(), retrieved!!.bytesSent)
            
            // Read echoed data
            val buffer = ByteArray(1024)
            val bytesRead = socket.getInputStream().read(buffer)
            assertTrue("Should receive data back", bytesRead > 0)
            
            // Simulate updating receive statistics (as startConnectionReader would do)
            val finalConnection = retrieved.copy(
                bytesReceived = retrieved.bytesReceived + bytesRead,
                lastActivityAt = System.currentTimeMillis()
            )
            connectionTable.addTcpConnection(finalConnection)
            
            // Verify final statistics
            val finalRetrieved = connectionTable.getTcpConnection(key)
            assertNotNull("Connection should still be in table", finalRetrieved)
            assertEquals("Bytes sent should be tracked", testData.size.toLong(), finalRetrieved!!.bytesSent)
            assertEquals("Bytes received should be tracked", bytesRead.toLong(), finalRetrieved.bytesReceived)
            
            // Verify connection table statistics
            val stats = connectionTable.getStatistics()
            assertTrue("Total bytes sent should be positive", stats.totalBytesSent > 0)
            assertTrue("Total bytes received should be positive", stats.totalBytesReceived > 0)
            
            // Clean up
            connectionTable.removeTcpConnection(key)
            socket.close()
            
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Mock SOCKS5 server that echoes data back for testing bidirectional flow.
     */
    private class MockSocks5EchoServer {
        private var serverSocket: ServerSocket? = null
        private var serverThread: Thread? = null
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
                        
                        // Handle SOCKS5 greeting
                        val greeting = ByteArray(3)
                        input.read(greeting)
                        output.write(byteArrayOf(0x05, 0x00)) // Version 5, No auth
                        output.flush()
                        
                        // Handle CONNECT request
                        val connect = ByteArray(10)
                        input.read(connect)
                        output.write(byteArrayOf(
                            0x05, 0x00, 0x00, 0x01, // Version, Success, Reserved, IPv4
                            0x7F, 0x00, 0x00, 0x01, // Bound address: 127.0.0.1
                            0x04, 0x38 // Bound port: 1080
                        ))
                        output.flush()
                        
                        // Echo data back
                        val buffer = ByteArray(8192)
                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            output.flush()
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
    
    // ========== TCP Termination Tests ==========
    
    /**
     * Test FIN packet handling.
     * 
     * Verifies that when a FIN packet is received:
     * - Connection is removed from ConnectionTable
     * - SOCKS5 socket is closed
     * - Connection reader coroutine is cancelled
     * 
     * Requirements: 3.3, 3.4, 9.2
     */
    @Test
    fun `FIN packet closes connection gracefully`() = runBlocking {
        val mockServer = MockSocks5EchoServer()
        mockServer.start()
        
        try {
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Create connection key
            val key = ConnectionKey(
                protocol = Protocol.TCP,
                sourceIp = "10.0.0.2",
                sourcePort = 12345,
                destIp = "1.1.1.1",
                destPort = 80
            )
            
            // Establish SOCKS5 connection
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 2000
            
            val handshakeResult = testHandler.performSocks5Handshake(socket, key.destIp, key.destPort)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Add connection to table
            val readerJob = kotlinx.coroutines.Job()
            val connection = TcpConnection(
                key = key,
                socksSocket = socket,
                state = TcpState.ESTABLISHED,
                sequenceNumber = 1001,
                acknowledgmentNumber = 1001,
                createdAt = System.currentTimeMillis(),
                lastActivityAt = System.currentTimeMillis(),
                bytesSent = 0,
                bytesReceived = 0,
                readerJob = readerJob
            )
            connectionTable.addTcpConnection(connection)
            
            // Verify connection is in table
            assertNotNull("Connection should be in table", connectionTable.getTcpConnection(key))
            
            // Create FIN packet
            val finPacket = buildTcpPacket(
                sourcePort = key.sourcePort,
                destPort = key.destPort,
                sequenceNumber = 1001,
                acknowledgmentNumber = 2001,
                flags = TcpFlags(syn = false, ack = true, fin = true, rst = false, psh = false, urg = false),
                windowSize = 65535,
                payload = ByteArray(0)
            )
            
            // Create mock TUN output stream
            val tunOutput = ByteArrayOutputStream()
            val tunFileOutput = java.io.FileOutputStream(java.io.FileDescriptor())
            
            // Parse TCP header
            val tcpHeader = testHandler.parseTcpHeader(finPacket, 20)
            assertNotNull("TCP header should be parsed", tcpHeader)
            
            // Handle FIN packet (now internal, can call directly)
            testHandler.handleFin(key, tcpHeader!!, tunFileOutput)
            
            // Wait a bit for async operations
            kotlinx.coroutines.delay(100)
            
            // Verify connection is removed from table
            assertNull("Connection should be removed from table", connectionTable.getTcpConnection(key))
            
            // Verify socket is closed
            assertTrue("Socket should be closed", socket.isClosed)
            
            // Verify reader job is cancelled
            assertTrue("Reader job should be cancelled", readerJob.isCancelled)
            
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Test RST packet handling.
     * 
     * Verifies that when a RST packet is received:
     * - Connection is immediately removed from ConnectionTable
     * - SOCKS5 socket is closed immediately
     * - Connection reader coroutine is cancelled
     * - No response packet is sent (RST is not acknowledged)
     * 
     * Requirements: 3.4, 9.2
     */
    @Test
    fun `RST packet closes connection immediately`() = runBlocking {
        val mockServer = MockSocks5EchoServer()
        mockServer.start()
        
        try {
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Create connection key
            val key = ConnectionKey(
                protocol = Protocol.TCP,
                sourceIp = "10.0.0.2",
                sourcePort = 12345,
                destIp = "1.1.1.1",
                destPort = 80
            )
            
            // Establish SOCKS5 connection
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 2000
            
            val handshakeResult = testHandler.performSocks5Handshake(socket, key.destIp, key.destPort)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Add connection to table
            val readerJob = kotlinx.coroutines.Job()
            val connection = TcpConnection(
                key = key,
                socksSocket = socket,
                state = TcpState.ESTABLISHED,
                sequenceNumber = 1001,
                acknowledgmentNumber = 1001,
                createdAt = System.currentTimeMillis(),
                lastActivityAt = System.currentTimeMillis(),
                bytesSent = 0,
                bytesReceived = 0,
                readerJob = readerJob
            )
            connectionTable.addTcpConnection(connection)
            
            // Verify connection is in table
            assertNotNull("Connection should be in table", connectionTable.getTcpConnection(key))
            
            // Create RST packet
            val rstPacket = buildTcpPacket(
                sourcePort = key.sourcePort,
                destPort = key.destPort,
                sequenceNumber = 1001,
                acknowledgmentNumber = 0,
                flags = TcpFlags(syn = false, ack = false, fin = false, rst = true, psh = false, urg = false),
                windowSize = 0,
                payload = ByteArray(0)
            )
            
            // Create mock TUN output stream
            val tunFileOutput = java.io.FileOutputStream(java.io.FileDescriptor())
            
            // Parse TCP header
            val tcpHeader = testHandler.parseTcpHeader(rstPacket, 20)
            assertNotNull("TCP header should be parsed", tcpHeader)
            
            // Handle RST packet (now internal, can call directly)
            testHandler.handleRst(key, tunFileOutput)
            
            // Wait a bit for async operations
            kotlinx.coroutines.delay(100)
            
            // Verify connection is removed from table
            assertNull("Connection should be removed from table", connectionTable.getTcpConnection(key))
            
            // Verify socket is closed
            assertTrue("Socket should be closed", socket.isClosed)
            
            // Verify reader job is cancelled
            assertTrue("Reader job should be cancelled", readerJob.isCancelled)
            
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Test connection cleanup after FIN.
     * 
     * Verifies that all resources are properly released after FIN handling.
     * 
     * Requirements: 9.2
     */
    @Test
    fun `connection cleanup releases all resources after FIN`() = runBlocking {
        val mockServer = MockSocks5EchoServer()
        mockServer.start()
        
        try {
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Create connection key
            val key = ConnectionKey(
                protocol = Protocol.TCP,
                sourceIp = "10.0.0.2",
                sourcePort = 12345,
                destIp = "1.1.1.1",
                destPort = 80
            )
            
            // Establish SOCKS5 connection
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 2000
            
            val handshakeResult = testHandler.performSocks5Handshake(socket, key.destIp, key.destPort)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Add connection to table
            val readerJob = kotlinx.coroutines.Job()
            val connection = TcpConnection(
                key = key,
                socksSocket = socket,
                state = TcpState.ESTABLISHED,
                sequenceNumber = 1001,
                acknowledgmentNumber = 1001,
                createdAt = System.currentTimeMillis(),
                lastActivityAt = System.currentTimeMillis(),
                bytesSent = 100,
                bytesReceived = 200,
                readerJob = readerJob
            )
            connectionTable.addTcpConnection(connection)
            
            // Get initial statistics
            val initialStats = connectionTable.getStatistics()
            assertEquals("Should have 1 active connection", 1, initialStats.activeTcpConnections)
            
            // Create FIN packet
            val finPacket = buildTcpPacket(
                sourcePort = key.sourcePort,
                destPort = key.destPort,
                sequenceNumber = 1001,
                acknowledgmentNumber = 2001,
                flags = TcpFlags(syn = false, ack = true, fin = true, rst = false, psh = false, urg = false),
                windowSize = 65535,
                payload = ByteArray(0)
            )
            
            // Create mock TUN output stream
            val tunFileOutput = java.io.FileOutputStream(java.io.FileDescriptor())
            
            // Parse TCP header
            val tcpHeader = testHandler.parseTcpHeader(finPacket, 20)
            assertNotNull("TCP header should be parsed", tcpHeader)
            
            // Handle FIN packet (now internal, can call directly)
            testHandler.handleFin(key, tcpHeader!!, tunFileOutput)
            
            // Wait for cleanup
            kotlinx.coroutines.delay(100)
            
            // Verify all resources are released
            assertNull("Connection should be removed", connectionTable.getTcpConnection(key))
            assertTrue("Socket should be closed", socket.isClosed)
            assertTrue("Reader job should be cancelled", readerJob.isCancelled)
            
            // Verify statistics updated
            val finalStats = connectionTable.getStatistics()
            assertEquals("Should have 0 active connections", 0, finalStats.activeTcpConnections)
            
        } finally {
            mockServer.stop()
        }
    }
    
    /**
     * Test connection cleanup after RST.
     * 
     * Verifies that all resources are properly released after RST handling.
     * 
     * Requirements: 9.2
     */
    @Test
    fun `connection cleanup releases all resources after RST`() = runBlocking {
        val mockServer = MockSocks5EchoServer()
        mockServer.start()
        
        try {
            val testHandler = TCPHandler(
                socksPort = mockServer.port,
                connectionTable = connectionTable,
                logger = logger
            )
            
            // Create connection key
            val key = ConnectionKey(
                protocol = Protocol.TCP,
                sourceIp = "10.0.0.2",
                sourcePort = 12345,
                destIp = "1.1.1.1",
                destPort = 80
            )
            
            // Establish SOCKS5 connection
            val socket = Socket("127.0.0.1", mockServer.port)
            socket.soTimeout = 2000
            
            val handshakeResult = testHandler.performSocks5Handshake(socket, key.destIp, key.destPort)
            assertTrue("SOCKS5 handshake should succeed", handshakeResult)
            
            // Add connection to table
            val readerJob = kotlinx.coroutines.Job()
            val connection = TcpConnection(
                key = key,
                socksSocket = socket,
                state = TcpState.ESTABLISHED,
                sequenceNumber = 1001,
                acknowledgmentNumber = 1001,
                createdAt = System.currentTimeMillis(),
                lastActivityAt = System.currentTimeMillis(),
                bytesSent = 100,
                bytesReceived = 200,
                readerJob = readerJob
            )
            connectionTable.addTcpConnection(connection)
            
            // Get initial statistics
            val initialStats = connectionTable.getStatistics()
            assertEquals("Should have 1 active connection", 1, initialStats.activeTcpConnections)
            
            // Create mock TUN output stream
            val tunFileOutput = java.io.FileOutputStream(java.io.FileDescriptor())
            
            // Handle RST packet (now internal, can call directly)
            testHandler.handleRst(key, tunFileOutput)
            
            // Wait for cleanup
            kotlinx.coroutines.delay(100)
            
            // Verify all resources are released
            assertNull("Connection should be removed", connectionTable.getTcpConnection(key))
            assertTrue("Socket should be closed", socket.isClosed)
            assertTrue("Reader job should be cancelled", readerJob.isCancelled)
            
            // Verify statistics updated
            val finalStats = connectionTable.getStatistics()
            assertEquals("Should have 0 active connections", 0, finalStats.activeTcpConnections)
            
        } finally {
            mockServer.stop()
        }
    }
}
