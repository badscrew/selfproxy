package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPv4Header
import com.sshtunnel.android.vpn.packet.Protocol
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Integration test for DNS routing through SOCKS5.
 * 
 * Tests the complete flow:
 * 1. UDP DNS query received
 * 2. Query routed through SOCKS5 using DNS-over-TCP
 * 3. DNS response received
 * 4. UDP response packet constructed and sent
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */
class DnsRoutingIntegrationTest {
    
    private lateinit var mockSocksServer: MockSocksServer
    private lateinit var udpHandler: UDPHandler
    private lateinit var connectionTable: ConnectionTable
    private lateinit var logger: TestLogger
    private lateinit var mockTunOutputStream: MockFileOutputStream
    
    @Before
    fun setup() {
        // Start mock SOCKS5 server
        mockSocksServer = MockSocksServer()
        mockSocksServer.start()
        
        // Setup components
        connectionTable = ConnectionTable()
        logger = TestLogger()
        mockTunOutputStream = MockFileOutputStream()
        
        udpHandler = UDPHandler(
            socksPort = mockSocksServer.port,
            connectionTable = connectionTable,
            logger = logger
        )
    }
    
    @After
    fun teardown() {
        mockSocksServer.stop()
    }
    
    // ========== DNS Query Through SOCKS5 Tests ==========
    
    @Test
    fun `route DNS query through SOCKS5 and receive response`() = runBlocking {
        // Prepare mock DNS response
        val expectedDomain = "example.com"
        val expectedIp = "93.184.216.34"
        mockSocksServer.setDnsResponse(expectedDomain, expectedIp)
        
        // Build DNS query packet
        val dnsQuery = buildDnsQuery(expectedDomain)
        val packet = buildUdpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53,
            payload = dnsQuery
        )
        
        // Parse IP header
        val ipHeader = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = packet.size,
            identification = 0,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.UDP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "8.8.8.8"
        )
        
        // Handle the UDP packet
        udpHandler.handleUdpPacket(packet, ipHeader, mockTunOutputStream)
        
        // Wait for async processing
        delay(500)
        
        // Verify SOCKS5 server received the connection
        assertTrue("SOCKS5 server should have received connection", mockSocksServer.hadConnection())
        
        // Verify DNS query was sent
        assertTrue("DNS query should have been sent", mockSocksServer.receivedDnsQuery())
        
        // Verify response packet was written to TUN
        val responsePackets = mockTunOutputStream.getWrittenPackets()
        assertTrue("Should have written response packet", responsePackets.isNotEmpty())
        
        // Verify response packet structure
        val responsePacket = responsePackets[0]
        assertTrue("Response packet should be valid", responsePacket.size >= 28) // IP + UDP headers
        
        // Verify it's a UDP packet
        val protocol = responsePacket[9].toInt() and 0xFF
        assertEquals("Response should be UDP", 17, protocol)
        
        // Verify source and dest are swapped
        val responseSourceIp = extractIpAddress(responsePacket, 12)
        val responseDestIp = extractIpAddress(responsePacket, 16)
        assertEquals("Response source should be DNS server", "8.8.8.8", responseSourceIp)
        assertEquals("Response dest should be original source", "10.0.0.2", responseDestIp)
        
        // Verify ports are swapped
        val responseSourcePort = extractPort(responsePacket, 20)
        val responseDestPort = extractPort(responsePacket, 22)
        assertEquals("Response source port should be 53", 53, responseSourcePort)
        assertEquals("Response dest port should be original source port", 54321, responseDestPort)
    }
    
    @Test
    fun `handle DNS timeout gracefully`() = runBlocking {
        // Configure mock server to delay response beyond timeout
        mockSocksServer.setResponseDelay(6000) // 6 seconds, timeout is 5 seconds
        
        val dnsQuery = buildDnsQuery("timeout.test")
        val packet = buildUdpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "8.8.8.8",
            destPort = 53,
            payload = dnsQuery
        )
        
        val ipHeader = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = packet.size,
            identification = 0,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.UDP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "8.8.8.8"
        )
        
        // Handle the UDP packet
        udpHandler.handleUdpPacket(packet, ipHeader, mockTunOutputStream)
        
        // Wait for timeout
        delay(6000)
        
        // Verify no response packet was written (timeout)
        val responsePackets = mockTunOutputStream.getWrittenPackets()
        assertTrue("Should not have written response packet on timeout", responsePackets.isEmpty())
        
        // Verify timeout was logged
        assertTrue("Should have logged timeout warning", logger.hasWarning("timed out"))
    }
    
    @Test
    fun `handle SOCKS5 connection failure`() = runBlocking {
        // Stop SOCKS5 server to simulate connection failure
        mockSocksServer.stop()
        
        val dnsQuery = buildDnsQuery("fail.test")
        val packet = buildUdpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 11111,
            destIp = "8.8.8.8",
            destPort = 53,
            payload = dnsQuery
        )
        
        val ipHeader = IPv4Header(
            version = 4,
            headerLength = 20,
            totalLength = packet.size,
            identification = 0,
            flags = 0,
            fragmentOffset = 0,
            ttl = 64,
            protocol = Protocol.UDP,
            checksum = 0,
            sourceIP = "10.0.0.2",
            destIP = "8.8.8.8"
        )
        
        // Handle the UDP packet
        udpHandler.handleUdpPacket(packet, ipHeader, mockTunOutputStream)
        
        // Wait for processing
        delay(500)
        
        // Verify no response packet was written
        val responsePackets = mockTunOutputStream.getWrittenPackets()
        assertTrue("Should not have written response packet on connection failure", responsePackets.isEmpty())
        
        // Verify error was logged
        assertTrue("Should have logged error", logger.hasError())
    }
    
    @Test
    fun `handle multiple concurrent DNS queries`() = runBlocking {
        // Setup multiple DNS responses
        mockSocksServer.setDnsResponse("example1.com", "1.1.1.1")
        mockSocksServer.setDnsResponse("example2.com", "2.2.2.2")
        mockSocksServer.setDnsResponse("example3.com", "3.3.3.3")
        
        val domains = listOf("example1.com", "example2.com", "example3.com")
        
        // Send multiple DNS queries concurrently
        domains.forEachIndexed { index, domain ->
            launch {
                val dnsQuery = buildDnsQuery(domain)
                val packet = buildUdpPacket(
                    sourceIp = "10.0.0.2",
                    sourcePort = 50000 + index,
                    destIp = "8.8.8.8",
                    destPort = 53,
                    payload = dnsQuery
                )
                
                val ipHeader = IPv4Header(
                    version = 4,
                    headerLength = 20,
                    totalLength = packet.size,
                    identification = 0,
                    flags = 0,
                    fragmentOffset = 0,
                    ttl = 64,
                    protocol = Protocol.UDP,
                    checksum = 0,
                    sourceIP = "10.0.0.2",
                    destIP = "8.8.8.8"
                )
                
                udpHandler.handleUdpPacket(packet, ipHeader, mockTunOutputStream)
            }
        }
        
        // Wait for all queries to complete
        delay(1000)
        
        // Verify all responses were written
        val responsePackets = mockTunOutputStream.getWrittenPackets()
        assertEquals("Should have received 3 responses", 3, responsePackets.size)
    }
    
    // ========== Helper Functions ==========
    
    private fun buildUdpPacket(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpHeaderSize = 8
        val ipHeaderSize = 20
        val udpLength = udpHeaderSize + payload.size
        val totalSize = ipHeaderSize + udpLength
        
        val buffer = ByteBuffer.allocate(totalSize)
        
        // IP header
        buffer.put((0x45).toByte()) // Version 4, IHL 5
        buffer.put(0) // TOS
        buffer.putShort(totalSize.toShort())
        buffer.putShort(0) // Identification
        buffer.putShort(0) // Flags
        buffer.put(64) // TTL
        buffer.put(17) // Protocol (UDP)
        buffer.putShort(0) // Checksum
        buffer.put(ipStringToBytes(sourceIp))
        buffer.put(ipStringToBytes(destIp))
        
        // UDP header
        buffer.putShort(sourcePort.toShort())
        buffer.putShort(destPort.toShort())
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0) // Checksum
        
        // Payload
        buffer.put(payload)
        
        return buffer.array()
    }
    
    private fun buildDnsQuery(domain: String): ByteArray {
        val buffer = ByteBuffer.allocate(512)
        
        // DNS header
        buffer.putShort(0x1234.toShort()) // Transaction ID
        buffer.putShort(0x0100.toShort()) // Flags: standard query
        buffer.putShort(1) // Questions: 1
        buffer.putShort(0) // Answer RRs
        buffer.putShort(0) // Authority RRs
        buffer.putShort(0) // Additional RRs
        
        // Question section
        domain.split('.').forEach { label ->
            buffer.put(label.length.toByte())
            buffer.put(label.toByteArray())
        }
        buffer.put(0) // End of name
        
        buffer.putShort(1) // Type: A
        buffer.putShort(1) // Class: IN
        
        return buffer.array().copyOf(buffer.position())
    }
    
    private fun ipStringToBytes(ip: String): ByteArray {
        return ip.split('.').map { it.toInt().toByte() }.toByteArray()
    }
    
    private fun extractIpAddress(packet: ByteArray, offset: Int): String {
        return "${packet[offset].toInt() and 0xFF}.${packet[offset + 1].toInt() and 0xFF}." +
               "${packet[offset + 2].toInt() and 0xFF}.${packet[offset + 3].toInt() and 0xFF}"
    }
    
    private fun extractPort(packet: ByteArray, offset: Int): Int {
        return ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
    }
    
    // ========== Mock Classes ==========
    
    /**
     * Mock SOCKS5 server for testing DNS routing.
     */
    private class MockSocksServer {
        private var serverSocket: ServerSocket? = null
        private val running = AtomicBoolean(false)
        private val dnsResponses = mutableMapOf<String, String>()
        private var responseDelay = 0L
        private val hadConnectionFlag = AtomicBoolean(false)
        private val receivedDnsQueryFlag = AtomicBoolean(false)
        
        val port: Int
            get() = serverSocket?.localPort ?: 0
        
        fun start() {
            serverSocket = ServerSocket(0) // Random available port
            running.set(true)
            
            // Accept connections in background
            Thread {
                while (running.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        hadConnectionFlag.set(true)
                        handleClient(client)
                    } catch (e: Exception) {
                        if (running.get()) {
                            e.printStackTrace()
                        }
                    }
                }
            }.start()
        }
        
        fun stop() {
            running.set(false)
            serverSocket?.close()
        }
        
        fun setDnsResponse(domain: String, ip: String) {
            dnsResponses[domain] = ip
        }
        
        fun setResponseDelay(delayMs: Long) {
            responseDelay = delayMs
        }
        
        fun hadConnection(): Boolean = hadConnectionFlag.get()
        fun receivedDnsQuery(): Boolean = receivedDnsQueryFlag.get()
        
        private fun handleClient(client: Socket) {
            Thread {
                try {
                    val input = client.getInputStream()
                    val output = client.getOutputStream()
                    
                    // SOCKS5 handshake
                    if (!performHandshake(input, output)) {
                        client.close()
                        return@Thread
                    }
                    
                    // Handle DNS-over-TCP
                    handleDnsQuery(input, output)
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    client.close()
                }
            }.start()
        }
        
        private fun performHandshake(input: InputStream, output: OutputStream): Boolean {
            // Read greeting
            val greeting = ByteArray(3)
            if (input.read(greeting) != 3) return false
            if (greeting[0] != 0x05.toByte()) return false
            
            // Send method selection
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()
            
            // Read connect request
            val request = ByteArray(10)
            if (input.read(request) < 10) return false
            if (request[0] != 0x05.toByte() || request[1] != 0x01.toByte()) return false
            
            // Send success response
            val response = byteArrayOf(
                0x05, 0x00, 0x00, 0x01,
                0, 0, 0, 0,  // Bind address
                0, 0          // Bind port
            )
            output.write(response)
            output.flush()
            
            return true
        }
        
        private fun handleDnsQuery(input: InputStream, output: OutputStream) {
            try {
                // Read DNS query length
                val lengthBytes = ByteArray(2)
                if (input.read(lengthBytes) != 2) return
                
                val queryLength = ByteBuffer.wrap(lengthBytes).short.toInt() and 0xFFFF
                if (queryLength <= 0 || queryLength > 4096) return
                
                // Read DNS query
                val query = ByteArray(queryLength)
                var totalRead = 0
                while (totalRead < queryLength) {
                    val read = input.read(query, totalRead, queryLength - totalRead)
                    if (read <= 0) return
                    totalRead += read
                }
                
                receivedDnsQueryFlag.set(true)
                
                // Apply delay if configured
                if (responseDelay > 0) {
                    Thread.sleep(responseDelay)
                }
                
                // Build DNS response
                val response = buildDnsResponse(query)
                
                // Send response with length prefix
                val responseLengthBytes = ByteBuffer.allocate(2).putShort(response.size.toShort()).array()
                output.write(responseLengthBytes)
                output.write(response)
                output.flush()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        private fun buildDnsResponse(query: ByteArray): ByteArray {
            // Simple DNS response builder
            val buffer = ByteBuffer.allocate(512)
            
            // Copy query header and modify flags
            buffer.put(query.copyOf(12))
            buffer.putShort(2, 0x8180.toShort()) // Response flags
            buffer.putShort(6, 1) // Answer RRs: 1
            
            // Copy question section (skip header)
            var pos = 12
            while (pos < query.size && query[pos] != 0.toByte()) {
                val labelLen = query[pos].toInt() and 0xFF
                buffer.put(query.copyOfRange(pos, pos + labelLen + 1))
                pos += labelLen + 1
            }
            buffer.put(0) // End of name
            pos++
            
            // Copy QTYPE and QCLASS
            if (pos + 4 <= query.size) {
                buffer.put(query.copyOfRange(pos, pos + 4))
            }
            
            // Answer section
            buffer.putShort(0xC00C.toShort()) // Name pointer
            buffer.putShort(1) // Type: A
            buffer.putShort(1) // Class: IN
            buffer.putInt(300) // TTL: 5 minutes
            buffer.putShort(4) // Data length
            buffer.put(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte())) // IP: 93.184.216.34
            
            return buffer.array().copyOf(buffer.position())
        }
    }
    
    /**
     * Mock FileOutputStream that captures written packets.
     */
    private class MockFileOutputStream : FileOutputStream(java.io.FileDescriptor()) {
        private val packets = mutableListOf<ByteArray>()
        
        override fun write(b: ByteArray) {
            packets.add(b.copyOf())
        }
        
        override fun write(b: ByteArray, off: Int, len: Int) {
            packets.add(b.copyOfRange(off, off + len))
        }
        
        override fun flush() {
            // No-op
        }
        
        fun getWrittenPackets(): List<ByteArray> = packets.toList()
    }
    
    /**
     * Test logger that captures log messages.
     */
    private class TestLogger : Logger {
        private val logs = mutableListOf<String>()
        private val warnings = mutableListOf<String>()
        private val errors = mutableListOf<String>()
        
        override fun verbose(tag: String, message: String, throwable: Throwable?) {
            logs.add("VERBOSE: $tag: $message")
        }
        
        override fun debug(tag: String, message: String, throwable: Throwable?) {
            logs.add("DEBUG: $tag: $message")
        }
        
        override fun info(tag: String, message: String, throwable: Throwable?) {
            logs.add("INFO: $tag: $message")
        }
        
        override fun warn(tag: String, message: String, throwable: Throwable?) {
            warnings.add(message)
            logs.add("WARN: $tag: $message")
        }
        
        override fun error(tag: String, message: String, throwable: Throwable?) {
            errors.add(message)
            logs.add("ERROR: $tag: $message")
        }
        
        override fun getLogEntries(): List<com.sshtunnel.logging.LogEntry> = emptyList()
        override fun clearLogs() { logs.clear() }
        override fun setVerboseEnabled(enabled: Boolean) {}
        override fun isVerboseEnabled(): Boolean = true
        
        fun hasWarning(substring: String): Boolean = warnings.any { it.contains(substring, ignoreCase = true) }
        fun hasError(): Boolean = errors.isNotEmpty()
    }
}
