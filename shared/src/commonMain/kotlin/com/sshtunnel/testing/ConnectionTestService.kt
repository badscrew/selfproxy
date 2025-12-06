package com.sshtunnel.testing

// TODO: This is a temporary stub - will be replaced with Shadowsocks implementation in task 16
interface ConnectionTestService {
    suspend fun testConnection(): Result<ConnectionTestResult>
}

data class ConnectionTestResult(
    val success: Boolean,
    val externalIp: String?,
    val latencyMs: Long?,
    val errorMessage: String?
)
