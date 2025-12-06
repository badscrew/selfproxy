package com.sshtunnel.ssh

import android.content.Context
import android.os.Build
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LogEntry
import com.sshtunnel.logging.LogLevel
import com.sshtunnel.storage.PrivateKey
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based tests for Native SSH Client functionality.
 * 
 * Tests native SSH client behavior including:
 * - Process termination detection
 * - Connection state updates
 * - Resource cleanup
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class NativeSSHClientPropertiesTest {
    
    private lateinit var context: Context
    private lateinit var binaryManager: BinaryManager
    private lateinit var privateKeyManager: PrivateKeyManager
    private lateinit var commandBuilder: SSHCommandBuilder
    private lateinit var processManager: ProcessManager
    private lateinit var connectionMonitor: ConnectionMonitor
    private lateinit var testLogger: TestLogger
    private lateinit var nativeSSHClient: AndroidNativeSSHClient
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        binaryManager = mockk(relaxed = true)
        privateKeyManager = mockk(relaxed = true)
        commandBuilder = mockk(relaxed = true)
        processManager = mockk(relaxed = true)
        connectionMonitor = mockk(relaxed = true)
        testLogger = TestLogger()
        
        nativeSSHClient = AndroidNativeSSHClient(
            context = context,
            binaryManager = binaryManager,
            privateKeyManager = privateKeyManager,
            commandBuilder = commandBuilder,
            processManager = processManager,
            connectionMonitor = connectionMonitor,
            logger = testLogger
        )
    }
    
    /**
     * Feature: native-ssh-client, Property 12: Process termination detection
     * Validates: Requirements 6.5, 7.5
     * 
     * For any SSH process, when the process terminates (gracefully or unexpectedly),
     * the system should detect the termination and update the connection state.
     */
    @Test
    fun `process termination should be detected and connection state updated`() = runTest {
        // Feature: native-ssh-client, Property 12: Process termination detection
        // Validates: Requirements 6.5, 7.5
        
        checkAll(
            100,
            Arb.serverProfile(),
            Arb.privateKey(),
            Arb.int(1024..65535)
        ) { profile, privateKey, localPort ->
            // Setup mocks for successful connection
            val architecture = Architecture.ARM64
            val binaryPath = "/data/data/com.sshtunnel/files/ssh"
            val keyPath = "/data/data/com.sshtunnel/files/keys/key_${profile.id}"
            
            every { binaryManager.detectArchitecture() } returns architecture
            coEvery { binaryManager.getCachedBinary(architecture) } returns binaryPath
            coEvery { binaryManager.verifyBinary(binaryPath) } returns true
            coEvery { privateKeyManager.writePrivateKey(profile.id, privateKey.keyData) } returns Result.success(keyPath)
            
            // Create a mock process
            val mockProcess = mockk<Process>(relaxed = true)
            every { mockProcess.isAlive } returns true
            
            val command = listOf(binaryPath, "-D", localPort.toString(), "-N", "-T")
            every { commandBuilder.buildCommand(binaryPath, profile, keyPath, localPort) } returns command
            coEvery { processManager.startProcess(command) } returns Result.success(mockProcess)
            every { processManager.monitorOutput(mockProcess) } returns flowOf("SSH connection established")
            every { connectionMonitor.monitorConnection(mockProcess, localPort) } returns flowOf(ConnectionHealthState.Healthy)
            
            // Connect and create port forwarding
            val sessionResult = nativeSSHClient.connect(
                profile = profile,
                privateKey = privateKey,
                passphrase = null,
                connectionTimeout = 30.seconds,
                enableCompression = false,
                strictHostKeyChecking = false
            )
            
            sessionResult.isSuccess shouldBe true
            val session = sessionResult.getOrThrow()
            
            val portResult = nativeSSHClient.createPortForwarding(session, localPort)
            portResult.isSuccess shouldBe true
            
            // Initially, connection should be alive
            every { processManager.isProcessAlive(mockProcess) } returns true
            nativeSSHClient.isConnected(session) shouldBe true
            
            // Simulate process termination
            every { processManager.isProcessAlive(mockProcess) } returns false
            
            // Connection should now be detected as terminated
            nativeSSHClient.isConnected(session) shouldBe false
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 12: Process termination detection (graceful shutdown)
     * Validates: Requirements 7.5
     * 
     * When the SSH process is stopped gracefully, the system should update
     * the connection state to disconnected and clean up resources.
     */
    @Test
    fun `graceful shutdown should update connection state and cleanup resources`() = runTest {
        // Feature: native-ssh-client, Property 12: Process termination detection
        // Validates: Requirements 7.5
        
        checkAll(
            100,
            Arb.serverProfile(),
            Arb.privateKey(),
            Arb.int(1024..65535)
        ) { profile, privateKey, localPort ->
            // Setup mocks for successful connection
            val architecture = Architecture.ARM64
            val binaryPath = "/data/data/com.sshtunnel/files/ssh"
            val keyPath = "/data/data/com.sshtunnel/files/keys/key_${profile.id}"
            
            every { binaryManager.detectArchitecture() } returns architecture
            coEvery { binaryManager.getCachedBinary(architecture) } returns binaryPath
            coEvery { binaryManager.verifyBinary(binaryPath) } returns true
            coEvery { privateKeyManager.writePrivateKey(profile.id, privateKey.keyData) } returns Result.success(keyPath)
            
            // Create a mock process
            val mockProcess = mockk<Process>(relaxed = true)
            every { mockProcess.isAlive } returns true
            
            val command = listOf(binaryPath, "-D", localPort.toString(), "-N", "-T")
            every { commandBuilder.buildCommand(binaryPath, profile, keyPath, localPort) } returns command
            coEvery { processManager.startProcess(command) } returns Result.success(mockProcess)
            every { processManager.monitorOutput(mockProcess) } returns flowOf("SSH connection established")
            every { connectionMonitor.monitorConnection(mockProcess, localPort) } returns flowOf(ConnectionHealthState.Healthy)
            
            // Connect and create port forwarding
            val sessionResult = nativeSSHClient.connect(
                profile = profile,
                privateKey = privateKey,
                passphrase = null,
                connectionTimeout = 30.seconds,
                enableCompression = false,
                strictHostKeyChecking = false
            )
            
            sessionResult.isSuccess shouldBe true
            val session = sessionResult.getOrThrow()
            
            val portResult = nativeSSHClient.createPortForwarding(session, localPort)
            portResult.isSuccess shouldBe true
            
            // Connection should be alive
            every { processManager.isProcessAlive(mockProcess) } returns true
            nativeSSHClient.isConnected(session) shouldBe true
            
            // Gracefully disconnect
            coEvery { processManager.stopProcess(mockProcess, 5) } answers {
                every { mockProcess.isAlive } returns false
            }
            coEvery { privateKeyManager.deletePrivateKey(profile.id) } returns Result.success(Unit)
            
            val disconnectResult = nativeSSHClient.disconnect(session)
            disconnectResult.isSuccess shouldBe true
            
            // Verify process was stopped gracefully
            coVerify { processManager.stopProcess(mockProcess, 5) }
            
            // Verify private key was deleted
            coVerify { privateKeyManager.deletePrivateKey(profile.id) }
            
            // Connection should now be disconnected
            every { processManager.isProcessAlive(mockProcess) } returns false
            nativeSSHClient.isConnected(session) shouldBe false
        }
    }
}

/**
 * Test logger implementation that collects log entries.
 */
private class TestLogger : Logger {
    private val logs = mutableListOf<LogEntry>()
    private var verboseEnabled = false
    
    override fun verbose(tag: String, message: String, throwable: Throwable?) {
        if (verboseEnabled) {
            logs.add(LogEntry(System.currentTimeMillis(), LogLevel.VERBOSE, tag, message, throwable))
        }
    }
    
    override fun debug(tag: String, message: String, throwable: Throwable?) {
        logs.add(LogEntry(System.currentTimeMillis(), LogLevel.DEBUG, tag, message, throwable))
    }
    
    override fun info(tag: String, message: String, throwable: Throwable?) {
        logs.add(LogEntry(System.currentTimeMillis(), LogLevel.INFO, tag, message, throwable))
    }
    
    override fun warn(tag: String, message: String, throwable: Throwable?) {
        logs.add(LogEntry(System.currentTimeMillis(), LogLevel.WARN, tag, message, throwable))
    }
    
    override fun error(tag: String, message: String, throwable: Throwable?) {
        logs.add(LogEntry(System.currentTimeMillis(), LogLevel.ERROR, tag, message, throwable))
    }
    
    override fun getLogEntries(): List<LogEntry> = logs.toList()
    
    override fun clearLogs() {
        logs.clear()
    }
    
    override fun setVerboseEnabled(enabled: Boolean) {
        verboseEnabled = enabled
    }
    
    override fun isVerboseEnabled(): Boolean = verboseEnabled
}

/**
 * Custom generators for property-based testing.
 */
private fun Arb.Companion.serverProfile() = arbitrary {
    ServerProfile(
        id = Arb.long(1L..1000L).bind(),
        name = Arb.string(5..20).bind(),
        hostname = "test.example.com",
        port = Arb.int(1..65535).bind(),
        username = Arb.string(3..16).bind(),
        keyType = KeyType.ED25519,
        createdAt = System.currentTimeMillis(),
        lastUsed = null
    )
}

private fun Arb.Companion.privateKey() = arbitrary {
    PrivateKey(
        keyData = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest_key_data\n-----END OPENSSH PRIVATE KEY-----".toByteArray(),
        keyType = KeyType.ED25519
    )
}
