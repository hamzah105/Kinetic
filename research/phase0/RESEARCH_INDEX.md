# Project Kinetic — Phase 0 Research Index

## Status

Phase 0 is complete as a research-only artifact set. It contains no Kinetic application modules, production Kotlin, imported dependencies or copied upstream source.

- Reference projects analyzed: **7**
- Normalized materially inspected implementation/source files: **609**
- Raw per-repository material-artifact ledger: **713 files**
- Additional focused testing/benchmark/fixture inspection: **92 files** (overlaps the repository ledgers and is reported separately)
- Markdown reports in this directory: **22** (21 numbered reports plus this index)
- Upstream tests/models/services executed: **0**
- Git-backed reference worktrees modified: **0**

The 609 count normalizes the seven disjoint repository ledgers to production/behavior-bearing source: OpenClaw 220 + OpenDroid 101 + AndyClaw 118 + Hermes 58 + MobileRun 57 + AnyClaw 37 + AirLLM 18. Physical repository-relative path is the identity; tests, examples, README/license and dependency/build/config files are excluded, while runtime prompt/template/descriptor source is included. The 713 figure preserves every report's broader material-artifact boundary. A material read means a full file or a bounded implementation/symbol slice used as evidence; raw search/enumeration is excluded. The 92-file testing ledger overlaps and expands those source audits, so it is not added to either total.

## Recommended reading path

For a fast decision, read:

1. [00 — Executive Summary](00_EXECUTIVE_SUMMARY.md)
2. [20 — Recommendation](20_RECOMMENDATION.md)
3. [12 — Agent Architecture Patterns](12_AGENT_ARCHITECTURE_PATTERNS.md)
4. [10 — Android/Play Capability Matrix](10_ANDROID_CAPABILITY_POLICY_MATRIX.md)
5. [11 — License Provenance Matrix](11_LICENSE_PROVENANCE_MATRIX.md)
6. [19 — Open Questions](19_OPEN_QUESTIONS.md)

For implementation-source planning in a later authorized phase, add [09 — Cross-Repository Matrix](09_CROSS_REPOSITORY_MATRIX.md), [17 — Reuse Candidates](17_REUSE_CANDIDATES.md), and [16 — Testing/Benchmark Assets](16_TESTING_AND_BENCHMARK_ASSETS.md).

## Complete report set

| Report | Purpose | Principal conclusion |
|---|---|---|
| [00_EXECUTIVE_SUMMARY.md](00_EXECUTIVE_SUMMARY.md) | Decision-level summary, scope, statistics and major findings | Build a new durable Android-native kernel from selected OpenClaw/OpenDroid components plus Hermes/MobileRun semantics; do not choose one upstream as the base |
| [01_REPOSITORY_INVENTORY.md](01_REPOSITORY_INVENTORY.md) | Exact checkout/branch/commit, languages, build, license, architecture, tests and completeness | Seven projects are present; six Git checkouts are intact/clean, while MobileRun has no Git metadata and therefore incomplete exact provenance |
| [02_OPENCLAW_ANALYSIS.md](02_OPENCLAW_ANALYSIS.md) | Deep audit of Android, required packages and control-plane source roots | Android is a polished native operator/capability edge, not the trusted agent; Gateway owns the self-contained control plane Kinetic must implement natively |
| [03_OPENDROID_ANALYSIS.md](03_OPENDROID_ANALYSIS.md) | Native loop/actions/providers/memory/permissions/service/data/UI audit | Closest self-contained Android reference, but active work/approvals cannot recover after process death and broad autonomy depends on accessibility |
| [04_ANDYCLAW_ANALYSIS.md](04_ANDYCLAW_ANALYSIS.md) | Agent/executor/extensions/IPC/safety/sessions/memory/local-model audit | Broadest Android feature catalog, but GPL-3.0, privileged assumptions, two inconsistent extension systems and serious execution/security defects require clean-room use |
| [05_HERMES_ANALYSIS.md](05_HERMES_ANALYSIS.md) | Semantic Python agent/provider/context/memory/skill/subagent/gateway/cron audit | Strongest orchestration semantics: persist intent before effect, derived request context, progressive skills and bounded recovery; Python/unrestricted tools do not port |
| [06_MOBILERUN_ANALYSIS.md](06_MOBILERUN_ANALYSIS.md) | Direct and manager/executor agents, UI state, actions, trajectories, macros, app cards and MCP | Best mobile grounding/planner/trajectory contracts; desktop drivers, prompt policy, external packages and missing Git revision prevent direct runtime adoption |
| [07_ANYCLAW_ANALYSIS.md](07_ANYCLAW_ANALYSIS.md) | Package descriptor, OpenAPI, pipeline, adapters, registry, MCP and daemon audit | Bounded dataflow can inspire Skill IR; shell/Python/Node/browser adapters, unsigned installs, unauthenticated bridge and zero tests are Core rejections |
| [08_AIRLLM_ANALYSIS.md](08_AIRLLM_ANALYSIS.md) | Layer streaming, loading, compression, hardware/I/O and Android feasibility | `RESEARCH_ONLY`: reduces weight residency but repeatedly trades memory for I/O/latency/energy; no Android-native runtime exists here |
| [09_CROSS_REPOSITORY_MATRIX.md](09_CROSS_REPOSITORY_MATRIX.md) | Required 30-subsystem best/second reference matrix with paths, licenses, portability, burden and disposition | No repository wins globally; the recommended subsystem-by-subsystem combination consistently favors a new Kotlin domain kernel |
| [10_ANDROID_CAPABILITY_POLICY_MATRIX.md](10_ANDROID_CAPABILITY_POLICY_MATRIX.md) | Technical/permission/background/Play classification for every required Android capability, plus AppFunctions | Play Core must work without autonomous general accessibility, broad storage/package/install privileges, root or daemon assumptions; use build-time profiles |
| [11_LICENSE_PROVENANCE_MATRIX.md](11_LICENSE_PROVENANCE_MATRIX.md) | Repository, path, header/subtree, attribution, copyleft/dependency risk and reuse decision | OpenClaw/OpenDroid are the main permissive pools; AndyClaw source, restrictive Hermes skills, opaque artifacts and unpinned MobileRun source are hard boundaries |
| [12_AGENT_ARCHITECTURE_PATTERNS.md](12_AGENT_ARCHITECTURE_PATTERNS.md) | Cross-source kernel/provider/tool/journal/context/skill/UI/scheduler/IPC synthesis | Durable state is authority; model is proposer; policy broker and effect journal sit between planning and Android execution |
| [13_SKILL_AND_PLUGIN_COMPATIBILITY.md](13_SKILL_AND_PLUGIN_COMPATIBILITY.md) | Measured ecosystem dependencies, three compatibility tiers and declarative learning path | Most useful skill value is metadata/procedure, while much execution is Tier 3 Node/Python/shell; V8 does not solve compatibility |
| [14_LOCAL_MODEL_OPTIONS_FOUND.md](14_LOCAL_MODEL_OPTIONS_FOUND.md) | Every represented local inference route and non-final engine/storage interface proposal | OpenDroid LiteRT-LM/AI Core is the strongest Play-oriented seed; AndyClaw is provenance/GPL-constrained and AirLLM is research-only |
| [15_SECURITY_PATTERNS.md](15_SECURITY_PATTERNS.md) | Trust boundaries, strongest/reference weaknesses, non-final capability metadata and abuse cases | Combine OpenClaw audit/posture, Hermes identity/secrets, OpenDroid pure approval checks and AndyClaw attenuation into a new deny-by-default broker |
| [16_TESTING_AND_BENCHMARK_ASSETS.md](16_TESTING_AND_BENCHMARK_ASSETS.md) | Test/eval inventory, coverage/oracles, device matrix, trajectory schema and release gates | Begin with deterministic journal/policy/provider/grounding conformance and fault injection; live-model demos are not correctness evidence |
| [17_REUSE_CANDIDATES.md](17_REUSE_CANDIDATES.md) | Named code/contract/test candidates, prerequisites and reject list | Narrow OpenClaw Android + OpenDroid infrastructure are the code pool; Hermes/MobileRun mostly donate semantics and tests |
| [18_ARCHITECTURAL_CONFLICTS.md](18_ARCHITECTURAL_CONFLICTS.md) | Twenty-five cross-source conflicts with resolutions and proof still required | Resolve authority, durability, profile separation, dynamic skills, inference, data and lifecycle before architecture freeze |
| [19_OPEN_QUESTIONS.md](19_OPEN_QUESTIONS.md) | P0/P1/P2 questions, future spike evidence and exit criteria | Local-model feasibility, effect state, policy/approval, Play purpose, AppFunctions, Skill IR and data policy are the leading blockers |
| [20_RECOMMENDATION.md](20_RECOMMENDATION.md) | Final architecture/product/source stance and explicit answers to all 25 requested questions | Kinetic is viable without Node/Python/Termux/PRoot if it chooses Android-native durability, security, capability profiles and constrained interoperability |

## Source snapshot

| Reference | Audited local revision/status |
|---|---|
| OpenClaw | `main` at `ebb301c32d9e2e1ec61baa783564eb38e4296895`, clean |
| Hermes Agent | `main` at `87bc710609f8b89b6e6b4aa418dde8ee30ec6873`, clean |
| AndyClaw | `main` at `04a520177a5996777814b59ffeb28c1aeecc489a`, clean |
| OpenDroid | `main` at `9e3ef380eb0084d77054c9a48c42d08654b3ae4b`, clean |
| MobileRun | source archive, 243 files, no `.git`; exact branch/commit/date unknown |
| AirLLM | `main` at `64a4e4fc3749aa7dc9bba4788f560ed0d7e74bd2`, clean |
| AnyClaw | `main` at `eb7052708f993552e70043a1bff8b9a243148c4f`, clean |

No fetch, pull, checkout, reset, dependency installation, model download or destructive command was used. Policy research in report 10 is a dated 2026-08-25 snapshot and must be revalidated before product/release decisions.

## Headline decisions

- **Best native foundation:** OpenClaw Android components around a new kernel; OpenDroid is the best self-contained semantic/integration reference.
- **Best reuse opportunity:** OpenClaw Android outbox/dispatch/handlers/tests plus OpenDroid provider/model/secret/migration infrastructure.
- **Biggest licensing constraint:** AndyClaw GPL-3.0 and unresolved opaque/native/model provenance; restrictive Hermes skill subtrees are also excluded.
- **Biggest Play constraint:** general LLM-planned AccessibilityService autonomy cannot be a Play Core dependency under the audit assumption/current policy evidence.
- **Strongest local-model seed:** OpenDroid LiteRT-LM/AI Core, pending real-device quality/performance/thermal/license validation.
- **Biggest unresolved technical question:** whether a provenance-clear embedded model can meet structured-agent quality and Android resource/device coverage targets.
- **Required lifecycle stance:** Room journal + WorkManager/eligible FGS leases + restart reconciliation; never a foreground-service daemon.
- **Required ecosystem stance:** declarative Skill IR first; genuine Node/Linux/Python stays outside Core.

## Boundary to future work

This index does not authorize Phase 1 or later. The next phase should consume the evidence and P0 gates here; it should not reinterpret these reports as production code or as permission to copy a candidate without its provenance and license conditions.
