# MIRA — Rebrand Change Log

Date: 2026-10-16

## Branding
- User-facing project name: **MIRA**.
- Brand expansion: **Memory, Intelligence, Reasoning & Action**.
- Tagline: **Connected & Wise**.
- Added the supplied logo at `branding/mira-logo.png`.
- Set the Android application display label to `MIRA`.
- Updated project documentation to use MIRA branding where appropriate.

## Compatibility decisions
- Kept Kotlin and the existing agent, memory, task, model-runtime, Gmail, Android action, and screen-context architecture.
- Kept package identifiers, service class names, and internal filenames stable to avoid unnecessary migration risk.
- No model weights or credentials are included in this archive.

## Validation status
- This is a source/branding update, not a compiled APK.
- The included static-audit report is from the source project and should be rerun after this change.
- A full Gradle Android build and device testing on the target OnePlus device are still required.
- Adaptive launcher icon generation and final trademark/app-store/domain checks remain outstanding.


## Accessibility and compile follow-up (2026-10-18)
- Integrated a generic, user-enabled AccessibilityService controller while preserving the existing MIRA architecture.
- Added conservative authentication/financial/permission checkpoints, single-action expiry, unique-label selection, safe hinted-field text entry, and manual handoff for send/submit/pay/transfer/trade/destructive actions.
- Added policy unit tests, security/source audits, documentation, and CI steps for source audits, JVM unit tests, and Android Lint.
- Applied targeted fixes for the earlier Kotlin build errors. Full Android Gradle build remains unverified in this source environment; see `MIRA_FINAL_STATIC_REVIEW.md`.
