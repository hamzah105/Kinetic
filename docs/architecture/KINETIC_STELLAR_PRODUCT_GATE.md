# Kinetic Stellar product gate and late-2026 backlog

Unnumbered refinement, not a new roadmap functional phase. Original Kinetic visual work;
no OpenAI branding, logos, launch artwork or copied trade dress.

## Design and navigation

Compose Material 3 semantic roles define Kinetic Light and Stellar Dark; Follow system is
the default, with optional Android 12+ dynamic color. Dark uses a deep neutral background,
restrained periwinkle accent, muted teal and warm action surfaces. A sparse static three-star
mark appears only in the header, never behind conversation text. It has empty accessibility
semantics. There is no particle animation or motion requirement; chat position uses immediate
scrolling. Color/typography and 8/12/16dp spacing provide hierarchy without decorative density.
[Material 3 guidance](https://developer.android.com/develop/ui/compose/designsystems/material3).

The primary screen is conversation, compact tappable provider/profile status, the pending
approval card if any, and composer. Provider, Memory, Context, Appearance and Debug are
destinations behind Menu/sheets. New conversation, cancellation, all provider operations,
memory controls, manual compaction, summary metadata and the journal remain accessible.
No unsupported historical conversation-list feature is invented.

Approval cards retain exact-input review and explicit Approve/Reject. Each existing tool
has deterministic disclosure of its effect, data leaving Kinetic, and reversal limits. Browser
navigation may cause a network request; clipboard replaces prior content without automatic
undo; chooser/dialer/composer dispatch does not mean sharing/calling/sending occurred.

Memory is grouped by scope, then NEEDS ATTENTION (CONFLICTED), CURRENT (ACTIVE), and HISTORY
(SUPERSEDED). Existing edit/replace, resolution, per-row delete, deliberate USER clear,
session clear and provenance remain. Historical records do not appear current. Context's
"Why Kinetic knows this" uses planner metadata and summary provenance, not generated reasoning.

## Adaptive and accessibility boundary

Layout is driven by live Compose constraints: compact below 600dp, medium below 840dp,
expanded otherwise. A requested Memory/Context destination can become a 360dp supporting
pane at expanded width. Large font scales use the single-pane sheet layout. Chat content
has a bounded reading width, controls wrap instead of overflowing fixed rows, sheet content
scrolls, and approval cards are inside the conversation scroller. Composer respects IME and
safe drawing insets. A separate adaptive dependency is not justified for this small two-pane
layout; no toolchain upgrade is needed. Foldable hinge/posture-specific layouts remain future
work. [Window-size guidance](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes).

Material buttons/switches provide native interaction semantics and minimum targets; headings,
explicit switch descriptions and polite runtime status are supplied. Lifecycle and selection
states are textual, not color-only. Light/dark main text contrast is tested at >=4.5:1.
System font scaling, portrait/landscape, the expanded supporting pane and Activity recreation
were inspected on the existing emulator without app-target instrumentation. The final installed-build
system-bar, dynamic/medium-window and keyboard/IME gate passed September 9, 2026;
see [the exact engineering checkpoint](../ASTRA_ENGINEERING_VERIFICATION.md). Full owner TalkBack
usability and a wider device/foldable matrix cannot be replaced by automated semantic checks.
[Touch-target reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/minimumInteractiveComponentSize.modifier).

Live inspection found that an explicit dark app theme could inherit dark system-status icons
from a light device theme. Palette and system-bar contrast now share the same tested theme
selection, applied with WindowInsetsControllerCompat. The Activity explicitly requests
adjustResize for IME insets. [Android edge-to-edge guidance](https://developer.android.com/develop/ui/compose/system/setup-e2e).

The healthy-device retry also reproduced fixed-column overflow with a landscape docked
IME. When available height is below 360dp scaled for large text, the conversation column
now scrolls vertically, with its nested message list bounded to 160dp. Text, Send and
approval controls remain scroll-reachable even when they cannot all fit at once; normal
portrait behavior and execution semantics are unchanged. The corrected installed APK
passed multiline/IME, 200% font, approval and no-replay checks without owner-memory writes.

## Deferred user-selected input architecture

Images: future [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker) gives an explicit user selection and scoped URI grant
without camera or broad storage permission. A bounded attachment domain should record MIME,
size, dimensions, explicit outbound consent, lifecycle and source attribution; decode downscaled
with strict resource limits, do not persist arbitrary grants or EXIF by default. Only then
enable imageInput in the adapter capability profile and encode the selected image as untrusted
context. No selection or upload is implemented in this gate.

Documents: future [SAF ACTION_OPEN_DOCUMENT](https://developer.android.com/training/data-storage/shared/documents-files) with an explicit MIME allowlist and user selection;
no MANAGE_EXTERNAL_STORAGE, directory traversal or unrestricted filesystem. Validate size,
type, read errors and parsing budgets; extracted text is untrusted and cannot authorize tools.
Persist only user-approved grants/content with deletion semantics. No document parser or
file capability is implemented here. Both surfaces need separate authorization and tests.

## Prioritized backlog

| Priority | Candidate | Prerequisite / boundary |
|---|---|---|
| P0 this gate | Astra Responses; capability profiles; operating contract; provider conformance; local latency/cost metrics; Stellar theme and navigation | Complete engineering and owner gates; no new execution authority |
| P1 delivered foundation | Fast/Balanced/Deep; Why Kinetic knows this | Evaluate owner usability; deepen only after evidence |
| P1 | Photo Picker image input | Explicit selection, resource bounds, outbound consent, no broad storage |
| P1 | SAF document input | MIME allowlist, bounded parsing, untrusted text, deletion model |
| P1 | Context Lens explanations | Deterministic selected/omitted evidence only, no fake reasoning |
| P1 | Mid-turn steering | Specify cancellation, pending approval, exact-binding invalidation and process death first |
| P1 | Evaluation / scenario replay | Synthetic recorded provider streams, approval/effect traces, no automatic real side effects |
| P2 | Prompt-cache optimization | Measure cache writes, hit rate, latency, retention and total cost before changing prefixes |
| P2 | Explicit Research Mode | Opt-in data disclosure and new tool threat model; no silent hosted search |
| P2 | Memory Constellations | Optional inspectable visualization, never inferred governance or color-only state |
| P2 | Hybrid local/cloud routing | Requires actual accepted Phase 5 runtime/benchmarks; absent in this checkout |
| P3 | Allowlisted MCP connector gateway | Identity, schemas, least privilege, bounded results and Kinetic policy/approval |
| P3 | Async read-only architecture | Prove non-effectful tools, cancellation and result association before any concurrency |

Explicitly deferred: Android computer-use control, background autonomy, arbitrary shell,
hosted executable tools, autonomous multi-agent execution, broad permissions and embeddings
without measured retrieval inadequacy. No feature above authorizes a next phase.

Retrieval evaluation should use a versioned synthetic set of recency distractors, synonyms,
USER/SESSION isolation, legacy records, supersession, conflicts and stale summaries. Measure
selected-memory precision/recall, omissions, budget use and answer support versus lexical
baseline. Only measured shortfalls can justify a separately reviewed embedding experiment.
