# MIRA static analysis and pre-build review

Review date: 2026-10-18

## Scope and limits

This is a source-level review of the Android/Kotlin starter. It is not a substitute for Android Lint, Gradle compilation, dependency vulnerability scanning, or testing on the target OnePlus device. The current environment does not provide an Android SDK or a Gradle wrapper, so no APK build is claimed.

## Changes made during this review

- Serialized complete agent turns so one stateful local-model conversation cannot be interleaved by concurrent requests.
- Serialized model load, generation, and unload with a coroutine mutex.
- Kept model swap transactional: an existing model remains available until the candidate model and conversation initialize.
- Bounded selected model file copying to 6 GiB and available app-private storage while preserving a 256 MiB free-space reserve.
- Added 120-second inference and 60-second tool deadlines. Timed-out tools return a warning to check the target app/account before retrying.
- Propagated coroutine cancellation rather than accidentally treating it as an ordinary tool/model error.
- Rejected malformed argument containers, unknown tool IDs, unknown argument keys, invalid timer units/durations, invalid alarm times, invalid email recipient input, and overlong user requests.
- Required confirmation for sensitive Gmail reads/drafts, contact dialing, calendar creation, memory deletion, and all high-risk tools.
- Kept tool execution behind a fixed allowlist. No shell, `Runtime.exec`, `ProcessBuilder`, dynamic code evaluation, or arbitrary model-selected Android intents are exposed.
- Added bounded OkHttp timeouts and Gmail draft recipient/subject/body validation.
- Disabled cleartext traffic explicitly and kept Android backup disabled.
- Fixed potential integer overflow in the deterministic timer route.

## Security posture

- Model output is untrusted input. It can request only named tools; the app validates arguments before dispatch.
- Retrieved email snippets and tool outputs are labelled untrusted in the prompt. This is a defense-in-depth instruction, not a guarantee against prompt injection. The allowlist and approval gates remain the actual capability boundary.
- Calls are opened in the dialer rather than placed directly. Calendar entries open an editor. Gmail draft creation never sends mail.
- Gmail access tokens are held in memory only in this starter. Reauthorization may be required after process death. OAuth consent screen, package/signing configuration, and API scopes still require deployment-specific setup.
- Local SQLite stores are not encrypted. Android backup is disabled, but a rooted/compromised device can still expose app-private data. Consider encrypted storage if the threat model requires it.

## Known limitations before release

1. Run Gradle compilation and Android Lint with a real Android SDK; verify LiteRT-LM 0.18.0 API signatures against the resolved dependency.
2. Add automated unit tests for tool schemas, JSON protocol parsing, approval gates, timeout/cancellation, and memory matching.
3. Validate Gemma 4 E2B tool-protocol reliability on the actual phone. The current protocol is JSON-in-prompt, not native structured tool calling.
4. Test model loading on device for memory pressure, thermal throttling, GPU compatibility, and fallback behavior. A large model may not be usable on every device.
5. Complete Gmail OAuth verification and test token expiry, consent, API errors, and user revocation.
6. Add Tamil intent tests and speech/TTS device checks. The current natural-language routing is primarily English.
7. Add rate limiting, resumable task checkpoints, and robust foreground-service behavior before unattended background operation.

## Automated test source added

`app/src/test/java/com/naveensmk/mira/AgentRuntimeTest.kt` covers: successful tool-then-final flow, unknown tool rejection, invalid timer rejection, denied sensitive action, and malformed non-object arguments. These tests have been added but could not be executed without a Gradle wrapper/Android SDK in this environment.

Task records store the user's request text locally to support history/debugging. The task database is not encrypted; avoid sending secrets in requests until an explicit retention/redaction policy is implemented.

## Pre-build static checks

The source tree should be scanned for dangerous dynamic execution APIs and manifest export settings. `MainActivity` is intentionally exported as the launcher activity. Existing system-bound voice/recognition services and the new user-enabled `.GenericAccessibilityService` are declared with Android binding permissions; no extra receiver is declared. Run Android Lint and dependency scanning before distributing a release build.

## Audit results from this source snapshot

- Lightweight source audit for the updated snapshot: **46/46 checks passed** (`python tools/static_audit.py`).
- Agent-runtime Kotlin type/syntax check: compiled the core loop against minimal Android/JSON interface stubs successfully. This does not validate Android SDK, LiteRT-LM, Gmail OAuth, or Gradle integration.
- A full project compile/test run remains blocked because this environment has no Android SDK (`android.jar`) and the project has no Gradle wrapper installed. No APK has been built.


## Accessibility-controller review (2026-10-18)

The `.GenericAccessibilityService` is user-enabled through Android Settings, protected by `BIND_ACCESSIBILITY_SERVICE`, and configured without gesture injection. `AppSafetyPolicy` forces a manual handoff for detected authentication/financial screens and blocks final-action targets. Those classifiers are heuristic, not a guarantee for unknown or localized UI. Editable field values are not inspected by the policy. Credential-like user requests are filtered before transcript display and task persistence. See `ACCESSIBILITY_CONTROLLER.md` and `MIRA_FINAL_STATIC_REVIEW.md`.

Latest checks: `tools/static_audit.py` **46/46 passed**; `tools/security_audit.py` **19/19 passed**; all **6 XML** resources/manifests parsed; controller/policy Kotlin smoke compile, seven policy test methods, and four screen-context redaction/bounds test methods passed against minimal hand-written Android/JUnit stubs; agent runtime/registry smoke checks passed. These isolated checks do not replace an Android SDK build. Full Gradle build and Android Lint remain unverified because this workspace lacks an Android SDK, Gradle executable, and Gradle wrapper JAR.
