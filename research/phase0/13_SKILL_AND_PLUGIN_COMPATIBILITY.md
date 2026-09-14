# Skill and Plugin Compatibility

## Bottom line

The reusable ecosystem is mostly **instruction metadata, tool schemas and workflow semantics**, not portable JavaScript. Embedding V8 would recover only a small subset. Genuine OpenClaw plugins depend on Node/npm semantics; many OpenClaw and Hermes `SKILL.md` packages instruct the agent to use shell, Python, Node or external CLIs. Kinetic should make a validated declarative Skill IR its default and treat restricted JavaScript as an optional Tier 2—not the foundation.

Hermes-style learning can become declarative, but that is a redesign. The checked-out `/learn` implementation asks the current model to gather sources and write a new `SKILL.md` through a management tool; it does not automatically synthesize a bounded executable plan from trajectories.

## Measured local ecosystem shape

These counts are a lexical/filesystem snapshot, not claims that every mention is a hard dependency:

| Skill set | `SKILL.md` count | Skill dirs with only `SKILL.md` | Bundled Python files | Bundled JS/TS files | Bundled shell/PowerShell files | SKILLs mentioning npm family | mentioning Python | mentioning shell/terminal/CLI | mentioning Node |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| OpenClaw `skills/` | 51 | 40 | 6 | 1 | 4 | 7 | 5 | 45 | 11 |
| Hermes `skills/` | 70 | 34 | 68 | 3 | 7 | 18 | 46 | 64 | 14 |
| Hermes `optional-skills/` | 111 | 32 | 79 | 4 | 6 | 20 | 86 | 104 | 27 |

“Only `SKILL.md`” does not mean native/declarative: the instructions may invoke an installed CLI. Conversely, a referenced Python script might be replaceable with a native Kinetic tool. Each skill needs semantic classification.

OpenClaw plugins are a different scale: `Eco_reference/openclaw/extensions/` contains 150 package manifests and 8,134 JS/TS-family files; 1,379 files matched direct Node-builtin/`child_process` usage patterns. No checked-in `.node`/`.so`/DLL was found there, but npm dependencies can still resolve to native addons at install time. This is a Node package ecosystem, not “some JavaScript.”

## Ecosystem formats

### OpenClaw skills

Evidence:

- `Eco_reference/openclaw/skills/skill-creator/SKILL.md` defines frontmatter (`name`, `description`, optional metadata/homepage/allowed-tools/invocation/license) plus progressively loaded `scripts/`, `references/`, `assets/`, and `agents/`.
- `Eco_reference/openclaw/skills/weather/SKILL.md` demonstrates the real semantic split: preferred `web_fetch` tool call is portable, while fallback `curl`, Homebrew install metadata and shell commands are not.
- Runtime discovery/eligibility/prompt assembly lives under `Eco_reference/openclaw/src/skills/` and maps description metadata into model-visible skill context; supporting files are loaded on demand.

What is executable vs declarative:

- Frontmatter and prose are declarative/instructional.
- A code fence is not a typed runtime step; it is text the model may choose to follow.
- Supporting scripts and declared installers become executable only through a real tool/shell/runtime.
- `allowed-tools`/requirements metadata helps availability selection but is not a complete Android permission/risk policy.

Kinetic mapping: ingest description/procedure as source material, resolve references, and compile only recognized operations into Kinetic IR. Never treat arbitrary Markdown commands as pre-authorized execution.

### OpenClaw plugins

Evidence:

- `Eco_reference/openclaw/packages/plugin-package-contract/src/` and `packages/plugin-sdk/src/` define package/host contracts.
- Concrete providers in `Eco_reference/openclaw/extensions/` are npm workspaces with TS/JS implementations and frequent `node:` built-ins.
- Provider, channel, memory, media, browser, local-CLI, diagnostics and model extensions assume module loading, package dependencies and often Node filesystem/network/process APIs.

Kinetic mapping: reuse portable JSON-schema/event/provider contracts; reimplement chosen providers in Kotlin. Do not attempt npm compatibility in Kinetic Core.

### Hermes skills and learning

Representation/discovery:

- `Eco_reference/hermes-agent/agent/skill_utils.py` scans hierarchical skill directories, excludes caches/virtualenvs, parses frontmatter, checks platform/requirements and creates compact description entries.
- `agent/skill_preprocessing.py`, `skill_commands.py`, `skill_bundles.py` and prompt-builder paths handle selection, commands/bundles and prompt injection.
- A typical `skills/.../SKILL.md` carries YAML frontmatter, trigger description, platform/tags/related skills, then procedural prose with references/scripts/templates.
- Skill descriptions are always-cheap routing metadata; body/supporting resources load only when selected. This progressive disclosure is portable.

Learning/self-improvement reality:

- `Eco_reference/hermes-agent/agent/learn_prompt.py::build_learn_prompt` converts `/learn <request>` into an ordinary live-agent turn. It tells the agent to gather named files/URLs/conversation evidence and invoke `skill_manage(action="create")` to author one `SKILL.md`.
- The file explicitly says there is “no separate distillation engine.” Learning inherits every power and failure mode of the current agent/toolset.
- `agent/learning_mutations.py` exposes user-initiated inspect/edit/delete; skill deletion archives, memory edits use atomic rewrite helpers, and cache invalidation refreshes selection.
- `agent/learning_graph.py` and usage/curator paths visualize/score the combined skill/memory journey; they do not prove learned workflows correct.

Kinetic mapping: retain user-triggered capture, progressive disclosure, versioning/archive and usage feedback. Replace free-form executable skill creation with proposal→compile→validate→simulate→human approve.

License warning: several Hermes productivity skills have restrictive subtree licenses and cannot be copied or transformed merely because the repository root is MIT. See `11_LICENSE_PROVENANCE_MATRIX.md`.

### AndyClaw skills and Android extensions

Evidence:

- `Eco_reference/AndyClaw/AndyClaw/src/main/java/org/ethereumphone/andyclaw/skills/SkillLoader.kt` loads `SKILL.md` directories with precedence `extra < bundled < managed < workspace`.
- `Eco_reference/AndyClaw/AndyClaw/src/main/java/org/ethereumphone/andyclaw/skills/SkillFrontmatter.kt` parses OpenClaw-like metadata, requirements/install specs, invocation policy and optional execution/tool specs.
- `Eco_reference/AndyClaw/AndyClaw/src/main/java/org/ethereumphone/andyclaw/skills/SkillManifest.kt` has description, tools and permissions; `ToolDefinition` carries JSON input schema, approval and required permissions.
- `Eco_reference/AndyClaw/AndyClaw/src/main/java/org/ethereumphone/andyclaw/extensions/ExtensionSkillAdapter.kt` converts an `ExtensionDescriptor` into standard skill tools and maps success, error, permission denied, timeout and approval-required results.
- `Eco_reference/AndyClaw/ExtensionExample/src/main/res/raw/extension_manifest.json` is a minimal function list with name, description and JSON input schema.

Kinetic mapping: Android package discovery and function-to-tool adaptation are strong concepts. The GPL-3.0 source must be clean-room reimplemented; identity, signatures, permissions, cancellation and AppFunctions need a stronger protocol than the example manifest.

### AnyClaw packages and pipelines

Descriptor:

- `Eco_reference/anyclaw/internal/pkg/manifest.go` defines package version/name/description/author/source/adapter and commands with typed-but-shallow args.
- `Manifest.InferAdapter` chooses `openapi`, `cli`, `script`, or `pipeline` based on command fields.
- HTTP auth is bearer/basic/API-key metadata keyed to a credential name; `internal/pkg/credentials.go` stores plaintext YAML with OS mode 0600—appropriate for a desktop prototype, not Android secrets.
- `internal/config/openapi.go::fromOpenAPI/convertOperation` ingests operations; `internal/adapter/openapi.go::OpenAPIAdapter.Execute` performs HTTP execution; `internal/frontend/mcp/server.go` maps installed commands into MCP tools.

Executable behavior:

- `adapter/cli.go` substitutes unescaped values into a command string and executes `sh -c`: Tier 3 and a command-injection pattern Kinetic must reject.
- `adapter/script.go` writes inline Python/Node to a temp file and starts the external runtime: Tier 3.
- Registry YAML such as `registry/packages/web-access/web-access.yaml` embeds Python, local-daemon and CDP assumptions: Tier 3.
- `adapter/pipeline.go` provides a promising dataflow vocabulary (`fetch`, `select`, `map`, `filter`, `limit`, `navigate`, `evaluate`), but raw fetch URLs create SSRF/data-exfiltration risk and `evaluate` is arbitrary browser JS. The implementation also iterates a Go map for each step (so multi-op ordering is not explicit), skips failed per-item fetches, and `pipelineNeedsBrowser` recognizes `evaluate` but not `navigate`.

Kinetic mapping: preserve only a typed, ordered, bounded transformation subset. Replace `fetch` with named network capabilities/endpoints; replace `navigate`/`evaluate` with explicit high-risk provider tools; require one opcode per step and typed failure policy.

### MobileRun skill/app-card assets

`Eco_reference/mobilerun-main/SKILL.md` explains how another agent invokes the MobileRun CLI/MCP surface; it is documentation, not an in-process runtime format. App cards and trajectories are more relevant: app-specific grounding hints can become signed/declarative perception packs, while actual actions remain registered Kinetic capabilities.

## JavaScript / desktop compatibility tiers

### Tier 1 — native/declarative Kinetic skill

Eligible semantic content:

- `SKILL.md` trigger, prerequisites, constraints and procedure where each effect maps to an existing Kinetic capability;
- JSON Schema input/output descriptions;
- AnyClaw-style `select`, bounded `map`, bounded `filter`, `limit`, templates and explicit dataflow;
- OpenAPI operations only after endpoint/auth/schema review and conversion to a named network capability;
- AndyClaw-style function manifests and Android AppFunctions metadata;
- MobileRun app-card selectors/grounding hints and trajectory assertions;
- prompt-only reasoning rubrics with no hidden side effect.

Required conversion: Markdown is evidence, not execution. A compiler/import review must identify parameters, preconditions, effects, failure branches, confirmations and outputs.

### Tier 2 — restricted JavaScript with Kinetic host APIs

Possible only for genuinely pure deterministic transforms:

- no `node:` built-ins, CommonJS/Node globals, npm resolution, native addon, dynamic import, `eval`, WebAssembly unless separately approved, raw sockets, process creation, reflection into Android, or ambient filesystem/network;
- no direct Android API; only unforgeable capability objects issued for the current step;
- CPU/time/memory/output quotas, cancellation, deterministic serialization and audit;
- signed/versioned source, static scan and policy review; runtime-downloaded interpreted code remains the developer's Play-policy responsibility.

The observed skill trees contain very few bundled JS files; a Tier 2 engine therefore has a small immediate compatibility payoff. It may be justified later for portable transforms, not for “OpenClaw compatibility.”

### Tier 3 — genuine Node/Linux/desktop semantics

Keep outside Kinetic Core:

- OpenClaw extension packages that use Node built-ins, npm dependencies, child processes, filesystem conventions, local daemons or external CLIs;
- Hermes skills that execute Python/shell/Node, install packages, call OS services or assume a desktop filesystem;
- AnyClaw `cli` and `script` adapters, browser CDP bridge and arbitrary `evaluate`;
- skills requiring Termux, PRoot, Docker, systemd, Homebrew/apt, Unix sockets or native Node addons;
- Python ML/automation packages and external toolchains.

Tier 3 can be exposed remotely through MCP or an explicitly installed advanced companion, but its results/authority still enter Kinetic through normal policy and audit boundaries.

## Proposed constrained Kinetic Skill IR (requirements, not final schema)

The IR should be data, versioned and validated before persistence. Minimum conceptual sections:

| Area | Required meaning |
|---|---|
| Identity/provenance | Stable skill ID/version, author/source URI, content digest/signature, license decision, imported-from format/version |
| Routing | Short trigger description, examples/counterexamples, required modalities/capabilities, availability predicate |
| Interface | Typed input/output schemas, sensitivity labels, defaults and localization keys |
| Execution | Ordered single-op steps: capability call, transform/select/map/filter/limit, bounded foreach, condition, approval checkpoint, emit/return |
| Authority | Explicit capability IDs/scopes; no ambient permissions. Risk/network/foreground/data policies resolved from host descriptors, not overridable by the skill |
| Control | Maximum steps/items/bytes/time/retries; cancellation and compensation/idempotency keys; typed failure branch for each effect |
| Context | Named input/output bindings and redaction rules; no implicit access to all conversation/memory/device data |
| Validation | Static schema validation, endpoint/provider resolution, cycle/bound checks, signature/license status, simulated tool fixtures and policy outcome |
| Lifecycle | Draft/approved/disabled/revoked states, compatibility range, migration, usage/evaluation stats and rollback |

Explicitly excluded from the Play-safe IR: shell command strings, inline Python/Node, arbitrary URLs, arbitrary browser JavaScript, bytecode/native payloads, reflection/class names, direct file paths outside URI grants, package installation and requests to weaken battery/security controls.

## Declarative Hermes-style learning pipeline

Yes, the useful learning behavior can be transformed:

1. **Capture:** user explicitly marks a successful trajectory or supplies sources. Redact secrets/third-party personal data before storage.
2. **Generalize:** a model proposes parameters, preconditions, invariants and failure branches from evidence; it cannot publish or execute the result.
3. **Compile:** translate only recognized operations into Skill IR; unresolved shell/script prose becomes an import error or a request to bind a named capability.
4. **Validate:** schema, bounds, capability availability, dataflow taint, network destinations, license/provenance and policy simulation.
5. **Test:** replay against mocks/recorded fixtures and optionally a user-visible dry run. Compare outputs/effects, not merely “model said success.”
6. **Approve:** show requested scopes, confirmations, data egress and exact generalized steps; user signs off on a version.
7. **Use and observe:** emit versioned trajectory/evaluation events. Failures create a revision proposal, never silent live self-modification.
8. **Curate:** pin, disable, archive, restore and roll back; periodically request revalidation after capability/schema/app version changes.

This preserves Hermes' strongest ideas—user-initiated distillation, progressive disclosure, archive/restore and usage feedback—while removing unrestricted executable learning.

## AppFunctions and MCP relationship

- A Kinetic skill orchestrates; it does not own every implementation.
- Android AppFunctions and Kinetic-native capabilities are local providers.
- MCP is a remote/external provider protocol.
- Each discovered function becomes a Kinetic tool descriptor, then policy adds local risk/scope/confirmation metadata.
- Skills bind stable logical capability IDs and declare compatibility predicates; provider selection is runtime routing, not embedded code.

## Recommendation

Implement Tier 1 first. Defer Tier 2 until real imported skills demonstrate a need that cannot be expressed as typed transformations. Make Tier 3 an explicit remote/advanced boundary. Do not market an embedded JS engine as Node/OpenClaw compatibility.
