package com.sshtunnel.ssh

import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import com.sshtunnel.logging.LogEntry
import com.sshtunnel.logging.Logger
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Property-based tests for SSH Command Builder functionality.
 * 
 * Tests the SSH command construction logic to ensure all required options
 * are included and properly formatted.
 */
class SSHCommandBuilderPropertiesTest {
    
    private val testLogger = object : Logger {
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
    
    private val commandBuilder = AndroidSSHCommandBuilder(testLogger)
    
    /**
     * Feature: native-ssh-client, Property 3: Dynamic port forwarding configuration
     * Validates: Requirements 1.5
     * 
     * For any valid port number, when the system builds an SSH command,
     * the command should include the -D option with that port number.
     */
    @Test
    fun `SSH command should include dynamic port forwarding with specified port`() = runTest {
        // Feature: native-ssh-client, Property 3: Dynamic port forwarding configuration
        // Validates: Requirements 1.5
        
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.validPort(),
            Arb.filePath()
        ) { profile, localPort, privateKeyPath ->
            val binaryPath = "/data/data/com.sshtunnel/files/ssh"
            
            val command = commandBuilder.buildCommand(
                binaryPath = binaryPath,
                profile = profile,
                privateKeyPath = privateKeyPath,
                localPort = localPort
            )
            
            // Command should contain -D option
            command shouldContain "-D"
            
            // The port should appear right after -D
            val dashDIndex = command.indexOf("-D")
            val portAfterDashD = command[dashDIndex + 1]
            portAfterDashD shouldBe localPort.toString()
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 6: SSH command includes private key path
     * Validates: Requirements 4.3
     * 
     * For any SSH command built with a private key, the command should include
     * the -i option followed by the key file path.
     */
    @Test
    fun `SSH command should include private key path with -i option`() = runTest {
        // Feature: native-ssh-client, Property 6: SSH command includes private key path
        // Validates: Requirements 4.3
        
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.validPort(),
            Arb.filePath()
        ) { profile, localPort, privateKeyPath ->
            val binaryPath = "/data/data/com.sshtunnel/files/ssh"
            
            val command = commandBuilder.buildCommand(
                binaryPath = binaryPath,
                profile = profile,
                privateKeyPath = privateKeyPath,
                localPort = localPort
            )
            
            // Command should contain -i option
            command shouldContain "-i"
            
            // The private key path should appear right after -i
            val dashIIndex = command.indexOf("-i")
            val pathAfterDashI = command[dashIIndex + 1]
            pathAfterDashI shouldBe privateKeyPath
        }
    }
    
    /**
     * Feature: native-ssh-client, Property 8: SSH command includes required options
     * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
     * 
     * For any SSH command built by the system, the command should include all
     * required options: -D (port forwarding), -N (no remote command), -T (no pseudo-terminal),
     * ServerAliveInterval=60, ServerAliveCountMax=10, ExitOnForwardFailure=yes,
     * and ConnectTimeout=30.
     */
    @Test
    fun `SSH command should include all required options`() = runTest {
        // Feature: native-ssh-client, Property 8: SSH command includes required options
        // Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
        
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.validPort(),
            Arb.filePath()
        ) { profile, localPort, privateKeyPath ->
            val binaryPath = "/data/data/com.sshtunnel/files/ssh"
            
            val command = commandBuilder.buildCommand(
                binaryPath = binaryPath,
                profile = profile,
                privateKeyPath = privateKeyPath,
                localPort = localPort
            )
            
            // Requirement 5.1: Dynamic port forwarding (-D)
            command shouldContain "-D"
            command shouldContain localPort.toString()
            
            // Requirement 5.2: No remote command execution (-N)
            command shouldContain "-N"
            
            // Requirement 5.3: No pseudo-terminal allocation (-T)
            command shouldContain "-T"
            
            // Requirement 5.4: ServerAliveInterval=60
            command shouldContain "ServerAliveInterval=60"
            
            // Requirement 5.5: ServerAliveCountMax=10
            command shouldContain "ServerAliveCountMax=10"
            
            // Requirement 5.6: ExitOnForwardFailure=yes
            command shouldContain "ExitOnForwardFailure=yes"
            
            // Requirement 5.7: ConnectTimeout=30
            command shouldContain "ConnectTimeout=30"
            
            // Verify -o options are properly formatted
            // Each SSH option should be preceded by -o
            val serverAliveIntervalIndex = command.indexOf("ServerAliveInterval=60")
            command[serverAliveIntervalIndex - 1] shouldBe "-o"
            
            val serverAliveCountMaxIndex = command.indexOf("ServerAliveCountMax=10")
            command[serverAliveCountMaxIndex - 1] shouldBe "-o"
            
            val exitOnForwardFailureIndex = command.indexOf("ExitOnForwardFailure=yes")
            command[exitOnForwardFailureIndex - 1] shouldBe "-o"
            
            val connectTimeoutIndex = command.indexOf("ConnectTimeout=30")
            command[connectTimeoutIndex - 1] shouldBe "-o"
        }
    }
    
    /**
     * Additional test: Verify command structure and user@host format
     * 
     * Ensures the command includes the binary path, port specification,
     * and properly formatted user@host string.
     */
    @Test
    fun `SSH command should have correct structure with user@host`() = runTest {
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.validPort(),
            Arb.filePath()
        ) { profile, localPort, privateKeyPath ->
            val binaryPath = "/data/data/com.sshtunnel/files/ssh"
            
            val command = commandBuilder.buildCommand(
                binaryPath = binaryPath,
                profile = profile,
                privateKeyPath = privateKeyPath,
                localPort = localPort
            )
            
            // First element should be the binary path
            command[0] shouldBe binaryPath
            
            // Command should contain port specification
            command shouldContain "-p"
            val dashPIndex = command.indexOf("-p")
            command[dashPIndex + 1] shouldBe profile.port.toString()
            
            // Last element should be user@host
            val userHost = "${profile.username}@${profile.hostname}"
            command.last() shouldBe userHost
        }
    }
    
    // Custom generators for property-based testing
    
    companion object {
        /**
         * Generates valid port numbers (1024-65535).
         * Avoids privileged ports (0-1023).
         */
        fun Arb.Companion.validPort(): Arb<Int> = int(1024..65535)
        
        /**
         * Generates valid file paths for private keys.
         * Note: These paths may not exist in test environment, but are valid format.
         */
        fun Arb.Companion.filePath(): Arb<String> = arbitrary {
            val id = Arb.long(1L, 10000L).bind()
            "/data/data/com.sshtunnel/files/keys/key_$id"
        }
        
        /**
         * Generates valid server profiles with random but realistic values.
         */
        fun Arb.Companion.serverProfile(): Arb<ServerProfile> = arbitrary {
            ServerProfile(
                id = Arb.long(1L, 1000L).bind(),
                name = Arb.string(5..20, Codepoint.alphanumeric()).bind(),
                hostname = hostname().bind(),
                port = Arb.int(1..65535).bind(),
                username = username().bind(),
                keyType = Arb.enum<KeyType>().bind(),
                createdAt = System.currentTimeMillis(),
                lastUsed = null
            )
        }
        
        /**
         * Generates valid hostnames (domain names or IP addresses).
         */
        fun Arb.Companion.hostname(): Arb<String> = arbitrary {
            val parts = List(3) { Arb.int(1, 255).bind() }
            "${parts[0]}.${parts[1]}.${parts[2]}.${Arb.int(1, 255).bind()}"
        }
        
        /**
         * Generates valid SSH usernames (3-32 characters, alphanumeric).
         */
        fun Arb.Companion.username(): Arb<String> = arbitrary {
            val length = Arb.int(3, 32).bind()
            Arb.string(length, Codepoint.alphanumeric()).bind()
        }
    }
}
