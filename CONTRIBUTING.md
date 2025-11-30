# Contributing to SSH Tunnel Proxy

Thank you for your interest in contributing to SSH Tunnel Proxy! This document provides guidelines and setup instructions for contributors.

## Table of Contents

- [Development Environment Setup](#development-environment-setup)
- [Project Structure](#project-structure)
- [Building the Project](#building-the-project)
- [Running Tests](#running-tests)
- [Code Style](#code-style)
- [Submitting Changes](#submitting-changes)
- [Reporting Issues](#reporting-issues)

## Development Environment Setup

### Prerequisites

Before you begin, ensure you have the following installed:

#### Required Software

1. **Java Development Kit (JDK) 17 or higher**
   - Download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)
   - Verify installation: `java -version`

2. **Android Studio** (Latest stable version recommended)
   - Download from [developer.android.com](https://developer.android.com/studio)
   - Includes Android SDK and required build tools
   - Alternative: IntelliJ IDEA Ultimate with Android plugin

3. **Android SDK**
   - Minimum API Level: 26 (Android 8.0)
   - Target API Level: 34 (Android 14)
   - Install via Android Studio SDK Manager:
     - Android SDK Platform 34
     - Android SDK Build-Tools 34.0.0
     - Android SDK Platform-Tools
     - Android Emulator (for testing)

4. **Git**
   - Download from [git-scm.com](https://git-scm.com/)
   - Verify installation: `git --version`

#### Optional but Recommended

1. **Android Device or Emulator**
   - Physical device with USB debugging enabled (preferred for VPN testing)
   - Or Android Emulator (API 26+) via Android Studio

2. **SSH Server for Testing**
   - Local SSH server or cloud instance
   - Must support SOCKS5 dynamic port forwarding
   - OpenSSH recommended

### Initial Setup

#### 1. Clone the Repository

```bash
git clone https://github.com/badscrew/selfproxy.git
cd selfproxy
```

#### 2. Configure Android SDK Path

Create or edit `local.properties` in the project root:

```properties
sdk.dir=/path/to/your/Android/Sdk
```

**Windows example:**
```properties
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

**macOS/Linux example:**
```properties
sdk.dir=/Users/YourUsername/Library/Android/sdk
```

#### 3. Verify Gradle Setup

The project uses Gradle wrapper, so no separate Gradle installation is needed.

**Windows:**
```powershell
.\gradlew.bat --version
```

**macOS/Linux:**
```bash
./gradlew --version
```

#### 4. Sync Project with Gradle Files

Open the project in Android Studio:
1. File → Open → Select the project directory
2. Wait for Gradle sync to complete
3. Resolve any SDK or dependency issues

### IDE Configuration

#### Android Studio Settings

1. **Kotlin Plugin**
   - Should be installed by default
   - Verify: Settings → Plugins → Kotlin

2. **Code Style**
   - Settings → Editor → Code Style → Kotlin
   - Set from: Kotlin style guide
   - Or import `.editorconfig` if provided

3. **Enable Auto-Import**
   - Settings → Editor → General → Auto Import
   - Check "Add unambiguous imports on the fly"
   - Check "Optimize imports on the fly"

4. **Increase Memory (Optional)**
   - Help → Edit Custom VM Options
   - Add or modify:
     ```
     -Xmx4096m
     -XX:MaxMetaspaceSize=512m
     ```

### Environment Variables

No special environment variables are required. The project uses standard Android/Kotlin tooling.

## Project Structure

```
ssh-tunnel-proxy/
├── shared/                      # Kotlin Multiplatform shared code
│   ├── src/
│   │   ├── commonMain/         # Platform-agnostic business logic
│   │   ├── commonTest/         # Shared unit tests
│   │   ├── androidMain/        # Android-specific implementations
│   │   └── androidUnitTest/    # Android-specific tests
│   └── build.gradle.kts
├── androidApp/                  # Android application
│   ├── src/main/
│   │   ├── kotlin/             # Android UI (Jetpack Compose)
│   │   └── res/                # Android resources
│   └── build.gradle.kts
├── docs/                        # Documentation
├── .kiro/                       # Kiro IDE configuration
│   ├── specs/                  # Feature specifications
│   └── steering/               # Development guidelines
├── build.gradle.kts            # Root build configuration
├── settings.gradle.kts         # Gradle settings
└── gradle.properties           # Gradle properties
```

## Building the Project

### Build Commands

**Build everything:**
```bash
./gradlew build
```

**Build Android APK (Debug):**
```bash
./gradlew androidApp:assembleDebug
```

**Build Android APK (Release):**
```bash
./gradlew androidApp:assembleRelease
```

**Install on connected device:**
```bash
./gradlew androidApp:installDebug
```

**Or manually install:**
```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### Build Outputs

- Debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Release APK: `androidApp/build/outputs/apk/release/androidApp-release.apk`

### Troubleshooting Build Issues

**File lock errors (Windows):**
```powershell
.\gradlew.bat --stop
taskkill /F /IM java.exe
.\gradlew.bat androidApp:assembleDebug
```

**Clean build:**
```bash
./gradlew clean build
```

**Refresh dependencies:**
```bash
./gradlew --refresh-dependencies
```

See [gradle-build-troubleshooting.md](.kiro/steering/gradle-build-troubleshooting.md) for more details.

## Running Tests

### Unit Tests

**Run all tests:**
```bash
./gradlew test
```

**Run shared module tests:**
```bash
./gradlew shared:testDebugUnitTest
```

**Run specific test class:**
```bash
./gradlew shared:testDebugUnitTest --tests "com.sshtunnel.data.ServerProfilePropertiesTest"
```

### Test Reports

After running tests, view HTML reports:
- Shared module: `shared/build/reports/tests/testDebugUnitTest/index.html`

### Property-Based Tests

This project uses Kotest for property-based testing. Tests use `@Test` annotations with `checkAll`:

```kotlin
@Test
fun `profile serialization round-trip should preserve data`() = runTest {
    checkAll(100, Arb.serverProfile()) { profile ->
        val json = Json.encodeToString(profile)
        val deserialized = Json.decodeFromString<ServerProfile>(json)
        deserialized shouldBe profile
    }
}
```

See [testing-strategy.md](.kiro/steering/testing-strategy.md) for more details.

## Code Style

### Kotlin Coding Conventions

This project follows the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).

**Key points:**
- Use 4 spaces for indentation
- Use camelCase for functions and variables
- Use PascalCase for classes
- Maximum line length: 120 characters
- Use meaningful names

### Linting

**Check code style:**
```bash
./gradlew ktlintCheck
```

**Auto-format code:**
```bash
./gradlew ktlintFormat
```

### Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `chore`: Build/tooling changes

**Example:**
```
feat(vpn): add UDP ASSOCIATE support for video calling

Implemented SOCKS5 UDP ASSOCIATE to enable video calling apps
like WhatsApp and Zoom to work through the tunnel.

Closes #42
```

See [git-practices.md](.kiro/steering/git-practices.md) for more details.

## Submitting Changes

### Workflow

1. **Fork the repository**
   - Click "Fork" on GitHub

2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes**
   - Write code
   - Add tests
   - Update documentation

4. **Test your changes**
   ```bash
   ./gradlew test
   ./gradlew androidApp:assembleDebug
   ```

5. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: add amazing feature"
   ```

6. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request**
   - Go to the original repository on GitHub
   - Click "New Pull Request"
   - Select your fork and branch
   - Fill out the PR template

### Pull Request Guidelines

**Before submitting:**
- [ ] Code follows project style guidelines
- [ ] All tests pass locally
- [ ] New tests added for new features
- [ ] Documentation updated if needed
- [ ] Commit messages follow conventional format
- [ ] No merge conflicts with main branch

**PR Description should include:**
- Clear description of changes
- Related issue numbers (if applicable)
- Screenshots for UI changes
- Testing steps performed

### Code Review Process

1. Maintainers will review your PR
2. Address any feedback or requested changes
3. Once approved, your PR will be merged
4. Your contribution will be credited in release notes

## Reporting Issues

### Bug Reports

When reporting bugs, please include:

1. **Environment:**
   - Android version
   - Device model
   - App version

2. **Steps to reproduce:**
   - Detailed steps to trigger the bug
   - Expected behavior
   - Actual behavior

3. **Logs:**
   - Relevant logcat output
   - Error messages
   - Screenshots if applicable

4. **Additional context:**
   - SSH server type (OpenSSH, etc.)
   - Network conditions
   - Any workarounds found

### Feature Requests

When requesting features:

1. **Use case:** Describe the problem you're trying to solve
2. **Proposed solution:** How you envision the feature working
3. **Alternatives:** Other solutions you've considered
4. **Additional context:** Why this would be valuable

## Development Guidelines

### Architecture

- **Shared module (commonMain):** Platform-agnostic business logic
- **Platform modules (androidMain):** Android-specific implementations
- **UI layer (androidApp):** Jetpack Compose UI

See [kotlin-multiplatform-architecture.md](.kiro/steering/kotlin-multiplatform-architecture.md) for details.

### Security

- Never commit credentials or API keys
- Use Android Keystore for sensitive data
- Follow security best practices in [ssh-tunnel-security.md](.kiro/steering/ssh-tunnel-security.md)

### Testing

- Write unit tests for business logic
- Use property-based tests for universal properties
- Test Android-specific code with instrumented tests
- See [testing-strategy.md](.kiro/steering/testing-strategy.md)

## Getting Help

- **Documentation:** Check the [docs/](docs/) directory
- **Issues:** Search existing issues on GitHub
- **Discussions:** Start a discussion on GitHub
- **Code:** Review existing code for examples

## License

By contributing to this project, you agree that your contributions will be licensed under the Apache License 2.0.

## Acknowledgments

Parts of this project were developed using agentic coding with AI assistance. We welcome contributions from both human developers and AI-assisted development workflows.

Thank you for contributing to SSH Tunnel Proxy! 🚀
