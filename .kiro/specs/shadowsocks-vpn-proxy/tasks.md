# Implementation Plan

- [x] 1. Create server setup documentation





  - Write comprehensive guide for installing Shadowsocks on Ubuntu Linux
  - Include automated installation script with secure defaults
  - Document cipher selection and password generation
  - Provide troubleshooting tips and firewall configuration
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 2. Set up project dependencies and remove SSH code





  - Remove all SSH-related dependencies (JSch, native SSH binaries)
  - Add Shadowsocks library dependency (shadowsocks-android or shadowsocks-libev)
  - Remove SSH-specific code files and tests
  - Update Gradle build files with new dependencies
  - Clean up unused imports and references
  - _Requirements: 11.1, 11.2_

- [x] 3. Define shared data models and enums





  - Create CipherMethod enum with supported ciphers
  - Create ServerProfile data class with Shadowsocks fields
  - Create ShadowsocksConfig data class
  - Create ConnectionState sealed class hierarchy
  - Create VpnStatistics data class
  - Add @Serializable annotations for all data classes
  - _Requirements: 1.1, 9.1, 9.2, 9.3_

- [x] 3.1 Write property test for ServerProfile serialization


  - **Property 1: Profile CRUD operations maintain data integrity**
  - **Validates: Requirements 1.1, 1.2, 1.3**

- [x] 4. Update database schema for Shadowsocks




  - Modify server_profiles table to include Shadowsocks fields (server_host, server_port, cipher)
  - Remove SSH-specific fields (username, key_type, etc.)
  - Create migration script from old schema to new schema
  - Update SQLDelight queries for new schema
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 5. Implement ProfileRepository for Shadowsocks profiles





  - Update ProfileRepository interface for Shadowsocks fields
  - Implement createProfile with validation
  - Implement getProfile, getAllProfiles, updateProfile, deleteProfile
  - Add profile validation (hostname, port range, cipher)
  - Update last_used timestamp on profile access
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 5.1 Write property test for profile repository


  - **Property 1: Profile CRUD operations maintain data integrity**
  - **Validates: Requirements 1.1, 1.2, 1.3**

- [x] 5.2 Write property test for profile deletion


  - **Property 3: Profile deletion removes all associated data**
  - **Validates: Requirements 1.4, 2.3**

- [x] 6. Implement CredentialStore for password encryption




  - Create CredentialStore interface
  - Implement Android Keystore encryption for passwords
  - Implement storePassword with hardware-backed encryption
  - Implement retrievePassword with decryption
  - Implement deletePassword with secure erasure
  - Add password sanitization for logging
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 6.1 Write property test for password encryption


  - **Property 2: Password encryption round-trip preserves data**
  - **Validates: Requirements 2.1, 2.2**

- [ ] 7. Implement ShadowsocksClient interface and Android implementation
  - Create ShadowsocksClient interface in shared module
  - Implement AndroidShadowsocksClient using shadowsocks library
  - Implement start() method to launch local SOCKS5 proxy
  - Implement stop() method to terminate proxy
  - Implement testConnection() for server validation
  - Implement observeState() for connection state flow
  - Add support for all required ciphers (aes-256-gcm, chacha20-ietf-poly1305, aes-128-gcm)
  - _Requirements: 3.1, 3.2, 8.1, 8.2, 9.1, 9.2, 9.3, 9.4_

- [ ] 7.1 Write property test for cipher validation
  - **Property 8: Cipher method validation rejects unsupported ciphers**
  - **Validates: Requirements 9.1, 9.2, 9.3, 9.5**

- [ ] 7.2 Write unit tests for ShadowsocksClient
  - Test start/stop lifecycle
  - Test connection state transitions
  - Test error handling for invalid configurations
  - _Requirements: 3.1, 3.2, 3.5_

- [ ] 8. Implement PacketRouter for TUN to SOCKS5 routing
  - Create PacketRouter class for packet handling
  - Implement packet reading from TUN interface
  - Implement packet parsing (IP, TCP, UDP headers)
  - Implement SOCKS5 connection establishment
  - Implement packet forwarding through SOCKS5
  - Implement response packet routing back to TUN
  - Add statistics tracking (bytes sent/received, speeds)
  - _Requirements: 4.1, 4.2, 4.4, 7.2, 7.3_

- [ ] 8.1 Write unit tests for packet parsing
  - Test IP packet parsing
  - Test TCP header extraction
  - Test UDP header extraction
  - _Requirements: 4.1, 4.2_

- [ ] 9. Implement VPN Service for Shadowsocks
  - Update TunnelVpnService to work with Shadowsocks
  - Implement TUN interface creation with proper routing
  - Implement DNS server configuration to prevent leaks
  - Integrate PacketRouter for traffic handling
  - Implement app exclusion filtering
  - Add foreground notification for VPN status
  - _Requirements: 3.3, 3.4, 4.1, 4.2, 4.3, 4.5, 5.2, 5.5_

- [ ] 9.1 Write property test for traffic routing
  - **Property 9: VPN tunnel routes all traffic when active**
  - **Validates: Requirements 4.1, 4.2, 4.3**

- [ ] 9.2 Write property test for app exclusion
  - **Property 10: Excluded apps bypass VPN tunnel**
  - **Validates: Requirements 5.2**

- [ ] 10. Implement AppRoutingRepository
  - Create AppRoutingRepository interface in shared module
  - Implement getExcludedApps() using database
  - Implement setExcludedApps() with persistence
  - Implement isAppExcluded() for quick lookup
  - Add validation for package names
  - _Requirements: 5.3, 5.4, 5.5_

- [ ] 10.1 Write property test for app routing persistence
  - **Property 5: App routing configuration persists correctly**
  - **Validates: Requirements 5.3, 5.4, 5.5**

- [ ] 11. Implement ConnectionManager
  - Create ConnectionManager interface in shared module
  - Implement connect() to coordinate Shadowsocks and VPN
  - Implement disconnect() to cleanly shut down connection
  - Implement testConnection() for pre-connection validation
  - Implement connection state machine with proper transitions
  - Implement observeConnectionState() flow
  - Implement observeStatistics() flow
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 7.1, 7.2, 7.3, 7.4, 8.1, 8.2, 8.3, 8.4_

- [ ] 11.1 Write property test for connection state machine
  - **Property 4: Connection state transitions are valid**
  - **Validates: Requirements 3.1, 3.4, 3.5**

- [ ] 11.2 Write unit tests for ConnectionManager
  - Test connect flow
  - Test disconnect flow
  - Test error handling
  - Test state transitions
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 12. Implement automatic reconnection logic
  - Add reconnection state to ConnectionManager
  - Implement exponential backoff algorithm (1s, 2s, 4s, 8s, 16s, 32s, 60s max)
  - Implement network change detection
  - Implement automatic reconnection on network change
  - Add user notification after 5 failed attempts
  - Add reconnection attempt counter
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 12.1 Write property test for backoff calculation
  - **Property 7: Reconnection backoff increases exponentially**
  - **Validates: Requirements 6.2**

- [ ] 12.2 Write unit tests for reconnection logic
  - Test backoff timing
  - Test network change handling
  - Test max retry behavior
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 13. Implement statistics tracking
  - Add statistics collection in PacketRouter
  - Implement byte counters (sent/received)
  - Implement speed calculation (upload/download)
  - Implement connection duration tracking
  - Add real-time statistics updates via Flow
  - _Requirements: 7.2, 7.3, 7.4, 7.5_

- [ ] 13.1 Write property test for statistics monotonicity
  - **Property 6: Statistics accumulation is monotonic**
  - **Validates: Requirements 7.2, 7.3**

- [ ] 14. Update UI for Shadowsocks profiles
  - Update ProfileFormDialog for Shadowsocks fields (host, port, password, cipher)
  - Remove SSH-specific UI fields (username, key type, key file)
  - Add cipher selection dropdown
  - Add password field with visibility toggle
  - Add port validation (1024-65535)
  - Update ProfilesScreen to display Shadowsocks info
  - _Requirements: 1.1, 1.2, 1.5, 9.1, 9.2, 9.3_

- [ ] 15. Update ConnectionScreen for Shadowsocks
  - Update connection status display
  - Update statistics display (bytes, speed, duration)
  - Add real-time statistics updates
  - Update error message display
  - Remove SSH-specific status information
  - _Requirements: 3.4, 3.5, 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 16. Implement connection testing UI
  - Add "Test Connection" button to profile form
  - Implement test connection flow in ViewModel
  - Display test results (success/failure, latency)
  - Show specific error messages for failures
  - Add loading indicator during test
  - Add timeout handling (10 seconds)
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [ ] 17. Implement app routing UI
  - Create app selection screen
  - Display list of installed apps with icons
  - Add checkbox for each app to exclude from VPN
  - Implement search/filter for apps
  - Add "Select All" / "Deselect All" options
  - Persist selections via AppRoutingRepository
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 18. Update ViewModels for Shadowsocks
  - Update ProfilesViewModel for Shadowsocks profiles
  - Update ConnectionViewModel for Shadowsocks connection
  - Add connection testing logic to ViewModel
  - Add statistics observation in ViewModel
  - Add error handling and user feedback
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 3.1, 3.2, 3.3, 7.1, 7.2, 7.3, 8.1_

- [ ] 19. Update dependency injection
  - Remove SSH-related DI modules
  - Add Shadowsocks client to DI
  - Add ConnectionManager to DI
  - Add CredentialStore to DI
  - Add AppRoutingRepository to DI
  - Update ViewModels with new dependencies
  - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

- [ ] 20. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 21. Write integration tests
  - Test end-to-end connection flow
  - Test profile creation to connection
  - Test network change handling
  - Test app exclusion functionality
  - Test reconnection behavior
  - _Requirements: 3.1, 3.2, 3.3, 4.1, 5.2, 6.1_

- [ ] 22. Update documentation
  - Update README with Shadowsocks information
  - Update PRFAQ for Shadowsocks
  - Add migration guide from SSH to Shadowsocks
  - Update screenshots
  - Document supported ciphers
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [ ] 23. Final testing and cleanup
  - Test on physical Android device
  - Verify DNS leak prevention
  - Test battery usage
  - Test with various Shadowsocks servers
  - Remove all SSH-related code and files
  - Clean up unused dependencies
  - _Requirements: 4.5, all requirements_

- [ ] 24. Final Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
