# Phase 5B Candidate 1 — pre-integration audit

## Current Candidate 2 follow-up — 2026-09-13

**Candidate 2: D. DEVICE UNSUPPORTED / UNAVAILABLE.** Galaxy A06/SM-A065F is
not on the current official Prompt API support list; ordinary package inspection
found AICore absent. Stage4 stop applied: checkStatus NOT_RUN, no SDK/probe/source
change, model download, inference or APK update. See the
[Candidate 2 preflight and current next task](PHASE5B_CANDIDATE2_AICORE_PREFLIGHT.md).
Candidate1 remains BLOCKED, not rejected. The Candidate1 findings and former next
tasks below remain historical; Phase5B is IN PROGRESS and Phase6 NOT STARTED.

## Candidate 1 history (preserved)

September 13 provenance follow-up: **D. INCONCLUSIVE — BINARY-SPECIFIC PROVENANCE
CANNOT BE ESTABLISHED; Candidate 1 remains BLOCKED.** See the
[binary-specific audit and current next task](PHASE5B_LITERTLM_0170_LICENSE_PROVENANCE.md).
The September 12 preflight evidence and then-recommended task below are historical;
no implementation or benchmark has resumed.

Date: 2026-09-12. **IN PROGRESS; CANDIDATE 1 EVALUATED AT PREFLIGHT;
BLOCKED — RUNTIME THIRD-PARTY LICENSE PROVENANCE UNRESOLVED.**

This is not an implementation or inference pass. The owner authorized Gemma E2B
text-first / LiteRT-LM, not a production choice, other candidate or Phase6.
Phase5A remains COMPLETE/ACCEPTED; separate Astra/Stellar owner acceptance pending.

## Stop condition and exact evidence

Stage0/3 supply-chain review found an unresolved discrepancy between the runtime's
Apache-2.0 declaration and its bundled third-party notice. Under the owner's
explicit license-unclear stop condition, integration and model provisioning stopped.

Inspected official Google Maven artifact, without executing its code:

- Coordinate: `com.google.ai.edge.litertlm:litertlm-android:0.17.0`.
- [Canonical AAR](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.17.0/litertlm-android-0.17.0.aar).
- AAR length: **20,492,644 bytes**.
- Computed SHA-256: `28AA6BC43EFCEE35B31795F9E5CA633C3DEC06D9F3FB85ECB6A753FA360E2134`.
- Root LICENSE:11,357bytes; THIRD_PARTY_NOTICE.txt:2,146,747bytes.
- Notice SHA-256: `67D807A83A6E4F9457365CA61FF3FA1DB95B135A17FEEEFADEFF8E9F02123243`.
- Notice line6884 begins **Google Runtime Environment**, followed by GPLv2 text,
  ending before the Gson section. That18,042-character section contains no
  Classpath/linking/runtime-library exception mention. Other sections include
  Eigen/MPL and LLVM build-tool licensing; exceptions elsewhere do not establish
  their applicability to this specific component.

This **does not prove GPL-covered code is linked into the Android binary**, nor
that LiteRT-LM is incompatible with Kinetic. The notice may include build-time
components. No authoritative mapping or applicable exception was established for
that entry from the inspected package, pinned public build rule or bounded official
search. A shipped-component/SBOM or upstream clarification is needed to close the
license/provenance review. No legal incompatibility ruling is made.

The audited AAR is cached outside source at
`E:\Projects\.tooling\temp\litertlm-android-0.17.0.aar`; it is not a project dependency,
not installed, and no native entry point was loaded. No model weights downloaded.

## Current official runtime research

Google Maven metadata reports release0.17.0; GitHub identifies tagv0.17.0 at commit
`e9fd8c53ff968071774206163027dd84bedfe925`. These are research pins, not floating
implementation dependencies. Sources: [release](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.17.0),
[Maven metadata](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml),
[POM](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.17.0/litertlm-android-0.17.0.pom).

| Check | Observed / remaining boundary |
|---|---|
| Android minimum | Actual AAR manifest minSdk24; Kinetic min26 and phone API36 exceed it. |
| Packaged ABIs | arm64-v8a and x86_64; no armeabi-v7a JNI library. Nonlocal 32-bit app compatibility would require guarded availability, not blind native loading. |
| Native payload | liblitertlm_jni.so:21,802,960bytes ARM64;25,968,008bytes x86_64. Each ELF PT_LOAD alignment16,384bytes. This is ELF alignment evidence, not final signed-APK ZIP alignment or a16KB-device test. |
| POM dependencies | Gson2.14.0, kotlin-reflect2.4.0, coroutines-android1.11.0. Kotlin2.3.21 build compatibility NOT TESTED; dependency version alone does not prove a mandatory compiler migration. |
| License | Root/POM Apache-2.0; bundled notice provenance unresolved as above. No complete transitive-license approval. |
| CPU | Documented Backend.CPU; intended first baseline, not initialized. |
| GPU | Optional libvndksupport.so/libOpenCL.so declarations documented; no GPU integration or device capability claim. |
| NPU | Vendor-specific libraries/device compatibility; deliberately not selected or packaged. |

Pinned [Kotlin guide](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.17.0/docs/api/kotlin/getting_started.md)
documents model paths, background initialization, AutoCloseable engine/conversation,
callback/Flow streaming, and manual tools using automaticToolCalling=false with
native message.toolCalls. No callback may execute Kinetic capabilities. This is
upstream API evidence, not an implemented or validated Kinetic adapter.

Pinned [Engine source](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.17.0/kotlin/java/com/google/ai/edge/litertlm/Engine.kt)
passes context/output bounds and backend configuration to JNI and synchronizes
creation/deletion. [Conversation source](https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.17.0/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt)
returns native tool calls without invocation when automatic calling is false.
Cancellation teardown, precise token-count API, library linkage/complete NOTICE
mapping and full tool-schema normalization review remain incomplete at the stop.
Do not label them verified, unsupported, or safe by inference.

## Model research pin — NOT downloaded or integrity-verified

Phase5A's E2B candidate refers to **Gemma4**, not Gemma3n or E4B. The vendor-linked
repository is [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm).
The official pinned Kotlin guide links the LiteRT community artifact repository.

- Repository revision: `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1`.
- CPU-capable prospective artifact: `gemma-4-E2B-it.litertlm`.
- Published exact size: **2,588,147,712bytes**.
- Published LFS SHA-256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
- [Immutable artifact URL](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm).
- [Metadata source](https://huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm?blobs=true).

The [pinned model card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/README.md)
declares Apache-2.0, mixed2/4/8bit mobile quantization, CPU/XNNPACK, and32K artifact
context. It describes on-demand vision/audio loading; the CPU-capable file must
not be called physically text-only. Smaller2,008,432,640byte GPU/web files are
distinct and were not substituted or downloaded. Exact tokenizer/bundle inspection
is NOT RUN. Kinetic would restrict inputs to text, context to4096 and output to256
or less; advertised128K family context from Phase5A research is not this artifact's
operating limit. Published memory estimates are not whole-app requirements or
measurements on this Samsung phone. No universal RAM exclusion was established.

## Source/architecture audit and design boundary — NOT IMPLEMENTED

Existing five modules remain unchanged. LocalModelProvider/LocalModelDescriptor and
LocalBenchmarkResult are pure kernel contracts. ConfiguredModelProvider manually
selects simulation before credential access and pins stream/continuation; existing
LocalProviderSelectionTest covers no-HTTP/no-credential selection and no fallback.
Room remainsv6. No real-local backend or automatic router exists.

If the blocker is cleared, a separate `:data:local-inference` Android adapter is the
preferred boundary: depend on core only; app assembles it. No persistence, capability,
policy, approval, ledger or credential objects enter it. This is a design proposal,
not a created module. Existing source and phase-history/safety documents were
inspected before any implementation; no implementation began.

Planned artifact store: app-owned path, bounded staging, exact bytes/SHA, canonical
containment and symlink refusal, atomic publish, incomplete staging ignored, previous
valid artifact retained, deletion only after closing model handles. No Room metadata
table or migration. Planned single engineering provisioning path: host-to-app-private
MODEL DATA only, with host and app validation; not yet created or invoked.

Engine must be off-main, serialized, cancellable with uncertain conversation disposed,
and explicitly reinitialized after process death. Native Flow deltas must be genuine;
token metrics unavailable until trustworthy counting is established. No code from
external samples copied. No downloaded executable loading or runtime plugin path.

Ordinary tool-looking text stays inert. Safe provider-native proposals alone may be
normalized before registry/policy/approval/ledger. No new regression added while
license stop applies; the separate OpenRouter issue remains unfixed. No automatic
tool execution was enabled because no runtime was integrated.

## Verification disposition

See [physical preflight / benchmark-not-run report](../PHASE5B_CANDIDATE1_PHYSICAL_BENCHMARK_20260912.md).
No fresh tests, build, install, APK/signature verification or real inference.
Prior244-test accepted baseline is retained, not a Phase5B gate pass.

Exactly one next task, not begun: resolve the pinned0.17.0 Android AAR third-party
license provenance, including whether the GRE entry is build-only or shipped and
what terms apply, before resuming this same Candidate1 gate. No candidate switch,
toolchain migration, production default or Phase6 is authorized by this report.
