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


## Follow-up audit fixes (2026-10-16)
- Added the missing `android.view.View` import used by `onCreateContentView()`.
- Updated `tools/static_audit.py` to expect the hardened 4,000-character limit.
- Incremented the screen-capture generation for every screenshot and cleared prior capture text before OCR, preventing stale OCR from being accepted for the new generation.
- Replaced unconditional six-digit redaction with contextual OTP/code/passcode redaction so Indian postal PIN codes such as 600001 are preserved.
- Changed external recognizer discovery to `PackageManager.MATCH_ALL`; package visibility and service eligibility still require Android device testing.

## Not fully resolved in this source-only pass
- **Approval dialog lifecycle:** `confirmAgentAction()` still uses an Activity-owned dialog/coroutine. A lifecycle-aware DialogFragment/ViewModel refactor requires broader UI state changes and has not been claimed as fixed here. Test rotation/split-screen and ensure pending approvals cancel safely before release.
- **Gmail token lifecycle:** the connector stores the access token in memory. Google Identity authorization may return tokens through its authorization flow, but this code does not implement a durable, explicit reauthorization/refresh strategy. Do not persist tokens in plaintext; add a lifecycle-aware authorization provider and Keystore-backed storage only if persistence is truly required.
- **Build verification:** this environment has no Gradle executable or wrapper, so Kotlin compilation, JUnit execution, Android Lint, and device tests remain unverified.


## Accessibility controller follow-up (2026-10-18)
- Added the user-enabled `.GenericAccessibilityService`, guarded by Android's `BIND_ACCESSIBILITY_SERVICE` binding permission and an explicit XML configuration.
- Restricts actions to exact visible label clicks, unique hinted-field entry, scrolling, and Android Back; no gesture injection or arbitrary intents.
- Adds 15-second action expiry, serialized request/result handling, conservative financial/authentication checkpoints, and globally blocked send/submit/payment/trade/destructive action labels.
- The controller never reads editable current values. Obvious credential-bearing commands are withheld before transcript display and task persistence, and UI-entry values are omitted from agent history.
- These safeguards are heuristic, especially for unknown, localized, obfuscated, or inaccessible UIs. They are not a guarantee that all sensitive screens can be classified. See `ACCESSIBILITY_CONTROLLER.md` and `MIRA_FINAL_STATIC_REVIEW.md`.
