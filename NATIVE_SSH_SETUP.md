# Native SSH Client Setup

This document provides instructions for setting up the native OpenSSH client implementation.

## Overview

The SSH Tunnel Proxy app uses native OpenSSH binaries compiled for Android to provide reliable SSH connections. This replaces the problematic sshj Java library that had a 67% failure rate.

## Prerequisites

- Android SDK with API 26+ (Android 8.0+)
- Gradle 8.0+
- For binary download: Linux, Mac, or WSL (Windows Subsystem for Linux)

## Quick Setup

### 1. Download Binaries

**Option A: Automated (Recommended)**

On Linux/Mac/WSL:
```bash
cd /path/to/ssh-tunnel-proxy
chmod +x .kiro/temp/scripts/download-termux-binaries.sh
./.kiro/temp/scripts/download-termux-binaries.sh
```

**Option B: Manual Download**

See detailed instructions in `androidApp/src/main/jniLibs/BINARY_SETUP.md`

### 2. Verify Setup

```bash
# Check binaries are present
find androidApp/src/main/jniLibs -name "*.so" -type f

# Should show 12 files (3 per architecture × 4 architectures)
```

### 3. Build

```bash
# Windows
.\gradlew.bat androidApp:assembleDebug

# Linux/Mac
./gradlew androidApp:assembleDebug
```

### 4. Install and Test

```bash
# Install on device
adb install androidApp/build/outputs/apk/debug/androidApp-arm64-v8a-debug.apk

# Check logs
adb logcat | grep -i "ssh\|binary"
```

## Architecture Support

The app supports four CPU architectures:

| Architecture | Android ABI | Devices | APK Size |
|--------------|-------------|---------|----------|
| ARM64 | arm64-v8a | Most modern devices (95%+) | +3-4 MB |
| ARM32 | armeabi-v7a | Legacy devices | +3-4 MB |
| x86_64 | x86_64 | Emulators, some tablets | +3-4 MB |
| x86 | x86 | Legacy emulators | +3-4 MB |

## APK Splits

The build is configured to create separate APKs for each architecture:

```
androidApp/build/outputs/apk/release/
├── androidApp-arm64-v8a-release.apk      (~3-4 MB)
├── androidApp-armeabi-v7a-release.apk    (~3-4 MB)
├── androidApp-x86_64-release.apk         (~3-4 MB)
├── androidApp-x86-release.apk            (~3-4 MB)
└── androidApp-universal-release.apk      (~12-15 MB)
```

When distributed via Google Play, users automatically get the correct APK for their device.

## Binary Sources

All binaries come from the Termux project:

- **OpenSSH**: Version 9.6p1 or later
- **OpenSSL**: Version 3.2.0 or later
- **Source**: https://packages.termux.dev/
- **License**: BSD (OpenSSH), Apache 2.0 (OpenSSL)

See `androidApp/src/main/jniLibs/LICENSE` for full license information.

## Directory Structure

```
androidApp/src/main/jniLibs/
├── README.md              # Binary documentation
├── BINARY_SETUP.md        # Detailed setup guide
├── LICENSE                # License information
├── .gitignore            # Git configuration
├── arm64-v8a/
│   ├── libssh.so         # OpenSSH binary
│   ├── libcrypto.so      # OpenSSL crypto library
│   └── libssl.so         # OpenSSL SSL library
├── armeabi-v7a/
│   ├── libssh.so
│   ├── libcrypto.so
│   └── libssl.so
├── x86_64/
│   ├── libssh.so
│   ├── libcrypto.so
│   └── libssl.so
└── x86/
    ├── libssh.so
    ├── libcrypto.so
    └── libssl.so
```

## Build Configuration

The native SSH setup is configured in `androidApp/build.gradle.kts`:

```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        isUniversalApk = true
    }
}
```

Version codes are automatically assigned:
- arm64-v8a: Base × 10 + 1
- armeabi-v7a: Base × 10 + 2
- x86_64: Base × 10 + 3
- x86: Base × 10 + 4
- Universal: Base × 10 + 0

## Testing

### Unit Tests

Run unit tests for binary management:

```bash
.\gradlew.bat shared:testDebugUnitTest --tests "*BinaryManager*"
```

### Integration Tests

Test on real device:

```bash
# Install app
adb install androidApp/build/outputs/apk/debug/androidApp-arm64-v8a-debug.apk

# Monitor logs
adb logcat -c
adb logcat | grep -E "BinaryManager|NativeSSH|ProcessManager"

# Test SSH connection
# (Use app UI to connect to SSH server)
```

### Manual Testing Checklist

- [ ] Binary extraction works on first run
- [ ] Correct architecture binary is selected
- [ ] SSH process starts successfully
- [ ] SOCKS5 proxy is created
- [ ] Traffic routes through tunnel
- [ ] Connection remains stable
- [ ] Graceful shutdown works
- [ ] Fallback to sshj works if native fails

## Troubleshooting

### Binaries Not Found

**Problem**: Build fails with "jniLibs not found"

**Solution**: Run the download script or manually place binaries in jniLibs directories

### Wrong Architecture

**Problem**: App crashes with "UnsatisfiedLinkError"

**Solution**: 
1. Check device architecture: `adb shell getprop ro.product.cpu.abi`
2. Install correct APK for device architecture
3. Or install universal APK

### Binary Not Executable

**Problem**: "Permission denied" when running SSH

**Solution**: Check BinaryManager implementation sets executable permissions correctly

### Large APK Size

**Problem**: APK is too large

**Solution**: Use APK splits (already configured) or distribute via Google Play

## Updating Binaries

When new OpenSSH or OpenSSL versions are released:

1. Download new packages from Termux
2. Run download script or manually update binaries
3. Update version numbers in `androidApp/src/main/jniLibs/README.md`
4. Test on all architectures
5. Increment app version code
6. Release update

Monitor security advisories:
- OpenSSH: https://www.openssh.com/security.html
- OpenSSL: https://www.openssl.org/news/vulnerabilities.html

## Development Workflow

### Adding New Architecture

1. Download binary for new architecture from Termux
2. Create directory: `androidApp/src/main/jniLibs/<new-abi>/`
3. Copy binaries to directory
4. Update `build.gradle.kts` splits configuration
5. Test on device with new architecture

### Removing Architecture

1. Remove directory: `androidApp/src/main/jniLibs/<abi>/`
2. Update `build.gradle.kts` splits configuration
3. Update documentation

## CI/CD Integration

For continuous integration:

```yaml
# Example GitHub Actions workflow
- name: Download SSH binaries
  run: |
    chmod +x .kiro/temp/scripts/download-termux-binaries.sh
    ./.kiro/temp/scripts/download-termux-binaries.sh

- name: Build APKs
  run: ./gradlew androidApp:assembleRelease

- name: Upload APKs
  uses: actions/upload-artifact@v3
  with:
    name: apks
    path: androidApp/build/outputs/apk/release/*.apk
```

## References

- [Native SSH Client Design](.kiro/specs/native-ssh-client/design.md)
- [Native SSH Client Requirements](.kiro/specs/native-ssh-client/requirements.md)
- [Binary Setup Guide](androidApp/src/main/jniLibs/BINARY_SETUP.md)
- [Termux Packages](https://packages.termux.dev/)
- [Android JNI Guide](https://developer.android.com/ndk/guides/libs)
- [APK Splits Documentation](https://developer.android.com/studio/build/configure-apk-splits)

## Support

For issues:
1. Check this guide and BINARY_SETUP.md
2. Review device logs: `adb logcat`
3. Check GitHub issues
4. Consult design document for implementation details

## License

The native binaries are licensed separately:
- OpenSSH: BSD License
- OpenSSL: Apache License 2.0

See `androidApp/src/main/jniLibs/LICENSE` for full details.
