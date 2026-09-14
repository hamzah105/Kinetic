# Phase 5A Android / Chrome stability diagnosis — 2026-09-10

**ENVIRONMENT BLOCKED — CHROME STANDALONE INSTABILITY PERSISTS.**
The conditional Kinetic clean device flow was not started. No Kinetic-owned defect
was proved and no source correction was made. The September 9 failed window in
[the original verification](PHASE5A_ENGINEERING_VERIFICATION.md) remains failed;
this is a separate diagnostic window, not a retrospective pass.

## Preflight and artifact

Roadmap was read first and completely, followed by the Phase 5A verification and
architecture documents. Starting state matched the owner brief: Phase 4C ACCEPTED,
Phase 4 COMPLETE, Phase 5 IN PROGRESS, Phase 5A IMPLEMENTED / AUTOMATED GATE PASSED /
DEVICE GATE BLOCKED, Phase 5B NOT STARTED. Separate Astra/Stellar engineering passed,
owner acceptance pending. No applicable AGENTS.md was found in the project.

Window: **2026-09-10 00:59:49–01:06:16 PKT**. Same online `emulator-5554`,
`Kinetic_API_36`, boot-complete, boot ID `f7e45119-59cb-4eb9-aa7d-7b21e7f510ac`.
No emulator restart, wipe, recreation, snapshot operation or SDK/AVD/project move.
Host available RAM **3,038,308 KiB** (about 2.90 GiB), OS-visible **12,534,540 KiB**;
C: free **19,222,593,536 bytes** (about 17.90 GiB). Read-only host CIM queries
required elevated sandbox access; no host setting was changed.

Installed version `0.4.4-astra-stellar`; first install September 1 11:51:15 and
last update September 9 11:42:45 unchanged. On-device base.apk SHA-256 and local
built APK SHA-256 both:
`D0438351DB6E9EBA22459C1466256999D4D4E49487E43A888AA8062DC4C0A466`.
Fresh apksigner verification of that identical artifact confirms signer:
`42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
No install, build, key generation or application-ID change occurred.

## Android and Chrome evidence

System_server answered service/package/input queries. Input dispatch was enabled,
not frozen, with empty inbound queues. Kinetic was initially top-resumed, PID 19911;
no new launch or conversation was performed. Home reached Launcher, confirmed by
fresh screenshot and subsequent successful UI hierarchy. Notification shade
expanded, produced a fresh hierarchy and collapsed normally. Initial UIAutomator
null roots were not treated as successful responsiveness evidence.

Chrome **133.0.6943.137** (last update August 28) initially had PID 20491, a timed-out
meminfo query, and a retained Chrome input channel marked unresponsive. At
**01:01:19.823**, Android killed PID 20491 for **excessive binder traffic during
cached** (exit reason 9, subreason 7; recorded RSS 171 MB). This resource-usage exit
is not counted as an ANR. The process was already gone before standalone launch;
no force-stop was used for that first launch.

| Diagnostic | Exact evidence / result |
|---|---|
| Standalone normal Chrome launch | MAIN + LAUNCHER, explicit Chrome launcher component, shell UID 2000; cold Status ok, TotalTime 12,862 ms, WaitTime 13,028 ms |
| Actual usability | Blank browser surface with address bar, no first-run/sign-in/update prompt visible; UIAutomator null root, meminfo timeout. Successful launch status did not establish usable Chrome |
| Ordinary address-bar focus | One tap at screenshot-confirmed address bar; **new am_anr 01:03:49.481**, PID **23449**, MotionEvent DOWN timeout, 5,004 ms |
| Additional first-process warning | WindowManager reported another focus timeout at **01:03:55.216**, 5,008 ms, during the same unresponsive process episode; retained separately, not hidden or counted as another am_anr record |
| One authorized restart | `am force-stop com.android.chrome` at 01:04:02; old process absent; normal MAIN/LAUNCHER start, new PID **23782**. No data/cache clear |
| Restart result | **Status timeout**, UNKNOWN launch state, WaitTime **20,055 ms**; UIAutomator again returned null root |
| Second process ANR | **am_anr 01:04:59.188**, PID **23782**, FocusEvent(hasFocus=true) timeout, 5,034 ms; trace 01:04:59.250, exit 01:05:01.631 |
| Final foreground | Home/Launcher top-resumed; Chrome main process absent in final pidof sample |

First ANR trace: `anr_2026-09-10-01-03-49-957.gz`; its exit reason is USER REQUESTED
because of the authorized force-stop. That exit label does **not** erase the ANR.
The audit therefore uses event-buffer evidence and trace references, not just
reason=ANR exit records. Five main/system ANR-report lines are not five independent
process incidents. There are **two am_anr incidents across two Chrome processes**,
plus the additional first-process WindowManager focus-timeout warning above.

No example.com navigation or control HTTPS VIEW intent was sent: both required
healthy Chrome, which was never established. No Kinetic request, approval or
external dispatch occurred in this retry. No account sign-in/default-browser change.

## Cause classification and stopping boundary

The reproducible observation is **Chrome standalone input/startup instability in
this Android environment**, including failure after one ordinary process restart.
Of the proposed classifications, E (another evidenced cause: persistent standalone
Chrome instability) is the closest observation-level label. It is not a resolved
internal root cause. A transient one-off explanation is insufficient for this window.
No new general Android ANR storm was observed, but host/guest pressure is not excluded.
Kinetic/external HTTPS dispatch is **not necessary** to reproduce the failure:
both launches originated from Android shell MAIN/LAUNCHER, before Kinetic testing.
This does not prove the precise cause of the historical September 9 Chrome incident
or its overlapping Kinetic relaunch timeout. No Kinetic lifecycle/input patch is justified.

The bounded permitted Chrome restart was exhausted. Under the explicit healthy-
environment condition, Kinetic baseline cold/relaunch, Local selection/streaming,
Reject, pending rotation, Approve, return/relaunch replay checks and Debug UI were
**NOT RUN in this window**. Their prior results remain prior evidence, not new passes.
Exactly **0 new Kinetic HTTPS dispatches** and no new effect/approval rows occurred;
this is not a fresh accepted-action exactly-once proof. No owner acceptance flow ran.

## Preservation and regression evidence

Before/after Room **v6**, `quick_check=ok`, counts **15 sessions / 98 messages /
9 memories / 1 summary / 6 approvals / 10 effects**. Original 90-message SQL hash:
`D399C87ECC92470C7B5532FE462BB949934C666C53255C41B86B2EEA6E244236`.
Sorted memories/summary SQL hash:
`79AF7DD48604A7256ED0FEBCB7DDBB2DBFCB3042526CCEE35C83A37311F052D9`.
Both match the prior verified checkpoint. No owner memory was changed or deleted.

Before/after provider preference SHA-256:
`4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
Read-only parsing emitted only CLOUD mode and ciphertext/IV presence booleans, never
their values. Cloud was never switched, so no restoration write was necessary.
Cloud Ready was the prior verified UI state, **not freshly visually certified** here.
Byte preservation does not newly prove credential decryptability or paid-provider use.
No provider request, credential decryption/export, data clear or uninstall occurred.

Unchanged source inspection confirms ConfiguredModelProvider selects Local before
either cloud credential accessor. Existing LocalProviderSelectionTest rejects both
credential accessors and HTTP, including pinned continuation; no automatic fallback.
Local remains SIMULATED / TEST, with no real model, download, JNI/NDK runtime,
automatic routing, permission expansion or Room migration. Existing kernel policy,
approval and ledger authority were not modified. Network independence remains
source/prior-test evidence, not a new live request/packet measurement.

Existing seven-suite XML was recounted: **244 passed, 0 failures, 0 errors, 0 skipped**.
No rerun is claimed. Prior lint **0 errors / 5 unchanged warnings**, successful
assemble and safety guard remain the baseline. No source/build/manifest/catalog
file outside generated directories has a timestamp newer than the verified APK.
There is no Git metadata, so this is supporting timestamp/artifact evidence, not
a certified clean Git diff. Only this report and roadmap were edited for this task.

## Final bounded log audit

No logs were cleared. Retained main/system/crash scan: **155,512 lines**, **8,615
window lines**, **7 current Kinetic PID lines**. Kinetic fatal markers **0**,
Room/SQLite/migration error markers **0**, credential/header markers **0**, Kinetic
startup-failure markers **0**. Additional system-log startup-timeout pattern scan
returned 0; the directly observed Chrome adb launch timeout remains a failure.

New retry-window ANRs: **Kinetic 0; Chrome 2 am_anr incidents; System UI 0;
Launcher 0; system_server/process-system 0; other Android 0**. The additional Chrome
WindowManager warning is described above. Historical Kinetic September 6/8 ANRs,
initialization failures, System UI/Launcher failures and Chrome September 9 ANR
remain historical relative to this new window, without converting their failed
windows into passes. No fresh Kinetic startup usability test is inferred from zero
Kinetic ANRs. This bounded marker audit is not an unrestricted security guarantee.

## Handoff

Phase 5A stays **IMPLEMENTED; AUTOMATED GATE PASSED; DEVICE GATE BLOCKED**.
Phase 5 IN PROGRESS; Phase 5B and Phase 6 NOT STARTED; Phase 4C ACCEPTED and Phase 4
COMPLETE. Separate Astra/Stellar owner acceptance stays pending. No owner flow is
issued for execution because the engineering gate did not pass.

Exactly one recommended next task, **not begun**: a bounded read-only diagnosis of
the two standalone Chrome ANR traces correlated with host/guest CPU and I/O evidence,
to identify the specific blocker before authorizing another device-gate attempt.
No Chrome update, reset, emulator change or Kinetic patch is implied or begun.

`roadmap.md` is updated after this report as the final workspace modification.
