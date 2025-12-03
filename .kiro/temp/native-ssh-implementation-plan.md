# Native SSH Binary Implementation Plan

## Context

**Problem**: sshj library has 67% connection failure rate due to SSH channels closing prematurely with "Stream closed" errors. The same SSH server works perfectly with native `ssh -D` on PC.

**Solution**: Bundle native OpenSSH binary with the app and use `ssh -D` command instead of Java SSH libraries.

**Status**: Ready to implement. All debugging work committed and pushed to GitHub (commit: 36ed7b6).

---

## Implementation Plan

### Phase 1: Research & Preparation

#### 1.1 Find/Build OpenSSH Binary for Android
- **Option A**: Use pre-built binaries from Termux
  - Termux provides OpenSSH compiled for Android (ARM, ARM64, x86, x86_64)
  - License: BSD (compatible with our app)
  - Source: https://github.com/termux/termux-packages/tree/master/packages/openssh
  
- **Option B**: Build from source
  - Cross-compile OpenSSH for Android using NDK
  - More control but more complex
  - Requires: Android NDK, OpenSSL for Android

**Recommendation**: Start with Option A (Termux binaries) for faster implementation.

#### 1.2 Determine Required Binaries
Minimum required:
- `ssh` - Main SSH client binary
- `ssh-keygen` - For key generation (if needed)

Dependencies (may be needed):
- `libcrypto.so` - OpenSSL crypto library
- `libssl.so` - OpenSSL SSL library

#### 1.3 Check Binary Sizes
- Estimate APK size increase
- Consider using only ARM64 for modern devices (smaller APK)
- Or include all architectures (ARM, ARM64, x86, x86_64) for compatibility

---

### Phase 2: Project Setup

#### 2.1 Create Native Library Structure
```
androidApp/
  src/
    main/
      jniLibs/           # Native libraries directory
        arm64-v8a/       # ARM 64-bit
          ssh
          libcrypto.so
          libssl.so
        armeabi-v7a/     # ARM 32-bit
          ssh
          libcrypto.so
          libssl.so
        x86_64/          # x86 64-bit (emulator)
          ssh
          libcrypto.so
          libssl.so
        x86/             # x86 32-bit (emulator)
          ssh
          libcrypto.so
          libssl.so
```

#### 2.2 Update build.gradle.kts
```kotlin
android {
    // Ensure native libraries are packaged
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    
    // Optional: Split APKs by architecture to reduce size
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }
}
```

---

### Phase 3: Implementation

#### 3.1 Create NativeSSHClient Class

**File**: `androidApp/src/main/kotlin/com/sshtunnel/android/ssh/NativeSSHClient.kt`

```kotlin
class NativeSSHClient(
    private val context: Context,
    private val logger: Logger
) {
    private var sshProcess: Process? = null
    private val sshBinaryPath: String by lazy {
        extractSSHBinary()
    }
    
    /**
     * Extract SSH binary from APK to app's private directory
     */
    private fun extractSSHBinary(): String {
        val arch = getDeviceArchitecture()
        val binaryName = "ssh"
        val targetDir = File(context.filesDir, "bin")
        targetDir.mkdirs()
        
        val targetFile = File(targetDir, binaryName)
        
        // Extract from jniLibs
        context.assets.open("$arch/$binaryName").use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Make executable
        targetFile.setExecutable(true, false)
        
        return targetFile.absolutePath
    }
    
    /**
     * Start SSH with dynamic port forwarding (SOCKS5)
     */
    fun startSSHTunnel(
        profile: ServerProfile,
        privateKeyPath: String,
        localPort: Int
    ): Result<Int> {
        val command = buildSSHCommand(
            profile = profile,
            privateKeyPath = privateKeyPath,
            localPort = localPort
        )
        
        sshProcess = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        
        // Monitor process output in background
        monitorSSHProcess()
        
        return Result.success(localPort)
    }
    
    /**
     * Build SSH command with all necessary options
     */
    private fun buildSSHCommand(
        profile: ServerProfile,
        privateKeyPath: String,
        localPort: Int
    ): List<String> {
        return listOf(
            sshBinaryPath,
            "-D", localPort.toString(),           // Dynamic port forwarding
            "-N",                                  // No remote command
            "-T",                                  // No pseudo-terminal
            "-o", "StrictHostKeyChecking=no",     // Don't verify host key
            "-o", "ServerAliveInterval=60",       // Keep-alive every 60s
            "-o", "ServerAliveCountMax=10",       // 10 missed = disconnect
            "-o", "ExitOnForwardFailure=yes",     // Exit if forwarding fails
            "-o", "ConnectTimeout=30",            // 30s connection timeout
            "-i", privateKeyPath,                 // Private key
            "-p", profile.port.toString(),        // Port
            "${profile.username}@${profile.hostname}"  // user@host
        )
    }
    
    /**
     * Monitor SSH process output for errors
     */
    private fun monitorSSHProcess() {
        Thread {
            sshProcess?.inputStream?.bufferedReader()?.use { reader ->
                reader.lineSequence().forEach { line ->
                    logger.info("SSH", line)
                    // Parse for connection status, errors, etc.
                }
            }
        }.start()
    }
    
    /**
     * Stop SSH tunnel
     */
    fun stopSSHTunnel() {
        sshProcess?.destroy()
        sshProcess?.waitFor(5, TimeUnit.SECONDS)
        if (sshProcess?.isAlive == true) {
            sshProcess?.destroyForcibly()
        }
        sshProcess = null
    }
    
    /**
     * Check if SSH tunnel is running
     */
    fun isRunning(): Boolean {
        return sshProcess?.isAlive == true
    }
    
    private fun getDeviceArchitecture(): String {
        return when (Build.SUPPORTED_ABIS[0]) {
            "arm64-v8a" -> "arm64-v8a"
            "armeabi-v7a" -> "armeabi-v7a"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "arm64-v8a" // Default to ARM64
        }
    }
}
```

#### 3.2 Update AndroidSSHClient Interface

Add method to switch between implementations:

```kotlin
interface SSHClient {
    // Existing methods...
    
    /**
     * Get implementation type
     */
    fun getImplementationType(): SSHImplementationType
}

enum class SSHImplementationType {
    SSHJ,      // Current Java library implementation
    NATIVE     // Native SSH binary implementation
}
```

#### 3.3 Create Factory to Choose Implementation

```kotlin
object SSHClientFactory {
    fun create(
        context: Context,
        logger: Logger,
        preferNative: Boolean = true
    ): SSHClient {
        return if (preferNative && isNativeSSHAvailable(context)) {
            NativeSSHClientWrapper(context, logger)
        } else {
            AndroidSSHClient(logger) // Existing sshj implementation
        }
    }
    
    private fun isNativeSSHAvailable(context: Context): Boolean {
        // Check if native SSH binary is available
        val arch = getDeviceArchitecture()
        return try {
            context.assets.list(arch)?.contains("ssh") == true
        } catch (e: Exception) {
            false
        }
    }
}
```

#### 3.4 Handle Private Key Storage

Native SSH requires key file on disk:

```kotlin
class PrivateKeyManager(private val context: Context) {
    /**
     * Write private key to secure location
     */
    fun writePrivateKey(profileId: Long, keyData: ByteArray): String {
        val keyDir = File(context.filesDir, "keys")
        keyDir.mkdirs()
        
        val keyFile = File(keyDir, "key_$profileId")
        keyFile.writeBytes(keyData)
        
        // Set permissions: owner read/write only
        keyFile.setReadable(false, false)
        keyFile.setReadable(true, true)
        keyFile.setWritable(false, false)
        keyFile.setWritable(true, true)
        
        return keyFile.absolutePath
    }
    
    /**
     * Delete private key file
     */
    fun deletePrivateKey(profileId: Long) {
        val keyFile = File(context.filesDir, "keys/key_$profileId")
        keyFile.delete()
    }
}
```

---

### Phase 4: Integration

#### 4.1 Update VPN Service

Modify `TunnelVpnService` to use native SSH:

```kotlin
class TunnelVpnService : VpnService() {
    private lateinit var sshClient: NativeSSHClient
    
    override fun onCreate() {
        super.onCreate()
        sshClient = NativeSSHClient(this, logger)
    }
    
    private suspend fun startTunnel() {
        // Write private key to file
        val keyPath = privateKeyManager.writePrivateKey(
            profileId = currentProfile.id,
            keyData = privateKey.keyData
        )
        
        // Start SSH tunnel
        val result = sshClient.startSSHTunnel(
            profile = currentProfile,
            privateKeyPath = keyPath,
            localPort = 1080
        )
        
        if (result.isSuccess) {
            // Continue with VPN setup...
        }
    }
    
    override fun onDestroy() {
        sshClient.stopSSHTunnel()
        privateKeyManager.deletePrivateKey(currentProfile.id)
        super.onDestroy()
    }
}
```

#### 4.2 Update Connection State Monitoring

Monitor SSH process to detect disconnections:

```kotlin
private fun monitorSSHConnection() {
    scope.launch {
        while (sshClient.isRunning()) {
            delay(1000)
            // Check if SOCKS5 port is still accepting connections
            if (!isSOCKS5PortOpen(1080)) {
                // Connection lost, attempt reconnect
                handleConnectionLost()
            }
        }
    }
}

private fun isSOCKS5PortOpen(port: Int): Boolean {
    return try {
        Socket("127.0.0.1", port).use { true }
    } catch (e: Exception) {
        false
    }
}
```

---

### Phase 5: Testing

#### 5.1 Unit Tests
- Test SSH binary extraction
- Test command building
- Test process lifecycle management
- Test private key file handling

#### 5.2 Integration Tests
- Test SSH connection establishment
- Test SOCKS5 proxy functionality
- Test reconnection on failure
- Test cleanup on disconnect

#### 5.3 Manual Testing
- Test on physical device (ARM64)
- Test on emulator (x86_64)
- Test with different SSH servers
- Test network interruptions
- Test battery usage
- Test long-running connections

---

### Phase 6: Optimization

#### 6.1 APK Size Optimization
- Use only ARM64 for production (most devices)
- Provide separate APKs for different architectures
- Strip debug symbols from binaries

#### 6.2 Performance Optimization
- Cache extracted binaries (don't extract every time)
- Optimize process monitoring (reduce polling)
- Implement connection pooling if needed

#### 6.3 Error Handling
- Handle SSH binary extraction failures
- Handle SSH process crashes
- Implement automatic reconnection
- Provide meaningful error messages to users

---

## Expected Benefits

### Reliability
- ✅ **100% success rate** (same as native SSH)
- ✅ **No channel management issues**
- ✅ **Battle-tested OpenSSH implementation**
- ✅ **Proven to work with user's SSH server**

### Performance
- ✅ **Native performance** (no Java overhead)
- ✅ **Efficient memory usage**
- ✅ **Better battery life** (native code)

### Maintainability
- ✅ **Less code to maintain** (no SOCKS5 implementation)
- ✅ **Fewer dependencies** (no sshj library)
- ✅ **Easier debugging** (standard SSH logs)

---

## Potential Challenges

### 1. Binary Size
- **Issue**: SSH binary + libraries = ~2-5 MB per architecture
- **Solution**: Use APK splits, only include ARM64 for production

### 2. Security
- **Issue**: Bundling binaries could be seen as security risk
- **Solution**: Use official Termux binaries, verify checksums, document source

### 3. Updates
- **Issue**: Need to update binaries for security patches
- **Solution**: Monitor OpenSSH releases, update binaries in new app versions

### 4. Compatibility
- **Issue**: Different Android versions might have issues
- **Solution**: Test on multiple Android versions (API 26+)

### 5. Process Management
- **Issue**: Android might kill background processes
- **Solution**: Use foreground service, handle process death gracefully

---

## Alternative: Hybrid Approach

If native SSH has issues, implement a **hybrid approach**:

1. **Try native SSH first** (preferred)
2. **Fall back to sshj** if native fails
3. **Let user choose** in settings

This provides maximum compatibility while preferring the reliable native implementation.

---

## Resources

### OpenSSH for Android
- Termux OpenSSH: https://github.com/termux/termux-packages/tree/master/packages/openssh
- Pre-built binaries: https://packages.termux.dev/apt/termux-main/pool/main/o/openssh/

### Android NDK
- NDK Documentation: https://developer.android.com/ndk
- Cross-compilation guide: https://developer.android.com/ndk/guides/other_build_systems

### Process Management
- ProcessBuilder: https://developer.android.com/reference/java/lang/ProcessBuilder
- Runtime.exec(): https://developer.android.com/reference/java/lang/Runtime#exec(java.lang.String[])

### Security
- Android file permissions: https://developer.android.com/training/articles/security-tips#StoringData
- Private key handling: https://developer.android.com/training/articles/keystore

---

## Timeline Estimate

- **Phase 1 (Research)**: 2-4 hours
- **Phase 2 (Setup)**: 1-2 hours
- **Phase 3 (Implementation)**: 4-6 hours
- **Phase 4 (Integration)**: 2-3 hours
- **Phase 5 (Testing)**: 3-4 hours
- **Phase 6 (Optimization)**: 2-3 hours

**Total**: 14-22 hours

---

## Success Criteria

- ✅ SSH tunnel establishes successfully
- ✅ SOCKS5 proxy works on port 1080
- ✅ Web browsing success rate > 95%
- ✅ No "Stream closed" errors
- ✅ Stable long-running connections
- ✅ Automatic reconnection works
- ✅ APK size increase < 10 MB
- ✅ Battery usage acceptable

---

## Next Steps

1. **Download Termux OpenSSH binaries** for all architectures
2. **Create jniLibs directory structure** in androidApp
3. **Implement NativeSSHClient class** with basic functionality
4. **Test SSH binary extraction and execution** on emulator
5. **Integrate with existing VPN service**
6. **Test end-to-end** with real SSH server
7. **Optimize and polish**

---

## Notes

- This approach bypasses all sshj channel management issues
- Uses the exact same SSH implementation that works on PC
- Provides native performance and reliability
- Slightly larger APK but much better user experience
- Can fall back to sshj if needed for compatibility

**Status**: Ready to implement. All current work committed to Git (commit: 36ed7b6).
