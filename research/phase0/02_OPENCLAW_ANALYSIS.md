# OpenClaw forensic architecture and reuse audit

## Scope, exact checkout, and method

This report covers only the local repository at `E:\Projects\Eco_reference\openclaw`. It is a read-only Phase 0 audit; no OpenClaw source, dependency, lockfile, generated file, or build output was changed, and no Kinetic implementation was created. Unless an absolute path is shown, every source reference below is an exact checkout-relative path under that root; class, interface, and function names identify the implementation symbols inspected.

| Fact | Exact local evidence |
|---|---|
| Remote | `origin https://github.com/hamzah105/openclaw` (fetch/push), from local Git metadata. This is a fork URL; `package.json` and `AGENTS.md` identify the canonical project as `openclaw/openclaw`. |
| Branch | `main` |
| Commit | `ebb301c32d9e2e1ec61baa783564eb38e4296895` |
| Commit date | `2026-08-01T13:01:40-07:00` |
| Commit subject | `fix(moonshot): forward abort signal through Kimi web search provider (#105125)` |
| Worktree | Clean for the audited paths; Git showed no local changes. |
| Repository scale | Git tracks 30,372 paths; `rg --files` exposes 29,900 paths under its ignore/discovery rules. Within the latter view, dominant extensions include 24,318 `.ts`, 1,080 `.swift`, 1,080 `.md`, 578 `.json`, 441 `.mjs`, and 429 `.kt`. The inventory's 24,325 TypeScript count uses the broader filesystem-extension scan, not a conflicting revision. |
| Primary build | pnpm 11 monorepo, TypeScript/Node ESM, `package.json` `build` -> `scripts/build-all.mjs`; root engine is Node `>=22.22.3 <23` OR `>=24.15.0 <25` OR `>=25.9.0`. |
| Android build | Gradle/AGP/Kotlin, modules `:app`, `:benchmark`, `:wear`, and `:wear-shared`; `apps/android/settings.gradle.kts`. |
| License | MIT, Copyright 2026 OpenClaw Foundation; `LICENSE`, `package.json`. Pi/pi-mono-derived portions have a separately preserved MIT notice in `THIRD_PARTY_NOTICES.md`. |

The mandatory audit roots contain 6,137 paths: `apps/android` 653; `packages/agent-core` 34; `llm-core` 9; `gateway-client` 35; `gateway-protocol` 152; `plugin-sdk` 27; `plugin-package-contract` 3; `memory-host-sdk` 122; `media-core` 17; `speech-core` 20; `tool-call-repair` 10; `src/agents` 2,663; `src/tools` 1; `src/tasks` 83; `src/sessions` 53; `src/memory` 1; `src/mcp` 20; `src/skills` 194; `src/routing` 19; `src/security` 88; `src/system-agent` 89; `src/context-engine` 15; `src/cron` 325; `src/gateway` 1,449; `src/node-host` 53; and `src/provider-runtime` 2.

The audit enumerated every path in those roots, searched the complete mandatory scope for runtime and Android integration facts, and materially inspected 256 evidence-bearing files. The grouped ledger is at the end. Tests were not executed: the audit intentionally preserved the reference checkout and reports the test architecture/assets found in source rather than claiming a green run.

## Executive finding

The official Android app is not an Android agent runtime. It is a sophisticated native operator client plus a capability node attached to a remote OpenClaw Gateway.

`apps/android/app/src/main/java/ai/openclaw/app/NodeRuntime.kt` proves the division. It owns two `GatewaySession` instances: an operator session for chat, models, configuration, sessions, tasks, cron, skills, approvals, and management RPCs, and a node session that advertises Android capabilities and receives `node.invoke` requests. `NodeRuntime` never instantiates an LLM provider, `Agent`/agent loop, context engine, memory search manager, scheduler, or tool planner. Its generated `GatewayProtocol.kt` exposes 350 RPC method names and 46 event names; that breadth is remote control-plane surface, not local implementation.

The reusable Android achievement is therefore the edge: hardened WebSocket/device authentication, TLS/pinning, foreground-aware capability dispatch, native Android tools, durable client outbox/cache behavior, voice/audio plumbing, Compose UI, and a Play/third-party capability split. The missing self-contained core is nearly all of `src/gateway`, the embedded agent runner, provider routing, canonical session/task/cron storage, policy/approval ownership, memory/context, plugins/skills, and recovery coordination.

This distinction should drive Kinetic architecture:

```text
OpenClaw Android today
  native UI + native Android capability node
                  |
          authenticated Gateway protocol
                  |
  trust + agent loop + providers + tools + state + cron + memory + delivery

Self-contained Kinetic
  native UI + native capability layer
                  |
      in-process typed control-plane interfaces
                  |
  Kotlin agent kernel + policy + durable state + providers + scheduler + memory
```

Kinetic should reuse/adapt the left-hand native patterns and translate selected contracts/invariants from the right-hand side. It should not embed the Node Gateway wholesale.

## Classification key

| Class | Meaning for Kinetic |
|---|---|
| **A** | Already native Android and reusable/adaptable under MIT, subject to dependency/license review and Kinetic-specific API reshaping. |
| **B** | Portable contract/schema; preserve semantics and conformance vectors, express as Kotlin sealed types/data classes/serialization. |
| **C** | Valuable TypeScript algorithm or invariant; translate behavior, do not transplant a Node runtime merely to execute it. |
| **D** | Tightly bound to Node, desktop/Linux processes, filesystem, native Node add-ons, external CLIs, or OpenClaw host composition. |
| **E** | Unnecessary for Kinetic Core or a premature compatibility burden. It can remain remote/optional or be omitted. |

Classification is per implementation, not per directory. Several packages contain B/C contracts inside otherwise D implementations.

## Why Android still requires a Gateway

### What Android implements locally

`NodeRuntime` and its collaborators implement the following local edge responsibilities:

- Native app/UI lifecycle and presentation. `NodeApp.ensureRuntime`, `MainActivity`, `MainViewModel`, Compose screens, and the foreground service own Android process/UI state.
- Two authenticated protocol roles. `NodeRuntime.operatorSession` is an operator; `NodeRuntime.nodeSession` is a node. `apps/android/app/src/main/java/ai/openclaw/app/gateway/GatewaySession.kt` owns challenge/response, request correlation, event delivery, reconnect, timeouts, and node invoke handling.
- Device capability advertisement and invocation. `InvokeCommandRegistry.advertisedCapabilities` / `advertisedCommands`, `AndroidPermissionSnapshot.gatewayPermissions`, and `InvokeDispatcher.handleInvoke` expose only commands supported by build flavor, device feature, user setting, permission, and foreground state.
- Android-native side effects: camera, location, device/app discovery, notifications, contacts, calendar, photos, motion, SMS/call log in the sideload flavor, canvas/A2UI, audio/PTT, and the optional sideload-only accessibility executor.
- Client resilience. `ChatCommandOutbox` and the Room implementation journal user commands before network send, maintain attempt versions/branch epochs, distinguish accepted/failed/ambiguous delivery, and reconcile after reconnect/process death. `ClientDatabases.kt` separates disposable Gateway cache from durable client state.
- Local media/voice primitives. Android `SpeechRecognizer`, `TextToSpeech`, `AudioRecord`, Media3, and CameraX are used for recognition/capture/playback. Actual Talk sessions and agent responses remain Gateway RPCs.

### What Android explicitly delegates

The following are Gateway calls or Gateway-fed projections, not local engines:

| Surface visible in Android | Evidence | Actual owner |
|---|---|---|
| Chat/run execution | `NodeRuntime` calls `chat.send`, `chat.history`, `chat.abort`; `ChatController` consumes streamed Gateway events. | Gateway `src/gateway/server-methods/chat*.ts`, embedded runner `src/agents/embedded-agent-runner*.ts`. |
| Models/providers | Android displays/chooses catalogs via RPC and even labels Ollama, but has no provider transport or inference session. | Gateway/provider/plugin runtime and `src/agents` model resolution. |
| Sessions/transcripts | Android caches projections; it does not own the canonical transcript. | SQLite-backed host session manager and Gateway session APIs. |
| Tasks/subagents | Android parses/lists/cancels task state. | `src/tasks/task-registry*.ts`, agent harness/subagent runtimes. |
| Cron/proactivity | Android offers cron CRUD/run UI. | `src/cron/store.ts`, `service.ts`, isolated runner, task ledger. |
| Memory/search | Android invokes memory/doctor surfaces. | `packages/memory-host-sdk` plus Gateway method handlers/plugins. |
| Skills/ClawHub/workshop | Android searches, enables, installs, and reviews through RPC. | `src/skills` loader/lifecycle/scanner/workshop and Gateway. |
| Approvals/questions | Android presents and resolves decisions. | Gateway owns request identity, authorization, pending state, expiration, and execution binding. |
| Talk/realtime agent | `TalkModeManager`, `RealtimeAgentCoordinator`, and Wear call `talk.session.*` / `talk.speak`. | Gateway Talk relay, providers, agent/tool loop. |
| Routing/delivery/channels | Android selects Gateway/agent/session but has no multi-channel router. | `src/routing`, Gateway, channel plugins and delivery queues. |
| System-agent/config | Android hosts a controller UI; mutations are RPCs. | `src/system-agent` and Gateway. |

The Gateway is therefore not a WebSocket proxy. It is the system's trusted control plane and most of its data plane.

### Exact Gateway responsibilities that must exist natively

1. **Ingress, identity, and authorization.** `src/gateway/server/ws-connection/connect-admission.ts`, `handshake-auth-helpers.ts`, `connect-device-pairing.ts`, `authenticated-request-dispatch.ts`, `method-scopes.ts`, and `methods/registry.ts` negotiate protocol ranges, verify credentials/device proof, bind role/scopes, revalidate pairing generation, rate-limit invalid traffic, and authorize each method. `handleGatewayRequest` gates session mutations, startup state, suspension, and restart.
2. **Agent/run admission and execution.** `src/gateway/server-methods/agent.ts`, `chat.ts`, and the `src/agents/embedded-agent-runner` pipeline create/admit runs, build runtime context, choose model/auth profile, stream the provider, execute tools, apply steering/follow-ups, compact context, and publish terminal state.
3. **Provider/model/auth routing.** `src/agents/model-fallback-*.ts`, auth-profile storage, provider/plugin registries, and Gateway model methods select credentials/models and decide whether replay/fallback is safe. Crucially, fallback is suppressed after committed side effects or direct-delivery evidence.
4. **Canonical sessions/transcripts.** `src/agents/sessions/session-manager.ts` is an explicit SQLite transcript tree; Gateway session/chat handlers persist user turns, assistant/tool events, leaf controls, model changes, and restart-recovery ownership. Android's cache is not authoritative.
5. **Task/subagent state.** `src/tasks/task-registry-state.ts` persists before publishing in-memory state, maintains owner/run/session indexes, and separates execution status from delivery status. SQLite and recovery/maintenance code repair orphaned/lost work.
6. **Tool catalog, policy, approvals, and execution.** `src/agents/agent-tools.before-tool-call*.ts`, `tool-policy*.ts`, tool registry, Gateway approval methods, and node registry decide availability, authorize final adjusted parameters, register approvals before exposure, dispatch local/remote tools, and record results.
7. **Node routing.** `src/gateway/node-registry.ts` and `node-registry.invoke-stream.ts` track node connection/pairing generations, command manifests, invoke deadlines, idempotency, input/progress/result streams, cancellation, and route-change failure. Android only fulfills an invocation once selected.
8. **Scheduling/proactivity.** `src/cron` owns schedule calculation, timezone/stagger semantics, durable reservation, startup catch-up, concurrency admission, isolated/current/named sessions, run history, retry/failure alerts, delivery, and restart repair.
9. **Memory/context.** `packages/memory-host-sdk` owns embedding/search/storage engines; `src/context-engine/types.ts` defines context assembly/ingest/compaction hooks and host capability requirements. Gateway initializes/quarantines implementations.
10. **Skills/plugins/MCP/channels.** Gateway discovers and activates plugins/skills, enforces install/code policies, exposes MCP/HTTP surfaces, and routes channel messages. Android only displays/control-manages these.
11. **Delivery/event projection.** `src/gateway/server-chat.ts`, broadcasts/subscriptions, routing, channel adapters, and task delivery logic give recipients scoped/ordered updates and perform external sends.
12. **Configuration, secrets, health, audit, migration, and lifecycle.** `server-start.ts`, `server-lifecycle.ts`, `server-runtime-startup-services.ts`, `src/security`, system agent, config handlers, and audit methods initialize services, materialize secrets at controlled boundaries, report health, migrate state, and shut down coherently.

## Minimum viable native control plane for Kinetic

An OpenClaw-compatible feature list is not the minimum. The minimum self-contained Kinetic control plane is the smallest set that can accept a user turn, run a model/tool loop safely, survive process death, and resume without duplicate side effects.

| Native component | Minimum responsibility | OpenClaw evidence to port semantically | Android-native implementation direction |
|---|---|---|---|
| `RunCoordinator` | Create run ID, single-flight/session admission, cancellation, terminal classification. | `packages/agent-core/src/agent-loop.ts`; `src/gateway/server-methods/agent*.ts`. | Kotlin coroutines, structured concurrency, `SupervisorJob`; no JNI. |
| `AgentKernel` | Context -> streamed assistant events -> validated tool calls -> tool results -> next turn; steering/follow-up; bounded loop. | `runAgentLoop`, `Agent`, embedded runner loop. | Kotlin sealed event stream/`Flow`; no JS/Node. |
| `ModelRouter` | Resolve cloud/local provider, credentials, capabilities, fallback candidates; never replay after uncertain side effects. | `llm-core` contracts, model fallback runner/attempt. | Kotlin provider interfaces; Android Keystore-backed secret references. |
| `DurableJournal` | Persist user admission, run phase, transcript events, tool intent/result, and terminal outcome before observable transitions. | SQLite session tree, task registry state, Android outbox. | Room/SQLite transactions, append-oriented event tables, idempotency keys. |
| `CapabilityRegistry` | Typed tool schemas, required permissions/scopes, risk/confirmation/foreground/availability metadata, local executor binding. | `src/tools/types.ts`, Android `InvokeCommandRegistry`, Gateway descriptors. | Kotlin registry + generated JSON Schema adapters. |
| `PolicyAndApprovalEngine` | Evaluate final parameters at execution boundary; bind approval to run/tool/request/arguments; deny on drift; audit. | before-tool-call pipeline, exec approval plan, node invoke approval, permission snapshot. | Pure Kotlin policy; stable canonical argument hash; UI approval flow. |
| `ContextAndMemory` | Assemble prompt/context; retrieve bounded memories; compact without losing audit history. | `ContextEngine` contract, session branches, memory embedding contract. | Room/SQLite FTS/vector option; separate raw journal from model projection. |
| `TaskScheduler` | Durable task status and delivery status; WorkManager-compatible one-shot/periodic triggers; restart reconciliation. | task registry and cron reservation/catch-up/repair. | Room + WorkManager/AlarmManager only where justified; no systemd imitation. |
| `LifecycleController` | Rehydrate from durable journal, reconcile `running/sending`, honor foreground/background capability constraints. | Android service/outbox, Gateway restart recovery, cron task ledger. | Application/process observer + WorkManager + narrowly scoped foreground service. |
| `Audit/Telemetry` | Append decision/trajectory events, redaction, bounded retention, export. | security/system-agent audit, task/session events, Gateway trajectory tests. | Room event log; privacy-first local export. |

Not required for the first self-contained kernel: multi-channel Gateway server, arbitrary plugins, ClawHub, remote nodes, full cron expression parity, system-agent configuration wizard, general MCP hosting, desktop shell/sandbox, Docker, terminal emulation, or Accessibility-based general autonomy. Those are E for the core milestone even when their contracts are interesting.

## Android implementation audit

### Build and architecture

`apps/android/app/build.gradle.kts` uses `compileSdk=37`, `targetSdk=36`, `minSdk=31`, Kotlin/Java 17, Compose, coroutines, Kotlin serialization, Room, AndroidX Security Crypto, CameraX, OkHttp, Media3, Bouncy Castle, Coil, ML Kit barcode scanning, and dnsjava. Product flavors are `play` and `thirdParty`. The Play foreground service type is `connectedDevice|microphone`; third-party adds `location`.

The `ndk.abiFilters` list covers ARMv7, ARM64, x86, and x86_64 because dependencies package small native libraries. Searches found no app-owned `CMakeLists.txt`, C/C++, JNI bridge, or AIDL service under `apps/android`. Node is invoked by Gradle to stage A2UI assets; it is a build-time tool, not an Android runtime dependency. There is no Hilt/Dagger/Koin container; dependencies are composed manually. No WorkManager or DataStore implementation was found. Room is the durability mechanism.

### Manifest and platform surfaces

`app/src/main/AndroidManifest.xml` declares network/connected-device/microphone foreground service, notifications, nearby discovery, foreground location, camera/audio, contacts, calendar, and activity recognition. It registers:

- non-exported `NodeForegroundService`;
- permission-protected `DeviceNotificationListenerService`;
- exported Wear Data Layer listener with constrained intent filters;
- non-exported `FileProvider`;
- `MainActivity` for launcher, `ACTION_ASSIST`, `SEND`, and `SEND_MULTIPLE` media/document shares.

Its `<queries>` block is limited to launcher activities and speech recognition services. It does not request `QUERY_ALL_PACKAGES`. The audited manifests contain no `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`, MediaProjection service/permission, overlay, VPN, device admin, `VoiceInteractionService`, or arbitrary exported Binder/AIDL surface.

`app/src/thirdParty/AndroidManifest.xml` adds background/FGS location, media/photos, SMS, call-log permissions, and a disabled-by-default exported `OpenClawAccessibilityService`/developer activity protected by the platform accessibility bind permission where applicable. The Play flavor does not carry an additional manifest; sensitive permissions/components exist only in the third-party source set/manifest.

### Lifecycle and process death

`NodeApp.ensureRuntime` lazily creates the process singleton and can restore a background runtime. `NodeForegroundService.startRuntimeIfNeeded` returns `START_STICKY`, keeps Gateway connectivity alive when explicitly enabled, and restores after a fresh process. It deliberately disables foreground-only capture state when backgrounded and suppresses service restart after explicit disconnect.

This is a strong edge-client pattern, not proof that Android can host a permanent daemon. The service can still be killed, background-start restrictions still apply, and its durable behavior comes from Room/outbox/state restoration. Kinetic should copy that premise: a service is a lease for active user-visible work; correctness must live in durable state and restart reconciliation.

`MainViewModel` uses `SavedStateHandle` for UI drafts/selections, while durable commands/transcripts use Room. This is the correct division: `SavedStateHandle` is UI reconstruction, not the task system of record.

### Gateway transport, device identity, and TLS

`GatewaySession` is unusually mature and is the strongest directly reusable Android subsystem:

- desired-state reconnect with capped backoff and network-restore retry;
- `connect.challenge` followed by signed nonce/timestamp/role/scopes using Ed25519;
- request UUIDs, deferred correlation, timeout/abort handling, and a connection-generation lease so a request is committed only to the intended socket epoch;
- explicit distinction between not-enqueued, definitive error, and `GatewayRequestOutcomeUnknown`, which prevents unsafe automatic replay;
- ordered message processing, sequence handling, pending-request drain on disconnect;
- bounded node invoke acknowledgement, per-invoke coroutine, default timeout and cancellation;
- role-token persistence and constrained remote retry behavior.

`ConnectionManager` requires TLS for remote hosts and permits cleartext only for local/private development cases. A discovery fingerprint is not silently trusted. `GatewayTls.kt` uses platform trust/hostname verification for ordinary DNS endpoints; pin/TOFU paths require the expected SHA-256 certificate fingerprint. A pinned route may bypass hostname matching only because the exact certificate fingerprint is checked.

`DeviceIdentityStore` generates Ed25519 through Bouncy Castle, derives `deviceId` from the public key, signs the challenge payload, stores PKCS#8 private-key bytes in encrypted preferences, and migrates an older plaintext file. This is better than plaintext but not a hardware-backed Android Keystore signing key: the private key is serialized/extractable after preferences decryption. Kinetic should prefer an Android Keystore non-exportable signing key where algorithm/device support permits, with an explicit software-key fallback/migration policy.

`SecurePrefs` uses `MasterKey` and `EncryptedSharedPreferences` for secrets; ordinary preferences hold non-secret settings. `DeviceAuthStore` keys encrypted role tokens by Gateway/device/role and preserves scope metadata. The approach is A, while AndroidX Security Crypto's lifecycle/deprecation status should be checked at implementation time and potentially replaced with direct Keystore + authenticated encryption.

### Native capabilities and authorization boundary

`InvokeCommandRegistry` is a useful model for availability, with `InvokeCommandSpec(name, requiresForeground, availability)` and independent capability descriptors. It derives advertised commands/caps from `NodeRuntimeFlags`, build flavor, device feature, user toggle, and permissions. `AndroidPermissionSnapshot.gatewayPermissions` sends independently grantable facts rather than one broad “Android access” boolean.

`InvokeDispatcher.handleInvoke` then rechecks availability at execution time and routes to typed handlers. This double boundary matters: advertisement is not authorization. Canvas calls are serialized with a `Mutex`; foreground-only calls fail with structured errors. Kinetic should extend this design with risk, required Kinetic scopes, confirmation policy, data sensitivity, network policy, and audit policy, but should not treat a static manifest as sufficient.

Direct A candidates include the camera/location/contacts/calendar/motion/notification/media handlers, permission snapshot, dispatch/availability design, bounded JPEG/media behavior, and installed-app opt-in policy. They need Kinetic-specific schemas and tests, not wholesale package renaming.

### Android AppFunctions bridge seam

No Android AppFunctions implementation or dependency was found in the audited Android/mandatory scope. OpenClaw nevertheless exposes a useful adapter seam: `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeCommandRegistry.kt` describes runtime availability and `src/tools/types.ts` separates a tool descriptor from its executor. A future bridge should remain an adapter, not make an AppFunction the Kinetic domain object:

```text
Kinetic ToolExecutor
       ^
typed Kinetic CapabilityDescriptor
       v
AppFunction discovery/invocation adapter
```

Compared with an OpenClaw `node.invoke` command, the descriptor must add Android caller/owner identity, AppFunction availability/version, required Android permissions, foreground/background constraints, risk/confirmation policy, argument/result size limits, cancellation/idempotency, and audit provenance. Discovery must never imply authorization; `InvokeDispatcher.handleInvoke`'s execution-time recheck remains necessary. This is **B/C** as an architectural mapping and `UNKNOWN_REQUIRES_POLICY_RESEARCH` for a future Play product until the selected Android API level, OEM availability, caller model, and current policy are verified.

### Play and third-party split

`apps/android/app/src/play/java/ai/openclaw/app/SensitiveFeatureConfig.kt` sets SMS, call log, photos, background location, and accessibility control to false. Its `MobileUiHandler` always returns `MOBILE_UI_UNAVAILABLE`. The third-party flavor enables those flags and supplies the real handlers. This is a real compile-time/source-set boundary, not a UI-only toggle.

The third-party accessibility implementation still contains good defensive engineering: `AccessibilitySnapshotter` caps output at 400 nodes, depth 40, 4,000 visits, and 200 characters per text field; password/sensitive editable content is redacted. `AccessibilityActionExecutor` binds references to snapshot generation, refreshes nodes, checks package/UI epoch before blind coordinate gestures, rejects stale targets, and refuses password text entry. Those safeguards are worth documenting, but under the project's Play assumption the general autonomous accessibility surface is `ADVANCED_SIDELOAD_ONLY`, not a Kinetic Play dependency.

Play-oriented classifications from this repository:

| Capability | OpenClaw evidence | Kinetic classification |
|---|---|---|
| Camera, microphone, foreground location, contacts/calendar with contextual permission | Main manifest + runtime checks | `PLAY_SAFE_OR_LIKELY`, with prominent purpose/just-in-time consent and data minimization. |
| Connected-device/microphone foreground service | `NodeForegroundService`, flavor service type | `PLAY_SAFE_OR_LIKELY` only for active/user-visible work; not a daemon substitute. |
| Notification listener | permission-protected service and opt-in settings | `PLAY_RESTRICTED_REQUIRES_REVIEW`; avoid making core autonomy depend on it. |
| Launcher-visible installed apps | `<queries>` + explicit user enablement | `PLAY_SAFE_OR_LIKELY` if limited to declared/launcher queries; do not add `QUERY_ALL_PACKAGES`. |
| SMS/call log/background location/recent-library photos | third-party manifest/source set | `PLAY_RESTRICTED_REQUIRES_REVIEW` or `ADVANCED_SIDELOAD_ONLY`; not core. |
| General Accessibility UI control | third-party-only source set, Play stub | `ADVANCED_SIDELOAD_ONLY` under the stated design constraint. |
| `MANAGE_EXTERNAL_STORAGE`, overlay, app installation, root/Shizuku, device admin, VPN | absent | E for Kinetic Core; only a separately justified advanced/system profile could investigate. |

### Voice and multimodal behavior

`VoiceWakeManager` uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` and offline-preference intent extras. `ChatDictation` uses the same platform recognizer. `TalkModeManager` coordinates platform `SpeechRecognizer`, `AudioRecord`, local `TextToSpeech`, remote PCM/audio, cancellation, and audio-focus transitions. `MessageSpeechController` and `TalkSpeakClient` fall back to on-device TTS if `talk.speak` is unavailable.

However, realtime/agent voice is not self-contained: `TalkModeManager`, `RealtimeAgentCoordinator`, NodeRuntime, and Wear invoke `talk.session.create`, `appendAudio`, `startTurn`, `submitToolResult`, `steer`, `acknowledgeMark`, `cancelOutput`, and `close` on the Gateway. There is no Android Whisper, ONNX/TFLite/LiteRT speech model, local chat LLM, or local embedding runtime in the app.

The camera, audio capture/playback, platform speech/TTS, media codecs, and lifecycle arbitration are A. The Gateway Talk protocol is B if interoperability is desired. The server/provider speech implementation is not an Android engine.

### Persistence and uncertain outcomes

`ChatCommandOutbox` is the best concrete process-death/retry reference in OpenClaw Android:

- command row is written before network send;
- row ID is the protocol idempotency key;
- state distinguishes queued, sending, accepted, failed, and parked/ambiguous cases;
- an attempt version prevents stale completion from mutating a later retry;
- session mutation/branch epochs prevent a command from replaying into a changed transcript branch;
- process restart converts in-flight `Sending` into an unconfirmed state rather than blindly resending;
- history reconciliation attempts to prove delivery; unresolved accepted/orphaned cases are parked for manual resolution;
- cancellation ownership is explicit, so UI coroutine cancellation does not strand durable state.

`ClientDatabases` separates a disposable Gateway cache (`fallbackToDestructiveMigration`) from a durable client-state database with fail-closed explicit migrations and transactional Gateway removal/import. Kinetic should preserve this separation between reconstructable projection and authoritative durable intent.

## Package-by-package classification

| Package | Class | Implementation finding and Kinetic decision |
|---|---|---|
| `packages/agent-core` | **B/C** | `runAgentLoop` is the cleanest portable loop: evented turns/messages, transform/convert context, provider streaming, validated JSON tool args, parallel vs sequential execution, pre/post hooks, abort boundaries, steering/follow-ups, and model-visible tool errors. `Agent` is in-memory and provides no persistence. Port semantics into Kotlin; do not adopt it as the entire runtime. |
| `packages/llm-core` | **B** | `types.ts` defines normalized provider/model/context/message/tool/usage/stop-reason and assistant stream events (`start`, text/thinking/toolcall deltas, `done`, `error`). `StreamFunction` encodes runtime failure in stream events rather than throwing. Translate to Kotlin sealed types and provider interfaces. TypeBox is an implementation choice, not a dependency to carry. |
| `packages/gateway-client` | **B/C/D** | Excellent protocol/reconnect/session projection semantics, but published implementation uses Node `crypto`, `ws`, Node engines and host injection. Browser entry exists but is still Gateway-specific. Android already has the native equivalent. Use conformance/reconnect/secure-URL semantics; do not embed this client. |
| `packages/gateway-protocol` | **B** | Strong closed TypeBox schemas for frames, connect/hello, agent, nodes, tasks, sessions, cron, skills, questions, approvals, errors, and audit. Protocol is v4; general client min v4, node/probe min v3. Android advertises 3–4, which is accepted because it includes current v4. Generate Kotlin models/tests only if OpenClaw interop is a product goal; otherwise derive a smaller Kinetic protocol. |
| `packages/plugin-sdk` | **D/E** | Existing files are mostly facades into OpenClaw host runtime. The exact clean checkout is not a stable standalone source boundary: `package.json` advertises 60 default source exports, but 35 targets are absent under `packages/plugin-sdk/src` (for example `core.ts`, `ssrf-runtime.ts`, `async-lock-runtime.ts`, `delivery-queue-runtime.ts`). Build scripts generate/check distribution surfaces. Do not base Kinetic Core on this package. Extract only narrowly documented contracts. |
| `packages/plugin-package-contract` | **B/E** | Small package compatibility metadata (`openclaw.compat.pluginApi`, build OpenClaw version, min Gateway/builtWith/sdk normalization). Useful precedent for versioned package metadata, unnecessary until Kinetic has an extension package format. |
| `packages/memory-host-sdk` | **B/C/D** | Embedding/provider/query interfaces and bounded input/normalization/retry algorithms are portable. Implementations use `node:sqlite`, `sqlite-vec` native extension, dynamic `node-llama-cpp`, worker child processes, filesystem corpus, and QMD child processes/taskkill. Port interfaces and retrieval semantics; implement Android storage/inference independently. |
| `packages/media-core` | **C/D** | Valuable bounded stream reads, signature-first inline image sanitation, MIME normalization/sniff caps, inbound-root policy. Current code uses Node `Buffer`, `node:path`, and a Node build. Translate algorithms to `InputStream`/`ByteBuffer`, `ContentResolver`, SAF/URI grants. Do not reproduce desktop absolute-path policy as Android's primary media model. |
| `packages/speech-core` | **C/D** | Provider resolution/fallback, text normalization, streaming/non-streaming synthesis attempts and channel delivery compatibility are useful semantics. Implementation imports the OpenClaw plugin SDK, Node `Buffer`, provider registry, persistence and transcoding. Android already proves native capture/TTS. Port provider contracts/fallback rules only. |
| `packages/tool-call-repair` | **C** | Pure TS parser/stream normalizer repairs bracket, Harmony, JSON, and XML-ish tool calls. It applies allowlists, protected ranges, bounded UTF-8 payloads (default 256 KB), incremental buffering and terminal scrubbing/promotion. Valuable compatibility layer for defective providers, but complex and security-sensitive; port only with its tests and keep strict native structured calling primary. |

### `agent-core` loop semantics worth preserving

`runAgentLoop` has several non-obvious correctness properties that should become Kinetic tests:

- Provider abort/error becomes a terminal event; tool execution sees `AbortSignal` at boundaries.
- Only structured `toolUse` content executes. Arguments are parsed/validated before the tool hook.
- `beforeToolCall` may approve, adjust, or block; `afterToolCall` observes terminal outcome.
- Tools can declare sequential execution; otherwise parallel calls complete concurrently but their results are emitted in original source order, preserving deterministic model context.
- Tool exceptions become tool results visible to the model, allowing recovery rather than crashing the outer run.
- Network-derived tool results carry taint metadata.
- Steering and follow-up queues are explicit rather than prompt-string hacks.

OpenClaw's full `src/agents` runner adds important host invariants: model fallback candidates are attempted only while replay is safe; committed side effects/direct delivery stop fallback. Context overflow uses bounded compaction retries and branch-preserving tool-result truncation, not destructive transcript rewrite. Translate those invariants into the native journal/policy layer.

### Prompt construction, planning boundary, and observability

`src/agents/system-prompt.ts` exports `buildAgentSystemPrompt`. It constructs `full`, `minimal`, or identity-only prompt surfaces from the *effective* tool set, runtime/workspace facts, sandbox constraints, bootstrap/context files, skills, prepared memory, delegation, channel behavior, provider contributions, and heartbeat guidance. It filters project-scoped curated context before injection and splits a hashed/cached stable prefix from a volatile date/session/channel/approval suffix. Its own text says project instructions guide tool use but never grant tool availability: prompt guidance is deliberately subordinate to registry/policy enforcement. These composition and cache-boundary ideas are **C**; the 63 KB Node/OpenClaw-specific renderer is **D/E** for Kinetic.

There is no separate symbolic planner service in the principal `agent-core`/embedded path. The provider produces assistant reasoning and structured tool calls inside the iterative loop; task/subagent/cron layers coordinate durable work around it. Kinetic therefore should not copy a fictional OpenClaw “planner/executor split.” It may add an explicit `Plan` representation where inspectability or long-horizon recovery requires one, while keeping tool authorization at execution time.

For observability, `src/agents/embedded-agent-runner/run/attempt-trajectory.ts::prepareEmbeddedAttemptTrajectory` resolves an attempt-local trajectory target and records `session.started` plus `trace.metadata` containing run/session identity, provider/model/API, trigger/channel, timeout/modes, tool counts, skill snapshot, and a system-prompt report. `src/agents/trace-base.ts::buildAgentTraceBase` standardizes the small correlation tuple used by provider/model diagnostics. `src/agents/sessions/telemetry.ts::isInstallTelemetryEnabled` makes install telemetry an explicit environment-or-persisted setting, not an implicit side effect. The portable lesson is **B/C**: typed, correlated, redacted local trajectory events with explicit collection/export policy; filesystem/process-environment plumbing is **D**.

## Mandatory `src` area findings

| Area | Class | Evidence and decision |
|---|---|---|
| `src/agents` | **C/D** | Full host runner owns lanes, session runtime, provider/auth fallback, compaction, tools, plugins, sandbox, subagents and delivery. Too coupled/large to port. Lift `run` state transitions, replay-safety, cancellation, deterministic tool ordering, loop detection, compaction, and tests. |
| `src/tools/types.ts` | **B** | Small descriptor (`owner` core/plugin/channel/MCP, executor, availability expression). Good starting concept, but Kinetic needs Android permissions, scopes, risk, confirmation, sensitivity, network/foreground/audit metadata. |
| `src/tasks` | **B/C/D** | `TaskRecord` separates runtime (`subagent`, ACP, CLI, cron), execution status (`queued/running/succeeded/failed/timed_out/cancelled/lost`) and delivery status. `task-registry-state` persists before memory and uses composite writes/indexes. SQLite/Kysely implementation is D; state/reconciliation semantics are C. |
| `src/sessions` and `src/agents/sessions` | **B/C/D** | Current authority is SQLite, not JSONL. `SessionManager` uses append-only parent-linked transcript events and leaf controls for branch/fork/rewind. User turns use exact idempotency. Restart recovery persists terminal-delivery intent before external send. Port append/tree/idempotency/recovery semantics to Room; do not port Node storage facade. |
| `src/memory/root-memory-files.ts` | **D/E** | Desktop workspace/root-file compatibility helper, not a memory engine. Actual architecture is in `memory-host-sdk`/plugins. |
| `src/mcp` | **B/D/E** | MCP stdio servers, channel bridge, plugin tools, Gateway client and Node process lifecycle. `plugin-tools-handlers` correctly applies the same before-tool-call boundary and AbortSignal to MCP calls. MCP contracts/authorization invariants are useful; stdio/Node server is not Android Core. Add a constrained client/bridge later. |
| `src/skills` | **B/C/D/E** | `Skill` contract, frontmatter validation, prompt catalog/versioning and source provenance are portable. Loader/install/ClawHub/workshop use Node filesystem, package managers, downloads, SQLite and executable content. Kinetic should initially support signed/declarative skills; arbitrary executable OpenClaw skills are E/Tier 3. |
| `src/routing` | **C** | `resolveAgentRoute` has deterministic precedence: exact peer, parent peer, peer-kind wildcard, guild+role, guild, team, account, channel, default. Session keys encode agent/channel/account/peer and normalize identity links. Worth translating if Kinetic becomes multi-agent/multi-app; unnecessary for a single-agent MVP. |
| `src/security` | **C/D** | Broad host audit covers Gateway exposure/auth, filesystem permissions, dangerous flags, plugin trust/code safety, exec/sandbox drift, model risk and suppressions. Host probing is D. Fail-closed visibility, safe-regex/input bounds, secret handling, finding/suppression/audit data contracts are C. |
| `src/system-agent` | **D/E** | Node/OpenClaw configuration operator. It requires live-verified inference, exact approval, path write policy, revalidation before commit, post-write verification and append audit. Strong transaction ideas, but Kinetic should use typed settings/onboarding rather than port this conversational host administrator. |
| `src/context-engine` | **B/C** | `ContextEngine` contract covers bootstrap, ingest/batch, per-turn assembly, maintenance, compaction, subagent lifecycle and disposal; `AssembleResult` carries prompt authority/projection. Registry compatibility and persisted quarantine are useful. Translate a smaller interface; avoid plugin-host coupling. |
| `src/cron` | **B/C/D** | Rich schedules (`at`, `every`, cron/TZ/stagger, on-exit/stream command), sessions and delivery; store is actually SQLite-backed despite legacy `jobs.json` naming. Durable reservation, startup catch-up, concurrency admission and task-ledger repair are valuable. Croner/child processes/commands are D; map safe schedules to WorkManager/AlarmManager. |
| `src/gateway` | **C/D/E** | Trusted server/control plane. Almost every control-plane invariant is valuable, but the Node HTTP/WebSocket/plugin/channel server is not an Android starting point. Build native in-process services, optionally expose a small authenticated interop endpoint later. |
| `src/node-host` | **D/E** | Headless Node worker advertises system commands, plugins, skills and MCP, then executes processes/PTY/CLI with an extensive approval-binding pipeline. It is deliberately node-local enforcement. Kinetic Core must not recreate arbitrary shell semantics. Translate only authorization invariants for bounded native tools. |
| `src/provider-runtime` | **C** | `operation-retry.ts` is a compact bounded exponential retry policy: retry read/poll/download on 429/5xx/timeouts/network; do not retry create by default to avoid duplicate side effects; do not retry ordinary 400/401/403/404. Directly adaptable with jitter/budget tests. |

## Persistence, scheduling, and recovery lessons

### Sessions

`src/agents/sessions/session-manager.ts` identifies itself as a session tree backed by explicit SQLite transcript identity. Events have IDs and `parentId`; active-path traversal selects a leaf without erasing abandoned branches. Fork/rewind creates/repoints branch state. JSONL appears in migration/export compatibility, but the live system of record is SQLite. Kinetic should not copy older “one JSON file per chat” assumptions from OpenClaw documentation or stale architecture descriptions.

The crucial pattern is dual representation:

- immutable/auditable raw journal;
- bounded model-facing context projection, which may compact/truncate without deleting evidence.

### Tasks

Task execution and delivery are separate dimensions. A run can be terminal while delivery is pending/failed/session-queued/parent-missing. This prevents “model finished” from being confused with “user received result.” `task-registry-state.ts` updates the durable store before in-memory publication and rebuilds indexes on restore. `task-registry.maintenance.ts` and reconciliation mark work lost or recover it when runtime ownership disappeared.

Kinetic should persist `Task`, `Attempt`, `Effect`, and `Delivery` facts transactionally. Process-local coroutine state is a cache, not authority.

### Cron

`src/cron/store.ts` uses `node:sqlite`; the historical `jobs.json` path is a logical partition/compatibility key. The service writes queued/running markers before execution, limits concurrent admissions, repairs interrupted `running` records on startup, and can use the task ledger to restore a finalized result after a stale cron write. Startup catch-up caps immediate work and staggers overflow to avoid a restart storm.

On Android, translate safe declarative schedules and recovery, not shell payloads. WorkManager provides eventual durable execution, while exact alarms require separate user-facing justification/permission and should not be the default. On-exit/stream/argv/script jobs are Tier 3/E for Play Core.

## Tool, policy, and approval security

OpenClaw's most reusable security insight is that permission and approval must be checked at the last trustworthy boundary against the exact operation that will execute.

`agent-tools.before-tool-call` layers loop detection, core/special policy, voice confirmation, plugin policy, approval, hooks, and final approval. Hook errors fail closed. If a hook adjusts parameters, authorization applies to the adjusted operation. Exec approval registration happens before the pending response is exposed, closing an approve-before-registration race. Node `system.run` binds approval to canonical working directory, executable/script identity, shell/argv interpretation and policy snapshot, then revalidates drift immediately before spawn.

For Kinetic native tools, port these principles:

1. Parse into one canonical typed request.
2. Resolve availability/permissions without side effects.
3. Evaluate policy and confirmation against canonical final parameters.
4. Persist approval request before UI publication.
5. Bind approval to request ID, run ID, tool ID, canonical-argument hash, permission/scope snapshot, and expiry.
6. Recheck foreground state, Android permission, target identity and policy version immediately before execution.
7. Persist effect intent/result and emit an audit event.
8. Treat cancellation/timeout/connection loss as potentially ambiguous unless the executor proves no side effect.

Do not port OpenClaw's Docker/Podman/SSH/process sandbox as an Android security boundary. Android app sandboxing, URI grants, permission APIs, isolated services where valuable, and narrow capability executors are the relevant primitives.

Additional reusable security details:

- `context-visibility.ts` defaults supplemental history/thread/quote/forwarded context to omitted unless mode/allowlist permits it.
- `safe-regex.ts` rejects nested repetition/ambiguous unbounded alternation, bounds input windows, and caps its cache.
- media helpers sniff bytes rather than trusting caller MIME metadata and cap stream/sniff size.
- MCP/channel approval handling requires authenticated ingress to assert `senderIsOwner`; missing owner metadata fails closed.
- Gateway role authorization happens before scope authorization; node/operator roles do not share a permissive method surface.
- Pairing/credential generations are revalidated after authentication, so a rotated/revoked device cannot keep using an old socket indefinitely.

## Local inference findings

### Android app

No embedded local chat LLM, embedding model, Whisper, TensorFlow Lite, LiteRT, MediaPipe LLM, ONNX Runtime, llama.cpp/llamatik, or app-owned JNI inference integration exists in `apps/android`. Strings/catalogs for Ollama represent a Gateway-side provider. Platform on-device `SpeechRecognizer` and `TextToSpeech` are OS services, not Kinetic-owned model engines.

### OpenClaw host

The only actual in-process local neural runtime in the mandatory OpenClaw scope is local memory embeddings:

- `packages/memory-host-sdk/src/host/embedding-defaults.ts` defaults to `hf:ggml-org/embeddinggemma-300m-qat-q8_0-GGUF/embeddinggemma-300m-qat-Q8_0.gguf`.
- `embeddings.ts` dynamically imports `node-llama-cpp`, resolves/downloads the model, loads it, creates an embedding context (default context 4096), normalizes vectors, exposes AbortSignal checks, records GPU/offload/memory runtime facts, and disposes context/model/runtime.
- `embeddings-worker.ts` isolates that state in a Node worker/child process and serializes access.
- `sqlite.ts` uses `node:sqlite`; `sqlite-vec.ts` loads the native vector extension.

Ollama/LM Studio support is HTTP to external local servers, not an Android in-process engine. The `sherpa-onnx-tts` occurrence under skills is an external executable skill/tool with runtime/model directories, not an Android library integration.

Recommendation: **ADAPT_INTERFACE_AND_LIFECYCLE_ONLY**. The embedding contract (`id`, `model`, `embedQuery`, `embedBatch`, `close`, `AbortSignal`) and explicit runtime facts are B. `node-llama-cpp`, worker processes and native SQLite extension are D. A Kinetic Android engine needs a separately evaluated AAR/JNI/native library, model storage/download verification, thermal/memory controls and device-tier compatibility.

Interface-level mapping for the later local-model research phase:

- `LocalModelEngine`: enumerate support and open an engine; not a global singleton.
- `ModelDescriptor`: model ID, task, format/quantization, byte size/hash/license, context/output constraints, required RAM/storage/backend.
- `InferenceSession`: explicit lifecycle, cancellation, streaming, metrics and close.
- `StructuredGeneration`: constrained JSON/tool schema capability and validation/fallback facts.
- `EmbeddingEngine`: batch/query contract, dimensions, normalization and vector metadata.
- `ModelStorage`: verified partial download, atomic activation, quota/eviction, provenance/license, version rollback.

No orchestration method here requires JNI. JNI/NDK is justified only for a chosen inference/audio/vector implementation with measured benefit.

## JavaScript and OpenClaw ecosystem compatibility

Embedding V8 or a lightweight JS interpreter does not provide Node built-ins, module resolution, npm native add-ons, processes, or a POSIX filesystem. The audited code supports three tiers:

### Tier 1 — native/declarative Kinetic skill

Suitable inputs are metadata, prompt/instruction resources, typed tool schemas, constrained pipelines, routing rules, schedules, and pure deterministic transforms with no ambient authority. OpenClaw precedents are `Skill` metadata/prompt version/source provenance, TypeBox schemas, plugin package compatibility metadata, and pure normalization/retry/routing algorithms. Translate/compile these to Kotlin or a declarative IR; validate size, version, signature/provenance and required capabilities before activation.

### Tier 2 — restricted JavaScript with Kinetic host APIs

Potentially viable only for pure JS modules with deterministic resource caps and no imports outside an allowlisted standard library. Network, files, secrets, timers and Android capabilities must be explicit async host calls that pass through Kinetic policy/audit. Candidate logic includes small normalization/formatting/routing functions. Neither `plugin-sdk` nor a generic npm package should be assumed Tier 2 merely because it is TypeScript.

Required controls include CPU/wall/memory limits, cancellation, no reflection/JNI/classloader access, canonical serialization, module/signature pinning, host-call schemas, no dynamic code download/eval, and explicit upgrade/revocation.

### Tier 3 — genuine Node/Linux/desktop semantics

The following stay outside Kinetic Core: `node:*` imports; `child_process`; shells/PTY; arbitrary filesystem/path traversal; package managers and installer scripts; native Node add-ons (`node-llama-cpp`, `sqlite-vec`, ONNX/node-pty/sharp-class dependencies); Docker/Podman/SSH sandbox; MCP stdio child servers; QMD and other CLIs; Python; environment/PATH-dependent skills; plugin lifecycle expecting OpenClaw global runtime; desktop channel adapters.

Most executable OpenClaw plugins/skills are Tier 3. A future remote compatibility node may run them, but the native app should communicate through a constrained capability protocol rather than pretend the code is local/Play-safe.

## Testing and benchmark assets

The mandatory scope contains a very large test corpus. Filename-based enumeration found 1,151 test/support paths under `src/agents`, 605 under `src/gateway`, 160 under `src/cron`, 75 under `src/skills`, 48 under `src/security`, 44 under `gateway-protocol`, 38 under `memory-host-sdk`, and substantial tests in every other control-plane area.

Android has 206 Kotlin test files under unit/instrumented source sets (205 unit, 1 instrumented by path) and 2,347 `@Test` annotations across Android Kotlin. Of these, 184 unit-test files are under the phone app, 13 under Wear, and 2 under wear-shared; additional flavor-specific tests account for the remainder. One instrumented canvas lifecycle test and macrobenchmark infrastructure exist.

High-value Kinetic adaptations:

| Kinetic concern | OpenClaw test evidence to adapt |
|---|---|
| Network loss/reconnect/ambiguous send | Android `GatewaySessionReconnectTest`, `GatewaySessionInvokeTimeoutTest`, `ChatControllerReconnectRestoreTest`, `ChatControllerOutboxTest`, `RoomChatCommandOutboxTest`. |
| Process-death recovery | `NodeForegroundServiceTest`, `ClientDatabasesTest`, task registry process-state/maintenance tests, cron restart-catchup/startup-run-repair tests, chat restart recovery tests. |
| Tool correctness/permissions | `InvokeCommandRegistryTest`, `InvokeDispatcherTest`, handler tests, `AndroidPermissionSnapshotTest`. |
| Stale UI grounding | third-party `AccessibilitySnapshotterTest`, `MobileUiHandlerTest`, canvas lifecycle/action trust tests. Adapt only to advanced profile or native app-controlled surfaces. |
| Planner/tool loop | `packages/agent-core/src/agent-loop.test.ts`, `src/agents/tool-loop-detection.test.ts`, before-tool-call approval/e2e tests. |
| Hallucinated/malformed tool calls | gateway protocol validator tests and all three `tool-call-repair` suites. |
| Cancellation and replay safety | node-host abort tests, embedded runner abort/timeout/fallback tests, task executor/registry cancellation tests. |
| Session branching/idempotency | `session-manager.test.ts`, `session-manager.user-idempotency.test.ts`, user-turn transcript persistence tests. |
| Scheduling | cron protocol conformance, schedule/stagger, run admission, restart catch-up, timer and task-ledger tests. |
| Security | Gateway authz/pairing tests, security audit tests, safe-regex/external-content/install policy tests, Android TLS/device-auth tests. |
| Performance/battery | Android macrobenchmark `StartupMacrobenchmark#coldStartup`, scripts for startup/simpleperf. Add battery, WorkManager latency, thermal, model memory and long-run foreground tests; OpenClaw does not supply these end-to-end Kinetic measures. |

Kinetic should create replayable run trajectories with model events, normalized tool request, policy decision, executor result, state transition and delivery fact. OpenClaw's test architecture is strongest for invariants/unit failures; it is not by itself an Android agent task-success benchmark.

## License and provenance

| Candidate | Provenance/license evidence | Decision |
|---|---|---|
| OpenClaw Android transport/outbox/capability patterns | Root `LICENSE`, MIT, Copyright 2026 OpenClaw Foundation. No conflicting file-level header was found in audited Kotlin/TS files. | Reuse/adapt is legally plausible under MIT; preserve copyright/license in substantial copies and record source/commit. Prefer semantic adaptation where Kinetic APIs diverge. |
| `agent-core`, protocol and pure algorithms | Root MIT; `gateway-client` and `gateway-protocol` also ship package-local MIT LICENSE files. | Reuse contracts/test vectors or translate semantics with attribution. Do not assume dependencies inherit MIT. |
| Pi/pi-mono-derived implementation portions | `THIRD_PARTY_NOTICES.md`: Pi/pi-mono MIT, Copyright 2025 Mario Zechner. | If copying/adapting an identified derived portion, preserve both OpenClaw and Pi notices. Trace file history before substantial copying because the notice is repository-wide rather than file-tagged. |
| Android dependencies/assets | 14 bundled notice files cover AndroidX Compose/Media3/Room/Wear, Bouncy Castle, Coil, CommonMark, dnsjava, KaTeX, Kotlin, Manrope, nibor autolink, OkHttp/Okio, SLF4J. Tests assert Apache, MIT, BSD-2/3, OFL and Bouncy Castle texts. | Kinetic must generate its own dependency/SBOM/notices from versions actually used; do not copy OpenClaw's bundle as a substitute. Font/assets have their own conditions. |
| Node/native optional dependencies | pnpm policy lists native/binary packages and build gates including sqlite-vec, node-pty, node-llama-cpp, ONNX-related packages, sharp, etc. | Not automatically covered by OpenClaw's MIT grant. Review every selected Android/native artifact, model and transitive dependency separately. |

No repository/file-level copyleft conflict was found in the audited OpenClaw source. The main licensing risk is provenance drift: copying a substantial Pi-derived or third-party portion while preserving only the OpenClaw root notice, or assuming a downloadable model/native dependency is MIT because the host is.

## Compatibility and integrity findings

1. The checkout is Git-clean and auditable at a fixed commit, but its remote is a personal fork rather than the canonical URL recorded by the project. Any reuse decision should retain both the local hash and upstream provenance.
2. `packages/plugin-sdk` is source-incomplete relative to its own `exports`: 60 advertised default targets, 35 missing. This may be an intentional generated-build facade/refactor state, but exact source consumers cannot treat that package directory as independently intact. Kinetic should not copy from a generated/private SDK boundary without tracing the real `src/plugin-sdk` owner and build outputs.
3. Gateway protocol versioning is explicit. `PROTOCOL_VERSION=4`, `MIN_CLIENT_PROTOCOL_VERSION=4`, node/probe minimum 3. Android's shared advertised interval is 3–4; current operator sessions work because v4 overlaps. Kinetic interop should use role-aware generated conformance tests and not assume all v3 features exist.
4. Android's private signing identity is software-serialized PKCS#8 inside encrypted preferences, not guaranteed hardware-backed. Treat this as a security improvement opportunity.
5. Room client cache/outbox is not a local authoritative agent database. Reusing it without adding native run/task/memory journals would reproduce the Gateway dependency under a new name.
6. The Android service is `START_STICKY`, but correctness explicitly depends on recovery. It cannot justify a systemd-like architecture or defeat OEM process management.
7. Protocol/tool surfaces are much broader than a native MVP (350 Android method constants). Protocol compatibility should be an adapter module, not the Kinetic domain model.

## Concrete reuse recommendations

### Strongest A candidates

1. `GatewaySession` request-generation lease, ambiguous-outcome classification, reconnect, cancellation and ordered pump — adapt even if the transport becomes local/optional, because the same semantics apply to cloud provider calls and remote extensions.
2. Room `ChatCommandOutbox`/`ClientDatabases` journaling, attempt versioning, branch mutation gate and reconciliation — generalize into Kinetic effect/run journals.
3. `InvokeCommandRegistry` + `AndroidPermissionSnapshot` + `InvokeDispatcher` — expand into the native capability registry/policy executor.
4. TLS/pinning/device auth and Gateway registry UX — reusable for remote Kinetic nodes/cloud bridge, with hardware-backed key improvement.
5. Native camera/location/contacts/calendar/notification/audio/speech/TTS handlers and their tests — adapt behind Kinetic schemas and just-in-time permission flows.
6. Play vs third-party source-set split and hard Play stubs — reuse the architectural boundary, not necessarily every advanced capability.
7. Accessibility snapshot/action freshness/redaction controls — reference for advanced-only automation and for any app-owned semantic UI surface; never make it Play Core.

### Strongest B/C candidates

- `llm-core` event/message/tool/provider contracts.
- `agent-core` loop and deterministic tool-result ordering.
- Gateway frame/error/node/task/session/approval schemas as interoperability input.
- replay-safe fallback and side-effect commitment rules.
- append-only transcript tree, exact idempotency and terminal-delivery recovery.
- task execution/delivery separation and cron durable reservation/catch-up.
- context-engine lifecycle/quarantine contract.
- memory embedding interface and bounded retrieval/input rules.
- routing precedence and session-key scoping when multi-agent routing is needed.
- media signature/sniff/stream limits, safe regex, visibility fail-closed policy, and retry-by-operation-kind.

### Reject from Kinetic Core

- embedding Node/V8 as the orchestration runtime;
- arbitrary Node plugins/npm packages/shell/child processes/Python/CLIs;
- node-host system command/PTY/Docker sandbox parity;
- desktop path-based media/file semantics in place of Android URI grants/SAF;
- general accessibility automation in Play;
- persistent foreground service as daemon/scheduler;
- full OpenClaw Gateway, channels, ClawHub, system agent, and 350-method protocol before the native kernel works;
- JNI for orchestration, routing, policy, persistence or ordinary Android tools.

## Bottom-line answers for synthesis

**Why is the official Android application Gateway-dependent?** Because it implements presentation, transport and Android side effects while the Gateway owns trust, model/agent execution, canonical state, policy, scheduling, memory, skills/plugins, delivery and recovery coordination. The two-session design in `NodeRuntime` makes that split explicit.

**What must exist natively to remove the Gateway?** A Kotlin run coordinator/agent loop, provider/model router, authoritative SQLite journal for sessions/tasks/effects/delivery, capability registry/executors, policy/approval engine, context/memory subsystem, durable scheduler, lifecycle/reconciliation controller and audit/trajectory store. MCP, remote nodes, plugins and multi-channel delivery can follow as adapters.

**What is OpenClaw's biggest reuse opportunity?** The combination of native Android edge hardening and server-side correctness invariants: especially uncertain-outcome handling, durable outbox/idempotency, final-boundary authorization, append-only transcript branches, task/delivery separation and replay-safe fallback.

**What should not be inferred?** OpenClaw Android is not evidence that a local agent already exists, that a foreground service replaces a durable scheduler, that V8 supplies Node compatibility, that local Ollama labels mean in-app inference, or that the Play flavor can rely on accessibility/SMS/call-log/background-location features.

**Recommended architectural stance:** use OpenClaw Android as the best reference for a native operator/capability edge, `agent-core`/`llm-core`/protocol as semantic inputs, and Gateway/task/session/cron code as an invariant library for clean Kotlin design. Do not make Kinetic an Android shell around OpenClaw Gateway.

## Material inspection ledger

The audit materially inspected **256 local files**: 7 repository/build/license documents, 55 Android build/manifest/runtime/test files, 66 package implementation/schema/test files, and 128 files under the required `src` architecture roots. “Material” means full-file reading or a bounded implementation/symbol slice used to establish a finding; it excludes paths that were only enumerated. Directory-wide negative searches (for WorkManager, DataStore, DI frameworks, AIDL/JNI, local-model stacks, restricted permissions, etc.) are additional and are not counted as files.

### Repository/build/license — 7

`AGENTS.md`; `package.json`; `pnpm-workspace.yaml`; `LICENSE`; `THIRD_PARTY_NOTICES.md`; `apps/android/AGENTS.md`; `apps/android/README.md`.

### Android — 55

`apps/android/settings.gradle.kts`; `apps/android/gradle/libs.versions.toml`; `apps/android/app/build.gradle.kts`; `apps/android/app/src/main/AndroidManifest.xml`; `apps/android/app/src/thirdParty/AndroidManifest.xml`; `apps/android/app/src/play/java/ai/openclaw/app/SensitiveFeatureConfig.kt`; `apps/android/app/src/thirdParty/java/ai/openclaw/app/SensitiveFeatureConfig.kt`; `apps/android/app/src/main/java/ai/openclaw/app/NodeApp.kt`; `MainActivity.kt`; `MainViewModel.kt`; `NodeForegroundService.kt`; `NodeRuntime.kt`; `SecurePrefs.kt`; `gateway/GatewayProtocol.kt`; `gateway/GatewaySession.kt`; `gateway/DeviceIdentityStore.kt`; `gateway/DeviceAuthStore.kt`; `gateway/GatewayTls.kt`; `node/ConnectionManager.kt`; `node/InvokeCommandRegistry.kt`; `node/InvokeDispatcher.kt`; `node/AndroidPermissionSnapshot.kt`; `node/DeviceHandler.kt`; `node/NotificationsHandler.kt`; `node/CameraHandler.kt`; `node/LocationHandler.kt`; `node/ContactsHandler.kt`; `node/CalendarHandler.kt`; `node/PhotosHandler.kt`; `apps/android/app/src/play/java/ai/openclaw/app/node/MobileUiHandler.kt`; `apps/android/app/src/thirdParty/java/ai/openclaw/app/node/MobileUiHandler.kt`; `accessibility/AccessibilitySnapshotter.kt`; `accessibility/AccessibilityActionExecutor.kt`; `accessibility/OpenClawAccessibilityService.kt`; `chat/ChatCommandOutbox.kt`; `chat/ClientDatabases.kt`; `chat/ChatController.kt`; `chat/MessageSpeechController.kt`; `voice/VoiceWakeManager.kt`; `voice/TalkModeManager.kt`; `voice/TalkSpeakClient.kt`; `voice/AndroidAudioInputSession.kt`; `voice/RealtimeAgentCoordinator.kt`; `ui/chat/ChatDictation.kt`; and focused tests `GatewaySessionReconnectTest.kt`, `GatewaySessionInvokeTimeoutTest.kt`, `ChatControllerOutboxTest.kt`, `RoomChatCommandOutboxTest.kt`, `ClientDatabasesTest.kt`, `NodeForegroundServiceTest.kt`, `InvokeDispatcherTest.kt`, `ConnectionManagerTest.kt`, `GatewayTlsTest.kt`, third-party `MobileUiHandlerTest.kt`, and `AndroidLicenseNoticesTest.kt`.

### Packages — 66

`packages/agent-core/package.json`, `src/agent-loop.ts`, `agent.ts`, `types.ts`, `validation.ts`, `agent-loop.test.ts`; `packages/llm-core/package.json`, `src/types.ts`, `validation.ts`, `utils/event-stream.ts`; `packages/gateway-client/package.json`, `src/client.ts`, `protocol-client.ts`, `session-projection.ts`, `session-subscriptions.ts`, `reconnect-policy.ts`, `protocol-client.sequence.test.ts`; `packages/gateway-protocol/package.json`, `src/version.ts`, `schema/frames.ts`, `nodes.ts`, `tasks.ts`, `approvals.ts`, `exec-approvals.ts`, `cron.ts`, `sessions.ts`, `agent.ts`, `protocol-validator.ts`; `packages/plugin-sdk/package.json`, `src/security-runtime.ts`, `plugin-runtime.ts`, `provider-entry.ts`; `packages/plugin-package-contract/package.json`, `src/index.ts`; `packages/memory-host-sdk/package.json`, `src/engine-embeddings.ts`, `engine-foundation.ts`, `engine-storage.ts`, `engine-qmd.ts`, `host/embeddings.ts`, `embeddings.types.ts`, `embeddings-worker.ts`, `embedding-defaults.ts`, `sqlite.ts`, `sqlite-vec.ts`, `qmd-process.ts`, `node-llama.ts`; `packages/media-core/package.json`, `src/inbound-path-policy.ts`, `read-byte-stream-with-limit.ts`, `inline-image-data-url.ts`, `mime.ts`, `content-length.ts`; `packages/speech-core/package.json`, `src/tts.ts`, `tts-types.ts`, `tts-provider-resolution.ts`, `tts-synthesis.ts`, `tts-streaming.ts`, `speech-text.ts`; `packages/tool-call-repair/package.json`, `src/contracts.ts`, `grammar.ts`, `payload.ts`, `promote.ts`, `stream-normalizer.ts`.

### Core `src` architecture — 128

- Agents/tools: `src/agents/embedded-agent-runner.ts`, `embedded-agent-runner/run.ts`, `model-fallback-runner.ts`, `model-fallback-attempt.ts`, `agent-tools.ts`, `agent-tools.before-tool-call.ts`, `agent-tools.before-tool-call.policy.ts`, `tool-policy.ts`, `tool-policy-pipeline.ts`, `sandbox-tool-policy.ts`, `compaction.ts`, `context-window-guard.ts`, `tool-loop-detection.ts`, `system-prompt.ts`, `trace-base.ts`, `sessions/telemetry.ts`, `embedded-agent-runner/run/attempt-trajectory.ts`, `tools/common.ts`, `sessions/session-manager.ts`, `sessions/session-manager-branching.ts`, and `src/tools/types.ts`.
- Tasks/sessions/memory: `src/tasks/task-registry.types.ts`, `task-registry-state.ts`, `task-registry.store.sqlite.ts`, `task-registry.maintenance.ts`, `task-registry.reconcile.ts`, `task-executor.ts`; `src/sessions/session-key-utils.ts`, `session-state-events.ts`, `session-lifecycle-admission.ts`, `user-turn-transcript.ts`; `src/memory/root-memory-files.ts`.
- MCP: `src/mcp/openclaw-tools-serve.ts`, `plugin-tools-serve.ts`, `channel-server.ts`, `channel-bridge.ts`, `plugin-tools-handlers.ts`, `tools-stdio-server.ts`.
- Skills/routing: `src/skills/loading/skill-contract.ts`, `frontmatter.ts`, `local-loader.ts`, `session.ts`, `runtime/refresh-state.ts`, `runtime/tool-dispatch.ts`, `security/scanner.ts`, `security/workspace-audit.ts`, `lifecycle/install.ts`, `workshop/policy.ts`, `workshop/apply-transition.ts`, `workshop/store.ts`; `src/routing/resolve-route.ts`, `session-key.ts`, `bindings.ts`, `binding-scope.ts`, `conversation-ref.ts`.
- Security/system agent: `src/security/audit.ts`, `audit.types.ts`, `audit-gateway-config.ts`, `audit-deep-code-safety.ts`, `audit-plugins-trust.ts`, `external-content.ts`, `exec-filesystem-policy.ts`, `install-policy.ts`, `secret-mask.ts`, `secret-equal.ts`, `safe-regex.ts`, `context-visibility.ts`; `src/system-agent/system-agent.ts`, `chat-engine.ts`, `operations-execute.ts`, `config-write-policy.ts`, `operator-approval.ts`, `post-write-verification.ts`, `inference-route.ts`, `inference-fallback.ts`, `transcript-store.ts`, `audit.ts`.
- Context/cron: `src/context-engine/types.ts`, `legacy.ts`, `registry.ts`, `host-compat.ts`, `quarantine-health.ts`; `src/cron/types.ts`, `store.ts`, `service.ts`, `service/state.ts`, `service/run-admission.ts`, `service/timer-catchup.ts`, `service/startup-run-repair.ts`, `service/task-ledger.ts`, `schedule.ts`, `isolated-agent/run-fallback-policy.ts`.
- Gateway: `src/gateway/server.ts`, `server-start.ts`, `server-lifecycle.ts`, `server-methods.ts`, `server-methods-list.ts`, `method-scopes.ts`, `methods/registry.ts`, `server/ws-connection/authenticated-request-dispatch.ts`, `connect-admission.ts`, `handshake-auth-helpers.ts`, `connect-device-pairing.ts`, `node-registry.ts`, `node-registry.invoke-stream.ts`, `node-invoke-sanitize.ts`, `server-chat.ts`, `server-methods/agent.ts`, `chat.ts`, `nodes.ts`, `approval.ts`, `cron.ts`, `tasks.ts`, `sessions.ts`, `memory-search.ts`, `chat-restart-recovery.ts`.
- Node/provider runtime: `src/node-host/runtime.ts`, `client.ts`, `runner.ts`, `invoke.ts`, `invoke-system-run.ts`, `invoke-system-run-plan.ts`, `exec-policy.ts`, `plugin-node-host.ts`, `mcp.ts`, `skills.ts`; `src/provider-runtime/operation-retry.ts`, `operation-retry.test.ts`.
