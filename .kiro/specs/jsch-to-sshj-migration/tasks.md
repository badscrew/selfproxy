# Implementation Plan: JSch to sshj Migration

- [x] 1. Update project dependencies





  - Remove JSch dependency from `shared/build.gradle.kts`
  - Add sshj 0.38.0 dependency
  - Add BouncyCastle dependencies (bcprov-jdk18on, bcpkix-jdk18on)
  - Sync Gradle and verify build succeeds
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 2. Rewrite AndroidSSHClient.connect() method





  - Replace JSch connection logic with sshj SSHClient
  - Update private key loading to use sshj's KeyProvider
  - Implement host key verification using sshj's HostKeyVerifier
  - Update session configuration (compression, keep-alive, algorithms)
  - Update exception mapping from JSch to sshj exceptions
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 10.1, 10.2, 10.5_

- [x] 2.1 Write property test for connection establishment


  - **Property 1: Valid credentials establish connections**
  - **Validates: Requirements 2.1**

- [x] 2.2 Write property test for key type support

  - **Property 2: All key types are supported**
  - **Validates: Requirements 2.2**

- [x] 2.3 Write property test for session persistence

  - **Property 3: Sessions persist during connection**
  - **Validates: Requirements 2.3, 7.2**

- [x] 3. Rewrite AndroidSSHClient.createPortForwarding() method





  - Replace JSch setPortForwardingL() with sshj LocalPortForwarder
  - Implement SOCKS5 proxy creation using sshj's forward() method
  - Verify proxy binds to localhost (127.0.0.1) only
  - Ensure dynamic port assignment works correctly
  - Update error handling for port forwarding failures
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3_

- [x] 3.1 Write property test for SOCKS5 proxy creation


  - **Property 4: SOCKS5 proxy creation**
  - **Validates: Requirements 3.1, 3.2**

- [x] 3.2 Write property test for SOCKS5 connection acceptance


  - **Property 5: SOCKS5 connections are accepted**
  - **Validates: Requirements 3.3**

- [x] 3.3 Write property test for SOCKS5 handshake


  - **Property 6: SOCKS5 handshake compliance**
  - **Validates: Requirements 3.4, 4.1**

- [x] 3.4 Write property test for CONNECT requests


  - **Property 7: CONNECT requests succeed**
  - **Validates: Requirements 3.5, 4.2**

- [x] 3.5 Write property test for bidirectional data relay


  - **Property 8: Bidirectional data relay**
  - **Validates: Requirements 4.3**

- [x] 3.6 Write property test for concurrent connections


  - **Property 9: Concurrent connections**
  - **Validates: Requirements 4.5**

- [x] 4. Update AndroidSSHClient.sendKeepAlive() method




  - Replace JSch keep-alive with sshj keep-alive mechanism
  - Configure keep-alive interval (60 seconds)
  - Verify keep-alive packets are sent correctly
  - _Requirements: 7.1_

- [x] 4.1 Write property test for keep-alive packets


  - **Property 12: Keep-alive packets**
  - **Validates: Requirements 7.1**

- [ ] 5. Update AndroidSSHClient.disconnect() method
  - Replace JSch disconnect with sshj disconnect
  - Ensure SOCKS5 proxy is closed before SSH session
  - Verify all resources are released properly
  - _Requirements: 5.4, 7.4_

- [ ] 5.1 Write property test for clean disconnection
  - **Property 10: Clean disconnection**
  - **Validates: Requirements 5.4, 7.4**

- [ ] 6. Update exception mapping
  - Map sshj exceptions to ConnectionError types
  - Maintain user-friendly error messages
  - Ensure all error categories are covered
  - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [ ] 6.1 Write property test for exception mapping
  - **Property 11: Exception mapping**
  - **Validates: Requirements 6.1**

- [ ] 7. Update security configuration
  - Configure strong encryption algorithms
  - Verify key-only authentication (no passwords)
  - Implement host key verification
  - _Requirements: 10.1, 10.2, 10.5_

- [ ] 7.1 Write property test for strong encryption
  - **Property 13: Strong encryption**
  - **Validates: Requirements 10.1**

- [ ] 7.2 Write property test for key-only authentication
  - **Property 14: Key-only authentication**
  - **Validates: Requirements 10.2**

- [ ] 7.3 Write property test for host key verification
  - **Property 15: Host key verification**
  - **Validates: Requirements 10.5**

- [ ] 8. Checkpoint - Ensure all tests pass, ask the user if questions arise

- [ ] 9. Run SOCKS5 integration test
  - Use existing JSchSocksTestScreen to test SOCKS5 functionality
  - Verify Test 1: Connect to SOCKS5 port succeeds
  - Verify Test 2: SOCKS5 handshake succeeds
  - Verify Test 3: CONNECT request succeeds
  - Verify Test 4: HTTP request through tunnel succeeds
  - _Requirements: 8.1, 8.3_

- [ ] 10. Remove all JSch references
  - Search codebase for remaining JSch imports
  - Remove any JSch-specific code or comments
  - Verify no JSch dependencies remain
  - _Requirements: 1.2, 1.4_

- [ ] 11. Update documentation and naming
  - Rename JSchSocksTestScreen to SocksTestScreen
  - Update code comments to reference sshj instead of JSch
  - Update CURRENT_STATUS.md with migration completion
  - Update any relevant documentation files
  - _Requirements: 1.4_

- [ ] 12. Final checkpoint - Ensure all tests pass, ask the user if questions arise
