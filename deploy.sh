#!/bin/bash

################################################################################
# VisionTrade Android - Deploy Script
#
# Automated build, test, and deployment to Android device.
#
# Prerequisites:
#   - Android device connected via USB
#   - USB debugging enabled
#   - adb installed
#
# Usage: ./deploy.sh [OPTIONS]
#
# Options:
#   -f, --fast       Build debug APK only (skip tests)
#   -t, --test       Run tests before deployment
#   -l, --list       List connected devices
#   -u, --uninstall  Remove existing app before install
#
################################################################################

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

# Configuration
RUN_TESTS=true
UNINSTALL=false
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.visiontrade"
ACTIVITY="com.visiontrade.MainActivity"

################################################################################
# Functions
################################################################################

print_header() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  VisionTrade Android - Deploy${NC}"
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

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -f|--fast)
                RUN_TESTS=false
                print_info "Fast deploy enabled (skipping tests)"
                shift
                ;;
            -t|--test)
                RUN_TESTS=true
                shift
                ;;
            -u|--uninstall)
                UNINSTALL=true
                shift
                ;;
            -l|--list)
                list_devices
                exit 0
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

show_help() {
    echo "VisionTrade Android Deploy Script"
    echo ""
    echo "Usage: ./deploy.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -f, --fast       Build only, skip tests (faster)"
    echo "  -t, --test       Run tests before deploy (default)"
    echo "  -u, --uninstall  Uninstall before install"
    echo "  -l, --list       List connected devices"
    echo "  -h, --help       Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./deploy.sh                   # Build, test, deploy"
    echo "  ./deploy.sh -f                # Quick deploy (skip tests)"
    echo "  ./deploy.sh -u                # Uninstall, rebuild, deploy"
    echo ""
    echo "Prerequisites:"
    echo "  - Android device connected via USB"
    echo "  - USB debugging enabled"
    echo "  - adb installed"
    echo ""
}

check_prerequisites() {
    print_step "Checking prerequisites..."
    
    # Check adb
    if ! command -v adb &> /dev/null; then
        print_error "adb not found"
        echo ""
        echo "Install Android SDK Platform Tools:"
        echo "  - Android Studio SDK Manager"
        echo "  - Or: brew install android-platform-tools (macOS)"
        exit 1
    fi
    print_success "adb found"
    
    # Check build script
    if [ ! -f "build.sh" ]; then
        print_error "build.sh not found"
        exit 1
    fi
    print_success "build.sh found"
}

list_devices() {
    echo "Connected Android devices:"
    echo ""
    
    if ! adb devices; then
        print_error "adb command failed"
        return 1
    fi
    
    echo ""
    local device_count=$(adb devices | grep -v "List of" | grep -v "^$" | wc -l)
    if [ "$device_count" -le 1 ]; then
        print_error "No devices found"
        echo ""
        echo "Connect an Android device and enable USB debugging:"
        echo "  1. Connect device via USB"
        echo "  2. Settings → About → tap Build Number 7 times"
        echo "  3. Settings → Developer Options → USB Debugging ON"
        echo "  4. Tap 'Allow' on device when prompted"
        return 1
    fi
}

check_device_connected() {
    print_step "Checking for connected devices..."
    
    if ! adb get-state > /dev/null 2>&1; then
        print_error "No device connected"
        echo ""
        echo "Connect an Android device via USB:"
        echo "  adb devices"
        echo ""
        echo "Enable USB debugging on device:"
        echo "  Settings → Developer Options → USB Debugging"
        exit 1
    fi
    
    local device=$(adb devices | grep device | head -1 | awk '{print $1}')
    print_success "Device connected: $device"
}

build_apk() {
    print_step "Building APK..."
    echo ""
    
    if ./build.sh --debug --clean; then
        if [ ! -f "$APK_PATH" ]; then
            print_error "APK not found at $APK_PATH"
            exit 1
        fi
        
        local apk_size=$(ls -lh "$APK_PATH" | awk '{print $5}')
        print_success "APK ready ($apk_size)"
    else
        print_error "Build failed"
        exit 1
    fi
}

run_tests() {
    if [ "$RUN_TESTS" = false ]; then
        print_info "Skipping tests (use -t to run)"
        return 0
    fi
    
    print_step "Running tests..."
    echo ""
    
    if ./test.sh; then
        print_success "All tests passed"
    else
        print_error "Tests failed"
        echo ""
        read -p "Continue deployment anyway? (y/N) " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Deployment cancelled"
            exit 1
        fi
    fi
}

uninstall_app() {
    if [ "$UNINSTALL" = false ]; then
        return 0
    fi
    
    print_step "Uninstalling existing app..."
    
    if adb uninstall "$PACKAGE_NAME" 2>/dev/null; then
        print_success "App uninstalled"
    else
        print_info "App not installed (this is fine)"
    fi
}

install_apk() {
    print_step "Installing APK..."
    
    if ! adb install -r "$APK_PATH"; then
        print_error "Installation failed"
        exit 1
    fi
    
    print_success "APK installed successfully"
}

launch_app() {
    print_step "Launching app..."
    
    if adb shell am start -n "$PACKAGE_NAME/$ACTIVITY"; then
        print_success "App launched"
    else
        print_error "Could not launch app"
        echo ""
        echo "Manual launch: Open VisionTrade from app drawer"
        return 1
    fi
}

show_deployment_summary() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Deployment Complete ✓${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo ""
    
    print_success "VisionTrade installed and running"
    echo ""
    echo "What's next:"
    echo ""
    echo "Test the app:"
    echo "  1. Manual Data Entry - Enter 3 candles"
    echo "  2. Screenshot Analysis - Upload 3 charts"
    echo "  3. Camera Analysis - Capture 3 timeframes"
    echo "  4. Risk Calculator - Calculate position size"
    echo "  5. History - Check saved analyses"
    echo ""
    echo "View app logs:"
    echo "  adb logcat | grep VisionTrade"
    echo ""
    echo "Uninstall:"
    echo "  adb uninstall $PACKAGE_NAME"
    echo ""
}

show_build_log() {
    echo ""
    echo "View detailed logs:"
    echo "  adb logcat -v threadtime | grep VisionTrade"
    echo ""
    echo "Save logs to file:"
    echo "  adb logcat > app.log"
    echo ""
}

################################################################################
# Main
################################################################################

main() {
    print_header
    
    parse_arguments "$@"
    
    check_prerequisites
    echo ""
    
    check_device_connected
    echo ""
    
    build_apk
    echo ""
    
    run_tests
    echo ""
    
    uninstall_app
    echo ""
    
    install_apk
    echo ""
    
    launch_app
    echo ""
    
    show_deployment_summary
    show_build_log
}

main "$@"
