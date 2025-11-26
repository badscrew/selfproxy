# tun2socks Integration Guide

## Current Status

✅ **Phase 1 Complete:** VPN Infrastructure
- VPN service created and working
- TUN interface configuration complete
- SSH connection and SOCKS5 proxy working
- VPN lifecycle management implemented
- Integration with SSH Connection Manager complete

❌ **Phase 2 Needed:** Packet Routing
- Native tun2socks library required
- Current Kotlin implementation is incomplete

## The Problem

Our pure Kotlin `Tun2SocksEngine` can:
1. ✅ Read IP packets from TUN interface
2. ✅ Parse packet headers (IP, TCP, UDP)
3. ✅ Connect to SOCKS5 proxy
4. ❌ **Cannot construct and write response packets back to TUN**

This is why you see "No Internet" - packets are captured but not routed back.

## The Solution

Integrate a native tun2socks library that provides a complete TCP/IP stack.

## Recommended Library: hev-socks5-tunnel

**Why this one:**
- Lightweight (~2MB)
- Specifically designed for SOCKS5
- Active development
- Easy Android integration
- Good performance

**Download:**
```
https://github.com/heiher/hev-socks5-tunnel/releases
```

## Integration Steps

### Step 1: Download the Library

1. Visit: https://github.com/heiher/hev-socks5-tunnel/releases/latest
2. Download `hev-socks5-tunnel-<version>.aar`
3. Place in `androidApp/libs/` directory

### Step 2: Update Tun2SocksEngine

Replace the current Kotlin implementation with JNI calls to the native library:

```kotlin
class Tun2SocksEngine(
    private val tunFd: Int,
    private val socksAddress: String
) {
    
    companion object {
        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }
    
    external fun start(tunFd: Int, socksAddr: String): Int
    external fun stop(): Int
    external fun isRunning(): Boolean
    
    fun start(): Result<Unit> {
        val result = start(tunFd, socksAddress)
        return if (result == 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to start tun2socks: $result"))
        }
    }
    
    fun stop(): Result<Unit> {
        stop()
        return Result.success(Unit)
    }
}
```

### Step 3: Update TunnelVpnService

```kotlin
private fun startVpn(serverAddress: String) {
    serviceScope.launch {
        // ... existing code ...
        
        // Start tun2socks with file descriptor
        val tunFd = vpnInterface!!.fd
        val socksAddress = "127.0.0.1:$socksPort"
        
        tun2socks = Tun2SocksEngine(tunFd, socksAddress)
        val result = tun2socks!!.start()
        
        // ... rest of code ...
    }
}
```

### Step 4: Build and Test

```bash
.\gradlew.bat androidApp:assembleDebug
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

## Alternative Libraries

### Option 2: AndroidLibV2rayLite
- **Pros:** Includes full V2Ray functionality
- **Cons:** Larger size (~20MB), more complex
- **Download:** https://github.com/2dust/AndroidLibV2rayLite/releases

### Option 3: Build xjasonlyu/tun2socks
- **Pros:** Modern, well-maintained
- **Cons:** Requires Go and gomobile to build
- **Repo:** https://github.com/xjasonlyu/tun2socks

## Testing After Integration

1. **Connect to SSH server**
2. **VPN should start automatically**
3. **Open browser and navigate to a website**
4. **Check external IP:** https://ifconfig.me
   - Should show your SSH server's IP
5. **Check for DNS leaks:** https://dnsleaktest.com
   - Should show DNS through tunnel

## Expected Behavior

**Before tun2socks:**
- ❌ "No Internet" error
- ❌ "Site cannot be reached"
- ✅ VPN key icon appears
- ✅ SSH connection works

**After tun2socks:**
- ✅ Internet works through VPN
- ✅ All traffic routed through SSH tunnel
- ✅ DNS queries through tunnel
- ✅ External IP shows SSH server

## Troubleshooting

### Library Not Loading
```
Error: java.lang.UnsatisfiedLinkError: dlopen failed
```
**Solution:** Ensure AAR file is in `androidApp/libs/` and contains native libraries for your device architecture.

### SOCKS5 Connection Failed
```
Error: Failed to connect to SOCKS5 proxy
```
**Solution:** Verify SSH connection is active and SOCKS5 port is correct.

### Still No Internet
```
VPN active but no internet
```
**Solution:** Check logs for tun2socks errors:
```bash
adb logcat | grep -i tun2socks
```

## Performance Expectations

**With native tun2socks:**
- Latency: +20-50ms (due to SSH tunnel)
- Throughput: 10-50 Mbps (depends on SSH server)
- Battery: +2-5% per hour
- Memory: +15-30 MB

## Next Steps

1. Download hev-socks5-tunnel AAR
2. Place in `androidApp/libs/`
3. Update Tun2SocksEngine with JNI bindings
4. Test and verify traffic routing

## Questions?

- Check logs: `adb logcat | grep -i "tun2socks\|vpn"`
- Verify SOCKS5: `adb logcat | grep -i socks`
- Check SSH: `adb logcat | grep -i ssh`
