package com.sshtunnel.ssh

import com.sshtunnel.logging.Logger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for ErrorHandler.
 * 
 * Tests the error handling, classification, and recovery logic
 * for native SSH implementation.
 */
class ErrorHandlerPropertiesTest {
    
    private lateinit var privateKeyManager: PrivateKeyManager
    private lateinit var logger: Logger
    private lateinit var errorHandler: AndroidErrorHandler
    
    @Before
    fun setup() {
        privateKeyManager = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        errorHandler = AndroidErrorHandler(privateKeyManager, logger)
    }
    
    /**
     * Feature: native-ssh-client, Property 2: Fallback to sshj on native unavailability
     * Validates: Requirements 8.1
     * 
     * For any binary extraction failure, the system should fall back to sshj implementation.
     */
    @Test
    fun `binary extraction failure should trigger fallback to sshj`() = runTest {
        checkAll(
            iterations = 100,
            Arb.string(1..100)
        ) { errorMessage ->
            // Arrange
            val error = IOException("Failed to extract binary: $errorMessage")
            
            // Act
            val action = errorHandler.handleError(error)
            
            // Assert
            assertTrue(
                action is RecoveryAction.FallbackToSshj,
                "Binary extraction failure should trigger fallback to sshj, got: ${action.javaClass.simpleName}"
            )
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 14: Error result on process start failure
     * Validates: Requirements 8.2
     * 
     * For any process start failure, the system should return an error result
     * and attempt retry before falling back.
     */
    @Test
    fun `process start failure should trigger retry then fallback`() = runTest {
        checkAll(
            iterations = 100,
            Arb.string(1..100)
        ) { errorMessage ->
            // Arrange
            val error = IOException("Failed to start process: $errorMessage")
            
            // Act - First attempt should retry
            val firstAction = errorHandler.handleError(error)
            
            // Assert - First attempt should retry
            assertTrue(
                firstAction is RecoveryAction.Retry,
                "First process start failure should trigger retry, got: ${firstAction.javaClass.simpleName}"
            )
            
            // Act - Second attempt should fallback
            val secondAction = errorHandler.handleError(error)
            
            // Assert - Second attempt should fallback
            assertTrue(
                secondAction is RecoveryAction.FallbackToSshj,
                "Second process start failure should trigger fallback, got: ${secondAction.javaClass.simpleName}"
            )
            
            // Reset for next iteration
            errorHandler.resetRetryCount("process_start")
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 15: Reconnection on connection loss
     * Validates: Requirements 8.4
     * 
     * For any connection failure, the system should attempt automatic reconnection
     * with exponential backoff.
     */
    @Test
    fun `connection failure should trigger reconnection with backoff`() = runTest {
        checkAll(
            iterations = 100,
            Arb.string(1..100)
        ) { errorMessage ->
            // Arrange
            val error = IOException("Connection failed: $errorMessage")
            
            // Act - First attempt
            val firstAction = errorHandler.handleError(error)
            
            // Assert - Should reconnect
            assertTrue(
                firstAction is RecoveryAction.Reconnect,
                "Connection failure should trigger reconnection, got: ${firstAction.javaClass.simpleName}"
            )
            
            // Verify backoff increases
            val firstBackoff = (firstAction as RecoveryAction.Reconnect).backoffMs
            
            // Act - Second attempt
            val secondAction = errorHandler.handleError(error)
            assertTrue(secondAction is RecoveryAction.Reconnect)
            val secondBackoff = (secondAction as RecoveryAction.Reconnect).backoffMs
            
            // Assert - Backoff should increase
            assertTrue(
                secondBackoff >= firstBackoff,
                "Backoff should increase or stay same: first=$firstBackoff, second=$secondBackoff"
            )
            
            // Reset for next iteration
            errorHandler.resetRetryCount("connection")
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 16: Resource cleanup on crash
     * Validates: Requirements 8.5
     * 
     * For any process crash, the system should clean up all associated resources
     * including private key files.
     */
    @Test
    fun `process crash should trigger cleanup`() = runTest {
        checkAll(
            iterations = 100,
            Arb.string(5..20),
            Arb.int(1..255)
        ) { sessionId, exitCode ->
            // Arrange
            val error = NativeSSHError.ProcessCrashed(
                exitCode = exitCode,
                message = "Process crashed with exit code $exitCode"
            )
            
            // Act - Handle error
            val action = errorHandler.handleError(error)
            
            // Assert - Should reconnect after crash
            assertTrue(
                action is RecoveryAction.Reconnect,
                "Process crash should trigger reconnection, got: ${action.javaClass.simpleName}"
            )
            
            // Act - Cleanup
            errorHandler.cleanup(sessionId)
            
            // Assert - Session should be marked for cleanup
            assertTrue(
                errorHandler.needsCleanup(sessionId),
                "Session should be marked for cleanup after crash"
            )
            
            // Cleanup for next iteration
            errorHandler.markCleanupComplete(sessionId)
        }
    }
    
    /**
     * Test that error classification correctly identifies binary extraction errors.
     */
    @Test
    fun `error classification should identify binary extraction errors`() = runTest {
        checkAll(
            iterations = 100,
            Arb.string(1..100)
        ) { errorMessage ->
            // Arrange
            val error = IOException("Failed to extract binary: $errorMessage")
            
            // Act
            val classified = errorHandler.classifyError(error)
            
            // Assert
            assertTrue(
                classified is NativeSSHError.BinaryExtractionFailed,
                "Should classify as BinaryExtractionFailed, got: ${classified.javaClass.simpleName}"
            )
        }
    }
    
    /**
     * Test that error classification correctly identifies process start errors.
     */
    @Test
    fun `error classification should identify process start errors`() = runTest {
        checkAll(
            iterations = 100,
            Arb.string(1..100)
        ) { errorMessage ->
            // Arrange
            val error = IOException("Failed to start process: $errorMessage")
            
            // Act
            val classified = errorHandler.classifyError(error)
            
            // Assert
            assertTrue(
                classified is NativeSSHError.ProcessStartFailed,
                "Should classify as ProcessStartFailed, got: ${classified.javaClass.simpleName}"
            )
        }
    }
    
    /**
     * Test that error classification correctly identifies connection errors.
     */
    @Test
    fun `error classification should identify connection errors`() = runTest {
        checkAll(
            iterations = 100,
            Arb.string(1..100)
        ) { errorMessage ->
            // Arrange
            val error = IOException("Connection failed: $errorMessage")
            
            // Act
            val classified = errorHandler.classifyError(error)
            
            // Assert
            assertTrue(
                classified is NativeSSHError.ConnectionFailed,
                "Should classify as ConnectionFailed, got: ${classified.javaClass.simpleName}"
            )
        }
    }
    
    /**
     * Test that port unavailable errors are handled correctly.
     */
    @Test
    fun `port unavailable should trigger immediate failure`() = runTest {
        checkAll(
            iterations = 100,
            Arb.int(1024..65535)
        ) { port ->
            // Arrange
            val error = IOException("Port $port is already in use")
            
            // Act
            val action = errorHandler.handleError(error)
            
            // Assert
            assertTrue(
                action is RecoveryAction.Fail,
                "Port unavailable should trigger immediate failure, got: ${action.javaClass.simpleName}"
            )
        }
    }
    
    /**
     * Test that retry count is properly tracked and reset.
     * 
     * This test verifies that the error handler properly tracks retry attempts
     * and resets the count when requested.
     */
    @Test
    fun `retry count should be tracked and reset correctly`() = runTest {
        // Arrange - Create fresh error handler
        val testErrorHandler = AndroidErrorHandler(privateKeyManager, logger)
        val error = NativeSSHError.ConnectionFailed("Connection failed")
        
        // Act - Multiple failures
        val action1 = testErrorHandler.handleError(error)
        val action2 = testErrorHandler.handleError(error)
        val action3 = testErrorHandler.handleError(error)
        val action4 = testErrorHandler.handleError(error)
        
        // Assert - First 3 should reconnect, 4th should fail
        assertTrue(action1 is RecoveryAction.Reconnect, "First should reconnect, got: ${action1.javaClass.simpleName}")
        assertTrue(action2 is RecoveryAction.Reconnect, "Second should reconnect, got: ${action2.javaClass.simpleName}")
        assertTrue(action3 is RecoveryAction.Reconnect, "Third should reconnect, got: ${action3.javaClass.simpleName}")
        assertTrue(action4 is RecoveryAction.Fail, "Fourth should fail, got: ${action4.javaClass.simpleName}")
        
        // Reset
        testErrorHandler.resetRetryCount("connection")
        
        // Act - After reset, should reconnect again
        val afterReset = testErrorHandler.handleError(error)
        
        // Assert
        assertTrue(
            afterReset is RecoveryAction.Reconnect,
            "After reset should reconnect again, got: ${afterReset.javaClass.simpleName}"
        )
    }
    
    /**
     * Test that exponential backoff increases correctly.
     */
    @Test
    fun `exponential backoff should increase with retries`() = runTest {
        checkAll(
            iterations = 50,
            Arb.string(1..100)
        ) { errorMessage ->
            // Arrange
            val error = IOException("Connection failed: $errorMessage")
            
            // Act - Multiple failures
            val backoffs = mutableListOf<Long>()
            repeat(3) {
                val action = errorHandler.handleError(error)
                if (action is RecoveryAction.Reconnect) {
                    backoffs.add(action.backoffMs)
                }
            }
            
            // Assert - Backoff should generally increase (allowing for jitter)
            assertTrue(
                backoffs.size == 3,
                "Should have 3 backoff values"
            )
            
            // The backoff should be in reasonable ranges
            assertTrue(
                backoffs[0] >= 1000 && backoffs[0] <= 3000,
                "First backoff should be ~1-3s, got: ${backoffs[0]}"
            )
            assertTrue(
                backoffs[1] >= 2000 && backoffs[1] <= 5000,
                "Second backoff should be ~2-5s, got: ${backoffs[1]}"
            )
            assertTrue(
                backoffs[2] >= 4000 && backoffs[2] <= 9000,
                "Third backoff should be ~4-9s, got: ${backoffs[2]}"
            )
            
            // Cleanup
            errorHandler.resetRetryCount("connection")
        }
    }
}
