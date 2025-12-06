package com.sshtunnel.reconnection

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Property-based tests for ReconnectionStateMachine.
 * 
 * Feature: shadowsocks-vpn-proxy, Property 7: Reconnection backoff increases exponentially
 * Validates: Requirements 6.2
 */
class ReconnectionStateMachinePropertiesTest {
    
    @Test
    fun `backoff should increase exponentially with each attempt`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 7: Reconnection backoff increases exponentially
        // Validates: Requirements 6.2
        
        val stateMachine = ReconnectionStateMachine()
        
        checkAll(
            iterations = 100,
            Arb.int(0..10) // Test attempts 0 through 10
        ) { attemptNumber ->
            val backoff = stateMachine.calculateBackoff(attemptNumber)
            
            // Backoff should be positive
            assertTrue(
                backoff.inWholeSeconds > 0,
                "Backoff for attempt $attemptNumber should be positive, got ${backoff.inWholeSeconds}s"
            )
            
            // For attempts before max, backoff should be 2^attemptNumber seconds
            if (attemptNumber < 6) { // 2^6 = 64 > 60 (max)
                val expectedSeconds = (1 shl attemptNumber).toLong() // 2^attemptNumber
                assertTrue(
                    backoff.inWholeSeconds == expectedSeconds,
                    "Backoff for attempt $attemptNumber should be ${expectedSeconds}s, got ${backoff.inWholeSeconds}s"
                )
            }
            
            // Backoff should never exceed max (60 seconds)
            assertTrue(
                backoff.inWholeSeconds <= 60,
                "Backoff for attempt $attemptNumber should not exceed 60s, got ${backoff.inWholeSeconds}s"
            )
        }
    }
    
    @Test
    fun `backoff should be capped at maximum retry interval`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 7: Reconnection backoff increases exponentially
        // Validates: Requirements 6.2
        
        val stateMachine = ReconnectionStateMachine()
        val maxRetryInterval = stateMachine.getMaxRetryInterval()
        
        checkAll(
            iterations = 100,
            Arb.int(6..100) // Test high attempt numbers where cap should apply
        ) { attemptNumber ->
            val backoff = stateMachine.calculateBackoff(attemptNumber)
            
            // Backoff should be capped at max retry interval
            assertTrue(
                backoff <= maxRetryInterval,
                "Backoff for attempt $attemptNumber should be capped at ${maxRetryInterval.inWholeSeconds}s, got ${backoff.inWholeSeconds}s"
            )
            
            // For high attempts, backoff should equal max
            assertTrue(
                backoff == maxRetryInterval,
                "Backoff for attempt $attemptNumber should equal max ${maxRetryInterval.inWholeSeconds}s, got ${backoff.inWholeSeconds}s"
            )
        }
    }
    
    @Test
    fun `backoff should be monotonically increasing until cap`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 7: Reconnection backoff increases exponentially
        // Validates: Requirements 6.2
        
        val stateMachine = ReconnectionStateMachine()
        
        checkAll(
            iterations = 100,
            Arb.int(0..9) // Test consecutive attempts
        ) { attemptNumber ->
            val currentBackoff = stateMachine.calculateBackoff(attemptNumber)
            val nextBackoff = stateMachine.calculateBackoff(attemptNumber + 1)
            
            // Next backoff should be >= current backoff (monotonically increasing)
            assertTrue(
                nextBackoff >= currentBackoff,
                "Backoff should increase: attempt $attemptNumber = ${currentBackoff.inWholeSeconds}s, attempt ${attemptNumber + 1} = ${nextBackoff.inWholeSeconds}s"
            )
            
            // If not at cap, next should be exactly double (or reach cap)
            if (currentBackoff < 60.seconds) {
                val expectedNext = minOf((currentBackoff.inWholeSeconds * 2).seconds, 60.seconds)
                assertTrue(
                    nextBackoff == expectedNext,
                    "Next backoff should be ${expectedNext.inWholeSeconds}s, got ${nextBackoff.inWholeSeconds}s"
                )
            }
        }
    }
    
    @Test
    fun `recordAttempt should increment counter and return correct backoff`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 7: Reconnection backoff increases exponentially
        // Validates: Requirements 6.2
        
        checkAll(
            iterations = 100,
            Arb.int(1..10) // Test multiple sequential attempts
        ) { numAttempts ->
            val stateMachine = ReconnectionStateMachine()
            
            for (i in 0 until numAttempts) {
                val backoff = stateMachine.recordAttempt()
                val expectedBackoff = stateMachine.calculateBackoff(i)
                
                assertTrue(
                    backoff == expectedBackoff,
                    "recordAttempt() at attempt $i should return ${expectedBackoff.inWholeSeconds}s, got ${backoff.inWholeSeconds}s"
                )
                
                assertTrue(
                    stateMachine.getCurrentAttempt() == i + 1,
                    "After $i attempts, counter should be ${i + 1}, got ${stateMachine.getCurrentAttempt()}"
                )
            }
        }
    }
    
    @Test
    fun `reset should clear attempt counter`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 7: Reconnection backoff increases exponentially
        // Validates: Requirements 6.2
        
        checkAll(
            iterations = 100,
            Arb.int(1..20) // Test with various attempt counts before reset
        ) { attemptsBeforeReset ->
            val stateMachine = ReconnectionStateMachine()
            
            // Record some attempts
            repeat(attemptsBeforeReset) {
                stateMachine.recordAttempt()
            }
            
            // Verify counter increased
            assertTrue(
                stateMachine.getCurrentAttempt() == attemptsBeforeReset,
                "Before reset, counter should be $attemptsBeforeReset, got ${stateMachine.getCurrentAttempt()}"
            )
            
            // Reset
            stateMachine.reset()
            
            // Verify counter is back to 0
            assertTrue(
                stateMachine.getCurrentAttempt() == 0,
                "After reset, counter should be 0, got ${stateMachine.getCurrentAttempt()}"
            )
            
            // Verify next backoff is back to initial value
            val backoffAfterReset = stateMachine.recordAttempt()
            assertTrue(
                backoffAfterReset == 1.seconds,
                "First backoff after reset should be 1s, got ${backoffAfterReset.inWholeSeconds}s"
            )
        }
    }
}
