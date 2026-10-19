# MIRA agent execution design

## Implemented execution loop

MIRA now uses a model-neutral JSON protocol around `LocalModelEngine`. The model proposes one typed action at a time; the app validates the tool ID and arguments against an allowlist; the app asks for approval for sensitive actions; the Android/Gmail/memory handler runs; then the result is fed back to the model for the next step. The loop stops after three tool calls per user request.

Expected model responses:

```json
{"type":"tool_call","tool":"maps.search","arguments":{"query":"nearest pharmacy"},"rationale":"Find the requested place"}
```

```json
{"type":"final","message":"I opened Maps with the search results."}
```

If the model fails to emit this protocol, MIRA shows its output as text rather than executing guessed actions.

## Tool inventory

- `alarm.set`: opens Android alarm editor with a validated 24-hour time.
- `timer.set`: opens Android timer for up to 24 hours.
- `maps.directions`, `maps.search`: opens web-backed Maps intents/URLs.
- `youtube.search`: opens YouTube search results.
- `phone.dial_contact`: contact picker then dialer; never places a call automatically.
- `calendar.create`: opens an editable calendar event; the user reviews and saves it.
- `gmail.search_unread`: reads recent unread message metadata/snippets after confirmation and Gmail OAuth.
- `gmail.create_draft`: creates a Gmail draft after confirmation; does not send it.
- `memory.save`: local-only; only executes when the original request explicitly starts with an English remember/save-to-memory phrase.
- `memory.forget`: deletes matching local memory after confirmation.

## Safety boundaries

- No arbitrary shell, JavaScript evaluation, arbitrary URL tool, arbitrary Android intent, or direct phone call.
- Unknown tool IDs, unknown arguments, invalid types, missing arguments, and oversized inputs are rejected.
- Sensitive actions require a per-action confirmation dialog. The model cannot approve its own request.
- Email content and tool results are treated as untrusted context. Gmail read access is scoped to the current user's OAuth consent; drafts are not sent.
- The loop is bounded, but is not a formal proof against prompt injection. Continue to test hostile messages, malformed JSON, and cancellation behavior.

## Known limitations

- The protocol relies on the model following JSON instructions; it is not using LiteRT-LM's native structured tool-call API yet. Models with weak instruction following may answer in plain text instead of invoking tools.
- The planner is sequential, not parallel; there is no background WorkManager scheduler, resumable long-running plan, or cross-session task queue integration yet.
- Android phrase shortcuts remain mostly English. Tamil speech recognition/TTS depends on the device's installed recognition and speech services; Tamil tool planning has not been validated.
- Gmail OAuth requires a correctly configured Google Cloud project and must be tested with the target app signing configuration.
- The llama.cpp provider remains a placeholder. Only `.litertlm` is implemented in the current runtime adapter.
- No successful Android build or on-device test has been run in this environment (Gradle/Android SDK not available here).
