package com.sshtunnel.testing

import com.sshtunnel.ssh.SSHConnectionManager
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Implementation of ConnectionTestService using Ktor HttpClient.
 * 
 * This implementation queries external IP check services (ifconfig.me or ipify.org)
 * to verify that traffic is properly routed through the SSH tunnel.
 * 
 * @property httpClient Ktor HttpClient for making HTTP requests
 * @property connectionManager SSH connection manager to check connection state
 */
class ConnectionTestServiceImpl(
    private val httpClient: HttpClient,
    private val connectionManager: SSHConnectionManager
) : ConnectionTestService {
    
    companion object {
        // List of external IP check services to try (in order)
        private val TEST_SERVICES = listOf(
            "https://ifconfig.me/ip",
            "https://api.ipify.org",
            "https://icanhazip.com"
        )
        
        // Timeout for each test request
        private val REQUEST_TIMEOUT = 10.seconds
    }
    
    override suspend fun testConnection(expectedServerIp: String?): Result<ConnectionTestResult> {
        return try {
            // Check if there's an active connection
            if (connectionManager.getCurrentConnection() == null) {
                return Result.failure(
                    Exception(ConnectionTestError.NoActiveConnection().message)
                )
            }
            
            // Try each test service until one succeeds
            var lastError: Throwable? = null
            for (serviceUrl in TEST_SERVICES) {
                try {
                    val result = queryExternalIp(serviceUrl, expectedServerIp)
                    return Result.success(result)
                } catch (e: Exception) {
                    lastError = e
                    // Continue to next service
                }
            }
            
            // All services failed
            Result.failure(
                Exception(
                    ConnectionTestError.TestServiceUnreachable(
                        message = "All test services failed to respond",
                        cause = lastError
                    ).message
                )
            )
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    ConnectionTestError.Unknown(
                        message = "Connection test failed: ${e.message}",
                        cause = e
                    ).message
                )
            )
        }
    }
    
    /**
     * Queries an external IP check service and measures latency.
     * 
     * @param serviceUrl URL of the IP check service
     * @param expectedServerIp Expected server IP for comparison
     * @return ConnectionTestResult with test results
     * @throws Exception if the request fails or times out
     */
    private suspend fun queryExternalIp(
        serviceUrl: String,
        expectedServerIp: String?
    ): ConnectionTestResult {
        var externalIp = ""
        
        val latency = measureTime {
            withTimeout(REQUEST_TIMEOUT) {
                val response: HttpResponse = httpClient.get(serviceUrl) {
                    headers {
                        append(HttpHeaders.UserAgent, "SSH-Tunnel-Proxy/1.0")
                    }
                }
                
                if (!response.status.isSuccess()) {
                    throw Exception(
                        ConnectionTestError.TestServiceUnreachable(
                            message = "Test service returned status: ${response.status}"
                        ).message
                    )
                }
                
                externalIp = response.bodyAsText().trim()
                
                // Validate IP format (basic check)
                if (!isValidIpAddress(externalIp)) {
                    throw Exception(
                        ConnectionTestError.InvalidResponse(
                            message = "Invalid IP address format: $externalIp"
                        ).message
                    )
                }
            }
        }
        
        // Determine if routing is correct
        val isRoutingCorrectly = if (expectedServerIp != null) {
            externalIp == expectedServerIp.trim()
        } else {
            // If no expected IP provided, we can't verify routing
            // but the test succeeded in getting an IP
            true
        }
        
        return ConnectionTestResult(
            externalIp = externalIp,
            expectedServerIp = expectedServerIp,
            isRoutingCorrectly = isRoutingCorrectly,
            latency = latency,
            testServiceUsed = serviceUrl
        )
    }
    
    /**
     * Validates if a string is a valid IPv4 or IPv6 address.
     * 
     * @param ip String to validate
     * @return true if valid IP address, false otherwise
     */
    private fun isValidIpAddress(ip: String): Boolean {
        // Basic IPv4 validation (e.g., 192.168.1.1)
        val ipv4Pattern = Regex(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
            "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        
        // Basic IPv6 validation (simplified - accepts most common formats)
        val ipv6Pattern = Regex(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|" +
            "^::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|" +
            "^([0-9a-fA-F]{1,4}:){1,7}:$"
        )
        
        return ipv4Pattern.matches(ip) || ipv6Pattern.matches(ip)
    }
}
