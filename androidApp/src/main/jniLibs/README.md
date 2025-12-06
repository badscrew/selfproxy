# Native SSH Binaries

This directory contains OpenSSH binaries and dependencies compiled for Android from the Termux project.

## Binary Sources

### OpenSSH
- **Source**: Termux OpenSSH package
- **Project**: https://github.com/termux/termux-packages
- **Package**: openssh
- **Version**: 9.6p1 (as of 2024-01)
- **License**: BSD (see LICENSE file)

### OpenSSL Libraries
- **Source**: Termux OpenSSL package
- **Project**: https://github.com/termux/termux-packages
- **Package**: openssl
- **Version**: 3.2.0 (as of 2024-01)
- **License**: Apache 2.0

## Download Instructions

To download the latest binaries from Termux:

1. Visit https://packages.termux.dev/
2. Search for "openssh" and "openssl"
3. Download the appropriate .deb packages for each architecture:
   - aarch64 (ARM64) → arm64-v8a
   - arm (ARM32) → armeabi-v7a
   - x86_64 → x86_64
   - i686 (x86) → x86

4. Extract binaries from .deb packages:
   ```bash
   ar x openssh_*.deb
   tar xf data.tar.xz
   ```

5. Copy binaries to appropriate directories:
   - `usr/bin/ssh` → `jniLibs/<arch>/libssh.so`
   - `usr/lib/libcrypto.so.*` → `jniLibs/<arch>/libcrypto.so`
   - `usr/lib/libssl.so.*` → `jniLibs/<arch>/libssl.so`

## Directory Structure

```
jniLibs/
├── arm64-v8a/          # ARM64 (64-bit ARM)
│   ├── libssh.so       # OpenSSH binary (renamed from ssh)
│   ├── libcrypto.so    # OpenSSL crypto library
│   └── libssl.so       # OpenSSL SSL library
├── armeabi-v7a/        # ARM32 (32-bit ARM)
│   ├── libssh.so
│   ├── libcrypto.so
│   └── libssl.so
├── x86_64/             # x86 64-bit (emulators)
│   ├── libssh.so
│   ├── libcrypto.so
│   └── libssl.so
└── x86/                # x86 32-bit (legacy emulators)
    ├── libssh.so
    ├── libcrypto.so
    └── libssl.so
```

## Binary Checksums

### ARM64 (arm64-v8a)
- libssh.so: `[TO BE UPDATED]`
- libcrypto.so: `[TO BE UPDATED]`
- libssl.so: `[TO BE UPDATED]`

### ARM32 (armeabi-v7a)
- libssh.so: `[TO BE UPDATED]`
- libcrypto.so: `[TO BE UPDATED]`
- libssl.so: `[TO BE UPDATED]`

### x86_64
- libssh.so: `[TO BE UPDATED]`
- libcrypto.so: `[TO BE UPDATED]`
- libssl.so: `[TO BE UPDATED]`

### x86
- libssh.so: `[TO BE UPDATED]`
- libcrypto.so: `[TO BE UPDATED]`
- libssl.so: `[TO BE UPDATED]`

## Verification

To verify binary integrity:

```bash
# Calculate SHA256 checksum
sha256sum jniLibs/arm64-v8a/libssh.so
```

## Updates

When updating binaries:
1. Download new versions from Termux
2. Update version numbers in this file
3. Update checksums
4. Test on all architectures
5. Increment app version code

## Attribution

These binaries are from the Termux project:
- Termux: https://termux.dev/
- Termux Packages: https://github.com/termux/termux-packages
- OpenSSH: https://www.openssh.com/
- OpenSSL: https://www.openssl.org/

## License

- OpenSSH: BSD License
- OpenSSL: Apache License 2.0
- See individual LICENSE files for details

## Notes

- Binaries are compiled for Android with Termux's build system
- No modifications have been made to the binaries
- Binaries are stripped of debug symbols to reduce size
- All binaries are position-independent executables (PIE)
