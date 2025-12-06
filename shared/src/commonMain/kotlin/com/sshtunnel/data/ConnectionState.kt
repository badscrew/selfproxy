package com.sshtunnel.data

import kotlinx.serialization.Serializable

/**
 * Represents the current state of the VPN connection.
 */
@Serializable
sealed class ConnectionState {
    /**
     * No active connection.
     */
    @Serializable
    data object Disconnected : ConnectionState()
    
    /**
     * Attempting to establish a connection.
     */
    @Serializable
    data object Connecting : ConnectionState()
    
    /**
     * Successfully connected to the Shadowsocks server with VPN tunnel active.
     * 
     * @property profileId ID of the connected server profile
     * @property serverAddress Server hostname or IP address
     * @property connectedAt Timestamp when connection was established (milliseconds since epoch)
     */
    @Serializable
    data class Connected(
        val profileId: Long,
        val serverAddress: String,
        val connectedAt: Long
    ) : ConnectionState()
    
    /**
     * Connection was lost and attempting to reconnect.
     * 
     * @property attempt Current reconnection attempt number (1-based)
     */
    @Serializable
    data class Reconnecting(
        val attempt: Int
    ) : ConnectionState()
    
    /**
     * Connection failed or encountered an error.
     * 
     * @property message Human-readable error message
     * @property errorType Type of error that occurred
     */
    @Serializable
    data class Error(
        val message: String,
        val errorType: ErrorType = ErrorType.UNKNOWN
    ) : ConnectionState()
}

/**
 * Types of connection errors.
 */
@Serializable
enum class ErrorType {
    /**
     * Server is unreachable (network error, wrong address, etc.)
     */
    SERVER_UNREACHABLE,
    
    /**
     * Authentication failed (wrong password)
     */
    AUTHENTICATION_FAILED,
    
    /**
     * Unsupported or invalid cipher method
     */
    UNSUPPORTED_CIPHER,
    
    /**
     * VPN permission was denied or revoked
     */
    VPN_PERMISSION_DENIED,
    
    /**
     * Connection timeout
     */
    TIMEOUT,
    
    /**
     * Unknown or unclassified error
     */
    UNKNOWN
}
