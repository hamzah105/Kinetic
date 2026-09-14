# Astra integration and product-refinement gate

Unnumbered gate, 2026-09-05. Engineering verification and owner acceptance are separate;
the final verified results belong in `roadmap.md`. This gate does not start Phase 5.

## Canonical-state audit

The checkout has no Git metadata. The authoritative roadmap lists Phase 4A/B accepted,
Phase 4C implemented with owner governance acceptance pending, and Phase 5 NOT STARTED.
Source, Gradle settings, architecture documents in phase order, and tests agree: there is
no Phase 5 local-model module, runtime, benchmark, provider or test artifact. The owner's
reported Phase 5 completion cannot be corroborated in this checkout and is not fabricated.
Existing modules remain app, pure JVM kernel, persistence, model and Android capabilities.
No external reference code or artwork was copied.

The unfinished Phase 4C repair is present in source. Pre-gate report artifacts establish
181 passing tests: kernel 91, model JVM 22, capability JVM 12, app JVM 10, Room Android 20,
model Android 9, capability Android 17; zero failures/errors/skips. Lint artifacts show
0 errors/9 warnings. The repair APK had been assembled but not installed: the device still
reported `0.4.2-phase4c-governance`. Part B owner conflict/resolution passed historically;
Part A owner update/supersession failed because legacy ungoverned PostgreSQL remained ACTIVE.
Neither repair installation nor owner acceptance is inferred from the old test artifacts.

The repair adopts only strictly parseable, compatible, visible USER_EXPLICIT legacy records
when a future explicit governance transaction targets their exact normalized subject and
scope/session. Installation and ordinary retrieval never rewrite old rows. An explicit
update can also reconcile an already-current duplicate with other legacy alternatives.
This gate additionally prevents lower-provenance duplicates from superseding explicit
alternatives or adopting legacy governance. No migration or schema change is introduced.

## Official API findings (checked before implementation)

The exact model ID is `gpt-6-astra`. Documented reasoning levels are low, medium, high,
xhigh and max; none is unsupported. Context is 1,050,000 tokens, maximum output 128,000.
Text input/output and image input are documented; audio/video are not. Standard per-million
USD pricing is input 10, cached input 1, cache writes 12.50, output 50. Above 272,000 input
tokens, input/cache rates double and output rises 1.5x for the whole request. Rollout and
account/model access must be verified by the owner; engineering makes no availability claim.
[Model specification](https://developers.openai.com/api/docs/models/gpt-6-astra).

Tool calling requires Responses. The adapter uses low effort initially and omits temperature,
top_p, top_logprobs, logprobs and output-text logprob includes. Mid-turn steering, async tools,
configuration-update caching optimization and vendor multi-agent features are deliberately
not integrated. Kinetic Fast is an effort profile, not OpenAI's paid Fast service tier.
[Migration guidance](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-6-astra).

Strict functions use flat type/function/name/parameters metadata, all properties required,
additionalProperties false, and nullable types for optional email fields. Kinetic removes
allowed null email values before applying its unchanged typed validator. Continuations
return the original call_id with a function_call_output and replay the original local output
items, including encrypted reasoning, rather than using a server conversation.
[Function calling](https://developers.openai.com/api/docs/guides/function-calling),
[stateless reasoning](https://developers.openai.com/api/docs/guides/reasoning).

`store=false` disables ordinary stored Responses application state. It does NOT establish
zero retention: abuse monitoring and cache-related retention may apply. Provider data is
not used for training by default under the documented API policy; account-specific controls
are not assumed. No background response, server conversation, hosted file, or analytics is
created. [Data controls](https://developers.openai.com/api/docs/guides/your-data).

Prompt caching is provider-managed; no cache optimization/retention override is sent in
this gate. Reported input token details include cached_tokens and cache_write_tokens, which
are accounted separately. Costs remain unknown if required usage is absent.
[Prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching).

Provider misalignment monitoring is an additional fallible safeguard, not authorization.
A stopped/failed response fails the turn; Kinetic does not auto-resume, retry or disable
monitoring. Local schema, approval and effect checks remain necessary.
[Safety monitoring](https://developers.openai.com/api/docs/guides/safety-checks/misalignment-monitoring).

## Provider boundaries and runtime contract

`ModelProvider` is retained. `ConfiguredModelProvider` selects Fake, existing compatible
Chat Completions, or direct `OpenAIResponsesProvider` in `:data:model`. It pins the selected
adapter through a turn and its single continuation. OpenAI JSON, HTTP and encrypted output
items never enter core, Room, the journal or UI. A terminal-turn hook discards transient
continuation material on completion, failure, rejection and cancellation. Process death
loses it intentionally; restored approval execution never reconstructs network continuation.

Provider-neutral `ModelCapabilities` describes streaming, structured tools, image input,
reasoning levels, caching, steering, async tools, context maximum and provider kind. An
explicit catalog maps the exact Astra ID to supported adapter features. Unknown models
are rejected in the direct path; the compatible path retains arbitrary configured models
with its explicit structured-tool switch. No model-name substring heuristics are used.
Image input, steering and async are false because their Kinetic adapters are not enabled,
even though Astra has corresponding upstream capabilities.

`KineticOperatingContract` v1.0 is concise, versioned and tested. It describes role, trust,
proposal-only function semantics, exact approval, foreground execution, fail-closed replay,
untrusted memory/documents/images, derived historical summaries, canonical Room authority,
provider data policy, response style and a sorted per-request capability manifest. Architecture
documents are not loaded into requests. The contract guides the model; deterministic local
policy, not the model's obedience, supplies security.

## Stream, execution and cancellation

Responses SSE maps response.created to Started, output_text.delta to TextDelta, function
item/argument events to non-authoritative progress, and terminal validated output to Completed.
Raw reasoning is not displayed. Arguments remain inert until a matching completed function
and completed response are verified. Completion is emitted once after EOF; malformed,
incomplete, extra-terminal, altered-argument and unsupported hosted-tool streams fail closed.
HTTP errors are typed without reflecting bodies or credentials. Connection retry and redirects
are disabled; cancellation closes the active call. Local frame/total/text/argument/output
bounds prevent unbounded accumulation. [Streaming reference](https://developers.openai.com/api/docs/guides/streaming-responses).

The completed ToolCall still crosses registry validation, capability policy, CONFIRM,
exact argument binding, durable effect claim and foreground lifecycle coordinator. Partial
progress never touches the ledger. One proposal and one final no-tools continuation remain
the limit. No direct Android API, executable async tool, server tool or automatic retry exists.

## Context, profiles, credentials and metrics

ContextPlanner retains its 8,192 estimated-token practical budget and 1,024 output reserve;
the direct request explicitly caps output at 1,024 (including reasoning). This can yield an
incomplete response, especially with Deep; it fails safely, not as a completed answer. Raising
the budget requires evaluation. Provider context maximum is only an upper bound. Relevant
USER memory, isolated SESSION memory, chronological recent messages and derived summaries
remain bounded; SUPERSEDED rows are excluded. No embeddings or server compaction are added.

Fast/ Balanced/ Deep map to low/ medium/ high for Astra, with Fast as default. Compatible
models retain existing settings rather than inheriting unsupported reasoning parameters.
Runtime stages reflect actual Kinetic state; no private chain of thought is exposed.

Existing compatible ciphertext/IV preference names and Android Keystore alias are retained.
OpenAI has its own encrypted pair and non-secret profile/tool settings in the same already
backup-excluded private preferences. The app's existing non-exportable encryption key protects
both pairs, but provider API credentials are never copied or used interchangeably. No new
signing key or Room migration is needed. Only selected-provider explicit Clear removes its
pair. Blank saves, metadata changes, mode/profile changes and decrypt failures retain data.
The credential input is masked and deliberately not rememberSaveable: plaintext must not
enter Android saved-instance state. Real keys are entered only by the owner in Kinetic.

`LocalProviderMetrics` retains at most 20 records in process memory, never disk/Room/analytics:
provider/model/profile, total duration, first text latency, available input/cache/write/output
tokens, outcome and approximate standard-tier USD cost. Missing usage is unknown, not zero;
cache writes are included in cost. The pricing snapshot is dated and is not an invoice or
an account-specific discount/tier guarantee. There are no prompt/message/credential logs.

## Verification policy

Preserve all existing tests and the production-package `verifyDeviceTestSafety` guard. Run
the seven established JVM/library-device lanes serially, then guard/lint/assemble. Never run
production app instrumentation. New tests cover request/profile/data mapping, SSE, cancellation,
errors, partial and complete tools, direct-adapter-to-kernel CONFIRM/Reject/Approve/no replay,
continuation binding/discard, metrics, context, themes and isolated credentials. Existing
library tests continue to cover Android foreground ownership, Activity recreation, exact
authorization, Room migration/reopen, legacy governance and summary durability.

Real Astra network behavior and owner-account access remain an owner acceptance gate after
synthetic TLS tests and signer-preserving device verification. No real key is supplied to
commands, tests, source, logs, docs or Codex.
