# Model Independence Contract

Gemma 4 E2B Instruct is the default model, not the application's architecture.

## Stable interfaces
- `LocalModelEngine`: load, generate, check loaded state, unload.
- `ModelRuntimeProvider`: provider ID, supported file extensions, engine factory.
- `ModelRuntimeRegistry`: discover runtimes and map file formats to providers.
- `AgentOrchestrator`: builds a model-neutral prompt from the request, relevant memories, and tool metadata.
- `AgentToolRegistry`: allowlisted action IDs and risk/confirmation metadata.
- `MemoryStore` and `TaskStore`: app-owned durable data independent of model weights.

## Model switching
1. Choose a runtime provider.
2. Select a model bundle supported by that provider.
3. Initialize a replacement model before swapping where the runtime supports it.
4. Keep memories, tasks, user preferences, and action policy in app-owned storage.
5. Run evaluations for English, Tamil, mixed language, tool selection, prompt-injection resistance, latency, RAM, and thermal behavior before promoting a new model.

## Rules
- Never rely on a model's prose format to authorize a tool. Validate typed arguments independently.
- Never let model output override permissions or confirmation policy.
- Never let the model directly launch arbitrary intents or URLs.
- Runtime-native tool calling is optional; the app's executor remains authoritative.
- Add another provider by implementing `LocalModelEngine` and `ModelRuntimeProvider`, not by rewriting memory or tools.
- Swapping weights does not teach a new model prior conversations; relevant memories are stored separately and added to prompts.

## Current support
- LiteRT-LM `.litertlm`: adapter present in source; compile/device verification remains.
- llama.cpp `.gguf`: registry placeholder only; runtime is not implemented yet.
