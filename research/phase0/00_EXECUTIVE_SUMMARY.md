# Project Kinetic Phase 0 Executive Summary

## Outcome

The audit supports building Kinetic, but not by selecting one reference repository as its base. The evidence favors a new Android-native kernel composed from four strongest sources:

- **OpenClaw Android:** best native reliability/capability edge and selective MIT reuse pool;
- **Hermes Agent:** best provider-neutral orchestration, context, skill, memory and recovery semantics;
- **OpenDroid:** closest self-contained Android agent and strongest Apache-2.0 Android/local-model seed;
- **MobileRun:** best UI grounding, planner/executor, macro and trajectory contracts.

AndyClaw is a valuable GPL-constrained clean-room concept/negative-test reference. AnyClaw contributes a compact declarative descriptor/pipeline idea but unsafe execution boundaries. AirLLM is research-only for model residency; it is not an Android runtime.

The recommended differentiator is not “desktop agent inside Android.” It is a **durable effect-native Android agent**: the model proposes typed operations, an independent capability broker authorizes them, a Room journal owns every attempt/effect, Android lifecycle components temporarily execute eligible work, and restart reconciliation prevents duplicate or forgotten side effects.

## Scope completed

All seven immediate projects under `E:\Projects\Eco_reference` were analyzed at their exact local state:

| Repository | Exact local revision | Role in the recommendation |
|---|---|---|
| OpenClaw | `ebb301c32d9e2e1ec61baa783564eb38e4296895` | Native Android components + control-plane correctness invariants |
| Hermes Agent | `87bc710609f8b89b6e6b4aa418dde8ee30ec6873` | Agent/provider/context/memory/skill/cron semantics |
| AndyClaw | `04a520177a5996777814b59ffeb28c1aeecc489a` | Clean-room Android feature/IPC/local-model concepts; GPL boundary |
| OpenDroid | `9e3ef380eb0084d77054c9a48c42d08654b3ae4b` | Self-contained Android reference and permissive adaptation candidates |
| MobileRun | no Git metadata in archive | UI/planner/trajectory semantics; source reuse blocked pending commit recovery |
| AirLLM | `64a4e4fc3749aa7dc9bba4788f560ed0d7e74bd2` | Model-residency research only |
| AnyClaw | `eb7052708f993552e70043a1bff8b9a243148c4f` | Constrained Skill IR/import/MCP inspiration |

The normalized count is **609 materially inspected implementation/source files**: production and behavior-bearing source, runtime prompts/templates and descriptors, excluding tests, examples, README/license and dependency/build/config files. The raw seven-ledger aggregate is **713 material artifacts** under each report's broader stated boundary. The testing synthesis additionally inspected 92 focused test/benchmark/fixture files, with overlap against those ledgers; it is reported separately rather than added. Raw filename/search enumeration is excluded. No upstream test/model/service was executed and no reference source was modified.

Full provenance and inventory: [01_REPOSITORY_INVENTORY.md](01_REPOSITORY_INVENTORY.md).

## Largest architectural discovery

OpenClaw's Android application is not an Android-hosted OpenClaw agent. `Eco_reference/openclaw/apps/android/app/src/main/java/ai/openclaw/app/NodeRuntime.kt` creates Gateway-facing sessions; Android owns UI, transport and phone capabilities, while Gateway code under `src/agents`, `src/tasks`, `src/cron`, `src/memory`, `src/security`, `src/gateway` and related packages owns the trusted control plane.

Removing Gateway therefore requires much more than moving an LLM API call onto the phone: Kinetic needs a native provider/agent loop, canonical sessions/runs/effects/delivery, policy/approvals, context/memory, scheduling, skills, audit and recovery. The decomposition is in [02_OPENCLAW_ANALYSIS.md](02_OPENCLAW_ANALYSIS.md) and [12_AGENT_ARCHITECTURE_PATTERNS.md](12_AGENT_ARCHITECTURE_PATTERNS.md).

## Largest reuse opportunity

The best future reuse/adaptation pool is the combination of:

- OpenClaw MIT `GatewaySession`, `ChatCommandOutbox`, `ClientDatabases`, `InvokeCommandRegistry`, `InvokeDispatcher`, `AndroidPermissionSnapshot`, TLS/device identity flows, native Android handlers, Play/third-party split and tests;
- OpenDroid Apache-2.0 `WrappedLLMProvider`, `SecurePrefs`, `ModelDownloadWorker`, `OnDeviceModelRegistry`, action/approval invariants, plan identity guards and migration tests;
- portable OpenClaw/Hermes contracts and behavior tests for provider streams, malformed tools, persistence ordering, context/compaction, scheduling, memory and skills.

These components should conform to Kinetic interfaces; they must not drag Gateway, accessibility, Python/Node or a monolithic upstream loop into the kernel. See [17_REUSE_CANDIDATES.md](17_REUSE_CANDIDATES.md).

## Licensing and provenance conclusion

The principal hard boundary is AndyClaw's GPL-3.0 license: direct implementation reuse in a distributed non-GPL Kinetic derivative carries reciprocal licensing consequences. Its active and unfinished extension systems, execution engine, memory and local-model concepts should normally be clean-room reimplemented or interoperated with through an independently specified protocol.

Other non-obvious constraints:

- Hermes productivity-skill subtrees contain restrictive `LICENSE.txt` terms and are rejected for copying/derivation despite the MIT root.
- Hermes `plugins/security-guidance/patterns.py` is Apache-2.0 upstream material with a NOTICE.
- AndyClaw's Llamatik/Tinfoil AARs, vendored Whisper tree and model assets lack sufficient adjacent provenance.
- MobileRun has no exact Git revision in the archive.
- AirLLM has auxiliary files whose MIT comments conflict with its Apache-2.0 root.
- Every model, tokenizer, downloaded skill, remote package and dependency needs its own provenance; repository license does not cover it automatically.

See [11_LICENSE_PROVENANCE_MATRIX.md](11_LICENSE_PROVENANCE_MATRIX.md).

## Play distribution conclusion

Play Core must be useful without general LLM-planned AccessibilityService automation. Current official policy research says Accessibility cannot be requested for an app that autonomously initiates, plans and executes actions/decisions; the exact current sources and caveats are recorded in [10_ANDROID_CAPABILITY_POLICY_MATRIX.md](10_ANDROID_CAPABILITY_POLICY_MATRIX.md).

Core also should not depend on broad storage/package visibility, package installation/dynamic executable code, SMS/call-log roles, overlays, root/Shizuku, persistent special-use foreground service, background capture or battery-optimization exemptions. Prefer app-owned Android APIs, explicit intents/deep links, system pickers, SAF/MediaStore, WorkManager, visible bounded FGS use, AppFunctions and narrow authenticated integrations.

Use one kernel with compile-time `play`, `advanced` and optional `oem/system` capability providers/manifests. OpenClaw's Android product flavors are the strongest local proof of this boundary; OpenDroid/AndyClaw's broad single manifests are counterexamples.

## Local model conclusion

The strongest embedded Android option represented locally is OpenDroid's LiteRT-LM/AI Core stack:

- `Eco_reference/opendroid/app/src/main/java/com/opendroid/ai/core/llm/providers/LiteRTLMProvider.kt`;
- `OnDeviceModelRegistry.kt`;
- `GemmaProvider.kt` and `HybridOnDeviceProvider.kt`;
- `Eco_reference/opendroid/app/src/main/java/com/opendroid/ai/core/llm/ModelDownloadWorker.kt`.

It is a seed, not a selection: CPU is hardcoded in one path, catalog metadata/hashes/licenses are incomplete, and a stream failure can be emitted as normal content. AndyClaw's GGUF/Whisper paths are broader but GPL/provenance-constrained; AirLLM repeatedly streams weights and is not Android-feasible on current evidence. Real-device structured-task, RAM, TTFT, throughput, battery and thermal benchmarks are required. See [14_LOCAL_MODEL_OPTIONS_FOUND.md](14_LOCAL_MODEL_OPTIONS_FOUND.md) and [08_AIRLLM_ANALYSIS.md](08_AIRLLM_ANALYSIS.md).

## Skill and learning conclusion

The useful ecosystem is primarily procedural knowledge, metadata, schemas and workflow semantics—not portable JavaScript. Many OpenClaw/Hermes skills invoke shell, Python, Node, npm or external CLIs. Embedded V8 would not provide Node compatibility.

Recommended tiers:

- **Tier 1:** compile reviewed `SKILL.md`, typed OpenAPI/AppFunction descriptors and bounded AnyClaw-style dataflow into a native declarative Skill IR;
- **Tier 2:** optional restricted expressions/JavaScript only after a demonstrated need, with no ambient Android/network/filesystem authority;
- **Tier 3:** real Node/Linux/Python/shell/CLI/native-addon packages remain outside Core.

Hermes learning can become declarative only as a redesigned evidence→proposal→compile→validate/simulate→review→activate→evaluate/rollback pipeline. A learned skill may compose existing authority but never create new permission or bypass confirmation. See [13_SKILL_AND_PLUGIN_COMPATIBILITY.md](13_SKILL_AND_PLUGIN_COMPATIBILITY.md).

## Required kernel invariants

1. The model is an untrusted planner; authorization is deterministic and outside prompts.
2. Durable Room state is authority; services, workers and coroutines are leases.
3. Tool intent is persisted before an external effect.
4. Non-idempotent unknown outcomes are reconciled, not blindly retried.
5. Provider tokens/tool calls/errors are typed; failure text is never successful output.
6. Context selection does not mutate the durable transcript.
7. Capability availability and policy are checked again at invocation.
8. Approval binds exact canonical arguments and resolved targets.
9. Parallelism requires declared non-conflicting resource/effect scopes.
10. UI targets are bound to a snapshot/viewport/app/window epoch.
11. Secrets never enter model context, ordinary logs or broad audit payloads.
12. Learned skills cannot expand authority and remain versioned/reversible.
13. Play and Advanced capability surfaces are compile-time separable.
14. Process death, permission denial, cancellation, network loss and provider failure are ordinary tested states.

Security comparison: [15_SECURITY_PATTERNS.md](15_SECURITY_PATTERNS.md). Testing/release-gate plan: [16_TESTING_AND_BENCHMARK_ASSETS.md](16_TESTING_AND_BENCHMARK_ASSETS.md).

## Biggest unresolved question

The largest technical unknown is whether a clearly licensed embedded model can deliver reliable structured planning/tool calls within acceptable RAM, latency, battery and thermal limits across a commercially useful Android device range. That result influences offline scope, router design, minimum API/device profile, download/storage architecture and how much planning must remain cloud-backed.

Other P0 blockers are the durable effect state machine, policy/approval schema, Play Core user journeys/profile identity, AppFunctions viability, Skill IR coverage, sensitive-data policy and OEM lifecycle behavior. Each future spike has an explicit exit criterion in [19_OPEN_QUESTIONS.md](19_OPEN_QUESTIONS.md).

## Recommendation

Advance only to requirements/architecture decision work after accepting those P0 gates. Do not begin app scaffolding from a reference tree, promise arbitrary Play-safe cross-app autonomy, select AirLLM/opaque AndyClaw binaries, or add a general scripting runtime.

The full 25-question response, technical/Play/security risk lists and five architectural opportunities are in [20_RECOMMENDATION.md](20_RECOMMENDATION.md). Use [RESEARCH_INDEX.md](RESEARCH_INDEX.md) as the entry point to the complete Phase 0 record.
