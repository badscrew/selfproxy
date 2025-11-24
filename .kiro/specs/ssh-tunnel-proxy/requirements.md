# Requirements Document

## Introduction

SSH Tunnel Proxy is an Android application that enables users to route their mobile device's internet traffic through their own SSH servers using SOCKS5 proxy tunneling. Unlike commercial VPN services, this application gives users complete control over their privacy by allowing them to use their own infrastructure (cloud servers, home servers, or workplace servers) as secure tunnels. The application supports multiple authentication methods, profile management, per-app routing, connection monitoring, and automatic reconnection capabilities.

**Note:** This initial version focuses on SOCKS5 proxy implementation. HTTP proxy support may be considered for future releases to provide additional compatibility options.

## Glossary

- **SSH_Tunnel_Proxy_App**: The Android application system that manages SSH connections and traffic routing
- **SSH_Server**: A remote server that the user controls or has access to, which accepts SSH connections
- **SOCKS5_Proxy**: A local proxy server created by the application that routes traffic through the SSH tunnel
- **Server_Profile**: A saved configuration containing SSH server connection details and credentials
- **VPN_Service**: Android's VPN API used to route device traffic through the SOCKS5 proxy
- **Connection_Manager**: The component responsible for establishing and maintaining SSH tunnel connections
- **Credential_Store**: Encrypted storage for SSH authentication credentials on the device
- **Traffic_Monitor**: Component that tracks bandwidth usage and connection statistics
- **Auto_Reconnect_Service**: Background service that detects connection drops and attempts reconnection

## Requirements

### Requirement 1

**User Story:** As a privacy-conscious user, I want to establish an SSH tunnel to my own server, so that I can route my mobile traffic through infrastructure I control.

#### Acceptance Criteria

1. WHEN a user provides valid SSH server credentials (hostname, port, username, and authentication method), THE SSH_Tunnel_Proxy_App SHALL establish an SSH connection to the SSH_Server
2. WHEN the SSH connection is established, THE SSH_Tunnel_Proxy_App SHALL create a local SOCKS5_Proxy that routes traffic through the tunnel
3. WHEN the SOCKS5_Proxy is active, THE SSH_Tunnel_Proxy_App SHALL route device traffic through the SSH_Server
4. WHEN a user disconnects the tunnel, THE SSH_Tunnel_Proxy_App SHALL terminate the SSH connection and stop the SOCKS5_Proxy
5. WHEN connection establishment fails, THE SSH_Tunnel_Proxy_App SHALL display a specific error message indicating the failure reason

### Requirement 2

**User Story:** As a user with multiple SSH servers, I want to save and manage multiple server profiles, so that I can quickly switch between different servers.

#### Acceptance Criteria

1. WHEN a user creates a new Server_Profile with valid connection details, THE SSH_Tunnel_Proxy_App SHALL save the profile to persistent storage
2. WHEN a user views their saved profiles, THE SSH_Tunnel_Proxy_App SHALL display all Server_Profile entries with their names and server addresses
3. WHEN a user selects a Server_Profile, THE SSH_Tunnel_Proxy_App SHALL load the profile's connection details and establish a connection
4. WHEN a user deletes a Server_Profile, THE SSH_Tunnel_Proxy_App SHALL remove the profile from persistent storage
5. WHEN a user edits a Server_Profile, THE SSH_Tunnel_Proxy_App SHALL update the stored profile with the new details

### Requirement 3

**User Story:** As a security-conscious user, I want to authenticate using SSH keys, so that I can maintain secure access without using passwords.

#### Acceptance Criteria

1. WHEN a user selects private key authentication, THE SSH_Tunnel_Proxy_App SHALL support RSA, ECDSA, and Ed25519 key formats
2. WHEN a user provides a private key file, THE SSH_Tunnel_Proxy_App SHALL parse and validate the key format
3. WHEN a user provides a passphrase-protected private key, THE SSH_Tunnel_Proxy_App SHALL prompt for the passphrase and decrypt the key
4. WHEN a user stores SSH credentials, THE SSH_Tunnel_Proxy_App SHALL encrypt the credentials in the Credential_Store
5. WHEN the SSH_Tunnel_Proxy_App retrieves stored credentials, THE SSH_Tunnel_Proxy_App SHALL decrypt them for connection establishment

### Requirement 4

**User Story:** As a mobile user, I want the tunnel to automatically reconnect when my connection drops, so that my privacy protection remains continuous.

#### Acceptance Criteria

1. WHEN the SSH connection drops unexpectedly, THE Auto_Reconnect_Service SHALL detect the disconnection within 10 seconds
2. WHEN a disconnection is detected, THE Auto_Reconnect_Service SHALL attempt to re-establish the SSH connection
3. WHEN reconnection attempts fail, THE Auto_Reconnect_Service SHALL retry with exponential backoff up to a maximum interval of 60 seconds
4. WHEN the device switches between WiFi and mobile data, THE Auto_Reconnect_Service SHALL re-establish the SSH tunnel on the new network
5. WHEN reconnection succeeds, THE SSH_Tunnel_Proxy_App SHALL restore the SOCKS5_Proxy and resume traffic routing

### Requirement 5

**User Story:** As a user who wants selective tunneling, I want to choose which apps use the tunnel, so that I can optimize performance and functionality.

#### Acceptance Criteria

1. WHEN the SSH_Tunnel_Proxy_App operates in VPN mode, THE SSH_Tunnel_Proxy_App SHALL provide an interface to select apps for tunnel exclusion
2. WHEN a user excludes an app from the tunnel, THE VPN_Service SHALL route that app's traffic directly without using the SOCKS5_Proxy
3. WHEN a user includes an app in the tunnel, THE VPN_Service SHALL route that app's traffic through the SOCKS5_Proxy
4. WHEN the user modifies app routing settings, THE SSH_Tunnel_Proxy_App SHALL apply the changes without requiring a full reconnection
5. WHEN the device has root access, THE SSH_Tunnel_Proxy_App SHALL enable per-app proxying without using the VPN_Service

### Requirement 6

**User Story:** As a user monitoring my usage, I want to see real-time connection statistics, so that I can track bandwidth consumption and connection health.

#### Acceptance Criteria

1. WHEN the SSH tunnel is active, THE Traffic_Monitor SHALL track bytes sent and received through the tunnel
2. WHEN a user views connection statistics, THE SSH_Tunnel_Proxy_App SHALL display total data transferred, current upload speed, and current download speed
3. WHEN the SSH tunnel is active, THE SSH_Tunnel_Proxy_App SHALL display connection duration and current connection status
4. WHEN the connection status changes, THE SSH_Tunnel_Proxy_App SHALL update the displayed status within 2 seconds
5. WHEN a user requests statistics reset, THE Traffic_Monitor SHALL clear accumulated bandwidth data while maintaining the active connection

### Requirement 7

**User Story:** As a user concerned about battery life, I want the app to minimize power consumption, so that I can maintain tunnel protection throughout the day.

#### Acceptance Criteria

1. WHEN the SSH tunnel is idle with no traffic, THE Connection_Manager SHALL send keep-alive packets at configurable intervals to maintain the connection
2. WHEN the device enters doze mode, THE SSH_Tunnel_Proxy_App SHALL request battery optimization exemption to maintain the tunnel
3. WHEN the tunnel is active, THE SSH_Tunnel_Proxy_App SHALL use efficient polling intervals to balance responsiveness and battery consumption
4. WHEN the user enables battery saver mode, THE SSH_Tunnel_Proxy_App SHALL adjust keep-alive intervals to reduce power usage
5. WHEN the device battery level is critically low, THE SSH_Tunnel_Proxy_App SHALL notify the user and offer to disconnect the tunnel

### Requirement 8

**User Story:** As a user experiencing connection issues, I want detailed error messages and diagnostics, so that I can troubleshoot problems effectively.

#### Acceptance Criteria

1. WHEN authentication fails, THE SSH_Tunnel_Proxy_App SHALL display a message indicating whether the failure was due to invalid credentials, key format issues, or server rejection
2. WHEN the SSH_Server is unreachable, THE SSH_Tunnel_Proxy_App SHALL display a message indicating network connectivity issues or incorrect server address
3. WHEN port forwarding is disabled on the SSH_Server, THE SSH_Tunnel_Proxy_App SHALL detect the limitation and inform the user
4. WHEN connection attempts timeout, THE SSH_Tunnel_Proxy_App SHALL display the timeout duration and suggest checking firewall settings
5. WHEN the SSH_Tunnel_Proxy_App encounters an error, THE SSH_Tunnel_Proxy_App SHALL log diagnostic information that can be exported for troubleshooting

### Requirement 9

**User Story:** As a user who values privacy, I want assurance that my credentials and traffic are not collected, so that I can trust the application.

#### Acceptance Criteria

1. WHEN the SSH_Tunnel_Proxy_App stores credentials, THE Credential_Store SHALL encrypt them using Android Keystore with hardware-backed encryption where available
2. WHEN the SSH_Tunnel_Proxy_App operates, THE SSH_Tunnel_Proxy_App SHALL not transmit any user data to third-party servers
3. WHEN the SSH_Tunnel_Proxy_App logs diagnostic information, THE SSH_Tunnel_Proxy_App SHALL exclude sensitive data such as passwords, private keys, and traffic content
4. WHEN a user uninstalls the application, THE SSH_Tunnel_Proxy_App SHALL ensure all stored credentials are removed from the device
5. WHEN the application source code is reviewed, THE SSH_Tunnel_Proxy_App SHALL demonstrate that no analytics, tracking, or data collection mechanisms are present

### Requirement 10

**User Story:** As a user configuring the application, I want to customize connection behavior, so that I can optimize the tunnel for my specific needs.

#### Acceptance Criteria

1. WHEN a user accesses settings, THE SSH_Tunnel_Proxy_App SHALL provide options to configure SSH port, connection timeout, and keep-alive interval
2. WHEN a user enables compression, THE Connection_Manager SHALL negotiate SSH compression with the SSH_Server
3. WHEN a user configures DNS settings, THE VPN_Service SHALL route DNS queries through the tunnel or use custom DNS servers as specified
4. WHEN a user sets a custom SOCKS5 port, THE SSH_Tunnel_Proxy_App SHALL create the SOCKS5_Proxy on the specified local port
5. WHEN a user enables strict host key checking, THE Connection_Manager SHALL verify the SSH_Server host key against stored known hosts

### Requirement 11

**User Story:** As a user who needs the tunnel to start automatically, I want the app to connect on boot or network change, so that my protection is always active.

#### Acceptance Criteria

1. WHEN a user enables auto-connect on boot, THE SSH_Tunnel_Proxy_App SHALL establish the tunnel automatically when the device starts
2. WHEN a user selects a default Server_Profile for auto-connect, THE SSH_Tunnel_Proxy_App SHALL use that profile for automatic connections
3. WHEN auto-connect is enabled and the device has no network connectivity, THE Auto_Reconnect_Service SHALL wait for network availability before attempting connection
4. WHEN a user disables auto-connect, THE SSH_Tunnel_Proxy_App SHALL not establish tunnels automatically
5. WHEN auto-connect fails after 3 attempts, THE SSH_Tunnel_Proxy_App SHALL notify the user and stop automatic connection attempts

### Requirement 12

**User Story:** As a developer or advanced user, I want to verify the tunnel is working correctly, so that I can ensure my traffic is properly routed.

#### Acceptance Criteria

1. WHEN the tunnel is active, THE SSH_Tunnel_Proxy_App SHALL provide a test function that verifies traffic is routed through the SSH_Server
2. WHEN a user initiates a connection test, THE SSH_Tunnel_Proxy_App SHALL query an external service to determine the apparent IP address
3. WHEN the test completes, THE SSH_Tunnel_Proxy_App SHALL display whether the IP address matches the SSH_Server location
4. WHEN the tunnel is active, THE SSH_Tunnel_Proxy_App SHALL display the local SOCKS5_Proxy address and port for manual configuration
5. WHEN a user enables verbose logging, THE SSH_Tunnel_Proxy_App SHALL log detailed connection events for debugging purposes
