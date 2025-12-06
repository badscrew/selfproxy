#!/bin/bash

################################################################################
# Shadowsocks Server Installation Script
# 
# This script automates the installation and configuration of shadowsocks-libev
# on Ubuntu Linux with secure defaults.
#
# Usage: sudo ./install-shadowsocks.sh
#
# Requirements:
# - Ubuntu 20.04 LTS or newer
# - Root or sudo privileges
# - Internet connection
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration variables
DEFAULT_PORT=8388
DEFAULT_CIPHER="chacha20-ietf-poly1305"
CONFIG_FILE="/etc/shadowsocks-libev/config.json"

################################################################################
# Helper Functions
################################################################################

print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

check_root() {
    if [[ $EUID -ne 0 ]]; then
        print_error "This script must be run as root or with sudo"
        exit 1
    fi
}

check_ubuntu() {
    if [[ ! -f /etc/os-release ]]; then
        print_error "Cannot detect OS version"
        exit 1
    fi
    
    . /etc/os-release
    
    if [[ "$ID" != "ubuntu" ]]; then
        print_warning "This script is designed for Ubuntu. Your OS: $ID"
        read -p "Continue anyway? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
    
    print_success "OS detected: $PRETTY_NAME"
}

generate_password() {
    # Generate a secure 32-character password
    openssl rand -base64 32 | tr -d "=+/" | cut -c1-32
}

get_public_ip() {
    # Try multiple services to get public IP
    local ip=""
    
    ip=$(curl -s -4 ifconfig.me) || \
    ip=$(curl -s -4 icanhazip.com) || \
    ip=$(curl -s -4 ipinfo.io/ip) || \
    ip=$(wget -qO- -4 ifconfig.me)
    
    echo "$ip"
}

################################################################################
# Installation Functions
################################################################################

update_system() {
    print_header "Updating System"
    
    apt-get update -qq
    print_success "Package list updated"
    
    apt-get upgrade -y -qq
    print_success "System upgraded"
}

install_dependencies() {
    print_header "Installing Dependencies"
    
    apt-get install -y -qq \
        software-properties-common \
        curl \
        wget \
        gnupg \
        ca-certificates
    
    print_success "Dependencies installed"
}

install_shadowsocks() {
    print_header "Installing Shadowsocks-libev"
    
    # Check if already installed
    if command -v ss-server &> /dev/null; then
        print_warning "Shadowsocks-libev is already installed"
        read -p "Reinstall? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            return 0
        fi
    fi
    
    # Install from Ubuntu repositories
    apt-get install -y shadowsocks-libev
    
    print_success "Shadowsocks-libev installed"
    
    # Verify installation
    if ! command -v ss-server &> /dev/null; then
        print_error "Installation failed - ss-server command not found"
        exit 1
    fi
    
    local version=$(ss-server -h 2>&1 | head -n 1 | grep -oP 'shadowsocks-libev \K[0-9.]+')
    print_info "Installed version: $version"
}

configure_shadowsocks() {
    print_header "Configuring Shadowsocks"
    
    # Get configuration from user or use defaults
    echo ""
    read -p "Enter server port (default: $DEFAULT_PORT): " PORT
    PORT=${PORT:-$DEFAULT_PORT}
    
    echo ""
    echo "Available ciphers:"
    echo "  1) chacha20-ietf-poly1305 (recommended for mobile)"
    echo "  2) aes-256-gcm (recommended for servers with AES-NI)"
    echo "  3) aes-128-gcm (fastest)"
    read -p "Select cipher (1-3, default: 1): " CIPHER_CHOICE
    CIPHER_CHOICE=${CIPHER_CHOICE:-1}
    
    case $CIPHER_CHOICE in
        1) CIPHER="chacha20-ietf-poly1305" ;;
        2) CIPHER="aes-256-gcm" ;;
        3) CIPHER="aes-128-gcm" ;;
        *) CIPHER="$DEFAULT_CIPHER" ;;
    esac
    
    echo ""
    read -p "Generate random password? (Y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Nn]$ ]]; then
        read -sp "Enter password: " PASSWORD
        echo
        if [[ -z "$PASSWORD" ]]; then
            print_error "Password cannot be empty"
            exit 1
        fi
    else
        PASSWORD=$(generate_password)
        print_success "Generated secure password"
    fi
    
    # Create config directory if it doesn't exist
    mkdir -p /etc/shadowsocks-libev
    
    # Backup existing config if present
    if [[ -f "$CONFIG_FILE" ]]; then
        cp "$CONFIG_FILE" "${CONFIG_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
        print_info "Existing config backed up"
    fi
    
    # Create configuration file
    cat > "$CONFIG_FILE" <<EOF
{
    "server": "0.0.0.0",
    "server_port": $PORT,
    "password": "$PASSWORD",
    "timeout": 300,
    "method": "$CIPHER",
    "fast_open": true,
    "nameserver": "8.8.8.8",
    "mode": "tcp_and_udp"
}
EOF
    
    # Set proper permissions
    chmod 600 "$CONFIG_FILE"
    
    print_success "Configuration file created: $CONFIG_FILE"
}

configure_firewall() {
    print_header "Configuring Firewall"
    
    # Check if UFW is installed
    if ! command -v ufw &> /dev/null; then
        print_info "UFW not installed, installing..."
        apt-get install -y ufw
    fi
    
    # Enable UFW if not already enabled
    if ! ufw status | grep -q "Status: active"; then
        print_warning "Enabling UFW firewall"
        # Allow SSH first to prevent lockout
        ufw allow 22/tcp
        echo "y" | ufw enable
    fi
    
    # Allow Shadowsocks port
    ufw allow $PORT/tcp
    ufw allow $PORT/udp
    
    print_success "Firewall configured for port $PORT"
    
    # Show firewall status
    print_info "Current firewall rules:"
    ufw status numbered
}

enable_bbr() {
    print_header "Enabling BBR Congestion Control"
    
    # Check if BBR is available
    if ! modprobe tcp_bbr 2>/dev/null; then
        print_warning "BBR not available on this kernel"
        return 0
    fi
    
    # Check if already enabled
    if sysctl net.ipv4.tcp_congestion_control | grep -q bbr; then
        print_info "BBR already enabled"
        return 0
    fi
    
    # Enable BBR
    echo "tcp_bbr" >> /etc/modules-load.d/modules.conf
    
    # Configure sysctl
    cat >> /etc/sysctl.conf <<EOF

# BBR Congestion Control
net.core.default_qdisc=fq
net.ipv4.tcp_congestion_control=bbr
EOF
    
    # Apply changes
    sysctl -p > /dev/null
    
    print_success "BBR congestion control enabled"
}

optimize_system() {
    print_header "Optimizing System Settings"
    
    # Backup sysctl.conf
    cp /etc/sysctl.conf /etc/sysctl.conf.backup.$(date +%Y%m%d_%H%M%S)
    
    # Add optimizations
    cat >> /etc/sysctl.conf <<EOF

# Shadowsocks Optimizations
fs.file-max = 51200
net.core.rmem_max = 67108864
net.core.wmem_max = 67108864
net.ipv4.tcp_rmem = 4096 87380 67108864
net.ipv4.tcp_wmem = 4096 65536 67108864
net.ipv4.tcp_mtu_probing = 1
EOF
    
    # Apply changes
    sysctl -p > /dev/null
    
    print_success "System optimizations applied"
}

start_service() {
    print_header "Starting Shadowsocks Service"
    
    # Stop service if running
    systemctl stop shadowsocks-libev 2>/dev/null || true
    
    # Enable service
    systemctl enable shadowsocks-libev
    print_success "Service enabled for auto-start"
    
    # Start service
    systemctl start shadowsocks-libev
    
    # Wait a moment for service to start
    sleep 2
    
    # Check if service is running
    if systemctl is-active --quiet shadowsocks-libev; then
        print_success "Shadowsocks service is running"
    else
        print_error "Failed to start Shadowsocks service"
        print_info "Checking logs..."
        journalctl -u shadowsocks-libev -n 20 --no-pager
        exit 1
    fi
    
    # Verify port is listening
    if ss -tulpn | grep -q ":$PORT"; then
        print_success "Service listening on port $PORT"
    else
        print_warning "Port $PORT may not be listening"
    fi
}

display_connection_info() {
    print_header "Installation Complete!"
    
    local public_ip=$(get_public_ip)
    
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║         Shadowsocks Server Connection Details             ║${NC}"
    echo -e "${GREEN}╠════════════════════════════════════════════════════════════╣${NC}"
    echo -e "${GREEN}║${NC}                                                            ${GREEN}║${NC}"
    echo -e "${GREEN}║${NC}  ${BLUE}Server Address:${NC}  $public_ip                           ${GREEN}║${NC}"
    echo -e "${GREEN}║${NC}  ${BLUE}Server Port:${NC}     $PORT                                ${GREEN}║${NC}"
    echo -e "${GREEN}║${NC}  ${BLUE}Password:${NC}        $PASSWORD                            ${GREEN}║${NC}"
    echo -e "${GREEN}║${NC}  ${BLUE}Cipher Method:${NC}   $CIPHER                              ${GREEN}║${NC}"
    echo -e "${GREEN}║${NC}                                                            ${GREEN}║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    print_warning "IMPORTANT: Save these connection details securely!"
    print_info "You will need them to configure the Android app"
    
    echo ""
    print_info "Configuration file: $CONFIG_FILE"
    print_info "View logs: sudo journalctl -u shadowsocks-libev -f"
    print_info "Service status: sudo systemctl status shadowsocks-libev"
    
    echo ""
    print_success "Setup complete! You can now connect from your Android device."
}

################################################################################
# Main Installation Flow
################################################################################

main() {
    clear
    
    print_header "Shadowsocks Server Installation"
    echo ""
    echo "This script will install and configure shadowsocks-libev"
    echo "on your Ubuntu server with secure defaults."
    echo ""
    
    # Pre-flight checks
    check_root
    check_ubuntu
    
    echo ""
    read -p "Continue with installation? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "Installation cancelled"
        exit 0
    fi
    
    # Installation steps
    update_system
    install_dependencies
    install_shadowsocks
    configure_shadowsocks
    configure_firewall
    enable_bbr
    optimize_system
    start_service
    
    # Display results
    display_connection_info
    
    echo ""
    print_success "Installation completed successfully!"
    
    # Save connection details to file
    local details_file="$HOME/shadowsocks-connection-details.txt"
    cat > "$details_file" <<EOF
Shadowsocks Server Connection Details
======================================

Server Address: $(get_public_ip)
Server Port: $PORT
Password: $PASSWORD
Cipher Method: $CIPHER

Configuration File: $CONFIG_FILE

Generated: $(date)
EOF
    
    chmod 600 "$details_file"
    print_info "Connection details saved to: $details_file"
}

# Run main function
main "$@"
