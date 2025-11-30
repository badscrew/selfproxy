# SSH Tunnel Proxy

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.21-purple.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2026+-green.svg)](https://developer.android.com/)

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
