package com.sshtunnel.ssh

import com.sshtunnel.data.ServerProfile

/**
 * Android implementation of SSH command builder.
 * 
 * Constructs SSH commands with:
 * - Dynamic port forwarding (-D)
 * - No remote command execution (-N)
 * - No pseudo-terminal (-T)
 * - Keep-alive settings (ServerAliveInterval, ServerAliveCountMax)
 * - Connection options (ExitOnForwardFailure, ConnectTimeout)
 * - Private key authentication (-i)
 */
class AndroidSSHCommandBuilder : SSHCommandBuilder {
    
    override fun buildCommand(
        binaryPath: String,
        profile: ServerProfile,
        privateKeyPath: String,
        localPort: Int
    ): List<String> {
        return buildList {
            // SSH binary path
            add(binaryPath)
            
            // Dynamic port forwarding - creates SOCKS5 proxy on local port
            add("-D")
            add(localPort.toString())
            
            // No remote command execution
            add("-N")
            
            // No pseudo-terminal allocation
            add("-T")
            
            // Private key authentication
            add("-i")
            add(privateKeyPath)
            
            // Keep-alive settings - send keep-alive every 60 seconds
            add("-o")
            add("ServerAliveInterval=60")
            
            // Keep-alive settings - allow 10 missed keep-alives before disconnecting
            add("-o")
            add("ServerAliveCountMax=10")
            
            // Exit if port forwarding fails
            add("-o")
            add("ExitOnForwardFailure=yes")
            
            // Connection timeout - 30 seconds
            add("-o")
            add("ConnectTimeout=30")
            
            // Port specification
            add("-p")
            add(profile.port.toString())
            
            // User@host
            add("${profile.username}@${profile.hostname}")
        }
    }
}
