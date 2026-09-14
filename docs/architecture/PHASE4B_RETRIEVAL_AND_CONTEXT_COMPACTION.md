# Phase 4B: Retrieval and Context Compaction

Phase 4B evolves the existing Phase 4A memory injection path into one deterministic,
provider-neutral context pipeline. It adds query-aware memory selection, derived durable
session summaries, explicit input budgeting, and safe inspection. It does not add another
database or an alternate execution path.

## Context planning

`ContextPlanner` is pure Kotlin in `:core:kernel`. Its inputs are the current session and query,
a bounded `MemoryContext` candidate pool, the latest completed summary for that session, durable
messages, and a `ContextBudget`. Its `ContextPlan` records selected memory metadata, the optional
summary and its range, selected message range, size estimates, counts omitted by coverage or
budget, and omission reason codes. Android, Room, and provider wire types do not cross this API.

The default budget is 8,192 estimated tokens. It reserves 512 for trusted system/security text
and 1,024 for provider output. The conservative estimator is `ceil(characterCount / 4)`, with
small fixed framing allowances for memories, messages, and summaries. The current user request
is selected first, then relevant memories, recent unsummarized conversation, and finally the
derived summary if it fits. Recent messages are returned in chronological order. At most 20 are
included. Messages covered by an included or available current summary are excluded from the
recent-message pool, preventing double-context inflation. Security instructions are emitted by
the provider independently and are never eligible for truncation.

## Deterministic memory retrieval

Room returns at most 32 recent active USER candidates and 32 recent active candidates owned by
the current SESSION. `DeterministicMemoryRanker` normalizes case and punctuation, removes a small
fixed stop-word list, and scores only records with at least one meaningful query-token match:

- 10 points per distinct overlapping term;
- 4 points per term in the longest ordered matching run;
- 8 points for PREFERENCE memory when the query contains a preference term;
- 6 points for TASK_CONTEXT memory when the query contains a session/task term;
- 30 points when every meaningful query term appears in the memory;
- 100 points for an exact normalized query/content match.

Records are ordered by score descending, then `updatedAt` descending, then stable `memoryId`
ascending. Recency is therefore only a tie-breaker. Zero-score records are omitted instead of
filling a quota. Injection remains capped at four USER and four current-SESSION records. A long
record receives no size-based relevance bonus and may still be omitted by the context budget.
Conflicting relevant memories are not merged or deleted; contradiction governance remains out of
scope for Phase 4B.

## Session summary domain and storage

`SessionSummary` is distinct from `MemoryRecord`. It is always session-scoped and records a stable
ID, session ID, bounded content, first and last covered durable message sequence, cumulative source
count, creation time, SHA-256 source digest, `MODEL_DERIVED` provenance, and `COMPLETED` status.
Summaries are never USER memories and are never promoted into cross-session facts.

Room schema v5 adds `session_summaries` to the existing database with a session foreign key,
coverage index, and unique source digest. Migration 4-to-5 creates only that table and its indexes;
there is no destructive fallback. Original messages remain authoritative and are never deleted or
overwritten. Deleting a summary, if done through the repository, does not delete its messages.

`ConversationSummaryService` compacts incrementally. The first result covers an older prefix while
reserving the latest four messages. A later request combines the previous completed summary with
only newly eligible messages, advances the coverage boundary, and preserves the full Room history.
Manual compaction needs at least five unsummarized USER/ASSISTANT messages. Automatic compaction
needs at least 24, runs only during a foreground turn, and is default OFF because cloud mode can
incur an extra provider request and cost. No WorkManager, service, scheduler, or restart replay is
used.

## Generator, safety, and recovery

`ConversationSummaryGenerator` is a core port. Fake mode uses a deterministic local generator.
Cloud mode reuses the configured OpenAI-compatible transport and credentials; it does not create a
second provider configuration. Summary calls expose no tools, use `tool_choice: none`, and carry a
bounded safety instruction. Source messages are serialized as untrusted data. The instruction
requires factual attribution, preserved uncertainty and unresolved work, and forbids manufactured
preferences, permissions, approvals, or policy changes.

Before a remote/local generator is invoked, credential-shaped source content causes the affected
compaction to be refused. Generated content is also checked for credential shapes, blank output,
and the 4,000-character limit before a completed row is saved. No source or generated secret is
journaled. A provider/configuration/authentication/network/timeout/rate-limit/server/malformed
failure returns a safe recoverable status and leaves the prior completed summary and original
history untouched. Coroutine cancellation propagates to the HTTP call and no partial row is saved.
Process death has the same durable outcome: because only a complete response is inserted and no
pending summary job is restored, relaunch cannot automatically resend or install partial output;
an explicit foreground retry is required.

## Provider context separation

The OpenAI-compatible request keeps four distinguishable layers in order:

1. trusted static Kinetic system/security instruction;
2. an optional untrusted durable-memory JSON block;
3. an optional derived, untrusted session-summary JSON block;
4. selected ordinary USER/ASSISTANT messages.

Memory and summary blocks explicitly state that they cannot authorize tools, grant approval, or
change policy. Retrieval produces data only. It cannot create a `ToolCall`, approval, Android
Intent, ledger effect, or capability execution; the existing structured provider output, registry,
policy, argument binding, approval gate, and lifecycle coordinator remain authoritative.

## Debug inspection

The developer panel exposes `Compact current session context now`, summary existence and bounded
preview, coverage range, source count, timestamp/provenance, and a Context Inspector. The inspector
shows budget totals, candidate and selected counts, summary use/range, included and omitted message
counts, safe memory ID prefixes, scopes, categories, scores, and match counts. It deliberately does
not repeat complete memory content, provider credentials, or secret-like summary data.

Embeddings and vector databases are deferred. Phase 4B intentionally establishes an explainable,
testable deterministic baseline so later retrieval-quality measurements can justify added semantic
complexity. It adds no embeddings API, ANN index, background worker, cloud memory sync, permission,
or new Android capability.
