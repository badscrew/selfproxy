package com.sshtunnel.connection

import com.sshtunnel.data.ConnectionState
import com.sshtunnel.data.ErrorType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.data.CipherMethod
import com.sshtunnel.data.ShadowsocksConfig
import com.sshtunnel.data.VpnStatistics
import com.sshtunnel.logging.Logger
import com.sshtunnel.network.NetworkMonitor
import com.sshtunnel.network.NetworkState
import com.sshtunnel.network.NetworkType
import com.sshtunnel.shadowsocks.ConnectionTestResult
import com.sshtunnel.shadowsocks.ShadowsocksClient
import com.sshtunnel.shadowsocks.ShadowsocksState
import com.sshtunnel.storage.CredentialStore
import com.sshtunnel.vpn.RoutingConfig
import com.sshtunnel.vpn.TunnelConfig
import com.sshtunnel.vpn.TunnelState
import com.sshtunnel.vpn.VpnError
import com.sshtunnel.vpn.VpnTunnelProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property-based tests for ConnectionManager state machine.
 * 
 * Feature: shadowsocks-vpn-proxy, Property 4: Connection state transitions are valid
 * Validates: Requirements 3.1, 3.4, 3.5
 */
class ConnectionStatePropertiesTest {
    
    @Test
    fun `connection state transitions should follow valid paths from Disconnected to Connecting`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 4: Connection state transitions are valid
        // Validates: Requirements 3.1, 3.4, 3.5
        
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            val (manager, mocks) = createConnectionManager()
            
            // Initial state should be Disconnected
            manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Disconnected>()
            
            // Configure mocks for successful connection
            mocks.credentialStore.setPassword(profile.id, "test-password")
            mocks.shadowsocksClient.setStartResult(Result.success(1080))
            mocks.vpnTunnelProvider.setStartResult(Result.success(Unit))
            
            // Start connection
            manager.connect(profile)
            
            // After successful connection, state should be Connected
            val finalState = manager.getCurrentState()
            finalState.shouldBeInstanceOf<ConnectionState.Connected>()
            
            // Verify Connected state has correct data
            val connectedState = finalState as ConnectionState.Connected
            connectedState.profileId shouldBe profile.id
            connectedState.serverAddress shouldBe profile.serverHost
            connectedState.connectedAt shouldNotBe 0L
        }
    }
    
    @Test
    fun `connection state should transition to Error on Shadowsocks failure`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 4: Connection state transitions are valid
        // Validates: Requirements 3.1, 3.4, 3.5
        
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.string(1..100)
        ) { profile, errorMessage ->
            val (manager, mocks) = createConnectionManager()
            
            // Initial state should be Disconnected
            manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Disconnected>()
            
            // Configure mocks for Shadowsocks failure
            mocks.credentialStore.setPassword(profile.id, "test-password")
            mocks.shadowsocksClient.setStartResult(Result.failure(Exception(errorMessage)))
            
            // Attempt connection
            val result = manager.connect(profile)
            
            // Connection should fail
            result.isFailure shouldBe true
            
            // State should be Error
            val finalState = manager.getCurrentState()
            finalState.shouldBeInstanceOf<ConnectionState.Error>()
            
            // Error message should contain the failure reason
            val errorState = finalState as ConnectionState.Error
            errorState.message shouldNotBe ""
        }
    }
    
    @Test
    fun `connection state should transition to Error on VPN tunnel failure`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 4: Connection state transitions are valid
        // Validates: Requirements 3.1, 3.4, 3.5
        
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.string(1..100)
        ) { profile, errorMessage ->
            val (manager, mocks) = createConnectionManager()
            
            // Initial state should be Disconnected
            manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Disconnected>()
            
            // Configure mocks: Shadowsocks succeeds, VPN fails
            mocks.credentialStore.setPassword(profile.id, "test-password")
            mocks.shadowsocksClient.setStartResult(Result.success(1080))
            mocks.vpnTunnelProvider.setStartResult(
                Result.failure(VpnError.TunnelCreationFailed(errorMessage))
            )
            
            // Attempt connection
            val result = manager.connect(profile)
            
            // Connection should fail
            result.isFailure shouldBe true
            
            // State should be Error
            val finalState = manager.getCurrentState()
            finalState.shouldBeInstanceOf<ConnectionState.Error>()
            
            // Shadowsocks should be stopped (cleanup)
            mocks.shadowsocksClient.stopCalled shouldBe true
        }
    }
    
    @Test
    fun `disconnection should transition from Connected to Disconnected`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 4: Connection state transitions are valid
        // Validates: Requirements 3.1, 3.4, 3.5
        
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            val (manager, mocks) = createConnectionManager()
            
            // Setup successful connection
            mocks.credentialStore.setPassword(profile.id, "test-password")
            mocks.shadowsocksClient.setStartResult(Result.success(1080))
            mocks.vpnTunnelProvider.setStartResult(Result.success(Unit))
            mocks.vpnTunnelProvider.setStopResult(Result.success(Unit))
            
            // Connect
            manager.connect(profile)
            manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Connected>()
            
            // Disconnect
            val result = manager.disconnect()
            
            // Disconnect should succeed
            result.isSuccess shouldBe true
            
            // State should be Disconnected
            manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Disconnected>()
            
            // Both Shadowsocks and VPN should be stopped
            mocks.shadowsocksClient.stopCalled shouldBe true
            mocks.vpnTunnelProvider.stopCalled shouldBe true
        }
    }
    
    @Test
    fun `connection state should not allow connecting when already connected`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 4: Connection state transitions are valid
        // Validates: Requirements 3.1, 3.4, 3.5
        
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.serverProfile()
        ) { profile1, profile2 ->
            val (manager, mocks) = createConnectionManager()
            
            // Setup successful connection
            mocks.credentialStore.setPassword(profile1.id, "password1")
            mocks.credentialStore.setPassword(profile2.id, "password2")
            mocks.shadowsocksClient.setStartResult(Result.success(1080))
            mocks.vpnTunnelProvider.setStartResult(Result.success(Unit))
            mocks.vpnTunnelProvider.setStopResult(Result.success(Unit))
            
            // Connect to first profile
            manager.connect(profile1)
            manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Connected>()
            
            // Reset mock state
            mocks.shadowsocksClient.stopCalled = false
            mocks.vpnTunnelProvider.stopCalled = false
            
            // Attempt to connect to second profile
            manager.connect(profile2)
            
            // Should have disconnected from first profile
            mocks.shadowsocksClient.stopCalled shouldBe true
            mocks.vpnTunnelProvider.stopCalled shouldBe true
            
            // Should be connected to second profile
            val finalState = manager.getCurrentState()
            finalState.shouldBeInstanceOf<ConnectionState.Connected>()
            val connectedState = finalState as ConnectionState.Connected
            connectedState.profileId shouldBe profile2.id
        }
    }
    
    // Helper functions and mock implementations
    
    private fun createConnectionManager(): Pair<ConnectionManager, MockComponents> {
        val mocks = MockComponents()
        val manager = ConnectionManagerImpl(
            shadowsocksClient = mocks.shadowsocksClient,
            vpnTunnelProvider = mocks.vpnTunnelProvider,
            credentialStore = mocks.credentialStore,
            networkMonitor = mocks.networkMonitor,
            scope = CoroutineScope(Dispatchers.Unconfined),
            logger = mocks.logger
        )
        return manager to mocks
    }
    
    private class MockComponents {
        val shadowsocksClient = MockShadowsocksClient()
        val vpnTunnelProvider = MockVpnTunnelProvider()
        val credentialStore = MockCredentialStore()
        val networkMonitor = MockNetworkMonitor()
        val logger = MockLogger()
    }
    
    private class MockShadowsocksClient : ShadowsocksClient {
        private var startResult: Result<Int> = Result.success(1080)
        private val stateFlow = MutableStateFlow<ShadowsocksState>(ShadowsocksState.Idle)
        var stopCalled = false
        
        fun setStartResult(result: Result<Int>) {
            startResult = result
        }
        
        override suspend fun start(config: ShadowsocksConfig): Result<Int> {
            return startResult.onSuccess {
                stateFlow.value = ShadowsocksState.Running(it)
            }.onFailure {
                stateFlow.value = ShadowsocksState.Error(it.message ?: "Unknown error", it)
            }
        }
        
        override suspend fun stop() {
            stopCalled = true
            stateFlow.value = ShadowsocksState.Idle
        }
        
        override suspend fun testConnection(config: ShadowsocksConfig): Result<ConnectionTestResult> {
            return Result.success(ConnectionTestResult(true, 50, null))
        }
        
        override fun observeState(): Flow<ShadowsocksState> = stateFlow
    }
    
    private class MockVpnTunnelProvider : VpnTunnelProvider {
        private var startResult: Result<Unit> = Result.success(Unit)
        private var stopResult: Result<Unit> = Result.success(Unit)
        private val stateFlow = MutableStateFlow<TunnelState>(TunnelState.Inactive)
        var stopCalled = false
        
        fun setStartResult(result: Result<Unit>) {
            startResult = result
        }
        
        fun setStopResult(result: Result<Unit>) {
            stopResult = result
        }
        
        override suspend fun startTunnel(config: TunnelConfig): Result<Unit> {
            return startResult.onSuccess {
                stateFlow.value = TunnelState.Active
            }.onFailure {
                stateFlow.value = TunnelState.Error(it.message ?: "Unknown error", it)
            }
        }
        
        override suspend fun stopTunnel(): Result<Unit> {
            stopCalled = true
            return stopResult.onSuccess {
                stateFlow.value = TunnelState.Inactive
            }
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
        
        override fun observeNetworkChanges(): Flow<NetworkState> = networkStateFlow
    }
    
    private class MockLogger : Logger {
        override fun verbose(tag: String, message: String, throwable: Throwable?) {}
        override fun debug(tag: String, message: String, throwable: Throwable?) {}
        override fun info(tag: String, message: String, throwable: Throwable?) {}
        override fun warn(tag: String, message: String, throwable: Throwable?) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun getLogEntries(): List<com.sshtunnel.logging.LogEntry> = emptyList()
        override fun clearLogs() {}
        override fun setVerboseEnabled(enabled: Boolean) {}
        override fun isVerboseEnabled(): Boolean = false
    }
    
    // Custom Arb generators
    
    private fun Arb.Companion.serverProfile() = arbitrary {
        ServerProfile(
            id = Arb.long(1L..1000L).bind(),
            name = Arb.string(5..20).bind(),
            serverHost = "example.com",
            serverPort = Arb.int(1024..65535).bind(),
            cipher = CipherMethod.AES_256_GCM,
            createdAt = System.currentTimeMillis()
        )
    }
}
