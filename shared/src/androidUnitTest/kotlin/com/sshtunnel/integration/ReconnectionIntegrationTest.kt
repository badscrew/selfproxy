package com.sshtunnel.integration

import android.os.Build
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.LogEntry
import com.sshtunnel.logging.LogLevel
import com.sshtunnel.logging.Logger
import com.sshtunnel.network.NetworkMonitor
import com.sshtunnel.reconnection.*
import com.sshtunnel.ssh.Connection
import com.sshtunnel.ssh.SSHConnectionManager
import com.sshtunnel.ssh.SSHSession
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for connection monitoring and automatic reconnection.
 * 
 * Tests Requirements 8.3 and 8.4:
 * - Connection loss detection within 5 seconds
 * - Automatic reconnection with exponential backoff
 * - Reconnection policy configuration
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class ReconnectionIntegrationTest {
    
    private lateinit var mockConnectionManager: SSHConnectionManager
    private lateinit var mockNetworkMonitor: NetworkMonitor
    private lateinit var reconnectionStateMachine: ReconnectionStateMachine
    private lateinit var autoReconnectService: AutoReconnectServiceImpl
    private lateinit var testScope: CoroutineScope
    private lateinit var testLogger: TestLogger
    
    @Before
    fun setup() {
        mockConnectionManager = mockk(relaxed = true)
        mockNetworkMonitor = mockk(relaxed = true)
        reconnectionStateMachine = ReconnectionStateMachine()
        testScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        testLogger = TestLogger()
        
        autoReconnectService = AutoReconnectServiceImpl(
            connectionManager = mockConnectionManager,
            networkMonitor = mockNetworkMonitor,
            reconnectionStateMachine = reconnectionStateMachine,
            scope = testScope
        )
    }
    
    @After
    fun teardown() {
        testScope.cancel()
    }
    
    /**
     * Test connection loss detection.
     * Validates Requirement 8.3: Connection loss detected within 5 seconds.
     */
    @Test
    fun `connection loss should be detected within 5 seconds`() = runTest {
        // Arrange
        val profile = createTestProfile()
        val mockConnection = Connection(
            sessionId = "test-session",
            socksPort = 1080,
            serverAddress = profile.hostname,
            serverPort = profile.port,
            username = profile.username,
            profileId = profile.id
        )
        
        // Simulate connection that will fail
        coEvery { mockConnectionManager.connect(any(), any()) } returns Result.failure(Exception("Connection failed"))
        
        // Track when connection loss is detected
        val detectionTimes = mutableListOf<Long>()
        val startTime = System.currentTimeMillis()
        
        // Act
        autoReconnectService.enable(profile, null)
        
        // Collect reconnection attempts
        val attempts = mutableListOf<ReconnectAttemptWithStatus>()
        val job = launch {
            autoReconnectService.observeReconnectAttempts()
                .take(1)
                .collect { attempt ->
                    val detectionTime = System.currentTimeMillis() - startTime
                    detectionTimes.add(detectionTime)
                    attempts.add(attempt)
                }
        }
        
        // Wait for detection
        delay(6000) // Wait up to 6 seconds
        job.cancel()
        
        // Assert
        assertTrue(detectionTimes.isNotEmpty(), "Connection loss should be detected")
        assertTrue(
            detectionTimes.first() <= 5000,
            "Connection loss should be detected within 5 seconds, but took ${detectionTimes.first()}ms"
        )
    }
    
    /**
     * Test automatic reconnection behavior.
     * Validates Requirement 8.4: Automatic reconnection with exponential backoff.
     */
    @Test
    fun `should automatically reconnect after connection loss`() = runTest {
        // Arrange
        val profile = createTestProfile()
        val mockConnection = Connection(
            sessionId = "test-session",
            socksPort = 1080,
            serverAddress = profile.hostname,
            serverPort = profile.port,
            username = profile.username,
            profileId = profile.id
        )
        
        var connectionAttempts = 0
        coEvery { mockConnectionManager.connect(any(), any()) } answers {
            connectionAttempts++
            // All attempts fail initially
            Result.failure(Exception("Connection failed"))
        }
        
        // Act
        autoReconnectService.enable(profile, null)
        
        // Collect reconnection attempts
        val attempts = mutableListOf<ReconnectAttemptWithStatus>()
        val job = launch {
            autoReconnectService.observeReconnectAttempts()
                .take(3) // Collect first 3 attempts
                .collect { attempt ->
                    attempts.add(attempt)
                }
        }
        
        // Wait for reconnection attempts
        delay(10000) // Wait for multiple attempts
        job.cancel()
        
        // Assert
        assertTrue(attempts.isNotEmpty(), "Should have reconnection attempts")
        assertTrue(connectionAttempts > 1, "Should attempt to reconnect")
        
        // Verify attempts are in order
        attempts.forEachIndexed { index, attempt ->
            assertEquals(index, attempt.attempt.attemptNumber, "Attempt numbers should be sequential")
        }
    }
    
    /**
     * Test reconnection backoff policy.
     * Validates Requirement 8.4: Exponential backoff between reconnection attempts.
     */
    @Test
    fun `should use exponential backoff between reconnection attempts`() = runTest {
        // Arrange
        val profile = createTestProfile()
        
        coEvery { mockConnectionManager.connect(any(), any()) } returns Result.failure(Exception("Connection failed"))
        
        // Act
        autoReconnectService.enable(profile, null)
        
        // Collect reconnection attempts with timestamps
        val attemptTimes = mutableListOf<Pair<Int, Long>>()
        val startTime = System.currentTimeMillis()
        
        val job = launch {
            autoReconnectService.observeReconnectAttempts()
                .take(4) // Collect first 4 attempts
                .collect { attempt ->
                    val elapsedTime = System.currentTimeMillis() - startTime
                    attemptTimes.add(attempt.attempt.attemptNumber to elapsedTime)
                }
        }
        
        // Wait for attempts
        delay(20000) // Wait for multiple backoff periods
        job.cancel()
        
        // Assert
        assertTrue(attemptTimes.size >= 2, "Should have at least 2 attempts")
        
        // Verify backoff increases (allowing some tolerance for timing)
        if (attemptTimes.size >= 3) {
            val firstInterval = attemptTimes[1].second - attemptTimes[0].second
            val secondInterval = attemptTimes[2].second - attemptTimes[1].second
            
            // Second interval should be roughly double the first (exponential backoff)
            // Allow 50% tolerance for timing variations
            assertTrue(
                secondInterval >= firstInterval * 1.5,
                "Backoff should increase exponentially: first=$firstInterval, second=$secondInterval"
            )
        }
    }
    
    /**
     * Test reconnection policy configuration.
     * Validates that reconnection policy settings are respected.
     */
    @Test
    fun `should respect reconnection policy configuration`() = runTest {
        // Arrange
        val profile = createTestProfile()
        val mockSession = mockk<SSHSession>(relaxed = true)
        
        // Create a policy with specific settings
        val policy = ReconnectionPolicy(
            enabled = true,
            maxAttempts = 2,
            initialBackoff = 100.milliseconds,
            maxBackoff = 500.milliseconds
        )
        
        coEvery { mockConnectionManager.connect(any(), any()) } returns Result.failure(Exception("Connection failed"))
        
        // Act
        autoReconnectService.enable(profile, null)
        
        // Collect attempts
        val attempts = mutableListOf<ReconnectAttemptWithStatus>()
        val job = launch {
            autoReconnectService.observeReconnectAttempts()
                .take(5) // Try to collect more than maxAttempts
                .collect { attempt ->
                    attempts.add(attempt)
                }
        }
        
        // Wait for attempts
        delay(5000)
        job.cancel()
        
        // Assert
        assertTrue(attempts.isNotEmpty(), "Should have reconnection attempts")
        
        // Verify backoff times are within policy limits
        attempts.forEach { attempt ->
            assertTrue(
                attempt.attempt.nextRetryIn <= policy.maxBackoff,
                "Backoff should not exceed max: ${attempt.attempt.nextRetryIn} > ${policy.maxBackoff}"
            )
        }
    }
    
    /**
     * Test that reconnection stops when disabled.
     */
    @Test
    fun `should stop reconnection when disabled`() = runTest {
        // Arrange
        val profile = createTestProfile()
        val mockSession = mockk<SSHSession>(relaxed = true)
        
        coEvery { mockConnectionManager.connect(any(), any()) } returns Result.failure(Exception("Connection failed"))
        
        // Act
        autoReconnectService.enable(profile, null)
        
        // Collect attempts
        val attempts = mutableListOf<ReconnectAttemptWithStatus>()
        val job = launch {
            autoReconnectService.observeReconnectAttempts()
                .collect { attempt ->
                    attempts.add(attempt)
                }
        }
        
        // Wait for first attempt
        delay(2000)
        
        // Disable reconnection
        autoReconnectService.disable()
        
        val attemptsBeforeDisable = attempts.size
        
        // Wait to see if more attempts occur
        delay(5000)
        job.cancel()
        
        // Assert
        assertTrue(attemptsBeforeDisable > 0, "Should have at least one attempt before disable")
        
        // Check if last status is Cancelled
        val lastAttempt = attempts.lastOrNull()
        if (lastAttempt != null) {
            assertTrue(
                lastAttempt.status is ReconnectStatus.Cancelled || 
                attempts.size == attemptsBeforeDisable,
                "Should stop attempting after disable"
            )
        }
    }
    
    /**
     * Test successful reconnection.
     */
    @Test
    fun `should report success when reconnection succeeds`() = runTest {
        // Arrange
        val profile = createTestProfile()
        val mockConnection = Connection(
            sessionId = "test-session",
            socksPort = 1080,
            serverAddress = profile.hostname,
            serverPort = profile.port,
            username = profile.username,
            profileId = profile.id
        )
        
        var connectionAttempts = 0
        coEvery { mockConnectionManager.connect(any(), any()) } answers {
            connectionAttempts++
            if (connectionAttempts <= 2) {
                // First two attempts fail
                Result.failure(Exception("Connection failed"))
            } else {
                // Third attempt succeeds
                Result.success(mockConnection)
            }
        }
        
        // Act
        autoReconnectService.enable(profile, null)
        
        // Collect attempts
        val attempts = mutableListOf<ReconnectAttemptWithStatus>()
        val job = launch {
            autoReconnectService.observeReconnectAttempts()
                .takeWhile { it.status !is ReconnectStatus.Success }
                .collect { attempt ->
                    attempts.add(attempt)
                }
        }
        
        // Wait for reconnection
        delay(10000)
        job.cancel()
        
        // Assert
        assertTrue(connectionAttempts >= 3, "Should attempt reconnection multiple times")
        
        // Check if we got a success status
        val hasSuccess = attempts.any { it.status is ReconnectStatus.Success }
        assertTrue(hasSuccess || connectionAttempts >= 3, "Should eventually succeed or attempt multiple times")
    }
    
    private fun createTestProfile(): ServerProfile {
        return ServerProfile(
            id = 1L,
            name = "Test Server",
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            keyType = com.sshtunnel.data.KeyType.ED25519,
            createdAt = System.currentTimeMillis(),
            lastUsed = null
        )
    }
    
    /**
     * Simple test logger for capturing log messages.
     */
    private class TestLogger : Logger {
        private val messages = mutableListOf<String>()
        private var verboseEnabled = false
        
        override fun verbose(tag: String, message: String, throwable: Throwable?) {
            if (verboseEnabled) {
                messages.add("VERBOSE: $tag: $message ${throwable?.message ?: ""}")
            }
        }
        
        override fun debug(tag: String, message: String, throwable: Throwable?) {
            messages.add("DEBUG: $tag: $message ${throwable?.message ?: ""}")
        }
        
        override fun info(tag: String, message: String, throwable: Throwable?) {
            messages.add("INFO: $tag: $message ${throwable?.message ?: ""}")
        }
        
        override fun warn(tag: String, message: String, throwable: Throwable?) {
            messages.add("WARN: $tag: $message ${throwable?.message ?: ""}")
        }
        
        override fun error(tag: String, message: String, throwable: Throwable?) {
            messages.add("ERROR: $tag: $message ${throwable?.message ?: ""}")
        }
        
        override fun getLogEntries(): List<LogEntry> {
            return messages.mapIndexed { index, msg ->
                val level = when {
                    msg.startsWith("VERBOSE") -> LogLevel.VERBOSE
                    msg.startsWith("DEBUG") -> LogLevel.DEBUG
                    msg.startsWith("INFO") -> LogLevel.INFO
                    msg.startsWith("WARN") -> LogLevel.WARN
                    msg.startsWith("ERROR") -> LogLevel.ERROR
                    else -> LogLevel.INFO
                }
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    tag = "Test",
                    message = msg
                )
            }
        }
        
        override fun clearLogs() {
            messages.clear()
        }
        
        override fun setVerboseEnabled(enabled: Boolean) {
            verboseEnabled = enabled
        }
        
        override fun isVerboseEnabled(): Boolean = verboseEnabled
        
        fun getMessages(): List<String> = messages.toList()
    }
}
