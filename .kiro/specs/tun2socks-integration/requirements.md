# Requirements: tun2socks Integration

## Overview

Replace the manual packet routing implementation with the tun2socks library to provide reliable VPN traffic routing through SOCKS5 proxy.

## Problem Statement

The current manual packet routing implementation in PacketRouter is incomplete and doesn't actually route traffic. It lacks:
- Full TCP/IP stack implementation
- Proper packet construction with sequence numbers and checksums
- TCP connection state management
- Reliable packet forwarding

This results in "No Internet" when the VPN is active, even though the VPN interface is created successfully.

## Solution

Integrate the tun2socks library (specifically `xjasonlyu/tun2socks` or `ambrop72/badvpn`) which provides:
- Complete TUN interface to SOCKS5 proxy routing
- Full TCP/IP stack implementation
- UDP support (including DNS)
- Proven, battle-tested code
- Active maintenance and community support

## Requirements

### 1. Library Integration

**1.1** Add tun2socks library dependency to the Android project
- Use Go-based tun2socks compiled as Android native library (.so files)
- Support all Android architectures (arm64-v8a, armeabi-v7a, x86, x86_64)
- Include JNI bindings for Java/Kotlin integration

**1.2** Create Kotlin wrapper for tun2socks native library
- Provide clean Kotlin API for starting/stopping tun2socks
- Handle native library loading and initialization
- Manage lifecycle and error handling

### 2. VPN Service Integration

**2.1** Replace PacketRouter with tun2socks integration
- Remove manual packet routing code
- Use tun2socks to handle all packet forwarding
- Pass TUN file descriptor to tun2socks

**2.2** Configure tun2socks with SOCKS5 proxy settings
- Pass SOCKS5 proxy address (127.0.0.1:port)
- Configure DNS routing through SOCKS5
- Set appropriate MTU and buffer sizes

**2.3** Handle tun2socks lifecycle
- Start tun2socks when VPN service starts
- Stop tun2socks when VPN service stops
- Handle tun2socks errors and crashes

### 3. Error Handling

**3.1** Detect tun2socks initialization failures
- Report errors if native library fails to load
- Handle missing architecture support
- Provide meaningful error messages

**3.2** Handle runtime errors
- Detect when tun2socks process crashes
- Attempt restart on recoverable errors
- Disconnect VPN on unrecoverable errors

**3.3** Monitor tun2socks health
- Periodically check if tun2socks is running
- Detect hung or frozen tun2socks process
- Restart if necessary

### 4. Performance

**4.1** Optimize buffer sizes
- Configure appropriate buffer sizes for TUN interface
- Tune tun2socks parameters for mobile networks
- Balance memory usage and throughput

**4.2** Minimize battery impact
- Ensure tun2socks doesn't cause excessive CPU usage
- Monitor and log performance metrics
- Optimize for mobile device constraints

### 5. Compatibility

**5.1** Support all Android versions
- Minimum API level 21 (Android 5.0)
- Test on various Android versions
- Handle API differences gracefully

**5.2** Support all architectures
- arm64-v8a (64-bit ARM)
- armeabi-v7a (32-bit ARM)
- x86 (32-bit Intel)
- x86_64 (64-bit Intel)

### 6. Testing

**6.1** Verify traffic routing
- Test HTTP/HTTPS traffic through VPN
- Test DNS resolution through tunnel
- Verify no DNS leaks

**6.2** Test error scenarios
- Test behavior when SOCKS proxy is unavailable
- Test VPN restart scenarios
- Test network switching (WiFi ↔ mobile data)

**6.3** Performance testing
- Measure throughput with tun2socks
- Monitor battery usage
- Check memory consumption

## Success Criteria

1. ✅ VPN successfully routes all traffic through SOCKS5 proxy
2. ✅ Web browsing works through VPN
3. ✅ DNS queries are routed through tunnel (no leaks)
4. ✅ No "No Internet" message when VPN is active
5. ✅ Stable operation without crashes
6. ✅ Acceptable performance (latency, throughput)
7. ✅ Reasonable battery consumption

## Out of Scope

- Custom packet filtering or inspection
- Split tunneling based on packet content
- Traffic shaping or QoS
- IPv6 support (can be added later)

## Dependencies

- Existing VPN service infrastructure (TunnelVpnService)
- Existing SSH connection and SOCKS5 proxy
- Android NDK for native library integration

## Risks

1. **Native library complexity** - JNI integration can be tricky
   - Mitigation: Use well-documented tun2socks implementations with existing Android bindings

2. **Architecture support** - Need to provide .so files for all architectures
   - Mitigation: Use pre-built binaries or automated build process

3. **Library maintenance** - Dependency on external library
   - Mitigation: Choose actively maintained library with good community support

4. **APK size increase** - Native libraries will increase APK size
   - Mitigation: Use Android App Bundle to deliver architecture-specific APKs

## References

- [xjasonlyu/tun2socks](https://github.com/xjasonlyu/tun2socks) - Go-based implementation
- [ambrop72/badvpn](https://github.com/ambrop72/badvpn) - C-based implementation
- [Android VpnService Documentation](https://developer.android.com/reference/android/net/VpnService)
- [Android NDK Guide](https://developer.android.com/ndk/guides)
