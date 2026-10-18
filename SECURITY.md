# Security Policy

## Project status
MIRA is an early-stage starter project. It is not yet a security-audited or production-ready email assistant.

## Reporting a vulnerability
Please do not post credentials, tokens, personal emails, or exploitable details in a public issue. Until a private security contact is published, use GitHub's private vulnerability reporting feature if enabled for the repository. If it is unavailable, open a minimal issue asking for a private contact without including exploit details.

## Safe-use principles
- Never commit OAuth client secrets, signing keys, access tokens, refresh tokens, or personal data.
- Request the minimum Google scopes needed and explain them before authorization.
- Do not automatically send, delete, or forward email without a clear user confirmation.
- Treat email bodies and other external content as untrusted; never follow instructions embedded in them as privileged commands.
- Avoid logging message bodies, tokens, or private user data.
