# MIRA — Connected & Wise + Screen Context: Security Review

## Scope

Static source review of the new default-assistant integration, screen text capture, screenshot handling, OCR fallback, and prompt-context wiring. This review does not assert that the Android project compiles or that device behavior has been verified.

## Findings and mitigations

| Risk | Mitigation | Remaining limitation |
|---|---|---|
| Unauthorized service binding | Voice interaction services require `BIND_VOICE_INTERACTION`; recognition service is separately permission-protected and delegates to a different installed recognizer on older Android releases. | Verify service resolution and role picker on device. |
| Unintended screen capture | Uses Android's user-invoked assistant session callbacks; no background capture loop or AccessibilityService. | OEM and foreground app behavior varies. |
| Sensitive screen content persisted | Screen context and screenshot are held in memory only; explicit clear button; process death drops context. | Memory can remain until cleared or process death. |
| Stale OCR callback restores cleared content | Generation token invalidates delayed OCR updates after clear/new invocation. | Needs device race testing. |
| Excessive memory use | Screenshot downsampled to max 1024-pixel edge; text limited to 8,000 chars; accessibility tree limited to 350 nodes. | Measure heap/latency on target device. |
| Prompt injection through page text | Screen content is labelled untrusted in the model prompt; tool registry/confirmation gates remain authoritative. | Model prompt text alone is not a security boundary; test adversarial pages. |
| Network disclosure | Screen capture/OCR code makes no network calls; OCR uses bundled on-device ML Kit Latin recognizer. | Other app features, such as Gmail, use network separately. |
| Protected app/window capture | No attempt to bypass `FLAG_SECURE` or device policy. | Secure apps may return no text/screenshot. |
| Accessibility API overreach | No `AccessibilityService` registered. | No arbitrary cross-app clicking or universal UI control. |
| Multimodal overclaim | Current engine interface accepts text prompts only; screenshot is captured and OCR'd but image pixels are not passed to the model. | Visual reasoning for charts/photos needs a multimodal runtime adapter. |

## Automated checks

`tools/static_audit.py`: 29/29 checks passed. Manifest and assistant XML files parse successfully.

These checks include exported component protection, bounded agent loop/timeouts, no forbidden dynamic execution APIs, presence of assistant/recognition services, bounded screen context, clear-context path, and untrusted prompt labelling.

## Not yet verified

- Full Gradle build / Android Lint / dependency resolution.
- Runtime compatibility of `BIND_SPEECH_RECOGNITION_SERVICE` on the target OxygenOS version.
- Android RoleManager picker visibility and successful selection.
- AssistStructure and screenshot callbacks on OnePlus 15R.
- ML Kit OCR callback lifecycle, thermal/memory impact, and Tamil behavior.
- Voice assistant invocation from the device's configured gesture.
- Native multimodal image input into LiteRT-LM.
