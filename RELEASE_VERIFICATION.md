# MIRA Release Verification

Date: 2026-10-18

## Checks run on this source archive
- Lightweight static audit: 46/46 passed (`python tools/static_audit.py`).
- Security source audit: 19/19 passed (`python tools/security_audit.py`).
- XML resources/manifest: 6 XML files parsed without errors.
- Accessibility policy/controller Kotlin syntax smoke compile: passed against minimal hand-written Android API stubs. This is not an Android framework compile.
- Seven policy test methods passed for credential-command rejection, financial field hints, numeric/credential-like payloads, known financial package hints, sensitive labels, restricted action targets, and ordinary labels.
- Four screen-context redaction/bounds test methods passed (OTP, PIN, passcode, email, length). Agent runtime/registry argument validation smoke checks also passed.

## Build status
- Gradle executable: not found.
- Gradle wrapper: not present in source archive.
- Android SDK / `android.jar`: not found.
- Full Gradle build / Android Lint / device tests: **not run**.
- APK build success: **not claimed**.

The GitHub Actions workflow provisions JDK 17, Android SDK, and Gradle 8.11.1, then runs the source audits before `assembleDebug`. Run the workflow and inspect the resulting APK artifact before release.

## Safety notes
- Accessibility control is user-opt-in and OS-bound.
- Sensitive credential and financial-action screens trigger manual handoff based on conservative package/label heuristics.
- The controller does not automate payment/trade submission and does not read editable field values.
- Screen heuristics are not a guarantee for unknown/localized/obfuscated interfaces; test on-device and retain the deny rules.
