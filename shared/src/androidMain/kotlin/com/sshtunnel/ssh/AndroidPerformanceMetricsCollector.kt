package com.sshtunnel.ssh

import com.sshtunnel.logging.Logger
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Android implementation of PerformanceMetricsCollector.
 * 
 * Thread-safe metrics collection using atomic operations.
 * Provides real-time performance monitoring for SSH connections.
 */
class AndroidPerformanceMetricsCollector(
    private val logger: Logger
) : PerformanceMetricsCollector {
    
    private val connectionTimeMs = AtomicLong(0)
    private val processUptimeMs = AtomicLong(0)
    private val memoryUsageBytes = AtomicLong(0)
    private val reconnectionAttempts = AtomicInteger(0)
    private val successfulReconnections = AtomicInteger(0)
    private val failedReconnections = AtomicInteger(0)
    private val lastHealthCheckMs = AtomicLong(0)
    private val totalHealthChecks = AtomicLong(0)
    private val failedHealthChecks = AtomicLong(0)
    
    companion object {
        private const val TAG = "PerformanceMetrics"
    }
    
    override fun recordConnectionTime(timeMs: Long) {
        connectionTimeMs.set(timeMs)
        logger.debug(TAG, "Connection established in ${timeMs}ms")
    }
    
    override fun recordReconnectionAttempt(successful: Boolean) {
        reconnectionAttempts.incrementAndGet()
        if (successful) {
            successfulReconnections.incrementAndGet()
            logger.debug(TAG, "Reconnection successful (${successfulReconnections.get()}/${reconnectionAttempts.get()})")
        } else {
            failedReconnections.incrementAndGet()
            logger.debug(TAG, "Reconnection failed (${failedReconnections.get()}/${reconnectionAttempts.get()})")
        }
    }
    
    override fun recordHealthCheck(successful: Boolean) {
        totalHealthChecks.incrementAndGet()
        lastHealthCheckMs.set(System.currentTimeMillis())
        
        if (!successful) {
            failedHealthChecks.incrementAndGet()
        }
        
        // Log metrics periodically (every 100 checks)
        if (totalHealthChecks.get() % 100 == 0L) {
            val metrics = getMetrics()
            logger.info(TAG, "Health check stats: ${metrics.getHealthCheckSuccessRate()}% success rate " +
                    "(${totalHealthChecks.get()} total, ${failedHealthChecks.get()} failed)")
        }
    }
    
    override fun updateUptime(uptimeMs: Long) {
        processUptimeMs.set(uptimeMs)
    }
    
    override fun updateMemoryUsage(bytes: Long) {
        memoryUsageBytes.set(bytes)
        
        // Log if memory usage is high (> 50MB)
        val mb = bytes / (1024.0 * 1024.0)
        if (mb > 50.0) {
            logger.warn(TAG, "High memory usage: ${String.format("%.2f", mb)} MB")
        }
    }
    
    override fun getMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            connectionEstablishmentTimeMs = connectionTimeMs.get(),
            processUptimeMs = processUptimeMs.get(),
            memoryUsageBytes = memoryUsageBytes.get(),
            reconnectionAttempts = reconnectionAttempts.get(),
            successfulReconnections = successfulReconnections.get(),
            failedReconnections = failedReconnections.get(),
            lastHealthCheckMs = lastHealthCheckMs.get(),
            totalHealthChecks = totalHealthChecks.get(),
            failedHealthChecks = failedHealthChecks.get()
        )
    }
    
    override fun reset() {
        connectionTimeMs.set(0)
        processUptimeMs.set(0)
        memoryUsageBytes.set(0)
        reconnectionAttempts.set(0)
        successfulReconnections.set(0)
        failedReconnections.set(0)
        lastHealthCheckMs.set(0)
        totalHealthChecks.set(0)
        failedHealthChecks.set(0)
        
        logger.debug(TAG, "Metrics reset")
    }
    
    /**
     * Log current metrics summary.
     */
    fun logMetricsSummary() {
        val metrics = getMetrics()
        logger.info(TAG, buildString {
            appendLine("=== SSH Performance Metrics ===")
            appendLine("Connection time: ${metrics.connectionEstablishmentTimeMs}ms")
            appendLine("Uptime: ${String.format("%.2f", metrics.getUptimeHours())} hours")
            appendLine("Memory usage: ${String.format("%.2f", metrics.getMemoryUsageMB())} MB")
            appendLine("Connection success rate: ${String.format("%.2f", metrics.getConnectionSuccessRate())}%")
            appendLine("Health check success rate: ${String.format("%.2f", metrics.getHealthCheckSuccessRate())}%")
            appendLine("Total health checks: ${metrics.totalHealthChecks}")
            appendLine("Reconnection attempts: ${metrics.reconnectionAttempts}")
            append("==============================")
        })
    }
}
