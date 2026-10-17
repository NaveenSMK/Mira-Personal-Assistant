# Inspiration review: OpenClaw, Hermes Agent, Grok Bot, Claude Code, and Codex

This is an architecture review, not a claim of feature parity. MIRA is an Android app with a small, local-first tool surface; these projects solve broader agent/runtime problems on desktop, server, or coding-agent platforms.

## Patterns worth adopting

### OpenClaw

OpenClaw separates tools, skills, plugins, and automation rather than treating them as one generic capability. Its tool policy and execution approvals are distinct gates. MIRA adopts a small typed allowlist and per-action approval gate; it deliberately does not expose a shell tool. Future skill packs should be declarative workflow guidance, while tools remain typed executable code.

References: https://docs.openclaw.ai/tools · https://docs.openclaw.ai/tools/exec-approvals · https://github.com/openclaw/openclaw/blob/main/docs/tools/skills.md

### Hermes Agent

Hermes has a real agent loop: assemble prompt/tool schemas, call the model, parse tool calls, execute tools, append results, and iterate. It also emphasizes persistent sessions, bounded memory, model/provider adapters, observability, and interruption. MIRA now has the first minimal plan/act/observe loop and keeps model runtime behind `LocalModelEngine`. The current implementation is sequential, short-lived, and JSON-prompt-driven; it is not yet Hermes-equivalent in session persistence, recovery, native tool schemas, skills, or background jobs.

References: https://hermes-agent.nousresearch.com/docs/developer-guide/agent-loop/ · https://github.com/NousResearch/hermes-agent/blob/main/website/docs/developer-guide/architecture.md · https://github.com/NousResearch/hermes-agent/blob/main/website/docs/user-guide/features/tools.md

### Grok Bot

I interpret “gork bot” as Grok Bot. The important design lesson is not to copy its feature set blindly: a reported incident described an agent sending sensitive financial details to the wrong Slack channel. For MIRA, that reinforces explicit destination display, user confirmation before outbound communication, and keeping separate app/account scopes isolated. Current MIRA can create a Gmail draft only and never sends it.

Reference: https://www.businessinsider.com/personal-ai-agent-grok-bot-posted-bank-details-company-slack-2026-10

### Claude Code

Claude Code provides useful patterns for planning modes, tool allow/deny rules, explicit permission prompts, and bounded agentic turns. MIRA mirrors the principle that the model's intention is not authorization: the host app decides whether an action is allowed and asks the user before sensitive actions.

References: https://docs.anthropic.com/en/docs/claude-code/cli-usage · https://docs.anthropic.com/en/docs/claude-code/iam

### OpenAI Codex

Codex's approval modes and sandboxing illustrate that autonomy should be adjustable rather than a single all-or-nothing switch. For an Android personal assistant, use risk tiers: harmless local navigation can proceed, private-data reads and mutations need review, and irreversible/high-impact actions should remain blocked or require a stronger confirmation path. MIRA currently uses per-tool confirmation and does not offer a global “unrestricted” mode.

Reference: https://help.openai.com/en/articles/11096431

## MIRA implementation choices

1. **Model independence:** `LocalModelEngine` is a narrow provider interface. The current working adapter is intended for `.litertlm`; llama.cpp is only a placeholder.
2. **Typed actions:** the model emits a tool ID plus string arguments. The app validates both against a fixed registry. Unknown tools and extra arguments are rejected.
3. **Human control:** the model cannot approve itself. The app asks for confirmation before Gmail reads/drafts, memory deletion, contact/dialer, and calendar actions.
4. **Bounded autonomy:** maximum five tool calls per user turn; no background infinite loop.
5. **Local-first memory:** explicit saved facts remain in SQLite, separate from model weights. Gmail is accessed only through the user's OAuth grant.
6. **No arbitrary execution:** no shell, arbitrary code, direct phone call, arbitrary Android intent, or generic URL-open tool is exposed to the model.

## Recommended next milestones

1. Build and device-test the current source on the target OnePlus device, including LiteRT-LM GPU/CPU behavior and memory pressure.
2. Replace JSON prompting with native structured tool calls where the selected LiteRT-LM API/model supports them; keep the validator as the policy boundary.
3. Persist task plans, step outcomes, cancellation state, and audit events in `TaskStore`; add WorkManager only for user-scheduled/background tasks.
4. Add a tool capability/permission screen and a visible per-step execution timeline.
5. Add tests for malformed tool calls, prompt injection in email snippets, denial/cancel, repeated tool loops, app-not-found errors, and model swap failures.
6. Add Tamil phrase/argument tests and verify Tamil ASR/TTS on the actual device before advertising bilingual agent execution.
7. Implement model adapters one at a time, with file format checks and device compatibility tests; do not present an adapter in the UI as available until it really loads a model.
