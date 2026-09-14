# Phase6 — build-only hybrid router foundation, 2026-09-15

## Scope

Owner explicitly authorized Phase6 after Candidate3B host-level completeness.
Build-first only: no ADB, emulator, physical-device tests, instrumentation, install,
real provider request or model execution. Phase7 is not implemented here. No claim
of production routing, Android runtime or owner acceptance follows from host tests.

## Domain and selection

Pure-Kotlin HybridModelRouter in :core:kernel consumes typed requirements, candidate
capabilities/availability and supplied environment. It does not accept prompt text,
memory, summary content, credentials, URLs, ToolRegistry, policy or Android objects.
Typed fixed provider IDs, enums and counts form bounded inspectable decisions.
No model classification, textual tool parsing, dynamic context activation or learned
cost/latency policy is introduced.

Default routing mode is MANUAL, including absent/unknown persisted values. Existing
manual provider buttons also explicitly return to MANUAL without altering keys.
HYBRID requires an explicit UI choice. Policy booleans (local-only and tool-required)
are ordinary private settings, not model-editable fields. No Room migration.

Deterministic policy:

- Explicit per-operation pin wins; MANUAL otherwise pins the existing provider mode.
  An ineligible pin fails, not silently replaced by another candidate.
- Local-only forbids network providers. Tool-required excludes text-only LOCAL_LLAMA.
- Known context overflow excludes a candidate (LOCAL_LLAMA cap1024).
- Missing/incompatible/unavailable local backend is excluded. Invalid/missing cloud
  configuration/key-presence excludes Hybrid cloud before secret retrieval.
- Hybrid rejects network UNAVAILABLE and UNKNOWN. Manual retains its existing
  attempt-on-unknown-network behavior; explicit offline metadata still rejects it.
- Known constrained battery/thermal excludes real local. UNKNOWN remains UNKNOWN.
- Fake/SIMULATED are excluded from automatic Hybrid selection; explicit test/manual
  pins retain their existing development behavior.
- Eligible candidates use stable local-first order, then OpenAI, compatible cloud.
  This is a fixed preference, not a measured cost/latency prediction. No price,
  performance, battery or thermal measurements are fabricated.
- No eligible candidate produces typed NO_ELIGIBLE_PROVIDER (legacy manual missing
  local retains LOCAL_UNAVAILABLE). Reasons include explicit pin, local-first,
  stable-order selection and individual rejection codes.

Availability UNKNOWN means only eligible to **attempt** when no known constraint
disallows it: installed local still needs hash/ABI/memory/native checks; configured
cloud may fail authentication/server checks. These UNKNOWN values are preserved in
the decision/inspector, not relabeled READY. Unknown remote context capacity is null,
not unlimited or a guessed limit; selection rejects known overflow only. Existing
ContextPlanner/adapter checks still bound actual requests and fail without rerouting.

The runtime supplies the existing estimateTokens(current input)+320 minimum budget
before planning. It is admission metadata, NOT exact local token accounting. The
selected provider's limits/reserves then drive normal ContextPlanner selection; the
native full-template tokenizer remains final authority for the local1024 envelope.
No native overflow causes cloud fallback. Direct provider calls and summaries supply
their complete input-range estimates. Summaries retain the prior typed oversized
source limitation; no source truncation is added.

## Pinning and authority

A default no-op ModelProvider.prepareTurn hook is invoked immediately before the
existing context/tool planning logic. AgentRuntime is not rewritten. ConfiguredModelProvider
serializes preparation, snapshots settings, computes a decision, and only then
reads the selected cloud credential and constructs that adapter. No unselected key
is decrypted. Provider capabilities/tool support come from the pinned adapter.
The same adapter handles generation and continuation until finishTurn. A different
concurrent turn is rejected. Failed preparation is retained until turn cleanup.
Settings/telemetry changes cannot switch an active turn. No cross-provider retry.

Each summary is separately selected once, with tools disabled for summary routing,
and holds that adapter for the whole operation. A fresh cloud summary adapter avoids
overwriting an active turn's transport continuation state. Failures/cancellation do
not select another provider. SessionSummary validation, provenance and original
history preservation remain unchanged. Routing itself has no memory write API.

ToolRegistry -> Policy -> ApprovalGate -> EffectLedger -> Android coordinator remains
the only capability path. A route supporting structured proposals is NOT approval
to execute. Local pseudo-tools, JSON and model statements remain text only.

## Inspector and telemetry limits

Provider settings contain a bounded Router inspector: current mode, last selected
backend/reason, five candidate evaluations, constraints, cloud prohibition and local
unavailability. It displays enum/count metadata only, never prompts, memory, model
output, endpoint URLs, configurable model IDs or credentials.

Network metadata is explicitly **user supplied**, transient and defaults UNKNOWN.
No ACCESS_NETWORK_STATE permission or connectivity probe is added. The user can
supply AVAILABLE/UNAVAILABLE/UNKNOWN for the foundation; it is not claimed measured
connectivity. Battery/thermal are UNKNOWN in production wiring; the domain accepts
trusted supplied resource states for later integration and host tests. No monitoring
service or hardware benchmark is introduced.

## Verification

**243 tests passed; 0 failures, 0 errors, 0 skipped:** kernel127, model88, app16,
Android-capability JVM12. Twenty-one new tests:11 pure-router and10 configured-provider
integration fixtures. Existing manual/Fake/SIMULATED, memory, summary, tool, approval,
streaming and continuation suites remain green. No Android instrumentation executed.

Coverage includes explicit/manual pins, pinned capabilities/turn/continuation,
private cloud rejection, offline/unknown network, missing local, local1024 boundary,
tools excluding local text, local-first deterministic ordering, known resource
constraints, UNKNOWN values retained, no eligible provider, default MANUAL, local
selection without credentials, failed-selection pinning, local failure without cloud,
cloud failure without local, independent summary pinning/failure, malicious source
remaining text, and bounded diagnostics excluding prompt/endpoint/key values.
Cloud failure uses an intercepting fixture that throws before network; existing
transport suites use local test servers, not real provider services.

Compile/unit/lint/assembleDebug/verifyDeviceTestSafety final serialized gate passed
in **2m49s**, after final reason-code and summary-adapter isolation review. Lint:
**0 errors / 4 warnings** (OldTargetApi, GradleDependency, two UseKtx). The preceding
gate also passed3m18s; counts above describe final XML. Existing JDK21, offline Gradle,
two workers/no parallel projects, in-process Kotlin, pinned NDK/CMake/Ninja retained.

APK app/build/outputs/apk/debug/app-debug.apk: **20,977,567 bytes**;
SHA256 **A8B28D5837414463ECB72BA3197B45FF7B77825F238822E34E4CCB77CEF2A0B0**.
Signer verified **42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2**.
Application dev.kinetic.app, versionCode1/versionName0.4.4-astra-stellar, min26/target36.
APK permissions: only INTERNET plus existing app-scoped non-exported receiver
signature permission. ZIP16KB alignment check exit0. No GGUF bundled; three existing
native-notice assets retained. ARM64 libkinetic_local.so3,987,376bytes, SHA256
**CCB1C6075A919D662D10A3995EED1EF2351FCFDA35DDE0697D1067173CCA18EE**:
byte-identical to Candidate3B completeness. Existing AndroidX UI ABI libraries retained.

No Roomv6 schema/manifest/native/build-signing change. Credential encryption,
decryption, aliases and ciphertext fields retained; only non-secret routing settings
and manual-mode reset were added to the existing settings store. No actual device
credentials or app data read or written. The workspace is not a Git repository;
no fabricated clean-diff claim is made. Source changes are scoped to router domain,
provider preparation/failure mapping and selection, settings/UI wiring, and tests.

**HYBRID ROUTER FOUNDATION IMPLEMENTED; HOST VERIFIED; MANUAL MODE REMAINS DEFAULT;
DEVICE/OWNER VALIDATION DEFERRED TO PHASE10A.** Production routing acceptance is not claimed.

## Deferred validation

Phase10A: real-device routing UX/persistence, actual network changes and credential
availability, native availability/streaming/cancellation, local resource behavior,
token boundary cases, provider switching between turns, cloud structured approval
and continuation, lifecycle/exactly-once/no-replay, Room/credential preservation,
accessibility/visuals and process logs. No new device evidence is claimed.

Phase5B remains host-complete with device validation deferred and its candidate
history intact; local is experimental, not a permanent production winner. Exactly
one next recommended task, not begun: **PHASE7A — BUILD-ONLY MCP ADAPTER / HOST
FOUNDATION**, requiring a subsequent owner instruction.
