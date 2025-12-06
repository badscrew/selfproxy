# Native SSH Security Considerations

## Overview

The native SSH implementation includes comprehensive security hardening to protect against common vulnerabilities and ensure safe operation. This document outlines the security measures implemented and best practices for maintaining security.

## Security Features

### 1. Argument Validation

All SSH command arguments are validated before execution to prevent command injection and other attacks:

#### Hostname Validation
- **Format**: Must conform to RFC 1123 hostname format
- **Length**: Maximum 253 characters
- **Characters**: Only alphanumeric, hyphens, and dots allowed
- **Dangerous Characters**: Rejects `;`, `&`, `|`, `` ` ``, `$`, `(`, `)`, `<`, `>`, newlines

#### Username Validation
- **Format**: POSIX portable filename character set
- **Length**: Maximum 32 characters
- **Characters**: Only alphanumeric, dots, underscores, and hyphens allowed

#### Port Validation
- **Range**: 1-65535 (valid TCP port range)
- **Type**: Integer validation

#### Path Validation
- **Traversal Prevention**: Rejects paths containing `..`
- **Absolute Paths**: Requires absolute paths starting with `/`
- **Length**: Maximum 4096 characters
- **Existence**: Verifies file exists and is readable
- **Dangerous Characters**: Rejects shell metacharacters

### 2. Output Sanitization

All SSH process output is sanitized before logging to prevent information leakage:

#### Sensitive Patterns Redacted
- Private keys (PEM format)
- Passwords and passphrases
- SSH public keys (RSA, Ed25519, ECDSA)
- Authentication tokens
- Any pattern matching `password=`, `passphrase=`, `key=`

#### Sanitization Process
```kotlin
// Before logging
val rawOutput = sshProcess.readLine()
val sanitizedOutput = securityValidator.sanitizeOutput(rawOutput)
logger.info(TAG, sanitizedOutput)
```

### 3. Safe Error Messages

Error messages are sanitized and made user-friendly to prevent information disclosure:

#### Technical to User-Friendly Mapping
- `Connection refused` → "Unable to connect to SSH server. Please check the hostname and port."
- `Connection timed out` → "Connection timed out. Please check your network connection."
- `Authentication failed` → "Authentication failed. Please check your username and private key."
- `Host key verification failed` → "Server host key verification failed."
- `Permission denied` → "Permission denied. Please check your credentials."

#### Error Message Limits
- Maximum 100 characters for user-facing messages
- Technical details logged separately (sanitized)
- No stack traces in user-facing messages

### 4. Binary Integrity Verification

SSH binaries are verified for integrity and security before execution:

#### Verification Checks
1. **File Existence**: Binary file must exist
2. **File Type**: Must be a regular file (not symlink or directory)
3. **Permissions**: Must be executable and readable
4. **Location**: Must be in app's private directory (`/data/data/`)
5. **Checksum**: SHA-256 checksum verification (on first extraction)
6. **Not World-Writable**: Ensures binary cannot be modified by other apps

#### Verification Process
```kotlin
// Verify binary before use
val binaryPath = binaryManager.extractBinary(architecture).getOrThrow()
val integrityCheck = securityValidator.validateBinaryIntegrity(binaryPath)
if (integrityCheck.isFailure) {
    throw SecurityException("Binary integrity validation failed")
}
```

### 5. Command Array Validation

SSH command arrays are validated to prevent command injection:

#### Validation Checks
- No null bytes (`\0`) in arguments
- No newlines (`\n`, `\r`) in arguments
- Warning on shell expansion patterns (`$(`, `` ` ``)
- Non-empty command array
- All arguments are strings

### 6. Private Key Protection

Private keys are handled securely throughout their lifecycle:

#### Storage
- Written to app's private directory only
- File permissions set to owner read/write only (600)
- Never logged or exposed in error messages

#### Cleanup
- Deleted immediately after SSH connection terminates
- Deleted on connection failure
- Deleted on app uninstall (automatic with app data)

#### Memory
- Key data cleared from memory after writing to disk
- No key data in exception messages

## Security Best Practices

### For Developers

1. **Never Log Sensitive Data**
   ```kotlin
   // ❌ BAD
   logger.info(TAG, "Using key: $privateKey")
   
   // ✅ GOOD
   logger.info(TAG, "Using key type: ${privateKey.keyType}")
   ```

2. **Always Sanitize Output**
   ```kotlin
   // ❌ BAD
   logger.info(TAG, sshProcess.readLine())
   
   // ✅ GOOD
   val sanitized = securityValidator.sanitizeOutput(sshProcess.readLine())
   logger.info(TAG, sanitized)
   ```

3. **Use Safe Error Messages**
   ```kotlin
   // ❌ BAD
   throw Exception("Connection failed: ${error.stackTrace}")
   
   // ✅ GOOD
   val safeMessage = securityValidator.generateSafeErrorMessage(error)
   throw Exception(safeMessage)
   ```

4. **Validate All Inputs**
   ```kotlin
   // ✅ GOOD
   val validation = securityValidator.validateSSHArguments(profile, keyPath, port)
   if (validation.isFailure) {
       throw validation.exceptionOrNull()!!
   }
   ```

### For Users

1. **Use Strong SSH Keys**
   - Prefer Ed25519 keys (most secure)
   - Use RSA keys with at least 2048 bits
   - Avoid DSA keys (deprecated)

2. **Protect Your Private Keys**
   - Never share private keys
   - Use passphrase protection when possible
   - Store keys securely on your device

3. **Verify Server Identity**
   - Check server fingerprints on first connection
   - Be cautious of host key change warnings

4. **Keep App Updated**
   - Install updates promptly for security patches
   - Monitor for security advisories

## Threat Model

### Protected Against

✅ **Command Injection**: Argument validation prevents shell metacharacters
✅ **Path Traversal**: Path validation prevents `../` attacks
✅ **Information Leakage**: Output sanitization removes sensitive data
✅ **Binary Tampering**: Integrity verification ensures binary authenticity
✅ **Credential Theft**: Encrypted storage and secure cleanup
✅ **MITM Attacks**: SSH encryption and host key verification

### Not Protected Against

❌ **Compromised SSH Server**: User's responsibility to secure their server
❌ **Malicious Apps**: Android sandbox provides isolation
❌ **Physical Device Access**: Requires device encryption
❌ **Rooted Devices**: Root access bypasses app sandbox
❌ **Network Traffic Analysis**: Use VPN or Tor for additional privacy

## Security Checklist

Before releasing code:

- [ ] All user inputs validated
- [ ] All SSH output sanitized before logging
- [ ] Error messages use safe generation
- [ ] Binary integrity verified before execution
- [ ] Private keys cleaned up on all code paths
- [ ] No sensitive data in logs
- [ ] No hardcoded credentials
- [ ] All file paths validated
- [ ] Command arrays validated
- [ ] Security tests passing

## Incident Response

If a security vulnerability is discovered:

1. **Assess Impact**: Determine severity and affected users
2. **Develop Fix**: Create and test security patch
3. **Release Update**: Push emergency update if critical
4. **Notify Users**: Transparent communication about the issue
5. **Document**: Update security documentation
6. **Review**: Conduct post-mortem to prevent similar issues

## Security Testing

### Automated Tests

Run security-focused tests:

```bash
# Run all security tests
./gradlew shared:testDebugUnitTest --tests "*Security*"

# Run argument validation tests
./gradlew shared:testDebugUnitTest --tests "*ArgumentValidation*"

# Run sanitization tests
./gradlew shared:testDebugUnitTest --tests "*Sanitization*"
```

### Manual Security Testing

1. **Command Injection Testing**
   - Try hostnames with shell metacharacters
   - Try usernames with special characters
   - Verify all attempts are rejected

2. **Path Traversal Testing**
   - Try paths with `../`
   - Try absolute paths outside app directory
   - Verify all attempts are rejected

3. **Output Sanitization Testing**
   - Generate SSH output with fake private keys
   - Verify keys are redacted in logs
   - Check log files for sensitive data

4. **Binary Integrity Testing**
   - Modify binary file
   - Verify integrity check fails
   - Verify app refuses to use modified binary

## Security Updates

### Binary Updates

When updating OpenSSH binaries:

1. Download from trusted source (Termux)
2. Verify checksums match official releases
3. Update checksum constants in code
4. Test thoroughly before release
5. Document version and source in commit

### Dependency Updates

Monitor security advisories for:
- Kotlin coroutines
- Android libraries
- Build tools
- Gradle plugins

## Compliance

### Data Privacy

- **No Data Collection**: App does not collect or transmit user data
- **Local Storage Only**: All data stored locally on device
- **No Analytics**: No usage tracking or analytics
- **No Ads**: No advertising or tracking SDKs

### Open Source

- **Source Available**: Full source code available for audit
- **License**: Apache 2.0 license
- **Contributions**: Security contributions welcome
- **Disclosure**: Responsible disclosure policy

## Resources

- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [SSH Protocol Security](https://www.ssh.com/academy/ssh/protocol)
- [OpenSSH Security Advisories](https://www.openssh.com/security.html)

## Contact

For security issues, please contact:
- Email: [security contact]
- GitHub: [Create private security advisory]

**Do not disclose security vulnerabilities publicly until a fix is available.**
