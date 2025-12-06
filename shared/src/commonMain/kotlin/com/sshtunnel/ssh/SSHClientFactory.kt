package com.sshtunnel.ssh

import com.sshtunnel.logging.Logger

/**
 * Factory for creating SSH client instances.
 * 
 * Determines which SSH implementation to use based on availability and user preference.
 * Supports both native OpenSSH binary and sshj Java library implementations.
 */
interface SSHClientFactory {
    /**
     * Create an SSH client instance.
     * 
     * @param logger Logger for diagnostic output
     * @param implementationType SSH implementation type preference
     * @return SSH client instance (native or sshj)
     */
    fun create(
        logger: Logger,
        implementationType: SSHImplementationType = SSHImplementationType.AUTO
    ): SSHClient
    
    /**
     * Check if native SSH is available.
     * 
     * @return true if native SSH binary is available for this device architecture
     */
    fun isNativeSSHAvailable(): Boolean
}
