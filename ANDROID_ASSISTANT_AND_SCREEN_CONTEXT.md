# Android default assistant and screen context

## What this implementation adds

- A `VoiceInteractionService` entry point protected by `BIND_VOICE_INTERACTION`.
- A `VoiceInteractionSessionService` and assistant session UI.
- A `RoleManager.ROLE_ASSISTANT` request button.
- Screen text extraction from `AssistStructure` when Android and the foreground app permit it.
- System-provided screenshot handling, downsampled to a maximum 1024-pixel edge.
- Bundled on-device ML Kit Latin-script OCR as a fallback.
- A bounded, in-memory screen context. It is not written to SQLite and is not uploaded by the screen-capture path.
- A clear-context button and generation token so delayed OCR cannot repopulate context after a clear/new capture.
- Screen content is explicitly labelled untrusted in the model prompt to reduce prompt-injection risk.

## Enable it on the phone

1. Install the app build.
2. Open MIRA and tap **Set MIRA as default Android assistant**.
3. Accept Android's system role picker.
4. Use the phone's configured assistant gesture/button to summon MIRA while another app is foregrounded.
5. Tap **Ask MIRA about this screen**.
6. Ask a question by voice. The current text-based model path receives extracted screen text as context.
7. Use **Clear captured screen context** when finished.

OEM settings and gestures differ. On OxygenOS, the exact menu label/gesture may differ by OS release.

## Hard platform boundaries

- This is not a privileged/system app. Default-assistant role does not grant unrestricted access to all app internals.
- The OS can omit assist data or screenshots based on the user's assist settings, policy, foreground app, or secure-window flags. Banking/password/DRM apps may intentionally block capture.
- `AssistStructure` is best-effort UI text; custom canvases, games, video, maps, and web content may not expose meaningful nodes.
- Bundled screenshot OCR currently targets Latin script. Tamil text can be read when the app exposes it through `AssistStructure`; Tamil screenshot OCR is not yet integrated.
- The screenshot bitmap is captured and kept in memory for future multimodal support, but the current `LocalModelEngine.generate(prompt: String)` API does **not** feed image pixels to the model. Current reasoning is based on assist text and OCR, not visual image understanding.
- The recognition-service component delegates to an installed external recognizer on older Android releases, excluding itself to avoid recursion. MIRA's own microphone UI remains user-initiated. Speech may be processed by the configured recognizer/provider; MIRA does not claim that system speech recognition is always on-device. Always-on wake-word detection is not implemented.
- No `AccessibilityService` is registered. Accessibility is a powerful user-granted capability and should not be used as a generic workaround for app restrictions. A dedicated accessibility feature should only be added for a clearly disclosed, user-benefiting accessibility use case.

## Security and privacy notes

- Screen capture occurs only as part of an explicit assistant invocation; it is not a background screen recorder.
- Screen text and image are held in process memory, not persistent storage. Clear them from the UI when done; process death also discards them.
- Screen content may contain untrusted instructions. It is labelled untrusted in the prompt; tool allowlists and approval gates remain the authority for actions.
- The app's separate agent task store may retain user requests. This is distinct from screen-context storage.
- The screenshot capture path does not send content to a server. Do not add cloud multimodal calls without a separate opt-in, visible disclosure, and a privacy review.

## Validation status

Static XML and source audits pass. A full Android build, role-picker verification, assist callback verification, and device tests have not been run in this environment because the Android SDK and Gradle executable/wrapper are unavailable. Test on the target OnePlus device before release.
