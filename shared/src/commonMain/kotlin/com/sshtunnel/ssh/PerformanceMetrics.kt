package com.sshtunnel.ssh

/**
 * Performance metrics for SSH connection monitoring.
 * 
 * Tracks key performance indicators:
 * - Connection establishment time
 * - Memory usage
 * - Process uptime
 * - Reconnection attempts
 * - Data transfer statistics
 */
data class PerformanceMetrics(
    val connectionEstablishmentTimeMs: Long = 0,
    val processUptimeMs: Long = 0,
    val memoryUsageBytes: Long = 0,
    val reconnectionAttempts: Int = 0,
    val successfulReconnections: Int = 0,
    val failedReconnections: Int = 0,
    val lastHealthCheckMs: Long = 0,
    val totalHealthChecks: Long = 0,
    val failedHealthChecks: Long = 0
) {
    /**
     * Calculate connection success rate.
     */
    fun getConnectionSuccessRate(): Double {
        val totalAttempts = reconnectionAttempts + 1 // Include initial connection
        val successfulAttempts = successfulReconnections + 1 // Include initial connection
        return if (totalAttempts > 0) {
            (successfulAttempts.toDouble() / totalAttempts.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * Calculate health check success rate.
     */
    fun getHealthCheckSuccessRate(): Double {
        return if (totalHealthChecks > 0) {
            ((totalHealthChecks - failedHealthChecks).toDouble() / totalHealthChecks.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * Get uptime in hours.
     */
    fun getUptimeHours(): Double {
        return processUptimeMs / (1000.0 * 60.0 * 60.0)
    }
    
    /**
     * Get memory usage in MB.
     */
    fun getMemoryUsageMB(): Double {
        return memoryUsageBytes / (1024.0 * 1024.0)
    }
}

/**
 * Interface for collecting performance metrics.
 */
interface PerformanceMetricsCollector {
    /**
     * Record connection establishment time.
     */
    fun recordConnectionTime(timeMs: Long)
    
    /**
     * Record reconnection attempt.
     */
    fun recordReconnectionAttempt(successful: Boolean)
    
    /**
     * Record health check result.
     */
    fun recordHealthCheck(successful: Boolean)
    
    /**
     * Update process uptime.
     */
    fun updateUptime(uptimeMs: Long)
    
    /**
     * Update memory usage.
     */
    fun updateMemoryUsage(bytes: Long)
    
    /**
     * Get current metrics snapshot.
     */
    fun getMetrics(): PerformanceMetrics
    
    /**
     * Reset all metrics.
     */
    fun reset()
}
