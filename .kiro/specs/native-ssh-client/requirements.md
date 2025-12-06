# Requirements Document

## Introduction

This document specifies the requirements for implementing a native OpenSSH client for the SSH Tunnel Proxy Android application. The current implementation using the sshj Java library experiences a 67% connection failure rate due to SSH channels closing prematurely with "Stream closed" errors. The native SSH implementation will bundle OpenSSH binaries with the application to provide the same reliability as the native `ssh -D` command on desktop systems.

## Glossary

- **Native SSH Client**: An implementation that uses the OpenSSH binary compiled for Android rather than a Java SSH library
- **SSH Binary**: The compiled OpenSSH executable file for Android architectures
- **Dynamic Port Forwarding**: SSH's `-D` option that creates a SOCKS5 proxy on a local port
- **jniLibs**: Android's directory structure for native libraries organized by CPU architecture
- **Process**: An operating system process created to run the SSH binary
- **Private Key File**: A file on disk containing the SSH private key required by the native SSH binary
- **APK Split**: Android's mechanism for creating separate APK files for different device architectures
- **Termux**: An Android terminal emulator that provides pre-compiled Linux binaries including OpenSSH
- **Architecture**: The CPU instruction set (ARM64, ARM32, x86_64, x86) that determines which binary to use

## Requirements

### Requirement 1

**User Story:** As a user, I want the SSH tunnel to establish connections reliably, so that I can consistently access the internet through my SSH server.

#### Acceptance Criteria

1. WHEN the system attempts to establish an SSH connection THEN the system SHALL use the native OpenSSH binary for the device architecture
2. WHEN the native SSH binary is not available for the device architecture THEN the system SHALL fall back to the existing sshj implementation
3. WHEN an SSH connection is established using the native binary THEN the system SHALL achieve greater than 95% connection success rate
4. WHEN the SSH connection is active THEN the system SHALL maintain the connection without premature channel closures
5. WHEN the system starts the SSH process THEN the system SHALL configure dynamic port forwarding on the specified local port

### Requirement 2

**User Story:** As a developer, I want to bundle OpenSSH binaries with the application, so that native SSH functionality is available on all supported devices.

#### Acceptance Criteria

1. WHEN the application is built THEN the system SHALL include OpenSSH binaries for ARM64, ARM32, x86_64, and x86 architectures
2. WHEN the application is built THEN the system SHALL include required OpenSSL libraries (libcrypto.so, libssl.so) for each architecture
3. WHEN the application is installed THEN the system SHALL organize native binaries in the jniLibs directory structure by architecture
4. WHEN the application starts THEN the system SHALL extract the SSH binary from the APK to the application's private directory
5. WHEN the SSH binary is extracted THEN the system SHALL set executable permissions on the binary file

### Requirement 3

**User Story:** As a user, I want the application to automatically select the correct SSH binary for my device, so that the SSH connection works without manual configuration.

#### Acceptance Criteria

1. WHEN the application determines which binary to use THEN the system SHALL detect the device's CPU architecture
2. WHEN the device architecture is ARM64 THEN the system SHALL use the arm64-v8a SSH binary
3. WHEN the device architecture is ARM32 THEN the system SHALL use the armeabi-v7a SSH binary
4. WHEN the device architecture is x86_64 THEN the system SHALL use the x86_64 SSH binary
5. WHEN the device architecture is x86 THEN the system SHALL use the x86 SSH binary
6. WHEN the device architecture is not recognized THEN the system SHALL default to the ARM64 binary

### Requirement 4

**User Story:** As a user, I want my SSH private key to be securely stored and used by the native SSH client, so that my credentials remain protected.

#### Acceptance Criteria

1. WHEN the system needs to use a private key THEN the system SHALL write the key data to a file in the application's private directory
2. WHEN the private key file is created THEN the system SHALL set file permissions to owner read/write only
3. WHEN the SSH connection is established THEN the system SHALL pass the private key file path to the SSH binary using the `-i` option
4. WHEN the SSH connection is terminated THEN the system SHALL delete the private key file from disk
5. WHEN the application is uninstalled THEN the system SHALL ensure all private key files are removed with the application data

### Requirement 5

**User Story:** As a user, I want the SSH tunnel to start with appropriate configuration options, so that the connection is stable and secure.

#### Acceptance Criteria

1. WHEN the system builds the SSH command THEN the system SHALL include the `-D` option with the local SOCKS5 port
2. WHEN the system builds the SSH command THEN the system SHALL include the `-N` option to prevent remote command execution
3. WHEN the system builds the SSH command THEN the system SHALL include the `-T` option to disable pseudo-terminal allocation
4. WHEN the system builds the SSH command THEN the system SHALL include ServerAliveInterval=60 to send keep-alive packets every 60 seconds
5. WHEN the system builds the SSH command THEN the system SHALL include ServerAliveCountMax=10 to allow 10 missed keep-alives before disconnecting
6. WHEN the system builds the SSH command THEN the system SHALL include ExitOnForwardFailure=yes to exit if port forwarding fails
7. WHEN the system builds the SSH command THEN the system SHALL include ConnectTimeout=30 to set a 30-second connection timeout

### Requirement 6

**User Story:** As a user, I want to monitor the SSH connection status, so that I know when the tunnel is active or if problems occur.

#### Acceptance Criteria

1. WHEN the SSH process starts THEN the system SHALL capture and log the process output
2. WHEN the SSH process outputs a message THEN the system SHALL parse the message for connection status and errors
3. WHEN the system checks connection status THEN the system SHALL verify the SSH process is alive
4. WHEN the system checks connection status THEN the system SHALL verify the SOCKS5 port is accepting connections
5. WHEN the SSH process terminates unexpectedly THEN the system SHALL detect the termination and update connection state

### Requirement 7

**User Story:** As a user, I want the SSH tunnel to stop cleanly when I disconnect, so that system resources are properly released.

#### Acceptance Criteria

1. WHEN the user requests disconnection THEN the system SHALL send a termination signal to the SSH process
2. WHEN the SSH process is terminated THEN the system SHALL wait up to 5 seconds for graceful shutdown
3. WHEN the SSH process does not terminate within 5 seconds THEN the system SHALL forcibly kill the process
4. WHEN the SSH process is stopped THEN the system SHALL delete the private key file
5. WHEN the SSH process is stopped THEN the system SHALL update the connection state to disconnected

### Requirement 8

**User Story:** As a user, I want the application to handle SSH connection failures gracefully, so that I receive clear error messages and the system attempts recovery.

#### Acceptance Criteria

1. WHEN the SSH binary extraction fails THEN the system SHALL log the error and fall back to the sshj implementation
2. WHEN the SSH process fails to start THEN the system SHALL return a failure result with an error message
3. WHEN the SSH connection is lost THEN the system SHALL detect the disconnection within 5 seconds
4. WHEN the SSH connection is lost THEN the system SHALL attempt automatic reconnection according to the reconnection policy
5. WHEN the SSH process crashes THEN the system SHALL clean up resources and notify the user

### Requirement 9

**User Story:** As a developer, I want to optimize the APK size impact of bundling native binaries, so that the application download size remains reasonable.

#### Acceptance Criteria

1. WHEN the application is built for production THEN the system SHALL support APK splits by architecture
2. WHEN APK splits are enabled THEN the system SHALL create separate APK files for each architecture
3. WHEN APK splits are enabled THEN the system SHALL create a universal APK containing all architectures
4. WHEN the application is distributed THEN the system SHALL allow users to download only the APK for their device architecture
5. WHEN the APK size is measured THEN the size increase from native binaries SHALL be less than 10 MB per architecture

### Requirement 10

**User Story:** As a user, I want the option to choose between native SSH and the Java library implementation, so that I can use whichever works best for my setup.

#### Acceptance Criteria

1. WHEN the application initializes the SSH client THEN the system SHALL check if native SSH is available
2. WHEN native SSH is available THEN the system SHALL prefer the native implementation by default
3. WHEN the user accesses settings THEN the system SHALL provide an option to choose SSH implementation type
4. WHEN the user selects an SSH implementation type THEN the system SHALL persist the preference
5. WHEN the system creates an SSH client THEN the system SHALL respect the user's implementation preference

### Requirement 11

**User Story:** As a developer, I want comprehensive tests for the native SSH implementation, so that I can verify reliability and catch regressions.

#### Acceptance Criteria

1. WHEN unit tests are executed THEN the system SHALL test SSH binary extraction for all architectures
2. WHEN unit tests are executed THEN the system SHALL test SSH command building with various configurations
3. WHEN unit tests are executed THEN the system SHALL test process lifecycle management
4. WHEN unit tests are executed THEN the system SHALL test private key file handling and cleanup
5. WHEN integration tests are executed THEN the system SHALL test SSH connection establishment with a real SSH server
6. WHEN integration tests are executed THEN the system SHALL test SOCKS5 proxy functionality through the native SSH tunnel
7. WHEN integration tests are executed THEN the system SHALL test reconnection behavior on connection loss

### Requirement 12

**User Story:** As a developer, I want to optimize binary extraction and caching, so that the application starts quickly and doesn't waste resources.

#### Acceptance Criteria

1. WHEN the SSH binary is extracted for the first time THEN the system SHALL cache the extracted binary in the application's private directory
2. WHEN the application starts and the binary is already extracted THEN the system SHALL reuse the cached binary without re-extracting
3. WHEN the application version is updated THEN the system SHALL re-extract the SSH binary to ensure the latest version is used
4. WHEN the extracted binary's checksum is verified THEN the system SHALL compare it against the expected checksum
5. WHEN the extracted binary is corrupted THEN the system SHALL re-extract the binary from the APK

### Requirement 13

**User Story:** As a developer, I want to source OpenSSH binaries from Termux, so that I can use trusted, pre-compiled binaries with proper licensing.

#### Acceptance Criteria

1. WHEN the project is built THEN the system SHALL use OpenSSH binaries from the Termux project
2. WHEN the binaries are included THEN the system SHALL document the source URL and version of the Termux OpenSSH package
3. WHEN the binaries are included THEN the system SHALL verify the BSD license compatibility
4. WHEN the binaries are included THEN the system SHALL include attribution to the Termux project in the application
5. WHEN security updates are available THEN the system SHALL provide a process for updating the bundled binaries

### Requirement 14

**User Story:** As a developer, I want to handle Android process management constraints, so that the SSH tunnel remains stable even when Android tries to optimize battery usage.

#### Acceptance Criteria

1. WHEN the SSH process is started THEN the system SHALL run it within the context of a foreground VPN service
2. WHEN Android attempts to kill background processes THEN the system SHALL detect the SSH process termination
3. WHEN the SSH process is killed by Android THEN the system SHALL restart the process automatically
4. WHEN the device enters Doze mode THEN the system SHALL maintain the SSH connection using foreground service exemptions
5. WHEN the SSH process is monitored THEN the system SHALL check process health at least once per second

### Requirement 15

**User Story:** As a user, I want the application to provide clear success criteria and metrics, so that I can verify the native SSH implementation is working correctly.

#### Acceptance Criteria

1. WHEN the SSH tunnel is established THEN the system SHALL achieve greater than 95% web browsing success rate
2. WHEN the SSH connection is active THEN the system SHALL report zero "Stream closed" errors
3. WHEN the SSH connection runs for extended periods THEN the system SHALL maintain stable connections for at least 24 hours
4. WHEN the APK size is measured THEN the total size increase SHALL be less than 10 MB
5. WHEN battery usage is measured THEN the battery consumption SHALL be comparable to or better than the sshj implementation
