# Phase 0 Recommendation

## Decision

Proceed to a later requirements/architecture phase for a **new Android-native Kinetic kernel**. Do not fork or transplant any one reference application, do not make OpenClaw Gateway a hidden product requirement, and do not use a shell/container/Node/Python environment as the phone runtime.

The recommended foundation is:

1. a Room-backed durable run/effect journal and restart reconciler;
2. a provider-neutral planner/model loop;
3. a deterministic capability broker outside the model;
4. Android-native executors selected by compile-time distribution profile;
5. separate typed context, memory, skill and artifact stores;
6. optional adapters for AppFunctions, MCP, OpenClaw Gateway or isolated extensions;
7. an embedded local-model interface whose first benchmark candidate is OpenDroid's LiteRT-LM pattern.

OpenClaw Android is the strongest native component and reliability foundation, while OpenDroid is the closest self-contained Android agent reference. Kinetic should place a new kernel behind/adjoining the former's best native patterns and borrow the latter's practical provider/model/permission infrastructure selectively.

## What the source archaeology changes

The ecosystem does not contain a hidden “finished Kinetic” waiting to be renamed:

- OpenClaw Android is a capable native operator, but `Eco_reference/openclaw/apps/android/app/src/main/java/ai/openclaw/app/NodeRuntime.kt` opens operator/node Gateway sessions; model execution, canonical sessions/tasks, scheduling, memory, policy and delivery remain in `src/agents`, `src/tasks`, `src/cron`, `src/memory`, `src/gateway` and related host code.
- OpenDroid runs locally in `Eco_reference/opendroid/app/src/main/java/com/opendroid/ai/core/agent/AgentLoop.kt`, but its active coroutine, approval/input continuations and resume cursor are not reconstructable after process death even though plan snapshots reach Room.
- AndyClaw has the broadest native feature count but is GPL-3.0, privilege/ethOS-oriented, uses opaque model AARs and contains execution/security defects documented in `04_ANDYCLAW_ANALYSIS.md`.
- Hermes provides excellent agent semantics in `agent/conversation_loop.py`, `agent/transports/`, context/memory/skill/subagent/cron modules, but the implementation assumes Python and broad desktop tools. “Local” inference is usually an external HTTP endpoint.
- MobileRun provides the best UI-grounding/planner/trajectory ideas in `mobilerun/agent`, `tools/ui`, `macro` and tests, but it is a desktop Python controller whose decisive driver packages are external and whose archive lacks Git provenance.
- AnyClaw shows a compact descriptor/pipeline/MCP normalization pattern, but its shell/script/browser paths are unrestricted and its checkout has no tests.
- AirLLM reduces accelerator weight residency by repeatedly moving model layers; it has no Android runtime and exchanges memory for storage I/O/latency/energy.

## Recommended Kinetic product boundary

### Play Core

Play Core should be useful with:

- app-owned conversations, task planning and durable recovery;
- cloud providers and optional embedded models;
- JSON-schema/typed tool calling;
- app-private/SAF documents, user-mediated camera/media/contact/calendar/share flows;
- push-to-talk voice and visible, bounded live work;
- WorkManager-backed deferred/scheduled maintenance;
- built-in native capabilities and downloaded **declarative** skills;
- optional AppFunctions and narrowly authenticated network/MCP adapters;
- parameter-bound approvals, emergency stop, local audit and user data controls.

It should not depend on general AccessibilityService autonomy, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, SMS/call-log roles, package installation, overlays, root/Shizuku, arbitrary scripts, dynamic DEX/JAR/SO, exact alarms or an immortal foreground service. The technical/policy evidence is in `10_ANDROID_CAPABILITY_POLICY_MATRIX.md`.

### Advanced / research profile

An Advanced artifact may add cross-app accessibility, broader device integration, developer bridges or isolated extension providers only through the same descriptor/policy/journal interfaces. It needs a different manifest/source-set dependency graph and likely separate signing/update channel. OpenClaw's `apps/android/app/build.gradle.kts` plus `src/play`/`src/thirdParty` and `src/thirdParty/AndroidManifest.xml` prove this split is practical. A runtime toggle inside the Play binary is not separation.

### OEM / system profile

Privileged package management, secure settings, force stop, reboot, device policy and similar AndyClaw/ethOS surfaces belong only in an OEM/platform-signed or managed-device provider. They are not ordinary Advanced-app assumptions.

## Minimum self-contained control plane

To operate without OpenClaw Gateway, Kinetic needs native ownership of:

| Native responsibility | Minimum behavior | Strongest evidence source |
|---|---|---|
| Run coordinator | Bounded provider/tool state machine, typed failures, cancellation and checkpoint transitions | OpenClaw `packages/agent-core/src/agent-loop.ts`; Hermes `agent/conversation_loop.py` |
| Provider/router | Normalized messages, streams, tool calls, usage, terminal errors, privacy-aware fallback | Hermes `agent/transports/base.py`/`types.py`; OpenClaw `packages/llm-core/src/`; OpenDroid provider wrappers |
| Durable state | Session branches plus Run/Step/Attempt/Effect/Delivery, idempotency and unknown-outcome reconciliation | OpenClaw Android `chat/ChatCommandOutbox.kt`, `ClientDatabases.kt`; host `src/tasks`, `src/agents/sessions`; Hermes persist-before-tool behavior |
| Capability broker | Registry, schema/target validation, permissions/scopes, policy, confirmation, quotas and invocation-time recheck | OpenClaw Android `InvokeCommandRegistry.kt`/`InvokeDispatcher.kt`; OpenDroid `ActionSchema.kt`; security synthesis in `15_SECURITY_PATTERNS.md` |
| Native executors | Android APIs with typed results, lifecycle/permission failure and profile availability | OpenClaw Android handlers; selected OpenDroid actions/infrastructure |
| Context/memory | Derived request view, compaction, evidence-linked memory, retrieval and privacy budgets | Hermes `agent/context_engine.py`, `context_compressor.py`, `memory_provider.py`; OpenClaw memory contracts |
| Skills/learning | Provenance, progressive discovery, constrained IR, proposal/evaluation/version/rollback | OpenClaw `src/skills`; Hermes skill/learning files; bounded AnyClaw pipeline concepts |
| Scheduler | Persisted triggers, Android work admission, catch-up, limited headless capability set and delivery | OpenClaw `src/cron`; Hermes `cron`; WorkManager mapping in report 10 |
| Audit/artifacts | Non-droppable correctness journal plus privacy-bounded audit/trajectory/artifacts | OpenClaw `src/audit/audit-event-types.ts`; MobileRun trajectory/macro assets |
| Lifecycle reconciler | Process-death claim/repair, cancellation, ambiguous effect status and user notification | OpenClaw outbox/service/task recovery tests; negative evidence from OpenDroid/AndyClaw |

MCP, multi-agent routing, remote nodes, broad channel gateways, executable plugins and OpenClaw protocol parity are optional adapters, not minimum kernel responsibilities.

## Implementation-source stance for later phases

| Source pool | Later-phase stance |
|---|---|
| OpenClaw MIT Android code and narrow portable packages | Selective **REUSE/ADAPT** with notices, dependency review and tests |
| OpenDroid Apache-2.0 infrastructure | Selective **ADAPT**, retaining license/notices/modified-file requirements |
| Hermes MIT control-plane code | Mostly **PORT_SEMANTICS** into Kotlin; avoid Python architecture and unrestricted tools |
| MobileRun MIT archive | **PORT_SEMANTICS** only until exact commit and external dependency provenance are recovered |
| AnyClaw MIT descriptor/OpenAPI/pipeline | **ADAPT/CLEAN_ROOM** only for a typed bounded subset with a new test suite |
| AndyClaw GPL-3.0 | **CLEAN_ROOM/PROTOCOL_INTEROP_POSSIBLE** unless Kinetic deliberately becomes GPL-compatible |
| AirLLM Apache-2.0 | **RESEARCH_ONLY**; benchmark the residency idea, do not port the Python stack |

Path-level decisions and exceptions are in `11_LICENSE_PROVENANCE_MATRIX.md` and `17_REUSE_CANDIDATES.md`.

## Explicit answers to the 25 final questions

### 1. What already exists that Kinetic should not rebuild?

Do not rebuild from zero:

- OpenClaw Android's reconnect/uncertain-outcome transport, Room outbox, database separation, capability advertisement/dispatch, permission snapshot, TLS/device identity UX and many native camera/location/contacts/calendar/notification/media/voice handlers under `Eco_reference/openclaw/apps/android/app/src/main/java/ai/openclaw/app/`.
- OpenClaw's provider/tool/event schemas and tests in `packages/agent-core`, `llm-core`, `gateway-protocol`, `gateway-client`, `media-core`, `speech-core` and `tool-call-repair` where the selected boundary is truly portable.
- OpenDroid's `WrappedLLMProvider`, `SecurePrefs`, `ModelDownloadWorker`, `OnDeviceModelRegistry`, pure action/approval tests and Room migration fixtures.
- Hermes' normalized transport, persist-before-effect ordering, bounded recovery, request-only context selection, progressive skill disclosure and memory/subagent/cron state semantics.
- MobileRun's snapshot/coordinate contracts, action vocabulary, direct versus manager/executor strategies, guarded macro divergence and trajectory test ideas.

These should be adopted at the contract/test level or selectively adapted, not blindly copied as frameworks.

### 2. Which existing native Android implementation provides the best starting architectural foundation?

**OpenClaw Android** provides the best production-quality Android foundation: `GatewaySession.kt`, `ChatCommandOutbox.kt`, `ClientDatabases.kt`, `InvokeCommandRegistry.kt`, `InvokeDispatcher.kt`, native handlers, Play/third-party source sets and a large test corpus are stronger than rebuilding equivalents. It is not a self-contained agent and therefore needs a new native kernel.

**OpenDroid** is the closest self-contained architectural reference because `core/agent/AgentLoop.kt` actually plans/executes on the device and includes embedded inference. Use it to understand integration and gaps, not as the kernel wholesale.

### 3. What should be copied/adapted from permissively licensed projects?

Subject to future authorization and attribution:

- MIT OpenClaw: narrow Android components above; selected TypeBox/protocol contracts if interop is required; pure loop/media/speech/repair tests and algorithms.
- Apache-2.0 OpenDroid: provider retry/cancellation, secure secret/settings split, model download verification, typed action definitions, plan identity checks, migration and denial/error tests.
- MIT Hermes: normalized transport/data contracts, error classification, tool-loop signatures/caps, context/compaction and progressive skill/memory provider semantics—prefer Kotlin semantic ports.
- MIT AnyClaw: descriptor/import ideas and a bounded declarative pipeline only after security redesign and new tests.
- MIT MobileRun: only after the exact source revision is pinned; use planner/UI/macro/trajectory algorithms and test assets.

The exact files, notices and prerequisites are itemized in `17_REUSE_CANDIDATES.md`.

### 4. What should be clean-room reimplemented?

- AndyClaw agent loop, execution engine, tool/capability descriptor, memory/session design, extension protocol and local-model wrapper concepts because `Eco_reference/AndyClaw/LICENSE` is GPL-3.0 and several artifact origins are unresolved.
- A secure extension/AppFunctions/Binder protocol rather than either inconsistent AndyClaw extension path under `AndyClaw/.../extensions` and `app/src/main/aidl`.
- Kinetic's policy/approval schema, durable run/effect journal and Skill IR; no reference supplies the complete required contract.
- MobileRun semantics until its Git commit is recovered, and its Android adapter regardless, because the checked-in controller depends on external drivers.
- AnyClaw's pipeline interpreter if selected, excluding its current shell/script/browser behavior.

### 5. What should remain conceptual inspiration only?

- AirLLM dense/sparse residency and prefetch ideas (`air_llm/airllm/airllm_base.py`), pending Android benchmarks.
- AndyClaw's privileged ethOS/system tools, safety/heartbeat negative patterns and opaque Llamatik/Tinfoil/Whisper artifacts.
- OpenClaw/Hermes Node/Python plugin hosts, shells, package installers, desktop sandboxes and unrestricted skills.
- MobileRun's ADB/Portal recovery and prompt-enforced permission handling.
- AnyClaw's unauthenticated loopback/browser debugger/cookie bridge and arbitrary executable adapters.

### 6. What portions of OpenClaw would actually need native Kotlin equivalents for Kinetic to operate without a remote Gateway?

Native equivalents are required for the agent/provider loop (`packages/agent-core`, `llm-core`, `src/agents` semantics), canonical sessions/tasks/effects/delivery (`src/agents/sessions`, `src/tasks`), context/memory (`src/context-engine`, `src/memory`), scheduling (`src/cron`), policy/approvals/security (`src/security`, tool policy paths), skill catalog/runtime (`src/skills`), provider routing/fallback (`src/provider-runtime` and agent runner paths), audit and lifecycle reconciliation. A smaller native protocol between these Kotlin components should replace Gateway frames internally.

OpenClaw channel gateways, node-host shell/PTY, system agent, general plugins, remote nodes, multi-channel routing and full MCP breadth are not required for a self-contained MVP. `02_OPENCLAW_ANALYSIS.md` provides the package-by-package decomposition.

### 7. Can an Android-native Kinetic agent kernel realistically be implemented without Node, Python, Termux, or PRoot?

**Yes.** Nothing fundamental in planning, structured calls, provider HTTP/WebSocket streaming, policy, Room persistence, WorkManager scheduling, Android capabilities, AppFunctions/Binder/MCP serialization, memory retrieval or Compose UI requires those runtimes. OpenDroid demonstrates a local Kotlin loop; OpenClaw demonstrates native Android capabilities; both use coroutines/Room/Android APIs.

The cost is deliberate incompatibility with Tier 3 shell/Node/Python skills. That is an acceptable and desirable Core boundary, not a missing dependency.

### 8. Which features genuinely require NDK/JNI?

Potentially:

- a selected llama.cpp/GGUF or other native local LLM engine when no maintained Android/JVM package supplies the required performance/features;
- offline Whisper-class ASR, specialized VAD/audio DSP/codecs, or image kernels when measured Android APIs/libraries are insufficient;
- narrowly indispensable native acceleration/compatibility libraries with complete provenance.

OpenDroid's LiteRT-LM/AI Core path may avoid custom JNI because native work is packaged behind Android APIs. AndyClaw's `LlamaCpp.kt`/Whisper JNI proves a possible boundary but its code/artifacts should not be reused.

### 9. Which features absolutely do not require NDK/JNI?

Agent orchestration, planner/executor coordination, task/effect state machine, policy/approvals, capability registry, provider routing and HTTP/WebSocket clients, JSON/schema validation, Room/session/memory metadata, WorkManager/AlarmManager/FGS coordination, Android API tools, AppFunctions/Binder adapters, MCP encoding, secrets/Keystore calls, audit, Skill IR interpretation, Compose UI and onboarding. The references implement all analogous control-plane concerns in Kotlin, TypeScript, Python or Go rather than performance-critical native code.

### 10. What is the strongest local inference option already represented in the checked-out sources?

**OpenDroid's LiteRT-LM integration** is the strongest Play-oriented seed: `core/llm/providers/LiteRTLMProvider.kt` uses `com.google.ai.edge.litertlm.Engine`; `OnDeviceModelRegistry.kt` models Gemma/Qwen options; `ModelDownloadWorker.kt` handles WorkManager download/resume/temp/verification/load checks. `GemmaProvider.kt`/`HybridOnDeviceProvider.kt` add AI Core/ML Kit paths.

It is not production-ready: backend selection is currently CPU, several hashes/sizes/license fields are missing, and stream failure can be emitted as normal text. AndyClaw's GGUF wrapper is broader but GPL/opaque; AirLLM is not Android. A real-device benchmark remains a P0 question.

### 11. What Android lifecycle design is supported by the existing projects?

Persist intent/checkpoint/effect state first; let an Activity, coroutine, Worker or typed foreground service temporarily claim eligible work; reconcile after restart; never assume the service is immortal. OpenClaw Android's `ChatCommandOutbox.kt`, `ClientDatabases.kt`, `NodeForegroundService.kt` and reconnect tests are the best positive evidence. OpenDroid's orphanable RUNNING plans and AndyClaw's streamed-tool/heartbeat behavior are negative evidence.

Use WorkManager for deferrable persistent work, an eligible FGS only for active perceptible operations, AlarmManager narrowly for genuine exact user events, and Room for authority. A killed non-idempotent effect with no receipt becomes `unknown`, not automatically retried.

### 12. Which proposed Kinetic capabilities conflict with a Google Play distribution target?

Under the audit's current assumption: general LLM-planned AccessibilityService automation; broad/all-files storage; broad package inventory; package installation/self-update/dynamic executable code; arbitrary shell/Node/Python/plugin runtimes; root/Shizuku; privileged device/admin controls; SMS/call-log access absent an eligible core/default-handler role; perpetual/special-use FGS; background microphone/location/screen capture as general autonomy; overlays/VPN/battery-optimization exemption without a qualifying core purpose. Exact categories and alternatives are in `10_ANDROID_CAPABILITY_POLICY_MATRIX.md`.

### 13. What architecture could separate Play-safe execution from advanced execution without forking the entire codebase?

One domain/kernel module and one capability descriptor/policy/journal contract, with implementation-provider modules/source sets such as `play`, `advanced` and optional `oem`. Each profile supplies a compile-time allowlist, manifest overlay, dependencies, executors and availability facts. Play uses hard stubs/absence for advanced tool IDs; Advanced may add accessibility/privileged adapters. CI must scan the final Play manifest/dependency/source surface. OpenClaw's flavor split is the concrete precedent.

If signing/update policy requires separate application IDs, share protocol/data migration—not APK permissions or hidden code paths.

### 14. Which ecosystem skill formats could realistically become a native Kinetic skill representation?

- OpenClaw/Hermes `SKILL.md` frontmatter, description index, procedural text, references/assets and requirement metadata can be import source for Tier 1 after compilation/validation.
- AnyClaw's bounded `fetch/select/map/filter/limit` idea can become typed explicit dataflow, not its `[]map[string]any`/browser/script implementation.
- Reviewed OpenAPI operations can be imported into typed capabilities with preserved parameter locations/output schemas/auth destinations.
- AppFunctions metadata and built-in Kinetic descriptors can be projected into the same catalog.

Shell, Python, Node, npm, external CLIs, arbitrary filesystem, browser `evaluate` and native addons remain Tier 3. A restricted JavaScript Tier 2 is optional, not necessary for the strongest value. See `13_SKILL_AND_PLUGIN_COMPATIBILITY.md`.

### 15. Can Hermes-style skill learning be transformed into declarative rather than unrestricted executable skills?

**Yes, but it is a redesign.** Hermes `agent/learn_prompt.py::build_learn_prompt` currently turns `/learn` into an ordinary agent request to gather evidence and call `skill_manage`; it explicitly has no separate distillation engine. Kinetic should retain user-triggered evidence capture, progressive disclosure, provenance, archive/version and usage feedback, then use:

`evidence -> Knowledge/SkillProposal -> typed IR compile -> static policy/type checks -> simulation/fixtures -> user review -> signed/versioned activation -> per-call authorization -> success/failure feedback/rollback`.

A learned skill can only compose or narrow existing capabilities. It cannot add a runtime, permission, destination or approval exemption.

### 16. Which AndyClaw extension concepts should be combined with Android AppFunctions?

Keep the concept of a discoverable typed function descriptor, app/package provider identity, signing identity, per-function permissions/approval, UID separation and normalized success/error/denied/timeout states from `AndyClaw/.../extensions` and `skills/ToolDefinition.kt`.

Combine those with AppFunctions discovery/invocation as one `CapabilityProvider`: translate function metadata into a Kinetic descriptor, overlay Kinetic risk/data/network/audit policy, authorize parameter-bound calls, normalize typed results and journal attempts. Do **not** copy AndyClaw's raw `IExtension` transaction, suffix-based provider, uncorrelated broadcast, fire-and-forget activity or unfinished incompatible `IAndyClawSkill.aidl`. A Binder fallback needs signature permission, caller UID/certificate checks, version/request/idempotency IDs, death/cancel/deadline and bounded result contract. AppFunctions remains preview/unproven, so it cannot be the only integration path yet.

### 17. Which MobileRun planner/executor and trajectory ideas are worth porting?

- Replaceable direct FastAgent and manager/executor strategies over one action layer (`mobilerun/agent/fast_agent`, `manager`, `executor`, `droid`).
- `UIState`/formatter semantics where element indices are snapshot-local; explicitly typed device/screenshot/normalized coordinate transforms (`tools/ui`, `tools/helpers/coordinate.py`, coordinate tests).
- A sealed action vocabulary with structured outcomes rather than raw gesture strings.
- Macro v2 semantic pre-state guard, divergence detection and handoff (`macro/state.py`, `matcher.py`, `replay.py`, `handoff.py`).
- Event/trajectory records for evaluation and replay, with the important correction that the durable effect journal is separate and non-droppable.
- Versioned/signed app-specific guides inspired by app cards, not raw remote Markdown as trusted prompt policy.

Do not port ADB/Portal recovery, prompt permission handling, Python/LlamaIndex plumbing or the assumption that trajectories are checkpoints.

### 18. Which OpenDroid pieces are production-quality enough to adapt?

The best candidates are `WrappedLLMProvider` retry/cancellation/partial-stream rules; `SecurePrefs` + settings secret split; `ModelDownloadWorker` resume/temp/hash/load-check workflow; the small `ActionSchema` implementation; VoiceApprovalParser; plan/session identity checks that reject stale completions; Room schema/migration fixtures; and crash-log capture-time redaction as defense in depth. Carry the pure AUTO/fallback approval behavior as `PORT_SEMANTICS` and test vectors, not as the final Kinetic policy model.

They still need Kinetic policy/data/lifecycle hardening. Do not adapt `AgentLoop.kt` wholesale, broad permission groups, accessibility automators as Core, `fallbackToDestructiveMigration`, YOLO bypass or LiteRT error-as-content behavior.

### 19. Which OpenClaw Android components are already better than rebuilding equivalents?

- `gateway/GatewaySession.kt` request-generation lease, reconnect, cancellation, timeout and ambiguous-outcome classification;
- `chat/ChatCommandOutbox.kt`, `ClientDatabases.kt`, `ChatController.kt` journaling/projection/reconciliation;
- `node/InvokeCommandRegistry.kt`, `InvokeDispatcher.kt`, `AndroidPermissionSnapshot.kt` availability/execution boundary;
- `gateway/GatewayTls.kt`, device auth/registry UX, with a hardware-backed identity improvement;
- bounded native camera/location/contacts/calendar/notification/photo/media/audio/speech/TTS handlers and tests;
- Play/third-party source-set hard separation;
- advanced accessibility snapshot/action freshness/redaction safeguards as Advanced-only reference;
- the Android unit/macrobenchmark/license-notice test architecture.

### 20. What are the ten largest technical risks?

1. **Durable effect correctness:** process death or timeout duplicates/loses non-idempotent actions; OpenDroid/AndyClaw expose this gap.
2. **Planner/tool reliability:** small/local/cloud models hallucinate tools/arguments or enter repair loops; strict schemas and evaluation are unproven.
3. **Local-model feasibility:** acceptable structured-agent quality, RAM, TTFT, speed, thermal, battery and device coverage are unknown.
4. **Android lifecycle variability:** Doze, force-stop, OEM task killers, FGS restrictions and notification denial disrupt long workflows.
5. **UI grounding:** app updates, rotation, scroll, IME, foldables and stale screenshots/nodes can target the wrong control.
6. **Provider fallback after partial output:** route changes can duplicate text/tool intent or leak data from local to cloud.
7. **Capability/policy complexity:** a descriptor too weak creates escapes; one too broad becomes unimplementable/incomprehensible.
8. **Skill/extension interoperability:** importing rich ecosystems without Node/Python may either lose value or recreate unsafe runtimes.
9. **Memory/context quality:** retrieval, compaction and learned knowledge can become stale, poisoned or too expensive.
10. **Performance footprint:** simultaneous model, media, Room, network, voice and trajectory work can cause jank, storage pressure and battery drain.

### 21. What are the ten largest Play Store/policy risks?

1. General autonomous AccessibilityService planning/execution.
2. SMS and call-log permissions without qualifying default-handler/core purpose.
3. `MANAGE_EXTERNAL_STORAGE` or broad file-agent behavior.
4. `QUERY_ALL_PACKAGES` and installed-app inventory beyond targeted visibility.
5. `REQUEST_INSTALL_PACKAGES`, self-update or remotely downloaded DEX/JAR/native executable code.
6. Persistent/special-use foreground service used as an agent daemon, invalid background starts or undeclared FGS types.
7. Background/continuous location, microphone or camera capture without a narrowly qualifying visible purpose.
8. NotificationListener/MediaProjection content collection and insufficient prominent consent/minimization.
9. Overlay/VPN/ignore-battery-optimization/exact-alarm special access without a core eligible purpose.
10. Interpreted/downloaded scripts or declarative packages whose host APIs allow policy violations, plus inaccurate Data safety/retention/deletion disclosures.

These are current engineering risk classifications, not legal advice or guarantees of review outcome.

### 22. What are the ten largest security/privacy risks?

1. Prompt injection in web/email/notification/memory/skill/UI content causing an effect.
2. Approval spoofing, replay or time-of-check/time-of-use target substitution.
3. Provider/API/extension secrets entering prompts, logs, crash reports, trajectories or backups.
4. Malicious or compromised extension/AppFunction/MCP caller/provider gaining ambient phone authority.
5. Unsigned/swapped/unlicensed model or skill supply-chain artifacts and executable remote content.
6. Screenshots, accessibility trees, notifications, contacts, messages, location or voice leaking to cloud/storage/analytics.
7. SSRF, redirect/DNS rebinding, credential-origin misuse or arbitrary network exfiltration from imported tools/OpenAPI/pipelines.
8. Unknown-outcome retries or concurrency races duplicating external communication, financial, delete or account effects.
9. Long-term memory/learned-skill poisoning, staleness and cross-profile data leakage.
10. Excessive audit/trajectory retention, weak export/delete controls or restored data/approval tokens on another device.

### 23. What are the five strongest architectural opportunities for making Kinetic genuinely different from existing Android agents?

1. **A durable effect-native Android kernel:** model every external action as a checkpointed, idempotency-aware, reconcilable effect instead of wrapping an agent coroutine in a service.
2. **One capability fabric:** native tools, AppFunctions, explicit IPC, MCP and optional remote/OpenClaw adapters normalize into the same policy/approval/audit contract.
3. **Compile-time honest capability profiles:** one kernel with Play, Advanced and OEM providers—no hidden broad powers and no wholesale codebase fork.
4. **Privacy/resource-aware hybrid inference:** route by structured-tool quality, sensitivity, connectivity, latency, cost, battery and thermal state, with explicit local-to-cloud escalation.
5. **Declarative learned skills with evidence:** convert trajectories and user teaching into typed, tested, reviewable, reversible workflows rather than downloaded executable packages.

### 24. What assumptions in the original Kinetic concept are demonstrably wrong based on the source code?

- An Android OpenClaw app does **not** imply the OpenClaw control plane already runs on Android; it remains Gateway-dependent.
- A foreground service does **not** provide systemd-like durability; even the stronger OpenClaw client needs Room/outbox/restoration.
- “Uses Room” does **not** prove process-death recovery; OpenDroid can orphan RUNNING plans.
- Embedded V8/JavaScript does **not** supply Node/npm/shell/native-addon compatibility; ecosystem counts show extensive Tier 3 dependencies.
- General autonomous accessibility is **not** a safe Play foundation under the stated/current policy constraint.
- “Local provider” often means Ollama/LM Studio/llama.cpp server, not embedded phone inference.
- AirLLM fitting a huge model in low VRAM does **not** make it interactive or energy-feasible on Android; dense generation re-reads weights.
- JNI is **not** a faster/default orchestration architecture; its demonstrated value is local inference/audio.
- A declarative-looking YAML/Markdown package is **not** necessarily declarative; AnyClaw/OpenClaw/Hermes packages can execute shell/Python/Node/browser code.
- A root open-source license does **not** make every subtree/model/binary reusable; Hermes restrictions, AndyClaw GPL/opaque artifacts and AirLLM header inconsistency prove otherwise.

### 25. What assumptions remain unproven and require spikes before architecture freeze?

The P0 set is:

1. exact Play Core journeys and distribution/profile identity;
2. formal run/effect/reconciliation state machine;
3. descriptor/policy/target-bound approval expressiveness;
4. embedded model quality/performance/battery/device coverage and model license;
5. typed streaming/partial-output/fallback conformance;
6. AppFunctions availability, caller identity, confirmation and fallback viability;
7. secure extension/MCP interoperability requirements;
8. Skill IR coverage of representative ecosystem workflows and learned-skill validation burden;
9. UI snapshot/coordinate accuracy across real devices/app versions;
10. sensitive-data/memory/audit/backup policy;
11. WorkManager/FGS/alarm reliability across OEMs;
12. whether OpenClaw Gateway/MCP/subagents/hierarchical planning are launch requirements at all.

Each spike and exit criterion is specified in `19_OPEN_QUESTIONS.md`. None was executed in Phase 0.

## Ten decisions to freeze before application scaffolding

1. Play Core user journeys and compile-time profile boundary.
2. Run/Step/Attempt/Effect/Delivery lifecycle, idempotency and unknown-outcome rules.
3. Provider stream/error/fallback and cancellation contract.
4. Capability descriptor/policy/approval/identity model.
5. Initial built-in tool set and Android least-privilege/API paths.
6. Context/memory/data sensitivity/retention/backup model.
7. Skill IR and learning proposal/evaluation lifecycle.
8. Local inference engine/model/device support decision backed by benchmarks.
9. AppFunctions/IPC/MCP adapter scope and trust model.
10. Test trajectory, task corpus, process-death/device/policy release gates.

## Final recommendation

Kinetic is technically realistic as a native Android agent without Node, Python, Termux or PRoot, but only if it rejects desktop compatibility as the organizing principle. The differentiator should not be “more permissions” or “OpenClaw on a phone.” It should be **recoverable effects, capability-native security, honest Android distribution profiles, embedded/remote provider choice, and declarative evidence-backed skills**.

Phase 0 supports moving forward to requirements work after the P0 questions are accepted as explicit gates. It does not support implementing the app yet, choosing AirLLM/AndyClaw as the runtime, or promising Play-safe arbitrary cross-app autonomy.
