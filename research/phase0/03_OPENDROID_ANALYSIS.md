# OpenDroid implementation analysis

## Audit identity and conclusion

| Item | Exact local checkout |
|---|---|
| Repository | `Eco_reference/opendroid` |
| Remote | `https://github.com/yashab-cyber/opendroid` |
| Branch | `main` |
| Commit | `9e3ef380eb0084d77054c9a48c42d08654b3ae4b` |
| Latest local commit date | `2026-08-01T14:24:19Z` |
| Repository license | Apache License 2.0 (`Eco_reference/opendroid/LICENSE`) |
| Android baseline | compile/target 35, min 26, Kotlin/JVM 21 |
| Audit method | Read-only static implementation audit of the checked-out commit; no README-only conclusions and no source changes |

Path convention: in this report, a citation beginning `.../` expands to `Eco_reference/opendroid/app/src/main/java/com/opendroid/ai/` unless the citation explicitly names another root such as `app/src/main/res` or `app/src/test`. Each implementation claim also names the relevant class/function where useful.

OpenDroid is the closest checked-out reference to a conventional, self-contained Android agent, and several small infrastructure components are credible adaptation candidates. It is **not** a durable task runtime: conversations, plan snapshots, and step results reach Room, but the active execution job, approval/input continuations, proposal ownership, and resume cursor live only in memory. No startup path finds and resumes an unfinished plan. Its broad cross-app autonomy also depends materially on an all-app `AccessibilityService` and on capabilities such as `MANAGE_EXTERNAL_STORAGE`, overlays, notification access, and package visibility that should not be foundational requirements of Kinetic's Play build.

The best foundation to borrow is therefore the Android plumbing around a new kernel—not `AgentLoop` wholesale. The strongest candidates are the provider wrapper's cancellation-aware retry behavior, the secret-store boundary, the model-download verification workflow, the typed action schema, the session-pinning/cancel race defenses, and Room migration tests.

## Architecture actually implemented

| Layer | Implementation evidence | Observed responsibility | Kinetic judgment |
|---|---|---|---|
| Application/lifecycle | `Eco_reference/opendroid/app/src/main/java/com/opendroid/ai/OpenDroidApp.kt` — `OpenDroidApp.onCreate`; `MainActivity.kt` — `MainActivity` | Hilt application, crash handler, preference migration, Compose host, service startup | Useful Android shell; it does not restore an agent run |
| Agent coordinator | `.../core/agent/AgentLoop.kt` — `AgentLoop`, `AgentState`, `processQuery`, `executePlanLoop` | Query serialization, routing, LLM calls, approval, execution, re-evaluation, response streaming | Feature-rich monolith; reference/partial adaptation only |
| Intent/planning | `.../core/agent/IntentClassifier.kt` — `requiresAction`, `classifyComplexity`; `PlanningPrompts.kt`; `PlanValidator.kt` | Keyword plus LLM routing, JSON plan production, handler validation and repair | Useful semantics, but brittle JSON and heuristic routing |
| Plan state | `.../data/models/Plan.kt` — `Plan`/`PlanStatus`; `PlanStep.kt` — `PlanStep`/`StepStatus`; `PlanManager.kt` | In-memory state flow plus Room snapshots, step dependency selection, edit/cancel | Good race guards; not a resumable state machine |
| Tool contract | `.../core/agent/ActionSchema.kt` — `ActionDefinition`/`ParamDefinition`/`ALL_ACTIONS` | Static typed input definitions, categories, defaults and `neverAutoApprove` | Adapt and extend with output, risk and execution metadata |
| Tool dispatch | `.../actions/ActionDispatcher.kt` — `execute`/`safeExecute`; `ActionAutoMapper.kt` | Normalize, network precheck, schema/defaults, alias repair, handler invocation | Do not retain silent fuzzy remapping as a security boundary |
| Android execution | `.../actions/SystemActions.kt`, `CommunicationActions.kt`, `CalendarActions.kt`, `AdvancedControlActions.kt` and other action groups | Android API, intent, content-provider and accessibility-backed actions | Split by Play-safe/restricted/advanced/system tier |
| Accessibility | `.../accessibility/OpenDroidAccessibilityService.kt` plus four automators | Node lookup, click/type/scroll/global actions, gestures, screenshots, overlay | Advanced-only under the project constraints |
| LLM/provider | `.../core/llm/LLMProvider.kt`, `LLMProviderFactory.kt`, `ProviderCatalog.kt` | Common cloud/on-device interface, active-provider selection, retry wrapper | Strong adaptation candidate |
| Local models | `.../core/llm/OnDeviceModelRegistry.kt`, `ModelDownloadWorker.kt`, `.../providers/LiteRTLMProvider.kt`, `GemmaProvider.kt` | Model descriptors, download/import/verification, AI Core and LiteRT-LM inference | Strong architectural reference; repair integrity/error contracts |
| Memory | `.../core/memory/MemoryManager.kt`, `MemoryExtractor.kt`, `WorkingMemory.kt` and Room repositories | Transcript, facts, macros/task history, notification context, RAM working state | Persistence pieces useful; retrieval/extraction need replacement |
| UI | `.../ui/viewmodel/ChatViewModel.kt`, `PlanViewModel.kt`, `SettingsViewModel.kt` | Exposes singleton state flows and manual plan/history controls | UI concepts only; durable task state must be repository-owned |

Abbreviated control path:

`Chat/voice -> AgentLoop.processQuery -> direct fast path or IntentClassifier -> chat completion OR plan generation -> PlanValidator -> approval policy -> PlanManager -> ActionDispatcher -> Android handler -> ReEvaluationEngine -> next step/final summary`

There is no MCP client/server or Android AppFunctions implementation in this checkout. There is also no app-owned NDK/JNI build; native inference arrives through Android dependencies (`com.google.ai.edge.litertlm` and ML Kit AI Core).

## Exact agent loop

### How a goal becomes actions

1. `AgentLoop.processQuery` cancels a prior top-level job, pins the current conversation session once, persists the user message, and serializes new work with `queryMutex`. A special path delivers a new message into a pending `CompletableDeferred` rather than replacing the task.
2. `processQueryLocked` attempts local alias/direct alarm/timer handling before an LLM call.
3. `IntentClassifier.requiresAction` first forces many action-like keywords, then asks the selected LLM for a device-action/chat classification, with another keyword fallback. `classifyComplexity` separates fast/simple work from complex planning.
4. Plain chat builds context from `MemoryManager.getRelevantContext` plus the last ten persisted messages and streams a provider response.
5. Action requests ask the LLM for plan JSON. When `multiAgentModeEnabled` is true, this is not a durable agent hierarchy: planner and critic requests run concurrently and a third LLM call merges them.
6. `PlanValidator` checks names against the dispatcher, performs contact resolution, repairs some hallucinated actions, and may turn missing information into `ASK_USER`.
7. `PlanManager.startNewPlan` changes the plan to `RUNNING` and saves it. `AutoApprovalPolicy` then either runs it or exposes `AgentState.PlanProposed`.
8. `executePlanLoop` repeatedly selects the first dependency-ready step, interpolates prior results into parameters, calls `ActionDispatcher.execute`, stores the result/task log, optionally invokes a fallback action, and asks `ReEvaluationEngine` whether to continue, modify, or abandon.
9. Unknown actions are persisted and sent to an LLM replan path. `NeedsInput` can pause for a user response, capped at five attempts. A final LLM summary is persisted and exposed as `AgentState.Speaking`.

Evidence: `Eco_reference/opendroid/app/src/main/java/com/opendroid/ai/core/agent/AgentLoop.kt` — `processQuery`, `processQueryLocked`, `fallbackOrError`, `approveProposedPlan`, `cancelCurrentTask`, `executePlanLoop`, `resolveNeedsInput`; `IntentClassifier.kt`; `PlanValidator.kt`; `ReEvaluationEngine.kt`.

### Plan representation and scheduler semantics

`Plan` contains an ID, goal, ordered `PlanStep` list, status and timestamps. Each step contains action, string parameters, dependencies, fallback, result/error and `canParallelize`. The executor nevertheless takes one `getNextExecutableStep` at a time; `canParallelize` is not consumed. Dependencies are considered satisfied when their steps are either `COMPLETED` **or `FAILED`**, so a dependent step may run after a failed prerequisite unless LLM re-evaluation changes the plan.

`PlanManager` uses a mutex around read-modify-write operations, mirrors the plan in `WorkingMemory.activePlan`, and saves every state mutation through `PlanRepository`. Its expected-plan-ID and terminal-state checks are useful defenses against a cancelled old job relabeling a replacement or completed plan. It supports manual edits only while steps are pending.

What is missing for Kinetic is a durable run/action ledger with:

- an explicit resume policy and checkpoint cursor;
- action attempt IDs and idempotency keys;
- leases/ownership so only one worker resumes a run;
- persisted approvals and waiting-input records;
- post-crash classification of an action as never-started, in-flight/unknown, committed, retryable, or compensatable;
- concurrency/side-effect metadata actually enforced by the scheduler.

Evidence: `.../data/models/Plan.kt` — `PlanStatus`; `PlanStep.kt` — `canParallelize`/`StepStatus`; `.../core/agent/PlanManager.kt` — `startNewPlan`, `loadPlan`, `cancelPlan`, `getNextExecutableStep`, `areDependenciesMet`.

### UI state

`AgentState` is a sealed UI-facing state: `Idle`, `Listening`, `Thinking`, `PlanProposed`, `ExecutingPlan`, `Speaking` and `Error`. `ChatViewModel` collects the singleton loop/repository flows. `PlanViewModel.loadPlan` can explicitly load a saved plan for display/editing, but no `SavedStateHandle` or process-start bootstrap reconnects UI state to a live run.

This is a useful distinction for Kinetic: presentation state can remain a projection, while durable task state must live below the ViewModel and be recoverable without the original coroutine.

## Execution control, approval and failure behavior

| Behavior | Exact implementation | Finding |
|---|---|---|
| Query concurrency | `AgentLoop.queryMutex` and one volatile `currentJob` | Serializes top-level tasks and actively replaces the old one |
| Session ownership | `activeTaskSessionId` and `proposedPlanSessionId` | Strong defense against cross-session message/approval corruption |
| Approval modes | `AutoApprovalPolicy.shouldAutoApprove` | `OFF` never; `AUTO` only if all primary/fallback actions are granted and none is `neverAutoApprove`; `YOLO` always |
| Dangerous actions | `ActionSchema.isNeverAutoApprove` | Effective in `AUTO`, explicitly bypassed by `YOLO` |
| Plan re-evaluation | `ReEvaluationEngine.evaluateAfterStep` | LLM decides continue/modify/abandon after each nonterminal step; advisory rather than deterministic policy |
| Primary fallback | `AgentLoop.executePlanLoop` | One named fallback action is attempted after primary failure |
| Provider retry | `WrappedLLMProvider` in `LLMProviderFactory.kt` | Up to three attempts, bounded time, exponential delay/jitter; cancellation is rethrown; a stream retries only before content emission |
| Provider failover | `LLMProviderFactory.getProvider` | No general cloud-provider failover; only `HybridOnDeviceProvider` switches between its two local backends |
| Action exception | `ActionDispatcher.safeExecute` | Generic `Exception` becomes `ActionResult.Failure` |
| Cancellation hazard | Same `safeExecute` | It does not rethrow `CancellationException`, so a handler cancellation can be converted to an ordinary failure; a later loop checkpoint may stop, but the boundary is incorrect |
| Hallucinated action | `ActionAutoMapper.mapAction` and dispatcher `SKIP` path | Many “security/check/confirm” hallucinations become successful skipped steps, which can erase a model-requested safety step |
| Dependency failure | `PlanManager.areDependenciesMet` | A failed dependency is treated as met |

The approval grant is an action-name allowlist. It does not scope a grant to recipient, data class, resource, time window, foreground state, network destination, or parameter constraints. Kinetic should approve a concrete capability invocation—not merely “`SEND_SMS` is allowed.”

## Action and Android API audit

The dispatcher combines handlers from system, communications, calendar/alarm, transport, information, media, food/shopping, finance, smart-home, macro, notification and advanced-control groups. This is real code, not only schemas.

Key implementation observations:

- `SystemActions` uses public settings panels/intents and APIs for app launch, volume, brightness, torch and global accessibility actions. Wi-Fi/mobile-data/hotspot operations sometimes degrade to settings panels or use reflection; they cannot be assumed to work as an ordinary modern Android app.
- `CommunicationActions` can directly place calls or send SMS when the host permission exists; otherwise it uses user-facing intents and, in some paths, accessibility automation. WhatsApp uses a deep link followed by `WhatsAppAutomator`.
- `CalendarActions` uses `ContentResolver`/insert intents for calendar and Android alarm/timer intents. The manifest declares `READ_CALENDAR` but not `WRITE_CALENDAR`, while the permission card also omits write-calendar, so direct writes are not consistently provisioned.
- `AdvancedControlActions` includes arbitrary file list/read/write/delete/copy/move/zip/unzip under broad storage access, Camera2 capture, installed-package discovery, and low-level accessibility click/type/scroll/coordinate actions. Canonical-path and Zip Slip checks are positive implementation details, but `MANAGE_EXTERNAL_STORAGE` is still the wrong default Kinetic file model. Prefer SAF/document grants.
- The central dispatcher checks a hard-coded set of network-required actions and schema inputs. There is no central policy for sensitive data, foreground-only execution, Play build availability, Android role, user-presence, network egress, or extension trust.

Evidence anchors: `Eco_reference/opendroid/app/src/main/java/com/opendroid/ai/actions/ActionDispatcher.kt` — `actionsMap`/`execute`; `SystemActions.kt`; `CommunicationActions.kt`; `CalendarActions.kt`; `AdvancedControlActions.kt`; `.../core/agent/ActionSchema.kt`.

## Accessibility and the autonomy dependency

`OpenDroidAccessibilityService` exposes static process access to the service, node-by-ID/text operations, `ACTION_SET_TEXT`, scrolling, global back/home/recents actions, coordinate gestures, screen text, an overlay, and API-30 screenshot capture encoded as JPEG/base64. Its `onAccessibilityEvent` is effectively empty; automation mostly polls the current tree.

`GenericAppAutomator` polls every 300 ms for up to five seconds. `WhatsAppAutomator`, `SmsAutomator` and `CallAutomator` depend on app resource IDs/text and fixed delays (roughly 0.5–3 seconds, with longer multi-stage flows). That makes success sensitive to app version, language, layout, device speed and confirmation dialogs.

Therefore:

- Some useful actions are independent of accessibility: alarms/timers, public intents, ContentResolver operations, flashlight, audio, app launch, approved SMS/call APIs, and app-owned UI.
- Broad arbitrary-app perception and reliable multi-screen click/type/send flows **do depend on accessibility**.
- Under Kinetic's Play-first constraint, this dependency must not sit in the core completion contract. Put general UI automation in an advanced distribution/runtime adapter and report it as unavailable in Play core.

Evidence: `.../accessibility/OpenDroidAccessibilityService.kt` — `takeScreenshotAndEncode`, `clickNodeByViewId`, `typeIntoFocusedField`, `performSwipe`; `GenericAppAutomator.kt`; `WhatsAppAutomator.kt`; `SmsAutomator.kt`; `CallAutomator.kt`; `.../res/xml/accessibility_service_config.xml` (all event types/windows, view IDs, gestures).

## Manifest, permissions and lifecycle

### Component topology

`Eco_reference/opendroid/app/src/main/AndroidManifest.xml` declares:

- exported launcher `MainActivity`;
- non-exported `OpenDroidService` as microphone + special-use foreground service;
- exported `OpenDroidAccessibilityService` protected by `BIND_ACCESSIBILITY_SERVICE`;
- exported `BootReceiver` for `BOOT_COMPLETED`;
- exported `OpenDroidNotificationListener` protected by `BIND_NOTIFICATION_LISTENER_SERVICE`;
- `allowBackup="true"` and `largeHeap="true"`.

`MainActivity` starts the foreground service only after microphone permission. `BootReceiver` also starts it at boot. `OpenDroidService.startForegroundCompat` knows about recent microphone-FGS restrictions and falls back to the special-use type, then returns `START_STICKY`. Sticky restart recreates voice/service machinery; it does not recover the task that was executing. Boot-started persistent microphone/wake behavior remains a platform/Play design risk and needs current policy validation.

`OpenDroidNotificationListener` persists notifications, classifies them, schedules auto-replies, attempts loop prevention and sends through `RemoteInput`. This is functional but places third-party notification content and autonomous reply behavior inside the trust boundary.

### Permission UX mismatch

The manifest requests Internet, microphone, contacts, calendar read, direct call/SMS, camera, foreground/background location, connectivity/Bluetooth control, write settings, notification policy, wake lock, foreground-service types, boot, overlay, notification access, legacy and all-files storage, usage access, package visibility and exact alarms.

`PermissionModel` provides cards for microphone, location, SMS/telephony, contacts/calendar, camera, notifications, storage, write-settings and accessibility. `PermissionsScreen` correctly uses activity-result contracts, `rememberSaveable` for a pending request, and rechecks special access on resume. But the modeled onboarding surface omits or under-models:

- notification-listener enablement;
- usage access;
- exact-alarm special access;
- overlay access;
- background-location sequencing;
- calendar write (also absent from the manifest);
- the policy reason and fallback for `QUERY_ALL_PACKAGES`;
- some Bluetooth and notification-policy controls.

The action catalog therefore promises more than onboarding can reliably provision.

### Process-death matrix

| State | Survives? | Evidence and consequence |
|---|---|---|
| Conversation/session messages | Yes | Room via `ConversationRepository`/`ConversationDao` |
| Plan/step snapshots | Yes | `PlanManager.persistPlan` -> `PlanRepository`/`PlanEntity` |
| Task history/unknown actions/models | Yes | Room entities/DAOs in `OpenDroidDatabase` |
| Settings | Yes | DataStore in `SettingsRepository` |
| API keys and selected sensitive facts | Yes, unless keystore invalidates | `SecurePrefs` uses Android Keystore + encrypted preferences |
| Current job and instruction pointer | **No** | `AgentLoop.currentJob` is a process coroutine; no persisted attempt/cursor |
| Pending plan approval | **No** | `AgentState.PlanProposed` and `proposedPlanSessionId` are memory-only |
| Waiting user input/contact selection | **No** | `PendingUserInput.CompletableDeferred` is memory-only |
| Active working memory/device state | **No** | `WorkingMemory.activePlan` and recent state are RAM |
| Automatic resume | **No** | `OpenDroidApp.onCreate` and `OpenDroidService.onCreate` never query unfinished plans; `PlanViewModel.loadPlan` is explicit UI loading only |

An app kill can consequently leave a database row labeled `RUNNING` with no owner and no deterministic answer about whether its last Android side effect happened. This is the principal architectural blocker for Kinetic.

Room migrations 1–7 exist and have tests, but `DatabaseModule.provideDatabase` also calls `fallbackToDestructiveMigration`. A missing future migration can therefore erase sessions, memory and plans rather than failing closed.

## LLM providers and local inference

### Provider selection and contracts

`LLMProvider` exposes `complete`, `streamComplete` and `isAvailable` and also implements the tool-generation-facing `AIProvider` contract. `SettingsRepository` persists a selected provider/model; `LLMProviderFactory.getProvider` canonicalizes it through `ProviderCatalog` and supplies the matching Hilt provider. Planning still relies primarily on prompt-generated JSON rather than provider-native structured tool calling.

The wrapped provider is a good reusable unit: it has typed error mapping, bounded retry, retry-after/jitter handling, preserves `CancellationException`, and refuses to replay a stream after text has been emitted. Its tests exercise those semantics. It retries the same provider; it is not a router across cloud providers.

### Local implementations

| Backend | Implementation | What is real | Gap |
|---|---|---|---|
| Android AI Core | `.../providers/GemmaProvider.kt` | ML Kit GenAI Prompt backend and availability checks | Device/OS/model availability dependent |
| LiteRT-LM | `.../providers/LiteRTLMProvider.kt` | Real `Engine` initialization, CPU backend, prompt budget and cached engine | Its local `downloadModel` API only writes placeholder metadata/simulated progress; actual downloads occur elsewhere |
| Hybrid | `.../providers/HybridOnDeviceProvider.kt` | Selects AI Core or LiteRT-LM and falls back to the other | Only local-backend fallback, not cloud/local policy routing |
| Download/import | `ModelRepository.kt` and `ModelDownloadWorker.kt` | WorkManager unique work, Range resume, temp/final files, expected size/hash when supplied, Zip Slip defense, SAF import and LiteRT engine compatibility check | Some registry entries have blank SHA/size, weakening integrity to size/parsing |

`OnDeviceModelRegistry` includes AI Core Gemma variants and LiteRT-LM Gemma/Qwen entries. Several descriptors leave `sha256` or `expectedSize` at defaults/TODO values; the Qwen descriptor is the strongest integrity case. Model provenance/license metadata is not part of the descriptor.

A concrete error-contract bug exists in `LiteRTLMProvider.streamComplete`: a caught failure is emitted as normal text beginning `Error (LiteRT-LM):`. The wrapper and hybrid provider cannot recognize that as a failure, so retry/fallback and typed UI errors can be bypassed. Kinetic's inference interface must distinguish data tokens from terminal typed errors.

OpenDroid itself does not justify new Kinetic NDK code. If LiteRT/AI Core meet the model requirements, their packaged native implementation should stay behind a Kotlin interface.

## Memory behavior

`MemoryManager.getRelevantContext(currentGoal)` does not use `currentGoal`. It loads all valid semantic memories that pass `MemoryExtractor.shouldStoreInSemanticMemory`, concatenates them, adds encrypted user name/DOB, RAM active-plan/device state, date/time and five recent notification summaries. Notification text is marked untrusted and simple tag text is stripped, which is a positive prompt-injection precaution, but it is still model-visible third-party content.

`MemoryExtractor` is a regex/heuristic extractor for fixed facts such as name, spouse, location, addresses and phone number. `searchMemory` loads all memories and does case-insensitive substring matching. There is no embedding/vector relevance pipeline, and there is no hard relevance/token limit in `getRelevantContext`. `summarizeOldConversations` stores only a truncated 200-character “summary” after 50 messages.

Kinetic should retain the separation between episodic/semantic/procedural/working concepts and Room repositories, but replace retrieval with bounded hybrid search, provenance, expiry, user visibility, sensitivity classes and prompt-injection-safe serialization.

Evidence: `.../core/memory/MemoryManager.kt` — `getRelevantContext`, `searchMemory`, `summarizeOldConversations`; `MemoryExtractor.kt` — `patterns`/`extractFacts`; `WorkingMemory.kt`; `SemanticMemory.kt`; `EpisodicMemory.kt`; `ProceduralMemory.kt`.

## Secrets, voice and privacy

- `SecurePrefs` uses `MasterKey.AES256_GCM` and `EncryptedSharedPreferences` with AES-SIV keys/AES-GCM values. `SettingsRepository` strips provider/ElevenLabs secrets from DataStore and overlays them from the secure store. It never silently falls back to plaintext. If the keystore/keyset is irrecoverable, it deletes the encrypted preference file and recreates it; Kinetic needs explicit credential-loss UX around that behavior.
- `SpeechRecognitionEngine` uses Android `SpeechRecognizer` and session IDs to discard stale callbacks.
- `WakeWordDetector` continuously restarts platform speech recognition after errors/results. This is not a guaranteed offline wake-word implementation: the selected Android recognizer may use a service/network, and a repeating recognizer carries battery/privacy/FGS implications.
- `TextToSpeechEngine` supports platform TTS and sends response text to ElevenLabs when configured, falling back locally. Its private `CoroutineScope(Dispatchers.IO)` has no job/supervisor cancellation in the service teardown path.
- `VoiceApprovalParser` gives rejection terms precedence over approval and cannot expand action grants, which is a sensible small safety primitive.

## Android/Play capability classification for this checkout

These are engineering categories for Kinetic architecture, not legal advice or a prediction of Play review.

| Capability in/near OpenDroid | Implementation evidence | Category | Kinetic Play dependency |
|---|---|---|---|
| AccessibilityService/general UI control | `OpenDroidAccessibilityService` and automators | `ADVANCED_SIDELOAD_ONLY` under the stated project constraint | No |
| MediaProjection | No declaration/implementation found | `NOT_IMPLEMENTED` | Use only explicit user-mediated capture if later added |
| NotificationListenerService/reply | `OpenDroidNotificationListener` | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Optional adapter, never kernel requirement |
| VoiceInteractionService | Not implemented | `NOT_IMPLEMENTED` | Do not claim assistant-role integration |
| Microphone/special-use FGS | `OpenDroidService` | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Narrow, user-visible voice mode only |
| WorkManager | Model downloads only | `PLAY_SAFE_OR_LIKELY` | Yes for bounded durable work; extend for task resumption |
| AlarmManager/exact alarms | Calendar/system actions + manifest | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Prefer inexact/work paths unless user-facing exact need qualifies |
| Wake lock | Declared | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Avoid as a task-durability substitute |
| Battery-optimization exemption | No dedicated onboarding flow found | `NOT_IMPLEMENTED/AVOID_DEFAULT` | No |
| Contacts/calendar | ContentResolver/actions | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Feature-gated, least privilege; fix calendar-write mismatch |
| SMS/direct phone calls | `SmsManager`/call paths | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Feature/role/policy gated; user-mediated intent fallback |
| Call log | No call-log permission/implementation found | `NOT_IMPLEMENTED` | No |
| Location/camera/microphone | Action/voice/vision handlers | `PLAY_RESTRICTED_REQUIRES_REVIEW` | Foreground/explicit-purpose adapters |
| Clipboard | No central robust capability found | `UNKNOWN/NOT_FOUND` | Foreground only if added |
| Installed-app discovery | package actions + `QUERY_ALL_PACKAGES` | `ADVANCED_SIDELOAD_ONLY` as currently broad | Use scoped `<queries>`/explicit intents |
| File access | `AdvancedControlActions` raw paths | `ADVANCED_SIDELOAD_ONLY` as currently designed | Replace with app storage + SAF grants |
| Storage Access Framework | Used for local-model import, not general file tools | `PLAY_SAFE_OR_LIKELY` | Yes |
| `MANAGE_EXTERNAL_STORAGE` | Manifest + advanced file tools | `ADVANCED_SIDELOAD_ONLY` | No |
| `REQUEST_INSTALL_PACKAGES` | Not declared/implemented | `NOT_IMPLEMENTED` | No core dependency |
| `QUERY_ALL_PACKAGES` | Declared | `ADVANCED_SIDELOAD_ONLY` as currently broad | No |
| Overlay windows | service/accessibility floating UI + manifest | `ADVANCED_SIDELOAD_ONLY` | No core dependency |
| VPN/device admin/Shizuku/root | Not implemented | `NOT_IMPLEMENTED` | No |
| Dynamic dex/JAR/native skill loading | Not implemented | `NOT_IMPLEMENTED` | Reject for Play core |
| Interpreted JavaScript | Not implemented | `NOT_IMPLEMENTED` | Prefer declarative skills |
| Downloadable models | WorkManager/model registry | `PLAY_SAFE_OR_LIKELY` with integrity/license/disclosure controls | Yes |
| Downloadable declarative skills | Not implemented | `NOT_IMPLEMENTED` | Good future Play-safe direction |
| Privileged connectivity/power controls | reflection/settings fallbacks | `SYSTEM_PRIVILEGED_ONLY` when direct control truly requires privileges | Keep out of ordinary build |

There is no flavor/source-set separation in `app/build.gradle`. The single application combines Play-plausible features with advanced permissions. Kinetic should instead compile one core and inject capability providers selected by distribution/runtime profile; unavailable capabilities must remain explicitly unavailable rather than being emulated with silent accessibility or reflection fallback.

## Tests and benchmark value

The checkout contains 26 JVM test files and no Android instrumentation-test source set. Materially examined examples include:

- `.../core/llm/WrappedLLMProviderTest.kt`: retry limits, timeout behavior, stream replay boundary and cancellation;
- `.../core/agent/ActionSchemaTest.kt`, `AutoApprovalPolicyTest.kt` and `NeverAutoApproveTest.kt`: schema/policy invariants, including YOLO semantics;
- `.../data/db/OpenDroidDatabaseMigrationTest.kt`: Room migrations against exported schemas;
- `.../core/permissions/PermissionModelTest.kt`: permission card state;
- `.../core/voice/VoiceApprovalParserTest.kt`: reject precedence.

Strong assets to adapt are provider-failure tests, schema/policy invariants and Room migration fixtures. Important missing evaluations are:

1. end-to-end `AgentLoop -> PlanManager -> ActionDispatcher` execution;
2. cancellation while a handler is inside `safeExecute`;
3. process kill after side effect/before step commit and deterministic resume;
4. persisted approval/waiting-input recovery;
5. permission denial and “don't ask again” at the handler boundary;
6. local/cloud routing and typed LiteRT stream failure;
7. network loss during a multi-step task;
8. accessibility grounding across app/version/language changes;
9. screenshot-to-action grounding;
10. foreground-service restart, boot restrictions and battery/runtime impact.

## Reuse and provenance decisions

Repository-level Apache-2.0 permits adaptation subject to its terms, preservation of applicable notices, attribution, and compatible third-party dependencies. No repository `NOTICE` file was found. Sampled first-party Kotlin implementation files generally have no SPDX/file license header; `LicenseScreen.kt` contains UI copyright text, not a distinct source license. Model artifacts and dependencies require their own provenance review.

| Candidate | Evidence | License/reuse classification | Decision |
|---|---|---|---|
| Provider wrapper | `core/llm/LLMProviderFactory.kt` — `WrappedLLMProvider` | Apache-2.0 repo; dependency-neutral logic | `ADAPT` after preserving cancellation/error tests |
| Provider/model catalog abstractions | `LLMProvider.kt`, `ProviderCatalog.kt`, `OnDeviceModelRegistry.kt` | Apache-2.0 repo; model licenses separate | `ADAPT` schema, add provenance/integrity/device requirements |
| Model storage/download | `ModelStoragePaths.kt`, `ModelDownloadWorker.kt`, `ModelRepository.kt` | Apache-2.0 repo; network/model terms separate | `ADAPT` after requiring hashes/signatures and one authoritative loader |
| Secrets boundary | `SecurePrefs.kt`, `SettingsRepository.kt` | Apache-2.0; AndroidX security dependency | `ADAPT`, add key-loss/re-auth state |
| Plan mutation race guards | `PlanManager.kt` and `AgentLoop.cancelCurrentTask` | Apache-2.0 | `PORT_SEMANTICS` into a durable engine |
| Action/parameter data model | `ActionSchema.kt` | Apache-2.0 | `ADAPT`, add typed output, risk, data, network, foreground, idempotency and availability |
| Voice approval parser | `VoiceApprovalParser.kt` | Apache-2.0 | `REUSE/ADAPT` with localized/UI confirmation tests |
| Room migration testing | `OpenDroidDatabaseMigrationTest.kt`, `app/schemas` | Apache-2.0 | `ADAPT` |
| Whole `AgentLoop` | `core/agent/AgentLoop.kt` | Apache-2.0 but tightly coupled and nondurable | `REFERENCE_ONLY` |
| ActionAutoMapper's fuzzy/`SKIP` logic | `actions/ActionAutoMapper.kt` | Apache-2.0 | `REJECT` as an execution/safety mechanism |
| General accessibility automators | `accessibility/*Automator.kt` | Apache-2.0 but brittle/policy-sensitive | `REFERENCE_ONLY` for advanced adapter tests |
| Broad action bundle | `actions/*.kt` | Apache-2.0 plus Android/policy coupling | Audit per capability; do not copy wholesale |

Do not copy or redistribute any downloaded/bundled model merely because the application source is Apache-2.0. The model's license, source URL, checksum and redistribution terms must be recorded separately.

## Highest-value Kinetic conclusions

1. Use OpenDroid as an Android integration reference, not as the Kinetic kernel.
2. Build a persisted task/run/attempt state machine before adding broad actions. A Room plan snapshot is insufficient.
3. Keep Play-safe and advanced executors behind the same typed capability contract; never make AccessibilityService, all-files access or package-wide discovery a hidden fallback.
4. Adapt the provider retry/error boundary, SecurePrefs split, model download/import verification, schema tests and plan identity race guards.
5. Replace action-name grants with parameter-bound approval/capability scopes.
6. Replace regex/all-memory prompt injection with bounded hybrid retrieval, provenance and sensitivity policies.
7. Preserve typed failures end to end. Normal text such as `Error (LiteRT-LM)` must never masquerade as successful inference.
8. Use WorkManager/foreground execution only as scheduling/liveness mechanisms; the durable ledger must make every resumption safe and idempotent.

## Inspection accounting and evidence ledger

The repository-wide enumeration found **180** Kotlin/Java/XML/Gradle implementation/configuration files under `app/src/main` and **26** JVM test files. The deep audit materially inspected **101 implementation files**, plus **8 tests** and **7 build/license/config artifacts**. “Materially inspected” means a full-file read or targeted symbol/body inspection used to support a finding above; simple filename enumeration is excluded.

| Material implementation area | Count | Representative exact evidence |
|---|---:|---|
| Agent, planning and plan models | 16 | `core/agent/AgentLoop.kt`, `IntentClassifier.kt`, `PlanManager.kt`, `PlanValidator.kt`, `ReEvaluationEngine.kt`, `AutoApprovalPolicy.kt`, `ActionSchema.kt`, `data/models/Plan.kt`, `PlanStep.kt`, `StepResult.kt` |
| Action handlers and accessibility | 21 | `actions/ActionDispatcher.kt`, `ActionAutoMapper.kt`, `Action.kt`, `ActionResult.kt`, 12 action-group files, `accessibility/OpenDroidAccessibilityService.kt` and four automators |
| LLM/provider/local-model stack | 22 | `core/llm/LLMProvider.kt`, `LLMProviderFactory.kt`, `ProviderCatalog.kt`, `OnDeviceModelRegistry.kt`, `ModelDownloadWorker.kt`, `ModelStoragePaths.kt`, `PromptBudget.kt`, `providers/LiteRTLMProvider.kt`, `HybridOnDeviceProvider.kt`, `GemmaProvider.kt` and sampled cloud providers/errors |
| Memory, Room and repositories | 22 | `core/memory/MemoryManager.kt`, `MemoryExtractor.kt`, `WorkingMemory.kt`, three memory facades, `data/repository/*.kt`, `OpenDroidDatabase.kt`, plan/task/memory/conversation entities and DAOs |
| Lifecycle, services, permissions, security, voice, DI and ViewModels | 20 | `OpenDroidApp.kt`, `MainActivity.kt`, three service/receiver files, `PermissionModel.kt`, `SecurePrefs.kt`, four voice files, two DI modules, three ViewModels and permission UI |
| **Implementation subtotal** | **101** | |
| Material tests | 8 | Provider wrapper, schema, two approval tests, migration, permission, voice and action-result tests |
| Build/license/config | 7 | `AGENTS.md`, `LICENSE`, `settings.gradle`, root/app `build.gradle`, manifest and accessibility config |
| **All materially inspected artifacts** | **116** | |

All paths above are relative to `E:\Projects`. No file under `Eco_reference/opendroid` was modified.
