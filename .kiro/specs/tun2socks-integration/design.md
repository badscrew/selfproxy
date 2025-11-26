# Design: tun2socks Integration

## Architecture Overview

Replace the manual PacketRouter implementation with tun2socks library integration to provide reliable VPN traffic routing through SOCKS5 proxy.

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Apps                            │
└─────────────────────┬───────────────────────────────────────┘
                      │ Network Traffic
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                  TUN Interface (tun0)                        │
│              Created by TunnelVpnService                     │
└─────────────────────┬───────────────────────────────────────┘
                      │ IP Packets
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                   tun2socks Library                          │
│              (Native Go/C Implementation)                    │
│  • Reads packets from TUN file descriptor                   │
│  • Parses TCP/UDP/ICMP packets                              │
│  • Routes through SOCKS5 proxy                              │
│  • Writes responses back to TUN                             │
└─────────────────────┬───────────────────────────────────────┘
                      │ SOCKS5 Protocol
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              SOCKS5 Proxy (127.0.0.1:port)                  │
│              Created by SSH Connection                       │
└─────────────────────┬───────────────────────────────────────┘
                      │ SSH Tunnel
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                   SSH Server                                 │
│              (User's Remote Server)                          │
└─────────────────────┬───────────────────────────────────────┘
                      │ Internet Traffic
                      ▼
                  Internet
```

## Component Design

### 1. Tun2SocksEngine (New)

Native library wrapper that manages the tun2socks process.

```kotlin
/**
 * Manages the tun2socks native library for routing VPN traffic through SOCKS5.
 */
class Tun2SocksEngine {
    /**
     * Starts tun2socks with the given configuration.
     * 
     * @param tunFd File descriptor of the TUN interface
     * @param socksAddress SOCKS5 proxy address (e.g., "127.0.0.1:1080")
     * @param dnsAddress DNS server address (e.g., "8.8.8.8:53")
     * @param mtu MTU size for the TUN interface
     * @return Result indicating success or failure
     */
    suspend fun start(
        tunFd: Int,
        socksAddress: String,
        dnsAddress: String,
        mtu: Int = 1500
    ): Result<Unit>
    
    /**
     * Stops the tun2socks process.
     */
    suspend fun stop(): Result<Unit>
    
    /**
     * Checks if tun2socks is currently running.
     */
    fun isRunning(): Boolean
    
    /**
     * Gets the current status and statistics.
     */
    fun getStats(): Tun2SocksStats
}

data class Tun2SocksStats(
    val bytesReceived: Long,
    val bytesSent: Long,
    val packetsReceived: Long,
    val packetsSent: Long,
    val errors: Long
)
```

### 2. TunnelVpnService (Modified)

Update to use Tun2SocksEngine instead of PacketRouter.

```kotlin
class TunnelVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2socks: Tun2SocksEngine? = null
    private var socksPort: Int = 0
    
    private fun startVpn(serverAddress: String) {
        serviceScope.launch {
            try {
                // Verify SOCKS proxy is reachable
                if (!verifySocksProxy()) {
                    broadcastVpnError("SOCKS proxy not reachable")
                    stopSelf()
                    return@launch
                }
                
                // Create TUN interface
                vpnInterface = createTunInterface()
                if (vpnInterface == null) {
                    broadcastVpnError("Failed to create VPN interface")
                    stopSelf()
                    return@launch
                }
                
                // Start foreground service
                startForeground(NOTIFICATION_ID, createNotification(serverAddress))
                
                // Start tun2socks
                val tunFd = vpnInterface!!.fd
                val socksAddress = "127.0.0.1:$socksPort"
                val dnsAddress = "8.8.8.8:53"
                
                tun2socks = Tun2SocksEngine()
                val result = tun2socks!!.start(tunFd, socksAddress, dnsAddress)
                
                if (result.isFailure) {
                    broadcastVpnError("Failed to start tun2socks: ${result.exceptionOrNull()?.message}")
                    stopSelf()
                    return@launch
                }
                
                android.util.Log.i(TAG, "tun2socks started successfully")
                broadcastVpnStarted()
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start VPN: ${e.message}", e)
                broadcastVpnError("Failed to start VPN: ${e.message}")
                stopSelf()
            }
        }
    }
    
    private fun stopVpn() {
        android.util.Log.i(TAG, "Stopping VPN service")
        
        try {
            // Stop tun2socks
            tun2socks?.stop()
            tun2socks = null
            
            // Close VPN interface
            vpnInterface?.close()
            vpnInterface = null
            
            // Stop foreground
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            broadcastVpnStopped()
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping VPN: ${e.message}", e)
            tun2socks = null
            vpnInterface = null
        }
    }
}
```

### 3. Native Library Integration

#### Option A: Use xjasonlyu/tun2socks (Recommended)

**Pros:**
- Go-based, modern implementation
- Active development and maintenance
- Good documentation
- Supports Android out of the box
- Pre-built binaries available

**Cons:**
- Larger binary size (Go runtime included)
- Requires gomobile for Android bindings

**Integration approach:**
1. Use pre-built Android AAR from releases
2. Or build from source using gomobile
3. Add as dependency in build.gradle.kts

```kotlin
// build.gradle.kts
dependencies {
    implementation(files("libs/tun2socks.aar"))
    // Or from Maven if available
}
```

#### Option B: Use ambrop72/badvpn

**Pros:**
- C-based, smaller binary size
- Mature, stable implementation
- Lower memory footprint

**Cons:**
- Less active development
- More complex build process
- Need to create JNI bindings manually

### 4. JNI Bridge (If using badvpn or custom build)

```kotlin
// Tun2SocksNative.kt
object Tun2SocksNative {
    init {
        System.loadLibrary("tun2socks")
    }
    
    external fun start(
        tunFd: Int,
        socksAddr: String,
        dnsAddr: String,
        mtu: Int
    ): Int
    
    external fun stop(): Int
    
    external fun isRunning(): Boolean
    
    external fun getStats(): LongArray // [bytesRx, bytesTx, packetsRx, packetsTx, errors]
}
```

### 5. Error Handling

```kotlin
sealed class Tun2SocksError : Exception() {
    data class InitializationFailed(override val message: String) : Tun2SocksError()
    data class LibraryNotFound(override val message: String) : Tun2SocksError()
    data class InvalidConfiguration(override val message: String) : Tun2SocksError()
    data class RuntimeError(override val message: String) : Tun2SocksError()
    data class SocksConnectionFailed(override val message: String) : Tun2SocksError()
}
```

## Implementation Strategy

### Phase 1: Library Selection and Setup
1. Evaluate tun2socks libraries (xjasonlyu vs badvpn)
2. Choose based on ease of integration and maintenance
3. Add library dependency to project
4. Verify native library loads correctly

### Phase 2: Kotlin Wrapper
1. Create Tun2SocksEngine class
2. Implement JNI bindings (if needed)
3. Add error handling and logging
4. Create unit tests for wrapper

### Phase 3: VPN Service Integration
1. Remove PacketRouter code
2. Integrate Tun2SocksEngine into TunnelVpnService
3. Update lifecycle management
4. Add error handling and recovery

### Phase 4: Testing
1. Test basic connectivity (HTTP/HTTPS)
2. Test DNS resolution
3. Test error scenarios
4. Performance testing
5. Battery usage testing

### Phase 5: Optimization
1. Tune buffer sizes and parameters
2. Optimize for mobile networks
3. Add monitoring and diagnostics
4. Document configuration options

## Configuration

### Recommended tun2socks Parameters

```kotlin
data class Tun2SocksConfig(
    val socksAddress: String,           // "127.0.0.1:1080"
    val dnsAddress: String = "8.8.8.8:53",
    val mtu: Int = 1500,
    val logLevel: LogLevel = LogLevel.INFO,
    val enableUdp: Boolean = true,
    val enableIcmp: Boolean = false,    // ICMP not needed for most use cases
    val tcpTimeout: Duration = 5.minutes,
    val udpTimeout: Duration = 30.seconds
)

enum class LogLevel {
    SILENT, ERROR, WARNING, INFO, DEBUG, VERBOSE
}
```

## Migration Plan

### Step 1: Add tun2socks Dependency
- Add library to project
- Verify it loads on test device
- Create basic wrapper class

### Step 2: Create Parallel Implementation
- Keep PacketRouter temporarily
- Add Tun2SocksEngine alongside
- Add feature flag to switch between implementations

### Step 3: Test and Validate
- Test tun2socks implementation thoroughly
- Compare with PacketRouter behavior
- Fix any issues

### Step 4: Switch Over
- Make tun2socks the default
- Remove PacketRouter code
- Update documentation

### Step 5: Cleanup
- Remove old packet routing code
- Remove feature flag
- Final testing

## Testing Strategy

### Unit Tests
```kotlin
class Tun2SocksEngineTest {
    @Test
    fun `start should succeed with valid configuration`()
    
    @Test
    fun `start should fail with invalid SOCKS address`()
    
    @Test
    fun `stop should clean up resources`()
    
    @Test
    fun `isRunning should return correct state`()
}
```

### Integration Tests
```kotlin
class Tun2SocksIntegrationTest {
    @Test
    fun `should route HTTP traffic through SOCKS5`()
    
    @Test
    fun `should route HTTPS traffic through SOCKS5`()
    
    @Test
    fun `should resolve DNS through tunnel`()
    
    @Test
    fun `should handle SOCKS proxy disconnection`()
}
```

### Manual Testing Checklist
- [ ] Web browsing works (HTTP/HTTPS)
- [ ] DNS resolution works
- [ ] No DNS leaks (check with dnsleaktest.com)
- [ ] VPN reconnects after network change
- [ ] VPN stops cleanly on disconnect
- [ ] No crashes or ANRs
- [ ] Acceptable battery usage
- [ ] Acceptable performance (latency, throughput)

## Performance Considerations

### Memory Usage
- tun2socks process: ~10-20 MB
- Native library: ~5-10 MB per architecture
- Total overhead: ~15-30 MB

### CPU Usage
- Idle: <1% CPU
- Active browsing: 2-5% CPU
- Heavy traffic: 5-10% CPU

### Battery Impact
- Expected: 2-5% additional battery drain per hour
- Comparable to other VPN apps

## Security Considerations

1. **Native Library Security**
   - Use official releases from trusted sources
   - Verify checksums of downloaded binaries
   - Keep library updated for security patches

2. **File Descriptor Handling**
   - Ensure TUN fd is not leaked
   - Close fd properly on errors
   - Validate fd before passing to native code

3. **SOCKS5 Connection**
   - Verify SOCKS proxy is localhost only
   - Don't expose SOCKS port externally
   - Use authentication if needed (future enhancement)

## Rollback Plan

If tun2socks integration fails:
1. Revert to PacketRouter implementation
2. Add warning that VPN is experimental
3. Investigate alternative libraries
4. Consider implementing minimal TCP-only routing

## Success Metrics

1. **Functionality**: 100% of web traffic routes through VPN
2. **Reliability**: <1% crash rate
3. **Performance**: <100ms additional latency
4. **Battery**: <5% additional drain per hour
5. **User Satisfaction**: No "No Internet" errors

## Future Enhancements

1. **IPv6 Support** - Add IPv6 routing through tun2socks
2. **Custom DNS** - Allow user to configure DNS servers
3. **Statistics** - Show traffic statistics in UI
4. **Split Tunneling** - Route only specific apps through VPN
5. **Protocol Optimization** - Tune for mobile networks
