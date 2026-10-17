# MIRA Roadmap

## Stage 0 — Starter project
- [x] Native Kotlin Android scaffold
- [x] Voice UI and English/Tamil selection scaffold
- [x] Gmail authorization/API scaffold for search/read and draft creation
- [x] Open-source project documentation starter
- [ ] Compile and run on a physical OnePlus device
- [ ] Verify OAuth flow with a configured Google Cloud project

## Stage 1 — Local assistant
- [ ] Integrate a maintained llama.cpp Android runtime or JNI layer
- [ ] Load a user-selected compatible GGUF model safely
- [ ] Add bounded context, cancellation, and memory-aware model settings
- [ ] Test Gemma 2 2B quantization, latency, thermal behavior, and RAM use
- [ ] Add tool routing with strict typed schemas and confirmation gates

## Stage 2 — Voice quality
- [ ] Test English, Tamil, and English/Tamil code-switching
- [ ] Decide whether speech recognition is online, offline, or selectable
- [ ] Test installed Android TTS voices and offline availability
- [ ] Add push-to-talk and clear listening/processing/speaking states

## Stage 3 — Gmail safety and UX
- [ ] Improve consent copy and error states
- [ ] Test account selection, authorization denial, expired tokens, and revocation
- [ ] Add explicit draft review before saving
- [ ] Add safe handling for malicious instructions in email content
- [ ] Do not add send/delete operations without separate user confirmation

## Stage 4 — Public beta
- [ ] Build reproducible debug/release APKs
- [ ] Test on multiple Android versions and devices
- [ ] Complete privacy policy and data-flow documentation
- [ ] Review third-party licenses and Google OAuth verification requirements
- [ ] Publish issue templates, release notes, and known limitations
