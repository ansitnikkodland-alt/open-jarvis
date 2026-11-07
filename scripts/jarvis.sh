#!/bin/bash
#
# jarvis.sh - authenticated Termux CLI bridge to Open Jarvis.
#

set -e

SCRIPT_NAME="$(basename "$0")"
CONFIG_URI="content://com.openjarvis.bridgeconfig/config"

json_escape() {
    local value="$1"
    value=${value//\\/\\\\}
    value=${value//\"/\\\"}
    value=${value//$'\n'/\\n}
    value=${value//$'\r'/\\r}
    value=${value//$'\t'/\\t}
    printf '%s' "$value"
}

get_bridge_config() {
    if [ ! -x /system/bin/content ]; then
        echo "Error: Android content command is unavailable" >&2
        return 1
    fi

    local config
    config=$(/system/bin/content query --uri "$CONFIG_URI" 2>/dev/null) || {
        echo "Error: Cannot read Jarvis bridge config. Ensure Termux is installed and start the Overlay Service." >&2
        return 1
    }

    BRIDGE_PORT=$(printf '%s\n' "$config" | sed -n 's/.*port=\([0-9][0-9]*\).*/\1/p')
    BRIDGE_TOKEN=$(printf '%s\n' "$config" | sed -n 's/.*token=\([A-Za-z0-9_-][A-Za-z0-9_-]*\).*/\1/p')

    if [ -z "$BRIDGE_PORT" ] || [ "$BRIDGE_PORT" = "0" ] || [ -z "$BRIDGE_TOKEN" ]; then
        echo "Error: Jarvis bridge is not running. Start the Open Jarvis Overlay Service first." >&2
        return 1
    fi
}

send_cmd() {
    local cmd="$1"
    if ! command -v socat >/dev/null 2>&1; then
        echo "Error: socat is required. Install with: pkg install socat" >&2
        return 1
    fi

    local escaped_cmd
    escaped_cmd=$(json_escape "$cmd")
    printf '{"cmd":"%s","requestId":"%s","token":"%s"}\n' "$escaped_cmd" "$$" "$BRIDGE_TOKEN" |
        socat - "TCP:127.0.0.1:$BRIDGE_PORT,connect-timeout=2"
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
  Commands use a random token delivered only to the Open Jarvis app,
  the installed Termux app UID, or Android ADB shell UID.
  The TCP server binds only to 127.0.0.1.

ADB forwarding from a computer:
  Read port/token with:
    adb shell content query --uri $CONFIG_URI
  Then forward the reported port:
    adb forward tcp:5000 tcp:<reported-port>
  Send a JSON request containing the returned token to 127.0.0.1:5000.

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

    get_bridge_config
    local response
    if ! response="$(send_cmd "$1")"; then
        echo "Error: Could not connect to Jarvis on loopback port $BRIDGE_PORT" >&2
        exit 1
    fi

    if parse_response "$response" "$(is_tty)"; then
        exit 0
    else
        exit 1
    fi
}

main "$@"
