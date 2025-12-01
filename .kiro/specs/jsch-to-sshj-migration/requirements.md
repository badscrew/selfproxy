# Requirements Document: JSch to sshj Migration

## Introduction

This specification defines the requirements for migrating the SSH Tunnel Proxy application from JSch to sshj library. The current JSch implementation has a fundamentally broken SOCKS5 proxy that immediately resets connections during the SOCKS5 handshake, preventing any traffic from flowing through the tunnel. Testing has confirmed that JSch's `setPortForwardingL()` creates a listening socket but does not properly implement the SOCKS5 protocol.

The migration to sshj will provide a working SOCKS5 proxy implementation, enabling the VPN to actually route traffic through the SSH tunnel.

## Glossary

- **SSH Client**: The component responsible for establishing SSH connections to remote servers
- **SOCKS5 Proxy**: A protocol that routes network traffic through an SSH tunnel using dynamic port forwarding
- **JSch**: The current SSH library (version 0.1.55) with broken SOCKS5 implementation
- **sshj**: The target SSH library with working SOCKS5 support
- **AndroidSSHClient**: The Android-specific implementation of the SSH client interface
- **Dynamic Port Forwarding**: SSH feature that creates a local SOCKS5 proxy for routing traffic
- **VPN Service**: The Android VPN service that captures and routes device traffic
- **Packet Router**: Component that routes captured packets through the SOCKS5 proxy

## Requirements

### Requirement 1: Library Migration

**User Story:** As a developer, I want to replace JSch with sshj, so that the SSH library has a working SOCKS5 proxy implementation.

#### Acceptance Criteria

1. WHEN the sshj dependency is added to the project THEN the system SHALL include sshj version 0.38.0 or later in the shared module
2. WHEN the JSch dependency is removed from the project THEN the system SHALL have no remaining JSch imports or references
3. WHEN the project builds THEN the system SHALL compile successfully without JSch dependencies
4. WHEN the migration is complete THEN the system SHALL use only sshj for all SSH operations

### Requirement 2: SSH Connection Establishment

**User Story:** As a user, I want to connect to my SSH server using sshj, so that I can establish an SSH tunnel.

#### Acceptance Criteria

1. WHEN a user provides valid SSH credentials THEN the system SHALL establish an SSH connection using sshj
2. WHEN connecting to an SSH server THEN the system SHALL support RSA, ECDSA, and Ed25519 key types
3. WHEN authentication succeeds THEN the system SHALL maintain the SSH session for the duration of the connection
4. WHEN the SSH server is unreachable THEN the system SHALL return a HostUnreachable error
5. WHEN authentication fails THEN the system SHALL return an AuthenticationFailed error with details

### Requirement 3: SOCKS5 Proxy Creation

**User Story:** As a user, I want sshj to create a working SOCKS5 proxy, so that my device traffic can be routed through the SSH tunnel.

#### Acceptance Criteria

1. WHEN an SSH connection is established THEN the system SHALL create a local SOCKS5 proxy using sshj's dynamic port forwarding
2. WHEN the SOCKS5 proxy is created THEN the system SHALL bind to localhost (127.0.0.1) on a dynamically assigned port
3. WHEN a client connects to the SOCKS5 proxy THEN the system SHALL accept the connection without resetting it
4. WHEN a client sends a SOCKS5 greeting THEN the system SHALL respond with a valid SOCKS5 response
5. WHEN a client sends a CONNECT request THEN the system SHALL establish the connection through the SSH tunnel

### Requirement 4: SOCKS5 Protocol Compliance

**User Story:** As a developer, I want the SOCKS5 proxy to properly implement the SOCKS5 protocol, so that TCP connections can be established through the tunnel.

#### Acceptance Criteria

1. WHEN a SOCKS5 handshake is initiated THEN the system SHALL respond with version 5 and method 0 (no authentication)
2. WHEN a CONNECT request is received THEN the system SHALL establish a connection to the target host through the SSH tunnel
3. WHEN a connection is established THEN the system SHALL relay data bidirectionally between client and target
4. WHEN a connection fails THEN the system SHALL return appropriate SOCKS5 error codes
5. WHEN multiple simultaneous connections are made THEN the system SHALL handle all connections without resetting

### Requirement 5: Backward Compatibility

**User Story:** As a user, I want my existing SSH profiles to continue working, so that I don't need to reconfigure my connections.

#### Acceptance Criteria

1. WHEN the migration is complete THEN the system SHALL connect to the same SSH servers as before
2. WHEN using existing SSH keys THEN the system SHALL authenticate successfully with sshj
3. WHEN the VPN connects THEN the system SHALL show the same connection status and information
4. WHEN disconnecting THEN the system SHALL cleanly close the SSH session and SOCKS5 proxy
5. WHEN the app is restarted THEN the system SHALL restore saved profiles without data loss

### Requirement 6: Error Handling

**User Story:** As a user, I want clear error messages when connections fail, so that I can troubleshoot issues.

#### Acceptance Criteria

1. WHEN an SSH connection fails THEN the system SHALL map sshj exceptions to appropriate ConnectionError types
2. WHEN authentication fails THEN the system SHALL provide specific error messages about the failure reason
3. WHEN the network is unavailable THEN the system SHALL return a NetworkUnavailable error
4. WHEN port forwarding fails THEN the system SHALL return a PortForwardingDisabled error with guidance
5. WHEN an unknown error occurs THEN the system SHALL log the error details for debugging

### Requirement 7: Connection Lifecycle

**User Story:** As a user, I want the SSH connection to remain stable, so that my VPN stays connected.

#### Acceptance Criteria

1. WHEN the SSH connection is established THEN the system SHALL send keep-alive packets every 60 seconds
2. WHEN the connection is idle THEN the system SHALL maintain the session without timing out
3. WHEN the network changes THEN the system SHALL detect the disconnection and update the connection state
4. WHEN disconnecting THEN the system SHALL close the SOCKS5 proxy before closing the SSH session
5. WHEN the app is closed THEN the system SHALL cleanly disconnect all SSH sessions

### Requirement 8: Testing and Validation

**User Story:** As a developer, I want to verify that sshj's SOCKS5 proxy works correctly, so that I can confirm the migration was successful.

#### Acceptance Criteria

1. WHEN the SOCKS5 test is run THEN the system SHALL successfully complete the SOCKS5 handshake
2. WHEN a CONNECT request is sent THEN the system SHALL establish a connection to the target host
3. WHEN HTTP traffic is sent through the proxy THEN the system SHALL receive valid HTTP responses
4. WHEN the VPN is connected THEN the system SHALL successfully route web browsing traffic
5. WHEN multiple connections are made THEN the system SHALL handle all connections without errors

### Requirement 9: Performance

**User Story:** As a user, I want the SSH connection to be fast and efficient, so that my VPN doesn't slow down my internet.

#### Acceptance Criteria

1. WHEN establishing an SSH connection THEN the system SHALL connect within 10 seconds on a normal network
2. WHEN routing traffic through SOCKS5 THEN the system SHALL add minimal latency overhead
3. WHEN handling multiple connections THEN the system SHALL not degrade performance significantly
4. WHEN transferring data THEN the system SHALL utilize available bandwidth efficiently
5. WHEN the connection is idle THEN the system SHALL use minimal CPU and battery resources

### Requirement 10: Security

**User Story:** As a user, I want my SSH connections to remain secure, so that my data is protected.

#### Acceptance Criteria

1. WHEN connecting to an SSH server THEN the system SHALL use strong encryption algorithms
2. WHEN authenticating THEN the system SHALL use only private key authentication (no passwords)
3. WHEN storing SSH keys THEN the system SHALL continue using Android Keystore encryption
4. WHEN the SOCKS5 proxy is created THEN the system SHALL bind only to localhost to prevent external access
5. WHEN host key verification is enabled THEN the system SHALL verify the server's host key

## Key Decisions Made

1. **Target Library**: sshj version 0.38.0 or later chosen for its mature SOCKS5 implementation and active maintenance
2. **Migration Strategy**: Complete replacement of JSch rather than gradual migration to avoid maintaining two SSH implementations
3. **API Compatibility**: Maintain the existing SSHClient interface to minimize changes to dependent code
4. **Testing Approach**: Use the existing SOCKS5 test screen to validate the migration before full deployment

## Success Criteria

The migration will be considered successful when:
1. All JSch code is removed and replaced with sshj
2. The SOCKS5 test passes all 4 tests (connect, handshake, CONNECT request, HTTP traffic)
3. Web browsing works through the VPN
4. Existing SSH profiles continue to work without modification
5. No regressions in connection stability or error handling
