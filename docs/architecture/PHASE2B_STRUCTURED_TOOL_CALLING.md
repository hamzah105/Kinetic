# Phase 2B Structured Real-Model Tool Calling

## Scope and invariant

Phase 2B connects OpenAI-compatible structured tool proposals to the existing native kernel. The invariant remains: **the model proposes intent; Kinetic controls execution**. No Android capability, dynamic execution, reflection dispatch, shell, scripting runtime, background autonomy, or permission beyond `INTERNET` is added.

Only two cloud-visible deterministic demo tools exist in this phase:

- `echo`: SAFE, exact `{ "text": string }` input, no approval.
- `protected_demo_tool`: CONFIRM, exact `{ "action": string }` input, identity-bound approval.

The provider/model setting `structuredToolCallingEnabled` defaults to false. Conversation-only operation sends `tool_choice: "none"` and no tool schemas.

## Provider-neutral boundary

`ModelToolSupport.Structured` supplies an explicit stable-ID allowlist. `ToolCall` remains the provider-neutral proposal and carries the provider call ID, stable tool ID, typed arguments, proposal turn identity, and call index. `ModelContinuationRequest` associates the original request, complete assistant proposal, and typed `ToolResult`. OpenAI wire objects do not enter `AgentRuntime`.

The runtime intersects provider exposure with `ToolRegistry.definitions()`. It validates one call only, current turn identity, index zero, exposure, registry resolution, and typed input contract before creating an effect. `ToolRegistry` remains the sole dispatch authority.

## OpenAI-compatible adapter

When enabled, `:data:model` emits Chat Completions `tools` with stable function names, concise descriptions, explicit object properties, required fields, maximum lengths, and `additionalProperties: false`. Requests use `tool_choice: "auto"` and `parallel_tool_calls: false`. Capability metadata, approval state, secrets, and database internals are not disclosed in schemas.

Non-stream and SSE responses are translated locally. The streaming assembler collects fragments by index and joins call ID, function name, and JSON arguments. It enforces local size/index bounds, finish-reason consistency, `[DONE]`, contiguous indexes, complete JSON, exact fields/types, and current exposed definitions. A partial, malformed, unknown, or unexposed call never becomes actionable. Multiple complete calls are returned as data so the kernel can reject the response deterministically; none execute.

Ordinary assistant content is never parsed for commands or JSON. Only `message.tool_calls` or streamed `delta.tool_calls` can create a proposal. Legacy `function_call` is rejected rather than guessed into the newer contract.

## Authorization and durable execution

The accepted path is:

1. Complete provider proposal.
2. Validate schema, identity, and exposure locally.
3. Resolve through `ToolRegistry` and check the typed contract.
4. Evaluate deterministic `CapabilityPolicy`.
5. Prepare the durable effect keyed by provider call ID.
6. Obtain durable exact session/turn/call approval for CONFIRM tools.
7. Persist `EXECUTING` before invocation.
8. Execute the typed tool.
9. Persist effect completion.
10. Send a bounded associated tool result.
11. Stream the final assistant response and complete the durable turn.

Effect preparation is duplicate-safe and returns false for an existing call ID in both in-memory and Room ledgers. Repeated provider call IDs therefore cannot silently create a second invocation, including after database reopen.

The provider receives only `status` and a bounded safe `output` or `error`, associated through the exact `tool_call_id`. Continuation history uses `system`, bounded durable user/assistant context, `assistant(tool_calls)`, then `tool(tool_call_id)`. Continuations force `tool_choice: "none"`; chained or parallel effects are outside this phase.

## Approval, cancellation, and recovery

CONFIRM proposals enter the existing `WAITING_FOR_APPROVAL` state. No execution or continuation occurs before approval. Approval and rejection remain bound to approval ID, session, turn, and call ID. Rejection cancels the turn, marks the unexecuted effect failed, and does not invoke continuation.

Cancellation before proposal completion cannot execute partial data. Cancellation while waiting cannot imply approval. Cancellation during continuation cancels the network call; it cannot downgrade or replay a completed effect.

Process recovery remains fail closed:

- `THINKING`: fail the interrupted turn; do not resend the request.
- `WAITING_FOR_APPROVAL`: restore the exact pending approval; do not execute.
- `EXECUTING`: fail the interrupted turn; never replay the effect.
- Tool completed before continuation finished: keep the effect `COMPLETED`, fail the interrupted turn, and require explicit new user action.

A recovered approval can resolve and execute its exact prepared demo effect, but provider continuation is intentionally not reconstructed or resent after process death.

## Persistence and UI

Room remains schema version 2. No new columns are required: conversation messages persist user/final-assistant/tool display content, while structured proposal, approval, and effect identities are already durable in journal, approval, and effect tables. Future provider context uses bounded user/final-assistant history; an in-process continuation supplies the transient valid assistant/tool pair directly and never sends an orphan historical tool message.

The Compose shell adds a per-model tool-capability switch, requested tool/safe argument summary, risk/approval status, generic approval controls, tool-result messages, and streamed final continuation. Debug journal access remains available and raw JSON/secrets are not displayed.

## Current limitation and deferred work

Phase 2B supports exactly one structured tool call and one final continuation. It does not support parallel calls, tool chains, recovered network continuation, public-cloud automated tests, or Android/device tools. All device-control capabilities remain deferred and unauthorized.
