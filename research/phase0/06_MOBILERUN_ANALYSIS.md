# MobileRun forensic analysis

## Audit identity and bottom line

- Reference root: `E:\Projects\Eco_reference\mobilerun-main`
- The directory has **no `.git` metadata**. Branch, commit, remote, commit time, submodule state, and dirty-tree status cannot be established from this artifact.
- `pyproject.toml` advertises `https://github.com/droidrun/mobilerun` as the homepage, package `mobilerun` version `0.6.17`, Python `>=3.11,<3.14`. That URL is package metadata, not proof of this directory's exact revision.
- Audit mode: static/read-only. No device, emulator, ADB/Portal service, cloud backend, provider, telemetry collector, or MCP subprocess was started.

MobileRun is a useful algorithm and data-contract reference for Kinetic's mobile UI agent. It is **not** an Android-native implementation: the checked-in Python orchestration controls Android/iOS through desktop-side drivers, and the decisive driver/Portal implementation lives in external packages (`mobilerun-core-local[cloud]>=0.6.0` and `mobilerun-sdk>=3.2.0`). The best reuse targets are its two planning modes, UI snapshot/coordinate contract, typed action/result seam, guarded trajectory/macro replay, retry logic, and app-specific guidance. A Kotlin port must replace ADB/Portal, Python workflows, subprocess MCP, and permissive prompt policy with Android-native, user-consented capabilities.

Recommended disposition:

| Area | Disposition | Kinetic use |
|---|---|---|
| Direct/ReAct versus manager-executor strategies | **ADAPT_ALGORITHM** | Two `PlannerStrategy` implementations over one action/checkpoint core. |
| UI snapshot, element index, coordinate transform | **ADAPT_ALGORITHM** | Immutable Kotlin snapshots with snapshot IDs and explicit coordinate spaces. |
| Action vocabulary and structured outcomes | **ADAPT_ALGORITHM** | Sealed `UiAction` and policy-aware `ActionOutcome`. |
| Trajectory and guarded macro replay | **ADAPT_ALGORITHM** | Durable event log plus semantic preconditions, stable targets, and divergence handoff. |
| App cards | **ADAPT_ALGORITHM** | Signed/versioned app guides with prompt-injection handling. |
| Python/ADB/Portal/desktop control plane | **NOT_RELEVANT** as Kinetic runtime | Reimplement via app-owned or accessibility-mediated Android capabilities. |
| MCP stdio subprocess integration | **RESEARCH_ONLY** | Protocol concepts only; Android core needs a brokered, permission-aware transport. |
| Ollama support | **RESEARCH_ONLY** for local inference | It is localhost HTTP, not in-process Android inference. |

## Integrity, license, dependency, and test posture

`LICENSE` is MIT, copyright 2025 Niels Schmidt; `pyproject.toml` declares MIT. A scan of 172 Python files found no SPDX/copyright source header. The root license provides repository-level terms, but snippet-level provenance is weak and the missing Git history prevents attribution validation for this artifact. Conceptual reimplementation with provenance notes is safer than source transplant.

The absence of `.git` is material. It prevents checking tag/version correspondence, whether files are tracked, whether generated/vendor content was altered, and whether the archive contains all files from an upstream revision. The external core dependency creates a second completeness problem: calls such as `mobilerun_core_local.driver.android.AndroidDriver` are visible, but their accessibility/ADB protocol, security, and licensing are not auditable here.

Test inventory:

- `tests/` contains 37 files: 36 `test_*.py` modules plus `eval_coordinate_grounding.py`.
- Focused coverage exists for Android/iOS vision-coordinate contracts, vision sizing, malformed FastAgent tool markup, XML parsing, manager response validation, macro schema/recording/matching/replay, inference retries, provider/OAuth behavior, configuration permissions, core-local wiring, Portal assets, and visual remote connections.
- `agent-test-flows/README.md` is not a deterministic suite. It explicitly asks an AI agent to invent smoke/full device tasks, cover modes/platforms/providers/macros/cloud, independently verify screens, and capture screenshots. It says “there is no fixed script.” This is useful exploratory guidance but cannot serve as a release gate without frozen tasks, fixtures, assertions, device images, and expected results.

Tests were inspected, not executed: imports depend on the missing external core/Portal packages and real device/provider environments, and running the flows would mutate devices and credentials.

## Architecture: direct mode versus manager/executor mode

`mobilerun/agent/droid/droid_agent.py::MobileAgent` is a `llama_index.core.workflow.Workflow` coordinator. `MobileAgent.execute_task` selects one of two paths:

### Direct / FastAgent path

When reasoning is disabled, `MobileAgent` invokes `mobilerun/agent/fast_agent/fast_agent.py::FastAgent`. It is a ReAct-like loop:

1. `_build_system_prompt` renders available actions and protocol rules.
2. `_build_user_prompt` includes the current goal, UI representation, screenshot, memory, and action history.
3. `handle_llm_input` calls the model with retry/timeout utilities.
4. `handle_llm_output` parses XML-like tool calls.
5. `execute_code` runs the selected actions through `ToolRegistry`.
6. `handle_execution_result` formats tool results back into the next model turn.
7. `finalize` produces success/reason/tool-call count.

This mode lets the same model reason and act every step. It has lower orchestration overhead but a larger prompt/protocol burden and no independent high-level replanning role.

### Manager/executor path

When reasoning is enabled, `MobileAgent.run_manager` and `MobileAgent.run_executor` alternate two workflows:

- `mobilerun/agent/manager/manager_agent.py::ManagerAgent` sees goal, current/previous state, screenshot, current application and app card, plan, memory, progress, action history, and errors. It produces exactly one unfinished `<plan>` or final `<request_accomplished success=...>`/`<answer>` outcome. `ManagerPlanDetailsEvent` carries `plan`, next `subgoal`, `thought`, `answer`, `memory_update`, `progress_summary`, and optional terminal success.
- `mobilerun/agent/executor/executor_agent.py::ExecutorAgent` receives the overall plan, one subgoal, UI/app-card/screenshot context, and action history. It returns one JSON action plus thought/description (`ExecutorActionEvent`), dispatches it, sleeps for UI settling, and reports `ExecutorActionResultEvent`.

`ManagerAgent._validate_and_retry` validates structured output with up to three correction attempts. `mobilerun/agent/manager/prompts.py` uses a stack-aware parser so nested text cannot masquerade as the one top-level plan/final tag. The coordinator feeds failures back to the manager; repeated executor errors set plan-error context so the next manager step can revise rather than repeat. A max-step boundary ends runaway work.

`StatelessManagerAgent` re-sends the relevant plan/state/history explicitly rather than relying on a long manager chat. This is closer to a checkpointable mobile design, although the current state remains in process.

### Kinetic extraction

Use one durable `TaskStateMachine` with interchangeable planners:

```text
PlannerStrategy
  DirectPlanner     -> thought + zero/one/many typed action requests
  HierarchicalPlanner
    ManagerPlanner  -> PlanRevision | TaskCompletion
    ExecutorPlanner -> exactly one atomic UiAction
```

Both must consume the same `UiSnapshot`, `ActionHistory`, `TaskBudget`, `PolicyContext`, and `TaskCheckpoint`. The planner must not own device access. This allows evaluation of direct versus hierarchical planning without duplicating execution/security logic.

## UI state, element identifiers, screenshots, and grounding

### State acquisition

`mobilerun/tools/ui/provider.py::StateProvider` abstracts state. Implementations include:

- `AndroidStateProvider`, which calls the external driver/Portal UI tree and formats it with a filter/formatter;
- `IOSStateProvider` in `ios_provider.py`;
- `ScreenshotOnlyStateProvider`, which returns screenshot/dimensions without an accessibility tree.

`fetch_state_with_retry` makes up to seven attempts with increasing delays (1, 2, 3, 5, 8, and 10-second style backoff across attempts) and invokes Portal recovery after repeated Android failures. `AndroidStateProvider._recover_portal` uses ADB secure-setting/content-provider mechanisms to re-enable/reconnect the Portal accessibility service. That is suitable for a developer-side test harness, not a Play-safe in-app recovery policy: a consumer app cannot depend on ADB changing secure settings.

The driver types imported from `mobilerun_core_local` are outside the archive, so the exact UI-tree source, accessibility-node identity, wire protocol, and OS-version handling cannot be verified here.

### `UIState` contract

`mobilerun/tools/ui/state.py::UIState` holds:

- raw/filtered element tree and formatted text shown to the model;
- screenshot bytes;
- focused text and phone state;
- physical screen dimensions;
- normalization/scaling flags and model-image dimensions.

`get_element(index)` resolves a formatter-assigned index. `get_element_coords` computes the indexed element's target point; `get_clear_point` searches for a safe, non-overlapped click point where possible. `convert_point` maps model/state coordinates to device coordinates.

The indexes are **presentation/snapshot-local**, not durable accessibility IDs. They can change whenever the UI tree, filtering, ordering, or formatter changes. Current actions pass a bare integer without a snapshot/version binding, so a delayed or replayed action can target the wrong node if the screen changed between planning and dispatch.

Kinetic should use:

```text
UiSnapshot(
  id, capturedAt, appPackage, activity, windowId,
  physicalViewport, modelViewport, screenshotRef,
  nodes: List<UiNode>
)

UiNode(
  localId, accessibilityNodeRef?, resourceId?, className,
  text?, contentDescription?, bounds, flags, parentLocalId?
)

ElementTarget(snapshotId, localId, semanticFallback, expectedBounds)
```

Execution rejects stale `snapshotId` by default and may re-resolve only under an explicit semantic policy.

### Screenshot-only coordinate contract

`ScreenshotOnlyStateProvider.get_state` deterministically resizes the screenshot and adds a coordinate grid for the model. `mobilerun/tools/ui/provider.py::resize_model_screenshot_with_grid` and `mobilerun/agent/utils/actions.py::_validate_screenshot_only_point` enforce that model coordinates are interpreted in the displayed image, then converted to the device viewport. Out-of-bounds and absent-contract calls fail before tapping.

In accessibility-tree mode, coordinate actions share the coordinate space of formatted element bounds. In screenshot-only mode, index actions are unavailable and tool descriptions explicitly say to use screenshot pixels—not grid cell numbers. `test_android_vision_coordinate_contract.py`, `test_ios_vision_coordinate_contract.py`, `test_vision_sizing.py`, and `eval_coordinate_grounding.py` treat this as a first-class contract.

This is directly useful. Kinetic should name coordinate spaces in the type system (`PhysicalPx`, `ScreenshotPx`, `NormalizedUnit`) and persist the exact affine transform with each screenshot. Raw `Int x, Int y` should never cross subsystem boundaries without a viewport ID.

## Action vocabulary and execution contracts

`mobilerun/agent/utils/signatures.py::build_tool_registry` registers the standard vocabulary:

- indexed actions: `click`, `long_press`, `type` (optional index, otherwise focused field);
- coordinate actions: `click_at`, `click_area`, `long_press_at`, `swipe`;
- direct text: `type_text`;
- device/app: `system_button`, `open_app` (friendly Android name or exact package/bundle depending backend);
- timing/control: `wait`, `complete`;
- conditional secret entry: `type_secret` when a credential manager exposes IDs.

The functions in `mobilerun/agent/utils/actions.py` take `ActionContext`, which composes driver, refreshed `UIState`, shared agent state, state provider, optional app-opener model, credential manager, streaming flag, and macro recorder. They return `mobilerun/agent/action_result.py::ActionResult(success, summary)`.

`ToolRegistry.register` stores function, simple parameter metadata, dependency/capability names, and enabled state. `disable_unsupported` filters tools against driver capabilities. `ToolRegistry.execute` refreshes context and emits lifecycle events. Custom and MCP tools share this surface.

Strengths:

- actions are already separated from planning;
- capability filtering prevents advertising functions a driver lacks;
- secrets are resolved by ID and typed without exposing the value to the model;
- coordinate preconditions are checked before device calls;
- action-level macro recording is integrated at the executor boundary.

Gaps:

- parameter metadata is a shallow dictionary rather than full JSON Schema;
- `get_param_types` flattens types by **parameter name across tools**, so the same parameter name with different types can be coerced incorrectly by the FastAgent XML parser;
- a dependency name is not a security policy. There is no required risk class, data scope, Android permission, user-confirmation rule, foreground-service constraint, or idempotency declaration;
- `ActionResult` only has boolean + text, losing typed error, retryability, target, before/after snapshot, side-effect record, and artifacts;
- several prompts authorize behavior that policy must forbid (below).

Kinetic should define sealed actions such as `Tap(ElementTarget)`, `TapPoint(ViewportPoint)`, `SetText(FieldTarget, SecretOrLiteral)`, `PressSystemButton`, `LaunchApp`, `Swipe`, `WaitForCondition`, and `CompleteTask`. Every action carries risk/effect metadata and yields a structured, auditable outcome.

## Prompt and policy audit

Prompt variants live under `mobilerun/config/prompts/manager`, `executor`, and `fast_agent`; `prompt_loader` and the manager/executor prompt modules select/render them.

Positive properties:

- manager output has a strict one-plan-or-one-final grammar;
- the next subgoal is always first, making executor scope explicit;
- UI, screenshot, app card, prior plan, memory, progress, and errors are delimited;
- FastAgent has a clear tool markup protocol and explicit `complete` semantics;
- executor is instructed to choose one atomic action.

Policy problems that must **not** be inherited:

- `config/prompts/manager/system.jinja2` tells the manager that when clarification is required it should make assumptions and “act as though you are the user.” `manager/rev1.jinja2` similarly says “act as the user would.” This is unsafe for consequential actions and directly conflicts with explicit-consent requirements.
- `config/prompts/executor/system.jinja2` tells the executor to close permission popups, including by clicking either “Don't Allow” or “Accept & continue,” before proceeding. A model must never grant an Android permission or consent prompt on a user's behalf. Even denial/closing needs task-aware policy because it may change app state.
- App cards and UI text are injected into model prompts without a cryptographic trust boundary. Delimiters are not sufficient to stop prompt injection.

Kinetic policy must execute outside the LLM:

- permission/consent/account/login/payment/destructive/share/send actions require a deterministic gate;
- the planner returns `NeedsUserDecision` instead of guessing;
- the executor cannot click system permission dialogs unless a narrowly scoped, freshly confirmed `ApprovalGrant` matches the exact permission and app;
- all external guidance/UI-derived text is tagged as untrusted data.

## FastAgent protocol and repair

`mobilerun/agent/fast_agent/xml_parser.py` defines `ToolCall`, `ToolCallParseStatus` (`VALID`, `NO_MARKUP`, `MALFORMED`), `ToolCallParseResult`, and `ToolResult`.

`parse_tool_calls_detailed` parses `<function_calls><invoke name=...><parameter name=...>` blocks, escapes structural markup inside parameter payloads, removes adjacent duplicate blocks, and coerces values using registry types. `format_tool_results` returns structured XML-like results to the next model call. Memory additions can be extracted from `<add_memory>`.

`FastAgent` distinguishes no markup from malformed markup. It asks for a corrected call when appropriate, stops after three malformed attempts, corrects a nonterminal response with no tool call, and executes multiple valid calls sequentially. External user messages can be queued into shared state, and tool results become the next observation.

This is a good bounded parser-repair example. Kinetic should prefer native provider tool calling or validated JSON Schema. If a text protocol is required, parse into a sealed AST, bind each call to the current snapshot, and never infer a parameter type globally by name.

## Trajectory recording, persistence, and replay

### Recording

`mobilerun/agent/trajectory/writer.py::TrajectoryWriter` writes staged/final trajectory artifacts through `WriterWorker`, an async FIFO with default capacity 300. Artifacts include event JSON, macro JSON, numbered screenshots, UI-state JSON, and GIFs. Submission returns false when the queue is full, so evidence can be dropped under load. `stop(timeout=30)` bounds draining.

`mobilerun/agent/utils/trajectory.py::Trajectory` loads/summarizes folders and macro files. The active `MobileAgentState` and LlamaIndex workflow state are in memory; the inspected orchestration does not persist a resumable task checkpoint at each planner/action boundary. Trajectory output is evidence after/beside execution, not a restart protocol.

Kinetic should make the event log the checkpoint source, write critical state synchronously/transactionally, store large screenshots separately by content hash, and distinguish optional visual artifacts from non-droppable action events.

### Macro schema and semantic guard

`mobilerun/macro/recorder.py::MacroRecorder` records schema-v2 action entries with `pre_state`, screen geometry, timestamp, elapsed time, and optional `post_state`. `mobilerun/macro/state.py` normalizes:

- phone package/activity;
- screen width/height;
- nodes containing resource ID, class, text, content description, clickable/enabled/focused flags, and bounds.

`node_semantic_key` intentionally forms a semantic identity; the current matcher does not include bounds in that key. `mobilerun/macro/matcher.py::compare_states` computes Jaccard overlap of node-key sets (85%) plus package/activity match (15%), then compares to a default 0.85 threshold.

`mobilerun/macro/replay.py::MacroPlayer` polls for each saved `pre_state` before executing. On timeout/divergence it either stops or invokes `mobilerun/macro/handoff.py::run_agent_handoff`; actions otherwise replay through the external Android driver. This is far safer than blind coordinate playback.

Limitations:

- action targets are still mostly raw coordinates from the original device/layout;
- matcher node sets lose multiplicity and bounds, so repeated identical labels and layout shifts can score misleadingly;
- fixed Jaccard weights/threshold are not calibrated per app/action;
- text in snapshots may contain sensitive data;
- the replay path is Android/external-driver specific;
- handoff can turn a deterministic macro into a broad agent run without a distinct approval/risk transition.

Kinetic should store `SemanticTarget` plus normalized bounds and selector alternatives, compare window/app/version/orientation, weight target-critical nodes separately, redact sensitive text, and treat divergence handoff as a new policy decision. Macros should be versioned, signed/user-owned, and previewable.

## App cards

`mobilerun/app_cards/app_card_provider.py::AppCardProvider.load_app_card(package_name, instruction)` is implemented by:

- `LocalAppCardProvider`: package-to-Markdown mapping from `config/app_cards/app_cards.json`, with cached file content;
- `ServerAppCardProvider`: remote lookup keyed by package/instruction with timeout/retries/cache;
- `CompositeAppCardProvider`: remote-first, local fallback.

`config/app_cards/gmail.md` is the representative instruction card. Manager/executor prompts insert card text as operational guidance for the current package.

The idea is valuable: app-specific operating knowledge should be loaded only when the app is active. The current artifact lacks signed publisher provenance, compatible app-version ranges, capability/permission requirements, schema, and prompt-injection treatment. Remote content can change behavior without an app update.

Kinetic's `AppGuide` should contain package certificate digest, app/version range, guide version/hash/signer, typed workflows/selectors, allowed capabilities, risk notes, localization, expiry, and test fixtures. Untrusted remote Markdown should never directly become authoritative system instructions.

## MCP integration

`mobilerun/mcp/config.py::MCPServerConfig` contains `command`, `args`, `env`, tool-name prefix, enabled flag, and include/exclude tool filters. `MCPClientManager` launches configured stdio servers through the Python MCP client, uses a temporary session for discovery, caches tool metadata, and lazily maintains a persistent session for calls. Server/tool names are prefixed to prevent collisions.

`mobilerun/mcp/adapter.py::schema_to_parameters` maps only top-level JSON Schema `properties`, `required`, type/default/description into the shallow MobileRun registry. `_create_tool_wrapper` calls MCP and concatenates text content; non-text structured/media blocks are not preserved as typed outcomes.

Security/portability implications:

- configured MCP servers are arbitrary local subprocesses;
- there is no manifest-level risk/effect/permission/host/user-confirmation/cancellation model;
- nested/union/constraint-rich schemas lose fidelity;
- media/structured result content loses fidelity;
- stdio child-process assumptions do not map to a Play-distributed Android app.

Kinetic may support MCP only through an explicit broker/companion or a tightly constrained transport. Discovered tools must be normalized into the same `CapabilityDescriptor` and pass mobile policy before they are advertised or invoked.

## Provider abstraction, retries, credentials, and local inference

### Providers

`mobilerun/agent/providers/registry.py` defines provider families/variants and includes OpenAI, OpenAI-like, Google GenAI, Ollama, Anthropic, OpenRouter, xAI/Grok, DeepSeek, MiniMax, ZAI, and OAuth variants. `agent/utils/llm_loader.py` resolves the profiles required by the chosen mode: manager/executor/app-opener for hierarchical mode or fast-agent/app-opener for direct mode. This role-to-profile split allows different models by role.

`agent/utils/inference.py` wraps chat, completion, and structured prediction. Each attempt has a default 500-second timeout; retry delay grows linearly with attempt. Permanent HTTP 4xx errors stop retries except transient semantics such as 408, 409, 425, and 429. Streaming has a whole-stream timeout.

Kinetic should retain per-role routing and structured retry classification, but 500 seconds is unsuitable as a foreground mobile default. Checkpoint-aware background work may have a larger deadline; UI turns need shorter connect/first-token/idle/whole-call deadlines and cancellation tied to lifecycle/network changes.

### Credentials

There are two relevant stores:

- `credential_manager/file_credential_manager.py::FileCredentialManager` reads secrets from a dictionary or YAML file and exposes IDs/values to action implementations. It does not itself encrypt data or enforce file mode.
- `config_manager/auth_profile_store.py::AuthProfileStore` stores API keys/OAuth profiles in one JSON object, serializes cross-process updates with `FileLock`, writes a temporary file, fsyncs, atomically replaces, and applies mode `0600` on platforms that support it. `credential_paths.py` places the file under the user's platform config directory.

This is sound desktop file hygiene but not Android secret storage. Kinetic should use Android Keystore-backed encryption, avoid model visibility of values, bind grants to capability/app/field, redact logs/trajectories, and handle token expiry/revocation explicitly.

### Local inference

The only local model path found is the LlamaIndex Ollama adapter. The registry defaults it to `http://localhost:11434`; setup caps context (default profile hint 32,768) and maps common settings to Ollama options. There is no bundled model format, JNI/native engine, LiteRT/NNAPI runtime, model download/verifier, Android memory/thermal manager, or offline tokenizer.

Therefore MobileRun demonstrates that planners can target a local HTTP provider, not that the agent runs an LLM in-process on Android. Kinetic's local engine must implement a separate `LocalModelEngine` abstraction and meet storage/RAM/latency/battery constraints.

## Telemetry and privacy

The package depends on PostHog and includes Phoenix/OpenTelemetry and optional Langfuse integration (`mobilerun/telemetry`, `agent/utils/tracing_setup.py`). Tracing paths can include prompts/tool events and may attach screenshots as encoded content when enabled. UI screenshots, accessibility text, credentials IDs, and task goals can all be sensitive.

Kinetic telemetry should default off for content, use explicit consent, separate operational metrics from payloads, redact on device, prohibit secrets/screenshots/accessibility trees by default, enforce retention/export/delete controls, and make every remote endpoint visible to the user.

## Android and Play portability assessment

| Reference behavior | Why it does not directly port | Required Kinetic replacement |
|---|---|---|
| Python 3.11 + LlamaIndex workflows | No standard Android app runtime; large dependency graph | Kotlin/coroutines state machine with Room checkpoints. |
| ADB/Portal driver and secure-setting recovery | Desktop/dev authority; not available to ordinary Play apps | User-enabled accessibility service or app-owned APIs, with visible consent and policy limits. |
| External `mobilerun-core-local` | Implementation and license absent from artifact | Independently specified Android capability interfaces. |
| Desktop subprocess MCP | Arbitrary code execution | Brokered/signed capabilities or no MCP in consumer core. |
| Prompt-driven permission popup handling | Violates consent boundary | Deterministic system-dialog detector + approval gate. |
| In-memory workflow state | Process death loses task | Persistent state transitions and idempotent action outbox. |
| Screenshot/accessibility telemetry | High privacy risk | Local redaction and opt-in content telemetry. |
| Ollama localhost | Assumes separate server | Native local engine or explicit companion app. |

## Proposed Kinetic data contracts

```text
TaskCheckpoint
  id, goal, strategy, state, planRevision, currentSubgoal,
  snapshotId, actionHistory, pendingAction, budgets,
  providerState, policyState, createdAt, updatedAt

UiSnapshot
  id, appIdentity, windowIdentity, orientation,
  physicalViewport, modelViewport, transform,
  nodes, screenshotRef, capturedAt

UiAction (sealed)
  TapElement | TapPoint | LongPress | SetText | TypeSecret |
  Swipe | PressButton | LaunchApp | WaitFor | Complete

ActionRequest
  id, taskId, sourceSnapshotId, action, expectedPrecondition,
  risk, requiredPermissions, confirmationPolicy, idempotencyKey

ActionOutcome
  status, typedError, retryability, beforeSnapshotId,
  afterSnapshotId, sideEffectReceipt, summary, artifacts

TrajectoryEvent
  monotonic sequence, task/action IDs, state transition,
  redacted payload, hashes, timestamps

AppGuide
  package/certificate/version constraints, version/hash/signer,
  declared capabilities, workflows/selectors, risk notes, expiry
```

## Synthesis-ready conclusions

1. MobileRun validates a two-strategy design: a direct ReAct loop and a hierarchical manager/executor can share one UI/action layer. Kinetic should make strategy replaceable and checkpointable.
2. Its strongest mobile-specific contribution is explicit UI/model/device coordinate handling. Kinetic should strengthen this with named coordinate types and snapshot-bound targets.
3. Formatter indexes are ephemeral. Never persist or execute a bare index without a snapshot ID and stale-state check.
4. The action vocabulary is a strong baseline, but capability availability is not security authorization. Add effects, permissions, confirmations, cancellation, and typed results.
5. The macro v2 pre-state guard and divergence handoff are worth adapting. Replace raw coordinate replay with semantic targets, better state comparison, and a new approval boundary on agent handoff.
6. Trajectory output is evidence, not a recovery checkpoint; its bounded writer may drop artifacts. Kinetic needs a non-droppable durable event log.
7. App cards are useful progressive app knowledge but need signed provenance, app-version compatibility, typed content, and prompt-injection defenses.
8. Current prompts improperly let the agent assume user intent and handle permission dialogs. Those behaviors must be prohibited by deterministic policy.
9. MCP and the device drivers assume a trusted desktop process. Neither should be embedded unchanged in a Play consumer app.
10. MobileRun's “local model” is Ollama over HTTP. It offers no native Android inference solution.
11. The missing `.git` history and external core package mean this archive cannot support a full provenance or end-to-end driver audit.

## Material implementation ledger

Material artifacts read or section-read: **72**. Repository-wide targeted searches and directory inventories are additional and not counted.

1. Root/meta and manual flow: `LICENSE`; `pyproject.toml`; `mobilerun/__init__.py`; `mobilerun/config_example.yaml`; `agent-test-flows/README.md`.
2. Coordinator/state: `mobilerun/agent/droid/droid_agent.py`; `mobilerun/agent/droid/state.py`; `mobilerun/agent/droid/events.py`; `mobilerun/agent/action_context.py`; `mobilerun/agent/action_result.py`.
3. Manager: `mobilerun/agent/manager/manager_agent.py`; `stateless_manager_agent.py`; `prompts.py`; `events.py`; `config/prompts/manager/system.jinja2`; `stateless.jinja2`; `rev1.jinja2`; `trained.jinja2`.
4. Executor: `mobilerun/agent/executor/executor_agent.py`; `prompts.py`; `events.py`; `config/prompts/executor/system.jinja2`; `rev1.jinja2`.
5. Direct agent/protocol: `mobilerun/agent/fast_agent/fast_agent.py`; `xml_parser.py`; `events.py`; `config/prompts/fast_agent/system.jinja2`; `user.jinja2`.
6. Actions/UI: `mobilerun/agent/tool_registry.py`; `agent/utils/actions.py`; `agent/utils/signatures.py`; `tools/ui/state.py`; `tools/ui/provider.py`; `tools/ui/screenshot_provider.py`; `tools/formatters/indexed_formatter.py`; `tools/helpers/coordinate.py`.
7. Trajectory/macro: `mobilerun/agent/trajectory/writer.py`; `agent/utils/trajectory.py`; `macro/state.py`; `macro/recorder.py`; `macro/matcher.py`; `macro/replay.py`; `macro/handoff.py`.
8. App cards: `mobilerun/app_cards/app_card_provider.py`; `app_cards/providers/local_provider.py`; `server_provider.py`; `composite_provider.py`; `config/app_cards/app_cards.json`; `gmail.md`; `README.md`.
9. MCP: `mobilerun/mcp/config.py`; `client.py`; `adapter.py`.
10. Providers/credentials/telemetry: `mobilerun/agent/utils/inference.py`; `llm_loader.py`; `llm_picker.py`; `agent/providers/registry.py`; `agent/providers/types.py`; `credential_manager/file_credential_manager.py`; `config_manager/credential_paths.py`; `config_manager/auth_profile_store.py`; `agent/utils/tracing_setup.py`.
11. Focused tests: `tests/test_android_vision_coordinate_contract.py`; `test_ios_vision_coordinate_contract.py`; `test_fast_agent_malformed_tool_guard.py`; `test_fast_agent_xml_parser.py`; `test_inference_retries.py`; `test_macro_guarded_replay.py`; `test_macro_recording_actions.py`; `test_macro_state_matcher.py`; `test_macro_v2_schema.py`; `test_manager_response_validation.py`.

The ledger intentionally excludes implementation claimed by the missing `mobilerun-core-local` dependency. No conclusion about that external driver's internals is inferred from wrapper calls in this checkout.
