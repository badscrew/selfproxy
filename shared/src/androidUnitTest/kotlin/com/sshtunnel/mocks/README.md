# Test Mocks

This directory contains mock implementations for testing SSH Tunnel Proxy components without requiring real Android hardware or SSH servers.

## Available Mocks

### MockCredentialStore

Mock implementation of `CredentialStore` that stores credentials in memory without encryption.

**Usage:**
```kotlin
@Test
fun `test credential storage`() = runTest {
    val credentialStore = MockCredentialStore()
    
    // Store a key
    val result = credentialStore.storeKey(
        profileId = 1L,
        privateKey = testKeyData,
        passphrase = null
    )
    assertTrue(result.isSuccess)
    
    // Retrieve the key
    val retrieved = credentialStore.retrieveKey(1L, null)
    assertTrue(retrieved.isSuccess)
    
    // Clean up
    credentialStore.clear()
}
```

**Features:**
- In-memory storage (no Android Keystore required)
- Automatic key type detection
- Passphrase validation
- Thread-safe operations

**Methods:**
- `storeKey(profileId, privateKey, passphrase)` - Store a private key
- `retrieveKey(profileId, passphrase)` - Retrieve a stored key
- `deleteKey(profileId)` - Delete a stored key
- `clear()` - Clear all stored keys (useful for test cleanup)
- `size()` - Get the number of stored keys

### MockSSHClient

Mock implementation of `SSHClient` that simulates SSH connections without requiring a real SSH server.

**Usage:**
```kotlin
@Test
fun `test SSH connection error handling`() = runTest {
    val mockClient = MockSSHClient()
    
    // Simulate unknown host error
    mockClient.simulateUnknownHost = true
    
    val result = mockClient.connect(
        profile = testProfile,
        privateKey = testKey,
        passphrase = null,
        connectionTimeout = 5.seconds,
        enableCompression = false,
        strictHostKeyChecking = false
    )
    
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is SSHError.UnknownHost)
    
    // Reset for next test
    mockClient.reset()
}
```

**Features:**
- Simulates successful connections
- Configurable error scenarios
- Port forwarding simulation
- Session management
- Keep-alive support

**Configuration Properties:**
- `shouldFailConnection` - Make connections fail
- `connectionError` - Custom error to return on connection failure
- `shouldFailPortForwarding` - Make port forwarding fail
- `portForwardingError` - Custom error for port forwarding
- `simulateUnknownHost` - Simulate DNS resolution failure
- `simulateTimeout` - Simulate connection timeout
- `simulateHostUnreachable` - Simulate unreachable host

**Methods:**
- `connect()` - Simulate SSH connection
- `createPortForwarding()` - Simulate SOCKS5 proxy creation
- `sendKeepAlive()` - Simulate keep-alive packet
- `disconnect()` - Simulate disconnection
- `isConnected()` - Check connection status
- `reset()` - Reset all configuration to defaults
- `activeSessionCount()` - Get number of active sessions

## When to Use Mocks vs Real Implementations

### Use Mocks When:
- Testing business logic that doesn't depend on Android-specific behavior
- Testing error handling scenarios
- Running tests in CI/CD without Android emulator
- Testing edge cases that are hard to reproduce with real components
- Unit testing individual components in isolation

### Use Real Implementations When:
- Integration testing with actual Android components
- Testing Android Keystore encryption behavior
- Testing real SSH server connections
- End-to-end testing of the complete flow
- Performance testing

## Example Test Structure

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MyComponentTest {
    
    private lateinit var mockCredentialStore: MockCredentialStore
    private lateinit var mockSshClient: MockSSHClient
    
    @Before
    fun setup() {
        mockCredentialStore = MockCredentialStore()
        mockSshClient = MockSSHClient()
    }
    
    @After
    fun teardown() {
        mockCredentialStore.clear()
        mockSshClient.reset()
    }
    
    @Test
    fun `test my component`() = runTest {
        // Use mocks in your test
    }
}
```

## Best Practices

1. **Always clean up** - Call `clear()` or `reset()` in `@After` methods
2. **Configure before use** - Set mock behavior before calling methods
3. **Test one scenario at a time** - Reset mocks between tests
4. **Use descriptive test names** - Clearly indicate what scenario is being tested
5. **Verify mock state** - Use `size()` and `activeSessionCount()` to verify behavior

## Adding New Mocks

When adding new mock implementations:

1. Implement the interface completely
2. Add configuration properties for different scenarios
3. Include a `reset()` method to clear state
4. Add helper methods for test assertions
5. Document usage in this README
6. Add example tests demonstrating usage

## Related Documentation

- [Testing Strategy](../../../../../.kiro/steering/testing-strategy.md)
- [Kotlin Multiplatform Architecture](../../../../../.kiro/steering/kotlin-multiplatform-architecture.md)
