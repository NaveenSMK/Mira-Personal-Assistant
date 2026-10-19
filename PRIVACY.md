# MIRA Privacy Notes (Draft)

**This document is a development draft, not a substitute for a reviewed privacy policy.** Update it to match actual app behavior before distributing MIRA.

## Intended design
MIRA is intended to prioritize on-device speech and language processing where supported. The local model is expected to be downloaded separately by the user. A selected local model file should be stored in app-private storage where practical.

## Google and Gmail
If Gmail features are enabled, the user explicitly connects a Google account and grants the requested Google permissions. The current starter requests Gmail read-only and compose permissions to search/read email and create drafts. It does not request permission to send or delete email.

Google APIs necessarily communicate with Google to perform Gmail operations. Email content retrieved for a user request may be passed to the on-device model when local inference is implemented. The app must not send Gmail content to an unrelated server unless that behavior is added, clearly disclosed, and consented to.

## Speech and text-to-speech
Android speech recognition may use an installed provider and may send audio to that provider, depending on device settings and implementation. Android text-to-speech may use installed local or network-backed voice services. Do not describe speech as fully offline until the exact configured providers have been tested.

## Contacts and calls
MIRA requests Contacts access only after the user asks to search a contact. If the user explicitly requests a call, MIRA resolves the contact and opens the system dialer for the user to review the number and tap Call. MIRA never uses a direct-call intent and does not request phone-call or call-history permission. For multiple matches, the user must choose the intended contact. The app does not read call history.

## Data minimization and controls
Before public release, review and verify:
- The local Gmail disconnect and full Google Account grant-revocation instructions.
- Local-data deletion controls and retention behavior on a device.
- The current permissions and Google scopes with plain-language explanations.
- A verified network/data-flow inventory, including any telemetry or crash reporting.
- A maintainer contact for privacy questions and data requests.

## Contact
Add a maintainer contact before publishing this document as the final privacy policy.


## Current prototype data retention

MIRA stores explicit memories in an app-private SQLite database. New task records store a generic task label, status, and timestamps only, with a 90-day retention limit and a 500-record cap. Earlier versions may have stored request text; Settings provides a clear-history action that removes those legacy details. These databases are not encrypted; do not put passwords, access tokens, or other secrets into memory entries. Android app backup is disabled in the manifest. Gmail access tokens are held in memory only.


## Gmail disconnection

MIRA's Settings screen offers a local disconnect that clears the access token held in memory and clears MIRA's local authorization flag. This does not by itself revoke the OAuth grant at Google. To revoke the grant completely, remove MIRA's access from the user's Google Account security / third-party connections settings. Gmail read and draft scopes may require Google OAuth verification depending on distribution and user type.

## Task history

New task records contain only a generic title, status, and timestamps; MIRA does not intentionally persist the raw request text in new task records. Older app versions may have saved request details. Use Settings → Memory & privacy → Clear task history to delete all records, including those legacy details. Task metadata is retained for up to 90 days and capped at 500 records.

## Accessibility disclosure

Before opening Android Accessibility settings from MIRA, the user must affirm a separate in-app disclosure. The optional controller works only on user-requested visible UI tasks, is designed to hand back sensitive/financial or final actions, and processes screen labels transiently on-device. The Play Console AccessibilityService declaration and required demo materials are separate publication steps and must be completed before distribution.
