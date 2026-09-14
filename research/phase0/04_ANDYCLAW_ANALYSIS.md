# AndyClaw implementation analysis

## Audit identity and conclusion

| Item | Exact local checkout |
|---|---|
| Repository | `Eco_reference/AndyClaw` |
| Remote | `https://github.com/hamzah105/AndyClaw` |
| Branch | `main` |
| Commit | `04a520177a5996777814b59ffeb28c1aeecc489a` |
| Latest local commit date | `2026-07-27T18:16:53+02:00` |
| Repository license | GNU GPL v3 (`Eco_reference/AndyClaw/LICENSE`) |
| Android baseline | app compile/target 36, min 35, arm64-v8a; Android library min 35 |
| Audit method | Read-only static implementation audit of the checked-out commit; no source changes |

Path convention: “app `.../`” expands to `Eco_reference/AndyClaw/app/src/main/java/org/ethereumphone/andyclaw/`; “library `.../`” expands to `Eco_reference/AndyClaw/AndyClaw/src/main/java/org/ethereumphone/andyclaw/`. AIDL, native, resource and test citations state their separate root explicitly. Each material finding names the relevant class/function where useful.

AndyClaw demonstrates the broadest native Android agent feature set in the checkout: iterative streamed tool use, tool search, subagents, Room sessions and hybrid memory, background heartbeat, APK extensions, downloaded skills, local GGUF inference and native Whisper. It also combines ordinary-app, unrestricted-execution and private/system capabilities in one application. It is not a suitable source foundation for a proprietary or permissively licensed Kinetic build: direct source reuse is GPL-constrained, and several critical execution/security paths are unsafe even independent of license.

The most important architectural discoveries are:

1. The actively integrated APK extension system is a hand-written raw-Binder/content-provider/broadcast/intent protocol. A second AIDL “external skill” system is unfinished and **wire-incompatible** with it despite an implementation comment claiming compatibility.
2. The agent streams model text to UI before scanning it for secret leakage, and logs complete prompts, tool calls, thinking and results to Logcat.
3. Streaming tools run in an independent `CoroutineScope(Dispatchers.IO)`, so cancelling the chat job does not structurally own/cancel the tool jobs.
4. `ParallelExecutionEngine` executes every ready tool concurrently, can invoke a runtime-approval tool twice, and contains a post-processor chaining bug that can discard safety sanitization.
5. Room preserves chat/session/memory records but there is no durable task/run/action checkpoint. Process death loses the live loop, approvals, ask-user state and in-flight tool ownership.
6. The heartbeat runs the full agent headlessly and auto-approves every approval request.

AndyClaw is consequently valuable as a **clean-room concept catalog and negative test corpus**, not as code to import.

## Build and runtime shape

`app/build.gradle.kts` defines one Android application with Compose, Room, security-crypto, AIDL, BeanShell, an NDK/CMake target, and project/binary dependencies on `:AndyClaw`, `llamatik.aar`, `tinfoil-bridge.aar`, Aurora gplayapi and ethOS SDKs. It limits native packaging to `arm64-v8a`. No Play/advanced/system product-flavor separation exists.

An optional `SYSTEM_APP=true` build hook rewrites the merged manifest to add `android:sharedUserId="android.uid.system"`. That is a private/system-image deployment mechanism, not an ordinary application strategy.

The code is split roughly as follows:

| Layer | Exact source | Responsibility | Finding |
|---|---|---|---|
| App composition | `Eco_reference/AndyClaw/app/src/main/java/org/ethereumphone/andyclaw/NodeApp.kt` — `NodeApp`/`onUserUnlocked` | Constructs providers, registries, memory, extensions and skills | Very broad service locator; mixes Play and system modes |
| Agent loop | `.../agent/AgentLoop.kt` — `AgentLoop.run` | Prompt assembly, memory, streamed LLM/tool iteration, compaction, subagents | Rich semantics; critical streaming/cancellation/logging defects |
| Tool scheduling | `.../agent/StreamingToolExecutor.kt` | Starts tool calls while LLM stream is still arriving | Latency-oriented but unstructured and risky |
| Generic executor | `AndyClaw/src/main/java/org/ethereumphone/andyclaw/ExecutionEngine/ParallelExecutionEngine.kt` | Preflight, parallel execution, runtime approval, post-processing, metrics | Reusable concept only; concrete correctness bugs |
| Tool registry | `.../skills/NativeSkillRegistry.kt`, `ToolSearchService.kt`, library `ToolDefinition.kt` | Built-ins, collision handling, tool discovery/prompt budget | Strong concepts; schema too weak for a security protocol |
| Sessions | library `sessions/*` plus app `ui/chat/ChatViewModel.kt` | Room chat history, title/model/tokens/context summary | Conversation durability, not task durability |
| Memory | library `memory/*` and app `BackgroundMemoryExtractor.kt` | FTS4/vector retrieval, chunks/tags, extraction | Best native memory reference among these two projects |
| Background | library `heartbeat/HeartbeatRunner.kt`; app `NodeForegroundService.kt`/`HeartbeatAgentRunner.kt` | Interval/quiet-hours heartbeat and headless agent execution | Not durable scheduling; unsafe autoapproval |
| Extensions | library `extensions/*`; app startup adapters | Installed-APK discovery and four invocation bridges | Active, but protocol/security lifecycle is not production quality |
| Gateway | library `gateway/*`/`protocol/*` | OpenClaw-like WebSocket node protocol prototype | No construction site outside its own declaration; apparently inactive |
| Local inference | app `llm/LocalLlmClient.kt`, `LlamaCpp.kt`, `ModelDownloadManager.kt`; `whisper/*` + C++ | GGUF chat/tool output and on-device ASR | Real inference; weak model integrity/provenance |
| System UI automation | `AgentDisplaySkill.kt`, `AgentDisplayAccessibilityService.kt`, private AIDL copies | ethOS `agentdisplay` virtual display/screenshot/input/tree | System/private capability, not ordinary-device autonomy |

## Exact agent loop

### Turn construction

`AgentLoop.run`:

1. scans inbound user text through `SafetyLayer`;
2. chooses enabled skills/tools using `SmartRouter` or per-conversation `ToolSearchService`;
3. injects retrieved memory only on the first turn or immediately after compaction;
4. builds a tier-aware system prompt with `PromptAssembler` and optional tool-catalog summary;
5. exposes meta-tools `spawn_subagent` and `ask_user` to non-local models;
6. iterates up to `MAX_ITERATIONS = 100`.

On each iteration it streams a model request with retry/reactive compaction. As tool-use blocks arrive, ordinary tools are immediately submitted to `StreamingToolExecutor`. Tool-search calls expand the current catalog, ask-user returns control after the turn, and subagents run isolated mini-loops with their own routing and a smaller token/iteration budget. Subagents cannot recursively spawn another subagent.

If an iteration contains no tool call, the loop completes. If it reaches 100 iterations, it also calls `onComplete` with accumulated text rather than returning a distinct limit-exceeded failure. `skillRegistry.cleanupAll` runs in `finally`.

Evidence: `Eco_reference/AndyClaw/app/src/main/java/org/ethereumphone/andyclaw/agent/AgentLoop.kt` — `run`, `buildSpawnSubagentTool`, `buildAskUserTool`, `runSubagent`; `.../skills/ToolSearchService.kt`; `.../skills/SmartRouter.kt`; library `skills/PromptAssembler.kt`.

### Tool search and registry

`NativeSkillRegistry` protects built-in tool names, tracks external collisions, namespaces colliding external functions, and increments a version so `ToolSearchService` can rebuild its index. `ToolSearchService` uses a BM25-style catalog and carries discovered tools forward within an `AgentLoop`/conversation instance. This is a valuable prompt-budget pattern.

`ToolDefinition` contains a name, description, JSON input schema, `requiresApproval`, Android `requiredPermissions` and `searchHint`. It lacks:

- typed output schema;
- stable namespaced capability/version ID;
- risk and data-sensitivity class;
- network destination/egress declaration;
- foreground/user-presence requirement;
- idempotency/side-effect/concurrency semantics;
- Play/runtime availability;
- audit/provenance and signing identity.

Those omissions are why the registry is useful for prompting but insufficient as Kinetic's execution authority.

## Executor forensics

### Streaming ownership defect

At every iteration, `AgentLoop` constructs:

`StreamingToolExecutor(..., scope = CoroutineScope(Dispatchers.IO))`

rather than using the current structured coroutine scope. `StreamingToolExecutor.addTool` starts `scope.async` jobs. `ChatViewModel.cancel` cancels only its `currentJob` and clears UI. `StreamingToolExecutor.reset` clears tracking; it does not cancel child jobs. A mutating Android/tool operation can therefore continue after the user thinks the turn was cancelled.

This is a critical Kinetic test case: all action attempts must be owned by a durable run and a structured execution scope; cancellation must transition the ledger and either cancel, wait for, or explicitly mark the side effect “outcome unknown.”

Evidence: `.../agent/AgentLoop.kt` at `StreamingToolExecutor` construction; `.../agent/StreamingToolExecutor.kt` — `addTool`/`awaitAll`/`reset`; `.../ui/chat/ChatViewModel.kt` — `cancel`.

### `ParallelExecutionEngine` defects

The library engine has a reasonable phase shape—preflight, execute, post-process, metrics—but its concrete semantics are unsafe:

| Defect | Exact code behavior | Consequence |
|---|---|---|
| Unconditional batch parallelism | `executeReadyTools` maps every ready call to `async` | Mutating or dependent tools race; `ToolDefinition` has no concurrency metadata |
| Runtime approval double execution | A tool is executed; if it returns `ToolExecResult.RequiresApproval`, `handleRequiresApproval` asks and then calls `executor.execute` again | Safe only if the first call is a guaranteed side-effect-free probe, an invariant the interface does not express/enforce |
| Post-processor chain bug | `runPostProcessing` initializes `processed`, then each processor receives the original `result` instead of prior `processed` | Factory order is safety sanitizer then truncator; the truncator can reconstruct output from raw content and discard a nonblocking sanitization |
| Generic error boundary | Executor catches regular exceptions around calls | Cancellation/side-effect semantics are not modeled as durable outcomes |

`ExecutionEngineFactory` adds rate-limit/parameter checks, Android permission requests, tool approval and enabled-skill checks, followed by safety sanitization and truncation. The preflight approval for a statically `requiresApproval` tool happens before execution; the double-call problem is specifically the separate runtime `RequiresApproval` result path.

`ParallelExecutionEngineTest.kt` covers batching, callbacks, blocking, approval and single post-processors. It does not test composition of two transforming post-processors, and it codifies the runtime two-invocation approval behavior rather than detecting it.

Evidence: `Eco_reference/AndyClaw/AndyClaw/src/main/java/org/ethereumphone/andyclaw/ExecutionEngine/ParallelExecutionEngine.kt` — `executeBatch`, `executeReadyTools`, `handleRequiresApproval`, `runPostProcessing`; app `agent/ExecutionEngineFactory.kt`.

## Output safety, observability and data leakage

`AgentLoop.StreamingCallback.onToken` immediately appends text and invokes `callbacks.onToken`. Only after `client.streamMessage` completes does the loop call `safety.scanLlmResponse`, and even a blocked result is merely logged. Text has already reached UI and may later be persisted.

The local path is worse for hidden reasoning: `LocalLlmClient.generateStream` forwards every raw delta, then strips `<think>...</think>` only when building the completed response.

The loop also writes the complete system prompt (in chunks), user message, available tool list, thinking blocks, tool inputs and tool results under the `AGENTDISPLAYDEBUGKEY` Logcat tag. Agent-display UI content is also extensively logged. This can expose secrets, memory, messages, wallet/tool parameters and third-party content to debug/log collection.

Required Kinetic correction:

`LLM bytes -> incremental redaction/secret scanner -> bounded UI stream -> persistence`

Tool/result logs must be structured, opt-in, sensitivity-aware and redacted before emission. Never log chain-of-thought or full prompts.

Evidence: `.../agent/AgentLoop.kt` — `onToken`, post-stream `scanLlmResponse` and `AGENTDISPLAYDEBUGKEY` calls; `.../llm/LocalLlmClient.kt` — `streamMessage`/`stripThinking`; `.../safety/SafetyLayer.kt`.

## Sessions, process death and cancellation

`ChatViewModel.sendMessage` creates/loads a Room session, persists the user message immediately, reconstructs model history from stored messages/summary, creates a new `AgentLoop` and streams the turn. Tool and assistant messages are persisted from separate `viewModelScope.launch` calls, which does not impose one transactional message/action ordering.

`Session` persists title/model/timestamps/token totals, an `isAborted` flag, and context-window metrics. There is no task ID, run ID, iteration cursor, tool-attempt row, input wait, approval decision, lease or resume policy. `ChatViewModel.cancel` does not update `Session.isAborted`.

| State | Survives process death? | Evidence/consequence |
|---|---|---|
| Session metadata/transcript | Yes | library `sessions/db/SessionDatabase.kt`/`SessionDao.kt` and `SessionManager` |
| Context summaries/token counters | Yes after asynchronous write completes | `ChatViewModel.onComplete` and session repository |
| Long-term memories/chunks/tags/embeddings | Yes | library `memory/db/MemoryDatabase.kt`/`MemoryDao.kt` |
| User message at turn start | Yes | synchronously awaited `sessionManager.addMessage` before loop |
| Partial assistant stream | Only after `flushStreamingText` | A kill between flush points loses visible-but-uncommitted deltas |
| Tool-result ordering | Not guaranteed | each callback starts an independent `viewModelScope.launch` DB write |
| Agent iteration/tool ownership | No | new loop per send; no run ledger |
| Approval continuation | No | `approvalContinuation: CancellableContinuation<Boolean>` is RAM |
| Pending ask-user overlay | No | `pendingAskUserRequest`/StateFlow are RAM |
| In-flight streaming tools | Not safely owned | independent IO scope may outlive UI cancellation |
| Deterministic resume | No | no unfinished-run startup query/reconciliation |

AndyClaw provides **conversation restoration**, not task restoration.

## Memory

The library memory design is stronger than OpenDroid's:

- `MemoryRepository` stores entries, chunks, tags, metadata and FTS4 rows in Room.
- `MemorySearchManager` combines keyword and cosine-vector results, defaulting to 0.3 keyword and 0.7 vector, and supports all-tags filtering.
- `MemoryManager` eagerly embeds new chunks when a provider exists, supports reindex and degrades to keyword-only search.
- `NodeApp` installs `OpenAiEmbeddingProvider` when wallet-backed ethOS auth or an OpenRouter API key is available; otherwise keyword retrieval remains functional.

`BackgroundMemoryExtractor` coalesces requests, skips local models, avoids duplicate extraction when `memory_store` was already invoked, and asks a cloud LLM for memories after enough new messages. Its `lastProcessedMessageCount` and pending history are instance RAM. Recreating the process can repeat extraction because no durable extraction watermark/idempotency key is stored.

For Kinetic, clean-room the hybrid retrieval/chunk/tag design, but persist extraction provenance and watermarks, define deletion/export semantics, redact before remote embeddings, and bind retrieval to task relevance and token budgets.

Evidence: `Eco_reference/AndyClaw/AndyClaw/src/main/java/org/ethereumphone/andyclaw/memory/MemoryManager.kt`; `memory/search/MemorySearchManager.kt` — `DEFAULT_VECTOR_WEIGHT`/`DEFAULT_KEYWORD_WEIGHT`; `memory/db/MemoryDao.kt`; app `agent/BackgroundMemoryExtractor.kt`; `memory/OpenAiEmbeddingProvider.kt`.

## Heartbeat and background execution

`HeartbeatRunner` is an in-process coroutine delay loop. It reads heartbeat instructions, honors quiet hours and suppresses identical text for 24 hours using `lastHeartbeatText`/`lastHeartbeatSentAt` fields in RAM. It has no durable next-run record, missed-run/catch-up policy, WorkManager/AlarmManager ownership, attempt ledger or persisted backoff.

`NodeForegroundService` hosts it in a `dataSync` foreground service, returns `START_STICKY`, and `BootReceiver` restarts it after boot on non-ethOS devices. Sticky recreation restarts scheduling from preferences; it does not restore a missed or in-flight task.

`HeartbeatAgentRunner` runs the full `AgentLoop` headlessly. Its `onApprovalNeeded` logs “Auto-approving” and returns true for every tool. Only missing Android runtime permissions may stop a call. Combined with shell, BeanShell, custom tools and Termux skills, that is an unacceptable autonomous execution boundary.

Kinetic should model proactive triggers as signed/persisted schedule records that enqueue a limited-capability task with an explicit headless allowlist. A foreground service may execute eligible work, but it must not define durability or imply universal approval.

Evidence: library `heartbeat/HeartbeatRunner.kt` — `start`/`executeHeartbeat` and RAM dedupe fields; app `NodeForegroundService.kt` — `onStartCommand`; `services/BootReceiver.kt`; `agent/HeartbeatAgentRunner.kt` — `onApprovalNeeded`.

## Gateway and protocol

The library includes a fairly complete-looking OpenClaw-style prototype:

- `GATEWAY_PROTOCOL_VERSION = 3`;
- WebSocket request/response/event frames;
- UUID correlation and a pending-deferred map with timeouts;
- reconnect support;
- signed device challenge and stored device token;
- `node.event` and incoming `node.invoke.request`/result handling;
- mDNS discovery for `_openclaw-gw._tcp.` and optional wide-area DNS.

However, repository-wide constructor search finds `GatewaySession(` only at its declaration. No app composition or service constructs it. Findings here must be treated as inactive library code, not proof the Android app depends on a Gateway.

`GatewayTls` supports an expected SHA-256 fingerprint and optional TOFU, otherwise default CA validation. It always returns `HostnameVerifier { _, _ -> true }`, disabling hostname matching. The `GatewayTlsParams.required` field is not used to enforce a transport requirement. Even as prototype code, this must not be reused.

Evidence: `Eco_reference/AndyClaw/AndyClaw/src/main/java/org/ethereumphone/andyclaw/gateway/GatewayProtocol.kt`; `GatewaySession.kt` — `GatewayConnection.request`/`handleEvent`/`handleInvokeRequest`; `GatewayDiscovery.kt`; `GatewayTls.kt` — `buildGatewayTlsConfig`.

## Extension architecture: what is actually active

There are **two different extension systems**.

### System A: integrated APK extensions

`NodeApp.onUserUnlocked` invokes `ExtensionEngine.discoverAndRegister` and converts discovered descriptors to `ExtensionSkillAdapter` instances registered in `NativeSkillRegistry`. This is the active path.

#### Manifest discovery

`ApkExtensionScanner.scanInstalledExtensions` enumerates installed packages and requires application metadata:

- `org.ethereumphone.andyclaw.EXTENSION = true`;
- optional `EXTENSION_ID` and `EXTENSION_NAME`;
- optional raw-resource `EXTENSION_MANIFEST` containing function JSON.

It detects bridge types in priority order:

1. service with action `org.ethereumphone.andyclaw.EXTENSION_SERVICE`;
2. provider whose authority ends in `.andyclaw.extension`;
3. receiver for `org.ethereumphone.andyclaw.EXTENSION_BROADCAST`;
4. activity for `org.ethereumphone.andyclaw.EXTENSION_ACTION`.

The raw manifest supplies `ExtensionFunction` records: name, description, JSON input schema, `requiresApproval` and host Android `requiredPermissions`. There is no output schema or protocol version.

Evidence: `.../extensions/discovery/ApkExtensionScanner.kt` — metadata/action constants, `scanInstalledExtensions`, `detectBridgeTypes`, `loadManifestFunctions`; `.../extensions/Extension.kt`.

#### Invocation protocol

`ExtensionEngine.execute` checks extension/function availability, security, host permissions and approval, then delegates to `ApkExtensionExecutor`.

| Bridge | Exact wire behavior | Lifecycle/problem |
|---|---|---|
| Bound service | Bind explicit package/action; raw Binder descriptor `org.ethereumphone.andyclaw.IExtension`; transaction 2 writes interface token + function string + params JSON; reads exception + result string | Bind and transact have timeouts, but no version negotiation, request ID, typed result, death recipient, remote cancellation or idempotency. Connections are retained until `unbindAll`; a later connection for the same package can overwrite the map entry before the old binding is released |
| Content provider | Constructs authority exactly as `packageName + ".andyclaw.extension"` and calls `contentResolver.call(uri, "execute", null, extras)` | Scanner accepts any authority merely ending with the suffix, so discovery and invocation rules differ. Call is synchronous with no coroutine timeout/cancellation envelope |
| Broadcast | Sends explicit-package request with function/params/response action; dynamically listens on `packageName.andyclaw.EXTENSION_RESPONSE` | No request nonce/correlation/authentication; concurrent requests share one action. The response receiver uses `RECEIVER_NOT_EXPORTED`, which appears incompatible with receiving a response from the external extension process on modern Android |
| Explicit activity | Starts the extension activity with function/params extras | Fire-and-forget; host reports success when launch succeeds, not when the capability completes |

`TRANSACTION_GET_MANIFEST = 1` exists but is not invoked in the active executor; discovery trusts the APK raw resource instead. Results are opaque strings wrapped as `ExtensionResult.Success`/`Error`.

Evidence: `.../extensions/execution/ApkExtensionExecutor.kt` — `INTERFACE_DESCRIPTOR`, `TRANSACTION_EXECUTE`, `bindToService`, `transactExecute`, `executeViaContentProvider`, `executeViaBroadcast`, `executeViaIntent`.

#### Registry and lifecycle

`ExtensionRegistry` indexes extensions by ID and functions globally by unqualified name; later registrations replace a prior function owner. `discoverAndRegister` updates what is found but does not reconcile/remove packages that disappeared. `NodeApp` registers new `ext:` adapters after discovery but, unlike its ClawHub/AI/custom-tool sync routines, does not first remove stale extension adapters. Runtime rescans can therefore leave stale callable entries or ambiguous ownership.

`ApkExtensionExecutor.unbindAll` exists, but it is not a per-call cleanup/failure reconciliation protocol. There is no package-change observer, stable descriptor hash, health check or active-call draining.

Evidence: `.../extensions/ExtensionRegistry.kt` — `register`/`unregister`/`functionIndex`; `ExtensionEngine.kt` — `discoverAndRegister`; app `NodeApp.kt` — `onUserUnlocked` and adapter sync methods.

#### Permission and trust model

`ExtensionSecurityManager` can validate that an APK is signed, compare a certificate hash when `ExtensionDescriptor.signingCertHash` is supplied, require UID isolation and check function permissions. But:

- `ApkExtensionScanner` does not populate `signingCertHash`, so default signature validation generally proves only that the installed APK has some signer, not that it has an approved signer.
- The permission loop calls host `context.checkSelfPermission`. It checks whether AndyClaw has the declared permissions, not whether the extension has them or whether the caller is authorized at the remote component.
- trusted IDs/descriptor trust and developer mode bypass checks.
- the example extension service is exported without a signature-level bind permission or caller-UID check.
- no descriptor signature/version/output contract is authenticated across invocation.

This is a warning/approval boundary, not a secure extension sandbox.

Evidence: `.../extensions/security/ExtensionSecurityManager.kt` — `checkExtension`, `checkFunctionPermissions`, `validateSignature`, `validateUidIsolation`; `ExtensionSecurityPolicy.kt`; `ExtensionExample/src/main/AndroidManifest.xml`.

### System B: unfinished AIDL external skills

`app/src/main/aidl/org/ethereumphone/andyclaw/IAndyClawSkill.aidl` declares:

- `String getManifestJson()`;
- `String execute(String tool, String paramsJson, String tier)`.

`ExternalSkillDiscovery` searches service metadata `org.ethereumphone.andyclaw.SKILL`. `ExternalSkillBinder.toAdapter` advertises an empty tool list and `execute` always returns “External skill execution not yet implemented.”

This path is not merely inactive; it is not wire-compatible with System A:

| Item | Active raw Binder | AIDL external skill |
|---|---|---|
| Interface descriptor | `org.ethereumphone.andyclaw.IExtension` | Generated `org.ethereumphone.andyclaw.IAndyClawSkill` |
| Manifest | Transaction 1 constant, unused; functions loaded from raw resource | `getManifestJson()` |
| Execute request | function + params JSON (two strings) | tool + params JSON + tier (three strings) |
| Integration | `ExtensionEngine` -> adapters -> registry | Adapter has no tools and execution is a stub |

Therefore the comment in `ApkExtensionExecutor.transactExecute` saying the wire is compatible with `IAndyClawSkill` is false at this commit. An extension implementing the AIDL interface will reject the active raw interface token or parse the wrong parcel.

Evidence: the AIDL file; `.../skills/external/ExternalSkillDiscovery.kt` — `META_KEY`; `ExternalSkillBinder.kt` — `toAdapter`/`execute`; library `ApkExtensionExecutor.kt`.

## Kinetic capability protocol and AppFunctions bridge

Do not adopt either AndyClaw IPC path unchanged. The useful conceptual mapping is:

`Kinetic Tool <-> versioned Kinetic Capability Descriptor <-> Android AppFunction or explicit IPC/MCP adapter`

A Kinetic descriptor should minimally carry:

- namespaced ID and semantic version;
- deterministic input **and output** schemas;
- provider package/component and signed descriptor hash;
- allowed signing certificate(s)/trust origin;
- user-visible description and availability predicate;
- Android/runtime permissions for both host and provider;
- data sensitivity, network egress and destination declarations;
- user-presence/foreground/approval policy;
- side-effect, idempotency and concurrency class;
- deadline/cancellation support and maximum result size;
- distribution tier: Play-safe, restricted, advanced or system;
- audit event shape and provenance.

For AppFunctions:

1. discover only app-declared, currently available functions through Android's supported API;
2. translate their typed metadata into a Kinetic descriptor;
3. run Kinetic policy and parameter-bound approval before invocation;
4. invoke through an AppFunctions adapter and normalize a typed result;
5. store attempt ID, deadline, completion and provenance in the durable task ledger.

For a Binder fallback, require a signature-level service permission, explicit component, caller-UID verification, certificate pin/allowlist, version negotiation, request ID, idempotency key, deadline, one-shot callback/result envelope, Binder death recipient and cancellation. Do not expose broad provider/broadcast/activity alternatives as equivalent completion semantics.

`PROTOCOL_INTEROP_POSSIBLE` applies only if compatibility with an independently specified existing ecosystem is genuinely required. The current checkout is not a sufficient stable protocol specification because its two implementations disagree. Any interoperability implementation should be independently written and legally reviewed.

## Skills, unrestricted execution and system-only capabilities

`OsCapabilities` reports `Tier.OPEN` or `Tier.PRIVILEGED` and maps capabilities. The mapping says `CODE_EXECUTE` requires privileged access, but registration/enforcement is inconsistent:

- `NodeApp` registers `CodeExecutionSkill` unconditionally.
- `CodeExecutionSkill` runs BeanShell in-process with application `Context`, `PackageManager`, `ContentResolver`, files directory and a `ToolBridge` that can call other tools. Its tool only requests approval; `execute` does not reject `Tier.OPEN`.
- `ShellSkill` is also registered and runs `ProcessBuilder("sh", "-c", command)` in app storage. Root is tier-gated, but ordinary shell is not.
- YOLO mode and headless heartbeat can satisfy approval automatically.
- LLM-created custom executable tools are loaded on startup.
- ClawHub executable skills can be copied into Termux, install missing packages, run setup scripts and invoke arbitrary entrypoints through `com.termux.RUN_COMMAND`.

`SkillThreatAnalyzer` is a useful heuristic scanner for prompt injection, exfiltration, binaries, shell/download/install patterns and server malware flags. `ClawHubManager.downloadAndAssess` nevertheless extracts files into the managed skills directory before confirmation, and `confirmInstall` does not hard-block `CRITICAL`—a user/autoapproval can proceed. Static warnings cannot substitute for isolation.

For Kinetic Play core, downloadable skills should be declarative plans/prompt assets over a fixed signed tool catalog. BeanShell, arbitrary shell, dynamic executable tools and Termux execution belong in `ADVANCED_SIDELOAD_ONLY` and must never share the headless approval profile.

Evidence: `.../skills/tier/OsCapabilities.kt`; `.../skills/builtin/CodeExecutionSkill.kt`; `ShellSkill.kt`; `.../extensions/clawhub/SkillThreatAnalyzer.kt`/`ClawHubManager.kt`; `.../skills/termux/ClawHubTermuxSkillAdapter.kt`/`TermuxSkillSync.kt`.

### Agent Display is not an ordinary Android automation solution

`AgentDisplaySkill` exposes tools only for the privileged tier and obtains a private service named `agentdisplay` through `android.os.ServiceManager`. The copied `android.os.IAgentDisplayService`/`IAgentAccessibilityProxy` AIDL surfaces virtual display creation, launch, screenshot, input injection and accessibility-tree access. `AgentDisplayAccessibilityService` bridges across displays and can fall back to framework/private input injection.

This is meaningful for an ethOS/system image, but it does not give a Play-distributed app arbitrary UI autonomy. Classify it `SYSTEM_PRIVILEGED_ONLY` and keep it behind an optional executor provider.

Evidence: `.../skills/builtin/AgentDisplaySkill.kt`; `.../services/AgentDisplayAccessibilityService.kt`; `app/src/main/aidl/android/os/IAgentDisplayService.aidl` and `IAgentAccessibilityProxy.aidl`; `res/xml/agent_display_a11y_config.xml`.

## Local models, voice and NDK

### Local LLM

`LocalLlmClient` formats prompts as Qwen ChatML or Gemma syntax based on the loaded filename, asks for Hermes-style `<tool_call>` JSON, parses it with regex/JSON fallback, and calls `LlamaCpp`. The comment says small models are limited to three tools, but `maxToolCount` is actually eight. Tool calling is text parsing after generation, not constrained structured decoding.

`LlamaCpp` wraps the bundled Llamatik bridge. Load/generate are synchronized, while streaming generation delegates directly to the static bridge without the same lock. Concurrency/model-lifecycle behavior needs instrumentation.

`GgufRegistry` safely imports SAF file descriptors through a temporary file and rename, but records only filename/size/basic runtime settings. `ModelDownloadManager` downloads Qwen 2.5 1.5B Instruct Q2_K (documented about 753 MB) to a temporary file and renames it. “Downloaded” means only file exists and length > 0; there is no expected size, SHA-256, signature, GGUF validation or model license metadata.

### Whisper

`WhisperTranscriber` copies bundled `ggml-base.en-q5_1.bin` to app storage, warms a process-resident native model and serializes transcription with a mutex. `WhisperBridgeNative` loads `whisper_jni`; `whisper_jni.cpp` validates/decodes 16-kHz WAV and calls the copied whisper/ggml code. `CMakeLists.txt` applies arm64/dot-product optimizations.

This is a legitimate NDK/JNI use: optimized local audio inference. Planner, task state, IPC, policy, skills, memory and Android tools do **not** require NDK.

### Bundled artifact provenance gap

| Artifact | Size at audited checkout | Evidence/provenance finding |
|---|---:|---|
| `app/src/main/assets/ggml-base.en-q5_1.bin` | 59,721,011 bytes | No adjacent license/model card/checksum found |
| `llamatik/llamatik.aar` | 43,092,553 bytes | Binary AAR; no adjacent license/notice found |
| `tinfoil-bridge/tinfoil-bridge.aar` | 94,311,942 bytes | Binary AAR; no adjacent license/notice found |
| copied `app/src/main/cpp/whisper/*` | source tree | `CMakeLists.txt` says whisper.cpp/FUTO origin, but most copied files lack file license headers and no bundled upstream license file was found |

These gaps must be resolved before reuse or redistribution. Repository GPL does not establish the redistribution terms for a model or opaque third-party binary.

Evidence: `.../llm/LocalLlmClient.kt`, `LlamaCpp.kt`, `GgufRegistry.kt`, `ModelDownloadManager.kt`; `.../whisper/WhisperTranscriber.kt`/`WhisperBridgeNative.kt`; `app/src/main/cpp/CMakeLists.txt`/`whisper_jni.cpp`.

## Manifest and Android/Play capability audit

The single app manifest declares ordinary and privileged/system permissions together: contacts, SMS, camera, location, FGS data-sync, wake lock, ignore-battery-optimization request, Wi-Fi/Bluetooth privileged controls, tethering/phone-state, call log/calls, calendar, usage stats, all-files access, package install/delete/cache/force-stop, audio recording, reboot/device power, notification policy, exact alarms, boot, `QUERY_ALL_PACKAGES` and Termux `RUN_COMMAND`.

It also exports `HeartbeatBindingService` and `LauncherBindingService` without a manifest permission, and exports `HeartbeatSettingsProvider` and `AiNameProvider` without read/write permissions. Even if their payloads look limited, external input/output surfaces should be permissioned and fuzz-tested.

| Capability | Exact implementation | Category | Kinetic Play dependency |
|---|---|---|---|
| General Accessibility/UI automation | AgentDisplay accessibility + private system service | `SYSTEM_PRIVILEGED_ONLY` in this implementation | No |
| MediaProjection | No implementation found | `NOT_IMPLEMENTED` | Explicit user-mediated only if added |
| NotificationListenerService | `AndyClawNotificationListener` | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Optional |
| VoiceInteractionService | Not implemented | `NOT_IMPLEMENTED` | No claim |
| Foreground service | `NodeForegroundService` dataSync heartbeat | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Only bounded visible execution |
| WorkManager | No agent/task scheduling implementation found | `NOT_IMPLEMENTED` | Add for eligible durable work |
| AlarmManager/exact alarms | reminder/cron skills and exact-alarm permission | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Feature-gated |
| WakeLock/battery exemption | manifest and service behavior | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Not a durability mechanism |
| Contacts/calendar/SMS/phone/call log | built-in skills + broad permissions | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Least-privilege optional adapters |
| Location/camera/microphone | built-in skills/Whisper | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Foreground/explicit purpose |
| Clipboard | `ClipboardSkill` | `PLAY_SAFE_OR_LIKELY` only with foreground/privacy restrictions | Optional |
| Installed-app discovery | `AppsSkill`/package skills + `QUERY_ALL_PACKAGES` | `ADVANCED_SIDELOAD_ONLY` as currently broad | Use scoped queries |
| App-private/SAF files | `FileSystemSkill`/GGUF import | `PLAY_SAFE_OR_LIKELY` | Yes |
| `MANAGE_EXTERNAL_STORAGE` | `StorageSkill` and manifest | `ADVANCED_SIDELOAD_ONLY` | No |
| `REQUEST_INSTALL_PACKAGES`/privileged install | Aurora/package skills | `ADVANCED_SIDELOAD_ONLY` or `SYSTEM_PRIVILEGED_ONLY` | No |
| Overlay windows | No overlay permission found | `NOT_IMPLEMENTED` | No |
| VPN/device admin | No dedicated implementation found | `NOT_IMPLEMENTED` | No |
| Shizuku/root | root shell path on privileged tier, no Shizuku integration found | `SYSTEM_PRIVILEGED_ONLY` | No |
| Dynamic dex/JAR/native | Native libs packaged at build; no safe dynamic-native protocol | `ADVANCED/SYSTEM` if later downloadable | Reject dynamic code in Play core |
| Interpreted code | BeanShell, shell and Termux (not JavaScript) | `ADVANCED_SIDELOAD_ONLY` | No |
| Downloadable models | Qwen downloader/GGUF import | `PLAY_SAFE_OR_LIKELY` after integrity/license controls | Yes |
| Downloadable declarative skills | SKILL.md can be instruction-only | `PLAY_SAFE_OR_LIKELY` if signed/validated/non-executable | Yes |
| Downloadable executable skills | ClawHub Termux/custom/AI code | `ADVANCED_SIDELOAD_ONLY` | No |
| APK extensions | broad package scan and custom IPC | `PLAY_RESTRICTED_REQUIRES_REVIEW`; current trust model unsuitable | Prefer AppFunctions/explicit integrations |
| Connectivity/package/power/AgentDisplay | hidden/private/system APIs | `SYSTEM_PRIVILEGED_ONLY` | No |

Kinetic should use one kernel and separate executor providers/source sets: `play`, `advanced` and optionally OEM/system. Tool availability must be compiled/configured from the provider; a Play build must not merely hide advanced tools in UI while retaining their permissions and code paths.

## Tests and benchmark value

The checkout contains 14 test source files, including two boilerplate example-module tests. Materially examined:

- library `ExecutionEngine/ParallelExecutionEngineTest.kt`;
- `app/src/test/java/org/ethereumphone/andyclaw/llm/LocalLlmClientTest.kt`;
- instrumentation `llm/LocalLlmInferenceTest.kt`;
- `skills/ToolSearchServiceTest.kt`;
- `skills/builtin/ToolBridgeTest.kt`;
- routing/classifier tests.

Useful assets are the generic execution fixtures, tool-search ranking tests, local prompt/tool parser cases and optional real-device local inference/performance test. Missing high-value tests are:

1. active APK discovery across service/provider/broadcast/intent;
2. raw Binder versus `IAndyClawSkill` interoperability (which would expose the mismatch);
3. certificate pin/trust, caller UID and exported-component authorization;
4. package uninstall/rescan and stale adapter cleanup;
5. concurrent extension calls, Binder death, timeout and cancellation;
6. safety scan before the first UI token;
7. two transforming post-processors in sequence;
8. user cancel while a streaming mutating tool is running;
9. process death after tool side effect/before transcript commit;
10. heartbeat recovery, missed-run semantics and headless allowlist;
11. permission denial/no-UI in heartbeat;
12. model checksum/corruption/license metadata;
13. FGS/battery/runtime impact;
14. private AgentDisplay absence on ordinary Android.

## GPL and third-party provenance boundary

The root license is GPL-3.0. Sampled first-party Kotlin files usually have no separate file header, so the repository license is the governing project signal. Several Aurora-derived files, including `skills/builtin/aurorastore/PlayStoreHttpClient.kt`, explicitly state `SPDX-License-Identifier: GPL-3.0-or-later` and upstream copyright. Copied native sources and opaque AAR/model artifacts need separate provenance resolution as noted above.

**Any direct reuse/adaptation of AndyClaw implementation source in a distributed Kinetic derivative must be treated as GPL-compatible/obligation-bearing unless counsel establishes otherwise.** “Open source” is not permission to mix it into a differently licensed codebase.

Required classifications:

| Useful component/concept | Exact source evidence | Classification | Kinetic decision |
|---|---|---|---|
| `AgentLoop` implementation | app `agent/AgentLoop.kt` | `REUSE_ONLY_IF_GPL_COMPATIBLE` | Do not copy; independently design from behavioral requirements |
| Streamed tool scheduler | `StreamingToolExecutor.kt` | `REUSE_ONLY_IF_GPL_COMPATIBLE` | Do not reuse; clean-room with structured ownership |
| Generic execution engine | library `ExecutionEngine/*` | `REUSE_ONLY_IF_GPL_COMPATIBLE` | Do not reuse source; use defects as conformance tests |
| Preflight/postflight phase concept | `ParallelExecutionEngine`/`ExecutionEngineFactory` | `CLEAN_ROOM_REIMPLEMENT_CONCEPT` | Build typed policy/action pipeline from specification |
| Tool search/discovery budgeting | `ToolSearchService.kt`/`NativeSkillRegistry.kt` | `CLEAN_ROOM_REIMPLEMENT_CONCEPT` | Reimplement BM25/catalog/session discovery semantics |
| Tool/capability descriptor | library `skills/ToolDefinition.kt` | `CLEAN_ROOM_REIMPLEMENT_CONCEPT` | Design a richer versioned Kinetic schema |
| Hybrid memory retrieval | library `memory/*` | `CLEAN_ROOM_REIMPLEMENT_CONCEPT` | Reimplement Room FTS/vector/chunk/tag semantics |
| Sessions/compaction | library `sessions/*`, app `ContextCompactor.kt` | `CLEAN_ROOM_REIMPLEMENT_CONCEPT` | Reimplement conversation persistence; add task ledger |
| Heartbeat semantics | library `heartbeat/*` | `CLEAN_ROOM_REIMPLEMENT_CONCEPT` | Reimplement as durable, least-capability scheduling |
| APK extension wire | `extensions/discovery`/`execution` | `PROTOCOL_INTEROP_POSSIBLE` | Only independent implementation if real ecosystem compatibility is required; current wire is inconsistent |
| AIDL external-skill wire | `IAndyClawSkill.aidl`/external binder | `IGNORE` at this commit | Stubbed and incompatible with active path |
| Extension security implementation | `extensions/security/*` | `REUSE_ONLY_IF_GPL_COMPATIBLE` | Do not copy; requirements inform clean-room threat model |
| APK discovery/adapter concept | `ExtensionEngine.kt`/`ExtensionSkillAdapter.kt` | `CLEAN_ROOM_REIMPLEMENT_CONCEPT` | Prefer AppFunctions; explicit signed IPC fallback |
| ClawHub threat categories | `SkillThreatAnalyzer.kt` | `CLEAN_ROOM_REIMPLEMENT_CONCEPT` | Reimplement policy checks; do not rely on heuristic approval |
| Shell/BeanShell/Termux/custom executable skills | app `skills/builtin`/`termux`/`customtools` | `IGNORE` for Play core | Advanced-only architecture, separately permissioned |
| Gateway protocol implementation | library `gateway/*` | `REUSE_ONLY_IF_GPL_COMPATIBLE` | Do not copy; inactive and TLS-flawed |
| Gateway interoperability surface | `GatewayProtocol.kt`/`OpenClawProtocolConstants.kt` | `PROTOCOL_INTEROP_POSSIBLE` | Independently implement only against a stable external spec |
| Local LLM prompt/parser implementation | `LocalLlmClient.kt` | `REUSE_ONLY_IF_GPL_COMPATIBLE` | Clean-room provider adapter; prefer structured generation |
| Whisper JNI/source copy | `app/src/main/cpp/*` | `IGNORE` until provenance resolved | Use a clearly licensed upstream dependency/version |
| System shared-UID/AgentDisplay paths | build hook, private AIDL and skill | `IGNORE` for Play core | Optional OEM/system provider only |

## Highest-value Kinetic conclusions

1. Clean-room the concepts; do not import AndyClaw source into a non-GPL Kinetic codebase.
2. Build output safety before streaming, structured action ownership, a durable attempt ledger and parameter-bound policy as kernel invariants.
3. Treat AppFunctions as the first Android-native app capability path. Use narrow signed Binder IPC only where AppFunctions cannot meet a real use case.
4. Do not claim compatibility with AndyClaw's extensions until a protocol is independently specified and the raw/AIDL contradiction is resolved.
5. Keep shell, BeanShell, Termux, executable downloaded skills, all-files/package install and headless autoapproval out of Play core.
6. Reimplement the strongest concepts: tier-aware registry, prompt-budgeted tool search, hybrid memory, session compaction and proactive schedule semantics.
7. Preserve local inference behind replaceable Kotlin interfaces; NDK is justified for optimized inference, not the agent architecture.
8. Turn the observed defects into tests: pre-display leakage, postprocessor composition, double execution on approval, cancel-orphaned tools, process-death ambiguity, extension caller authentication and model corruption.

## Inspection accounting and evidence ledger

Repository-wide enumeration found **263** Kotlin/Java/AIDL/C/C++/header/XML implementation/configuration files under `app/src/main`, **87** under `AndyClaw/src/main`, and **14** test files. The deep audit materially inspected **118 implementation files**, plus **6 tests** and **10 build/license/binary/config artifacts**. “Materially inspected” means a full-file read or targeted symbol/body inspection used in a finding above; simple name enumeration is excluded.

| Material implementation area | Count | Representative exact evidence |
|---|---:|---|
| Agent, compaction and execution engine | 21 | app `agent/AgentLoop.kt`, `StreamingToolExecutor.kt`, `ExecutionEngineFactory.kt`, budget/compaction/memory prompt files; library `ExecutionEngine/*` |
| Extensions, discovery, execution, security and ClawHub | 22 | library `extensions/Extension.kt`, `ExtensionEngine.kt`, `ExtensionRegistry.kt`, `ExtensionSkillAdapter.kt`, `discovery/ApkExtensionScanner.kt`, `execution/ApkExtensionExecutor.kt`, `security/*`, `clawhub/*`; app AIDL/external binders and example service |
| Skills, capabilities, commands and safety | 26 | library skill contracts/registry; app `NativeSkillRegistry.kt`, `ToolSearchService.kt`, `SmartRouter.kt`, `OsCapabilities.kt`, code/shell/files/package/AgentDisplay/Termux/custom-tool paths and `safety/*` |
| Gateway, heartbeat, protocol and services | 17 | library `gateway/*`, `heartbeat/*`, `protocol/*`; app `NodeForegroundService.kt`, `HeartbeatAgentRunner.kt`, boot/binding/provider/accessibility services |
| Memory and sessions | 15 | library `memory/*` and `sessions/*`; app `BackgroundMemoryExtractor.kt`/`OpenAiEmbeddingProvider.kt` |
| LLM, model, Whisper and native bridge | 12 | app `llm/LocalLlmClient.kt`, `LlamaCpp.kt`, `GgufRegistry.kt`, `ModelDownloadManager.kt`, provider adapters, `whisper/*`, C++ JNI/core samples |
| App lifecycle and chat/settings UI state | 5 | `NodeApp.kt`, `NodeRuntime.kt`, `ui/chat/ChatViewModel.kt` and settings/runtime owners |
| **Implementation subtotal** | **118** | |
| Material tests | 6 | Execution engine, local parser, local instrumentation, tool search, tool bridge and routing |
| Build/license/binary/config | 10 | `LICENSE`, `settings.gradle.kts`, root/app/library build files, app manifest, accessibility config and three bundled artifact inspections |
| **All materially inspected artifacts** | **134** | |

All paths above are relative to `E:\Projects`. No file under `Eco_reference/AndyClaw` was modified.
