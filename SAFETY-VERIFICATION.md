# Safety hardening: verification boundary

The PR remains a draft. Do not install on a personal device yet.

Implemented: session-generation invalidation and cancellation of registered agent jobs;
revocation of outstanding approvals on service interruption, unbind and destruction;
main-thread, non-suspending final package/window/structural and text-digest check;
exact, unambiguous text matching; low-level legacy entry points fail closed;
explicit one-read consent for each screen capture used by AgentCore.
Password-marked trees are blocked entirely. SocketServer remains disabled.
Coordinate gestures and the legacy self-healing executor are disabled.

Each mutation requires a fresh read approval followed by action approval. A digest
of that approved screen is consumed once, and checked again after action approval.
Text is hashed only after read/action consent; hashes are process-local and not logged.

Limits: Android cannot guarantee atomicity across another application's process. Custom unmarked PIN keypads are not reliably
identified. Deny reading confidential screens and disable Accessibility during
secret entry. No persistent protected-app configuration or confidential-mode UI
is implemented by this change. Existing saved history is not erased.
The fixed approval-UI delay is only scheduling; authorization depends on the
subsequent session and window checks, never on that delay alone.

Automated coverage added: session stop/reconnect, queued and late tasks,
registry-wide revocation, and Android tests of unapproved legacy entry points.
The latter uses an unattached service and is not a full interactive flow test.

Still required on a test device/emulator: real Allow/Deny with foreground restoration;
same-layout text replacement; focus/window/app changes; password and custom PIN
screens; notification denial/timeout; service disable/re-enable while a model
request or approval is pending; multiple AgentCore instances; socket port absence.
Review Play Protect's exact warning without bypassing it.
