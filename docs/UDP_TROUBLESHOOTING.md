# UDP and Video Calling Troubleshooting Guide

This guide helps you diagnose and fix issues with UDP traffic and video calling applications when using SSH Tunnel Proxy.

## Quick Diagnostics

### 1. Check Server Support

First, verify your SSH server supports SOCKS5 UDP ASSOCIATE:

```bash
# Connect with dynamic forwarding
ssh -D 1080 user@your-server.com

# In another terminal, test UDP support
# This should work if UDP ASSOCIATE is supported
nc -u -x localhost:1080 8.8.8.8 53
```

### 2. Check App Logs

Enable verbose logging in the app:
1. Open SSH Tunnel Proxy
2. Go to Settings → Logging
3. Set log level to "Verbose"
4. Try making a video call
5. Go to Settings → Export Logs

Look for these key messages:

✅ **Success indicators:**
```
UDP ASSOCIATE connection established
UDP ASSOCIATE handshake successful: relay endpoint
Sent UDP datagram to relay endpoint
Received UDP datagram from relay
```

❌ **Failure indicators:**
```
SOCKS5 UDP ASSOCIATE failed: Command not supported (code: 0x07)
UDP ASSOCIATE handshake failed
Failed to establish UDP ASSOCIATE connection
```

### 3. Test with Known Working App

Test with a simple UDP application first:
1. Install a DNS lookup app
2. Connect to VPN
3. Perform DNS lookup
4. Check if it works through the tunnel

## Common Issues and Solutions

### Issue 1: "Command not supported" (Error 0x07)

**Symptom:** Video calls don't work, logs show error code 0x07

**Cause:** SSH server doesn't support UDP ASSOCIATE

**Solutions:**

1. **Use OpenSSH** (recommended):
   ```bash
   # OpenSSH supports UDP ASSOCIATE by default
   ssh -D 1080 user@server
   ```

2. **Install Dante SOCKS server**:
   ```bash
   # On Ubuntu/Debian
   sudo apt-get install dante-server
   
   # Configure /etc/danted.conf
   # See: https://www.inet.no/dante/doc/latest/config/server.html
   ```

3. **Use Shadowsocks** (alternative):
   ```bash
   # Install shadowsocks-libev
   sudo apt-get install shadowsocks-libev
   
   # Configure with UDP relay enabled
   ```

### Issue 2: Video Calls Connect But No Audio/Video

**Symptom:** Call connects but no media streams

**Possible Causes:**
- Firewall blocking UDP ports
- NAT traversal issues
- Packet loss or high latency

**Solutions:**

1. **Check firewall rules on server:**
   ```bash
   # Allow common WebRTC ports
   sudo ufw allow 3478:3479/udp  # STUN/TURN
   sudo ufw allow 49152:65535/udp  # WebRTC media
   ```

2. **Test network quality:**
   ```bash
   # Check latency
   ping your-server.com
   
   # Check packet loss
   mtr your-server.com
   ```

3. **Try different network:**
   - Switch between WiFi and mobile data
   - Try a different WiFi network
   - Check if mobile carrier blocks UDP

### Issue 3: Calls Work Initially Then Drop

**Symptom:** Video call starts working but drops after 1-2 minutes

**Possible Causes:**
- Connection timeout
- Idle connection cleanup
- Network instability

**Solutions:**

1. **Check connection timeout settings:**
   - App uses 2-minute idle timeout by default
   - Active calls should keep connection alive
   - Check logs for "Connection cleanup" messages

2. **Verify network stability:**
   ```bash
   # Monitor connection
   watch -n 1 'ss -tunap | grep 1080'
   ```

3. **Check server keep-alive:**
   ```bash
   # In ~/.ssh/config
   Host your-server
       ServerAliveInterval 60
       ServerAliveCountMax 3
   ```

### Issue 4: High Latency or Choppy Audio

**Symptom:** Video calls work but quality is poor

**Possible Causes:**
- High network latency
- Bandwidth limitations
- Server overload

**Solutions:**

1. **Measure latency:**
   ```bash
   # Check round-trip time
   ping -c 10 your-server.com
   
   # Should be < 100ms for good quality
   ```

2. **Check bandwidth:**
   ```bash
   # Test download speed
   wget -O /dev/null http://speedtest.tele2.net/10MB.zip
   
   # Test upload speed
   curl -T largefile.bin ftp://speedtest.tele2.net/upload/
   ```

3. **Optimize server:**
   - Use server closer to your location
   - Ensure server has adequate bandwidth
   - Check server CPU/memory usage

### Issue 5: Some Apps Work, Others Don't

**Symptom:** WhatsApp works but Zoom doesn't (or vice versa)

**Possible Causes:**
- App-specific protocols
- Different port ranges
- App exclusion settings

**Solutions:**

1. **Check app exclusions:**
   - Settings → App Routing
   - Ensure video calling apps are NOT excluded
   - Remove any exclusions for calling apps

2. **Check app permissions:**
   - Settings → Apps → [App Name] → Permissions
   - Ensure microphone and camera permissions granted

3. **Try different apps:**
   - Test with multiple calling apps
   - Helps identify if issue is app-specific or general

### Issue 6: DNS Leaks

**Symptom:** DNS queries not going through tunnel

**Possible Causes:**
- VPN not routing DNS
- System DNS cache
- App using hardcoded DNS

**Solutions:**

1. **Test for DNS leaks:**
   - Visit https://dnsleaktest.com
   - Should show your server's location, not your real location

2. **Clear DNS cache:**
   ```bash
   # On Android (requires root)
   adb shell
   su
   ndc resolver flushdefaultif
   ```

3. **Check VPN DNS settings:**
   - App should automatically configure DNS
   - Check logs for "DNS server" messages

## Advanced Diagnostics

### Packet Capture

Capture packets to analyze traffic:

```bash
# On server, capture SOCKS5 traffic
sudo tcpdump -i any -n port 1080 -w socks5.pcap

# Analyze with Wireshark
wireshark socks5.pcap
```

Look for:
- SOCKS5 handshake (VER=0x05, CMD=0x03)
- UDP ASSOCIATE response (REP=0x00)
- Encapsulated UDP packets

### Server-Side Logs

Check SSH server logs:

```bash
# On server
sudo tail -f /var/log/auth.log

# Look for SOCKS5 connection messages
grep -i socks /var/log/auth.log
```

### Network Monitoring

Monitor network connections:

```bash
# On Android device (requires root)
adb shell
su
netstat -anp | grep -E '(1080|UDP)'

# Check active UDP connections
ss -u -a
```

## Performance Optimization

### Reduce Latency

1. **Use nearby server:**
   - Choose server geographically close to you
   - Reduces round-trip time

2. **Optimize SSH connection:**
   ```bash
   # In ~/.ssh/config
   Host your-server
       Compression yes
       CompressionLevel 6
       TCPKeepAlive yes
   ```

3. **Use faster network:**
   - 5G > 4G > 3G
   - Wired > WiFi
   - 5GHz WiFi > 2.4GHz WiFi

### Reduce Bandwidth Usage

1. **Lower video quality:**
   - In calling app settings
   - Reduces bandwidth requirements

2. **Disable video:**
   - Use voice-only calls
   - Significantly reduces bandwidth

3. **Close other apps:**
   - Reduces background traffic
   - More bandwidth for calls

## Testing Checklist

Before reporting an issue, verify:

- [ ] SSH server supports UDP ASSOCIATE
- [ ] Firewall allows UDP traffic
- [ ] App has necessary permissions
- [ ] VPN is connected and active
- [ ] DNS is not leaking
- [ ] Network connection is stable
- [ ] Server has adequate bandwidth
- [ ] Logs show UDP ASSOCIATE success
- [ ] Tested with multiple apps
- [ ] Tested on different networks

## Getting Help

If you've tried everything and still have issues:

1. **Export logs:**
   - Settings → Export Logs
   - Include in bug report

2. **Provide details:**
   - Android version
   - App version
   - SSH server type and version
   - Network type (WiFi/4G/5G)
   - Calling app and version
   - Error messages from logs

3. **Create issue:**
   - GitHub: https://github.com/yourusername/ssh-tunnel-proxy/issues
   - Include all information above

## Reference

### SOCKS5 Error Codes

| Code | Meaning | Common Cause |
|------|---------|--------------|
| 0x00 | Success | - |
| 0x01 | General failure | Server error |
| 0x02 | Connection not allowed | Firewall/ACL |
| 0x03 | Network unreachable | Routing issue |
| 0x04 | Host unreachable | Destination down |
| 0x05 | Connection refused | Port closed |
| 0x06 | TTL expired | Timeout |
| 0x07 | Command not supported | No UDP ASSOCIATE |
| 0x08 | Address type not supported | IPv6 on IPv4-only |

### Common Ports

| Port Range | Protocol | Usage |
|------------|----------|-------|
| 53 | UDP | DNS queries |
| 3478-3479 | UDP | STUN/TURN (NAT traversal) |
| 5060-5061 | UDP/TCP | SIP (VoIP signaling) |
| 16384-32767 | UDP | RTP (media streams) |
| 49152-65535 | UDP | WebRTC media |

### Useful Commands

```bash
# Test SOCKS5 proxy
curl --socks5 localhost:1080 https://ifconfig.me

# Test UDP through SOCKS5
nc -u -x localhost:1080 8.8.8.8 53

# Check open ports
nmap -sU -p 3478-3479,49152-65535 your-server.com

# Monitor bandwidth
iftop -i tun0

# Check connection stats
ss -s
```

## Additional Resources

- [RFC 1928: SOCKS Protocol Version 5](https://www.rfc-editor.org/rfc/rfc1928)
- [OpenSSH Documentation](https://www.openssh.com/manual.html)
- [WebRTC Troubleshooting](https://webrtc.org/getting-started/testing)
- [STUN/TURN Servers](https://www.stunprotocol.org/)
