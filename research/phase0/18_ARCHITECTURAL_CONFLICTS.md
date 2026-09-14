# Architectural Conflicts and Phase 0 Resolutions

## Purpose

The references often solve the same problem with incompatible assumptions. This report records which side Kinetic should take before a later architecture phase. “Resolution” means a Phase 0 recommendation, not an implemented ADR.

## Conflict matrix

| ID | Conflict | Evidence in the checked-out sources | Phase 0 resolution | Still needs proof |
|---:|---|---|---|---|
| C01 | Remote Gateway versus self-contained phone kernel | OpenClaw Android delegates agent/provider/session/task/cron/policy/memory/routing to Gateway through `Eco_reference/openclaw/apps/android/app/src/main/java/ai/openclaw/app/gateway/GatewaySession.kt`; host ownership is under `src/agents`, `src/tasks`, `src/cron`, `src/gateway`. OpenDroid instead runs `core/agent/AgentLoop.kt` locally. | Build a small native kernel; make OpenClaw/Gateway interoperability an adapter, not the runtime authority. | Device/resource envelope for full local orchestration and provider streaming. |
| C02 | Service/coroutine state versus durable task state | OpenDroid's active job, approval/NeedsInput continuations and cursor remain in `AgentLoop.kt`/ViewModel/service memory despite persisted plan rows. OpenClaw Android uses `chat/ChatCommandOutbox.kt` and `ClientDatabases.kt`; Hermes persists tool-call intent before dispatch in `agent/conversation_loop.py`. | Durable `Run/Step/Attempt/Effect/Delivery` journal is authoritative; services and coroutines are leases. | Exact Android Room transaction/reconciliation design and executor-specific idempotency. |
| C03 | Arbitrary cross-app UI autonomy versus Play distribution | OpenDroid `accessibility/OpenDroidAccessibilityService.kt` and automators are central to broad completion. OpenClaw moves `MobileUiHandler` into a third-party flavor and hard-stubs it in `src/play`; `apps/android/app/src/thirdParty/AndroidManifest.xml` adds accessibility/sensitive permissions. | Core must complete useful tasks without general autonomous AccessibilityService. Cross-app UI automation is an Advanced capability with honest `unavailable` results in Core. | Which narrow accessibility uses, if any, match a declared Play-eligible purpose after formal policy review. |
| C04 | One manifest with all powers versus compile-time capability profiles | OpenDroid has one broad `app/src/main/AndroidManifest.xml`. AndyClaw similarly combines ordinary and privileged/system capabilities. OpenClaw uses `play`/`thirdParty` source sets in `apps/android/app/build.gradle.kts`. | One domain kernel; product/source-set injected capability providers and manifests for `play`, `advanced`, optionally `oem/system`. No UI-only hiding. | Exact Gradle/module topology, signing/update channels and shared-data migration. |
| C05 | Model as authority versus model as proposer | OpenDroid `AutoApprovalPolicy.kt` has name-level AUTO and total YOLO bypass. AndyClaw `safety/SafetyLayer.kt` and `ToolAttenuation.kt` are bypassed when safety is off/YOLO. MobileRun policy is substantially prompt-driven. | A deterministic capability broker outside the model/prompt decides allow/ask/deny/unavailable on every call. No bypass defeats hard floors. | Final risk taxonomy, confirmation UX and enterprise/parental policy composition. |
| C06 | Deny lists versus capability allowlists | OpenClaw `src/security/dangerous-tools.ts` centralizes important default denies; AndyClaw protects a named set in `ToolAttenuation.kt`. New/renamed tools can escape name-based lists. | Default-deny registry with explicit descriptor/effect metadata; named deny sets remain defense in depth and migration checks. | Descriptor schema and conformance rules that remain monotonic across version changes. |
| C07 | Action-name approval versus parameter/target-bound approval | OpenDroid `ActionSchema.kt`/`AutoApprovalPolicy.kt` grant action names. AndyClaw `ToolDefinition.kt` carries `requiresApproval` boolean. Hermes `tools/approval.py` binds command descriptions/session choices more richly but remains host-command oriented. | Approval token binds canonical tool/version/arguments/resolved target/policy/sensitivity/expiry and is single-use when required. | Target-resolution timing for mutable contacts/accounts/documents and accessible confirmation summaries. |
| C08 | Prompt fencing versus actual isolation | OpenClaw `src/security/external-content.ts` and AndyClaw `SafetyLayer.kt` use random markers/sanitization. These influence model behavior but cannot enforce tool authority. | Preserve labels/bounds as defense in depth; attenuate tool view and enforce policy independently. | Injection-resilience evaluation corpus across notification, email, web, memory, skills and screenshots. |
| C09 | Dynamic executable ecosystem versus Play-safe learned skills | OpenClaw/Hermes skills may call shell, Python, Node, npm and CLIs; AnyClaw `internal/adapter/cli.go`, `script.go` and `pipeline.go` execute shell/scripts/browser JS. Android dynamic code policy constrains remote DEX/JAR/native code. | Core accepts declarative validated Skill IR over built-in/signed capabilities. Restricted JS is optional Tier 2 only after a concrete need and sandbox proof; genuine Node/Linux stays external. | Skill IR expressiveness, migration coverage and whether Tier 2 has enough user value to justify its attack surface. |
| C10 | Autonomous learned mutation versus reviewed knowledge proposals | Hermes `agent/learn_prompt.py` asks the live agent to write `SKILL.md`; background review/mutation paths can make durable changes. | Learned memory/skills are evidence-linked, versioned proposals; compile, validate, simulate/evaluate and approve before effectful activation. | Confidence/evaluation thresholds, expiry, rollback and user review burden. |
| C11 | Generic in-process plugins versus isolated Android integration | OpenClaw `src/plugins`/`packages/plugin-sdk`, Hermes `hermes_cli/plugins.py`, and AnyClaw adapters expect host runtime powers. AndyClaw has APK/Binder extension paths under `AndyClaw/.../extensions`, plus a separate unfinished AIDL skill path. | Core executors are native/built-in. External app integrations use AppFunctions or typed authenticated IPC; Advanced plugins are isolated and capability-attenuated. | AppFunctions availability/feature completeness and a final extension publisher/signing model. |
| C12 | Reusing AndyClaw code versus a permissive Kinetic codebase | `Eco_reference/AndyClaw/LICENSE` is GPL-3.0; some Aurora files are explicitly GPL-3.0-or-later; opaque AAR/model/native provenance is incomplete. | No direct AndyClaw implementation reuse unless Kinetic deliberately adopts GPL-compatible distribution. Clean-room concepts and protocol interoperability only. | Legal review of any desired wire protocol and missing third-party artifact provenance. |
| C13 | Root repository license versus component provenance | Hermes root is MIT, but `plugins/security-guidance/patterns.py` is Apache-2.0 with `NOTICE`, and productivity-skill `LICENSE.txt` files restrict derivatives. AndyClaw opaque binaries/models lack notices. MobileRun lacks exact Git revision. | Path-level provenance ledger is mandatory; exclude restrictive/unknown candidates until resolved. Generate Kinetic SBOM/notices from actual dependencies. | MobileRun exact commit; AndyClaw AAR/model/whisper provenance; model-weight terms for every selected local model. |
| C14 | Desktop local endpoint versus embedded local model | Hermes/OpenClaw “local” providers often mean Ollama/llama.cpp-compatible HTTP endpoints. OpenDroid has real LiteRT/AI Core integrations; AndyClaw uses a bundled Llamatik AAR; AirLLM streams layers on PyTorch/CUDA/MLX. | `LocalModelEngine` explicitly means in-process/device execution. Remote loopback is a separate endpoint class. Prefer OpenDroid LiteRT-LM as the first represented engine to spike. | Real-device RAM, first-token/tokens-per-second, thermal, battery, structured-call accuracy, model licensing and supported-device coverage. |
| C15 | Error text in token stream versus typed terminal failure | OpenDroid `LiteRTLMProvider.streamComplete` catches failure and emits an `Error (...)` string as content, which can bypass wrapper fallback. Hermes/OpenClaw normalize terminal states more explicitly. | Streaming contract separates data tokens, usage, tool calls and terminal typed error. No provider may encode transport failure as successful assistant content. | Cross-provider conformance suite and partial-output fallback policy. |
| C16 | Automatic retries versus effect safety | OpenClaw `src/provider-runtime/operation-retry.ts` avoids retrying creates by default. OpenDroid provider retry is cancellation-aware and avoids replay after emitted stream content. Agent systems otherwise may retry after ambiguous failure. | Retry only under typed failure + idempotency rules. Non-idempotent effect timeout becomes `unknown` and is reconciled, not blindly repeated. | Per-capability idempotency keys/status APIs and UI for unresolved outcomes. |
| C17 | Opportunistic tool batching versus declared effect scheduling | Hermes `agent/tool_dispatch_helpers.py` uses safe batches and filesystem overlap checks. AndyClaw parallel engine has composition/double-execution defects documented in `04_ANDYCLAW_ANALYSIS.md`. | Scheduler admits concurrency from resolved resource/effect scopes; unknown is a barrier; one durable owner per attempt. | Formal scope algebra and executor cancellation/compensation contracts. |
| C18 | Per-agent budgets versus aggregate autonomy budget | Hermes children receive independent iteration budgets in `tools/delegate_tool.py`; OpenClaw/Hermes support fan-out/subagents. | Apply both local step/run limits and parent-owned tree/account budgets for tokens, time, effects, network, battery and child count. | Budget allocation/reclamation and behavior under headless scheduled work. |
| C19 | Trajectory/log as observability versus recovery source | MobileRun trajectory writers and screenshots support inspection/replay but can be bounded/dropped. OpenClaw audit is deliberately metadata-only. | Non-droppable minimal effect journal for correctness; separate privacy-bounded audit/trajectory/artifact projections for evaluation. | Storage cost, encryption, export format, retention and privacy UX. |
| C20 | Raw coordinates versus snapshot-bound UI targets | MobileRun coordinate-contract tests distinguish screenshot/device coordinates; OpenClaw advanced accessibility binds references to snapshot generation/package/UI epoch. | Coordinate spaces are types; all element/point targets carry viewport/snapshot transform and revalidate before action. | Robust rotation/foldable/multi-window/IME/zoom handling and visual-grounding accuracy. |
| C21 | Plain memory/transcripts versus sensitive typed knowledge | Hermes `tools/memory_tool.py` stores Markdown; OpenDroid Room models provide categories but basic retrieval; screenshots/notifications can enter context in Android agents. | Typed records with evidence, scope, sensitivity, provenance, expiry/version, encryption and user delete/export; ephemeral UI state is not long-term memory. | Retrieval quality under strict token/privacy budgets and poisoning defenses. |
| C22 | Foreground service as daemon versus Android-native scheduling | OpenDroid/AndyClaw services and heartbeat paths approximate long-lived agents. Android lifecycle/policy do not provide systemd semantics. | WorkManager for deferrable persistence, eligible typed FGS for active user-visible work, AlarmManager narrowly, and restart reconciliation for all. | Latency/reliability across OEM battery managers and exact eligible FGS types/use cases. |
| C23 | Broad filesystem authority versus document grants | OpenDroid advanced actions and manifests include broad storage; AnyClaw/desktop agents assume arbitrary host paths. | Core uses app-private storage, MediaStore and SAF URI grants. `MANAGE_EXTERNAL_STORAGE` is not a foundation. | Which user workflows cannot be met through SAF/MediaStore and whether any justify a non-Play profile. |
| C24 | MCP/tool discovery versus local mobile authorization | MobileRun and AnyClaw expose powerful operations over MCP; OpenClaw has broad MCP/host integration. | MCP is a transport adapter. Every remote/discovered operation normalizes into a local descriptor and passes on-device caller identity, policy, confirmation and audit. | Mobile-friendly transport/authentication, offline behavior and interoperability subset. |
| C25 | Native code everywhere versus targeted NDK use | AndyClaw uses JNI/native LLM and Whisper; OpenDroid wraps packaged LiteRT/AI Core; orchestration references are Kotlin/Python/TS/Go. | Kotlin/coroutines/Room/AndroidX for orchestration. NDK only for measured inference/audio/performance or indispensable native compatibility behind a Kotlin interface. | Benchmark which local model/audio engine actually requires custom JNI versus maintained Android packages. |

## Conflicts inside individual references

### OpenClaw

- Android is architecturally polished yet not autonomous: native capability quality does not remove Gateway ownership.
- `packages/plugin-sdk/package.json` advertises a larger source-export surface than this exact checkout contains under `packages/plugin-sdk/src`; do not freeze architecture against that unstable/generated boundary.
- The Play/third-party split is a strong pattern, but protocol method breadth is much larger than a native Kinetic MVP. Compatibility belongs in an adapter.

### OpenDroid

- Room persistence coexists with process-local execution ownership and `fallbackToDestructiveMigration`; “uses Room” does not mean durable/resumable.
- `neverAutoApprove` is meaningful in AUTO but defeated by YOLO.
- A typed action list coexists with broad, upfront permission grouping and accessibility dependence.
- The strongest embedded local engine also has a stream/error contract bug and incomplete catalog checksums/license metadata.

### AndyClaw

- Extension security defaults are stricter, while the application safety layer defaults off and YOLO disables multiple controls.
- Two extension systems coexist: the active integrated raw Binder/provider/broadcast/intent path and an unfinished AIDL external-skill path; they are not one coherent protocol.
- The parallel engine and streaming execution have useful abstractions but observed ownership/composition/double-execution defects.
- Broad native functionality is inseparable from GPL and unresolved artifact provenance for direct-reuse purposes.

### Hermes

- The loop has unusually rich recovery/persistence semantics, but repeated-call hard stops are opt-in and many tools retain unrestricted host power.
- Progressive skill discovery is clean, but skill content can still invoke shell/Python/Node.
- Background learning is structured curation, yet generative mutations can become durable without independent correctness proof.
- External memory/provider calls generally fail open for availability; Kinetic must decide which security/durability boundaries instead fail closed.

### MobileRun

- Trajectories and macros look replayable, but recording is not a crash-consistent task ledger.
- UI semantics are strong, while underlying device authority/recovery depends on desktop ADB/Portal and external packages.
- App cards improve grounding but are prompt content rather than authenticated, version-constrained policy objects.

### AnyClaw

- Declarative YAML unifies commands, but adapters collapse into arbitrary shell/script/browser execution.
- Two overlapping descriptor/auth/result stacks behave differently; untested fields such as auth prefix are not reliable contracts.
- Registry convenience expands provenance and runtime-code trust without a corresponding signing/policy system.

### AirLLM

- Layer streaming lowers accelerator residency but exchanges it for disk/CPU transfer, dependency complexity and latency. It solves a server/workstation memory problem, not Android orchestration or automatically Android-feasible inference.

## Decisions that should become formal ADRs later

1. Native durable run/effect journal and reconciliation semantics.
2. Capability descriptor, policy lattice and parameter-bound approval tokens.
3. Compile-time Play/Advanced/OEM capability-provider separation.
4. Provider/local-model streaming/error/fallback contract.
5. Skill IR, provenance, learning proposal and evaluation lifecycle.
6. AppFunctions, explicit IPC and MCP interoperability boundary.
7. Memory/data sensitivity, retention, encryption and prompt-routing policy.
8. UI snapshot/coordinate/target identity model.
9. Background scheduling and foreground execution mapping.
10. Test trajectory and release-gate architecture.

## Blocking conflicts versus deferred choices

Architecture freeze should be blocked until C02, C03/C04, C05/C07, C09/C10, C13, C14/C15, C19/C21 and C22 have explicit decisions and spike evidence. UI styling, exact dependency-injection library, Compose navigation structure, branding and broad OpenClaw wire compatibility can be deferred; they do not determine the correctness kernel.
