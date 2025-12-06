# Design Document

## Overview

The native SSH client implementation replaces the unreliable sshj Java library with native OpenSSH binaries compiled for Android. This design leverages the battle-tested OpenSSH implementation that achieves 100% connection success on desktop systems, bundling it with the Android application to provide the same reliability on mobile devices.

The implementation uses Android's process management to execute the native SSH binary with dynamic port forwarding (`ssh -D`), creating a SOCKS5 proxy that the VPN service routes traffic through. This approach eliminates the channel management issues that plague the sshj library while providing native performance and better battery efficiency.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Android Application                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐         ┌─────────────────┐              │
│  │ VPN Service  │────────▶│ SSH Client      │              │
│  │              │         │ Factory         │              │
│  └──────────────┘         └────────┬────────┘              │
│         │                           │                        │
│         │                  ┌────────▼────────┐              │
│         │                  │  Native SSH     │              │
│         │                  │  Client         │              │
│         │                  └────────┬────────┘              │
│         │                           │                        │
│         │                  ┌────────▼────────┐              │
│         │                  │  Process        │              │
│         │                  │  Manager        │              │
│         │                  └────────┬────────┘              │
│         │                           │                        │
│         │                  ┌────────▼────────┐              │
│         │                  │  SSH Binary     │              │
│         │                  │  (OpenSSH)      │              │
│         │                  └────────┬────────┘              │
│         │                           │                        │
│         │                  ┌────────▼────────┐              │
│         └─────────────────▶│  SOCKS5 Proxy   │              │
│                            │  (Port 1080)    │              │
│                            └─────────────────┘              │
│                                                               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  SSH Server      │
                    │  (User's Server) │
                    └──────────────────┘
```

### Component Interaction Flow

1. **VPN Service** requests SSH connection from **SSH Client Factory**
2. **SSH Client Factory** determines whether to use native or sshj implementation
3. **Native SSH Client** extracts the SSH binary for the device architecture
4. **Process Manager** starts the SSH process with dynamic port forwarding
5. **SSH Binary** establishes connection to the SSH server
6. **SOCKS5 Proxy** becomes available on localhost:1080
7. **VPN Service** routes all traffic through the SOCKS5 proxy

## Components and Interfaces

### 1. SSH Client Factory

**Responsibility**: Determine which SSH implementation to use based on availability and user preference.

```kotlin
interface SSHClientFactory {
    /**
     * Create an SSH client instance
     * @param context Android context for accessing resources
     * @param logger Logger for diagnostic output
     * @param preferNative Whether to prefer native implementation
     * @return SSH client instance (native or sshj)
     */
    fun create(
        context: Context,
        logger: Logger,
        preferNative: Boolean = true
    ): SSHClient
    
    /**
     * Check if native SSH is available
     * @param context Android context
     * @return true if native SSH binary is available
     */
    fun isNativeSSHAvailable(context: Context): Boolean
}
```

### 2. Native SSH Client

**Responsibility**: Manage the native SSH process lifecycle and provide SSH connectivity.

```kotlin
interface NativeSSHClient : SSHClient {
    /**
     * Start SSH tunnel with dynamic port forwarding
     * @param profile Server connection profile
     * @param privateKeyPath Path to private key file on disk
     * @param localPort Local port for SOCKS5 proxy
     * @return Result containing the SOCKS5 port or error
     */
    suspend fun startSSHTunnel(
        profile: ServerProfile,
        privateKeyPath: String,
        localPort: Int
    ): Result<Int>
    
    /**
     * Stop the SSH tunnel
     */
    suspend fun stopSSHTunnel()
    
    /**
     * Check if SSH tunnel is running
     * @return true if the SSH process is alive
     */
    fun isRunning(): Boolean
    
    /**
     * Get the SSH process output stream
     * @return Flow of log lines from SSH process
     */
    fun observeProcessOutput(): Flow<String>
}
```

### 3. Binary Manager

**Responsibility**: Extract, cache, and manage SSH binaries for different architectures.

```kotlin
interface BinaryManager {
    /**
     * Extract SSH binary from APK to private directory
     * @param architecture Target CPU architecture
     * @return Path to extracted binary
     */
    suspend fun extractBinary(architecture: String): Result<String>
    
    /**
     * Get cached binary path if available
     * @param architecture Target CPU architecture
     * @return Path to cached binary or null
     */
    fun getCachedBinary(architecture: String): String?
    
    /**
     * Verify binary integrity
     * @param binaryPath Path to binary file
     * @return true if binary is valid and executable
     */
    suspend fun verifyBinary(binaryPath: String): Boolean
    
    /**
     * Detect device architecture
     * @return Architecture string (arm64-v8a, armeabi-v7a, x86_64, x86)
     */
    fun detectArchitecture(): String
}
```

### 4. Process Manager

**Responsibility**: Start, monitor, and stop the SSH process.

```kotlin
interface ProcessManager {
    /**
     * Start SSH process with specified command
     * @param command List of command arguments
     * @return Result containing the Process or error
     */
    suspend fun startProcess(command: List<String>): Result<Process>
    
    /**
     * Stop process gracefully with timeout
     * @param process Process to stop
     * @param timeoutSeconds Timeout for graceful shutdown
     */
    suspend fun stopProcess(process: Process, timeoutSeconds: Int = 5)
    
    /**
     * Check if process is alive
     * @param process Process to check
     * @return true if process is running
     */
    fun isProcessAlive(process: Process): Boolean
    
    /**
     * Monitor process output
     * @param process Process to monitor
     * @return Flow of output lines
     */
    fun monitorOutput(process: Process): Flow<String>
}
```

### 5. Private Key Manager

**Responsibility**: Securely write private keys to disk and clean them up.

```kotlin
interface PrivateKeyManager {
    /**
     * Write private key to secure file
     * @param profileId Profile identifier
     * @param keyData Private key bytes
     * @return Path to key file
     */
    suspend fun writePrivateKey(profileId: Long, keyData: ByteArray): Result<String>
    
    /**
     * Delete private key file
     * @param profileId Profile identifier
     */
    suspend fun deletePrivateKey(profileId: Long)
    
    /**
     * Set secure file permissions
     * @param filePath Path to file
     */
    suspend fun setSecurePermissions(filePath: String)
}
```

### 6. SSH Command Builder

**Responsibility**: Build SSH command with appropriate options.

```kotlin
interface SSHCommandBuilder {
    /**
     * Build SSH command for dynamic port forwarding
     * @param binaryPath Path to SSH binary
     * @param profile Server profile
     * @param privateKeyPath Path to private key
     * @param localPort Local SOCKS5 port
     * @return List of command arguments
     */
    fun buildCommand(
        binaryPath: String,
        profile: ServerProfile,
        privateKeyPath: String,
        localPort: Int
    ): List<String>
}
```

### 7. Connection Monitor

**Responsibility**: Monitor SSH connection health and detect failures.

```kotlin
interface ConnectionMonitor {
    /**
     * Start monitoring connection
     * @param process SSH process to monitor
     * @param socksPort SOCKS5 port to check
     * @return Flow of connection states
     */
    fun monitorConnection(
        process: Process,
        socksPort: Int
    ): Flow<ConnectionState>
    
    /**
     * Check if SOCKS5 port is accepting connections
     * @param port Port to check
     * @return true if port is open
     */
    suspend fun isPortOpen(port: Int): Boolean
}

sealed class ConnectionState {
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
```

## Data Models

### SSH Implementation Type

```kotlin
enum class SSHImplementationType {
    NATIVE,  // Native OpenSSH binary
    SSHJ     // Java sshj library
}
```

### Architecture Type

```kotlin
enum class Architecture(val abiName: String) {
    ARM64("arm64-v8a"),
    ARM32("armeabi-v7a"),
    X86_64("x86_64"),
    X86("x86");
    
    companion object {
        fun fromAbi(abi: String): Architecture {
            return values().find { it.abiName == abi } ?: ARM64
        }
    }
}
```

### Binary Metadata

```kotlin
data class BinaryMetadata(
    val architecture: Architecture,
    val version: String,
    val checksum: String,
    val extractedPath: String,
    val extractedAt: Long
)
```

### SSH Process State

```kotlin
sealed class SSHProcessState {
    object NotStarted : SSHProcessState()
    object Starting : SSHProcessState()
    data class Running(val pid: Int, val socksPort: Int) : SSHProcessState()
    object Stopping : SSHProcessState()
    object Stopped : SSHProcessState()
    data class Failed(val error: String) : SSHProcessState()
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*


### Property Reflection

After analyzing all acceptance criteria, several properties can be consolidated to avoid redundancy:

- Properties 4.4 and 7.4 both test key file deletion after connection termination - these can be combined
- Properties about command construction (5.1-5.7, 4.3) can be grouped into comprehensive command validation properties
- Architecture mapping properties (3.2-3.5) can be combined into a single mapping property
- Fallback properties (1.2, 8.1) can be consolidated into a single fallback behavior property

### Correctness Properties

Property 1: Native binary selection by architecture
*For any* device architecture, when the system selects an SSH binary, it should choose the binary matching that architecture
**Validates: Requirements 1.1, 3.2, 3.3, 3.4, 3.5**

Property 2: Fallback to sshj on native unavailability
*For any* connection attempt, when the native SSH binary is not available or extraction fails, the system should fall back to the sshj implementation
**Validates: Requirements 1.2, 8.1**

Property 3: Dynamic port forwarding configuration
*For any* valid port number, when the system builds an SSH command, the command should include the `-D` option with that port number
**Validates: Requirements 1.5**

Property 4: Executable permissions on extracted binary
*For any* extracted SSH binary, the file should have executable permissions set
**Validates: Requirements 2.5**

Property 5: Private key file creation in private directory
*For any* private key data, when the system writes the key to disk, the file should be created in the application's private directory with owner-only read/write permissions
**Validates: Requirements 4.1, 4.2**

Property 6: SSH command includes private key path
*For any* SSH command built with a private key, the command should include the `-i` option followed by the key file path
**Validates: Requirements 4.3**

Property 7: Private key cleanup on termination
*For any* SSH connection, when the connection is terminated or stopped, the private key file should be deleted from disk
**Validates: Requirements 4.4, 7.4**

Property 8: SSH command includes required options
*For any* SSH command built by the system, the command should include all required options: `-D` (port forwarding), `-N` (no remote command), `-T` (no pseudo-terminal), ServerAliveInterval=60, ServerAliveCountMax=10, ExitOnForwardFailure=yes, and ConnectTimeout=30
**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7**

Property 9: Process output capture
*For any* started SSH process, the system should provide access to the process output stream for logging and monitoring
**Validates: Requirements 6.1**

Property 10: Process alive status check
*For any* SSH process, when the system checks if the process is running, the result should accurately reflect whether the process is alive
**Validates: Requirements 6.3**

Property 11: SOCKS5 port availability check
*For any* port number, when the system checks if the port is accepting connections, the result should accurately reflect whether the port is open
**Validates: Requirements 6.4**

Property 12: Process termination detection
*For any* SSH process, when the process terminates (gracefully or unexpectedly), the system should detect the termination and update the connection state
**Validates: Requirements 6.5, 7.5**

Property 13: Graceful shutdown with timeout
*For any* SSH process, when termination is requested, the system should wait up to 5 seconds for graceful shutdown before forcing termination
**Validates: Requirements 7.1, 7.2, 7.3**

Property 14: Error result on process start failure
*For any* SSH process start attempt, when the process fails to start, the system should return a failure result containing an error message
**Validates: Requirements 8.2**

Property 15: Reconnection on connection loss
*For any* active SSH connection, when the connection is lost, the system should attempt automatic reconnection according to the configured policy
**Validates: Requirements 8.4**

Property 16: Resource cleanup on crash
*For any* SSH process, when the process crashes, the system should clean up all associated resources including private key files
**Validates: Requirements 8.5**

Property 17: Native SSH availability check
*For any* SSH client initialization, the system should check whether native SSH binaries are available before attempting to use them
**Validates: Requirements 10.1**

Property 18: Native implementation preference
*For any* SSH client creation, when native SSH is available and no user preference overrides it, the system should create a native SSH client instance
**Validates: Requirements 10.2**

Property 19: User preference persistence
*For any* SSH implementation preference set by the user, the preference should be persisted and respected in subsequent SSH client creations
**Validates: Requirements 10.3, 10.4, 10.5**

Property 20: Binary caching on first extraction
*For any* SSH binary extraction, when the binary is extracted for the first time, it should be cached in the application's private directory for reuse
**Validates: Requirements 12.1**

Property 21: Cached binary reuse
*For any* application start, when a valid cached binary exists, the system should reuse it without re-extracting from the APK
**Validates: Requirements 12.2**

Property 22: Re-extraction on version update
*For any* application version update, the system should re-extract the SSH binary to ensure the latest version is used
**Validates: Requirements 12.3**

Property 23: Checksum verification
*For any* extracted binary, when checksum verification is performed, the system should compare the actual checksum against the expected checksum
**Validates: Requirements 12.4**

Property 24: Re-extraction on corruption
*For any* extracted binary, when the binary is detected as corrupted (checksum mismatch or execution failure), the system should re-extract it from the APK
**Validates: Requirements 12.5**

Property 25: Process termination detection
*For any* SSH process being monitored, when Android kills the process, the system should detect the termination within the monitoring interval
**Validates: Requirements 14.2**

Property 26: Automatic process restart
*For any* SSH process, when the process is killed by Android, the system should automatically restart the process
**Validates: Requirements 14.3**

Property 27: Process health monitoring frequency
*For any* active SSH connection, the system should check process health at least once per second
**Validates: Requirements 14.5**

## Error Handling

### Error Categories

1. **Binary Extraction Errors**
   - Binary not found in APK
   - Insufficient storage space
   - Permission denied on private directory
   - Corrupted binary in APK

2. **Process Start Errors**
   - Binary not executable
   - Invalid command arguments
   - Process creation failure
   - Port already in use

3. **Connection Errors**
   - SSH authentication failure
   - Network unreachable
   - Connection timeout
   - Host key verification failure

4. **Runtime Errors**
   - Process crash
   - Process killed by Android
   - SOCKS5 port closed
   - Network change during connection

### Error Handling Strategy

```kotlin
sealed class SSHError {
    data class BinaryExtractionFailed(val reason: String) : SSHError()
    data class ProcessStartFailed(val reason: String) : SSHError()
    data class ConnectionFailed(val reason: String) : SSHError()
    data class ProcessCrashed(val exitCode: Int) : SSHError()
    data class ProcessKilled(val signal: Int) : SSHError()
    object PortUnavailable : SSHError()
}

interface ErrorHandler {
    /**
     * Handle SSH error and determine recovery action
     * @param error The error that occurred
     * @return Recovery action to take
     */
    suspend fun handleError(error: SSHError): RecoveryAction
}

sealed class RecoveryAction {
    object FallbackToSshj : RecoveryAction()
    object Retry : RecoveryAction()
    object Reconnect : RecoveryAction()
    data class Fail(val message: String) : RecoveryAction()
}
```

### Error Recovery Flow

```
┌─────────────────┐
│  Error Occurs   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Classify Error  │
└────────┬────────┘
         │
         ▼
    ┌────────────────────┐
    │ Binary Extraction? │──Yes──▶ Fallback to sshj
    └────────┬───────────┘
             │ No
             ▼
    ┌────────────────────┐
    │ Process Start?     │──Yes──▶ Retry once, then fallback
    └────────┬───────────┘
             │ No
             ▼
    ┌────────────────────┐
    │ Connection?        │──Yes──▶ Reconnect with backoff
    └────────┬───────────┘
             │ No
             ▼
    ┌────────────────────┐
    │ Process Crash?     │──Yes──▶ Restart process
    └────────┬───────────┘
             │ No
             ▼
    ┌────────────────────┐
    │ Report to User     │
    └────────────────────┘
```

## Testing Strategy

### Unit Testing

Unit tests will verify individual components in isolation:

**Binary Manager Tests:**
- Test architecture detection for all supported ABIs
- Test binary extraction from APK
- Test binary caching and reuse
- Test checksum verification
- Test corruption detection and re-extraction

**Command Builder Tests:**
- Test command construction with various profiles
- Test inclusion of all required SSH options
- Test port number formatting
- Test private key path inclusion
- Test username and hostname formatting

**Process Manager Tests:**
- Test process start with valid commands
- Test process termination (graceful and forced)
- Test process alive status checking
- Test output stream capture

**Private Key Manager Tests:**
- Test key file creation in private directory
- Test file permission setting
- Test key file deletion
- Test cleanup on errors

**Connection Monitor Tests:**
- Test port availability checking
- Test process health monitoring
- Test connection state transitions
- Test disconnection detection

### Property-Based Testing

Property-based tests will verify universal properties across many inputs using Kotest:

**Test Framework**: Kotest Property Testing
**Minimum Iterations**: 100 per property
**Test Structure**: Use `@Test` annotations with `runTest` wrapper (not Kotest spec styles)

**Property Test Examples:**

```kotlin
class NativeSSHPropertiesTest {
    
    @Test
    fun `architecture selection matches device ABI`() = runTest {
        // Feature: native-ssh-client, Property 1: Native binary selection by architecture
        // Validates: Requirements 1.1, 3.2, 3.3, 3.4, 3.5
        checkAll(100, Arb.architecture()) { arch ->
            val binaryManager = BinaryManager(context)
            val selectedBinary = binaryManager.selectBinary(arch)
            selectedBinary.architecture shouldBe arch
        }
    }
    
    @Test
    fun `SSH command includes all required options`() = runTest {
        // Feature: native-ssh-client, Property 8: SSH command includes required options
        // Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
        checkAll(100, Arb.serverProfile(), Arb.port(), Arb.filePath()) { profile, port, keyPath ->
            val command = SSHCommandBuilder().buildCommand(
                binaryPath = "/path/to/ssh",
                profile = profile,
                privateKeyPath = keyPath,
                localPort = port
            )
            
            command shouldContain "-D"
            command shouldContain port.toString()
            command shouldContain "-N"
            command shouldContain "-T"
            command shouldContain "ServerAliveInterval=60"
            command shouldContain "ServerAliveCountMax=10"
            command shouldContain "ExitOnForwardFailure=yes"
            command shouldContain "ConnectTimeout=30"
            command shouldContain "-i"
            command shouldContain keyPath
        }
    }
    
    @Test
    fun `private key file is deleted after connection termination`() = runTest {
        // Feature: native-ssh-client, Property 7: Private key cleanup on termination
        // Validates: Requirements 4.4, 7.4
        checkAll(100, Arb.long(), Arb.byteArray(Arb.int(256..4096))) { profileId, keyData ->
            val keyManager = PrivateKeyManager(context)
            
            // Write key
            val keyPath = keyManager.writePrivateKey(profileId, keyData).getOrThrow()
            File(keyPath).exists() shouldBe true
            
            // Simulate connection termination
            keyManager.deletePrivateKey(profileId)
            
            // Key should be deleted
            File(keyPath).exists() shouldBe false
        }
    }
    
    @Test
    fun `cached binary is reused without re-extraction`() = runTest {
        // Feature: native-ssh-client, Property 21: Cached binary reuse
        // Validates: Requirements 12.2
        checkAll(100, Arb.architecture()) { arch ->
            val binaryManager = BinaryManager(context)
            
            // First extraction
            val firstPath = binaryManager.extractBinary(arch).getOrThrow()
            val firstTimestamp = File(firstPath).lastModified()
            
            // Second call should reuse cache
            val secondPath = binaryManager.extractBinary(arch).getOrThrow()
            val secondTimestamp = File(secondPath).lastModified()
            
            firstPath shouldBe secondPath
            firstTimestamp shouldBe secondTimestamp
        }
    }
}
```

### Custom Generators

```kotlin
object SSHGenerators {
    fun Arb.Companion.architecture() = arbitrary {
        Architecture.values().random()
    }
    
    fun Arb.Companion.port() = int(1024..65535)
    
    fun Arb.Companion.filePath() = arbitrary {
        "/data/data/com.sshtunnel/files/keys/key_${long(1..1000).bind()}"
    }
    
    fun Arb.Companion.serverProfile() = arbitrary {
        ServerProfile(
            id = long(1..1000).bind(),
            name = string(5..20).bind(),
            hostname = domain().bind(),
            port = int(1..65535).bind(),
            username = string(3..16, Codepoint.alphanumeric()).bind(),
            keyType = enum<KeyType>().bind()
        )
    }
}
```

### Integration Testing

Integration tests will verify end-to-end functionality:

**SSH Connection Integration:**
- Start native SSH process with real SSH server
- Verify SOCKS5 proxy is created on specified port
- Test data transfer through SOCKS5 proxy
- Verify connection stability over time
- Test reconnection on network interruption

**VPN Service Integration:**
- Integrate native SSH with VPN service
- Route traffic through native SSH tunnel
- Verify DNS queries go through tunnel
- Test app-specific routing with native SSH
- Verify battery usage is acceptable

**Error Recovery Integration:**
- Test fallback to sshj when native fails
- Test automatic reconnection on connection loss
- Test process restart on crash
- Test cleanup on all error paths

### Manual Testing Checklist

- [ ] Test on ARM64 physical device
- [ ] Test on ARM32 physical device (if available)
- [ ] Test on x86_64 emulator
- [ ] Test with different SSH servers (OpenSSH, Dropbear, etc.)
- [ ] Test with different key types (RSA, ECDSA, Ed25519)
- [ ] Test network interruptions (WiFi to mobile data)
- [ ] Test long-running connections (24+ hours)
- [ ] Test battery usage over extended period
- [ ] Test APK size for each architecture
- [ ] Test fallback to sshj when native unavailable
- [ ] Test user preference for SSH implementation
- [ ] Verify no "Stream closed" errors occur

## Performance Considerations

### Binary Extraction Optimization

- **Cache extracted binaries**: Don't re-extract on every app start
- **Lazy extraction**: Only extract when first needed
- **Background extraction**: Extract during app initialization, not on connection
- **Checksum verification**: Only verify if cache is suspected corrupted

### Process Monitoring Optimization

- **Efficient polling**: Check process health every 1 second (not more frequently)
- **Event-driven monitoring**: Use process output stream for real-time status
- **Batch port checks**: Don't check port on every monitoring cycle
- **Coroutine-based**: Use Kotlin coroutines for non-blocking monitoring

### Memory Management

- **Stream buffering**: Use buffered readers for process output
- **Resource cleanup**: Always close streams and processes
- **Weak references**: Use weak references for callbacks to prevent leaks
- **Process lifecycle**: Tie process lifecycle to service lifecycle

### Battery Optimization

- **Foreground service**: Run SSH process within foreground VPN service
- **Efficient keep-alive**: Use SSH's built-in keep-alive (ServerAliveInterval)
- **Reduce polling**: Minimize active monitoring when connection is stable
- **Native efficiency**: Native SSH uses less battery than Java implementation

## Security Considerations

### Private Key Protection

- **File permissions**: Set owner-only read/write (600) on key files
- **Private directory**: Store keys in app's private directory (not accessible to other apps)
- **Immediate cleanup**: Delete key files immediately after connection terminates
- **No logging**: Never log private key contents
- **Memory clearing**: Clear key data from memory after writing to disk

### Binary Integrity

- **Checksum verification**: Verify binary checksums after extraction
- **Source verification**: Use official Termux binaries with known checksums
- **Update process**: Document process for updating binaries for security patches
- **Attribution**: Include Termux attribution and license information

### Process Security

- **No shell injection**: Use ProcessBuilder with argument list (not shell command string)
- **Argument validation**: Validate all arguments before passing to SSH
- **Output sanitization**: Sanitize SSH output before logging
- **Error message safety**: Don't expose sensitive information in error messages

### Network Security

- **Host key verification**: Support strict host key checking (optional)
- **Known hosts**: Maintain known_hosts file for verified servers
- **Connection encryption**: All traffic encrypted by SSH
- **No plaintext credentials**: Only support key-based authentication

## Deployment Considerations

### APK Size Management

**Binary Sizes (approximate):**
- SSH binary: 800 KB - 1.2 MB per architecture
- libcrypto.so: 1.5 MB - 2 MB per architecture
- libssl.so: 400 KB - 600 KB per architecture
- **Total per architecture**: ~2.7 MB - 3.8 MB

**Optimization Strategies:**
1. **APK Splits**: Create separate APKs for each architecture
2. **Universal APK**: Provide universal APK with all architectures for compatibility
3. **Production focus**: Consider ARM64-only for production (covers 95%+ of devices)
4. **Binary stripping**: Strip debug symbols from binaries

**Expected APK Size Impact:**
- Single architecture: +3-4 MB
- All architectures: +12-15 MB
- With APK splits: Users download only +3-4 MB for their device

### Version Management

**Binary Versioning:**
- Track OpenSSH version in build configuration
- Include version in binary metadata
- Re-extract binaries on app version update
- Document binary source and version in app

**Update Process:**
1. Monitor OpenSSH security advisories
2. Download updated Termux binaries
3. Verify checksums
4. Update binaries in project
5. Increment app version
6. Release update to users

### Compatibility

**Minimum Requirements:**
- Android API 26+ (Android 8.0+)
- ARM64, ARM32, x86_64, or x86 architecture
- 50 MB free storage space
- VPN permission granted

**Tested Configurations:**
- Android 8.0 - 14
- ARM64 (primary target)
- ARM32 (legacy devices)
- x86_64 (emulators)

## Migration Path

### Gradual Rollout

**Phase 1: Beta Testing**
- Enable native SSH for beta users only
- Collect metrics on success rate and performance
- Monitor for crashes and errors
- Gather user feedback

**Phase 2: Opt-In**
- Add setting to enable native SSH
- Default to sshj for stability
- Allow users to opt into native SSH
- Continue monitoring metrics

**Phase 3: Default Native**
- Make native SSH the default
- Keep sshj as fallback
- Allow users to switch back if needed
- Monitor for issues

**Phase 4: Native Only**
- Remove sshj dependency (optional)
- Native SSH only
- Smaller APK size
- Simpler codebase

### Rollback Plan

If native SSH has critical issues:
1. **Immediate**: Disable native SSH via remote config
2. **Short-term**: Release hotfix defaulting to sshj
3. **Long-term**: Fix native SSH issues and re-enable

## Success Metrics

### Reliability Metrics

- **Connection success rate**: Target >95% (vs 33% with sshj)
- **Stream closed errors**: Target 0 (vs frequent with sshj)
- **Connection stability**: Target 24+ hours without disconnection
- **Crash rate**: Target <0.1% of connections

### Performance Metrics

- **Connection time**: Target <5 seconds to establish
- **Memory usage**: Target <50 MB for SSH process
- **Battery usage**: Target <5% per hour of active connection
- **APK size**: Target <10 MB increase per architecture

### User Experience Metrics

- **User satisfaction**: Target >4.5/5 stars
- **Support tickets**: Target 50% reduction in connection issues
- **Retention**: Target improved retention due to reliability
- **Fallback usage**: Target <5% of users needing sshj fallback

## Future Enhancements

### Potential Improvements

1. **SSH multiplexing**: Reuse SSH connections for multiple SOCKS5 sessions
2. **Connection pooling**: Maintain pool of ready SSH connections
3. **Custom SSH options**: Allow users to specify additional SSH options
4. **Host key management**: UI for managing known hosts
5. **Key generation**: Built-in SSH key generation using ssh-keygen binary
6. **Connection profiles**: Save SSH options per profile
7. **Bandwidth monitoring**: Track data usage through SSH tunnel
8. **Compression**: Enable SSH compression for slow connections

### iOS Support

When adding iOS support:
- Use same architecture (native SSH binary)
- Bundle OpenSSH for iOS architectures
- Use similar process management approach
- Share connection logic in common module
- Platform-specific binary extraction and process management

## Conclusion

The native SSH client implementation provides a reliable, performant alternative to the problematic sshj library. By leveraging the battle-tested OpenSSH implementation, we achieve the same reliability as desktop SSH clients while maintaining native performance and battery efficiency on Android.

The hybrid approach with fallback to sshj ensures maximum compatibility, while the modular design allows for easy testing and future enhancements. The implementation follows Android best practices for process management, security, and battery optimization.

Success criteria focus on measurable improvements in connection reliability (>95% success rate), elimination of "Stream closed" errors, and acceptable APK size impact (<10 MB per architecture). The gradual rollout plan ensures we can monitor real-world performance and roll back if needed.
