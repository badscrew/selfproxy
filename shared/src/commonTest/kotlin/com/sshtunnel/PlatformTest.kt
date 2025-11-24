package com.sshtunnel

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun testPlatform() {
        val platform = getPlatform()
        assertTrue(platform.name.isNotEmpty(), "Platform name should not be empty")
    }
}
