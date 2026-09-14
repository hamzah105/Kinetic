# Hermes Agent forensic analysis

## Audit identity and bottom line

- Reference root: `E:\Projects\Eco_reference\hermes-agent`
- Upstream recorded by the checkout: `https://github.com/hamzah105/hermes-agent`
- Branch / revision: `main` / `87bc710609f8b89b6e6b4aa418dde8ee30ec6873`
- Revision date / subject: `2026-08-01T11:40:59-07:00` / `fix(agent): scope parallel batches from V4A patch headers`
- Declared package: `hermes-agent` `0.19.1`, Python `>=3.11,<3.14` (`pyproject.toml` `[project]`).
- Audit mode: static, read-only implementation archaeology. No reference files were changed and no Hermes process, model, network provider, gateway, or scheduled job was started.

Hermes is the strongest reference here for **agent orchestration semantics**, not for an Android runtime. Its most reusable ideas are the typed provider/transport seam, progressive skill loading, separated operational memory versus procedural skills, request-only context selection, crash-safe tool-result persistence, bounded tool-loop repair, context compaction, and explicit subagent lifecycle states. The Python implementation, unrestricted in-process plugins, desktop shell/tool surface, and Termux deployment model should not be ported into the Play-distributed Kinetic core.

Recommended disposition:

| Area | Disposition | Kinetic use |
|---|---|---|
| Normalized provider response, tool call/result, memory/context contracts | **ADAPT_ALGORITHM** | Re-express as sealed Kotlin interfaces and immutable data classes. |
| Agent loop repair, compaction, skill progressive disclosure, learning review | **ADAPT_ALGORITHM** | Preserve invariants and tests, not Python control flow. |
| Gateway routing/session/turn-lease concepts | **ADAPT_ALGORITHM** | Use durable Android-native session ownership; do not copy the process-local fail-open lease. |
| Cron job state machine | **ADAPT_ALGORITHM** | Map to WorkManager/AlarmManager with explicit delivery and idempotency semantics. |
| Python plugins, shell tools, arbitrary skill scripts, Termux packaging | **NOT_RELEVANT** for Play core | May inform an explicitly separate developer/desktop bridge only. |
| Local model support | **RESEARCH_ONLY** as an integration pattern | Hermes is an HTTP client for Ollama/LM Studio/llama.cpp-compatible servers, not an embedded Android inference engine. |

## Repository integrity, license, and test posture

`LICENSE` is MIT, copyright 2025 Nous Research. `pyproject.toml` repeats `license = "MIT"` and limits the package to Python 3.11–3.13. The checkout also contains component-specific license material: `plugins/security-guidance/LICENSE` plus `NOTICE`, licenses beneath several bundled document skills, and other nested license files. Those nested notices must remain attached if any such component is reused; the root MIT file does not erase component-level obligations.

Header coverage is weak as a provenance mechanism. Across 595 tracked Python files under `agent/`, `tools/`, `gateway/`, `providers/`, `plugins/`, and `cron/`, only `plugins/security-guidance/patterns.py` matched an SPDX/copyright-header scan. This does **not** negate the root license, but it means copied snippets cannot be provenance-classified reliably from source headers alone. Kinetic should port concepts clean-room-style and retain a design provenance record rather than transplanting source.

The tracked checkout contains 3,185 test-shaped files across the whole monorepo; 2,628 files are under `tests/`, and `tests-js/` contains seven files (three actual `.test.ts` files plus its configuration/package files). `pyproject.toml` sets `testpaths = ["tests"]` and declares markers for integration-style separation. The main test tree is organized into `agent`, `providers`, `plugins`, `skills`, `tools`, `gateway`, `cron`, `integration`, `e2e`, `stress`, and platform/UI areas. This is unusually rich evidence for edge-case intent, but tests were inspected rather than executed because execution would require the reference's optional providers, credentials, platform services, and mutable home/session stores.

Dependency observations that matter to adoption:

- `pyproject.toml` pins `openai==2.24.0`; Anthropic and MCP are optional extras (`anthropic==0.87.0`, `mcp==1.28.1`). Provider-specific packages are intentionally outside the base dependency set.
- The README explicitly supports CLI/gateway use on desktop/server and a manually tested **Termux** path. Termux is Linux userland on Android; it is not proof of an embeddable Android library, background-execution compliance, accessibility policy compliance, or Play-compatible packaging.
- Hermes is a broad application, not a narrow library. Any adoption boundary must be interface-level.

## Semantic architecture

The central architecture is a facade plus collaborating subsystems:

1. `run_agent.py::AIAgent` is the public compatibility facade. Construction delegates to `agent/agent_init.py::init_agent`; `AIAgent.run_conversation` binds relay/session/accounting/subagent context and delegates to `agent/conversation_loop.py::run_conversation`.
2. `agent/agent_init.py::init_agent` resolves provider/model/client behavior, toolsets and callbacks, memory, plugins, prompt caching, context compression, iteration budget, and child-agent identity.
3. `agent/conversation_loop.py::run_conversation` owns the model/tool state machine. It prepares a request-only context view, calls a provider transport, normalizes the reply, validates/repairs tool calls, persists messages, executes tools, appends results, and repeats until completion or a terminal guard.
4. `tools/registry.py::ToolRegistry` is the runtime tool registry and dispatch contract. `agent/tool_executor.py` and `agent/tool_dispatch_helpers.py` implement execution, batching, output envelopes, untrusted-output fencing, and result budgeting.
5. `agent/context_engine.py::ContextEngine`, `agent/context_compressor.py::ContextCompressor`, and `agent/conversation_compression.py::compress_context` separate selection from durable compaction.
6. `tools/memory_tool.py::MemoryStore` and `agent/memory_provider.py::MemoryProvider` separate built-in local memory from one selected external memory backend.
7. Skills are Markdown packages discovered and progressively disclosed through `agent/skill_utils.py` and `tools/skills_tool.py`; mutation is governed by `tools/skill_manager_tool.py`, `tools/skills_guard.py`, `tools/skill_provenance.py`, and `tools/write_approval.py`.
8. `hermes_cli/plugins.py::PluginManager` loads in-process extensions and exposes tools/hooks/platforms/providers/context engines plus a guarded host-LLM facade.
9. `tools/delegate_tool.py` runs internal child agents; `agent/subagent_lifecycle.py::SubagentLifecycleService` is the smaller public, plugin-safe lifecycle contract.
10. `gateway/run.py::GatewayRunner` multiplexes platform adapters and persistent sessions. `cron/` adds an autonomous execution lane.

This separation is more useful than the concrete Python modules. A Kinetic port should retain a small immutable `AgentTurn` state machine and inject provider, tool, memory, skill, context, checkpoint, and policy services.

## Agent loop and prompt construction

### Turn preparation and state

`agent/turn_context.py` prepares each turn: sanitized user content, the persisted/cached system prompt, session counters, preflight compression, plugin hooks, external-memory prefetch, and incremental persistence state. `agent/prompt_builder.py` and `agent/system_prompt.py` compose the stable base prompt with tool, platform, memory, and skill metadata.

The important invariant is that request-time additions need not mutate history. `agent/conversation_loop.py::_apply_context_engine_selection` deep-copies the API request and calls `ContextEngine.select_context`; the persisted transcript remains authoritative. This is the right shape for Kinetic: durable event history plus a derived `ModelContextView`, never a single mutable list used both as audit log and prompt.

`agent/agent_init.py` creates an `agent.iteration_budget.IterationBudget` when none is injected. Despite stale comments near top-level constructor parameters saying the limit is “shared,” `tools/delegate_tool.py::_build_child_agent` explicitly passes `iteration_budget=None`, and `agent/iteration_budget.py::IterationBudget` documents independent child budgets. Therefore current semantics are **per-agent budgets**, not one tree-wide budget; aggregate fan-out cost can exceed the parent's cap. Kinetic needs both a per-run cap and a tree/account-level budget if subagents are enabled.

### Provider-neutral turn result

`agent/transports/types.py` defines the canonical transport seam:

- `ToolCall(id, name, arguments, provider_data)` keeps common fields flat and protocol-only material in `provider_data` (for example Codex item IDs or Gemini thought signatures).
- `Usage` normalizes prompt/completion/total/cached token counts.
- `NormalizedResponse(content, tool_calls, finish_reason, reasoning, usage, provider_data)` gives the loop one provider-neutral result.

`agent/transports/base.py::ProviderTransport` requires `convert_messages`, `convert_tools`, `build_kwargs`, and `normalize_response`; optional methods validate responses, extract cache statistics, and map finish reasons. `agent/transports/__init__.py::get_transport` maps API modes to Chat Completions, Anthropic Messages, Codex Responses, or Bedrock implementations. `run_agent.py::AIAgent._get_transport` caches one instance per API mode.

Kinetic should copy this **shape**: `ProviderAdapter.prepareRequest`, `ProviderAdapter.execute`, and `ProviderAdapter.normalize`, with a typed opaque protocol sidecar that is serializable. It should not expose arbitrary `Map<String, Any>` to the whole application; isolate protocol data behind the adapter.

### Completion and recovery ladder

The loop has explicit, bounded recovery states rather than treating every malformed response as a new unconstrained prompt:

- dropped/empty assistant responses receive a post-tool nudge, then limited thinking-prefill/empty retries and provider fallback;
- incomplete scratchpad and output-length responses use bounded continuation paths;
- content-filter termination is distinct from retryable provider failure;
- `agent/error_classifier.py::classify_api_error` returns structured recovery attributes (retryability, compression, credential rotation, fallback), used in `conversation_loop.py` rather than scattered string-only decisions;
- replay-specific faults have targeted one-shot repairs: invalid reasoning signatures, invalid encrypted Codex content, unsupported multimodal tool messages, oversized images, Unicode/surrogate payload faults, and llama.cpp grammar incompatibility.

This is a good algorithmic reference. Kinetic should model recovery as `FailureClass -> RecoveryDecision` with attempt counters stored in the task checkpoint. It should avoid the reference's provider-specific branches accumulating in a monolithic loop by letting adapters contribute bounded recovery transforms.

## Tools, results, and crash safety

### Capability registration

`tools/registry.py::ToolEntry` / `ToolRegistry` hold schema, handler, availability check, async status, description/emoji, size behavior, and plugin provenance. Runtime results are normally strings; the registry permits a defined multimodal envelope and converts unexpected return types into a `tool_result_contract` error. `tool_error` and `tool_result` provide a consistent JSON-string convention.

This is not yet a complete mobile capability descriptor. It lacks a mandatory effect class, Android permission declaration, foreground-service need, data-domain scope, user-confirmation rule, and deterministic cancellation contract. Kinetic's `CapabilityDescriptor` should add those fields and make them enforceable by a policy engine, not prompt text.

### Validation, repair, and execution

The model-facing pipeline performs several repairs:

- fuzzy/known-name correction and argument repair before dispatch;
- an invalid call in a mixed batch receives a paired error result while valid calls still run;
- malformed JSON or truncated arguments do not trigger the target side effect; they receive an error result the model can correct;
- repeated all-invalid batches stop after a bounded strike count;
- `agent/tool_guardrails.py` tracks repeated signatures, failures, and loop caps to warn, synthesize a result, or halt.

`agent/tool_dispatch_helpers.py::_plan_tool_batch_segments` preserves model order while dividing a batch into maximal parallel-safe runs and sequential barriers. Read-only tools and explicitly opted-in MCP calls may run concurrently; filesystem calls are parallelized only when canonical target paths do not overlap. Unknown or unparseable calls become sequential barriers. Runs shorter than two are demoted to sequential execution.

This is a valuable contract: **parallelism must be admitted from declared effects/scopes, not inferred from tool names**. Kinetic can improve it by requiring each capability invocation to resolve a `ResourceScope` set before scheduling.

### Results and persistence

`agent/tool_dispatch_helpers.py::make_tool_result_message` produces protocol-compatible tool messages and adds risk metadata. Outputs from network/untrusted tools are wrapped in neutralized `<untrusted_tool_result>` delimiters; delimiter text inside data is defanged first. Tool-result budgets apply across a turn, and large/multimodal results get bounded text summaries for history and diagnostics.

The most important correctness invariant in `agent/conversation_loop.py` is ordering: the assistant row containing tool-call intent is incrementally persisted **before** executing side effects. If that persistence fails, tools do not run. Tool results are then persisted incrementally. This substantially reduces “side effect happened but the audit log forgot why” failures.

For Kinetic, use an outbox/checkpoint protocol:

1. persist `ToolInvocationPlanned` with idempotency key;
2. obtain policy/approval;
3. execute through the capability adapter;
4. persist `ToolInvocationFinished` with typed status and bounded artifacts;
5. only then advance the planner state.

Hermes does not turn every external side effect into a transactional/idempotent operation, so exact-once execution is not guaranteed merely by message persistence.

## Skills: representation, discovery, selection, and execution boundary

### Representation

Hermes skills are directories rooted by `SKILL.md`. `agent/skill_utils.py::parse_frontmatter` parses YAML frontmatter; validation requires meaningful frontmatter/body, with `name` and `description` as the core identity. Optional metadata includes platforms, environments, activation conditions, required environment/credential inputs, tags, related skills, and configuration variables. A package may include `references/`, `templates/`, `assets/`, and `scripts/` for progressive disclosure.

Sources include bundled skills, user-local skills, configured external directories, an organization mirror, and plugin-qualified skills. `agent/skill_utils.py::iter_skill_index_files` explicitly excludes nested support `SKILL.md` files from discovery, preventing reference packages from accidentally becoming top-level skills. Plugin collisions are addressable as `namespace:name`.

### Discovery and selection

The system prompt receives a compact index of skill names and frontmatter descriptions. Discovery filters globally/platform-disabled skills and hard platform constraints; environment/condition metadata changes visibility/readiness. `tools/skills_tool.py::skills_list` returns the small index, while `skill_view` loads the body or a support file after resolving collisions, rejecting traversal, checking platform/disable state, surfacing security/readiness warnings, and recording usage.

There is **no embedding retriever or learned ranker in the runtime selection path**. The LLM selects from the description index under system-prompt instructions, then calls `skill_view`. `agent/learning_graph.py` is a visualization/relationship surface (explicit related edges plus lexical memory/skill associations), not a runtime semantic selector.

This is a sensible low-complexity baseline for Kinetic, but descriptions are untrusted prompt material. Kinetic should parse skills into a constrained `SkillDescriptor` and rank candidates outside the generative model using declared capability/permission/availability matches plus optional local embeddings. The model should see only top candidates and must still request a skill explicitly.

### Executable versus declarative content

`agent/skill_preprocessing.py` can resolve templates and inline shell-oriented material, and skill packages may ship support scripts. Therefore a Hermes skill is not merely documentation; it may cause executable desktop behavior through the normal tool surface. `tools/skills_hub.py` adds install/lock/audit/quarantine mechanics and `tools/skills_guard.py` performs heuristic scanning, but these are not a cryptographic publisher trust chain or an Android sandbox.

Kinetic should split the concept:

- a Play-safe declarative `Skill IR` containing instructions, typed inputs/outputs, required Kinetic capabilities, network hosts, data scopes, and confirmation policy;
- optional signed native capability modules built into the app or delivered through an approved feature mechanism;
- no downloaded shell, Python, JavaScript, or arbitrary code in the consumer app.

### Mutation and ownership

`tools/skill_manager_tool.py` supports create/edit/patch/delete and support-file operations with atomic writes, path/frontmatter/size validation, provenance, ownership, and read-before-write rules. Bundled, pinned, Hub/external, and user-owned content have different protections. Background review is limited to curator-managed content and must read before mutating. `tools/write_approval.py` can stage writes under the Hermes home when approval mode is enabled; that mode is optional and is not the default.

The safe Kinetic default should be the inverse: learned skill changes are always staged as proposals, diffed, provenance-tagged, and explicitly accepted unless a narrow user-authored automation policy permits otherwise.

## Learning and self-improvement pipeline

Hermes implements a real experience-to-knowledge loop, but it is curation by a second LLM pass rather than online model training.

`agent/turn_finalizer.py` triggers periodic review counters. `agent/background_review.py::spawn_background_review_thread` forks an agent after the user-visible response, using the main runtime or an auxiliary model selected by `_resolve_review_runtime`. The fork:

- receives a bounded digest of recent history;
- disables ordinary persistence/compression;
- restricts tools to memory and skill mutation;
- shares the built-in memory store but not the selected external memory provider;
- writes with explicit background-review provenance;
- is instructed to capture stable preferences/corrections in memory and reusable techniques in skills, prefer updating an existing umbrella skill, and avoid transient failures or unvalidated procedures.

Successful actions can be summarized by `background_review.py::summarize_background_review_actions`. `tools/skill_usage.py` records view/use sidecar data, and provenance/curator state guards later mutations.

The learning flow is:

`turn evidence -> bounded review digest -> candidate memory/skill mutation -> ownership/read checks -> commit or optional approval staging -> future prompt/index reload`.

Limitations relevant to Kinetic:

- The reviewer is another generative run; truth is not independently verified.
- With write approval disabled, it can commit durable memory/skills autonomously.
- The learning graph does not validate causality or procedural success.
- There is no evaluation set, confidence calibration, rollback scoring, or expiry policy tied to app/model versions.

Kinetic should represent every learned item as `KnowledgeProposal(id, evidenceRefs, type, content, confidence, author, createdAt, expiry, scope, status)` and promote it only after deterministic checks and user/policy approval. Procedural knowledge should carry success/failure counters and invalidate on app/version/capability changes.

## Memory storage and retrieval

### Built-in memory

`tools/memory_tool.py::MemoryStore` keeps two bounded plaintext Markdown stores under the Hermes home:

- `MEMORY.md` for durable operational facts/notes;
- `USER.md` for persona and preferences.

Entries are separated with the `§` delimiter. The store supports add/replace/remove and atomic batch writes, locks mutation, applies size budgets, handles duplicate/ambiguous substring targets, and detects external file drift before destructive rewrite (creating a backup rather than silently overwriting). `format_for_system_prompt` builds a frozen, sanitized snapshot. Suspected prompt-injection entries are replaced by blocked placeholders.

The system-prompt memory is intentionally frozen for prompt-cache stability. A memory tool write is visible in the tool result immediately but does not automatically rewrite the current cached system prompt; compression/session prompt rebuild reloads it. Kinetic should make this epoch explicit (`MemorySnapshot.version`) so users and tests understand when new memory becomes planner context.

### External provider contract

`agent/memory_provider.py::MemoryProvider` is a lifecycle interface covering availability/initialization, a static prompt block, prefetch, queue/sync, provider tools, turn/session callbacks, pre-compression context, memory-write notification, delegation, and backup. `agent/memory_manager.py::MemoryProviderManager` chooses one external provider, refuses core tool shadowing, applies a bounded prefetch timeout, serializes background sync/prefetch work through one worker, drains with a bound at shutdown, and fails open on provider errors.

Prefetched material is fenced as `<memory-context>`, sanitized, and added only to the current request. It does not alter durable history. This separation—durable store, selected provider, derived per-turn context—is directly useful.

For Kinetic, replace Markdown substring mutation with a typed local database:

- `MemoryRecord(id, kind, text, normalizedKey, evidenceRefs, scope, sensitivity, createdAt, updatedAt, expiry, version)`;
- FTS/embedding retrieval behind `MemoryRetriever`;
- an explicit redaction and at-rest encryption policy;
- no raw memory in telemetry;
- deterministic per-turn token/record budget.

## Context selection and compaction

`agent/context_engine.py::ContextEngine` defines a small replaceable contract: identity/token budget/threshold, response updates, compression decision/info, `compress`, optional `prune`, request-only `select_context`, turn-complete notification, and session/model/tool binding.

The default `agent/context_compressor.py::ContextCompressor` uses a hybrid algorithm:

1. deterministically prune/deduplicate older tool results and strip historical image payloads;
2. preserve a protected head plus a recent active tail;
3. summarize the middle with a structured prompt covering resolved work, active work, decisions, files, and pending items;
4. retain prior rolling summaries across repeated compactions;
5. preserve recently loaded skills where possible; if hard pressure removes one, emit a canonical `SKILL_PRUNED` reload marker;
6. rebuild an alternation-safe history with an explicit compression boundary and no orphan tool results.

`agent/conversation_compression.py::CompressionCommitFence` prevents a caller timeout from allowing a late summary worker to mutate the session behind its back. Per-session compression locks/leases prevent concurrent forks. Ordinary summary failure gets a deterministic fallback; authentication/access/network/config failures can abort compaction and leave the transcript unchanged. Anti-thrash guards detect repeatedly ineffective compression and apply cooldowns.

The default durable behavior uses the session database's in-place archive/compact path: older rows become inactive but remain searchable/recoverable, and the compacted active transcript stays under the same logical session. A legacy rotation path can create a child session. This is superior to destructively replacing history.

Kinetic should adopt an append-only event log plus `ContextSnapshot` records. A snapshot should contain source event IDs, algorithm/model/version, redaction status, token estimates, and hash. Compaction never deletes raw local events until a separate retention policy does so.

## Plugin model and capability declaration

`hermes_cli/plugins.py::PluginManager` discovers four sources: bundled, user (`~/.hermes/plugins`), project (`./.hermes/plugins`, opt-in via `HERMES_ENABLE_PROJECT_PLUGINS`), and Python entry points in `hermes_agent.plugins`. Later sources win collisions. A directory plugin needs `plugin.yaml` and `__init__.py::register(ctx)`.

`PluginManifest` records `name`, `version`, `description`, `author`, environment requirements, `provides_tools`, `provides_hooks`, source/path, kind, and key. Kinds are `standalone`, `backend`, `exclusive`, `platform`, and `model-provider`. Bundled backends auto-load, bundled platforms register deferred loaders, external standalone/backend plugins require enablement, and exclusive/model-provider categories use their own selectors/discovery.

`PluginContext` can register tools, hooks, middleware, commands, skills, platform adapters, many media/search/browser/secret providers, auxiliary tasks, and one context engine. It exposes a host LLM via `agent/plugin_llm.py::PluginLlm`; provider/model/profile/agent overrides fail closed unless `plugins.entries.<id>.llm.allow_*` permits them. It also exposes `SubagentLifecycleService` rather than raw child `AIAgent` objects. A non-bundled plugin needs explicit configuration to replace a built-in tool; bundled code is trusted for overrides.

Two caveats are decisive:

1. `provides_tools`/`provides_hooks` are descriptive metadata. Actual code registration is runtime truth; the manifest does not statically declare effects, hosts, Android permissions, data access, confirmation needs, or background execution.
2. Enabled plugins are imported Python and run in-process. The tool-override/LLM-override gates do not sandbox arbitrary plugin code.

The sample manifests make this visible: `plugins/spotify/plugin.yaml` lists seven tools; `plugins/platforms/telegram/plugin.yaml` declares credentials and descriptive features; `plugins/memory/honcho/plugin.yaml` declares a memory integration. None is a sufficient mobile permission/effect manifest.

Kinetic should not support executable third-party plugins in the Play core. Use a versioned, signed `ExtensionManifest` whose capabilities are a strict subset of app-compiled adapters and whose declared permissions/scopes are enforced before activation.

## Provider abstraction and local inference

`providers/base.py::ProviderProfile` is declarative: provider identity/aliases, API mode, auth/env variables, endpoints/model catalog, headers, vision/tool-message support, token defaults, message preparation, request extras, and model-fetch hooks. `providers/__init__.py` lazily discovers bundled `plugins/model-providers`, user provider plugins, then legacy single-file modules; later registration replaces earlier entries.

Provider identity is also resolved through `hermes_cli/providers.py`: a models.dev catalog is merged with Hermes transport/auth overlays and user configuration. `hermes_cli/auth.py::ProviderConfig` carries runtime auth and endpoint details. These parallel historical surfaces are converging but remain a warning for Kinetic: keep one authoritative provider registry.

Local inference evidence:

- `hermes_cli/auth.py` and `hermes_cli/providers.py` define LM Studio at `http://127.0.0.1:1234/v1` using the OpenAI-chat transport.
- generic/custom setup accepts OpenAI-compatible local URLs such as Ollama `http://localhost:11434/v1`;
- `agent/agent_init.py` probes Ollama and injects/caps `num_ctx` so the server does not remain at its small default or overallocate VRAM;
- `agent/model_metadata.py` probes Ollama `/api/tags`/`/api/show`, LM Studio native model endpoints, and llama.cpp/OpenAI-compatible metadata;
- `run_agent.py` can explicitly preload an LM Studio model or allow JIT loading and contains provider-specific response-repair workarounds.

There is no embedded tensor engine, model downloader suitable for Android app storage, LiteRT/NNAPI backend, JNI runtime, or mobile thermal/memory policy. “Local” means a separately running HTTP server (or desktop/Termux process). Therefore Hermes contributes a `RemoteOrLocalEndpointProvider` integration pattern only; AirLLM/native mobile engines must be evaluated separately for Kinetic's in-process local model.

## Subagents

### Internal delegation

`tools/delegate_tool.py::delegate_task` accepts one goal or a bounded batch, supports synchronous and detached/background fan-out, and returns consolidated JSON results. Default role is `leaf`; an explicit `orchestrator` retains delegation only if the operator enables it and the configured depth allows it. Depth defaults flat (`MAX_DEPTH = 1`) but has no hard configured ceiling; concurrency defaults to three and can be raised. Model-supplied iteration limits are ignored in favor of operator configuration.

`_build_child_agent` intersects/narrows parent toolsets, strips unsafe child tools, preserves configured MCP toolsets, assigns stable subagent/parent/depth IDs, optionally routes to a cheaper/different provider, and creates a **fresh** child iteration budget. Dangerous terminal approvals in worker threads are auto-denied by default; `delegation.subagent_auto_approve` is an explicit unsafe opt-in. Progress, thinking, tool start/finish, cost, token use, and a bounded summary/tool trace are relayed. Cancellation is cooperative—Python threads cannot be hard-killed—and heartbeat logic stops masking a wedged child.

### Public lifecycle contract

`agent/subagent_lifecycle.py` is a cleaner extraction. It defines immutable `SubagentLaunchRequest`, `SubagentHandle`, `SubagentStatus`, `SubagentTerminalState`, `SubagentCancelResult`, `SubagentResult`, and `SubagentReconnectResult`, plus `SubagentState` values from `PENDING` through terminal and cancellation states. Handles contain an HMAC capability bound to child/parent/time. Requests cannot broaden parent toolsets and have bounded goal/context/metadata sizes.

The service is process-local: live work cannot reconnect after restart, completed records are retained in memory for one hour, and cancellation calls the child's cooperative interrupt. The source docstring is explicit about this. Kinetic should preserve the lifecycle types but back them with durable Room/WorkManager state and resumable/cancellable operations; never promise reconnection from an in-memory handle.

## Gateway and session semantics

`gateway/platforms/base.py::BasePlatformAdapter` defines the platform seam: `connect`, `disconnect`, `send`, and `get_chat_info`, with default media, streaming/edit, typing, formatting, slash/clarification, and delivery helpers. Adapters receive normalized `MessageEvent`/source data and route replies through platform-specific IDs/threads.

`gateway/session.py::SessionSource` and `SessionContext` preserve platform/chat/user/thread/profile/relay provenance. Untrusted names and metadata are neutralized before system-prompt inclusion. Session keys and IDs are checked against path traversal before filesystem use.

`gateway/session_state.py` consolidates per-routing-key state into:

- `TurnState`: running agent, timing, process/session lease, busy ack, generation-bound turn lease;
- `ConversationState`: model/reasoning/service-tier overrides, queued events, sidecar notes, ephemeral context, voice state;
- `PersistentState`: approvals, update prompt, staged images/commands, and monotonic run generation.

`gateway/turn_lease.py::SessionTurnLeaseRegistry` serializes `load history -> run -> flush` by **resolved session ID**, covering two routing keys mapped to one transcript. Tokens are generation/identity checked, release is idempotent, and mid-turn session rotation can rebind the same lock. However, it is process-local and deliberately fails open after a long wait, allowing possible interleaving rather than wedging. The module explicitly excludes concurrent CLI processes from its guarantee. Kinetic should use a database transaction/lease with stale-owner recovery, not a process-local fail-open mutex for durable task history.

`gateway/run.py::GatewayRunner` adds busy-input policy, queueing/interrupts, per-turn agent construction, streaming progress, reconnect backoff, profile-scoped credentials, shutdown drain, restart recovery, session continuation, and cron/watch completion delivery. It is an operational reference, not a mobile-ready service: Android background limits and notification/foreground-service rules need a different lifecycle.

## Cron and autonomous execution

`cron/jobs.py::create_job` persists a self-contained job with identifier/name/prompt, ordered skills, provider/model/base URL and creation-time snapshots, optional prerun script, `no_agent` mode, upstream job-output context, parsed schedule, repeat counters, enabled/state/timestamps/status/error, delivery/origin, enabled toolsets, workdir, and optional session attachment. Schedules support one-shot ISO times, intervals, and cron expressions in the configured Hermes timezone.

Jobs live in a locked, atomically replaced `jobs.json` under a profile-specific Hermes home. Output artifacts are stored per job with retention. `cron/executions.py` separately stores execution attempts in SQLite and recovers interrupted owners.

Correctness semantics are deliberate:

- `cron/scheduler.py::tick` takes a cross-process file lock and advances recurring `next_run_at` **before** dispatch, choosing at-most-once behavior after crashes rather than restart storms.
- `cron/jobs.py::claim_dispatch` durably consumes a finite one-shot attempt before its side effect; `run_claim` heartbeats distinguish a long run from a dead owner.
- `cron/scheduler.py::run_one_job` records an execution, applies profile secret scope, runs agent or `no_agent` script, saves output, applies autonomous-silence rules, delivers, records delivery separately from task success, tears down resources, and finalizes execution state.
- `cron/lifecycle_guard.py` rejects scheduled gateway restart/kill persistence loops. Fully assembled prompts, including loaded skill content, are injection-scanned before noninteractive execution. Cron agents always lose interactive/self-scheduling toolsets; per-job toolsets cannot bypass the global deny list.

Risks for Kinetic are still substantial: jobs can run scripts, the built-in scheduler is a long-lived gateway ticker, and at-most-once semantics can intentionally miss work. A mobile design needs explicit job class, idempotency key, retry/backoff policy, charging/network/idle constraints, exact-alarm justification, foreground notification requirements, user-visible history, and separate `taskSucceeded` versus `deliverySucceeded` states.

## Security and portability findings

| Finding | Evidence | Kinetic consequence |
|---|---|---|
| Untrusted tool output is fenced | `agent/tool_dispatch_helpers.py::_maybe_wrap_untrusted` and `_neutralize_delimiters` | Preserve typed trust labels; do not rely only on XML-like prompt delimiters. |
| Tool intent persists before side effect | `agent/conversation_loop.py` incremental persistence around tool execution | Adopt an outbox/checkpoint protocol. |
| Skills/plugins can execute arbitrary desktop code | `skill_preprocessing.py`, support scripts, Python plugin import | Exclude executable downloads from Play core. |
| Background learning can mutate durable knowledge without approval | `background_review.py`; optional `write_approval.py` | Make proposal/approval the default and preserve evidence. |
| Child cancellation is cooperative and state is in-memory | `delegate_tool.py`, `subagent_lifecycle.py` | Use durable jobs, cancellation tokens, and recoverable checkpoints. |
| Gateway serialization is process-local and fail-open | `gateway/turn_lease.py` module contract | Use a DB-level lease/transaction. |
| Local inference is server-based | LM Studio/Ollama endpoint handling | Do not count Hermes as an Android on-device engine. |
| Prompt metadata is sanitized but still model-visible | `gateway/session.py`, memory/tool fencing | Enforce authorization outside prompts. |

## Proposed Kinetic abstractions

The following Kotlin-level boundaries capture the reusable semantics without importing Python architecture:

```text
AgentRuntime
  run(checkpoint: TaskCheckpoint, input: UserTurn): TurnOutcome

PlannerStrategy
  next(context: ModelContextView, tools: List<CapabilityDescriptor>): PlannerDecision

ModelProvider
  capabilities(model): ModelCapabilities
  complete(request, cancellation): ProviderResponse

ProviderAdapter
  prepare(canonicalRequest): WireRequest
  normalize(wireResponse): CanonicalModelResponse

CapabilityRegistry / CapabilityExecutor / CapabilityPolicy
  resolve + authorize + execute typed CapabilityInvocation

MemoryStore / MemoryRetriever / KnowledgeProposalStore
ContextSelector / ContextCompactor / ContextSnapshotStore
SkillCatalog / SkillResolver / SkillProposalStore
TaskCheckpointStore / ToolOutbox
SubagentService
GatewayAdapter / SessionRepository / TurnLeaseRepository
ScheduledTaskRepository / ScheduledTaskRunner / DeliveryRouter
```

Minimum canonical values should include:

- `CanonicalModelResponse(text, reasoningRef, toolCalls, finishReason, usage, protocolState)`;
- `CapabilityDescriptor(id, version, inputSchema, outputSchema, effects, resourceScopes, androidPermissions, networkHosts, confirmationPolicy, availability, cancellationMode)`;
- `ToolInvocation(id, taskId, descriptorVersion, arguments, idempotencyKey, plannedAt)` and `ToolOutcome(status, data, artifacts, trust, error, metrics)`;
- `ModelContextView(sourceEventIds, memorySnapshotVersion, skillVersions, tokenEstimate, messages)`;
- `TaskCheckpoint(state, attemptCounters, pendingInvocations, providerState, contextSnapshotId, budget)`;
- `KnowledgeProposal(evidenceRefs, scope, confidence, expiry, approvalState)`.

## Synthesis-ready conclusions

1. Hermes validates a modular agent design: provider protocol, planning loop, tools, memory, skills, compaction, child lifecycle, transport/gateway, and scheduling can be separated cleanly.
2. Its best hard invariant is **persist intent before side effect**; Kinetic should strengthen that into a typed, idempotency-aware tool outbox.
3. Skill selection is description-index + model choice, not learned retrieval. Kinetic can keep progressive disclosure while adding deterministic policy/ranking and a constrained Skill IR.
4. “Self-improvement” is background curation into memory/skills, not weight training. Treat every output as a proposal with evidence, version, expiry, evaluation, and rollback.
5. Memory and skills correctly represent different things—user/state versus reusable procedure—and should remain separate stores and prompt budgets.
6. Context selection is request-only; compaction archives rather than erases. This should be a foundational Kinetic data-model rule.
7. The plugin model is far too permissive for Play distribution. Only signed/declarative descriptors over app-compiled capabilities are suitable.
8. Subagent lifecycle types are reusable, but current work/handles are not restart durable and child budgets are independent. Kinetic needs tree budgets and persisted lifecycle state.
9. Gateway routing and cron contain mature failure semantics, but their long-lived Python process assumptions must be replaced by Android lifecycle primitives.
10. Hermes local-model support is an OpenAI-compatible endpoint client. It does not answer Kinetic's embedded inference question.

## Material implementation ledger

Material artifacts read or section-read: **62**. Broad `rg`/tracked-file scans and test-directory enumeration are additional and are not counted as material reads.

1. Root/meta: `LICENSE`; `README.md`; `pyproject.toml`; `setup.py`; `run_agent.py`.
2. Agent loop/state: `agent/agent_init.py`; `agent/conversation_loop.py`; `agent/turn_context.py`; `agent/turn_finalizer.py`; `agent/iteration_budget.py`; `agent/error_classifier.py`; `agent/tool_guardrails.py`; `agent/tool_executor.py`; `agent/tool_dispatch_helpers.py`.
3. Prompt/context: `agent/prompt_builder.py`; `agent/system_prompt.py`; `agent/context_engine.py`; `agent/context_compressor.py`; `agent/conversation_compression.py`.
4. Memory/learning/skills: `agent/memory_provider.py`; `agent/memory_manager.py`; `agent/background_review.py`; `agent/learning_graph.py`; `agent/learning_mutations.py`; `agent/skill_utils.py`; `agent/skill_preprocessing.py`; `agent/skill_commands.py`; `agent/skill_bundles.py`.
5. Subagent/plugin/provider: `agent/subagent_lifecycle.py`; `agent/plugin_llm.py`; `providers/base.py`; `providers/__init__.py`; `agent/transports/base.py`; `agent/transports/types.py`; `agent/transports/__init__.py`; `agent/transports/chat_completions.py`; `hermes_cli/plugins.py`; `hermes_cli/lifecycle.py`; `hermes_cli/auth.py`; `hermes_cli/providers.py`.
6. Tool services: `tools/registry.py`; `tools/skills_tool.py`; `tools/skill_manager_tool.py`; `tools/skills_hub.py`; `tools/skills_guard.py`; `tools/skill_usage.py`; `tools/write_approval.py`; `tools/memory_tool.py`; `tools/delegate_tool.py`.
7. Gateway: `gateway/platforms/base.py`; `gateway/session.py`; `gateway/session_state.py`; `gateway/turn_lease.py`; `gateway/run.py`.
8. Scheduler: `cron/jobs.py`; `cron/scheduler.py`; `cron/executions.py`; `cron/scheduler_provider.py`; `cron/lifecycle_guard.py`.
9. Representative manifests: `plugins/spotify/plugin.yaml`; `plugins/platforms/telegram/plugin.yaml`; `plugins/memory/honcho/plugin.yaml`.

No claim in this report depends only on a README statement where an implementation path was available. Absence claims (no embedded Android model engine, no learned skill ranker, process-local subagent reconnect) were checked against the relevant runtime contracts and repository-wide targeted searches.
