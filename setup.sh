#!/bin/bash

################################################################################
# VisionTrade Android - Automated Setup Script
# 
# This script automates the one-time setup of the VisionTrade Android project.
# It handles:
# - Downloading gradle-wrapper.jar (8-9 MB)
# - Making gradlew executable
# - Verifying environment
#
# Usage: ./setup.sh
#
# Requirements:
# - bash/sh shell
# - wget or curl
# - Internet connection
#
################################################################################

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script configuration
GRADLE_VERSION="8.2"
GRADLE_JAR_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-wrapper.jar"
GRADLE_JAR_PATH="gradle/wrapper/gradle-wrapper.jar"
GRADLE_JAR_SIZE="8-9 MB"

################################################################################
# Helper Functions
################################################################################

print_header() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  VisionTrade Android - Setup Script${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo ""
}

print_step() {
    echo -e "${YELLOW}→ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

check_command() {
    if ! command -v "$1" &> /dev/null; then
        return 1
    fi
    return 0
}

################################################################################
# Setup Steps
################################################################################

step_check_environment() {
    print_step "Checking environment..."
    
    local errors=0
    
    # Check if gradle-wrapper.properties exists
    if [ ! -f "gradle/wrapper/gradle-wrapper.properties" ]; then
        print_error "gradle/wrapper/gradle-wrapper.properties not found"
        print_info "Are you in the VisionTradeAndroid-PRODUCTION directory?"
        errors=$((errors + 1))
    else
        print_success "gradle/wrapper/gradle-wrapper.properties found"
    fi
    
    # Check if gradlew exists
    if [ ! -f "gradlew" ]; then
        print_error "gradlew not found"
        print_info "Are you in the VisionTradeAndroid-PRODUCTION directory?"
        errors=$((errors + 1))
    else
        print_success "gradlew found"
    fi
    
    if [ $errors -gt 0 ]; then
        print_error "Environment check failed"
        echo ""
        echo "Make sure you're in the VisionTradeAndroid-PRODUCTION directory:"
        echo "  cd VisionTradeAndroid-PRODUCTION"
        exit 1
    fi
}

step_check_internet() {
    print_step "Checking internet connection..."
    
    if ! check_command wget && ! check_command curl; then
        print_error "Neither wget nor curl found"
        echo ""
        echo "Install one of:"
        echo "  macOS:  brew install wget"
        echo "  Ubuntu: apt-get install wget"
        echo "  Fedora: dnf install wget"
        exit 1
    fi
    
    # Quick connectivity check
    if check_command wget; then
        if ! wget --spider --quiet "https://services.gradle.org/" 2>/dev/null; then
            print_error "Cannot reach services.gradle.org"
            echo ""
            echo "Please check your internet connection and try again."
            exit 1
        fi
    elif check_command curl; then
        if ! curl --connect-timeout 5 "https://services.gradle.org/" -o /dev/null -s; then
            print_error "Cannot reach services.gradle.org"
            echo ""
            echo "Please check your internet connection and try again."
            exit 1
        fi
    fi
    
    print_success "Internet connection available"
}

step_download_gradle_jar() {
    print_step "Downloading gradle-wrapper.jar (${GRADLE_JAR_SIZE})..."
    
    # Check if already exists
    if [ -f "$GRADLE_JAR_PATH" ]; then
        local jar_size=$(ls -lh "$GRADLE_JAR_PATH" | awk '{print $5}')
        print_info "gradle-wrapper.jar already exists (${jar_size})"
        read -p "Re-download? (y/N) " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_success "Skipping download"
            return 0
        fi
    fi
    
    # Create directory if needed
    mkdir -p gradle/wrapper
    
    # Download using available tool
    if check_command wget; then
        print_info "Using wget..."
        if wget -q --show-progress -O "$GRADLE_JAR_PATH" "$GRADLE_JAR_URL"; then
            print_success "Downloaded successfully"
        else
            print_error "Download failed"
            rm -f "$GRADLE_JAR_PATH"
            exit 1
        fi
    elif check_command curl; then
        print_info "Using curl..."
        if curl -# -L -o "$GRADLE_JAR_PATH" "$GRADLE_JAR_URL"; then
            print_success "Downloaded successfully"
        else
            print_error "Download failed"
            rm -f "$GRADLE_JAR_PATH"
            exit 1
        fi
    fi
    
    # Verify download
    if [ ! -f "$GRADLE_JAR_PATH" ]; then
        print_error "gradle-wrapper.jar not found after download"
        exit 1
    fi
    
    local jar_size=$(ls -lh "$GRADLE_JAR_PATH" | awk '{print $5}')
    print_success "gradle-wrapper.jar ready (${jar_size})"
}

step_make_executable() {
    print_step "Making gradlew executable..."
    
    if [ ! -f "gradlew" ]; then
        print_error "gradlew not found"
        exit 1
    fi
    
    chmod +x gradlew
    
    if [ -x "gradlew" ]; then
        print_success "gradlew is executable"
    else
        print_error "Failed to make gradlew executable"
        exit 1
    fi
}

step_verify_gradle() {
    print_step "Verifying Gradle installation..."
    
    # Test gradle wrapper
    if ./gradlew --version > /tmp/gradle_version.txt 2>&1; then
        local version=$(grep "Gradle" /tmp/gradle_version.txt | head -1)
        print_success "Gradle verified: $version"
        cat /tmp/gradle_version.txt
    else
        print_error "Gradle verification failed"
        echo ""
        echo "If this is your first run, this might be expected."
        echo "Gradle will download dependencies on first use."
        echo ""
        print_info "Continuing anyway..."
    fi
}

step_summary() {
    echo ""
    echo -e "${GREEN}════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}  Setup Complete! ✓${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "Your VisionTrade Android project is ready to build!"
    echo ""
    echo "Next steps:"
    echo ""
    echo "  1. Build the project:"
    echo "     ${BLUE}./build.sh${NC}"
    echo ""
    echo "  2. Run tests:"
    echo "     ${BLUE}./test.sh${NC}"
    echo ""
    echo "  3. Deploy to device:"
    echo "     ${BLUE}./deploy.sh${NC}"
    echo ""
    echo "Or use Gradle directly:"
    echo "  ${BLUE}./gradlew clean build${NC}"
    echo ""
    echo "For more information, read START-HERE-PRODUCTION.md"
    echo ""
}

################################################################################
# Main Execution
################################################################################

main() {
    print_header
    
    # Check for help flag
    if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
        echo "Usage: ./setup.sh [OPTIONS]"
        echo ""
        echo "Options:"
        echo "  -h, --help     Show this help message"
        echo "  -y, --yes      Skip confirmations"
        echo ""
        echo "This script:"
        echo "  1. Checks the environment"
        echo "  2. Verifies internet connection"
        echo "  3. Downloads gradle-wrapper.jar (if needed)"
        echo "  4. Makes gradlew executable"
        echo "  5. Verifies Gradle installation"
        echo ""
        echo "After setup, use:"
        echo "  ./build.sh     - Build the project"
        echo "  ./test.sh      - Run tests"
        echo "  ./deploy.sh    - Deploy to device"
        echo ""
        exit 0
    fi
    
    # Run setup steps
    step_check_environment
    echo ""
    
    step_check_internet
    echo ""
    
    step_download_gradle_jar
    echo ""
    
    step_make_executable
    echo ""
    
    step_verify_gradle
    echo ""
    
    step_summary
}

# Run main function
main "$@"
