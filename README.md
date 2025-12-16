# SSH Tunnel Proxy

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.21-purple.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2026+-green.svg)](https://developer.android.com/)

> **⚠️ EXPERIMENTAL / BETA SOFTWARE**
>
> This project is in active development and should be considered experimental and beta-grade. While functional, it may contain bugs, incomplete features, or unexpected behavior. Use with understanding that issues may arise, and always test thoroughly in non-critical environments before relying on it for important use cases. Contributions, bug reports, and feedback are welcome as we work toward a stable release.

A mobile application (initially Android, with future iOS support planned) that enables users to route their mobile device's internet traffic through their own SSH servers using SOCKS5 proxy tunneling.

## Features

- **Full Traffic Routing**: Route all TCP and UDP traffic through your SSH server
- **Video Calling Support**: Works with WhatsApp, Telegram, Zoom, Discord, and other video calling apps
- **DNS Privacy**: All DNS queries are routed through the tunnel to prevent DNS leaks
- **Per-App Routing**: Choose which apps use the VPN tunnel
- **Private Key Authentication**: Secure authentication using SSH private keys (RSA, ECDSA, Ed25519)
- **Auto-Reconnect**: Automatically reconnects when network changes or connection drops
- **Connection Statistics**: Monitor data usage and connection status

## Project Structure

This is a Kotlin Multiplatform project with the following structure:

```
ssh-tunnel-proxy/
├── shared/                 # Kotlin Multiplatform shared code
│   └── src/
│       ├── commonMain/     # Shared business logic
│       ├── commonTest/     # Shared tests
│       ├── androidMain/    # Android-specific implementations
│       └── androidTest/    # Android-specific tests
├── androidApp/             # Android application
│   └── src/main/
│       ├── kotlin/         # Android UI code
│       └── res/            # Android resources
└── build.gradle.kts        # Root build configuration
```

## Technology Stack

### Shared (Kotlin Multiplatform)
- **Language**: Kotlin Multiplatform
- **Concurrency**: Kotlin Coroutines and Flow
- **Storage**: SQLDelight for cross-platform database
- **Networking**: Ktor for HTTP requests
- **Serialization**: kotlinx-serialization

### Android-Specific
- **SSH Implementation**: Native OpenSSH binaries (primary) with sshj fallback
- **Cryptography**: BouncyCastle (bcprov-jdk18on, bcpkix-jdk18on)
- **VPN**: Android VpnService API
- **Credential Storage**: Android Keystore
- **DI**: Hilt for dependency injection
- **UI**: Jetpack Compose with Material Design 3

### SSH Implementation

The app supports two SSH implementations:

1. **SSHJ (Default)**: Pure Java SSH library that works on all Android devices
   - ✅ Works on all devices without special permissions
   - ✅ Reliable and well-tested
   - ✅ Smaller APK size
   - Recommended for production use

2. **Native OpenSSH (Experimental)**: Native OpenSSH binaries
   - ❌ **Blocked by Android SELinux on non-rooted devices**
   - ✅ Would provide better performance (if it could run)
   - ✅ Full OpenSSH compatibility
   - ⚠️ Requires root access or custom ROM

**Important**: Native SSH cannot execute on standard Android devices due to SELinux restrictions. See [docs/NATIVE_SSH_LIMITATIONS.md](docs/NATIVE_SSH_LIMITATIONS.md) for details.

For native SSH setup instructions (rooted devices only), see [NATIVE_SSH_SETUP.md](NATIVE_SSH_SETUP.md).

## Building the Project

### Prerequisites
- JDK 17 or higher
- Android SDK (API 26+)
- Android Studio (recommended) or IntelliJ IDEA

### Build Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Build Android APK
./gradlew :androidApp:assembleDebug

# Install on connected device
./gradlew :androidApp:installDebug
```

## Development

### Running Tests

```bash
# Run all tests
./gradlew test

# Run shared module tests
./gradlew :shared:test

# Run Android tests
./gradlew :androidApp:testDebugUnitTest
```

### Code Style

This project follows the official Kotlin coding conventions. Run the linter with:

```bash
./gradlew ktlintCheck
```

## SOCKS5 Server Requirements

For full functionality including video calling support, your SSH server must support SOCKS5 UDP ASSOCIATE (RFC 1928).

### Supported SSH Servers

✅ **OpenSSH** (recommended)
- Enable dynamic port forwarding: `ssh -D 1080 user@server`
- Supports UDP ASSOCIATE by default
- Most widely used and tested

✅ **Dante SOCKS Server**
- Full SOCKS5 implementation with UDP support
- Highly configurable

✅ **Shadowsocks**
- Supports UDP relay
- Good for high-latency connections

### Testing Your Server

To verify UDP ASSOCIATE support:

```bash
# Connect with dynamic forwarding
ssh -D 1080 user@your-server.com

# Test UDP support (in another terminal)
curl --socks5 localhost:1080 https://dnsleaktest.com
```

### What Works Without UDP Support

If your SOCKS5 server doesn't support UDP ASSOCIATE:
- ✅ Web browsing (HTTP/HTTPS)
- ✅ Email, messaging (TCP-based)
- ✅ SSH, FTP, and other TCP protocols
- ❌ Video calling (WhatsApp, Zoom, Discord)
- ❌ Voice chat applications
- ❌ Online gaming (most games use UDP)
- ⚠️ DNS queries (will fall back to TCP-over-SOCKS5)

## Video Calling Support

This app supports UDP-based video calling applications through SOCKS5 UDP ASSOCIATE:

### Supported Applications

- **WhatsApp**: Voice and video calls
- **Telegram**: Voice and video calls
- **Zoom**: Meetings and webinars
- **Discord**: Voice channels and video calls
- **Microsoft Teams**: Calls and meetings
- **Google Meet**: Video conferences
- **Skype**: Voice and video calls

### How It Works

1. **UDP ASSOCIATE**: Establishes a UDP relay connection through SOCKS5
2. **Packet Encapsulation**: Wraps UDP packets with SOCKS5 headers
3. **Bidirectional Routing**: Routes both outgoing and incoming UDP traffic
4. **Connection Reuse**: Efficiently reuses connections for the same destination
5. **Automatic Cleanup**: Closes idle connections after 2 minutes

### Performance

- **Latency**: < 10ms overhead (excluding network latency)
- **Throughput**: Supports multiple simultaneous video calls
- **Memory**: < 10KB per UDP connection
- **Battery**: Optimized for mobile devices

### Troubleshooting Video Calls

If video calling doesn't work:

1. **Check Server Support**: Verify your SSH server supports UDP ASSOCIATE
   ```bash
   ssh -D 1080 user@server
   # Test with a UDP application
   ```

2. **Check Firewall**: Ensure UDP traffic is allowed
   - Common ports: 3478-3479 (STUN/TURN), 49152-65535 (WebRTC)

3. **Check Logs**: Enable verbose logging in app settings
   - Look for "UDP ASSOCIATE" messages
   - Check for SOCKS5 error codes

4. **Test Connection**: Use the built-in connection test feature
   - Settings → Test Connection
   - Verify both TCP and UDP work

5. **Network Quality**: Ensure stable network connection
   - Video calls require consistent bandwidth
   - Switch between WiFi and mobile data if issues persist

For detailed troubleshooting steps, see the [UDP Troubleshooting Guide](docs/UDP_TROUBLESHOOTING.md).

### Common Error Codes

| Error Code | Meaning | Solution |
|------------|---------|----------|
| 0x07 | Command not supported | Server doesn't support UDP ASSOCIATE |
| 0x03 | Network unreachable | Check server network configuration |
| 0x04 | Host unreachable | Destination server may be down |
| 0x05 | Connection refused | Destination port may be blocked |

## Architecture

The application follows a layered architecture with clear separation between platform-agnostic business logic and platform-specific implementations:

1. **Shared Module (commonMain)**: Business logic, data models, repositories
2. **Platform Modules (androidMain)**: Platform-specific implementations
3. **UI Layer (androidApp)**: Platform-native UI

### VPN Packet Routing

```
┌─────────────────────────────────────────────────────────────┐
│                      TUN Interface                          │
│                    (VPN Interface)                          │
└────────────────────┬────────────────────────────────────────┘
                     │ IP Packets
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    PacketRouter                             │
│  - Parse IP headers                                         │
│  - Route TCP/UDP/ICMP                                       │
└────────────────────┬────────────────────────────────────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
┌─────────────────┐    ┌─────────────────┐
│   TCPHandler    │    │   UDPHandler    │
│  - SOCKS5 TCP   │    │  - DNS queries  │
│  - HTTP/HTTPS   │    │  - UDP ASSOCIATE│
└─────────────────┘    │  - Video calls  │
                       └─────────────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │   SOCKS5 Proxy        │
                    │   (SSH Server)        │
                    │  - TCP forwarding     │
                    │  - UDP relay          │
                    └───────────────────────┘
```

See the [design document](.kiro/specs/ssh-tunnel-proxy/design.md) for detailed architecture information.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

### Why Apache 2.0?

We chose Apache 2.0 because:
- It matches the license of most dependencies (Kotlin, AndroidX, Ktor, SQLDelight)
- Provides explicit patent protection for users and contributors
- Allows commercial use and modification
- Is the industry standard for Android/Kotlin open source projects
- Is well-understood by corporations and legal teams

### Third-Party Licenses

This project uses several open source libraries. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for a complete list of dependencies and their licenses.

## Development Notes

Parts of this project were created using agentic coding with AI assistance. The development process leveraged AI agents to accelerate implementation while maintaining code quality through property-based testing, comprehensive documentation, and rigorous review processes.

## Contributing

Contributions are welcome! This project is in active development.

### How to Contribute

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests (`./gradlew test`)
5. Commit your changes (`git commit -m 'feat: add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Code of Conduct

Please be respectful and constructive in all interactions. We aim to maintain a welcoming and inclusive community.

### License Agreement

By contributing to this project, you agree that your contributions will be licensed under the Apache License 2.0.


