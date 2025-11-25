# Implementation Plan

## Phase 1: Project Setup and Shared Core

- [x] 1. Set up Kotlin Multiplatform project structure





  - Create shared module with commonMain, commonTest, androidMain, androidTest source sets
  - Create androidApp module for Android UI
  - Configure Gradle build files for multiplatform
  - Set up SQLDelight for cross-platform database
  - Add core dependencies (Kotlin Coroutines, Ktor, kotlinx-serialization)
  - _Requirements: Foundation for all requirements_

- [x] 2. Define shared data models and database schema




  - Create ServerProfile data class in commonMain
  - Create AppRoutingConfig data class in commonMain
  - Create ConnectionSettings data class in commonMain
  - Define SQLDelight schema for server_profiles table
  - Define SQLDelight schema for app_routing_config table
  - _Requirements: 2.1, 2.2, 2.5, 5.1_

- [x] 2.1 Write property test for profile data model






  - **Property 6: Profile creation round-trip**
  - **Validates: Requirements 2.1**

- [x] 3. Implement shared Profile Repository





  - Create ProfileRepository interface in commonMain
  - Implement ProfileRepositoryImpl using SQLDelight in commonMain
  - Implement createProfile, getProfile, getAllProfiles, updateProfile, deleteProfile methods
  - Add error handling with Result types
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 3.1 Write property test for profile repository






  - **Property 7: Profile listing completeness**
  - **Validates: Requirements 2.2**

- [x]* 3.2 Write property test for profile selection


  - **Property 8: Profile selection loads correct details**
  - **Validates: Requirements 2.3**

- [x]* 3.3 Write property test for profile deletion


  - **Property 9: Profile deletion removes data**
  - **Validates: Requirements 2.4**

- [x]* 3.4 Write property test for profile updates



  - **Property 10: Profile updates persist changes**
  - **Validates: Requirements 2.5**

## Phase 2: Android Platform Implementation - Core Infrastructure

- [x] 4. Set up Android app module and basic UI structure




  - Create MainActivity with Jetpack Compose
  - Set up Hilt dependency injection
  - Create navigation structure (profiles, connection, settings screens)
  - Add Material Design 3 theme
  - Configure AndroidManifest with required permissions
  - _Requirements: Foundation for UI requirements_

- [x] 5. Implement Android Credential Store





  - Create CredentialStore interface in commonMain
  - Implement AndroidCredentialStore in androidMain using Android Keystore
  - Implement key encryption using AES-256-GCM
  - Store encrypted keys in EncryptedSharedPreferences
  - Implement storeKey, retrieveKey, deleteKey methods
  - _Requirements: 3.4, 3.5, 9.1_

- [x] 5.1 Write property test for credential encryption





  - **Property 14: Credential storage round-trip with encryption**
  - **Validates: Requirements 3.4, 3.5, 9.1**

- [ ] 6. Implement SSH key parsing and validation

  - Create key parser for RSA, ECDSA, Ed25519 formats
  - Implement key format validation
  - Add support for passphrase-protected keys
  - Implement key type detection
  - _Requirements: 3.1, 3.2, 3.3_

- [ ] 6.1 Write property test for key format support

  - **Property 11: All key formats are supported**
  - **Validates: Requirements 3.1**

- [ ]* 6.2 Write property test for key parsing
  - **Property 12: Key parsing validates format**
  - **Validates: Requirements 3.2**

- [ ]* 6.3 Write property test for passphrase decryption
  - **Property 13: Passphrase-protected keys decrypt correctly**
  - **Validates: Requirements 3.3**

## Phase 3: SSH Connection and SOCKS5 Proxy

- [ ] 7. Implement Android SSH Client using JSch
  - Create SSHClient interface in commonMain
  - Implement AndroidSSHClient in androidMain using JSch library
  - Implement SSH connection with private key authentication
  - Implement dynamic port forwarding (SOCKS5)
  - Add keep-alive packet support
  - Handle SSH session lifecycle
  - _Requirements: 1.1, 1.2, 7.1_

- [ ]* 7.1 Write property test for SSH connection
  - **Property 1: Valid credentials establish connections**
  - **Validates: Requirements 1.1**

- [ ]* 7.2 Write property test for SOCKS5 proxy creation
  - **Property 2: Connected sessions create SOCKS5 proxies**
  - **Validates: Requirements 1.2**

- [ ] 8. Implement shared SSH Connection Manager
  - Create SSHConnectionManager interface in commonMain
  - Implement connection state management with Flow
  - Implement connect, disconnect methods
  - Add connection state observation (Disconnected, Connecting, Connected, Error)
  - Implement error handling and specific error messages
  - _Requirements: 1.1, 1.4, 1.5, 8.1, 8.2, 8.4_

- [ ]* 8.1 Write property test for disconnection cleanup
  - **Property 4: Disconnection cleans up resources**
  - **Validates: Requirements 1.4**

- [ ]* 8.2 Write property test for error messages
  - **Property 5: Connection failures produce specific error messages**
  - **Validates: Requirements 1.5, 8.1, 8.2, 8.4**

## Phase 4: VPN Service and Traffic Routing

- [ ] 9. Implement Android VPN Service
  - Create VpnTunnelProvider interface in commonMain
  - Implement AndroidVpnTunnelProvider in androidMain
  - Extend Android VpnService class
  - Create TUN interface configuration
  - Implement packet routing through SOCKS5 proxy
  - Add DNS routing through tunnel
  - Create foreground service notification
  - _Requirements: 1.3, 10.3_

- [ ]* 9.1 Write property test for traffic routing
  - **Property 3: Active proxies route traffic through SSH server**
  - **Validates: Requirements 1.3**

- [ ] 10. Implement per-app routing
  - Add app selection UI for exclusions
  - Implement app exclusion using addDisallowedApplication
  - Implement routing configuration updates without reconnection
  - Add routing mode support (exclude vs include)
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ]* 10.1 Write property test for app exclusion
  - **Property 20: App routing exclusion correctness**
  - **Validates: Requirements 5.2**

- [ ]* 10.2 Write property test for app inclusion
  - **Property 21: App routing inclusion correctness**
  - **Validates: Requirements 5.3**

- [ ]* 10.3 Write property test for routing updates
  - **Property 22: Routing changes apply without reconnection**
  - **Validates: Requirements 5.4**

- [ ] 11. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Phase 5: Auto-Reconnect and Network Monitoring

- [ ] 12. Implement shared reconnection state machine
  - Create ReconnectionStateMachine in commonMain
  - Implement exponential backoff calculation
  - Add reconnection attempt tracking
  - Implement max retry interval (60 seconds)
  - _Requirements: 4.3_

- [ ]* 12.1 Write property test for backoff pattern
  - **Property 17: Exponential backoff retry pattern**
  - **Validates: Requirements 4.3**

- [ ] 13. Implement Android Network Monitor
  - Create NetworkMonitor interface in commonMain
  - Implement AndroidNetworkMonitor using ConnectivityManager
  - Observe network changes (WiFi, mobile data, lost, available)
  - Emit network state changes as Flow
  - _Requirements: 4.4_

- [ ] 14. Implement Auto-Reconnect Service
  - Create AutoReconnectService interface in commonMain
  - Implement reconnection logic using shared state machine
  - Detect SSH connection drops via keep-alive failures
  - Trigger reconnection on network changes
  - Use WorkManager for background reconnection attempts
  - _Requirements: 4.1, 4.2, 4.4, 4.5_

- [ ]* 14.1 Write property test for disconnection detection
  - **Property 15: Disconnection detection**
  - **Validates: Requirements 4.1**

- [ ]* 14.2 Write property test for reconnection attempts
  - **Property 16: Reconnection attempts after disconnection**
  - **Validates: Requirements 4.2**

- [ ]* 14.3 Write property test for network change reconnection
  - **Property 18: Network change triggers reconnection**
  - **Validates: Requirements 4.4**

- [ ]* 14.4 Write property test for reconnection restoration
  - **Property 19: Successful reconnection restores proxy**
  - **Validates: Requirements 4.5**

## Phase 6: Configuration and Settings

- [ ] 15. Implement connection settings
  - Create settings UI screen
  - Add SSH port configuration
  - Add connection timeout configuration
  - Add keep-alive interval configuration
  - Add compression toggle
  - Add DNS mode selection (through tunnel, custom, system)
  - Add custom SOCKS5 port configuration
  - Add strict host key checking toggle
  - Persist settings using DataStore
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [ ]* 15.1 Write property test for compression negotiation
  - **Property 27: Compression negotiation**
  - **Validates: Requirements 10.2**

- [ ]* 15.2 Write property test for DNS routing
  - **Property 28: DNS routing configuration**
  - **Validates: Requirements 10.3**

- [ ]* 15.3 Write property test for custom SOCKS5 port
  - **Property 29: Custom SOCKS5 port configuration**
  - **Validates: Requirements 10.4**

- [ ]* 15.4 Write property test for host key verification
  - **Property 30: Host key verification**
  - **Validates: Requirements 10.5**

- [ ] 16. Implement battery optimization
  - Request battery optimization exemption
  - Implement configurable keep-alive intervals
  - Adjust keep-alive on battery saver mode
  - Add low battery notification and disconnect option
  - _Requirements: 7.2, 7.3, 7.4, 7.5_

- [ ]* 16.1 Write property test for keep-alive packets
  - **Property 23: Keep-alive packets maintain idle connections**
  - **Validates: Requirements 7.1**

- [ ]* 16.2 Write property test for battery saver adjustment
  - **Property 24: Battery saver adjusts keep-alive intervals**
  - **Validates: Requirements 7.4**

## Phase 7: Connection Testing and Diagnostics

- [ ] 17. Implement shared Connection Test Service
  - Create ConnectionTestService interface in commonMain
  - Implement using Ktor HttpClient in commonMain
  - Query external IP check service (ifconfig.me or ipify.org)
  - Compare result with expected server IP
  - Measure connection latency
  - _Requirements: 12.1, 12.2, 12.3_

- [ ]* 17.1 Write property test for connection test
  - **Property 31: Connection test queries external IP**
  - **Validates: Requirements 12.2**

- [ ]* 17.2 Write property test for routing validation
  - **Property 32: Connection test validates routing**
  - **Validates: Requirements 12.3**

- [ ] 18. Implement diagnostic logging
  - Add verbose logging toggle
  - Implement log sanitization (remove sensitive data)
  - Add log export functionality
  - Display SOCKS5 proxy address and port in UI
  - _Requirements: 8.5, 9.3, 12.4, 12.5_

- [ ]* 18.1 Write property test for log sanitization
  - **Property 25: Error logging excludes sensitive data**
  - **Validates: Requirements 8.5, 9.3**

- [ ]* 18.2 Write property test for verbose logging
  - **Property 33: Verbose logging increases detail**
  - **Validates: Requirements 12.5**

## Phase 8: UI Implementation

- [ ] 19. Implement Profile Management UI
  - Create profile list screen with all saved profiles
  - Add profile creation form (name, hostname, port, username, key file picker)
  - Add profile edit functionality
  - Add profile deletion with confirmation
  - Add profile selection for connection
  - Display profile details (name, server address)
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ] 20. Implement Connection UI
  - Create main connection screen
  - Display connection status (disconnected, connecting, connected, error)
  - Add connect/disconnect button
  - Show current profile information when connected
  - Display error messages with specific failure reasons
  - Add connection test button
  - Show test results (external IP, routing status)
  - _Requirements: 1.1, 1.4, 1.5, 12.1, 12.2, 12.3_

- [ ] 21. Implement App Routing UI
  - Create app selection screen
  - List all installed apps with checkboxes
  - Implement app search/filter
  - Show excluded/included apps
  - Apply routing changes dynamically
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ] 22. Implement Settings UI
  - Create settings screen with all configuration options
  - Add SSH connection settings section
  - Add VPN settings section (DNS mode)
  - Add battery optimization section
  - Add diagnostics section (verbose logging, log export)
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 12.5_

## Phase 9: Privacy and Security Hardening

- [ ] 23. Implement privacy safeguards
  - Verify no third-party data transmission
  - Ensure no analytics or tracking code
  - Implement credential cleanup on app uninstall
  - Add security audit checklist verification
  - _Requirements: 9.2, 9.4, 9.5_

- [ ]* 23.1 Write property test for privacy
  - **Property 26: Privacy - no third-party data transmission**
  - **Validates: Requirements 9.2**

- [ ] 24. Implement error handling improvements
  - Add specific error detection for port forwarding disabled
  - Improve timeout error messages with suggestions
  - Add network connectivity error detection
  - Implement authentication failure categorization
  - _Requirements: 8.1, 8.2, 8.3, 8.4_

## Phase 10: Testing and Polish

- [ ] 25. Integration testing
  - Test SSH connection with real SSH server (test container)
  - Test VPN service with actual packet routing
  - Test profile repository with real database
  - Test end-to-end connection flow
  - _Requirements: All_

- [ ] 26. Manual testing and bug fixes
  - Test on multiple Android versions
  - Test network switching (WiFi ↔ mobile data)
  - Test battery optimization behavior
  - Test app exclusions with various apps
  - Verify DNS leak prevention
  - Test with different SSH servers
  - _Requirements: All_

- [ ] 27. Performance optimization
  - Optimize battery usage
  - Reduce memory footprint
  - Improve connection establishment time
  - Optimize reconnection speed
  - _Requirements: 7.3_

- [ ] 28. Documentation and release preparation
  - Write README with setup instructions
  - Document SSH server requirements
  - Create user guide for key generation
  - Add troubleshooting guide
  - Prepare for open source release
  - _Requirements: 9.5_

- [ ] 29. Final Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

