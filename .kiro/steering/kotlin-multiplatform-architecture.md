---
inclusion: always
---

# Kotlin Multiplatform Architecture Guidelines

## Project Structure

Follow this standard Kotlin Multiplatform project structure:

```
shared/
  src/
    commonMain/kotlin/          # Shared business logic
    commonTest/kotlin/          # Shared tests
    androidMain/kotlin/         # Android-specific implementations
    androidTest/kotlin/         # Android-specific tests
    iosMain/kotlin/             # iOS-specific implementations (future)
    iosTest/kotlin/             # iOS-specific tests (future)

androidApp/
  src/main/kotlin/              # Android UI and app-specific code

iosApp/                         # iOS app (future)
```

## Code Organization Principles

### What Goes in commonMain (Shared)

- **Business Logic**: Connection management, state machines, validation
- **Data Models**: All data classes and domain models
- **Repository Interfaces**: Data access abstractions
- **Use Cases**: Application-specific business rules
- **Utilities**: Pure Kotlin utilities without platform dependencies

### What Goes in Platform Modules (androidMain/iosMain)

- **Platform APIs**: VPN services, credential storage, network monitoring
- **Platform-Specific Implementations**: Concrete implementations of shared interfaces
- **UI Code**: Jetpack Compose (Android), SwiftUI (iOS)
- **Platform Libraries**: JSch (Android), NMSSH (iOS)

## Dependency Management

### Shared Dependencies (commonMain)

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("com.squareup.sqldelight:runtime:2.0.0")
                implementation("io.ktor:ktor-client-core:2.3.5")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
    }
}
```

### Android Dependencies

```kotlin
val androidMain by getting {
    dependencies {
        implementation("com.jcraft:jsch:0.1.55")
        implementation("androidx.security:security-crypto:1.1.0-alpha06")
        implementation("com.squareup.sqldelight:android-driver:2.0.0")
    }
}
```

## Interface Design Pattern

Use `expect`/`actual` for platform-specific implementations:

```kotlin
// commonMain
expect interface SSHClient {
    suspend fun connect(host: String, port: Int): Result<Session>
}

// androidMain
actual class AndroidSSHClient : SSHClient {
    override suspend fun connect(host: String, port: Int): Result<Session> {
        // JSch implementation
    }
}

// iosMain (future)
actual class IOSSSHClient : SSHClient {
    override suspend fun connect(host: String, port: Int): Result<Session> {
        // NMSSH implementation
    }
}
```

## Testing Strategy

### Shared Tests (commonTest)

- Test all business logic in commonTest
- Use Kotlin Test framework
- Mock platform-specific dependencies

```kotlin
class ProfileRepositoryTest {
    @Test
    fun `creating profile should persist data`() = runTest {
        // Test shared logic
    }
}
```

### Platform Tests

- Test platform-specific implementations
- Android: Use JUnit + MockK
- iOS: Use XCTest (future)

## Best Practices

1. **Keep Platform Code Minimal**: Maximize code in commonMain
2. **Use Interfaces**: Define interfaces in commonMain, implement in platform modules
3. **Avoid Platform Types in Shared Code**: Don't leak Android/iOS types into commonMain
4. **Use Coroutines**: Prefer coroutines over platform-specific async patterns
5. **Dependency Injection**: Use constructor injection, avoid platform-specific DI frameworks in shared code
6. **Error Handling**: Use `Result<T>` for consistent error handling across platforms

## Common Pitfalls to Avoid

❌ **Don't**: Use Android-specific types in commonMain
```kotlin
// commonMain - WRONG
fun processData(context: Context) { } // Context is Android-specific
```

✅ **Do**: Use platform-agnostic abstractions
```kotlin
// commonMain - CORRECT
interface PlatformContext
fun processData(context: PlatformContext) { }

// androidMain
actual class AndroidPlatformContext(val context: Context) : PlatformContext
```

❌ **Don't**: Put UI code in commonMain
```kotlin
// commonMain - WRONG
fun showDialog() { /* Compose code */ }
```

✅ **Do**: Keep UI in platform modules
```kotlin
// androidMain
@Composable
fun ShowDialog() { /* Compose code */ }
```

## Migration Path

When adding iOS support later:

1. Ensure all business logic is in commonMain
2. Create iosMain module
3. Implement platform-specific interfaces for iOS
4. Create iOS UI in SwiftUI
5. Reuse all shared tests

## Resources

- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [SQLDelight Documentation](https://cashapp.github.io/sqldelight/)
- [Ktor Client Documentation](https://ktor.io/docs/client.html)
