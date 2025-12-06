package com.sshtunnel.ssh

import com.sshtunnel.data.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// TODO: This is a temporary stub - will be replaced with Shadowsocks implementation in task 11
interface SSHConnectionManager {
    suspend fun connect(profile: ServerProfile): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    fun observeConnectionState(): Flow<ConnectionState>
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val connection: Connection) : ConnectionState()
    data class Error(val error: ConnectionError) : ConnectionState()
}

data class Connection(
    val profileId: Long,
    val socksPort: Int,
    val sessionId: String
)

sealed class ConnectionError(open val message: String) {
    data class AuthenticationFailed(override val message: String) : ConnectionError(message)
    data class ConnectionTimeout(override val message: String) : ConnectionError(message)
    data class HostUnreachable(override val message: String) : ConnectionError(message)
    data class PortForwardingDisabled(override val message: String) : ConnectionError(message)
    data class InvalidKey(override val message: String) : ConnectionError(message)
    data class UnknownHost(override val message: String) : ConnectionError(message)
    data class NetworkUnavailable(override val message: String) : ConnectionError(message)
    data class CredentialError(override val message: String) : ConnectionError(message)
    data class Unknown(override val message: String) : ConnectionError(message)
}
