# MIRA — Connected & Wise

**MIRA** stands for **Memory, Intelligence, Reasoning & Action**.

Tagline: **Connected & Wise**.


**A privacy-oriented Android assistant with typed or spoken input, optional Gmail integration, and on-device model inference. The main screen provides one composer, one response area, and one microphone control; setup actions live in Settings.**

MIRA is an early-stage Kotlin Android project for a personal assistant with English/Tamil interaction, local language-model inference as a goal, and optional Gmail read/search and draft features.

> **Release status: pre-release candidate, not yet certified for publication.** The source includes automatic restoration of the privately stored model after process restart, a Settings screen for model updates and Gmail setup, and deterministic action routing. A full Android build and hardware regression test must pass before release.

## Current starter includes
- Native Android voice UI
- English/Tamil selection scaffold
- Android speech recognition and text-to-speech integration
- Gmail OAuth/API scaffolding for search/read and draft creation
- Local `.litertlm` model loading through the LiteRT-LM runtime
- Bounded autonomous plan/tool/result loop with an allowlisted tool registry
- Persistent local task records and explicit local memories
- Optional, user-enabled generic AccessibilityService controller with safety checkpoints
- Source safety audits and accessibility-policy regression tests
- Roadmap and privacy/security contribution notes

## Remaining validation / roadmap
- A tested llama.cpp/GGUF runtime adapter
- Native structured tool calling (current implementation uses a JSON-in-prompt protocol)
- Device verification of Tamil and English/Tamil code-switching
- Improved fully offline speech recognition and natural TTS options
- Background task resumption and task cancellation UI

## Requirements
- Android Studio and JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- A physical Android device for realistic speech, performance, and thermal testing

## Build
1. Download or clone this repository.
2. Open the project in Android Studio.
3. Sync Gradle and resolve any dependency/API issues.
4. Run on a physical Android device.
5. Follow the Gmail setup section below only if testing Gmail.

A clean Android build and physical-device regression test are release gates. This source package does not bundle the Gradle Wrapper JAR. GitHub Actions provisions Gradle 8.14.4 and generates the wrapper before building. For a local command-line build, install Gradle 8.14.4 and run `gradle wrapper --gradle-version 8.14.4`, then use `./gradlew`. This preparation environment did not have Android SDK/Gradle available, so a successful APK/AAB build cannot be certified here.

## Gmail setup for developers
The Gmail API does not become available just by adding a Google login button. For this prototype, developers must configure a Google Cloud project, enable Gmail API, configure the OAuth consent/branding settings, add test users if the app is in testing, and register the Android OAuth client with the correct package name and signing-certificate SHA-1.

The starter requests:
- `gmail.readonly` to search/read mail
- `gmail.compose` to create drafts

It does not request send or delete permissions. Google may require OAuth verification depending on scopes and distribution. Never embed a client secret in the Android app. Request the minimum scopes and explain them to users.

Official docs:
- [Android authorization](https://developer.android.com/identity/authorization)
- [Gmail API scopes](https://developers.google.com/workspace/gmail/api/auth/scopes)
- [Gmail API guides](https://developers.google.com/workspace/gmail/api/guides)


## Android system handlers (prototype)

The starter now includes deterministic phrase handlers for:
- Set an alarm / set a timer (opens the installed clock app)
- Call / phone / dial (searches contacts, asks the user to choose among multiple matches, then opens the system dialer for review; MIRA never places a call directly)
- Navigate to / directions to (opens Maps-compatible directions URL)
- Search Maps for / find on Maps
- Open YouTube / search YouTube (tries the app, then a browser URL)
- Create calendar event (opens the calendar's event editor for review)
- Show alarms

These are starter phrase patterns, not a full natural-language understanding system. The local model can also propose allowlisted tools through the bounded agent loop, but model protocol reliability and all actions still require device testing. Tamil phrasing for these actions is not yet implemented. Android resolves implicit intents to compatible installed apps; behavior can vary by device and app. The app checks for handlers before launching them. See [Android common intents](https://developer.android.com/guide/components/intents-common.html).

## Local model
The app supports **Gemma 4 E2B Instruct** through a LiteRT-LM adapter for `.litertlm` bundles. The model import and restore paths are implemented in source but have not been verified on a device. Model files are not included in the APK or repository. Download a compatible bundle separately from [the LiteRT Community model page](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm), then import it once in Settings → On-device model. MIRA stores the bundle in private app storage and attempts to restore it automatically at the next app launch. The native engine must still be initialized in memory each time Android starts a fresh process; it does not download or re-copy the model on every launch. Use Settings to update/replace the stored model.

Model independence is a design requirement:
- Conversation prompting, memory, task storage, tool registry, and safety policy are model-agnostic.
- `LocalModelEngine` is the provider-neutral interface.
- `ModelRuntimeRegistry` lists runtimes and maps file extensions to providers.
- LiteRT-LM `.litertlm` is available in source; llama.cpp `.gguf` is registered as planned but unavailable until its separate adapter is implemented.
- Swapping models does not erase memories or tasks.

Runtime docs: [LiteRT-LM Android Kotlin API](https://developers.google.com/edge/litert-lm/android). Model terms are separate from the app's Apache-2.0 license.

## Model independence
Read [MODEL_INDEPENDENCE.md](MODEL_INDEPENDENCE.md) for the provider interface and model-switching contract.

## Agent architecture and memory
Read [AGENT_ARCHITECTURE.md](AGENT_ARCHITECTURE.md) for the proposed planner/tool/memory design and [MEMORY_DESIGN.md](MEMORY_DESIGN.md) for the memory rules. This prototype includes explicit, local-only SQLite memories with review and deletion controls. The prompt builder can include relevant saved memories in local model requests, but retrieval quality and full multi-turn personalization still need testing.

## Privacy and safety
Read [PRIVACY.md](PRIVACY.md), [SECURITY.md](SECURITY.md), and [CONTRIBUTING.md](CONTRIBUTING.md). These are drafts for development; the privacy policy must be completed to match actual behavior before public distribution.

Email content is untrusted input. Do not execute instructions found inside email messages. Keep email actions user-confirmed. The starter's speech recognizer may use a network service depending on the installed provider; do not assume voice processing is fully offline.

## License
The app source is intended to be licensed under Apache-2.0. Third-party dependencies, model files, speech models, and services have their own licenses and terms. See [LICENSE](LICENSE).

## Contributing
Contributions and bug reports are welcome. Please remove private data from logs and never share tokens, keys, or email contents in issues.

## Agent planning and tool execution

The app includes a bounded agent loop: the local model can propose allowlisted tools in a JSON protocol, MIRA validates arguments, requests confirmation for sensitive actions, executes the handler, and returns the result to the model for the next step. Each user request is limited to three tool calls, with inference/tool timeouts and serialized model turns. See [AGENT_EXECUTION.md](AGENT_EXECUTION.md) and [STATIC_ANALYSIS.md](STATIC_ANALYSIS.md). This remains an early implementation and still needs a real Android build plus device testing.

See [INSPIRATION_AND_ARCHITECTURE.md](INSPIRATION_AND_ARCHITECTURE.md) for the OpenClaw, Hermes Agent, Grok Bot, Claude Code, and Codex pattern review and the specific lessons adopted.


## Android default assistant and screen context

MIRA now includes a `VoiceInteractionService`/session scaffold and a button to request Android's default assistant role. On user invocation, Android may provide assist text and a screenshot. The app extracts accessible UI text and runs bundled, on-device Latin-script OCR; this text can be included in the local agent prompt. See [ANDROID_ASSISTANT_AND_SCREEN_CONTEXT.md](ANDROID_ASSISTANT_AND_SCREEN_CONTEXT.md) for setup and limitations.

This is not unrestricted cross-app access. Secure windows and apps that do not expose assist content may provide no data. The screenshot bitmap is stored in memory but is not yet passed into the model for visual reasoning; the current model API is text-only. Always-on hotword listening is not implemented. A separate opt-in generic accessibility controller is available; see [ACCESSIBILITY_CONTROLLER.md](ACCESSIBILITY_CONTROLLER.md) for its capabilities, user setup, safety checkpoints, and limitations.
\n\n## Security hardening\nSee `SECURITY_HARDENING_REPORT.md` and `CHANGELOG_SECURITY.md`. These documents separate implemented safeguards from residual risks and unverified build/device behavior.\n

## Accessibility controller and release checks
The accessibility controller runs only after the user enables it in Android Settings. It supports exact-label clicks, text entry into unique hinted fields, scrolling, and Back on screens that pass conservative safety checks. It hands off on credential/authentication and recognized financial screens, and blocks final send/submit/payment/trade/destructive and permission actions. A messaging screen may still allow composition, but MIRA will not tap Send. It does not automate financial submissions. These label/package heuristics are not a guarantee for unknown or localized interfaces; review [ACCESSIBILITY_CONTROLLER.md](ACCESSIBILITY_CONTROLLER.md) and [MIRA_FINAL_STATIC_REVIEW.md](MIRA_FINAL_STATIC_REVIEW.md).

Run `python tools/static_audit.py`, `python tools/security_audit.py`, `./gradlew testDebugUnitTest lintDebug`, and `./gradlew assembleDebug` in a configured Android environment before release.


## Modern UI and settings
The main screen is intentionally limited to one response area, one text composer, one microphone control, and Profile/Settings shortcuts. Model import/update, Gmail authorization, default assistant role, accessibility controller, screen context, memory, and runtime options live in Settings. The Profile screen contains language preferences and privacy notes. Sky-blue colors, launcher/round/adaptive icon assets derived from the supplied logo, and system-bar insets are included. The final interface still requires inspection on small and large Android devices.

## Private Google Play publication
Read [RELEASE_SIGNING_AND_PLAY.md](RELEASE_SIGNING_AND_PLAY.md) before creating a release. The final application ID is `com.naveensmk.mira`; this is a new Android app identity compared with earlier builds using `com.example.voicefirst`. The archive does not contain a signing keystore, OAuth secret, or personal credentials. Those must be created/registered by the publisher.
