# Native SSH Implementation Limitations

## Overview

This document describes the limitations and challenges encountered when implementing native OpenSSH support for Android.

## Critical Limitation: SELinux Restrictions

### The Problem

**Native SSH execution is blocked by Android's SELinux (Security-Enhanced Linux) policy on non-rooted devices.**

When attempting to execute the native SSH binary extracted to the app's private directory, Android's SELinux policy denies the execution with:

```
avc: denied { execute_no_trans } for path="/data/data/com.sshtunnel.android/files/native-ssh/ssh_arm64-v8a"
scontext=u:r:untrusted_app:s0:c61,c257,c512,c768
tcontext=u:object_r:app_data_file:s0:c61,c257,c512,c768
tclass=file permissive=0

error=13, Permission denied
```

### Why This Happens

1. **SELinux Policy**: Android's SELinux policy prevents untrusted apps from executing binaries in their private data directories
2. **Security by Design**: This is an intentional security feature to prevent malicious apps from executing arbitrary code
3. **No Workaround**: There is no legitimate way to bypass this restriction without:
   - Root access (not available on most devices)
   - System-level permissions (not granted to user apps)
   - Custom ROM with modified SELinux policy (not practical for general distribution)

### What We Tried

1. ✅ **Downloaded OpenSSH binaries from Termux** - Successfully obtained ARM64, ARM32, x86_64, and x86 binaries
2. ✅ **Bundled binaries in APK** - Placed binaries in `jniLibs/` directories with proper architecture mapping
3. ✅ **Extracted binaries at runtime** - Successfully extracted from APK to app's private directory
4. ✅ **Set executable permissions** - `setExecutable(true)` succeeded
5. ✅ **Verified binary integrity** - SHA-256 checksums validated
6. ❌ **Execute binary** - **BLOCKED BY SELINUX**

### Technical Details

**Binary Information:**
- Source: Termux packages (OpenSSH 10.2p1)
- Architectures: arm64-v8a, armeabi-v7a, x86_64, x86
- Location in APK: `lib/{arch}/libssh.so`
- Extraction path: `/data/user/0/com.sshtunnel.android/files/native-ssh/ssh_arm64-v8a`
- File permissions: `-rwx------` (executable set correctly)
- SELinux context: `u:object_r:app_data_file:s0` (correct for app data)

**SELinux Policy:**
- App context: `u:r:untrusted_app:s0`
- Required permission: `execute_no_trans`
- Policy decision: **DENIED** (permissive=0)

## Workaround: SSHJ Library

The app includes a fallback to the **sshj** Java-based SSH library, which works without SELinux restrictions because it doesn't execute external binaries.

### Using SSHJ

**Option 1: Change in Settings**
1. Open the app
2. Go to Settings → SSH Implementation
3. Select "SSHJ" instead of "Auto" or "Native"

**Option 2: Change Default in Code**
Modify `ConnectionSettings.kt`:
```kotlin
data class ConnectionSettings(
    // ...
    val sshImplementationType: SSHImplementationType = SSHImplementationType.SSHJ // Changed from AUTO
)
```

### SSHJ vs Native SSH

| Feature | Native SSH | SSHJ |
|---------|-----------|------|
| **Works on Android** | ❌ No (SELinux blocked) | ✅ Yes |
| **Performance** | Would be faster | Slightly slower (pure Java) |
| **Binary Size** | ~3-4 MB per arch | ~1 MB (pure Java) |
| **Compatibility** | OpenSSH standard | SSH protocol standard |
| **Dependencies** | OpenSSL libraries | None (pure Java) |
| **Root Required** | Yes (for execution) | No |

## Devices Where Native SSH Might Work

Native SSH execution might work on:

1. **Rooted devices** with SELinux set to permissive mode
2. **Custom ROMs** with modified SELinux policies
3. **Development/debug builds** with SELinux disabled
4. **Very old Android versions** (pre-SELinux enforcement)

**Note**: These scenarios are not suitable for production apps distributed via Play Store.

## Implementation Status

### What Was Implemented ✅

1. **Binary Management**
   - Download script for Termux binaries
   - APK bundling in `jniLibs/`
   - Runtime extraction from APK
   - SHA-256 checksum verification
   - Architecture detection (ARM64, ARM32, x86_64, x86)

2. **Native SSH Client**
   - SSH command builder with security validation
   - Process management for SSH subprocess
   - SOCKS5 dynamic port forwarding (-D flag)
   - Connection monitoring and health checks
   - Keep-alive mechanism
   - Error handling and diagnostics

3. **Security Features**
   - Private key encryption with Android Keystore
   - Secure file permissions (0600 for keys)
   - Command injection prevention
   - Path traversal protection
   - Safe error message generation

4. **Integration**
   - Factory pattern with automatic fallback
   - Settings UI for implementation selection
   - Logging and diagnostics
   - Performance metrics collection

### What Doesn't Work ❌

1. **Binary Execution** - Blocked by SELinux on non-rooted devices
2. **Native SSH Connections** - Cannot establish due to execution failure
3. **Performance Benefits** - Cannot be realized due to execution block

## Recommendations

### For Users

**Use SSHJ implementation** - It works reliably on all Android devices without special permissions.

### For Developers

1. **Keep the native SSH code** - It's well-implemented and could be useful for:
   - Rooted devices
   - Custom ROMs
   - Future Android policy changes
   - Reference implementation

2. **Default to SSHJ** - Change the default implementation type to SSHJ for better out-of-box experience

3. **Document clearly** - Make it clear in the UI that native SSH requires root access

4. **Consider alternatives**:
   - Pure Java SSH implementations (sshj, JSch)
   - Kotlin Multiplatform SSH library (if one exists)
   - WireGuard or other VPN protocols that don't require SSH

## Future Possibilities

### Android Policy Changes

If Android ever allows apps to execute binaries from their private directories (unlikely for security reasons), the native SSH implementation would work immediately.

### Alternative Approaches

1. **NDK/JNI Integration**: Compile OpenSSH as a native library (.so) and call it via JNI
   - **Challenge**: OpenSSH is designed as a standalone binary, not a library
   - **Effort**: Significant refactoring of OpenSSH codebase required

2. **libssh Integration**: Use libssh (C library) via JNI
   - **Challenge**: Need to wrap C API for Kotlin
   - **Benefit**: Designed as a library, easier to integrate

3. **Pure Kotlin SSH**: Implement SSH protocol in Kotlin
   - **Challenge**: Complex protocol, significant development effort
   - **Benefit**: No native code, works everywhere

## Conclusion

The native SSH implementation is **technically complete and well-designed**, but **cannot function on standard Android devices due to SELinux restrictions**. The SSHJ fallback provides a working solution for all users.

The native SSH code should be kept in the codebase as:
- A reference implementation
- Potential future use if Android policies change
- Use on rooted/custom devices
- Educational value

**Recommended Action**: Change default SSH implementation to SSHJ for production releases.

## References

- [Android SELinux Documentation](https://source.android.com/docs/security/features/selinux)
- [Termux OpenSSH Package](https://github.com/termux/termux-packages/tree/master/packages/openssh)
- [SSHJ Library](https://github.com/hierynomus/sshj)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
