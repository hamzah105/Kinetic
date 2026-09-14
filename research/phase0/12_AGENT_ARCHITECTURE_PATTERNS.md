# Agent Architecture Patterns

## Executive finding

No checked-out repository is a suitable whole-application base for Kinetic. The strongest architecture is a **composition of invariants**:

- OpenClaw supplies the best native Android edge, durable outbox/projection patterns, broad protocol/state-machine evidence and mature failure tests.
- Hermes supplies the clearest provider-neutral loop, context selection, progressive skills, memory-provider seam, bounded recovery and subagent semantics.
- OpenDroid supplies the closest self-contained Android app and practical Android/provider/model plumbing, while proving that a coroutine-owned loop plus plan snapshots is not durable execution.
- MobileRun supplies the best UI-state, coordinate, trajectory and hierarchical planner/executor contracts, but it is an external Python/ADB system.
- AndyClaw supplies useful Kotlin execution, extension IPC, parallelism and local-model ideas, constrained by privileged-device assumptions and GPL-3.0.
- AnyClaw and AirLLM are narrow experiments: declarative adapters/MCP and layer-streamed inference respectively, not application foundations.

Kinetic should have one small Android-native kernel in which the durable run/effect journal, policy broker and typed capability registry are independent of the selected model, planner strategy, skill representation and executor implementation.

## Observed architectural shapes

| Reference | Actual control-plane shape | Most useful invariant | Do not inherit |
|---|---|---|---|
| OpenClaw | Node Gateway owns agent/session/task/cron/tool/plugin logic; Android is a native authenticated node/client with capabilities and durable client state | Capability advertisement is separate from invocation-time availability; persist intent/outbox before network/effect; explicit lifecycle statuses | Gateway dependency, Node runtime, desktop shell/plugin surface, huge protocol as Kinetic domain model |
| Hermes | Python facade initializes provider/tool/memory/skill/plugin services; a provider-neutral conversation loop owns normalized calls and repair | Derived request context is separate from durable transcript; bounded typed recovery; persist tool intent before side effect | Python/in-process plugins, terminal/code execution, process-local leases as durable authority |
| OpenDroid | One Compose/Hilt/Room app; `AgentLoop` plans and dispatches native actions while ViewModel/service own active jobs | Pure action/approval decisions and practical Android provider/download integration | In-memory approval continuations and cursor, all-app accessibility as core completion path, broad manifest |
| AndyClaw | Android library/app split; execution engine, native skills, APK extensions, services and local/cloud LLMs | Typed tools, isolated extension concept, parallel execution stages, local model seam | GPL source, ethOS/system privileges, opaque AARs, default-off safety |
| MobileRun | Desktop Python direct agent or manager/executor drives mobile device adapter and records trajectories/macros | Snapshot-bound UI targets and named coordinate semantics; planner strategies share one action seam | ADB/Portal repair, Python runtime, prompt-enforced policy, trajectory-as-checkpoint assumption |
| AnyClaw | Go daemon/CLI turns package manifests into CLI/script/pipeline/OpenAPI commands and MCP tools | Compact typed package-to-tool normalization | `sh -c`, arbitrary scripts/browser evaluation, shallow policy, plaintext credential model |
| AirLLM | Python model loader streams transformer layers/experts between storage, CPU and accelerator | Make model residency/storage strategy replaceable and observable | PyTorch/CUDA/MLX implementation as Android runtime or agent architecture |

## Candidate Kinetic component boundaries

This is a Phase 0 decomposition, not a final package graph or API design.

```text
UI / voice / app-owned deep link / approved system event
                         |
                 Interaction Controller
                         |
       +-----------------+------------------+
       |                                    |
 Durable Session/Run Store           Context Assembler
       |                         memory + skills + UI snapshot
       |                                    |
       +----------------> Planner Runtime <-+
                              |
                    normalized proposed calls
                              |
                       Capability Broker
                policy + schema + confirmation
                              |
              +---------------+----------------+
              |               |                |
       Native executors    AppFunctions   Isolated/remote adapter
              |               |                |
              +---------------+----------------+
                              |
                 Effect Journal / Reconciler
                              |
              redacted result + audit projection
```

Cross-cutting services are `ProviderRouter`, `LocalModelEngine`, `TaskScheduler`, `MemoryStore/Retriever`, `SkillCatalog/Compiler`, `AuditStore`, `SecretStore`, `NetworkPolicy`, `ArtifactStore` and `ResourceBudget`. The planner never receives direct references to an Android `Context`, secrets, Binder objects or unrestricted HTTP/filesystem clients.

## 1. Durable run model

### Reference evidence

- OpenClaw Android's `apps/android/app/src/main/java/ai/openclaw/app/chat/ChatCommandOutbox.kt` and `ClientDatabases.kt` separate queued intent from a reconstructable Gateway projection. `NodeForegroundService.kt` and reconnect tests demonstrate service state restoration without claiming a service is immortal.
- OpenClaw host task/session/cron roots under `src/tasks/`, `src/agents/sessions/` and `src/cron/` distinguish task attempts, terminal states, session branches and restart reconciliation.
- Hermes `agent/conversation_loop.py` incrementally persists the assistant tool-call message before dispatching tools, then persists results; `agent/tool_dispatch_helpers.py` plans safe parallel segments.
- OpenDroid `core/agent/AgentLoop.kt` persists conversations and plan/step results, but the active coroutine, proposal identity, input/approval continuations and resume cursor remain process-local. `core/service/OpenDroidService.kt` does not scan and resume unfinished work at startup.
- MobileRun `mobilerun/agent/trajectory/writer.py` and `mobilerun/agent/utils/trajectory.py` recording is bounded observability and may omit artifacts; it is not a transactional execution ledger.

### Required Kinetic invariant

The durable kernel should model at least:

| Entity | Required facts |
|---|---|
| `Session` | User/profile, context epoch, branch/parent, lifecycle and retention class |
| `Run` | Intent reference, planner/provider/model versions, policy version, budget, current state |
| `Step` | Proposed typed operation, dependencies, snapshot/context reference, retry policy |
| `Attempt` | Start/end, executor identity, deadline, cancellation and typed failure |
| `Effect` | Idempotency key, canonical argument digest, approval reference, dispatch/outcome/reconciliation state |
| `Delivery` | User-visible result/notification destination, attempts and acknowledgement |
| `Artifact` | Screenshot/model/tool output by content hash, sensitivity, encryption and expiry |

State transitions are append-first or transactional. A process-local coroutine is only the current lease holder. On restart, a reconciler claims eligible work, marks abandoned attempts, checks effect-specific outcome, and resumes from persisted state.

For non-idempotent effects, `unknown` is a real terminal/reconciliation state. “Retry after timeout” cannot be the default for messaging, payment, deletion, posting or account changes.

## 2. Planner and agent-loop contract

### What transfers

Hermes' `agent/transports/types.py` defines a normalized response with content, tool calls, finish reason, reasoning, usage and provider-only sidecar. `agent/transports/base.py` separates message/tool conversion, request construction, response normalization and validation. OpenClaw `packages/agent-core/src/agent-loop.ts` and `packages/llm-core/src/` similarly expose provider-neutral loop/event types. MobileRun proves that a direct ReAct strategy and a manager/executor strategy can share a device-action layer.

Kinetic should make the strategy replaceable:

- `DirectPlanner`: one bounded loop for ordinary turns;
- `HierarchicalPlanner`: manager creates checkpointed goals and a constrained executor handles one subgoal at a time;
- `DeterministicWorkflowPlanner`: compiled Skill IR/macros with model assistance only at declared choice points;
- `HumanPlan`: user-authored or confirmed steps.

All strategies produce the same typed `CandidateInvocation` and consume the same `CapabilityResult`. They cannot bypass policy or persistence.

### Recovery must be data, not tangled exception branches

Hermes' `agent/error_classifier.py`, provider transports and conversation loop distinguish credential rotation, compression, fallback, content filtering, malformed calls, invalid reasoning signatures and bounded continuation. OpenClaw `packages/tool-call-repair/` handles several defective structured-call formats behind limits. OpenDroid's `WrappedLLMProvider` in `core/llm/LLMProviderFactory.kt` preserves cancellation, uses bounded retry/jitter and does not replay a stream once tokens have escaped.

A Kinetic recovery decision should carry:

- failure class and source layer;
- retryability and idempotency precondition;
- attempt/backoff/deadline budget;
- whether context compaction or provider/model fallback is allowed;
- whether any partial content/effect was emitted;
- user-visible action and audit reason.

Malformed arguments generate a tool error; they never reach an executor. Provider fallback cannot silently change privacy class (for example local to cloud) without route policy and user preference.

## 3. Provider routing and local inference

The provider seam should normalize request, streaming events, tool calls, usage, terminal error and cancellation. Provider-specific opaque data stays inside the adapter and must be serializable if it is needed for replay.

Useful evidence:

- Hermes `agent/transports/` is the broadest provider-neutral contract and supports provider-specific replay data.
- OpenClaw `packages/llm-core/` and host provider/runtime code provide event and retry semantics.
- OpenDroid `core/llm/providers/LiteRTLMProvider.kt`, `GemmaProvider.kt` and `HybridOnDeviceProvider.kt` demonstrate embedded/on-device routing, though one LiteRT stream path emits an error as ordinary text.
- AndyClaw `app/.../llm/LlamaCpp.kt` provides a GGUF engine-shaped seam, but its Llamatik AAR is opaque in this checkout and the repository is GPL-3.0.
- AirLLM demonstrates storage/residency strategy separation, not a mobile API.

The Kinetic interface should not call a model “local” merely because it is on localhost/Ollama. It should expose execution locality, network use, model provenance, context limit, structured-output support, concurrency, memory estimate and thermal/resource policy. See `14_LOCAL_MODEL_OPTIONS_FOUND.md`.

## 4. Capability broker and executor boundary

### Reference lessons

- OpenClaw Android's `node/InvokeCommandRegistry.kt` advertises only available methods and `InvokeDispatcher.kt` rechecks availability before executing. Foreground-only operations return structured failures and mutable canvas operations are serialized.
- Hermes `tools/registry.py` provides schema, handler, availability, async/result-size behavior and provenance. `agent/tool_dispatch_helpers.py` treats unknown/unparseable calls as sequential barriers and parallelizes only permitted segments.
- OpenDroid `core/agent/ActionSchema.kt` provides typed parameters/categories, but approval is still mainly action-name based.
- AndyClaw `skills/ToolDefinition.kt` adds input schema, approval and Android permissions; extension adapters return explicit denied/timed-out/error states.
- AnyClaw shows why a manifest must not map directly to a shell command.

### Kinetic requirement

Every invocation follows:

`resolve descriptor -> validate closed schema -> resolve targets -> compute resource/effect scopes -> policy decision -> confirmation if required -> journal -> execute -> normalize outcome -> journal/audit`.

Advertisement is not authorization, installation is not authorization, Android permission is not Kinetic authorization, and a skill's tool list is not authorization. The non-final metadata requirements are in `15_SECURITY_PATTERNS.md`.

Executors use typed arguments. There is no generic “run string command” in Core. File operations use Storage Access Framework grants/app-private storage; network calls use destination policies; third-party app integration prefers intents, app-owned deep links, AppFunctions or documented provider APIs.

## 5. Parallelism, cancellation and subagents

### Reference lessons

- Hermes batches only maximal parallel-safe runs; overlapping filesystem targets and unknown effects become sequential barriers. Internal child agents have explicit lifecycle states, but budgets are per-agent rather than one aggregate tree budget.
- AndyClaw contains a staged execution engine and `ParallelExecutionEngineTest.kt`; its exact Kotlin implementation is GPL, but the testable effect-graph idea is useful.
- OpenClaw has lane/session/task ownership, abort/timeout tests and subagent/session tools; the host implementation is too coupled to port.

### Kinetic requirement

Parallel scheduling is based on declared/resolved effect scopes, not model assertion or a name list. Examples:

- two read-only calls with disjoint or shareable resources may run together;
- two writes to the same document/contact/account serialize;
- an unknown scope is a barrier;
- user confirmations serialize around the exact invocation they authorize;
- screen/UI actions serialize against one snapshot/window epoch;
- external communication and financial effects use hard per-run and per-time-window budgets.

A cancellation signal has run, step and attempt scope. It stops planner/model/network work promptly, but a dispatched effect is reconciled according to executor semantics. Subagents receive an attenuated capability view and child budget. The parent owns aggregate time/token/effect/battery budgets and cannot create an unbounded fan-out tree.

## 6. Context, memory and prompt construction

Hermes is the strongest reference:

- `agent/turn_context.py` and prompt builders compose stable system/tool/skill/memory context;
- `agent/conversation_loop.py::_apply_context_engine_selection` derives a request-only copy rather than mutating durable history;
- `agent/context_engine.py` separates selection, compression and pruning;
- `tools/memory_tool.py` and `agent/memory_provider.py` separate a local durable store from an optional retrieval provider.

OpenDroid's Room memory entities and OpenClaw's memory host contracts add persistence/embedding variants, but neither is a ready Kinetic memory policy. MobileRun's UI state belongs in a short-lived grounded context, not long-term memory.

Kinetic should maintain distinct stores:

- immutable/redacted conversation and run events;
- user-visible semantic/preferences memory with evidence, scope, sensitivity, expiry and version;
- procedural skills/workflows with provenance and evaluation state;
- ephemeral working context and UI snapshots;
- large encrypted artifacts referenced by hash.

The `ContextAssembler` produces a bounded `ModelContextView` from those stores. It labels untrusted content, omits secret values, records source/version references and never changes durable history just to fit a provider request. Memory writes during a turn become visible according to an explicit snapshot epoch.

## 7. Skills, learning and plugins

OpenClaw and Hermes use compact description indexes plus progressive loading of `SKILL.md` and support resources. Hermes `/learn` is an ordinary agent turn that gathers material and asks `skill_manage` to write a skill; background review is another generative curation pass. Neither proves learned procedures correct.

Kinetic's safe architecture is:

`source skill -> parse/provenance -> capability resolution -> compile constrained Skill IR -> static policy checks -> simulation/test -> user review -> versioned activation -> per-run reauthorization`.

A learned skill can compose existing capabilities and narrow their parameters. It cannot introduce a new executor, Android permission, network host or confirmation bypass. Tiering and compatibility details are in `13_SKILL_AND_PLUGIN_COMPATIBILITY.md`.

General in-process plugin systems from OpenClaw/Hermes and arbitrary script adapters from AnyClaw stay outside Core. If an Advanced profile supports extension packages, they communicate over typed, authenticated, bounded IPC and receive capability-specific handles—not host objects or a generic broker superuser.

## 8. UI grounding and mobile action semantics

MobileRun's `UIState`/action paths and coordinate tests establish a particularly valuable rule: a target is meaningful only relative to the exact snapshot and coordinate transform from which it was derived. OpenClaw third-party Android improves this with bounded accessibility snapshots, redacted password/editable content and stale reference/package/UI-epoch checks. OpenDroid has broader automators but weaker durable grounding.

Kinetic target types should distinguish:

- semantic element reference bound to snapshot/window/app/version;
- physical display pixels;
- screenshot pixels and crop/scale/rotation transform;
- normalized coordinates;
- app-owned view/action identity.

Raw `(x, y)` is not a cross-layer contract. Before execution, the adapter revalidates snapshot epoch, foreground package, orientation, window identity and sensitive-field policy. General cross-app accessibility remains an Advanced capability; Core uses app-owned UI, documented intents/providers and AppFunctions where possible.

## 9. Background autonomy and scheduling

OpenClaw cron/task code and Hermes `cron/` show rich admission, scheduling, concurrency and delivery states, while Android platform rules require a narrower mapping. OpenDroid's long-running service is not a durable scheduler.

Kinetic should distinguish:

- **persistent deferrable work:** WorkManager;
- **user-visible active work:** foreground service with declared type/notification and start eligibility;
- **precise user-facing alarm:** AlarmManager only when exactness is core and policy permits;
- **in-app continuation:** coroutine scoped to visible UI, checkpointed if correctness matters;
- **external event:** explicitly declared receiver/provider/AppFunction route, with current policy re-evaluation.

Scheduled work persists intent and a delivery policy, not a suspended agent coroutine. At fire time, it reconstructs a minimal context, applies current permissions/network/battery/profile policy, respects quotas and asks for user presence where the effect class requires it. A schedule cannot capture a permanent confirmation token.

## 10. Remote bridges, MCP and Android IPC

OpenClaw's Gateway protocol is mature but much larger than Kinetic's minimum; MobileRun and AnyClaw expose MCP from desktop/daemon processes. AndyClaw demonstrates APK/Binder-style extension discovery. Android AppFunctions provide an experimental typed app-to-app direction on supported devices, covered in `10_ANDROID_CAPABILITY_POLICY_MATRIX.md`.

Recommended boundary:

- Kinetic's domain models remain transport-neutral.
- Optional OpenClaw/MCP/cloud/desktop bridges are adapters that normalize discovered operations into Kinetic descriptors.
- Remote identity is authenticated and mapped to a local user/profile; remote callers never inherit the phone user's broad ambient authority.
- Capability availability, policy and confirmation are evaluated on the device at call time.
- IPC schemas are closed/versioned, payloads and time bounded, sensitive artifacts use narrow URI grants, and caller signing/package identity is verified.
- The Play build does not download executable plugin code or remote DEX/native libraries.

## Architectural conflicts that must be resolved before implementation

1. **Authority:** action-name allowlists and prompt safety versus parameter/target/data-scoped deterministic policy.
2. **Durability:** coroutine/service-owned execution versus a transactional run/effect journal.
3. **Portability:** desktop Node/Python/shell plugins versus Android-native declarative capabilities.
4. **Autonomy:** cross-app accessibility completion versus Play-safe public APIs/AppFunctions and honest unavailability.
5. **Inference:** one provider interface versus differing cloud/local privacy, streaming, grammar, memory and thermal behavior.
6. **Memory:** convenient raw transcript/Markdown storage versus sensitive, evidence-linked, expiring user-controlled records.
7. **Learning:** autonomous text mutation versus proposed, compiled, evaluated and reversible workflows.
8. **Concurrency:** opportunistic batching versus declared effect scopes, aggregate budgets and crash reconciliation.
9. **Profiles:** runtime flags around dangerous code versus compile-time-separated Core and Advanced capability providers.

`18_ARCHITECTURAL_CONFLICTS.md` records the decision matrix in detail.

## Phase 0 recommended invariants

These are the architecture-level acceptance criteria for a later design phase:

1. The model can propose but cannot authorize or execute.
2. Durable state is authoritative; services/coroutines are leases.
3. Tool intent is journaled before an external effect.
4. Every non-idempotent effect supports explicit unknown-outcome reconciliation.
5. Provider failure is typed; error text is never a successful token stream.
6. Context selection never mutates the durable transcript.
7. Capability policy is applied again at execution time.
8. Parallelism requires declared/resolved non-conflicting resource scopes.
9. Child agents and scheduled runs receive attenuated capabilities and aggregate budgets.
10. UI targets are snapshot/viewport bound and stale targets fail closed.
11. Learned skills compose existing authority only and remain versioned/reversible.
12. Secrets do not enter model context, ordinary logs or general audit content.
13. Core and Advanced capability surfaces are compile-time separable and testable.
14. Android process death, permission denial, network loss and cancellation are normal state transitions, not exceptional afterthoughts.

## Phase 0 disposition

Proceed to a later architecture design around a native durable kernel, but do not select a reference repository as the base. The best clean starting sequence is conceptually: OpenClaw Android's durable edge and capability boundary + Hermes' provider-neutral loop/context contracts + OpenDroid's Android/provider/model plumbing + MobileRun's grounded action contracts, all rewritten behind the independent Kinetic policy/effect journal described here.
