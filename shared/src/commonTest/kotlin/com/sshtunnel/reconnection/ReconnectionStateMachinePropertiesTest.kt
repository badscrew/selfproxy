package com.sshtunnel.reconnection

import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.pow
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based tests for ReconnectionStateMachine.
 * 
 * Feature: ssh-tunnel-proxy, Property 17: Exponential backoff retry pattern
 * Validates: Requirements 4.3
 */
class ReconnectionStateMachinePropertiesTest {
    
    @Test
    fun `exponential backoff should follow 2^n pattern up to max interval`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 17: Exponential backoff retry pattern
        // Validates: Requirements 4.3
        checkAll(
            iterations = 100,
            Arb.int(0..20) // Test attempt numbers from 0 to 20
        ) { attemptNumber ->
            val stateMachine = ReconnectionStateMachine()
            val backoff = stateMachine.calculateBackoff(attemptNumber)
            
            // Calculate expected backoff: min(2^attemptNumber, 60) seconds
            val expectedSeconds = kotlin.math.min(
                2.0.pow(attemptNumber).toInt(),
                60
            )
            
            // Verify backoff matches expected value
            backoff.inWholeSeconds shouldBe expectedSeconds.toLong()
        }
    }
    
    @Test
    fun `backoff should never exceed max retry interval of 60 seconds`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 17: Exponential backoff retry pattern
        // Validates: Requirements 4.3
        checkAll(
            iterations = 100,
            Arb.int(0..100) // Test with large attempt numbers
        ) { attemptNumber ->
            val stateMachine = ReconnectionStateMachine()
            val backoff = stateMachine.calculateBackoff(attemptNumber)
            
            // Verify backoff never exceeds 60 seconds
            backoff.inWholeSeconds shouldBeLessThanOrEqual 60L
        }
    }
    
    @Test
    fun `backoff should be monotonically increasing until max interval`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 17: Exponential backoff retry pattern
        // Validates: Requirements 4.3
        checkAll(
            iterations = 100,
            Arb.int(0..10) // Test consecutive attempts
        ) { startAttempt ->
            val stateMachine = ReconnectionStateMachine()
            
            val backoff1 = stateMachine.calculateBackoff(startAttempt)
            val backoff2 = stateMachine.calculateBackoff(startAttempt + 1)
            
            // If we haven't reached max interval, backoff should increase
            if (backoff1.inWholeSeconds < 60L) {
                backoff2.inWholeSeconds shouldBeGreaterThan backoff1.inWholeSeconds
            } else {
                // Once at max, should stay at max
                backoff2.inWholeSeconds shouldBe 60L
            }
        }
    }
    
    @Test
    fun `recordAttempt should increment attempt counter and return correct backoff`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 17: Exponential backoff retry pattern
        // Validates: Requirements 4.3
        checkAll(
            iterations = 100,
            Arb.list(Arb.int(1..10), 1..15) // Simulate multiple attempts
        ) { attempts ->
            val stateMachine = ReconnectionStateMachine()
            
            attempts.forEachIndexed { index, _ ->
                val expectedBackoff = stateMachine.calculateBackoff(index)
                val actualBackoff = stateMachine.recordAttempt()
                
                // Verify backoff matches expected value
                actualBackoff shouldBe expectedBackoff
                
                // Verify attempt counter incremented
                stateMachine.getCurrentAttempt() shouldBe index + 1
            }
        }
    }
    
    @Test
    fun `reset should clear attempt counter`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 17: Exponential backoff retry pattern
        // Validates: Requirements 4.3
        checkAll(
            iterations = 100,
            Arb.int(1..20) // Number of attempts before reset
        ) { numAttempts ->
            val stateMachine = ReconnectionStateMachine()
            
            // Record multiple attempts
            repeat(numAttempts) {
                stateMachine.recordAttempt()
            }
            
            // Verify counter is non-zero
            stateMachine.getCurrentAttempt() shouldBeGreaterThanOrEqual numAttempts
            
            // Reset
            stateMachine.reset()
            
            // Verify counter is back to zero
            stateMachine.getCurrentAttempt() shouldBe 0
            
            // Verify next backoff starts from beginning
            val backoff = stateMachine.calculateBackoff(0)
            backoff shouldBe 1.seconds
        }
    }
    
    @Test
    fun `backoff for attempt 0 should be 1 second`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 17: Exponential backoff retry pattern
        // Validates: Requirements 4.3
        val stateMachine = ReconnectionStateMachine()
        val backoff = stateMachine.calculateBackoff(0)
        
        backoff shouldBe 1.seconds
    }
    
    @Test
    fun `backoff sequence should match expected pattern`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 17: Exponential backoff retry pattern
        // Validates: Requirements 4.3
        val stateMachine = ReconnectionStateMachine()
        
        // Expected sequence: 1, 2, 4, 8, 16, 32, 60, 60, ...
        val expectedSequence = listOf(1L, 2L, 4L, 8L, 16L, 32L, 60L, 60L, 60L)
        
        expectedSequence.forEachIndexed { index, expectedSeconds ->
            val backoff = stateMachine.calculateBackoff(index)
            backoff.inWholeSeconds shouldBe expectedSeconds
        }
    }
    
    @Test
    fun `max retry interval should be 60 seconds`() = runTest {
        // Feature: ssh-tunnel-proxy, Property 17: Exponential backoff retry pattern
        // Validates: Requirements 4.3
        val stateMachine = ReconnectionStateMachine()
        
        stateMachine.getMaxRetryInterval() shouldBe 60.seconds
    }
}
