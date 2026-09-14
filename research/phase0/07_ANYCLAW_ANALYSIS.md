# AnyClaw forensic architecture and reuse audit

## Audit identity and bottom line

| Item | Exact local evidence | Finding |
|---|---|---|
| Checkout | `Eco_reference/anyclaw` | Intact, clean Git checkout for the files inspected. |
| Remote | repository metadata | `origin = https://github.com/hamzah105/anyclaw` |
| Branch / revision | repository metadata | `main` at `eb7052708f993552e70043a1bff8b9a243148c4f` |
| Latest local commit | repository metadata | `2026-03-29T21:25:18-07:00`, `feat: clawhub repo, skill packages, structure-inferred capabilities (SKILL.md detection)` |
| Primary implementation | `go.mod`; 46 tracked `*.go` files | Go 1.25 module, Cobra CLI, YAML, `mcp-go`, Gorilla WebSocket; it also ships a Manifest V3 Chrome extension and dynamically runs shell, Python, Node, and browser JavaScript. |
| Platform | `main.go::main`; `cmd/root.go::Execute`; `extension/manifest.json` | Desktop CLI plus Chrome extension. This is not an Android project and contains no Android manifest, Gradle, lifecycle, WorkManager, Room, Keystore, or on-device model integration. |
| Recommendation | implementation evidence below | **ADAPT_ALGORITHM** for the descriptor idea and a strictly bounded subset of its pipeline semantics. Treat MCP as a protocol reference. Do not port the unrestricted adapters or desktop daemon into Kinetic Core. |

AnyClaw's genuinely useful architectural idea is not its universal execution runtime. It is the normalization of heterogeneous integrations into a small package/command descriptor and the exposure of those commands through a common adapter and MCP facade. The current descriptor does not distinguish declarative data flow from arbitrary executable code, and the runtime deliberately crosses shell, Python, Node, downloaded source, browser debugger, cookie, and network boundaries. Kinetic should split those categories before installation, make risk and provenance machine-readable, and only admit a typed declarative IR into a Play-distributed build.

## Integrity, license, and test posture

- `LICENSE` is MIT, copyright `2025 FastClaw AI`. The repository-level license permits reuse with preservation of the copyright and permission notice.
- A repository-wide scan of the 46 Go files found no SPDX, copyright, or alternate file-level license header. No nested `LICENSE`, `NOTICE`, or `COPYING` file is present. This is not proof that downloaded packages inherit the repository license: `internal/registry/registry.go::Package.Source`, `internal/registry/repo.go::DefaultRepos`, and `cmd/install.go` deliberately fetch third-party content from other repositories and ClawHub.
- `go.mod` is the dependency inventory: `mcp-go v0.45.0`, Cobra, `x/term`, YAML, Gorilla WebSocket, and indirect JSON-schema/support libraries. Kinetic must independently inventory licenses for any dependency it actually chooses; MIT at the repository root does not cover all transitive code or remotely installed packages.
- There are **zero tracked `*_test.go` files**. No unit, integration, conformance, security, or fixture-driven test harness exists in this checkout. The example YAML files are demonstrations, not assertions. Any semantics adapted to Kinetic therefore need a fresh contract suite.
- Git status was clean when audited. The audit did not execute adapters, fetch packages, start the daemon, or mutate the reference.

Reuse classification:

| Candidate | Classification | License/provenance consequence |
|---|---|---|
| Descriptor vocabulary and pure pipeline concept | `ADAPT` / preferably clean Kotlin expression of the idea | Repository is MIT, but a new typed Android contract should be authored rather than copying Go runtime code. Preserve attribution if source is reused. |
| Minimal OpenAPI ingestion behavior | `REFERENCE_ONLY` | The parser is too lossy for a production contract; use a maintained parser or implement against an explicitly supported OpenAPI subset. |
| MCP tool facade | `PORT_SEMANTICS` | Protocol interoperability is useful; the Go library/runtime is not reusable in Kotlin as-is. |
| CLI, script, OpenCLI TypeScript, bb-site, browser evaluate | `REJECT` for Play Kinetic Core; `REFERENCE_ONLY` for a separately trusted desktop/research host | Remote package licensing is not established, and behavior is unrestricted executable code. |
| Registry installer | `REJECT` as a security/provenance design | It has no signature, digest, publisher identity, declared permissions, or immutable version constraint. |

## Two overlapping internal models

The checkout contains two related but non-identical contract stacks.

1. The legacy OpenAPI path uses `internal/config/config.go::Config`, `Backend`, `Auth`, `Skill`, `Field`, `SkillBackend`, and `SkillResponse`. `internal/core/router.go::Router` builds an in-memory name-to-`Skill` map and delegates `Execute(ctx, skillName, params)` to `internal/backend/backend.go::Backend`. `internal/backend/http/client.go::Client` implements that backend.
2. Installed packages use `internal/pkg/manifest.go::Manifest`, `Command`, `Arg`, `HTTPConfig`, `Auth`, and `ScriptConfig`. `cmd/run.go` / `cmd/root.go` load a manifest, call `Manifest.InferAdapter`, construct `internal/adapter/adapter.go::Adapter`, and execute the selected `Command`.

These stacks duplicate HTTP/auth/result ideas: `backend.Response` and `adapter.Result` both hold textual `Content` plus optional `map[string]any Data`, while `config.Auth` and `pkg.Auth` repeat the same fields. That is useful archaeology: Kinetic should define one canonical capability descriptor and one canonical invocation result rather than allowing an import format to become a second runtime model.

There is no agent loop, planner, LLM provider, prompt construction, conversation/session store, memory, embedding retrieval, scheduler, heartbeat, task checkpoint, streaming result protocol, approval manager, or telemetry pipeline. `context.Context` reaches adapter calls and child processes/HTTP requests, which supplies cooperative cancellation in some paths, but it is not durable task state.

## Package descriptor and command abstraction

`internal/pkg/manifest.go::Manifest` defines `anyclaw` format version, name, package version, description, author, optional adapter, source marker, and commands. `Command` defines name/description, argument map, shell `run` template, optional HTTP config, optional script config, a pipeline represented as `[]map[string]any`, and display columns. `Arg` records only `string`/`int`/`bool`, required, string default, description, and short flag.

Important implementation consequences:

- `Manifest.LoadManifestData` validates only YAML decoding and non-empty package name. It does not validate a supported format version, unique command names, argument types, mutually exclusive execution bodies, endpoint policy, path traversal in file references, or a schema for pipeline steps.
- `Manifest.InferAdapter` returns an explicit package adapter or scans commands and returns the first command shape it recognizes: HTTP, shell run, script, then pipeline; default is CLI. Adapter selection is package-wide in `cmd/run.go` and `internal/frontend/mcp/server.go::NewServerFromManifests`. A heterogeneous manifest can therefore be routed through the wrong adapter.
- `Command.Pipeline` is untyped YAML data. Misspellings and invalid operands are discovered only during execution in `PipelineAdapter.Execute`.
- `Arg.Type` is descriptive more often than enforcing. CLI parsing performs some value conversion, but MCP registration in `internal/frontend/mcp/server.go::registerPackageCommand` advertises **every argument through `mcp.WithString`**, ignoring `int` and `bool`.
- `HTTPConfig` has a raw base URL, method, path, and auth. It has no allowed host set, redirect policy, timeout, response-size limit, content-type allowlist, sensitivity, or network-purpose declaration.
- The descriptor has no output schema, side-effect classification, Android permission, Kinetic scope, confirmation policy, foreground requirement, audit rule, availability predicate, idempotency, retry policy, timeout, cancellation semantics, or compensation action.

`internal/adapter/adapter.go::Adapter.Execute(context.Context, *pkg.Command, map[string]any, packageDir)` is a compact polymorphic seam. Its `Result` preserves human-readable text and optionally a JSON object, but not arrays/scalars as structured top-level results, binary/media parts, progress, citations, typed errors, retry hints, or a durable invocation ID.

## OpenAPI ingestion and HTTP execution

`internal/config/loader.go::Load` recognizes YAML/JSON and converts OpenAPI into the legacy internal model through `internal/config/openapi.go::fromOpenAPI`. The parser intentionally models a small OpenAPI 3 subset:

- first server only;
- first component security scheme only;
- operations from `paths`, using `operationId` or a method/path-derived name;
- query/path/header parameters as flat fields;
- first request-body content entry, with one level of local `#/components/schemas/...` resolution;
- primitive type, description, required, and default.

The parser does not model operation-level/global security application, responses/output schemas, multiple servers, server variables, headers vs query behavior as distinct runtime locations, cookie parameters, arrays/items, enums, numeric/string constraints, formats, nullable, examples, discriminators, `allOf`/`oneOf`/`anyOf`, recursive references, callbacks, links, or content negotiation. `convertOperation` flattens request-body properties into the same map as parameters, so name/location collisions are possible. Map iteration also means choosing the "first" server content or security scheme is not a stable preference policy where a Go map is involved.

Two HTTP executors then exist:

- `internal/backend/http/client.go::Client.Execute` services legacy `Skill` calls. It substitutes `{name}` path placeholders, serializes remaining parameters as JSON for POST/PUT/PATCH or query parameters otherwise, reads the entire response, and optionally extracts a dot path via `extractContent`.
- `internal/adapter/openapi.go::OpenAPIAdapter.Execute` performs equivalent work for package `Command.HTTP`, returning pretty JSON when it decodes as an object.

Both use an unconfigured `http.Client`/`http.DefaultClient`, read the full body without a size bound, return response bodies in errors, and have no retry/backoff or redirect/host policy. Path values are string-substituted without segment escaping. `PipelineAdapter.doRequest` is even weaker: it does not reject HTTP error status at all before attempting to decode the body.

Authentication is also inconsistent. `internal/adapter/openapi.go::applyAuth` tries `~/.anyclaw/credentials.yaml` by `Auth.TokenEnv`, then an environment variable, and emits bearer/basic/custom API-key headers. `internal/backend/http/client.go::Client.applyAuth` uses only environment variables. Both data structures contain `Prefix`, but neither implementation applies it; API-key users must include any prefix in the stored secret. This is evidence that a descriptor field without conformance tests is not a contract.

Kinetic mapping: import OpenAPI into a reviewed intermediate descriptor, retain parameter locations and output schemas, resolve credentials by opaque Keystore-backed handle, enforce HTTPS/host/redirect/size/time policies, and require a policy decision before invocation. Do not feed arbitrary remote OpenAPI directly into an executable registry.

## Declarative pipeline: useful seed, unsafe current boundary

`internal/adapter/pipeline.go::PipelineAdapter.Execute` threads one untyped `data` value through sequential one-key maps. The non-browser operations are:

| Operation | Exact implementation | Semantics and limitation |
|---|---|---|
| `fetch` | `pipelineFetch`, `httpGet`, `httpGetWithParams`, `httpPost` | GET/POST to a rendered URL. There is no endpoint allowlist, credential binding, body-size/status policy, or bounded fan-out. |
| `select` | `pipelineSelect` | Dot traversal through JSON objects only; no arrays or typed missing-value policy. |
| `map` | `pipelineMap` | Maps arrays into object rows with string-rendered templates; most values lose native type. |
| `filter` | `pipelineFilter`, `evalFilterExpr` | Only truthiness, `&&`, and `!` over item fields; unrecognized text may become truthy rather than a validation error. |
| `limit` | `pipelineLimit` | Integer or a heuristic that extracts numbers from an expression and chooses the smallest; invalid expressions can silently return unbounded input. Negative values are not explicitly rejected before slicing. |
| `navigate` / `evaluate` | `pipelineNeedsBrowser`, `executeWithBridge` | Browser navigation plus arbitrary JavaScript evaluation; this is privileged executable behavior, not a declarative transform. |

`renderTemplate` supports `${{ args.X }}`, `${{ item.X }}`, `${{ index + N }}`, a JSON string filter, and a narrow ternary. It is an ad hoc expression evaluator rather than a parsed, typed language. `fetchPerItem` performs serial network fan-out and silently `continue`s on each failed item, erasing partial-failure evidence. Go map iteration over each step permits more than one operation in a step while leaving their execution order unspecified.

The repository example `examples/web.yaml` demonstrates the attractive part: fetch a collection, bound it, map IDs, fetch details, filter, project output, and bound again. A constrained Kinetic Skill IR can preserve that composition but must not preserve the representation or trust model.

Proposed Kinetic import boundary (interface-level research only):

```text
SkillPackageDescriptor
  formatVersion, packageId, version, signer, contentDigest, provenance
  declaredCapabilities, supportedApp/API versions, risk summary

CapabilityCommand
  commandId, typed inputSchema, typed outputSchema
  requiredAndroidPermissions, requiredKineticScopes
  riskLevel, confirmationPolicy, dataSensitivity, networkPolicy
  foregroundRequirement, timeout/cancellation/idempotency/audit policy
  program: ConstrainedSkillProgram

ConstrainedSkillProgram (sealed and versioned)
  HttpRequest(endpointRef, typed bindings, responseBound)
  Select(typed path)
  Map(typed expression)
  Filter(typed predicate)
  Limit(validated non-negative bound)
  InvokeCapability(capabilityId, typed arguments)  // policy is rechecked
```

The validator must impose operation count, fan-out, payload, time, memory, and output bounds; forbid dynamic hosts unless explicitly authorized; preserve per-item failures; and checkpoint step/result metadata. Shell, filesystem, Python, Node, browser `evaluate`, dynamic class loading, and arbitrary network fetch must not be expressible in the Play-safe IR.

## Executable adapters and compatibility tiers

AnyClaw currently places declarative and executable packages behind the same `Adapter` interface. They require different trust treatment.

### CLI adapter

`internal/adapter/cli.go::CLIAdapter.Execute` substitutes unescaped parameter strings directly into `Command.Run`, removes unresolved placeholders, normalizes whitespace, and invokes `sh -c`. An attacker-controlled argument can become shell syntax. It also supports interactive stdin/stdout when attached to a terminal. This is genuine shell/desktop semantics and is **TIER 3**, `ADVANCED_SIDELOAD_ONLY` at best, and not acceptable in Kinetic Core.

### Script adapter

`internal/adapter/script.go::ScriptAdapter.Execute` writes inline code to a temporary `.py`/`.js` file or joins a declared file to the package directory, then launches the named runtime with the full inherited process environment and JSON stdin. There is no sandbox, import restriction, network/filesystem policy, environment filtering, resource quota, signature requirement, or output-size bound. Context cancellation can terminate the direct child but does not create a security boundary. Python/Node packages are **TIER 3**.

### OpenCLI TypeScript adapter

`internal/adapter/opencli_ts.go::OpenCLITSAdapter.Execute` requires a real Node binary. `stripTypeScript` uses regular expressions to remove a few syntax forms; it is not a TypeScript parser and cannot reliably preserve language semantics. `buildTSShim` exposes Node's global environment plus a `page` bridge and executes downloaded code from stdin. Embedded V8 alone would not reproduce Node, package, process, module, or host behavior. This path is **TIER 3**, not evidence that restricted JavaScript is sufficient.

### Browser and bb-site adapters

`internal/adapter/bbsite.go::BbSiteAdapter.Execute` and `internal/site/runner.go::Runner.Run` interpolate arguments as JSON, wrap package-supplied code as an async function, and send it to `BridgeEvaluate`. `internal/adapter/pipeline.go::executeWithBridge` does the same for `evaluate` steps. These adapters can execute arbitrary JavaScript in a privileged browser tab and are executable, regardless of whether their metadata is stored in YAML.

Compatibility classification for Kinetic:

| Tier | AnyClaw examples | Kinetic decision |
|---|---|---|
| **TIER 1: native/declarative** | Reviewed OpenAPI commands; bounded `fetch`/`select`/`map`/`filter`/`limit` programs after conversion and validation | Candidate for a signed, versioned Kinetic Skill IR. Every operation remains mediated by policy. |
| **TIER 2: restricted JavaScript with Kinetic host APIs** | None is actually implemented here. A future expression-only transform could qualify if it has no Node globals, dynamic imports, raw sockets, filesystem, reflection, or direct Android access. | Optional research slot, not required for the IR. Prefer a typed expression language because the current useful transforms are small. |
| **TIER 3: genuine desktop semantics** | `CLIAdapter`, `ScriptAdapter`, `OpenCLITSAdapter`, bb-site/site adapter, browser `evaluate`, system CLI wrappers, ClawHub packages that invoke external tools | Exclude from Kinetic Core. A separately installed/paired advanced host could expose selected capabilities through authenticated RPC/MCP. |

## Browser/daemon trust boundary

The bridge is powerful by design:

- `extension/manifest.json` requests Chrome `debugger`, `tabs`, `cookies`, `activeTab`, and `alarms`, plus `<all_urls>` host access.
- `extension/background.js::handleCommand` accepts `navigate`, debugger-backed `Runtime.evaluate`, tab management, cookie reads, screenshots, and session/window information. `cmdExec` evaluates arbitrary source; `cmdCookies` returns cookie names and values.
- `internal/adapter/daemon.go::Daemon.Start` listens on loopback port 19825 with `/ws`, `/status`, and `/command`. The WebSocket upgrader's `CheckOrigin` always returns true.
- Neither `handleExtensionWS` nor `handleCommand` checks an authentication token, client identity, Origin, or the `X-OpenCLI` header. `internal/adapter/browser.go::bridgeGet/bridgePost` sets that header, but the server never treats it as authorization.
- A local web page is normally constrained from arbitrary loopback reads, but it can still attempt requests, and any local process can call the daemon. A process connecting to `/ws` can replace `extConn`; a process POSTing `/command` can drive whichever extension is connected. Loopback is reachability, not authentication.
- `EnsureDaemon` forks the AnyClaw executable and records only a PID file; it supplies no authenticated rendezvous, per-client capability token, OS peer check, encrypted channel, or durable supervised-service model. The daemon retains pending requests in memory and loses them on process death.

Kinetic must not reproduce this as an unauthenticated localhost bridge. If an advanced companion exists, pair it with user-visible approval, mutual authentication, scoped/rotating credentials, origin/client binding, replay protection, command allowlists, explicit browser profile isolation, and an audit trail. For a Play build, arbitrary debugger/evaluate/cookie access should be treated as **PLAY_RESTRICTED_REQUIRES_REVIEW** or **ADVANCED_SIDELOAD_ONLY**, not as ordinary skill execution.

## Registry and installation mechanics

`internal/registry/registry.go::FetchIndex` fetches a mutable raw-GitHub YAML index (or `ANYCLAW_REGISTRY_URL`) into `Package{Name, Description, Type, Source, Tags}`. `registry/index.yaml` points to mutable remote YAML. The model has no publisher key, digest, immutable revision, package license, minimum runtime, permission declaration, risk, review status, revocation, or transparency record.

`internal/registry/repo.go::DefaultRepos` adds OpenCLI, bb-sites, and ClawHub. `cmd/install.go` accepts:

- registry name;
- arbitrary HTTP(S) YAML/OpenAPI URL;
- GitHub repository/subdirectory;
- local directory/file;
- existing system CLI;
- ClawHub fallback through `npx clawhub@latest install`;
- OpenCLI TypeScript and bb-sites JavaScript sources.

`cmd/install.go::fetchURL`, `installFromGitHub`, `installFromClawhub`, `installBBSitesPackage`, and `installFromRepo` download and transform content without verifying a content digest or signature. The ClawHub path explicitly executes a floating `npx ...@latest`. `internal/pkg/store.go::Store.Install` persists files and YAML below `~/.anyclaw/packages`; package name/file names are not a substitute for canonical path validation and publisher trust.

The root `SKILL.md` is an agent-facing instruction manual telling an agent to search, install, and run CLI commands. `internal/gen/skillmd.go::WriteSkillMD`/`WriteManifestSkillMD` generate Markdown and curl/CLI instructions. These are prompt documents, not a safe executable IR; their prose and remote descriptions are also prompt-injection surfaces.

Kinetic registry requirements inferred from these gaps: immutable version + digest; signing identity; human-readable and machine-readable license; reviewed operation graph; requested scopes/permissions/network hosts; compatibility and min/max API/app versions; install-time and invocation-time approval; revocation; reproducible artifact; and a quarantine/review state. Downloadable declarative skills can be `PLAY_SAFE_OR_LIKELY` only if they remain data interpreted by a bounded host. Downloadable native/JAR/dex/Node/Python code is a different distribution and policy class.

## MCP exposure

`cmd/mcp.go` selects legacy single-config or installed-package mode. `internal/frontend/mcp/server.go::Server` uses `mcp-go` and `server.NewStdioServer`:

- `NewServer` registers legacy `config.Skill` entries.
- `NewServerFromManifests` picks one adapter per manifest and registers each command.
- `registerPackageCommand` names tools `<package>_<command>` and executes the adapter with the caller's `context.Context`.
- successful results become text-only `mcp.NewToolResultText`; structured `Result.Data`, arrays, binary/media, progress, and provenance are discarded at the MCP boundary.
- all inputs are advertised as strings; there is no output schema, MCP annotation for read-only/destructive/idempotent behavior, risk, permission, confirmation, or per-client authorization.

This is a useful interoperability proof, not a security boundary. Kinetic should map its canonical capability descriptor to MCP and keep policy enforcement below every frontend, so an MCP caller cannot bypass the same approval, scope, and network checks used by an in-app agent. It also needs cancellation-to-operation propagation, durable invocation IDs, bounded/typed content parts, and client/session audit identity.

## Secrets and authentication

`internal/pkg/credentials.go::Credentials` stores a map of package key to secret in plaintext YAML at `~/.anyclaw/credentials.yaml`, written with mode `0600`. File permissions reduce multi-user disclosure on POSIX but provide no encryption at rest, device binding, biometric/user-auth option, rotation metadata, per-command scope, redaction lifecycle, or protection from any code already running as the user. On Windows the POSIX mode is not equivalent to an Android Keystore guarantee.

For Kinetic, descriptors should reference a `credentialHandle`, never embed secret material or expose it to the LLM/tool result. A credential broker should use Android Keystore-backed encryption, apply the secret only inside the network/capability host, constrain destination and header placement, redact logs, support expiry/rotation/revocation, and optionally require recent user authentication. `Auth.Prefix` being declared but unused is a specific conformance test Kinetic should retain as a negative regression case.

## Security/policy comparison

The reference descriptor does not provide fields comparable to the proposed Kinetic policy metadata. The closest constructs are only `Arg` type/required, `HTTPConfig.Auth`, `SiteAdapter.ReadOnly`, and `context.Context`. `SiteAdapter.ReadOnly` is parsed in `internal/site/adapter.go` but is not enforced by `Runner.Run` or the browser daemon; it is descriptive metadata, not policy.

| Needed Kinetic property | AnyClaw evidence | Gap |
|---|---|---|
| `toolId`, description, input schema | `Manifest.Name`, `Command.Name/Description/Args` | IDs are un-namespaced mutable strings; schema is shallow and MCP flattens types. |
| output schema | `Result.Content/Data` | No declared contract; structured data is object-only and lost over MCP. |
| permissions/scopes | none | Adapters obtain ambient process/browser authority. |
| risk/confirmation | `SiteAdapter.ReadOnly` only | Not propagated or enforced; no approval architecture. |
| sensitivity/network policy | raw `BaseURL`, pipeline-rendered URL | No destination, redirect, secret-flow, or result classification. |
| foreground/availability | runtime `LookPath`, extension connectivity checks | No preflight descriptor, Android lifecycle, or user-visible execution rule. |
| audit/idempotency | none | No invocation ledger, task checkpoint, replay key, or compensation semantics. |

Required security invariant for Kinetic: the registry, planner, Skill IR interpreter, MCP facade, and AppFunction bridge may describe or request an operation, but only the capability host plus policy engine can authorize and execute it. Descriptor claims such as `readOnly` must be validated against the operation graph and still enforced at runtime.

## Android and Google Play translation

There is no Android code to reuse directly. The relevant capability classification is therefore about translating concepts:

| AnyClaw behavior | Capability class | Kinetic disposition |
|---|---|---|
| Signed, bounded, data-only pipeline calling reviewed HTTPS APIs | `PLAY_SAFE_OR_LIKELY` subject to data/privacy/network disclosure | Viable declarative skill profile with policy mediation and no dynamic code. |
| MCP client/server to a user-configured remote endpoint | `PLAY_SAFE_OR_LIKELY` or `PLAY_RESTRICTED_REQUIRES_REVIEW` depending on exposed powers | Authenticate, scope, display endpoint trust, and re-authorize each local effect. |
| Downloaded Markdown skill instructions | `UNKNOWN_REQUIRES_POLICY_RESEARCH` | Treat as untrusted content; do not grant new capability from prose. |
| Arbitrary browser automation/evaluate/cookie collection | `PLAY_RESTRICTED_REQUIRES_REVIEW` / likely `ADVANCED_SIDELOAD_ONLY` for general autonomous use | Not a Kinetic Play dependency. Prefer app-owned APIs, AppFunctions, intents, or user-mediated flows. |
| Shell, external CLI, Python, Node, dynamic executable packages | `ADVANCED_SIDELOAD_ONLY` | Exclude from Android-native Core; optionally pair with a separate trusted companion. |
| Dynamic dex/JAR/native code analog | `ADVANCED_SIDELOAD_ONLY` and high distribution risk | Not justified by the declarative pipeline use case. |

AnyClaw provides no evidence for AccessibilityService, MediaProjection, foreground service, WorkManager, AlarmManager, notification listener, VoiceInteractionService, camera/microphone, contacts/calendar/location, SAF, or package visibility. It also provides no local LLM: there is no LiteRT/TFLite, MediaPipe, llama.cpp, ONNX, or native inference module.

### AppFunctions research bridge

AnyClaw's command normalization maps conceptually to Android AppFunctions, but the current execution details should not cross the bridge:

```text
AnyClaw-like imported command
    -> validated Kinetic Capability Descriptor
    -> canonical typed Kinetic Tool
    -> policy/approval/credential broker
    -> Android AppFunction adapter (when an app exposes a compatible function)
```

The import must retain typed inputs/outputs and app/function identity, replace raw `base_url`/`run`/`script` with a bound capability ID, declare Android permission and Kinetic scope requirements, include availability/app-version checks, and translate cancellation and typed errors. AppFunction discovery does not imply authorization; the user and target app remain security principals. Conversely, a Kinetic capability can be exported to AppFunctions only with an intentionally limited public contract, not by automatically exporting every installed skill or MCP tool.

## Persistence, recovery, observability, and tests

Persisted state is limited to installed manifests/files (`internal/pkg/store.go::Store`), repository config, plaintext credentials, site adapters, and a daemon PID. There is no persistent invocation/session/task state. `Daemon.pending` is an in-memory map; process death loses calls. Pipeline state is a local variable; a killed process cannot resume. No event log, trace span, structured audit record, planner trajectory, or redaction mechanism is implemented.

Cancellation reaches HTTP requests and `exec.CommandContext`, but several waits use direct `time.Sleep`; browser commands use a separate 30-second daemon timeout and do not accept the invocation context. HTTP clients have inconsistent/no explicit timeouts. There is no retry policy except connection polling; `fetchPerItem` silently skips failures rather than surfacing a retryable partial result.

Because this checkout has zero Go tests, Kinetic should build new tests around the reusable idea:

- descriptor version/type/uniqueness validation and canonical hashing;
- package signature/license/provenance/revocation;
- OpenAPI subset accept/reject fixtures and lossless parameter locations;
- expression parser/type checking and operation/fan-out/payload bounds;
- URL/redirect/DNS/network allowlist enforcement and secret destination binding;
- partial failure, cancellation, timeout, retry, idempotency, and process-death resume;
- permission denial and approval revocation between steps;
- MCP schema fidelity and inability to bypass policy;
- prompt-injection resistance for descriptions/results;
- negative fixtures for shell metacharacters, path traversal, arbitrary browser eval, credential leakage, ignored auth prefix, and unauthenticated loopback callers.

## Synthesis-ready conclusions

1. AnyClaw demonstrates a valuable **normalization pattern**: package -> command -> adapter -> frontend. Kinetic should retain one canonical capability model and treat import formats only as translators.
2. The pure subset of fetch/select/map/filter/limit is a credible seed for a constrained Skill IR. The current `[]map[string]any` format, heuristic expressions, browser steps, and silent partial failures are not production contracts.
3. Declarative storage does not make behavior declarative. Inline Python, Node, shell templates, browser JavaScript, and remote CLIs are unrestricted executable code.
4. Adapter selection is package-wide and inferred from the first matching command shape. Kinetic must validate one explicit execution kind per command/program.
5. OpenAPI ingestion is intentionally lossy. Preserve typed locations, schemas, security application, outputs, and explicit supported-subset diagnostics.
6. MCP exposure is straightforward but currently string/text-only and policy-blind. Place policy beneath all frontends.
7. The registry is a discovery list, not a software supply-chain design. Signed immutable artifacts, license/provenance, permission manifests, review, and revocation are mandatory.
8. The unauthenticated loopback daemon plus `<all_urls>` debugger/cookie extension is the largest security discovery in this repository.
9. A restricted JS runtime is not needed to capture the strongest AnyClaw idea. Its actual TypeScript path requires Node and arbitrary host behavior, so it is Tier 3.
10. **Recommendation: `ADAPT_ALGORITHM`** for the descriptor and bounded pipeline concepts; `PORT_SEMANTICS` for MCP; `REJECT` unrestricted adapters from Play Kinetic Core.

## Material implementation ledger

Material artifacts read or section-read: **45**. Repository-wide inventories, Git metadata queries, and targeted searches are additional and not counted.

1. Root/build/docs (4): `LICENSE`; `go.mod`; `README.md`; `SKILL.md`.
2. Runtime contracts/config/package store (10): `internal/pkg/manifest.go`; `credentials.go`; `store.go`; `internal/config/config.go`; `loader.go`; `openapi.go`; `internal/core/types.go`; `router.go`; `internal/backend/backend.go`; `internal/backend/http/client.go`.
3. Adapters (9): `internal/adapter/adapter.go`; `openapi.go`; `cli.go`; `script.go`; `pipeline.go`; `browser.go`; `daemon.go`; `opencli_ts.go`; `bbsite.go`.
4. Frontends, registry, generation, and browser site bridge (8): `internal/frontend/mcp/server.go`; `internal/registry/registry.go`; `repo.go`; `internal/gen/skillmd.go`; `internal/site/adapter.go`; `runner.go`; `extension/manifest.json`; `extension/background.js`.
5. Entry/commands (6): `main.go`; `cmd/root.go`; `run.go`; `install.go`; `mcp.go`; `auth.go`.
6. Real descriptors/examples (8): `registry/index.yaml`; `registry/packages/web-access/web-access.yaml`; `translator/openapi.yaml`; `query-domains/query-domains.yaml`; `examples/web.yaml`; `openapi.yaml`; `cli.yaml`; `script.yaml`.

No source from this repository was copied into Kinetic. This report records behavior and proposes independently defined contracts only.
