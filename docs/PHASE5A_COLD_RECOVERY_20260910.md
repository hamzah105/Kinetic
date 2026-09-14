# Phase 5A authorized same-AVD cold recovery — 2026-09-10

**ENVIRONMENT BLOCKED — NEW-BOOT ANDROID SYSTEM INSTABILITY.**
The one owner-authorized cold start was performed. The Android health prerequisite
failed before Chrome or Kinetic testing. No second restart or repair was attempted.
The earlier September 9/10 failed windows remain failed historical evidence.

## Authority and preserved launch

Roadmap read first. Starting status: Phase 4 COMPLETE / 4C ACCEPTED; Phase 5
IN PROGRESS; Phase 5A IMPLEMENTED / AUTOMATED GATE PASSED / DEVICE GATE BLOCKED;
Phase 5B NOT STARTED. Separate Astra/Stellar owner acceptance remains pending.
Owner reported closing VS Code and Emulator and authorized exactly one same-AVD
cold restart. Neither VS Code nor Android Studio was opened; unrelated apps were
not closed. No source, build, install, renderer or host-setting change was made.

Preflight found no emulator/QEMU process and no ADB device. The AVD locator resolves
to `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd`, API 36 Google APIs x86_64,
Pixel 7, configured two CPUs/2G RAM, 1080x2400/420 dpi, unchanged GPU auto settings.
Locator/config SHA-256 before AND after:

- `F1640E33E6F0F0E0ECFD2D95E3FB9F55F95AC83AC5FD7866A1594AD3A77D7BC6`
- `D64BD0D0EACB3F2869E16D73C89BABC2FABFDFC8326D5EFD18A922E575CDDF4C`

Exact command, with existing AVD/SDK environment paths:

```text
E:\Projects\.tooling\android-sdk\emulator\emulator.exe -avd Kinetic_API_36 -no-snapshot -feature -QuickbootFileBacked -verbose
```

No snapshot load/save, userdata deletion, snapshot deletion, config.ini edit,
renderer/backend experiment, resolution/RAM/core change or AVD move. Emulator
process PID **10192**, QEMU **12524**. Existing established mitigation was retained.

## Timing — sampled observations, PKT

| Milestone | Timestamp / outcome |
|---|---|
| Start command | 12:03:16.814; emulator process 12:03:17, QEMU 12:03:18 |
| First observed ADB transport | 12:03:39.832, offline |
| First observed online | **12:04:31.445**, about 75 seconds after command |
| New boot ID | `729b9ff6-0510-4481-8426-e86382d82187` |
| First observed sys.boot_completed=1 | **12:06:37.745**, about 201 seconds after command |
| Launcher top-resumed bookkeeping | 12:06:40 probe; **usable input not certified** |
| System UI usable | **Not established: startup ANR preceded boot completion** |
| Interactive testing stop | Failure observed 12:06:40; no Chrome/Kinetic launch |
| Bounded final event/log cutoff | **12:08:44.881**; last sampled ANR 12:08:42.303 |

Android was not launched straight into app tests on boot completion. Its explicit
fail-closed health condition was already unsatisfied, so no successful stabilization
or interactive usability interval is claimed. Later commands only collected read-only
failure/preservation evidence. Emulator was left running; no additional restart,
force-stop, or automatic repair was performed. Counts are bounded to the cutoff,
not a promise that an unhealthy running guest cannot generate later events.

## Host state

| Sample | Available RAM | CPU | Committed / limit | E: queue; read/write latency |
|---|---:|---:|---|---|
| Before launch, 12:02:43 | 4,216,444 KiB (~4.02 GiB); performance sample 4,082 MiB | 7% | 11,137,146,880 / 20,140,552,192 bytes | Not sampled |
| During boot, 12:05:04 | 2,917 MiB | 85% | 14,922,588,160 / 20,140,552,192 bytes | 0; 4.60 / 0.14 ms |
| Failure follow-up, 12:07:26 | **665 MiB** | **93%** | 15,785,263,104 / 20,140,552,192 bytes | 1; 10.89 / 76.90 ms |

Prelaunch C: free **21,459,722,240 bytes**, E: **122,009,284,608 bytes**.
The initial lower-load state was verified, but did not persist during boot.
Physical memory headroom became low; commit limit was not exhausted. These samples
do not identify which host process caused the RAM decline. No host app closure,
pagefile, GPU driver, Windows audio or storage configuration change was attempted.

## New Android failures and pressure

First failures, all **failed to complete startup**:

- **12:06:16.333 — System UI, PID 1068**
- **12:06:20.320 — Phone, PID 1192**
- **12:06:23.723 — Settings, PID 1271**

System_server PID 781 later answered service/input queries. InputDispatcher was
enabled and not frozen, with no pending/inbound events. These responses and Launcher
top-resumed bookkeeping do not override the failed System UI startup or certify
interactive health. Chrome and Kinetic had no PID at the checked point.

System UI ANR report at **12:06:18.011**:

- Load **10.45 / 4.49 / 1.70**.
- CPU PSI some avg10 **75.53%**.
- Memory PSI some/full avg10 **0.46 / 0.00%**.
- I/O PSI some/full avg10 **77.78 / 9.88%**.
- CPU interval **12:05:56.229–12:06:16.371**: graphics composer ranchu PID 532
  **93%** (92% kernel); SurfaceFlinger **5%**; guest total **98%**, including
  **78% kernel and 12% iowait**. Short following sample composer **92%**.

This is relevant new graphics/CPU pressure evidence, not a newly captured Chrome
`eglSwapBuffers` stack. No Chrome launch occurred to reproduce the old dependency.

Later ANR report at **12:08:11.345** records CPU PSI some **45.55%**, memory
some/full **8.48/4.87%**, I/O some/full **97.66/43.65%**, and guest total **100%**,
including **67% iowait**, 25% kernel, 6.6% user. Thus new I/O pressure emerged too;
the preceding read-only Chrome diagnostic's later healthy-disk samples are not
substituted for this new boot. These observations do not uniquely isolate the host
cause or justify an application patch.

A guest follow-up sample showed load **21.54/8.55/3.26**, available memory
**1,517,068 KiB**, swap total/free both **1,899,216 KiB**. Guest memory availability
does not establish adequate host resources. PSI values above come from timestamped
ANR reports; no permission bypass was used to read protected `/proc/pressure` files.

### Bounded new-boot ANR counts

| Category | New events through 12:08:44 |
|---|---:|
| Kinetic | **0**, not launched |
| Chrome | **0**, not launched |
| System UI | **1** |
| Launcher | **0** |
| system_server / process-system | **0** |
| Other Android | **27** |
| Total | **28** |

Other process counts: phone 1, Settings 1, keyboard 2, android.as 2, media module 1,
wellbeing 2, GMS persistent 1, search interactor 1, messaging RCS 1, print spooler 1,
GMS 1, settings intelligence 2, Photos 2, search 2, keychain 1, contacts 2, Gmail 1,
dialer 3. These are events, not distinct-app counts. Most are startup failures;
GMS includes a SIM-state broadcast timeout and search includes a 20,002-ms service
timeout. Historical retained DropBox entries from September 7/9/earlier September
10 were not counted. Logcat was never cleared; ordinary cold boot starts a new
volatile logging lifetime, while persisted history remains available.

## Conditional checks not run

Standalone Chrome, example.com, Kinetic initial/cold launch, Local selection,
streaming, protected Reject, pending rotation, Approve, browser handoff, return,
force-stop/relaunch replay, Debug and Cloud Ready UI checks: **NOT RUN — ANDROID
HEALTH PREREQUISITE FAILED**. No owner acceptance was performed or issued as passed.
New Kinetic HTTPS dispatch count **0** is not an exactly-once accepted-action pass.
Network independence remains the prior source/automated-test result, not a fresh
live Local request measurement. No real provider request or model download occurred.

## Preservation and log audit

Read-only Room **v6**, quick_check **ok**, counts unchanged:
**15 sessions / 98 messages / 9 memories / 1 summary / 6 approvals / 10 effects**.
Full logical dump SHA-256 equals the previous verified checkpoint:
`0FF2A5731B006AB4D47AD606F179435D99A719666BE2C8CED3CAE420BAEBB30A`.
No owner data/history/memory/summary rewrite occurred.

Provider preference SHA-256 remains
`4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
This proves byte preservation of the previously verified CLOUD/configuration and
ciphertext/IV state; no plaintext credential was emitted/exported/decrypted.
Cloud was never switched; no restoration write or fresh Cloud Ready visual claim.

Installed base.apk SHA-256 remains
`D0438351DB6E9EBA22459C1466256999D4D4E49487E43A888AA8062DC4C0A466`.
Fresh apksigner verification of the existing corresponding build completed and
confirmed signer
`42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
No build/install/uninstall/application-ID or permission change. Existing version
0.4.4-astra-stellar retained; no new version produced.

Bounded log sample **28,208 lines**; Kinetic process-start events **0**, Kinetic
fatal markers **0**, Room/SQLite/migration error markers **0**, credential/header
markers **0**, example.com START markers **0**. System process-start timeouts are
present and explicitly failed; zero Kinetic markers does not imply app usability.
No instrumentation or new automated gate. Prior **244 passed, 0 failures/errors/
skips**, lint **0 errors / 5 unchanged warnings**, build success remain prior evidence.

## Final state and exactly one next task

Phase 5A stays **IMPLEMENTED; AUTOMATED GATE PASSED; DEVICE GATE BLOCKED**.
Phase 5 IN PROGRESS; Phase 5B NOT STARTED. Separate Astra/Stellar owner status unchanged.
The one authorized cold restart has been exhausted. No additional repair is authorized
or begun by this report. No source patch is justified by these external failures.

Exactly one recommended next task, **not begun**: a bounded read-only investigation
of the new boot's host memory/CPU spike and guest graphics/I/O pressure, identifying
the responsible resource consumers before proposing another repair. Do not repeat
the cold restart or start the Kinetic acceptance flow automatically.

This report precedes the final `roadmap.md` edit. No further workspace file edit
is performed after roadmap; normal emulator-managed runtime writes are not manual
project modifications.
