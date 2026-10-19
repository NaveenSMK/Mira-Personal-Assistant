# MIRA post-change engineering review

**Review date:** 18 October 2026  
**Artifact:** `MIRA_Private_Play_Release_Final_Reviewed.zip`  
**Review type:** Source/static review after remediation; not an Android release certification.

## Implemented in this revision

1. **Accessibility consent:** Added a separate versioned in-app disclosure with an affirmative consent button before opening Android Accessibility Settings, plus a revoke-consent control. The controller is inert without consent. The Play Console AccessibilityService declaration/video requirement remains an external publication gate.
2. **Task privacy and lifecycle:** New task records use a generic label and status/timestamps, not the raw request. Task metadata is capped at 500 records and 90 days; old details can be deleted via Settings → Memory & privacy → Clear task history. Task/memory SQLite operations run on `Dispatchers.IO`. `RUNNING` task statuses are marked failed when the DB is opened after process death.
3. **Gmail connection control:** Added local disconnect clearing the in-memory token and local authorization state; a Gmail API 401 clears local state. UI explains that full OAuth grant revocation must also be performed through the user's Google Account. This path still needs real OAuth/token-expiry/revocation testing.
4. **Model lifecycle and memory:** The native engine is process-owned across Activity recreation. Replacement unloads the active native engine before initializing a replacement so two 2.6-GB-class engines are not deliberately initialized together. The previous model file is retained if candidate initialization fails and can be reloaded. Inference output is capped at 6,000 characters, prompts are bounded, and prefill retry preserves coroutine cancellation. Device verification is still required for repeated inference and updates.
5. **Inference recovery:** Repeated LiteRT prefill-capacity errors now trigger one bounded escalation: recreate the conversation, retry with a compact prompt, then close and reinitialize the active engine once before one last compact retry. The active model file is retained and the model is not silently replaced. This implements the reported reload workaround automatically, but the model/device-specific error must still be tested on hardware.
6. **Speech:** The configured Android speech-recognition service is preferred; when the setting is unavailable, Android intent resolution is used instead of selecting the first matching package by iteration order. Android 14+ multilingual detection is requested when supported; older recognizers disclose the limitation and retry Tamil after an English no-match. This is a fallback, not a guarantee of bilingual detection on every installed recognizer.
7. **Screen context:** AssistStructure and OCR are kept as separate sources. Each assistant session has its own generation, each screenshot has its own OCR generation, empty/failed OCR no longer erases AssistStructure text, and late callbacks from older captures are ignored.
8. **Action correctness/safety:** Argument-validation failures and explicit handler failures are reported as failed tasks rather than successes. Failure matching catches embedded phrases such as “Gmail is not connected”, action-shaped requests cannot be reported complete when the model returns prose or a final answer without executing a tool, and contact-disambiguation results stop the model loop while the user chooses. Contact calls always open the system dialer for user review; MIRA no longer requests `CALL_PHONE` or invokes `ACTION_CALL`. Financial/sensitive screen checkpoints were broadened and missing active package identity fails closed.
9. **Runtime and UI:** Removed the selectable-but-unimplemented llama.cpp/GGUF provider and its placeholder engine. Improved primary/muted contrast and raised main action/icon hit targets to at least 48 dp. Activity response and status text are retained as canonical state and are not pushed into detached views when the user is on another screen.
10. **Build/source checks:** Restored optional native-library declarations with API-31 lint targeting; the API-27 navigation-bar style remains in `values-v27`. Both GitHub workflows provision Gradle 8.14.4 and generate the wrapper before invoking it.
11. **Regression coverage:** Added tests for screen-context retention across screenshot OCR, stale OCR generations, explicit tool failure reporting, model-only claims without tool execution, embedded Gmail failure messages, contact disambiguation, and the corrected invalid-timer failure status. Existing routing tests continue to cover browser-vs-Maps and YouTube separation.

## Verification results performed here

- `tools/static_audit.py`: **62/62 checks passed** after updates.
- `tools/security_audit.py`: **19/19 source checks passed**.
- XML resources/manifest are parsed by the static audit.
- Kotlin sources were passed to `kotlinc` as a syntax smoke check, but a meaningful compilation is not possible without Android, AndroidX, LiteRT-LM, OkHttp, JSON, and coroutine dependencies. No Kotlin syntax diagnostics were found in the resulting output; unresolved dependency references are expected in this environment.
- A focused pure Kotlin routing/context smoke test passed 12 assertions; its repeatable source is `tools/PureKotlinSmoke.kt`. It should also run in CI.

## Not certified / remaining release gates

- **No full Gradle build, Android Lint, APK/AAB build, emulator test, or physical-device test was executed here.** This environment has no Android SDK or Gradle executable. Therefore lint is not represented as clean and the app is not represented as build-verified.
- The ZIP does not include `gradle-wrapper.jar`. GitHub Actions provisions Gradle 8.14.4 and creates the wrapper at job start; for local builds install Gradle 8.14.4 and run `gradle wrapper --gradle-version 8.14.4` once.
- Accessibility policy compliance needs the final Play Console declaration and review/demo materials; code disclosure alone is not approval.
- Gmail still needs OAuth validation using the final application ID and actual debug/Play signing certificate(s), Gmail API enablement, a real test account, expired token, denied consent, and revoked grant.
- Test on representative Android 8–16 devices: 20+ consecutive LiteRT-LM turns without reloading; import/reload/update while loaded; 10+ Tamil and English recognition cycles; locale fallback, empty OCR and late callbacks; browser/Maps/YouTube routing; contacts with zero/one/multiple matches; Gmail connect/read/draft/disconnect/reconnect; Activity recreation; and secure/financial screen handoff.
- MainActivity remains a large Activity rather than a full ViewModel/StateFlow architecture. The most immediate detached-view and model-lifecycle risks are mitigated, but a focused architectural extraction is still advisable before a broader public release.
- Agent tool results are still returned as strings from handlers and explicit error messages are classified conservatively. Agent execution now fails closed on malformed action protocols and embedded handler failures, but handlers still use strings internally. A fully typed `Success / NeedsConfirmation / NeedsDisambiguation / Failure` contract plus action-specific integration tests remains a worthwhile next step; do not treat the model's final prose by itself as proof of an external side effect.
- The prompt cap is a conservative character limit, not the LiteRT-LM tokenizer's exact token count. Continue keeping prompts small and verify against the selected model's context/prefill behavior.
- `PRIVACY.md` is still a draft; replace it with a reviewed public policy and add a real privacy contact before distribution.

## Release recommendation

**Status: changes applied; ready for full CI and device verification, not certified for distribution yet.** Do not distribute to the private Play track until CI passes `testDebugUnitTest`, Android Lint, debug build, signed release bundle, and the external/device release gates above.
