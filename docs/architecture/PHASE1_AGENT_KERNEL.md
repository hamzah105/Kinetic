# Phase 1 Agent Kernel Foundation

> Historical Phase 1/1.1 architecture record. Phase 2A now adds the bounded, conversation-only provider path described in [PHASE2A_STREAMING_CONVERSATION.md](PHASE2A_STREAMING_CONVERSATION.md); the Phase 1 statements below describe the verified baseline at that phase boundary.

## Scope

Phase 1 establishes a native Kotlin control plane and a minimal Compose developer shell. It proves one bounded agent turn, deterministic tool selection, policy enforcement, interactive approval, journaling, and cancellation. It does not implement Android device control, real inference, remote protocols, background autonomy, or any Phase 2 capability.

The implementation is original Kinetic code guided by the Phase 0 requirements and architecture findings. No source was copied from the audited reference repositories.

## Module and package boundaries

| Boundary | Responsibility | Android dependency |
|---|---|---|
| `:app` | Compose UI, `ViewModel`, recovery startup, and manual dependency assembly | Yes |
| `:data:persistence` | Room database, entities/DAO, domain mapping, durable session store and run ledger | Yes |
| `:core:kernel/agent` | Turn coordinator, protocol, state machine, cancellation, typed errors | No |
| `:core:kernel/model` | Provider-neutral request/response contract and deterministic fake | No |
| `:core:kernel/tools` | Typed tool contracts, immutable allowlist, harmless demo implementations | No |
| `:core:kernel/policy` | Capability metadata, deterministic policy, identity-bound approval gate | No |
| `:core:kernel/session` | Immutable session snapshots plus `SessionStore` and `RunLedger` ports | No |
| `:core:kernel/logging` | Structured event journal and redaction boundary | No |

The six conceptual core areas use packages inside one pure Kotlin/JVM module. Splitting them into six Gradle modules in Phase 1 would add build and API overhead without improving isolation. Their interfaces are explicit so later implementations can be separated when Android dependencies or independent release boundaries justify it.

## Runtime lifecycle

Every admitted request owns one turn ID. `DefaultAgentRuntime` serializes active turns and routes every state mutation through `AgentStateMachine`.

```text
IDLE -> THINKING -> COMPLETED
                  -> EXECUTING -> COMPLETED
                  -> WAITING_FOR_APPROVAL -> EXECUTING -> COMPLETED
                                            -> CANCELLED (rejected)

THINKING / WAITING_FOR_APPROVAL / EXECUTING -> FAILED or CANCELLED
COMPLETED / FAILED / CANCELLED -> IDLE (next turn only)
```

Any edge not in the explicit transition table throws `IllegalStateTransitionException`. A model response may contain no tool or one tool call in Phase 1. Multiple calls fail closed rather than being partially executed.

Cancellation uses the active coroutine `Job`. The runtime cancels a pending approval, records a cancellation in a `NonCancellable` cleanup section, publishes `CANCELLED`, and rethrows `CancellationException` to preserve structured concurrency.

## Agent and model protocol

The internal protocol uses data classes and sealed interfaces: `AgentRequest`, `AgentMessage`, `ModelRequest`, `ModelResponse`, `ToolCall`, `ToolResult`, `ApprovalRequest`, `AgentTurnResult`, and `AgentError`. Arbitrary maps are not passed through the execution path.

`ModelProvider` is the adapter boundary for a future cloud provider, Gemini Nano/AICore, LiteRT model, llama.cpp engine, or ADK bridge. The kernel only sees normalized requests and structured responses. `FakeModelProvider` is the sole Phase 1 implementation. It is deterministic, performs no network activity, and supports fixed prompts that exercise success and failure paths.

A future provider adapter may propose tool calls but cannot authorize or execute them. ADK, MCP, A2A, and AppFunctions may later sit behind adapters; none is a foundation dependency and none is integrated in Phase 1.

## Tool registry and capability policy

`ToolRegistry` is an immutable, duplicate-rejecting allowlist. The runtime resolves a proposed stable tool ID and validates the sealed `ToolInput` kind against its `ToolInputContract` before evaluating policy. Unknown tools and malformed inputs produce `InvalidToolCall`; there is no reflective or name-based fallback.

Phase 1 registers four harmless tools:

| Tool | Input | Risk | Confirmation | External effect |
|---|---|---:|---:|---|
| `echo` | `EchoInput` | `SAFE` | No | None |
| `current_app_time` | `NoToolInput` | `SAFE` | No | Reads injected app clock only |
| `protected_demo_tool` | `ProtectedDemoInput` | `CONFIRM` | Yes | None; approval demonstration |
| `failing_demo_tool` | `NoToolInput` | `SAFE` | No | None; typed failure demonstration |

Every capability declares `riskLevel`, `requiresConfirmation`, `requiredPermissions`, and `distributionAvailability`. Risk levels are `SAFE`, `CONFIRM`, `SENSITIVE`, and `RESTRICTED`. Distribution profiles are `PLAY_CORE` and `LAB`. The policy fails closed when a profile is unavailable or a required permission is absent, denies `RESTRICTED` tools in Play Core, and requires approval for every non-safe risk or explicit confirmation flag.

This is a domain distinction only in Phase 1. A later release must enforce the Play/lab split at compile-time through source sets or separate artifacts, manifests, dependencies, signing, and CI inspection—not through a runtime toggle in one Play binary.

## Approval gate

`InteractiveApprovalGate` owns a single pending, identity-bound `ApprovalRequest`. `LedgerBackedApprovalGate` wraps it in production and commits the decision to `RunLedger` before releasing the in-process waiter. The request carries approval, session, turn, and call IDs plus typed tool input and capability metadata. Resolution must match the original approval, turn, and call IDs. The runtime enters `WAITING_FOR_APPROVAL` before suspending, and only a durably recorded `APPROVE` decision permits the transition to `EXECUTING`. Rejection produces typed `ApprovalRejected`, records rejection, and ends the turn as `CANCELLED` without starting the tool.

The deterministic acceptance path is:

```text
"Run the protected demo action"
  -> FakeModelProvider proposes protected_demo_tool
  -> registry and typed input validation
  -> CONFIRM policy decision
  -> WAITING_FOR_APPROVAL
  -> approve: EXECUTING -> "Protected demo action completed." -> COMPLETED
  -> reject: ApprovalRejected -> CANCELLED, no execution-start event
```

Tests validate both full paths and their journal entries.

## Sessions and persistence

`AgentSession` contains `sessionId`, `createdAt`, `updatedAt`, ordered messages, and ordered journal entries. The kernel retains a pure Kotlin `SessionStore` port and an in-memory implementation for deterministic JVM tests. Android production wiring uses `RoomSessionStore` from `:data:persistence`; the app sees only the port through `KineticPersistence`, not Room or database types.

Room schema version 1 contains `sessions`, `messages`, `journal_entries`, `turns`, `approvals`, and `effects`. Foreign keys preserve ownership, and a unique `(sessionId, sequence)` journal index prevents ordering ambiguity. Turns store safe request summaries and terminal error/summary fields. Approval rows retain the exact durable identity and typed Phase 1 input needed to restore the prompt. Effect rows bind one call to its owning session and turn.

The production builder does not enable destructive migration. Version 1 exports its schema under `data/persistence/schemas`; any future schema bump must add and test an explicit Room migration before release.

## Durable execution ledger and recovery

`RunLedger` is a pure kernel port. Its Room adapter performs state changes transactionally. The runtime writes a `THINKING` turn before model work, a `PENDING` effect before approval or execution, `WAITING_FOR_APPROVAL` plus its approval row before showing the prompt, and `EXECUTING` before invoking a tool. Successful effect and turn completion are committed in one transaction.

Startup calls `recover(sessionId)` before admitting a new turn:

| Durable state at process death | Restart behavior |
|---|---|
| `THINKING` | Becomes `FAILED` with typed `RecoveryInterrupted`; interruption and transition are journaled; explicit retry required. |
| `WAITING_FOR_APPROVAL` + `PENDING` | Restores the original request and remains waiting for an explicit Approve or Reject action. It is never promoted automatically. |
| `WAITING_FOR_APPROVAL` + `REJECTED` | Reconciles to `CANCELLED`; the tool is not started. |
| `WAITING_FOR_APPROVAL` + stale/missing/approved authorization | Fails closed instead of treating the stale decision as permission to continue. |
| `EXECUTING` | Becomes `FAILED` with typed `RecoveryInterrupted`; the tool is not replayed because its side effect may already have occurred. |
| `COMPLETED`, `FAILED`, `CANCELLED` | Restores the matching terminal state and safe summary/error. A completed effect reconstructs a display-only `RecoveredToolOutput`, never another execution. |

This is deliberately at-most-once/fail-closed recovery, not transparent continuation. Model calls in progress are not retried, and uncertain effects are never automatically replayed. A user must create a new turn to retry interrupted work.

## Event journal and secret handling

`SessionEventJournal` records typed events for user requests, model responses, tool requests, approval requests and decisions, execution start/completion, errors, cancellations, and state transitions. `RoomSessionStore` maps each event to explicit columns rather than a UI-text blob and assigns a strictly increasing per-session sequence inside a database transaction. Event IDs, session/turn ownership, and ordering survive close/reopen and process death.

Journal summaries are bounded and pass through `JournalSanitizer`, which redacts common credential assignments. Tool input types expose deliberately limited summaries rather than raw values. Errors record stable codes and user-safe messages; raw exceptions and stack traces never cross the public error boundary. This is defense in depth, not a future substitute for a dedicated encrypted secret store.

## Error model

`AgentError` distinguishes `ModelFailure`, `ToolFailure`, `PermissionFailure`, `ApprovalRejected`, `InvalidToolCall`, `Cancelled`, `RecoveryInterrupted`, `RestoredAgentError`, and `InternalFailure`. Adapter exceptions are mapped at the boundary. The internal incident ID on an unexpected failure permits correlation without exposing implementation details.

## Developer shell

The Compose screen can enter and start a request, display state and safe failure detail, show fake model output, present an approval card, approve or reject, display a fake tool result, cancel an active turn, and inspect the structured journal. `KernelViewModel` performs small manual dependency assembly and blocks new work until startup reconciliation completes.

Current-result presentation is turn-scoped. Model text is selected only from assistant messages carrying the active runtime turn ID, and the result card reads only the current `RuntimeState.Completed.toolResult`. Historical messages and journal entries remain available, but a new, rejected, failed, or cancelled turn cannot display a successful result from an earlier turn.

The manifest requests no Android permissions and declares no services, receivers, providers, or network requirements.

## Phase 0 decisions applied

- New Android-native kernel; no hidden Gateway, Node, Python, Termux, PRoot, or desktop runtime dependency.
- Provider-neutral loop and deterministic capability broker outside model control.
- Explicit allowlist, typed validation, approval, structured audit, safe errors, and cancellation.
- Play Core/lab availability metadata anticipates a compile-time capability split.
- Room remains an Android adapter below pure Kotlin session and run-ledger ports; no Room, `Context`, Activity, or ViewModel type enters `:core:kernel`.
- Kotlin semantic design was preferred over transplanting monolithic or platform-mismatched agent loops.
- No code or assets were copied from AndyClaw (GPL-3.0), unresolved MobileRun provenance, restricted Hermes productivity materials, opaque native binaries, or any other reference.

## Future extension points

- `ModelProvider`: cloud and on-device inference adapters, streaming extensions, usage metadata.
- `Tool`: Android-native capabilities supplied by distribution-specific modules.
- `CapabilityPolicy`: richer scopes, quotas, target constraints, invocation-time permission rechecks.
- `ApprovalGate`: parameter-diff UX, expiration, and richer authorization scopes.
- `SessionStore` / `RunLedger`: explicit schema migrations, multiple effects per turn, and stronger delivery/idempotency protocols for real side-effecting tools.
- Protocol adapters: narrowly authenticated MCP, A2A, AppFunctions, and optional ADK integration below the same policy boundary.

## Deliberately deferred

Real model providers, streaming, multi-tool loops, context compaction, semantic memory, background work, Android permissions and capabilities, AppFunctions, MCP, A2A, ADK, skills/plugins, device automation, multimodal input, local-model benchmarking, and distribution flavors are outside Phase 1.1.

No Accessibility service, shell/ADB/root execution, notification/SMS/call-log/contact access, microphone, camera, network model API, production local model, or autonomous background component is present.
