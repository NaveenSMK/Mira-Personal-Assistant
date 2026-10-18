# MIRA Generic Accessibility Controller

## What it adds

MIRA now includes an opt-in Android `AccessibilityService` controller. It is an additional tool path that preserves the existing voice-interaction service, local model engine, agent registry, Gmail connector, memory store, and deterministic Android actions.

The model can propose one named UI action at a time:

- Click one exact, unique, visible label.
- Enter up to 500 characters into one exact, unique editable field selected by its hint text. The controller does not read the field's current value.
- Scroll forward or backward.
- Use Android Back.

Commands expire after 15 seconds. The agent waits for the service's result before continuing to the next proposed action. No arbitrary intents, raw coordinates, shell commands, or gesture injection are exposed.

## Enable it

1. Install MIRA on the Android device.
2. Open MIRA and press **Enable MIRA Accessibility Controller**.
3. In Android Settings, find MIRA under installed accessibility services and enable it yourself.
4. Return to an ordinary test screen (for example, Android Settings or a notes app) and make one low-risk request at a time.
5. Disable the service from Android Settings when you do not need it.

MIRA cannot enable its own accessibility service. Android or an app may refuse to expose content/actions, and MIRA does not bypass those restrictions.

## Safety checkpoints

- Banking/trading/finance-related package-name hints are always treated as a manual-handoff checkpoint.
- Credential, verification, account, and financial-action labels trigger conservative screen checkpoints. Password-typed fields are checkpoints.
- `Send`, `Submit`, `Pay`, `Transfer`, `Withdraw`, `Buy`, `Sell`, `Trade`, `Confirm`, `Approve`, `Authorize`, `Delete`, `Purchase`, and similar targets are not clicked through the generic controller.
- UI text resembling credential automation is rejected before the request is written to the task store. Text values from UI-entry tool calls are omitted from agent history.
- The controller never reads the current text from editable fields and does not log screen text, entered text, or exception details.
- If a screen is inaccessible, ambiguous, or matches a checkpoint, the action stops and the user must continue manually.

For payments, transfers, orders, trades, and other financial submissions, the controller does not provide a submission action. The user must perform such actions directly in the target app.

## Limitations that must be understood

Android accessibility trees vary by app and Android version. Checkpoint matching is conservative but heuristic, primarily using package-name hints and English labels. An unknown app or localized/obfuscated interface may not expose enough information for perfect classification. Therefore this should not be represented as a guarantee that every possible financial/authentication screen can be identified. Keep the financial/action target deny rules in place, test on-device, and do not weaken them to make a blocked workflow continue.

The text-entry action intentionally requires an exact visible hint. Fields without a usable hint are unsupported rather than targeted by guessed coordinates or existing contents.

## Tests and verification

- `python tools/static_audit.py`
- `python tools/security_audit.py`
- `app/src/test/java/com/example/voicefirst/AppSafetyPolicyTest.kt`

These checks cover source-level boundaries and policy regressions. A real build and device test still require JDK/Gradle, Android SDK platform 35, and dependency resolution. See `MIRA_FINAL_STATIC_REVIEW.md` for the verification actually performed for this source archive.
