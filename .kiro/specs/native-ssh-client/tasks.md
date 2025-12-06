# Implementation Plan

- [x] 1. Set up native binary infrastructure





  - Download OpenSSH binaries from Termux for all architectures (ARM64, ARM32, x86_64, x86)
  - Create jniLibs directory structure in androidApp module
  - Place binaries and OpenSSL libraries in architecture-specific directories
  - Update build.gradle.kts to configure APK splits for architecture-specific builds
  - Document binary sources, versions, and checksums
  - _Requirements: 2.1, 2.2, 2.3, 13.1, 13.2, 13.3_

- [x] 2. Implement Binary Manager component





  - Create BinaryManager interface and implementation
  - Implement device architecture detection logic
  - Implement binary extraction from APK to private directory
  - Implement binary caching mechanism with version tracking
  - Implement checksum verification for extracted binaries
  - Implement corruption detection and re-extraction logic
  - Set executable permissions on extracted binaries
  - _Requirements: 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 12.1, 12.2, 12.3, 12.4, 12.5_

- [x] 2.1 Write property test for binary manager


  - **Property 1: Native binary selection by architecture**
  - **Property 4: Executable permissions on extracted binary**
  - **Property 20: Binary caching on first extraction**
  - **Property 21: Cached binary reuse**
  - **Property 22: Re-extraction on version update**
  - **Property 23: Checksum verification**
  - **Property 24: Re-extraction on corruption**
  - **Validates: Requirements 1.1, 2.5, 3.2, 3.3, 3.4, 3.5, 12.1, 12.2, 12.3, 12.4, 12.5**

- [x] 3. Implement Private Key Manager component








  - Create PrivateKeyManager interface and implementation
  - Implement private key file creation in app's private directory
  - Implement secure file permission setting (owner read/write only)
  - Implement private key file deletion
  - Implement cleanup on errors
  - _Requirements: 4.1, 4.2, 4.4_

- [x] 3.1 Write property test for private key manager


  - **Property 5: Private key file creation in private directory**
  - **Property 7: Private key cleanup on termination**
  - **Validates: Requirements 4.1, 4.2, 4.4**

- [x] 4. Implement SSH Command Builder component





  - Create SSHCommandBuilder interface and implementation
  - Implement command construction with dynamic port forwarding (-D option)
  - Add SSH options: -N (no remote command), -T (no pseudo-terminal)
  - Add keep-alive options: ServerAliveInterval=60, ServerAliveCountMax=10
  - Add connection options: ExitOnForwardFailure=yes, ConnectTimeout=30
  - Add private key option: -i with key file path
  - Add port and user@host formatting
  - _Requirements: 1.5, 4.3, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

- [x] 4.1 Write property test for SSH command builder


  - **Property 3: Dynamic port forwarding configuration**
  - **Property 6: SSH command includes private key path**
  - **Property 8: SSH command includes required options**
  - **Validates: Requirements 1.5, 4.3, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7**

- [x] 5. Implement Process Manager component




  - Create ProcessManager interface and implementation
  - Implement process start with ProcessBuilder
  - Implement process output stream capture and monitoring
  - Implement process alive status checking
  - Implement graceful process termination with 5-second timeout
  - Implement forced process termination (destroyForcibly)
  - Implement process output monitoring with Flow
  - _Requirements: 6.1, 6.3, 7.1, 7.2, 7.3_

- [x] 5.1 Write property test for process manager



  - **Property 9: Process output capture**
  - **Property 10: Process alive status check**
  - **Property 12: Process termination detection**
  - **Property 13: Graceful shutdown with timeout**
  - **Validates: Requirements 6.1, 6.3, 7.1, 7.2, 7.3**

- [x] 6. Implement Connection Monitor component





  - Create ConnectionMonitor interface and implementation
  - Implement SOCKS5 port availability checking
  - Implement connection health monitoring (check every 1 second)
  - Implement connection state flow (Connected, Disconnected, Error)
  - Implement process termination detection
  - Implement connection state updates
  - _Requirements: 6.4, 6.5, 7.5, 14.5_

- [x] 6.1 Write property test for connection monitor


  - **Property 11: SOCKS5 port availability check**
  - **Property 27: Process health monitoring frequency**
  - **Validates: Requirements 6.4, 14.5**

- [x] 7. Implement Native SSH Client





  - Create NativeSSHClient interface and implementation
  - Integrate BinaryManager for binary extraction
  - Integrate PrivateKeyManager for key file handling
  - Integrate SSHCommandBuilder for command construction
  - Integrate ProcessManager for process lifecycle
  - Integrate ConnectionMonitor for health monitoring
  - Implement startSSHTunnel method
  - Implement stopSSHTunnel method with cleanup
  - Implement isRunning status check
  - Implement process output observation
  - _Requirements: 1.1, 1.4, 1.5, 6.2, 7.4, 7.5_

- [x] 7.1 Write property test for native SSH client


  - **Property 12: Process termination detection**
  - **Validates: Requirements 6.5, 7.5**

- [x] 8. Implement Error Handler component





  - Create SSHError sealed class hierarchy
  - Create ErrorHandler interface and implementation
  - Implement error classification logic
  - Implement recovery action determination
  - Implement fallback to sshj on binary extraction failure
  - Implement retry logic for process start failures
  - Implement reconnection logic for connection failures
  - Implement cleanup on process crashes
  - _Requirements: 8.1, 8.2, 8.4, 8.5_

- [x] 8.1 Write property test for error handler


  - **Property 2: Fallback to sshj on native unavailability**
  - **Property 14: Error result on process start failure**
  - **Property 15: Reconnection on connection loss**
  - **Property 16: Resource cleanup on crash**
  - **Validates: Requirements 8.1, 8.2, 8.4, 8.5**

- [x] 9. Implement SSH Client Factory





  - Create SSHClientFactory interface and implementation
  - Implement native SSH availability checking
  - Implement SSH client creation with preference handling
  - Implement native vs sshj selection logic
  - Implement fallback mechanism when native unavailable
  - _Requirements: 1.2, 10.1, 10.2_

- [x] 9.1 Write property test for SSH client factory


  - **Property 17: Native SSH availability check**
  - **Property 18: Native implementation preference**
  - **Validates: Requirements 10.1, 10.2**

- [x] 10. Implement user preference management





  - Add SSH implementation type preference to settings
  - Implement preference persistence using DataStore
  - Implement preference retrieval in SSH client factory
  - Add UI setting for SSH implementation selection
  - Update SSH client creation to respect user preference
  - _Requirements: 10.3, 10.4, 10.5_

- [x] 10.1 Write property test for preference management


  - **Property 19: User preference persistence**
  - **Validates: Requirements 10.3, 10.4, 10.5**

- [x] 11. Integrate native SSH with VPN service





  - Update TunnelVpnService to use SSH client factory
  - Implement SSH tunnel start in VPN service lifecycle
  - Implement private key file creation before SSH start
  - Implement private key file cleanup after SSH stop
  - Update connection state management for native SSH
  - Implement process monitoring in VPN service
  - Handle SSH process termination and restart
  - _Requirements: 14.1, 14.2, 14.3_

- [x] 11.1 Write property test for VPN integration



  - **Property 25: Process termination detection**
  - **Property 26: Automatic process restart**
  - **Validates: Requirements 14.2, 14.3**

- [x] 12. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. Implement connection monitoring and reconnection





  - Integrate ConnectionMonitor with VPN service
  - Implement connection loss detection (within 5 seconds)
  - Implement automatic reconnection with exponential backoff
  - Implement reconnection policy configuration
  - Update connection state UI based on monitoring
  - _Requirements: 8.3, 8.4_

- [x] 13.1 Write integration test for reconnection


  - Test connection loss detection
  - Test automatic reconnection behavior
  - Test reconnection backoff policy
  - _Requirements: 8.3, 8.4_

- [x] 14. Add logging and diagnostics





  - Implement SSH process output parsing
  - Add structured logging for SSH events
  - Implement error message extraction from SSH output
  - Add connection status logging
  - Implement diagnostic information collection
  - _Requirements: 6.2_

- [x] 15. Implement performance optimizations



  - Optimize binary extraction (lazy loading)
  - Implement efficient process monitoring (reduce polling)
  - Optimize memory usage (stream buffering)
  - Implement battery-efficient monitoring
  - Add performance metrics collection
  - _Requirements: Performance considerations from design_

- [ ] 16. Add security hardening
  - Implement argument validation for SSH command
  - Add output sanitization for logging
  - Implement safe error message generation
  - Add binary integrity verification
  - Document security considerations
  - _Requirements: Security considerations from design_

- [ ] 17. Create comprehensive integration tests
  - Test end-to-end SSH connection with real server
  - Test SOCKS5 proxy functionality through native SSH
  - Test VPN traffic routing through native SSH tunnel
  - Test network change handling (WiFi to mobile data)
  - Test long-running connection stability
  - Test fallback to sshj when native fails
  - Test user preference switching
  - _Requirements: 11.5, 11.6, 11.7_

- [ ] 18. Perform manual testing and validation
  - Test on ARM64 physical device
  - Test on x86_64 emulator
  - Test with different SSH servers
  - Test with different key types (RSA, ECDSA, Ed25519)
  - Test network interruptions
  - Test battery usage over 24 hours
  - Verify no "Stream closed" errors
  - Measure APK size increase
  - Verify >95% connection success rate
  - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_

- [ ] 19. Update documentation
  - Document native SSH implementation in README
  - Add troubleshooting guide for native SSH
  - Document binary sources and versions
  - Add Termux attribution and license information
  - Document migration path from sshj
  - Create user guide for SSH implementation selection
  - _Requirements: 13.4_

- [ ] 20. Final checkpoint - Verify all success criteria
  - Ensure all tests pass, ask the user if questions arise.
