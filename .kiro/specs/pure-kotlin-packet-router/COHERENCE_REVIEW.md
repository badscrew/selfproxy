# Coherence Review: Pure Kotlin Packet Router Specification

## Review Date
2025-11-27

## Review Scope
Cross-checking requirements.md, design.md, tasks.md, and README.md for:
- Consistency between documents
- Completeness of coverage
- Alignment of requirements → design → tasks
- Missing or conflicting information

---

## ✅ STRENGTHS

### 1. Requirements Coverage
All 15 requirements are well-defined with clear acceptance criteria. Requirements map cleanly to design components and implementation tasks.

### 2. Component Architecture
The component breakdown is consistent across all documents:
- IPPacketParser
- TCPHandler
- UDPHandler
- PacketBuilder
- ConnectionTable
- PacketRouter

### 3. Task Organization
30 tasks organized into 7 logical phases with clear dependencies and progression.

### 4. Testing Strategy
Comprehensive testing approach with unit tests, integration tests, and property-based tests defined in both design and tasks.

---

## ⚠️ ISSUES FOUND

### Issue 1: File Location Inconsistency
**Severity: MEDIUM**

**Problem:**
- Tasks.md specifies: `shared/src/androidMain/kotlin/com/sshtunnel/android/vpn/packet/`
- Design.md doesn't specify exact file locations
- Current PacketRouter is in: `androidApp/src/main/kotlin/com/sshtunnel/android/vpn/`

**Impact:**
- Confusion about where to place new components
- Mixing androidApp and shared module code

**Recommendation:**
Since this is Android-specific VPN code (not cross-platform business logic), it should stay in `androidApp` module, not `shared` module.

**Fix Required:**
Update Task 1 and Task 2 to use:
- `androidApp/src/main/kotlin/com/sshtunnel/android/vpn/packet/` (not shared)

---

### Issue 2: Missing SequenceNumberTracker Implementation
**Severity: LOW**

**Problem:**
- Design.md defines `SequenceNumberTracker` class (line 424-437)
- Tasks.md mentions creating it in Task 3
- But it's not included in the TcpConnection data class or TCPHandler interface

**Impact:**
- Unclear how sequence numbers are actually managed
- TcpConnection has sequenceNumber/acknowledgmentNumber fields but no tracker

**Recommendation:**
Clarify whether:
- A. SequenceNumberTracker is embedded in TcpConnection
- B. SequenceNumberTracker is used by TCPHandler
- C. Sequence numbers are tracked directly in TcpConnection fields

**Fix Required:**
Add clarification to design.md about SequenceNumberTracker usage pattern.

---

### Issue 3: ConnectionKey Protocol Enum Duplication
**Severity: LOW**

**Problem:**
- Design.md defines `ConnectionKey.Protocol` enum (TCP, UDP)
- Design.md also defines `Protocol` enum (TCP, UDP, ICMP, UNKNOWN)
- Two different Protocol enums with overlapping values

**Impact:**
- Potential confusion about which Protocol enum to use where
- ConnectionKey uses ConnectionKey.Protocol
- IPPacketParser uses Protocol

**Recommendation:**
Use a single Protocol enum with all values (TCP, UDP, ICMP, UNKNOWN) everywhere.

**Fix Required:**
- Remove ConnectionKey.Protocol nested enum
- Use the main Protocol enum in ConnectionKey
- Update design.md to show: `data class ConnectionKey(val protocol: Protocol, ...)`

---

### Issue 4: PacketBuilder Method Signature Inconsistency
**Severity: LOW**

**Problem:**
In design.md, PacketBuilder has:
```kotlin
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
```

But TCPHandler.sendTcpPacket has:
```kotlin
private suspend fun sendTcpPacket(
    tunOutputStream: FileOutputStream,
    sourceIp: String,
    sourcePort: Int,
    destIp: String,
    destPort: Int,
    flags: Int,  // ← Int, not TcpFlags
    seqNum: Long,
    ackNum: Long,
    payload: ByteArray = byteArrayOf()
)
```

**Impact:**
- Type mismatch: flags is Int in one place, TcpFlags in another
- Inconsistent parameter names (seqNum vs sequenceNumber)

**Recommendation:**
Standardize on TcpFlags type everywhere for type safety.

**Fix Required:**
Update TCPHandler.sendTcpPacket to use `flags: TcpFlags` instead of `flags: Int`.

---

### Issue 5: Missing RouterStatistics Definition
**Severity: LOW**

**Problem:**
- PacketRouter.getStatistics() returns `RouterStatistics`
- But only `ConnectionStatistics` is defined
- No `RouterStatistics` data class in design.md

**Impact:**
- Unclear what statistics the router exposes
- Ambiguity between ConnectionStatistics and RouterStatistics

**Recommendation:**
Either:
- A. Rename to use ConnectionStatistics consistently
- B. Define RouterStatistics as a wrapper around ConnectionStatistics

**Fix Required:**
Add RouterStatistics definition or clarify it's the same as ConnectionStatistics.

---

### Issue 6: Task 1 Path Specification Error
**Severity: HIGH**

**Problem:**
Task 1 says: "Create IPPacketParser object in shared/src/androidMain/kotlin/com/sshtunnel/android/vpn/packet/"

This path is WRONG because:
- `shared/src/androidMain` is for shared multiplatform code
- But the path includes `/android/` which is app-specific
- VPN packet routing is Android-specific, not shared logic

**Impact:**
- Will create files in wrong location
- Breaks Kotlin Multiplatform architecture
- Mixes concerns between shared and platform-specific code

**Recommendation:**
VPN packet routing should be in androidApp module since it's Android-specific.

**Fix Required:**
Change all task paths from:
- `shared/src/androidMain/kotlin/com/sshtunnel/android/vpn/`

To:
- `androidApp/src/main/kotlin/com/sshtunnel/android/vpn/`

---

### Issue 7: Missing Integration with Existing VPN Service
**Severity: MEDIUM**

**Problem:**
- Current TunnelVpnService.kt already exists and uses PacketRouter
- Tasks don't mention updating TunnelVpnService to use new components
- No task for removing old PacketRouter stub

**Impact:**
- Unclear how new implementation integrates with existing service
- Risk of leaving old code in place

**Recommendation:**
Add explicit integration tasks.

**Fix Required:**
Add task in Phase 4:
- "14.1 Update TunnelVpnService to use refactored PacketRouter"
- "14.2 Remove old PacketRouter stub implementation"

---

### Issue 8: Requirement 10 (Fragmentation) Not in MVP Tasks
**Severity: LOW**

**Problem:**
- Requirement 10 covers packet fragmentation (5 acceptance criteria)
- But fragmentation is listed as "Optional Enhancement" (Task 29)
- Inconsistency: is it required or optional?

**Impact:**
- Unclear if fragmentation is needed for MVP
- Requirements say it's required, tasks say it's optional

**Recommendation:**
Clarify scope: fragmentation is complex and likely not needed for MVP.

**Fix Required:**
Either:
- A. Mark Requirement 10 as "(Optional - Post-MVP)"
- B. Move Task 29 to Phase 6 as required

**Suggested:** Mark Requirement 10 as optional since most packets fit in MTU.

---

### Issue 9: Missing Error Handling in Design Workflows
**Severity: LOW**

**Problem:**
- Design.md shows happy-path workflows (TCP connection, DNS query)
- No error flow diagrams
- Requirements 11 covers error handling but no design workflow

**Impact:**
- Implementers may not understand error flow
- Missing guidance on error recovery

**Recommendation:**
Add error flow diagrams to design.md.

**Fix Required:**
Add section "Error Handling Workflows" with diagrams for:
- SOCKS5 connection failure
- DNS timeout
- TUN interface error

---

### Issue 10: Property Test Coverage Incomplete
**Severity: LOW**

**Problem:**
- Design.md defines 3 property tests
- Tasks.md only mentions 2 property tests (Task 2.2, Task 4.2)
- Missing property test for "TCP connection establishment always creates connection table entry"

**Impact:**
- Property test from design.md won't be implemented

**Recommendation:**
Ensure all property tests from design are in tasks.

**Fix Required:**
Task 4.2 already covers this, but verify the property description matches.

---

## 📊 COVERAGE ANALYSIS

### Requirements → Design Mapping

| Requirement | Design Component | Status |
|-------------|------------------|--------|
| Req 1 (Read packets) | PacketRouter.routePackets() | ✅ Covered |
| Req 2 (Parse TCP) | TCPHandler.parseTcpHeader() | ✅ Covered |
| Req 3 (TCP state) | TcpState enum, TcpConnection | ✅ Covered |
| Req 4 (SOCKS5) | TCPHandler.performSocks5Handshake() | ✅ Covered |
| Req 5 (TCP forward) | TCPHandler.handleData(), startConnectionReader() | ✅ Covered |
| Req 6 (Parse UDP) | UDPHandler.parseUdpHeader() | ✅ Covered |
| Req 7 (DNS) | UDPHandler.queryDnsThroughSocks5() | ✅ Covered |
| Req 8 (Checksums) | PacketBuilder.calculate*Checksum() | ✅ Covered |
| Req 9 (Resources) | ConnectionTable | ✅ Covered |
| Req 10 (Fragmentation) | PacketBuilder (mentioned) | ⚠️ Optional |
| Req 11 (Errors) | Error handling in all components | ✅ Covered |
| Req 12 (Logging) | Logger integration | ✅ Covered |
| Req 13 (Statistics) | ConnectionStatistics | ✅ Covered |
| Req 14 (UDP ASSOCIATE) | Not in MVP | ✅ Marked optional |
| Req 15 (IPv6) | Not in MVP | ✅ Marked optional |

### Design → Tasks Mapping

| Design Component | Tasks | Status |
|------------------|-------|--------|
| IPPacketParser | Task 1, 1.1 | ✅ Covered |
| PacketBuilder | Task 2, 2.1, 2.2 | ✅ Covered |
| ConnectionKey, TcpConnection, UdpConnection | Task 3 | ✅ Covered |
| ConnectionTable | Task 4, 4.1, 4.2 | ✅ Covered |
| TCPHandler | Tasks 5-10 | ✅ Covered |
| UDPHandler | Tasks 11-13 | ✅ Covered |
| PacketRouter integration | Tasks 14-17 | ✅ Covered |
| Logging | Task 18, 18.1 | ✅ Covered |
| Error handling | Task 19, 19.1 | ✅ Covered |
| Testing | Tasks 20-23 | ✅ Covered |
| Documentation | Tasks 24-26 | ✅ Covered |

---

## 🔧 RECOMMENDED FIXES

### Priority 1 (Must Fix Before Implementation)

1. **Fix Task 1 file path** - Change from `shared/src/androidMain` to `androidApp/src/main`
2. **Fix Task 2 file path** - Same as above
3. **Fix Task 5 file path** - Same as above
4. **Fix Task 11 file path** - Same as above
5. **Add integration tasks** - Update TunnelVpnService, remove old stub

### Priority 2 (Should Fix)

6. **Clarify SequenceNumberTracker usage** - Add to design.md
7. **Unify Protocol enums** - Remove ConnectionKey.Protocol duplication
8. **Fix TCPHandler.sendTcpPacket signature** - Use TcpFlags type
9. **Define RouterStatistics** - Or clarify it's same as ConnectionStatistics
10. **Mark Requirement 10 as optional** - Align with tasks

### Priority 3 (Nice to Have)

11. **Add error flow diagrams** - Help implementers understand error handling
12. **Verify property test coverage** - Ensure all property tests are in tasks

---

## ✅ CONCLUSION

**Overall Assessment: GOOD with FIXABLE ISSUES**

The specification is well-structured and comprehensive. The main issues are:
1. File path errors (androidApp vs shared module)
2. Minor type inconsistencies
3. Missing integration tasks

These are all easily fixable and don't affect the core design.

**Recommendation: Fix Priority 1 issues before starting implementation.**
