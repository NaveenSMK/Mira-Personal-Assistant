# MIRA Release Verification

Date: 2026-10-18

## Checks run
- `python tools/static_audit.py`: **54/54 passed**.
- `python tools/security_audit.py`: **19/19 passed**.
- `AndroidIntentParser.kt` compiled with local `kotlinc`; **16/16 routing smoke cases passed**, including browser vs Maps, YouTube launch/search, contact/call routing, polite phrasing, and timer/alarm phrasing.
- XML/resource/package/manifest component/workflow YAML cross-check: **0 errors**.
- Updated `AndroidIntentParserTest.kt` to expect the normalized contact query `wife` for “Please make a call to my wife.” These JUnit tests have not been executed by Gradle in this environment.
- Verified that there is no Gradle executable, configured Android SDK, or Gradle wrapper JAR available here.

## Not run
- Full Android Gradle build, Android unit tests, Android Lint, APK/AAB build, and physical-device install.
- Real Gemma 4 E2B LiteRT-LM repeated-turn test, Tamil speech recognition, Gmail OAuth, runtime contacts/call permissions, private Play install/upgrade, and Play Protect review.

The workflows in `.github/workflows/` provision JDK 17, Android SDK API 36/build tools, and Gradle 8.14.4, then run audits/tests/Lint and build either a debug APK or a signed release AAB. The release workflow needs publisher-provided signing secrets. No successful Android compilation or publish-ready binary is claimed here.

## Release checklist

Follow `RELEASE_SIGNING_AND_PLAY.md` and `POST_INSTALL_ISSUE_TRACKER.md`. Do not publish until CI succeeds, device regression tests pass, Gmail OAuth package/certificate fingerprints match the installed Play-signed identity, the privacy policy has been reviewed and published at an accessible URL, and Play Console declarations are complete.
