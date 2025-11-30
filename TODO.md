# TODO - Future Enhancements

This document tracks planned features, improvements, and known issues for the SSH Tunnel Proxy project.

## High Priority

### 1. DNS Traffic Tunneling via UDP ASSOCIATE
**Status**: Not Implemented  
**Priority**: High  
**Complexity**: High

**Current State**:
- DNS queries are resolved locally by Android's DNS resolver
- Only TCP connections are tunneled through SOCKS5
- JSch's SOCKS5 proxy doesn't support UDP ASSOCIATE command

**Goal**:
- Tunnel DNS queries through SOCKS5 UDP ASSOCIATE
- Prevent DNS leaks completely
- Support DNS over UDP (port 53) and DNS over TLS (port 853)

**Implementation Options**:
1. **Replace JSch**: Use a SOCKS5 library that supports UDP ASSOCIATE
   - Pros: Full SOCKS5 support, standard implementation
   - Cons: Need to find/implement alternative SSH library
   
2. **Custom SOCKS5 Server**: Implement SOCKS5 server on SSH server side
   - Pros: Full control, can optimize for mobile
   - Cons: Requires server-side installation
   
3. **DNS-over-TCP Fallback**: Convert UDP DNS to TCP DNS
   - Pros: Works with current JSch implementation
   - Cons: Slower, not all DNS servers support TCP

**References**:
- RFC 1928 (SOCKS5 Protocol)
- RFC 7766 (DNS over TCP)
- JSch limitations: https://github.com/mwiede/jsch

**Related Files**:
- `androidApp/src/main/kotlin/com/sshtunnel/android/vpn/UDPHandler.kt`
- `androidApp/src/main/kotlin/com/sshtunnel/android/vpn/PacketRouter.kt`

---

### 2. Video Calling Support (UDP Traffic)
**Status**: Not Implemented  
**Priority**: High  
**Complexity**: High  
**Depends On**: #1 (DNS Traffic Tunneling)

**Current State**:
- UDP traffic cannot be tunneled through JSch SOCKS5
- Video calling apps (WhatsApp, Zoom, Discord) won't work
- README claims support but it's not implemented

**Goal**:
- Support UDP-based video calling applications
- Implement SOCKS5 UDP ASSOCIATE for real-time media
- Support STUN/TURN protocols

**Applications to Support**:
- WhatsApp voice/video calls
- Telegram voice/video calls
- Zoom meetings
- Discord voice channels
- Microsoft Teams
- Google Meet
- Skype

**Implementation Requirements**:
1. SOCKS5 UDP ASSOCIATE support
2. Low-latency UDP packet routing
3. Connection reuse for same destination
4. Automatic cleanup of idle connections
5. NAT traversal support (STUN/TURN)

**Performance Targets**:
- Latency overhead: < 10ms
- Support multiple simultaneous calls
- Memory per connection: < 10KB
- Battery efficient

**References**:
- RFC 5389 (STUN)
- RFC 5766 (TURN)
- WebRTC specifications

---

### 3. Fix SOCKS5 Connection Reset Errors
**Status**: Bug  
**Priority**: High  
**Complexity**: Medium

**Current State**:
- Constant "Connection reset" errors in logs
- TCPHandler and UDPHandler failing SOCKS5 handshakes
- Traffic not flowing properly despite VPN being "connected"

**Symptoms**:
```
E TCPHandler: SOCKS5 handshake error: Connection reset
E UDPHandler: SOCKS5 handshake error: Connection reset
E UDPHandler: UDP ASSOCIATE handshake error: Connection reset
```

**Root Causes**:
1. JSch SOCKS5 proxy doesn't support UDP ASSOCIATE
2. Packet router attempting UDP operations on TCP-only proxy
3. DNS over TLS (port 853) attempts failing

**Solution**:
- Implement local DNS resolution (Option 1)
- Only forward TCP connections through SOCKS5
- Handle UDP traffic separately or drop gracefully

**Related Files**:
- `androidApp/src/main/kotlin/com/sshtunnel/android/vpn/TCPHandler.kt`
- `androidApp/src/main/kotlin/com/sshtunnel/android/vpn/UDPHandler.kt`
- `androidApp/src/main/kotlin/com/sshtunnel/android/vpn/PacketRouter.kt`

---

## Medium Priority

### 4. iOS Support
**Status**: Planned  
**Priority**: Medium  
**Complexity**: High

**Current State**:
- Project structure supports Kotlin Multiplatform
- Shared business logic in commonMain
- No iOS-specific implementation yet

**Requirements**:
1. iOS VPN implementation (NetworkExtension framework)
2. iOS SSH client (NMSSH or similar)
3. iOS credential storage (Keychain)
4. SwiftUI interface
5. App Store compliance

**Shared Code Ready**:
- ✅ Data models
- ✅ Repository interfaces
- ✅ Business logic
- ✅ Database schema (SQLDelight)

**iOS-Specific Needed**:
- ❌ VPN tunnel provider
- ❌ SSH client implementation
- ❌ Keychain integration
- ❌ UI implementation

---

### 5. Connection Statistics and Monitoring
**Status**: Partially Implemented  
**Priority**: Medium  
**Complexity**: Low

**Current State**:
- Basic connection status tracking
- No data usage statistics
- No bandwidth monitoring

**Features to Add**:
- Real-time data usage (upload/download)
- Connection duration tracking
- Bandwidth usage graphs
- Historical statistics
- Export statistics to CSV

**UI Mockup**:
```
┌─────────────────────────────┐
│ Connection Statistics       │
├─────────────────────────────┤
│ Duration: 2h 34m            │
│ Data Sent: 145 MB           │
│ Data Received: 892 MB       │
│ Current Speed: ↑ 2.3 MB/s   │
│                ↓ 5.1 MB/s   │
└─────────────────────────────┘
```

---

### 6. Auto-Reconnect Improvements
**Status**: Basic Implementation  
**Priority**: Medium  
**Complexity**: Medium

**Current State**:
- Basic reconnection on network change
- No exponential backoff
- No connection quality monitoring

**Improvements Needed**:
1. Exponential backoff for failed reconnections
2. Connection quality monitoring (latency, packet loss)
3. Automatic server switching if connection poor
4. Smart reconnection (don't reconnect if user disconnected manually)
5. Notification when reconnection fails

**Algorithm**:
```
Attempt 1: Immediate
Attempt 2: 1 second delay
Attempt 3: 2 seconds delay
Attempt 4: 4 seconds delay
Attempt 5: 8 seconds delay
Max delay: 60 seconds
```

---

### 7. Per-App VPN Routing
**Status**: Partially Implemented  
**Priority**: Medium  
**Complexity**: Low

**Current State**:
- Basic app exclusion implemented
- No UI for selecting apps
- No app inclusion mode (only exclusion)

**Features to Add**:
1. UI to select apps for VPN routing
2. Search/filter apps by name
3. Include mode (only selected apps use VPN)
4. Exclude mode (all apps except selected use VPN)
5. Save routing profiles
6. Quick toggle for common apps

**UI Design**:
```
┌─────────────────────────────┐
│ App Routing                 │
├─────────────────────────────┤
│ Mode: [Exclude Selected]    │
│                             │
│ 🔍 Search apps...           │
│                             │
│ ☑ Chrome                    │
│ ☐ Gmail                     │
│ ☑ WhatsApp                  │
│ ☐ Banking App               │
└─────────────────────────────┘
```

---

## Low Priority

### 8. Multiple Server Profiles
**Status**: Implemented  
**Priority**: Low (Enhancement)  
**Complexity**: Low

**Current State**:
- Multiple profiles supported
- Basic CRUD operations
- No profile groups or favorites

**Enhancements**:
1. Profile groups/folders
2. Favorite profiles
3. Quick switch between profiles
4. Profile import/export
5. Profile templates

---

### 9. Connection Testing
**Status**: Basic Implementation  
**Priority**: Low  
**Complexity**: Low

**Current State**:
- Basic SSH connection test
- No comprehensive diagnostics

**Features to Add**:
1. Test SSH connection
2. Test SOCKS5 proxy
3. Test DNS resolution
4. Test UDP support
5. Measure latency
6. Check for DNS leaks
7. Verify IP address change
8. Generate diagnostic report

---

### 10. Dark Mode and Themes
**Status**: Not Implemented  
**Priority**: Low  
**Complexity**: Low

**Current State**:
- Material Design 3 with system theme
- No custom themes

**Features to Add**:
1. Force dark/light mode
2. Custom color schemes
3. AMOLED black theme
4. Theme scheduling (auto dark at night)

---

### 11. Localization (i18n)
**Status**: English Only  
**Priority**: Low  
**Complexity**: Medium

**Languages to Support**:
- Spanish
- French
- German
- Chinese (Simplified & Traditional)
- Japanese
- Korean
- Russian
- Arabic
- Portuguese

---

### 12. Widget Support
**Status**: Not Implemented  
**Priority**: Low  
**Complexity**: Medium

**Widget Ideas**:
1. Quick connect/disconnect toggle
2. Connection status indicator
3. Data usage display
4. Quick profile switcher

---

## Technical Debt

### 13. Improve Error Handling
**Status**: Ongoing  
**Priority**: Medium  
**Complexity**: Low

**Issues**:
- Generic error messages
- No user-friendly error explanations
- Limited error recovery

**Improvements**:
1. Specific error messages for common issues
2. Suggested solutions in error dialogs
3. Automatic error recovery where possible
4. Better logging for debugging

---

### 14. Add Property-Based Tests
**Status**: Partially Implemented  
**Priority**: Medium  
**Complexity**: Medium

**Current State**:
- Some property tests exist
- Not comprehensive coverage

**Areas Needing Tests**:
1. Packet parsing and routing
2. SOCKS5 protocol handling
3. Connection state machine
4. Profile serialization
5. Credential encryption

---

### 15. Performance Optimization
**Status**: Ongoing  
**Priority**: Medium  
**Complexity**: Medium

**Areas to Optimize**:
1. Packet routing efficiency
2. Memory usage (especially for long connections)
3. Battery consumption
4. Connection establishment time
5. UI responsiveness

**Profiling Needed**:
- CPU usage during active connection
- Memory allocation patterns
- Battery drain analysis
- Network efficiency

---

### 16. Security Audit
**Status**: Not Done  
**Priority**: High  
**Complexity**: High

**Areas to Audit**:
1. Credential storage (Android Keystore usage)
2. Private key handling in memory
3. SOCKS5 implementation security
4. VPN packet routing security
5. DNS leak prevention
6. Traffic analysis resistance

**External Audit**:
- Consider professional security audit before 1.0 release
- Bug bounty program for security issues

---

## Documentation

### 17. User Documentation
**Status**: Basic  
**Priority**: Medium  
**Complexity**: Low

**Needed**:
1. Setup guide with screenshots
2. Troubleshooting guide
3. FAQ
4. Video tutorials
5. Server setup guide

---

### 18. Developer Documentation
**Status**: Basic  
**Priority**: Medium  
**Complexity**: Low

**Needed**:
1. Architecture documentation
2. API documentation
3. Contributing guide
4. Code style guide
5. Testing guide

---

## Infrastructure

### 19. CI/CD Pipeline
**Status**: Not Implemented  
**Priority**: Medium  
**Complexity**: Medium

**Requirements**:
1. Automated builds on push
2. Run tests on PR
3. Code coverage reporting
4. Automated releases
5. APK signing for releases

---

### 20. Crash Reporting
**Status**: Not Implemented  
**Priority**: Medium  
**Complexity**: Low

**Options**:
- Firebase Crashlytics
- Sentry
- Bugsnag

**Privacy Considerations**:
- No PII in crash reports
- User opt-in for crash reporting
- Open source alternative preferred

---

## Known Issues

### Issue #1: VPN Shows Connected But Traffic Not Flowing
**Status**: Active Bug  
**Severity**: High  
**Workaround**: None currently

**Description**:
VPN shows as connected with key icon, but actual traffic is not being routed properly due to SOCKS5 UDP ASSOCIATE failures.

**Related**: #3 (Fix SOCKS5 Connection Reset Errors)

---

### Issue #2: DNS Leaks
**Status**: Known Limitation  
**Severity**: High  
**Workaround**: None

**Description**:
DNS queries are resolved locally instead of through the tunnel, potentially leaking DNS information.

**Related**: #1 (DNS Traffic Tunneling)

---

### Issue #3: High Battery Drain
**Status**: Under Investigation  
**Severity**: Medium  
**Workaround**: None

**Description**:
Battery consumption higher than expected during active VPN connection.

**Possible Causes**:
- Constant packet processing
- Failed connection retries
- Inefficient wake locks

---

## Completed

### ✅ Basic VPN Connection
- SSH connection establishment
- SOCKS5 proxy creation
- VPN interface setup
- Basic packet routing

### ✅ Profile Management
- Create/edit/delete profiles
- Private key storage
- Profile persistence

### ✅ VPN Permission Handling
- Request VPN permission
- Auto-retry after permission grant
- State management

### ✅ Connection State Management
- Track SSH and VPN states
- Broadcast state changes
- UI state updates

---

## Notes

- Items marked with ⚠️ require breaking changes
- Items marked with 🔒 have security implications
- Items marked with 📱 are mobile-specific
- Items marked with 🖥️ are server-side requirements

---

**Last Updated**: 2024-11-30  
**Version**: 0.1.0-alpha
