# Testing and benchmark assets

## Scope, evidence status, and non-execution statement

This report inventories testing, evaluation, replay, and benchmark assets in all seven local references under `E:\Projects\Eco_reference`: OpenClaw, Hermes Agent, OpenDroid, AndyClaw, MobileRun, AirLLM, and AnyClaw. It synthesizes the local source trees and the existing Phase 0 reports, especially `02_OPENCLAW_ANALYSIS.md`, `03_OPENDROID_ANALYSIS.md`, `05_HERMES_ANALYSIS.md`, and `06_MOBILERUN_ANALYSIS.md`.

Repository-specific sections occasionally shorten a repeated path to `tests/...`; it is relative to the repository named by that section. Within an exact-source table cell, a later repository-relative path or sibling filename inherits the repository root or directory of the preceding fully qualified path. The material-inspection ledger expands every inspected asset to an exact path relative to `E:\Projects`.

Three evidence labels are used deliberately:

- **Enumerated** means the path/count was obtained by repository-wide filename and directory scans. It proves that an asset exists, not that it is collected, passes, or tests the behavior implied by its name.
- **Materially inspected** means the file was read in full or its relevant test bodies, fixtures, assertions, and helpers were inspected. This report materially inspected **92 source test/benchmark/fixture files**: OpenClaw 30, Hermes 22, MobileRun 14, OpenDroid 8, AndyClaw 6, AirLLM 6, and AnyClaw 6. Eleven existing Phase 0 reports and several build/test configuration files were section-read in addition and are not included in that 92-file source count.
- **Executed** means actually run against its framework/environment. **No upstream test, benchmark, device flow, model, daemon, network service, or evaluator was executed.** Running them was neither necessary nor safe for this read-only archaeology pass: several require credentials, downloads, live Gateways, mutable home directories, GPUs, preinstalled models, ADB-controlled devices, accessibility services, or external packages absent from the checkout.

Reference source remained read-only. Final Git checks were clean at these exact local commits:

| Repository | Audited checkout | Final status |
|---|---:|---|
| `Eco_reference/openclaw` | `ebb301c32d9e` | clean |
| `Eco_reference/hermes-agent` | `87bc710609f8` | clean |
| `Eco_reference/AndyClaw` | `04a520177a59` | clean |
| `Eco_reference/opendroid` | `9e3ef380eb00` | clean |
| `Eco_reference/airllm` | `64a4e4fc3749` | clean |
| `Eco_reference/anyclaw` | `eb7052708f99` | clean |
| `Eco_reference/mobilerun-main` | no local Git metadata | source archive was not modified; exact upstream revision remains unprovable |

## Executive conclusion

There is no turnkey Kinetic test suite in any reference. The strongest composite is:

1. **OpenClaw** for transport ambiguity, idempotency, process/reconnect recovery, task/session/cron invariants, authorization-at-execution, malformed tool streams, and native Android lifecycle tests.
2. **Hermes** for adversarial agent/tool behavior, cancellation propagation, provider fallback, durable execution ledgers, concurrency/property stress, and broad failure regression coverage.
3. **MobileRun** for screenshot/accessibility coordinate contracts, malformed planner/tool output, trajectory/macro fixtures, guarded replay, and an independent grounding-eval pattern.
4. **OpenDroid** for native JVM tests around provider retry/cancellation, action schemas, permission presentation, approval policy, Room migration, crash redaction, and voice approval parsing.
5. **AndyClaw** for clean-room behavioral requirements around staged/parallel tool execution and a real-device local-model smoke/performance harness; its GPL-3.0 source is not a default copy source.
6. **AirLLM** for host-side weight-split/compression correctness and GPU memory/output-parity experiments only.
7. **AnyClaw** as a negative signal: zero Go tests. Its YAML examples should seed import/rejection fixtures, not be trusted as conformance assets.

The missing centerpiece is a deterministic, Android-native, end-to-end fault-injection harness whose durable journal is both the recovery authority and the evaluation trace. Kinetic must keep deterministic kernel/safety gates separate from stochastic model-quality evaluations; a model success-rate run can never waive a failed authorization, duplicate-effect, or recovery invariant.

## Repository-wide enumerated posture

Counts below use each repository's apparent conventions and are not directly comparable. Syntactic annotation/function counts do not account for parameterized expansion, skips, collection errors, or duplicate names.

| Repository | Enumerated assets | What the count really means | Materially inspected | Executed |
|---|---:|---|---:|---|
| OpenClaw | 10,382 test-shaped paths by filename/directory heuristic, including 9,478 `*.test.ts`; Android has 206 Kotlin test-source files across `test`, `testDebug`, `testThirdParty`, and `androidTest`, with 2,347 `@Test` annotations | Includes fixtures/support/configs; not 10,382 independently runnable cases | 30 | No |
| Hermes Agent | 3,185 test-shaped paths across the monorepo; `tests/` has 2,628 files, 2,557 Python test modules and 21,675 syntactic test functions; `tests-js/` has three `*.test.ts` files | Very broad pytest tree; default config excludes `integration`; stress/manual/external-service lanes are separate | 22 | No |
| OpenDroid | 26 JVM Kotlin test files, 270 `@Test` annotations; no `src/androidTest` tree | Primarily JUnit/Robolectric; Room schemas are exposed to debug JVM tests | 8 | No |
| AndyClaw | 14 Kotlin test files, 337 `@Test` annotations, including two boilerplate example-module tests and two instrumentation files | One meaningful device inference suite; much of the count comes from large routing/execution tests | 6 | No |
| MobileRun | 37 files in `tests/`: 36 `test_*.py` modules plus one manual grounding eval; 407 syntactic test functions | `eval_coordinate_grounding.py` is intentionally not collected by pytest | 14 | No |
| AirLLM | 11 files under `air_llm/tests`: four named Python modules, `__init__.py`, and six notebooks, plus one test-shaped dataset script elsewhere; 10 Python test methods | `test_streaming_gpu.py` and the dataset script are manual harnesses, not collected unit tests | 6 | No |
| AnyClaw | zero `*_test.go` files | No unit, integration, conformance, security, fuzz, or benchmark harness | 6 example/registry fixtures | No |

Framework details matter. `Eco_reference/hermes-agent/pyproject.toml` sets `testpaths=["tests"]`, excludes the `integration` marker by default, and declares special WAL/SSH/isolation markers. `Eco_reference/mobilerun-main/pyproject.toml` only adds the repository to `pythonpath`; its live-device behavior is not automatically gated. `Eco_reference/opendroid/app/build.gradle` enables Android resources for Robolectric and uses exported Room schemas. OpenClaw has independent pnpm/Vitest, Android unit/instrumentation, and macrobenchmark surfaces rather than one unified runner.

## Reusable OpenClaw assets

### Android transport, lifecycle, and durable intent

| Exact local source | Observed test intent | Kinetic use |
|---|---|---|
| `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/gateway/GatewaySessionReconnectTest.kt` | Connection-generation leases reject replacement sockets before enqueue; disconnect waits for in-flight token persistence/terminal callbacks; stale drains cannot cancel replacement RPCs; definitely-unsent node events remain queued; reconnect policy changes with pairing/token state. | Port the fault scenarios into a `MockWebServer`/fake-transport suite. The essential oracle is send classification: **not enqueued**, **definitive failure**, or **outcome unknown**. |
| `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/gateway/GatewaySessionInvokeTimeoutTest.kt` | IPv6 authority formatting plus bounded execution/ack timeout resolution. | Pure Kotlin boundary/property tests for timeout parsing, overflow, zero-as-disable, IPv6, and deadlines. |
| `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/chat/ChatControllerOutboxTest.kt` | Offline admission survives controller recreation; reconnect flush is FIFO with row ID as idempotency key; ownership/session-settings gates block unsafe replay; ambiguous accepted rows are parked before younger sends. | Generalize into Kinetic effect-journal tests with process recreation and fake-clock/fake-network control. |
| `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/chat/RoomChatCommandOutboxTest.kt` | Stable ordering at colliding clocks, bounded admission receipts, queue cap, exact expiry boundary, interrupted `Sending -> unconfirmed`, ownership isolation, retry identity refresh, and transaction behavior. | Direct semantic precedent for Room DAO/migration/property tests; never infer correctness from coroutine state. |
| `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/chat/ChatControllerReconnectRestoreTest.kt` | Reconnect re-adopts session/run state, consumes live events, restores plan snapshots, invalidates late snapshots/errors, and distinguishes accepted-before-drop sends. | Use as the model for projection rebuild tests; Kinetic's canonical journal must make these easier because it is local authority, not a Gateway cache. |
| `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/NodeForegroundServiceTest.kt` | Fresh-process sticky restore, explicit-disconnect race, foreground-capability suppression, service type/notification behavior. | Kill/recreate service and application owners under Robolectric plus real-device `am force-stop`/process-kill lanes. Treat service liveness separately from durable correctness. |
| `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/chat/ClientDatabasesTest.kt` | Disposable cache versus durable client DB behavior and migration/removal rules. | Separate reconstructable projections from authoritative intent/task/event tables and test migrations independently. |

### Capability, policy, agent, and state invariants

| Exact local source | Observed test intent | Kinetic use |
|---|---|---|
| `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/node/InvokeCommandRegistryTest.kt`, `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/node/InvokeDispatcherTest.kt`, `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/node/AndroidPermissionSnapshotTest.kt` | Flavor/permission/foreground availability, execution-time dispatch, structured failures, and independently advertised grants. | Per-capability contract suite: availability is not authorization; revoke permission between proposal and dispatch and assert zero executor entry. |
| `Eco_reference/openclaw/src/agents/agent-tools.before-tool-call.integration.e2e.test.ts` | Hooks block before execution, abort prevents late hook completion entering a tool, adjusted parameters stay run-scoped, hook deduplication, and parallel outcomes preserve model order. | Translate test intent, not TypeScript fixtures: final parameters must be reauthorized once, per run/tool-call identity. |
| `Eco_reference/openclaw/src/agents/tool-loop-detection.test.ts` | Repeated/failing tool patterns are detected and bounded. | Property/fuzz corpus for effect budgets and model-visible recovery without another side effect. |
| `Eco_reference/openclaw/packages/tool-call-repair/src/grammar.test.ts`, `Eco_reference/openclaw/packages/tool-call-repair/src/payload.test.ts`, `Eco_reference/openclaw/packages/tool-call-repair/src/stream-normalizer.test.ts` | Malformed/split/oversized tool markup, protected Markdown ranges, byte caps, incremental buffering, terminal scrubbing, no secret/call leakage, and linear scanning. | Adapt malformed-call corpora and invariants. Kinetic's executor must still accept only validated structured calls; repair never directly executes. |
| `Eco_reference/openclaw/src/provider-runtime/operation-retry.test.ts` | Malformed attempt counts, retryable versus permanent failures, abort during backoff, and no default retry for create operations. | Operation-kind retry matrix with deterministic virtual time and explicit side-effect/idempotency classes. |
| `Eco_reference/openclaw/src/agents/sessions/session-manager.user-idempotency.test.ts` | Same text with distinct keys remains distinct; key collision outside the append parent is rejected; concurrently persisted turns are adopted without duplication. | Append-only Room transcript tests with exact idempotency and branch/parent identity. |
| `Eco_reference/openclaw/src/tasks/task-registry.maintenance.issue-60299.test.ts` | Stale runtime ownership becomes `lost`; live owners remain; durable cron terminal results repair stale tasks; unrelated records cannot repair; retention is bounded. | Process-death reconciliation matrix for run/task/effect/delivery state. |
| `Eco_reference/openclaw/src/cron/service.restart-catchup.test.ts`, `src/cron/service/ops.run-admission.test.ts` | Missed-slot catch-up, interrupted marker repair without blind replay, one-shot non-replay, shared concurrency caps, queued reservation revalidation and cleanup. | WorkManager/fake-clock scheduling tests. Port semantics, not cron/Node process mechanics. |
| `Eco_reference/openclaw/src/trajectory/runtime-store.sqlite.test.ts` | Database order rather than recorder-local sequence, bounded trailing windows, global retention, session cascade. | SQLite trajectory-store contract, with a separate non-droppable effect journal so retention cannot erase recovery authority. |
| `Eco_reference/openclaw/src/skills/workshop/service-evaluation.test.ts`, `history-scan.resume.test.ts`, `experience-review.test.ts` | Attributed evaluation lifecycle, baseline/candidate drift rejection, blocking outcomes, persisted scan resume, delayed policy recheck, and conservative proposal prompts. | Strong lifecycle tests for learned-skill **proposals**. Evaluator outputs are mocked; these do not demonstrate that a learned skill improves held-out tasks. |

### Android benchmark assets and limitations

- `Eco_reference/openclaw/apps/android/benchmark/src/main/java/ai/openclaw/app/benchmark/StartupMacrobenchmark.kt` runs ten cold-start timing iterations and ten warm startup/scroll frame-timing iterations. It is directly adaptable, but its known-device `assumeTrue` skip must be a hard “no measurement” failure in a supported release-lab lane.
- `Eco_reference/openclaw/apps/android/benchmark/src/main/java/ai/openclaw/app/benchmark/CronJobNavigationTest.kt` drives a deterministic screenshot-mode Automations fixture through UIAutomator. It is a UI navigation smoke test, not scheduler execution validation.
- `Eco_reference/openclaw/apps/android/scripts/perf-online-benchmark.sh` measures a live Gateway-connected UI path through ADB/UI dumps; `perf-startup-hotspots.sh` wraps simpleperf startup capture. Both are environment-bound diagnostic templates.
- OpenClaw supplies no end-to-end Android agent task-success suite, WorkManager/battery budget suite, thermal/local-inference benchmark, or broad instrumentation matrix. Its Android strength is failure semantics, not autonomous task evaluation.

## Reusable Hermes assets

Hermes has the broadest adversarial host-runtime corpus, but it is Python/desktop/server code. Port the scenario and oracle, not its thread/process implementation.

| Exact local source | Observed test intent | Kinetic use |
|---|---|---|
| `Eco_reference/hermes-agent/tests/run_agent/test_streaming_tool_call_repair.py`, `test_dropped_tool_call_recovery.py` | Repairs fragmented arguments; recovers when finish reason claims a dropped call; bounds retries; keeps repair scaffolding out of durable transcript. | Provider-stream corruption fixtures and transcript cleanliness assertions. |
| `Eco_reference/hermes-agent/tests/run_agent/test_tool_call_guardrail_runtime.py` | Repeated-failure warn/halt, blocked calls never start, rewrite occurs before guard/approval/checkpoint/dispatch, concurrent result order remains deterministic. | State-machine ordering assertions and side-effect budget tests. |
| `Eco_reference/hermes-agent/tests/run_agent/test_tool_batch_segmentation.py` | Maximal parallel-safe segments separated by effect/path barriers; reader/writer overlap and symlink aliases; cancellation drains later segments with paired results. | Resource-scope scheduling property tests. Avoid name-based “parallel safe” guesses. |
| `Eco_reference/hermes-agent/tests/run_agent/test_provider_fallback.py` | Ordered/deduplicated fallback chains, provider API-mode transition, credential isolation, pool rotation. | Local/cloud router test matrix, augmented with OpenClaw's no-replay-after-commit rule. |
| `Eco_reference/hermes-agent/tests/run_agent/test_interrupt_propagation.py`, `Eco_reference/hermes-agent/tests/tools/test_approval_interrupt.py` | Parent/child and per-thread cancellation isolation; a blocked approval wait denies promptly on the owning interrupt while another thread's interrupt does not release it. | Structured coroutine cancellation, per-run isolation, and approval-wait interruption tests. |
| `tests/tools/test_smart_approval_injection.py` | Shell-comment injection stripping and anti-injection prompt framing around approval classification. | Retain adversarial strings as fixtures, but deterministic policy—not a guard LLM—must own Kinetic authorization. |
| `tests/cron/test_execution_ledger.py`, `test_claim_job_for_fire.py` | Durable claimed/running/terminal transitions, terminal immutability, corrupt-store fail-closed, restart marks unknown without requeue, at-most-once fire claim/CAS and stale-claim recovery. | Excellent schedule/effect ledger requirements for Room transactions and restart tests. |
| `tests/test_batch_runner_checkpoint.py`, `test_batch_runner_durability.py` | Atomic checkpoint replacement, deduped resume state, trajectory `fsync` before checkpoint completion, cleanup after interruption. | Adapt ordering and atomicity principles. `tests/integration/test_checkpoint_resumption.py` is a diagnostic script with prints/manual timing, not a release-ready automated gate. |
| `tests/plugins/memory/test_holographic_retrieval.py` | Sanitized FTS queries and a small seeded retrieval fixture. | Starting fixture only; far too small for Kinetic retrieval quality/privacy evaluation. |
| `tests/tools/test_skill_improvements.py`, `test_skill_provenance.py` | Skill patch mechanics, ambiguous patch failure, write-origin context isolation. | Proposal mechanics/provenance tests, not learned-skill utility evidence. |
| `tests/test_trajectory_compressor.py` | Token metrics, protected turns, paired tool/assistant preservation, truncation and summary behavior. | Context projection tests; raw audit/effect journal must never be destructively compacted. |
| `tests/stress/test_property_fuzzing.py`, `test_concurrency_reclaim_race.py` | Randomized Kanban invariants and late-complete/CAS races. | Translate to state-machine model/property tests with fake time and many interleavings. |
| `tests/stress/test_benchmarks.py` | Dispatch/recompute/query latency at 100/1k/10k tasks and JSON output. | Useful benchmark shape, but it prints baselines and has no pass thresholds. Kinetic must version budgets per device tier. |
| `tests/conformance/test_vector_generator.py` and `tests/conformance/vectors/*.json` | Reproducible platform formatting vectors with parity/semantic/divergent labels and source commit. | Strong fixture provenance pattern; its current vectors concern channel formatting, not Android agent success. |

Hermes gaps are decisive: no Android lifecycle or permission harness, no screenshot grounding corpus, no battery/thermal test, and no held-out evaluation proving its background skill curation improves future tasks.

## Reusable MobileRun assets

| Exact local source | Observed test/eval intent | Kinetic use |
|---|---|---|
| `Eco_reference/mobilerun-main/tests/test_android_vision_coordinate_contract.py`, `test_ios_vision_coordinate_contract.py`, `test_vision_sizing.py` | Declared model screenshot dimensions, affine conversion back to native space, provider-specific resize policy, out-of-space rejection, and refusal when the per-step coordinate contract disappears. | Best coordinate-space contract in the references. Port fixtures into named Kotlin units such as `ScreenshotPx`, `PhysicalPx`, `ViewportId`, and reject stale/undefined transforms. |
| `tests/eval_coordinate_grounding.py` | Manual connected-device/model eval chooses accessibility-bounded labels, sends the exact resized+grid image, parses model coordinates, converts them, and reports hit plus distance-to-center. | Convert into a versioned, non-mutating physical-device eval with fixed screens/targets, model/provider versions, repeated trials, hit rate, center error and independent UI-state oracle. It is currently not collected by pytest. |
| `tests/test_fast_agent_malformed_tool_guard.py`, `test_fast_agent_xml_parser.py` | Three-strike bound, no execution for malformed DSML/XML, valid-call recovery and streak reset, adversarial nested/unterminated markup. | Add to Kinetic's provider/parser corpus; assert no capability executor entry before closed-schema validation. |
| `tests/test_manager_response_validation.py` | Rejects ambiguous/nested/unterminated final/plan forms, repairs within a bound, fails closed, and prevents invalid planner output reaching executor. | Planner-envelope conformance. It validates shape, not plan quality or task success. |
| `tests/test_macro_v2_schema.py`, `test_macro_recording_actions.py`, `test_macro_state_matcher.py`, `test_macro_guarded_replay.py` | Version gate, refreshed pre-state, no secret serialization, normalized state match, wait-until-match, stop or agent handoff on divergence. | Adapt guarded replay semantics to signed/versioned Kinetic workflows. Replace raw coordinate reuse with semantic targets and reauthorization at handoff. |
| `tests/test_inference_retries.py` | 400/401/403/404/422 are permanent; 408/409/425/429/5xx retry across chat/completion/structured helpers; provider-specific status shapes. | Provider operation retry table with virtual time; combine with effect/idempotency classification. |
| `tests/test_config_loader_permissions.py`, `test_app_cards.py` | Owner-only POSIX config mode and app-card retrieval behavior. | POSIX mode is not an Android secret-store test; app-card fixtures can inspire signed/versioned AppGuide tests. |

`Eco_reference/mobilerun-main/agent-test-flows/README.md` is explicitly exploratory: an AI invents tasks, there is “no fixed script,” and success is independently checked with screenshots. Preserve it as a manual charter only. A release gate needs frozen tasks, seeded device/app state, assertions, exact build/model/device identifiers, and immutable evidence. The absent `mobilerun-core-local`/Portal implementation also prevents treating wrapper tests as complete driver validation.

## Reusable OpenDroid assets

OpenDroid's 26 files are all JVM-side; there is no instrumentation source set. The most valuable materially inspected assets are:

- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/llm/WrappedLLMProviderTest.kt`: same-model snapshot across retry, no retry for cancellation, stream retry only before first emission, exact delay/jitter validation, bounded empty-stream failure, and no implicit endpoint for custom providers.
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/agent/ActionSchemaTest.kt`: required/default/enum-synonym/unknown-action cases. Kinetic should be stricter: unknown or corrected values stay non-executable until a closed typed request is validated.
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/agent/AutoApprovalPolicyTest.kt` and `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/agent/NeverAutoApproveTest.kt`: all-plan/fallback blocking and hard-category cases. The YOLO behavior that approves irreversible actions is a negative fixture, not a feature to preserve.
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/data/db/OpenDroidDatabaseMigrationTest.kt`: Robolectric migration chain/schema/data preservation from exported Room schemas.
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/permissions/PermissionModelTest.kt`: API-level visibility/request-order matrices, partial grant, “don't ask again,” special-settings routes, and exclusion of pseudo-permissions from runtime requests.
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/voice/VoiceApprovalParserTest.kt`: rejection wins over mixed approval language and substring false positives.
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/actions/base/ActionResultTest.kt`: result/fallback/needs-input mapping.

Gaps: no `AgentLoop -> planner -> dispatcher` end-to-end fixture, no kill-point resume, no handler-bound permission revocation, no accessibility/screenshot benchmark, no WorkManager/foreground-service instrumentation, and no LiteRT device/thermal/structured-tool suite.

## Reusable AndyClaw assets, with GPL boundary

AndyClaw's test source is GPL-3.0-covered project source. Use it to derive independently authored behavioral requirements unless Kinetic intentionally accepts GPL obligations.

- `Eco_reference/AndyClaw/AndyClaw/src/test/java/org/ethereumphone/andyclaw/ExecutionEngine/ParallelExecutionEngineTest.kt` exercises empty/single/mixed batches, executor exception conversion, result order, preflight block, permission/approval callbacks, parallel timing and phase metrics. Preserve the semantic oracles; replace wall-clock `<500 ms` assertions with virtual scheduling/barriers to avoid flakes. The source audit found important missing/defective cases—postprocessor composition, pre-display safety, cancel-orphaned work, double execution around approval—which should become Kinetic negative tests.
- `Eco_reference/AndyClaw/app/src/test/java/org/ethereumphone/andyclaw/llm/LocalLlmClientTest.kt` covers valid/unknown/malformed tagged/raw JSON calls, text preservation, tool schema prompt injection, and a rough prompt-size cap.
- `Eco_reference/AndyClaw/app/src/androidTest/java/org/ethereumphone/andyclaw/llm/LocalLlmInferenceTest.kt` loads a downloaded Qwen2.5-1.5B GGUF, checks basic/tool/no-tool prompts, logs tokens/time, and asserts total time below 120 seconds. It skips when the model is absent and does not gate peak RSS, energy, thermal throttling, TTFT, sustained throughput, cancellation, structured-argument accuracy, checksum, or device tiers; treat it as a harness sketch.
- `Eco_reference/AndyClaw/app/src/test/java/org/ethereumphone/andyclaw/skills/ToolSearchServiceTest.kt`, `Eco_reference/AndyClaw/app/src/test/java/org/ethereumphone/andyclaw/skills/builtin/ToolBridgeTest.kt`, and `Eco_reference/AndyClaw/app/src/test/java/org/ethereumphone/andyclaw/skills/SmartRouterTest.kt` cover search caps/session carryover, unknown/disabled/approval-required tools, ordered parallel results, and keyword/tier routing. Build a clean-room ranked retrieval corpus with relevance judgments rather than copying GPL fixtures.

## AirLLM and AnyClaw posture

AirLLM has narrow host-inference assets:

- `Eco_reference/airllm/air_llm/tests/test_kimi_k3_split.py` builds miniature safetensor checkpoints and asserts every module survives bit-for-bit, packed integer dtype remains intact, one-module shards are hard-linked rather than copied, and shared shards are materialized safely.
- `Eco_reference/airllm/air_llm/tests/test_compression.py` checks no-compression equality and 4/8-bit round-trip RMSE below 0.1 on CUDA tensors; `Eco_reference/airllm/air_llm/tests/test_automodel.py` checks model-family dispatch.
- `Eco_reference/airllm/air_llm/tests/test_streaming_gpu.py` is a manual CUDA harness for VRAM cap, peak allocated memory, elapsed generation, and exact greedy-token parity against a full-load reference. `Eco_reference/airllm/air_llm/airllm/profiler.py::LayeredProfiler` only accumulates stage timings/minimum free CUDA memory.
- `Eco_reference/airllm/scripts/test_cn_dataset_lenghts.py` downloads a tokenizer/dataset and prints source/target length quantiles. It is manual data analysis, not inference correctness or an Android benchmark.

These are **RESEARCH_ONLY** for Kinetic. They do not measure Android RSS, flash bandwidth/wear, joules/token, TTFT, sustained throughput, thermal throttling, delegate compatibility, process death, or structured generation.

AnyClaw has zero Go tests. Materially inspected examples `Eco_reference/anyclaw/examples/cli.yaml`, `Eco_reference/anyclaw/examples/openapi.yaml`, `Eco_reference/anyclaw/examples/script.yaml`, `Eco_reference/anyclaw/examples/web.yaml` and registry descriptors under `Eco_reference/anyclaw/registry/packages/` are demonstrations containing shell templates, Python/network scripts, raw OpenAPI and untyped pipelines. Use them to build parser rejection/confinement fixtures: unsupported versions, heterogeneous command adapters, unknown keys/types, argument-location collisions, unsafe URL/redirects, size/time limits, template injection, unauthorized runtime, and missing output schema. Successful parsing must never imply executable authority.

## Kinetic coverage and oracle matrix

| Required concern | Best local precedent | Kinetic test design and independent oracle | Proposed gate |
|---|---|---|---|
| Task success | MobileRun manual flow charter; AndyClaw real-model smoke; neither is sufficient | Versioned scenario corpus with seeded fixture apps/data, initial state, allowed effects, terminal state predicate, cost budget and prohibited effects. Verify device/app state independently, never from the agent's final text. Repeat stochastic runs by exact model/version/seed/settings. | Critical deterministic tasks: 100%. Model/UI corpus: versioned target plus 95% confidence interval; initial RC target at least 90% for Play-safe structured tasks and 80% for cross-screen UI tasks, with zero prohibited effects. |
| Tool correctness | OpenClaw dispatcher/approval tests; Hermes segmented dispatch; OpenDroid schema tests | Contract suite generated per descriptor: valid boundaries, missing/extra/wrong types, availability, foreground, permission, idempotency, output/error schema, redaction and executor call count. | 100% contract cases; zero executor entry for invalid/unauthorized input. |
| Planner quality | MobileRun manager validation/direct-vs-manager; OpenDroid plan model reveals failed dependencies may still unblock | Labeled goals with valid dependency DAGs, effect/cost budgets and recoverable faults. Score goal coverage, invalid/dead/redundant steps, dependency correctness, risky-step ordering, replan quality and executed-versus-proposed divergence. | Schema/DAG/policy validity 100%; no dependent effect after failed prerequisite unless an explicit replan supersedes it; quality may not regress more than 3 percentage points from frozen baseline. |
| Hallucinated tool arguments | OpenClaw tool-call repair; Hermes/MobileRun malformed-call corpora | Grammar/schema mutation and property fuzzing: unknown tool, unknown field, Unicode confusable, nesting/depth/size, split stream, duplicate keys, bad enum/URI/number, prose/tool hybrids. Record parser/validator/policy/executor boundary. | 100% rejected or model-visible typed repair; zero side effects and zero secret/call-markup leakage. |
| Cancellation | Hermes propagation/approval interrupt; OpenClaw abort-before-hook and invoke deadlines | Inject cancel during queue, model wait/delta, policy, approval, before executor, idempotent executor, ambiguous external effect, child task, delivery and compaction. | No new effect starts after cancellation; in-process P95 acknowledgement <=1 s and network/provider P95 <=2 s where cancellation is supported; unsupported external cancellation becomes explicit `outcome_unknown`, never success. |
| Process death | OpenClaw outbox/service/task/cron recovery | Kill after each durable boundary: admission, approval persistence, effect intent, dispatch, side effect before result, result before transcript/delivery, schedule reservation, model download activation. Recreate process and reconcile from Room only. | All fault points converge to specified state; zero blind replay of non-idempotent/unknown effects; zero lost accepted durable intents. |
| Permission denial/revocation | OpenClaw registry/dispatcher; OpenDroid permission matrix | Fresh denial, rationale, “don't ask again,” special-settings denial, one-time permission expiry, revoke between proposal/approval/dispatch, background/foreground transition, flavor absence. Use fake capability then real device. | Typed denial and zero effect in 100%; stale approval/grant never bypasses execution-time check. |
| Network loss | OpenClaw reconnect/outbox; MobileRun/Hermes retry tables | Mock DNS/TLS/pin failure, offline before enqueue, disconnect before headers, after server acceptance, mid-stream, 408/409/425/429/5xx, captive/slow network and restore. Server fixture records accepted idempotency IDs. | Retry only declared-safe operations; ambiguous creates are parked/reconciled; no duplicates; reconnect ordering/property tests all pass. |
| Provider/local-cloud failover | OpenDroid pre-emission stream retry; Hermes fallback; OpenClaw no replay after committed evidence | Matrix by error phase, partial token/tool call, credential/model capability, local load/thermal failure, cloud privacy policy and committed effect. | Fallback only before externally visible/committed work; no secret cross-provider leakage; exact model/provider attribution in trajectory; zero replay after ambiguous effect. |
| Accessibility/screenshot grounding | MobileRun coordinate contracts/manual eval; OpenClaw advanced stale-epoch checks | Frozen synthetic screens plus physical fixture app across DPI, aspect, rotation, insets, font scale, locale, dark mode, animation and occlusion. Bind target to screenshot/UI-tree hash, window/package/epoch and affine transform. | Synthetic target hit >=95%, physical fixture hit >=90%, zero action on stale epoch/wrong package/undefined transform/password field. Advanced-only suite cannot gate availability of Play Core. |
| Task/session resume | OpenClaw session/task/outbox; Hermes checkpoint/ledger | Restart/branch/retry matrices with exact idempotency, parent/leaf identity, model/tool partials, child tasks and delivery status. Compare final journal/projection against uninterrupted golden run. | Semantically identical terminal state and no duplicate effects; unsupported in-flight effect explicitly unknown; all supported Room migrations pass. |
| Memory retrieval | Hermes small FTS fixture; OpenClaw memory contracts | Versioned corpus with relevant facts, temporal supersession, distractors, paraphrase, multilingual/CJK, sensitivity/owner scopes, deletions and adversarial prompt text. Measure Recall@k, MRR/nDCG, stale-fact rate, leakage and latency. | Initial Recall@5 >=0.90 and MRR >=0.80 on frozen corpus; zero cross-user/sensitivity/deleted-fact leakage; thresholds split by local/cloud route. |
| Learned-skill reuse | OpenClaw evaluation lifecycle; Hermes patch/provenance mechanics | Train/propose on one set, compile/static-policy test, evaluate on disjoint held-out tasks and app/model versions, compare no-skill baseline, require explicit activation, then rollback/canary. Track contamination and authority delta. | No authority expansion; all static/safety cases pass; activate only if held-out success improves significantly or median model/tool rounds fall >=20% with no correctness/safety regression; rollback restores prior behavior. |
| Battery, thermal, runtime performance | OpenClaw macrobenchmark/simpleperf; Andy local model; AirLLM GPU harness | Macrobenchmark startup/frame timing; Perfetto/simpleperf; Android power/energy counters where supported; PSS/storage/network; local-model TTFT/tokens/s/joules per token; 30-minute sustained and 24-hour background/wakeup soak on physical tiers. | No ANR/crash; <=10% regression from frozen tier baseline for startup, TTFT, throughput and energy/task unless approved; obey thermal policy without runaway retries; absolute budgets are frozen after the Phase 1/device spike, not invented per release. |

The numeric quality/performance targets above are provisional Phase 0 gates. They must be frozen with the scenario corpus and device/model matrix before implementation comparisons begin; changing a threshold after seeing a candidate result invalidates the comparison.

## Proposed test harness architecture

Kinetic needs five reusable seams before broad capabilities are implemented:

1. `ScriptedModel`: emits deterministic text/tool deltas, failures, partials, delays and usage; records normalized requests. It drives kernel gates without paying for or depending on a live model.
2. `RecordingCapabilityExecutor`: exposes typed availability/permission/effect semantics, blocks on controllable barriers, records canonical requests and supports known/unknown outcomes.
3. `VirtualClockAndScheduler`: controls timeouts, retry jitter, WorkManager-like due windows, missed schedules and expiry without wall-clock sleeps.
4. `FaultInjector`: can terminate the process or throw/disconnect at named journal boundaries, corrupt/truncate selected non-secret fixtures, revoke permission, change foreground target, and swap network/provider state.
5. `IndependentOracle`: reads fixture-app database/content-provider state, fake HTTP server receipts, Room journal, UIAutomator state or screenshot target bounds. It never trusts the model's completion claim.

Every scenario should declare: immutable ID/version; required capability profile; initial durable/device/app state; prompt/event trigger; exact model/provider policy; allowed and prohibited effects; injected fault schedule; terminal state predicate; time/token/effect/energy budget; privacy class; repetitions; and artifact retention policy.

## Staged test pyramid and CI lanes

| Layer | Typical contents | Environment | Cadence |
|---|---|---|---|
| L0 static/conformance | Kotlin/API compatibility, schema snapshots, forbidden Play manifest/dependency/source scans, licenses/model hashes, skill compiler, migration declarations | Host CI | Every change |
| L1 pure unit/property | Agent state machine, planner DAG, parser/schema, policy monotonicity, canonical hashes, retry/fallback, routing, redaction, coordinate transforms, memory ranking | JVM with fake clock/model/executor; property/fuzz runner | Every change; fast shard |
| L2 component integration | Room migrations/transactions, journal/projector, MockWebServer, scheduler adapter, Keystore abstraction, model download store, trajectory export | JVM/Robolectric plus instrumented DB where needed | Every change/nightly |
| L3 emulator instrumentation | Permissions, foreground/background/service recreation, rotation/process recreation, intents/content providers/AppFunctions adapter, Compose accessibility, fake fixture apps | API-level emulator matrix | Nightly and RC |
| L4 physical deterministic | OEM process management, real sensors/audio/camera where safe, accessibility/screenshot grounding, local inference, network switching, thermal/memory/power | Managed low/mid/high device lab | Nightly subset; weekly/full RC |
| L5 stochastic agent eval | Frozen tasks across approved cloud/local models, direct versus planned modes, skill/no-skill A/B, repeated trials and confidence intervals | Isolated seeded emulator/physical fixtures | Nightly canary; full RC |
| L6 adversarial/soak | Process-kill matrix, 24-hour scheduling/background, concurrency, fuzz, storage exhaustion, corrupt DB/model, security abuse corpus | Dedicated emulators/devices; no personal data | Weekly and RC |

PR gating should stay deterministic and under a fixed time budget. Live provider/model tests must report `PASS`, `FAIL`, or `NOT_RUN` with reason; `NOT_RUN` is never silently treated as pass for a supported RC configuration. Flaky retries may diagnose infrastructure but cannot convert a reproducible product failure into green.

## Minimum device and environment matrix

The exact commercial device list belongs in the later compatibility phase, but the axes are already clear:

| Axis | Minimum coverage |
|---|---|
| Android versions | Emulator at Kinetic's eventual minimum API, a middle API, target API, and latest supported API; preview builds are informational until declared supported. |
| Hardware tiers | Low tier (4–6 GB RAM, slower storage/CPU), mid tier (~8 GB), high tier (12+ GB/accelerator), each with measured available storage. |
| OEM lifecycle | Pixel/reference behavior plus at least two OEMs with materially different background/process-management behavior; force-stop, low-memory kill, reboot, app update and restore. |
| Display/UI | Small/large phone, portrait/landscape, at least one foldable/tablet if supported; 1.0/large font, multiple densities, gesture/three-button navigation, light/dark, animation scale, RTL and representative locales. |
| Network | Offline, Wi-Fi, cellular-equivalent throttling, high latency/loss, DNS failure, TLS/pin mismatch, network handoff and server-accepted/client-disconnected ambiguity. |
| Permission state | Fresh install, partial grant, denied, permanently denied, one-time grant expired, permission revoked while queued, special access removed, Play versus Advanced flavor. |
| Inference | Scripted model everywhere; approved cloud providers in isolated lane; each bundled/downloadable local model on every declared compatible hardware/backend tier. |
| Storage/thermal/power | Low-space download/migration, battery saver, charging/not charging, thermal headroom and sustained load. Absolute thresholds are per tier, not averaged across devices. |

Use synthetic fixture apps and test accounts. Never run destructive/paid/message/contact/calendar scenarios against personal data or production services.

## Canonical trajectory and evaluation schema

The trajectory must be a typed projection of durable events, not an ad hoc debug log. Critical state/effect events are transactionally durable and non-droppable; large screenshots/audio/model prompts are separate content-addressed artifacts that may be omitted by policy without invalidating recovery.

| Field group | Required contents |
|---|---|
| Schema/order | `schemaVersion`, `eventId`, monotonic `sequence`, wall and elapsed timestamps, parent/causal event IDs. Storage order is authoritative, following OpenClaw's SQLite trajectory test. |
| Correlation | `scenarioId/version`, `sessionId`, `taskId`, `runId`, `attemptId`, `turnId`, `planId/stepId`, `toolCallId`, `effectId`, `deliveryId`. |
| Build/environment | App/version/commit/flavor, Android API/build, device model/tier, locale/display/thermal/network state, model/provider/backend/version/quantization, policy/capability/skill versions. |
| Event type | Admission, state transition, plan produced/revised, model request/delta/terminal, tool proposed/parsed/validated, policy decision, approval request/decision, effect intent/start/result/unknown, memory query/hits, skill proposal/evaluation/activation/rollback, checkpoint/recovery, delivery and terminal outcome. |
| Decision evidence | Canonical schema/result, argument hash rather than secret args where possible, permission/foreground/policy snapshot, approval binding/expiry, retry/fallback reason, idempotency key, executor outcome certainty. |
| Artifacts | Content hash, MIME/size/dimensions, screenshot viewport/affine transform/UI-tree epoch/package/window, redaction policy and optional encrypted artifact reference. Never inline secrets/password pixels by default. |
| Oracle/eval | Expected terminal/effects, independent observed state, pass/fail reason, task success, tool/plan/grounding/retrieval metrics, latency/token/energy/resource metrics, evaluator/version and confidence/repetitions. |
| Privacy/retention | Data classification, redaction actions, local/export consent, encryption key reference, retention/expiry and deletion tombstone. Raw prompts, tool results, contacts and screenshots are not ordinary telemetry. |

Minimum causal order for a mutating tool is:

```text
tool.proposed -> tool.validated -> policy.decided
-> approval.persisted/resolved -> effect.intent.persisted
-> effect.started -> effect.result | effect.outcome_unknown
-> transcript/projector update -> delivery state -> run terminal
```

Fault injection occurs between every adjacent pair. A replay runner rehydrates the journal and asserts the same terminal semantics, while never reissuing an ambiguous non-idempotent effect.

## Release gates

### Non-negotiable correctness and safety gates

1. All L0–L2 deterministic suites pass with zero quarantined product failures; every skip has an explicit unsupported configuration rather than a missing dependency accident.
2. Invalid, hallucinated, stale, unauthorized, unapproved, or permission-revoked tool calls produce **zero executor entries** across the fixed corpus and property/fuzz suite.
3. Approval replay, changed-argument digest, expired approval, wrong run/tool/caller, and flavor/policy drift are rejected 100%.
4. The full named process-death matrix converges without duplicate non-idempotent effects, lost accepted intents, or false success. Ambiguous effects remain explicit and require reconciliation/user decision.
5. Cancellation starts no later effect, unblocks policy/approval/model waits within the declared latency budget, and cannot leak across runs/children.
6. Every supported Room migration path preserves required data and produces the exact current schema; corrupt or newer-unknown stores fail closed with recoverable export/diagnostics.
7. Play Core manifest/dependency/source scans find zero Advanced-only services/permissions, general accessibility control, dynamic executable code, or prohibited broad storage/package surfaces.
8. Trajectory/privacy tests find zero raw secrets, passwords, auth headers, sensitive screenshots, or cross-user memory in default logs/exports.

### Quality and performance gates

1. Frozen task, planner, grounding, retrieval and skill-eval corpora meet the versioned thresholds in the coverage matrix with confidence intervals and no safety failure.
2. Candidate versus last released build has no unexplained >3-point task/grounding quality regression and no >10% regression in tier-specific startup, TTFT, throughput, energy/task or memory budgets.
3. Provider failover passes every pre-/post-emission and pre-/post-effect case; model/provider/credential attribution is exact in traces.
4. Local inference passes model hash/load, structured tool/no-tool, cancellation, memory, thermal and sustained-run gates on every device declared compatible. A skipped/missing model is `NOT_RUN`, not pass.
5. Learned skills remain proposals until static checks, held-out evaluation, authority comparison, approval and rollback tests pass; evaluator/mock lifecycle tests alone cannot authorize activation.
6. A 24-hour background/schedule soak produces no duplicate fires, unbounded wakeups/retries, stuck foreground notification, ANR/crash, or unreconciled running record.

## Major gaps that Kinetic must fill

1. No reference supplies a self-contained native Android agent-kernel end-to-end suite.
2. No repository has a frozen, independently verified Android task-success corpus spanning ordinary apps and Play-safe capabilities.
3. No reference systematically kills an Android process at every model/policy/effect/journal boundary.
4. Android permission tests are largely presentation/registry tests; handler-bound revoke/“don't ask again”/background transitions are missing.
5. No complete WorkManager/AlarmManager/reboot/update/missed-run test matrix exists.
6. MobileRun has the best grounding tests, but its real driver is external and its manual flow invents tasks at run time.
7. Learned-skill tests cover creation, drift, provenance and evaluator lifecycle—not held-out utility, contamination, expiry, compatibility or rollback quality.
8. Memory tests are too small and do not jointly measure relevance, temporal supersession, deletion, sensitivity and cross-user leakage.
9. Local-model tests do not provide a shared Android matrix for structured generation, cloud failover, cancellation, peak RSS, energy/token and thermal throttling.
10. Battery/runtime evidence is mostly startup timing, desktop stress, CUDA VRAM or a weak `<120 s` device smoke threshold.
11. No reference tests an Android AppFunctions bridge, caller identity, version negotiation, cancellation, argument bounds or policy mapping.
12. AnyClaw is untested, and MobileRun lacks Git provenance; neither should donate unmodified release fixtures without reconstruction and attribution review.

## Reuse and provenance decisions

| Repository | Test/eval disposition | License/provenance constraint |
|---|---|---|
| OpenClaw | `ADAPT_FIXTURES` / `PORT_TEST_INTENT`; reuse permissive protocol vectors where useful | MIT plus Pi/pi-mono notice for derived portions; keep source commit and applicable notices |
| Hermes | `PORT_TEST_INTENT`; adapt adversarial strings, state transitions, and conformance provenance format | Root MIT with nested component notices; do not assume all skill/plugin fixtures share root-only provenance |
| OpenDroid | `ADAPT_FIXTURES` for pure schema/provider/permission/migration behavior | Apache-2.0 obligations and dependency/model provenance |
| AndyClaw | `CLEAN_ROOM_BEHAVIORAL_TESTS` by default | GPL-3.0 applies to test source too; direct copying/adaptation has GPL consequences |
| MobileRun | `ADAPT_ALGORITHM_AND_RECONSTRUCT_FIXTURES` | MIT archive, but missing Git metadata/external driver packages weaken exact provenance and completeness |
| AirLLM | `REFERENCE_ONLY` host research benchmark shapes | Apache-2.0 source; model weights and PyTorch/Transformers/bitsandbytes terms are separate |
| AnyClaw | `NEGATIVE_FIXTURES_ONLY` until an independently specified contract suite exists | MIT Go source, but downloaded registry package provenance is independent |

## Material inspection ledger — 92 source files

All paths below are exact and relative to `E:\Projects`.

### OpenClaw — 30

- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/gateway/GatewaySessionReconnectTest.kt`
- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/gateway/GatewaySessionInvokeTimeoutTest.kt`
- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/chat/ChatControllerReconnectRestoreTest.kt`
- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/chat/ChatControllerOutboxTest.kt`
- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/chat/RoomChatCommandOutboxTest.kt`
- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/NodeForegroundServiceTest.kt`
- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/chat/ClientDatabasesTest.kt`
- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/node/InvokeDispatcherTest.kt`
- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/node/InvokeCommandRegistryTest.kt`
- `Eco_reference/openclaw/apps/android/app/src/test/java/ai/openclaw/app/node/AndroidPermissionSnapshotTest.kt`
- `Eco_reference/openclaw/src/agents/sessions/session-manager.test.ts`
- `Eco_reference/openclaw/src/agents/sessions/session-manager.user-idempotency.test.ts`
- `Eco_reference/openclaw/src/agents/tool-loop-detection.test.ts`
- `Eco_reference/openclaw/src/agents/agent-tools.before-tool-call.integration.e2e.test.ts`
- `Eco_reference/openclaw/src/provider-runtime/operation-retry.test.ts`
- `Eco_reference/openclaw/src/cron/service.restart-catchup.test.ts`
- `Eco_reference/openclaw/src/cron/service/ops.run-admission.test.ts`
- `Eco_reference/openclaw/src/tasks/task-registry.process-state.test.ts`
- `Eco_reference/openclaw/src/tasks/task-registry.maintenance.issue-60299.test.ts`
- `Eco_reference/openclaw/packages/tool-call-repair/src/stream-normalizer.test.ts`
- `Eco_reference/openclaw/packages/tool-call-repair/src/payload.test.ts`
- `Eco_reference/openclaw/packages/tool-call-repair/src/grammar.test.ts`
- `Eco_reference/openclaw/src/skills/workshop/service-evaluation.test.ts`
- `Eco_reference/openclaw/src/skills/workshop/history-scan.resume.test.ts`
- `Eco_reference/openclaw/src/skills/workshop/experience-review.test.ts`
- `Eco_reference/openclaw/src/trajectory/runtime-store.sqlite.test.ts`
- `Eco_reference/openclaw/apps/android/benchmark/src/main/java/ai/openclaw/app/benchmark/StartupMacrobenchmark.kt`
- `Eco_reference/openclaw/apps/android/benchmark/src/main/java/ai/openclaw/app/benchmark/CronJobNavigationTest.kt`
- `Eco_reference/openclaw/apps/android/scripts/perf-online-benchmark.sh`
- `Eco_reference/openclaw/apps/android/scripts/perf-startup-hotspots.sh`

### Hermes Agent — 22

- `Eco_reference/hermes-agent/tests/run_agent/test_streaming_tool_call_repair.py`
- `Eco_reference/hermes-agent/tests/run_agent/test_tool_call_guardrail_runtime.py`
- `Eco_reference/hermes-agent/tests/run_agent/test_dropped_tool_call_recovery.py`
- `Eco_reference/hermes-agent/tests/run_agent/test_provider_fallback.py`
- `Eco_reference/hermes-agent/tests/run_agent/test_interrupt_propagation.py`
- `Eco_reference/hermes-agent/tests/run_agent/test_tool_batch_segmentation.py`
- `Eco_reference/hermes-agent/tests/tools/test_approval_interrupt.py`
- `Eco_reference/hermes-agent/tests/tools/test_smart_approval_injection.py`
- `Eco_reference/hermes-agent/tests/cron/test_execution_ledger.py`
- `Eco_reference/hermes-agent/tests/cron/test_claim_job_for_fire.py`
- `Eco_reference/hermes-agent/tests/plugins/memory/test_holographic_retrieval.py`
- `Eco_reference/hermes-agent/tests/tools/test_skill_improvements.py`
- `Eco_reference/hermes-agent/tests/tools/test_skill_provenance.py`
- `Eco_reference/hermes-agent/tests/test_trajectory_compressor.py`
- `Eco_reference/hermes-agent/tests/test_batch_runner_durability.py`
- `Eco_reference/hermes-agent/tests/test_batch_runner_checkpoint.py`
- `Eco_reference/hermes-agent/tests/tools/test_checkpoint_manager.py`
- `Eco_reference/hermes-agent/tests/integration/test_checkpoint_resumption.py`
- `Eco_reference/hermes-agent/tests/stress/test_benchmarks.py`
- `Eco_reference/hermes-agent/tests/stress/test_property_fuzzing.py`
- `Eco_reference/hermes-agent/tests/stress/test_concurrency_reclaim_race.py`
- `Eco_reference/hermes-agent/tests/conformance/test_vector_generator.py`

### MobileRun — 14

- `Eco_reference/mobilerun-main/tests/test_android_vision_coordinate_contract.py`
- `Eco_reference/mobilerun-main/tests/test_ios_vision_coordinate_contract.py`
- `Eco_reference/mobilerun-main/tests/test_vision_sizing.py`
- `Eco_reference/mobilerun-main/tests/eval_coordinate_grounding.py`
- `Eco_reference/mobilerun-main/tests/test_fast_agent_malformed_tool_guard.py`
- `Eco_reference/mobilerun-main/tests/test_fast_agent_xml_parser.py`
- `Eco_reference/mobilerun-main/tests/test_manager_response_validation.py`
- `Eco_reference/mobilerun-main/tests/test_macro_guarded_replay.py`
- `Eco_reference/mobilerun-main/tests/test_macro_state_matcher.py`
- `Eco_reference/mobilerun-main/tests/test_macro_recording_actions.py`
- `Eco_reference/mobilerun-main/tests/test_macro_v2_schema.py`
- `Eco_reference/mobilerun-main/tests/test_inference_retries.py`
- `Eco_reference/mobilerun-main/tests/test_config_loader_permissions.py`
- `Eco_reference/mobilerun-main/tests/test_app_cards.py`

### OpenDroid — 8

- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/llm/WrappedLLMProviderTest.kt`
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/agent/ActionSchemaTest.kt`
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/agent/AutoApprovalPolicyTest.kt`
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/agent/NeverAutoApproveTest.kt`
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/data/db/OpenDroidDatabaseMigrationTest.kt`
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/permissions/PermissionModelTest.kt`
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/core/voice/VoiceApprovalParserTest.kt`
- `Eco_reference/opendroid/app/src/test/java/com/opendroid/ai/actions/base/ActionResultTest.kt`

### AndyClaw — 6

- `Eco_reference/AndyClaw/AndyClaw/src/test/java/org/ethereumphone/andyclaw/ExecutionEngine/ParallelExecutionEngineTest.kt`
- `Eco_reference/AndyClaw/app/src/test/java/org/ethereumphone/andyclaw/llm/LocalLlmClientTest.kt`
- `Eco_reference/AndyClaw/app/src/androidTest/java/org/ethereumphone/andyclaw/llm/LocalLlmInferenceTest.kt`
- `Eco_reference/AndyClaw/app/src/test/java/org/ethereumphone/andyclaw/skills/ToolSearchServiceTest.kt`
- `Eco_reference/AndyClaw/app/src/test/java/org/ethereumphone/andyclaw/skills/builtin/ToolBridgeTest.kt`
- `Eco_reference/AndyClaw/app/src/test/java/org/ethereumphone/andyclaw/skills/SmartRouterTest.kt`

### AirLLM — 6

- `Eco_reference/airllm/air_llm/tests/test_streaming_gpu.py`
- `Eco_reference/airllm/air_llm/tests/test_kimi_k3_split.py`
- `Eco_reference/airllm/air_llm/tests/test_compression.py`
- `Eco_reference/airllm/air_llm/tests/test_automodel.py`
- `Eco_reference/airllm/air_llm/airllm/profiler.py`
- `Eco_reference/airllm/scripts/test_cn_dataset_lenghts.py`

### AnyClaw — 6

- `Eco_reference/anyclaw/examples/cli.yaml`
- `Eco_reference/anyclaw/examples/openapi.yaml`
- `Eco_reference/anyclaw/examples/script.yaml`
- `Eco_reference/anyclaw/examples/web.yaml`
- `Eco_reference/anyclaw/registry/packages/translator/openapi.yaml`
- `Eco_reference/anyclaw/registry/packages/web-access/web-access.yaml`

## Bottom line

Kinetic should begin with a deterministic journal/state-machine conformance suite, not live-model demos. OpenClaw and Hermes provide the richest failure invariants; MobileRun provides the strongest grounding contracts; OpenDroid supplies useful native JVM fixtures; AndyClaw supplies clean-room device/local-inference test ideas; AirLLM remains host research; AnyClaw demonstrates the cost of having descriptors without tests. The release criterion is not “the agent said it succeeded.” It is: independent state proves the goal, every effect was authorized and attributable, the durable journal survives every injected fault, and the same build stays within measured Android device, privacy, power, and thermal budgets.
