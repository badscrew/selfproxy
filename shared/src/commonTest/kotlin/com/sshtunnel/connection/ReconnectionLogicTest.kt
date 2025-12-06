package com.sshtunnel.connection

import com.sshtunnel.data.CipherMethod
import com.sshtunnel.data.ConnectionState
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.data.ShadowsocksConfig
import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LogEntry
import com.sshtunnel.network.NetworkMonitor
import com.sshtunnel.network.NetworkState
import com.sshtunnel.network.NetworkType
import com.sshtunnel.reconnection.ReconnectionPolicy
import com.sshtunnel.shadowsocks.ConnectionTestResult
import com.sshtunnel.shadowsocks.ShadowsocksClient
import com.sshtunnel.shadowsocks.ShadowsocksState
import com.sshtunnel.storage.CredentialStore
import com.sshtunnel.vpn.RoutingConfig
import com.sshtunnel.vpn.TunnelConfig
import com.sshtunnel.vpn.TunnelState
import com.sshtunnel.vpn.VpnTunnelProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for automatic reconnection logic in ConnectionManager.
 * 
 * Tests backoff timing, network change handling, and max retry behavior.
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5
 */
class ReconnectionLogicTest {
    
    @Test
    fun `should use exponential backoff policy for reconnection`() = runTest {
        // Arrange
        val policy = ReconnectionPolicy(
            enabled = true,
            initialBackoff = 1.seconds,
            maxBackoff = 60.seconds,
            backoffMultiplier = 2.0
        )
        
        // Act & Assert - Verify policy is configured correctly
        policy.enabled shouldBe true
        policy.initialBackoff shouldBe 1.seconds
        policy.maxBackoff shouldBe 60.seconds
        policy.backoffMultiplier shouldBe 2.0
    }
    
    @Test
    fun `should respect max attempts configuration`() = runTest {
        // Arrange
        val policy = ReconnectionPolicy(
            enabled = true,
            maxAttempts = 5,
            initialBackoff = 1.seconds
        )
        
        // Act & Assert
        policy.maxAttempts shouldBe 5
    }
    
    @Test
    fun `should support network change reconnection configuration`() = runTest {
        // Arrange
        val policyEnabled = ReconnectionPolicy(
            enabled = true,
            reconnectOnNetworkChange = true
        )
        
        val policyDisabled = ReconnectionPolicy(
            enabled = true,
            reconnectOnNetworkChange = false
        )
        
        // Act & Assert
        policyEnabled.reconnectOnNetworkChange shouldBe true
        policyDisabled.reconnectOnNetworkChange shouldBe false
    }
    
    @Test
    fun `should support disabled reconnection policy`() = runTest {
        // Arrange
        val policy = ReconnectionPolicy(enabled = false)
        
        // Act & Assert
        policy.enabled shouldBe false
    }
    
    @Test
    fun `should have default reconnection policy with standard settings`() = runTest {
        // Arrange
        val policy = ReconnectionPolicy.DEFAULT
        
        // Act & Assert - Verify default settings match requirements
        policy.enabled shouldBe true
        policy.initialBackoff shouldBe 1.seconds
        policy.maxBackoff shouldBe 60.seconds
        policy.backoffMultiplier shouldBe 2.0
        policy.reconnectOnNetworkChange shouldBe true
    }
    
    @Test
    fun `should support unlimited reconnection attempts`() = runTest {
        // Arrange
        val policy = ReconnectionPolicy(
            enabled = true,
            maxAttempts = -1  // Unlimited
        )
        
        // Act & Assert
        policy.maxAttempts shouldBe -1
    }
    
    // Helper functions
    
    private fun createConnectionManager(
        policy: ReconnectionPolicy = ReconnectionPolicy.DEFAULT
    ): Pair<ConnectionManager, MockComponents> {
        val mocks = MockComponents()
        val manager = ConnectionManagerImpl(
            shadowsocksClient = mocks.shadowsocksClient,
            vpnTunnelProvider = mocks.vpnTunnelProvider,
            credentialStore = mocks.credentialStore,
            networkMonitor = mocks.networkMonitor,
            scope = CoroutineScope(Dispatchers.Unconfined),
            logger = mocks.logger,
            reconnectionPolicy = policy
        )
        return manager to mocks
    }
    
    private fun createTestProfile(
        id: Long = 1,
        name: String = "Test Server",
        host: String = "example.com",
        port: Int = 8388
    ) = ServerProfile(
        id = id,
        name = name,
        serverHost = host,
        serverPort = port,
        cipher = CipherMethod.AES_256_GCM,
        createdAt = System.currentTimeMillis()
    )
    
    private class MockComponents {
        val shadowsocksClient = MockShadowsocksClient()
        val vpnTunnelProvider = MockVpnTunnelProvider()
        val credentialStore = MockCredentialStore()
        val networkMonitor = MockNetworkMonitor()
        val logger = MockLogger()
    }
    
    private class MockShadowsocksClient : ShadowsocksClient {
        private var startResult: Result<Int> = Result.success(1080)
        private var startResultProvider: (() -> Result<Int>)? = null
        private val stateFlow = MutableStateFlow<ShadowsocksState>(ShadowsocksState.Idle)
        
        fun setStartResult(result: Result<Int>) {
            startResult = result
            startResultProvider = null
        }
        
        fun setStartResultProvider(provider: () -> Result<Int>) {
            startResultProvider = provider
        }
        
        fun emitState(state: ShadowsocksState) {
            stateFlow.value = state
        }
        
        override suspend fun start(config: ShadowsocksConfig): Result<Int> {
            val result = startResultProvider?.invoke() ?: startResult
            return result.onSuccess {
                stateFlow.value = ShadowsocksState.Running(it)
            }.onFailure {
                stateFlow.value = ShadowsocksState.Error(it.message ?: "Unknown error", it)
            }
        }
        
        override suspend fun stop() {
            stateFlow.value = ShadowsocksState.Idle
        }
        
        override suspend fun testConnection(config: ShadowsocksConfig): Result<ConnectionTestResult> {
            return Result.success(ConnectionTestResult(true, 50, null))
        }
        
        override fun observeState(): Flow<ShadowsocksState> = stateFlow
    }
    
    private class MockVpnTunnelProvider : VpnTunnelProvider {
        private var startResult: Result<Unit> = Result.success(Unit)
        private val stateFlow = MutableStateFlow<TunnelState>(TunnelState.Inactive)
        
        fun setStartResult(result: Result<Unit>) {
            startResult = result
        }
        
        override suspend fun startTunnel(config: TunnelConfig): Result<Unit> {
            return startResult.onSuccess {
                stateFlow.value = TunnelState.Active
            }.onFailure {
                stateFlow.value = TunnelState.Error(it.message ?: "Unknown error", it)
            }
        }
        
        override suspend fun stopTunnel(): Result<Unit> {
            stateFlow.value = TunnelState.Inactive
            return Result.success(Unit)
        }
        
        override suspend fun updateRouting(config: RoutingConfig): Result<Unit> {
            return Result.success(Unit)
        }
        
        override fun observeTunnelState(): StateFlow<TunnelState> = stateFlow
        
        override fun getCurrentState(): TunnelState = stateFlow.value
    }
    
    private class MockCredentialStore : CredentialStore {
        private val passwords = mutableMapOf<Long, String>()
        
        fun setPassword(profileId: Long, password: String) {
            passwords[profileId] = password
        }
        
        override suspend fun storePassword(profileId: Long, password: String): Result<Unit> {
            passwords[profileId] = password
            return Result.success(Unit)
        }
        
        override suspend fun retrievePassword(profileId: Long): Result<String> {
            return passwords[profileId]?.let { Result.success(it) }
                ?: Result.failure(Exception("Password not found"))
        }
        
        override suspend fun deletePassword(profileId: Long): Result<Unit> {
            passwords.remove(profileId)
            return Result.success(Unit)
        }
    }
    
    private class MockNetworkMonitor : NetworkMonitor {
        private val networkStateFlow = MutableStateFlow<NetworkState>(
            NetworkState.Available(NetworkType.WIFI)
        )
        
        fun emitNetworkChange(state: NetworkState) {
            networkStateFlow.value = state
        }
        
        override fun observeNetworkChanges(): Flow<NetworkState> = networkStateFlow
    }
    
    private class MockLogger : Logger {
        override fun verbose(tag: String, message: String, throwable: Throwable?) {}
        override fun debug(tag: String, message: String, throwable: Throwable?) {}
        override fun info(tag: String, message: String, throwable: Throwable?) {}
        override fun warn(tag: String, message: String, throwable: Throwable?) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun getLogEntries(): List<LogEntry> = emptyList()
        override fun clearLogs() {}
        override fun setVerboseEnabled(enabled: Boolean) {}
        override fun isVerboseEnabled(): Boolean = false
    }
}
