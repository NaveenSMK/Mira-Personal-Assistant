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
MIRA requests Contacts access only after the user asks to search a contact. If the user explicitly requests a call, it may request Android phone permission to place the call after resolving a single matching contact; if that permission is denied, it opens the system dialer for user review. For multiple matches, the user must choose the intended contact. The app does not read call history.

## Data minimization and controls
Before public release, implement and document:
- A way to disconnect the Google account and revoke or clear app-held authorization state.
- Clear local-data deletion controls.
- A list of permissions and Google scopes with plain-language explanations.
- A network/data-flow inventory, including any telemetry or crash reporting.
- A contact for privacy questions and data requests.

## Contact
Add a maintainer contact before publishing this document as the final privacy policy.


## Current prototype data retention

The prototype stores explicit memories and recent task records in app-private SQLite databases. Task records include a truncated copy of the user's request (up to 4,000 characters) to track what was run. These databases are not encrypted; avoid putting passwords, access tokens, or other secrets in requests. Android app backup is disabled in the manifest. Gmail access tokens are held in memory only in this starter.
