#!/bin/bash

################################################################################
# VisionTrade Android - Build Script
#
# Automated build with progress reporting and error handling.
#
# Usage: ./build.sh [OPTIONS]
#
# Options:
#   -c, --clean      Force clean build (default)
#   -i, --incremental Incremental build (faster)
#   -d, --debug      Debug APK only
#   -r, --release    Release APK only
#   -v, --verbose    Verbose output
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
BUILD_TYPE="clean"
APK_TYPE="both"
VERBOSE=""
GRADLE_CMD="./gradlew"

################################################################################
# Functions
################################################################################

print_header() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  VisionTrade Android - Build${NC}"
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

check_gradle() {
    if [ ! -f "$GRADLE_CMD" ]; then
        print_error "gradlew not found"
        echo ""
        echo "Run setup first:"
        echo "  ./setup.sh"
        exit 1
    fi
    
    if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
        print_error "gradle-wrapper.jar not found"
        echo ""
        echo "Run setup first:"
        echo "  ./setup.sh"
        exit 1
    fi
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -c|--clean)
                BUILD_TYPE="clean"
                shift
                ;;
            -i|--incremental)
                BUILD_TYPE="incremental"
                shift
                ;;
            -d|--debug)
                APK_TYPE="debug"
                shift
                ;;
            -r|--release)
                APK_TYPE="release"
                shift
                ;;
            -v|--verbose)
                VERBOSE="--info"
                shift
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
    echo "VisionTrade Android Build Script"
    echo ""
    echo "Usage: ./build.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -c, --clean        Force clean build (default)"
    echo "  -i, --incremental  Incremental build (faster for development)"
    echo "  -d, --debug        Build debug APK only"
    echo "  -r, --release      Build release APK only"
    echo "  -v, --verbose      Verbose build output"
    echo "  -h, --help         Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./build.sh                    # Clean build, both APKs"
    echo "  ./build.sh -i -d             # Quick debug build"
    echo "  ./build.sh -r -v             # Release build, verbose"
    echo ""
}

execute_build() {
    local cmd="$GRADLE_CMD"
    
    # Add clean if requested
    if [ "$BUILD_TYPE" == "clean" ]; then
        print_step "Clean build (removing old artifacts)..."
        cmd="$cmd clean"
    else
        print_info "Incremental build (faster)"
    fi
    
    # Add build type
    case $APK_TYPE in
        debug)
            print_step "Building debug APK..."
            cmd="$cmd assembleDebug"
            ;;
        release)
            print_step "Building release APK..."
            cmd="$cmd assembleRelease"
            ;;
        both)
            print_step "Building all artifacts..."
            cmd="$cmd build"
            ;;
    esac
    
    # Add verbose if requested
    if [ -n "$VERBOSE" ]; then
        cmd="$cmd $VERBOSE"
    fi
    
    # Execute
    echo ""
    print_info "Running: $cmd"
    echo ""
    
    if $cmd; then
        print_success "Build completed successfully"
        return 0
    else
        print_error "Build failed"
        return 1
    fi
}

show_build_artifacts() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Build Artifacts${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo ""
    
    if [ "$APK_TYPE" == "debug" ] || [ "$APK_TYPE" == "both" ]; then
        local debug_apk="app/build/outputs/apk/debug/app-debug.apk"
        if [ -f "$debug_apk" ]; then
            local size=$(ls -lh "$debug_apk" | awk '{print $5}')
            print_success "Debug APK: $debug_apk ($size)"
            echo "  Install: adb install -r $debug_apk"
        fi
    fi
    
    if [ "$APK_TYPE" == "release" ] || [ "$APK_TYPE" == "both" ]; then
        local release_apk="app/build/outputs/apk/release/app-release-unsigned.apk"
        if [ -f "$release_apk" ]; then
            local size=$(ls -lh "$release_apk" | awk '{print $5}')
            print_success "Release APK: $release_apk ($size)"
            echo "  Note: Must be signed before installation"
        fi
    fi
    
    echo ""
    echo "Run tests:"
    echo "  ./test.sh"
    echo ""
    echo "Deploy to device:"
    echo "  ./deploy.sh"
    echo ""
}

################################################################################
# Main
################################################################################

main() {
    print_header
    
    parse_arguments "$@"
    
    check_gradle
    
    execute_build
    local build_result=$?
    
    if [ $build_result -eq 0 ]; then
        show_build_artifacts
        exit 0
    else
        echo ""
        print_error "Build failed - check output above for errors"
        exit 1
    fi
}

main "$@"
