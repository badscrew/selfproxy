package com.sshtunnel.data

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Real-time statistics for an active VPN connection.
 * 
 * @property bytesSent Total bytes sent through the tunnel
 * @property bytesReceived Total bytes received through the tunnel
 * @property uploadSpeed Current upload speed in bytes per second
 * @property downloadSpeed Current download speed in bytes per second
 * @property connectedDuration Time elapsed since connection was established
 */
@Serializable
data class VpnStatistics(
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    @Serializable(with = DurationSerializer::class)
    val connectedDuration: Duration = 0.seconds
) {
    /**
     * Total bytes transferred (sent + received).
     */
    val totalBytes: Long
        get() = bytesSent + bytesReceived
    
    /**
     * Returns a human-readable string representation of bytes.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * Returns a human-readable string representation of speed.
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        return "${formatBytes(bytesPerSecond)}/s"
    }
}
