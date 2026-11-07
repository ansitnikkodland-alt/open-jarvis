#!/bin/bash
#
# jarvis.sh - CLI bridge to Open Jarvis Android agent over an authenticated
# Android abstract UNIX-domain socket.
# Usage: jarvis [command] [--help]
#

set -e

SCRIPT_NAME="$(basename "$0")"
SOCKET_NAME="openjarvis_cli_bridge"
TIMEOUT=30

send_cmd() {
    local cmd="$1"
    if ! command -v socat >/dev/null 2>&1; then
        echo "Error: socat is required for the authenticated local Jarvis bridge" >&2
        echo "Install with: pkg install socat" >&2
        exit 1
    fi
    printf '{"cmd":"%s","requestId":"%s"}\n' "$cmd" "$$" |
        socat - "ABSTRACT-CONNECT:$SOCKET_NAME,connect-timeout=2"
}

parse_response() {
    local response="$1"
    local is_tty="$2"

    if echo "$response" | grep -q '"status":"error"'; then
        echo "Error: $(echo "$response" | grep -o '"result":"[^"]*"' | cut -d'"' -f4)" >&2
        return 1
    fi

    local result
    result=$(echo "$response" | grep -o '"result":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ "$is_tty" = "true" ]; then
        echo -e "$result"
    else
        echo "$result"
    fi
}

is_tty() {
    [ -t 1 ]
}

show_help() {
    cat <<EOF
Open Jarvis CLI Bridge

Usage:
  $SCRIPT_NAME <command>       Execute a command
  $SCRIPT_NAME status          Show service status
  $SCRIPT_NAME history         Show recent task history
  $SCRIPT_NAME providers       Show provider statistics
  $SCRIPT_NAME memory          Show memory context
  $SCRIPT_NAME --help          Show this help

Examples:
  $SCRIPT_NAME "open whatsapp"
  $SCRIPT_NAME status

Security:
  Uses Android abstract UNIX socket '$SOCKET_NAME'.
  The app verifies the connecting Android UID (Termux, app UID, or ADB shell).
  No TCP listener is opened by the Jarvis app.

ADB forwarding from a computer:
  adb forward tcp:5000 localabstract:$SOCKET_NAME
  echo '{"cmd":"status","requestId":"1"}' | nc 127.0.0.1 5000

Exit codes:
  0 = success
  1 = error (service not running, command failed, or bridge unavailable)
EOF
}

main() {
    if [ "$1" = "--help" ]; then
        show_help
        exit 0
    fi

    if [ -z "$1" ]; then
        echo "Error: No command provided" >&2
        show_help
        exit 1
    fi

    local response
    if ! response="$(send_cmd "$1")"; then
        echo "Error: Could not connect to Jarvis. Start the Open Jarvis overlay service first." >&2
        exit 1
    fi

    if parse_response "$response" "$(is_tty)"; then
        exit 0
    else
        exit 1
    fi
}

main "$@"
