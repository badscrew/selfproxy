# Native SSH Logging and Diagnostics

This document describes the logging and diagnostic features available in the native SSH client implementation.

## Overview

The native SSH client provides comprehensive logging and diagnostic capabilities to help troubleshoot connection issues and monitor SSH tunnel health.

## Features

### 1. SSH Process Output Parsing

The `SSHOutputParser` automatically parses SSH process output to extract structured events:

- **Connection Events**: Connecting, Connected, Disconnected
- **Authentication Events**: Authenticating, Authentication Success/Failure
- **Key Exchange**: Algorithm information
- **Port Forwarding**: Establishment and failures
- **Keep-Alive**: Keep-alive packet tracking
- **Errors and Warnings**: Categorized by severity

### 2. Structured Logging

All SSH events are logged with appropriate severity levels:

- **VERBOSE**: Detailed output for debugging (keep-alive, raw SSH output)
- **DEBUG**: Key exchange, process monitoring
- **INFO**: Connection lifecycle events (connecting, connected, disconnected)
- **WARN**: Warnings and non-critical errors
- **ERROR**: Critical errors (authentication failures, connection errors)

### 3. Error Message Extraction

The parser automatically extracts and categorizes error messages:

- **FATAL**: Fatal errors requiring immediate attention
- **CRITICAL**: Critical errors preventing connection (auth failures, network errors)
- **ERROR**: Standard errors
- **WARNING**: Non-critical issues

### 4. Connection Status Logging

Connection health is continuously monitored and logged:

- **Healthy**: Connection is active and SOCKS5 port is responding
- **Unhealthy**: Connection issues detected
- **Disconnected**: Connection lost or terminated

### 5. Diagnostic Information Collection

The `SSHDiagnostics` class collects comprehensive diagnostic information:

- **Device Information**: Manufacturer, model, Android version, architecture
- **Application Information**: Package name, version
- **Connection Information**: Server details, key type
- **Binary Information**: SSH binary path, size, permissions
- **Network Information**: WiFi, cellular, VPN status
- **Recent Events**: Last 50 SSH events
- **Recent Logs**: Last 50 log entries

## Usage Examples

### Accessing Diagnostic Reports

```kotlin
// Get the native SSH client instance
val nativeClient = sshClient as? AndroidNativeSSHClient

// Collect diagnostics for a session
val report = nativeClient?.collectDiagnostics(sessionId)

// Get formatted diagnostic report
val reportText = nativeClient?.getDiagnosticReport(sessionId)

// Display or export the report
println(reportText)
```

### Monitoring SSH Events

```kotlin
// Observe process output
nativeClient.observeProcessOutput().collect { line ->
    // Raw SSH output
    println("SSH: $line")
}
```

### Viewing Logs

```kotlin
// Get all log entries
val logs = logger.getLogEntries()

// Filter by level
val errors = logs.filter { it.level == LogLevel.ERROR }

// Export logs
val logExporter = LogExporter()
val exportedText = logExporter.exportAsText(logs)
val exportedJson = logExporter.exportAsJson(logs)
```

## Log Output Examples

### Connection Establishment

```
[INFO] AndroidNativeSSHClient: === Starting Native SSH Connection ===
[INFO] AndroidNativeSSHClient: Server: user@example.com:22
[INFO] AndroidNativeSSHClient: Key Type: ED25519
[INFO] AndroidNativeSSHClient: Timeout: 30s
[INFO] AndroidNativeSSHClient: Compression: false
[INFO] AndroidNativeSSHClient: Strict Host Key Checking: false
[DEBUG] AndroidNativeSSHClient: Detected device architecture: arm64-v8a
[INFO] AndroidNativeSSHClient: Using SSH binary at: /data/user/0/com.sshtunnel/files/ssh
[DEBUG] AndroidNativeSSHClient: Binary verification successful
[INFO] AndroidNativeSSHClient: === Native SSH Session Created Successfully ===
[INFO] AndroidNativeSSHClient: Session ID: 12345678-1234-1234-1234-123456789abc
```

### SSH Process Events

```
[INFO] AndroidNativeSSHClient: Connecting to example.com:22
[INFO] AndroidNativeSSHClient: Authenticating with server
[DEBUG] AndroidNativeSSHClient: Key exchange: curve25519
[INFO] AndroidNativeSSHClient: Authentication successful
[INFO] AndroidNativeSSHClient: Port forwarding established on port 1080
[INFO] AndroidNativeSSHClient: Connection Status: Healthy
```

### Error Handling

```
[ERROR] AndroidNativeSSHClient: SSH error: Connection refused - server may be down or port blocked
[ERROR] AndroidNativeSSHClient: Authentication failed: public key authentication failed
[WARN] AndroidNativeSSHClient: Connection Status: Unhealthy - SOCKS5 port not responding
```

### Disconnection

```
[INFO] AndroidNativeSSHClient: === Disconnecting Native SSH Session ===
[INFO] AndroidNativeSSHClient: Session ID: 12345678-1234-1234-1234-123456789abc
[DEBUG] AndroidNativeSSHClient: Stopping SSH process
[INFO] AndroidNativeSSHClient: SSH process stopped
[DEBUG] AndroidNativeSSHClient: Deleting private key file
[INFO] AndroidNativeSSHClient: === Native SSH Session Disconnected Successfully ===
```

## Diagnostic Report Example

```
=== SSH Tunnel Proxy Diagnostic Report ===
Generated: 2025-01-24 15:30:45

--- Device Information ---
Manufacturer: Google
Model: Pixel 6
Android Version: 14 (API 34)
Architecture: arm64-v8a
Supported ABIs: arm64-v8a, armeabi-v7a, armeabi

--- Application Information ---
Package: com.sshtunnel.android
Version: 1.0.0 (1)

--- Connection Information ---
Server: user@example.com:22
Key Type: ED25519

--- SSH Binary Information ---
Path: /data/user/0/com.sshtunnel/files/ssh
Exists: true
Size: 1048576 bytes
Executable: true
Last Modified: 2025-01-24 15:00:00

--- Network Information ---
Connected: true
WiFi: true
Cellular: false
VPN: false

--- Recent SSH Events ---
→ Connecting to example.com:22
→ Authenticating
✓ Authentication successful
→ Key exchange: curve25519
✓ Port forwarding established on port 1080
✓ Connected
→ Keep-alive
→ Keep-alive

--- Recent Logs (last 20) ---
[15:30:40.123] I/AndroidNativeSSHClient: Starting native SSH connection
[15:30:40.456] D/AndroidBinaryManager: Detected architecture: arm64-v8a
[15:30:40.789] I/AndroidNativeSSHClient: Using SSH binary at: /data/user/0/com.sshtunnel/files/ssh
[15:30:41.012] I/AndroidNativeSSHClient: SSH process started successfully
[15:30:41.234] I/AndroidNativeSSHClient: Connecting to example.com:22
[15:30:41.567] I/AndroidNativeSSHClient: Authenticating with server
[15:30:42.890] I/AndroidNativeSSHClient: Authentication successful
[15:30:43.123] I/AndroidNativeSSHClient: Port forwarding established on port 1080
[15:30:43.456] I/AndroidNativeSSHClient: Connection Status: Healthy

=== End of Diagnostic Report ===
```

## Troubleshooting with Logs

### Connection Refused

Look for:
```
[ERROR] SSH error: Connection refused - server may be down or port blocked
```

**Solution**: Check if SSH server is running and port is accessible.

### Authentication Failure

Look for:
```
[ERROR] Authentication failed: public key authentication failed
```

**Solution**: Verify private key is correct and authorized on server.

### Network Issues

Look for:
```
[ERROR] SSH error: Network is unreachable - check internet connection
[ERROR] SSH error: Connection timed out - check network connectivity
```

**Solution**: Check network connectivity and firewall settings.

### Binary Issues

Look for:
```
[ERROR] Failed to extract SSH binary
[ERROR] Binary verification failed
```

**Solution**: Reinstall the app or clear app data.

## Best Practices

1. **Enable Verbose Logging for Debugging**: Set `logger.setVerboseEnabled(true)` when troubleshooting
2. **Collect Diagnostics on Errors**: Always collect diagnostic report when connection fails
3. **Export Logs for Support**: Use `LogExporter` to export logs for support tickets
4. **Monitor Connection Health**: Watch for "Unhealthy" status to detect issues early
5. **Review Recent Events**: Check recent SSH events to understand connection lifecycle

## Privacy and Security

All logging automatically sanitizes sensitive data:

- Private keys are never logged
- Passwords and passphrases are masked
- Authentication tokens are redacted
- Only connection metadata is logged (hostnames, ports, usernames)

The `LogSanitizer` automatically removes or masks:
- Password patterns
- Private key content
- Passphrases
- Authentication tokens
- Base64-encoded keys

## Performance Considerations

- Verbose logging is disabled by default to reduce overhead
- Log entries are limited to 1000 in memory
- Process output monitoring uses efficient buffered readers
- Connection health checks run at 1-second intervals
- State change logging reduces noise by only logging transitions

## Integration with VPN Service

The VPN service can access diagnostic information:

```kotlin
class TunnelVpnService : VpnService() {
    private fun onConnectionError() {
        // Get diagnostic report
        val nativeClient = sshClient as? AndroidNativeSSHClient
        val report = nativeClient?.getDiagnosticReport(sessionId)
        
        // Log or display to user
        logger.error(TAG, "Connection failed. Diagnostic report:\n$report")
        
        // Optionally save to file for user to export
        saveDiagnosticReport(report)
    }
}
```

## Future Enhancements

Potential improvements to logging and diagnostics:

1. **Real-time Event Streaming**: WebSocket or SSE for live log viewing
2. **Log Filtering**: Advanced filtering by tag, level, time range
3. **Performance Metrics**: Connection time, throughput, latency tracking
4. **Crash Reports**: Automatic diagnostic collection on crashes
5. **Remote Logging**: Optional secure log upload for support
6. **Log Rotation**: Automatic log file rotation and cleanup
7. **Custom Log Handlers**: Plugin system for custom log destinations
