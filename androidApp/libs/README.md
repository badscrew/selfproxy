# Native Libraries Directory

This directory contains native libraries (.aar files) for tun2socks integration.

## Quick Start: Download Required Library

**EASIEST METHOD - Use HEV-SOCKS5-TUNNEL (Recommended)**

This is a lightweight, pure tun2socks implementation specifically for Android:

1. **Download the library:**
   ```
   https://github.com/heiher/hev-socks5-tunnel/releases
   ```
   - Look for the latest release
   - Download `hev-socks5-tunnel-<version>.aar`
   - Place it in this `androidApp/libs/` directory

2. **Alternative: Direct download link (if available):**
   - Visit: https://github.com/heiher/hev-socks5-tunnel/releases/latest
   - Download the AAR file
   - Save to: `androidApp/libs/hev-socks5-tunnel.aar`

## Other Options (More Complex)

### Option 2: AndroidLibV2rayLite
1. Download from: https://github.com/2dust/AndroidLibV2rayLite/releases
2. Look for `libv2ray.aar` (includes tun2socks)
3. Place in this directory
4. Note: Larger file size (~20MB)

### Option 3: Build from Source
1. Clone: https://github.com/xjasonlyu/tun2socks
2. Install Go and gomobile
3. Run: `gomobile bind -target=android`
4. Copy generated AAR to this directory

## Current Status

⚠️ **Library not yet added** - The app currently uses a simplified Kotlin implementation that doesn't fully work.

**What works now:**
- ✅ VPN interface creation
- ✅ SSH connection and SOCKS5 proxy
- ✅ Packet capture from TUN interface
- ❌ Packet routing (needs native library)
- ❌ DNS resolution (needs native library)

**After adding the library:**
- ✅ Full packet routing through SOCKS5
- ✅ DNS resolution through tunnel
- ✅ All traffic routed correctly

## Integration Steps (After Download)

Once you have the AAR file in this directory:

1. The build.gradle.kts already includes: `implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))`
2. We'll update Tun2SocksEngine to use the native library
3. Rebuild and test

## Why We Need This

The current pure Kotlin implementation can:
- Read packets from TUN interface ✅
- Parse IP/TCP/UDP headers ✅
- Connect to SOCKS5 proxy ✅
- **Cannot write responses back to TUN** ❌ (This is the problem!)

A native tun2socks library provides:
- Complete TCP/IP stack implementation
- Proper packet construction with checksums
- TCP state machine
- UDP support
- DNS handling
- Battle-tested, production-ready code
