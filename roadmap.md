# PROJECT KINETIC — ROADMAP

**Project root:** `E:\Projects\Kinetic`  
**Reference repositories:** `E:\Projects\Eco_reference`  
**Authoritative continuity file:** `roadmap.md`  
**Last updated:** 2026-09-15

> **Codex continuity rule:** Every implementation prompt must instruct Codex to **READ `roadmap.md` FIRST** and **UPDATE `roadmap.md` LAST**.  
> Later phases require explicit owner authorization. Under the owner's build-first strategy, deferred device acceptance is not a recurring implementation prerequisite. Never equate host verification with device or owner acceptance.

---

# 0. CURRENT PROJECT STATE

## Current status

| Phase | Status |
|---|---|
| Phase 0 — Ecosystem archaeology / baseline research | **COMPLETE** |
| Phase 1 — Native Agent Kernel | **COMPLETE** |
| Phase 1.1 — Durable Persistence & Process-Death Recovery | **COMPLETE + OWNER ACCEPTED** |
| Phase 2 — Real Model Integration | **COMPLETE** |
| Phase 2A — Cloud LLM + Streaming Conversation | **ACCEPTED** |
| Phase 2B — Structured Agent Tool Calling | **ACCEPTED** |
| Phase 3 — Native Android Capability System | **COMPLETE** |
| Phase 3A — Native Android Capability Foundation | **ACCEPTED** |
| Phase 3B — User-Mediated Android Handoffs | **ACCEPTED** |
| Phase 3C — Android Capability Lifecycle & Execution Coordinator | **ACCEPTED** |
| Phase 4 — Memory & Context Engine | **COMPLETE** |
| Phase 4A — Controlled Durable Memory Foundation | **ACCEPTED** |
| Phase 4B — Query-Aware Retrieval / Session Compaction | **ACCEPTED** |
| Phase 4C — Memory Governance | **ACCEPTED** |
| Unnumbered Astra / Stellar integration and refinement gate | **ENGINEERING DEVICE GATE PASSED; OWNER ACCEPTANCE PENDING** |
| Phase 5 — On-device model benchmark / SLM runtime | **IN PROGRESS** |
| Phase 5A — Local AI architecture & benchmark foundation | **COMPLETE; AUTOMATED GATE PASSED; PHYSICAL ARM64 ENGINEERING DEVICE GATE PASSED; OWNER ACCEPTANCE PASSED; ACCEPTED** |
| Phase 5B — Real local backend / physical benchmarking | **IN PROGRESS; CANDIDATE 1 PROVENANCE-BLOCKED; CANDIDATE 2 UNAVAILABLE ON SM-A065F; CANDIDATE 3A PHYSICAL FEASIBILITY PASSED — B. VIABLE BUT PERFORMANCE LIMITED; CANDIDATE3B LOCAL PROVIDER IMPLEMENTATION COMPLETE AT HOST LEVEL — REAL LOCAL TEXT + LOCAL SUMMARIZATION + HARDENED MODEL MANAGEMENT IMPLEMENTED; HOST VERIFIED; DEVICE VALIDATION DEFERRED TO PHASE10A** |
| Phase 6 — Hybrid model router | **HYBRID ROUTER FOUNDATION IMPLEMENTED; HOST VERIFIED; MANUAL MODE REMAINS DEFAULT; DEVICE/OWNER VALIDATION DEFERRED TO PHASE10A** |
| Phase 7A — MCP adapter / host foundation | **NOT STARTED** |
| Phase 10A — Final integration & real-device validation | **PLANNED; DEFERRED UNTIL FUNCTIONAL BUILD PHASES ARE IMPLEMENTED; NOT STARTED** |

## Owner development strategy — authoritative realignment, 2026-09-14

**BUILD FIRST.** Stop repeated emulator/physical acceptance gates during implementation.
No connected Samsung or ADB requirement; do not start/restart an emulator for routine
phase work. Use proportionate host compile/unit/lint checks while building authorized
functional phases. Device-dependent checks are **DEFERRED**, never fabricated as passed.
This directive supersedes older per-phase device/owner-gate prerequisites and dated
next-device-task recommendations below; historical results and acceptance remain intact.
No automatic authorization for later phases or unrelated future features follows from it.

[Build-first strategy and future gate](docs/architecture/BUILD_FIRST_STRATEGY.md)
incorporates the owner's supplied `D:\Hamza\PROMPT.txt`, read completely (ends
mid-sentence). It plans safe prompt-first customization, voice STT/TTS considering
Deepgram BYOK, secure manual secret entry, optional conversationally disableable
tutorials, appropriate Google OAuth/provider/model UX, inspectable scoped
personalization and task-focused context. These future features are NOT implemented
by Candidate3B. Model text remains untrusted and cannot change secrets, permissions,
policy, approvals or execute capabilities. Broader Android control requires the
existing registry/policy/approval/ledger/coordinator and permission boundaries.

After functional build phases, **Phase10A FINAL INTEGRATION & REAL-DEVICE VALIDATION
GATE** will rigorously test a real Android device with owner coordination. No routine
emulator fallback. Acceptance and release readiness remain explicit separate outcomes.

## Current verified architecture

```text
:app
   ├── :core:kernel
   ├── :data:persistence
   ├── :data:model
   └── :data:android-capabilities
```

- `:core:kernel` remains pure Kotlin/JVM.
- `:data:persistence` is the Android/Room durability implementation.
- `:data:model` contains cloud-model transport/configuration, manual local-provider selection and a read-only Android capability probe.
- Phase6 adds pure-Kotlin deterministic opt-in Hybrid routing, pinned before context planning and through continuation; MANUAL remains default. Router metadata/inspector never includes prompts or secrets. No cross-provider fallback or new execution authority. See [Phase6 architecture and evidence](docs/architecture/PHASE6_HYBRID_MODEL_ROUTER.md).
- Candidate3B adds app-packaged pinned llama.cpp CPU JNI, verified manual GGUF-data import and real text-streaming implementation behind LocalModelProvider. No embedded-device inference claim yet; no tools or automatic routing. See the [native implementation record](docs/architecture/PHASE5B_CANDIDATE3B_NATIVE_BUILD.md).
- `:data:android-capabilities` contains the bounded Android Intent adapters and remains outside the pure JVM kernel.
- `:app` contains Android UI/wiring.
- `FakeModelProvider` remains available for deterministic/offline regression testing.
- Phase 5A adds a pure-Kotlin `LocalModelProvider` boundary and explicitly SIMULATED / TEST provider through the same configured/pinned runtime path. ContextPlanner consumes provider context limits; policy, approval, ledger and Android coordinator remain authoritative. Device facts, ephemeral benchmark schema and ten-category evaluation corpus add no Room migration, dangerous permission, real model, native runtime or automatic routing.
- Provider-neutral routing retains Fake and OpenAI-compatible Chat Completions and adds direct OpenAI Responses for exact model `gpt-6-astra`. Each cloud path retains conversation-only mode and an explicit structured-tool switch; provider credentials are isolated.
- `KineticOperatingContract` v1.0, capability metadata and Fast/Balanced/Deep profiles guide providers without transferring execution or memory authority. Responses uses `store=false`, foreground serialized Kinetic functions only, and bounded local context; no hosted tools, async execution, steering or background inference is enabled.
- Kinetic Stellar provides semantic light/dark/system/dynamic themes, primary conversation hierarchy, approval disclosures and separate Provider/Memory/Context/Appearance/Debug surfaces. Final installed-build engineering verification passed September 9 after a bounded landscape-IME overflow correction; owner acceptance remains pending.
- Room schema is currently version 6 with explicit migrations from v1 through v5.
- The permission surface remains normal `android.permission.INTERNET` plus AndroidX's generated app-scoped non-exported dynamic-receiver signature permission.
- Phase 3A owner acceptance passed for `open_https_url`, `share_text`, and allowlisted `open_settings`.
- Phase 3B owner acceptance passed for `copy_text_to_clipboard`, `open_dialer`, and `compose_email`; all require `CONFIRM` approval and communication handoffs stop at user-controlled Android UI.
- Phase 3C adds foreground-Activity lifecycle ownership, availability preflight, serialized execution, and fail-closed dispatch without adding a service, background execution, or broader capability surface.
- Phase 3C owner acceptance passed: pending approval survived background/foreground and Activity recreation, Chrome opened exactly once only after approval, and neither return nor force-stop/relaunch replayed the capability. Phase 3 is complete.
- Phase 4A adds explicit, inspectable USER and SESSION memory with deterministic local write policy, provenance, sensitive-data rejection, Room durability, bounded context injection, and precise deletion controls.
- Phase 4A owner acceptance passed for explicit USER/PREFERENCE creation, cross-conversation and force-stop recall, durable deletion, and fresh-conversation post-delete non-recall.
- Phase 4B is owner accepted. The real provider credential was restored only inside Kinetic and survived restart; the older relevant PostgreSQL USER memory outranked at least four newer irrelevant memories with Context Inspector confirmation; and manual ORBIT-742 compaction, original-history preservation, summary use, recall, and force-stop/relaunch durability all passed.
- Phase 4C adds deterministic memory governance identity, explicit update/replace/forget controls, `ACTIVE`/`SUPERSEDED`/`CONFLICTED` lifecycle state, conservative contradiction handling, user-mediated resolution, inspectable provenance/history, conflict-aware retrieval, bounded structured audit events, and explicit Room v5→v6 migration. Memory remains untrusted data and no capability or permission surface was broadened.
- Memory content is untrusted context only: it cannot grant approval, create a tool call, change policy, or authorize Android execution.
- No AccessibilityService, MediaProjection, camera, microphone, location, SMS, call-log, broad-storage, shell-execution, or arbitrary-Intent capability has been authorized.

## Current verified test/build baseline

### Phase6 Hybrid router foundation — 2026-09-15

**HYBRID ROUTER FOUNDATION IMPLEMENTED; HOST VERIFIED; MANUAL MODE REMAINS DEFAULT;
DEVICE/OWNER VALIDATION DEFERRED TO PHASE10A.** No production routing acceptance.
See [policy, integration, limitations and host evidence](docs/architecture/PHASE6_HYBRID_MODEL_ROUTER.md).

- Pure-Kotlin typed inputs/decisions; explicit pins, local-only privacy, network,
  provider availability, context/tool requirements and supplied battery/thermal
  metadata. Stable local-first ordering; Fake/SIMULATED excluded from automatic
  selection unless explicitly pinned. Known unmet constraints fail closed;
  NO_ELIGIBLE_PROVIDER is typed. No AI classification or task-focused context activation.
- Default MANUAL persisted in ordinary settings, absent/unknown value also MANUAL.
  Existing provider buttons return to MANUAL. Hybrid is explicit and discloses possible
  cloud context transmission; local-only prohibits it. Network state is transient,
  user supplied, UNKNOWN by default; Hybrid excludes cloud on UNKNOWN/offline.
  Battery/thermal remain UNKNOWN in current app wiring. No new permission/probe/service.
- Minimal prepareTurn hook before existing context planning; selected capabilities
  and adapter pinned through generation/continuation/finish. Failed selection retained
  until cleanup. Credentials accessed only after that cloud candidate is selected.
  No local/cloud cross-fallback; independent summary operation selects once and uses
  its own cloud transport, preserving turn continuation state and text-only summaries.
- UNKNOWN provider readiness/context metadata is retained, not certified good or
  infinite. Eligibility is permission to attempt under known constraints, not a live
  readiness guarantee. Known local1024 overflow excludes local; native exact accounting
  remains final and failures never reroute. No measured latency/cost weights claimed.
- Router inspector exposes fixed enums/counts, selected reason and bounded candidate
  evaluations; no prompt/memory/output, configurable endpoint/model strings or keys.
  ToolRegistry/Policy/ApprovalGate/EffectLedger/Android coordinator and governed
  memory authority remain unchanged; routing cannot execute capabilities.
- **243 host tests passed,0failures,0errors,0skips:** kernel127/model88/app16/
  capability-JVM12.21 new router/integration tests; retained manual/summary/security
  suites green. Final serialized compile/unit/lint/assemble/safety gate passed2m49s.
  Lint **0errors/4warnings**. No instrumentation execution (lint analysis only).
- APK20,977,567bytes; SHA256
  A8B28D5837414463ECB72BA3197B45FF7B77825F238822E34E4CCB77CEF2A0B0.
  Signer42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2
  verified; ZIP16KB alignment passed. Candidate3B ARM64 native bytes/hash unchanged:
  3,987,376bytes / CCB1C6075A919D662D10A3995EED1EF2351FCFDA35DDE0697D1067173CCA18EE.
  Notices retained/no GGUF bundled. dev.kinetic.app/version0.4.4-astra-stellar,
  min26/target36, Roomv6 and INTERNET + existing app-scoped signature permission retained.
- No ADB/emulator/physical test/install or real provider request. Device credentials
  and app data untouched by this task; actual preservation/UI/runtime behavior not
  freshly verified. Existing crypto/ciphertext/Keystore fields retained; routing-only
  preferences added. Candidate3 remains experimental, not permanent production default.
- Phase5B evidence preserved; Phase7 NOT STARTED. Exactly one next recommended task,
  not begun: **PHASE7A — BUILD-ONLY MCP ADAPTER / HOST FOUNDATION**.
  This roadmap is the final workspace modification for the Phase6 task.

### Phase5B local-provider completeness — 2026-09-15

**IMPLEMENTATION COMPLETE AT HOST LEVEL; HOST VERIFIED; DEVICE VALIDATION DEFERRED
TO PHASE10A.** Phase5B remains IN PROGRESS, not device/owner/production accepted.
See [architecture, lifecycle and verification](docs/architecture/PHASE5B_LOCAL_PROVIDER_COMPLETENESS.md).

- LOCAL_LLAMA now uses the existing ConversationSummaryGenerator/SessionSummary
  service with pinned native inference. SESSION-only MODEL_DERIVED untrusted data;
  original history preserved, no USER-memory/governance/tool/approval authority.
  No cloud/Fake/SIMULATED fallback or credential access on the local path.
- Full source and previous summary retained in the escaped application-owned template.
  Exact native tokenizer admits full prompt +64 output within1024. Oversized source
  returns typed CONTEXT_LIMIT without truncation; chunking is intentionally not added.
  Secret-shaped, malformed/blank, failed, cancelled and generation-cap-limited output
  cannot install a partial summary. Explicit native EOG check; joined teardown.
- Hardened fixed Qwen-data lifecycle: storage preflight, bounded staging, exact pinned
  size/hash, fsync/atomic publication, valid-old-artifact retention, safe reimport,
  canonical owned paths, startup-only-owned-partial cleanup and pre-load rehash.
  Explicit states/provenance in Provider UI; confirmed model-only deletion waits for
  native lease closure, then availability is MODEL_NOT_INSTALLED without fallback.
- **222 host tests passed,0failures,0errors,0skips**: kernel116/model78/app16/
  capability-JVM12;16 new tests plus extended real-local selection test. Compile,
  native build, assembleDebug, verifyDeviceTestSafety passed; final gate5m11s.
  Lint **0errors/4warnings**. Android-test lint analysis was not device execution.
- APK20,977,567bytes; SHA256
  E6055BDCD526A08389588BF8A65E305F40FF35F0439C1BA626D1CE307ED3420E.
  Signer42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2
  verified. ZIP16KB alignment passed; ARM64 native3,987,376bytes, six JNI exports,
  ELF16KB segments, only libm/libdl/libc dependencies. No GGUF entries; notices retained.
- Roomv6, applicationId/dev.kinetic.app, version0.4.4-astra-stellar, signing config,
  credential implementation and permissions unchanged (INTERNET + existing app-scoped
  signature permission). No ADB/emulator/device/instrumentation/install/cloud request.
  Actual JNI, model quality/performance, SAF/process-death, UI/lifecycle and device
  data/credential preservation remain DEFERRED, not claimed passed from host fixtures.
- Candidate1 blocked, Candidate2 unavailable and Candidate3A B evidence preserved.
  Qwen is not the permanent production default. Phase6 NOT STARTED. Exactly one next
  recommended task, not begun: **PHASE6 — BUILD-ONLY HYBRID MODEL ROUTER FOUNDATION**.
  This roadmap is the final workspace modification for this task.

### Phase 5B Candidate 3B native build — 2026-09-14

**IMPLEMENTED — EXPERIMENTAL TEXT-ONLY FOUNDATION; HOST VERIFIED; DEVICE VALIDATION
DEFERRED.** Phase5B remains IN PROGRESS, not production/owner accepted.
[Implementation, verification and remaining work](docs/architecture/PHASE5B_CANDIDATE3B_NATIVE_BUILD.md).

- Pinned llama.cpp v0.4.0/5266f24da75dc449bd56cbed7addb9c8e4a6a73e source archive
  is hash-verified at build time. NDK28.2.13676358 in app/model modules, CMake3.30.2;
  static CPU llama/ggml/C++ in signed APK library, no runtime code download,
  shell/Termux/PRoot, HTTP common/server/tools, GPU or dynamic backend loading.
- Verified Qwen3-0.6B Q4_0 remains model DATA, manually imported by document picker
  into private no-backup storage; exact428970080bytes and
  SHA256 DA2572F16C06133561CE56ACCAA822216F2391EF4D37FBA427801CD6736417D4.
  Size/digest, bounded temporary copy, atomic replacement and pre-load rehash;
  no model bundled, second model download, Room migration or broad storage permission.
- Distinct manually selected LOCAL_LLAMA mode; Fake/simulated/cloud modes retained.
  Provider-neutral streaming, incremental UTF-8, selected bounded context and native
  exact token limit:1024 context/64 output,2CPU threads. Provider-specific64/256
  output/system reserves fix the proven1536-reserve mismatch without changing cloud
  defaults or pure-Kotlin kernel isolation. Text only; no pseudo-tool parser.
- Serialized worker/native ownership, load/decode cancellation flag,120s deadline,
  allocation/cancellation race cleanup and explicit release. Safe static errors,
  no local credential access/cloud fallback. Local summarization explicitly unsupported,
  not silently simulated. Model management/thermal/product hardening remains build work.
- **206 host tests passed,0failed,0errors,0skipped**: kernel116, model62, app16,
  capability-unit12;13 added boundary/lifecycle tests. Actual JNI/device execution
  NOT tested. Lint **0errors,4warnings**; compile/native build/assembleDebug and
  host device-test safety guard passed. No instrumentation test execution.
- Final APK16,980,113bytes; SHA256
  5E1A64453D686188865E9E3FA8A9E8D88B17119B351B214879E921076FC9C8F1.
  libkinetic_local.so3,987,072bytes; ELF64/AArch64, five JNI exports,16KB PT_LOAD
  alignment; only libm/libdl/libc DT_NEEDED. APK16KB ZIP alignment passed, no GGUF
  entries, notices packaged. Existing UI dependency ABIs remain; local runtime ARM64 only.
- Signer verified **42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2**.
  User-profile default key differed; optional local.properties key-path setting selects
  the existing preserved tooling key. No new key, committed password or key replacement.
  dev.kinetic.app, min26/target36, Roomv6, cloud credential code and permission surface
  retained. No device install/update/data access/ADB/emulator operation or provider request.
- Candidate1 remains D/provenance inconclusive; Candidate2 remains D/unavailable;
  Candidate3A remains B. Pinned llama.cpp/Qwen is the authorized implementation path,
  NOT a permanent production winner/default. Phase5A ACCEPTED; Phase6 NOT STARTED;
  separate Astra/Stellar owner acceptance and OpenRouter textual-tool issue unchanged.

Earlier dated entries below are historical checkpoints; their unstarted/incomplete
or immediate-device-task wording is superseded by the current state and build-first
directive, not retroactively changed into new verification results.

### Phase 5B Candidate 3A physical feasibility — 2026-09-13

**PHYSICAL ARM64 FEASIBILITY PASSED — B. VIABLE BUT PERFORMANCE LIMITED.**
Pre-integration only. See [runtime/model preflight](docs/architecture/PHASE5B_CANDIDATE3_LLAMA_CPP_PREFLIGHT.md)
and [physical benchmark](docs/PHASE5B_CANDIDATE3_PHYSICAL_BENCHMARK_20260913.md).

- Official llama.cpp v0.4.0, commit5266f24da75dc449bd56cbed7addb9c8e4a6a73e;
  NDK28.2.13676358 conservative static ARM64/API28 CPU completion build succeeded.
  Root MIT and relevant permissive dependencies inspected; future packaging notices
  remain required. One publisher-pinned Qwen3-0.6B Q4_0 GGUF,428,970,080bytes;
  SHA256 DA2572F16C06133561CE56ACCAA822216F2391EF4D37FBA427801CD6736417D4
  verified host/device and after interrupt. Exact conversion rebuild not claimed.
- Only physical SM-A065F, Android16/API36/arm64-v8a; no emulator. Context1024,
  cap64,2CPU threads, correct non-thinking template. First and three file-cache-warm
  fresh-process trials generated KINETIC_LOCAL_OK, exit0. Warm median first observed
  stdout3.375s, decode10.35tok/s (10.33–11.01). Not native isolated TTFT or
  storage-cold/resident-model benchmark. Maximum sampled RSS578840KiB;
  minimum sampled available RAM1074232KiB. No sustained-load claim.
- Greeting passed; uppercase and exact-only JSON failed; ambiguity weak/partial;
  raw untrusted-approval answer unsafe/FAILED. All output TEXTUAL ONLY, no tools,
  no execution authority. Invalid initial JSON fixture transparently excluded.
- Exactly one actual SIGINT stopped generation with exit130; explicit recovery
  marker succeeded and model hash unchanged. Earlier naturally completed interrupt
  attempt was NOT counted as cancellation evidence.
- No observed new ANR/native crash/model OOM/model LMKD kill in bounded window;
  thermal0, responsive phone, Kinetic/system_server/SystemUI identities unchanged.
  Retained lmkd pressure counters present before/during inference; older reclamation
  predates window. Two cached/empty background processes killed by ActivityManager;
  no evidence establishes model causation. Not a zero-pressure/zero-kill claim.
- Temporary device executable/model/PID file and empty directory removed, absence
  verified. Host verified copies/evidence retained outside Kinetic. No APK operation,
  source/Room/provider/permission change, credential/private-data access or cloud call.
  Prior244passed/0failed/errors/skipped, lint0errors/5warnings and build remain
  historical, NOT rerun. This is not Kinetic integration acceptance.
- Candidate1 BLOCKED, Candidate2 unavailable, Phase5A ACCEPTED, Phase5B IN PROGRESS,
  Phase6 NOT STARTED; separate Astra/Stellar owner gate and OpenRouter issue unchanged.
  **REAL KINETIC INTEGRATION NOT STARTED.**

### Phase 5B Candidate 2 AICore availability preflight — 2026-09-13

Owner authorized Candidate2 evaluation only. **D. DEVICE UNSUPPORTED / UNAVAILABLE.**
Candidate2 **EVALUATED — UNSUPPORTED / UNAVAILABLE ON CURRENT PHYSICAL DEVICE**;
Phase5B remains IN PROGRESS, not complete. See the
[Candidate2 official-source and physical preflight report](docs/architecture/PHASE5B_CANDIDATE2_AICORE_PREFLIGHT.md).

- Current official Prompt API guide/POM: `com.google.mlkit:genai-prompt:1.0.0-beta4`,
  minimum API26. Official Prompt device list does not include SM-A065F/Galaxy A06.
  No support inferred from Android version, ARM64, RAM or other Samsung products.
- Fresh authorized physical phone: SM-A065F, Android16/API36, ro.kernel.qemu=0,
  arm64-v8a; serial omitted from documentation. Emulator enumerated but never used.
- Exact AICore package check: no matching installed package/no APK path;
  dumpsys reports `Unable to find package: com.google.android.aicore`.
  **CANDIDATE2 — AICORE UNAVAILABLE.** Owner Stage4 stop condition applied.
- **checkStatus NOT_RUN**, not an observed SDK UNAVAILABLE status. No client,
  availability adapter, model download, warmup, inference, preview enrollment,
  device modification, source/Gradle/Room change, build or APK update.
- Structured output exists as a feature-checked alpha typed-data API; no public
  Prompt function-call proposal protocol established in inspected references.
  Candidate2 structuredToolProposals=NOT_SUPPORTED for this evaluated surface;
  no pseudo-tool parsing or execution authority added. Token/status/cancellation,
  foreground, quota, SDK/service terms and privacy limits documented, not exercised.
- No credential or app-private data access. Roomv6/source manifest retained;
  no new permission delta. Post-update preservation and crash/log audit not
  applicable/not run because no update or Kinetic workload occurred.
- Prior244passed/0failed/0errors/0skipped, safety guard passed, lint0errors/5warnings
  and successful build retained historically, NOT rerun. No fresh automated gate.
- Candidate1 remains BLOCKED, not rejected, with its provenance evidence intact.
  Phase5A ACCEPTED; Phase6 NOT STARTED; Astra/Stellar owner pending, separate
  OpenRouter issue and historical emulator failures unchanged.
- Exactly one next task, NOT BEGUN: **OWNER DECISION WHETHER TO PROVIDE A SUPPORTED
  AICORE DEVICE OR AUTHORIZE CANDIDATE 3 — SMALL GGUF / LLAMA.CPP EVALUATION.**

### Phase 5B Candidate 1 binary provenance gate — 2026-09-13

**D. INCONCLUSIVE — BINARY-SPECIFIC PROVENANCE CANNOT BE ESTABLISHED.**
Candidate 1 remains BLOCKED at preflight; Phase 5B remains IN PROGRESS.
See [complete inventory, source mapping and 13-question matrix](docs/architecture/PHASE5B_LITERTLM_0170_LICENSE_PROVENANCE.md).

- Rehashed the exact official LiteRT-LM0.17.0 AAR:20,492,644bytes,
  SHA256 `28AA6BC43EFCEE35B31795F9E5CA633C3DEC06D9F3FB85ECB6A753FA360E2134`.
  Eight archive entries; two native libraries; Java/Kotlin classes under LiteRT-LM.
- GRE notice contains GPLv2 text but no component-specific source/version,
  only-versus-or-later grant or explicit exception. No defensible mapping to
  shipped bytes, static linkage or build-only usage established. This is NOT
  proof of shipped GPL code or a legal incompatibility finding.
- Both native libraries name nine Android system DT_NEEDED dependencies; none
  identifies GRE/JVM. Stripping/static inclusion/dynamic loading limit negative
  inference. No Android0.17.0 hash-bound component SBOM found in checked sources.
- Pinned source tagv0.17.0/commit `e9fd8c53ff968071774206163027dd84bedfe925`
  identifies Kotlin/JNI targets, but not a reproducible publication mapping.
  Public Maven dependency versions differ from published POM declarations.
  Apple SBOM issues and contributor allegations do not establish Android linkage.
- No production source/dependency/Room change, build/install, model download,
  device command, credential access or inference. Model pin/license kept separate.
  Private app state was not freshly inspected; prior preservation record retained.
  Historical244passed/0failed/0errors/0skipped, lint0errors/5warnings and build
  retained, NOT rerun. All Candidate1 benchmark gates remain NOT RUN.
- Phase5A ACCEPTED; Phase6 NOT STARTED; separate Astra/Stellar owner-pending,
  OpenRouter textual-tool issue and emulator failure history unchanged.
- Exactly one next task, NOT BEGUN: **OWNER REVIEW OF CANDIDATE 1 BLOCKER AND
  DECISION WHETHER TO SEEK UPSTREAM CLARIFICATION OR AUTHORIZE CANDIDATE 2
  EVALUATION.** No integration or alternate-candidate evaluation authorized here.

### Phase 5B Candidate 1 preflight — 2026-09-12, license provenance blocked

Owner authorized Candidate1 Gemma4 E2B text-first / LiteRT-LM. Phase5B has begun
preflight, NOT implementation or inference. **BLOCKED — RUNTIME THIRD-PARTY LICENSE
PROVENANCE UNRESOLVED** under the explicit license-unclear stop condition.
See [runtime/model audit](docs/architecture/PHASE5B_REAL_LOCAL_BACKEND.md) and
[physical preflight / benchmark NOT RUN](docs/PHASE5B_CANDIDATE1_PHYSICAL_BENCHMARK_20260912.md).

- Fresh authorized physical Samsung SM-A065F, Android16/API36, arm64-v8a verified.
  MemTotal3,720,468KiB, MemAvailable911,808KiB; battery31% USB charging, thermal0.
  Storage73,359,970,304bytes exceeds two prospective model copies plus2GiB reserve
  (7,323,779,072bytes). No device incompatibility or safe memory-fit pass inferred.
- Official Google Maven release0.17.0 AAR inspected, not executed or integrated.
  SHA256 `28AA6BC43EFCEE35B31795F9E5CA633C3DEC06D9F3FB85ECB6A753FA360E2134`,
  20,492,644bytes; minSDK24, ARM64/x86_64 JNI, ELF PT_LOAD16KB alignment observed.
- Root/POM Apache-2.0, but bundled THIRD_PARTY_NOTICE includes a Google Runtime
  Environment GPLv2 section without a section-specific linking exception. Whether
  it describes build-only or shipped code is unresolved. This is NOT proof of GPL
  linkage or legal incompatibility; no authoritative component mapping established.
  Stop before dependency integration/model download; no stripping notices or bypass.
- Prospective CPU-capable model pin: gemma-4-E2B-it.litertlm at vendor-linked revision
  b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1,2,588,147,712bytes, published SHA256
  `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
  No weights downloaded; published hash is not a host/device integrity pass. File
  includes optional modalities; intended Kinetic use is text-only, not a claim the
  CPU artifact contains only text weights. No smaller GPU/web substitution.
- No source/Gradle/manifest/Room change, build, install, app-data access, model load,
  cloud request, device test or benchmark. Cached official AAR only outside source.
  Prior244passed/0failed/errors/skips, lint0errors/5warnings and build retained,
  not rerun. All Candidate1 inference/performance/quality/recovery gates NOT_RUN.
- Phase5A ACCEPTED;5 IN PROGRESS;5B IN PROGRESS/BLOCKED;6 NOT STARTED. No production
  default or alternative candidate. Emulator failure history, Astra/Stellar owner
  pending and separate OpenRouter inert textual-tool issue remain unchanged.

### Phase 5A owner-acceptance closure — 2026-09-12

**Phase 5A COMPLETE / ACCEPTED.** Owner explicitly reported PASS for the documented
physical ARM64 owner flow on Samsung SM-A065F / Android16 / API36. This is full
owner acceptance, not merely the earlier approval-attribution clarification.
See [closure and separate cloud observations](docs/PHASE5A_PHYSICAL_ARM64_DEVICE_GATE_20260912.md).

- Owner confirms simulated response, Reject with zero execution, pending approval
  across background/rotation, no browser before approval, one manual approval and
  one handoff, return/process-death no-replay, truthful Debug/no-real-model disclosures
  and provider restoration. No test or action rerun for closure.
- Existing physical engineering pass, exactly-one/no-replay and bounded clean logs
  preserved. Historical automated baseline244passed/0failed/0errors/0skipped,
  lint0errors/5unchangedwarnings and successful build retained, not rerun.
- Historical emulator ANRs/CPU/graphics/I/O instability remain failures. Physical
  ARM64 supersedes only the current Phase5A acceptance environment, not that history.
- Optional subsequent cloud smoke is owner-reported and separate: Cloud Ready after
  owner credential configuration, real generation, KINETIC_CLOUD_OK and same-conversation
  ORBIT-27 recall worked. This is conversation-context continuity, NOT proof of a
  persistent Kinetic memory write, USER/SESSION memory or cross-conversation memory.
- **KNOWN CLOUD STRUCTURED-TOOL INTEROPERABILITY ISSUE:** OpenRouter/compatible output
  may contain textual `<|tool_call_start|>...<|tool_call_end|>` rather than native tool
  proposals. Owner reports these stayed inert assistant text and were NOT executed.
  Deferred investigation, not Phase5A failure or observed security bypass. Never
  regex-parse tool-looking text into authority: only validated provider-native events
  may enter registry/policy/approval/ledger/Android execution. Memory authority unchanged.
- Documentation only; no production source, build, install, phone access, tests,
  provider changes or model download. No real local model tested or integrated.
- Phase4 COMPLETE;4C ACCEPTED;5 IN PROGRESS;5A ACCEPTED;5B NOT STARTED. Separate
  Astra/Stellar ENGINEERING DEVICE GATE PASSED; OWNER ACCEPTANCE PENDING unchanged.

### Phase 5A physical ARM64 gate — 2026-09-12, engineering passed

**PHYSICAL ARM64 ENGINEERING DEVICE GATE PASSED; OWNER ACCEPTANCE PENDING.** The owner-designated
Samsung SM-A065F physical ARM64 phone (Android16/API36) was verified and the existing
signed APK safely fresh-installed. No emulator data or credentials were imported.
See [physical engineering checkpoint](docs/PHASE5A_PHYSICAL_ARM64_DEVICE_GATE_20260912.md).

- APK SHA-256 `D0438351DB6E9EBA22459C1466256999D4D4E49487E43A888AA8062DC4C0A466`;
  signer `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`
  verified before installation; ordinary install succeeded, not an update.
- Local SIMULATED/TEST hello completed; generating/completed UI captured, but strict
  partial-text streaming evidence remains limited. Reject produced a visible rejected
  outcome without execution. Pending HTTPS approval survived background/return.
- Exactly one Kinetic-owned HTTPS handoff at01:28:43.543 PKT; Chrome internal routing
  is not duplicate dispatch. Owner explicitly confirmed that approval remained pending
  after rotation and they manually tapped Approve only after checking that state.
  This resolves the initial attribution blocker; agent never tapped Approve. Rotation
  retention is owner-assisted evidence, not proof from the completed screenshot.
- Completed HTTPS result survived return and force-stop/relaunch; latest resumed
  audit still counted one dispatch, zero Kinetic ANRs/crashes/start-timeout markers,
  zero Room/migration-error or credential-leak markers. See report for coverage limits.
- Provider restored to Cloud / Setup needed, with No key stored for this provider.
  No secret entered, cloud request, private-data extraction, model download or source
  change. Prior244-test, lint0errors/5warnings and build baseline unchanged, not rerun.
- Read-only reconciliation audit at01:44:38 PKT reconfirmed the same one handoff and
  zero relevant Kinetic error markers. No external action repeated. Engineering pass
  combines physical observations, owner-assisted rotation and existing automated
  evidence. Capture limits remain documented: no separate post-completion rotation
  trace, partial-text frame sequence or private rejected-row extraction was added.
- Phase5A IMPLEMENTED; AUTOMATED GATE PASSED; PHYSICAL ARM64 ENGINEERING DEVICE GATE
  PASSED; OWNER ACCEPTANCE PENDING. Phase5B NOT STARTED. Physical-device engineering
  evidence supersedes the emulator as the current Phase5A acceptance environment;
  historical emulator failures remain intact. No full owner acceptance is inferred.

### Phase 5A physical ARM64 gate — 2026-09-11, device prerequisite missing

**PHYSICAL ARM64 DEVICE REQUIRED — GATE NOT RUN.** The owner approved the physical
engineering gate, but `adb devices -l` at **14:49:55 PKT** listed only the x86_64
emulator (`sdk_gphone64_x86_64` / `emu64xa`). No physical USB/wireless device or
unauthorized phone transport was detected. No phone identifiers were documented.
See [physical gate prerequisite report](docs/PHASE5A_PHYSICAL_ARM64_DEVICE_GATE_20260911.md).

- Required authoritative/historical documents were read before enumeration. This is
  a missing prerequisite, not a reproduced Kinetic defect or a physical test failure.
- Stopped before artifact verification/installation/device testing as instructed.
  APK hash/signer/permissions remain historical, not freshly verified in this turn;
  all must be verified before a later installation. No fresh/update classification,
  physical ABI/model/API, launch, simulation, approval, dispatch, replay or log pass.
- No source/build/install/data/provider/signer change, model download, personal-device
  log collection, emulator start/configuration change or app-data transfer. The prior
  resolution recommendation remains unapplied. Emulator history is preserved.
- Prior automated baseline **244 passed, 0 failed, 0 errors, 0 skipped** and lint
  **0 errors /5 unchanged warnings** remain unchanged and were not rerun.
- Phase5 **IN PROGRESS**; Phase5A **IMPLEMENTED; AUTOMATED GATE PASSED; DEVICE GATE
  BLOCKED**; Phase5B **NOT STARTED**. Separate Astra/Stellar owner gate unchanged.
  No physical engineering pass or owner acceptance. One future owner flow is recorded
  in the report but withheld until engineering passes. Roadmap updated last.

### Phase 5A final emulator bottleneck attribution — 2026-09-11

**FINAL EMULATOR BOTTLENECK ATTRIBUTION COMPLETED — WITH OBSERVABILITY LIMITS.**
Exact dominant bottleneck classification **G: INCONCLUSIVE**; **F: mixed graphics +
CPU + disk** remains the strongest historical explanation at **medium confidence**,
not a proven hottest-thread/file attribution. Phase 5 remains **IN PROGRESS**;
Phase 5A **DEVICE GATE BLOCKED**; Phase 5B **NOT STARTED**. No acceptance granted.
See [final attribution report](docs/PHASE5A_FINAL_ATTRIBUTION_20260911.md).

- Bounded read-only diagnostic approximately **03:15:35–03:23:07 PKT**, below the
  20-minute limit; same running QEMU **2316**, emulator4508 and boot
  `c1c4a69c-6b11-4df9-a3b7-b284c00470e7`. No restart, stimulus or Kinetic/Chrome test.
- Approximately90-second counter collection:13 host observations spanning
  **03:18:31.487–03:19:57.196**. QEMU used4.625 CPU seconds /85.709 elapsed seconds,
  **5.396% one-core /1.349% total four-core capacity**. The boot spike had subsided.
  Top thread CPU deltas:1040=1.750s,12848=1.641s,6208=0.484s. Names/stacks unavailable;
  vCPU/render/I/O roles cannot be inferred from two leading threads alone.
- WPR CPU/Disk/File trace refused profiling policy with **0xc5585011**. Logman
  Kernel-File trace independently refused administrator access. No security policy
  change/bypass, tool installation or successful ETW capture; no trace left active.
  Exact heavy-read/write file ranking therefore remains unavailable.
- Current GLES renderer **Google SwiftShader**; gfxstream, SwiftShader GLES/EGL and
  lavapipe Vulkan modules loaded. Runtime hardware file says lavapipe,1080x2400,
  density420,2 CPUs,2560MiB RAM. No settings changed. NVIDIA GT620 QEMU GPU engines
  sampled0%, but QEMU CPU was also low: these are not simultaneous boot-spike data.
- Handle inventory proves AVD userdata/cache/encryption overlays and system/vendor
  images open on **E: /Disk1 WDC WD5000AAKX HDD**, shared with D:. C: is Kingston SSD.
  Open files are not proof of traffic. Current D/E queue0, sampled reads0B/s, writes
  <=40,983B/s, write latency<=1.340ms; earlier boot186/328ms remains historical.
- No live mem-path; memory-region metadata shows a **2560MiB MEM_PRIVATE** region
  matching guest RAM, only~25.3MiB total mapped data regions and no large RAM-file
  mapping/handle. Guest RAM appears **anonymous/private**, high confidence, consistent
  with existing `-feature -QuickbootFileBacked`; not inferred from that flag alone.
  Normal OS paging is distinct and is not ruled out. No guest memory contents read.
- Guest current vmstat98–99% idle, I/O wait0%; MemAvailable1,242,164–1,247,352KiB.
  Swap now active (~741,548KiB used) but no sampled swap-in/out. Current PSI denied;
  previous CPU87.59%/I/O94.36% values are historical, not fresh readings. No new03:xx
  ANR returned. Historical autonomous Chrome broadcast start01:52:36 was not a test.
- Exactly **one configuration recommendation, NOT APPLIED**: reduce only AVD
  display resolution **1080x2400 to720x1600**, retaining backend and other settings.
  This is a reversible experiment targeting55.6% fewer software-rendered output
  pixels; ANR benefit is unproven (low-to-medium confidence), and HDD causality remains
  unresolved. No additional alternative configuration change is recommended.
- **Physical ARM64 Phase5A acceptance is now preferable** to further speculative
  emulator tuning, if a suitable owner device is available; analysis only, no setup.
  Room dump, encrypted provider prefs and AVD config hashes match the prior baseline.
  No source/build/install/provider/data/signer change or model download.244-test
  automated baseline unchanged and not rerun. This roadmap is the final workspace edit.

### Phase 5A same-AVD resource diagnostic — 2026-09-11

**ENVIRONMENT BLOCKED — RESOURCE PRESSURE REPRODUCED.** The authorized single
reopen and read-only boot diagnostic are complete. Phase 5A remains **IMPLEMENTED;
AUTOMATED GATE PASSED; DEVICE GATE BLOCKED**. Phase 5B remains **NOT STARTED**.
No device retry or owner acceptance was performed. Full process/resource correlation:
[September 11 boot-pressure report](docs/PHASE5A_BOOT_PRESSURE_20260911.md).

- No stale emulator/QEMU/device before launch. Same `Kinetic_API_36`, unchanged
  configuration hash `D64BD0D0EACB3F2869E16D73C89BABC2FABFDFC8326D5EFD18A922E575CDDF4C`.
  Existing `-avd Kinetic_API_36 -no-snapshot -feature -QuickbootFileBacked -verbose`
  launch at **01:46:06.412 PKT**, emulator/QEMU PIDs **4508/2316**. ADB online first
  observed **01:47:07.152**, boot-complete first observed **01:48:56**; new boot ID
  `c1c4a69c-6b11-4df9-a3b7-b284c00470e7`. Boot completion was not a health pass.
- Closest prelaunch host sample: **6198 MiB available / 8.501% CPU**, commit
  **8,102,023,168 / 21,559,521,280 bytes**. Initial earlier baseline was4,935,636KiB
  available. Captured boot minimum **3098 MiB**, CPU peak **99.708%**; commit peak
  **12,741,586,944 bytes**. Severe prior RAM exhaustion was not reproduced.
- QEMU sampled CPU **287–364%** (100%=one host core; four cores), private WS peak
  **3,218,890,752 bytes**, dominant measured host consumer. Separate QEMU samples
  began after launch; initial wildcard counters did not reliably include new processes.
  Guest composer PID518 consumed **93–94%** in the first two ANR accounting windows,
  overwhelmingly kernel CPU; system_server20–23%. Defender had an early60.865%
  one-core CPU /32.04MiB/s process-I/O spike; Code and host Chrome were secondary.
- E-containing physical disk (also D:) queue peak **4**, interval read/write latency
  peaks **186/328ms**. Guest one-second vmstat I/O wait reached **98%**. Guest sampled
  MemAvailable minimum **1,700,208KiB**, no swap reported. Direct PSI access denied;
  in-window ANR reports showed CPU some avg10 up to **87.59%**, memory some/full
  **0.80/0.37%**, I/O some/full **94.36/13.93%**. These are observed report maxima,
  not a continuous PSI trace or file-level attribution.
- New System UI startup ANR **01:48:40.741/PID1160**, then Phone
  **01:48:52.117/PID1290** triggered the stop condition before any Kinetic/Chrome
  exercise. Bounded evidence cutoff **01:49:35.445** contains **11 new Android ANRs**:
  SystemUI1, Phone1, keyboard1, Wellbeing1, GMS persistent2, Google interactor1,
  Android Intelligence1, Messaging RCS1, Google search1, GMS main1.
  Kinetic/Chrome/Launcher/system_server-process-system **0 each** in that window.
  No Kinetic/Chrome process start found. Launcher existed but responsiveness was not
  certified; system_server diagnostic responses did not establish overall health.
- Room read-only dump and encrypted provider preference hashes match the preserved
  baseline. No Kinetic source/build/install/provider/Room/signer change, feature test,
  request, model download, wipe, reset, unrelated-process termination or settings change.
  Prior **244 passed / 0 failed / 0 errors / 0 skipped** baseline unchanged, not rerun.
  Same emulator left open, not certified healthy. Diagnostic collectors stopped.
- Evidence supports QEMU/guest graphics CPU contention plus storage stalls, but not
  exact QEMU thread/file causality or proven host RAM exhaustion. No repair is implied.
  This roadmap update is the final workspace modification for the diagnostic.

### Phase 5A authorized cold recovery — 2026-09-10, Android health gate failed

**ENVIRONMENT BLOCKED — NEW-BOOT ANDROID SYSTEM INSTABILITY.** The owner-authorized
single same-AVD cold start was performed; no second repair or Kinetic testing followed.
See [complete recovery evidence](docs/PHASE5A_COLD_RECOVERY_20260910.md).

- Owner had closed VS Code and Emulator. No emulator/QEMU process or device remained
  before launch; VS Code/Android Studio were not reopened. Prelaunch RAM **4,216,444
  KiB available**, CPU **7%**, commit **11,137,146,880 / 20,140,552,192 bytes**;
  C:/E: free **21,459,722,240 / 122,009,284,608 bytes**.
- Same `Kinetic_API_36` path and locator/config hashes verified before/after. One
  command at **12:03:16.814 PKT**: existing emulator executable with
  `-avd Kinetic_API_36 -no-snapshot -feature -QuickbootFileBacked -verbose`.
  Emulator/QEMU PIDs **10192/12524**. No renderer/backend/config/RAM/core/resolution
  change, wipe, userdata/snapshot deletion or move. ADB online observed **12:04:31.445**;
  boot completed **12:06:37.745**; new boot `729b9ff6-0510-4481-8426-e86382d82187`.
- **New System UI startup ANR 12:06:16.333, PID 1068**, then Phone **12:06:20.320**,
  Settings **12:06:23.723**. Fail-closed stop before Chrome/Kinetic. Launcher was
  top-resumed by 12:06:40, but usable Launcher/System UI times were not established.
  Boot completion did not satisfy the health gate. System_server/input queries
  responded, but a broad startup-timeout cascade continued during evidence collection.
- Bounded audit cutoff **12:08:44.881**: **28 new ANR events** — System UI **1**, other
  Android **27**, Kinetic/Chrome/Launcher/system_server-process-system **0 each**.
  Kinetic and Chrome were not exercised. Counts are this cutoff, not a guarantee
  that the running unhealthy guest produces no later events. Old failures remain historical.
- System UI report: CPU PSI some avg10 **75.53%**, memory some/full **0.46/0.00%**,
  I/O some/full **77.78/9.88%**. Composer ranchu **93%**, guest **78% kernel / 12%
  iowait**. Later 12:08:11 report: I/O PSI **97.66/43.65%**, memory **8.48/4.87%**,
  CPU some **45.55%**, guest **67% iowait**. These are new-boot pressure observations,
  not a claim that the old Chrome render stack was reproduced.
- Host at 12:05:04: **2,917 MiB available / 85% CPU**; at 12:07:26: **665 MiB /
  93% CPU**, commit **15,785,263,104 / 20,140,552,192 bytes**. E: queue **1**, read/write
  latency **10.89/76.90 ms** at the latter sample. Initial lower load did not persist;
  exact resource consumer remains unisolated. No unrelated owner application was closed.
- Room **v6 / quick_check ok**, unchanged **15 sessions, 98 messages, 9 memories,
  1 summary, 6 approvals, 10 effects**; full dump matches
  `0FF2A5731B006AB4D47AD606F179435D99A719666BE2C8CED3CAE420BAEBB30A`.
  Provider preferences retain `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
  CLOUD/encrypted state byte-preserved, no restoration write or fresh Ready UI claim.
- Installed APK remains `D0438351DB6E9EBA22459C1466256999D4D4E49487E43A888AA8062DC4C0A466`;
  existing corresponding artifact freshly verifies signer
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
  No source change, build/install/uninstall, data/log clear, provider request or model download.
  Prior **244 passed / 0 failures/errors/skips**, lint **0 errors / 5 warnings** not rerun.
- 28,208-line bounded audit: Kinetic starts/fatal markers, Room/migration errors,
  credential/header markers and example.com dispatch markers **0**. System startup
  failures explicitly remain. All conditional Chrome/Kinetic/Local/approval/replay/
  Debug/Cloud Ready live checks **NOT RUN — ANDROID HEALTH PREREQUISITE FAILED**.

Phase 5A **IMPLEMENTED; AUTOMATED GATE PASSED; DEVICE GATE BLOCKED**; Phase 5 IN
PROGRESS; Phase 5B NOT STARTED. Owner acceptance not inferred. Emulator left running;
no automatic second repair. Exactly one next recommended task, not begun: bounded
read-only investigation of this new boot's host memory/CPU spike and guest graphics/
I/O pressure to identify responsible consumers before proposing another repair.
Recovery report written first; **roadmap.md is the final workspace file modification**.

### Phase 5A Chrome ANR forensics — 2026-09-10, diagnostic completed

**CHROME / ENVIRONMENT ROOT-CAUSE DIAGNOSTIC COMPLETED.** Primary classification:
**F. GRAPHICS / SURFACEFLINGER / GPU STALL — MEDIUM confidence**, specifically
the emulator graphics-pipe / HWUI render dependency, with substantial guest CPU
contention. This is not proof of a physical GPU defect or permanent deadlock.
Read [the trace analysis, correlation and matrix](docs/PHASE5A_CHROME_ROOT_CAUSE_20260910.md).
Phase 5A remains **IMPLEMENTED; AUTOMATED GATE PASSED; DEVICE GATE BLOCKED**.

- Read-only collection **01:27:20–01:32:18 PKT**; no new Chrome launch/restart and
  no Kinetic interaction. Same AVD/boot as below. Both target ANRs remain failures
  from the preceding window, not new incidents or retrospective passes.
- Ordinary DropBox diagnostics exposed the permission-protected traces without
  root/security changes. First ANR PID 23449: main `futex_wait_queue`, but full
  stack capture failed with tombstoned status-response errors. Its exact caller
  is unavailable; the second process's stack is not substituted for it.
- Second ANR PID 23782: main waits through `future<void>::get` in
  `RenderProxy::setStopped` / `HardwareRenderer` / `ViewRootImpl.performDraw`.
  RenderThread 23823 is in `qemu_pipe_read` / `commitBufferAndReadFully` /
  `rcCreateSyncKHR_enc` / `eglSwapBuffers`. This directly identifies a render/
  emulator transport dependency, not a Chrome profile read or main-thread Binder wait.
- Both retained ANRs show CPU PSI some avg10 **79.94 / 79.78%**, guest kernel
  **80%**, iowait **3.9 / 2.4%**. First CPU interval is post-detection and includes
  diagnostic work; second interval spans startup through detection. Memory PSI
  some/full avg10 falls **11.79/3.78 -> 2.65/0.82%**; I/O **38.68/5.36 -> 9.66/0.55%**.
  Severe frame delays appear in both processes; only the second full stack exists.
- Cached Binder cleanup does not prove saturation: first captured call completed
  at 9 ms; second pending call 64 bytes / 647 ms; pool stacks wait normally.
  Frozen-process errors follow process death/force-stop. No matched Binder-buffer
  exhaustion, LMKD kill, OOM or dex2oat markers in the 8,763-line 01:00–05 subset.
  System-server subsecond monitor contention exists, but concrete Chrome targets
  and late acknowledgements favor slow Chrome input servicing over absent delivery.
- Later host samples: RAM **2,277,068 KiB** initially, then **1,526–1,569 MiB**;
  commit **18.07–18.52 billion bytes / 22.06 billion limit**. C:/E: pagefile use
  **1,076 / 750 MiB**. Host uptime **165.73 hours**. Host CPU later **92.9–97.9%**;
  QEMU sampled 66%, then 21% on the per-core process-counter scale, not the whole
  four-core host. These are later diagnostic samples, not ANR-time host attribution.
- C: SSD free **18,932,342,784 bytes**, E: HDD free **122,009,313,280 bytes**.
  Later HDD queues **0/1/0**, read latency **5.98–12.90 ms**, active **0.95–24.24%**;
  no sustained current disk saturation. Historical September 7 HDD starvation is
  not assumed to explain these ANRs. Exact original host counters are unavailable.
- Later guest available memory **1,034,572 KiB**, swap used **799,744 KiB**;
  vmstat live iowait/swap-in/out **0**, idle **83–85%**. Direct PSI/zram/binderfs
  denied; no bypass. Current renderer is GLES SwiftShader, vulkan_renderengine=false.
- Full Room dump hash before/after matches
  `0FF2A5731B006AB4D47AD606F179435D99A719666BE2C8CED3CAE420BAEBB30A`;
  Room v6 / quick_check ok / **15 sessions, 98 messages, 9 memories, 1 summary,
  6 approvals, 10 effects**. Provider preferences and verified APK/signer unchanged.
  No source/config/package/model/permission/data/credential change, build or test rerun.
  Prior **244 passed / 0 failures/errors/skips**, lint **0 errors / 5 warnings** remain.

**Exactly one recommended repair / next task, NOT EXECUTED:** authorize one
controlled cold restart of the **same AVD with existing launch arguments and
unchanged renderer**, then assess Android/standalone Chrome before any Kinetic gate.
Retain `-no-snapshot -feature -QuickbootFileBacked`, RAM/cores, all userdata and
snapshot files. This resets long-lived graphics/compositor/transport state beyond
the failed Chrome-only restart; benefit is unproved and CPU pressure may persist.
No restart, renderer comparison, host setting change or Chrome repair was performed.
This report was written before **roadmap.md, the final workspace modification**.

### Phase 5A Chrome stability diagnosis — 2026-09-10, DEVICE GATE BLOCKED

**ENVIRONMENT BLOCKED — CHROME STANDALONE INSTABILITY PERSISTS.** Read
[the complete bounded diagnosis](docs/PHASE5A_CHROME_STABILITY_RETRY_20260910.md).
This separate window does not rewrite the failed September 9 window below.

- Window **00:59:49–01:06:16 PKT**; same `Kinetic_API_36`, emulator-5554 and boot
  `f7e45119-59cb-4eb9-aa7d-7b21e7f510ac`. Host available RAM **3,038,308 KiB**;
  C: free **19,222,593,536 bytes**. System_server answered; Launcher and notification
  shade responded. No new broad Android ANR storm was observed.
- Chrome 133.0.6943.137 initially had a meminfo timeout/unresponsive input channel.
  Android killed cached PID 20491 at **01:01:19.823** for excessive binder traffic;
  this is a resource-usage exit, not an ANR. Standalone MAIN/LAUNCHER launch reported
  Status ok / 12,862 ms but did not become responsive to normal address-bar input.
- **New Chrome am_anr 01:03:49.481**, PID **23449**, MotionEvent timeout 5,004 ms.
  An additional WindowManager focus-timeout warning at **01:03:55.216** belongs to
  that unresponsive process episode. One authorized Chrome-only force-stop/restart
  at 01:04:02 then returned **Status timeout / WaitTime 20,055 ms**, followed by
  **new Chrome am_anr 01:04:59.188**, PID **23782**, focus timeout 5,034 ms.
  Two am_anr incidents across two processes; duplicate report lines are not extra
  incidents. The first exit's force-stop label does not erase its recorded ANR.
- Both failures occurred on Android-shell standalone Chrome launches, **before
  any Kinetic interaction or HTTPS handoff**. Persistent Chrome/environment
  instability is demonstrated; exact internal root cause remains unresolved.
  Kinetic dispatch is not necessary to reproduce it. September 9's precise cause
  and overlapping Kinetic timeout are not retroactively explained or exonerated.
- Healthy-Chrome prerequisite failed, so control HTTPS navigation and Kinetic
  baseline/Local/approval/rotation/no-replay/Debug gate were **NOT RUN**. New Kinetic
  dispatches **0**; this is not a new accepted-action exactly-once pass. Final
  foreground is Launcher. No owner acceptance is inferred.
- Installed and built APK hash still
  `D0438351DB6E9EBA22459C1466256999D4D4E49487E43A888AA8062DC4C0A466`;
  fresh signature verification of the identical artifact confirms
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
  No source change, build, install or test rerun. Existing XML recounted **244
  passed / 0 failures/errors/skips**; prior lint **0 errors / 5 unchanged warnings**.
- Before/after Room **v6 / quick_check ok**, **15 sessions / 98 messages / 9 memories /
  1 summary / 6 approvals / 10 effects**. Original history and memory/summary hashes
  match prior evidence. Provider preferences retain original SHA-256
  `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`;
  CLOUD and encrypted ciphertext/IV remain present. Cloud was never switched;
  prior Cloud Ready was not freshly visually certified. No decryption/provider call.
- New ANRs: Kinetic **0**, Chrome **2 am_anr incidents** plus the noted same-process
  WindowManager warning, System UI **0**, Launcher **0**, system/process-system **0**,
  other Android **0**. Bounded audit: 155,512 retained lines / 8,615 window lines /
  7 Kinetic PID lines; fatal, Room/migration, credential/header and Kinetic startup
  failure markers **0**. Direct Chrome launch timeout remains a failure despite
  zero matched process-start-timeout log patterns. Logs were not cleared.
- No data/cache clear, uninstall, AVD restart/wipe/recreation/move, snapshot deletion,
  model download, Windows audio change or Kinetic patch. Only diagnostic report and
  this roadmap changed; **roadmap.md was the final workspace modification**.

Phase 5A remains **IMPLEMENTED; AUTOMATED GATE PASSED; DEVICE GATE BLOCKED**.
Phase 4C ACCEPTED; Phase 4 COMPLETE; Phase 5 IN PROGRESS; Phase 5B/6 NOT STARTED.
Separate Astra/Stellar engineering passed / owner acceptance pending remains unchanged.

### Phase 5A foundation — 2026-09-09–10

**IMPLEMENTED; AUTOMATED GATE PASSED; DEVICE GATE BLOCKED.** See
[architecture and dated primary research](docs/architecture/PHASE5_ON_DEVICE_AI.md)
and [complete engineering/device evidence](docs/PHASE5A_ENGINEERING_VERIFICATION.md).

The owner's Phase 5A brief explicitly accepted repaired Phase 4C: SQLite ACTIVE/current,
PostgreSQL SUPERSEDED/history, fresh-conversation recall and Context Inspector select
SQLite, and recall survives force-stop/relaunch. **Phase 4C ACCEPTED; Phase 4 COMPLETE.**
Acceptance reconciliation was recorded in the architecture checkpoint before source
work, with this authoritative roadmap updated last. Phase 5A alone was authorized;
separate Astra/Stellar owner UX/provider/tool acceptance is not inferred.

- Seven-suite final XML: kernel JVM **116**, model JVM **49**, capability JVM **12**,
  app JVM **16**, Room Android **20**, model/security/probe Android **14**, capability
  Android **17** — **244 passed, 0 failed, 0 errors, 0 skipped** (24 above 220).
- Safety guard passed; all 51 Android tests executed in library-owned packages.
  No production-app instrumentation. Lint **0 errors / 5 unchanged warnings**;
  `assembleDebug` **BUILD SUCCESSFUL**. Initial new recovery test-fixture misuse
  was corrected to construct an idle runtime; production recovery was unchanged.
- Signer continuity verified against installed and built APKs:
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
  `adb install -r` succeeded September 9 11:42:45, preserving first-install time
  September 1 11:51:15. Installed and built APK SHA-256 both
  `D0438351DB6E9EBA22459C1466256999D4D4E49487E43A888AA8062DC4C0A466`;
  version name remains `0.4.4-astra-stellar`.
- Full Room dump and provider preferences matched immediately across the update.
  Final Room v6 / quick_check ok: **15 sessions, 98 messages, 9 memories, 1 summary,
  6 approvals, 10 effects**. Original 90-message and memory/summary hashes remain
  unchanged; original provider-preferences hash restored exactly with **Cloud Ready**.
  No key export, real provider request, clear/uninstall, AVD reset or network/audio change.
- Same `Kinetic_API_36`, x86_64, boot
  `f7e45119-59cb-4eb9-aa7d-7b21e7f510ac`. Local selection/streaming and Debug probe
  work. Protected-demo Reject persisted without execution; URL approval remained
  pending across rotation and Home/return. One Kinetic URL dispatch at
  September 9 **11:48:00.690**, truthful durable completion/continuation, and no
  replay on return or force-stop/relaunch. Rotation settings restored to 1/1.
- **Exact blocker:** new **Chrome ANR**, PID 18857, `am_anr` **11:48:37.842**,
  focus-event input timeout; trace 11:48:38.373, exit 11:48:58.314. Kinetic relaunch
  returned adb **Status: timeout** (15,139 ms wait), then displayed at **11:49:06.546**
  (+25,382 ms) and recovered its completed state. No Kinetic-owned cause is proved;
  no source patch was made for this incident. Do not claim a clean device pass.
- New Kinetic/System UI/Launcher/system_server ANRs found: **0 each**. Chrome: **1**
  new incident, separate from retained historical failures. Final owned fatal and
  Room error markers **0**, window credential/header markers **0**. Logs were not
  cleared; interrupted overnight gaps were not continuously monitored.
- No real local model/performance runner, model download, NDK/JNI, AI Pack delivery,
  permission/schema change or automatic local/cloud routing. Emulator facts are not
  physical ARM64 model-performance evidence. Phase 5B and Phase 6 remain unstarted.

The dated entries below are historical snapshots. Their earlier Phase 4 acceptance,
Phase 5 authorization and environment-blocker instructions are superseded by the
current table, this checkpoint and section 21, not silently rewritten as past passes.

### Final installed-build retry — 2026-09-08–09, ENGINEERING DEVICE GATE PASSED

**ENGINEERING DEVICE GATE PASSED; OWNER ACCEPTANCE PENDING.** Read
[the complete retry evidence](docs/ASTRA_DEVICE_RETRY_20260908.md) and
[the current engineering boundary](docs/ASTRA_ENGINEERING_VERIFICATION.md).
This result supersedes the older blocked/resume instructions below without
erasing their historical ANRs or retrospectively claiming those attempts passed.

- Same already-running `Kinetic_API_36`, boot ID
  `f7e45119-59cb-4eb9-aa7d-7b21e7f510ac`. The owner had recovered C: storage and
  the running emulator already used `-no-snapshot -feature -QuickbootFileBacked`.
  This retry did not restart, wipe, recreate or move the AVD/SDK/project.
- Start window: September 8 **01:40:31.899 PKT**. Available host RAM **3,752,096 KiB**
  (3,664.16 MiB); C: free **22,753,083,392 bytes**. Final sample September 9
  00:34:43: RAM **2,485,896 KiB**, C: **23,453,159,424 bytes**. Launcher/System UI
  and system_server were responsive before engineering launch. Interrupted gaps
  are not claimed as continuously monitored UI time.
- A reproducible Kinetic-owned defect was proved twice: landscape docked IME
  left insufficient height for fixed header/composer/actions. Only
  `KineticDeveloperScreen.kt` and `StellarPresentationTest.kt` changed. Short
  viewports now scroll with a bounded message list; normal portrait, provider,
  governance, Room and capability behavior were not changed. Live retest reached
  the text and full Send button above IME; keyboard close and multiline worked.
- Final seven-suite baseline: kernel JVM **95**, model JVM **47**, capability JVM
  **12**, app JVM **16**, Room Android **20**, model/security Android **13**,
  capability Android **17**: **220 passed, 0 failed, 0 errors, 0 skipped**.
  Changed app JVM and all 50 Android tests executed; unchanged JVM suites were
  UP-TO-DATE with passing XML. Safety guard passed; no production-app instrumentation.
  Lint **0 errors / 5 unchanged warnings**; `assembleDebug` **BUILD SUCCESSFUL**.
- Corrected installed version remains **0.4.4-astra-stellar**, APK SHA-256
  `5E39D26290350AD45D7B3D4FD3C5EEBC90E083362EFB7E042A1E0057C2C2D35F`.
  Required signer verified before update:
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
  `adb install -r` **Success**; installed hash equals verified build. First install
  stays September 1 11:51:15; update September 8 12:05:56. No uninstall, data clear,
  replacement key, signing bypass, application-ID or permission change.
- Corrected cold launches: **7,565 / 10,375 / 6,621 ms**, all Status ok and usable;
  warm external-task return **742 ms**. No new Kinetic startup timeout/ANR/crash.
  Explicit light/dark/system/dynamic and system bars, portrait/landscape,
  medium/expanded Memory/Context/Debug navigation, IME/multiline and bounded 200%
  text/scroll-reachability checks passed. Full owner TalkBack remains pending.
- One Fake `https://example.com` request stayed WAITING_FOR_APPROVAL across
  background/foreground and rotation. One Approve produced one Kinetic UID-10219
  URL dispatch at September 8 **12:15:12.551**. Chrome's UID-10160 internal tab
  handoff is not a duplicate. Warm return, Activity recreation and force-stop/
  relaunch did not replay. Durable result: **HTTPS URL opened through Android.**
  Chrome page completion is not claimed; no real provider request was made.
- Full DB and provider hashes matched immediately across update. Final Room **v6**,
  quick_check **ok**; sessions **11**, messages **84**, turns **23**, memories **9**,
  summaries **1**, approvals **4**, effects **8**. Original 80-message and full
  memory/summary hashes match baseline; only one engineering turn was added.
  Cloud restored; provider preferences match original SHA-256
  `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
  No owner-memory rewrite or credential export. All temporary display/input
  settings restored; Kinetic left open in portrait with Cloud Ready.
- Bounded final audit: **0 new Kinetic ANRs**, **0 newly detected System UI,
  Launcher, system_server/process-system or Chrome ANRs**. Historical Kinetic
  startup ANRs/timeouts remain. System UI PID 5170's delayed report/termination
  at 01:40:42–43 belongs to its **pre-window 01:40:22 detection/trace**, not a new
  event. This distinction is documented with exact evidence, not suppressed.
  No log-clear command was run. Window-scoped Kinetic fatal, initialization-timeout,
  Room/SQLite/migration-error and credential/Authorization/Bearer marker counts
  were zero. This is a bounded audit, not an unrestricted security guarantee.

Phase 4C remains **IMPLEMENTED — OWNER GOVERNANCE ACCEPTANCE PENDING**. Owner Part B
is accepted; repaired Part A was not performed on the owner's behalf. Phase 4
**IN PROGRESS**; Phase 5 **NOT STARTED**. Exactly one next recommended task is the
owner's existing **Flow 3 — Stellar / memory** in
[ASTRA_OWNER_ACCEPTANCE.md](docs/ASTRA_OWNER_ACCEPTANCE.md), followed by reporting
pass/fail for acceptance reconciliation. It was not begun; other owner flows are
not silently accepted. This roadmap update is the final project/workspace edit.

### Historical bounded AVD root-cause diagnostic — 2026-09-07

**ENVIRONMENT BLOCKED — ROOT-CAUSE DIAGNOSTIC COMPLETED.** Primary classification:
**F. HOST MEMORY / PAGEFILE / DISK PRESSURE**, specifically disk/I/O starvation;
confidence **MEDIUM**. The exact source of all disk traffic remains unisolated;
graphics load is an observed additional contributor, not a proven sole cause.
See [the full diagnostic report](docs/AVD_STARTUP_ROOT_CAUSE_DIAGNOSTIC.md) and
[the updated engineering boundary](docs/ASTRA_ENGINEERING_VERIFICATION.md).

- Windows 10 Pro 19045; i5-4690, four logical CPUs; installed RAM 12 GiB,
  OS-visible 12,241 MiB. Host commit remained about 11.5–11.7 / 18.48 GiB during
  the busy real-AVD boot. WHPX was installed, usable and actually active.
- Same `Kinetic_API_36`, same API 36 revision-7 x86_64 image, unchanged config,
  normal userdata. One diagnostic cold launch at 12:06:30 PKT; ADB online observed
  12:07:24, boot complete observed 12:09:23. System UI failed startup at 12:08:42.
  The retained new-window sample contains 28 ANRs: System UI 1, other Android
  components 27; Kinetic starts/ANRs 0, Launcher/system_server/Chrome ANRs 0.
  Historical failures are separate. No Kinetic launch or provider request occurred.
- Early composer CPU saturation gave way to sustained guest 80–82% I/O wait and
  I/O full PSI avg10 up to 73.15%. The AVD/SDK and mapped guest-RAM backing file
  reside on E:'s HDD. Host memory availability alone did not make Android healthy.
- The one temporary `-gpu host` comparison failed before ADB during graphics
  initialization. It had no closable window/ADB; only its verified diagnostic
  process was stopped. No renderer/config/driver change was made permanent.
- One separate `Kinetic_Diagnostic_Control_20260907` AVD used the same image and
  Pixel 7 defaults with no Kinetic/data copied. It remained ADB-offline for 328
  seconds: no healthy control established, not a measured zero-ANR pass. HDD queue
  reached 102 and transfer latency 415 ms despite over 4 GiB RAM available. After
  normal shutdown stalled, only the verified control processes were stopped;
  control files remain. No real-AVD replacement or snapshot deletion occurred.
- No simultaneous owner-confirmed Realtek popups were reported for this window;
  the conditional no-audio trial was not run. Windows/audio settings unchanged.
- Room and provider hashes matched before/after the real baseline boot, with
  zero-length WAL. No app/source/build/signing/install/data-clear operation occurred.
  The prior signer remains `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`;
  an additional read-only signer recheck stalled and is not claimed as a new pass.
  Prior 219 passed / 0 failures/errors/skips and lint/build results were not rerun.

**Exactly one next recommended repair, NOT EXECUTED:** authorize one temporary
same-AVD cold launch with `-no-snapshot -feature -QuickbootFileBacked -verbose`,
preserving default renderer, RAM/cores, userdata and snapshot files. This targets
the evidenced HDD-backed guest-RAM path; its benefit remains unproven. Verify no
`-mem-path` in the resulting QEMU command and healthy Android before Kinetic use.
Do not apply this repair without owner authorization. No further repair is started.

The final device gate is still blocked, not engineering-passed or owner-accepted.
Phase 4C remains **IMPLEMENTED — OWNER GOVERNANCE ACCEPTANCE PENDING**.
Phase 4 remains **IN PROGRESS**; Phase 5 remains **NOT STARTED**.
This diagnostic supersedes the older next-task instructions below; their historical
evidence is retained. `roadmap.md` is the final workspace modification for this task.

### Controlled same-AVD restart — 2026-09-07, system instability persists

**ENVIRONMENT BLOCKED — AVD / ANDROID SYSTEM INSTABILITY PERSISTS.** The requested
normal shutdown/restart completed, but new Android system ANRs appeared before Kinetic
was exercised. The installed-build gate was stopped; no source change was made.
See [the process-specific restart evidence](docs/ASTRA_ENGINEERING_VERIFICATION.md).

- Host physical RAM: **4,302 MiB available** of 12,241 MiB at 04:39:36 PKT,
  **4,575 MiB** during boot at 04:43:16. Current sampled host RAM was not critically low.
- Confirmed the same `Kinetic_API_36` at
  `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd`; AVD locator/config hashes
  remained unchanged. Normal emulator-console shutdown succeeded, old emulator/QEMU
  PIDs 14552/17980 terminated, and ADB listed no device before restart.
- Restart at **04:40:34.751 PKT**, new emulator/QEMU PIDs 19544/18176, used
  `-avd Kinetic_API_36 -no-snapshot-load`: fresh Android boot without deleting snapshots
  or userdata. No AVD recreation, wipe, uninstall, data clear, signer/application-ID
  change, audio change or unrelated host-app closure occurred.
- New boot ID `f296d4db-c2e2-4d71-8b6e-4acb8c3197a0` differs from previous
  `695af1d2-ddcb-4039-ad19-a2af5737a018`. ADB was online by 04:43:30; boot-complete
  was observed at 04:45:43. Boot completion is not a healthy-system acceptance result.
- **New System UI ANR at 04:44:56.292**, PID 1086, failed to complete startup.
  Ten additional new ANRs through 04:46:26 affected Phone (two), Settings,
  permissioncontroller, wellbeing, Play services, keyboard, media provider, android.as
  and search interactor. System_server PID 787 later answered service queries and
  Launcher was top-resumed, but System UI startup failed and the environment remained
  unsuitable for exercising Kinetic. Underlying host/emulator/Android cause is unproven.
- The 1,046-line new-boot event sample through 04:46:26 contained: **Kinetic ANRs 0,
  System UI ANRs 1, Launcher ANRs 0, system/process-system ANRs 0, Chrome ANRs 0,
  other Android ANRs 10**. Kinetic process starts were **0**, with no Kinetic PID.
  Zero Kinetic ANRs is therefore not an app-startup pass. Historical Kinetic startup
  and 03:16:06 input-dispatch ANRs remain recorded separately, not counted as new.
- Installed version remains `0.4.4-astra-stellar`, first install September 1 11:51:15,
  last update September 6 12:45:41. On-device APK hash remains
  `B4EB0D7019DDE57914C4CB2F9195D8DD79E4529BAD633126E593DC8C784DADE5`;
  its matching pulled artifact reverified certificate
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
- Before/after raw database SHA-256 matches
  `424997FC54F4501AE2A6FD271F900B2486ABF4B86252E8E770BA9F3771111D73`, with empty
  WAL at both samples. Stored database contents are byte-for-byte preserved across
  restart; no new relational-count/integrity/usability check is claimed.
- Provider settings are byte-for-byte preserved at
  `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`;
  CLOUD, ciphertext and IV remain present. No credential value was printed, altered
  or sent to a provider; decryptability/use is not newly verified.
- A bounded 11,638-line main/system/crash scan had zero matched Kinetic fatal/startup,
  Room/SQLite/migration and credential/header markers. This does not negate the new
  event-buffer ANRs. No logs were cleared. All startup/visual/adaptive/IME/accessibility/
  approval/no-replay flows remain **NOT RUN — POST-RESTART ENVIRONMENT BLOCKED**.
- No source/config change, build, install, instrumentation, public provider request,
  owner memory write or owner acceptance occurred. Prior **219 passed, 0 failed/errors/
  skipped** and lint **0 errors, 5 warnings** remain historical, not rerun results.

The unnumbered gate stays **IMPLEMENTED — FINAL DEVICE GATE BLOCKED BY ENVIRONMENT;
OWNER ACCEPTANCE PENDING**. Phase 4C remains **IMPLEMENTED — OWNER GOVERNANCE
ACCEPTANCE PENDING**, Phase 4 IN PROGRESS and Phase 5 NOT STARTED. Engineering
documentation was updated first; roadmap.md was the final workspace modification.

### Installed-build device preflight — 2026-09-07, ENVIRONMENT BLOCKED

The requested stability/visual/no-replay gate stopped at its explicit environment
fail-closed rule, before controlled launches or UI interaction. Full evidence is in
[the updated engineering checkpoint](docs/ASTRA_ENGINEERING_VERIFICATION.md).

- At 04:28:24 PKT Windows had **4,658 MiB available physical RAM**, 12,241 MiB total,
  load 61%. Host RAM was not critically low at this sample. `Kinetic_API_36`, serial
  `emulator-5554`, reported boot complete, but Android was not healthy enough to test.
- Retained events identify a **Kinetic-owned input-dispatch ANR at 03:16:06.034**,
  PID 3320, before this preflight. Launcher ANRs occurred at 03:15:28, 03:18:40 and
  04:27:40; system-process ANRs at 03:17:34, 03:19:31 and **04:28:23.308**. The last
  system timeout concerned focus on Kinetic's ANR dialog; its owner was system PID 801,
  not Kinetic/System UI. A historical Chrome startup ANR remains visible from September 6.
  Multiple dumpsys probes were uninformative and a stalled screenshot probe was cancelled.
- The bounded event sample contained Kinetic ANR 1, Launcher ANR 3, system ANR 3,
  Chrome ANR 1, System UI ANR 0 and Kinetic crash 0. These are retained-log counts,
  not a clean new test window. Root cause is unresolved; current host RAM does not
  prove either an app-only defect or memory pressure as the sole cause.
- The actual installed APK was pulled and freshly inspected: version
  `0.4.4-astra-stellar`, SHA-256
  `B4EB0D7019DDE57914C4CB2F9195D8DD79E4529BAD633126E593DC8C784DADE5`, signer
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
  Permissions remain INTERNET plus the app-scoped signature permission; no microphone.
- Read-only provider inspection confirms CLOUD, ciphertext and IV present, and unchanged
  original preferences SHA-256
  `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
  No credential value was printed, altered, copied between providers or sent to a provider.
  Room's existing file is present; relational counts, memory/summary preservation and
  integrity were not freshly verified. No owner records were deliberately rewritten.
- A 10,474-line main/system/crash sample had zero matched Kinetic fatal-process/start-
  timeout, Room/SQLite/migration and credential/header markers. This bounded result
  does not negate the event-buffer ANRs or establish a clean device/security gate.
  Logs were not cleared. Windows audio correlation was not established; no host audio,
  registry, driver or device setting was modified.
- No controlled startup, theme, adaptive, keyboard/IME, large-text, approval or replay
  flow ran. No source/config change, rebuild, reinstall, instrumentation, wipe, data clear,
  host-app closure, provider call or owner acceptance was performed. Existing repair
  tests/artifacts remain present. Prior automated baseline remains **219 passed,
  0 failed/errors/skipped**, lint **0 errors/5 warnings**; no rerun is claimed.

Current gate: **IMPLEMENTED — FINAL DEVICE GATE BLOCKED BY ENVIRONMENT;
OWNER ACCEPTANCE PENDING**. Phase 4C remains owner-governance acceptance pending;
Part B passed and repaired Part A remains owner-controlled. Phase 4 is IN PROGRESS;
Phase 5 is NOT STARTED. Documentation was updated before roadmap.md; roadmap.md was
the final workspace modification for this attempt.

### Continuity reconciliation — 2026-09-07 (no implementation)

The conditional Phase 5A brief was reconciled against this complete roadmap and the
actual checkout. The preceding acceptance gate is still unsatisfied: Phase 4C is
**IMPLEMENTED — OWNER GOVERNANCE ACCEPTANCE PENDING**, and the unnumbered Astra/Stellar
final installed-build device gate remains incomplete. No new functional work was authorized
or started. Existing provider/UI implementation was not duplicated or relabeled.

- Read-only inspection confirmed the legacy-governance repair in both repositories:
  adoption requires a future explicit, strictly parseable, same-subject/scope transaction;
  replacing an existing target value supersedes other current alternatives. Conflict and
  lower-authority protections remain. Kernel regressions and the Room reopen regression
  are present. Part B owner acceptance remains passed; repaired Part A still needs owner retest.
- Recounted the existing XML results: **219 passed, 0 failed, 0 errors, 0 skipped**
  (kernel JVM 95, model JVM 47, capability JVM 12, app JVM 15, Room Android 20,
  model/security Android 13, capability Android 17). Existing lint XML reports confirm
  **0 errors, 5 warnings** (app 3, model 1, persistence 1, capabilities 0).
  These are inspected prior-run artifacts, not a new test/lint/build run; the older
  181-test repair checkpoint is not the latest baseline.
- No Git repository exists here, so a clean Git diff cannot be certified. No inspected
  Kotlin/build-script/version-catalog/manifest file has a modification timestamp newer
  than the final APK; timestamps are supporting evidence, not a source-content diff.
- Recomputed APK SHA-256 remains
  `B4EB0D7019DDE57914C4CB2F9195D8DD79E4529BAD633126E593DC8C784DADE5`.
  Fresh apksigner verification confirms certificate SHA-256
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
  Device package metadata still shows `0.4.4-astra-stellar`, first install
  `2026-09-01 11:51:15`, and last update `2026-09-06 12:45:41`.
- The emulator is online, boot-complete, and Kinetic is top-resumed. The preceding
  launch-only handoff also displayed Cloud Ready, after dismissing a System UI ANR
  with Wait. Neither observation is a fresh complete startup/visual/no-replay/log gate;
  the recorded ANR history and outstanding checks below remain open.
- No install, rebuild, data clear, uninstall, credential operation, provider request,
  memory mutation, source/config change or new device-test lane was performed in this
  reconciliation. Earlier preservation evidence remains historical; no new Room or
  credential-preservation audit is claimed. Only roadmap.md was changed, last.

Remaining owner governance acceptance, once the outstanding engineering device gate is
healthy: submit `Update my preferred Kinetic test database to SQLite.`; verify SQLite
is current and legacy PostgreSQL is superseded with history retained; ask in a new
conversation, inspect Context Inspector, and repeat recall after force-stop/relaunch.
PostgreSQL must not appear as another current value. Do not delete unrelated memories.
The separately documented unnumbered provider/tool/Stellar owner checks also remain
pending; no acceptance is inferred from this reconciliation or from opening the app.

### Historical checkpoint — Astra / Stellar gate, 2026-09-06

**Implementation and automated/signing/preservation gates pass. The final installed-build
device visual/startup gate is BLOCKED, not complete. Owner acceptance remains PENDING.**
Read [the precise verification and resume checkpoint](docs/ASTRA_ENGINEERING_VERIFICATION.md)
before continuing. The historical verification entries below are retained, not overwritten.

Canonical reconciliation:

- Before this gate, the roadmap and actual five-module checkout established Phase 4A/B accepted,
  Phase 4C owner governance acceptance pending and Phase 5 NOT STARTED. No Phase 5 runtime,
  provider, benchmark or test artifact was found; the owner's reported completion cannot be
  corroborated here. There is no Git metadata in this checkout. No next functional phase began.
- The unfinished narrow legacy-governance repair was present in source. Its pre-gate reports
  established **181 passed, 0 failed/errors/skipped**: kernel 91, model JVM 22, capabilities
  JVM 12, app JVM 10, Room Android 20, model Android 9, capabilities Android 17. This was newer
  than the historical 174-test roadmap entry; the device initially still ran 0.4.2.
- Owner Part B conflict/resolution passed. Owner Part A update/supersession failed because
  legacy ungoverned PostgreSQL remained ACTIVE. The repair is now tested and installed, but
  owner Part A retest remains pending. Only a future explicit compatible USER_EXPLICIT
  governance transaction adopts legacy records. Installation/retrieval does not rewrite them.
  Lower-authority derived duplicates cannot displace explicit alternatives or adopt legacy rows.

Final engineering artifacts:

- **219 tests passed; 0 failed; 0 errors; 0 skipped.** Kernel JVM 95; model JVM 47;
  Android-capability JVM 12; app JVM 15; Room Android 20; model/security Android 13;
  Android-capability Android 17. All three safe library device lanes ran again, serially.
- `verifyDeviceTestSafety` remained enabled. No production-app instrumentation, destructive
  target-package operation or guard bypass was used.
- Four lint tasks passed: **0 errors, 5 warnings** (app 3, model 1, persistence 1, capabilities 0).
  Warnings are OldTargetApi, two UseKtx, ApplySharedPref and KaptUsageInsteadOfKsp. Existing SDK
  XML-version warning remains; no toolchain/target upgrade was performed to silence warnings.
- Final serialized gate: **BUILD SUCCESSFUL in 9m 3s**, 292 actionable tasks, 28 executed and
  264 up-to-date. The preceding 218-test full gate also passed; the final count adds a regression
  for explicit theme/system-bar contrast. `assembleDebug` succeeded with explicit IME resizing.
- APK: `E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk`, version
  `0.4.4-astra-stellar`, 12,901,230 bytes. Built and pulled installed APK SHA-256:
  `B4EB0D7019DDE57914C4CB2F9195D8DD79E4529BAD633126E593DC8C784DADE5`.
- Installed-before and built signing certificates were both verified before `adb install -r`:
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
  Update returned **Success**. First install remains `2026-09-01 11:51:15`; final update is
  `2026-09-06 12:45:41`. No uninstall, data clear, application-ID change, replacement key,
  signing bypass or signing secret was introduced.
- APK permissions remain INTERNET plus the generated app-scoped dynamic-receiver signature
  permission. No dangerous permission, agent service or background autonomy was added.

Persistence and live verification:

- Room remains schema v6 with no new migration. The full logical DB hash matched before/after
  the initial gate install, proving no historical rewrite on installation. After engineering
  echo/approval tests, the full logical DB hash again matched before and after final update
  Success: `0A6F152FD3CDE0FEF4FDAFA50CA1D81331DA0FAE56A62E8739833767E17E4384`.
- Final state: sessions 10, messages 80, turns 22, memories 9, summaries 1, approvals 3, effects 7.
  Added test history was retained; owner rows were not deleted or rewritten. Seven ACTIVE and
  two SUPERSEDED memories remain. The original sorted memory/summary hash is still
  `68C70006F8440FEF3117B47295BB7A41F3AD3ECAF8E1A21F024CDE45C1BE5EDA`.
- Cloud/OpenRouter mode is restored. Provider preferences remain byte-for-byte at original
  SHA-256 `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`, including the
  encrypted credential pair and existing metadata. Compatible UI showed a retained key;
  direct OpenAI showed no stored key. No real OpenAI credential was entered or copied, and
  no real provider request was made by engineering. Synthetic isolated TLS/device tests cover
  separate credentials, blank save/switch/profile/reopen retention and selected-only Clear.
- On the earlier installed gate build, Fake echo/result/continuation persisted through cold
  boot. Browser Reject durably rejected with zero dispatch. A second pending approval survived
  background/foreground and rotation without execution. Approve dispatched once; Chrome became
  foreground and the tool result truthfully described Android opening the URL, not task completion.
- Return, Activity recreation and force-stop/relaunch did not duplicate the effect. Audit found
  one example.com browser START from Kinetic UID 10219; Chrome UID 10160's separate internal
  IntentDispatcher-to-tab START is not Kinetic replay. Final APK update also preserved all effects.
- Light/dark/system selection, 200% font portrait/landscape, scrollable approval details, expanded
  Memory pane, CURRENT/HISTORY/provenance/controls and separate Context/Debug surfaces were
  inspected. Final corrected system bars, stable dynamic/medium-window visual checks and keyboard/
  IME traversal remain outstanding. Full TalkBack usability remains an owner test.
- **Device audit is NOT clean:** process-system/System UI/Pixel Launcher/Chrome ANR dialogs
  occurred. Chrome page loading is not verified. Logcat records one Kinetic startup ANR at
  `01:42:21` (PID 8088, failed to complete startup), before the last UI patch, with severe guest
  memory pressure. Final-update exit-info records process-start timeouts at `12:49:20` and
  `12:53:29`. Windows available RAM measured 367 MB, later 590 MB. Resource pressure is a strong
  suspected contributor, not proof that app defects are excluded. A later Kinetic process became
  top-resumed, but recurring launcher ANR dialogs still interrupted navigation.
- Bounded log audit found 0 Kinetic fatal-process markers, 0 Room/SQLite exception or migration-
  integrity markers and 0 credential-value/Authorization markers. Do NOT report zero Kinetic ANRs
  or a completed final visual/startup gate. No logs were cleared to manufacture a pass.
- Display settings restored: font 1.0, auto-rotation 1, user rotation 1, physical 1080x2400 with no
  size override, density 420; hardware-keyboard IME setting remains 0. No other host apps were closed.

Documentation: [provider/contract/data architecture](docs/architecture/ASTRA_INTEGRATION_GATE.md),
[Stellar/adaptive/input architecture and P0–P3 backlog](docs/architecture/KINETIC_STELLAR_PRODUCT_GATE.md),
[engineering evidence/resume boundary](docs/ASTRA_ENGINEERING_VERIFICATION.md), and
[three owner acceptance flows](docs/ASTRA_OWNER_ACCEPTANCE.md).

This unnumbered gate is **IMPLEMENTED — FINAL DEVICE GATE BLOCKED; OWNER ACCEPTANCE PENDING**.
Phase 4C owner repair acceptance is still pending; Phase 4 stays IN PROGRESS and Phase 5 stays
NOT STARTED. No image/document upload, steering, hosted tools, async execution, embeddings,
arbitrary shell or background autonomy was added. Roadmap.md was updated last for this checkpoint.

### Historical verification entries

Latest Codex verification after Phase 2A implementation:

- **46 tests passed**
- **0 failed**
- **0 errors**
- **0 skipped**
- Kernel JVM: 28
- Model JVM: 9
- App JVM: 5
- Persistence Android: 2
- Model/security Android: 2
- Lint: **0 errors**
- `assembleDebug`: **BUILD SUCCESSFUL**
- Emulator: `Kinetic_API_36`
- APK installs and launches successfully
- Phase 1 → Phase 2A Room migration verified
- Fake provider regression verified
- No dangerous/device-control permissions added

Latest known Phase 2A APK:

```text
E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk
SHA-256:
7CC76BEF8B0D1A368DAF9B37E8B07F23B9C42C2A4916196D91AC31FEA9270AAE
```

Latest Codex verification after Phase 2B implementation:

- **60 tests passed**
- **0 failed**
- **0 errors**
- **0 skipped**
- Kernel JVM: 35
- Model JVM: 15
- App JVM: 5
- Persistence Android: 3
- Model/security Android: 2
- Lint: **0 errors, 13 warnings**
- `assembleDebug`: **BUILD SUCCESSFUL**
- Connected tests: **BUILD SUCCESSFUL** on `Kinetic_API_36` / API 36
- APK install and cold launch: PASS
- Fake safe echo proposal/result/continuation: PASS
- Protected demo WAITING/Reject/repeat/Approve/exactly-one-result/continuation: PASS
- Current-process log audit: 0 fatal exceptions, 0 ANRs, 0 Room failures, 0 credential markers
- Room remains schema v2; no destructive migration added
- Permission surface remains only normal `android.permission.INTERNET`

Latest Phase 2B APK:

```text
E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk
Size: 12,535,908 bytes
SHA-256:
668408E0AC011FC575AAED68332A5B1C3061B092F977E3157649BF5596EC2023
```

Latest Codex verification after Phase 3A implementation:

- **78 tests passed**
- **0 failed**
- **0 errors**
- **0 skipped**
- Kernel JVM: 39
- Model JVM: 17
- Android-capability JVM: 6
- App JVM: 5
- Persistence Android: 5
- Model/security Android: 2
- Android-capability connected tests: 4
- Lint: **0 errors, 14 warnings** across app/data modules
- `assembleDebug`: **BUILD SUCCESSFUL**
- Connected tests: **BUILD SUCCESSFUL** on `Kinetic_API_36` / API 36
- Existing install upgraded successfully with `adb install -r`; no uninstall or data clear occurred
- Installed Phase 2 signer and rebuilt Phase 3A signer: SHA-256 `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`
- Original matching debug key recovered at machine-local `C:\Users\CodexSandboxOffline\.android\debug.keystore`; no signing path or password was committed
- Original first-install timestamp remained `2026-08-28 02:01:46`; Phase 3A update timestamp is `2026-08-30 00:39:21`
- Persisted Phase 2 conversation/history, pending approval recovery, Cloud/OpenRouter metadata, structured-tool setting, encrypted credential fields, and Android-Keystore-backed credential state remained present
- URL Reject stayed inside Kinetic; URL Approve opened Chrome exactly once
- Wi-Fi Settings Approve opened only `Settings$WifiSettingsActivity`
- Share Approve opened Android's chooser and did not select or launch a recipient
- Fake SAFE echo returned `KINETIC_TOOL_OK`; Cloud selection remained usable and was restored after deterministic checks
- Post-force-stop recovery did not redispatch a completed capability
- Crash buffer: empty; 0 fatal exceptions, 0 ANRs, 0 Room/SQLite failures
- Permission surface remains normal `android.permission.INTERNET` plus AndroidX's app-scoped non-exported dynamic-receiver signature permission

Latest Phase 3A APK:

```text
E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk
Size: 12,534,619 bytes
SHA-256:
9F529385D5107002390B91FF91C2A1BC573650B8322D1035B3A68501D34A26A0
```

Latest Codex verification after Phase 3B implementation:

- **89 tests passed**
- **0 failed**
- **0 errors**
- **0 skipped**
- Kernel JVM: 42
- Model JVM: 17
- Android-capability JVM: 10
- App JVM: 5
- Persistence Android: 6
- Model/security Android: 2
- Android-capability connected tests: 7
- Lint: **0 errors, 14 warnings** across app/data modules
- `assembleDebug`: **BUILD SUCCESSFUL**
- Connected tests: **BUILD SUCCESSFUL** on `Kinetic_API_36` / API 36
- Installed Phase 3A signer, rebuilt Phase 3B signer, and post-update installed signer all match SHA-256 `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`
- Existing install upgraded with `adb install -r`; no uninstall, application-id change, data clear, replacement key, or signing bypass occurred
- Original first-install timestamp remains `2026-08-28 02:01:46`; installed version is `0.3.1-phase3b`
- Persisted Room data, conversation history, Cloud/OpenRouter configuration, structured-tool setting, encrypted credential fields, and Android-Keystore-backed credential state remain present
- Clipboard Approve wrote `KINETIC_CLIPBOARD_OK`; a safe paste verified the exact value; Reject did not replace it
- Dialer Approve opened only Google Dialer with `+92 300 1234567` populated; no call was placed; Reject stayed in Kinetic
- Email Approve produced the exact `ACTION_SENDTO`/`mailto:` handoff and resolved specifically to Gmail's external compose activity, but the emulator has zero accounts and Gmail redirected to first-run account setup before showing the populated compose surface
- Email Reject stayed in Kinetic and transmitted nothing
- Completed/rejected Phase 3B records survived force-stop/restart without redispatch
- Phase 2 fake echo and Phase 3A HTTPS/share/Wi-Fi Settings live regressions passed after the update
- Current-process and system log audit: 0 fatal exceptions, 0 ANRs, 0 Room/migration failures, and 0 credential markers
- Permission surface remains normal `android.permission.INTERNET` plus AndroidX's app-scoped non-exported dynamic-receiver signature permission

Phase 3B pre-email-fix APK:

```text
E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk
Size: 12,551,003 bytes
SHA-256:
A6799AC0FA1FED576C8C3961291D5C94394D19393914D005C27FFFCDAEFC690F
```

Latest Codex verification after the Phase 3B email-composer interoperability repair:

- **94 tests passed**
- **0 failed**
- **0 errors**
- **0 skipped**
- Kernel JVM: 42
- Model JVM: 17
- Android-capability JVM: 10
- App JVM: 5
- Persistence Android: 6
- Model/security Android: 2
- Android-capability connected tests: 12
- Lint: **0 errors, 12 warnings**
- `assembleDebug`: **BUILD SUCCESSFUL**
- Connected tests: **BUILD SUCCESSFUL** on `Kinetic_API_36` / API 36
- Root cause: the old `ACTION_SENDTO` URI carried only `mailto:recipient`; Gmail honored the URI recipient but ignored `EXTRA_SUBJECT` and `EXTRA_TEXT`
- Repair: subject and body are now UTF-8 percent-encoded RFC 6068 `mailto:` query fields, with normalized CRLF body lines and the standardized Intent extras retained as compatibility fallbacks
- Only the fixed query keys `subject` and `body` can be emitted; recipient/subject CR/LF validation, bounded input, exact SHA-256 argument binding, `CONFIRM`, and user-controlled Send remain intact
- Installed pre-update, rebuilt, and installed post-update signers all match SHA-256 `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`
- Existing install upgraded with `adb install -r`; no uninstall, application-id change, data clear, replacement key, signing bypass, or new permission occurred
- Original first-install timestamp remains `2026-08-28 02:01:46`; installed version is `0.3.2-phase3b-emailfix`
- Room database/history, Cloud/OpenRouter configuration, securely stored API-key state, and structured-tool setting remained present; Cloud mode was restored after deterministic checks
- Gmail external compose engineering verification: To `test@example.com`, Subject `KINETIC_EMAIL_OK`, and Body `Hello from Kinetic` were all visibly populated; Send was not pressed
- Email Reject stayed in Kinetic; force-stop/restart did not reopen Gmail or redispatch the rejected/completed handoff
- Post-update live regressions passed for fake echo, clipboard exact paste, populated `ACTION_DIAL`, HTTPS navigation, share chooser text, and allowlisted Wi-Fi Settings
- Current-process and system log audit: 0 fatal exceptions, 0 ANRs, 0 Room/migration failures, and 0 credential markers
- Permission surface remains normal `android.permission.INTERNET` plus AndroidX's generated app-scoped non-exported dynamic-receiver signature permission
- Phase 3B status is **ACCEPTED** after the owner successfully repeated the Gmail composer test with the exact recipient, subject, and body populated; no email was sent

Latest Phase 3B email-fix APK:

```text
E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk
Version: 0.3.2-phase3b-emailfix
Size: 12,551,011 bytes
SHA-256:
ADA745FC8BC43A84EF38F093464F856C6CBDB599738C34D9CF27080B8041D013
```

Latest Codex verification after Phase 3C implementation:

- **102 tests passed**
- **0 failed**
- **0 errors**
- **0 skipped**
- Kernel JVM: 42
- Model JVM: 17
- Android-capability JVM: 12
- App JVM: 5
- Persistence Android: 7
- Model/security Android: 2
- Android-capability connected tests: 17
- The authoritative serialized gate passed with one Gradle worker; lint: **0 errors, 12 warnings**; `assembleDebug`: **BUILD SUCCESSFUL**
- Phase 3C availability, foreground-owner replacement, genuine Activity recreation, destroyed/no-foreground rejection, no-handler failure, concurrent-dispatch rejection, return-without-redispatch, and durable audit coverage all passed on `Kinetic_API_36` / API 36
- Built and installed APK signers match SHA-256 `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`
- `adb install -r` succeeded; no uninstall, application-id change, data clear, replacement key, signing bypass, or new permission occurred
- Original first-install timestamp remains `2026-08-28 02:01:46`; installed version is `0.3.3-phase3c-lifecycle`
- The update preserved the existing Room database/history and Cloud/OpenRouter configuration. Immediately after update the prior counts remained sessions 43, messages 160, journal entries 507, turns 55, effects 44, approvals 39; after the live regression the accumulated counts were sessions 53, messages 193, journal entries 609, turns 65, effects 51, approvals 46
- Provider preferences retained SHA-256 `DF4B7FA6EF0E48F69CC2F1165BC7BFAAD154B3D0A7B07B121AA18BF283C2D7AE`; the UI still reported the Android-Keystore-backed key as securely stored, and Cloud mode was restored after deterministic checks
- A pending HTTPS approval survived background/foreground and rotation; approval opened Chrome exactly once; returning to Kinetic, Activity recreation, and force-stop/relaunch did not reopen Chrome or replay the capability
- Live regressions passed for share chooser text `KINETIC_SHARE_OK`, allowlisted Wi-Fi Settings, exact clipboard write/paste `KINETIC_CLIPBOARD_OK`, populated Dialer number `923001234567`, and Gmail compose fields To `test@example.com`, Subject `KINETIC_EMAIL_OK`, Body `Hello from Kinetic`; no target was selected, setting changed, call placed, or email sent
- Existing tool-result semantics remained truthful: Kinetic reports that a chooser, Settings surface, dialer, browser, or email composer was opened, never that the external user-controlled action completed
- Current-process, crash-buffer, exit-history, and ANR audits found 0 fatal exceptions, 0 Kinetic ANRs, 0 Room/SQLite errors, and 0 authorization/API-key leakage markers
- Permission surface remains normal `android.permission.INTERNET` plus AndroidX's generated app-scoped non-exported dynamic-receiver signature permission

Latest Phase 3C APK:

```text
E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk
Version: 0.3.3-phase3c-lifecycle
Size: 12,551,007 bytes
SHA-256:
298EA9CD53D3DB82EE58EB1779508364008B0F4E82925096B47925714B3A4C7B
```

Latest Codex verification after Phase 4A implementation:

- **128 tests passed**
- **0 failed**
- **0 errors**
- **0 skipped**
- Kernel JVM: 58
- Model JVM: 18
- Android-capability JVM: 12
- App JVM: 8
- Persistence Android: 13
- Model/security Android: 2
- Android-capability connected tests: 17
- The authoritative serialized gate passed with one Gradle worker; all four lint tasks passed with **0 errors, 14 warnings**; `assembleDebug`: **BUILD SUCCESSFUL**
- Room migrated explicitly from v3 to v4 and created the app-private `memories` table without destructive fallback. Immediately after update the prior counts remained sessions 54, messages 196, journal entries 622, turns 66, effects 52, approvals 47; memories began at 0.
- Built, pre-update installed, and post-update installed APK signers match SHA-256 `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
- `adb install -r` succeeded; no uninstall, data clear, application-id change, replacement key, signing bypass, or new permission occurred. Original first-install timestamp remains `2026-08-28 02:01:46`; installed version is `0.4.0-phase4a-memory`.
- Existing history and provider configuration survived. Provider preferences retained SHA-256 `DF4B7FA6EF0E48F69CC2F1165BC7BFAAD154B3D0A7B07B121AA18BF283C2D7AE`, the Android-Keystore-backed key remained securely stored, and Cloud mode was restored after deterministic checks.
- Live memory verification passed: explicit USER/PREFERENCE creation, visible provenance, cross-conversation recall, force-stop/relaunch recall, durable deletion, and post-delete non-recall. The temporary acceptance memory was deleted, leaving 0 memory records.
- A focused protected HTTPS rejection remained truthful (`REJECTED` approval and terminal `FAILED/approval_rejected` effect), did not dispatch externally, and did not replay after force-stop/relaunch.
- Current-process, crash-buffer, exit-history, and ANR audits found 0 fatal exceptions, 0 Kinetic ANRs, 0 Room/SQLite errors, and 0 credential/authentication leakage markers.
- Permission surface remains normal `android.permission.INTERNET` plus AndroidX's generated app-scoped non-exported dynamic-receiver signature permission.

Latest Phase 4A APK:

```text
E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk
Version: 0.4.0-phase4a-memory
Size: 12,616,547 bytes
SHA-256:
003C96FA9CC7ECEBB336EC245FE14D8C6726474766468B04EF56D7CEC72A1102
```

Latest Codex verification after Phase 4B implementation work:

- **156 tests passed**, **0 failed**, **0 errors**, **0 skipped**.
- Kernel JVM: 79; model JVM: 21; Android-capability JVM: 12; app JVM: 9; persistence Android: 15; model/security Android: 3; Android-capability connected: 17.
- The authoritative serialized gate passed with one Gradle worker. All four lint tasks passed with **0 errors, 14 warnings**. `assembleDebug`: **BUILD SUCCESSFUL**.
- Room migrated explicitly from v4 to v5 and created `session_summaries` without destructive fallback. Immediately after `adb install -r`, preserved counts remained sessions 63, messages 214, journal entries 662, turns 73, effects 53, approvals 48, memories 0; summaries began at 0.
- Built and pre-update installed signers matched SHA-256 `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`. `adb install -r` succeeded without uninstall, data clear, application-ID change, replacement key, signing bypass, or new permission.
- Query-aware live verification passed: an older PostgreSQL USER preference was selected despite four newer irrelevant USER memories; Fake mode answered PostgreSQL; Context Inspector identified the selected older record using safe metadata.
- Manual SESSION compaction passed through the production UI. A `MODEL_DERIVED` completed summary covered messages 1..2, all original messages remained in Room, ORBIT-742 was recalled using the summary, the inspector reported `Summary used 1..2`, and force-stop/relaunch preserved the summary and recall without creating another summary or replaying an effect.
- All six temporary engineering USER memories were removed through the controlled Memory UI, restoring the memory table to 0. The completed engineering summary and original test conversation remain as inspectable test artifacts.
- After live verification, accumulated counts were sessions 65, messages 242, journal entries 703, turns 80, effects 53, approvals 48, memories 0, summaries 1.
- Logcat and exit-history audits found 0 fatal exceptions, 0 Kinetic ANRs, 0 Room/SQLite errors, and 0 credential markers. Exit history contained only expected package-update, force-stop, and task-removal reasons.
- **Blocking preservation failure:** before provider-mode testing, provider preferences matched preserved SHA-256 `DF4B7FA6EF0E48F69CC2F1165BC7BFAAD154B3D0A7B07B121AA18BF283C2D7AE` and the UI reported a securely stored key. During restoration from Fake to Cloud, encrypted preference entries `provider_api_key_ciphertext` and `provider_api_key_iv` became absent. Non-secret provider metadata remains intact, but the preference hash is now `87B3A444986248ACDC757B2C56363A1D797FACDE783D40105678541E1D9594F0` and the UI reports `API key missing`. No recoverable `.bak` or prior encrypted-preference copy was found. The ciphertext/IV cannot be reconstructed from the old hash; the owner must re-enter the API key directly in Kinetic.
- Provider/API-key preservation is mandatory, so Phase 4B is **BLOCKED — PROVIDER CREDENTIAL RESTORATION REQUIRED** despite green code, tests, lint, build, signer, migration, retrieval, compaction, persistence, and security checks.

Latest Phase 4B engineering APK:

```text
E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk
Version: 0.4.1-phase4b-context
Size: 12,682,142 bytes
SHA-256:
8AE6EE1F82B14B33A8E546E65950483EBBAAEB69D003E24A625283A2C0904AEB
```

Latest Codex verification after the Phase 4B provider credential-retention audit:

- Root-cause classification: **APPLICATION BUG — reproduced and fixed**. Historical automation selected `Use Cloud` at `(520,700)`, inside recorded bounds `[434,672][602,725]`; the explicit Clear control was at `[364,2006][583,2059]` and was not invoked. Independently, a pre-fix Android test reproduced the unintended deletion: any credential decryption failure caused `apiKey()` to remove both ciphertext and IV.
- The repaired store now retains and verifies ciphertext/IV across Fake/Cloud mode changes, blank-key metadata saves, and automatic-compaction setting writes. Decryption requires the existing Android Keystore alias and preserves encrypted material on all failures. `clearApiKey()` is the sole remaining ciphertext/IV deletion caller.
- Provider UI now distinguishes `No key stored`, `Replace API key`, `Stored key retained` for blank input, and explicit `Clear API key`; plaintext is never redisplayed.
- **164 tests passed**, **0 failed**, **0 errors**, **0 skipped**: kernel JVM 79; model JVM 22; Android-capability JVM 12; app JVM 9; persistence Android 16; model/security Android 9; Android-capability connected 17.
- All four lint tasks passed with **0 errors, 9 warnings**. `assembleDebug`: **BUILD SUCCESSFUL**.
- Built APK: 12,779,824 bytes; SHA-256 `501551B610A98FA9373E8A168674E2BEEB926D67243FDACD34028FED1E2419BB`; signer SHA-256 `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
- Before the final device flow, installed and built signers matched and `adb install -r` succeeded. The provider preference hash and Room v5 counts remained unchanged at sessions 65, messages 242, journal entries 703, turns 80, effects 53, approvals 48, memories 0, summaries 1.
- The synthetic UI save displayed the repaired `Stored key retained` state and preferences contained ciphertext/IV but not plaintext. The first app-target instrumentation probe correctly detected that ADB text injection had stored only a partial synthetic marker rather than falsely claiming decryptability.
- **Device-state blocker:** Gradle's experimental `:app:connectedDebugAndroidTest` harness removed the target `dev.kinetic.app` package after the probe. That app-target lane was immediately removed from the project and is not part of the 164-test authoritative gate. The package is currently absent; no fresh reinstall was performed afterward. The previously recorded Room history/configuration are therefore no longer available through the emulator filesystem.
- A byte-for-byte post-uninstall userdata recovery image is preserved at `E:\Projects\.tooling\temp\Kinetic_userdata_after_test_harness_uninstall.qcow2` (2,783,903,744 bytes). No ordinary Room backup or usable disk snapshot exists, and ext4/fscrypt inspection did not recover the deleted database. The real provider key was already absent before this audit; no real credential was entered or exposed during it.
- Phase 4B remains blocked. Do not claim final synthetic force-stop/Clear completion, restored provider credentials, preserved installed data, or owner acceptance until the owner decides whether to continue forensic recovery or authorize a fresh install.

Latest Codex verification after the Phase 4B device recovery and harness-safety gate:

- The bounded backup check found no usable pre-uninstall recovery source. The AVD `default_boot` directory contained only a Quick Boot RAM image dependent on the active post-removal userdata disk; it could not independently restore application-private data. No full pre-removal userdata copy, Room export, or app-data backup was found. No deleted-block carving was attempted.
- The immutable post-removal evidence image remains at `E:\Projects\.tooling\temp\Kinetic_userdata_after_test_harness_uninstall.qcow2`, size 2,783,903,744 bytes, SHA-256 `872A7C1A31A549C50E3A3E85A7A8431E18A6B05EC82BDD86A37196C6711F4167`.
- Exact uninstall cause: AGP 8.13.2's Unified Test Platform configured `AndroidTestApkInstallerPlugin` with `uninstall_after_test: true` for both the production target APK and its instrumentation APK. The experimental `:app:connectedDebugAndroidTest` targeted `dev.kinetic.app`; UTP teardown logged `Uninstalling dev.kinetic.app`. No project script or test invoked `adb uninstall`, `pm uninstall`, or `pm clear`.
- The root `verifyDeviceTestSafety` guard self-tests its destructive-command matcher, rejects production-app instrumentation sources and package-removal/clear commands, and makes every `:app` connected/device Android test fail before UTP execution. Library instrumentation remains enabled under disposable test package identities. A live fail-closed probe left the package absent, produced no UTP log, and preserved the emulator boot ID.
- The authoritative serialized gate passed: **164 tests passed**, **0 failed**, **0 errors**, **0 skipped** (kernel JVM 79; model JVM 22; Android-capability JVM 12; app JVM 9; persistence Android 16; model/security Android 9; Android-capability connected 17). All four lint tasks passed with **0 errors, 9 warnings**. `assembleDebug`: **BUILD SUCCESSFUL**.
- APK SHA-256 remained `501551B610A98FA9373E8A168674E2BEEB926D67243FDACD34028FED1E2419BB`; signer SHA-256 remained `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`. Because the package was absent, the authorized `adb install` was truthfully recorded as a fresh install, not a data-preserving update.
- Fresh initialization passed: Room schema v5 created cleanly; Kinetic cold-launched and remained top-resumed; Memory and Provider panels worked; Fake structured-tool execution and an approved clipboard capability completed truthfully. Historical conversations and memories were not restored or fabricated.
- The live synthetic credential gate passed through the production UI and secure store: save; Cloud/Fake/Cloud switching; blank-key model/base-URL saves; automatic-compaction on/off; force-stop/relaunch; masked retained-key UI; and production-provider decryption against emulator loopback all retained identical ciphertext/IV. Explicit Clear alone removed both entries and set `hasKey=false`. No synthetic plaintext appeared in preferences, Room, journal output, or logcat; no public provider request was made.
- Final audits found 0 Kinetic fatal markers, 0 Kinetic ANRs, 0 Room/SQLite errors, 0 Authorization/Bearer markers, and no production-package uninstall in library UTP logs. The only Kinetic exit record was the intentional force-stop. Manifest permissions remain INTERNET plus AndroidX's generated app-scoped signature permission.
- Non-secret OpenRouter metadata visible in the preserved Phase 3B UI audit artifact was re-entered into the fresh configuration; no credential was restored. The app is ready for the owner to enter the real key only inside Kinetic and then run the combined provider plus retrieval/compaction acceptance flow.

Latest Phase 4B credential-retention APK:

```text
E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk
Version: 0.4.1-phase4b-context
Size: 12,779,824 bytes
SHA-256:
501551B610A98FA9373E8A168674E2BEEB926D67243FDACD34028FED1E2419BB
```

Latest Codex verification after Phase 4C memory-governance implementation:

- Phase 4B owner acceptance is complete: the real API key was entered only in Kinetic, Cloud mode was restored, and the credential survived restart. An older PostgreSQL USER preference outranked at least four newer irrelevant memories and Context Inspector confirmed its selection. Manual ORBIT-742 compaction created a durable summary with explicit coverage while preserving original messages; summary use, recall, and force-stop/relaunch recall all passed.
- Phase 4C introduces deterministic governed-memory identity and transactional lifecycle handling for `ACTIVE`, `SUPERSEDED`, and `CONFLICTED` records. Explicit update/replace preserves bounded history, conservative contradictions retain both values until resolution, and precise forget refuses missing or ambiguous targets.
- Retrieval excludes `SUPERSEDED` records, retains conflicting candidates as untrusted context, and requires the provider to express uncertainty rather than silently choose. Session summaries remain separate derived artifacts and are not governed-memory revisions.
- Memory UI exposes grouped lifecycle history, safe provenance/source prefixes, timestamps, governance-key hash prefixes, edit/replace, conflict resolution, and delete. Context Inspector exposes safe lifecycle/key-prefix diagnostics without raw journal content.
- Room migrated explicitly from v5 to v6, adding nullable governance and revision-link columns plus a governance index without destructive fallback. Migration, existing-row preservation, provider-preference preservation, and process-reopen governance tests passed.
- The authoritative serialized gate passed: **174 tests passed**, **0 failed**, **0 errors**, **0 skipped** (kernel JVM 85; model JVM 22; Android-capability JVM 12; app JVM 10; persistence Android 19; model/security Android 9; Android-capability connected 17). The production-app instrumentation lane remained blocked by the fail-closed device-safety guard.
- All four lint tasks passed with **0 errors, 9 warnings** (app 7; persistence 1; model 1; Android capabilities 0). `assembleDebug`: **BUILD SUCCESSFUL**.
- Final APK: version `0.4.2-phase4c-governance`, 12,829,511 bytes, SHA-256 `324221B6EA7856D805C83A3A07FE03AD13ECC1043060961117C97FB36CF12798`, signer SHA-256 `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
- The pre-update installed APK and final APK signers matched exactly. Final `adb install -r` returned `Success`; no uninstall, data clear, application-ID change, replacement key, signing bypass, or new permission occurred. The installed APK hash matches the built APK hash.
- Cold launch completed Room v5→v6 migration and left Kinetic top-resumed. Final preserved state is sessions 4, messages 55, journal entries 107, turns 15, effects 4, approvals 1, memories 5, summaries 1, governed engineering records 0. Provider preferences retain SHA-256 `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`; ciphertext and IV entries remain present, and Cloud/provider configuration remains intact.
- Bounded live governance smoke passed: explicit database update made SQLite active and PostgreSQL superseded; an editor contradiction preserved both values as conflicted; choosing IntelliJ resolved the conflict; force-stop/relaunch preserved the resolved state. All temporary governance records were then deleted through the Memory UI, restoring the original five memories. No capability replay occurred and no public provider request was made.
- Final audits found 0 fatal exceptions, 0 Kinetic ANRs, 0 Room/SQLite errors, 0 migration-verification failures, 0 credential-field markers, 0 Authorization/Bearer markers, and 0 OpenRouter key-prefix markers in logcat. Permissions remain INTERNET plus AndroidX's generated app-scoped signature permission.
- Phase 4C is **IMPLEMENTED — OWNER GOVERNANCE ACCEPTANCE PENDING**. Phase 4 remains in progress until the single combined owner governance flow passes. Phase 5 is **NOT STARTED**.

## Phase 2A live-test status

**OWNER ACCEPTED.** The project owner completed the real OpenAI-compatible cloud-provider flow and confirmed genuine conversation, multi-turn context, cancellation, and durable behavior. Phase 2B was subsequently authorized explicitly.

## Phase 2B live-test status

**OWNER ACCEPTED.** The project owner completed the real structured-tool acceptance:

- safe structured echo: **PASS**
- protected tool Reject: **PASS**
- protected tool Approve with exactly one successful result and cloud continuation: **PASS**
- prompted claim that approval already existed did not bypass `CONFIRM`: **PASS**

Phase 2 is complete.

## Phase 3A live-test status

**OWNER ACCEPTED.** The project owner completed the native-capability acceptance:

- HTTPS Approve: **PASS** — browser opened `https://example.com` exactly once
- HTTPS Reject: **PASS** — browser did not open
- `share_text` chooser: **PASS** — chooser showed `KINETIC_SHARE_OK`, selected no recipient, and sent nothing
- `open_settings(WIFI)`: **PASS** — Wi-Fi Settings opened and Kinetic changed no setting

## Phase 3B device-gate status

**ACCEPTED.** Preserve the initial owner-observed defect history:

Phase 3B email acceptance initial attempt:

- Gmail configured successfully
- recipient populated: **PASS**
- subject populated: **FAIL**
- body populated: **FAIL**

The interoperability repair and all automated/device engineering gates pass. The owner then repeated the Gmail test successfully: To `test@example.com`, Subject `KINETIC_EMAIL_OK`, and Body `Hello from Kinetic` were populated exactly, and no email was sent. Phase 3B is accepted.

## Phase 3C device-gate status

**ACCEPTED.** The owner completed the lifecycle acceptance flow: pending approval survived background/foreground and Activity rotation/recreation; the capability did not execute before approval; Chrome opened exactly once after approval; returning to Kinetic caused no duplicate dispatch; and force-stop/relaunch caused no replay. Phase 3 is **COMPLETE**.

## Phase 4A device-gate status

**ACCEPTED.** Owner results:

- explicit USER/PREFERENCE creation: **PASS**
- cross-conversation recall: **PASS**
- force-stop/relaunch recall: **PASS**
- durable deletion: **PASS**
- post-delete fresh-conversation non-recall: **PASS**

## Phase 4B device-gate status

**ACCEPTED.** The owner restored the real provider credential only inside Kinetic and confirmed restart retention. Query-aware retrieval selected the older relevant PostgreSQL memory over at least four newer irrelevant memories with Context Inspector confirmation. Manual ORBIT-742 session compaction preserved original history, exposed summary use, recalled the codename, and survived force-stop/relaunch.

## Phase 4C device-gate status

**ACCEPTED.** Owner Part B conflict/resolution previously passed. The Phase 5A brief
now explicitly confirms repaired Part A: explicit SQLite update, ACTIVE/current
SQLite, SUPERSEDED/history PostgreSQL, correct new-conversation recall and Context
Inspector selection, and force-stop/relaunch recall. Historical failed Part A and
the narrow repair remain documented; the owner retest closes Phase 4. This does not
close separate Astra/Stellar owner UX/provider/tool acceptance.

---

# 1. PRODUCT DEFINITION

Kinetic is a **native Android autonomous/multimodal AI-agent framework**.

It is not a Linux compatibility layer and must not require:

- Termux
- PRoot
- an embedded Linux distribution
- Python as the core runtime
- Node.js as the core runtime

Production architecture should use:

- Kotlin / Android APIs
- Jetpack / Android platform primitives
- selective NDK/Bionic components only where technically justified
- model-provider abstractions independent of any one vendor
- deterministic policy and approval outside model authority

Long-term product goal:

> Build a native Android agent runtime capable of reasoning, memory, approved tool use, Android-native capabilities, multimodal context, cloud/local/hybrid inference, interoperable protocols, and safe public distribution.

---

# 2. ARCHITECTURAL INVARIANTS

These rules are project-level constraints and may not be weakened by later phases.

## 2.1 Model / execution separation

> **Models propose actions; deterministic Kinetic policy authorizes execution.**

The model must never directly:

- execute Android APIs
- bypass `ToolRegistry`
- bypass policy
- bypass approval
- bypass effect-ledger durability
- promote its own permissions
- silently authorize stale actions

## 2.2 Core portability

`:core` / `:core:kernel` should remain Android-independent where avoidable.

Android-specific implementations belong behind ports/adapters.

## 2.3 Process-death safety

Android process death must never silently replay a side effect.

For uncertain interrupted execution:

- fail closed
- journal interruption
- require explicit user retry/new action
- never infer approval from restart state

## 2.4 Play distribution boundary

The Play-distributed artifact must not depend on autonomous Accessibility-based UI clicking as its foundational execution architecture.

## 2.5 Storage boundary

Do not architect Kinetic around `MANAGE_EXTERNAL_STORAGE`.

Use scoped/user-selected/platform-supported storage.

## 2.6 Dynamic executable code boundary

Do not design production Kinetic around downloading and executing arbitrary:

- DEX
- JAR
- `.so`
- APK/plugin bytecode

Runtime extension should use bounded schemas/protocols/IPC rather than untrusted executable-code loading.

## 2.7 Native-code boundary

NDK/Bionic is a **performance / systems-integration layer**, not a privilege-escalation mechanism.

## 2.8 JavaScript boundary

An optional embedded V8/JS runtime may be researched later.

It must not be treated as equivalent to Node.js and must not become a hidden Linux/Node compatibility layer.

## 2.9 Provenance

Every adapted/copied external component must retain source and license provenance.

GitHub availability does not equal legal reuse permission.

---

# 3. COMPLETED FOUNDATION

# Phase 0 — Ecosystem Archaeology

**Status: COMPLETE**

Purpose:

- investigate existing agent runtimes
- identify Android-specific constraints
- identify reuse opportunities
- identify licensing/provenance boundaries
- define native-Kinetic architecture rather than cloning a desktop/Linux agent

Research files under:

```text
Kinetic/research/
```

must remain evidence/reference material, not runtime dependencies.

## ECO_REFERENCE archaeology

A dedicated analysis is being/has been requested for the repositories under:

```text
E:\Projects\Eco_reference
```

Expected report:

```text
Kinetic/research/ECO_ANALYSIS.md
```

The analysis must remain advisory. It does not automatically authorize code reuse.

Before reuse:

```text
reference source
      ↓
license/provenance review
      ↓
architectural-fit review
      ↓
copy / adapt / clean-room rewrite decision
```

The archaeology should map references against these planned Kinetic surfaces:

1. Native Agent Kernel
2. Model Router
3. Policy + Approval Kernel
4. Tool Registry
5. Durable Session/Event Journal
6. Android Service Manager
7. Native Android Capability Layer
8. JNI/Bionic Native Node
9. MediaProjection Context Engine
10. MCP Adapter/Host
11. Partner-App IPC/Extension SDK
12. AppFunctions Adapter
13. On-device SLM Runtime
14. Optional Embedded JavaScript/V8 Runtime
15. Play vs Lab Distribution Boundary

---

# Phase 1 — Native Agent Kernel

**Status: COMPLETE**

Implemented foundation:

- native Android Compose shell
- pure-Kotlin/JVM agent kernel
- provider abstraction
- deterministic `FakeModelProvider`
- structured `ToolCall`, `ToolResult`, `AgentMessage`, approval/error contracts
- allowlisted `ToolRegistry`
- capability/risk metadata
- deterministic `ApprovalGate`
- explicit state machine:

```text
IDLE
THINKING
WAITING_FOR_APPROVAL
EXECUTING
COMPLETED
FAILED
CANCELLED
```

- session isolation
- event journal abstraction
- typed errors
- cancellation
- developer/debug UI
- deterministic fake tools

Verified manual flow:

```text
User
  ↓
FakeModelProvider
  ↓
ToolCall
  ↓
Policy / Approval
  ↓
ToolRegistry
  ↓
Fake Tool
  ↓
ToolResult
```

---

# Phase 1.1 — Durable Persistence & Recovery

**Status: COMPLETE + OWNER ACCEPTED**

Added:

```text
:data:persistence
```

Room persistence covers:

- sessions
- messages
- journal entries
- turns
- approvals
- effects

Durability guarantees:

- pending approval restores as pending
- pending approval never becomes approved merely because the process restarted
- rejection remains rejection
- approval is tied to specific turn/call identities
- effects become durably executing before invocation
- interrupted execution is never automatically replayed
- terminal states restore accurately
- event ordering survives persistence
- sessions remain isolated
- stale prior-turn result cannot leak into current-turn presentation

Recovery policy:

| Interrupted state | Recovery behavior |
|---|---|
| `THINKING` | reconcile to typed `FAILED`; explicit retry required |
| `WAITING_FOR_APPROVAL` | restore pending approval; explicit approval/rejection required |
| `EXECUTING` | reconcile to typed `FAILED`; never replay automatically |
| `COMPLETED` | restore accurately |
| `FAILED` | restore accurately |
| `CANCELLED` | restore accurately |

Manual owner acceptance:

- Approve path: PASS
- Reject path: PASS
- Reject transition: `WAITING_FOR_APPROVAL -> CANCELLED`
- rejected tool did not execute
- pending approval survived force-stop/relaunch
- pending approval did not auto-approve
- completed state survived restart
- stale result regression fixed and manually verified

Phase 1/1.1 is the trusted safety/durability baseline for all future phases.

---

# 4. PHASE 2 — REAL MODEL INTEGRATION

Phase 2 is intentionally divided into two gates.

```text
Phase 2
├── 2A — Cloud LLM + Streaming Conversation
└── 2B — Structured Agent Tool Calling
```

This separation prevents networking/model failures from being conflated with agent-execution failures.

---

# Phase 2A — Cloud LLM + Streaming Conversation

**Status: ACCEPTED**

Implemented:

- `:data:model`
- generic OpenAI-compatible provider
- genuine SSE streaming transport
- secure Android-Keystore-backed API-key storage
- provider base URL + model configuration
- Fake/Cloud provider switching
- provider-neutral model stream events
- typed provider failures
- cancellation
- bounded timeouts
- no automatic request replay
- durable Room conversation history
- bounded multi-turn context
- Compose conversation UI
- provider/model status
- debug visibility
- Room v1 → v2 migration
- HTTPS-only normal path
- cleartext disabled
- API-key redaction/security tests

Current context strategy:

- concise Kinetic system instruction
- latest 20 ordered USER/ASSISTANT messages
- tool messages excluded in Phase 2A
- other sessions excluded

Phase 2A's conversation-only mode remains available when structured tools are disabled.

Acceptance gate:

- real provider connection
- genuine streaming observed
- multi-turn context observed
- cancellation observed
- history remains durable
- no key leakage
- no tool execution capability

Acceptance gate completed by the project owner.

---

# Phase 2B — Structured Agent Tool Calling

**Status: ACCEPTED**

Goal:

Connect a real model's structured tool proposals to Kinetic's already-proven deterministic execution boundary.

Target pipeline:

```text
Real LLM
   ↓
structured ToolCall proposal
   ↓
schema validation
   ↓
ToolRegistry lookup
   ↓
Capability Policy
   ↓
Permission requirements
   ↓
ApprovalGate
   ↓
EffectLedger
   ↓
tool execution
   ↓
ToolResult
   ↓
model continuation
```

Implemented Phase 2B scope:

- provider-neutral `ModelToolSupport`, extended `ToolCall`, and `ModelContinuationRequest`
- explicit cloud allowlist: `echo` and `protected_demo_tool` only
- OpenAI-compatible function schemas with `tool_choice: auto` and parallel calls disabled
- non-stream and fragmented SSE tool-call decoding by index/ID/name/arguments
- strict JSON, exact-field, type, size, turn, exposure, and identity validation
- ordinary prose/printed JSON remains non-executable
- `ToolRegistry`, capability policy, and the existing exact approval gate remain authoritative
- duplicate provider call IDs fail closed in both in-memory and Room ledgers
- effect completion is durable before model continuation begins
- bounded associated tool result and correctly ordered assistant/tool continuation messages
- one final streamed natural-language continuation; chained tool calls rejected
- process death never resends model work or replays completed/uncertain effects
- deterministic Fake provider and local TLS MockWebServer coverage
- Compose tool status, safe argument/risk display, approval controls, result, and continuation
- architecture documentation in `docs/architecture/PHASE2B_STRUCTURED_TOOL_CALLING.md`

Current limitation: exactly one tool proposal and one final continuation per turn. Rejection does not request a provider continuation, and process-restored approvals execute safely without automatically rebuilding network continuation context.

Owner live acceptance passed for safe structured echo, protected Reject, protected Approve with exactly one result, cloud continuation, and prompted-approval-bypass resistance. Phase 2 is complete.

---

# 5. PHASE 3 — NATIVE ANDROID CAPABILITY SYSTEM

**Status: COMPLETE**

Goal:

Introduce narrow, legitimate Android-native capabilities behind Kinetic's Phase 1/2 safety pipeline.

Subphase state:

- **Phase 3A — ACCEPTED**
- **Phase 3B — ACCEPTED**
- **Phase 3C — ACCEPTED**

Implemented Phase 3A capabilities:

- `open_https_url(url)` — lowercase, bounded, credential-free `https://` URLs only; Android `ACTION_VIEW`
- `share_text(text)` — bounded non-empty text; Android `ACTION_SEND`, `text/plain`, and `Intent.createChooser`
- `open_settings(destination)` — internal allowlist containing only `GENERAL` and `WIFI`

All three capabilities expose structured metadata, cross the app boundary, require `CONFIRM`, use the existing `ToolRegistry`/policy/approval/effect pipeline, and return bounded dispatch-only results. The model cannot provide an Intent action, component, package, flags, extras, recipient, MIME type, or unreviewed Settings destination.

Implemented Phase 3B capabilities:

- `copy_text_to_clipboard(text)` — bounded, non-empty plain-text write through `ClipboardManager`; no clipboard read, history, listener, or monitoring surface
- `open_dialer(phone_number)` — conservative local number validation and internal `tel:` construction; `ACTION_DIAL` only, with no call permission or direct-call API
- `compose_email(recipient?, subject?, body?)` — bounded local fields and internal RFC 6068 `mailto:` construction with encoded `subject`/`body` query fields plus compatibility extras; untargeted `ACTION_SENDTO` only, with no attachments, package/component/flags, arbitrary headers, or automatic transmission

All Phase 3B capabilities are registered in the existing `ToolRegistry`, require call-specific `CONFIRM`, reuse SHA-256 argument binding and the durable EffectLedger, and return only bounded dispatch semantics. Model prose and pseudo-tool markup remain non-executable.

Every capability must follow:

```text
ToolCall
   ↓
Tool Registry
   ↓
Capability Policy
   ↓
Permission Check
   ↓
Approval
   ↓
Effect Ledger
   ↓
Android API
```

Do not introduce Accessibility-driven general UI automation in this phase.

Phase 3A implementation details:

- Android implementation is isolated in `:data:android-capabilities`; `:core:kernel` remains pure Kotlin/JVM
- Phase 3A's foreground dispatcher introduced centralized Activity handoffs, write-only clipboard dispatch, and typed failure handling; Phase 3C now owns this boundary through `ForegroundAndroidCapabilityExecutionCoordinator`
- approval/effect identity is SHA-256-bound to the exact validated tool input
- effect state is durably persisted before dispatch; completed or uncertain effects do not replay after restart
- strict model schemas reject additional properties and raw Android implementation details
- existing Compose approval UI shows bounded safe summaries
- Room v2 → v3 migration persists the authorization binding without destructive migration
- documentation: `docs/architecture/PHASE3A_NATIVE_ANDROID_CAPABILITIES.md`
- Phase 3B documentation: `docs/architecture/PHASE3B_USER_MEDIATED_HANDOFFS.md`
- no dangerous permission, direct communication, background service, shell, Accessibility, package enumeration, or arbitrary Intent was added

Phase 3C implementation details:

- `ForegroundAndroidCapabilityExecutionCoordinator` holds only a weak reference to the currently resumed `Activity`; `MainActivity` registers in `onResume` and unregisters in `onPause`, with identity-safe replacement handling
- exact validation and call-specific approval remain authoritative; an availability preflight now runs after both and before the durable executing transition, so absent, stale, destroyed, finishing, or non-resumed owners fail closed
- a process-local atomic execution guard rejects overlapping capability dispatch instead of queueing a second external effect
- lifecycle state is rechecked at dispatch time to close the availability/launch race; cancellation propagates without turning a cancelled coroutine into a successful effect
- availability and dispatch failures produce bounded structured audit events that survive Room recovery without persisting private arguments
- completed or uncertain effects remain non-replayable across return, Activity recreation, and force-stop/relaunch
- documentation: `docs/architecture/PHASE3C_ANDROID_CAPABILITY_LIFECYCLE.md`
- Phase 3C adds no service, background execution, dangerous permission, new tool, or broadened Intent surface
- Owner lifecycle acceptance passed with exactly-once dispatch and no return, recreation, or process-restart replay

Deferred beyond Phase 3B: direct calls/SMS/email or other messaging, contacts, app launching/enumeration, notifications, clipboard reads/history/monitoring, files/attachments, camera, microphone, location, alarms/background autonomy, Accessibility/UI automation, MediaProjection, and other device-control surfaces.

---

# 6. PHASE 4 — MEMORY & CONTEXT ENGINE

**Status: COMPLETE**

Subphase state:

- **Phase 4A — ACCEPTED**
- **Phase 4B — ACCEPTED**
- **Phase 4C — ACCEPTED**

Goal:

Move beyond simple recent-message context into controlled, inspectable memory.

Potential scope:

- conversation summaries
- long-term memory objects
- user-approved durable preferences
- tool-result memory
- episodic/session memory
- semantic retrieval
- deletion/forget controls
- context budgeting
- retrieval provenance
- memory privacy boundaries
- protection against storing secrets unnecessarily

Do not make the model the authority over what is permanently remembered.

Implemented Phase 4A foundation:

- provider-neutral pure-Kotlin `MemoryRecord`, `MemoryScope`, `MemoryCategory`, `MemoryProvenance`, `MemoryContext`, and `MemoryRepository` contracts in `:core:kernel`
- exactly two scopes (`USER`, `SESSION`), three bounded categories (`FACT`, `PREFERENCE`, `TASK_CONTEXT`), and explicit provenance (`USER_EXPLICIT`, `USER_MESSAGE_DERIVED`, `SYSTEM_CREATED`)
- anchored explicit remember commands only; ordinary conversation is not silently promoted into durable USER memory
- one deterministic `ControlledMemoryService` production write/delete authority with length validation and conservative credential/secret rejection; raw model output never writes directly to Room
- exact normalized SHA-256 duplicate identity; duplicates reuse the existing record rather than creating another durable row
- existing Room database upgraded to schema v4 with an explicit v3→v4 migration, exported schema, indexed `memories` entity, and no destructive fallback or second database
- deterministic context construction injects at most four latest USER and four latest current-SESSION memories before the existing recent-20-message window; SESSION isolation is enforced locally
- model transport marks memory as untrusted JSON data in a separate system context block; memory cannot alter ToolRegistry, policy, approval, permissions, EffectLedger, or the structured-tool proposal channel
- Compose Memory panel lists USER/current-SESSION records with category, safe provenance, bounded preview, individual delete, current-session clear, and deliberately confirmed clear-all-USER behavior
- safe structured memory journal events use identifiers, counts, categories, scope, and bounded metadata rather than full secret-bearing content
- memory remains durable across process death; deletion remains durable; memory operations do not replay Android capabilities
- documentation: `docs/architecture/PHASE4A_CONTROLLED_MEMORY.md`
- no embedding, vector database, semantic search, automatic summarization/extraction, WorkManager, background daemon, cloud sync, new Android tool, or new permission was introduced

Implemented and owner-accepted Phase 4B engineering work:

- pure-Kotlin `ContextPlanner`, `ContextPlan`, `ContextBudget`, and deterministic ranker; Android, Room, and provider DTOs remain outside the planner
- bounded pools of up to 32 active USER and 32 current-SESSION candidates, locally ranked to at most four relevant records per scope; zero-relevance records are omitted
- explainable normalized-overlap, ordered-run, category-affinity, full-coverage, and exact-match scoring; recency is only a deterministic tie-breaker before stable memory ID
- dedicated SESSION-only `SessionSummary` domain and generator port with explicit coverage, cumulative source count, SHA-256 digest, `MODEL_DERIVED` provenance, and completed-only persistence
- Room v5 `session_summaries` table and explicit v4→v5 migration, exported schema, indexes, session foreign key, no destructive fallback, and no second database
- incremental foreground compaction preserves original messages, reserves four recent messages, and combines only the previous summary plus newly eligible source messages
- credential-shaped source/generated summary content, blank/oversized/malformed output, cancellation, and provider failures cannot install a partial summary or damage prior context
- Fake mode uses deterministic local summarization; Cloud mode reuses the configured transport with tools disabled and a bounded injection-resistant instruction
- deterministic approximate token budgeting reserves system/security and output space, preserves the current request, and exposes coverage/budget omission reasons
- provider formatting separates trusted instructions, untrusted memory JSON, derived untrusted summary JSON, and ordinary messages; retrieval artifacts cannot authorize or execute tools
- developer UI adds default-OFF automatic compaction, production-path manual compaction, summary metadata/preview, and safe Context Inspector diagnostics
- documentation: `docs/architecture/PHASE4B_RETRIEVAL_AND_CONTEXT_COMPACTION.md`
- no embeddings, vector database, contradiction resolution, automatic permanent-memory extraction, background work, cloud sync, new capability, application-ID change, or permission was introduced

Implemented and owner-accepted Phase 4C engineering work:

- pure-Kotlin governed-memory contracts and deterministic SHA-256 identity derived from scope, owner-session identity, and normalized subject
- explicit anchored create, update/replace, and forget commands; exact duplicates are idempotent, while missing or ambiguous forget targets fail safely
- transactional `ACTIVE`, `SUPERSEDED`, and `CONFLICTED` lifecycle state with bounded revision links and conservative contradiction handling
- explicit updates supersede prior current values without erasing history; conflicting values remain inspectable until the user selects a winner
- `USER_EXPLICIT` provenance cannot be silently displaced by lower-authority derived provenance
- compatible strictly parseable legacy USER_EXPLICIT records are adopted only by a future explicit same-subject/scope governance transaction; installation and retrieval never rewrite them, and lower-authority duplicates cannot adopt them
- query-aware retrieval excludes superseded values, includes conflicts as untrusted context, and directs the model to state uncertainty instead of choosing silently
- session summaries remain distinct `MODEL_DERIVED` artifacts and are not folded into governed-memory revision chains
- Compose Memory UI groups revision history and exposes safe provenance, lifecycle, timestamps, source/key prefixes, edit/replace, resolve, and delete controls
- structured governance journal events record bounded identifiers, action, related IDs, and key-hash prefixes without raw memory content
- Room schema v6 and explicit v5→v6 migration add governance identity/value and revision-link columns plus a composite governance index without destructive fallback
- concurrency and process-reopen tests cover duplicate updates, simultaneous contradictions, durable supersession, conflict, resolution, and preserved provider preferences
- documentation: `docs/architecture/PHASE4C_MEMORY_GOVERNANCE.md`
- no embeddings, vector database, fuzzy/LLM conflict merge, background governance, cloud sync, automatic permanent extraction, new Android capability, application-ID change, or permission was introduced

---

# 7. PHASE 5 — ON-DEVICE MODEL BENCHMARK & SLM RUNTIME

**Status: IN PROGRESS**

**Phase 5A — LOCAL AI ARCHITECTURE & BENCHMARK FOUNDATION:**
**COMPLETE; AUTOMATED GATE PASSED; PHYSICAL ARM64 ENGINEERING DEVICE GATE PASSED;
OWNER ACCEPTANCE PASSED; ACCEPTED.**

Contracts, simulated local provider, ordinary capability probe, ephemeral benchmark
validation, model-neutral corpus/tool scoring and bounded Provider/Debug UI are
implemented. Current official research, candidate scorecard, model-data storage/JNI
rules and physical-device requirements are in
`docs/architecture/PHASE5_ON_DEVICE_AI.md`. No production default is selected.
The original Phase5A scorecard recommended Gemma/LiteRT-LM before the subsequent
Candidate1 provenance blocker and Candidate2 AICore unavailability. Candidate3A's
physical B result now authorizes pinned llama.cpp/Qwen as the current implementation
path, not a production winner. Candidate3B's text, local summary and hardened model
management implementation is host verified; real-device integration validation is
deferred under the owner strategy.

244 automated tests, lint/build, signer and update preservation passed. Historical
emulator Chrome ANR/relaunch timeout remains documented in
`docs/PHASE5A_ENGINEERING_VERIFICATION.md`; the subsequent physical ARM64 engineering
gate and explicit owner acceptance passed. Current closure:
`docs/PHASE5A_PHYSICAL_ARM64_DEVICE_GATE_20260912.md`.
**Phase5B IN PROGRESS — Candidate1 provenance-BLOCKED, Candidate2 unavailable,
Candidate3A physical feasibility passed (B), Candidate3B local provider implementation
complete at host level/host verified; device validation DEFERRED. Phase5 not complete.**

Phase5B prerequisites are satisfied on recorded evidence: Phase4 complete,4C/5A
accepted; provider-neutral boundary and simulated provider implemented; verified
physical ARM64 evidence recorded at earlier gates. The phone is not required to
remain connected. No fabricated metrics or automatic hybrid routing; Roomv6 and
unchanged non-dangerous permission surface. Current implementation and historical
device evidence remain distinguished.
The subsequent Candidate1 brief authorized Phase5B to begin. Initial supply-chain
review stopped on unresolved bundled-license provenance before integration/download;
see the current preflight report. This does not revoke Phase5A acceptance.

Goal:

Benchmark practical native Android local-model paths before committing Kinetic to one runtime.

Candidate paths to evaluate later:

- Android-native / LiteRT-family runtimes
- platform on-device AI surfaces where available
- llama.cpp-derived native integration where licensing/fit is acceptable
- other Android-native runtimes discovered by evidence

Measure:

- first-token latency
- tokens/sec
- RAM
- thermal behavior
- battery
- APK/model-pack size
- startup time
- ABI/device compatibility
- quantization quality
- context limits
- tool-call quality

Do not choose a local runtime based only on desktop benchmarks.

---

# 8. PHASE 6 — HYBRID MODEL ROUTER

**Status: HYBRID ROUTER FOUNDATION IMPLEMENTED; HOST VERIFIED; MANUAL MODE REMAINS
DEFAULT; DEVICE/OWNER VALIDATION DEFERRED TO PHASE10A.**

Explicitly authorized after Phase5B host completeness. Implemented deterministic
typed router, pinned provider preparation before context planning, separate summary
selection, opt-in settings and bounded Router inspector.243 host tests/lint/build/
signer gate passed; no device or production acceptance. Known-constraint and UNKNOWN
telemetry limitations are explicit in [the implementation record](docs/architecture/PHASE6_HYBRID_MODEL_ROUTER.md).
No task-focused context activation, AI classifier or cross-provider fallback.

Goal:

Route requests between cloud and local models.

Model Router may consider:

- privacy
- network availability
- latency
- model capability
- context size
- cost
- battery/thermal state
- user preference
- task sensitivity

Routing must remain deterministic/policy-governed where safety matters.

Potential future adapter:

- Google ADK or similar agent framework may be evaluated **behind** Kinetic abstractions.
- Kinetic must not become merely a wrapper around an external agent framework.

---

# 9. PHASE 7 — INTEROPERABILITY

**Status: NOT STARTED**

Subprojects:

## 7A — MCP Adapter / Host

- bounded MCP client/host architecture
- capability allowlisting
- schema validation
- approval/policy integration
- network trust boundaries
- no arbitrary executable plugin loading

## 7B — AppFunctions Adapter

Where supported by Android:

- expose/consume suitable Android AppFunctions
- map functions to Kinetic tool metadata
- preserve deterministic policy/approval

## 7C — A2A Adapter

- agent-to-agent communication
- identity/authentication
- capability advertisement
- bounded delegation
- provenance/audit

## 7D — Partner-App IPC / Extension SDK

Prefer platform IPC such as:

- Binder/AIDL
- explicit intents
- content-provider style surfaces where appropriate

Do not rely on downloaded executable plugins.

## 7E — Secure onboarding / provider and model UX — PLANNED ONLY

- Official Google OAuth where the provider supports it; sign-in is not universal
  model entitlement. Keep authentication, provider authorization and model selection distinct.
- API keys/login credentials/tokens entered only through secure manual UI/official
  authorization surfaces; never chat, model-visible memory or generated arguments.
- Provider-neutral guidance and explicit cost/privacy/availability disclosures.

---

# 10. PHASE 8 — ADVANCED AUTOMATION & DISTRIBUTION BOUNDARY

**Status: NOT STARTED**

Formalize:

```text
play
lab
```

or equivalent distribution/build boundary only when required by implemented capability differences.

## Play artifact

Must remain compatible with Play policies and public-distribution expectations.

Avoid foundational dependence on autonomous Accessibility execution.

## Lab artifact

May research broader experimental automation where legally/platform-permitted, but must still preserve:

- explicit permissions
- deterministic policy
- approvals
- audit
- user visibility
- process-death safety
- no privilege-escalation assumptions

Do not prematurely add flavors before there is a real capability divergence.

## 8A — Safe prompt-first customization and tutorials — PLANNED ONLY

Typed allowlisted proposals for essentially all safe Kinetic settings, themes and
customization, with validation, user visibility, confirmations/audit and reversibility
where appropriate. Optional AI-guided tutorials can be disabled conversationally
through a safe setting; disabling guidance cannot disable security policy.
No model-text preference writes, secret changes, permission grants or approval bypass.

## 8B — Inspectable scoped personalization — PLANNED ONLY

Structures analogous to soul.md/memory.md must preserve governed-memory provenance,
scope, conflict/supersession, inspectability and deletion. Prefer canonical governed
records with optional Markdown views/exports; keep persona/preferences/session/task
context separate. No arbitrary agent-file instructions or security-policy override.

## 8C — Broader bounded Android control — PLANNED ONLY

Expand explicit capability/permission surfaces only through ToolRegistry → Policy →
ApprovalGate → EffectLedger → Android coordinator. Voice/prompt input does not add
execution authority, covert observation or arbitrary Android/shell access.

---

# 11. PHASE 9 — MULTIMODAL CONTEXT

**Status: NOT STARTED**

Potential surfaces:

- image input
- camera input
- microphone
- speech-to-text
- text-to-speech
- document/file understanding
- screen-context capture where legitimate
- MediaProjection-based context

MediaProjection requirements:

- explicit user consent
- Android lifecycle correctness
- foreground-service requirements where applicable
- clear recording indicator/UX
- strict privacy controls
- no covert capture

Multimodal context is input to reasoning; it does not bypass the execution policy.

## 9A — Voice-first interaction — PLANNED ONLY

STT/TTS with initial evaluation of Deepgram BYOK, secure manual key UI, explicit
microphone/privacy/cost disclosures, consent, foreground lifecycle/cancellation,
audio retention policy and accessible text alternatives. No provider/API suitability
assumed without later research; no voice code or microphone permission added now.

## 9B — Task-focused dynamic context activation — PLANNED ONLY

Activate only relevant scoped context for the current task, with bounded budgets,
explainable inclusion/exclusion, task boundaries and durable canonical history.
The owner's physics/search/messaging/video example motivates the design, not a
permission to observe other apps. Any cross-app context needs explicit lawful
platform/permission/capability design and user control. No covert capture or
cross-task leakage; context cannot grant policy or approval authority.

---

# 12. PHASE 10 — PUBLIC BETA / HARDENING

**Status: NOT STARTED**

Goal:

Prepare Kinetic for external testers and eventual public distribution.

Scope:

- crash handling
- telemetry with privacy controls
- structured audit logs
- backup/restore decisions
- Room migration suite
- device/API compatibility matrix
- battery/resource budgets
- security review
- provider failure hardening
- secret-storage review
- threat model
- abuse cases
- prompt/tool injection defenses
- dependency/license inventory
- SBOM/provenance
- release signing
- Play policy review
- privacy policy
- user data deletion/export
- onboarding
- provider setup UX
- accessibility of Kinetic's own UI
- documentation
- extension SDK documentation if applicable

## 10A — FINAL INTEGRATION & REAL-DEVICE VALIDATION GATE

**PLANNED; DEFERRED UNTIL PLANNED FUNCTIONAL BUILD PHASES ARE IMPLEMENTED. NOT STARTED.**
With owner coordination, rigorously validate on real Android: signed upgrade and
native ABI/API/16KB loading, verified model import/storage, real streaming/UTF-8,
context/quality/performance, cancellation/leaks/process death, memory/thermal/LMKD,
manual provider selection/no cloud fallback, Room/history/governed memory/summary
and credentials, policy/approval/exactly-once/no-replay, prompt-control adversarial
cases, applicable OAuth/voice consent/privacy, themes/adaptive/IME/accessibility,
and process-specific new crash/ANR logs. Use isolated fixtures and non-destructive
updates, preserve retained evidence, and separate engineering from owner acceptance.
No phone connection, ADB or emulator launch is a routine build-phase prerequisite.

## 10B — Public beta / release hardening

The distribution/hardening scope above follows final integration validation;
no fabricated release readiness from host builds or historical device evidence.

---

# 13. PLANNED ARCHITECTURAL MODULES

These are target architectural responsibilities, **not a requirement to create 15 Gradle modules**.

| # | Responsibility | Current state |
|---|---|---|
| 1 | Native Agent Kernel | Implemented |
| 2 | Model Router | Provider abstraction implemented; hybrid router future |
| 3 | Policy + Approval Kernel | Implemented |
| 4 | Tool Registry | Implemented |
| 5 | Durable Session/Event Journal | Implemented |
| 6 | Android Service Manager | Future |
| 7 | Native Android Capability Layer | Future |
| 8 | JNI/Bionic Native Node | Future/research |
| 9 | MediaProjection Context Engine | Future |
| 10 | MCP Adapter/Host | Future |
| 11 | Partner-App IPC/Extension SDK | Future |
| 12 | AppFunctions Adapter | Future |
| 13 | On-device SLM Runtime | Future |
| 14 | Optional Embedded JavaScript/V8 Runtime | Future research only |
| 15 | Play vs Lab Distribution Boundary | Future; introduce only when needed |

Module boundaries should be introduced when they improve dependency direction, testing, or lifecycle separation—not merely to match this table.

---

# 14. SECURITY / PRIVACY REQUIREMENT LEDGER

Unless a phase explicitly approves and tests them, Kinetic must not add:

- AccessibilityService
- `MANAGE_EXTERNAL_STORAGE`
- `QUERY_ALL_PACKAGES`
- SMS permissions
- call-log permissions
- contacts permissions
- camera
- microphone
- location
- MediaProjection behavior
- notification-listener service
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- arbitrary shell execution
- downloaded executable plugins
- TLS verification bypass
- globally enabled cleartext
- hard-coded API keys

Every newly requested permission/capability requires:

1. functional requirement
2. Android/API justification
3. user-visible behavior
4. policy review
5. security review
6. automated tests
7. manual acceptance where relevant

---

# 15. MODEL PROVIDER PRINCIPLES

Kinetic must remain provider-independent.

Supported architecture should allow:

```text
Fake provider
Cloud provider(s)
Local provider(s)
Hybrid router
```

without rewriting the AgentRuntime.

Provider secrets:

- never committed
- never placed in roadmap.md
- never stored in conversation Room tables
- never journaled
- never exposed in UI after storage
- never written into diagnostic messages
- never logged as Authorization/Bearer values

---

# 16. EFFECT / APPROVAL SAFETY CONTRACT

No future feature may bypass this contract.

For risk-bearing execution:

```text
Proposal
  ↓
Validate
  ↓
Resolve registered capability
  ↓
Policy
  ↓
Permission check
  ↓
Approval if required
  ↓
Durably mark effect
  ↓
Execute
  ↓
Durably record result
```

Rules:

- approval is call-specific
- stale approval is invalid
- rejected calls cannot execute
- interrupted uncertain effects cannot auto-replay
- model messages cannot mutate authorization state
- model text is not executable authority

---

# 17. BUILD-FIRST VERIFICATION STANDARD

During authorized implementation, retain regressions and use proportionate host
compile/unit/lint/assemble checks, static permission and secret-boundary review.
Report actual counts and exact implementation coverage. Do not require a connected
phone or start/restart an emulator for routine work. Do not repeat device acceptance
gates while planned functional phases are being built.

Platform/instrumentation, launches, real performance, logcat/ANR, preservation,
visual/lifecycle and manual acceptance checks are tracked as DEFERRED to Phase10A,
not passed or failed by assumption. Host analysis of instrumentation sources is not
device execution. Existing device-test safety guard stays intact.

Use distinct states: IMPLEMENTED, HOST VERIFIED, DEVICE VALIDATION DEFERRED,
ENGINEERING DEVICE VERIFIED, OWNER ACCEPTED. Never silently mark acceptance or
overall production completion from implementation claims or host tests alone.

---

# 18. TOOLCHAIN / DEVELOPMENT ENVIRONMENT

Current verified development environment is intentionally kept stable unless a genuine incompatibility requires change.

Known working setup:

- Windows
- Android Studio installed
- workspace/local JDK 21 for Gradle build runtime
- Java 17 bytecode compatibility where required
- Android SDK consolidated on `E:`
- API 36 emulator
- `Kinetic_API_36`
- ADB operational
- NDK 28.2 available for future native work
- Gradle/AGP combination currently working and should not be broadly upgraded during unrelated feature phases

Do not perform speculative toolchain upgrades inside feature work.

---

# 19. REFERENCE-REPOSITORY POLICY

Reference repositories are research inputs.

Before adapting any code:

| Decision | Requirement |
|---|---|
| Copy | license explicitly compatible + provenance preserved |
| Adapt | license compatible + meaningful source attribution/provenance |
| Clean-room rewrite | use when design is useful but code license is incompatible/uncertain |
| Reject | architecture conflicts with native Android, security, Play, or Kinetic invariants |

In particular:

- do not copy GPL code into a project whose intended licensing/distribution would be incompatible
- inspect nested licenses/submodules/vendor code
- record commit SHA used for architectural reference
- do not assume repository root license covers every bundled dependency

---

# 20. CODEX CONTINUITY PROTOCOL

Every Kinetic Codex prompt must include:

```text
PROJECT ROOT:
E:\Projects\Kinetic

AUTHORITATIVE CONTROL DOCUMENT:
E:\Projects\Kinetic\roadmap.md

READ roadmap.md FIRST.
UPDATE roadmap.md LAST.
```

Codex must:

1. read current roadmap before edits
2. inspect current source rather than assume old architecture
3. preserve completed-phase behavior
4. operate only inside the authorized phase
5. run reasonable host tests/builds; defer device validation per the current owner strategy
6. report blockers rather than falsify completion
7. update roadmap with verified facts only
8. stop after the requested gate
9. never automatically begin the next phase

If roadmap and code disagree:

- inspect implementation/tests
- report the discrepancy
- do not silently rewrite historical project state to make them agree

---

# 21. CURRENT BUILD CHECKPOINT / NEXT AUTHORIZED WORK

## Candidate3B and Phase6 router host verified; Phase7 not started

Current: Candidate3B text, local summarization, hardened model management and the
Phase6 opt-in Hybrid router are host verified (243 tests; lint0errors/4warnings; signed APK).
MANUAL remains default. Full device
integration and owner validation are DEFERRED to Phase10A. Phone not required.
History below preserves the progression to this implementation path.

Phase 4C is ACCEPTED and Phase 4 COMPLETE by explicit owner confirmation. Phase 5A
was authorized and its foundation is implemented; automated checks pass. Its clean
emulator installed-build device gate was BLOCKED by the September 9 Chrome ANR and
Kinetic relaunch timeout. The physical engineering gate has now passed as recorded
below. No emulator repair or Kinetic-specific root cause is claimed.

The September 10 bounded diagnosis reproduced two standalone Chrome ANR incidents,
including after the one permitted Chrome process restart, without Kinetic dispatch.
The conditional clean Kinetic flow was therefore not started. Exact internal cause
is unresolved; see `docs/PHASE5A_CHROME_STABILITY_RETRY_20260910.md`.

The read-only root-cause diagnostic is now completed: **F. GRAPHICS / SURFACEFLINGER /
GPU STALL — MEDIUM confidence**, specifically HWUI's render-thread dependency on
the emulator graphics pipe, with significant CPU contention. First full stack and
exact host-side cause remain unresolved; see `docs/PHASE5A_CHROME_ROOT_CAUSE_20260910.md`.

The owner subsequently authorized that one cold restart. It was performed at
12:03:17 September 10 with unchanged launch settings and failed the Android health
gate before Chrome/Kinetic: System UI plus 27 other new Android ANRs through the
12:08:44 cutoff. See `docs/PHASE5A_COLD_RECOVERY_20260910.md`. The restart authorization
is exhausted; no second repair or acceptance attempt is implied.

The owner subsequently authorized one same-AVD reopen with resource sampling on
September 11. That diagnostic is now complete: **ENVIRONMENT BLOCKED — RESOURCE
PRESSURE REPRODUCED**. Eleven new Android ANRs through01:49:35.445, QEMU CPU up to
91% of host capacity, guest graphics/kernel contention and storage stalls occurred
while available host RAM remained above3GiB. See
`docs/PHASE5A_BOOT_PRESSURE_20260911.md`. No Kinetic/Chrome test or repair followed.

The owner-authorized final attribution diagnostic is now completed within its
20-minute bound; see `docs/PHASE5A_FINAL_ATTRIBUTION_20260911.md`. Current QEMU had
settled; Windows denied privileged CPU/file tracing, leaving exact root attribution
**G: inconclusive** and historical mixed graphics/CPU/disk hypothesis at medium
confidence. The single unapplied configuration recommendation is display resolution
1080x2400 to720x1600 with other settings unchanged. Physical-device acceptance is
now considered preferable; no emulator repair, restart or acceptance retry was run.

The owner approved the physical ARM64 engineering gate. Its September11 preflight
at14:49:55 found only the emulator; **PHYSICAL ARM64 DEVICE REQUIRED — GATE NOT RUN**.
See `docs/PHASE5A_PHYSICAL_ARM64_DEVICE_GATE_20260911.md`. No emulator fallback or
physical-device operation followed in that September11 attempt. The September12
resume verified the artifact before installation; no emulator data/credentials imported.

The September12 resume verified the owner-designated Samsung SM-A065F physical
ARM64 phone and passed the fresh-install engineering gate. Owner explicitly confirmed
pending approval survived rotation before their manual Approve tap; read-only audit
reconfirmed exactly one handoff and existing no-replay evidence. Attribution is
resolved without repeating the action. Detailed evidence and capture limits are in
`docs/PHASE5A_PHYSICAL_ARM64_DEVICE_GATE_20260912.md`. Cloud was restored without a key.

Owner subsequently explicitly reported the complete physical Phase5A owner flow PASS
on September12. Phase5A is COMPLETE/ACCEPTED. Optional owner cloud smoke and the inert
textual-tool interoperability issue are tracked separately in the closure report;
neither reopens Phase5A. No source/device/test action was performed for closure.

The subsequent Phase5B Candidate1 authorization began fresh official-source and
physical-device preflight. LiteRT-LM0.17.0's bundled GRE/GPLv2 notice entry lacks an
established build-only/shipped-component mapping; the license-unclear stop condition
was applied. This is not a device/model failure or proven license incompatibility.

The September13 binary-specific provenance follow-up concluded **D. INCONCLUSIVE —
BINARY-SPECIFIC PROVENANCE CANNOT BE ESTABLISHED**. Candidate1 stays BLOCKED;
the GPL notice was not mapped to shipped code or proven build-only. See the
[provenance audit](docs/architecture/PHASE5B_LITERTLM_0170_LICENSE_PROVENANCE.md).

Owner subsequently authorized Candidate2 AICore preflight. The current official
Prompt API list excludes Galaxy A06/SM-A065F; fresh ordinary package evidence
shows AICore absent. **Candidate2: D. DEVICE UNSUPPORTED / UNAVAILABLE.** Stage4
stop applied before any SDK adapter; checkStatus NOT_RUN. Candidate1 remains
BLOCKED with its evidence unchanged. See the
[Candidate2 preflight](docs/architecture/PHASE5B_CANDIDATE2_AICORE_PREFLIGHT.md).

The earlier Candidate2 next-task recommendation was superseded by owner
authorization of Candidate3A. Its bounded physical feasibility now passed:
**B. VIABLE BUT PERFORMANCE LIMITED.** Exact pins, performance limitations,
raw quality failures, memory-pressure audit and completed temporary-device cleanup
are in the [Candidate3A benchmark](docs/PHASE5B_CANDIDATE3_PHYSICAL_BENCHMARK_20260913.md).
At the Candidate3A checkpoint real Kinetic runtime integration had not begun.
It progressed to Candidate3B host completeness, followed by explicitly authorized
Phase6 opt-in routing above. No permanent production model default has been selected.

The owner authorized Candidate3B BUILD, its local-provider completeness follow-up,
and then Phase6, with device testing deferred. All are implemented/host verified as
recorded above. Exactly one next recommended task, **not begun**:
**PHASE7A — BUILD-ONLY MCP ADAPTER / HOST FOUNDATION**.
This recommendation alone does not authorize Phase7 implementation; no repeated
device gate is implied. Text-only local authority boundaries remain unchanged.

Engineering and owner acceptance are recorded in
[physical Phase 5A verification](docs/PHASE5A_PHYSICAL_ARM64_DEVICE_GATE_20260912.md).
The separate Astra/Stellar status remains **ENGINEERING DEVICE GATE PASSED; OWNER
ACCEPTANCE PENDING**, with no fabricated provider/tool/UX owner acceptance.
Phase5 stays IN PROGRESS; Phase5B is IN PROGRESS, Candidate1 remains BLOCKED,
Candidate2 unavailable, Candidate3A physical feasibility passed with classification B;
Candidate3B real native text, local summarization and hardened model management
IMPLEMENTED/HOST VERIFIED; implementation complete at host level, final device
validation DEFERRED. Large-source summary limitation and deferred validation remain explicit.
Phase6 Hybrid foundation is IMPLEMENTED/HOST VERIFIED; MANUAL remains default;
device/owner validation DEFERRED. Phase7 NOT STARTED.
No Candidate1 owner acceptance flow until engineering passes.

---

# 22. FUTURE ROADMAP REVISION RULE

This roadmap is a living architectural contract.

Update it when:

- a phase is implemented
- a manual gate passes/fails
- architecture materially changes
- an important security invariant is added
- research establishes a new constraint
- an external reference is approved/rejected for reuse
- Android/Play policy materially changes a planned capability

Do not use roadmap updates to retroactively claim unverified functionality.

---

# END-STATE VISION

A mature Kinetic runtime should eventually support:

```text
User / multimodal context
          ↓
      Model Router
     ↙           ↘
 Local SLM      Cloud LLM
     ↘           ↙
       Agent Kernel
            ↓
     Structured Proposal
            ↓
    Policy + Permission
            ↓
       Approval Gate
            ↓
       Effect Ledger
            ↓
 Tool Registry / Capability Layer
     ↓        ↓         ↓
 Android     MCP      Partner IPC
 APIs       tools      / AppFunctions
            ↓
        Tool Results
            ↓
     Durable Memory
            ↓
      Model Continuation
```

while preserving the central rule:

> **The model may reason and propose; Kinetic remains the authority that decides what actually executes.**
