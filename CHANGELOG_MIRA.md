# MIRA Release Change Log

## 2026-10-18 — Modern UI, reliability, and private-Play preparation

### User experience
- Replaced the former setup-heavy landing screen with a sky-blue MIRA interface: one response area, one typed composer, a microphone button, send action, and Profile/Settings shortcuts.
- Moved model setup/update, Gmail connection and operations, default-assistant role, accessibility, screen context, memory, and runtime options to Settings.
- Added edge-to-edge system-bar insets to keep the top content clear of the Android clock and navigation bar.
- Rebuilt launcher, round, and adaptive launcher icons using the user-supplied MIRA logo.

### Model and speech lifecycle
- Added private model-file persistence and startup restoration. Import is only needed initially or when updating the model; a cold Android process still needs to initialize the model in memory.
- Close the prior LiteRT-LM conversation before creating the next conversation. Added bounded prompts and one shorter-prompt retry for the reported prefill state-capacity error.
- Recreate and clean up the speech recognizer for each attempt; add Auto, English (India), and Tamil (India) language settings with clearer language-pack errors.
- Request language-aligned output for English and Tamil input.

### Android intent routing and safety
- Added deterministic routing/tests to keep browser search, YouTube, Maps, contact search, and calling intents distinct.
- YouTube launch/search tries the YouTube app and uses browser URLs when the app is unavailable.
- Contact search handles no/one/multiple matches; explicit call requests use on-demand `CALL_PHONE` permission when there is a single match, require choosing among multiple matches, and fall back to the system dialer if call permission is denied.
- Normalized known legacy tool argument aliases such as `key` to the expected argument names while retaining validation and confirmation checks.
- Kept sensitive financial/credential actions on a conservative manual handoff path.

### Branding and release setup
- Renamed the old source package/service/file identity to `com.naveensmk.mira` and MIRA-named source files where applicable. Android framework types like `VoiceInteractionSession` retain their required platform names.
- Set compile/target SDK to API 36 and added GitHub workflows to install the SDK platform/build tools, run audits/tests/lint, and optionally build a signed release AAB from publisher-provided secrets.
- Added signing, OAuth certificate, target API, privacy disclosure, and private-Play guidance. No keystore, OAuth secrets, Play Console setup, or signed binary are included.

### Validation status
- Static safety audit: 54/54 checks passed.
- Source security audit: 19/19 checks passed.
- Pure intent-parser smoke tests: 16/16 mappings passed, including browser/Maps/YouTube/call routing and natural timer/alarm phrasings.
- Full Android Gradle build/unit test/Lint and real-device tests were **not run in this environment** because an Android SDK and Gradle installation/wrapper were unavailable. The model prefill retry, Tamil speech provider behavior, Gmail OAuth, Play signing/certificate setup, and Play Protect status remain device/publisher validation gates. Do not treat this source archive as a verified publish-ready binary.
