# Design Document: Pure Kotlin Packet Router

## Overview

The Pure Kotlin Packet Router is a critical networking component that enables transparent traffic routing through a SOCKS5 proxy without native libraries. It operates at the IP layer, intercepting packets from Android's TUN interface, parsing protocol headers, managing connection state, and reconstructing response packets.

This design prioritizes correctness, maintainability, and debuggability over raw performance. By implementing the router in pure Kotlin, we gain:
- **Easier debugging**: No JNI boundary, full Kotlin stack traces
- **Better maintainability**: Single language, familiar tooling
- **Cross-platform potential**: Core logic can be shared with iOS (Kotlin Multiplatform)
- **Simpler build**: No native library compilation or architecture-specific binaries

The router handles TCP and UDP protocols, with special handling for DNS queries. It maintains a connection table for active sessions, implements proper TCP state machines, and ensures correct packet checksums.

## Architecture

### High-Level Design

```
┌─────────────────┐
│  TUN Interface  │ (Android VPN)
└────────┬────────┘
         │ Raw IP Packets
         ▼
┌─────────────────────────────────────────┐
│         Packet Router                    │
│  ┌────────────────────────────────────┐ │
│  │  Packet Reader (Coroutine)         │ │
│  │  - Reads from TUN FileDescriptor   │ │
│  │  - Dispatches to protocol handlers │ │
│  └────────────────────────────────────┘ │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  TCP Handler                       │ │
│  │  - TCP state machine               │ │
│  │  - Connection table                │ │
│  │  - SOCKS5 handshake                │ │
│  └────────────────────────────────────┘ │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  UDP Handler                       │ │
│  │  - DNS query routing               │ │
│  │  - UDP datagram forwarding         │ │
│  └────────────────────────────────────┘ │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  Packet Builder                    │ │
│  │  - Constructs IP/TCP/UDP packets   │ │
│  │  - Calculates checksums            │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
         │ SOCKS5 Protocol
         ▼
┌─────────────────┐
│  SOCKS5 Proxy   │ (SSH Tunnel)
└─────────────────┘
```


### Component Architecture

The router is composed of several specialized components:

1. **PacketRouter** (Main Coordinator)
   - Manages lifecycle (start/stop)
   - Reads packets from TUN interface
   - Dispatches to protocol handlers
   - Manages connection table

2. **IPPacketParser**
   - Parses IP headers (version, protocol, addresses)
   - Validates packet structure
   - Extracts protocol-specific data

3. **TCPHandler**
   - Manages TCP connections and state machines
   - Performs SOCKS5 handshake
   - Forwards TCP data bidirectionally
   - Handles connection lifecycle

4. **UDPHandler**
   - Routes UDP datagrams through SOCKS5
   - Special handling for DNS queries
   - Manages UDP connection mapping

5. **PacketBuilder**
   - Constructs IP/TCP/UDP packets
   - Calculates checksums (IP, TCP, UDP)
   - Handles packet fragmentation

6. **ConnectionTable**
   - Tracks active TCP/UDP connections
   - Provides connection lookup by 5-tuple
   - Manages connection timeouts
   - Collects statistics

## Data Structures

### Connection Key

```kotlin
data class ConnectionKey(
    val protocol: Protocol,
    val sourceIp: String,
    val sourcePort: Int,
    val destIp: String,
    val destPort: Int
)
```

### TCP Connection State

```kotlin
data class TcpConnection(
    val key: ConnectionKey,
    val socksSocket: Socket,
    val state: TcpState,
    val sequenceNumber: Long,
    val acknowledgmentNumber: Long,
    val createdAt: Long,
    val lastActivityAt: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val readerJob: Job
)

enum class TcpState {
    CLOSED,
    SYN_SENT,
    ESTABLISHED,
    FIN_WAIT_1,
    FIN_WAIT_2,
    CLOSING,
    TIME_WAIT
}
```

### UDP Connection State

```kotlin
data class UdpConnection(
    val key: ConnectionKey,
    val socksSocket: Socket?,
    val createdAt: Long,
    val lastActivityAt: Long,
    val bytesSent: Long,
    val bytesReceived: Long
)
```


### Packet Structures

#### IP Packet Header (IPv4)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|Version|  IHL  |Type of Service|          Total Length         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         Identification        |Flags|      Fragment Offset    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Time to Live |    Protocol   |         Header Checksum       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Source Address                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Destination Address                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

#### TCP Packet Header

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Sequence Number                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Acknowledgment Number                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Data |           |U|A|P|R|S|F|                               |
| Offset| Reserved  |R|C|S|S|Y|I|            Window             |
|       |           |G|K|H|T|N|N|                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           Checksum            |         Urgent Pointer        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

#### UDP Packet Header

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            Length             |           Checksum            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Component Interfaces

### PacketRouter (Main Component)

```kotlin
class PacketRouter(
    private val tunInputStream: FileInputStream,
    private val tunOutputStream: FileOutputStream,
    private val socksPort: Int,
    private val logger: Logger
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionTable = ConnectionTable()
    private val tcpHandler = TcpHandler(socksPort, connectionTable, logger)
    private val udpHandler = UdpHandler(socksPort, connectionTable, logger)
    private val packetBuilder = PacketBuilder()
    
    fun start()
    fun stop()
    fun getStatistics(): RouterStatistics
    
    private suspend fun routePackets()
    private suspend fun processPacket(packet: ByteArray)
}
```

### IPPacketParser

```kotlin
object IPPacketParser {
    fun parseIPv4Header(packet: ByteArray): IPv4Header?
    fun extractProtocol(packet: ByteArray): Protocol
    fun extractSourceIP(packet: ByteArray): String
    fun extractDestIP(packet: ByteArray): String
    fun getHeaderLength(packet: ByteArray): Int
    fun validateChecksum(packet: ByteArray): Boolean
}

data class IPv4Header(
    val version: Int,
    val headerLength: Int,
    val totalLength: Int,
    val identification: Int,
    val flags: Int,
    val fragmentOffset: Int,
    val ttl: Int,
    val protocol: Protocol,
    val checksum: Int,
    val sourceIP: String,
    val destIP: String
)

enum class Protocol(val value: Int) {
    TCP(6),
    UDP(17),
    ICMP(1),
    UNKNOWN(-1)
}
```


### TCPHandler

```kotlin
class TcpHandler(
    private val socksPort: Int,
    private val connectionTable: ConnectionTable,
    private val logger: Logger
) {
    suspend fun handleTcpPacket(
        packet: ByteArray,
        ipHeader: IPv4Header,
        tunOutputStream: FileOutputStream
    )
    
    private suspend fun parseTcpHeader(packet: ByteArray, offset: Int): TcpHeader
    private suspend fun handleSyn(key: ConnectionKey, tcpHeader: TcpHeader)
    private suspend fun handleData(key: ConnectionKey, payload: ByteArray)
    private suspend fun handleFin(key: ConnectionKey)
    private suspend fun handleRst(key: ConnectionKey)
    private suspend fun establishSocks5Connection(key: ConnectionKey): Socket?
    private suspend fun performSocks5Handshake(socket: Socket, destIp: String, destPort: Int): Boolean
    private suspend fun startConnectionReader(connection: TcpConnection, tunOutputStream: FileOutputStream)
    private suspend fun sendTcpPacket(
        tunOutputStream: FileOutputStream,
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        flags: Int,
        seqNum: Long,
        ackNum: Long,
        payload: ByteArray = byteArrayOf()
    )
}

data class TcpHeader(
    val sourcePort: Int,
    val destPort: Int,
    val sequenceNumber: Long,
    val acknowledgmentNumber: Long,
    val dataOffset: Int,
    val flags: TcpFlags,
    val windowSize: Int,
    val checksum: Int,
    val urgentPointer: Int
)

data class TcpFlags(
    val fin: Boolean,
    val syn: Boolean,
    val rst: Boolean,
    val psh: Boolean,
    val ack: Boolean,
    val urg: Boolean
) {
    fun toByte(): Int {
        var flags = 0
        if (fin) flags = flags or 0x01
        if (syn) flags = flags or 0x02
        if (rst) flags = flags or 0x04
        if (psh) flags = flags or 0x08
        if (ack) flags = flags or 0x10
        if (urg) flags = flags or 0x20
        return flags
    }
    
    companion object {
        fun fromByte(byte: Int): TcpFlags {
            return TcpFlags(
                fin = (byte and 0x01) != 0,
                syn = (byte and 0x02) != 0,
                rst = (byte and 0x04) != 0,
                psh = (byte and 0x08) != 0,
                ack = (byte and 0x10) != 0,
                urg = (byte and 0x20) != 0
            )
        }
    }
}
```

### UDPHandler

```kotlin
class UdpHandler(
    private val socksPort: Int,
    private val connectionTable: ConnectionTable,
    private val logger: Logger
) {
    suspend fun handleUdpPacket(
        packet: ByteArray,
        ipHeader: IPv4Header,
        tunOutputStream: FileOutputStream
    )
    
    private suspend fun parseUdpHeader(packet: ByteArray, offset: Int): UdpHeader
    private suspend fun handleDnsQuery(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        dnsPayload: ByteArray,
        tunOutputStream: FileOutputStream
    )
    private suspend fun queryDnsThroughSocks5(
        dnsServer: String,
        dnsPort: Int,
        query: ByteArray
    ): ByteArray?
    private suspend fun sendUdpPacket(
        tunOutputStream: FileOutputStream,
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        payload: ByteArray
    )
}

data class UdpHeader(
    val sourcePort: Int,
    val destPort: Int,
    val length: Int,
    val checksum: Int
)
```


### PacketBuilder

```kotlin
class PacketBuilder {
    fun buildIPv4Packet(
        sourceIp: String,
        destIp: String,
        protocol: Protocol,
        payload: ByteArray,
        identification: Int = Random.nextInt(65536),
        ttl: Int = 64
    ): ByteArray
    
    fun buildTcpPacket(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        sequenceNumber: Long,
        acknowledgmentNumber: Long,
        flags: TcpFlags,
        windowSize: Int = 65535,
        payload: ByteArray = byteArrayOf()
    ): ByteArray
    
    fun buildUdpPacket(
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        payload: ByteArray
    ): ByteArray
    
    fun calculateIPChecksum(header: ByteArray): Int
    fun calculateTcpChecksum(
        sourceIp: String,
        destIp: String,
        tcpSegment: ByteArray
    ): Int
    fun calculateUdpChecksum(
        sourceIp: String,
        destIp: String,
        udpDatagram: ByteArray
    ): Int
    
    private fun calculateChecksum(data: ByteArray, offset: Int = 0, length: Int = data.size): Int
}
```

### ConnectionTable

```kotlin
class ConnectionTable {
    private val tcpConnections = ConcurrentHashMap<ConnectionKey, TcpConnection>()
    private val udpConnections = ConcurrentHashMap<ConnectionKey, UdpConnection>()
    private val lock = Mutex()
    
    suspend fun addTcpConnection(connection: TcpConnection)
    suspend fun getTcpConnection(key: ConnectionKey): TcpConnection?
    suspend fun removeTcpConnection(key: ConnectionKey): TcpConnection?
    suspend fun getAllTcpConnections(): List<TcpConnection>
    
    suspend fun addUdpConnection(connection: UdpConnection)
    suspend fun getUdpConnection(key: ConnectionKey): UdpConnection?
    suspend fun removeUdpConnection(key: ConnectionKey): UdpConnection?
    
    suspend fun cleanupIdleConnections(idleTimeoutMs: Long = 120_000)
    suspend fun closeAllConnections()
    
    fun getStatistics(): ConnectionStatistics
}

data class ConnectionStatistics(
    val totalTcpConnections: Int,
    val activeTcpConnections: Int,
    val totalUdpConnections: Int,
    val activeUdpConnections: Int,
    val totalBytesSent: Long,
    val totalBytesReceived: Long
)

// RouterStatistics is an alias for ConnectionStatistics
typealias RouterStatistics = ConnectionStatistics
```

## Detailed Workflows

### TCP Connection Establishment Flow

```
Client (TUN)          PacketRouter          SOCKS5 Proxy          Remote Server
    |                      |                      |                      |
    |--SYN--------------->|                      |                      |
    |                      |--TCP Connect-------->|                      |
    |                      |                      |--TCP Connect-------->|
    |                      |                      |<-----SYN-ACK---------|
    |                      |<-----Connected-------|                      |
    |<----SYN-ACK----------|                      |                      |
    |--ACK--------------->|                      |                      |
    |                      |                      |                      |
    |--Data-------------->|--Data--------------->|--Data--------------->|
    |                      |                      |                      |
    |<----Data------------|<-----Data------------|<-----Data------------|
    |                      |                      |                      |
```

### DNS Query Flow

```
Client (TUN)          PacketRouter          SOCKS5 Proxy          DNS Server
    |                      |                      |                      |
    |--UDP DNS Query----->|                      |                      |
    |  (port 53)           |                      |                      |
    |                      |--TCP Connect-------->|                      |
    |                      |--SOCKS5 Handshake--->|                      |
    |                      |--DNS Query (TCP)---->|--DNS Query (TCP)---->|
    |                      |                      |                      |
    |                      |<-----DNS Response----|<-----DNS Response----|
    |<----UDP DNS Response-|                      |                      |
    |                      |--TCP Close---------->|                      |
    |                      |                      |                      |
```


## Implementation Details

### TCP State Machine

The TCP handler implements a simplified TCP state machine:

1. **CLOSED**: No connection exists
2. **SYN_SENT**: SYN packet received, SOCKS5 connection being established
3. **ESTABLISHED**: Connection active, data can flow
4. **FIN_WAIT_1**: FIN sent, waiting for ACK
5. **FIN_WAIT_2**: FIN ACK received, waiting for remote FIN
6. **CLOSING**: Both sides closing simultaneously
7. **TIME_WAIT**: Connection closed, waiting for delayed packets

**Simplified Implementation**: For MVP, we'll implement a minimal state machine:
- CLOSED → SYN_SENT (on SYN)
- SYN_SENT → ESTABLISHED (on SOCKS5 success)
- ESTABLISHED → CLOSED (on FIN/RST)

Full TCP state machine can be added post-MVP if needed.

### Sequence Number Management

TCP requires tracking sequence and acknowledgment numbers:

```kotlin
class SequenceNumberTracker {
    private var nextSeqNum: Long = Random.nextLong(0, 0xFFFFFFFF)
    private var nextAckNum: Long = 0
    
    fun getNextSeq(): Long = nextSeqNum
    fun getNextAck(): Long = nextAckNum
    
    fun advanceSeq(bytes: Int) {
        nextSeqNum = (nextSeqNum + bytes) and 0xFFFFFFFF
    }
    
    fun updateAck(ackNum: Long) {
        nextAckNum = ackNum
    }
}
```

**Simplified Implementation**: For MVP, we'll use simplified sequence tracking:
- Initial SEQ: Random 32-bit number
- Increment SEQ by payload length
- ACK = received SEQ + received payload length

### Checksum Calculation

IP, TCP, and UDP checksums use the Internet Checksum algorithm (RFC 1071):

```kotlin
private fun calculateChecksum(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
    var sum = 0L
    var i = offset
    
    // Sum all 16-bit words
    while (i < offset + length - 1) {
        val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
        sum += word
        i += 2
    }
    
    // Add remaining byte if odd length
    if (i < offset + length) {
        sum += (data[i].toInt() and 0xFF) shl 8
    }
    
    // Fold 32-bit sum to 16 bits
    while (sum shr 16 != 0L) {
        sum = (sum and 0xFFFF) + (sum shr 16)
    }
    
    // One's complement
    return (sum.inv() and 0xFFFF).toInt()
}
```

**TCP Checksum Pseudo-Header**:
```
+--------+--------+--------+--------+
|           Source Address          |
+--------+--------+--------+--------+
|         Destination Address       |
+--------+--------+--------+--------+
|  zero  |  PTCL  |    TCP Length   |
+--------+--------+--------+--------+
```

### SOCKS5 Protocol Implementation

SOCKS5 handshake for TCP connections:

```kotlin
private suspend fun performSocks5Handshake(
    socket: Socket,
    destIp: String,
    destPort: Int
): Boolean = withContext(Dispatchers.IO) {
    try {
        val output = socket.getOutputStream()
        val input = socket.getInputStream()
        
        // 1. Send greeting: [VER, NMETHODS, METHODS]
        output.write(byteArrayOf(0x05, 0x01, 0x00)) // Version 5, 1 method, No auth
        output.flush()
        
        // 2. Read method selection: [VER, METHOD]
        val greeting = ByteArray(2)
        if (input.read(greeting) != 2 || greeting[0] != 0x05.toByte()) {
            logger.error("SOCKS5 greeting failed")
            return@withContext false
        }
        
        // 3. Send connect request: [VER, CMD, RSV, ATYP, DST.ADDR, DST.PORT]
        val ipBytes = InetAddress.getByName(destIp).address
        val portBytes = ByteBuffer.allocate(2).putShort(destPort.toShort()).array()
        
        val request = byteArrayOf(
            0x05,  // Version
            0x01,  // CMD: CONNECT
            0x00,  // Reserved
            0x01   // ATYP: IPv4
        ) + ipBytes + portBytes
        
        output.write(request)
        output.flush()
        
        // 4. Read response: [VER, REP, RSV, ATYP, BND.ADDR, BND.PORT]
        val response = ByteArray(10)
        val read = input.read(response)
        if (read < 10 || response[0] != 0x05.toByte() || response[1] != 0x00.toByte()) {
            logger.error("SOCKS5 connect failed: reply=${response[1]}")
            return@withContext false
        }
        
        logger.debug("SOCKS5 handshake successful: $destIp:$destPort")
        true
    } catch (e: Exception) {
        logger.error("SOCKS5 handshake error: ${e.message}", e)
        false
    }
}
```


### DNS-over-TCP Implementation

DNS queries are typically UDP, but we route them through TCP for reliability:

```kotlin
private suspend fun queryDnsThroughSocks5(
    dnsServer: String,
    dnsPort: Int,
    query: ByteArray
): ByteArray? = withContext(Dispatchers.IO) {
    var socket: Socket? = null
    try {
        // Connect to DNS server through SOCKS5
        socket = Socket()
        socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5000)
        socket.soTimeout = 5000
        
        if (!performSocks5Handshake(socket, dnsServer, dnsPort)) {
            return@withContext null
        }
        
        val output = socket.getOutputStream()
        val input = socket.getInputStream()
        
        // DNS-over-TCP format: 2-byte length + DNS query
        val queryLength = ByteBuffer.allocate(2).putShort(query.size.toShort()).array()
        output.write(queryLength)
        output.write(query)
        output.flush()
        
        // Read response length
        val responseLengthBytes = ByteArray(2)
        if (input.read(responseLengthBytes) != 2) {
            return@withContext null
        }
        
        val responseLength = ByteBuffer.wrap(responseLengthBytes).short.toInt() and 0xFFFF
        if (responseLength <= 0 || responseLength > 4096) {
            return@withContext null
        }
        
        // Read response
        val response = ByteArray(responseLength)
        var totalRead = 0
        while (totalRead < responseLength) {
            val read = input.read(response, totalRead, responseLength - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        
        if (totalRead == responseLength) response else null
    } catch (e: Exception) {
        logger.error("DNS query failed: ${e.message}", e)
        null
    } finally {
        socket?.close()
    }
}
```

### Connection Cleanup

Idle connections must be cleaned up to prevent resource exhaustion:

```kotlin
private fun startConnectionCleanup() {
    scope.launch {
        while (isActive) {
            delay(30_000) // Check every 30 seconds
            
            val now = System.currentTimeMillis()
            val idleTimeout = 120_000L // 2 minutes
            
            connectionTable.getAllTcpConnections().forEach { connection ->
                if (now - connection.lastActivityAt > idleTimeout) {
                    logger.debug("Closing idle TCP connection: ${connection.key}")
                    closeTcpConnection(connection.key)
                }
            }
        }
    }
}
```

## Error Handling Strategy

### Packet Parsing Errors

```kotlin
private suspend fun processPacket(packet: ByteArray) {
    try {
        val ipHeader = IPPacketParser.parseIPv4Header(packet)
        if (ipHeader == null) {
            logger.verbose("Invalid IP packet, dropping")
            return
        }
        
        when (ipHeader.protocol) {
            Protocol.TCP -> tcpHandler.handleTcpPacket(packet, ipHeader, tunOutputStream)
            Protocol.UDP -> udpHandler.handleUdpPacket(packet, ipHeader, tunOutputStream)
            else -> logger.verbose("Unsupported protocol: ${ipHeader.protocol}")
        }
    } catch (e: Exception) {
        logger.error("Error processing packet: ${e.message}", e)
        // Continue processing next packet
    }
}
```

### SOCKS5 Connection Errors

```kotlin
private suspend fun establishSocks5Connection(key: ConnectionKey): Socket? {
    return try {
        val socket = Socket()
        socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5000)
        
        if (performSocks5Handshake(socket, key.destIp, key.destPort)) {
            socket
        } else {
            socket.close()
            sendTcpRst(key)
            null
        }
    } catch (e: Exception) {
        logger.error("Failed to establish SOCKS5 connection: ${e.message}", e)
        sendTcpRst(key)
        null
    }
}
```

### TUN Interface Errors

```kotlin
private suspend fun routePackets() {
    val buffer = ByteArray(MAX_PACKET_SIZE)
    
    withContext(Dispatchers.IO) {
        try {
            while (isActive) {
                try {
                    val length = tunInputStream.read(buffer)
                    if (length > 0) {
                        val packet = buffer.copyOf(length)
                        launch { processPacket(packet) }
                    } else if (length < 0) {
                        logger.error("TUN interface closed")
                        break
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        logger.error("Error reading from TUN: ${e.message}", e)
                        delay(100) // Brief pause before retry
                    }
                }
            }
        } finally {
            logger.info("Packet routing stopped")
        }
    }
}
```


## Performance Considerations

### Memory Management

**Connection Table Size Limits**:
- Maximum 1000 concurrent TCP connections
- Maximum 500 concurrent UDP connections
- Automatic cleanup of idle connections (2 minute timeout)

**Buffer Sizes**:
- Packet read buffer: 32KB (max IP packet size)
- Per-connection buffers: 8KB for reading from SOCKS5
- Total estimated memory: ~50MB for 1000 connections

### Concurrency Model

**Coroutine Usage**:
- Main packet reader: Single coroutine reading from TUN
- Packet processing: Launch new coroutine per packet (lightweight)
- Connection readers: One coroutine per TCP connection reading from SOCKS5
- Connection cleanup: Single periodic coroutine

**Thread Pool**:
- Use Dispatchers.IO for all I/O operations
- Avoid blocking the main thread
- Use structured concurrency for lifecycle management

### Throughput Optimization

**Packet Batching** (Future Enhancement):
- Read multiple packets before processing
- Batch writes to TUN interface
- Reduce context switching overhead

**Zero-Copy** (Future Enhancement):
- Use ByteBuffer for packet manipulation
- Minimize array copying
- Direct buffer access where possible

### Latency Optimization

**Fast Path for Established Connections**:
```kotlin
// Quick lookup for existing connections
val connection = connectionTable.getTcpConnection(key)
if (connection != null && connection.state == TcpState.ESTABLISHED) {
    // Fast path: just forward data
    forwardData(connection, payload)
    return
}
```

**Inline Small Packets**:
- Process small packets inline instead of launching coroutine
- Reduces overhead for ACK packets and small data transfers

## Testing Strategy

### Unit Tests

Test individual components in isolation:

```kotlin
class IPPacketParserTest {
    @Test
    fun `parse valid IPv4 header`() {
        val packet = buildTestIPv4Packet()
        val header = IPPacketParser.parseIPv4Header(packet)
        
        assertNotNull(header)
        assertEquals(4, header.version)
        assertEquals(Protocol.TCP, header.protocol)
    }
    
    @Test
    fun `reject invalid IP version`() {
        val packet = buildTestIPv6Packet()
        val header = IPPacketParser.parseIPv4Header(packet)
        
        assertNull(header)
    }
}

class PacketBuilderTest {
    @Test
    fun `build TCP SYN packet with correct checksum`() {
        val packet = PacketBuilder().buildTcpPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80,
            sequenceNumber = 1000,
            acknowledgmentNumber = 0,
            flags = TcpFlags(syn = true, ack = false, fin = false, rst = false, psh = false, urg = false)
        )
        
        // Verify packet structure
        assertTrue(packet.size >= 40) // IP + TCP headers
        
        // Verify checksum is non-zero
        val checksumOffset = 20 + 16 // IP header + TCP checksum offset
        val checksum = ((packet[checksumOffset].toInt() and 0xFF) shl 8) or 
                       (packet[checksumOffset + 1].toInt() and 0xFF)
        assertNotEquals(0, checksum)
    }
}
```

### Integration Tests

Test packet routing end-to-end:

```kotlin
class PacketRouterIntegrationTest {
    private lateinit var mockTunInput: PipedInputStream
    private lateinit var mockTunOutput: PipedOutputStream
    private lateinit var mockSocksServer: MockSocksServer
    private lateinit var router: PacketRouter
    
    @Before
    fun setup() {
        mockTunInput = PipedInputStream()
        mockTunOutput = PipedOutputStream()
        mockSocksServer = MockSocksServer(port = 1080)
        mockSocksServer.start()
        
        router = PacketRouter(
            tunInputStream = FileInputStream(mockTunInput.fd),
            tunOutputStream = FileOutputStream(mockTunOutput.fd),
            socksPort = 1080,
            logger = TestLogger()
        )
    }
    
    @Test
    fun `route TCP connection through SOCKS5`() = runTest {
        router.start()
        
        // Send TCP SYN packet
        val synPacket = buildTcpSynPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 12345,
            destIp = "1.1.1.1",
            destPort = 80
        )
        mockTunInput.write(synPacket)
        
        // Wait for SOCKS5 connection
        delay(100)
        
        // Verify SOCKS5 server received connection
        assertTrue(mockSocksServer.hasConnection("1.1.1.1", 80))
        
        // Verify SYN-ACK sent back to TUN
        val response = mockTunOutput.readPacket()
        assertNotNull(response)
        assertTrue(isSynAckPacket(response))
    }
    
    @Test
    fun `route DNS query through SOCKS5`() = runTest {
        router.start()
        
        // Send DNS query packet
        val dnsQuery = buildDnsQueryPacket(
            sourceIp = "10.0.0.2",
            sourcePort = 54321,
            destIp = "8.8.8.8",
            destPort = 53,
            domain = "example.com"
        )
        mockTunInput.write(dnsQuery)
        
        // Mock DNS response
        mockSocksServer.respondToDns("example.com", "93.184.216.34")
        
        // Wait for response
        delay(100)
        
        // Verify DNS response sent back to TUN
        val response = mockTunOutput.readPacket()
        assertNotNull(response)
        assertTrue(isDnsResponsePacket(response))
    }
}
```


### Property-Based Tests

Use Kotest for property-based testing:

```kotlin
class PacketRouterPropertiesTest {
    
    @Test
    fun `checksum calculation is symmetric`() = runTest {
        // Property: Calculating checksum twice should return original data validity
        checkAll(100, Arb.byteArray(Arb.int(20..1500))) { data ->
            val checksum1 = PacketBuilder().calculateChecksum(data)
            val checksum2 = PacketBuilder().calculateChecksum(data)
            checksum1 shouldBe checksum2
        }
    }
    
    @Test
    fun `IP packet round-trip preserves data`() = runTest {
        // Property: Building and parsing IP packet should preserve data
        checkAll(
            100,
            Arb.ipAddress(),
            Arb.ipAddress(),
            Arb.protocol(),
            Arb.byteArray(Arb.int(0..1400))
        ) { sourceIp, destIp, protocol, payload ->
            val packet = PacketBuilder().buildIPv4Packet(
                sourceIp = sourceIp,
                destIp = destIp,
                protocol = protocol,
                payload = payload
            )
            
            val parsed = IPPacketParser.parseIPv4Header(packet)
            parsed shouldNotBe null
            parsed!!.sourceIP shouldBe sourceIp
            parsed.destIP shouldBe destIp
            parsed.protocol shouldBe protocol
        }
    }
    
    @Test
    fun `TCP connection establishment always creates connection table entry`() = runTest {
        // Property: Every successful TCP SYN should create a connection
        checkAll(
            100,
            Arb.ipAddress(),
            Arb.port(),
            Arb.ipAddress(),
            Arb.port()
        ) { sourceIp, sourcePort, destIp, destPort ->
            val connectionTable = ConnectionTable()
            val key = ConnectionKey(
                protocol = Protocol.TCP,
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                destIp = destIp,
                destPort = destPort
            )
            
            // Simulate connection establishment
            val connection = TcpConnection(
                key = key,
                socksSocket = mockSocket(),
                state = TcpState.ESTABLISHED,
                sequenceNumber = 1000,
                acknowledgmentNumber = 2000,
                createdAt = System.currentTimeMillis(),
                lastActivityAt = System.currentTimeMillis(),
                bytesSent = 0,
                bytesReceived = 0,
                readerJob = Job()
            )
            
            connectionTable.addTcpConnection(connection)
            
            val retrieved = connectionTable.getTcpConnection(key)
            retrieved shouldNotBe null
            retrieved!!.key shouldBe key
        }
    }
}

// Custom Arb generators
fun Arb.Companion.ipAddress() = arbitrary {
    val octets = List(4) { Arb.int(0..255).bind() }
    octets.joinToString(".")
}

fun Arb.Companion.port() = Arb.int(1024..65535)

fun Arb.Companion.protocol() = Arb.enum<Protocol>()
```

### Manual Testing Checklist

- [ ] HTTP browsing works (port 80)
- [ ] HTTPS browsing works (port 443)
- [ ] DNS resolution works
- [ ] Large file downloads work
- [ ] Streaming video works
- [ ] WebSocket connections work
- [ ] Multiple simultaneous connections work
- [ ] Connection survives network switch (WiFi ↔ Mobile)
- [ ] No memory leaks after extended use
- [ ] CPU usage remains reasonable

## Security Considerations

### Packet Validation

- Validate all packet headers before processing
- Reject malformed packets
- Prevent buffer overflows with size checks
- Validate IP addresses and ports

### Resource Limits

- Maximum connection table size (1000 TCP, 500 UDP)
- Maximum packet size (32KB)
- Connection timeout (2 minutes idle)
- DNS query timeout (5 seconds)

### Privacy

- Never log packet payload data
- Sanitize logs to remove sensitive information
- Don't store packet contents longer than necessary

## Future Enhancements

### IPv6 Support

- Parse IPv6 headers (40 bytes)
- Use SOCKS5 ATYP_IPV6 (0x04)
- Calculate IPv6 checksums
- Handle IPv6 extension headers

### SOCKS5 UDP ASSOCIATE

- Support UDP forwarding for non-DNS traffic
- Implement SOCKS5 UDP encapsulation
- Handle UDP fragmentation

### Performance Optimizations

- Packet batching for reduced overhead
- Zero-copy buffer management
- Connection pooling for DNS queries
- Fast path for established connections

### Advanced Features

- ICMP support (ping)
- Packet fragmentation and reassembly
- TCP window scaling
- Selective acknowledgment (SACK)
- Congestion control awareness

## References

- RFC 791: Internet Protocol (IP)
- RFC 793: Transmission Control Protocol (TCP)
- RFC 768: User Datagram Protocol (UDP)
- RFC 1071: Computing the Internet Checksum
- RFC 1928: SOCKS Protocol Version 5
- RFC 1035: Domain Names - Implementation and Specification
