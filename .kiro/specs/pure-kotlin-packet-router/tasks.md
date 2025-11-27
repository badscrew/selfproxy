# Implementation Tasks: Pure Kotlin Packet Router

## Phase 1: Core Infrastructure

- [x] 1. Create packet parsing utilities





  - Create IPPacketParser object in androidApp/src/main/kotlin/com/sshtunnel/android/vpn/packet/
  - Implement parseIPv4Header() method
  - Implement extractProtocol(), extractSourceIP(), extractDestIP() methods
  - Implement getHeaderLength() and validateChecksum() methods
  - Add Protocol enum (TCP, UDP, ICMP, UNKNOWN)
  - Add IPv4Header data class
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 1.1 Write unit tests for IP packet parsing


  - Test valid IPv4 header parsing
  - Test invalid IP version rejection
  - Test protocol extraction
  - Test IP address extraction
  - Test checksum validation

- [x] 2. Create packet builder utilities




  - Create PacketBuilder class in androidApp/src/main/kotlin/com/sshtunnel/android/vpn/packet/
  - Implement buildIPv4Packet() method
  - Implement buildTcpPacket() method
  - Implement buildUdpPacket() method
  - Implement calculateIPChecksum() method
  - Implement calculateTcpChecksum() method with pseudo-header
  - Implement calculateUdpChecksum() method
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 2.1 Write unit tests for packet building


  - Test IP packet construction
  - Test TCP packet construction with correct checksum
  - Test UDP packet construction
  - Test checksum calculation accuracy
  - Test odd-length data handling in checksum

- [x] 2.2 Write property tests for packet operations


  - **Property: Checksum calculation is symmetric**
  - **Property: IP packet round-trip preserves data**
  - Test with various packet sizes and content

- [x] 3. Create connection management data structures




  - Create ConnectionKey data class with Protocol enum
  - Create TcpConnection data class with TcpState enum
  - Create UdpConnection data class
  - Create TcpHeader data class with TcpFlags
  - Create UdpHeader data class
  - Create SequenceNumberTracker class for TCP sequence management
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 4. Implement ConnectionTable





  - Create ConnectionTable class in androidApp/src/main/kotlin/com/sshtunnel/android/vpn/
  - Implement addTcpConnection(), getTcpConnection(), removeTcpConnection()
  - Implement addUdpConnection(), getUdpConnection(), removeUdpConnection()
  - Implement cleanupIdleConnections() with configurable timeout
  - Implement closeAllConnections()
  - Implement getStatistics() for connection tracking
  - Add thread-safe access with Mutex
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 13.1, 13.2, 13.3, 13.4, 13.5_

- [x] 4.1 Write unit tests for ConnectionTable


  - Test adding and retrieving connections
  - Test connection removal
  - Test idle connection cleanup
  - Test statistics tracking
  - Test thread safety with concurrent access

- [x] 4.2 Write property test for connection table


  - **Property: TCP connection establishment always creates connection table entry**
  - Test with various connection parameters

## Phase 2: TCP Handler Implementation

- [x] 5. Implement TCP packet parsing





  - Create TCPHandler class in androidApp/src/main/kotlin/com/sshtunnel/android/vpn/
  - Implement parseTcpHeader() method
  - Extract source port, dest port, sequence number, ack number
  - Parse TCP flags (SYN, ACK, FIN, RST, PSH, URG)
  - Extract TCP payload
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 5.1 Write unit tests for TCP parsing


  - Test TCP header parsing
  - Test flag extraction
  - Test payload extraction
  - Test various TCP packet types (SYN, ACK, FIN, RST)

- [x] 6. Implement SOCKS5 handshake for TCP




  - Implement performSocks5Handshake() method
  - Send SOCKS5 greeting (version, methods)
  - Read method selection response
  - Send CONNECT command with destination IP and port
  - Read CONNECT response
  - Handle SOCKS5 errors with specific error codes
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 6.1 Write unit tests for SOCKS5 handshake


  - Test successful handshake
  - Test authentication failure
  - Test connection refused
  - Test timeout handling
  - Use mock SOCKS5 server

- [ ] 7. Implement TCP connection establishment
  - Implement handleSyn() method
  - Create ConnectionKey from packet
  - Establish SOCKS5 connection
  - Perform SOCKS5 handshake
  - Add connection to ConnectionTable
  - Start connection reader coroutine
  - Send SYN-ACK packet back to TUN
  - _Requirements: 3.1, 3.2, 4.1, 4.2, 4.3, 4.4_

- [ ] 7.1 Write integration test for TCP connection establishment
  - Test full SYN → SOCKS5 → SYN-ACK flow
  - Verify connection table entry created
  - Verify SOCKS5 connection established

- [ ] 8. Implement TCP data forwarding
  - Implement handleData() method for TUN → SOCKS5
  - Extract TCP payload from packet
  - Look up connection in ConnectionTable
  - Write payload to SOCKS5 socket
  - Update sequence numbers
  - Implement startConnectionReader() for SOCKS5 → TUN
  - Read data from SOCKS5 socket in coroutine
  - Build TCP packet with data
  - Write packet to TUN interface
  - Update statistics (bytes sent/received)
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 13.1_

- [ ] 8.1 Write integration test for TCP data forwarding
  - Test bidirectional data flow
  - Verify data integrity
  - Test large data transfers
  - Verify statistics tracking

- [ ] 9. Implement TCP connection termination
  - Implement handleFin() method
  - Send FIN to SOCKS5 socket
  - Update TCP state to FIN_WAIT
  - Send FIN-ACK back to TUN
  - Implement handleRst() method
  - Close SOCKS5 socket immediately
  - Remove connection from ConnectionTable
  - Cancel connection reader coroutine
  - _Requirements: 3.3, 3.4, 9.2_

- [ ] 9.1 Write unit tests for TCP termination
  - Test FIN handling
  - Test RST handling
  - Verify connection cleanup
  - Verify resources released

- [ ] 10. Implement TCP state machine
  - Add TcpState enum (CLOSED, SYN_SENT, ESTABLISHED, FIN_WAIT, etc.)
  - Implement state transitions in TcpConnection
  - Handle state-specific packet processing
  - Implement connection timeout for idle connections
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 9.5_

- [ ] 10.1 Write unit tests for TCP state machine
  - Test state transitions
  - Test invalid state transitions
  - Test timeout handling

## Phase 3: UDP Handler Implementation

- [ ] 11. Implement UDP packet parsing
  - Create UDPHandler class in androidApp/src/main/kotlin/com/sshtunnel/android/vpn/
  - Implement parseUdpHeader() method
  - Extract source port, dest port, length, checksum
  - Extract UDP payload
  - Detect DNS queries (port 53)
  - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [ ] 11.1 Write unit tests for UDP parsing
  - Test UDP header parsing
  - Test payload extraction
  - Test DNS query detection

- [ ] 12. Implement DNS query routing
  - Implement handleDnsQuery() method
  - Extract DNS query payload from UDP packet
  - Implement queryDnsThroughSocks5() using DNS-over-TCP
  - Connect to DNS server through SOCKS5
  - Send DNS query with 2-byte length prefix
  - Read DNS response with length prefix
  - Handle DNS timeout (5 seconds)
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 12.1 Write integration test for DNS routing
  - Test DNS query through SOCKS5
  - Test DNS response construction
  - Test DNS timeout handling
  - Use mock DNS server

- [ ] 13. Implement UDP response packet construction
  - Implement sendUdpPacket() method
  - Build IP header with swapped source/dest
  - Build UDP header with correct length
  - Calculate UDP checksum (or set to 0)
  - Write packet to TUN interface
  - _Requirements: 6.5, 8.3_

- [ ] 13.1 Write unit tests for UDP packet construction
  - Test UDP response packet structure
  - Test checksum calculation
  - Test DNS response packet

## Phase 4: PacketRouter Integration

- [ ] 14. Refactor PacketRouter to use new components
  - Update PacketRouter class to use IPPacketParser
  - Update to use TCPHandler for TCP packets
  - Update to use UDPHandler for UDP packets
  - Update to use PacketBuilder for response packets
  - Update to use ConnectionTable for connection management
  - Remove old stub implementations
  - _Requirements: 1.1, 1.2, 1.3, 1.5_

- [ ] 14.1 Update TunnelVpnService integration
  - Verify TunnelVpnService correctly instantiates refactored PacketRouter
  - Ensure FileInputStream/FileOutputStream are passed correctly
  - Update any service-level error handling
  - Test VPN service lifecycle with new router

- [ ] 14.2 Remove old PacketRouter stub code
  - Delete old packet routing logic from PacketRouter.kt
  - Remove unused helper methods
  - Clean up any temporary workarounds
  - Verify no references to old implementation remain

- [ ] 15. Implement packet routing main loop
  - Implement routePackets() method with coroutine
  - Read packets from TUN FileInputStream
  - Parse IP header for each packet
  - Dispatch to appropriate handler (TCP/UDP)
  - Handle packet parsing errors gracefully
  - Continue processing on errors
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 11.1, 11.2, 11.3, 11.4, 11.5_

- [ ] 15.1 Write integration test for packet routing
  - Test end-to-end packet flow
  - Test error handling
  - Test multiple concurrent connections

- [ ] 16. Implement connection cleanup
  - Add periodic cleanup coroutine
  - Check for idle connections every 30 seconds
  - Close connections idle for >2 minutes
  - Clean up resources properly
  - _Requirements: 9.2, 9.3, 9.4, 9.5_

- [ ] 16.1 Write unit tests for connection cleanup
  - Test idle connection detection
  - Test cleanup timing
  - Test resource release

- [ ] 17. Implement statistics tracking
  - Track bytes sent/received per connection
  - Track connection duration
  - Implement getStatistics() method
  - Provide total and per-connection statistics
  - Ensure thread-safe access
  - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5_

- [ ] 17.1 Write unit tests for statistics
  - Test byte counting
  - Test duration tracking
  - Test statistics aggregation

## Phase 5: Logging and Debugging

- [ ] 18. Implement comprehensive logging
  - Add verbose logging for packet reception
  - Log connection establishment with details
  - Log connection closure with reason and duration
  - Log SOCKS5 handshake failures with error codes
  - Never log packet payload data (privacy)
  - Use existing Logger with verbose mode
  - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

- [ ] 18.1 Write tests for logging
  - Verify verbose logging produces detailed logs
  - Verify payload data is never logged
  - Verify sensitive data is sanitized

- [ ] 19. Add error handling and recovery
  - Wrap all packet processing in try-catch
  - Log errors with context
  - Continue processing after errors
  - Handle TUN interface read/write errors
  - Handle SOCKS5 connection errors
  - Send RST packets on connection failures
  - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

- [ ] 19.1 Write tests for error handling
  - Test malformed packet handling
  - Test SOCKS5 failure handling
  - Test TUN interface error handling
  - Verify router continues after errors

## Phase 6: Testing and Optimization

- [ ] 20. Integration testing with real SSH tunnel
  - Test HTTP browsing (port 80)
  - Test HTTPS browsing (port 443)
  - Test DNS resolution
  - Test large file downloads
  - Test multiple simultaneous connections
  - Test connection survival during network switch
  - _Requirements: All_

- [ ] 21. Performance testing and optimization
  - Measure throughput (target: 10 Mbps)
  - Measure latency overhead (target: <10ms)
  - Measure memory usage (target: <50MB)
  - Measure CPU usage (target: <10% idle)
  - Optimize hot paths if needed
  - Profile with Android Profiler
  - _Requirements: Non-functional requirements_

- [ ] 22. Memory leak testing
  - Run extended test sessions (1+ hour)
  - Monitor memory usage over time
  - Check for connection leaks
  - Check for coroutine leaks
  - Use LeakCanary for detection
  - _Requirements: 9.2, 9.3, 9.4_

- [ ] 23. Manual testing checklist
  - [ ] HTTP browsing works
  - [ ] HTTPS browsing works
  - [ ] DNS resolution works
  - [ ] Large file downloads work
  - [ ] Streaming video works
  - [ ] WebSocket connections work
  - [ ] Multiple simultaneous connections work
  - [ ] Connection survives network switch
  - [ ] No memory leaks after extended use
  - [ ] CPU usage remains reasonable
  - _Requirements: All_

## Phase 7: Documentation and Polish

- [ ] 24. Add code documentation
  - Document all public methods with KDoc
  - Add inline comments for complex logic
  - Document packet format assumptions
  - Document SOCKS5 protocol implementation
  - Add architecture diagrams to code
  - _Requirements: All_

- [ ] 25. Update user-facing documentation
  - Update README with packet router details
  - Add troubleshooting guide for connection issues
  - Document performance characteristics
  - Add FAQ for common issues
  - _Requirements: All_

- [ ] 26. Final checkpoint - Ensure all tests pass
  - Run all unit tests
  - Run all integration tests
  - Run all property tests
  - Verify no regressions in existing functionality
  - _Requirements: All_

## Optional Enhancements (Post-MVP)

- [ ] 27. Implement IPv6 support
  - Parse IPv6 headers
  - Use SOCKS5 ATYP_IPV6
  - Calculate IPv6 checksums
  - Handle IPv6 extension headers
  - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_

- [ ] 28. Implement SOCKS5 UDP ASSOCIATE
  - Support UDP forwarding for non-DNS traffic
  - Implement SOCKS5 UDP encapsulation
  - Handle UDP fragmentation
  - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5_

- [ ] 29. Implement packet fragmentation
  - Fragment large packets from SOCKS5
  - Reassemble fragmented packets from TUN
  - Handle fragment timeout
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [ ] 30. Performance optimizations
  - Implement packet batching
  - Use zero-copy buffers
  - Optimize connection lookup
  - Add fast path for established connections
  - _Requirements: Performance_
