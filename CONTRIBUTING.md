# Contributing to MIRA

Thanks for your interest in improving MIRA.

## Before opening a change
- Search existing issues first.
- For bugs, include Android version, device model, steps to reproduce, and relevant logs with private data removed.
- Never attach access tokens, OAuth credentials, email contents, phone numbers, or other personal data.
- For larger changes, open an issue first to discuss the design.

## Pull requests
- Keep changes focused and explain why they are needed.
- Do not commit secrets, signing keys, `local.properties`, model weights, personal test data, or generated build folders.
- Include manual test steps and state clearly what you have not tested.
- Preserve the principle of least privilege for Android permissions and Google scopes.
- Treat email, notifications, and shared content as untrusted input.

## Model and dependency contributions
- Document model/runtime name, version, source, license, and device requirements.
- Do not bundle model weights unless their terms clearly allow redistribution.
- Test English and Tamil separately; report code-switching limitations honestly.

By submitting a contribution, you agree that it will be distributed under the repository's Apache-2.0 license unless a different agreement is explicitly made.
