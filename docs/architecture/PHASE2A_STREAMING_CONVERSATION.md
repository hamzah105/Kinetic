# Phase 2A Real Cloud LLM and Streaming Conversation

## Scope and invariant

Phase 2A adds real, multi-turn, text-only cloud conversation behind Kinetic's provider-neutral kernel. It preserves the project invariant:

> The model proposes intent; Kinetic controls execution.

The real provider cannot propose or execute Kinetic tools in this phase. Structured model tool calling, approval routing, and tool-result continuation remain Phase 2B work and are not implemented here. The deterministic `FakeModelProvider` remains available for offline development and the Phase 1 approval/tool regression paths.

## Module boundary

| Module | Phase 2A responsibility |
|---|---|
| `:core:kernel` | Provider-neutral stream events, runtime collection/cancellation, typed provider failures, turn-scoped streaming state, and existing policy/approval/tool boundaries. Pure Kotlin/JVM. |
| `:data:model` | Android provider settings, Android Keystore-backed secret storage, provider selection, HTTPS transport, OpenAI-compatible request/response mapping, and SSE decoding. |
| `:data:persistence` | Existing Room sessions, messages, journal, run ledger, explicit schema 1→2 migration, and durable message ordering. |
| `:app` | Provider settings and conversation UI, lifecycle-safe `ViewModel` wiring, active conversation selection, streamed rendering, cancellation, and retained developer journal. |

HTTP and Android security APIs do not enter `:core:kernel`. `AgentRuntime` continues to depend on `ModelProvider`, not on OkHttp, a vendor SDK, or provider-specific DTOs. This keeps future cloud, local, or hybrid adapters replaceable without rewriting the runtime.

## Model provider contract

`ModelProvider.generate` remains available for compatibility and focused non-streaming use. The minimal extension is `stream(ModelRequest): Flow<ModelStreamEvent>`, whose default implementation adapts a completed response into stream events. Providers may override it with genuine transport streaming. `TextDelta` carries incremental text and `Completed` carries the one normalized final `ModelResponse`.

`permitsToolProposals()` is an explicit capability boundary. It is `false` by default, `true` for the deterministic fake, and `false` for the cloud provider. The runtime supplies an empty tool list to conversation-only providers and rejects any resulting tool call as a malformed provider response before registry resolution, policy, approval, effect-ledger creation, or execution.

## OpenAI-compatible transport

`OpenAiCompatibleModelProvider` uses OkHttp and the OpenAI-compatible Chat Completions shape at `{baseUrl}/chat/completions`. Requests contain:

- configured model ID;
- a concise Kinetic system instruction;
- recent user/assistant conversation messages;
- `stream: true` for the streaming path;
- `tool_choice: "none"` and no `tools` declaration.

The system instruction states that Kinetic is an Android AI agent framework, Phase 2A is conversation-only, Android/device actions cannot be executed yet, and the assistant must not claim an action ran.

The provider validates an HTTPS base URL with a host and rejects embedded credentials, queries, and fragments. Cleartext traffic remains disabled globally, TLS verification is not modified, and no localhost exception or trust-all certificate path exists. The client has bounded connect/read/write/call timeouts, disables OkHttp connection-failure retry for these calls, and relies on explicit user retry.

## Streaming lifecycle

The cloud path consumes real `text/event-stream` data rather than splitting a completed string. Okio reads decoded UTF-8 lines, so transport chunks may split individual bytes or SSE lines without corrupting text. The decoder accumulates consecutive `data:` lines until the blank event boundary, handles comments, `[DONE]`, and a provider finish reason, and rejects malformed JSON, invalid choice/delta shapes, premature connection closure, data after completion, and tool-call payloads.

The runtime lifecycle remains:

```text
IDLE -> THINKING -> progressive TextDelta values -> COMPLETED
                -> FAILED
                -> CANCELLED
```

Partial output is held only in the runtime's turn-scoped `StateFlow`. The UI displays it only when its turn ID matches the active `THINKING` turn. The assistant message is appended to Room only after a valid completion, preventing a partial response from masquerading as durable completion.

## Cancellation and lifecycle

The UI starts one turn through the `ViewModel`; Compose recomposition cannot launch another request. Cancel stops the active runtime job. Coroutine cancellation invokes `Call.cancel()` through both the callback and streaming paths, closes the response, clears partial text, stops accepting deltas, and durably journals a Kinetic cancellation rather than a provider failure.

If Android kills the process during `THINKING`, existing fail-closed recovery marks the interrupted turn failed and never silently re-sends the HTTP request. The user may explicitly retry as a new turn. No foreground service or background autonomous generation was introduced.

## Context strategy

Each cloud request contains one system instruction followed by the last 20 durable messages whose role is `USER` or `ASSISTANT`. Tool messages are excluded. Filtering occurs before `takeLast`, and Room returns messages by durable per-session sequence, so order and session isolation are deterministic. Phase 2A deliberately has no token estimator, summarization, embedding, semantic memory, or cross-session retrieval.

## Durable conversation history

The existing Room database remains the sole conversation store. Schema version 2 adds `messages.sequence` and a unique `(sessionId, sequence)` index. `RoomSessionStore` allocates the next sequence transactionally and reads/observes messages in sequence order. This removes timestamp and lexical-ID ambiguity while retaining session and turn ownership.

`MIGRATION_1_2` adds and backfills sequence values for existing rows in deterministic `(sessionId, createdAt, messageId)` order, then creates the unique index. The production database registers that migration and still has no destructive fallback. Both schema versions are exported.

The app persists the active conversation ID separately from provider settings. Message history survives Activity recreation, force-stop, process restart, and an in-place Phase 1 database upgrade. Starting a new conversation changes the active session without merging histories.

## Secret storage and log boundary

Non-secret display name, base URL, model ID, timeout, and selected mode use a private SharedPreferences file. The API key is encrypted with AES/GCM; the non-exportable AES key is generated and retained by Android Keystore under `kinetic.phase2a.provider_api_key`. Preferences hold only Base64 ciphertext and IV. Save can preserve or replace the key, and a blank key field always retains it. Fake/Cloud and other non-secret settings writes explicitly retain the encrypted pair. Clear is the only deletion path. Decryption requires the existing Keystore alias; a failure preserves the encrypted material and reports a bounded replace-explicitly error rather than creating a replacement key or silently clearing the credential. The UI exposes only stored/not-stored and replace/clear semantics—never the recovered value.

The provider preferences file is explicitly excluded from Android backup/data extraction in addition to the application disabling backup. Decrypted bytes are zeroed after conversion, and the plaintext key is held only long enough to create the authorized request. There is no HTTP logging interceptor. Provider bodies and raw exceptions are not copied into user errors or the journal; only stable typed errors and safe messages cross the adapter boundary.

Instrumentation tests verify that a test key is absent from raw preferences, Room messages, Room journal records, and database bytes. JVM tests verify that response bodies containing a reflected test key do not reach safe exception messages. The existing journal sanitizer continues to redact common credential assignments as defense in depth.

## Error mapping

Provider failures cross the kernel as `ModelProviderException` with a `ModelProviderFailureKind`, then become `AgentError.ModelFailure` with stable codes:

| Condition | Kind / stable code |
|---|---|
| Missing or invalid configuration | `CONFIGURATION` / `model_configuration_missing` |
| HTTP 401 or 403 | `AUTHENTICATION` / `model_authentication_failure` |
| I/O reachability failure | `NETWORK` / `model_network_unavailable` |
| Socket/call timeout | `TIMEOUT` / `model_timeout` |
| HTTP 429 | `RATE_LIMIT` / `model_rate_limited` |
| HTTP 5xx or other provider rejection | `SERVER` / `model_server_failure` |
| Invalid JSON/SSE, incomplete stream, or tool payload | `MALFORMED_RESPONSE` / `model_malformed_response` |
| Unexpected adapter failure | `UNEXPECTED` / `model_failure` |

User cancellation retains the existing distinct `AgentError.Cancelled` path. API keys, authorization headers, provider response bodies, and raw exception strings are not included in these errors.

## UI behavior

The Compose shell now provides scrollable user/assistant/tool history, progressive assistant output, an input and Send action, Cancel generation while active, provider/model status, safe error text, New conversation, and a provider panel for save/replace/clear and Fake/Cloud selection. The journal/runtime information remains in a collapsible Debug section. Provider configuration is disabled during an active turn.

The source manifest adds only the normal `android.permission.INTERNET` permission and explicitly keeps cleartext traffic disabled. No dangerous permission, service, receiver, device automation, Accessibility integration, shell execution, or background component was added.

## Verification boundary

Automated network tests use a local TLS `MockWebServer`; no test uses a real API key or a paid public model endpoint. They cover non-stream and fragmented UTF-8 SSE mapping, bounded ordered context, configuration, HTTP/error categories, timeout, malformed/tool payload rejection, cancellation, provider switching, and secret exclusion. Runtime and Room tests cover turn scoping, recovery without resend, session isolation, reopen durability, and ordering.

The project owner subsequently completed the Phase 2A live-provider acceptance flow. This document remains the historical Phase 2A boundary; Phase 2B is documented separately.
