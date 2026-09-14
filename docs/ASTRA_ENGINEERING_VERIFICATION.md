# Astra / Stellar engineering checkpoint — updated 2026-09-09

## Current boundary — engineering device gate passed

**ENGINEERING DEVICE GATE PASSED; OWNER ACCEPTANCE PENDING.** The same-AVD retry
after owner storage recovery completed; see [the exact September 8–9 evidence](ASTRA_DEVICE_RETRY_20260908.md).
A reproducible landscape/docked-IME clipping defect was corrected only in the
screen's short-height scroll behavior, with one JVM regression. Final total:
**220 passed, 0 failed/errors/skipped**; lint **0 errors, 5 warnings**; assembly,
safety guard, same-signer `adb install -r`, preservation and live retest passed.
The corrected installed APK is still version 0.4.4-astra-stellar, SHA-256
`5E39D26290350AD45D7B3D4FD3C5EEBC90E083362EFB7E042A1E0057C2C2D35F`.
No new Kinetic ANR/crash/Room error or replay was observed. Historical Android
ANRs are retained and correlated by detection/trace time, not erased. Original
80 messages, 9 memories, one summary and provider preferences matched hashes;
one approved Fake engineering turn was added. All temporary settings restored.
Phase 4C remains IMPLEMENTED — OWNER GOVERNANCE ACCEPTANCE PENDING; Phase 5 NOT STARTED.
The single next recommended task is owner Flow 3 (Stellar/memory), not begun.
All older blocked/resume instructions below describe historical attempts and
are superseded by this boundary; their evidence remains intact.

## Historical boundary — root-cause diagnostic completed

**ENVIRONMENT BLOCKED — ROOT-CAUSE DIAGNOSTIC COMPLETED.** See the
[bounded diagnostic report](AVD_STARTUP_ROOT_CAUSE_DIAGNOSTIC.md) for host/toolchain
measurements, exact boot/ANR evidence, failed renderer comparison and isolated control.
Primary classification **F. HOST MEMORY / PAGEFILE / DISK PRESSURE**, specifically
I/O starvation; confidence **MEDIUM**. This is not a proven exclusive cause or a
successful device gate. WHPX works; host commit headroom remains. The same-AVD
cold boot produced 28 new Android ANRs (one System UI, 27 other components), zero
Kinetic starts/ANRs. Guest I/O wait reached 80–82%. The isolated control stayed
ADB-offline for 328 seconds with HDD queue up to 102 and latency up to 415 ms.
Host graphics mode failed before ADB; no permanent renderer change was made.

Exactly one next repair is recommended, **not authorized/executed by this diagnosis**:
one temporary same-AVD cold launch using `-no-snapshot -feature -QuickbootFileBacked`
to remove the evidenced HDD-backed RAM mapping, leaving renderer/RAM/cores/userdata
unchanged. Its benefit remains unproven. No snapshot deletion or Windows change.
The real AVD is not running; the isolated control was stopped and retained. Room
and provider hashes matched before/after the real baseline boot. Kinetic was not
launched, rebuilt, installed, cleared, or modified. A late signer recheck stalled;
the prior verified signer remains the continuity baseline, not a new verification.
Prior 219-test/lint/build results below are historical and were not rerun.
Phase 4C remains IMPLEMENTED — OWNER GOVERNANCE ACCEPTANCE PENDING; Phase 5 NOT STARTED.
All older resume instructions below are superseded by this diagnostic boundary.

## Controlled same-AVD restart — instability persists

**ENVIRONMENT BLOCKED — AVD / ANDROID SYSTEM INSTABILITY PERSISTS.** This later
attempt performed the explicitly requested normal shutdown and fresh restart, then
stopped before exercising Kinetic when new Android system ANRs appeared.

### Restart identity and preservation

- At 04:39:36 PKT, Windows available physical RAM was **4,302 MiB** of 12,241 MiB
  (64% load); during boot at 04:43:16 it was **4,575 MiB** (62% load).
- Same AVD: `Kinetic_API_36`, API 36, existing path
  `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd`; 2 GiB guest RAM and two CPUs.
  AVD locator and config hashes matched before/after; neither file was edited.
- Normal `adb emu kill` succeeded using the owner's console authentication after
  the sandbox user's token was rejected. Old emulator PID **14552** and QEMU PID
  **17980** both terminated, and ADB listed no device before restart.
- Restart launched at **04:40:34.751 PKT**, emulator PID **19544**, QEMU PID **18176**.
  Command: `emulator.exe -avd Kinetic_API_36 -no-snapshot-load`, with the same AVD
  root and SDK. The one-launch option avoids restoring unhealthy RAM state; no
  snapshots/userdata were deleted, no AVD was recreated, and audio settings were unchanged.
- Boot ID changed from `695af1d2-ddcb-4039-ad19-a2af5737a018` to
  `f296d4db-c2e2-4d71-8b6e-4acb8c3197a0`. ADB was online by 04:43:30;
  `sys.boot_completed=1` was observed at 04:45:43. This observation is not an exact
  boot-duration benchmark. Initial offline transport was refreshed without restarting ADB's server.
- Installed version remains `0.4.4-astra-stellar`; first install `2026-09-01 11:51:15`,
  last update `2026-09-06 12:45:41`. On-device APK SHA-256 still matches
  `B4EB0D7019DDE57914C4CB2F9195D8DD79E4529BAD633126E593DC8C784DADE5`.
  The matching previously pulled APK was reverified with apksigner:
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
- Before and after restart the raw database SHA-256 is identical:
  `424997FC54F4501AE2A6FD271F900B2486ABF4B86252E8E770BA9F3771111D73`;
  WAL length is zero at both samples. This establishes byte-for-byte database
  preservation across the restart, including its stored history/memory/summary/effect
  data; it is not a new Room integrity, relational-count or application usability test.
- Provider settings are also byte-for-byte unchanged at
  `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
  CLOUD, ciphertext and IV remain present; no known provider-key plaintext prefix was
  found in the preference scan. No plaintext was printed or real provider called.
  Credential decryption/use was not exercised while Android was unstable.

### New boot evidence, separate from historical ANRs

The new boot's event sample contained **1,046 lines through 04:46:26 PKT**, with
zero Kinetic process starts. `pidof dev.kinetic.app` returned no PID. No Kinetic
launch, UI tap, force-stop, rotation, theme change or capability proposal was performed.

| Process | New ANRs in that sample |
|---|---:|
| Kinetic | 0 — not exercised |
| System UI | 1 |
| Pixel Launcher | 0 |
| system_server / process named system | 0 |
| Chrome | 0 |
| Other Android components | 10 |

Exact new ANRs:

- 04:44:56.292 — **com.android.systemui**, PID 1086, failed to complete startup.
- 04:45:11.718 — com.android.phone, PID 1212, failed startup.
- 04:45:15.004 — com.android.settings, PID 1276, failed startup.
- 04:45:51.163 — permissioncontroller, PID 1519, safety-source broadcast timeout.
- 04:45:58.841 — wellbeing, PID 1590, failed startup.
- 04:46:05.907 — gms.persistent, PID 1523, failed startup.
- 04:46:12.525 — inputmethod.latin, PID 1558, failed startup.
- 04:46:13.086 — media.module, PID 1658, failed startup.
- 04:46:20.364 — android.as, PID 1765, failed startup.
- 04:46:20.888 — googlequicksearchbox:interactor, PID 1769, failed startup.
- 04:46:21.686 — com.android.phone, PID 1387, SIM-state broadcast timeout.

System_server PID 787 answered Activity/package queries later in boot and Launcher
was top-resumed, but top-resumed does not establish usable/responsive UI. System UI's
new startup ANR alone prevents this from being a valid device-gate window. The failures
precede any Kinetic execution and show Android environment instability independently
of exercising Kinetic; the underlying host/emulator/Android cause remains unproven.

Historical Kinetic ANRs (September 6 startup and September 7 03:16:06 input dispatch),
old startup timeouts and old Launcher/system/Chrome failures remain recorded below.
They are not counted as new restart-window failures. No log-clear command was used.

A later bounded main/system/crash scan returned 11,638 lines, with zero matched Kinetic
fatal-process/startup-failure, Room/SQLite/migration and credential/header markers.
Activity query at 04:46:44 still showed Launcher top-resumed. These limited scans do
not supersede the event-buffer ANRs or constitute a successful startup/security gate.

### Outcome and boundary

Repeated Kinetic launch, recreation/relaunch, all visual/theme/system-bar/adaptive checks,
IME/multiline/large-text/accessibility, approval/exactly-once/no-replay checks are
**NOT RUN — post-restart environment blocked**. No source/config change, build,
installation, instrumentation, provider request, owner memory write or owner acceptance
occurred. Prior 219-test and lint/build baseline is unchanged, not rerun. Windows
Realtek/audio/registry/device settings were untouched.

The next task is a bounded read-only diagnosis of the existing AVD's Android startup
bottleneck using host virtualization/resource and emulator/system-server timing evidence.
Do not substitute another blind restart, wipe/recreate the AVD, patch Kinetic without
evidence, or begin Phase 5. All owner flows remain deferred until the engineering gate
passes; Phase 4C Part A is still owner-controlled.

## Earlier installed-build preflight — ENVIRONMENT BLOCKED

The 2026-09-07 device-gate attempt stopped at the user's environment fail-closed rule.
No visual tapping, controlled launch/relaunch, rotation, approval, provider switch or
memory write was performed. The prior engineering evidence below remains historical;
none of its incomplete checks is promoted to a pass.

- Windows GlobalMemoryStatusEx at **04:28:24 PKT**: **4,658 MiB available** of
  **12,241 MiB total**, memory load **61%**. The CIM probe was denied; the native
  read-only probe succeeded. Host RAM was not critically low at this sample.
- ADB confirmed `Kinetic_API_36`, serial `emulator-5554`, boot-complete `1`.
  Boot-complete is not evidence of responsive Android services. Several dumpsys probes
  yielded no usable diagnosis, and a read-only screenshot request stalled and was
  cancelled without obtaining a visual result.
- Retained events show a **Kinetic-owned input-dispatch ANR**, PID 3320, at
  **03:16:06.034 on September 7** (6700 ms motion-event wait). This predates this
  preflight; it is neither a controlled startup test nor evidence of an app-only cause.
- Pixel Launcher ANRs: **03:15:28.088**, **03:18:40.103** (failed startup), and
  **04:27:40.607**. System-process ANRs, PID 801: **03:17:34.796**, **03:19:31.139**,
  and **04:28:23.308**. The latter was a focus-event timeout for the Kinetic ANR
  dialog: the process owning that ANR is **system**, not Kinetic or System UI.
- The retained 6,736-line event sample contained Kinetic ANR 1, Launcher ANR 3,
  system ANR 3, Chrome ANR 1, System UI ANR 0, and Kinetic crash 0. Chrome's
  startup ANR was **September 6 at 23:36:40.947**; other historical service/IME
  failures were also present. These counts are bounded retained-log observations,
  not counts from a new successful test window. Historical System UI failures remain
  recorded below. The current instability does not establish its root cause; adequate
  current host RAM neither excludes earlier pressure nor proves a Kinetic defect.
- Pulled the actual installed APK read-only to
  `E:\Projects\.tooling\temp\kinetic-stability-installed-20260907.apk`.
  aapt confirms `dev.kinetic.app`, version `0.4.4-astra-stellar`, and only INTERNET
  plus the generated app-scoped signature permission. No microphone permission.
  APK SHA-256 is still
  `B4EB0D7019DDE57914C4CB2F9195D8DD79E4529BAD633126E593DC8C784DADE5`;
  apksigner verifies the installed certificate as
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
- Read-only provider inspection confirms `CLOUD`, ciphertext present, IV present,
  and the unchanged original preferences SHA-256
  `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
  No plaintext key values were printed. The preference key-prefix scan found no
  `sk-or-v1-` or `sk-proj-` plaintext; this is not a decryption/real-provider test.
- The existing Room database is present (327,680 bytes, WAL length 0 at inspection),
  raw-file SHA-256 `424997FC54F4501AE2A6FD271F900B2486ABF4B86252E8E770BA9F3771111D73`.
  This raw-file hash is not the earlier logical-dump hash. No new relational counts,
  integrity/migration check, memory/summary audit or UI-preservation pass is claimed.
  No application data was modified by the diagnostic commands.
- The bounded main/system/crash sample returned 10,474 lines through 04:32:00:
  zero matched Kinetic fatal-process markers, Kinetic start-timeout markers,
  Room/SQLite exception/migration markers, and Authorization/Bearer/provider-key
  markers. This does not negate the ANRs positively found in the event buffer or
  establish a clean startup, no-replay or exhaustive security gate. Logs were not cleared.
- Existing kernel and Room passing XML cases for legacy adoption, existing-value
  replacement, conflict preservation, lower-authority protection and database reopen
  are still present. Installed APK identity ties this attempt to that repair artifact.
  Owner Part A was not performed; Part B acceptance remains recorded.
- No source/config change, build, install, instrumentation, destructive recovery,
  host-app closure, audio-device/driver/registry change, or provider request occurred.
  No Windows audio-dialog correlation was established; no emulator startup was
  attempted in this window. Tests/lint were not rerun: prior baseline remains
  219 passed/0 failed/errors/skipped and lint 0 errors/5 warnings.

All controlled startup, light/dark/system/dynamic, medium/adaptive, keyboard/IME,
large-text and Action Review/no-replay checks remain **NOT RUN — ENVIRONMENT BLOCKED**
for this attempt. The next task is a non-destructive restart of the same AVD and a rerun
of this installed-build gate, beginning with health preflight; stop again if repeated
system ANRs persist. Do not wipe, reinstall, clear data, infer owner acceptance or start
Phase 5. Owner acceptance remains deferred until a valid engineering window passes.

## Historical checkpoint — 2026-09-06

Implementation, automated suites, lint, build, signer continuity and in-place update are
verified. **Final installed-build visual/startup gate is BLOCKED by the current emulator
condition. Owner acceptance is PENDING.** This is not an accepted functional phase and does
not authorize Phase 5. Do not uninstall, clear data, replace signing keys or run production
app instrumentation to recover the device.

## Canonical baseline and repair

The roadmap and checkout establish Phase 4A/B accepted, Phase 4C owner governance acceptance
pending and Phase 5 NOT STARTED. No Phase 5 runtime/provider/benchmark/test artifact exists.
There is no Git metadata in this checkout. Existing repair edits were preserved; no source
file changed after the successful final build.

The pre-gate source/report baseline was 181 passing tests (91/22/12/10 JVM; 20/9/17 device),
not the roadmap's historical 174-test Phase 4C delivery. The device initially still ran
0.4.2-phase4c-governance. Owner Part B conflict/resolution passed; Part A legacy PostgreSQL
update/supersession failed. The narrow legacy adoption repair is now included in the installed
APK and tested; its owner Part A retest is still pending. Installation never adopts or
supersedes historical rows. Only a future explicit compatible governance transaction does so.

## Final serialized automated gate

Final invocation used installed Gradle 8.13, JDK 21, offline dependencies, one worker and
in-process Kotlin compilation. It invoked verifyDeviceTestSafety, all seven established
test lanes, all four lintDebug tasks and app assembleDebug. The daemon log records
**BUILD SUCCESSFUL in 9m 3s**, 292 actionable tasks (28 executed, 264 up-to-date).
All three library device lanes ran again: 20, 13 and 17 tests. No production-app connected
instrumentation ran, and the safety guard remained intact.

| Lane | Tests | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| Kernel JVM | 95 | 0 | 0 | 0 |
| Model JVM | 47 | 0 | 0 | 0 |
| Android-capability JVM | 12 | 0 | 0 | 0 |
| App JVM | 15 | 0 | 0 | 0 |
| Room Android | 20 | 0 | 0 | 0 |
| Model/security Android | 13 | 0 | 0 | 0 |
| Android-capability Android | 17 | 0 | 0 | 0 |
| **Total** | **219** | **0** | **0** | **0** |

The preceding 218-test full gate passed too. Live inspection then found dark app/system-bar
contrast mismatch; a shared theme decision, system-bar update and fifth Stellar regression
test fixed it. Explicit adjustResize accompanies the existing IME padding. Final visual
confirmation of those last changes remains outstanding; tests alone are not claimed as
visual acceptance.

Lint: **0 errors, 5 warnings** — app 3 (OldTargetApi, two UseKtx), model 1 (ApplySharedPref),
persistence 1 (KaptUsageInsteadOfKsp), capabilities 0. The existing SDK XML-version warning
also appeared during the build. No toolchain/target upgrade or credential durability change
was made merely to silence warnings.

## Final APK, signer and installation

- Version: `0.4.4-astra-stellar`; application ID unchanged: `dev.kinetic.app`.
- APK: `E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk`.
- Size: 12,901,230 bytes.
- Built **and pulled installed** APK SHA-256:
  `B4EB0D7019DDE57914C4CB2F9195D8DD79E4529BAD633126E593DC8C784DADE5`.
- Required installed-before and built certificate SHA-256 both verified before update:
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
- `adb install -r`: **Success**. Installed APK hash equals the final build.
- First install remains `2026-09-01 11:51:15`; final last update `2026-09-06 12:45:41`.
- No uninstall, app-data clear, signer replacement, signing bypass or application-ID change.
- Final APK permissions: INTERNET and generated app-scoped dynamic-receiver signature
  permission only. No dangerous permissions, services or background agent execution added.

## Preservation evidence

Before the first gate upgrade, the full logical Room dump hash was
`59E3142B4B719BBDC1D5D75CBF2568B40AF5D16570D04F81F9A17F8661969EBF`;
it was identical immediately after that update, before engineering conversation tests.

Before and again **after Success** for the final update, the logical database dump hash was
`0A6F152FD3CDE0FEF4FDAFA50CA1D81331DA0FAE56A62E8739833767E17E4384`.
No rows changed across this update. Final schema remains v6: sessions 10, messages 80,
turns 22, memories 9, summaries 1, approvals 3, effects 7. Added engineering history consists
of the harmless Fake echo and rejected/approved example.com requests; owner history was
not removed or rewritten.

The sorted memory-plus-summary digest remains exactly the original gate baseline:
`68C70006F8440FEF3117B47295BB7A41F3AD3ECAF8E1A21F024CDE45C1BE5EDA`.
Seven ACTIVE and two SUPERSEDED memories remain, including the deliberately unmodified
legacy PostgreSQL row awaiting the explicit owner repair retest.

Provider preferences returned to their original SHA-256 after restoring Cloud mode:
`4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
This hash also remained identical after the final update. OpenRouter endpoint, model,
structured-tool setting and encrypted credential pair are retained. The compatible UI
showed a retained stored key; direct OpenAI showed no stored key. No real OpenAI key was
entered, no credential was copied and no real provider request was made during engineering.
Only hashes, counts and non-secret UI metadata were inspected for production credentials.

## Live results and precise limitations

On the preceding installed gate build:

- Fake echo completed with a genuine tool result and continuation; persisted through cold boot.
- Browser Reject produced a durable REJECTED approval, failed/nonexecuted effect and zero
  Kinetic browser launches.
- A second pending approval survived background/foreground and rotation with no execution.
  Approve dispatched once; Chrome became foreground and the durable result truthfully says
  Android opened the URL, not that a web task completed.
- Return, Activity recreation and force-stop/relaunch did not duplicate the effect. Final
  log audit found one example.com START from Kinetic UID 10219. A separate START from Chrome
  UID 10160 is its internal IntentDispatcher-to-tab handoff, not Kinetic replay.
- Light/dark/system selection, 200% font portrait/landscape, scrollable approval details,
  expanded Memory supporting pane, CURRENT/HISTORY/provenance/actions and separate
  Context/Debug surfaces were inspected. All memory and summary rows stayed unchanged.
- Dynamic-color controls were exercised; stable dynamic visual review, medium-window review,
  keyboard traversal/IME and the final system-bar correction still need completion. Full
  TalkBack usability remains an owner check, not an automated semantic-test claim.

**The full device audit is not clean.** System/process-system, System UI, Pixel Launcher and
Chrome ANR dialogs occurred. Chrome page loading is not a verified pass. At `01:42:21`, logcat
recorded one Kinetic ANR: PID 8088 failed to complete startup. The report included load
19.84/12.68/9.44 and severe guest memory pressure (full avg60 32.37). This preceded the final
system-bar patch. After the final install, exit-info recorded initialization/start timeouts
at `12:49:20` and `12:53:29`. Windows available memory was measured at 367 MB (later 590 MB).
Resource pressure is a strong suspected contributor, not a proven exclusion of app defects.
Do not report zero Kinetic ANRs or a successful final installed-build UI gate.
On the last retry Kinetic did become top-resumed (PID 16097), but recurring Pixel Launcher
ANR dialogs interrupted navigation before the final Appearance checks could be performed.
No additional source change or provider request was made during these recovery attempts.

The bounded log marker audit found zero Kinetic fatal-process markers, zero Room/SQLite
exception or migration-integrity markers, and zero credential-value/Authorization markers.
This is a bounded audit, not proof that arbitrary future content can never leak. The startup
ANR/timeouts above remain explicit exceptions. No error logs were cleared to obtain a pass.

Display settings were restored: font scale 1.0, auto-rotation 1, user rotation 1, physical
1080x2400/no size override, density 420; hardware-keyboard IME setting remains original 0.
Cloud provider mode is restored. No other host application was closed to free resources.

## Exact resume boundary

Free sufficient host RAM using owner-chosen applications, then resume only the final
installed-build startup/visual gate on the same AVD without wiping or uninstalling. Inspect
the existing final APK first; do not rebuild or reimplement unless evidence requires it.
Verify dark system bars, dynamic color, medium window, keyboard/IME and a fresh healthy
startup/relaunch/no-replay audit. Keep the ANR history above; only a new successful verification
window can close the outstanding device gate. Then update roadmap.md last again.

Owner provider/tool/Stellar acceptance follows this engineering gate using
[the three existing flows](ASTRA_OWNER_ACCEPTANCE.md). Real keys go only into Kinetic.
Phase 4C Part A and this gate are not owner accepted. Phase 5 remains NOT STARTED.
