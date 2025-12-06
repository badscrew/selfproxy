package com.sshtunnel.ssh

import android.os.Build
import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LogEntry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
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
 * Property-based tests for ProcessManager functionality.
 * 
 * Tests process lifecycle management including:
 * - Process output capture
 * - Process alive status checking
 * - Process termination detection
 * - Graceful shutdown with timeout
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class ProcessManagerPropertiesTest {
    
    private lateinit var processManager: AndroidProcessManager
    private lateinit var testLogger: TestLogger
    
    @Before
    fun setup() {
        testLogger = TestLogger()
        processManager = AndroidProcessManager(testLogger)
    }
    
    /**
     * Feature: native-ssh-client, Property 9: Process output capture
     * Validates: Requirements 6.1
     * 
     * For any started SSH process, the system should provide access to
     * the process output stream for logging and monitoring.
     */
    @Test
    fun `process output should be captured and emitted as flow`() = runTest {
        // Feature: native-ssh-client, Property 9: Process output capture
        // Validates: Requirements 6.1
        
        checkAll(
            iterations = 20, // Reduced iterations for process operations
            Arb.simpleCommand()
        ) { command ->
            // Start a simple process that produces output
            val result = processManager.startProcess(command)
            result.isSuccess shouldBe true
            
            val process = result.getOrNull()
            process shouldNotBe null
            
            if (process != null) {
                // Monitor output - should be able to collect at least one line
                val outputFlow = processManager.monitorOutput(process)
                
                // Try to collect output with timeout
                val output = withTimeoutOrNull(2000) {
                    outputFlow.take(1).toList()
                }
                
                // Should have captured some output (or flow completed)
                // The flow should not throw exceptions
                output shouldNotBe null
                
                // Clean up
                processManager.stopProcess(process, timeoutSeconds = 1)
            }
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 10: Process alive status check
     * Validates: Requirements 6.3
     * 
     * For any SSH process, when the system checks if the process is running,
     * the result should accurately reflect whether the process is alive.
     */
    @Test
    fun `process alive status should accurately reflect process state`() = runTest {
        // Feature: native-ssh-client, Property 10: Process alive status check
        // Validates: Requirements 6.3
        
        checkAll(
            iterations = 20, // Reduced iterations for process operations
            Arb.simpleCommand()
        ) { command ->
            // Start a process
            val result = processManager.startProcess(command)
            result.isSuccess shouldBe true
            
            val process = result.getOrNull()
            process shouldNotBe null
            
            if (process != null) {
                // Process should be alive immediately after starting
                val isAliveAfterStart = processManager.isProcessAlive(process)
                isAliveAfterStart shouldBe true
                
                // Stop the process
                processManager.stopProcess(process, timeoutSeconds = 1)
                
                // Give it a moment to terminate
                delay(100)
                
                // Process should not be alive after stopping
                val isAliveAfterStop = processManager.isProcessAlive(process)
                isAliveAfterStop shouldBe false
            }
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 12: Process termination detection
     * Validates: Requirements 6.5, 7.5
     * 
     * For any SSH process, when the process terminates (gracefully or unexpectedly),
     * the system should detect the termination and update the connection state.
     */
    @Test
    fun `process termination should be detectable`() = runTest {
        // Feature: native-ssh-client, Property 12: Process termination detection
        // Validates: Requirements 6.5, 7.5
        
        checkAll(
            iterations = 20, // Reduced iterations for process operations
            Arb.shortLivedCommand()
        ) { command ->
            // Start a short-lived process
            val result = processManager.startProcess(command)
            result.isSuccess shouldBe true
            
            val process = result.getOrNull()
            process shouldNotBe null
            
            if (process != null) {
                // Process should be alive initially
                val isAliveInitially = processManager.isProcessAlive(process)
                isAliveInitially shouldBe true
                
                // Wait for process to complete naturally
                val exitCode = withTimeoutOrNull(3000) {
                    process.waitFor()
                }
                
                // Process should have terminated
                exitCode shouldNotBe null
                
                // isProcessAlive should now return false
                val isAliveAfterTermination = processManager.isProcessAlive(process)
                isAliveAfterTermination shouldBe false
            }
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 13: Graceful shutdown with timeout
     * Validates: Requirements 7.1, 7.2, 7.3
     * 
     * For any SSH process, when termination is requested, the system should
     * wait up to the specified timeout for graceful shutdown before forcing termination.
     */
    @Test
    fun `process should terminate within timeout period`() = runTest {
        // Feature: native-ssh-client, Property 13: Graceful shutdown with timeout
        // Validates: Requirements 7.1, 7.2, 7.3
        
        checkAll(
            iterations = 20, // Reduced iterations for process operations
            Arb.simpleCommand(),
            Arb.int(1..3) // Timeout in seconds
        ) { command, timeoutSeconds ->
            // Start a process
            val result = processManager.startProcess(command)
            result.isSuccess shouldBe true
            
            val process = result.getOrNull()
            process shouldNotBe null
            
            if (process != null) {
                // Process should be alive
                processManager.isProcessAlive(process) shouldBe true
                
                // Measure time to stop process
                val startTime = System.currentTimeMillis()
                processManager.stopProcess(process, timeoutSeconds)
                val elapsedTime = System.currentTimeMillis() - startTime
                
                // Process should be terminated
                processManager.isProcessAlive(process) shouldBe false
                
                // Should complete within reasonable time
                // Allow some overhead beyond the timeout
                val maxExpectedTime = (timeoutSeconds * 1000) + 1000 // +1 second overhead
                (elapsedTime <= maxExpectedTime) shouldBe true
            }
        }
    }
    
    /**
     * Test that stopProcess handles already-terminated processes gracefully.
     */
    @Test
    fun `stopProcess should handle already terminated processes gracefully`() = runTest {
        checkAll(
            iterations = 20,
            Arb.shortLivedCommand()
        ) { command ->
            // Start a short-lived process
            val result = processManager.startProcess(command)
            result.isSuccess shouldBe true
            
            val process = result.getOrNull()
            process shouldNotBe null
            
            if (process != null) {
                // Wait for process to terminate naturally
                withTimeoutOrNull(3000) {
                    process.waitFor()
                }
                
                // Try to stop already-terminated process
                // Should not throw exception
                val stopResult = try {
                    processManager.stopProcess(process, timeoutSeconds = 1)
                    true
                } catch (e: Exception) {
                    false
                }
                
                stopResult shouldBe true
            }
        }
    }
    
    /**
     * Test that startProcess fails gracefully with invalid commands.
     */
    @Test
    fun `startProcess should fail gracefully with invalid commands`() = runTest {
        checkAll(
            iterations = 20,
            Arb.invalidCommand()
        ) { command ->
            // Try to start process with invalid command
            val result = processManager.startProcess(command)
            
            // Should return a failure result (not throw exception)
            result.isFailure shouldBe true
        }
    }
    
    // Custom generators for property-based testing
    
    companion object {
        /**
         * Generates simple commands that produce output.
         * Uses echo command which is available on all platforms.
         */
        fun Arb.Companion.simpleCommand(): Arb<List<String>> = arbitrary {
            val message = Arb.string(5..20).bind()
            listOf("echo", message)
        }
        
        /**
         * Generates short-lived commands that terminate quickly.
         */
        fun Arb.Companion.shortLivedCommand(): Arb<List<String>> = arbitrary {
            // Use echo which terminates immediately after output
            val message = Arb.string(5..20).bind()
            listOf("echo", message)
        }
        
        /**
         * Generates invalid commands that should fail.
         */
        fun Arb.Companion.invalidCommand(): Arb<List<String>> = arbitrary {
            val choice = Arb.int(0..2).bind()
            when (choice) {
                0 -> emptyList() // Empty command
                1 -> listOf("nonexistent_command_${Arb.string(5..10).bind()}") // Non-existent command
                else -> listOf("/invalid/path/to/binary") // Invalid path
            }
        }
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
