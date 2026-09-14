# Phase 4C — Memory Governance, Conflict Resolution, and User Control

## Status and scope

Phase 4C adds deterministic governance to the controlled durable-memory foundation from Phases 4A and 4B. It does not add embeddings, fuzzy matching, model-driven merging, background inference, cloud memory, new Android permissions, or new tool authority.

Memory remains untrusted context. It cannot approve capabilities, alter Kinetic policy, create a tool call, or assert that an Android effect ran.

## Lifecycle contract

Every memory has one persisted lifecycle state:

- `ACTIVE`: current durable truth eligible for retrieval.
- `SUPERSEDED`: preserved history that is visible to the owner but never injected.
- `CONFLICTED`: an unresolved current alternative. All relevant alternatives remain visible and may be injected only with explicit conflict metadata.

The existing `retentionState` column carries this lifecycle. Phase 4C adds nullable governance metadata:

- `governanceKey`: SHA-256 of scope, session owner where applicable, and the deterministically normalized subject.
- `governanceSubject` and `governanceValue`: bounded structured parts parsed only from supported explicit commands.
- `supersedesMemoryId` and `supersededByMemoryId`: a bounded single-parent/single-successor provenance link.

Pre-v6 memories migrate as unchanged `ACTIVE` records with null governance metadata. They remain retrievable and user-deletable, but Kinetic does not invent subjects or values for them.

## Deterministic command surface

Supported explicit forms include:

- `Remember that my <subject> is <value>.`
- `Remember for this conversation that my <subject> is <value>.`
- `Update my <subject> to <value>.`
- `Replace my <subject> with <value>.`
- `Forget my <subject>.`

Ordinary prose does not silently become durable memory. Only exact, bounded parsing produces governance metadata.

An explicit update creates a new `ACTIVE` record and atomically marks every current same-key record `SUPERSEDED`. A normal remember with a different value for the same key conservatively marks both old and new alternatives `CONFLICTED`; it never chooses a winner. Exact repeated writes reuse the current record. `USER_EXPLICIT` records outrank lower provenance and cannot be displaced by derived content.

Natural-language forget succeeds only when the governance key has exactly one current target. Zero matches or multiple conflicted matches are refused without deletion. Targeted UI delete remains available for a record the user can see. Deleting never silently revives a superseded value.

## Atomic persistence and migration

Room database version 6 uses an explicit `MIGRATION_5_6`. It adds only nullable columns, converts the old unique content-key index to a normal index, and adds a composite governance lookup index. Existing sessions, messages, summaries, memories, provider preferences, and Android Keystore material are outside destructive migration paths.

Create, replace, conflict creation, resolution, and deterministic forget each execute in one Room transaction. The transaction reads the current same-key set, applies all lifecycle transitions, and writes the new record before committing. The in-memory reference repository applies the same state machine under a mutex. Repeated updates are idempotent, and serialized concurrent contradictory writes converge to one visible conflict set.

## Retrieval and prompt formatting

Candidate reads include only `ACTIVE` and `CONFLICTED` records. `SUPERSEDED` history is excluded before ranking. Phase 4B lexical scoring, candidate caps, selected-memory caps, and USER-before-SESSION ordering remain unchanged.

Provider context includes lifecycle state and only a short governance-key hash prefix. The fixed preamble tells the model that:

- `ACTIVE` values are current;
- `CONFLICTED` values are unresolved alternatives;
- the model must state uncertainty and ask for user resolution rather than choosing;
- `SUPERSEDED` values are historical and are not injected.

Session summaries remain derived historical context in their separate table and prompt block. They never mutate durable memory lifecycle and cannot silently replace current `ACTIVE` truth.

## User controls and inspection

Controlled memory groups records by governance key and shows newest-first history. Each row exposes lifecycle, category, provenance, update time, source session/turn prefixes, governance-key hash prefix, and content preview.

- Governed `ACTIVE` rows offer edit/replace and delete.
- `CONFLICTED` rows offer “Use this value” and delete.
- `SUPERSEDED` rows remain visible as history and offer delete only.

Context Inspector shows selected lifecycle and governance-key hash prefix alongside Phase 4B relevance diagnostics. It does not display credentials or raw secret material.

## Audit contract

Structured journal events record governance actions without raw memory content:

- `UPDATED`
- `CONFLICT_DETECTED`
- `RESOLVED`
- `FORGET_REQUESTED`
- `FORGOTTEN`
- `FORGET_REFUSED`

Events contain only an action, bounded memory IDs, and a 12-character governance-key hash prefix. Existing create, duplicate, reject, delete, clear, and inject audit events remain unchanged.

## Threat model

- Memory content and summaries remain untrusted model input.
- The sensitive-memory filter still runs before create, update, or UI replace writes.
- Rejected content is omitted from conversation history and structured audit.
- Forget subjects are omitted from displayed conversation history.
- No raw memory content is added to governance journal events.
- Lifecycle state cannot grant approval or bypass capability authorization.
- No signing, application ID, provider credential, Keystore, or permission behavior changes in Phase 4C.

## Rollback and compatibility

The v6 migration is forward-only in production. A rollback to a v5 binary is unsupported because Room will reject the newer schema; uninstalling or clearing data is not an acceptable rollback. Engineering rollback means shipping a new forward migration that preserves v6 rows and governance history.

The owner-found integration repair supersedes the original delete-and-recreate limitation:
strictly parseable compatible USER_EXPLICIT legacy rows are adopted only when a future explicit
governance transaction targets their exact subject/scope/session. Installation and retrieval alone
never rewrite historical rows. Updating an existing value also supersedes other targeted legacy
alternatives. Lower-provenance writes cannot adopt legacy governance or supersede explicit values.
Unparseable or unrelated records remain untouched. Conflict grouping remains exact and deterministic;
semantically similar subjects are not merged. Large conflict sets retain the existing context limits.

Owner reconciliation: Part B conflict/resolution passed; Part A update/supersession failed historically
because an ungoverned migrated PostgreSQL row stayed ACTIVE alongside governed SQLite. The repair
requires signer-preserving installation and an owner retest; neither installation nor green synthetic
tests constitute owner acceptance. See the unnumbered [Astra gate audit](ASTRA_INTEGRATION_GATE.md).
