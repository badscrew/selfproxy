package com.sshtunnel.connection

import com.sshtunnel.data.CipherMethod
import com.sshtunnel.data.ConnectionState
import com.sshtunnel.data.ErrorType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.data.ShadowsocksConfig
import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LogEntry
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Unit tests for ConnectionManager.
 * 
 * Tests connect flow, disconnect flow, error handling, and state transitions.
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
class ConnectionManagerTest {
    
    @Test
    fun `connect should successfully establish connection with valid credentials`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile = createTestProfile()
        
        mocks.credentialStore.setPassword(profile.id, "test-password")
        mocks.shadowsocksClient.setStartResult(Result.success(1080))
        mocks.vpnTunnelProvider.setStartResult(Result.success(Unit))
        
        // Act
        val result = manager.connect(profile)
        
        // Assert
        result.isSuccess shouldBe true
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Connected>()
        
        val connectedState = manager.getCurrentState() as ConnectionState.Connected
        connectedState.profileId shouldBe profile.id
        connectedState.serverAddress shouldBe profile.serverHost
    }
    
    @Test
    fun `connect should fail when password retrieval fails`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile = createTestProfile()
        
        // Don't set password - retrieval will fail
        
        // Act
        val result = manager.connect(profile)
        
        // Assert
        result.isFailure shouldBe true
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Error>()
        
        val errorState = manager.getCurrentState() as ConnectionState.Error
        errorState.errorType shouldBe ErrorType.AUTHENTICATION_FAILED
    }
    
    @Test
    fun `connect should fail when Shadowsocks client fails to start`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile = createTestProfile()
        
        mocks.credentialStore.setPassword(profile.id, "test-password")
        mocks.shadowsocksClient.setStartResult(
            Result.failure(Exception("Server unreachable"))
        )
        
        // Act
        val result = manager.connect(profile)
        
        // Assert
        result.isFailure shouldBe true
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Error>()
        
        val errorState = manager.getCurrentState() as ConnectionState.Error
        errorState.errorType shouldBe ErrorType.SERVER_UNREACHABLE
    }
    
    @Test
    fun `connect should fail and cleanup when VPN tunnel fails to start`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile = createTestProfile()
        
        mocks.credentialStore.setPassword(profile.id, "test-password")
        mocks.shadowsocksClient.setStartResult(Result.success(1080))
        mocks.vpnTunnelProvider.setStartResult(
            Result.failure(VpnError.PermissionDenied("VPN permission denied"))
        )
        
        // Act
        val result = manager.connect(profile)
        
        // Assert
        result.isFailure shouldBe true
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Error>()
        
        val errorState = manager.getCurrentState() as ConnectionState.Error
        errorState.errorType shouldBe ErrorType.VPN_PERMISSION_DENIED
        
        // Shadowsocks should be stopped (cleanup)
        mocks.shadowsocksClient.stopCalled shouldBe true
    }
    
    @Test
    fun `disconnect should successfully stop connection`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile = createTestProfile()
        
        // Setup successful connection
        mocks.credentialStore.setPassword(profile.id, "test-password")
        mocks.shadowsocksClient.setStartResult(Result.success(1080))
        mocks.vpnTunnelProvider.setStartResult(Result.success(Unit))
        mocks.vpnTunnelProvider.setStopResult(Result.success(Unit))
        
        manager.connect(profile)
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Connected>()
        
        // Act
        val result = manager.disconnect()
        
        // Assert
        result.isSuccess shouldBe true
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Disconnected>()
        
        // Both Shadowsocks and VPN should be stopped
        mocks.shadowsocksClient.stopCalled shouldBe true
        mocks.vpnTunnelProvider.stopCalled shouldBe true
    }
    
    @Test
    fun `disconnect should succeed even when VPN stop fails`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile = createTestProfile()
        
        // Setup successful connection
        mocks.credentialStore.setPassword(profile.id, "test-password")
        mocks.shadowsocksClient.setStartResult(Result.success(1080))
        mocks.vpnTunnelProvider.setStartResult(Result.success(Unit))
        mocks.vpnTunnelProvider.setStopResult(
            Result.failure(Exception("VPN stop failed"))
        )
        
        manager.connect(profile)
        
        // Act
        val result = manager.disconnect()
        
        // Assert - should still succeed and clean up
        result.isSuccess shouldBe true
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Disconnected>()
        mocks.shadowsocksClient.stopCalled shouldBe true
    }
    
    @Test
    fun `testConnection should return success for valid configuration`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile = createTestProfile()
        
        mocks.credentialStore.setPassword(profile.id, "test-password")
        mocks.shadowsocksClient.setTestResult(
            Result.success(ConnectionTestResult(true, 50, null))
        )
        
        // Act
        val result = manager.testConnection(profile)
        
        // Assert
        result.isSuccess shouldBe true
        val testResult = result.getOrNull()
        testResult shouldNotBe null
        testResult?.success shouldBe true
        testResult?.latencyMs shouldBe 50
    }
    
    @Test
    fun `testConnection should return failure when password retrieval fails`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile = createTestProfile()
        
        // Don't set password
        
        // Act
        val result = manager.testConnection(profile)
        
        // Assert
        result.isSuccess shouldBe true // Returns success with failure result
        val testResult = result.getOrNull()
        testResult shouldNotBe null
        testResult?.success shouldBe false
        testResult?.errorMessage shouldNotBe null
    }
    
    @Test
    fun `state transitions should be correct during connection lifecycle`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile = createTestProfile()
        
        mocks.credentialStore.setPassword(profile.id, "test-password")
        mocks.shadowsocksClient.setStartResult(Result.success(1080))
        mocks.vpnTunnelProvider.setStartResult(Result.success(Unit))
        mocks.vpnTunnelProvider.setStopResult(Result.success(Unit))
        
        // Initial state
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Disconnected>()
        
        // Connect
        manager.connect(profile)
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Connected>()
        
        // Disconnect
        manager.disconnect()
        manager.getCurrentState().shouldBeInstanceOf<ConnectionState.Disconnected>()
    }
    
    @Test
    fun `connecting when already connected should disconnect first`() = runTest {
        // Arrange
        val (manager, mocks) = createConnectionManager()
        val profile1 = createTestProfile(id = 1)
        val profile2 = createTestProfile(id = 2)
        
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
        
        // Connect to second profile
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
    
    @Test
    fun `error handling should properly categorize different error types`() = runTest {
        // Test server unreachable error
        val (manager1, mocks1) = createConnectionManager()
        val profile = createTestProfile()
        
        mocks1.credentialStore.setPassword(profile.id, "test-password")
        mocks1.shadowsocksClient.setStartResult(
            Result.failure(Exception("Connection refused - server unreachable"))
        )
        
        manager1.connect(profile)
        val errorState1 = manager1.getCurrentState() as ConnectionState.Error
        errorState1.errorType shouldBe ErrorType.SERVER_UNREACHABLE
        
        // Test authentication error
        val (manager2, mocks2) = createConnectionManager()
        mocks2.credentialStore.setPassword(profile.id, "test-password")
        mocks2.shadowsocksClient.setStartResult(
            Result.failure(Exception("Authentication failed"))
        )
        
        manager2.connect(profile)
        val errorState2 = manager2.getCurrentState() as ConnectionState.Error
        errorState2.errorType shouldBe ErrorType.AUTHENTICATION_FAILED
        
        // Test cipher error
        val (manager3, mocks3) = createConnectionManager()
        mocks3.credentialStore.setPassword(profile.id, "test-password")
        mocks3.shadowsocksClient.setStartResult(
            Result.failure(Exception("Unsupported cipher method"))
        )
        
        manager3.connect(profile)
        val errorState3 = manager3.getCurrentState() as ConnectionState.Error
        errorState3.errorType shouldBe ErrorType.UNSUPPORTED_CIPHER
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
        private var testResult: Result<ConnectionTestResult> = Result.success(
            ConnectionTestResult(true, 50, null)
        )
        private val stateFlow = MutableStateFlow<ShadowsocksState>(ShadowsocksState.Idle)
        var stopCalled = false
        
        fun setStartResult(result: Result<Int>) {
            startResult = result
        }
        
        fun setTestResult(result: Result<ConnectionTestResult>) {
            testResult = result
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
            return testResult
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
