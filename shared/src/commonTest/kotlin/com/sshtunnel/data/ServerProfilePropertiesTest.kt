package com.sshtunnel.data

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

/**
 * Property-based tests for ServerProfile data model.
 * 
 * Feature: ssh-tunnel-proxy, Property 6: Profile creation round-trip
 * Validates: Requirements 2.1
 */
class ServerProfilePropertiesTest {
    
    @Test
    fun `profile serialization round-trip should preserve data`() = runTest {
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            // Serialize to JSON
            val json = Json.encodeToString(profile)
            
            // Deserialize back
            val deserialized = Json.decodeFromString<ServerProfile>(json)
            
            // Verify all fields are preserved
            deserialized.id shouldBe profile.id
            deserialized.name shouldBe profile.name
            deserialized.hostname shouldBe profile.hostname
            deserialized.port shouldBe profile.port
            deserialized.username shouldBe profile.username
            deserialized.keyType shouldBe profile.keyType
            deserialized.createdAt shouldBe profile.createdAt
            deserialized.lastUsed shouldBe profile.lastUsed
        }
    }
    
    @Test
    fun `profile copy should preserve all data`() = runTest {
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            // Create a copy
            val copied = profile.copy()
            
            // Verify all fields are preserved
            copied shouldBe profile
            copied.id shouldBe profile.id
            copied.name shouldBe profile.name
            copied.hostname shouldBe profile.hostname
            copied.port shouldBe profile.port
            copied.username shouldBe profile.username
            copied.keyType shouldBe profile.keyType
            copied.createdAt shouldBe profile.createdAt
            copied.lastUsed shouldBe profile.lastUsed
        }
    }
    
    @Test
    fun `profile with different values should not be equal`() = runTest {
        checkAll(
            iterations = 100,
            Arb.serverProfile(),
            Arb.serverProfile()
        ) { profile1, profile2 ->
            // If any field differs, profiles should not be equal
            if (profile1.id != profile2.id ||
                profile1.name != profile2.name ||
                profile1.hostname != profile2.hostname ||
                profile1.port != profile2.port ||
                profile1.username != profile2.username ||
                profile1.keyType != profile2.keyType ||
                profile1.createdAt != profile2.createdAt ||
                profile1.lastUsed != profile2.lastUsed) {
                profile1 shouldNotBe profile2
            }
        }
    }
}

/**
 * Custom Kotest Arbitrary generators for ServerProfile and related types.
 */

/**
 * Generates random ServerProfile instances with valid data.
 */
fun Arb.Companion.serverProfile(): Arb<ServerProfile> = arbitrary {
    ServerProfile(
        id = Arb.long(0..Long.MAX_VALUE).bind(),
        name = Arb.profileName().bind(),
        hostname = Arb.hostname().bind(),
        port = Arb.sshPort().bind(),
        username = Arb.username().bind(),
        keyType = Arb.enum<KeyType>().bind(),
        createdAt = Arb.timestamp().bind(),
        lastUsed = Arb.timestamp().orNull(0.3).bind() // 30% chance of null
    )
}

/**
 * Generates valid profile names (3-50 characters).
 */
fun Arb.Companion.profileName(): Arb<String> = arbitrary {
    val length = Arb.int(3..50).bind()
    Arb.string(length, Codepoint.alphanumeric()).bind()
}

/**
 * Generates valid hostnames (domain names or IP addresses).
 */
fun Arb.Companion.hostname(): Arb<String> = Arb.choice(
    Arb.domain(),
    Arb.ipAddress()
)

/**
 * Generates valid domain names.
 */
fun Arb.Companion.domain(): Arb<String> = arbitrary {
    val parts = Arb.list(
        Arb.string(3..10, Codepoint.az()),
        2..4
    ).bind()
    val tld = Arb.of("com", "net", "org", "io", "dev").bind()
    parts.joinToString(".") + ".$tld"
}

/**
 * Generates valid IPv4 addresses.
 */
fun Arb.Companion.ipAddress(): Arb<String> = arbitrary {
    val octets = List(4) { Arb.int(0..255).bind() }
    octets.joinToString(".")
}

/**
 * Generates valid SSH port numbers (1-65535, with common ports weighted higher).
 */
fun Arb.Companion.sshPort(): Arb<Int> = Arb.choice(
    Arb.of(22, 2222, 22000), // Common SSH ports (higher weight)
    Arb.int(1..65535) // Any valid port
)

/**
 * Generates valid usernames (3-32 characters, alphanumeric).
 */
fun Arb.Companion.username(): Arb<String> = arbitrary {
    val length = Arb.int(3..32).bind()
    Arb.string(length, Codepoint.alphanumeric()).bind()
}

/**
 * Generates valid timestamps (milliseconds since epoch).
 */
fun Arb.Companion.timestamp(): Arb<Long> = Arb.long(
    0L..System.currentTimeMillis()
)
