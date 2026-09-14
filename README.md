# Project Kinetic

[![CI](https://github.com/hamzah105/Kinetic/actions/workflows/ci.yml/badge.svg)](https://github.com/hamzah105/Kinetic/actions/workflows/ci.yml)

Kinetic is an experimental, Kotlin-first native Android framework for autonomous and semi-autonomous AI agents.

> **Core rule:** models may propose intent; Kinetic owns validation, policy, approval, durability, and execution.

Kinetic is still under active development. There is no stable production release yet, and host-side verification must not be treated as real-device acceptance.

## What exists today

- Pure Kotlin/JVM agent kernel with typed model, tool, policy, session, and logging contracts.
- Durable Room-backed conversations, turns, approvals, effects, summaries, and governed memory.
- Provider-neutral streaming adapters for Fake/test, OpenAI-compatible chat, direct OpenAI Responses, and an experimental local llama.cpp provider.
- Explicit USER/SESSION memory governance with provenance, supersession, conflict handling, and bounded retrieval.
- Structured tool flow through `ToolRegistry -> Policy -> ApprovalGate -> EffectLedger -> execution`.
- User-mediated Android handoffs for HTTPS URLs, sharing, allowlisted settings, clipboard, dialer, and email composition.
- Experimental local Qwen3-0.6B Q4_0 inference with verified manual GGUF import, local summarization, cancellation, and model-data lifecycle controls.
- Deterministic opt-in Hybrid routing. **MANUAL remains the default** and provider failures do not silently fall back to another provider.

## Architecture

```text
:app
   ├── :core:kernel
   ├── :data:persistence
   ├── :data:model
   └── :data:android-capabilities
```

- `core/kernel` — pure Kotlin agent runtime, context, memory, routing, tools, policy, approvals, and effects.
- `data/persistence` — Room durability implementation.
- `data/model` — cloud transports, credential isolation, local llama.cpp JNI adapter, and model-data management.
- `data/android-capabilities` — bounded Android capability adapters.
- `app` — Compose UI and application wiring.

Internal roadmap and detailed private engineering reports are intentionally not published in this repository.

## Security model

Kinetic is designed around a strict authority boundary:

- Model text is **data**, never executable authority.
- Tool-looking JSON or textual markers do not become executable calls.
- Memory is untrusted context; it cannot grant approval or alter policy.
- Risk-bearing effects require validation, policy evaluation, and approval where required.
- Interrupted/uncertain side effects are not silently replayed.
- Provider credentials are isolated from conversation storage and model-visible context.
- No AccessibilityService, shell runtime, Termux/PRoot, arbitrary downloaded executable plugin, or broad-storage permission is part of the current architecture.
- GGUF model files are treated as data and verified before native loading.

Please read [SECURITY.md](SECURITY.md) before reporting a vulnerability.

## Local model path

The current experimental local path is intentionally narrow:

- Runtime: pinned `llama.cpp` v0.4.0 source, built into the signed Android app.
- Model: `Qwen3-0.6B-Q4_0.gguf`.
- Expected size: `428970080` bytes.
- Expected SHA-256: `DA2572F16C06133561CE56ACCAA822216F2391EF4D37FBA427801CD6736417D4`.
- Model weights are **not** bundled in this repository or APK.
- Import is explicit and app-private; unsupported/wrong-size/wrong-hash artifacts fail closed.

The pinned llama.cpp source archive used by the build is documented in [`third_party/README.md`](third_party/README.md).

## Build requirements

The current workspace targets:

- JDK 21 for Gradle
- Java 17 bytecode
- Android SDK 36
- NDK `28.2.13676358`
- CMake `3.30.2`
- Ninja

Run the host-side verification gate:

```bash
./gradlew --no-daemon --max-workers=2 --no-parallel \
  -Pkotlin.compiler.execution.strategy=in-process \
  :core:kernel:test \
  :data:model:testDebugUnitTest \
  :data:android-capabilities:testDebugUnitTest \
  :app:testDebugUnitTest \
  verifyDeviceTestSafety \
  lintDebug \
  assembleDebug
```

On Windows PowerShell use `./gradlew.bat`.

Device and instrumentation acceptance are intentionally separate from routine implementation. Do not use production-app instrumentation as a shortcut: `verifyDeviceTestSafety` protects the `dev.kinetic.app` package from destructive test behavior.

## Repository hygiene

Do not commit:

- API keys, OAuth tokens, provider credentials, or Authorization headers
- `local.properties`, keystores, signing passwords, `.env` files, or private certificates
- GGUF/model weights
- Room databases or app-private state
- internal `roadmap.md`, `PROMPT.txt`, or private engineering `docs/`

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Changes that broaden Android permissions, execution authority, native-code loading, provider fallback, or tool behavior require explicit security review.

## License

Kinetic is licensed under the [MIT License](LICENSE). Third-party components retain their own licenses and notices.
