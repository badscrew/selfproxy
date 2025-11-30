# Design Document: SOCKS5 UDP ASSOCIATE Implementation

## Overview

This design document specifies the implementation of SOCKS5 UDP ASSOCIATE functionality to enable UDP-based applications (video calling, voice chat, gaming) to work through the SSH Tunnel Proxy VPN. The implementation extends the existing UDPHandler to support full UDP traffic routing, not just DNS queries.

### Key Design Decisions

1. **Separate UDP Relay Sockets**: Each UDP ASSOCIATE connection uses a dedicated DatagramSocket for the relay
2. **Asynchronous Processing**: UDP reader coroutines handle responses without blocking the main packet router
3. **Connection Pooling**: Reuse existing UDP ASSOCIATE connections for the same destination to minimize overhead
4. **SOCKS5 UDP Header Encapsulation**: All UDP datagrams are wrapped with SOCKS5 headers before transmission
5. **Graceful Degradation**: If UDP ASSOCIATE fails, log the error but continue processing other traffic

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      TUN Interface                          │
│                    (VPN Interface)                          │
└────────────────────┬────────────────────────────────────────┘
                     │ UDP Packets
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    PacketRouter                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              IPPacketParser                          │  │
│  │  - Parse IP headers                                  │  │
│  │  - Identify protocol (TCP/UDP/ICMP)                 │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────┘
                     │ UDP Packets
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    UDPHandler                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  parseUdpHeader()                                    │  │
│  │  extractUdpPayload()                                 │  │
│  │  handleUdpPacket() ──┐                              │  │
│  └──────────────────────┼───────────────────────────────┘  │
│                         │                                   │
│         ┌───────────────┴────────────────┐                 │
│         │                                │                 │
│         ▼                                ▼                 │
│  ┌─────────────┐                ┌──────────────────┐      │
│  │ handleDns   │                │ handleGenericUdp │      │
│  │ Query()     │                │ Packet()         │      │
│  │ (existing)  │                │ (NEW)            │      │
│  └─────────────┘                └──────────────────┘      │
│                                          │                 │
│                                          ▼                 │
│                          ┌───────────────────────────┐    │
│                          │ establishUdpAssociate()   │    │
│                          │ - SOCKS5 handshake        │    │
│                          │ - Get relay endpoint      │    │
│                          └───────────────────────────┘    │
│                                          │                 │
│                                          ▼                 │
│                          ┌───────────────────────────┐    │
│                          │ sendUdpThroughSocks5()    │    │
│                          │ - Encapsulate packet      │    │
│                          │ - Send to relay           │    │
│                          └───────────────────────────┘    │
│                                          │                 │
│                                          ▼                 │
│                          ┌───────────────────────────┐    │
│                          │ startUdpReader()          │    │
│                          │ - Coroutine per connection│    │
│                          │ - Read from relay socket  │    │
│                          │ - Write to TUN            │    │
│                          └───────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                                          │
                                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  ConnectionTable                            │
│  - Track UDP ASSOCIATE connections                          │
│  - Manage lifecycle and cleanup                             │
│  - Collect statistics                                       │
└─────────────────────────────────────────────────────────────┘
                                          │
                                          ▼
┌─────────────────────────────────────────────────────────────┐
│              SOCKS5 Proxy (SSH Server)                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  TCP Control Connection                              │  │
│  │  - Receives UDP ASSOCIATE command                    │  │
│  │  - Returns relay endpoint (BND.ADDR:BND.PORT)       │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  UDP Relay Socket                                    │  │
│  │  - Receives encapsulated datagrams                   │  │
│  │  - Forwards to destination                           │  │
│  │  - Returns responses                                 │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                                          │
                                          ▼
                                  Internet/Destination
```

## Components and Interfaces

### 1. UDPHandler (Enhanced)

**Existing Methods:**
- `handleUdpPacket()` - Entry point for all UDP packets
- `parseUdpHeader()` - Parse UDP header from IP packet
- `extractUdpPayload()` - Extract UDP payload
- `isDnsQuery()` - Check if packet is DNS
- `handleDnsQuery()` - Handle DNS via TCP-over-SOCKS5

**New Methods:**

```kotlin
/**
 * Handles non-DNS UDP packets by routing through SOCKS5 UDP ASSOCIATE.
 */
private suspend fun handleGenericUdpPacket(
    sourceIp: String,
    sourcePort: Int,
    destIp: String,
    destPort: Int,
    payload: ByteArray,
    tunOutputStream: FileOutputStream
)

/**
 * Establishes a SOCKS5 UDP ASSOCIATE connection.
 * Returns the UDP relay endpoint (address and port) or null on failure.
 */
private suspend fun establishUdpAssociate(
    key: ConnectionKey
): UdpRelayEndpoint?

/**
 * Performs SOCKS5 UDP ASSOCIATE handshake on the control connection.
 */
private suspend fun performUdpAssociateHandshake(
    controlSocket: Socket,
    clientAddress: String = "0.0.0.0",
    clientPort: Int = 0
): UdpRelayEndpoint?

/**
 * Encapsulates a UDP datagram with SOCKS5 UDP header.
 */
private fun encapsulateUdpPacket(
    destIp: String,
    destPort: Int,
    payload: ByteArray
): ByteArray

/**
 * Parses SOCKS5 UDP response header and extracts payload.
 */
private fun decapsulateUdpPacket(
    socks5Packet: ByteArray
): UdpDecapsulatedPacket?

/**
 * Sends a UDP datagram through the SOCKS5 relay.
 */
private suspend fun sendUdpThroughSocks5(
    connection: UdpAssociateConnection,
    destIp: String,
    destPort: Int,
    payload: ByteArray
)

/**
 * Starts a coroutine to read responses from the UDP relay socket.
 */
private fun startUdpReader(
    key: ConnectionKey,
    connection: UdpAssociateConnection,
    tunOutputStream: FileOutputStream
): Job
```

### 2. New Data Classes

```kotlin
/**
 * Represents a SOCKS5 UDP ASSOCIATE connection.
 */
data class UdpAssociateConnection(
    val key: ConnectionKey,
    val controlSocket: Socket,          // TCP connection for SOCKS5 control
    val relaySocket: DatagramSocket,    // UDP socket for relay communication
    val relayEndpoint: UdpRelayEndpoint, // Where to send encapsulated datagrams
    val createdAt: Long,
    val lastActivityAt: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val readerJob: Job                  // Coroutine reading from relay socket
)

/**
 * SOCKS5 UDP relay endpoint (BND.ADDR and BND.PORT from server response).
 */
data class UdpRelayEndpoint(
    val address: String,
    val port: Int
)

/**
 * Decapsulated UDP packet from SOCKS5 response.
 */
data class UdpDecapsulatedPacket(
    val sourceIp: String,
    val sourcePort: Int,
    val payload: ByteArray
)
```

### 3. ConnectionTable (Enhanced)

**New Methods:**

```kotlin
/**
 * Add a UDP ASSOCIATE connection to the table.
 */
suspend fun addUdpAssociateConnection(connection: UdpAssociateConnection)

/**
 * Retrieve a UDP ASSOCIATE connection by its key.
 */
suspend fun getUdpAssociateConnection(key: ConnectionKey): UdpAssociateConnection?

/**
 * Remove a UDP ASSOCIATE connection from the table.
 */
suspend fun removeUdpAssociateConnection(key: ConnectionKey): UdpAssociateConnection?

/**
 * Update UDP ASSOCIATE connection statistics.
 */
suspend fun updateUdpAssociateStats(
    key: ConnectionKey,
    bytesSent: Long = 0,
    bytesReceived: Long = 0
)
```

## Data Models

### SOCKS5 UDP ASSOCIATE Protocol

#### UDP ASSOCIATE Request (sent on TCP control connection)

```
+----+-----+-------+------+----------+----------+
|VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT |
+----+-----+-------+------+----------+----------+
| 1  |  1  | X'00' |  1   | Variable |    2     |
+----+-----+-------+------+----------+----------+
```

- VER: 0x05 (SOCKS5)
- CMD: 0x03 (UDP ASSOCIATE)
- RSV: 0x00 (reserved)
- ATYP: 0x01 (IPv4), 0x03 (Domain), 0x04 (IPv6)
- DST.ADDR: Client's address (usually 0.0.0.0 for "any")
- DST.PORT: Client's port (usually 0 for "any")

#### UDP ASSOCIATE Response (received on TCP control connection)

```
+----+-----+-------+------+----------+----------+
|VER | REP |  RSV  | ATYP | BND.ADDR | BND.PORT |
+----+-----+-------+------+----------+----------+
| 1  |  1  | X'00' |  1   | Variable |    2     |
+----+-----+-------+------+----------+----------+
```

- VER: 0x05
- REP: 0x00 (success), or error code
- BND.ADDR: UDP relay server address
- BND.PORT: UDP relay server port

#### SOCKS5 UDP Request Header (prepended to each datagram)

```
+----+------+------+----------+----------+----------+
|RSV | FRAG | ATYP | DST.ADDR | DST.PORT |   DATA   |
+----+------+------+----------+----------+----------+
| 2  |  1   |  1   | Variable |    2     | Variable |
+----+------+------+----------+----------+----------+
```

- RSV: 0x0000 (reserved, must be zero)
- FRAG: 0x00 (fragment number, 0 = no fragmentation)
- ATYP: 0x01 (IPv4), 0x03 (Domain), 0x04 (IPv6)
- DST.ADDR: Destination address
- DST.PORT: Destination port
- DATA: UDP payload

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: UDP ASSOCIATE Handshake Correctness

*For any* UDP packet requiring SOCKS5 routing, establishing a UDP ASSOCIATE connection should result in a valid relay endpoint with non-zero port.

**Validates: Requirements 1.1, 1.2, 1.3, 1.4**

### Property 2: UDP Encapsulation Format Compliance

*For any* UDP datagram sent through SOCKS5, the encapsulated packet should have a valid SOCKS5 UDP header with RSV=0x0000, FRAG=0x00, and correct ATYP/DST.ADDR/DST.PORT fields.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 11.1, 11.2, 11.3**

### Property 3: UDP Decapsulation Correctness

*For any* SOCKS5 UDP response packet, decapsulation should correctly extract the source address, source port, and payload without data corruption.

**Validates: Requirements 4.1, 4.2, 4.3**

### Property 4: Connection Reuse Consistency

*For any* sequence of UDP packets to the same destination, if an active UDP ASSOCIATE connection exists, all packets should reuse that connection rather than creating new ones.

**Validates: Requirements 6.1, 6.2, 6.3, 6.4**

### Property 5: Bidirectional Communication Preservation

*For any* UDP packet sent through SOCKS5, if a response is received, the response should be correctly routed back to the original source IP and port.

**Validates: Requirements 4.3, 4.4, 4.5, 8.3**

### Property 6: Connection Cleanup Completeness

*For any* UDP ASSOCIATE connection that becomes idle, after the timeout period, the connection should be removed from the table and all associated resources (sockets, coroutines) should be cleaned up.

**Validates: Requirements 2.2, 2.3, 5.4, 7.4**

### Property 7: Error Isolation

*For any* UDP ASSOCIATE connection that fails, the failure should not affect other active UDP or TCP connections.

**Validates: Requirements 7.1, 7.2, 7.3, 7.5**

### Property 8: Statistics Accuracy

*For any* UDP ASSOCIATE connection, the sum of bytes sent across all transmitted packets should equal the bytesSent counter, and similarly for bytesReceived.

**Validates: Requirements 9.1, 9.2, 9.3, 9.4**

### Property 9: WebRTC Port Handling

*For any* UDP packet destined to common WebRTC ports (3478-3479, 49152-65535), the packet should be routed through SOCKS5 UDP ASSOCIATE with the same latency characteristics as other UDP traffic.

**Validates: Requirements 8.1, 8.2, 10.3**

### Property 10: Concurrent Connection Independence

*For any* two simultaneous UDP ASSOCIATE connections, operations on one connection (send, receive, close) should not interfere with operations on the other connection.

**Validates: Requirements 8.4, 10.2**

## Error Handling

### SOCKS5 Server Errors

| Error Code | Meaning | Handling Strategy |
|------------|---------|-------------------|
| 0x01 | General SOCKS server failure | Log error, drop packet, don't retry |
| 0x02 | Connection not allowed | Log error, drop packet, mark server as incompatible |
| 0x03 | Network unreachable | Log error, drop packet, may indicate routing issue |
| 0x04 | Host unreachable | Log error, drop packet, destination may be down |
| 0x05 | Connection refused | Log error, drop packet, destination port may be closed |
| 0x07 | Command not supported | Log error, mark server as not supporting UDP ASSOCIATE |
| 0x08 | Address type not supported | Log error, try alternative address type if available |

### Socket Errors

- **SocketTimeoutException**: Close connection, remove from table, log timeout
- **IOException on send**: Close connection, remove from table, log error
- **IOException on receive**: Close connection, remove from table, log error
- **BindException**: Log error, cannot establish relay socket, drop packet

### Malformed Packets

- **Invalid SOCKS5 header**: Log error, drop packet, continue processing
- **Truncated datagram**: Log error, drop packet, continue processing
- **Invalid address type**: Log error, drop packet, continue processing

## Testing Strategy

### Unit Tests

1. **SOCKS5 Message Construction**
   - Test UDP ASSOCIATE request message format
   - Test UDP encapsulation header construction
   - Test address type encoding (IPv4, IPv6, domain)

2. **SOCKS5 Message Parsing**
   - Test UDP ASSOCIATE response parsing
   - Test UDP decapsulation
   - Test error code handling

3. **Connection Management**
   - Test connection addition/removal from table
   - Test connection reuse logic
   - Test idle timeout cleanup

### Property-Based Tests

1. **Property 2: UDP Encapsulation Format Compliance**
   ```kotlin
   @Test
   fun `UDP encapsulation should produce valid SOCKS5 headers`() = runTest {
       checkAll(100, Arb.ipAddress(), Arb.port(), Arb.byteArray(0..1500)) { ip, port, payload ->
           val encapsulated = encapsulateUdpPacket(ip, port, payload)
           
           // Verify header structure
           encapsulated[0] shouldBe 0x00 // RSV high byte
           encapsulated[1] shouldBe 0x00 // RSV low byte
           encapsulated[2] shouldBe 0x00 // FRAG
           // ATYP should be valid (0x01, 0x03, or 0x04)
           encapsulated[3] in listOf(0x01, 0x03, 0x04) shouldBe true
           
           // Payload should be at the end
           val headerSize = calculateHeaderSize(encapsulated)
           encapsulated.copyOfRange(headerSize, encapsulated.size) shouldBe payload
       }
   }
   ```

2. **Property 3: UDP Decapsulation Correctness**
   ```kotlin
   @Test
   fun `UDP decapsulation should correctly extract source and payload`() = runTest {
       checkAll(100, Arb.ipAddress(), Arb.port(), Arb.byteArray(0..1500)) { ip, port, payload ->
           // Encapsulate then decapsulate
           val encapsulated = buildSocks5UdpPacket(ip, port, payload)
           val decapsulated = decapsulateUdpPacket(encapsulated)
           
           decapsulated shouldNotBe null
           decapsulated!!.sourceIp shouldBe ip
           decapsulated.sourcePort shouldBe port
           decapsulated.payload shouldBe payload
       }
   }
   ```

3. **Property 4: Connection Reuse Consistency**
   ```kotlin
   @Test
   fun `multiple packets to same destination should reuse connection`() = runTest {
       checkAll(100, Arb.connectionKey()) { key ->
           val connectionTable = ConnectionTable(testLogger)
           val handler = UDPHandler(socksPort, connectionTable, testLogger)
           
           // Send multiple packets
           repeat(5) {
               handler.handleGenericUdpPacket(
                   key.sourceIp, key.sourcePort,
                   key.destIp, key.destPort,
                   ByteArray(100), mockTunOutputStream
               )
           }
           
           // Should only have one connection
           val stats = connectionTable.getStatistics()
           stats.activeUdpConnections shouldBe 1
       }
   }
   ```

### Integration Tests

1. **End-to-End UDP Flow**
   - Set up mock SOCKS5 server with UDP ASSOCIATE support
   - Send UDP packet through VPN
   - Verify packet arrives at destination with correct encapsulation
   - Send response from destination
   - Verify response arrives at source

2. **WebRTC Simulation**
   - Simulate STUN binding request
   - Verify STUN response is correctly routed back
   - Test with multiple simultaneous "calls"

3. **Connection Cleanup**
   - Establish UDP ASSOCIATE connection
   - Wait for idle timeout
   - Verify connection is cleaned up
   - Verify resources are released

### Manual Testing

1. **WhatsApp Call Test**
   - Connect to VPN
   - Make WhatsApp voice call
   - Verify call connects and audio works
   - Monitor logs for UDP ASSOCIATE activity

2. **Zoom Meeting Test**
   - Connect to VPN
   - Join Zoom meeting
   - Verify video and audio work
   - Check connection statistics

3. **Discord Voice Test**
   - Connect to VPN
   - Join Discord voice channel
   - Verify voice communication works
   - Monitor UDP traffic

## Performance Considerations

### Latency Optimization

- **Async Processing**: All UDP operations use coroutines to avoid blocking
- **Direct Socket I/O**: Minimize data copying between buffers
- **Connection Pooling**: Reuse existing connections to avoid handshake overhead
- **Minimal Logging**: Use verbose logging only, avoid debug logs in hot path

### Memory Management

- **Fixed Buffer Sizes**: Use pre-allocated buffers for packet processing
- **Connection Limits**: Implement maximum concurrent UDP connections (default: 100)
- **Aggressive Cleanup**: Clean up idle connections every 30 seconds
- **Weak References**: Use weak references for connection tracking where appropriate

### Scalability

- **Per-Connection Coroutines**: Each UDP ASSOCIATE has its own reader coroutine
- **Non-Blocking I/O**: Use DatagramSocket with timeout for non-blocking reads
- **Concurrent Processing**: Multiple UDP packets can be processed simultaneously
- **Resource Pooling**: Reuse DatagramSocket instances where possible

## Security Considerations

### Privacy

- **No Payload Logging**: Never log UDP payload data (only sizes and metadata)
- **Encrypted Transport**: All UDP data is encrypted by SSH tunnel
- **No Data Retention**: Don't store UDP packet contents in memory longer than necessary

### Resource Limits

- **Max Connections**: Limit to 100 concurrent UDP ASSOCIATE connections
- **Max Packet Size**: Limit UDP packets to 65507 bytes (UDP maximum)
- **Rate Limiting**: Consider implementing per-connection rate limits if abuse detected

### Error Information

- **Sanitized Errors**: Don't expose internal IP addresses or sensitive data in error messages
- **Minimal Disclosure**: Log only necessary information for debugging

## Implementation Notes

### SOCKS5 Server Compatibility

- **OpenSSH**: Supports UDP ASSOCIATE via `ssh -D` (dynamic forwarding)
- **Dante**: Full SOCKS5 server with UDP support
- **Shadowsocks**: Supports UDP relay
- **Most SOCKS5 proxies**: May not support UDP ASSOCIATE (command 0x03)

### Known Limitations

1. **Fragmentation**: FRAG field is always 0x00 (no fragmentation support)
2. **IPv6**: Initial implementation focuses on IPv4, IPv6 can be added later
3. **Domain Names**: Initial implementation uses IP addresses, domain support can be added
4. **MTU**: UDP packets larger than MTU will be fragmented at IP layer

### Future Enhancements

1. **Connection Pooling**: Implement more sophisticated connection reuse strategies
2. **QoS**: Prioritize real-time traffic (VoIP) over bulk transfers
3. **Statistics Dashboard**: Expose UDP statistics in UI
4. **Per-App UDP Control**: Allow users to enable/disable UDP for specific apps

## Dependencies

### Existing Components

- **UDPHandler**: Extend with new methods
- **ConnectionTable**: Add UDP ASSOCIATE connection tracking
- **PacketBuilder**: Use for constructing response packets
- **Logger**: Use for logging UDP operations

### New Dependencies

- **DatagramSocket**: Java standard library (already available)
- **InetSocketAddress**: Java standard library (already available)
- **ByteBuffer**: Java standard library (already available)

### Testing Dependencies

- **Kotest**: Property-based testing (already in project)
- **MockK**: Mocking framework (already in project)
- **Coroutines Test**: Testing coroutines (already in project)

## Deployment Considerations

### Rollout Strategy

1. **Phase 1**: Implement core UDP ASSOCIATE functionality
2. **Phase 2**: Add comprehensive error handling
3. **Phase 3**: Optimize performance and add statistics
4. **Phase 4**: Test with real video calling apps
5. **Phase 5**: Release to users with documentation

### Monitoring

- Log UDP ASSOCIATE connection establishment
- Log connection failures with error codes
- Track UDP statistics (connections, bytes transferred)
- Monitor for unusual patterns (excessive connections, high error rates)

### Documentation

- Update user documentation to mention video calling support
- Add troubleshooting guide for UDP issues
- Document SOCKS5 server requirements
- Provide examples of compatible SOCKS5 servers

## References

- **RFC 1928**: SOCKS Protocol Version 5 (https://www.rfc-editor.org/rfc/rfc1928)
- **RFC 1929**: Username/Password Authentication for SOCKS V5
- **WebRTC Specification**: W3C WebRTC 1.0
- **STUN RFC 5389**: Session Traversal Utilities for NAT
- **OpenSSH Manual**: Dynamic port forwarding documentation
