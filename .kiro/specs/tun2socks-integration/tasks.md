# Implementation Tasks: tun2socks Integration

## Phase 1: Library Selection and Setup

- [x] 1. Research and select tun2socks library
  - Evaluate xjasonlyu/tun2socks (Go-based)
  - Evaluate ambrop72/badvpn (C-based)
  - Compare ease of integration, maintenance, and performance
  - Document decision and rationale
  - _Requirements: 1.1, 5.1, 5.2_

- [ ] 2. Add tun2socks library to project
  - Download or build tun2socks for Android
  - Add native libraries (.so files) for all architectures
  - Configure build.gradle.kts to include native libraries
  - Verify library loads correctly on test device
  - **STATUS:** Build config ready, awaiting library download
  - _Requirements: 1.1, 5.2_

- [ ] 3. Test native library loading
  - Create simple test to load native library
  - Verify on multiple architectures (arm64, arm, x86)
  - Handle UnsatisfiedLinkError gracefully
  - Log library version and build info
  - _Requirements: 1.1, 3.1_

## Phase 2: Kotlin Wrapper Implementation

- [x] 4. Create Tun2SocksEngine class
  - Define Kotlin interface for tun2socks
  - Implement start() method with configuration
  - Implement stop() method with cleanup
  - Implement isRunning() status check
  - Add proper error handling
  - _Requirements: 1.2, 2.1_

- [ ] 5. Implement JNI bindings (if needed)
  - Create native method declarations
  - Implement C/C++ JNI bridge code
  - Handle data type conversions (Kotlin ↔ C)
  - Add error handling in native code
  - _Requirements: 1.2_

- [ ] 6. Add configuration management
  - Create Tun2SocksConfig data class
  - Implement configuration validation
  - Add sensible defaults for mobile networks
  - Document all configuration options
  - _Requirements: 2.2, 4.1_

- [ ] 7. Implement error handling
  - Define Tun2SocksError sealed class
  - Map native errors to Kotlin exceptions
  - Add detailed error messages
  - Log errors for debugging
  - _Requirements: 3.1, 3.2_

## Phase 3: VPN Service Integration

- [x] 8. Update TunnelVpnService to use Tun2SocksEngine
  - Replace PacketRouter with Tun2SocksEngine
  - Pass TUN file descriptor to tun2socks
  - Configure SOCKS5 proxy address
  - Handle tun2socks lifecycle
  - _Requirements: 2.1, 2.2, 2.3_

- [ ] 9. Implement tun2socks lifecycle management
  - Start tun2socks when VPN starts
  - Stop tun2socks when VPN stops
  - Handle tun2socks crashes and restarts
  - Clean up resources on errors
  - _Requirements: 2.3, 3.2_

- [ ] 10. Add health monitoring
  - Periodically check if tun2socks is running
  - Detect hung or frozen tun2socks process
  - Restart tun2socks on failures
  - Report health status to VpnController
  - _Requirements: 3.3_

- [ ] 11. Remove old PacketRouter code
  - Delete PacketRouter.kt
  - Remove packet routing logic from TunnelVpnService
  - Clean up unused imports and dependencies
  - Update documentation
  - _Requirements: 2.1_

## Phase 4: Testing and Validation

- [ ] 12. Test basic connectivity
  - Test HTTP traffic through VPN
  - Test HTTPS traffic through VPN
  - Verify traffic routes through SOCKS5 proxy
  - Check external IP matches SSH server
  - _Requirements: 6.1_

- [ ] 13. Test DNS resolution
  - Verify DNS queries route through tunnel
  - Test with multiple DNS servers
  - Check for DNS leaks using dnsleaktest.com
  - Verify DNS responses are correct
  - _Requirements: 6.1_

- [ ] 14. Test error scenarios
  - Test with SOCKS proxy unavailable
  - Test VPN restart after network change
  - Test SSH disconnection during VPN
  - Test tun2socks crash recovery
  - _Requirements: 6.2_

- [ ] 15. Performance testing
  - Measure throughput (download/upload speeds)
  - Measure latency (ping times)
  - Monitor CPU usage during traffic
  - Monitor memory usage
  - _Requirements: 6.3, 4.1_

- [ ] 16. Battery usage testing
  - Measure battery drain with VPN active
  - Compare with VPN inactive
  - Test over extended period (1+ hours)
  - Optimize if drain is excessive
  - _Requirements: 4.2, 6.3_

## Phase 5: Optimization and Polish

- [ ] 17. Optimize tun2socks parameters
  - Tune buffer sizes for mobile networks
  - Adjust timeouts for better performance
  - Configure appropriate MTU
  - Test different configurations
  - _Requirements: 4.1_

- [ ] 18. Add statistics and monitoring
  - Implement getStats() method
  - Track bytes sent/received
  - Track packets sent/received
  - Display stats in UI (optional)
  - _Requirements: 4.2_

- [ ] 19. Improve error messages
  - Add user-friendly error descriptions
  - Provide troubleshooting suggestions
  - Log detailed errors for debugging
  - Update UI to show specific errors
  - _Requirements: 3.1_

- [ ] 20. Update documentation
  - Document tun2socks integration
  - Update architecture diagrams
  - Add troubleshooting guide
  - Document configuration options
  - _Requirements: All_

## Phase 6: Final Testing and Release

- [ ] 21. Comprehensive testing
  - Test on multiple Android versions
  - Test on multiple device types
  - Test with different SSH servers
  - Test with various network conditions
  - _Requirements: 5.1, 6.1, 6.2_

- [ ] 22. User acceptance testing
  - Deploy to test users
  - Gather feedback on connectivity
  - Gather feedback on performance
  - Fix any reported issues
  - _Requirements: All_

- [ ] 23. Final cleanup and optimization
  - Remove debug logging
  - Optimize APK size
  - Final code review
  - Update version number
  - _Requirements: All_

- [ ] 24. Release preparation
  - Update README with tun2socks info
  - Update changelog
  - Create release notes
  - Tag release in Git
  - _Requirements: All_

## Notes

### Library Selection Criteria

When choosing between tun2socks implementations, consider:

1. **xjasonlyu/tun2socks (Go)**
   - ✅ Active development
   - ✅ Good documentation
   - ✅ Android support out of the box
   - ✅ Easy integration (AAR available)
   - ❌ Larger binary size (~10MB)
   - ❌ Requires Go runtime

2. **ambrop72/badvpn (C)**
   - ✅ Smaller binary size (~2MB)
   - ✅ Lower memory footprint
   - ✅ Mature and stable
   - ❌ Less active development
   - ❌ More complex integration
   - ❌ Need to create JNI bindings

**Recommendation**: Start with xjasonlyu/tun2socks for faster integration. Can switch to badvpn later if binary size is a concern.

### Testing Checklist

Before marking integration complete, verify:

- [ ] Web browsing works (HTTP/HTTPS)
- [ ] DNS resolution works
- [ ] No DNS leaks
- [ ] VPN reconnects after network change
- [ ] VPN stops cleanly on disconnect
- [ ] No crashes or ANRs
- [ ] Battery usage is acceptable (<5% per hour)
- [ ] Performance is acceptable (<100ms latency)
- [ ] Works on all supported Android versions
- [ ] Works on all supported architectures

### Rollback Plan

If tun2socks integration fails or causes issues:

1. Keep PacketRouter code in a separate branch
2. Add feature flag to switch between implementations
3. Can quickly revert to PacketRouter if needed
4. Document issues for future attempts

### Success Criteria

Integration is successful when:

1. ✅ VPN routes all traffic through SOCKS5 proxy
2. ✅ No "No Internet" errors
3. ✅ DNS queries route through tunnel
4. ✅ No DNS leaks
5. ✅ Stable operation (no crashes)
6. ✅ Acceptable performance
7. ✅ Acceptable battery usage

## Dependencies

- Existing VPN service (TunnelVpnService)
- Existing SSH connection (SSHConnectionManager)
- Existing SOCKS5 proxy (AndroidSSHClient)
- Android NDK (for native library integration)

## Estimated Effort

- Phase 1: 4-8 hours (library selection and setup)
- Phase 2: 8-16 hours (Kotlin wrapper)
- Phase 3: 4-8 hours (VPN integration)
- Phase 4: 8-16 hours (testing)
- Phase 5: 4-8 hours (optimization)
- Phase 6: 4-8 hours (final testing)

**Total: 32-64 hours** (4-8 days of focused work)

## Priority

**HIGH** - This is blocking VPN functionality. Without proper packet routing, the VPN doesn't work.

## Related Tasks

- Original task 9.1 (Implement packet routing) - Being replaced
- Original task 9.2 (DNS routing) - Will be handled by tun2socks
- Task 9.3 (VPN integration) - Already complete, just needs tun2socks

## References

- [xjasonlyu/tun2socks GitHub](https://github.com/xjasonlyu/tun2socks)
- [ambrop72/badvpn GitHub](https://github.com/ambrop72/badvpn)
- [Android VpnService Documentation](https://developer.android.com/reference/android/net/VpnService)
- [Android NDK Guide](https://developer.android.com/ndk/guides)
- [JNI Tips](https://developer.android.com/training/articles/perf-jni)
