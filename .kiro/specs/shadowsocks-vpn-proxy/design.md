# Design Document

## Overview

This document describes the design for an Android VPN application that routes device traffic through a Shadowsocks proxy server. The application replaces the previous SSH-based approach with Shadowsocks, a lightweight and secure SOCKS5 proxy protocol specifically designed for circumventing network restrictions and protecting internet traffic.

The system consists of three main layers:
1. **Shared Business Logic** (Kotlin Multiplatform) - Profile management, connection state, data models
2. **Android Platform Layer** - Shadowsocks client implementation, VPN service, credential storage
3. **UI Layer** - Jetpack Compose interface for profile management and connection control

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Android UI Layer                     │
│  (Jetpack Compose - Profiles, Connection, Settings)     │
└────────────────┬────────────────────────────────────────┘
                 │
┌────────────────┴────────────────────────────────────────┐
│              Shared Business Logic Layer                 │
│  (KMP - ProfileRepository, ConnectionManager, Models)   │
└────────────────┬────────────────────────────────────────┘
                 │
┌────────────────┴────────────────────────────────────────┐
│              Android Platform Layer                      │
│                                                          │
│  ┌──────────────────┐  ┌─────────────────────────────┐ │
│  │ Shadowsocks      │  │    VPN Service              │ │
│  │ Client           │◄─┤  (TUN Interface)            │ │
│  │                  │  │                             │ │
│  │ - Protocol       │  │  - Packet Capture           │ │
│  │ - Encryption     │  │  - Packet Routing           │ │
│  │ - SOCKS5         │  │  - App Filtering            │ │
│  └──────────────────┘  └─────────────────────────────┘ │
│                                                          │
│  ┌──────────────────┐  ┌─────────────────────────────┐ │
│  │ Credential       │  │    Network Monitor          │ │
│  │ Store            │  │  (Connection Changes)       │ │
│  └──────────────────┘  └─────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### Component Interaction Flow

```
User Taps Connect
       │
       ▼
  UI Layer (Compose)
       │
       ▼
  ConnectionViewModel
       │
       ▼
  ConnectionManager (Shared)
       │
       ├──────────────────┐
       ▼                  ▼
ShadowsocksClient    VpnService
       │                  │
       │                  ▼
       │            TUN Interface
       │                  │
       │                  ▼
       │            Packet Router
       │                  │
       └──────────────────┤
                          ▼
                   SOCKS5 Proxy
                          │
                          ▼
                  Shadowsocks Server
```

## Components and Interfaces

### 1. Shadowsocks Client (Android Platform)

**Responsibility**: Implement Shadowsocks protocol, handle encryption, manage SOCKS5 proxy.

```kotlin
interface ShadowsocksClient {
    /**
     * Start Shadowsocks local proxy
     * @return Local SOCKS5 port on success
     */
    suspend fun start(config: ShadowsocksConfig): Result<Int>
    
    /**
     * Stop Shadowsocks proxy
     */
    suspend fun stop()
    
    /**
     * Test connection to server
     */
    suspend fun testConnection(config: ShadowsocksConfig): Result<ConnectionTestResult>
    
    /**
     * Observe connection state
     */
    fun observeState(): Flow<ShadowsocksState>
}

data class ShadowsocksConfig(
    val serverHost: String,
    val serverPort: Int,
    val password: String,
    val cipher: CipherMethod,
    val timeout: Duration = 5.seconds
)

enum class CipherMethod {
    AES_256_GCM,
    CHACHA20_IETF_POLY1305,
    AES_128_GCM
}

sealed class ShadowsocksState {
    object Idle : ShadowsocksState()
    object Starting : ShadowsocksState()
    data class Running(val localPort: Int) : ShadowsocksState()
    data class Error(val message: String, val cause: Throwable?) : ShadowsocksState()
}
```

**Implementation Approach**: Use shadowsocks-android library or shadowsocks-libev native binaries.

### 2. VPN Service (Android Platform)

**Responsibility**: Capture device traffic, route through Shadowsocks proxy, handle app filtering.

```kotlin
class ShadowsocksVpnService : VpnService() {
    private var tunInterface: ParcelFileDescriptor? = null
    private var socksPort: Int = 0
    
    suspend fun startTunnel(
        socksPort: Int,
        excludedApps: Set<String>
    ): Result<Unit>
    
    suspend fun stopTunnel()
    
    fun observeStatistics(): Flow<VpnStatistics>
}

data class VpnStatistics(
    val bytesSent: Long,
    val bytesReceived: Long,
    val uploadSpeed: Long, // bytes per second
    val downloadSpeed: Long,
    val connectedDuration: Duration
)
```

### 3. Connection Manager (Shared)

**Responsibility**: Coordinate Shadowsocks client and VPN service, manage connection lifecycle.

```kotlin
interface ConnectionManager {
    suspend fun connect(profile: ServerProfile): Result<Unit>
    suspend fun disconnect()
    suspend fun testConnection(profile: ServerProfile): Result<ConnectionTestResult>
    fun observeConnectionState(): Flow<ConnectionState>
    fun observeStatistics(): Flow<VpnStatistics>
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(
        val profileId: Long,
        val serverAddress: String,
        val connectedAt: Instant
    ) : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
    data class Error(val message: String, val cause: Throwable?) : ConnectionState()
}

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long?,
    val errorMessage: String?
)
```

### 4. Profile Repository (Shared)

**Responsibility**: Manage server profiles, persist configuration.

```kotlin
interface ProfileRepository {
    suspend fun createProfile(profile: ServerProfile): Result<Long>
    suspend fun getProfile(id: Long): ServerProfile?
    suspend fun getAllProfiles(): List<ServerProfile>
    suspend fun updateProfile(profile: ServerProfile): Result<Unit>
    suspend fun deleteProfile(id: Long): Result<Unit>
    suspend fun getLastUsedProfile(): ServerProfile?
}

@Serializable
data class ServerProfile(
    val id: Long = 0,
    val name: String,
    val serverHost: String,
    val serverPort: Int,
    val cipher: CipherMethod,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastUsed: Long? = null
)
```

### 5. Credential Store (Android Platform)

**Responsibility**: Securely store and retrieve Shadowsocks passwords.

```kotlin
interface CredentialStore {
    suspend fun storePassword(profileId: Long, password: String): Result<Unit>
    suspend fun retrievePassword(profileId: Long): Result<String>
    suspend fun deletePassword(profileId: Long): Result<Unit>
}
```

### 6. App Routing Repository (Shared)

**Responsibility**: Manage per-app VPN routing configuration.

```kotlin
interface AppRoutingRepository {
    suspend fun getExcludedApps(): Set<String>
    suspend fun setExcludedApps(packageNames: Set<String>): Result<Unit>
    suspend fun isAppExcluded(packageName: String): Boolean
}
```

### 7. Packet Router (Android Platform)

**Responsibility**: Route IP packets between TUN interface and SOCKS5 proxy.

```kotlin
class PacketRouter(
    private val tunInterface: ParcelFileDescriptor,
    private val socksPort: Int
) {
    suspend fun start()
    suspend fun stop()
    fun observeStatistics(): Flow<VpnStatistics>
}
```

## Data Models

### Database Schema (SQLDelight)

```sql
-- Server Profiles
CREATE TABLE server_profiles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    server_host TEXT NOT NULL,
    server_port INTEGER NOT NULL,
    cipher TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_used INTEGER
);

-- App Routing Configuration
CREATE TABLE app_routing_config (
    package_name TEXT PRIMARY KEY,
    excluded INTEGER NOT NULL DEFAULT 0
);

-- Connection Statistics (optional, for history)
CREATE TABLE connection_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_id INTEGER NOT NULL,
    connected_at INTEGER NOT NULL,
    disconnected_at INTEGER,
    bytes_sent INTEGER NOT NULL DEFAULT 0,
    bytes_received INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (profile_id) REFERENCES server_profiles(id) ON DELETE CASCADE
);
```

### Encrypted Storage

Passwords are stored separately using Android Keystore:

```
Encrypted Shared Preferences:
- Key: "password_<profile_id>"
- Value: Encrypted password string
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Profile CRUD operations maintain data integrity

*For any* server profile with valid fields (name, host, port, cipher), creating then retrieving the profile should return a profile with identical field values.
**Validates: Requirements 1.1, 1.2, 1.3**

### Property 2: Password encryption round-trip preserves data

*For any* profile ID and password string, storing then retrieving the password should return the original password unchanged.
**Validates: Requirements 2.1, 2.2**

### Property 3: Profile deletion removes all associated data

*For any* profile, after deletion, retrieving the profile should return null and retrieving its password should fail.
**Validates: Requirements 1.4, 2.3**

### Property 4: Connection state transitions are valid

*For any* sequence of connection operations, the state machine should only transition through valid states: Disconnected → Connecting → Connected or Disconnected → Connecting → Error → Disconnected.
**Validates: Requirements 3.1, 3.4, 3.5**

### Property 5: App routing configuration persists correctly

*For any* set of package names marked as excluded, after saving and retrieving the configuration, the excluded apps set should be identical.
**Validates: Requirements 5.3, 5.4, 5.5**

### Property 6: Statistics accumulation is monotonic

*For any* active VPN connection, bytes sent and bytes received should never decrease while the connection is active.
**Validates: Requirements 7.2, 7.3**

### Property 7: Reconnection backoff increases exponentially

*For any* sequence of failed connection attempts, the delay between attempts should follow exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s, 60s (capped).
**Validates: Requirements 6.2**

### Property 8: Cipher method validation rejects unsupported ciphers

*For any* cipher method string not in the supported set (aes-256-gcm, chacha20-ietf-poly1305, aes-128-gcm), attempting to create a profile should fail with a validation error.
**Validates: Requirements 9.1, 9.2, 9.3, 9.5**

### Property 9: VPN tunnel routes all traffic when active

*For any* active VPN connection without app exclusions, all network requests from the device should be routed through the Shadowsocks proxy.
**Validates: Requirements 4.1, 4.2, 4.3**

### Property 10: Excluded apps bypass VPN tunnel

*For any* app marked as excluded in routing configuration, network requests from that app should not be routed through the VPN tunnel.
**Validates: Requirements 5.2**

## Error Handling

### Connection Errors

| Error Type | Handling Strategy |
|------------|------------------|
| Server Unreachable | Retry with exponential backoff, notify user after 5 attempts |
| Authentication Failed | Stop retrying, show error to user, suggest checking password |
| Unsupported Cipher | Prevent connection, show error immediately |
| Network Changed | Automatically reconnect with new network |
| VPN Permission Revoked | Stop service, notify user, request permission again |

### Data Errors

| Error Type | Handling Strategy |
|------------|------------------|
| Profile Not Found | Return null, log warning |
| Invalid Profile Data | Reject with validation error, show specific field errors |
| Credential Decryption Failed | Show error, suggest re-entering password |
| Database Error | Retry operation once, then fail with error message |

### VPN Service Errors

| Error Type | Handling Strategy |
|------------|------------------|
| TUN Interface Creation Failed | Show error, check VPN permission |
| Packet Routing Error | Log error, attempt to recover, disconnect if persistent |
| SOCKS5 Connection Failed | Reconnect Shadowsocks client, retry packet |

## Testing Strategy

### Unit Testing

**Framework**: JUnit 5 + Kotlin Test + MockK

**Test Coverage**:
- Profile repository CRUD operations
- Credential encryption/decryption
- Connection state machine transitions
- App routing configuration
- Statistics calculation
- Cipher validation

**Example**:
```kotlin
class ProfileRepositoryTest {
    @Test
    fun `creating profile should persist all fields`() = runTest {
        val profile = ServerProfile(
            name = "Test Server",
            serverHost = "example.com",
            serverPort = 8388,
            cipher = CipherMethod.AES_256_GCM
        )
        
        val id = repository.createProfile(profile).getOrThrow()
        val retrieved = repository.getProfile(id)
        
        assertNotNull(retrieved)
        assertEquals(profile.name, retrieved.name)
        assertEquals(profile.serverHost, retrieved.serverHost)
    }
}
```

### Property-Based Testing

**Framework**: Kotest Property Testing

**Configuration**: Minimum 100 iterations per property test

**Test Tagging**: Each property test must include a comment with format:
```kotlin
// Feature: shadowsocks-vpn-proxy, Property 1: Profile CRUD operations maintain data integrity
// Validates: Requirements 1.1, 1.2, 1.3
```

**Custom Generators**:
```kotlin
fun Arb.Companion.serverProfile() = arbitrary {
    ServerProfile(
        id = 0,
        name = Arb.string(5..30).bind(),
        serverHost = Arb.domain().bind(),
        serverPort = Arb.int(1024..65535).bind(),
        cipher = Arb.enum<CipherMethod>().bind(),
        createdAt = Arb.long(0..System.currentTimeMillis()).bind()
    )
}

fun Arb.Companion.password() = arbitrary {
    Arb.string(8..64, Codepoint.alphanumeric()).bind()
}
```

**Property Tests**:
- Profile round-trip (create → retrieve)
- Password encryption round-trip (store → retrieve)
- Profile deletion completeness
- Connection state machine validity
- App routing persistence
- Statistics monotonicity
- Backoff calculation correctness
- Cipher validation

### Integration Testing

**Test Scenarios**:
1. End-to-end connection flow (profile → connect → VPN active)
2. Network change handling (WiFi → mobile data)
3. App exclusion functionality
4. Reconnection after connection loss
5. VPN service lifecycle (start → stop → restart)

**Test Environment**:
- Mock Shadowsocks server for testing
- Android emulator with network simulation
- Robolectric for Android-specific components

### UI Testing

**Framework**: Jetpack Compose Testing

**Test Coverage**:
- Profile creation flow
- Connection button interaction
- Statistics display updates
- Error message display
- App selection for routing

## Implementation Notes

### Shadowsocks Library Selection

**Option 1: shadowsocks-android library**
- Pros: Well-maintained, full protocol support, Kotlin-friendly
- Cons: Larger dependency size

**Option 2: shadowsocks-libev native binaries**
- Pros: Lightweight, battle-tested
- Cons: Requires JNI integration, more complex

**Recommendation**: Start with shadowsocks-android library for faster development, consider native binaries if size/performance becomes an issue.

### VPN Service Implementation

- Use `VpnService.Builder` to create TUN interface
- Route all traffic (0.0.0.0/0) through VPN
- Set DNS servers to prevent leaks
- Implement packet routing using NIO channels for performance
- Use coroutines for async packet processing

### Encryption

- All ciphers use AEAD mode (Authenticated Encryption with Associated Data)
- Password stored encrypted using Android Keystore
- Use hardware-backed encryption when available
- Clear sensitive data from memory after use

### Performance Considerations

- Use connection pooling for SOCKS5 connections
- Implement packet batching to reduce overhead
- Use direct ByteBuffers for packet processing
- Monitor memory usage and implement backpressure

### Battery Optimization

- Request battery optimization exemption for VPN service
- Implement efficient keep-alive mechanism
- Use WorkManager for reconnection scheduling
- Minimize wake locks

## Security Considerations

1. **Credential Protection**: All passwords encrypted with Android Keystore
2. **No Logging**: Never log passwords, encryption keys, or sensitive data
3. **DNS Leak Prevention**: All DNS queries routed through tunnel
4. **Certificate Validation**: Validate server certificates if using TLS
5. **Memory Safety**: Clear sensitive data from memory after use
6. **App Isolation**: Properly implement app exclusion to prevent routing loops

## Migration from SSH

### Data Migration

- Existing SSH profiles will not be migrated automatically
- Users will need to create new Shadowsocks profiles
- Provide in-app guidance for migration
- Consider showing a one-time migration notice

### Code Removal

- Remove all SSH-related code (JSch, native SSH client)
- Remove SSH-specific UI components
- Clean up SSH-related dependencies
- Archive SSH implementation in git history

### User Communication

- Update app description to mention Shadowsocks
- Provide migration guide in documentation
- Update screenshots and promotional materials
- Consider version bump to 2.0.0 to indicate major change

## Future Enhancements

1. **Plugin Support**: Support Shadowsocks plugins (v2ray-plugin, obfs)
2. **Multiple Servers**: Load balancing across multiple servers
3. **Traffic Rules**: Advanced routing rules based on domain/IP
4. **QR Code Import**: Import server config from QR codes
5. **Subscription Support**: Auto-update server lists from URLs
6. **IPv6 Support**: Full IPv6 routing support
7. **Split Tunneling**: More granular per-app routing options
