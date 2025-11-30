# Implementation Plan: SOCKS5 UDP ASSOCIATE

## Task List

- [x] 1. Create new data classes for UDP ASSOCIATE





  - Create UdpAssociateConnection data class with control socket, relay socket, and endpoint
  - Create UdpRelayEndpoint data class for BND.ADDR and BND.PORT
  - Create UdpDecapsulatedPacket data class for parsed responses
  - _Requirements: 1.3, 2.1_

- [x] 2. Enhance ConnectionTable for UDP ASSOCIATE tracking





  - Add ConcurrentHashMap for UDP ASSOCIATE connections
  - Implement addUdpAssociateConnection() method
  - Implement getUdpAssociateConnection() method
  - Implement removeUdpAssociateConnection() method
  - Implement updateUdpAssociateStats() method
  - Update cleanupIdleConnections() to handle UDP ASSOCIATE connections
  - Update closeAllConnections() to close UDP ASSOCIATE connections
  - Update getStatistics() to include UDP ASSOCIATE metrics
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 9.3_

- [x] 3. Implement SOCKS5 UDP ASSOCIATE handshake





  - Create performUdpAssociateHandshake() method
  - Build UDP ASSOCIATE request message (VER=0x05, CMD=0x03, ATYP, DST.ADDR, DST.PORT)
  - Send request on TCP control socket
  - Parse response to extract BND.ADDR and BND.PORT
  - Handle error codes (0x01-0x08) with specific error messages
  - Return UdpRelayEndpoint or null on failure
  - _Requirements: 1.1, 1.2, 1.3, 7.1, 11.1, 11.2_

- [x] 3.1 Write property test for UDP ASSOCIATE handshake


  - **Property 1: UDP ASSOCIATE Handshake Correctness**
  - **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
  - Generate random connection keys
  - Verify handshake produces valid relay endpoint with non-zero port
  - Test with mock SOCKS5 server responses

- [x] 4. Implement UDP packet encapsulation





  - Create encapsulateUdpPacket() method
  - Build SOCKS5 UDP header: RSV(0x0000), FRAG(0x00), ATYP, DST.ADDR, DST.PORT
  - Support IPv4 addresses (ATYP=0x01)
  - Append original UDP payload after header
  - Return complete encapsulated datagram
  - _Requirements: 3.1, 3.2, 3.5, 11.2, 11.3_

- [x] 4.1 Write property test for UDP encapsulation


  - **Property 2: UDP Encapsulation Format Compliance**
  - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 11.1, 11.2, 11.3**
  - Generate random IP addresses, ports, and payloads
  - Verify encapsulated packets have valid SOCKS5 headers
  - Verify RSV=0x0000, FRAG=0x00, correct ATYP
  - Verify payload is preserved
- [x] 5. Implement UDP packet decapsulation



- [ ] 5. Implement UDP packet decapsulation

  - Create decapsulateUdpPacket() method
  - Parse SOCKS5 UDP response header
  - Validate RSV=0x0000 and FRAG=0x00
  - Extract source IP address based on ATYP
  - Extract source port (2 bytes)
  - Extract payload (remaining bytes)
  - Return UdpDecapsulatedPacket or null if invalid
  - _Requirements: 4.1, 4.2, 7.3_

- [x] 5.1 Write property test for UDP decapsulation



  - **Property 3: UDP Decapsulation Correctness**
  - **Validates: Requirements 4.1, 4.2, 4.3**
  - Generate random SOCKS5 UDP packets
  - Verify decapsulation correctly extracts source address, port, and payload
  - Test round-trip: encapsulate then decapsulate should preserve data


- [x] 6. Implement establishUdpAssociate() method




  - Create TCP control socket to SOCKS5 proxy
  - Perform initial SOCKS5 greeting (VER=0x05, NMETHODS=0x01, METHOD=0x00)
  - Call performUdpAssociateHandshake() to get relay endpoint
  - Create DatagramSocket for UDP relay communication
  - Create UdpAssociateConnection object
  - Add connection to ConnectionTable
  - Return connection or null on failure
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1_

- [x] 7. Implement sendUdpThroughSocks5() method



  - Encapsulate UDP packet with SOCKS5 header
  - Create DatagramPacket with encapsulated data
  - Set destination to relay endpoint (BND.ADDR:BND.PORT)
  - Send datagram through relay socket
  - Update connection statistics (bytesSent)
  - Update lastActivityAt timestamp
  - Handle IOException gracefully
  - _Requirements: 3.5, 5.1, 9.1, 9.4_

- [ ] 8. Implement startUdpReader() coroutine

  - Launch coroutine in IO dispatcher
  - Create receive buffer (max UDP size: 65507 bytes)
  - Loop: receive datagram from relay socket with timeout
  - Decapsulate received packet to extract source and payload
  - Build IP+UDP response packet with swapped addresses
  - Write response packet to TUN interface
  - Update connection statistics (bytesReceived)
  - Handle timeout and IOException
  - Clean up connection on error or cancellation
  - _Requirements: 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 9.2_

- [ ] 9. Implement handleGenericUdpPacket() method

  - Create ConnectionKey from packet 5-tuple
  - Check if UDP ASSOCIATE connection exists for this key
  - If exists: reuse connection, send packet through it
  - If not exists: establish new UDP ASSOCIATE connection
  - If establishment fails: log error and drop packet
  - Call sendUdpThroughSocks5() to forward packet
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.5_

- [ ] 9.1 Write property test for connection reuse
  - **Property 4: Connection Reuse Consistency**
  - **Validates: Requirements 6.1, 6.2, 6.3, 6.4**
  - Generate sequence of packets to same destination
  - Verify only one connection is created
  - Verify all packets use the same connection

- [ ] 10. Update handleUdpPacket() to route non-DNS traffic

  - Modify existing handleUdpPacket() method
  - Keep existing DNS handling (isDnsQuery check)
  - Add else branch to call handleGenericUdpPacket() for non-DNS
  - Remove TODO comment about UDP ASSOCIATE
  - _Requirements: 1.1, 8.1, 8.2_

- [ ] 11. Enhance connection cleanup for UDP ASSOCIATE
  - Update ConnectionTable.cleanupIdleConnections()
  - Iterate through UDP ASSOCIATE connections
  - Check lastActivityAt against idle timeout (2 minutes)
  - For idle connections: cancel reader job, close sockets, remove from table
  - Log connection closure with duration and statistics
  - _Requirements: 2.2, 2.3, 7.4, 9.4_

- [ ] 11.1 Write property test for connection cleanup
  - **Property 6: Connection Cleanup Completeness**
  - **Validates: Requirements 2.2, 2.3, 5.4, 7.4**
  - Create connections with old lastActivityAt timestamps
  - Run cleanup
  - Verify idle connections are removed
  - Verify active connections are preserved

- [ ] 12. Add error handling and resilience
  - Wrap all UDP ASSOCIATE operations in try-catch
  - Log specific error messages for each SOCKS5 error code
  - Ensure errors in one connection don't affect others
  - Add timeout handling for relay socket operations
  - Validate all parsed data before use
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 11.4_

- [ ] 12.1 Write property test for error isolation
  - **Property 7: Error Isolation**
  - **Validates: Requirements 7.1, 7.2, 7.3, 7.5**
  - Create multiple UDP ASSOCIATE connections
  - Simulate failure in one connection
  - Verify other connections continue working normally

- [ ] 13. Update statistics tracking
  - Ensure bytesSent is updated on each send
  - Ensure bytesReceived is updated on each receive
  - Include UDP ASSOCIATE connections in getStatistics()
  - Add separate counters for UDP ASSOCIATE vs regular UDP
  - Log connection statistics on cleanup
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [ ] 13.1 Write property test for statistics accuracy
  - **Property 8: Statistics Accuracy**
  - **Validates: Requirements 9.1, 9.2, 9.3, 9.4**
  - Send known number of bytes through connection
  - Verify bytesSent counter matches
  - Receive known number of bytes
  - Verify bytesReceived counter matches

- [ ] 14. Add comprehensive logging
  - Log UDP ASSOCIATE connection establishment (INFO level)
  - Log relay endpoint details (DEBUG level)
  - Log packet encapsulation/decapsulation (VERBOSE level)
  - Log connection reuse (VERBOSE level)
  - Log errors with context (ERROR level)
  - Log connection cleanup with statistics (INFO level)
  - Never log payload data (privacy)
  - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [ ] 15. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 16. Write integration tests
  - Create mock SOCKS5 server with UDP ASSOCIATE support
  - Test end-to-end UDP packet flow
  - Test bidirectional communication
  - Test connection cleanup
  - Test error scenarios
  - _Requirements: 12.3, 12.4, 12.5_

- [ ] 16.1 Write property test for bidirectional communication
  - **Property 5: Bidirectional Communication Preservation**
  - **Validates: Requirements 4.3, 4.4, 4.5, 8.3**
  - Send UDP packet through SOCKS5
  - Simulate response from destination
  - Verify response is routed back to original source IP and port

- [ ] 16.2 Write property test for WebRTC port handling
  - **Property 9: WebRTC Port Handling**
  - **Validates: Requirements 8.1, 8.2, 10.3**
  - Generate packets to WebRTC ports (3478-3479, 49152-65535)
  - Verify packets are routed through SOCKS5 UDP ASSOCIATE
  - Measure latency to ensure it's within acceptable range

- [ ] 16.3 Write property test for concurrent connections
  - **Property 10: Concurrent Connection Independence**
  - **Validates: Requirements 8.4, 10.2**
  - Create multiple simultaneous UDP ASSOCIATE connections
  - Perform operations on each concurrently
  - Verify operations don't interfere with each other

- [ ] 17. Write unit tests for helper methods
  - Test SOCKS5 message construction
  - Test address encoding (IPv4)
  - Test port encoding (big-endian)
  - Test header parsing
  - Test error code mapping
  - _Requirements: 12.1, 12.2_

- [ ] 18. Performance testing and optimization
  - Measure latency for UDP packet forwarding
  - Verify latency is under 10ms (excluding network)
  - Test with multiple concurrent connections
  - Verify memory usage per connection is under 10KB
  - Profile hot paths and optimize if needed
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [ ] 19. Update documentation
  - Add code comments for all new methods
  - Document SOCKS5 UDP ASSOCIATE protocol in comments
  - Update README with video calling support
  - Add troubleshooting guide for UDP issues
  - Document SOCKS5 server requirements
  - _Requirements: 11.4, 11.5_

- [ ] 20. Final checkpoint - Manual testing
  - Test with WhatsApp voice/video call
  - Test with Telegram voice/video call
  - Test with Zoom meeting
  - Test with Discord voice channel
  - Verify all work correctly through VPN
  - Monitor logs for any errors
  - Check connection statistics
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_
