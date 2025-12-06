package com.sshtunnel.ssh

import com.sshtunnel.data.ServerProfile

/**
 * Builds SSH command with appropriate options for dynamic port forwarding.
 */
interface SSHCommandBuilder {
    /**
     * Build SSH command for dynamic port forwarding.
     * 
     * @param binaryPath Path to SSH binary
     * @param profile Server profile with connection details
     * @param privateKeyPath Path to private key file
     * @param localPort Local SOCKS5 port
     * @return List of command arguments ready for ProcessBuilder
     */
    fun buildCommand(
        binaryPath: String,
        profile: ServerProfile,
        privateKeyPath: String,
        localPort: Int
    ): List<String>
}
