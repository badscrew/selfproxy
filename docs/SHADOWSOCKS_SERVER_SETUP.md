# Shadowsocks Server Setup Guide

This guide provides step-by-step instructions for setting up a Shadowsocks server on Ubuntu Linux to use with the SSH Tunnel Proxy Android application.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Installation](#quick-installation)
- [Manual Installation](#manual-installation)
- [Configuration](#configuration)
- [Cipher Selection](#cipher-selection)
- [Password Generation](#password-generation)
- [Firewall Configuration](#firewall-configuration)
- [Starting the Service](#starting-the-service)
- [Troubleshooting](#troubleshooting)
- [Security Best Practices](#security-best-practices)

## Prerequisites

- Ubuntu 20.04 LTS or newer (Ubuntu 22.04 LTS recommended)
- Root or sudo access
- A public IP address or domain name
- Open port for Shadowsocks (default: 8388)

## Quick Installation

For a quick setup with secure defaults, use our automated installation script:

```bash
# Download the installation script
wget https://raw.githubusercontent.com/your-repo/ssh-tunnel-proxy/main/scripts/install-shadowsocks.sh

# Make it executable
chmod +x install-shadowsocks.sh

# Run the script
sudo ./install-shadowsocks.sh
```

The script will:
- Install shadowsocks-libev
- Generate a secure random password
- Configure with recommended cipher (chacha20-ietf-poly1305)
- Set up firewall rules
- Enable and start the service
- Display connection details

**Save the connection details shown at the end - you'll need them for the Android app!**

## Manual Installation

If you prefer to install manually or want more control:

### Step 1: Update System

```bash
sudo apt update
sudo apt upgrade -y
```

### Step 2: Install Shadowsocks-libev

```bash
# Install from Ubuntu repositories
sudo apt install shadowsocks-libev -y
```

For the latest version, you can use the official PPA:

```bash
# Add PPA (optional, for latest version)
sudo add-apt-repository ppa:max-c-lv/shadowsocks-libev -y
sudo apt update
sudo apt install shadowsocks-libev -y
```

### Step 3: Verify Installation

```bash
ss-server -h
```

You should see the Shadowsocks server help message.

## Configuration

### Create Configuration File

Create or edit the configuration file at `/etc/shadowsocks-libev/config.json`:

```bash
sudo nano /etc/shadowsocks-libev/config.json
```

### Basic Configuration

```json
{
    "server": "0.0.0.0",
    "server_port": 8388,
    "password": "YOUR_SECURE_PASSWORD_HERE",
    "timeout": 300,
    "method": "chacha20-ietf-poly1305",
    "fast_open": true,
    "nameserver": "8.8.8.8",
    "mode": "tcp_and_udp"
}
```

### Configuration Parameters Explained

- **server**: `0.0.0.0` listens on all interfaces
- **server_port**: Port number (1024-65535, default 8388)
- **password**: Your secure password (see Password Generation section)
- **timeout**: Connection timeout in seconds
- **method**: Encryption cipher (see Cipher Selection section)
- **fast_open**: Enable TCP Fast Open for better performance
- **nameserver**: DNS server for resolving domain names
- **mode**: Support both TCP and UDP traffic

## Cipher Selection

The Android app supports three modern AEAD ciphers. Choose one based on your needs:

### Recommended: chacha20-ietf-poly1305

```json
"method": "chacha20-ietf-poly1305"
```

**Best for**: Mobile devices, ARM processors
- Excellent performance on mobile CPUs
- Strong security (256-bit)
- Lower battery consumption
- **Recommended for most users**

### Alternative: aes-256-gcm

```json
"method": "aes-256-gcm"
```

**Best for**: Servers with AES-NI hardware acceleration
- Fastest on x86 CPUs with AES-NI
- Strong security (256-bit)
- Industry standard encryption

### Alternative: aes-128-gcm

```json
"method": "aes-128-gcm"
```

**Best for**: Maximum performance with good security
- Faster than AES-256
- Good security (128-bit)
- Lower resource usage

**Important**: The cipher you choose here must match the cipher selected in the Android app!

## Password Generation

**Never use a weak or common password!** Generate a strong random password:

### Using OpenSSL (Recommended)

```bash
# Generate a 32-character random password
openssl rand -base64 32
```

Example output: `7K9mP2xQ5vR8wT1nY4jL6hF3gD0sA9zX`

### Using /dev/urandom

```bash
# Generate a 24-character alphanumeric password
tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24 ; echo
```

### Password Requirements

- Minimum 16 characters
- Mix of uppercase, lowercase, numbers
- Avoid special characters that might cause issues
- Store securely (password manager recommended)

## Firewall Configuration

### Using UFW (Ubuntu Firewall)

```bash
# Enable UFW if not already enabled
sudo ufw enable

# Allow SSH (important - don't lock yourself out!)
sudo ufw allow 22/tcp

# Allow Shadowsocks port (adjust if you changed the port)
sudo ufw allow 8388/tcp
sudo ufw allow 8388/udp

# Check status
sudo ufw status
```

### Using iptables

```bash
# Allow Shadowsocks TCP
sudo iptables -A INPUT -p tcp --dport 8388 -j ACCEPT

# Allow Shadowsocks UDP
sudo iptables -A INPUT -p udp --dport 8388 -j ACCEPT

# Save rules
sudo netfilter-persistent save
```

### Cloud Provider Firewall

If using a cloud provider (AWS, Google Cloud, DigitalOcean, etc.), also configure their firewall:

**AWS Security Groups:**
- Add inbound rule: TCP port 8388, source 0.0.0.0/0
- Add inbound rule: UDP port 8388, source 0.0.0.0/0

**Google Cloud Firewall:**
```bash
gcloud compute firewall-rules create shadowsocks \
    --allow tcp:8388,udp:8388 \
    --source-ranges 0.0.0.0/0
```

**DigitalOcean:**
- Go to Networking → Firewalls
- Add inbound rules for TCP and UDP on port 8388

## Starting the Service

### Enable and Start Shadowsocks

```bash
# Enable service to start on boot
sudo systemctl enable shadowsocks-libev

# Start the service
sudo systemctl start shadowsocks-libev

# Check status
sudo systemctl status shadowsocks-libev
```

### Verify Service is Running

```bash
# Check if service is active
sudo systemctl is-active shadowsocks-libev

# Check if port is listening
sudo ss -tulpn | grep 8388
```

You should see output showing the service listening on port 8388.

### View Logs

```bash
# View recent logs
sudo journalctl -u shadowsocks-libev -n 50

# Follow logs in real-time
sudo journalctl -u shadowsocks-libev -f
```

## Troubleshooting

### Service Won't Start

**Check configuration syntax:**
```bash
# Validate JSON syntax
cat /etc/shadowsocks-libev/config.json | python3 -m json.tool
```

**Check logs for errors:**
```bash
sudo journalctl -u shadowsocks-libev -n 100 --no-pager
```

**Common issues:**
- Invalid JSON syntax (missing comma, bracket)
- Port already in use
- Invalid cipher method name
- Permission issues with config file

### Port Already in Use

```bash
# Check what's using the port
sudo lsof -i :8388

# Kill the process if needed
sudo kill -9 <PID>
```

### Can't Connect from Android App

**1. Verify server is listening:**
```bash
sudo ss -tulpn | grep 8388
```

**2. Test from another machine:**
```bash
# Install shadowsocks client
sudo apt install shadowsocks-libev

# Test connection
ss-local -s YOUR_SERVER_IP -p 8388 -k YOUR_PASSWORD -m chacha20-ietf-poly1305 -l 1080
```

**3. Check firewall:**
```bash
# UFW status
sudo ufw status verbose

# Test if port is accessible
nc -zv YOUR_SERVER_IP 8388
```

**4. Verify cloud provider firewall** (if applicable)

### High CPU Usage

- Consider using `aes-256-gcm` if your CPU has AES-NI
- Check for unusual traffic patterns
- Monitor with `htop` or `top`

### Connection Drops

**Increase timeout:**
```json
"timeout": 600
```

**Enable TCP Fast Open:**
```json
"fast_open": true
```

**Check system limits:**
```bash
# Increase file descriptor limit
echo "* soft nofile 51200" | sudo tee -a /etc/security/limits.conf
echo "* hard nofile 51200" | sudo tee -a /etc/security/limits.conf
```

## Security Best Practices

### 1. Use Strong Passwords

- Generate random passwords (see Password Generation section)
- Never reuse passwords
- Store in a password manager

### 2. Keep Software Updated

```bash
# Regular updates
sudo apt update && sudo apt upgrade -y

# Enable automatic security updates
sudo apt install unattended-upgrades -y
sudo dpkg-reconfigure -plow unattended-upgrades
```

### 3. Change Default Port

Using a non-standard port can reduce automated attacks:

```json
"server_port": 9876
```

Don't forget to update firewall rules!

### 4. Monitor Logs

```bash
# Set up log monitoring
sudo journalctl -u shadowsocks-libev -f
```

### 5. Limit Access (Optional)

If you only connect from specific IPs, restrict access:

```bash
# Allow only from specific IP
sudo ufw allow from YOUR_HOME_IP to any port 8388
```

### 6. Enable Fail2Ban (Optional)

```bash
sudo apt install fail2ban -y
```

### 7. Use a Firewall

Always run a firewall (UFW or iptables) with default deny policy.

## Testing Your Setup

### From Your Computer

Install a Shadowsocks client and test:

**Linux:**
```bash
sudo apt install shadowsocks-libev
ss-local -s YOUR_SERVER_IP -p 8388 -k YOUR_PASSWORD -m chacha20-ietf-poly1305 -l 1080
```

**macOS:**
```bash
brew install shadowsocks-libev
ss-local -s YOUR_SERVER_IP -p 8388 -k YOUR_PASSWORD -m chacha20-ietf-poly1305 -l 1080
```

**Windows:**
Download Shadowsocks-Windows from GitHub

### Test Connection

```bash
# Test with curl through SOCKS5 proxy
curl --socks5 127.0.0.1:1080 https://ifconfig.me
```

You should see your server's IP address.

## Connection Details for Android App

After setup, you'll need these details for the Android app:

- **Server Address**: Your server's IP or domain name
- **Server Port**: 8388 (or your custom port)
- **Password**: Your generated password
- **Encryption Method**: chacha20-ietf-poly1305 (or your chosen cipher)

## Performance Optimization

### Enable BBR Congestion Control

```bash
# Check if BBR is available
modprobe tcp_bbr
echo "tcp_bbr" | sudo tee -a /etc/modules-load.d/modules.conf

# Enable BBR
echo "net.core.default_qdisc=fq" | sudo tee -a /etc/sysctl.conf
echo "net.ipv4.tcp_congestion_control=bbr" | sudo tee -a /etc/sysctl.conf

# Apply changes
sudo sysctl -p
```

### Increase System Limits

```bash
# Edit sysctl.conf
sudo nano /etc/sysctl.conf
```

Add these lines:

```
fs.file-max = 51200
net.core.rmem_max = 67108864
net.core.wmem_max = 67108864
net.ipv4.tcp_rmem = 4096 87380 67108864
net.ipv4.tcp_wmem = 4096 65536 67108864
net.ipv4.tcp_mtu_probing = 1
```

Apply changes:

```bash
sudo sysctl -p
```

## Maintenance

### Regular Tasks

**Weekly:**
- Check logs for errors
- Monitor resource usage

**Monthly:**
- Update system packages
- Review firewall rules
- Rotate passwords (optional)

### Backup Configuration

```bash
# Backup config
sudo cp /etc/shadowsocks-libev/config.json ~/shadowsocks-config-backup.json
```

## Additional Resources

- [Shadowsocks Official Documentation](https://shadowsocks.org/)
- [shadowsocks-libev GitHub](https://github.com/shadowsocks/shadowsocks-libev)
- [Ubuntu Server Guide](https://ubuntu.com/server/docs)

## Support

If you encounter issues:

1. Check the Troubleshooting section above
2. Review server logs: `sudo journalctl -u shadowsocks-libev`
3. Test connectivity from another machine
4. Verify firewall configuration
5. Check the project's GitHub issues page

## Next Steps

Once your server is set up:

1. Note your connection details (server, port, password, cipher)
2. Install the SSH Tunnel Proxy Android app
3. Create a new profile with your server details
4. Test the connection
5. Enjoy secure browsing!
