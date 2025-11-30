# SOCKS5 UDP ASSOCIATE Protocol Documentation

This document describes the SOCKS5 UDP ASSOCIATE protocol implementation in SSH Tunnel Proxy.

## Overview

SOCKS5 UDP ASSOCIATE (RFC 1928) enables UDP traffic to be relayed through a SOCKS5 proxy server. Unlike TCP connections which use a single SOCKS5 CONNECT command, UDP requires:

1. **TCP Control Connection**: Maintains the UDP association
2. **UDP Relay Socket**: Sends and receives encapsulated datagrams
3. **Relay Endpoint**: Server-provided address/port for UDP relay

## Protocol Flow

### 1. Establish TCP Control Connection

```
Client                                    SOCKS5 Server
  |                                              |
  |--- TCP Connect to proxy port 1080 --------->|
  |                                              |
  |<-- TCP Connection Established --------------|
```

### 2. SOCKS5 Greeting

```
Client                                    SOCKS5 Server
  |                                              |
  |--- [VER=5, NMETHODS=1, METHOD=0] ---------->|
  |    (Version 5, 1 method, No authentication) |
  |                                              |
  |<-- [VER=5, METHOD=0] -----------------------|
  |    (Version 5, No authentication accepted)  |
```

**Client Request:**
```
+-----+----------+----------+
| VER | NMETHODS | METHODS  |
+-----+----------+----------+
|  1  |    1     | 1 to 255 |
+-----+----------+----------+
```

- VER: 0x05 (SOCKS version 5)
- NMETHODS: 0x01 (1 authentication method)
- METHODS: 0x00 (No authentication required)

**Server Response:**
```
+-----+--------+
| VER | METHOD |
+-----+--------+
|  1  |   1    |
+-----+--------+
```

- VER: 0x05 (SOCKS version 5)
- METHOD: 0x00 (No authentication)

### 3. UDP ASSOCIATE Request

```
Client                                    SOCKS5 Server
  |                                              |
  |--- [VER=5, CMD=3, RSV=0, ATYP=1, --------->|
  |     DST.ADDR=0.0.0.0, DST.PORT=0]          |
  |    (UDP ASSOCIATE request)                  |
  |                                              |
  |<-- [VER=5, REP=0, RSV=0, ATYP=1, ----------|
  |     BND.ADDR=x.x.x.x, BND.PORT=yyyy]       |
  |    (Success, relay endpoint provided)       |
```

**Client Request:**
```
+-----+-----+-------+------+----------+----------+
| VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT |
+-----+-----+-------+------+----------+----------+
|  1  |  1  | X'00' |  1   | Variable |    2     |
+-----+-----+-------+------+----------+----------+
```

- VER: 0x05 (SOCKS version 5)
- CMD: 0x03 (UDP ASSOCIATE)
- RSV: 0x00 (Reserved, must be zero)
- ATYP: 0x01 (IPv4), 0x03 (Domain), 0x04 (IPv6)
- DST.ADDR: Client's address (typically 0.0.0.0 for "any")
- DST.PORT: Client's port (typically 0 for "any")

**Server Response:**
```
+-----+-----+-------+------+----------+----------+
| VER | REP |  RSV  | ATYP | BND.ADDR | BND.PORT |
+-----+-----+-------+------+----------+----------+
|  1  |  1  | X'00' |  1   | Variable |    2     |
+-----+-----+-------+------+----------+----------+
```

- VER: 0x05 (SOCKS version 5)
- REP: Reply code (0x00 = success, see error codes below)
- RSV: 0x00 (Reserved)
- ATYP: Address type of BND.ADDR
- BND.ADDR: **Relay endpoint address** (where to send UDP datagrams)
- BND.PORT: **Relay endpoint port** (where to send UDP datagrams)

### 4. Send UDP Datagrams

```
Client                                    SOCKS5 Server
  |                                              |
  |--- UDP Datagram to BND.ADDR:BND.PORT ------>|
  |    [RSV=0, FRAG=0, ATYP=1,                  |
  |     DST.ADDR=dest, DST.PORT=port, DATA]     |
  |                                              |
  |                                              |--- Forward to destination
  |                                              |
  |<-- UDP Datagram from BND.ADDR:BND.PORT -----|
  |    [RSV=0, FRAG=0, ATYP=1,                  |
  |     SRC.ADDR=dest, SRC.PORT=port, DATA]     |
  |                                              |<-- Response from destination
```

**UDP Request Header (prepended to each datagram):**
```
+----+------+------+----------+----------+----------+
|RSV | FRAG | ATYP | DST.ADDR | DST.PORT |   DATA   |
+----+------+------+----------+----------+----------+
| 2  |  1   |  1   | Variable |    2     | Variable |
+----+------+------+----------+----------+----------+
```

- RSV: 0x0000 (Reserved, must be zero)
- FRAG: 0x00 (Fragment number, 0 = no fragmentation)
- ATYP: 0x01 (IPv4), 0x03 (Domain), 0x04 (IPv6)
- DST.ADDR: Destination address (4 bytes for IPv4, 16 for IPv6)
- DST.PORT: Destination port (2 bytes, big-endian)
- DATA: Original UDP payload

**UDP Response Header (prepended to each response):**
```
+----+------+------+----------+----------+----------+
|RSV | FRAG | ATYP | SRC.ADDR | SRC.PORT |   DATA   |
+----+------+------+----------+----------+----------+
| 2  |  1   |  1   | Variable |    2     | Variable |
+----+------+------+----------+----------+----------+
```

- RSV: 0x0000 (Reserved)
- FRAG: 0x00 (Fragment number)
- ATYP: Address type of SRC.ADDR
- SRC.ADDR: Source address (where response came from)
- SRC.PORT: Source port (where response came from)
- DATA: UDP payload from destination

### 5. Maintain Association

The TCP control connection MUST remain open for the lifetime of the UDP association. If the TCP connection is closed, the server will terminate the UDP relay.

```
Client                                    SOCKS5 Server
  |                                              |
  |=== TCP Control Connection (kept alive) =====|
  |                                              |
  |--- UDP Datagram 1 ------------------------->|
  |<-- UDP Response 1 --------------------------|
  |                                              |
  |--- UDP Datagram 2 ------------------------->|
  |<-- UDP Response 2 --------------------------|
  |                                              |
  |... (continue sending/receiving) ...         |
  |                                              |
  |--- TCP Close (terminates association) ----->|
```

## Address Types (ATYP)

| Value | Type | Address Format |
|-------|------|----------------|
| 0x01 | IPv4 | 4 bytes (e.g., 192.168.1.1) |
| 0x03 | Domain | 1 byte length + domain name |
| 0x04 | IPv6 | 16 bytes |

### IPv4 Example

```
ATYP = 0x01
Address = 192.168.1.100
Bytes: 0x01 0xC0 0xA8 0x01 0x64
```

### Domain Example

```
ATYP = 0x03
Domain = "example.com"
Length = 11
Bytes: 0x03 0x0B 'e' 'x' 'a' 'm' 'p' 'l' 'e' '.' 'c' 'o' 'm'
```

### IPv6 Example

```
ATYP = 0x04
Address = 2001:0db8:85a3:0000:0000:8a2e:0370:7334
Bytes: 0x04 0x20 0x01 0x0d 0xb8 0x85 0xa3 0x00 0x00 
       0x00 0x00 0x8a 0x2e 0x03 0x70 0x73 0x34
```

## Reply Codes (REP)

| Code | Meaning | Description |
|------|---------|-------------|
| 0x00 | Success | Request granted |
| 0x01 | General failure | General SOCKS server failure |
| 0x02 | Not allowed | Connection not allowed by ruleset |
| 0x03 | Network unreachable | Network unreachable |
| 0x04 | Host unreachable | Host unreachable |
| 0x05 | Connection refused | Connection refused by destination |
| 0x06 | TTL expired | TTL expired |
| 0x07 | Command not supported | **Command not supported (no UDP ASSOCIATE)** |
| 0x08 | Address type not supported | Address type not supported |

## Complete Example: WhatsApp Call

### Scenario
User makes a WhatsApp voice call to 192.0.2.100:3478 (STUN server)

### Step 1: Establish Control Connection

```
Client -> Server: TCP SYN to 127.0.0.1:1080
Server -> Client: TCP SYN-ACK
Client -> Server: TCP ACK
```

### Step 2: SOCKS5 Greeting

```
Client -> Server:
  0x05 0x01 0x00
  (VER=5, NMETHODS=1, METHOD=0)

Server -> Client:
  0x05 0x00
  (VER=5, METHOD=0)
```

### Step 3: UDP ASSOCIATE Request

```
Client -> Server:
  0x05 0x03 0x00 0x01 0x00 0x00 0x00 0x00 0x00 0x00
  (VER=5, CMD=3, RSV=0, ATYP=1, ADDR=0.0.0.0, PORT=0)

Server -> Client:
  0x05 0x00 0x00 0x01 0x7F 0x00 0x00 0x01 0x04 0xD2
  (VER=5, REP=0, RSV=0, ATYP=1, ADDR=127.0.0.1, PORT=1234)
```

Relay endpoint: 127.0.0.1:1234

### Step 4: Send STUN Binding Request

```
Client -> 127.0.0.1:1234 (UDP):
  Header:
    0x00 0x00           # RSV
    0x00                # FRAG
    0x01                # ATYP (IPv4)
    0xC0 0x00 0x02 0x64 # DST.ADDR (192.0.2.100)
    0x0D 0x96           # DST.PORT (3478)
  Payload:
    [STUN Binding Request data...]

Server -> 192.0.2.100:3478 (UDP):
  [STUN Binding Request data...]
  (SOCKS5 header removed, forwarded to destination)
```

### Step 5: Receive STUN Binding Response

```
192.0.2.100:3478 -> Server (UDP):
  [STUN Binding Response data...]

Server -> Client at 127.0.0.1:1234 (UDP):
  Header:
    0x00 0x00           # RSV
    0x00                # FRAG
    0x01                # ATYP (IPv4)
    0xC0 0x00 0x02 0x64 # SRC.ADDR (192.0.2.100)
    0x0D 0x96           # SRC.PORT (3478)
  Payload:
    [STUN Binding Response data...]
```

### Step 6: Continue Call

```
Client <-> Server: Multiple UDP datagrams for RTP media
                   (all encapsulated with SOCKS5 headers)

TCP Control Connection: Remains open throughout call
```

### Step 7: End Call

```
Client -> Server: TCP FIN (close control connection)
Server: Terminates UDP association
```

## Implementation Notes

### Fragmentation

Most SOCKS5 servers do NOT support fragmentation (FRAG != 0). Always set FRAG=0x00.

### Maximum Datagram Size

- Maximum UDP datagram: 65,507 bytes (65,535 - 20 IP header - 8 UDP header)
- SOCKS5 header overhead: 10 bytes (IPv4), 22 bytes (IPv6)
- Effective payload: ~65,497 bytes (IPv4)

### Connection Reuse

Multiple UDP flows to the same destination can reuse the same UDP ASSOCIATE connection:

```
Flow 1: 10.0.0.2:5000 -> 192.0.2.100:3478
Flow 2: 10.0.0.2:5001 -> 192.0.2.100:3478

Both can use the same UDP ASSOCIATE connection
```

### Idle Timeout

Connections idle for > 2 minutes are automatically cleaned up:
- Cancel reader coroutine
- Close UDP relay socket
- Close TCP control socket
- Remove from connection table

### Error Handling

- Malformed packets: Log and drop
- Socket errors: Close connection and cleanup
- Timeout: Continue (UDP is best-effort)
- One connection failure: Don't affect others

## Security Considerations

### Privacy

- Never log UDP payload data
- Only log metadata (sizes, addresses, ports)
- All traffic encrypted by SSH tunnel

### Resource Limits

- Maximum concurrent UDP ASSOCIATE connections: 100
- Maximum packet size: 65,507 bytes
- Idle timeout: 2 minutes
- Memory per connection: < 10KB

### Firewall Traversal

UDP ASSOCIATE works through NAT/firewalls because:
1. Outgoing UDP packets establish NAT mapping
2. Responses use same 5-tuple (reverse direction)
3. STUN/TURN handle complex NAT scenarios

## Testing

### Test UDP ASSOCIATE Support

```bash
# Method 1: Using netcat
ssh -D 1080 user@server
nc -u -x localhost:1080 8.8.8.8 53

# Method 2: Using curl with SOCKS5
curl --socks5 localhost:1080 https://dnsleaktest.com

# Method 3: Using dig with SOCKS5 proxy
dig @8.8.8.8 example.com
```

### Packet Capture

```bash
# Capture SOCKS5 traffic
sudo tcpdump -i any -n port 1080 -w socks5.pcap

# Analyze with Wireshark
wireshark socks5.pcap

# Filter for UDP ASSOCIATE
# Look for CMD=0x03 in SOCKS5 handshake
```

### Verify Encapsulation

```python
# Python script to verify SOCKS5 UDP header
import socket

# Read captured packet
with open('udp_packet.bin', 'rb') as f:
    packet = f.read()

# Parse SOCKS5 UDP header
rsv = int.from_bytes(packet[0:2], 'big')
frag = packet[2]
atyp = packet[3]

print(f"RSV: 0x{rsv:04x} (should be 0x0000)")
print(f"FRAG: 0x{frag:02x} (should be 0x00)")
print(f"ATYP: 0x{atyp:02x} (0x01=IPv4, 0x03=Domain, 0x04=IPv6)")

if atyp == 0x01:  # IPv4
    addr = '.'.join(str(b) for b in packet[4:8])
    port = int.from_bytes(packet[8:10], 'big')
    payload = packet[10:]
    print(f"Destination: {addr}:{port}")
    print(f"Payload size: {len(payload)} bytes")
```

## References

- [RFC 1928: SOCKS Protocol Version 5](https://www.rfc-editor.org/rfc/rfc1928)
- [RFC 1929: Username/Password Authentication for SOCKS V5](https://www.rfc-editor.org/rfc/rfc1929)
- [RFC 5389: Session Traversal Utilities for NAT (STUN)](https://www.rfc-editor.org/rfc/rfc5389)
- [WebRTC Specification](https://www.w3.org/TR/webrtc/)

## Appendix: Packet Formats

### Complete UDP ASSOCIATE Request

```
Offset  Bytes  Field        Value       Description
------  -----  -----------  ----------  ---------------------------
0       1      VER          0x05        SOCKS version 5
1       1      CMD          0x03        UDP ASSOCIATE command
2       1      RSV          0x00        Reserved
3       1      ATYP         0x01        IPv4 address type
4-7     4      DST.ADDR     0x00000000  Client address (0.0.0.0)
8-9     2      DST.PORT     0x0000      Client port (0)
```

### Complete UDP ASSOCIATE Response

```
Offset  Bytes  Field        Value       Description
------  -----  -----------  ----------  ---------------------------
0       1      VER          0x05        SOCKS version 5
1       1      REP          0x00        Success
2       1      RSV          0x00        Reserved
3       1      ATYP         0x01        IPv4 address type
4-7     4      BND.ADDR     varies      Relay endpoint address
8-9     2      BND.PORT     varies      Relay endpoint port
```

### Complete UDP Datagram (Encapsulated)

```
Offset  Bytes  Field        Value       Description
------  -----  -----------  ----------  ---------------------------
0-1     2      RSV          0x0000      Reserved
2       1      FRAG         0x00        No fragmentation
3       1      ATYP         0x01        IPv4 address type
4-7     4      DST.ADDR     varies      Destination address
8-9     2      DST.PORT     varies      Destination port
10+     var    DATA         varies      Original UDP payload
```
