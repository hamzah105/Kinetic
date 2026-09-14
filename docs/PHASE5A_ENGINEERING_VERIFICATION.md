# Phase 5A engineering verification — 2026-09-09–10

Scope: architecture and simulated local inference, not a real local LLM or physical
performance gate. See [architecture/research](architecture/PHASE5_ON_DEVICE_AI.md).
**IMPLEMENTED; AUTOMATED GATE PASSED; DEVICE GATE BLOCKED.** No owner acceptance
or clean engineering device pass is inferred. The blocker is the new Chrome ANR
and Kinetic relaunch timeout described below; a Kinetic-owned root cause is not proved.

## Starting evidence

- Owner explicitly accepted repaired Phase 4C governance in the Phase 5A brief;
  Phase 4 is therefore complete. Separate Astra/Stellar owner gate remains pending.
- No Git metadata exists in this checkout. Changes are scoped by direct inspection;
  no reset/checkout, toolchain upgrade, new dependency or unrelated source edit.
- Same `Kinetic_API_36`, `emulator-5554`, x86_64; boot ID
  `f7e45119-59cb-4eb9-aa7d-7b21e7f510ac`, Android boot completed.
- Host available RAM sample: 3,116,248 KiB of 12,534,540 KiB visible.
- Existing installed version `0.4.4-astra-stellar`; first install September 1
  11:51:15, prior update September 8 12:05:56. Pulled installed APK signer matches
  `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
- Fresh pre-update Room: schema 6, quick_check `ok`, 14 sessions, 90 messages,
  9 memories, 1 summary, 4 approvals, 8 effects. Owner's recent governance changes
  are included in this fresh baseline, not compared against obsolete pre-acceptance data.
- Full read-only SQL dump hash:
  `B6223C8E9E7644E179FBD69DFADAEF4450050DF16470264D81739FBA7E9D0138`.
- Original messages (rowid <= 90, ordered by messageId) hash:
  `D399C87ECC92470C7B5532FE462BB949934C666C53255C41B86B2EEA6E244236`.
- Memories plus summaries (ordered by their IDs) hash:
  `79AF7DD48604A7256ED0FEBCB7DDBB2DBFCB3042526CCEE35C83A37311F052D9`.
- Provider preferences hash:
  `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
  Only hashes and counts were emitted; no credential was decrypted/exported.

## Automated gate checkpoint

The first run exposed a new test-fixture error: recovery was called on a completed
runtime instead of a newly constructed idle runtime. The test now reconstructs
the runtime over the same session/ledger. Production recovery code was unchanged.

Final JVM suites: kernel 116, model 49, capability 12, app 16 — 193 passed,
0 failed/errors/skipped. Android suites: Room 20, model/security/probe 14,
capability 17 — 51 passed. **244 passed, 0 failed, 0 errors, 0 skipped**, versus
the prior 220: 21 new kernel tests, 2 provider-selection tests and 1 Android probe
test. All seven suites have passing XML; all Android tests actually executed.
The known emulator-console authentication warning did not prevent library UTP
execution (14 model tests include the new probe test); both serialized Gradle
commands ended BUILD SUCCESSFUL.

`verifyDeviceTestSafety` passed. `lintDebug` passed with 0 errors / 5 unchanged
warnings (app 3, model 1, persistence 1, capability 0). `assembleDebug` passed.
New APK SHA-256:
`D0438351DB6E9EBA22459C1466256999D4D4E49487E43A888AA8062DC4C0A466`.
Built signer exactly matches the installed certificate above.

Serialized offline Gradle ran with one worker, no parallel execution and the existing
JDK/SDK/cache/debug key. Library-only Android instrumentation is used; the production
application is never a UTP target. No app data clear, uninstall, AVD restart/wipe,
signer replacement, application-ID change, network disable or real provider request.

## Device/log verification window

Fresh resumed verification window starts **2026-09-09 11:31:15 PKT**. Earlier work
and interrupted gaps are not described as continuous device monitoring.
Initial exit-info/log audit shows no new Kinetic/System UI/Launcher/Chrome/system
ANRs. Retained Kinetic ANRs (September 8 01:40 and September 6 23:23), earlier
initialization failures and historical System UI/Launcher/Chrome ANRs remain
historical. Logs were not cleared.

## Installed-build results

`adb install -r` succeeded September 9 11:42:45. Both the complete SQL dump hash
and provider-preferences hash matched immediately after update. First-install time
remained September 1 11:51:15; no reinstall/reset. Version name remains
`0.4.4-astra-stellar`; the APK hash above identifies this Phase 5A build.
Final installed `base.apk` SHA-256 also exactly matches the built APK hash above,
confirming the signer-verified artifact is the installed build.

| Check | Evidence / result |
|---|---|
| Initial launch | COLD, Status ok, TotalTime 11,720 ms; Cloud Ready and existing SQLite recall visible |
| Local selection | Provider selector displays SIMULATED / TEST; no cloud configuration save or credential mutation |
| Streaming/text | Incremental local text observed, then durable COMPLETED; first adb-injected greeting contained an extra `e`, so helper was tightened to verify exact fixture text before subsequent sends |
| Reject | Exact `protected demo` reached CONFIRM/PENDING; Reject persisted REJECTED and effect FAILED with approval_rejected; no execution |
| Recreation/background | URL approval remained the same PENDING identity after portrait/landscape/portrait and Home/return |
| Approve | One `open https://example.com` dispatch from Kinetic UID 10219 at 11:48:00.690 |
| Continuation | Durable result and simulated continuation state only that Android opened the URL; no page-load success claim |
| No replay | One completed local URL effect after return and force-stop/relaunch; no second Kinetic URL START |
| Relaunch | **Timeout**, adb WaitTime 15,139 ms; subsequently Displayed at 11:49:06.546 (+25,382 ms) and usable COMPLETED UI |
| Local Debug | API 36; reported ABIs x86_64 and arm64-v8a, RAM 2,593,075,200 bytes, heap class 192 MiB, available storage 8,729,120,768 bytes; emulator heuristic true |
| Support/performance disclosure | Native adapters absent, device/model support unprobed, benchmark NOT RUN; reported ABI list does not prove native ARM hardware or production inference viability |
| Final state | September 10 00:20+, same AVD/boot; Kinetic visible, Cloud Ready restored, no new provider request |

Lower approval controls were not visible in the initial landscape capture;
it proves lifecycle retention, not renewed acceptance of every Stellar layout.
Portrait approval controls were used. No separate owner UX acceptance is inferred.
Unavailable/malformed/cancellation behavior is covered by deterministic tests; no
additional failure-fixture device requests were made after the environment incident.

## Blocker and final process-specific audit

**One new Chrome ANR**, PID 18857: WindowManager detection 11:48:36.540,
`am_anr` 11:48:37.842, trace 11:48:38.373, ActivityManager report 11:48:57.302,
exit 11:48:58.314 on September 9. Reason: input dispatch timed out waiting for
FocusEvent(hasFocus=false). These are multiple records of ONE incident, not
multiple Chrome failures. It occurred after the approved browser handoff and
overlapped the slow Kinetic relaunch. UIAutomator returned null roots during this
interval; the helper refused stale taps and never repeated approval.

New Kinetic ANRs: **0**. New System UI, Launcher and system_server/process-system
ANRs found: **0 each**. Historical retained Kinetic ANRs and initialization failures,
System UI and Launcher ANRs remain classified separately; the old Chrome incident
was visible in the initial audit, although later exit-info retention only showed
the new one. `lastanr` saying none since boot is not sufficient: event/log/exit-info
prove the new Chrome ANR. The Chrome event is not relabeled historical merely
because the task resumed on September 10.

Final audit: 166,243 retained main/system/crash lines, 129,864 window lines,
11 Kinetic-PID log lines; owned fatal markers **0**, Room/SQLite error markers
**0**, window credential/header markers **0**. Kinetic startup-failure *log-pattern*
count is 0, but the separately observed adb timeout above is still a failure.
There are two ANR-report lines for the one Chrome incident. URL log shows the one
Kinetic START and Chrome's internal routing START from UID 10160; the latter is
not a second Kinetic dispatch. No logs were cleared and interrupted gaps were not
continuously monitored. Host RAM at incident follow-up: 3,037,620 KiB available.

No source patch was made for this incident. Temporal overlap does not establish
its root cause or justify a Kinetic code change. A clean stable-environment
installed-build retry remains necessary before claiming the engineering device gate.

## Final preservation

Room v6 / quick_check `ok`; final counts: 15 sessions, 98 messages, 9 memories,
1 summary, 6 approvals and 10 effects. The two new local effects are one rejected
FAILED and one approved COMPLETED. Original 90-message hash, memory/summary hash,
and restored provider-preferences hash exactly match the starting hashes above.
The added session/messages/approval/effect rows are the authorized synthetic smoke.
Provider ciphertext/configuration is byte-preserved; live credential decryption or
a paid request was not performed. Library-owned synthetic Keystore tests passed.
Rotation settings restored to accelerometer_rotation=1 and user_rotation=1;
font scale/network/audio/theme settings were not changed by this gate.

## Exactly one owner acceptance flow — after the engineering device blocker clears

In Provider, select **Use Local · SIMULATED / TEST**, start a new conversation and
send `hello`. Confirm the simulation label and response. Send `protected demo`
and Reject: nothing executes. Send `open https://example.com`; rotate and return
before approving, confirm it is still pending, then approve once. Return from the
browser, force-stop/relaunch Kinetic and confirm the result remains without reopening
the browser. Check Debug's unprobed/NOT RUN disclosures, then restore Use Cloud and
confirm Cloud Ready plus existing history/memory. Report this single flow pass/fail;
do not regard a real model or physical performance as tested.

Exactly one recommended next task (not begun): bounded non-destructive Android/
Chrome stability diagnosis and a clean retry of the existing Phase 5A installed-build
device gate. No Phase 5B, model download or Phase 6 routing is authorized by this report.
