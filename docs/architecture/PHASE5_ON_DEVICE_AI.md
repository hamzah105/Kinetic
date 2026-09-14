# Phase 5A — on-device AI foundation

## Authority and checkpoint (2026-09-09)

Read `roadmap.md` before task work. Its starting current state was Phase 4C
implemented / owner governance acceptance pending, Phase 4 in progress, the
unnumbered Astra/Stellar engineering device gate passed / owner acceptance pending,
and Phase 5 not started. README still contained older verification status.

The owner's new Phase 5A brief explicitly confirms the repaired governance flow:
SQLite ACTIVE/current after explicit update, PostgreSQL SUPERSEDED/history,
new-conversation recall and Context Inspector select SQLite, and recall survives
force-stop/relaunch. This closes Phase 4C as ACCEPTED and Phase 4 as COMPLETE.
It does not close the separate Astra/Stellar owner UX/provider gate.
Phase 5A alone is authorized. This reconciliation is recorded before source work;
the authoritative roadmap will be updated as the final workspace modification.

## Implementation checkpoint

Architecture reconstruction completed: the existing five modules, provider port,
configured/pinned adapter selection, streaming/continuation, context planner,
memory governance, registry/policy/approval/durable ledger, Android coordinator,
Room v6, Compose Provider/Debug surfaces and root device safety guard were inspected.
No parallel runtime, real model download, native dependency, automatic router,
new permission or database migration was added. **Implemented; 244 automated tests
pass; engineering device gate BLOCKED by a new Chrome ANR and relaunch timeout.**
See [final verification evidence](../PHASE5A_ENGINEERING_VERIFICATION.md).

## Current primary-source research

Access date for every URL below: **2026-09-09**. These are live documentation
observations, not a promise that every API/model combination works on a phone.
No runtime SDK or model weights were downloaded for this gate.

### Android-managed Gemini Nano

The [ML Kit overview](https://developers.google.com/ml-kit/genai) describes
shared AICore-managed, on-device inference and different device lists for Prompt
and feature-specific APIs. Prompt support includes device-dependent Nano v2/v3/v4;
do not infer support from Android API level or RAM alone. Only top-foreground
inference is permitted. Per-app short-term BUSY and longer-duration battery quotas
exist; exact numeric quotas are not specified. Kinetic must return typed failures,
not silently retry or route to cloud. Additional GenAI terms apply.

The [Prompt setup guide](https://developers.google.com/ml-kit/genai/prompt/android/get-started)
documents status checks, explicit download, warmup, streaming and non-streaming,
token counting and output bounds. Input must be below 4,000 tokens; output over
4K is discouraged. UNAVAILABLE can mean missing initialization/configuration,
not permanent hardware rejection. Unlocked bootloaders are unsupported. Model
provisioning can need network even though prepared inference is offline.
The [Prompt overview](https://developers.google.com/ml-kit/genai/prompt/android)
is the entry point for custom prompts. We did not verify a portable native
function-call or constrained-JSON guarantee for Kinetic's schemas, nor precise
cancellation teardown behavior in the public reference (reference fetch failed).
Those remain explicit physical-adapter acceptance questions, not assumed support.
No universal minimum-RAM value is adopted.

### Gemma 4

The [Gemma overview](https://ai.google.dev/gemma/docs/core) identifies mobile E2B
and E4B variants and LiteRT-LM mobile deployment. Its approximate mobile weight
loading estimates are 1.1/2.5 GB, or 0.84/2.2 GB for text-only variants. They are
not whole-app RAM requirements: context/KV caches, software and workload add cost.
Do not equate effective parameter count, download bytes, and resident memory.

The [Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4)
identifies Apache-2.0 licensing, native function calling, text/image input and
audio on E2B/E4B, and nominal 128K small-model context. Those are model-level
claims, not proof that a particular mobile quantization/backend can fit that
window or reliably propose Kinetic tools. Artifact license/NOTICE, conversion,
tokenizer and runtime versions must be pinned separately before distribution.
No default model is selected.

### LiteRT / LiteRT-LM and custom models

[LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview) and
[official project](https://github.com/google-ai-edge/LiteRT-LM) distinguish the
LLM orchestration layer from generic LiteRT execution. The project reports
cross-platform, multimodal and tool-use support, hardware acceleration and an
Apache-2.0 runtime. Published example performance is not Kinetic evidence.

The [Kotlin guide](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/docs/api/kotlin/getting_started.md)
describes explicit model paths, engine initialization off the UI thread, resource
closure, CPU/GPU/NPU backends, normalized-to-be-adapted Flow streaming and tool
APIs. GPU native-library declarations and NPU packaging are backend-specific;
no such dependencies are added in Phase 5A. Use pinned versions, never the
guide's convenient `latest.release` selector in a reproducible production build.

The [official Conversation implementation](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt)
exposes cancellation and optional response formatting. Crucially, automatic
tool calling defaults on. A future Kinetic adapter must explicitly disable it,
expose schemas only, normalize proposals and return control to AgentRuntime.
Never hand native/runtime tool callbacks Android execution objects. Cancellation
must dispose uncertain conversation state; do not infer safe replay/reuse from
Flow completion. Exact released-version teardown requires testing.

[LiteRT NPU documentation](https://developers.google.com/edge/litert/next/npu)
lists vendor-specific compiled-model support, including Google Tensor,
Qualcomm, MediaTek, Intel and Samsung. Its Android NPU packaging example requires
API 31+ and arm64-v8a; model/operator/device/delegate compatibility remains
specific. Tensor's documented path is AOT rather than JIT. Native runtime code
belongs in signed application/feature delivery, not in model packs. Generic
custom tensor-model execution is not itself a chat/function-calling adapter.

### Native fallback

[llama.cpp](https://github.com/ggml-org/llama.cpp) is an MIT-licensed native
inference project. Its [Android guide](https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/android.md)
includes an Android binding with GGUF metadata, app-private model loading and
Kotlin token Flow, and CPU hardware-specific kernels. Broad build reach does not
mean acceptable performance on every Android device. Model/license/template,
quantization, context and build/delegate versions still determine feasibility.
Kinetic would use an embedded adapter, never the guide's command-line/shell path.

[Llamatik's official project](https://github.com/ferranpons/Llamatik) is an
MIT-licensed Kotlin Multiplatform wrapper around native engines. It documents
stream callbacks, session cancellation, configurable context and optional GPU
builds; default Android native builds are CPU-only. Its expanded speech/image and
optional remote features are outside scope. Wrapper convenience adds a lifecycle,
binary provenance and upgrade surface; constrained tool-schema reliability must
be measured rather than inferred from text generation. No wrapper is installed.

### Play model delivery

[Play for On-device AI (beta)](https://developer.android.com/google/play/on-device-ai)
documents install-time, fast-follow and on-demand model packs, device/RAM
targeting, and app-coupled model updates. AI packs contain model data, not
Java/Kotlin or native libraries. Current documented compressed per-pack limit is
1.5 GB and cumulative generated-app limit 4 GB; AGP 8.8+ is required. Large
artifacts need an explicit delivery-size check. App-only model use and Play
distribution/SDK terms apply. These findings guide future packaging; no pack,
download client, executable delivery or Gradle plugin is added now.

## Candidate scorecard — engineering judgment, not measured ranking

Ranks below mean **RECOMMENDED FOR PHASE 5B TESTING**, conditional on an available
supported physical device. No production/default model or accuracy ranking exists.
Gemma is a model family; LiteRT-LM is a runtime, so their combination is a candidate.

| Dimension | 1: Gemma E2B text-first / LiteRT-LM | 2: Nano / AICore Prompt | 3: small GGUF / llama.cpp (Llamatik optional) |
|---|---|---|---|
| Device reach | Potentially broad; backend/ABI/RAM dependent | Official supported-device subset | Broad CPU builds; practical speed unknown |
| Privacy / offline | Local after model provisioning | Local after managed provisioning | Local after model provisioning |
| Latency potential | Acceleration available; unmeasured | Managed hardware path; unmeasured | CPU/GPU build dependent; unmeasured |
| RAM footprint | Smaller mobile variant first; app peak unknown | Shared model; app/OS pressure unknown | Quantization/context dependent |
| Model quality | Unmeasured Kinetic corpus | Unmeasured Kinetic corpus | Depends on chosen artifact; unmeasured |
| Tool reliability | Native proposal support; validate independently | Structured guarantee not established here | Template/schema integration required |
| Runtime maturity | Official active LLM runtime; version pin required | Managed service, Prompt beta | Established native project; wrapper adds risk |
| Integration complexity | Medium/high lifecycle and binary work | Lower binary ownership, higher compatibility limits | High JNI/build ownership; wrapper may reduce code |
| License | Apache runtime / Gemma 4; inspect artifact notices | API/service terms and device-managed model | MIT engine/wrapper; model license separate |
| Play compatibility | Data packs plus signed runtime distribution | Ordinary SDK/service integration | Signed native runtime plus model data |
| Delivery complexity | Own large model provisioning | OS-managed model status/download | Own GGUF integrity/version/deletion |
| Vendor lock-in | Format/delegate dependence; port remains neutral | Highest service/device dependence | Lower vendor dependence; native API churn |
| Future modalities | Possible with appropriate artifact | API/device-dependent | Model/backend/wrapper-dependent |

Start any later comparison with the same corpus and bounded context. Do not pick
E4B, a large context, or an NPU merely because advertised throughput is higher.
The alternative candidates remain viable until physical results resolve tradeoffs.

## Implemented boundary

```text
ContextPlanner (provider-bounded, untrusted memory/summary)
  -> existing AgentRuntime -> ConfiguredModelProvider (manual, turn-pinned)
  -> LocalModelProvider -> normalized ModelStreamEvent / ToolCall
  -> registry + typed input contract -> policy -> exact approval
  -> durable effect ledger -> existing foreground Android coordinator
```

`LocalModelDescriptor` lives in the pure Kotlin kernel: family, identifier/version,
optional bytes/quantization/context/RAM, modalities, structured output/proposals,
streaming, ABI/acceleration requirements and explicit availability. Null means an
unknown numeric fact, never zero. Availability includes AVAILABLE, UNKNOWN,
MODEL_NOT_INSTALLED, DEVICE_UNSUPPORTED, RUNTIME_UNAVAILABLE, INSUFFICIENT_MEMORY,
INCOMPATIBLE_ABI, INITIALIZING and ERROR. Status can be rechecked at inference time.

`LocalModelProvider` extends the existing port. It has no Android Context,
persistence, policy, approval, HTTP or execution object. This is architectural
dependency isolation, not a new process sandbox: future native code still requires
trusted supply-chain review. Typed local-unavailable/context-limit failures flow
to stable kernel errors. Cancellation remains coroutine cancellation.

The existing provider metadata already had optional `maxContextTokens`; Phase 5A
now feeds it into ContextPlanner. Smaller budgets trim history/memory, preserve
the current request and fail typed if it cannot fit system/output reserves.
The configured test window is 4096 estimated tokens. Estimation is not a vendor
tokenizer; a real adapter must count its complete serialized system/tool/input
envelope and continuation, reserve output, and reject overflow before inference.
No local provider gets Room or ControlledMemoryService mutation authority.

The simulated backend is explicitly SIMULATED / TEST, with deterministic text
chunks, structured echo/protected-demo/URL proposals, truthful result continuation,
and opt-in unavailable/truncated-output fixtures. It is not Fake relabeled as a
real LLM. It has no network dependency. Configured selection returns it before
credential access and pins it through continuation even if settings later change.
Existing explicit summary compaction uses the deterministic summary generator in
this development mode; no cloud summary request is introduced. No auto-routing.

`AndroidLocalCapabilityProbe` in `:data:model` reads API, ABIs, ordinary memory
information, memory class, app-storage availability and an emulator heuristic.
It collects no unique identifiers, scans no packages, does not download or route,
and sends nothing remotely. Native adapters are absent; actual runtime/model
support remains unprobed. Pure `localCompatibility` reports only known ABI/RAM
constraints; lack of a measurement cannot prove unsupported hardware.

Provider UI adds one clearly labeled local test selector and bounded explanation.
Debug shows device facts and NOT RUN benchmark status. No Stellar redesign.

## Benchmark foundation and fixture protocol

`LocalBenchmarkResult` is ephemeral pure Kotlin data, not a Room table. It records
runtime/model/version/size/quantization, coarse device class, ABI/API and whether
the environment is physical or emulated. Typed measurements distinguish observed
values from NOT_RUN, NOT_SUPPORTED, NOT_MEASURABLE and SIMULATED_ONLY.
Numeric fields cover cold/warm load, first token, tokens/sec, prompt/output count,
peak/steady RAM, battery used, cancellation latency and separate structured,
tool and planning scores. Thermal, throttling, offline success and recovery are
typed observations. Validation rejects missing fields, negative/non-finite values,
fractional counts and out-of-range fractions. Simulated/emulator records reject
claimed real timing, token-throughput, memory, battery and thermal measurements.
Qualitative synthetic correctness observations remain explicitly environment-tagged.

`LocalEvaluationCorpus` contains ten non-secret model-neutral fixtures: greeting,
uppercase transformation, exact JSON, echo proposal, unavailable file-tool refusal,
approval-sensitive URL, ambiguous target, untrusted memory approval claim,
cancellation and malformed-output recovery. These prompts are for future real
adapters; the scripted provider is not claimed to understand or pass that corpus.

`evaluateLocalToolProposal` never executes. It checks registry/exposure membership,
typed arguments, schema/index/identity shape and expected policy path separately.
Continuation and resistance to bypass language are harness observations, unavailable
until exercised. Future raw JSON decoding must additionally measure exact-field
schema validity before normalization. A proposal score is not an approval and must
never feed a bypass. Quality scores cannot stand in for safety gate failures.

Future physical protocol: pin artifact/runtime/tokenizer/seed/context and device
class, run separate cold/warm trials, record distributions rather than one speed,
measure process plus runtime memory where supported, note shared-service attribution
limits, baseline battery/thermal under controlled conditions, test offline after
explicit provisioning, cancel during load/prefill/decode and verify disposal and
explicit-retry recovery. Missing trustworthy telemetry stays unavailable. Do not
run production-target instrumentation to gather it. No performance runner or real
backend is shipped in this foundation.

## Model storage and native boundary — design only

Model bytes are DATA. Future storage ownership is Kinetic app-private, with an
app-owned model record containing runtime format, exact version, expected byte
length, SHA-256, trusted provenance and license/NOTICE references. Model paths are
chosen by the storage adapter, never model output. Before owner-authorized download,
preflight space for staging plus final bytes and a documented reserve. Stream into
an incomplete staging object, enforce size/hash, then atomically publish; recovery
must ignore incomplete artifacts until an explicit retry or cleanup. Validate
canonical containment and symlinks. Keep an existing valid version until its
replacement is verified. User deletion removes only the selected model/cache after
closing its handles, never credentials or Room. AICore-managed deletion is governed
by its official API, not by deleting another app's files. No downloader exists yet.

No downloaded DEX, JAR execution, .so execution, arbitrary plugins, shell, package
management, Accessibility or model-authored filesystem operations. Future JNI is
limited to reviewed inference buffers, tokenizer/configuration and lifecycle handles;
it receives neither approval nor policy authority. Engine initialization/cancellation
must be off the main thread and bounded. Native libraries ship through normal signed
application/feature packaging with ABI, page-size, license and integrity checks.
No NDK/JNI/CMake/runtime dependency is added in Phase 5A.

## Scope boundaries

Room remains v6. Permissions, application ID and signer remain unchanged. Existing
cloud configuration and both provider credential slots are retained. The x86_64
Kinetic_API_36 AVD verifies architecture, failure states, UI and lifecycle only:
no ARM64 tokens/sec, battery, thermal, NPU or production model claim is valid here.
Phase 5B requires separate authorization and a supported physical device; Phase 6
alone owns any future automatic privacy/network/battery/cost/complexity routing.
Neither phase has begun.
