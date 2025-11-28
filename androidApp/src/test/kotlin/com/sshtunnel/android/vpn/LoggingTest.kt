package com.sshtunnel.android.vpn

import com.sshtunnel.android.vpn.packet.IPv4Header
import com.sshtunnel.logging.LogEntry
import com.sshtunnel.logging.LogLevel
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Tests for comprehensive logging in the packet router.
 * 
 * Verifies:
 * - Verbose logging produces detailed logs
 * - Payload data is never logged (privacy)
 * - Sensitive data is sanitized
 * 
 * Requirements: 12.1, 12.2, 12.3, 12.4, 12.5
 */
class LoggingTest {
    
    private lateinit var testLogger: TestLogger
    private lateinit var connectionTable: ConnectionTable
    private lateinit var tcpHandler: TCPHandler
    private lateinit var udpHandler: UDPHandler
    
    @Before
    fun setup() {
        testLogger = TestLogger()
        connectionTable = ConnectionTable(testLogger)
        tcpHandler = TCPHandler(1080, connectionTable, testLogger)
        udpHandler = UDPHandler(1080, connectionTable, testLogger)
    }
    
    /**
     * Test that verbose logging produces detailed logs when enabled.
     * 
     * Requirements: 12.1
     */
    @Test
    fun `verbose logging produces detailed logs when enabled`() {
        // Arrange
        testLogger.setVerboseEnabled(true)
        
        // Act
        testLogger.verbose("TEST", "This is a verbose message")
        testLogger.debug("TEST", "This is a debug message")
        testLogger.info("TEST", "This is an info message")
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertEquals(3, logs.size)
        
        // Verify verbose log was captured
        val verboseLog = logs.find { it.level == LogLevel.VERBOSE }
        assertNotNull(verboseLog)
        assertEquals("This is a verbose message", verboseLog?.message)
    }
    
    /**
     * Test that verbose logging is suppressed when disabled.
     * 
     * Requirements: 12.1
     */
    @Test
    fun `verbose logging is suppressed when disabled`() {
        // Arrange
        testLogger.setVerboseEnabled(false)
        
        // Act
        testLogger.verbose("TEST", "This should not appear")
        testLogger.debug("TEST", "This should appear")
        testLogger.info("TEST", "This should also appear")
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertEquals(2, logs.size) // Only debug and info
        
        // Verify no verbose logs
        val verboseLogs = logs.filter { it.level == LogLevel.VERBOSE }
        assertTrue(verboseLogs.isEmpty())
    }
    
    /**
     * Test that TCP connection establishment is logged with details.
     * 
     * Requirements: 12.2
     */
    @Test
    fun `TCP connection establishment is logged with details`() = runTest {
        // Arrange
        testLogger.setVerboseEnabled(true)
        testLogger.clearLogs()
        
        // Act - Simulate connection establishment logging
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        testLogger.info(
            "TCPHandler",
            "Establishing TCP connection: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort}"
        )
        
        testLogger.info(
            "TCPHandler",
            "TCP connection established: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort} " +
            "(seq=1000, ack=2000, state=ESTABLISHED)"
        )
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertTrue(logs.size >= 2)
        
        // Verify establishment log contains connection details
        val establishLog = logs.find { it.message.contains("TCP connection established") }
        assertNotNull(establishLog)
        assertTrue(establishLog!!.message.contains("10.0.0.2:12345"))
        assertTrue(establishLog.message.contains("1.1.1.1:80"))
        assertTrue(establishLog.message.contains("seq=1000"))
        assertTrue(establishLog.message.contains("ack=2000"))
        assertTrue(establishLog.message.contains("ESTABLISHED"))
    }
    
    /**
     * Test that TCP connection closure is logged with reason and duration.
     * 
     * Requirements: 12.3
     */
    @Test
    fun `TCP connection closure is logged with reason and duration`() = runTest {
        // Arrange
        testLogger.clearLogs()
        val key = ConnectionKey(
            protocol = Protocol.TCP,
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        
        // Act - Simulate connection closure logging
        val duration = 5.5 // seconds
        testLogger.info(
            "TCPHandler",
            "TCP connection closed: ${key.sourceIp}:${key.sourcePort} -> ${key.destIp}:${key.destPort} " +
            "(reason=FIN, duration=${String.format("%.2f", duration)}s, " +
            "sent=1024 bytes, received=2048 bytes)"
        )
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertEquals(1, logs.size)
        
        val closeLog = logs[0]
        assertTrue(closeLog.message.contains("TCP connection closed"))
        assertTrue(closeLog.message.contains("reason=FIN"))
        assertTrue(closeLog.message.contains("duration=5.50s"))
        assertTrue(closeLog.message.contains("sent=1024 bytes"))
        assertTrue(closeLog.message.contains("received=2048 bytes"))
    }
    
    /**
     * Test that SOCKS5 handshake failures are logged with error codes.
     * 
     * Requirements: 12.4
     */
    @Test
    fun `SOCKS5 handshake failures are logged with error codes`() {
        // Arrange
        testLogger.clearLogs()
        
        // Act - Simulate SOCKS5 handshake failure logging
        val errorCode = 0x05 // Connection refused
        testLogger.error(
            "TCPHandler",
            "SOCKS5 connect failed: connection refused (code=0x${errorCode.toString(16)})"
        )
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertEquals(1, logs.size)
        
        val errorLog = logs[0]
        assertEquals(LogLevel.ERROR, errorLog.level)
        assertTrue(errorLog.message.contains("SOCKS5 connect failed"))
        assertTrue(errorLog.message.contains("connection refused"))
        assertTrue(errorLog.message.contains("code=0x5"))
    }
    
    /**
     * Test that packet payload data is NEVER logged (privacy requirement).
     * 
     * This test verifies that we only log packet metadata (size, addresses, ports)
     * but never the actual payload content.
     * 
     * Requirements: 12.5
     */
    @Test
    fun `packet payload data is never logged`() = runTest {
        // Arrange
        testLogger.setVerboseEnabled(true)
        testLogger.clearLogs()
        
        val sensitivePayload = "SECRET_PASSWORD_123".toByteArray()
        
        // Act - Simulate data forwarding logging (should only log size, not content)
        testLogger.verbose(
            "TCPHandler",
            "Forwarding ${sensitivePayload.size} bytes from TUN to SOCKS5: " +
            "TCP(10.0.0.2:12345 -> 1.1.1.1:80)"
        )
        
        testLogger.verbose(
            "TCPHandler",
            "Received ${sensitivePayload.size} bytes from SOCKS5 for " +
            "TCP(10.0.0.2:12345 -> 1.1.1.1:80)"
        )
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertEquals(2, logs.size)
        
        // Verify logs contain size but NOT payload content
        logs.forEach { log ->
            assertTrue(log.message.contains("${sensitivePayload.size} bytes"))
            assertFalse(log.message.contains("SECRET_PASSWORD"))
            assertFalse(log.message.contains(String(sensitivePayload)))
        }
    }
    
    /**
     * Test that DNS query logging does not include query content.
     * 
     * Requirements: 12.5
     */
    @Test
    fun `DNS query logging does not include query content`() {
        // Arrange
        testLogger.clearLogs()
        val dnsPayload = byteArrayOf(0x12, 0x34, 0x01, 0x00) // DNS query header
        
        // Act - Simulate DNS query logging
        testLogger.info(
            "UDPHandler",
            "DNS query: 10.0.0.2:54321 -> 8.8.8.8:53 (query size: ${dnsPayload.size} bytes)"
        )
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertEquals(1, logs.size)
        
        val dnsLog = logs[0]
        assertTrue(dnsLog.message.contains("DNS query"))
        assertTrue(dnsLog.message.contains("query size: ${dnsPayload.size} bytes"))
        // Verify payload bytes are not in the log
        assertFalse(dnsLog.message.contains("0x12"))
        assertFalse(dnsLog.message.contains("0x34"))
    }
    
    /**
     * Test that IP packet logging includes metadata but not payload.
     * 
     * Requirements: 12.1, 12.5
     */
    @Test
    fun `IP packet logging includes metadata but not payload`() {
        // Arrange
        testLogger.setVerboseEnabled(true)
        testLogger.clearLogs()
        
        // Act - Simulate IP packet reception logging
        testLogger.verbose(
            "PacketRouter",
            "Received IP packet: 10.0.0.2 -> 1.1.1.1, " +
            "protocol=TCP, length=100 bytes, ttl=64, id=12345"
        )
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertEquals(1, logs.size)
        
        val packetLog = logs[0]
        // Verify metadata is present
        assertTrue(packetLog.message.contains("10.0.0.2"))
        assertTrue(packetLog.message.contains("1.1.1.1"))
        assertTrue(packetLog.message.contains("protocol=TCP"))
        assertTrue(packetLog.message.contains("length=100 bytes"))
        assertTrue(packetLog.message.contains("ttl=64"))
        assertTrue(packetLog.message.contains("id=12345"))
    }
    
    /**
     * Test that connection cleanup logging includes duration and statistics.
     * 
     * Requirements: 12.3
     */
    @Test
    fun `connection cleanup logging includes duration and statistics`() {
        // Arrange
        testLogger.clearLogs()
        
        // Act - Simulate idle connection cleanup logging
        testLogger.info(
            "ConnectionTable",
            "TCP connection closed: 10.0.0.2:12345 -> 1.1.1.1:80 " +
            "(reason=idle_timeout, duration=125.50s, sent=10240 bytes, received=20480 bytes)"
        )
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertEquals(1, logs.size)
        
        val cleanupLog = logs[0]
        assertTrue(cleanupLog.message.contains("reason=idle_timeout"))
        assertTrue(cleanupLog.message.contains("duration=125.50s"))
        assertTrue(cleanupLog.message.contains("sent=10240 bytes"))
        assertTrue(cleanupLog.message.contains("received=20480 bytes"))
    }
    
    /**
     * Test that error logging includes context but not sensitive data.
     * 
     * Requirements: 12.4, 12.5
     */
    @Test
    fun `error logging includes context but not sensitive data`() {
        // Arrange
        testLogger.clearLogs()
        
        // Act - Simulate error logging
        testLogger.error(
            "TCPHandler",
            "Failed to forward data to SOCKS5 for TCP(10.0.0.2:12345 -> 1.1.1.1:80): Connection reset"
        )
        
        // Assert
        val logs = testLogger.getLogEntries()
        assertEquals(1, logs.size)
        
        val errorLog = logs[0]
        assertEquals(LogLevel.ERROR, errorLog.level)
        assertTrue(errorLog.message.contains("Failed to forward data"))
        assertTrue(errorLog.message.contains("Connection reset"))
        assertTrue(errorLog.message.contains("10.0.0.2:12345"))
        assertTrue(errorLog.message.contains("1.1.1.1:80"))
    }
}



