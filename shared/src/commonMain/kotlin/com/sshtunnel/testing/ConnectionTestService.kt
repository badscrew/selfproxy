package com.sshtunnel.testing

import kotlin.time.Duration

/**
 * Service for testing SSH tunnel connections by verifying external IP routing.
 * 
 * This service queries external IP check services to verify that traffic is
 * properly routed through the SSH tunnel. It compares the apparent external IP
 * with the expected SSH server IP to validate correct routing.
 */
interface ConnectionTestService {
    /**
     * Tests the current connection by checking external IP and measuring latency.
     * 
     * @param expectedServerIp The expected external IP (SSH server's public IP), or null if unknown
     * @return Result containing ConnectionTestResult on success, or an error on failure
     */
    suspend fun testConnection(expectedServerIp: String? = null): Result<ConnectionTestResult>
}

/**
 * Result of a connection test.
 * 
 * @property externalIp The external IP address as seen by the test service
 * @property expectedServerIp The expected SSH server IP (if provided)
 * @property isRoutingCorrectly Whether the external IP matches the expected server IP
 * @property latency Round-trip latency for the test request
 * @property testServiceUsed The external service used for testing (e.g., "ifconfig.me")
 */
data class ConnectionTestResult(
    val externalIp: String,
    val expectedServerIp: String?,
    val isRoutingCorrectly: Boolean,
    val latency: Duration,
    val testServiceUsed: String
)

/**
 * Errors that can occur during connection testing.
 */
sealed class ConnectionTestError {
    abstract val message: String
    abstract val cause: Throwable?
    
    /**
     * No active connection to test.
     */
    data class NoActiveConnection(
        override val message: String = "No active SSH connection to test",
        override val cause: Throwable? = null
    ) : ConnectionTestError()
    
    /**
     * Failed to reach the test service.
     */
    data class TestServiceUnreachable(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionTestError()
    
    /**
     * Failed to parse the response from the test service.
     */
    data class InvalidResponse(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionTestError()
    
    /**
     * Request timed out.
     */
    data class Timeout(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionTestError()
    
    /**
     * Unknown error occurred.
     */
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null
    ) : ConnectionTestError()
}
