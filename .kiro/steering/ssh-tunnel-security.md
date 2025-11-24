---
inclusion: always
---

# SSH Tunnel Security Best Practices

## Credential Storage

### Android Keystore Usage

Always use Android Keystore for encrypting SSH private keys:

```kotlin
class AndroidCredentialStore {
    private val keyAlias = "ssh_key_encryption"
    
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        return if (keyStore.containsAlias(keyAlias)) {
            (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            )
            keyGenerator.generateKey()
        }
    }
}
```

### Key Storage Rules

1. **Never store private keys in plaintext**
2. **Never log private keys or passphrases**
3. **Use hardware-backed encryption when available**
4. **Clear sensitive data from memory after use**
5. **Delete keys when profiles are deleted**

## SSH Connection Security

### Host Key Verification

Implement strict host key checking to prevent MITM attacks:

```kotlin
class SSHConnectionManager {
    suspend fun connect(profile: ServerProfile): Result<Connection> {
        if (profile.strictHostKeyChecking) {
            val knownHosts = loadKnownHosts()
            if (!knownHosts.contains(profile.hostname)) {
                return Result.failure(UnknownHostException())
            }
        }
        // Continue connection
    }
}
```

### Supported Key Types

Only support modern, secure key types:
- ✅ Ed25519 (preferred)
- ✅ ECDSA (acceptable)
- ✅ RSA 2048+ bits (acceptable)
- ❌ RSA < 2048 bits (reject)
- ❌ DSA (reject)

### Connection Configuration

```kotlin
val secureSSHConfig = SSHConfig(
    strictHostKeyChecking = true,
    serverAliveInterval = 60, // Keep-alive
    serverAliveCountMax = 3,
    compression = false, // Disable unless needed
    preferredAuthentications = "publickey" // Key-only auth
)
```

## VPN Security

### DNS Leak Prevention

Always route DNS through the tunnel:

```kotlin
class AndroidVpnTunnelProvider {
    fun configureTunnel(builder: VpnService.Builder) {
        builder
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0) // Route all traffic
            .addDnsServer("8.8.8.8") // Use tunnel DNS
            .setBlocking(true)
    }
}
```

### App Exclusions

Be careful with app exclusions - excluded apps bypass the tunnel:

```kotlin
// Only exclude apps that explicitly need direct connection
fun configureAppRouting(builder: VpnService.Builder, config: RoutingConfig) {
    config.excludedApps.forEach { packageName ->
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            // Log but don't fail
        }
    }
}
```

## Data Privacy

### No Data Collection

The application must not collect or transmit any user data:

```kotlin
// ❌ NEVER do this
fun logConnectionDetails(profile: ServerProfile) {
    analytics.log("connection", mapOf(
        "hostname" to profile.hostname, // NO!
        "username" to profile.username  // NO!
    ))
}

// ✅ Only log non-sensitive information
fun logConnectionEvent() {
    logger.info("Connection attempt started") // OK
}
```

### Log Sanitization

Always sanitize logs to remove sensitive data:

```kotlin
fun sanitizeForLogging(error: SSHException): String {
    return error.message
        ?.replace(Regex("password=\\S+"), "password=***")
        ?.replace(Regex("key=\\S+"), "key=***")
        ?: "Unknown error"
}
```

## Network Security

### TLS for External Services

When testing connections or checking IP:

```kotlin
val httpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 10000
    }
    engine {
        // Enforce TLS 1.2+
        sslContext = SSLContext.getInstance("TLSv1.2")
    }
}
```

### Certificate Pinning (Optional)

For critical external services, consider certificate pinning:

```kotlin
val pinnedClient = HttpClient {
    engine {
        config {
            certificatePinner {
                add("api.example.com", "sha256/AAAAAAAAAA...")
            }
        }
    }
}
```

## Threat Model

### Protected Against

- ✅ Network eavesdropping (SSH encryption)
- ✅ ISP tracking (traffic routed through user's server)
- ✅ DNS leaks (DNS through tunnel)
- ✅ Credential theft (encrypted storage)
- ✅ MITM attacks (host key verification)

### Not Protected Against

- ❌ Compromised SSH server (user responsibility)
- ❌ Malicious apps on device (Android sandbox)
- ❌ Physical device access (requires device encryption)
- ❌ Traffic analysis at server exit point

## Security Checklist

Before releasing any code:

- [ ] Private keys encrypted with Android Keystore
- [ ] No sensitive data in logs
- [ ] No analytics or tracking code
- [ ] DNS routed through tunnel
- [ ] Host key verification implemented
- [ ] Weak key types rejected
- [ ] Credentials cleared from memory after use
- [ ] All network calls use HTTPS
- [ ] No hardcoded credentials or keys
- [ ] Open source code available for audit

## Incident Response

If a security issue is discovered:

1. **Assess Impact**: Determine what data/users are affected
2. **Patch Immediately**: Fix the vulnerability
3. **Notify Users**: Transparent communication about the issue
4. **Update Documentation**: Document the issue and fix
5. **Review Process**: Prevent similar issues in the future

## Resources

- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [SSH Protocol Security](https://www.ssh.com/academy/ssh/protocol)
