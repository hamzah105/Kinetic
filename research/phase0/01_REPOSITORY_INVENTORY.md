# Repository Inventory — Exact Local Snapshot

## Scope and method

The directory `Eco_reference/` contains exactly seven immediate project trees: `openclaw`, `hermes-agent`, `AndyClaw`, `opendroid`, `mobilerun-main`, `airllm`, and `anyclaw`. No additional repository was found.

For Git trees, metadata was read without changing global Git configuration (`git -c safe.directory=...`). No fetch, pull, checkout, reset, dependency installation, or reference-tree write was performed. “Appears intact” below means: full (not shallow), not sparse, no declared submodules, zero tracked files missing, and no tracked worktree changes at audit start. It does **not** certify that upstream itself is complete or production-ready.

`mobilerun-main` has no `.git` directory. Its extraction timestamps cannot be treated as commit dates, so branch, commit and local commit date are reported as unknown rather than inferred.

## Checkout ledger

| Local repository | Configured origin / embedded project URL | Branch | Commit | Most recent local commit date | Tracked or archive files | Integrity/provenance assessment |
|---|---|---:|---|---|---:|---|
| `openclaw` | configured origin `https://github.com/hamzah105/openclaw`; `package.json` identifies upstream `https://github.com/openclaw/openclaw.git` | `main` | `ebb301c32d9e2e1ec61baa783564eb38e4296895` | `2026-08-01T13:01:40-07:00` | 30,372 | Appears intact; origin is a fork, so commit identity—not the origin label—is authoritative |
| `hermes-agent` | configured origin `https://github.com/hamzah105/hermes-agent` | `main` | `87bc710609f8b89b6e6b4aa418dde8ee30ec6873` | `2026-08-01T11:40:59-07:00` | 8,204 | Appears intact; origin is a fork |
| `AndyClaw` | configured origin `https://github.com/hamzah105/AndyClaw` | `main` | `04a520177a5996777814b59ffeb28c1aeecc489a` | `2026-07-27T18:16:53+02:00` | 448 | Appears intact; origin is a fork |
| `opendroid` | `https://github.com/yashab-cyber/opendroid` | `main` | `9e3ef380eb0084d77054c9a48c42d08654b3ae4b` | `2026-08-01T14:24:19Z` | 310 | Appears intact |
| `mobilerun-main` | no Git metadata; `pyproject.toml` and `server.json` identify `https://github.com/droidrun/mobilerun` | unknown | unknown | unknown | 243 archive files | Source archive appears internally coherent (package, lock, docs, tests, flows), but exact commit provenance is incomplete and must be recovered before copying/adapting code |
| `airllm` | configured origin `https://github.com/hamzah105/airllm` | `main` | `64a4e4fc3749aa7dc9bba4788f560ed0d7e74bd2` | `2026-07-28T20:05:48-05:00` | 88 | Appears intact; origin is a fork |
| `anyclaw` | configured origin `https://github.com/hamzah105/anyclaw`; Go module is `github.com/fastclaw-ai/anyclaw` | `main` | `eb7052708f993552e70043a1bff8b9a243148c4f` | `2026-03-29T21:25:18-07:00` | 69 | Appears intact; origin is a fork |

## Language and build overview

Counts are filesystem extension counts excluding `.git`; they are orientation data, not generated-code-adjusted LOC.

| Repository | Dominant implementation languages | Build/package system | Platform targets evidenced locally |
|---|---|---|---|
| OpenClaw | TypeScript 24,325; Swift 1,080; Kotlin 429; MJS/MTS 732; shell and native fragments | pnpm 11 monorepo (`package.json`, `pnpm-workspace.yaml`, lockfile), TypeScript/Vitest; independent Gradle Android build; Swift/Xcode/SwiftPM app trees | Node-based gateway/CLI/server, web UI, Android phone/Wear OS, Apple platforms, container/server deployments |
| Hermes Agent | Python 3,712; TypeScript/TSX 1,940; MJS/JS 107 | Python 3.11–3.13, setuptools + `uv.lock`; npm workspaces for web/TUI/desktop | Desktop/server/CLI agent, gateways/transports, web/TUI/desktop; Termux constraints exist but this is not an Android-native runtime |
| AndyClaw | Kotlin 329; AIDL 7; C/C++/headers 14 | Gradle 9.1 Kotlin DSL, Android Gradle plugin, CMake 3.22.1; local AAR/JAR artifacts | Android 15+ (`minSdk 35`, `targetSdk 36`), ethOS/system-app integration, extension APK example, local/native inference bridges |
| OpenDroid | Kotlin 197 plus Android XML and a small website | Gradle 8.10.2 Groovy DSL; Compose, Hilt, Room/KAPT | Android 8+ (`minSdk 26`, `targetSdk 35`), single Android application |
| MobileRun | Python 172; Jinja templates 8 | Hatchling, `uv.lock`, Python 3.11–3.13, pytest | External Android/iOS device-control and agent framework; CLI and MCP server/client surfaces |
| AirLLM | Python 34; notebooks 14 | nested setuptools package plus `requirements.txt`; PyTorch/Transformers ecosystem | CUDA/PyTorch and MLX/macOS-oriented model inference research; no Android target |
| AnyClaw | Go 46; YAML 10; JavaScript 3 | Go modules (`go 1.25`), Cobra, `mcp-go`; browser extension manifest v3 | CLI/local daemon, MCP frontend, Chrome extension/browser bridge |

## License and notice inventory

| Repository | Repository license | Third-party/file-level structure found | Initial consequence |
|---|---|---|---|
| OpenClaw | MIT, © 2026 OpenClaw Foundation | Root `THIRD_PARTY_NOTICES.md` records adapted Pi/pi-mono MIT work; package-level MIT licenses under `packages/ai`, `packages/gateway-client`, and `packages/gateway-protocol`; Android ships generated texts under `apps/android/THIRD_PARTY_LICENSES/` | Permissive candidates are reusable only with retained notices and dependency/model/media provenance; provenance is better developed than in other Android references |
| Hermes Agent | MIT, © 2025 Nous Research | Separate licenses for productivity skills; `plugins/security-guidance/NOTICE` states `patterns.py` is a verbatim Apache-2.0 upstream file while surrounding plugin glue remains MIT; other plugin-level licenses exist | Do not apply “root MIT” blindly at file level; copy candidates require sub-tree inspection and notice retention |
| AndyClaw | GPL-3.0 | Only root GPL text was found; no consolidated third-party notice despite local AAR/JAR, C/C++, gplayapi, crypto/wallet/messaging dependencies | Direct source adaptation makes the distributed derivative GPL-compatible; default Kinetic decision is clean-room concept reimplementation or protocol interoperability, plus a separate dependency-provenance audit |
| OpenDroid | Apache-2.0 | Root license only; no consolidated notices | Permissive with Apache conditions and patent terms; dependency and downloadable-model licenses remain separate obligations |
| MobileRun | MIT, © 2025 Niels Schmidt | Root license only in the archive; lockfile records dependency graph | Exact source revision must be recovered before source reuse; dependencies and remotely supplied `mobilerun-*` packages are not covered by the repository’s MIT grant |
| AirLLM | Apache-2.0 | Root and nested `air_llm/LICENSE` copies | Algorithm study is permissive, but model weights, Transformers/PyTorch/bitsandbytes and model-family licenses are independent |
| AnyClaw | MIT, © 2025 FastClaw AI | Root license only | Go source is permissive; registry package APIs, credentials and third-party service terms remain separate |

See `11_LICENSE_PROVENANCE_MATRIX.md` for path-level decisions. Absence of a notice file is not evidence that dependencies or bundled artifacts have no obligations.

## Per-repository source inventory

### OpenClaw

- **Versions/build:** root `package.json` reports OpenClaw `2026.7.2`, ESM, Node `>=22.22.3 <23 || >=24.15.0 <25 || >=25.9.0`, and pnpm 11.15.1. `apps/android/app/build.gradle.kts` uses Java/Kotlin 17, `compileSdk 37`, `targetSdk 36`, `minSdk 31`, Compose, Room, CameraX, Media3, Security Crypto, coroutines, KSP, and `play`/`thirdParty` flavors.
- **Approximate architecture:** `openclaw.mjs` → `src/entry.ts`/`src/cli/run-main.ts` launches a large TypeScript control plane. Agent/model/tool/session/memory/cron/security/plugin logic lives in `src/` and contract packages; native apps connect through the gateway protocol. Android is a substantial native client/node, not the self-contained owner of the primary agent loop.
- **Important entry/config:** `openclaw.mjs`; `src/entry.ts`; `src/cli/run-main.ts`; `apps/android/app/src/main/java/ai/openclaw/app/NodeApp.kt`; `apps/android/app/src/main/AndroidManifest.xml`; `apps/android/app/build.gradle.kts`.
- **Important contracts/schemas:** `packages/gateway-protocol/src/`; `packages/gateway-client/src/`; `packages/agent-core/src/`; `packages/llm-core/src/`; `packages/plugin-sdk/src/`; `packages/plugin-package-contract/src/`; `packages/memory-host-sdk/src/`; `packages/tool-call-repair/src/`; Room JSON under `apps/android/app/schemas/`.
- **Important docs:** root `README.md`, `SECURITY.md`, `VISION.md`; `docs/`; `apps/android/README.md`, `apps/android/VERSIONING.md`, `apps/android/fastlane/SETUP.md`; package READMEs.
- **Important tests/QA:** root `test/` and `qa/`; colocated `*.test.ts`; Android `app/src/test*`, `app/src/androidTest`, Wear tests, `benchmark/`, and `scripts/voice-e2e.sh`. The checkout contains a very large test surface; the deep report selects behaviorally relevant tests rather than UI-test enumeration.
- **Completeness caveat:** large generated/release documentation and multiple platform applications are present; no missing tracked files. Deep audit scope is intentionally restricted to the requested control-plane and Android areas.

### Hermes Agent

- **Versions/build:** `pyproject.toml` reports `hermes-agent 0.19.1`, Python `>=3.11,<3.14`, exact-pinned core dependencies, setuptools 83, and CLI entry points `hermes`, `hermes-agent`, and `hermes-acp`. `package.json` adds npm workspaces for web/TUI/desktop.
- **Approximate architecture:** `run_agent.py` and `agent/conversation_loop.py` coordinate provider transports, tool execution, context compression, trajectories, subagents, guardrails and learning. Plugins declare optional behavior; `skills/`/`optional-skills/` provide prompt/instruction assets and executable helpers; `gateway/`, `cron/`, and state modules provide long-lived integration surfaces.
- **Important entry/config:** `run_agent.py`, `cli.py`, `hermes_cli/`, `gateway/`, `pyproject.toml`, `.env.example`, `cli-config.yaml.example`.
- **Important protocols/schemas:** `agent/transports/base.py`; `agent/transports/hermes_tools_mcp_server.py`; `mcp_serve.py`; `hermes_state_schema.py`; plugin YAML manifests; skill `SKILL.md` files.
- **Important docs:** root `README.md`, `SECURITY.md`, `CONTRIBUTING.md`; `docs/`; plugin/skill READMEs; `hermes-already-has-routines.md`.
- **Important tests:** `tests/` and `tests-js/`, plus plugin tests. High-value families cover conversation turns, providers/transports, tools, context compression, skills/learning, cron/gateway, credentials, MCP and end-to-end behavior.
- **Completeness caveat:** intact checkout, but generated/research datasets and frontend trees make raw file counts a poor measure of core-runtime size.

### AndyClaw

- **Versions/build:** Gradle modules `:app`, `:AndyClaw`, `:ExtensionExample`; application `org.ethereumphone.andyclaw`, version 66, `minSdk 35`, `targetSdk 36`. `app/build.gradle.kts` enables AIDL/CMake and consumes local `tinfoil-bridge.aar` and `llamatik.aar`.
- **Approximate architecture:** reusable Kotlin library module supplies `agent/AgentRunner.kt`, execution engine, skills, sessions, memory and gateway client; app module binds device/system services, built-in commands, local/cloud LLMs, safety and ethOS APIs. ExtensionExample demonstrates cross-package capability discovery/invocation.
- **Important entry/config:** `app/src/main/AndroidManifest.xml`, `app/src/main/java/org/ethereumphone/andyclaw/NodeApp.kt`, `app/src/main/java/org/ethereumphone/andyclaw/MainActivity.kt`, `AndyClaw/src/main/java/org/ethereumphone/andyclaw/agent/AgentRunner.kt`.
- **Important protocols/schemas:** seven AIDL files under `app/src/main/aidl/`; `ExtensionExample/src/main/res/raw/extension_manifest.json`; `AndyClaw/src/main/java/org/ethereumphone/andyclaw/skills/SkillManifest.kt`; `AndyClaw/src/main/java/org/ethereumphone/andyclaw/gateway/GatewayProtocol.kt`; `AndyClaw/src/main/java/org/ethereumphone/andyclaw/protocol/OpenClawProtocolConstants.kt`; committed Room schemas under `AndyClaw/schemas/`.
- **Important docs:** root `README.md`, `SYSTEM_PROMPT.md`, `AGENT_DISPLAY_API.md`, `AGENT_VIRTUAL_DISPLAY.md`, `guides/`.
- **Important tests:** `ParallelExecutionEngineTest.kt`; app tests for routing, tool bridge, wallet, provider adapters and local LLM; extension unit/instrumentation tests; `LocalLlmInferenceTest.kt`.
- **Completeness caveat:** source checkout is intact, but prebuilt AAR/JAR artifacts and system/ethOS privileges prevent a clean ordinary-device reproduction and require provenance/security review.

### OpenDroid

- **Versions/build:** application `com.opendroid.aiagent` 1.0.2, `minSdk 26`, `targetSdk 35`, Java/Kotlin 21. Compose, Hilt, Room, WorkManager, DataStore, Security Crypto, ML Kit GenAI Prompt and LiteRT-LM are declared in `app/build.gradle`.
- **Approximate architecture:** a single native Android app with Hilt-bound repositories/providers, `core/agent/AgentLoop.kt`, action registry/execution, Room conversation/task/memory storage, foreground voice/service surfaces, and Accessibility-driven general UI autonomy.
- **Important entry/config:** `app/src/main/AndroidManifest.xml`; `OpenDroidApp.kt`; `MainActivity.kt`; `core/agent/AgentLoop.kt`; `core/service/OpenDroidService.kt`; `accessibility/OpenDroidAccessibilityService.kt`.
- **Important protocols/schemas:** `core/agent/ActionSchema.kt`; action/result models; committed Room schemas `app/schemas/com.opendroid.ai.data.db.OpenDroidDatabase/{6,7}.json`.
- **Important docs:** root `README.md`, `SECURITY.md`, `RELEASE.md`; `docs/`; `vibecoder.md`.
- **Important tests:** 26 actual Kotlin files under `app/src/test`, notably `ActionSchemaTest`, approval-policy tests, LLM provider/budget/error tests, permission models, crash redaction/reporting, and Room migration tests. A naive `*Test.kt` repository scan yields 27 because production source `core/llm/ConnectionTest.kt` is not a test-tree file.
- **Completeness caveat:** intact source, but manifest breadth and service/accessibility assumptions are not evidence of Play eligibility or process-death correctness.

### MobileRun

- **Versions/build:** `pyproject.toml` reports `mobilerun 0.6.17`, Python 3.11–3.13 and Hatchling; it depends on separately packaged `mobilerun-sdk` and `mobilerun-core-local[cloud]`. `server.json` independently reports MCP descriptor version 1.0.0—this is not the Python package version.
- **Approximate architecture:** external device adapters feed UI trees/screenshots to direct, fast, or manager/executor agents; tool families issue Android/iOS actions; app cards add app-specific grounding; trajectories and flow assets support replay/evaluation; an MCP surface exposes device control.
- **Important entry/config:** `mobilerun/cli/main.py`; `mobilerun/agent/manager/`; `mobilerun/agent/executor/`; `mobilerun/agent/fast_agent/`; `mobilerun/agent/droid/`; `mobilerun/tools/`; `pyproject.toml`.
- **Important protocols/schemas:** `server.json` points to the MCP 2025-12-11 server schema; Pydantic models and Jinja prompts in `mobilerun/`; macro schemas exercised by `tests/test_macro_v2_schema.py`.
- **Important docs:** root `README.md`, `SKILL.md`, `CONTRIBUTING.md`; `docs/`; `agent-test-flows/README.md`.
- **Important tests:** 37 files in the test tree, including fast-agent malformed tool guards/XML parsing, manager response validation, macro guarded replay, provider retry, app cards, coordinate contracts and grounding evaluation.
- **Completeness caveat:** no Git metadata and some core behavior is in external Python packages, so this archive is usable for architecture study but not a fully provenance-pinned standalone implementation.

### AirLLM

- **Versions/build:** root requirements pin older bitsandbytes/Accelerate-era dependencies and Git installs of Transformers/PEFT; nested `air_llm/setup.py` packages `airllm`. This is a research Python distribution, not Android tooling.
- **Approximate architecture:** `air_llm/airllm/airllm_base.py` and model-family subclasses stream/load transformer layers and persist converted/split weights via `air_llm/airllm/persist/` backends. `air_llm/airllm/auto_model.py` selects implementations.
- **Important entry/config:** `air_llm/inference_example.py`; `air_llm/airllm/auto_model.py`; `air_llm/airllm/airllm_base.py`; `air_llm/airllm/persist/model_persister.py`; `requirements.txt`.
- **Important docs/tests:** root/nested READMEs and examples; `test_automodel.py`, `test_compression.py`, `test_streaming_gpu.py`, Kimi split test and model/MLX/compression notebooks.
- **Protocols/schemas:** none relevant to agent interoperability.
- **Completeness caveat:** intact small checkout, but dependency/version and hardware assumptions make executable reproduction environment-specific.

### AnyClaw

- **Versions/build:** Go module `github.com/fastclaw-ai/anyclaw`, Go 1.25, Cobra and `mcp-go 0.45.0`; Chrome extension manifest v3.
- **Approximate architecture:** CLI starts a core runtime/daemon; adapters ingest OpenAPI and commands; backend/frontend layers execute and expose packages through MCP; a registry installs declarative YAML packages; browser bridge carries powerful debugger/tab/cookie permissions.
- **Important entry/config:** `main.go`; `cmd/`; `internal/core/`; `internal/adapter/openapi.go`; `internal/frontend/mcp/`; `internal/registry/`; `SKILL.md`.
- **Important protocols/schemas:** `internal/pkg/manifest.go`; `registry/packages/*/*.yaml`; `registry/packages/translator/openapi.yaml`; `examples/openapi.yaml`; `extension/manifest.json`.
- **Important docs/tests:** root `README.md` and `SKILL.md`; examples. No Go test files are present.
- **Completeness caveat:** intact checkout but very small and effectively untested; browser/daemon behavior expands the trusted computing base.

## Inventory-level conclusions

1. The closest native foundations are OpenDroid (self-contained Android agent loop) and OpenClaw Android (higher-quality native client/capability/lifecycle components but Gateway-dependent).
2. OpenClaw and Hermes are far larger than their README narratives; their value lies in contracts, failure handling, tests and semantics—not wholesale transplantation.
3. AndyClaw is unusually valuable for Android IPC, parallel-execution contracts/negative tests, Room-backed memory/session concepts and local inference evidence, but its GPL-3.0, observed executor defects and privileged ethOS assumptions are hard boundaries.
4. MobileRun is the strongest UI-grounding/trajectory reference, but its missing Git metadata and external package dependencies prevent exact-copy decisions in Phase 0.
5. AnyClaw supplies a compact declarative registry/pipeline experiment, while AirLLM supplies an inference memory-management idea; neither is a Kinetic application foundation.
