package com.sshtunnel.data

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for establishing a Shadowsocks connection.
 * 
 * @property serverHost Shadowsocks server hostname or IP address
 * @property serverPort Shadowsocks server port
 * @property password Server password for authentication
 * @property cipher Encryption cipher method
 * @property timeout Connection timeout duration
 */
@Serializable
data class ShadowsocksConfig(
    val serverHost: String,
    val serverPort: Int,
    val password: String,
    val cipher: CipherMethod,
    @Serializable(with = DurationSerializer::class)
    val timeout: Duration = 5.seconds
)
