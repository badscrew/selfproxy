package com.sshtunnel.ssh

/**
 * SSH error types specific to native SSH implementation.
 */
sealed class NativeSSHError : Exception() {
    data class BinaryExtractionFailed(override val message: String, override val cause: Throwable? = null) : NativeSSHError()
    data class ProcessStartFailed(override val message: String, override val cause: Throwable? = null) : NativeSSHError()
    data class ConnectionFailed(override val message: String, override val cause: Throwable? = null) : NativeSSHError()
    data class ProcessCrashed(val exitCode: Int, override val message: String) : NativeSSHError()
    data class ProcessKilled(val signal: Int, override val message: String) : NativeSSHError()
    data object PortUnavailable : NativeSSHError() {
        override val message: String = "SOCKS5 port is unavailable"
    }
}

/**
 * Recovery actions that can be taken in response to errors.
 */
sealed class RecoveryAction {
    /**
     * Fall back to using the sshj implementation instead of native SSH.
     */
    data object FallbackToSshj : RecoveryAction()
    
    /**
     * Retry the operation (with optional delay).
     */
    data class Retry(val delayMs: Long = 0) : RecoveryAction()
    
    /**
     * Attempt to reconnect with exponential backoff.
     */
    data class Reconnect(val backoffMs: Long = 1000) : RecoveryAction()
    
    /**
     * Fail the operation with the given message.
     */
    data class Fail(val message: String) : RecoveryAction()
}

/**
 * Interface for handling SSH errors and determining recovery actions.
 */
interface ErrorHandler {
    /**
     * Handle an SSH error and determine the appropriate recovery action.
     * 
     * @param error The error that occurred
     * @return The recovery action to take
     */
    suspend fun handleError(error: Throwable): RecoveryAction
    
    /**
     * Classify an error into a specific error type.
     * 
     * @param error The error to classify
     * @return The classified error type
     */
    fun classifyError(error: Throwable): NativeSSHError
    
    /**
     * Clean up resources after an error.
     * 
     * @param sessionId The session ID to clean up
     */
    suspend fun cleanup(sessionId: String)
}
