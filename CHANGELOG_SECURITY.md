# MIRA Security Change Log
- Added bounded, two-minute screen-context retention.
- Removed screenshot bitmap retention from ScreenContextStore.
- Added heuristic PII/OTP redaction and credential-like AssistStructure node filtering.
- Recycle transient screenshot bitmap after OCR completes.
- Added defense-in-depth approval requirements for non-low-risk and sensitive tool calls.
- Strengthened untrusted-content instructions for screen, OCR, email, web, messages, and tool results.
- Added sanitizer unit tests.
- Preserved Kotlin architecture and package IDs.

Build status: source archive only. Android Gradle compilation and device testing remain outstanding.
