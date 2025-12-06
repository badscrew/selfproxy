# Manual Testing and Validation Guide

## Overview

This guide provides detailed instructions for manually testing and validating the native SSH client implementation. These tests require physical devices, real SSH servers, and extended monitoring periods.

## Prerequisites

### Required Hardware
- [x] ARM64 physical Android device (Android 8.0+)



- [ ] x86_64 Android emulator
- [ ] ARM32 physical device (optional, for legacy support validation)

### Required Software
- [ ] SSH server with public IP or accessible via port forwarding
- [ ] Multiple SSH key types (RSA, ECDSA, Ed25519)
- [ ] ADB (Android Debug Bridge) installed
- [ ] Battery monitoring tools

### Required Access
- [ ] SSH server credentials
- [ ] Ability to interrupt network (WiFi toggle, airplane mode)
- [ ] 24+ hours for battery testing

## Test Environment Setup

### 1. Prepare SSH Server

```bash
# Ensure SSH server is running
sudo systemctl status sshd

# Verify SSH server accepts connections
ssh -p 22 user@your-server.com

# Test with different key types
ssh -i ~/.ssh/id_rsa user@your-server.com
ssh -i ~/.ssh/id_ecdsa user@your-server.com
ssh -i ~/.ssh/id_ed25519 user@your-server.com
```

### 2. Generate Test Keys (if needed)

```bash
# RSA 2048-bit
ssh-keygen -t rsa -b 2048 -f test_rsa_key -N ""

# ECDSA 256-bit
ssh-keygen -t ecdsa -b 256 -f test_ecdsa_key -N ""

# Ed25519
ssh-keygen -t ed25519 -f test_ed25519_key -N ""

# Copy public keys to server
ssh-copy-id -i test_rsa_key.pub user@your-server.com
ssh-copy-id -i test_ecdsa_key.pub user@your-server.com
ssh-copy-id -i test_ed25519_key.pub user@your-server.com
```

### 3. Build and Install APK

```powershell
# Build debug APK
.\gradlew.bat androidApp:assembleDebug

# Install on device
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk

# Verify installation
adb shell pm list packages | Select-String "sshtunnel"
```

## Test Cases

### Test 1: ARM64 Physical Device Testing

**Objective**: Verify native SSH works on ARM64 devices

**Steps**:
1. Connect ARM64 Android device via USB
2. Enable USB debugging on device
3. Install APK: `adb install -r androidApp-debug.apk`
4. Open app and create SSH profile
5. Connect to SSH server
6. Verify VPN icon appears in status bar
7. Test internet connectivity through tunnel
8. Check logs: `adb logcat -s SSHTunnelProxy:* AndroidNativeSSHClient:*`

**Expected Results**:
- [ ] App installs successfully
- [ ] SSH connection establishes within 5 seconds
- [ ] VPN icon appears in status bar
- [ ] Internet traffic routes through tunnel
- [ ] No "Stream closed" errors in logs
- [ ] Connection remains stable for 10+ minutes

**Validation Requirements**: 15.1

---

### Test 2: x86_64 Emulator Testing

**Objective**: Verify native SSH works on x86_64 emulators

**Steps**:
1. Start x86_64 emulator (API 26+)
2. Install APK: `adb install -r androidApp-debug.apk`
3. Open app and create SSH profile
4. Connect to SSH server
5. Verify VPN icon appears
6. Test internet connectivity
7. Check logs for architecture detection

**Expected Results**:
- [ ] App installs successfully
- [ ] Correct x86_64 binary selected
- [ ] SSH connection establishes
- [ ] VPN functions correctly
- [ ] No architecture-related errors

**Validation Requirements**: 15.1

---

### Test 3: Different SSH Servers

**Objective**: Verify compatibility with various SSH server implementations

**Test Matrix**:

| Server Type | Version | Result | Notes |
|------------|---------|--------|-------|
| OpenSSH    | 8.x     | ☐ Pass / ☐ Fail | |
| OpenSSH    | 9.x     | ☐ Pass / ☐ Fail | |
| Dropbear   | Latest  | ☐ Pass / ☐ Fail | |
| Other      | _____   | ☐ Pass / ☐ Fail | |

**Steps** (for each server):
1. Configure SSH profile for server
2. Attempt connection
3. Verify successful authentication
4. Test data transfer through tunnel
5. Monitor for errors

**Expected Results**:
- [ ] Connects to OpenSSH 8.x
- [ ] Connects to OpenSSH 9.x
- [ ] Connects to Dropbear (if tested)
- [ ] No compatibility issues

**Validation Requirements**: 15.3

---

### Test 4: Different Key Types

**Objective**: Verify support for RSA, ECDSA, and Ed25519 keys

**Test Matrix**:

| Key Type | Key Size | Result | Connection Time | Notes |
|----------|----------|--------|----------------|-------|
| RSA      | 2048-bit | ☐ Pass / ☐ Fail | _____ sec | |
| RSA      | 4096-bit | ☐ Pass / ☐ Fail | _____ sec | |
| ECDSA    | 256-bit  | ☐ Pass / ☐ Fail | _____ sec | |
| ECDSA    | 384-bit  | ☐ Pass / ☐ Fail | _____ sec | |
| Ed25519  | Default  | ☐ Pass / ☐ Fail | _____ sec | |

**Steps** (for each key type):
1. Create SSH profile with specific key type
2. Import private key into app
3. Attempt connection
4. Measure connection time
5. Verify successful authentication
6. Test data transfer

**Expected Results**:
- [ ] All key types authenticate successfully
- [ ] Connection time <5 seconds for all types
- [ ] No key parsing errors
- [ ] Ed25519 preferred for best performance

**Validation Requirements**: 15.3

---

### Test 5: Network Interruptions

**Objective**: Verify reconnection behavior on network changes

**Test Scenarios**:

#### 5.1: WiFi to Mobile Data
1. Connect to SSH via WiFi
2. Verify connection is active
3. Disable WiFi (switch to mobile data)
4. Observe reconnection behavior
5. Verify connection restored

**Expected Results**:
- [ ] Detects network change within 5 seconds
- [ ] Automatically attempts reconnection
- [ ] Successfully reconnects on mobile data
- [ ] No data loss during transition
- [ ] User notified of reconnection

#### 5.2: Airplane Mode Toggle
1. Connect to SSH
2. Enable airplane mode
3. Wait 10 seconds
4. Disable airplane mode
5. Observe reconnection

**Expected Results**:
- [ ] Detects disconnection immediately
- [ ] Shows disconnected state
- [ ] Automatically reconnects when network available
- [ ] Connection restored within 10 seconds

#### 5.3: Poor Network Conditions
1. Connect to SSH
2. Simulate poor network (use network throttling)
3. Monitor connection stability
4. Verify keep-alive packets work

**Expected Results**:
- [ ] Connection maintained on slow network
- [ ] Keep-alive prevents timeout
- [ ] No false disconnections
- [ ] Graceful handling of packet loss

**Validation Requirements**: 15.3

---

### Test 6: Battery Usage Over 24 Hours

**Objective**: Measure battery consumption during extended SSH tunnel usage

**Setup**:
1. Fully charge device to 100%
2. Disconnect from charger
3. Connect SSH tunnel
4. Leave device idle with tunnel active
5. Monitor battery level every hour

**Monitoring**:

```bash
# Check battery stats
adb shell dumpsys battery

# Monitor power usage
adb shell dumpsys batterystats --reset
# ... wait 24 hours ...
adb shell dumpsys batterystats > battery_stats.txt
```

**Data Collection**:

| Hour | Battery % | Notes |
|------|-----------|-------|
| 0    | 100%      | Start |
| 1    | ____%     | |
| 2    | ____%     | |
| 3    | ____%     | |
| 4    | ____%     | |
| 6    | ____%     | |
| 8    | ____%     | |
| 12   | ____%     | |
| 16   | ____%     | |
| 20   | ____%     | |
| 24   | ____%     | End |

**Expected Results**:
- [ ] Battery drain <5% per hour
- [ ] Total drain <120% over 24 hours (allows device to last ~20 hours)
- [ ] No excessive wake locks
- [ ] Comparable to or better than sshj implementation
- [ ] Connection remains stable for full 24 hours

**Validation Requirements**: 15.4

---

### Test 7: Verify No "Stream Closed" Errors

**Objective**: Confirm elimination of the primary sshj issue

**Steps**:
1. Clear logcat: `adb logcat -c`
2. Connect SSH tunnel
3. Use tunnel for various activities:
   - Web browsing (10+ sites)
   - Video streaming (5+ minutes)
   - File downloads (multiple files)
   - API requests (100+ requests)
4. Monitor logs continuously
5. Keep connection active for 1+ hour

**Log Monitoring**:

```powershell
# Monitor for "Stream closed" errors
adb logcat | Select-String -Pattern "stream closed|Stream closed|channel closed"

# Monitor for SSH errors
adb logcat -s AndroidNativeSSHClient:* | Select-String -Pattern "error|Error|ERROR"
```

**Expected Results**:
- [ ] Zero "Stream closed" errors
- [ ] Zero premature channel closures
- [ ] All network requests complete successfully
- [ ] No connection drops during active use
- [ ] Stable connection for 1+ hour

**Validation Requirements**: 15.2

---

### Test 8: APK Size Measurement

**Objective**: Verify APK size increase is within acceptable limits

**Measurements**:

```powershell
# Measure APK sizes
Get-ChildItem androidApp\build\outputs\apk\debug\*.apk | Select-Object Name, @{Name="Size(MB)";Expression={[math]::Round($_.Length/1MB, 2)}}

# If APK splits are configured
Get-ChildItem androidApp\build\outputs\apk\debug\*\*.apk | Select-Object Name, @{Name="Size(MB)";Expression={[math]::Round($_.Length/1MB, 2)}}
```

**Data Collection**:

| APK Type | Size (MB) | Within Limit? | Notes |
|----------|-----------|---------------|-------|
| Universal (all architectures) | _____ | ☐ Yes / ☐ No | Target: <15 MB increase |
| ARM64 only | _____ | ☐ Yes / ☐ No | Target: <10 MB increase |
| ARM32 only | _____ | ☐ Yes / ☐ No | Target: <10 MB increase |
| x86_64 only | _____ | ☐ Yes / ☐ No | Target: <10 MB increase |
| x86 only | _____ | ☐ Yes / ☐ No | Target: <10 MB increase |

**Binary Size Breakdown**:

```bash
# Check binary sizes in jniLibs
Get-ChildItem androidApp\src\main\jniLibs\*\* -Recurse | Select-Object Name, @{Name="Size(MB)";Expression={[math]::Round($_.Length/1MB, 2)}}
```

**Expected Results**:
- [ ] Universal APK increase <15 MB
- [ ] Single architecture APK increase <10 MB
- [ ] APK splits reduce download size for users
- [ ] Binary sizes match expectations from design

**Validation Requirements**: 15.4

---

### Test 9: Connection Success Rate

**Objective**: Verify >95% connection success rate

**Test Protocol**:
1. Perform 100 connection attempts
2. Record success/failure for each
3. Calculate success rate
4. Analyze failure patterns

**Data Collection**:

```
Attempt | Result | Time (sec) | Error (if any)
--------|--------|------------|---------------
1       | ☐ S ☐ F | _____ | _____________
2       | ☐ S ☐ F | _____ | _____________
...
100     | ☐ S ☐ F | _____ | _____________

Total Successes: _____ / 100
Success Rate: _____%
```

**Test Conditions**:
- [ ] Mix of WiFi and mobile data
- [ ] Different times of day
- [ ] Various network conditions
- [ ] Fresh app starts and reconnections

**Expected Results**:
- [ ] Success rate >95%
- [ ] Average connection time <5 seconds
- [ ] Failures are transient (network issues, not code bugs)
- [ ] Significant improvement over sshj (33% success rate)

**Validation Requirements**: 15.5

---

## Automated Log Collection

### Setup Logging Script

Create `.kiro/temp/scripts/collect_test_logs.ps1`:

```powershell
# Collect comprehensive logs for manual testing
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logDir = ".kiro/temp/test_logs/$timestamp"
New-Item -ItemType Directory -Force -Path $logDir

# Collect logcat
Write-Host "Collecting logcat..."
adb logcat -d > "$logDir/logcat_full.txt"
adb logcat -d -s SSHTunnelProxy:* AndroidNativeSSHClient:* > "$logDir/logcat_ssh.txt"

# Collect battery stats
Write-Host "Collecting battery stats..."
adb shell dumpsys battery > "$logDir/battery.txt"
adb shell dumpsys batterystats > "$logDir/batterystats.txt"

# Collect network info
Write-Host "Collecting network info..."
adb shell dumpsys connectivity > "$logDir/connectivity.txt"

# Collect app info
Write-Host "Collecting app info..."
adb shell dumpsys package com.sshtunnel.android > "$logDir/package_info.txt"

# Collect memory info
Write-Host "Collecting memory info..."
adb shell dumpsys meminfo com.sshtunnel.android > "$logDir/meminfo.txt"

Write-Host "Logs collected in: $logDir"
```

### Usage

```powershell
# Run before each test
.\kiro\temp\scripts\collect_test_logs.ps1
```

## Test Results Summary

### Overall Results

| Test | Status | Notes |
|------|--------|-------|
| 1. ARM64 Device | ☐ Pass / ☐ Fail | |
| 2. x86_64 Emulator | ☐ Pass / ☐ Fail | |
| 3. Different SSH Servers | ☐ Pass / ☐ Fail | |
| 4. Different Key Types | ☐ Pass / ☐ Fail | |
| 5. Network Interruptions | ☐ Pass / ☐ Fail | |
| 6. Battery Usage (24h) | ☐ Pass / ☐ Fail | |
| 7. No Stream Closed Errors | ☐ Pass / ☐ Fail | |
| 8. APK Size | ☐ Pass / ☐ Fail | |
| 9. Connection Success Rate | ☐ Pass / ☐ Fail | |

### Success Criteria Met

- [ ] >95% connection success rate (Requirement 15.5)
- [ ] Zero "Stream closed" errors (Requirement 15.2)
- [ ] 24+ hour stability (Requirement 15.3)
- [ ] <10 MB APK size increase per architecture (Requirement 15.4)
- [ ] Acceptable battery usage (Requirement 15.4)

### Issues Found

Document any issues discovered during testing:

1. **Issue**: _____________________
   - **Severity**: Critical / High / Medium / Low
   - **Test**: _____________________
   - **Description**: _____________________
   - **Reproduction**: _____________________

2. **Issue**: _____________________
   - **Severity**: Critical / High / Medium / Low
   - **Test**: _____________________
   - **Description**: _____________________
   - **Reproduction**: _____________________

## Next Steps

After completing manual testing:

1. **If all tests pass**:
   - Mark task 18 as complete
   - Proceed to task 19 (documentation)
   - Prepare for production release

2. **If issues found**:
   - Document all issues in detail
   - Prioritize by severity
   - Create fix tasks for critical/high issues
   - Re-test after fixes

3. **Performance optimization**:
   - If battery usage is high, investigate optimization
   - If connection time is slow, profile bottlenecks
   - If APK size is too large, consider binary stripping

## Reporting

Create a test report summarizing:
- All test results
- Success rates and metrics
- Issues found and their severity
- Recommendations for production readiness
- Any remaining concerns

Save report as: `.kiro/specs/native-ssh-client/MANUAL_TEST_REPORT.md`
