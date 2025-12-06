package com.sshtunnel.ssh

import android.os.Build
import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LogEntry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import java.io.ByteArrayInputStream
import java.io.InputStream

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
            iterations = 100,
            Arb.outputLines()
        ) { outputLines ->
            // Create a mock process with output
            val mockProcess = mockk<Process>(relaxed = true)
            val outputStream = createInputStream(outputLines)
            every { mockProcess.inputStream } returns outputStream
            every { mockProcess.isAlive } returns true
            
            // Monitor output
            val outputFlow = processManager.monitorOutput(mockProcess)
            
            // Collect all output
            val collectedLines = outputFlow.toList()
            
            // Should have captured all output lines
            collectedLines shouldBe outputLines
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
            iterations = 100,
            Arb.boolean()
        ) { isAlive ->
            // Create a mock process with specific alive status
            val mockProcess = mockk<Process>(relaxed = true)
            every { mockProcess.isAlive } returns isAlive
            
            // Check if process is alive
            val result = processManager.isProcessAlive(mockProcess)
            
            // Result should match the mock's alive status
            result shouldBe isAlive
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
            iterations = 100,
            Arb.int(0..255) // Exit codes
        ) { exitCode ->
            // Create a mock process that starts alive then terminates
            val mockProcess = mockk<Process>(relaxed = true)
            var alive = true
            every { mockProcess.isAlive } answers { alive }
            every { mockProcess.waitFor() } answers {
                alive = false
                exitCode
            }
            
            // Process should be alive initially
            processManager.isProcessAlive(mockProcess) shouldBe true
            
            // Wait for process to terminate
            mockProcess.waitFor()
            
            // Process should not be alive after termination
            processManager.isProcessAlive(mockProcess) shouldBe false
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
            iterations = 100,
            Arb.int(1..5), // Timeout in seconds
            Arb.boolean() // Whether process terminates gracefully
        ) { timeoutSeconds, terminatesGracefully ->
            // Create a mock process
            val mockProcess = mockk<Process>(relaxed = true)
            var alive = true
            var destroyed = false
            var forciblyDestroyed = false
            
            every { mockProcess.isAlive } answers { alive }
            every { mockProcess.destroy() } answers {
                destroyed = true
                if (terminatesGracefully) {
                    alive = false
                }
            }
            every { mockProcess.destroyForcibly() } answers {
                forciblyDestroyed = true
                alive = false
                mockProcess
            }
            every { mockProcess.waitFor() } answers {
                if (!alive) 0 else {
                    // Simulate waiting
                    Thread.sleep(100)
                    if (alive) throw InterruptedException()
                    0
                }
            }
            
            // Process should be alive initially
            processManager.isProcessAlive(mockProcess) shouldBe true
            
            // Stop the process
            val startTime = System.currentTimeMillis()
            processManager.stopProcess(mockProcess, timeoutSeconds)
            val elapsedTime = System.currentTimeMillis() - startTime
            
            // Process should be terminated
            processManager.isProcessAlive(mockProcess) shouldBe false
            
            // destroy() should have been called
            destroyed shouldBe true
            
            // If process didn't terminate gracefully, destroyForcibly should have been called
            if (!terminatesGracefully) {
                forciblyDestroyed shouldBe true
            }
            
            // Should complete within reasonable time
            val maxExpectedTime = (timeoutSeconds * 1000) + 2000 // +2 seconds overhead
            (elapsedTime <= maxExpectedTime) shouldBe true
        }
    }
    
    /**
     * Test that stopProcess handles already-terminated processes gracefully.
     */
    @Test
    fun `stopProcess should handle already terminated processes gracefully`() = runTest {
        checkAll(
            iterations = 100,
            Arb.int(0..255) // Exit code
        ) { exitCode ->
            // Create a mock process that is already terminated
            val mockProcess = mockk<Process>(relaxed = true)
            every { mockProcess.isAlive } returns false
            every { mockProcess.destroy() } returns Unit
            every { mockProcess.destroyForcibly() } returns mockProcess
            every { mockProcess.waitFor() } returns exitCode
            
            // Try to stop already-terminated process
            // Should not throw exception
            val stopResult = try {
                processManager.stopProcess(mockProcess, timeoutSeconds = 1)
                true
            } catch (e: Exception) {
                false
            }
            
            stopResult shouldBe true
        }
    }
    
    // Custom generators and helper functions for property-based testing
    
    companion object {
        /**
         * Generates lists of output lines for testing output capture.
         */
        fun Arb.Companion.outputLines(): Arb<List<String>> = arbitrary {
            val lineCount = Arb.int(1..10).bind()
            List(lineCount) { Arb.string(5..50).bind() }
        }
        
        /**
         * Creates an InputStream from a list of strings.
         */
        fun createInputStream(lines: List<String>): InputStream {
            val content = lines.joinToString("\n")
            return ByteArrayInputStream(content.toByteArray())
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
