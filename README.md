# SSH Tunnel Proxy

A mobile application (initially Android, with future iOS support planned) that enables users to route their mobile device's internet traffic through their own SSH servers using SOCKS5 proxy tunneling.

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
- **SSH Library**: JSch
- **VPN**: Android VpnService API
- **Credential Storage**: Android Keystore
- **DI**: Hilt for dependency injection
- **UI**: Jetpack Compose with Material Design 3

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

## Architecture

The application follows a layered architecture with clear separation between platform-agnostic business logic and platform-specific implementations:

1. **Shared Module (commonMain)**: Business logic, data models, repositories
2. **Platform Modules (androidMain)**: Platform-specific implementations
3. **UI Layer (androidApp)**: Platform-native UI

See the [design document](.kiro/specs/ssh-tunnel-proxy/design.md) for detailed architecture information.

## License

[To be determined]

## Contributing

[To be determined]
