# Requirements Document: Pure Kotlin Packet Router

## Introduction

The Pure Kotlin Packet Router is a critical component that enables the SSH Tunnel Proxy application to route device traffic through a SOCKS5 proxy without relying on native libraries. This component reads IP packets from Android's TUN interface, parses them, routes TCP and UDP traffic through the SOCKS5 proxy, and writes response packets back to the TUN interface.

Unlike native solutions (like tun2socks), this implementation is written entirely in Kotlin, making it easier to maintain, debug, and extend. The router must handle the complexities of IP packet parsing, TCP state management, UDP forwarding, DNS resolution, and proper packet reconstruction with correct checksums.

**Scope**: This feature focuses on IPv4 support with TCP and UDP protocols. IPv6 support is considered a future enhancement.

## Glossary

- **TUN_Interface**: Android's virtual network interface that captures all device traffic when VPN is active
- **IP_Packet**: Internet Protocol packet containing headers and payload data
- **TCP_Connection**: Transmission Control Protocol connection with state management (SYN, ACK, FIN, RST)
- **UDP_Datagram**: User Datagram Protocol packet for connectionless communication
- **SOCKS5_Proxy**: The local proxy server created by SSH tunnel that forwards traffic
- **Packet_Router**: The component that routes packets between TUN interface and SOCKS5 proxy
- **TCP_State_Machine**: Tracks TCP connection state (CLOSED, SYN_SENT, ESTABLISHED, FIN_WAIT, etc.)
- **Checksum**: Error-detection code for verifying packet integrity
- **MTU**: Maximum Transmission Unit - maximum packet size (typically 1500 bytes)
- **DNS_Query**: Domain Name System query for resolving hostnames to IP addresses
- **Connection_Table**: Data structure tracking active TCP/UDP connections

## Requirements

### Requirement 1

**User Story:** As the VPN service, I want to read IP packets from the TUN interface, so that I can route device traffic through the SOCKS5 proxy.

#### Acceptance Criteria

1. WHEN the Packet_Router starts, THE Packet_Router SHALL continuously read packets from the TUN_Interface file descriptor
2. WHEN a packet is read from TUN_Interface, THE Packet_Router SHALL parse the IP header to determine protocol version (IPv4/IPv6)
3. WHEN an IPv4 packet is received, THE Packet_Router SHALL extract source IP, destination IP, and protocol type
4. WHEN an IPv6 packet is received, THE Packet_Router SHALL log and skip the packet (IPv6 not supported in MVP)
5. WHEN packet reading encounters an error, THE Packet_Router SHALL log the error and continue reading subsequent packets

### Requirement 2

**User Story:** As the packet router, I want to parse TCP packets correctly, so that I can establish and manage TCP connections through SOCKS5.

#### Acceptance Criteria

1. WHEN a TCP packet is received, THE Packet_Router SHALL parse the TCP header to extract source port, destination port, sequence number, acknowledgment number, and flags
2. WHEN a TCP SYN packet is received, THE Packet_Router SHALL initiate a new SOCKS5 connection to the destination
3. WHEN a TCP packet with data is received, THE Packet_Router SHALL forward the payload through the corresponding SOCKS5 connection
4. WHEN a TCP FIN or RST packet is received, THE Packet_Router SHALL close the corresponding SOCKS5 connection
5. WHEN a TCP packet is received for an unknown connection, THE Packet_Router SHALL log and drop the packet

### Requirement 3

**User Story:** As the packet router, I want to manage TCP connection state, so that I can properly handle the TCP handshake and connection lifecycle.

#### Acceptance Criteria

1. WHEN a TCP SYN packet is received, THE TCP_State_Machine SHALL transition to SYN_SENT state and establish SOCKS5 connection
2. WHEN SOCKS5 connection succeeds, THE TCP_State_Machine SHALL send SYN-ACK packet back to TUN and transition to ESTABLISHED state
3. WHEN a TCP FIN packet is received in ESTABLISHED state, THE TCP_State_Machine SHALL send FIN-ACK and transition to FIN_WAIT state
4. WHEN a TCP RST packet is received, THE TCP_State_Machine SHALL immediately close connection and transition to CLOSED state
5. WHEN a connection is idle for more than 120 seconds, THE TCP_State_Machine SHALL close the connection and remove it from Connection_Table

### Requirement 4

**User Story:** As the packet router, I want to perform SOCKS5 handshake for each TCP connection, so that traffic is properly routed through the SSH tunnel.

#### Acceptance Criteria

1. WHEN establishing a new TCP connection, THE Packet_Router SHALL connect to localhost on the SOCKS5 port
2. WHEN connected to SOCKS5, THE Packet_Router SHALL send SOCKS5 greeting with no authentication method
3. WHEN SOCKS5 greeting succeeds, THE Packet_Router SHALL send CONNECT command with destination IP and port
4. WHEN SOCKS5 CONNECT succeeds, THE Packet_Router SHALL mark the connection as established and ready for data forwarding
5. WHEN SOCKS5 handshake fails, THE Packet_Router SHALL send TCP RST packet back to TUN and log the failure

### Requirement 5

**User Story:** As the packet router, I want to forward TCP data bidirectionally, so that applications can communicate through the tunnel.

#### Acceptance Criteria

1. WHEN TCP data is received from TUN_Interface, THE Packet_Router SHALL extract the payload and write it to the SOCKS5 socket
2. WHEN data is received from SOCKS5 socket, THE Packet_Router SHALL construct a TCP packet with proper headers and write it to TUN_Interface
3. WHEN constructing TCP response packets, THE Packet_Router SHALL use correct sequence and acknowledgment numbers
4. WHEN constructing TCP response packets, THE Packet_Router SHALL calculate and set correct TCP checksum
5. WHEN TCP data forwarding encounters an error, THE Packet_Router SHALL close the connection and send RST packet

### Requirement 6

**User Story:** As the packet router, I want to parse UDP packets correctly, so that I can forward UDP traffic through SOCKS5.

#### Acceptance Criteria

1. WHEN a UDP packet is received, THE Packet_Router SHALL parse the UDP header to extract source port, destination port, and payload
2. WHEN a UDP packet is for DNS (port 53), THE Packet_Router SHALL handle it as a DNS query
3. WHEN a UDP packet is for non-DNS traffic, THE Packet_Router SHALL forward it through SOCKS5 UDP ASSOCIATE (if supported)
4. WHEN UDP packet parsing fails, THE Packet_Router SHALL log the error and drop the packet
5. WHEN a UDP response is received from SOCKS5, THE Packet_Router SHALL construct a UDP response packet and write it to TUN_Interface

### Requirement 7

**User Story:** As the packet router, I want to handle DNS queries through the tunnel, so that DNS resolution is not leaked.

#### Acceptance Criteria

1. WHEN a DNS query (UDP port 53) is received, THE Packet_Router SHALL extract the DNS query payload
2. WHEN forwarding DNS query, THE Packet_Router SHALL use DNS-over-TCP through SOCKS5 for reliability
3. WHEN DNS query is sent through SOCKS5, THE Packet_Router SHALL prepend the 2-byte length field (DNS-over-TCP format)
4. WHEN DNS response is received from SOCKS5, THE Packet_Router SHALL construct a UDP packet with the DNS response
5. WHEN DNS query times out (>5 seconds), THE Packet_Router SHALL log the timeout and drop the query

### Requirement 8

**User Story:** As the packet router, I want to calculate correct IP and TCP checksums, so that packets are not rejected by the network stack.

#### Acceptance Criteria

1. WHEN constructing an IP packet, THE Packet_Router SHALL calculate the IP header checksum using one's complement algorithm
2. WHEN constructing a TCP packet, THE Packet_Router SHALL calculate the TCP checksum including the pseudo-header
3. WHEN constructing a UDP packet, THE Packet_Router SHALL calculate the UDP checksum (or set to 0 for IPv4)
4. WHEN checksum calculation is performed, THE Packet_Router SHALL handle odd-length data correctly
5. WHEN a constructed packet is written to TUN, THE Packet_Router SHALL verify checksums are set correctly

### Requirement 9

**User Story:** As the packet router, I want to manage connection resources efficiently, so that memory usage remains reasonable.

#### Acceptance Criteria

1. WHEN a new connection is established, THE Packet_Router SHALL add it to the Connection_Table with a unique key
2. WHEN a connection is closed, THE Packet_Router SHALL remove it from Connection_Table and release resources
3. WHEN Connection_Table exceeds 1000 entries, THE Packet_Router SHALL close the oldest idle connections
4. WHEN the router stops, THE Packet_Router SHALL close all active connections and clear Connection_Table
5. WHEN a connection is idle for more than 120 seconds, THE Packet_Router SHALL automatically close it

### Requirement 10 (Optional - Post-MVP)

**User Story:** As the packet router, I want to handle packet fragmentation, so that large packets are properly processed.

#### Acceptance Criteria

1. WHEN a packet larger than MTU is received from SOCKS5, THE Packet_Router SHALL fragment it into multiple IP packets
2. WHEN fragmented packets are created, THE Packet_Router SHALL set correct fragment offset and flags
3. WHEN receiving fragmented packets from TUN, THE Packet_Router SHALL reassemble them before forwarding
4. WHEN fragment reassembly times out (>30 seconds), THE Packet_Router SHALL discard incomplete fragments
5. WHEN MTU is configured, THE Packet_Router SHALL respect the MTU value (default 1500 bytes)

**Note:** Most packets fit within standard MTU (1500 bytes), so fragmentation is rarely needed in practice. This can be implemented post-MVP if needed.

### Requirement 11

**User Story:** As the packet router, I want to handle errors gracefully, so that one bad packet doesn't crash the router.

#### Acceptance Criteria

1. WHEN packet parsing fails, THE Packet_Router SHALL log the error with packet details and continue processing
2. WHEN SOCKS5 connection fails, THE Packet_Router SHALL send RST packet to client and log the failure
3. WHEN TUN interface read fails, THE Packet_Router SHALL log the error and attempt to continue
4. WHEN TUN interface write fails, THE Packet_Router SHALL log the error and close the affected connection
5. WHEN an unexpected exception occurs, THE Packet_Router SHALL catch it, log it, and continue operation

### Requirement 12

**User Story:** As a developer, I want detailed logging for packet routing, so that I can debug connection issues.

#### Acceptance Criteria

1. WHEN verbose logging is enabled, THE Packet_Router SHALL log each packet received with protocol, source, and destination
2. WHEN a new connection is established, THE Packet_Router SHALL log the connection details (source, destination, protocol)
3. WHEN a connection is closed, THE Packet_Router SHALL log the closure reason and connection duration
4. WHEN SOCKS5 handshake fails, THE Packet_Router SHALL log the failure reason and SOCKS5 response code
5. WHEN logging packet details, THE Packet_Router SHALL not log packet payload data (privacy)

### Requirement 13

**User Story:** As the packet router, I want to track connection statistics, so that users can monitor traffic.

#### Acceptance Criteria

1. WHEN data is forwarded through a connection, THE Packet_Router SHALL track bytes sent and bytes received
2. WHEN a connection is active, THE Packet_Router SHALL track the connection duration
3. WHEN statistics are requested, THE Packet_Router SHALL provide total connections, active connections, bytes sent, and bytes received
4. WHEN the router is reset, THE Packet_Router SHALL reset all statistics counters
5. WHEN statistics are accessed, THE Packet_Router SHALL provide thread-safe access to statistics data

### Requirement 14 (Optional - Post-MVP)

**User Story:** As the packet router, I want to support SOCKS5 UDP ASSOCIATE, so that non-DNS UDP traffic can be routed.

#### Acceptance Criteria

1. WHEN a non-DNS UDP packet is received, THE Packet_Router SHALL establish SOCKS5 UDP ASSOCIATE connection
2. WHEN UDP ASSOCIATE succeeds, THE Packet_Router SHALL encapsulate UDP packets in SOCKS5 UDP format
3. WHEN UDP response is received, THE Packet_Router SHALL decapsulate and forward to TUN_Interface
4. WHEN UDP ASSOCIATE connection is idle for 60 seconds, THE Packet_Router SHALL close it
5. WHEN SOCKS5 server doesn't support UDP ASSOCIATE, THE Packet_Router SHALL log and drop UDP packets

### Requirement 15 (Optional - Post-MVP)

**User Story:** As the packet router, I want to support IPv6, so that modern networks are fully supported.

#### Acceptance Criteria

1. WHEN an IPv6 packet is received, THE Packet_Router SHALL parse the IPv6 header correctly
2. WHEN routing IPv6 through SOCKS5, THE Packet_Router SHALL use SOCKS5 ATYP_IPV6 address type
3. WHEN constructing IPv6 response packets, THE Packet_Router SHALL calculate correct IPv6 checksums
4. WHEN IPv6 extension headers are present, THE Packet_Router SHALL parse them correctly
5. WHEN IPv6 is not supported by SOCKS5 server, THE Packet_Router SHALL fall back to IPv4 or drop packets

## Non-Functional Requirements

### Performance

- **Throughput**: Support at least 10 Mbps throughput on mid-range devices
- **Latency**: Add no more than 10ms latency overhead for packet processing
- **Memory**: Use no more than 50MB RAM for connection table and buffers
- **CPU**: Use no more than 10% CPU on idle connections

### Reliability

- **Packet Loss**: Minimize packet loss to <1% under normal conditions
- **Connection Stability**: Maintain connections for hours without issues
- **Error Recovery**: Recover from transient errors without crashing

### Compatibility

- **Android Versions**: Support Android 8.0 (API 26) and above
- **SSH Servers**: Work with any SSH server that supports dynamic port forwarding
- **Network Types**: Work on WiFi, mobile data, and when switching between them

## Out of Scope

- IPv6 support (post-MVP)
- SOCKS5 UDP ASSOCIATE for non-DNS traffic (post-MVP)
- ICMP (ping) support
- IPsec or other VPN protocols
- Hardware acceleration
- Packet compression
