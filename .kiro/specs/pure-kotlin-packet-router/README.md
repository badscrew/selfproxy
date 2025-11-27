# Pure Kotlin Packet Router

## Overview

This specification defines a **pure Kotlin implementation** of a packet router that enables the SSH Tunnel Proxy app to route device traffic through a SOCKS5 proxy without relying on native libraries like tun2socks.

## Why Pure Kotlin?

**Advantages over native libraries:**
- ✅ **Easier debugging**: No JNI boundary, full Kotlin stack traces
- ✅ **Better maintainability**: Single language, familiar tooling
- ✅ **Cross-platform potential**: Core logic can be shared with iOS via Kotlin Multiplatform
- ✅ **Simpler build**: No native compilation or architecture-specific binaries
- ✅ **More control**: Full visibility into packet routing logic

**Trade-offs:**
- ⚠️ Potentially lower performance than optimized native code
- ⚠️ More complex implementation (we handle all packet parsing)
- ⚠️ Larger codebase to maintain

## What It Does

The packet router sits between Android's TUN interface and the SOCKS5 proxy:

```
Device Apps → TUN Interface → Packet Router → SOCKS5 Proxy → SSH Tunnel → Internet
```

**Key responsibilities:**
1. Read raw IP packets from TUN interface
2. Parse IP/TCP/UDP headers
3. Manage TCP connection state machines
4. Perform SOCKS5 handshake for each connection
5. Forward data bidirectionally
6. Construct response packets with correct checksums
7. Route DNS queries through the tunnel
8. Clean up idle connections

## Architecture

### Components

1. **IPPacketParser**: Parses IP headers, extracts protocol info
2. **TCPHandler**: Manages TCP connections, state machine, SOCKS5 handshake
3. **UDPHandler**: Routes UDP datagrams, special DNS handling
4. **PacketBuilder**: Constructs IP/TCP/UDP packets with correct checksums
5. **ConnectionTable**: Tracks active connections, manages timeouts
6. **PacketRouter**: Main coordinator, reads from TUN, dispatches to handlers

### Data Flow

**TCP Connection:**
```
1. Client sends SYN → TUN
2. Router parses packet, extracts dest IP:port
3. Router establishes SOCKS5 connection
4. Router sends SYN-ACK back to TUN
5. Client sends data → TUN → Router → SOCKS5 → SSH → Internet
6. Internet → SSH → SOCKS5 → Router → TUN → Client
```

**DNS Query:**
```
1. Client sends UDP DNS query → TUN
2. Router detects port 53 (DNS)
3. Router converts to DNS-over-TCP
4. Router sends through SOCKS5
5. Router receives DNS response
6. Router constructs UDP response packet
7. Router writes to TUN → Client
```

## Implementation Plan

### Phase 1: Core Infrastructure (Tasks 1-4)
- IP packet parsing
- Packet building with checksums
- Connection data structures
- Connection table

### Phase 2: TCP Handler (Tasks 5-10)
- TCP packet parsing
- SOCKS5 handshake
- Connection establishment
- Data forwarding
- Connection termination
- State machine

### Phase 3: UDP Handler (Tasks 11-13)
- UDP packet parsing
- DNS query routing
- UDP response construction

### Phase 4: Integration (Tasks 14-17)
- Refactor PacketRouter to use new components
- Main packet routing loop
- Connection cleanup
- Statistics tracking

### Phase 5: Logging & Debugging (Tasks 18-19)
- Comprehensive logging
- Error handling

### Phase 6: Testing (Tasks 20-23)
- Integration testing
- Performance testing
- Memory leak testing
- Manual testing

### Phase 7: Documentation (Tasks 24-26)
- Code documentation
- User documentation
- Final checkpoint

## Key Technical Challenges

### 1. TCP Sequence Numbers
TCP requires tracking sequence and acknowledgment numbers for each connection. We implement a simplified tracker that:
- Starts with random initial sequence number
- Increments by payload length
- Tracks acknowledgments from both sides

### 2. Checksum Calculation
IP, TCP, and UDP all require checksums using the Internet Checksum algorithm (RFC 1071):
- Sum all 16-bit words
- Fold 32-bit sum to 16 bits
- Take one's complement
- TCP/UDP include pseudo-header in calculation

### 3. DNS-over-TCP
DNS queries are typically UDP, but SOCKS5 UDP support is complex. We route DNS through TCP:
- Detect UDP packets to port 53
- Establish TCP connection to DNS server via SOCKS5
- Send DNS query with 2-byte length prefix (DNS-over-TCP format)
- Receive response and convert back to UDP packet

### 4. Connection Management
Must track potentially hundreds of concurrent connections:
- Use ConcurrentHashMap for thread-safe access
- Implement idle timeout (2 minutes)
- Periodic cleanup coroutine
- Proper resource cleanup on close

## Testing Strategy

### Unit Tests
- Test each component in isolation
- Mock dependencies
- Test edge cases and error conditions

### Integration Tests
- Test end-to-end packet flow
- Use mock SOCKS5 server
- Verify packet construction and parsing

### Property-Based Tests
- Use Kotest for property testing
- Test invariants (e.g., checksum symmetry)
- Test with random inputs

### Manual Tests
- Real-world usage scenarios
- HTTP/HTTPS browsing
- DNS resolution
- Large downloads
- Network switching

## Performance Targets

- **Throughput**: ≥10 Mbps on mid-range devices
- **Latency**: <10ms overhead for packet processing
- **Memory**: <50MB for connection table and buffers
- **CPU**: <10% on idle connections

## Current Status

✅ **Specification Complete**
- Requirements defined (15 requirements)
- Architecture designed
- Tasks planned (30 tasks)

⏳ **Implementation**: Not started
- Ready to begin Phase 1

## Next Steps

1. **Start with Phase 1**: Implement core infrastructure
   - Begin with Task 1: IP packet parsing
   - Write tests as you go
   
2. **Iterate and Test**: Build incrementally
   - Complete each phase before moving to next
   - Run tests frequently
   
3. **Integration**: Replace stub PacketRouter
   - Swap in new implementation
   - Test with real SSH tunnel

## Files

- `requirements.md`: Detailed requirements with acceptance criteria
- `design.md`: Architecture, interfaces, algorithms, workflows
- `tasks.md`: 30 implementation tasks across 7 phases
- `README.md`: This overview document

## Questions?

If you have questions about the spec or implementation approach, feel free to ask!
