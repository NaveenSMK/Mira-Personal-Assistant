# MIRA Memory Design

## Implemented in this prototype

A local SQLite table stores short memories only when the user explicitly says:
- `Remember that I prefer short answers`
- `Remember my usual meeting time is 10 AM`
- `What do you remember about me?`
- `Forget my usual meeting time`
- Or uses the View/Clear Memory controls.

The current implementation is intentionally basic: local text entries, duplicate prevention, listing, substring deletion, and clear-all. The current prompt builder can include a bounded subset of saved memories in local model prompts, but relevance ranking and persistent multi-turn memory behavior are still early-stage.

## Future memory categories

| Type | Example | Default policy |
|---|---|---|
| User profile | Prefers Tamil responses in the evening | Ask/confirm before saving when inferred |
| Explicit fact | “Remember my project is called MIRA” | Save on direct request |
| Episodic | Summary of a project decision | Offer to save; retain source and date |
| Task state | “Waiting for a reply from the clinic” | Save as a task, with status and expiry |
| Procedural skill | Steps that successfully book a calendar event | Propose, test, version, and ask before enabling |

## Rules

- Local-first; no cloud sync by default.
- Never silently memorize email bodies, WhatsApp messages, contacts, or credentials.
- Provide view, edit, forget, export, and clear-all controls.
- Avoid saving passwords, access tokens, financial identifiers, and unnecessary sensitive information.
- Store provenance and confidence for inferred facts.
- Memories can be wrong or stale; allow corrections and expiry.
- Retrieved memory is context, not authority: it cannot bypass tool permissions or confirmation requirements.
- Future semantic search must remain optional and be removable without losing the source records.

## Current limitation

The current app supports explicit save/list/delete and can pass selected saved memories into local model prompts. It does not yet have semantic/vector retrieval, automatic episodic summaries, skill learning, or comprehensive memory editing/export. This is a foundation, not a complete memory system.
