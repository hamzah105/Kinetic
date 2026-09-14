# Local Model Options Found

## Decision summary

The strongest **Play-oriented Android starting point in this checkout** is OpenDroid's LiteRT-LM path, because it uses an Android library directly, has an explicit model catalog, WorkManager-backed/resumable download logic, integrity hooks, lifecycle-closing logic, and a provider abstraction. The richest **GGUF/llama.cpp prototype** is AndyClaw's Llamatik adapter, but it is GPL-3.0 project code around an opaque 43 MB `llamatik.aar`, so it is evidence—not a safe default dependency.

AirLLM is not the default local runtime candidate. Its recommendation is **`RESEARCH_ONLY`**.

In the table, OpenDroid `.../` paths expand from `Eco_reference/opendroid/app/src/main/java/com/opendroid/ai/`, while AndyClaw `.../` paths expand from `Eco_reference/AndyClaw/app/src/main/java/org/ethereumphone/andyclaw/`. Other paths are stated from the named repository root.

## Implementations actually present

| Repository / implementation | Actual source evidence | What is real | Important gaps | Kinetic disposition |
|---|---|---|---|---|
| OpenDroid — LiteRT-LM | `Eco_reference/opendroid/app/build.gradle`; `.../core/llm/providers/LiteRTLMProvider.kt` (`LiteRTLMProvider`, `getOrInitializeEngine`, `invokeLiteRTInference`, `closeCachedEngine`); `.../core/llm/OnDeviceModelRegistry.kt`; `.../core/llm/ModelDownloadWorker.kt` | Direct Android integration with `com.google.ai.edge.litertlm.Engine`; cached/closed engine, per-conversation generation, flow streaming adapter, memory compatibility checks, model files in app storage. The registry lists Gemma 4, Gemma 3n and Qwen 2.5 LiteRT artifacts. `ModelDownloadWorker.performDownload` supports Range resume, status persistence, Hugging Face token, SHA-256 validation when supplied, temporary file and post-download load check | `EngineConfig` currently hard-codes `Backend.CPU()` despite comments claiming GPU/NPU; several Gemma sizes are marked TODO and several catalog entries have an empty SHA-256; `LiteRTLMProvider.downloadModel()` itself records a simulated/pending marker rather than owning the real worker; streaming paths partly adapt a non-streaming result; no grammar/schema-constrained tool generation; device/thermal benchmarks absent | **ADAPT** architecture and validate the current LiteRT API/version on target devices. Do not copy placeholder metadata or treat it as production-complete |
| OpenDroid — Android AI Core / ML Kit GenAI | `Eco_reference/opendroid/app/build.gradle`; `.../core/llm/providers/GemmaProvider.kt`; `.../core/llm/providers/HybridOnDeviceProvider.kt` | `GemmaProvider` integrates the ML Kit GenAI Prompt API; `HybridOnDeviceProvider.resolveBackend`, `delegateFor`, and `fallbackFor` switch between AI Core and LiteRT based on selected model/availability | Device/OEM/region/model availability is outside Kinetic's control; fallback does not itself guarantee semantic/tool-call equivalence | **ADAPT** as an optional system-managed engine behind the same interface, never as the sole local path |
| AndyClaw — Llamatik/llama.cpp GGUF | `Eco_reference/AndyClaw/app/src/main/java/org/ethereumphone/andyclaw/llm/LlamaCpp.kt`; `LocalLlmClient.kt`; `GgufRegistry.kt`; `ModelDownloadManager.kt`; prebuilt `Eco_reference/AndyClaw/llamatik/llamatik.aar` | `LlamaCpp.load` configures context, batch, threads, GPU layers and mmap via `LlamaBridge`; generation, streaming and tokenizer calls are exposed. `LocalLlmClient` adapts an OpenAI-like message API. `GgufRegistry` imports user-selected GGUFs through SAF/AIDL file descriptors into app-private storage using temporary-file/rename logic and tracks per-model runtime config. Default Qwen 2.5 1.5B Q2_K download is ~753 MB | The Llamatik implementation is an opaque 43 MB AAR in this tree; its exact llama.cpp revision, ABI configuration, license/notices and patches are not recoverable here. `ModelDownloadManager` has no expected digest/signature, no resume, no WorkManager/process-death continuation and no gated-license flow. Project wrapper source is GPL-3.0 | `LlamaCpp`/registry concepts: **CLEAN_ROOM**. A GGUF/llama.cpp engine remains a strong spike candidate only after selecting a provenance-clear upstream Android binding and measuring it |
| AndyClaw — Whisper native | `Eco_reference/AndyClaw/app/src/main/cpp/CMakeLists.txt`; `whisper_jni.cpp`; vendored `cpp/whisper/*`; `.../whisper/WhisperBridgeNative.kt`; `WhisperTranscriber.kt` | JNI loads an English Q5_1 base model (~60 MB per `WhisperTranscriber`), parses WAV, runs CPU Whisper (`use_gpu = false`), releases native context; Kotlin adds warm-up, mutex ownership, repetition cleanup and vocabulary correction | Vendored source provenance says “from whisper.cpp / FUTO keyboard” in CMake but no third-party notice was found; GPL project boundary applies; English-only model; global native context and serialized access; no streaming transcription/cancellation or device benchmark | Native speech is a valid NDK use, but this exact source is **REFERENCE_ONLY/CLEAN_ROOM** until provenance and licenses are resolved |
| Hermes — Ollama/LM Studio/local CLI ecosystem | `Eco_reference/hermes-agent/hermes_cli/model_setup_flows.py`; provider registry/plugin files; `Eco_reference/hermes-agent/website/docs/guides/local-ollama-setup.md`; `Eco_reference/hermes-agent/website/docs/user-guide/skills/bundled/mlops/mlops-inference-llama-cpp.md` | Connects to local HTTP processes or teaches users to run external inference; provider abstractions and model normalization are reusable semantics | Not an embedded Android inference engine; requires a separate server/process/CLI and often desktop/Linux assumptions | **PORT_SEMANTICS** for provider routing only; reject as Kinetic Core local runtime |
| MobileRun — Ollama/provider selection | `Eco_reference/mobilerun-main/pyproject.toml`; provider/LLM selection files under `mobilerun/` | Can select Ollama/OpenAI-like providers for the external control agent | The “local” endpoint is external to the controlled phone; not Android-native inference | **REFERENCE_ONLY** |
| OpenClaw Android | `Eco_reference/openclaw/apps/android/app/build.gradle.kts` and Android source tree | Native app has media/voice/gateway-client components but no embedded LiteRT, TFLite, MediaPipe, ONNX, llama.cpp or local-model engine in the Android module | Core inference/tool orchestration remains on the Gateway/provider runtime | No local engine to reuse; retain its gateway/provider protocol ideas |
| AirLLM | `Eco_reference/airllm/air_llm/airllm/airllm_base.py`; `persist/model_persister.py`; `persist/safetensor_model_persister.py`; root `requirements.txt` | Builds the full model structure on PyTorch `meta`, stores per-layer safetensor shards, loads a layer disk→CPU→GPU immediately before execution, releases it after execution, optionally prefetches, supports 4/8-bit handling, and has special per-expert MoE streaming. It avoids resident full-weight memory, not total model storage | PyTorch/Transformers/bitsandbytes/CUDA or MLX stack, Python runtime, pinned host memory and GPU transfer assumptions; repeated disk bandwidth/latency per forward/token; large temporary/conversion storage; mobile delegate formats do not expose equivalent arbitrary layer replacement; thermal, flash wear and energy cost are unmeasured | **RESEARCH_ONLY** |

No embedded ONNX Runtime implementation was found. No direct TensorFlow Lite/MediaPipe LLM implementation beyond the newer LiteRT-LM path was found. Qwen and Gemma local artifacts are represented through LiteRT-LM and/or GGUF; Whisper native exists only in AndyClaw.

## Why AirLLM is `RESEARCH_ONLY`

`AirLLMBaseModel` documents and implements the key mechanism precisely:

- `__init__` defaults to `device="cuda:0"`, sets compression/prefetching, splits or reuses layer shards, and instantiates the model on PyTorch `meta`.
- `_install_streaming_hooks`, `_pre_hook`, `_post_hook`, `load_layer_to_cpu`, and `move_layer_to_device` load each module for a forward and call `clean_memory()` afterward.
- `_setup_expert_streaming` improves sparse MoE behavior by loading only routed experts.
- The implementation guards pinned memory at 2 GiB per prefetched layer and explicitly notes that frontier MoE layers can be ~17 GiB, so two prefetched layers could lock ~34 GiB host RAM.
- Compression uses bitsandbytes/Transformers quantizer paths and sometimes expands packed payloads before computation; comments acknowledge roughly 4× expansion for some formats.
- `SafetensorModelPersister` writes a shard plus `.done` marker and loads the whole layer state on CPU.

This is a meaningful server/desktop memory-vs-I/O trade, but it conflicts with Android goals: a mobile token loop cannot assume CUDA, Python, PyTorch module mutation, abundant pinned RAM or sustained flash-to-accelerator traffic. Porting it would be a new runtime project, not an Android wrapper. Only the **idea** of explicit storage/RAM/accelerator tiers and independently verifiable model segments is worth retaining for experiments.

Before revisiting, a spike would have to show better end-to-end tokens/s, joules/token, peak RSS and thermal stability than a supported quantized LiteRT or GGUF engine on at least low-, mid- and high-tier devices. Until then, do not call it `ADAPT_ALGORITHM`.

## Strengths and defects of the two Android candidates

### OpenDroid LiteRT-LM

Strongest elements to adapt:

- `OnDeviceModelSpec` separates stable ID, family, format/backend, path/filename, version, SHA-256, expected size, license URL, auth, SDK floor and context window.
- `ModelDownloadWorker` makes model transfer persistent and stoppable, reports progress/ETA, handles HTTP/auth errors, resumes with Range, verifies a supplied digest, and validates loadability before marking success.
- `LiteRTLMProvider` closes conversations and cached engines, detects corrupt/simulated files and converts native failures into provider errors.
- `HybridOnDeviceProvider` demonstrates system-engine/direct-engine fallback.

Defects Kinetic must not inherit:

- A descriptor with an empty digest is not production-verifiable; all remotely downloaded artifacts require immutable versioned URLs and mandatory hashes/signatures.
- Model size TODOs must never drive allocation or download UI.
- A catalog claim of GPU/NPU delegates cannot coexist with a hard-coded CPU backend without explicit capability reporting.
- “Streaming” must represent actual incremental decoder output, not emitting a completed string in chunks.
- Model selection needs license acceptance/provenance, tokenizer/chat-template compatibility and engine-version compatibility, not just file presence.

### AndyClaw Llamatik/GGUF

Strongest concepts to clean-room reproduce:

- A model registry independent of the engine; SAF import copies to internal storage for native mmap and uses a temporary name before activation.
- Split hardware-level session settings (context/batch/threads/GPU layers/mmap) from per-generation sampling settings, so a temperature change does not reload a 750 MB model.
- Explicit load/unload, tokenizer counting, streaming callbacks and provider-format adaptation.

Defects Kinetic must not inherit:

- No binary should enter Kinetic without source/revision/ABI/license/SBOM provenance.
- Downloaded model identity must not be “file exists and length > 0.”
- Long downloads must survive process death and enforce quotas/free-space checks.
- Imported models require a parser/bounds-validation threat model; native parsers process attacker-controlled files with app permissions.

## Interface-level abstraction proposal (no implementation)

These are responsibility boundaries, not finalized Kotlin declarations.

| Interface/data type | Required semantic contract | Must not own |
|---|---|---|
| `LocalModelEngine` | Advertise engine ID/version, supported model formats/modalities/accelerators/structured modes; validate `ModelDescriptor` + device compatibility; estimate memory/storage; open an `InferenceSession`; expose deterministic capability errors | Downloads, UI state, task persistence, policy decisions |
| `ModelDescriptor` | Immutable ID/version; source/provenance/license acceptance; file set, sizes, hashes/signature; format/quantization/tokenizer/chat template; modalities/context; compatible engine versions/ABIs; SDK/device/RAM/storage/accelerator constraints; benchmark provenance | Mutable download progress or secrets |
| `InferenceSession` | Own exactly one loaded model/runtime context; warm-up; token count; one-shot and true-stream generation; cooperative cancellation/deadline; usage/timing/thermal signals; close/release idempotently; declare concurrency rules | Conversation history, long-term memory, approval policy |
| `StructuredGeneration` | Given a schema/grammar/tool set, report whether constraints are native, validated-repair, or unsupported; return typed success or a parse/constraint failure with raw output quarantined for audit; never silently reinterpret invalid tool arguments | Executing tools or approving effects |
| `EmbeddingEngine` | Descriptor-compatible embedding model/session, dimension/normalization/metric metadata, bounded batch and cancellation; version results so indexes can be rebuilt | Vector database lifecycle or memory ranking policy |
| `ModelStorage` | App-private catalog; resumable transfer/import; free-space/quota enforcement; mandatory integrity/signature and license checks; atomic activation/rollback; reference counting/session locks; deletion and orphan cleanup; provenance/audit events | Inference logic or network access outside an explicit transfer policy |

The provider/router above these interfaces should be format-neutral. A local engine must return the same typed tool-call candidate/result stream as a cloud provider, while the policy engine remains the sole path to effectful tool execution.

## NDK/JNI boundary

Legitimate native uses represented here:

- quantized model kernels, accelerator delegates and memory mapping;
- llama.cpp/GGUF inference if chosen after provenance and benchmark review;
- Whisper/audio DSP where a supported Java/Kotlin API is insufficient;
- performance-critical tokenizer/image/audio primitives after measurement.

Do **not** put model routing, task state, downloads, catalogs, permissions, prompts, tool schemas, approvals, sessions, Room persistence or retry orchestration in JNI. Kotlin/coroutines provide safer cancellation and lifecycle integration; native code does not make orchestration intrinsically faster.

## Required architecture spikes before choosing an engine

1. LiteRT-LM Qwen/Gemma cold load, warm tokens/s, peak RSS, energy/token and thermal throttling across representative devices.
2. Provenance-clear llama.cpp Android binding with the same matrix and GGUF parser fuzzing.
3. Actual structured JSON/schema/tool-call conformance under both engines; measure repair rate, not just chat quality.
4. Process death during model download, checksum, activation, model load and active generation.
5. Storage pressure, insufficient RAM, corrupted/truncated model, gated-license rejection and engine/model version mismatch.
6. AI Core availability/quality matrix and deterministic fallback behavior.
7. Native Whisper versus platform SpeechRecognizer for latency, battery, privacy, multilingual coverage and APK/model size.
8. Model license/provenance pipeline and Play treatment of downloaded data assets.

## Recommendation

Prototype the interface against **OpenDroid-style LiteRT-LM first**, retain an **independently sourced llama.cpp/GGUF second engine** as a measured alternative, and treat AI Core as an opportunistic system engine. Reuse AndyClaw's architectural ideas only through clean-room design. Keep AirLLM in the research backlog unless mobile measurements demonstrate a niche where layer streaming wins.
