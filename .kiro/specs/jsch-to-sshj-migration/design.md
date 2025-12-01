# Design Document: JSch to sshj Migration

## Overview

This design document outlines the migration from JSch to sshj for SSH connectivity in the SSH Tunnel Proxy application. The migration addresses the critical issue where JSch's SOCKS5 proxy implementation is broken, preventing any traffic from flowing through the SSH tunnel.

The migration will replace the `AndroidSSHClient` implementation while maintaining the existing `SSHClient` interface, ensuring minimal impact on dependent components like `SSHConnectionManager`, `VpnController`, and the UI layer.

## Architecture

### Current Architecture (JSch)

```
┌─────────────────────────────────────────┐
│     SSHConnectionManager (shared)       │
│  - Connection lifecycle management      │
│  - State observation                    │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│    AndroidSSHClient (androidMain)       │
│  - Uses JSch library                    │
│  - Broken SOCKS5 proxy ❌               │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         JSch Library                    │
│  - setPortForwardingL() broken          │
│  - Connection reset on handshake        │
└─────────────────────────────────────────┘
```

### Target Architecture (sshj)

```
┌─────────────────────────────────────────┐
│     SSHConnectionManager (shared)       │
│  - Connection lifecycle management      │
│  - State observation (unchanged)        │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│    AndroidSSHClient (androidMain)       │
│  - Uses sshj library                    │
│  - Working SOCKS5 proxy ✅              │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         sshj Library                    │
│  - LocalPortForwarder.forward()         │
│  - Full SOCKS5 protocol support         │
└─────────────────────────────────────────┘
```

## Components and Interfaces

### SSHClient Interface (No Changes)

The existing `SSHClient` interface in `shared/src/commonMain/kotlin/com/sshtunnel/ssh/SSHClient.kt` will remain unchanged:

```kotlin
interface SSHClient {
    suspend fun connect(
        profile: ServerProfile,
        privateKey: PrivateKey,
        passphrase: String?,
        connectionTimeout: Duration,
        enableCompression: Boolean,
        strictHostKeyChecking: Boolean
    ): Result<SSHSession>
    
    suspend fun createPortForwarding(
        session: SSHSession,
        localPort: Int
    ): Result<Int>
    
    suspend fun sendKeepAlive(session: SSHSession): Result<Unit>
    
    suspend fun disconnect(session: SSHSession): Result<Unit>
    
    fun isConnected(session: SSHSession): Boolean
}
```

### AndroidSSHClient Implementation (Complete Rewrite)

The `AndroidSSHClient` will be rewritten to use sshj:

**Key Changes:**
- Replace `JSch` with `SSHClient` (sshj)
- Replace `Session` (JSch) with `SSHClient` (sshj) in native session
- Replace `setPortForwardingL()` with `LocalPortForwarder.forward()`
- Update exception mapping from JSch exceptions to sshj exceptions
- Maintain the same error categorization and user-friendly messages

### SSHSession Data Class (Minor Changes)

The `SSHSession` wrapper will need to store sshj's `SSHClient` instead of JSch's `Session`:

```kotlin
data class SSHSession(
    val sessionId: String,
    val serverAddress: String,
    val serverPort: Int,
    val username: String,
    val socksPort: Int,
    val nativeSession: Any // Will hold sshj's SSHClient instead of JSch Session
)
```

## Data Models

No changes to data models. All existing models remain the same:
- `ServerProfile`
- `Connection`
- `ConnectionState`
- `ConnectionError`
- `PrivateKey`

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Valid credentials establish connections

*For any* valid SSH credentials (hostname, port, username, private key), connecting to an SSH server should succeed and return a connected session.

**Validates: Requirements 2.1**

### Property 2: All key types are supported

*For any* SSH server configured with RSA, ECDSA, or Ed25519 keys, the system should successfully authenticate and establish a connection.

**Validates: Requirements 2.2**

### Property 3: Sessions persist during connection

*For any* established SSH session, the session should remain active and connected for the duration of the connection without timing out.

**Validates: Requirements 2.3, 7.2**

### Property 4: SOCKS5 proxy creation

*For any* established SSH connection, creating port forwarding should result in a SOCKS5 proxy bound to localhost (127.0.0.1) on a dynamically assigned port.

**Validates: Requirements 3.1, 3.2**

### Property 5: SOCKS5 connections are accepted

*For any* SOCKS5 proxy created by sshj, connecting to the proxy should accept the connection without resetting it.

**Validates: Requirements 3.3**

### Property 6: SOCKS5 handshake compliance

*For any* SOCKS5 proxy, sending a SOCKS5 greeting (version 5, 1 method) should receive a valid response (version 5, method 0).

**Validates: Requirements 3.4, 4.1**

### Property 7: CONNECT requests succeed

*For any* valid target host and port, sending a SOCKS5 CONNECT request through the proxy should establish a connection through the SSH tunnel.

**Validates: Requirements 3.5, 4.2**

### Property 8: Bidirectional data relay

*For any* established SOCKS5 connection, data sent from the client should be relayed to the target, and data from the target should be relayed back to the client.

**Validates: Requirements 4.3**

### Property 9: Concurrent connections

*For any* number of simultaneous SOCKS5 connections, the proxy should handle all connections without resetting or failing.

**Validates: Requirements 4.5**

### Property 10: Clean disconnection

*For any* established SSH session with SOCKS5 proxy, disconnecting should cleanly close the SOCKS5 proxy first, then close the SSH session, releasing all resources.

**Validates: Requirements 5.4, 7.4**

### Property 11: Exception mapping

*For any* sshj exception thrown during SSH operations, the system should map it to an appropriate ConnectionError type with a user-friendly message.

**Validates: Requirements 6.1**

### Property 12: Keep-alive packets

*For any* established SSH connection, the system should send keep-alive packets at regular intervals (approximately every 60 seconds) to maintain the session.

**Validates: Requirements 7.1**

### Property 13: Strong encryption

*For any* SSH connection, the system should negotiate and use strong encryption algorithms (AES-256, ChaCha20) and reject weak algorithms.

**Validates: Requirements 10.1**

### Property 14: Key-only authentication

*For any* SSH connection attempt, the system should use only private key authentication and never attempt password authentication.

**Validates: Requirements 10.2**

### Property 15: Host key verification

*For any* SSH connection with strict host key checking enabled, the system should verify the server's host key before completing the connection.

**Validates: Requirements 10.5**


## Error Handling

### Exception Mapping Strategy

The migration will maintain the existing `ConnectionError` sealed class hierarchy while mapping sshj exceptions to appropriate error types:

```kotlin
sealed class ConnectionError {
    data class AuthenticationFailed(val reason: String) : ConnectionError()
    data class HostUnreachable(val host: String) : ConnectionError()
    data class NetworkUnavailable(val message: String) : ConnectionError()
    data class PortForwardingDisabled(val message: String) : ConnectionError()
    data class Timeout(val operation: String) : ConnectionError()
    data class Unknown(val message: String, val cause: Throwable?) : ConnectionError()
}
```

### sshj Exception Mapping

| sshj Exception | ConnectionError Type | User Message |
|----------------|---------------------|--------------|
| `UserAuthException` | `AuthenticationFailed` | "Authentication failed: [reason]" |
| `TransportException` (connection refused) | `HostUnreachable` | "Cannot reach server: [host]" |
| `TransportException` (network error) | `NetworkUnavailable` | "Network error: [details]" |
| `ConnectionException` (timeout) | `Timeout` | "Connection timed out" |
| `SSHException` (port forwarding) | `PortForwardingDisabled` | "Port forwarding failed: [reason]" |
| Other exceptions | `Unknown` | "Unexpected error: [message]" |

### Error Recovery

- **Connection Failures**: Retry with exponential backoff (1s, 2s, 4s, 8s)
- **Authentication Failures**: Do not retry (user intervention required)
- **Network Errors**: Wait for network availability before retrying
- **Port Forwarding Failures**: Log detailed error and suggest server configuration check

## Testing Strategy

### Unit Testing

Unit tests will verify specific functionality of the SSH client:

**Connection Tests:**
- Test successful connection with valid credentials
- Test connection failure with invalid hostname
- Test authentication failure with wrong key
- Test connection timeout handling

**Port Forwarding Tests:**
- Test SOCKS5 proxy creation
- Test proxy binds to localhost only
- Test dynamic port assignment
- Test proxy cleanup on disconnect

**Error Handling Tests:**
- Test exception mapping for each sshj exception type
- Test error messages are user-friendly
- Test error recovery logic

### Property-Based Testing

Property-based tests will verify universal properties across many inputs using Kotest:

**Testing Framework**: Kotest (already in use)
**Minimum Iterations**: 100 per property test
**Tagging Format**: `// Feature: jsch-to-sshj-migration, Property X: [property text]`

**Key Property Tests:**

1. **Connection Establishment** (Property 1, 2, 3)
   - Generate random valid credentials
   - Verify connection succeeds
   - Verify session persists

2. **SOCKS5 Protocol** (Property 4-9)
   - Generate random SOCKS5 requests
   - Verify handshake succeeds
   - Verify CONNECT works
   - Verify data relay works
   - Test concurrent connections

3. **Lifecycle Management** (Property 10, 12)
   - Verify clean disconnection
   - Verify keep-alive packets sent

4. **Security** (Property 13, 14, 15)
   - Verify strong encryption used
   - Verify no password auth
   - Verify host key checking

### Integration Testing

Integration tests will verify end-to-end functionality:

**SOCKS5 Test Screen:**
- Test 1: Connect to SOCKS5 port ✅
- Test 2: SOCKS5 handshake ✅
- Test 3: CONNECT request ✅
- Test 4: HTTP request through tunnel ✅

**VPN Integration:**
- Connect SSH → Create SOCKS5 → Start VPN → Browse web
- Verify traffic flows through tunnel
- Verify DNS resolution works
- Verify connection survives network changes

### Test Generators

Custom Kotest generators for domain types:

```kotlin
fun Arb.Companion.serverProfile() = arbitrary {
    ServerProfile(
        id = Arb.long(1..1000).bind(),
        name = Arb.string(5..20).bind(),
        hostname = Arb.domain().bind(),
        port = Arb.int(22..22).bind(),
        username = Arb.string(3..16, Codepoint.alphanumeric()).bind(),
        keyType = Arb.enum<KeyType>().bind()
    )
}

fun Arb.Companion.socks5Greeting() = arbitrary {
    byteArrayOf(0x05, 0x01, 0x00) // Version 5, 1 method, no auth
}

fun Arb.Companion.socks5ConnectRequest() = arbitrary {
    val host = Arb.domain().bind()
    val port = Arb.int(1..65535).bind()
    // Build SOCKS5 CONNECT request bytes
}
```

## Implementation Details

### Dependency Changes

**Remove from `shared/build.gradle.kts`:**
```kotlin
api("com.github.mwiede:jsch:0.2.16")
```

**Add to `shared/build.gradle.kts`:**
```kotlin
api("com.hierynomus:sshj:0.38.0")
api("org.bouncycastle:bcprov-jdk18on:1.77")
api("org.bouncycastle:bcpkix-jdk18on:1.77")
```

### Key API Differences

#### Connection Establishment

**JSch (old):**
```kotlin
val jsch = JSch()
jsch.addIdentity(keyPath, passphrase)
val session = jsch.getSession(username, hostname, port)
session.setConfig("StrictHostKeyChecking", if (strict) "yes" else "no")
session.connect(timeout)
```

**sshj (new):**
```kotlin
val ssh = SSHClient()
ssh.addHostKeyVerifier(if (strict) PromiscuousVerifier() else AcceptAllHostKeyVerifier())
ssh.connect(hostname, port)
ssh.authPublickey(username, keyProvider)
```

#### SOCKS5 Proxy Creation

**JSch (old - broken):**
```kotlin
val port = session.setPortForwardingL(0, "localhost", 0)
// Creates listening socket but SOCKS5 protocol is broken
```

**sshj (new - working):**
```kotlin
val forwarder = ssh.newLocalPortForwarder(
    LocalPortForwarder.Parameters("127.0.0.1", 0, "127.0.0.1", 0),
    ServerSocket(0)
)
val port = forwarder.listen().localPort
```

#### Keep-Alive

**JSch (old):**
```kotlin
session.setServerAliveInterval(60000)
session.setServerAliveCountMax(3)
```

**sshj (new):**
```kotlin
ssh.connection.keepAlive.keepAliveInterval = 60
```

#### Disconnection

**JSch (old):**
```kotlin
session.delPortForwardingL(port)
session.disconnect()
```

**sshj (new):**
```kotlin
forwarder.close()
ssh.disconnect()
```

### Private Key Loading

sshj requires BouncyCastle for key parsing:

```kotlin
private fun loadPrivateKey(keyData: ByteArray, passphrase: String?): KeyProvider {
    val keyProvider = if (passphrase != null) {
        ssh.loadKeys(String(keyData), null, PasswordUtils.createOneOff(passphrase.toCharArray()))
    } else {
        ssh.loadKeys(String(keyData), null, null)
    }
    return keyProvider
}
```

### Host Key Verification

```kotlin
private fun createHostKeyVerifier(strictHostKeyChecking: Boolean): HostKeyVerifier {
    return if (strictHostKeyChecking) {
        // Use known_hosts file
        OpenSSHKnownHosts(File(knownHostsPath))
    } else {
        // Accept all (for testing)
        AcceptAllHostKeyVerifier()
    }
}
```

## Migration Steps

### Phase 1: Dependency Update (30 minutes)

1. Update `shared/build.gradle.kts` with sshj dependencies
2. Remove JSch dependency
3. Sync Gradle and verify build succeeds
4. Resolve any dependency conflicts

### Phase 2: Implementation (2-3 hours)

1. **Rewrite AndroidSSHClient.connect()**
   - Replace JSch connection logic with sshj
   - Update exception handling
   - Test connection establishment

2. **Rewrite AndroidSSHClient.createPortForwarding()**
   - Replace JSch port forwarding with sshj LocalPortForwarder
   - Verify SOCKS5 proxy creation
   - Test with SOCKS5 test screen

3. **Update AndroidSSHClient.sendKeepAlive()**
   - Replace JSch keep-alive with sshj
   - Verify keep-alive packets sent

4. **Update AndroidSSHClient.disconnect()**
   - Replace JSch disconnect with sshj
   - Ensure proper cleanup order

5. **Update exception mapping**
   - Map sshj exceptions to ConnectionError types
   - Verify error messages are user-friendly

### Phase 3: Testing (1 hour)

1. **Run unit tests**
   - Verify all existing tests pass
   - Add new tests for sshj-specific behavior

2. **Run SOCKS5 test screen**
   - Verify all 4 tests pass
   - Confirm no connection resets

3. **Run property-based tests**
   - Verify all properties hold
   - Check for edge cases

4. **Integration testing**
   - Connect VPN and browse web
   - Verify traffic flows correctly
   - Test network change handling

### Phase 4: Cleanup (30 minutes)

1. Remove all JSch imports
2. Update documentation
3. Rename test screen from `JSchSocksTestScreen` to `SocksTestScreen`
4. Update comments and logging

## Rollback Plan

If the migration fails:

1. **Revert Dependencies**: Restore JSch in `shared/build.gradle.kts`
2. **Revert Code**: `git revert` to pre-migration commit
3. **Document Issues**: Update `CURRENT_STATUS.md` with findings
4. **Alternative Approach**: Consider server-side SOCKS5 proxy or different SSH library

## Performance Considerations

### Expected Performance

- **Connection Time**: Similar to JSch (2-5 seconds)
- **Throughput**: Should match or exceed JSch
- **Memory Usage**: Slightly higher due to BouncyCastle
- **CPU Usage**: Similar to JSch
- **Battery Impact**: No significant change expected

### Optimization Opportunities

- **Connection Pooling**: Reuse SSH connections when possible
- **Compression**: Enable SSH compression for slow networks
- **Buffer Sizes**: Tune buffer sizes for optimal throughput
- **Keep-Alive Tuning**: Adjust interval based on network conditions

## Security Considerations

### Improvements Over JSch

- **Modern Algorithms**: sshj supports newer encryption algorithms
- **Better Key Support**: Native Ed25519 support
- **Active Maintenance**: sshj is actively maintained (JSch is not)
- **Security Audits**: sshj has undergone security reviews

### Security Checklist

- ✅ Use strong encryption algorithms (AES-256, ChaCha20)
- ✅ Verify host keys when strict checking enabled
- ✅ Use only private key authentication
- ✅ Bind SOCKS5 proxy to localhost only
- ✅ Clear sensitive data from memory after use
- ✅ Log errors without exposing credentials

## Success Metrics

The migration will be considered successful when:

1. ✅ All JSch code removed
2. ✅ Project builds without JSch dependencies
3. ✅ All unit tests pass
4. ✅ All property-based tests pass
5. ✅ SOCKS5 test screen passes all 4 tests
6. ✅ VPN connects and web browsing works
7. ✅ No regressions in connection stability
8. ✅ Error handling maintains same quality
9. ✅ Performance is equivalent or better
10. ✅ No security regressions

## Timeline

- **Phase 1 (Dependencies)**: 30 minutes
- **Phase 2 (Implementation)**: 2-3 hours
- **Phase 3 (Testing)**: 1 hour
- **Phase 4 (Cleanup)**: 30 minutes

**Total Estimated Time**: 4-5 hours

## References

- [sshj Documentation](https://github.com/hierynomus/sshj)
- [sshj API Reference](https://www.javadoc.io/doc/com.hierynomus/sshj/latest/index.html)
- [SOCKS5 Protocol RFC 1928](https://www.rfc-editor.org/rfc/rfc1928)
- [SSH Protocol RFC 4254](https://www.rfc-editor.org/rfc/rfc4254)
- [BouncyCastle Documentation](https://www.bouncycastle.org/documentation.html)
