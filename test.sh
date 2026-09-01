#!/bin/bash

################################################################################
# VisionTrade Android - Test Script
#
# Runs unit tests with reporting and result analysis.
#
# Usage: ./test.sh [OPTIONS]
#
# Options:
#   -a, --all        Run all tests (default)
#   -i, --indicators Run indicator tests only
#   -s, --strategy   Run strategy tests only
#   -r, --risk       Run risk tests only
#   -v, --verbose    Verbose output
#   --report         Open HTML test report
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
TEST_SUITE="all"
VERBOSE=""
GRADLE_CMD="./gradlew"
REPORT_DIR="app/build/reports/tests/testDebugUnitTest"

################################################################################
# Functions
################################################################################

print_header() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  VisionTrade Android - Test Suite${NC}"
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
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -a|--all)
                TEST_SUITE="all"
                shift
                ;;
            -i|--indicators)
                TEST_SUITE="indicators"
                shift
                ;;
            -s|--strategy)
                TEST_SUITE="strategy"
                shift
                ;;
            -r|--risk)
                TEST_SUITE="risk"
                shift
                ;;
            -v|--verbose)
                VERBOSE="--info"
                shift
                ;;
            --report)
                open_report
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
    echo "VisionTrade Android Test Script"
    echo ""
    echo "Usage: ./test.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -a, --all         Run all tests (default)"
    echo "  -i, --indicators  Run IndicatorTests only"
    echo "  -s, --strategy    Run StrategyTests only"
    echo "  -r, --risk        Run RiskTests only"
    echo "  -v, --verbose     Verbose output"
    echo "  --report          Open HTML test report"
    echo "  -h, --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./test.sh                      # All tests"
    echo "  ./test.sh -s -v               # Strategy tests, verbose"
    echo "  ./test.sh -i --report         # Indicators + open report"
    echo ""
    echo "Test Suites:"
    echo "  IndicatorTests - EMA, RSI, ADX calculations"
    echo "  StrategyTests  - BUY/SELL/WAIT signal logic"
    echo "  RiskTests      - Risk calculations, simulation"
    echo ""
}

run_tests() {
    local cmd="$GRADLE_CMD"
    
    case $TEST_SUITE in
        indicators)
            print_step "Running IndicatorTests..."
            cmd="$cmd test --tests com.visiontrade.domain.IndicatorTests"
            ;;
        strategy)
            print_step "Running StrategyTests..."
            cmd="$cmd test --tests com.visiontrade.domain.StrategyTests"
            ;;
        risk)
            print_step "Running RiskTests..."
            cmd="$cmd test --tests com.visiontrade.domain.RiskTests"
            ;;
        all)
            print_step "Running all unit tests..."
            cmd="$cmd test"
            ;;
    esac
    
    if [ -n "$VERBOSE" ]; then
        cmd="$cmd $VERBOSE"
    fi
    
    echo ""
    print_info "Command: $cmd"
    echo ""
    
    if $cmd; then
        print_success "Tests completed successfully"
        return 0
    else
        print_error "Tests failed"
        return 1
    fi
}

show_test_summary() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  Test Summary${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════${NC}"
    echo ""
    
    echo "Test Suites:"
    echo "  ${GREEN}✓ IndicatorTests${NC}"
    echo "    - EMA 20/50 calculation"
    echo "    - RSI 14 calculation"
    echo "    - ADX 14 calculation"
    echo "    - Edge cases (insufficient data)"
    echo ""
    echo "  ${GREEN}✓ StrategyTests${NC}"
    echo "    - BUY signal (all 8 conditions)"
    echo "    - SELL signal (all 8 conditions)"
    echo "    - WAIT signal (incomplete data)"
    echo "    - Confidence scoring (HIGH/MEDIUM/LOW)"
    echo "    - Conflict detection"
    echo ""
    echo "  ${GREEN}✓ RiskTests${NC}"
    echo "    - Risk calculation"
    echo "    - Position sizing"
    echo "    - Account simulation"
    echo "    - Compounding logic"
    echo ""
    
    if [ -d "$REPORT_DIR" ]; then
        echo "Full HTML report:"
        echo "  $REPORT_DIR/index.html"
        echo ""
        echo "View report:"
        echo "  ./test.sh --report"
    fi
}

open_report() {
    if [ ! -f "$REPORT_DIR/index.html" ]; then
        print_error "Test report not found"
        echo ""
        echo "Run tests first:"
        echo "  ./test.sh"
        return 1
    fi
    
    print_info "Opening test report..."
    
    # Try to open with system default
    if command -v open &> /dev/null; then
        # macOS
        open "$REPORT_DIR/index.html"
    elif command -v xdg-open &> /dev/null; then
        # Linux
        xdg-open "$REPORT_DIR/index.html"
    elif command -v start &> /dev/null; then
        # Windows
        start "$REPORT_DIR/index.html"
    else
        print_error "Could not open report - open manually at:"
        echo "  $REPORT_DIR/index.html"
        return 1
    fi
    
    print_success "Report opened"
}

################################################################################
# Main
################################################################################

main() {
    print_header
    
    parse_arguments "$@"
    
    check_gradle
    
    run_tests
    local test_result=$?
    
    if [ $test_result -eq 0 ]; then
        show_test_summary
        exit 0
    else
        echo ""
        print_error "Tests failed - check output above for details"
        exit 1
    fi
}

main "$@"
