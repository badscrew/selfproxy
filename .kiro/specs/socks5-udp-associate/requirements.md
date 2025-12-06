# Requirements Document

## Introduction
 
This specification defines the requirements for implementing SOCKS5 UDP ASSOCIATE functionality in the SSH Tunnel Proxy VPN application. This feature will enable UDP-based applications such as video calling (WhatsApp, Telegram, Zoom, Discord), voice chat, and online gaming to work through the VPN tunnel. Currently, only DNS queries (UDP port 53) are supported; this enhancement will add support for all UDP traffic.

**CRITICAL CONSTRAINT**:Standard OpenSSH dynamic forwarding (`ssh -D`) does **NOT** support SOCKS5 UDP ASSOCIATE (command 0x03). This feature requires either:
1. A dedicated SOCKS5 server with UDP support (e.g., Dante, Shadowsocks, 3proxy)
2. A custom SSH server implementation with UDP relay capabilities
3. An alternative tunneling approach (e.g., tun2socks, VPN-over-SSH)

**Current Status**: The application correctly implements SOCKS5 UDP ASSOCIATE protocol, but most SSH servers reject the command with error 0x07 (Command not supported). This is a **server-side limitation**, not a client implementation issue.

## Glossary

- **SOCKS5**: A protocol that routes network packets between a client and server through a proxy server
- **UDP ASSOCIATE**: A SOCKS5 command (0x03) that establishes a UDP relay connection
- **UDP Relay**: A SOCKS5 server component that forwards UDP datagrams between client and destination
- **TUN Interface**: A virtual network interface that captures IP packets from applications
- **Datagram**: A self-contained, independent packet in UDP communication
- **UDPHandler**: The component responsible for processing UDP packets from the TUN interface
- **ConnectionTable**: A data structure tracking active network connections
- **WebRTC**: Web Real-Time Communication protocol used by video calling applications
- **STUN/TURN**: Protocols used for NAT traversal in real-time communication (typically UDP ports 3478-3479)

## Requirements

### Requirement 1: SOCKS5 UDP ASSOCIATE Protocol Implementation

**User Story:** As a VPN user, I want UDP traffic to be routed through the SOCKS5 proxy, so that video calling and voice chat applications work correctly.

#### Acceptance Criteria

1. WHEN the UDPHandler receives a non-DNS UDP packet, THEN the system SHALL establish a SOCKS5 UDP ASSOCIATE connection for that flow
2. WHEN establishing UDP ASSOCIATE, THEN the system SHALL send a SOCKS5 request with CMD=0x03 (UDP ASSOCIATE) to the proxy server
3. WHEN the SOCKS5 server responds to UDP ASSOCIATE, THEN the system SHALL parse the BND.ADDR and BND.PORT to determine the UDP relay endpoint
4. WHEN the UDP ASSOCIATE is established, THEN the system SHALL create a UDP socket bound to the relay endpoint
5. WHEN sending UDP datagrams through SOCKS5, THEN the system SHALL encapsulate each datagram with the SOCKS5 UDP request header (RSV, FRAG, ATYP, DST.ADDR, DST.PORT, DATA)

### Requirement 2: UDP Connection Management

**User Story:** As a system administrator, I want UDP connections to be properly tracked and managed, so that resources are efficiently utilized and connections are cleaned up appropriately.

#### Acceptance Criteria

1. WHEN a UDP ASSOCIATE connection is established, THEN the system SHALL add it to the ConnectionTable with the connection key (5-tuple)
2. WHEN a UDP connection is idle for more than 2 minutes, THEN the system SHALL close the UDP relay socket and remove the connection from the table
3. WHEN the VPN service stops, THEN the system SHALL close all active UDP relay sockets
4. WHEN tracking UDP connections, THEN the system SHALL record bytes sent and bytes received for statistics
5. WHEN a UDP connection is active, THEN the system SHALL update the lastActivityAt timestamp on each packet transmission

### Requirement 3: UDP Packet Encapsulation and Forwarding

**User Story:** As a developer, I want UDP packets to be correctly encapsulated according to SOCKS5 protocol, so that the proxy server can route them to the correct destination.

#### Acceptance Criteria

1. WHEN encapsulating a UDP packet for SOCKS5, THEN the system SHALL prepend the SOCKS5 UDP header: [RSV(2 bytes)=0x0000, FRAG(1 byte)=0x00, ATYP(1 byte), DST.ADDR, DST.PORT(2 bytes)]
2. WHEN the destination is an IPv4 address, THEN the system SHALL set ATYP=0x01 and include 4 bytes for DST.ADDR
3. WHEN the destination is an IPv6 address, THEN the system SHALL set ATYP=0x04 and include 16 bytes for DST.ADDR
4. WHEN the destination is a domain name, THEN the system SHALL set ATYP=0x03 and include [length(1 byte), domain name] for DST.ADDR
5. WHEN sending the encapsulated datagram, THEN the system SHALL transmit it to the UDP relay endpoint (BND.ADDR:BND.PORT)

### Requirement 4: UDP Response Processing

**User Story:** As a VPN user, I want responses from UDP services to be correctly routed back to my applications, so that bidirectional communication works properly.

#### Acceptance Criteria

1. WHEN receiving a datagram from the UDP relay socket, THEN the system SHALL parse the SOCKS5 UDP response header to extract the source address and payload
2. WHEN the SOCKS5 UDP header is parsed, THEN the system SHALL validate that RSV=0x0000 and FRAG=0x00
3. WHEN the payload is extracted, THEN the system SHALL construct a UDP packet with the original source/destination addresses swapped
4. WHEN constructing the response packet, THEN the system SHALL build a complete IP+UDP packet with correct checksums
5. WHEN the response packet is ready, THEN the system SHALL write it to the TUN interface for delivery to the application

### Requirement 5: UDP Reader Coroutine

**User Story:** As a system architect, I want UDP relay sockets to be monitored asynchronously, so that responses are processed without blocking other operations.

#### Acceptance Criteria

1. WHEN a UDP ASSOCIATE connection is established, THEN the system SHALL launch a coroutine to read from the UDP relay socket
2. WHEN the reader coroutine is active, THEN it SHALL continuously read datagrams from the relay socket with a timeout
3. WHEN a datagram is received, THEN the reader SHALL process it and write the response to the TUN interface
4. WHEN the connection is closed or times out, THEN the reader coroutine SHALL terminate gracefully
5. WHEN an error occurs in the reader, THEN it SHALL log the error and clean up the connection without crashing the router

### Requirement 6: Connection Reuse and Multiplexing

**User Story:** As a performance-conscious user, I want UDP connections to the same destination to reuse existing SOCKS5 associations, so that connection overhead is minimized.

#### Acceptance Criteria

1. WHEN a UDP packet arrives for a destination, THEN the system SHALL check if an active UDP ASSOCIATE connection exists for that 5-tuple
2. WHEN an active connection exists, THEN the system SHALL reuse it to send the datagram
3. WHEN no active connection exists, THEN the system SHALL establish a new UDP ASSOCIATE connection
4. WHEN multiple packets arrive rapidly for the same destination, THEN the system SHALL queue them and send through the same connection
5. WHEN a connection is reused, THEN the system SHALL update the lastActivityAt timestamp to prevent premature cleanup

### Requirement 7: Error Handling and Resilience

**User Story:** As a VPN user, I want the system to handle UDP errors gracefully, so that one failed connection doesn't affect other traffic.

#### Acceptance Criteria

1. WHEN the SOCKS5 server rejects UDP ASSOCIATE (REP != 0x00), THEN the system SHALL log the error with the specific failure reason and drop the packet
2. WHEN a UDP relay socket fails to send a datagram, THEN the system SHALL log the error and close that specific connection
3. WHEN receiving a malformed SOCKS5 UDP header, THEN the system SHALL log the error and drop the packet without crashing
4. WHEN the UDP relay socket times out, THEN the system SHALL close the connection and remove it from the table
5. WHEN any UDP operation fails, THEN the system SHALL continue processing other UDP and TCP traffic normally

### Requirement 8: WebRTC and Video Calling Support

**User Story:** As a user of video calling applications, I want WhatsApp calls, Zoom meetings, and Discord voice to work through the VPN, so that I can communicate privately.

#### Acceptance Criteria

1. WHEN a video calling application sends UDP packets to STUN/TURN servers (ports 3478-3479), THEN the system SHALL route them through SOCKS5 UDP ASSOCIATE
2. WHEN WebRTC media streams are transmitted (typically high ports 49152-65535), THEN the system SHALL handle them with low latency
3. WHEN bidirectional UDP communication occurs, THEN both outgoing and incoming packets SHALL be processed correctly
4. WHEN multiple simultaneous video calls are active, THEN each SHALL have its own UDP ASSOCIATE connection
5. WHEN a video call ends, THEN the system SHALL clean up the associated UDP connections within the idle timeout period

### Requirement 9: Statistics and Monitoring

**User Story:** As a system administrator, I want to monitor UDP traffic statistics, so that I can understand usage patterns and troubleshoot issues.

#### Acceptance Criteria

1. WHEN UDP packets are sent through SOCKS5, THEN the system SHALL increment the bytesSent counter for that connection
2. WHEN UDP responses are received, THEN the system SHALL increment the bytesReceived counter for that connection
3. WHEN querying connection statistics, THEN the system SHALL report total UDP connections, active UDP connections, and data transferred
4. WHEN a UDP connection is closed, THEN the system SHALL log the connection duration, bytes sent, and bytes received
5. WHEN monitoring the router, THEN UDP statistics SHALL be included in the overall RouterStatistics

### Requirement 10: Performance and Scalability

**User Story:** As a performance-conscious user, I want UDP routing to have minimal latency, so that real-time applications remain responsive.

#### Acceptance Criteria

1. WHEN processing UDP packets, THEN the system SHALL handle each packet asynchronously to avoid blocking
2. WHEN multiple UDP connections are active, THEN the system SHALL process them concurrently using coroutines
3. WHEN a UDP packet arrives, THEN the system SHALL forward it to SOCKS5 within 10 milliseconds (excluding network latency)
4. WHEN the system is under load, THEN UDP packet processing SHALL not degrade TCP performance
5. WHEN memory usage is measured, THEN each UDP connection SHALL consume less than 10KB of memory

### Requirement 11: Compatibility and Standards Compliance

**User Story:** As a developer, I want the implementation to follow SOCKS5 RFC 1928 standards, so that it works with any compliant SOCKS5 server that supports UDP ASSOCIATE.

#### Acceptance Criteria

1. WHEN implementing UDP ASSOCIATE, THEN the system SHALL follow RFC 1928 Section 7 (UDP ASSOCIATE)
2. WHEN constructing SOCKS5 UDP headers, THEN the system SHALL use the format specified in RFC 1928
3. WHEN handling fragmentation, THEN the system SHALL set FRAG=0x00 (no fragmentation) as most SOCKS5 servers don't support it
4. WHEN the SOCKS5 server doesn't support UDP ASSOCIATE (REP=0x07), THEN the system SHALL detect this, log an appropriate error, and inform the user that their SSH server doesn't support UDP
5. WHEN testing compatibility, THEN the system SHALL work with SOCKS5 servers that support UDP ASSOCIATE (e.g., Dante, Shadowsocks, 3proxy)

**Note**: OpenSSH dynamic forwarding (`ssh -D`) does **NOT** support UDP ASSOCIATE and will return error 0x07. This is a known limitation of OpenSSH's SOCKS5 implementation.

### Requirement 12: Server Capability Detection and User Notification

**User Story:** As a VPN user, I want to be informed when my SSH server doesn't support UDP traffic, so that I understand why video calling doesn't work and what I can do about it.

#### Acceptance Criteria

1. WHEN the first UDP ASSOCIATE request fails with error 0x07 (Command not supported), THEN the system SHALL mark the server as "UDP not supported" in the connection state
2. WHEN a server is marked as "UDP not supported", THEN the system SHALL NOT attempt UDP ASSOCIATE for subsequent UDP packets from that session
3. WHEN UDP is not supported, THEN the system SHALL display a persistent notification to the user: "Your SSH server doesn't support UDP traffic. Video calling and voice chat will not work. TCP traffic (web browsing) works normally."
4. WHEN displaying the connection status in the UI, THEN the system SHALL show "TCP Only" or "TCP + UDP" badge based on server capabilities
5. WHEN the user taps on the "TCP Only" badge, THEN the system SHALL show a dialog explaining: "Your SSH server (OpenSSH) doesn't support UDP ASSOCIATE. To enable video calling, you need a SOCKS5 server with UDP support like Dante, Shadowsocks, or 3proxy."

### Requirement 13: Fallback Behavior for Unsupported Servers

**User Story:** As a VPN user with an SSH server that doesn't support UDP, I want the VPN to continue working for TCP traffic, so that web browsing and other TCP applications still function.

#### Acceptance Criteria

1. WHEN UDP ASSOCIATE is not supported, THEN the system SHALL continue routing TCP traffic normally
2. WHEN UDP ASSOCIATE is not supported, THEN DNS queries SHALL continue to work via the existing TCP-based DNS resolution
3. WHEN UDP ASSOCIATE is not supported, THEN UDP packets (except DNS) SHALL be dropped with a log message
4. WHEN dropping UDP packets, THEN the system SHALL increment a "droppedUdpPackets" counter for statistics
5. WHEN the user checks statistics, THEN the system SHALL display the number of dropped UDP packets and explain why they were dropped

### Requirement 14: Testing and Validation

**User Story:** As a quality assurance engineer, I want comprehensive tests for UDP functionality, so that I can verify correct behavior.

#### Acceptance Criteria

1. WHEN testing UDP ASSOCIATE, THEN unit tests SHALL verify SOCKS5 handshake message construction
2. WHEN testing UDP encapsulation, THEN property tests SHALL verify correct header construction for various addresses
3. WHEN testing UDP forwarding, THEN integration tests SHALL verify end-to-end packet delivery
4. WHEN testing connection management, THEN tests SHALL verify proper cleanup and resource management
5. WHEN testing error scenarios, THEN tests SHALL verify graceful handling of all failure modes
