# Owner strategy realignment — 2026-09-14

This owner directive supersedes repeated per-phase device acceptance prerequisites.
Build planned functional work with proportionate host compile/unit/lint checks.
Do not require a connected Samsung, ADB, or routine emulator starts. Track
implementation, host verification, deferred device verification and owner acceptance
separately. Never infer fresh device results from compilation or historical tests.
roadmap.md remains authoritative and is updated last after this task's build work.

## Current scope

Candidate3B: pinned llama.cpp v0.4.0 + verified Qwen3-0.6B Q4_0 behind the existing
LocalModelProvider. Native app-packaged code, verified model DATA, manual selection,
text streaming only. Not a production winner/default. No Phase6 automatic routing.
Candidate1 provenance blocker and Candidate2 device unavailability remain evidence.
Candidate3A classification B remains historical physical feasibility, not JNI proof.

## Future functional subphases — planned only

These refine future roadmap work, not authorization to implement them in Candidate3B.

- Phase8A — Safe configuration control: prompt-first proposals for essentially all safe
  settings/customization, typed allowlist, validation, preview/confirmation where
  appropriate, audit and reversibility. Never arbitrary preferences/file writes.
- Phase9A — Voice interaction: STT/TTS and voice-first UI; initially evaluate Deepgram BYOK,
  consent, cloud disclosure, retention, audio lifecycle and cost. No vendor/API
  suitability assumed without fresh research. No microphone permission added now.
- Phase7E — Secure onboarding/provider UX: manual secure UI only for secrets, API keys and
  login credentials; official Google OAuth flows/provider/model choice where suitable.
  No passwords/tokens through chat, memory or model-visible context.
  Google sign-in is not universal model entitlement: authenticate only through
  supported official flows, keep provider authorization and model availability
  separate, and store tokens in secure storage outside model context.
- Phase8A includes optional AI-guided tutorials/configuration, disableable conversationally through
  an allowlisted non-security setting. Disabling tutorials cannot disable policy.
- Phase8B — Inspectable personalization/scoped context analogous to soul.md/memory.md:
  governed records, explicit provenance/scope/version/inspection/delete controls,
  not executable instructions or blindly copied agent files. Preserve governed
  memory and distinguish preferences from immutable application security policy.
  Prefer canonical structured governed records with optional inspectable Markdown
  views/exports; persona, user preference, session and task scopes must stay distinct.
- Phase9B — Task-focused dynamic context activation: select only relevant scoped artifacts,
  explain inclusion/exclusion, bound token budgets, prevent cross-task leakage and
  stale-context authority. Retain durable canonical history outside active context.
- Phase8C — Broader Android agentic control only through ToolRegistry → Policy → ApprovalGate
  → EffectLedger → Android coordinator and explicit Android permissions/capabilities.
  Model text cannot change secrets, permissions, policy, approval or execute actions.

Owner subsequently supplied `D:\Hamza\PROMPT.txt`; read completely. It reinforces
these plans and illustrates physics-related activity across search, messaging and
video. The file ends mid-sentence. Its broader vision is roadmap input, not an
instruction to implement it now. Cross-app context observation needs a future
permission/privacy/capability design and explicit user control; no surveillance,
AccessibilityService or arbitrary Android control is authorized by this analogy.

## Phase10A — FINAL INTEGRATION & REAL-DEVICE VALIDATION GATE — DEFERRED

After planned functional build phases are implemented, with owner coordination:
real Android ABI/API/device support; signed APK native loading/16KB packaging;
model import/hash/storage/low-memory/thermal behavior; real streaming, UTF-8,
token budgets, cancellation, process death and resource cleanup; provider switches
and zero cloud fallback; Room/history/memory/summary and credential preservation;
policy/approval/exactly-once/no-replay; prompt-control adversarial tests; applicable
OAuth/voice privacy, permissions and interruption; light/dark/dynamic themes,
rotation/adaptive/IME/accessibility; process-specific new crash/ANR/LMKD audit and
real model quality/performance across representative tasks. Use isolated fixtures
and non-destructive signed updates. Owner acceptance stays separate and explicit.

Device-dependent claims are DEFERRED, not PASSED. This future gate is not begun.
Phase10B public-beta/release hardening follows it; no release readiness by inference.
