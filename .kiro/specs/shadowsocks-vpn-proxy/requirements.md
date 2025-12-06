# Requirements Document

## Introduction

This document specifies the requirements for an Android VPN application that routes device traffic through a Shadowsocks proxy server. The application provides a privacy-focused solution for users who want to tunnel their internet traffic through their own Shadowsocks server, offering an alternative to the previous SSH-based approach.

## Glossary

- **Shadowsocks**: A secure SOCKS5 proxy protocol designed to protect internet traffic
- **VPN Service**: Android's VpnService API that allows apps to create virtual network interfaces
- **TUN Interface**: Virtual network interface that captures IP packets from the device
- **SOCKS5**: Socket Secure version 5, a protocol for routing network packets through a proxy server
- **Cipher**: Encryption algorithm used by Shadowsocks (e.g., aes-256-gcm, chacha20-ietf-poly1305)
- **Server Profile**: User-configured connection settings including server address, port, password, and cipher
- **App Routing**: Feature allowing users to select which apps use the VPN tunnel
- **Connection Manager**: Component responsible for establishing and maintaining Shadowsocks connections

## Requirements

### Requirement 1

**User Story:** As a user, I want to configure Shadowsocks server profiles, so that I can connect to my own Shadowsocks server.

#### Acceptance Criteria

1. WHEN a user creates a new profile THEN the system SHALL accept server hostname, port, password, and encryption method
2. WHEN a user saves a profile THEN the system SHALL validate that all required fields are populated
3. WHEN a user edits an existing profile THEN the system SHALL update the stored configuration
4. WHEN a user deletes a profile THEN the system SHALL remove all associated data including encrypted credentials
5. WHEN displaying profiles THEN the system SHALL show profile name, server address, and last connection time

### Requirement 2

**User Story:** As a user, I want my Shadowsocks credentials stored securely, so that my server password is protected.

#### Acceptance Criteria

1. WHEN a user saves a server password THEN the system SHALL encrypt it using Android Keystore
2. WHEN the system stores encrypted credentials THEN the system SHALL use hardware-backed encryption when available
3. WHEN a user deletes a profile THEN the system SHALL securely erase the associated credentials from storage
4. WHEN the application logs events THEN the system SHALL NOT include passwords or encryption keys in log output
5. WHEN credentials are loaded from storage THEN the system SHALL decrypt them only in memory and clear them after use

### Requirement 3

**User Story:** As a user, I want to connect to my Shadowsocks server with one tap, so that I can quickly establish a secure tunnel.

#### Acceptance Criteria

1. WHEN a user taps connect on a profile THEN the system SHALL establish a Shadowsocks connection to the specified server
2. WHEN establishing a connection THEN the system SHALL use the configured cipher method for encryption
3. WHEN a connection is established THEN the system SHALL create a VPN tunnel routing all traffic through Shadowsocks
4. WHEN a connection succeeds THEN the system SHALL display a VPN key icon in the status bar
5. WHEN a connection fails THEN the system SHALL display a clear error message indicating the failure reason

### Requirement 4

**User Story:** As a user, I want all my device traffic routed through the Shadowsocks tunnel, so that my internet activity is protected.

#### Acceptance Criteria

1. WHEN the VPN is active THEN the system SHALL route all TCP traffic through the Shadowsocks proxy
2. WHEN the VPN is active THEN the system SHALL route all UDP traffic through the Shadowsocks proxy
3. WHEN the VPN is active THEN the system SHALL route DNS queries through the Shadowsocks tunnel
4. WHEN routing traffic THEN the system SHALL maintain the original source and destination addresses
5. WHEN the tunnel is established THEN the system SHALL prevent DNS leaks by blocking direct DNS queries

### Requirement 5

**User Story:** As a user, I want to select which apps use the VPN, so that I can exclude specific applications from the tunnel.

#### Acceptance Criteria

1. WHEN a user views app routing settings THEN the system SHALL display a list of installed applications
2. WHEN a user excludes an app THEN the system SHALL route that app's traffic directly without using the VPN
3. WHEN a user includes an app THEN the system SHALL route that app's traffic through the Shadowsocks tunnel
4. WHEN app routing is configured THEN the system SHALL persist the configuration across app restarts
5. WHEN the VPN starts THEN the system SHALL apply the saved app routing configuration

### Requirement 6

**User Story:** As a user, I want the connection to automatically reconnect if it drops, so that my protection is maintained.

#### Acceptance Criteria

1. WHEN a connection is lost THEN the system SHALL attempt to reconnect automatically
2. WHEN reconnecting THEN the system SHALL use exponential backoff starting at 1 second up to 60 seconds
3. WHEN reconnection attempts fail repeatedly THEN the system SHALL notify the user after 5 failed attempts
4. WHEN the network changes (WiFi to mobile data) THEN the system SHALL re-establish the connection
5. WHEN reconnection succeeds THEN the system SHALL restore the VPN tunnel without user intervention

### Requirement 7

**User Story:** As a user, I want to see connection status and statistics, so that I can monitor my VPN usage.

#### Acceptance Criteria

1. WHEN the VPN is active THEN the system SHALL display connection status (connected, connecting, disconnected)
2. WHEN traffic flows through the tunnel THEN the system SHALL track bytes sent and received
3. WHEN displaying statistics THEN the system SHALL show upload speed, download speed, and total data transferred
4. WHEN the connection duration changes THEN the system SHALL display the elapsed time since connection
5. WHEN the user views the connection screen THEN the system SHALL update statistics in real-time

### Requirement 8

**User Story:** As a user, I want to test my Shadowsocks server configuration, so that I can verify it works before connecting.

#### Acceptance Criteria

1. WHEN a user initiates a connection test THEN the system SHALL attempt to connect to the Shadowsocks server
2. WHEN testing a connection THEN the system SHALL verify the server is reachable and credentials are valid
3. WHEN a test succeeds THEN the system SHALL display a success message with connection latency
4. WHEN a test fails THEN the system SHALL display a specific error message (unreachable, authentication failed, etc.)
5. WHEN testing THEN the system SHALL complete the test within 10 seconds or report a timeout

### Requirement 9

**User Story:** As a user, I want support for modern Shadowsocks encryption methods, so that my connection is secure.

#### Acceptance Criteria

1. WHEN selecting an encryption method THEN the system SHALL support aes-256-gcm cipher
2. WHEN selecting an encryption method THEN the system SHALL support chacha20-ietf-poly1305 cipher
3. WHEN selecting an encryption method THEN the system SHALL support aes-128-gcm cipher
4. WHEN connecting with a cipher THEN the system SHALL use the AEAD (Authenticated Encryption with Associated Data) mode
5. WHEN an unsupported cipher is configured THEN the system SHALL display an error and prevent connection

### Requirement 10

**User Story:** As a user, I want clear documentation on setting up a Shadowsocks server, so that I can deploy my own server easily.

#### Acceptance Criteria

1. WHEN a user reads the server setup guide THEN the system SHALL provide step-by-step instructions for Ubuntu Linux
2. WHEN following the setup guide THEN the system SHALL include a complete installation script
3. WHEN the installation script runs THEN the system SHALL install shadowsocks-libev on Ubuntu
4. WHEN the installation script completes THEN the system SHALL configure the server with secure default settings
5. WHEN the guide is provided THEN the system SHALL include instructions for generating secure passwords and selecting ciphers

### Requirement 11

**User Story:** As a developer, I want the codebase organized with clear separation of concerns, so that the system is maintainable.

#### Acceptance Criteria

1. WHEN implementing Shadowsocks client THEN the system SHALL separate protocol logic from Android platform code
2. WHEN implementing VPN service THEN the system SHALL separate packet routing from Shadowsocks connection management
3. WHEN implementing data storage THEN the system SHALL use repository pattern for data access
4. WHEN implementing UI THEN the system SHALL use MVVM architecture with ViewModels and Compose
5. WHEN implementing shared logic THEN the system SHALL place it in the Kotlin Multiplatform shared module
