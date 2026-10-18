# MIRA Agent Architecture

## Product goal

MIRA should behave like a personal agent, not just a voice-command launcher. It should understand goals, plan a sequence of actions, use approved tools, verify results where possible, remember durable preferences, and resume tasks after the app is closed.

## Lessons from existing agents

- **OpenClaw:** separate agent runtime, tool registry, persistent workspace, skills, sessions, and explicit tool policies. Its memory/workspace documentation is a useful model for durable notes and daily history.
- **Hermes Agent:** toolsets, persistent user/profile memory, session search, and skills that can be improved from repeated workflows.
- **Meta Muse:** goal tracking, multi-step plans, background work, app connectors, and approvals for consequential actions.
- **Grok Bot:** persistent work environment, browser/filesystem tools, multi-step delegated work, and reviewable drafts/approvals.

MIRA should adopt these patterns rather than copy any one implementation. A mobile-only Android app has different lifecycle, resource, background-execution, and privacy constraints from a desktop/cloud agent.

## Proposed layers

1. **Conversation and voice**
   - Push-to-talk first; wake-word detection only if battery/privacy implications are acceptable.
   - English, Tamil, and code-switching; language setting is user-controlled.
   - Speech recognition and TTS providers are disclosed as local or network-backed.

2. **Agent orchestrator**
   - Convert a request into a structured intent/plan.
   - Select tools from an allowlisted registry.
   - Run one step at a time, validate tool arguments, observe results, and decide whether to continue or ask the user.
   - Keep an audit trail of plans, actions, outcomes, failures, and approvals.

3. **Tool registry**
   - Android intents: alarms, timers, dialer, contacts picker, maps, YouTube, calendar, share sheet.
   - App APIs/connectors: Gmail first; other integrations only when their official APIs or Android contracts support them.
   - Web search/browser, reminders, local files, and future smart-home integrations.
   - Each tool declares its required permissions, risk level, argument schema, and whether approval is required.

4. **Memory**
   - User profile: stable preferences and communication style.
   - Explicit memories: facts the user asks MIRA to remember.
   - Episodic memory: summaries of prior sessions and unfinished tasks.
   - Procedural memory/skills: reusable workflows that worked, with versions and rollback.
   - Retrieval: keyword search first; embeddings/vector retrieval can be added after the core works.
   - All memories can be reviewed, corrected, exported, and deleted.

5. **Model runtime**
   - Local model is the default for private and routine tasks where quality permits.
   - Model adapter stays separate from tools and memory so models can be swapped.
   - Current prototype adapter uses LiteRT-LM `.litertlm` bundles; GGUF files require a separate llama.cpp adapter.
   - Optional cloud model fallback must be opt-in and show what information leaves the device.
   - The current adapter loads `.litertlm` bundles through LiteRT-LM; GGUF files need a separate llama.cpp adapter.

6. **Task and background scheduler**
   - Store task state durably in a database.
   - Use Android WorkManager for deferrable, persistent work; use foreground services only for justified user-visible ongoing tasks and in accordance with Android restrictions.
   - Show status and allow pause/cancel. Do not promise uninterrupted background execution where Android does not guarantee it.

## Memory and learning loop

1. Record a user-approved preference or explicit "remember this" request.
2. Store it locally with source, timestamp, and category.
3. Retrieve only relevant memories for the current task.
4. After a multi-step workflow, offer to save a reusable skill or preference.
5. Never silently turn private emails, messages, contacts, or sensitive data into long-term memories.
6. Do not claim the model weights improve from use. Initial "learning" means updating memory and approved skills, not online model fine-tuning.

## Safety model

- Read-only operations can usually run after authorization.
- Low-risk reversible actions can be configured for one-tap or pre-approved execution.
- External communications, purchases, deleting data, sharing private content, or changing security settings require a preview and explicit confirmation by default.
- Tool arguments must be validated independently of model output.
- Retrieved emails, web pages, documents, and messages are untrusted data, never privileged instructions.
- Per-tool permissions, revocation, and a visible action history are mandatory.
- Use least privilege; do not request broad Android permissions when an intent/picker suffices.

## Build order

1. Local explicit memory and review/delete controls.
2. Typed tool registry and action-risk policy.
3. Actual local LLM inference.
4. Planner/executor loop with observable outcomes and bounded steps.
5. Durable tasks and reminders.
6. Skill learning with user approval and version history.
7. Optional cross-device sync, encrypted and opt-in.
