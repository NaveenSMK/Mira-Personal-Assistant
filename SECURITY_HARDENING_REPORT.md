# MIRA Security Hardening Report

Date: 2026-10-16

## Implemented
- ScreenContextStore retains sanitized text only; screenshot bitmaps are not stored in the context store.
- Screen text is bounded to 4,000 characters and expires after two minutes.
- Clearing context invalidates pending OCR callbacks using a generation counter.
- Added best-effort redaction for common OTP, password-labelled values, card-like digit sequences, email addresses, and account-number-like strings.
- AssistStructure nodes with password input types or password/credit/OTP/verification autofill hints are skipped.
- OCR output is sanitized before storage; transient scaled bitmaps are recycled after OCR completes.
- AgentRuntime requires approval for non-low-risk actions and named sensitive operations.
- Agent prompts treat screen/OCR/email/web/message/tool output as untrusted.
- Added sanitizer unit tests.

## Residual risks
- Redaction is heuristic, not a DLP guarantee. Avoid screen context on highly sensitive screens.
- Android does not expose every source-window FLAG_SECURE state through this callback. The app does not claim to independently verify every window flag.
- OCR processes a screenshot transiently in memory before recycling it.
- Context is not intentionally written to MemoryStore or TaskStore; future logs/analytics must not record it.
- Gmail uses gmail.readonly and gmail.compose scopes. Access tokens are currently in-memory only; if persistence is added, use Keystore-backed encryption.
- Confirmation depends on the UI approval callback; production should fail closed if approval UI cannot be shown and show exact targets/arguments.
- Static source checks are not equivalent to Gradle build, Android lint, or real-device validation.
