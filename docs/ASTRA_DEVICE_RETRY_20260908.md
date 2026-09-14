# Astra / Stellar device retry — September 8, 2026

Status: **ENGINEERING DEVICE GATE PASSED; OWNER ACCEPTANCE PENDING**.
Completed September 9, 2026 after interrupted September 8 verification.

## Starting state and environment

Roadmap was read first. Starting gate: ENVIRONMENT BLOCKED — ROOT-CAUSE
DIAGNOSTIC COMPLETED. Phase 4C remains IMPLEMENTED — OWNER GOVERNANCE
ACCEPTANCE PENDING; Phase 5 NOT STARTED.

Verification window began **2026-09-08 01:40:31.899 PKT**. The same already-running
`Kinetic_API_36` AVD reported boot complete and boot ID
`f7e45119-59cb-4eb9-aa7d-7b21e7f510ac`. It was already running with
`-no-snapshot -feature -QuickbootFileBacked -verbose`; this task did not restart,
recreate, wipe or reconfigure its launch. Host available RAM was 3,752,096 KiB
(3,664.16 MiB); C: free space was 22,753,083,392 bytes (22.75 GB / 21.19 GiB).
Launcher, notification shade/System UI and system_server queries responded before
the engineering launch. Owner had already opened Kinetic at entry.

The retained Kinetic startup ANR at 01:40:05.407 (PID 5143) and System UI startup
ANR at 01:40:22.054 (PID 5170) both precede this window. They are historical,
not erased or counted as new. No new am_anr event was present at the 11:58 check
after continuation. Logs were not cleared. The long interrupted interval is not
claimed as continuous active UI observation.

Existing version: `0.4.4-astra-stellar`. Installed APK SHA-256:
`B4EB0D7019DDE57914C4CB2F9195D8DD79E4529BAD633126E593DC8C784DADE5`.
Certificate SHA-256:
`42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
The existing tooling debug keystore was independently verified to match; no new
key was generated. No application-ID change, data clear or uninstall occurred.

## Initial installed-build observations

- Ordinary launch: Status ok, WARM, 2,379 ms; usable Cloud/history restored afterward.
- Force-stop/cold launch: Status ok, COLD, 5,482 ms; usable Cloud/history restored.
- Explicit light/dark conversation, Memory and Context inspected; system bar
  contrast followed the palette. Follow system tracked Android night yes/no.
  Dynamic on was visually inspected in both palettes and restored off.
- Fake selected through existing Provider UI; no Save/clear/key edit or real
  provider request. Empty draft not sent during IME tests.
- Portrait docked IME: two-line text visible, composer and Send above keyboard.
  Rotation retained both draft lines; closing keyboard restored the full layout.
- Gboard initially used stylus/floating input. For the bounded docked-IME test,
  show_ime_with_hard_keyboard was temporarily changed 0→1 and absent
  stylus_handwriting_enabled temporarily set to 0. Both must be restored at finish.

## Reproducible Kinetic defect and bounded correction

At **11:54:08** and again **11:54:45 PKT**, landscape 2400×1080 / density 420,
font 1.0, with the docked Gboard keyboard open, hid the composer text and Send.
Close keyboard → controls return; reopen → same failure. Android remained
responsive and no new ANR accompanied it. Screenshots and fresh UI hierarchies
are retained in `.tooling/temp/kinetic-device-retry-20260908` outside the project.

Cause: KineticDeveloperScreen's non-scrolling Column reserved fixed header and
provider rows before the composer/actions; the IME-reduced height was smaller
than these fixed children. A weighted message list cannot repair that overflow.

Correction is confined to that screen plus one presentation JVM regression:
short available heights use a vertically scrollable outer Column and a bounded
160 dp message list. Normal-height layout is unchanged. The threshold accounts
for increased font scale. Fixed-size nested lists avoid unbounded lazy-list
measurement, consistent with [Android's layout guidance](https://developer.android.com/develop/ui/compose/lists#avoid-nesting-scrollable).
No provider, Room, governance, capability, manifest or version change.

The full safety/JVM/library-Android/lint/build gate passed, followed by a
same-signer update and successful live reproduction retest. In a short viewport,
the focused text is visible and scrolling reaches the full Send button above
the docked keyboard (`final-landscape-ime-bottom.png`). Closing the keyboard
restores the larger viewport; multiline text survives rotation. This is a
scroll-reachability guarantee, not a claim that every control fits simultaneously.

## Preservation baselines (read-only; no secret values copied)

- Room v6, quick_check ok; sessions 10, messages 80, memories 9, summaries 1,
  approvals 3, effects 7 before sending any new engineering message.
- Ordered memories plus summaries SHA-256 (sqlite insert-mode stream):
  `9ad9d802a0c1b655717290f31ded10a10cb79afb0f1b1ad66f206860dcaa1574`.
- All 80 ordered message rows SHA-256:
  `6bb6d06fe09b1ace30ec988266fedac6a2a3be0366b6eea3c59e82854ec7fefe`;
  maximum original createdAtEpochMillis `1788640800508`.
- Full logical dump immediately before correction update/tests:
  `cf9406de72bbcf294188449f94b93f6065450f7501dd2a46795c33fdccdf20c3`.
- Original Cloud provider XML SHA-256:
  `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
- Temporary Fake provider XML SHA-256:
  `427662c11bf0d9f8aa6e7d6e0d7fa8d6a8f9146a73310328c60d0ded20394e89`.

Governance source inspection retained explicit replacement/supersession,
transaction-only compatible legacy USER_EXPLICIT adoption, exclusion of
SUPERSEDED current retrieval, and conflict handling. No owner memory was changed.

## Final automated gate and update

| Suite | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| Kernel JVM | 95 | 0 | 0 | 0 |
| Model JVM | 47 | 0 | 0 | 0 |
| Capability JVM | 12 | 0 | 0 | 0 |
| App JVM | 16 | 0 | 0 | 0 |
| Room Android | 20 | 0 | 0 | 0 |
| Model/security Android | 13 | 0 | 0 | 0 |
| Capability Android | 17 | 0 | 0 | 0 |
| **Total** | **220** | **0** | **0** | **0** |

The changed app JVM suite and all 50 library Android tests executed; the three
unchanged JVM suites were Gradle UP-TO-DATE with their passing result XML retained.
The prior baseline was 219. No app-target instrumentation was added or run.
`verifyDeviceTestSafety` and its matcher self-test passed. Isolated fixture teardown
removed only its disposable library test packages, never `dev.kinetic.app`.

Offline, no-daemon, no-parallel, one-worker builds used the existing Gradle 8.13,
JDK 21 and SDK/cache. JVM/lint/assembly completed in 4m26s; Android lanes in 4m14s.
Lint: **0 errors, 5 unchanged warnings** (app 3, model 1, persistence 1).
`assembleDebug`: **BUILD SUCCESSFUL**. SDK XML and emulator-console auth warnings
were non-fatal; all fixture suites actually completed on emulator-5554.

Built and installed corrected APK SHA-256:
`5E39D26290350AD45D7B3D4FD3C5EEBC90E083362EFB7E042A1E0057C2C2D35F`.
apksigner verified the required certificate before `adb install -r`, which returned
**Success**. The installed hash matches the verified built APK. Version remains
`0.4.4-astra-stellar`, first install `2026-09-01 11:51:15`, update
`2026-09-08 12:05:56`. Full logical DB and temporary Fake-provider hashes above
matched immediately before and after update, and after library fixture testing.

## Final installed UI and lifecycle results

- Corrected cold launches returned Status ok: 7,565 ms after install; 10,375 ms
  after force-stop; 6,621 ms after restoring Cloud. All reached usable UI. These
  timings are not advertised as fast startup; no initialization timeout or ANR
  accompanied them. Background/foreground and Activity recreation were usable.
- Explicit Light and Stellar Dark approval/conversation controls and system bars
  were inspected on the corrected APK. Follow system tracked dark/light, and
  dynamic color worked in both. Memory and Context were inspected in light/dark
  before the layout-only correction; corrected Memory/Context/Debug navigation
  and approval surfaces were retested. Theme/provider code was not changed.
- Portrait 1080×2400, landscape 2400×1080, medium override 1800×2400 (~686 dp),
  and expanded 2400×1800 (~914 dp) were usable. Medium Memory/Context/Debug sheets
  and expanded conversation-plus-Memory pane were inspected. Conversation and
  approval details scroll; controls can be brought fully into view without
  horizontal overflow. The size override was reset.
- Portrait docked keyboard and multiline text passed. Landscape text and Send
  became reachable with scrolling after the correction. Keyboard close restores
  usable layout. The final unsent `IME_OK` draft was removed without submitting it.
- At font scale 2.0, composer remained usable, Memory edit/delete could be reached
  by scrolling, and exact approval/disclosure/actions remained scrollable and
  reachable in portrait/landscape. Full TalkBack and a wider device matrix are
  owner checks, not claimed here.

One new Fake request used exactly `https://example.com`. Approval
`approval-e9adc2b1-11e8-4142-8aac-2fb230bbbda8` and effect
`fake-call-turn-aac81886-2876-4ed1-8b73-5836e1ae5a74-open-url` stayed PENDING
through background/foreground, rotation, themes and resized-window inspection.
Context Inspector confirmed WAITING_FOR_APPROVAL; no URL dispatch occurred before
approval. Approve was tapped once. At **September 8 12:15:12.551 PKT**, the log
records exactly one URL VIEW START from Kinetic UID **10219**. At 12:15:13.673,
Chrome UID **10160** performed its own dispatcher-to-tab handoff; that is not a
second Kinetic dispatch. Chrome became top-resumed. Page completion was not required.

The first Back press remained inside Chrome; it is not represented as a successful
warm return. Subsequent explicit Kinetic relaunch restored COMPLETED without replay,
and Activity recreation did not replay. The final additional cold launch restored
Cloud/history. On September 9 at ~00:36, selecting the existing Chrome task from
Recents and returning to Kinetic produced a **HOT 742 ms** launch with the same
Kinetic PID 15385 and still only the original one UID-10219 URL dispatch. Thus both
warm external-task return and force-stop/relaunch were verified. Final approval is
APPROVED, effect COMPLETED, result **“HTTPS URL opened through Android.”** No claim
of completing work on the website is made.

## Final preservation, settings and audit

Room v6 / quick_check **ok**. Final counts: sessions **11**, messages **84**, turns
**23**, memories **9**, summaries **1**, approvals **4**, effects **8**. Additions
are exactly one engineering session/turn, four messages, one approval and one
effect. Both the full ordered original-80-message hash and the memory/summary
hash above match their baselines. No owner PostgreSQL/SQLite memory was rewritten.

Cloud mode was restored using **Use Cloud**, without Save/clear/key entry. Provider
XML returned exactly to the original `4D33…C505` digest and stayed identical after
the final cold launch. UI shows Cloud Ready and stored-key retention. Encrypted
provider state, endpoint/model and config are preserved; no real provider request,
plaintext-key export or credential copy was performed.

Restored and read back: Follow system, dynamic false, Android night no, font 1.0,
auto-rotation 1, user rotation 1, physical 1080×2400/no override, density 420,
hardware-keyboard IME setting 0 and stylus-handwriting override absent (`null`).
Kinetic is left open in portrait, Cloud Ready, with the completed engineering turn.
No Windows audio/registry/pagefile or AVD/SDK location changes were made.

Final host sample September 9 00:34:43 PKT: free RAM **2,485,896 KiB** (~2.37 GiB),
C: free **23,453,159,424 bytes** (~23.45 GB). QEMU PID 8856 and boot ID were
unchanged. The interrupted gaps are not claimed as continuously monitored UI time.

Process-specific bounded audit (retained main/system/crash logs, repeated event-log
checks and process-exit records):

| Owner | New verification ANRs | Historical evidence retained |
|---|---:|---|
| Kinetic | 0 | Sept 8 01:40:05 startup ANR; Sept 6 ANRs and two initialization timeouts remain documented |
| System UI | 0 newly detected | Sept 8 01:40:22 startup and 00:45:45 input ANRs; older Aug 30 records |
| Launcher | 0 | Sept 7 ANRs; older crash record |
| system_server / process-system | 0 observed | Earlier environment failures remain in historical reports |
| Chrome | 0 | Sept 5–6 ANR records remain |

Important timestamp distinction: System UI PID 5170's **ANR detection** was
01:40:22.054 (trace 01:40:22.123), before this window. Its delayed log report at
01:40:42.598 and termination at 01:40:43.249 were after window start but refer to
that exact same incident, not a new ANR. The two matching post-start system log
lines are retained and explained, not suppressed. Kinetic's two retained startup
markers are also pre-window (01:40:01.547 / 01:40:21.418).

Window-scoped audit found **0 Kinetic startup-timeout markers, 0 owned fatal
markers, 0 owned Room/SQLite/migration-error markers and 0 credential-pattern,
Authorization-header or Bearer-value markers**. Matching exited/current Kinetic
PIDs were used for ownership; fixture and Chrome processes were not attributed
to Kinetic. The same original browser dispatch remains the only one. This is
a bounded pattern audit, not a proof about every possible future secret format.
No log-clear command was run. Event buffers naturally rolled older entries out;
preflight evidence and process-exit/trace correlations preserve the historical facts.

Only two code files changed: KineticDeveloperScreen.kt and StellarPresentationTest.kt.
Documentation records the reproduction and result. The production-package safety
guard, signer, application ID, provider code, Room schema and governance repair
remain intact. Roadmap is the final workspace modification.

## Owner boundary

Phase 4C **IMPLEMENTED — OWNER GOVERNANCE ACCEPTANCE PENDING**; Part B accepted,
repaired Part A not performed on the owner's behalf. Phase 4 remains IN PROGRESS;
Phase 5 NOT STARTED. No owner acceptance is inferred from engineering success.

Exactly one owner acceptance flow recommended here: existing **Flow 3 — Stellar /
memory** in [ASTRA_OWNER_ACCEPTANCE.md](ASTRA_OWNER_ACCEPTANCE.md), including owner
accessibility review and the repaired Part A update/supersession retest.
Exactly one next task: have the owner run that flow and report pass/fail for
acceptance reconciliation. It was not begun. Other documented owner flows remain
pending; this retry does not silently accept them or authorize Phase 5.
