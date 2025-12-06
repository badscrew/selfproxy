package com.sshtunnel.mocks

import com.sshtunnel.data.ServerProfile
import com.sshtunnel.ssh.SSHClient
import com.sshtunnel.ssh.SSHError
import com.sshtunnel.ssh.SSHSession
import com.sshtunnel.storage.PrivateKey
import java.net.UnknownHostException
import java.util.UUID
import kotlin.time.Duration

/**
 * Mock implementation of SSHClient for testing.
 * 
 * This mock simulates SSH connection behavior without requiring a real SSH server.
 * It can be configured to simulate various success and failure scenarios.
 */
class MockSSHClient : SSHClient {
    
    private val activeSessions = mutableMapOf<String, MockSession>()
    
    // Configuration for simulating different scenarios
    var shouldFailConnection = false
    var connectionError: SSHError? = null
    var shouldFailPortForwarding = false
    var portForwardingError: SSHError? = null
    var simulateUnknownHost = false
    var simulateTimeout = false
    var simulateHostUnreachable = false
    
    data class MockSession(
        val session: SSHSession,
        var isConnected: Boolean = true,
        var socksPort: Int = 0
    )
    
    override suspend fun connect(
        profile: ServerProfile,
        privateKey: PrivateKey,
        passphrase: String?,
        connectionTimeout: Duration,
        enableCompression: Boolean,
        strictHostKeyChecking: Boolean
    ): Result<SSHSession> {
        // Simulate unknown host error
        if (simulateUnknownHost) {
            return Result.failure(
                SSHError.UnknownHost(
                    "Cannot resolve hostname: ${profile.hostname}"
                )
            )
        }
        
        // Simulate timeout error
        if (simulateTimeout) {
            return Result.failure(
                SSHError.ConnectionTimeout(
                    "Connection timed out after ${connectionTimeout.inWholeSeconds} seconds"
                )
            )
        }
        
        // Simulate host unreachable error
        if (simulateHostUnreachable) {
            return Result.failure(
                SSHError.HostUnreachable(
                    "Host unreachable: ${profile.hostname}"
                )
            )
        }
        
        // Simulate custom connection error
        if (shouldFailConnection) {
            return Result.failure(
                connectionError ?: SSHError.Unknown("Connection failed")
            )
        }
        
        // Create successful mock session
        val sessionId = UUID.randomUUID().toString()
        val session = SSHSession(
            sessionId = sessionId,
            serverAddress = profile.hostname,
            serverPort = profile.port,
            username = profile.username,
            socksPort = 0,
            nativeSession = Any() // Mock native session
        )
        
        activeSessions[sessionId] = MockSession(session)
        
        return Result.success(session)
    }
    
    override suspend fun createPortForwarding(
        session: SSHSession,
        localPort: Int
    ): Result<Int> {
        val mockSession = activeSessions[session.sessionId]
            ?: return Result.failure(
                SSHError.SessionClosed("Session not found")
            )
        
        if (!mockSession.isConnected) {
            return Result.failure(
                SSHError.SessionClosed("Session is not connected")
            )
        }
        
        // Simulate port forwarding error
        if (shouldFailPortForwarding) {
            return Result.failure(
                portForwardingError ?: SSHError.PortForwardingDisabled(
                    "Port forwarding disabled"
                )
            )
        }
        
        // Assign a mock SOCKS port
        val assignedPort = if (localPort > 0) localPort else 1080
        mockSession.socksPort = assignedPort
        
        return Result.success(assignedPort)
    }
    
    override suspend fun sendKeepAlive(session: SSHSession): Result<Unit> {
        val mockSession = activeSessions[session.sessionId]
            ?: return Result.failure(
                SSHError.SessionClosed("Session not found")
            )
        
        if (!mockSession.isConnected) {
            return Result.failure(
                SSHError.SessionClosed("Session is not connected")
            )
        }
        
        return Result.success(Unit)
    }
    
    override suspend fun disconnect(session: SSHSession): Result<Unit> {
        val mockSession = activeSessions[session.sessionId]
            ?: return Result.success(Unit) // Already disconnected
        
        mockSession.isConnected = false
        activeSessions.remove(session.sessionId)
        
        return Result.success(Unit)
    }
    
    override fun isConnected(session: SSHSession): Boolean {
        return activeSessions[session.sessionId]?.isConnected ?: false
    }
    
    override fun isSessionAlive(session: SSHSession): Boolean {
        return isConnected(session)
    }
    
    /**
     * Resets the mock to default state (useful for test cleanup).
     */
    fun reset() {
        activeSessions.clear()
        shouldFailConnection = false
        connectionError = null
        shouldFailPortForwarding = false
        portForwardingError = null
        simulateUnknownHost = false
        simulateTimeout = false
        simulateHostUnreachable = false
    }
    
    /**
     * Returns the number of active sessions (useful for test assertions).
     */
    fun activeSessionCount(): Int = activeSessions.count { it.value.isConnected }
}
