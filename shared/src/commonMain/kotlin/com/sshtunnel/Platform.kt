package com.sshtunnel

/**
 * Platform-specific interface for getting platform information.
 * This is a simple example to verify the multiplatform setup works.
 */
interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
