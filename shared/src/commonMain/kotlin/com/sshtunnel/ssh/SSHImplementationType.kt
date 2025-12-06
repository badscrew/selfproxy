package com.sshtunnel.ssh

/**
 * SSH implementation type.
 * 
 * Determines which SSH client implementation to use for connections.
 */
enum class SSHImplementationType {
    /**
     * Native OpenSSH binary implementation.
     * 
     * Uses the bundled OpenSSH binary for SSH connections.
     * Provides better reliability and performance but requires
     * native binaries for each architecture.
     */
    NATIVE,
    
    /**
     * Java sshj library implementation.
     * 
     * Uses the pure Java sshj library for SSH connections.
     * Works on all platforms but may have reliability issues.
     */
    SSHJ,
    
    /**
     * Automatic selection based on availability.
     * 
     * Prefers native implementation when available,
     * falls back to sshj if native is not available.
     */
    AUTO
}
