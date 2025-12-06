# Native SSH Binary Setup Guide

This guide explains how to set up the OpenSSH binaries for the native SSH client implementation.

## Overview

The native SSH client uses OpenSSH binaries compiled for Android from the Termux project. These binaries are bundled with the app in the `jniLibs` directory, organized by CPU architecture.

## Quick Start

### Option 1: Automated Download (Linux/Mac/WSL)

```bash
cd /path/to/ssh-tunnel-proxy
chmod +x .kiro/temp/scripts/download-termux-binaries.sh
./.kiro/temp/scripts/download-termux-binaries.sh
```

This script will:
1. Download OpenSSH and OpenSSL packages from Termux
2. Extract binaries for all architectures
3. Copy them to the correct directories
4. Generate checksums

### Option 2: Manual Download

1. Visit https://packages.termux.dev/
2. Search for "openssh" and "openssl"
3. Download packages for each architecture:
   - `openssh_9.6p1_aarch64.deb` → arm64-v8a
   - `openssh_9.6p1_arm.deb` → armeabi-v7a
   - `openssh_9.6p1_x86_64.deb` → x86_64
   - `openssh_9.6p1_i686.deb` → x86
   - Same for openssl packages

4. Extract .deb files:
   ```bash
   ar x openssh_9.6p1_aarch64.deb
   tar xf data.tar.xz
   ```

5. Copy binaries:
   ```bash
   # SSH binary (rename to .so)
   cp data/data/com.termux/files/usr/bin/ssh androidApp/src/main/jniLibs/arm64-v8a/libssh.so
   
   # OpenSSL libraries
   cp data/data/com.termux/files/usr/lib/libcrypto.so.3 androidApp/src/main/jniLibs/arm64-v8a/libcrypto.so
   cp data/data/com.termux/files/usr/lib/libssl.so.3 androidApp/src/main/jniLibs/arm64-v8a/libssl.so
   ```

6. Repeat for all architectures

## Directory Structure

After setup, your directory should look like:

```
androidApp/src/main/jniLibs/
├── README.md                    # Binary documentation
├── BINARY_SETUP.md             # This file
├── arm64-v8a/
│   ├── libssh.so               # OpenSSH binary (800KB - 1.2MB)
│   ├── libcrypto.so            # OpenSSL crypto (1.5MB - 2MB)
│   ├── libssl.so               # OpenSSL SSL (400KB - 600KB)
│   └── checksums.txt           # SHA256 checksums
├── armeabi-v7a/
│   ├── libssh.so
│   ├── libcrypto.so
│   ├── libssl.so
│   └── checksums.txt
├── x86_64/
│   ├── libssh.so
│   ├── libcrypto.so
│   ├── libssl.so
│   └── checksums.txt
└── x86/
    ├── libssh.so
    ├── libcrypto.so
    ├── libssl.so
    └── checksums.txt
```

## Verification

### Check Binary Presence

```bash
# List all binaries
find androidApp/src/main/jniLibs -name "*.so" -type f

# Expected output:
# androidApp/src/main/jniLibs/arm64-v8a/libssh.so
# androidApp/src/main/jniLibs/arm64-v8a/libcrypto.so
# androidApp/src/main/jniLibs/arm64-v8a/libssl.so
# ... (same for other architectures)
```

### Verify Checksums

```bash
# Calculate checksums
cd androidApp/src/main/jniLibs/arm64-v8a
sha256sum *.so

# Compare with checksums.txt
cat checksums.txt
```

### Test Binary Execution

```bash
# Check if binary is executable (on Linux/Mac)
file androidApp/src/main/jniLibs/arm64-v8a/libssh.so

# Expected: ELF 64-bit LSB shared object, ARM aarch64
```

## APK Splits Configuration

The build is configured to create separate APKs for each architecture:

- **arm64-v8a APK**: ~3-4 MB (most devices)
- **armeabi-v7a APK**: ~3-4 MB (legacy devices)
- **x86_64 APK**: ~3-4 MB (emulators)
- **x86 APK**: ~3-4 MB (legacy emulators)
- **Universal APK**: ~12-15 MB (all architectures)

### Version Codes

Each APK gets a unique version code:
- Base version: 1
- arm64-v8a: 11 (1 * 10 + 1)
- armeabi-v7a: 12 (1 * 10 + 2)
- x86_64: 13 (1 * 10 + 3)
- x86: 14 (1 * 10 + 4)
- Universal: 10 (1 * 10 + 0)

This ensures Google Play serves the correct APK to each device.

## Building

### Build All APKs

```bash
# Windows
.\gradlew.bat androidApp:assembleRelease

# Linux/Mac
./gradlew androidApp:assembleRelease
```

Output APKs will be in:
```
androidApp/build/outputs/apk/release/
├── androidApp-arm64-v8a-release.apk
├── androidApp-armeabi-v7a-release.apk
├── androidApp-x86_64-release.apk
├── androidApp-x86-release.apk
└── androidApp-universal-release.apk
```

### Build Single Architecture

```bash
# Build only ARM64
.\gradlew.bat androidApp:assembleRelease -Pandroid.splits.abi.enable=false
```

## Testing

### Test on Device

```bash
# Install appropriate APK for device architecture
adb install androidApp/build/outputs/apk/debug/androidApp-arm64-v8a-debug.apk

# Check if binaries are present
adb shell "ls -lh /data/app/*/lib/arm64/"

# Expected: libssh.so, libcrypto.so, libssl.so
```

### Test Binary Extraction

The app will extract binaries on first run. Check logs:

```bash
adb logcat | grep -i "ssh\|binary"
```

Expected logs:
```
BinaryManager: Extracting SSH binary for arm64-v8a
BinaryManager: Binary extracted to /data/data/com.sshtunnel.android/files/ssh
BinaryManager: Setting executable permissions
BinaryManager: Binary ready for use
```

## Troubleshooting

### Binaries Not Found

**Problem**: Build fails with "jniLibs not found"

**Solution**: 
1. Verify binaries are in correct directories
2. Check file names match exactly: `libssh.so`, `libcrypto.so`, `libssl.so`
3. Ensure .gitkeep files are removed after adding binaries

### Wrong Architecture

**Problem**: App crashes with "UnsatisfiedLinkError"

**Solution**:
1. Verify device architecture: `adb shell getprop ro.product.cpu.abi`
2. Ensure correct APK is installed for device
3. Check binary is for correct architecture: `file libssh.so`

### Binary Not Executable

**Problem**: "Permission denied" when running SSH binary

**Solution**:
1. Check BinaryManager sets executable permissions
2. Verify binary is extracted to app's private directory
3. Check SELinux context allows execution

### Large APK Size

**Problem**: APK is too large

**Solution**:
1. Use APK splits (already configured)
2. Distribute via Google Play (automatic APK selection)
3. Consider ARM64-only for production (covers 95%+ devices)
4. Strip debug symbols from binaries

## Updates

### Updating Binaries

When new OpenSSH or OpenSSL versions are released:

1. Download new packages from Termux
2. Extract and copy binaries
3. Update version numbers in README.md
4. Update checksums
5. Test thoroughly on all architectures
6. Increment app version code
7. Release update

### Security Updates

Monitor these sources for security updates:
- OpenSSH: https://www.openssh.com/security.html
- OpenSSL: https://www.openssl.org/news/vulnerabilities.html
- Termux: https://github.com/termux/termux-packages/releases

## License and Attribution

### OpenSSH
- License: BSD
- Copyright: OpenSSH developers
- Website: https://www.openssh.com/

### OpenSSL
- License: Apache 2.0
- Copyright: OpenSSL Project
- Website: https://www.openssl.org/

### Termux
- License: GPLv3
- Copyright: Termux developers
- Website: https://termux.dev/
- GitHub: https://github.com/termux/termux-packages

## Support

For issues with:
- Binary setup: Check this guide
- Binary extraction: Check BinaryManager implementation
- APK splits: Check build.gradle.kts configuration
- Runtime issues: Check device logs with `adb logcat`

## References

- [Termux Packages](https://packages.termux.dev/)
- [Android JNI Libraries](https://developer.android.com/ndk/guides/libs)
- [APK Splits](https://developer.android.com/studio/build/configure-apk-splits)
- [Native SSH Client Design](../../.kiro/specs/native-ssh-client/design.md)
