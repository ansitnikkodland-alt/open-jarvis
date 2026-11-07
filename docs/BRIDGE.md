# Open Jarvis CLI Bridge

The Termux CLI bridge controls the Open Jarvis Android agent through an Android
abstract UNIX-domain socket. The app does not open a TCP listener.

## Quick Start

1. Start Open Jarvis and enable the floating Overlay Service.
2. In Termux, install the socket client:

```bash
pkg install socat
```

3. Install the CLI helper:

```bash
cp scripts/jarvis.sh $PREFIX/bin/jarvis
chmod +x $PREFIX/bin/jarvis
```

## Usage

```bash
jarvis "open whatsapp"
jarvis status
jarvis history
jarvis providers
jarvis memory
```

## Protocol

The CLI connects to the Android abstract socket named
`openjarvis_cli_bridge`.

Request:

```json
{"cmd":"open whatsapp","requestId":"abc123"}
```

Response:

```json
{"requestId":"abc123","status":"done","result":"success"}
```

Status values are `progress`, `done`, and `error`.

## ADB forwarding

For a host computer connected with ADB:

```bash
adb forward tcp:5000 localabstract:openjarvis_cli_bridge
echo '{"cmd":"status","requestId":"1"}' | nc 127.0.0.1 5000
```

The Android-side socket authenticates the connecting peer UID. The supported
local peers are the Open Jarvis app UID, the installed Termux app UID, and the
Android shell UID used by ADB forwarding.

## Security model

- The server uses `LocalServerSocket` in Android's abstract UNIX-domain namespace.
- No TCP server is bound to Wi-Fi, mobile data, or other network interfaces.
- The server checks the actual peer UID from the accepted local socket; it does
  not use the server process UID as a substitute for the client UID.
- Requests are limited to 8 KiB, commands to 2,000 characters, and each allowed
  peer UID to 10 requests per rolling second.
- Unauthenticated peer UIDs are rejected.

## Troubleshooting

- **Bridge unavailable:** Start the Open Jarvis app and its Overlay Service.
- **socat missing:** Run `pkg install socat` in Termux.
- **ADB connection fails:** Check that `adb forward` targets the exact
  `localabstract:openjarvis_cli_bridge` name.
- **Termux rejected:** Ensure the installed Termux package is the official
  `com.termux` package and that the app has been restarted after installation.

