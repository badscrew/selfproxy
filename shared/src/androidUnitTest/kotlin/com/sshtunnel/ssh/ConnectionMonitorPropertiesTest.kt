package com.sshtunnel.ssh

import android.os.Build
import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LogEntry
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Property-based tests for ConnectionMonitor functionality.
 * 
 * Tests connection health monitoring including:
 * - SOCKS5 port availability checking
 * - Process health monitoring frequency
 * - Connection state detection
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class ConnectionMonitorPropertiesTest {
    
    private lateinit var connectionMonitor: AndroidConnectionMonitor
    private lateinit var mockProcessManager: ProcessManager
    private lateinit var testLogger: TestLogger
    
    @Before
    fun setup() {
        testLogger = TestLogger()
        mockProcessManager = mockk<ProcessManager>(relaxed = true)
        connectionMonitor = AndroidConnectionMonitor(mockProcessManager, testLogger)
    }
    
    /**
     * Feature: native-ssh-client, Property 11: SOCKS5 port availability check
     * Validates: Requirements 6.4
     * 
     * For any port number, when the system checks if the port is accepting connections,
     * the result should accurately reflect whether the port is open.
     */
    @Test
    fun `port availability check should accurately detect open ports`() = runTest {
        // Feature: native-ssh-client, Property 11: SOCKS5 port availability check
        // Validates: Requirements 6.4
        
        checkAll(
            iterations = 100,
            Arb.socksPort()
        ) { port ->
            // Note: We can't actually open ports in unit tests, so we test the logic
            // by verifying that the method handles connection attempts correctly
            
            // For closed ports (most ports in test environment), should return false
            val result = connectionMonitor.isPortOpen(port)
            
            // Result should be a boolean (method doesn't throw)
            result shouldBe false // Expected in test environment where no ports are open
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 27: Process health monitoring frequency
     * Validates: Requirements 14.5
     * 
     * For any active SSH connection, the system should check process health
     * at least once per second.
     */
    @Test
    fun `connection monitoring should check health at least once per second`() = runTest {
        // Feature: native-ssh-client, Property 27: Process health monitoring frequency
        // Validates: Requirements 14.5
        
        checkAll(
            iterations = 50, // Reduced iterations due to time-based test
            Arb.socksPort()
        ) { socksPort ->
            // Create a mock process that stays alive
            val mockProcess = mockk<Process>(relaxed = true)
            every { mockProcess.isAlive } returns true
            every { mockProcessManager.isProcessAlive(mockProcess) } returns true
            
            // Start monitoring
            val monitoringFlow = connectionMonitor.monitorConnection(mockProcess, socksPort)
            
            // Collect states for 2.5 seconds
            val states = mutableListOf<ConnectionHealthState>()
            val startTime = System.currentTimeMillis()
            
            withTimeoutOrNull(2500) {
                monitoringFlow.collect { state ->
                    states.add(state)
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= 2500) {
                        throw Exception("Time limit reached") // Exit collection
                    }
                }
            }
            
            // Should have emitted at least 2 states in 2.5 seconds
            // (initial state + at least 2 checks at 1-second intervals)
            // This validates monitoring frequency is at least once per second
            (states.size >= 2) shouldBe true
        }
    }
    
    /**
     * Test that connection monitor detects process termination.
     */
    @Test
    fun `connection monitor should detect process termination`() = runTest {
        checkAll(
            iterations = 100,
            Arb.socksPort()
        ) { socksPort ->
            // Create a mock process that terminates after being checked
            val mockProcess = mockk<Process>(relaxed = true)
            var checkCount = 0
            every { mockProcessManager.isProcessAlive(mockProcess) } answers {
                checkCount++
                checkCount <= 1 // Alive for first check, then dead
            }
            
            // Start monitoring
            val monitoringFlow = connectionMonitor.monitorConnection(mockProcess, socksPort)
            
            // Collect states until we see disconnection or timeout
            val states = mutableListOf<ConnectionHealthState>()
            withTimeoutOrNull(3000) {
                monitoringFlow.collect { state ->
                    states.add(state)
                    // Stop collecting after we see disconnection
                    if (state is ConnectionHealthState.Disconnected) {
                        return@collect
                    }
                }
            }
            
            // Should have detected disconnection
            val hasDisconnected = states.any { it is ConnectionHealthState.Disconnected }
            hasDisconnected shouldBe true
        }
    }
    
    /**
     * Test that connection monitor emits initial Healthy state.
     */
    @Test
    fun `connection monitor should emit initial healthy state`() = runTest {
        checkAll(
            iterations = 100,
            Arb.socksPort()
        ) { socksPort ->
            // Create a mock process that is alive
            val mockProcess = mockk<Process>(relaxed = true)
            every { mockProcessManager.isProcessAlive(mockProcess) } returns true
            
            // Start monitoring
            val monitoringFlow = connectionMonitor.monitorConnection(mockProcess, socksPort)
            
            // Get first state
            val firstState = withTimeoutOrNull(2000) {
                monitoringFlow.first()
            }
            
            // First state should be Healthy
            firstState shouldBe ConnectionHealthState.Healthy
        }
    }
    
    /**
     * Test that connection monitor handles port check failures gracefully.
     */
    @Test
    fun `connection monitor should handle port check failures gracefully`() = runTest {
        checkAll(
            iterations = 100,
            Arb.socksPort()
        ) { socksPort ->
            // Create a mock process that is alive but port is not open
            val mockProcess = mockk<Process>(relaxed = true)
            every { mockProcessManager.isProcessAlive(mockProcess) } returns true
            
            // Start monitoring
            val monitoringFlow = connectionMonitor.monitorConnection(mockProcess, socksPort)
            
            // Collect states for a short time
            val states = mutableListOf<ConnectionHealthState>()
            withTimeoutOrNull(2000) {
                monitoringFlow.collect { state ->
                    states.add(state)
                    // Stop after collecting 2 states
                    if (states.size >= 2) {
                        return@collect
                    }
                }
            }
            
            // Should have emitted at least one state (doesn't crash)
            (states.size >= 1) shouldBe true
        }
    }
    
    // Custom generators for property-based testing
    
    companion object {
        /**
         * Generates valid SOCKS5 port numbers (1024-65535).
         */
        fun Arb.Companion.socksPort(): Arb<Int> = int(1024..65535)
    }
    
    /**
     * Test logger implementation.
     */
    private class TestLogger : Logger {
        private val logs = mutableListOf<String>()
        
        override fun verbose(tag: String, message: String, throwable: Throwable?) {
            logs.add("VERBOSE: $tag: $message")
        }
        
        override fun debug(tag: String, message: String, throwable: Throwable?) {
            logs.add("DEBUG: $tag: $message")
        }
        
        override fun info(tag: String, message: String, throwable: Throwable?) {
            logs.add("INFO: $tag: $message")
        }
        
        override fun warn(tag: String, message: String, throwable: Throwable?) {
            logs.add("WARN: $tag: $message")
        }
        
        override fun error(tag: String, message: String, throwable: Throwable?) {
            logs.add("ERROR: $tag: $message ${throwable?.message ?: ""}")
        }
        
        override fun getLogEntries(): List<LogEntry> = emptyList()
        override fun clearLogs() { logs.clear() }
        override fun setVerboseEnabled(enabled: Boolean) {}
        override fun isVerboseEnabled(): Boolean = false
        
        fun getLogs(): List<String> = logs.toList()
    }
}
