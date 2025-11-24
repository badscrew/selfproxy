# PR/FAQ: SSH Tunnel Proxy for Android

## Press Release

**FOR IMMEDIATE RELEASE**

### Introducing SSH Tunnel Proxy: Your Personal VPN, Your Rules

**[City, Date]** — Today we're launching SSH Tunnel Proxy, an Android application that puts you in complete control of your mobile internet privacy. Unlike traditional VPN services that require monthly subscriptions and trust in third-party providers, SSH Tunnel Proxy lets you use your own SSH server as a secure SOCKS5 proxy.

**Your Server, Your Privacy**

SSH Tunnel Proxy is built on a simple principle: you shouldn't have to trust strangers with your internet traffic. If you already have access to an SSH server—whether it's a cloud instance, your home server, or a work machine—you can now use it as a secure tunnel for all your Android device's internet traffic.

**How It Works**

Simply provide your SSH server address and private key, and SSH Tunnel Proxy handles the rest. The app establishes a secure SSH tunnel and creates a local SOCKS5 proxy that routes your traffic through your server. No middlemen, no data collection, no subscription fees.

**Key Features**

- **Bring Your Own Server**: Use any SSH server you control or have access to
- **Multiple Profiles**: Save and switch between different SSH servers instantly
- **Per-App Routing**: Choose which apps use the tunnel (requires root or VPN mode)
- **Connection Monitoring**: Real-time bandwidth usage and connection status
- **Auto-Reconnect**: Seamless reconnection when switching networks
- **Open Source**: Full transparency with publicly available source code

**Who It's For**

SSH Tunnel Proxy is perfect for developers, system administrators, privacy enthusiasts, and anyone who already has SSH server access and wants to leverage it for mobile privacy without paying for yet another VPN subscription.

**Availability**

SSH Tunnel Proxy is available now on Google Play Store and as a direct APK download. The app is free and open source under the MIT license.

For more information, visit [website] or download from [Play Store link].

---

## Frequently Asked Questions

### General Questions

**Q: What is SSH Tunnel Proxy?**

A: SSH Tunnel Proxy is an Android app that creates a SOCKS5 proxy using SSH tunneling. It allows you to route your device's internet traffic through your own SSH server, giving you control over your privacy without relying on commercial VPN providers.

**Q: How is this different from a VPN app?**

A: Traditional VPN apps connect you to servers owned by the VPN company. SSH Tunnel Proxy lets you use your own SSH server, meaning you control where your traffic goes and who has access to it. You're not trusting a third party with your data.

**Q: Do I need to pay for a subscription?**

A: No. The app itself is free. However, you'll need access to an SSH server, which might be a cloud server you rent (typically $3-10/month), your home server, or a server provided by your workplace or school.

**Q: Is this legal?**

A: Yes. Using SSH tunnels is a standard networking practice. However, you're responsible for complying with your SSH server provider's terms of service and local laws regarding internet usage.

### Technical Questions

**Q: What SSH authentication methods are supported?**

A: The app supports private key authentication only (RSA, ECDSA, Ed25519), including keys protected with passphrases. Password authentication is not supported to encourage secure authentication practices.

**Q: Does this require root access?**

A: No. The app works without root by using Android's VPN API to route traffic through the SOCKS5 proxy. However, root access enables additional features like per-app proxying without VPN mode.

**Q: What happens if my SSH connection drops?**

A: The app includes auto-reconnect functionality. If the connection drops, it will automatically attempt to reconnect. You can configure retry intervals and behavior in settings.

**Q: Can I use this with any SSH server?**

A: Yes, as long as you have SSH access (port 22 or custom port) and the server allows port forwarding. Most standard SSH servers work out of the box.

**Q: Does this work on mobile data and WiFi?**

A: Yes. The app works on any network connection. It will automatically reconnect when you switch between WiFi and mobile data.

**Q: What's the performance impact?**

A: Performance depends on your SSH server's location and bandwidth. Expect some latency increase based on the distance to your server. Battery usage is comparable to other VPN apps.

### Privacy & Security Questions

**Q: Does the app collect any data?**

A: No. The app doesn't collect, store, or transmit any user data except what's necessary to establish the SSH connection to your server. Your SSH credentials are stored encrypted on your device only.

**Q: Can you see my SSH credentials or traffic?**

A: No. Everything stays on your device and your server. We never see your credentials, server addresses, or traffic. The app is open source, so you can verify this yourself.

**Q: Is my traffic encrypted?**

A: Yes. Traffic between your device and your SSH server is encrypted using SSH protocol. However, traffic from your SSH server to the final destination follows standard internet routing (encrypted if the destination uses HTTPS).

**Q: What if I don't trust the app?**

A: The app is fully open source. You can review the code, build it yourself, or have a security expert audit it. We encourage transparency and welcome security reviews.

### Setup & Usage Questions

**Q: How do I get started?**

A: You'll need:
1. An SSH server (cloud instance, home server, etc.)
2. SSH credentials (username and private key)
3. The SSH Tunnel Proxy app

Enter your server details in the app, tap connect, and you're secured.

**Q: I don't have an SSH server. Where can I get one?**

A: You can rent a cloud server from providers like DigitalOcean, Linode, AWS, Google Cloud, or Vultr. A basic instance costs $3-10/month. Alternatively, you can set up an SSH server on your home computer or Raspberry Pi.

**Q: Can I save multiple server profiles?**

A: Yes. You can save unlimited server profiles and switch between them with a single tap. This is useful if you have servers in different locations or for different purposes.

**Q: Can I route only specific apps through the tunnel?**

A: Yes, with limitations. In VPN mode (non-root), you can exclude apps from the tunnel. With root access, you have more granular per-app control.

**Q: Does this work with IPv6?**

A: IPv6 support depends on your SSH server configuration. The app supports IPv6 if your server does.

**Q: Will you support HTTP proxy in addition to SOCKS5?**

A: Currently, the app uses SOCKS5 proxy, which works well with SSH's native dynamic port forwarding. HTTP proxy support is being considered for future releases to provide additional compatibility options for specific use cases.

### Troubleshooting Questions

**Q: I can't connect to my server. What should I check?**

A: Verify:
- Server address and port are correct
- Your credentials are valid
- The server allows SSH connections from your IP
- Port forwarding is enabled on the server (check sshd_config)
- Firewall rules allow the connection

**Q: The connection keeps dropping. How do I fix this?**

A: Try:
- Enabling keep-alive packets in settings
- Increasing the reconnect timeout
- Checking your server's SSH timeout settings
- Ensuring your network isn't blocking SSH traffic

**Q: Some apps don't work with the proxy. Why?**

A: Some apps detect proxy usage and refuse to work, or they may use protocols that don't work well with SOCKS5. You can exclude these apps from the tunnel in settings.

**Q: Is there a desktop version?**

A: Currently, SSH Tunnel Proxy is Android-only. However, desktop operating systems have built-in SSH clients that can create similar tunnels using command-line tools.

---

## Customer Quotes

*"As a developer with multiple cloud servers, this app is perfect. I'm already paying for servers—why would I pay for a VPN too?"* — Alex M., Software Engineer

*"Finally, a VPN solution I can actually trust. I control the server, I control my data."* — Sarah K., Privacy Advocate

*"I set up a Raspberry Pi at home as my SSH server. Now I can access my home network securely from anywhere, and it doubles as my mobile VPN."* — James T., System Administrator

*"The per-app routing is a game changer. I can tunnel just my browser while keeping other apps on the regular connection."* — Maria G., Security Researcher
