# Shadowsocks Quick Reference

Quick reference guide for common Shadowsocks server operations.

## Service Management

```bash
# Start service
sudo systemctl start shadowsocks-libev

# Stop service
sudo systemctl stop shadowsocks-libev

# Restart service
sudo systemctl restart shadowsocks-libev

# Check status
sudo systemctl status shadowsocks-libev

# Enable auto-start on boot
sudo systemctl enable shadowsocks-libev

# Disable auto-start
sudo systemctl disable shadowsocks-libev
```

## Logs and Monitoring

```bash
# View recent logs
sudo journalctl -u shadowsocks-libev -n 50

# Follow logs in real-time
sudo journalctl -u shadowsocks-libev -f

# View logs since boot
sudo journalctl -u shadowsocks-libev -b

# View logs for specific time period
sudo journalctl -u shadowsocks-libev --since "1 hour ago"
```

## Configuration

```bash
# Edit configuration
sudo nano /etc/shadowsocks-libev/config.json

# Validate JSON syntax
cat /etc/shadowsocks-libev/config.json | python3 -m json.tool

# View current configuration
sudo cat /etc/shadowsocks-libev/config.json

# Backup configuration
sudo cp /etc/shadowsocks-libev/config.json ~/shadowsocks-backup.json
```

## Network Diagnostics

```bash
# Check if port is listening
sudo ss -tulpn | grep 8388

# Check if service is running
sudo systemctl is-active shadowsocks-libev

# Test port connectivity from another machine
nc -zv YOUR_SERVER_IP 8388

# Check firewall rules
sudo ufw status

# View all listening ports
sudo netstat -tulpn
```

## Firewall Management

```bash
# Allow Shadowsocks port (UFW)
sudo ufw allow 8388/tcp
sudo ufw allow 8388/udp

# Remove firewall rule
sudo ufw delete allow 8388/tcp
sudo ufw delete allow 8388/udp

# Check firewall status
sudo ufw status verbose

# Enable firewall
sudo ufw enable

# Disable firewall (not recommended)
sudo ufw disable
```

## Performance Monitoring

```bash
# Monitor CPU and memory usage
htop

# Check network connections
sudo ss -s

# Monitor bandwidth usage
sudo iftop

# Check system resources
vmstat 1

# Monitor disk I/O
iostat -x 1
```

## Common Configuration Changes

### Change Port

Edit `/etc/shadowsocks-libev/config.json`:
```json
{
    "server_port": 9876
}
```

Then restart:
```bash
sudo systemctl restart shadowsocks-libev
sudo ufw allow 9876/tcp
sudo ufw allow 9876/udp
```

### Change Password

Edit `/etc/shadowsocks-libev/config.json`:
```json
{
    "password": "your_new_password"
}
```

Then restart:
```bash
sudo systemctl restart shadowsocks-libev
```

### Change Cipher

Edit `/etc/shadowsocks-libev/config.json`:
```json
{
    "method": "aes-256-gcm"
}
```

Then restart:
```bash
sudo systemctl restart shadowsocks-libev
```

### Increase Timeout

Edit `/etc/shadowsocks-libev/config.json`:
```json
{
    "timeout": 600
}
```

Then restart:
```bash
sudo systemctl restart shadowsocks-libev
```

## Troubleshooting Commands

### Service Won't Start

```bash
# Check for errors
sudo journalctl -u shadowsocks-libev -n 100 --no-pager

# Validate config
cat /etc/shadowsocks-libev/config.json | python3 -m json.tool

# Check if port is already in use
sudo lsof -i :8388

# Test config manually
ss-server -c /etc/shadowsocks-libev/config.json -v
```

### Can't Connect from Client

```bash
# Verify service is running
sudo systemctl status shadowsocks-libev

# Check if port is listening
sudo ss -tulpn | grep 8388

# Check firewall
sudo ufw status

# Test from server itself
ss-local -s 127.0.0.1 -p 8388 -k YOUR_PASSWORD -m chacha20-ietf-poly1305 -l 1080

# Check for connection attempts in logs
sudo journalctl -u shadowsocks-libev -f
```

### High CPU Usage

```bash
# Check process CPU usage
top -p $(pgrep ss-server)

# Check connections
sudo ss -s

# Consider changing cipher to AES-GCM if CPU has AES-NI
grep aes /proc/cpuinfo
```

### Memory Issues

```bash
# Check memory usage
free -h

# Check Shadowsocks memory usage
ps aux | grep ss-server

# Monitor memory over time
watch -n 1 free -h
```

## Security Checks

```bash
# Check for failed authentication attempts
sudo journalctl -u shadowsocks-libev | grep -i "error\|fail"

# Monitor active connections
watch -n 1 'sudo ss -tulpn | grep 8388'

# Check system security updates
sudo apt update
sudo apt list --upgradable

# Verify firewall is active
sudo ufw status

# Check for listening services
sudo netstat -tulpn
```

## Backup and Restore

### Backup

```bash
# Backup configuration
sudo cp /etc/shadowsocks-libev/config.json ~/shadowsocks-backup-$(date +%Y%m%d).json

# Backup entire directory
sudo tar -czf ~/shadowsocks-backup-$(date +%Y%m%d).tar.gz /etc/shadowsocks-libev/
```

### Restore

```bash
# Restore configuration
sudo cp ~/shadowsocks-backup.json /etc/shadowsocks-libev/config.json
sudo systemctl restart shadowsocks-libev

# Restore from tar
sudo tar -xzf ~/shadowsocks-backup.tar.gz -C /
sudo systemctl restart shadowsocks-libev
```

## Update Shadowsocks

```bash
# Update package list
sudo apt update

# Upgrade shadowsocks-libev
sudo apt upgrade shadowsocks-libev

# Check version
ss-server -h | head -n 1

# Restart after update
sudo systemctl restart shadowsocks-libev
```

## Uninstall

```bash
# Stop and disable service
sudo systemctl stop shadowsocks-libev
sudo systemctl disable shadowsocks-libev

# Remove package
sudo apt remove shadowsocks-libev

# Remove configuration
sudo rm -rf /etc/shadowsocks-libev/

# Remove firewall rules
sudo ufw delete allow 8388/tcp
sudo ufw delete allow 8388/udp
```

## Quick Diagnostics Script

Save this as `check-shadowsocks.sh`:

```bash
#!/bin/bash

echo "=== Shadowsocks Server Diagnostics ==="
echo ""

echo "Service Status:"
systemctl is-active shadowsocks-libev && echo "✓ Running" || echo "✗ Not running"
echo ""

echo "Port Status:"
ss -tulpn | grep 8388 && echo "✓ Port listening" || echo "✗ Port not listening"
echo ""

echo "Recent Errors:"
journalctl -u shadowsocks-libev -n 10 --no-pager | grep -i error || echo "✓ No recent errors"
echo ""

echo "Firewall Status:"
ufw status | grep 8388 && echo "✓ Firewall configured" || echo "⚠ Firewall may not be configured"
echo ""

echo "Configuration:"
cat /etc/shadowsocks-libev/config.json | python3 -m json.tool && echo "✓ Valid JSON" || echo "✗ Invalid JSON"
```

Run with:
```bash
chmod +x check-shadowsocks.sh
sudo ./check-shadowsocks.sh
```

## Getting Help

If you're still having issues:

1. Check the [full setup guide](SHADOWSOCKS_SERVER_SETUP.md)
2. Review logs: `sudo journalctl -u shadowsocks-libev -n 100`
3. Test configuration: `cat /etc/shadowsocks-libev/config.json | python3 -m json.tool`
4. Verify firewall: `sudo ufw status`
5. Check GitHub issues for similar problems

## Useful Links

- [Shadowsocks Official Site](https://shadowsocks.org/)
- [shadowsocks-libev GitHub](https://github.com/shadowsocks/shadowsocks-libev)
- [Ubuntu Server Guide](https://ubuntu.com/server/docs)
- [UFW Documentation](https://help.ubuntu.com/community/UFW)
