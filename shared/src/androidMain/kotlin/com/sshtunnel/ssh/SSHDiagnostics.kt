package com.sshtunnel.ssh

import android.content.Context
import android.os.Build
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.LogEntry
import com.sshtunnel.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects diagnostic information for SSH connections.
 * 
 * Gathers system information, connection details, logs, and error history
 * to help troubleshoot SSH connection issues.
 */
class SSHDiagnostics(
    private val context: Context,
    private val logger: Logger
) {
    
    companion object {
        private const val TAG = "SSHDiagnostics"
    }
    
    /**
     * Collect comprehensive diagnostic information.
     * 
     * @param profile Server profile being connected to
     * @param binaryPath Path to SSH binary
     * @param recentEvents Recent SSH events from output parser
     * @return Diagnostic report
     */
    suspend fun collectDiagnostics(
        profile: ServerProfile,
        binaryPath: String?,
        recentEvents: List<SSHEvent> = emptyList()
    ): DiagnosticReport = withContext(Dispatchers.IO) {
        logger.debug(TAG, "Collecting diagnostic information")
        
        DiagnosticReport(
            timestamp = System.currentTimeMillis(),
            deviceInfo = collectDeviceInfo(),
            appInfo = collectAppInfo(),
            connectionInfo = collectConnectionInfo(profile),
            binaryInfo = collectBinaryInfo(binaryPath),
            recentEvents = recentEvents,
            recentLogs = logger.getLogEntries().takeLast(50),
            networkInfo = collectNetworkInfo()
        )
    }
    
    /**
     * Collect device information.
     */
    private fun collectDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            supportedAbis = Build.SUPPORTED_ABIS.toList()
        )
    }
    
    /**
     * Collect application information.
     */
    private fun collectAppInfo(): AppInfo {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return AppInfo(
            packageName = context.packageName,
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        )
    }
    
    /**
     * Collect connection information.
     */
    private fun collectConnectionInfo(profile: ServerProfile): ConnectionInfo {
        return ConnectionInfo(
            hostname = profile.hostname,
            port = profile.port,
            username = profile.username,
            keyType = profile.keyType.toString()
        )
    }
    
    /**
     * Collect SSH binary information.
     */
    private fun collectBinaryInfo(binaryPath: String?): BinaryInfo? {
        if (binaryPath == null) return null
        
        val file = File(binaryPath)
        return if (file.exists()) {
            BinaryInfo(
                path = binaryPath,
                exists = true,
                size = file.length(),
                isExecutable = file.canExecute(),
                lastModified = file.lastModified()
            )
        } else {
            BinaryInfo(
                path = binaryPath,
                exists = false,
                size = 0,
                isExecutable = false,
                lastModified = 0
            )
        }
    }
    
    /**
     * Collect network information.
     */
    private fun collectNetworkInfo(): NetworkInfo {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as? android.net.ConnectivityManager
        
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { 
            connectivityManager.getNetworkCapabilities(it) 
        }
        
        return NetworkInfo(
            isConnected = activeNetwork != null,
            isWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ?: false,
            isCellular = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ?: false,
            isVpn = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) ?: false
        )
    }
    
    /**
     * Format diagnostic report as human-readable text.
     */
    fun formatReport(report: DiagnosticReport): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timestamp = dateFormat.format(Date(report.timestamp))
        
        return buildString {
            appendLine("=== SSH Tunnel Proxy Diagnostic Report ===")
            appendLine("Generated: $timestamp")
            appendLine()
            
            appendLine("--- Device Information ---")
            appendLine("Manufacturer: ${report.deviceInfo.manufacturer}")
            appendLine("Model: ${report.deviceInfo.model}")
            appendLine("Android Version: ${report.deviceInfo.androidVersion} (API ${report.deviceInfo.apiLevel})")
            appendLine("Architecture: ${report.deviceInfo.architecture}")
            appendLine("Supported ABIs: ${report.deviceInfo.supportedAbis.joinToString(", ")}")
            appendLine()
            
            appendLine("--- Application Information ---")
            appendLine("Package: ${report.appInfo.packageName}")
            appendLine("Version: ${report.appInfo.versionName} (${report.appInfo.versionCode})")
            appendLine()
            
            appendLine("--- Connection Information ---")
            appendLine("Server: ${report.connectionInfo.username}@${report.connectionInfo.hostname}:${report.connectionInfo.port}")
            appendLine("Key Type: ${report.connectionInfo.keyType}")
            appendLine()
            
            if (report.binaryInfo != null) {
                appendLine("--- SSH Binary Information ---")
                appendLine("Path: ${report.binaryInfo.path}")
                appendLine("Exists: ${report.binaryInfo.exists}")
                if (report.binaryInfo.exists) {
                    appendLine("Size: ${report.binaryInfo.size} bytes")
                    appendLine("Executable: ${report.binaryInfo.isExecutable}")
                    appendLine("Last Modified: ${dateFormat.format(Date(report.binaryInfo.lastModified))}")
                }
                appendLine()
            }
            
            appendLine("--- Network Information ---")
            appendLine("Connected: ${report.networkInfo.isConnected}")
            appendLine("WiFi: ${report.networkInfo.isWifi}")
            appendLine("Cellular: ${report.networkInfo.isCellular}")
            appendLine("VPN: ${report.networkInfo.isVpn}")
            appendLine()
            
            if (report.recentEvents.isNotEmpty()) {
                appendLine("--- Recent SSH Events ---")
                report.recentEvents.takeLast(10).forEach { event ->
                    appendLine(formatEvent(event))
                }
                appendLine()
            }
            
            if (report.recentLogs.isNotEmpty()) {
                appendLine("--- Recent Logs (last 20) ---")
                report.recentLogs.takeLast(20).forEach { log ->
                    appendLine(formatLogEntry(log))
                }
                appendLine()
            }
            
            appendLine("=== End of Diagnostic Report ===")
        }
    }
    
    /**
     * Format SSH event for display.
     */
    private fun formatEvent(event: SSHEvent): String {
        return when (event) {
            is SSHEvent.Connecting -> "→ Connecting to ${event.target}"
            is SSHEvent.Connected -> "✓ Connected"
            is SSHEvent.Disconnected -> "✗ Disconnected: ${event.reason}"
            is SSHEvent.Authenticating -> "→ Authenticating"
            is SSHEvent.AuthenticationSuccess -> "✓ Authentication successful"
            is SSHEvent.AuthenticationFailure -> "✗ Authentication failed: ${event.reason}"
            is SSHEvent.KeyExchange -> "→ Key exchange: ${event.algorithm}"
            is SSHEvent.PortForwardingEstablished -> "✓ Port forwarding established on port ${event.port}"
            is SSHEvent.PortForwardingFailed -> "✗ Port forwarding failed: ${event.reason}"
            is SSHEvent.KeepAlive -> "→ Keep-alive"
            is SSHEvent.Error -> "✗ Error: ${event.message}"
            is SSHEvent.Warning -> "⚠ Warning: ${event.message}"
        }
    }
    
    /**
     * Format log entry for display.
     */
    private fun formatLogEntry(entry: LogEntry): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timestamp = dateFormat.format(Date(entry.timestamp))
        val level = entry.level.name.take(1)
        return "[$timestamp] $level/${entry.tag}: ${entry.message}"
    }
}

/**
 * Complete diagnostic report.
 */
data class DiagnosticReport(
    val timestamp: Long,
    val deviceInfo: DeviceInfo,
    val appInfo: AppInfo,
    val connectionInfo: ConnectionInfo,
    val binaryInfo: BinaryInfo?,
    val recentEvents: List<SSHEvent>,
    val recentLogs: List<LogEntry>,
    val networkInfo: NetworkInfo
)

/**
 * Device information.
 */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val architecture: String,
    val supportedAbis: List<String>
)

/**
 * Application information.
 */
data class AppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long
)

/**
 * Connection information.
 */
data class ConnectionInfo(
    val hostname: String,
    val port: Int,
    val username: String,
    val keyType: String
)

/**
 * SSH binary information.
 */
data class BinaryInfo(
    val path: String,
    val exists: Boolean,
    val size: Long,
    val isExecutable: Boolean,
    val lastModified: Long
)

/**
 * Network information.
 */
data class NetworkInfo(
    val isConnected: Boolean,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val isVpn: Boolean
)
