package com.sshtunnel.ssh

import android.content.Context
import android.os.Build
import com.sshtunnel.logging.LogEntry
import com.sshtunnel.logging.Logger
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Property-based tests for SSH Client Factory.
 * 
 * Tests universal properties that should hold across all inputs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class SSHClientFactoryPropertiesTest {
    
    private lateinit var context: Context
    private lateinit var factory: AndroidSSHClientFactory
    private lateinit var logger: TestLogger
    
    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        factory = AndroidSSHClientFactory(context)
        logger = TestLogger()
    }
    
    /**
     * Simple test logger implementation.
     */
    private class TestLogger : Logger {
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
    
    @Test
    fun `property 17 - native SSH availability check is consistent`() = runTest {
        // Feature: native-ssh-client, Property 17: Native SSH availability check
        // Validates: Requirements 10.1
        
        // For any SSH client initialization, the system should check whether native SSH 
        // binaries are available before attempting to use them
        
        // The availability check should be consistent - calling it multiple times
        // should return the same result
        val firstCheck = factory.isNativeSSHAvailable()
        val secondCheck = factory.isNativeSSHAvailable()
        val thirdCheck = factory.isNativeSSHAvailable()
        
        firstCheck shouldBe secondCheck
        secondCheck shouldBe thirdCheck
    }
    
    @Test
    fun `property 18 - native implementation preference is respected`() = runTest {
        // Feature: native-ssh-client, Property 18: Native implementation preference
        // Validates: Requirements 10.2
        
        // For any SSH client creation, when native SSH is available and no user preference 
        // overrides it, the system should create a native SSH client instance
        
        checkAll(100, Arb.boolean()) { preferNative ->
            val client = factory.create(logger, preferNative)
            
            // Client should always be created successfully
            client.shouldBeInstanceOf<SSHClient>()
            
            // If native is available and preferred, should get native client
            // If native is not available or not preferred, should get sshj client
            val nativeAvailable = factory.isNativeSSHAvailable()
            
            if (preferNative && nativeAvailable) {
                // Should get native client
                client.shouldBeInstanceOf<AndroidNativeSSHClient>()
            } else {
                // Should get sshj client
                client.shouldBeInstanceOf<AndroidSSHClient>()
            }
        }
    }
    
    @Test
    fun `property 18 - fallback to sshj when native unavailable`() = runTest {
        // Feature: native-ssh-client, Property 18: Native implementation preference
        // Validates: Requirements 10.2, 1.2
        
        // For any SSH client creation, when native SSH is not available,
        // the system should fall back to sshj implementation regardless of preference
        
        checkAll(100, Arb.boolean()) { preferNative ->
            val client = factory.create(logger, preferNative)
            
            // Client should always be created successfully
            client.shouldBeInstanceOf<SSHClient>()
            
            val nativeAvailable = factory.isNativeSSHAvailable()
            
            // If native is not available, should always get sshj client
            if (!nativeAvailable) {
                client.shouldBeInstanceOf<AndroidSSHClient>()
            }
        }
    }
    
    @Test
    fun `property 17 - availability check does not throw exceptions`() = runTest {
        // Feature: native-ssh-client, Property 17: Native SSH availability check
        // Validates: Requirements 10.1
        
        // The availability check should never throw exceptions, even if binaries
        // are missing or corrupted. It should return false instead.
        
        // Call availability check multiple times - should never throw
        repeat(10) {
            val result = try {
                factory.isNativeSSHAvailable()
                true
            } catch (e: Exception) {
                false
            }
            
            result shouldBe true // Should not throw
        }
    }
    
    @Test
    fun `property 18 - client creation always succeeds`() = runTest {
        // Feature: native-ssh-client, Property 18: Native implementation preference
        // Validates: Requirements 10.2
        
        // For any preference setting, client creation should always succeed
        // by falling back to sshj if native is unavailable
        
        checkAll(100, Arb.boolean()) { preferNative ->
            val client = factory.create(logger, preferNative)
            
            // Should always get a valid SSH client
            client.shouldBeInstanceOf<SSHClient>()
        }
    }
}
