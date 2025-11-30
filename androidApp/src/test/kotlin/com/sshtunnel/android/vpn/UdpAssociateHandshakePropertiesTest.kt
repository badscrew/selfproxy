package com.sshtunnel.android.vpn

import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Property-based tests for SOCKS5 UDP ASSOCIATE handshake.
 * 
 * Feature: socks5-udp-associate, Property 1: UDP ASSOCIATE Handshake Correctness
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4
 */
class UdpAssociateHandshakePropertiesTest {
    
    private lateinit var logger: Logger
    
    @Before
    fun setup() {
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
    }
    
    /**
     * Property 1: UDP ASSOCIATE Handshake Correctness
     * 
     * For any valid SOCKS5 server response, the handshake should produce
     * a valid relay endpoint with non-zero port.
     * 
     * Validates: Requirements 1.1, 1.2, 1.3, 1.4
     */
    @Test
    fun `UDP ASSOCIATE handshake should produce valid relay endpoint`() = runTest {
        // Feature: socks5-udp-associate, Property 1: UDP ASSOCIATE Handshake Correctness
        // Validates: Requirements 1.1, 1.2, 1.3, 1.4
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address(),
            Arb.validPort()
        ) { relayAddress, relayPort ->
            // Arrange: Create mock SOCKS5 server response
            val mockSocket = createMockSocket(relayAddress, relayPort, replyCode = 0x00)
            val udpHandler = UDPHandler(
                socksPort = 1080,
                connectionTable = ConnectionTable(),
                logger = logger
            )
            
            // Act: Perform handshake using reflection to access private method
            val method = UDPHandler::class.java.getDeclaredMethod(
                "performUdpAssociateHandshake",
                Socket::class.java,
                String::class.java,
                Int::class.java
            )
            method.isAccessible = true
            val endpoint = method.invoke(udpHandler, mockSocket, "0.0.0.0", 0) as? UdpRelayEndpoint
            
            // Assert: Endpoint should be valid
            assertNotNull("Handshake should produce a relay endpoint", endpoint)
            assertTrue("Relay port should be non-zero (got ${endpoint?.port})", endpoint != null && endpoint.port > 0)
            assertTrue("Relay port should be valid (got ${endpoint?.port})", endpoint != null && endpoint.port <= 65535)
            assertTrue("Relay address should not be empty", endpoint != null && endpoint.address.isNotEmpty())
        }
    }
    
    /**
     * Property 2: UDP ASSOCIATE handshake should handle all SOCKS5 error codes
     * 
     * For any SOCKS5 error code (0x01-0x08), the handshake should return null
     * and not throw an exception.
     * 
     * Validates: Requirements 7.1, 11.1, 11.2
     */
    @Test
    fun `UDP ASSOCIATE handshake should handle SOCKS5 error codes gracefully`() = runTest {
        // Feature: socks5-udp-associate, Property 1: UDP ASSOCIATE Handshake Correctness
        // Validates: Requirements 7.1, 11.1, 11.2
        
        checkAll(
            iterations = 100,
            Arb.socks5ErrorCode(),
            Arb.ipv4Address(),
            Arb.validPort()
        ) { errorCode, relayAddress, relayPort ->
            // Arrange: Create mock SOCKS5 server response with error
            val mockSocket = createMockSocket(relayAddress, relayPort, replyCode = errorCode)
            val udpHandler = UDPHandler(
                socksPort = 1080,
                connectionTable = ConnectionTable(),
                logger = logger
            )
            
            // Act: Perform handshake
            val method = UDPHandler::class.java.getDeclaredMethod(
                "performUdpAssociateHandshake",
                Socket::class.java,
                String::class.java,
                Int::class.java
            )
            method.isAccessible = true
            val endpoint = method.invoke(udpHandler, mockSocket, "0.0.0.0", 0) as? UdpRelayEndpoint
            
            // Assert: Should return null for error codes
            assertTrue("Handshake should return null for error code 0x${errorCode.toString(16)}", endpoint == null)
        }
    }
    
    /**
     * Property 3: UDP ASSOCIATE handshake should validate relay port
     * 
     * For any SOCKS5 response with port 0, the handshake should return null
     * as port 0 is invalid for UDP relay.
     * 
     * Validates: Requirements 1.3, 7.1
     */
    @Test
    fun `UDP ASSOCIATE handshake should reject zero relay port`() = runTest {
        // Feature: socks5-udp-associate, Property 1: UDP ASSOCIATE Handshake Correctness
        // Validates: Requirements 1.3, 7.1
        
        checkAll(
            iterations = 100,
            Arb.ipv4Address()
        ) { relayAddress ->
            // Arrange: Create mock SOCKS5 server response with port 0
            val mockSocket = createMockSocket(relayAddress, relayPort = 0, replyCode = 0x00)
            val udpHandler = UDPHandler(
                socksPort = 1080,
                connectionTable = ConnectionTable(),
                logger = logger
            )
            
            // Act: Perform handshake
            val method = UDPHandler::class.java.getDeclaredMethod(
                "performUdpAssociateHandshake",
                Socket::class.java,
                String::class.java,
                Int::class.java
            )
            method.isAccessible = true
            val endpoint = method.invoke(udpHandler, mockSocket, "0.0.0.0", 0) as? UdpRelayEndpoint
            
            // Assert: Should return null for port 0
            assertTrue("Handshake should return null for relay port 0", endpoint == null)
        }
    }
    
    // ========== Helper Functions ==========
    
    /**
     * Creates a mock Socket that simulates SOCKS5 server responses.
     */
    private fun createMockSocket(
        relayAddress: String,
        relayPort: Int,
        replyCode: Int
    ): Socket {
        // Build SOCKS5 response sequence
        val responseStream = ByteArrayOutputStream()
        
        // Step 1: Greeting response [VER=5, METHOD=0]
        responseStream.write(byteArrayOf(0x05, 0x00))
        
        // Step 2: UDP ASSOCIATE response
        // [VER=5, REP, RSV=0, ATYP=1 (IPv4), BND.ADDR (4 bytes), BND.PORT (2 bytes)]
        val ipBytes = java.net.InetAddress.getByName(relayAddress).address
        val portBytes = ByteBuffer.allocate(2).putShort(relayPort.toShort()).array()
        
        responseStream.write(byteArrayOf(
            0x05,                    // Version 5
            replyCode.toByte(),      // Reply code
            0x00,                    // Reserved
            0x01                     // ATYP: IPv4
        ))
        responseStream.write(ipBytes)
        responseStream.write(portBytes)
        
        val responseBytes = responseStream.toByteArray()
        
        // Create mock socket with input/output streams
        return object : Socket() {
            private val inputStream = ByteArrayInputStream(responseBytes)
            private val outputStream = ByteArrayOutputStream()
            
            override fun getInputStream(): InputStream = inputStream
            override fun getOutputStream(): OutputStream = outputStream
            override fun close() {}
            override fun isClosed(): Boolean = false
            override fun isConnected(): Boolean = true
        }
    }
    
    // ========== Custom Generators ==========
    
    companion object {
        /**
         * Generator for valid IPv4 addresses.
         */
        fun Arb.Companion.ipv4Address(): Arb<String> = arbitrary {
            val octet1 = Arb.int(1..255).bind()
            val octet2 = Arb.int(0..255).bind()
            val octet3 = Arb.int(0..255).bind()
            val octet4 = Arb.int(1..255).bind()
            "$octet1.$octet2.$octet3.$octet4"
        }
        
        /**
         * Generator for valid port numbers (1-65535).
         */
        fun Arb.Companion.validPort(): Arb<Int> = Arb.int(1..65535)
        
        /**
         * Generator for SOCKS5 error codes (0x01-0x08).
         */
        fun Arb.Companion.socks5ErrorCode(): Arb<Int> = Arb.int(0x01..0x08)
    }
}
