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
 * Feature: shadowsocks-vpn-proxy, Property 1: Profile CRUD operations maintain data integrity
 * Validates: Requirements 1.1, 1.2, 1.3
 */
class ServerProfilePropertiesTest {
    
    @Test
    fun `profile serialization round-trip should preserve data`() = runTest {
        // Feature: shadowsocks-vpn-proxy, Property 1: Profile CRUD operations maintain data integrity
        // Validates: Requirements 1.1, 1.2, 1.3
        checkAll(
            iterations = 100,
            Arb.serverProfile()
        ) { profile ->
            // Serialize to JSON
            val json = Json.encodeToString(profile)
            
            // Deserialize back
            val deserialized = Json.decodeFromString<ServerProfile>(json)
            
            // Verify all Shadowsocks fields are preserved
            deserialized.id shouldBe profile.id
            deserialized.name shouldBe profile.name
            deserialized.serverHost shouldBe profile.serverHost
            deserialized.serverPort shouldBe profile.serverPort
            deserialized.cipher shouldBe profile.cipher
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
            copied.serverHost shouldBe profile.serverHost
            copied.serverPort shouldBe profile.serverPort
            copied.cipher shouldBe profile.cipher
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
                profile1.serverHost != profile2.serverHost ||
                profile1.serverPort != profile2.serverPort ||
                profile1.cipher != profile2.cipher ||
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
 * Generates random ServerProfile instances with valid Shadowsocks data.
 */
fun Arb.Companion.serverProfile(): Arb<ServerProfile> = arbitrary {
    @Suppress("DEPRECATION")
    ServerProfile(
        id = Arb.long(0..Long.MAX_VALUE).bind(),
        name = Arb.profileName().bind(),
        serverHost = Arb.hostname().bind(),
        serverPort = Arb.shadowsocksPort().bind(),
        cipher = Arb.enum<CipherMethod>().bind(),
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
 * Generates valid Shadowsocks port numbers (1024-65535, with common ports weighted higher).
 */
fun Arb.Companion.shadowsocksPort(): Arb<Int> = Arb.choice(
    Arb.of(8388, 8389, 443, 8080), // Common Shadowsocks ports (higher weight)
    Arb.int(1024..65535) // Any valid non-privileged port
)

/**
 * Generates valid timestamps (milliseconds since epoch).
 */
fun Arb.Companion.timestamp(): Arb<Long> = Arb.long(
    0L..System.currentTimeMillis()
)
