---
inclusion: always
---

# Testing Strategy for SSH Tunnel Proxy

## Testing Pyramid

```
        /\
       /  \      E2E Tests (5%)
      /----\     
     /      \    Integration Tests (15%)
    /--------\   
   /          \  Unit Tests (80%)
  /____________\ 
```

## Unit Testing

### Shared Code Testing (commonTest)

Test all business logic in the shared module:

```kotlin
class ProfileRepositoryTest {
    private lateinit var repository: ProfileRepository
    private lateinit var database: TestDatabase
    
    @BeforeTest
    fun setup() {
        database = createInMemoryDatabase()
        repository = ProfileRepositoryImpl(database)
    }
    
    @Test
    fun `creating profile should persist data`() = runTest {
        // Arrange
        val profile = ServerProfile(
            name = "Test Server",
            hostname = "example.com",
            port = 22,
            username = "user",
            keyType = KeyType.ED25519
        )
        
        // Act
        val result = repository.createProfile(profile)
        
        // Assert
        assertTrue(result.isSuccess)
        val retrieved = repository.getProfile(result.getOrThrow())
        assertEquals(profile.name, retrieved?.name)
    }
    
    @AfterTest
    fun teardown() {
        database.close()
    }
}
```

### Android-Specific Testing

```kotlin
@RunWith(AndroidJUnit4::class)
class AndroidCredentialStoreTest {
    private lateinit var credentialStore: AndroidCredentialStore
    
    @Before
    fun setup() {
        credentialStore = AndroidCredentialStore(
            ApplicationProvider.getApplicationContext()
        )
    }
    
    @Test
    fun `storing and retrieving key should return same data`() = runTest {
        // Arrange
        val profileId = 1L
        val privateKey = generateTestKey()
        
        // Act
        credentialStore.storeKey(profileId, privateKey, null)
        val retrieved = credentialStore.retrieveKey(profileId, null)
        
        // Assert
        assertTrue(retrieved.isSuccess)
        assertArrayEquals(privateKey, retrieved.getOrThrow().keyData)
    }
}
```

## Property-Based Testing

### Using Kotest Property Testing

```kotlin
class ConnectionPropertiesTest : StringSpec({
    "valid credentials should establish connections" {
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            // Arrange
            val manager = SSHConnectionManager()
            
            // Act
            val result = manager.connect(profile)
            
            // Assert
            result.isSuccess shouldBe true
            result.getOrNull()?.serverAddress shouldBe profile.hostname
        }
    }
    
    "profile round-trip should preserve data" {
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            // Arrange
            val repository = ProfileRepositoryImpl(database)
            
            // Act
            val id = repository.createProfile(profile).getOrThrow()
            val retrieved = repository.getProfile(id)
            
            // Assert
            retrieved shouldNotBe null
            retrieved?.name shouldBe profile.name
            retrieved?.hostname shouldBe profile.hostname
        }
    }
})
```

### Custom Generators

```kotlin
object Generators {
    fun Arb.Companion.serverProfile() = arbitrary {
        ServerProfile(
            id = 0,
            name = Arb.string(5..20).bind(),
            hostname = Arb.domain().bind(),
            port = Arb.int(1..65535).bind(),
            username = Arb.string(3..16, Codepoint.alphanumeric()).bind(),
            keyType = Arb.enum<KeyType>().bind(),
            createdAt = Arb.long(0..System.currentTimeMillis()).bind(),
            lastUsed = Arb.long(0..System.currentTimeMillis()).orNull().bind()
        )
    }
    
    fun Arb.Companion.domain() = arbitrary {
        val parts = Arb.list(Arb.string(3..10, Codepoint.alphanumeric()), 2..4).bind()
        parts.joinToString(".") + ".com"
    }
    
    fun Arb.Companion.privateKey() = arbitrary {
        val keyType = Arb.enum<KeyType>().bind()
        val keySize = when (keyType) {
            KeyType.ED25519 -> 32
            KeyType.ECDSA -> 64
            KeyType.RSA -> 256
        }
        PrivateKey(
            keyData = Arb.byteArray(Arb.constant(keySize), Arb.byte()).bind(),
            keyType = keyType
        )
    }
}
```

### Property Test Tagging

Tag each property test with the design document reference:

```kotlin
class ConnectionPropertiesTest : StringSpec({
    // Feature: ssh-tunnel-proxy, Property 1: Valid credentials establish connections
    "valid credentials should establish connections" {
        checkAll(100, Arb.serverProfile()) { profile ->
            // Test implementation
        }
    }
    
    // Feature: ssh-tunnel-proxy, Property 6: Profile creation round-trip
    "profile round-trip should preserve data" {
        checkAll(100, Arb.serverProfile()) { profile ->
            // Test implementation
        }
    }
})
```

## Integration Testing

### SSH Connection Integration

```kotlin
@RunWith(AndroidJUnit4::class)
class SSHConnectionIntegrationTest {
    private lateinit var testContainer: SSHServerContainer
    
    @Before
    fun setup() {
        // Start test SSH server container
        testContainer = SSHServerContainer().apply {
            start()
        }
    }
    
    @Test
    fun `should connect to real SSH server`() = runTest {
        // Arrange
        val profile = ServerProfile(
            name = "Test",
            hostname = testContainer.host,
            port = testContainer.port,
            username = "testuser",
            keyType = KeyType.ED25519
        )
        val manager = SSHConnectionManager()
        
        // Act
        val result = manager.connect(profile)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Cleanup
        manager.disconnect()
    }
    
    @After
    fun teardown() {
        testContainer.stop()
    }
}
```

### VPN Service Integration

```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class VpnServiceIntegrationTest {
    @get:Rule
    val serviceRule = ServiceTestRule()
    
    @Test
    fun `VPN should route traffic through tunnel`() = runTest {
        // Arrange
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            TunnelVpnService::class.java
        )
        
        // Act
        val binder = serviceRule.bindService(intent)
        val service = (binder as TunnelVpnService.LocalBinder).getService()
        service.startTunnel(socksPort = 1080)
        
        // Assert
        val externalIp = checkExternalIp()
        assertEquals(expectedServerIp, externalIp)
        
        // Cleanup
        service.stopTunnel()
    }
}
```

## UI Testing

### Compose UI Testing

```kotlin
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun `should display saved profiles`() {
        // Arrange
        val profiles = listOf(
            ServerProfile(id = 1, name = "Server 1", hostname = "example.com"),
            ServerProfile(id = 2, name = "Server 2", hostname = "test.com")
        )
        
        // Act
        composeTestRule.setContent {
            ProfileScreen(profiles = profiles)
        }
        
        // Assert
        composeTestRule.onNodeWithText("Server 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server 2").assertIsDisplayed()
    }
    
    @Test
    fun `clicking connect should trigger connection`() {
        // Arrange
        var connectClicked = false
        val profile = ServerProfile(id = 1, name = "Test", hostname = "example.com")
        
        // Act
        composeTestRule.setContent {
            ProfileCard(
                profile = profile,
                onConnect = { connectClicked = true }
            )
        }
        composeTestRule.onNodeWithText("Connect").performClick()
        
        // Assert
        assertTrue(connectClicked)
    }
}
```

## Test Doubles

### Mocking with MockK

```kotlin
class ConnectionManagerTest {
    private val mockSSHClient = mockk<SSHClient>()
    private val mockVpnProvider = mockk<VpnTunnelProvider>()
    private lateinit var connectionManager: SSHConnectionManager
    
    @Before
    fun setup() {
        connectionManager = SSHConnectionManagerImpl(
            sshClient = mockSSHClient,
            vpnProvider = mockVpnProvider
        )
    }
    
    @Test
    fun `connection failure should emit error state`() = runTest {
        // Arrange
        coEvery { 
            mockSSHClient.connect(any(), any(), any(), any()) 
        } returns Result.failure(IOException("Connection refused"))
        
        // Act
        val states = mutableListOf<ConnectionState>()
        val job = launch {
            connectionManager.observeConnectionState().collect { states.add(it) }
        }
        
        connectionManager.connect(testProfile)
        delay(100)
        
        // Assert
        assertTrue(states.last() is ConnectionState.Error)
        
        // Cleanup
        job.cancel()
    }
}
```

### Fake Implementations

```kotlin
class FakeProfileRepository : ProfileRepository {
    private val profiles = mutableMapOf<Long, ServerProfile>()
    private var nextId = 1L
    
    override suspend fun createProfile(profile: ServerProfile): Result<Long> {
        val id = nextId++
        profiles[id] = profile.copy(id = id)
        return Result.success(id)
    }
    
    override suspend fun getProfile(id: Long): ServerProfile? {
        return profiles[id]
    }
    
    override suspend fun getAllProfiles(): List<ServerProfile> {
        return profiles.values.toList()
    }
    
    override suspend fun updateProfile(profile: ServerProfile): Result<Unit> {
        profiles[profile.id] = profile
        return Result.success(Unit)
    }
    
    override suspend fun deleteProfile(id: Long): Result<Unit> {
        profiles.remove(id)
        return Result.success(Unit)
    }
}
```

## Test Coverage

### Minimum Coverage Targets

- **Shared Code (commonMain)**: 80% line coverage
- **Android Platform Code**: 70% line coverage
- **UI Code**: 60% line coverage (focus on critical paths)

### Measuring Coverage

```bash
# Run tests with coverage
./gradlew testDebugUnitTestCoverage

# View coverage report
open shared/build/reports/coverage/test/debug/index.html
```

## Continuous Integration

### GitHub Actions Example

```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          
      - name: Run unit tests
        run: ./gradlew testDebugUnitTest
        
      - name: Run property tests
        run: ./gradlew testDebugUnitTest --tests "*PropertiesTest"
        
      - name: Generate coverage report
        run: ./gradlew testDebugUnitTestCoverage
        
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

## Testing Best Practices

### Do's

✅ Test behavior, not implementation
✅ Use descriptive test names
✅ Follow Arrange-Act-Assert pattern
✅ Keep tests independent
✅ Use property-based testing for universal properties
✅ Mock external dependencies
✅ Test edge cases and error conditions
✅ Run tests in CI/CD pipeline

### Don'ts

❌ Don't test private methods directly
❌ Don't use real SSH servers in unit tests
❌ Don't share state between tests
❌ Don't skip flaky tests (fix them!)
❌ Don't test framework code
❌ Don't write tests that depend on execution order
❌ Don't mock everything (use fakes for complex dependencies)

## Test Maintenance

### Refactoring Tests

When refactoring code:
1. Run all tests first (ensure they pass)
2. Refactor production code
3. Update tests if interfaces changed
4. Ensure all tests still pass
5. Check coverage hasn't decreased

### Handling Flaky Tests

If a test is flaky:
1. Identify the source of non-determinism
2. Fix timing issues (use proper synchronization)
3. Remove external dependencies
4. Use test fixtures for consistent state
5. If unfixable, quarantine and investigate

## Resources

- [Kotest Documentation](https://kotest.io/)
- [MockK Documentation](https://mockk.io/)
- [Android Testing Guide](https://developer.android.com/training/testing)
- [Property-Based Testing Guide](https://hypothesis.works/articles/what-is-property-based-testing/)
