# Phase 5A Chrome / environment root-cause diagnostic — 2026-09-10

**CHROME / ENVIRONMENT ROOT-CAUSE DIAGNOSTIC COMPLETED**

Primary classification: **F. GRAPHICS / SURFACEFLINGER / GPU STALL**.
Confidence: **MEDIUM**. The specifically evidenced mechanism is an emulator
graphics-pipe / HWUI render-thread stall, with substantial guest CPU scheduling
pressure. This is not proof of a physical GPU fault, a permanent deadlock, a
specific host driver bug, or the same exact call stack in both Chrome processes.
Phase 5A remains **IMPLEMENTED; AUTOMATED GATE PASSED; DEVICE GATE BLOCKED**.

## Scope, continuity and observation times

Roadmap was read first and completely. Required prior Phase 5A verification,
September 10 Chrome retry and September 7 AVD diagnostic reports were read before
new diagnostics. Phase 4 COMPLETE / Phase 4C ACCEPTED; Phase 5 IN PROGRESS;
Phase 5B NOT STARTED. Prior baseline 244 passed, 0 failed/errors/skipped; lint
0 errors / 5 unchanged warnings. No build, install or test rerun was needed.

Read-only collection window: **01:27:20–01:32:18 PKT, September 10**. Host and
guest clocks agreed to the displayed second at entry. The two retained ANRs are
from **01:03:49.481** and **01:04:59.188**, not new events produced by this task.
Contemporaneous guest PSI/CPU evidence is embedded in those retained ANR reports.
Host samples collected later cannot establish host disk/CPU conditions at 01:03–05.
This distinction is maintained throughout; no historical host metric is invented.

Same `Kinetic_API_36`, serial `emulator-5554`, boot
`f7e45119-59cb-4eb9-aa7d-7b21e7f510ac`. Chrome was absent; existing Kinetic PID
19911 was not launched, stopped, interacted with or sent a request. Current focus
was Launcher. Existing command lines remain:

`-avd Kinetic_API_36 -no-snapshot -feature -QuickbootFileBacked -verbose`

Emulator PID 11212, QEMU PID 8856. No emulator configuration, renderer, snapshot,
userdata, Windows setting, Chrome profile/cache/package or Kinetic source changed.
No root/remount/permission bypass was used. No logs were cleared.

## Host baseline — later diagnostic samples, NOT ANR-time telemetry

Windows boot: **2026-09-03 03:45:31.209 PKT**; uptime at 01:29:22 about
**165.73 hours**. Four logical processors. C: is disk 0 Kingston SV300S37A120G SSD;
E: shares disk 1 WDC WD5000AAKX-07U6AA0 HDD with D:. SDK and AVD remain on E:.

| Metric | Observed |
|---|---|
| OS-visible physical memory | 12,534,540 KiB |
| Initial available memory | 2,277,068 KiB; subsequent performance sample 2,174 MiB |
| Later available memory | 1,526 / 1,569 / 1,529 MiB at 01:29:17 / :19 / :21 |
| Commit | Initially 18,069,065,728 bytes; later 18,440,798,208–18,517,417,984 bytes; limit 22,058,618,880 bytes |
| C: pagefile | 6,363 MiB allocated, 1,076 MiB used, peak 1,589 MiB |
| E: pagefile | 2,432 MiB allocated, 750 MiB used, peak 1,020 MiB |
| C: free | 18,932,342,784 bytes |
| E: free | 122,009,313,280 bytes |
| Aggregate host CPU | Initial 61%; later 97.87 / 92.88 / 97.29%; final 95% |
| QEMU CPU | Initial 66%, final 21% on the process-counter scale where 100% is one logical CPU; not 66%/21% of four-core host capacity |
| emulator / adb CPU | 0% in sampled process counters |
| QEMU private working set | About 2.30–2.32 billion bytes |

High aggregate host CPU later is real, but QEMU did not account for all of it.
These samples include diagnostic activity and are not a synchronized reproduction.
No unrelated host application was closed or changed. Commit headroom remained;
physical-memory availability decreased but host commit exhaustion was not observed.

Three disk samples at 01:29:17.545 / :19.576 / :21.594:

| Physical disk | Active time (100-idle) | Queue | Read latency | Write latency | Read throughput | Write throughput |
|---|---|---|---|---|---|---|
| C: SSD | 16.37 / 8.04 / 2.91% | 0 / 0 / 0 | 0.42 / 0.40 / 1.15 ms | 0.75 / 0.08 / 0.78 ms | 6,551,672 / 1,369,050 / 4,511,946 B/s | 1,367,730 / 3,323,323 / 206,577 B/s |
| D:/E: HDD | 24.24 / 2.87 / 0.95% | 0 / 1 / 0 | 5.98 / 10.30 / 12.90 ms | 0.14 / 0.15 / no writes | 1,860,276 / 96,781 / 4,060 B/s | 6,106 / 2,016 / 0 B/s |

No sustained HDD saturation was present in these samples. Low write latency can
reflect caching; these are OS counters, not a physical-media benchmark. The old
September 7 queue-102 / 415-ms incident is not evidence of this window's cause.

## Guest baseline — later diagnostic samples

At 01:28:02: uptime **2 days 49 minutes**, load **0.81 / 0.71 / 3.07**, two guest CPUs.
MemTotal **2,532,300 KiB**, MemAvailable **1,034,572 KiB**, MemFree **250,964 KiB**;
SwapTotal **1,899,220 KiB**, SwapFree **1,099,476 KiB**, used **799,744 KiB**.
Dirty and Writeback were 0. Swap occupancy is not proof of current swap thrashing.

`vmstat 1 3` live interval rows: runnable 1, blocked 0, swap-in/out 0, block I/O
0, user 0–1%, system 14–17%, idle 83–85%, iowait 0%. The first vmstat row is the
since-boot average and is not presented as an interval measurement.
One top sample reported 48% system / 150% idle / 2% softirq on its 200% two-CPU scale.
Composer 25%, system_server 9%, sensors 9%, SurfaceFlinger 6.8%; top itself 38.6%
illustrates measurement overhead. Chrome was absent. No dex2oat marker was found
in the retained 01:00–05 log subset.

Direct `/proc/pressure/*`, `/proc/swaps`, zram `mm_stat`/`disksize` and binderfs
access were denied to Android shell. Exact current zram compression/activity and
current PSI are unavailable; aggregate swap figures above are available. No root
or SELinux changes were attempted. An unfiltered meminfo service attempt supplied
no useful zram summary. ANR-embedded PSI supplies the relevant historical evidence.
Exact ANR-time MemAvailable and numeric load averages were not in the retained
excerpts; later values are not substituted for them.

## ANR 1 — PID 23449

Chrome `com.google.android.apps.chrome.Main`, version 133.0.6943.137.
InputDispatcher detected unresponsiveness **01:03:48.539**; `am_anr`
**01:03:49.481**, MotionEvent DOWN at (490,208), waited **5,004 ms**.
DropBox `system_app_anr` entry **01:04:16** links to
`/data/anr/anr_2026-09-10-01-03-49-957`. Its header PID=0 is incomplete metadata;
the event, dumping-pid and wait-channel section identify **23449** consistently.
Force-stop exit reason from the preceding task does not erase this ANR.

Direct trace-file read was denied. Ordinary `dumpsys dropbox --print system_app_anr`
exposed the retained evidence without privilege changes. Full stack capture failed:

```text
libdebuggerd_client: failed to read status response from tombstoned: Try again
Waiting Channels: pid 23449 at 2026-09-10 01:03:49.858471400+0500
sysTid=23449 futex_wait_queue
```

Therefore the first main thread was sleeping on a futex at sampling time; its
owning monitor/caller, exact render dependency and native stack are **unavailable**.
A futex alone is not proof of Java deadlock, Binder wait, disk I/O or GPU wait.
Do not copy the second process's stack into this missing first trace.

Graphics timing in PID 23449: 3,182 skipped frames at **01:03:42.146**;
HWUI frame durations **58,436 ms at 01:03:47.692** and **59,628 ms at 01:03:48.873**.
These are end-to-end frame delays, not proof of equally long GPU-only execution.
An additional first-process focus timeout at 01:03:55.216 is retained from the
earlier report, not counted as another independent am_anr event.

## ANR 2 — PID 23782: direct render dependency

Same Chrome launcher component, following the preceding task's one process restart.
InputDispatcher detection **01:04:57.948**; `am_anr` **01:04:59.188**;
FocusEvent(hasFocus=true), waited **5,034 ms**. DropBox entry **01:05:01**,
trace `anr_2026-09-10-01-04-59-250`, stack header **01:04:58.603350800+0500**.
The previous launch's adb timeout was 20,055 ms and remains failed.

Main thread `Native`, kernel state S, no held mutex listed:

```text
futex_wait -> pthread_cond_wait -> future<void>::get
RenderProxy::setStopped
HardwareRenderer.nSetStopped / setStopped
ViewRootImpl.performDraw / performTraversals
Choreographer.doFrame -> Looper -> ActivityThread.main
```

RenderThread, TID **23823**, kernel state R in this sample:

```text
read -> qemu_pipe_read -> QemuPipeStream::commitBufferAndReadFully
rcCreateSyncKHR_enc -> createNativeSync
egl_window_surface_t::swapBuffers -> eglSwapBuffers
EglManager::swapBuffers -> SkiaOpenGLPipeline::swapBuffers
CanvasContext::draw -> DrawFrameTask -> RenderThread::threadLoop
```

Interpretation: main is synchronously waiting for render-thread work while that
thread is in the emulator's host graphics transport/native-sync path. Android 16
[RenderProxy source](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/libs/hwui/renderthread/RenderProxy.cpp)
confirms `setStopped` uses the render queue's `runSync`. A native `read` in this
QEMU graphics pipe is **not a filesystem read proving HDD starvation**. State R
and a single snapshot do not prove a permanently blocked host GPU or deadlock.
The main/render scheduling statistics show substantial accumulated run-queue delay:
main runtime **6.077 s**, scheduling wait **16.411 s**; RenderThread runtime
**1.647 s**, scheduling wait **9.073 s**. These are accumulated thread counters,
not a measured continuous duration of this individual render call.

PID 23782 skipped **1,554 frames at 01:04:49.106**; HWUI reported a **36,961-ms**
frame at **01:05:00.281**. The trace is in Android HWUI, not a Chrome profile read,
WebView initialization call, Java monitor cycle or synchronous Binder transaction.
Chromium IO/process-launcher threads shown in the trace were polling/waiting;
unresolved native Chromium symbols prevent broader claims about all startup work.

## Resource correlation at the retained ANRs

| Metric | First ANR | Second ANR |
|---|---:|---:|
| CPU PSI some avg10 | 79.94% | 79.78% |
| CPU PSI some avg60 | 65.30% | 79.98% |
| Memory PSI some/full avg10 | 11.79% / 3.78% | 2.65% / 0.82% |
| I/O PSI some/full avg10 | 38.68% / 5.36% | 9.66% / 0.55% |
| Guest TOTAL CPU-report percentage | 95% | 93% |
| User / kernel / iowait / softirq | 2.8 / 80 / 3.9 / 7.9% | 2.8 / 80 / 2.4 / 8.3% |
| system_server | 36% | 31% |
| Graphics composer / SurfaceFlinger | 33% / 20% | 22% / 11% |
| Chrome browser | Not listed in retained top ten | 36%, mostly kernel |
| Chrome RSS / swap | 284,796 / 18,068 KiB | 286,192 / 25,348 KiB |

First CPU interval **01:03:49.545–01:04:14.932** is AFTER detection and includes
dumping/restart work; it cannot establish the entire pre-failure CPU history.
Second interval **01:04:14.932–01:04:59.188** spans startup through detection,
giving stronger contemporaneous CPU evidence. PSI trends capture pressure around
the reports, not per-thread attribution. [Linux PSI documentation](https://docs.kernel.org/accounting/psi.html)
defines some/full stall time; system-level CPU full=0 is not absence of contention.

CPU starvation materially contributed or coincided with the render stall. Neither
host QEMU saturation at those exact timestamps nor which host workload caused it
can be proved from later host counters. Guest system_server, graphics and sensors
were substantial consumers; GMS 3.5–3.8% appeared but was not the dominant consumer.
I/O and memory pressure existed, especially in the first report, but diminished
markedly before the second failure while CPU pressure stayed high. The historical
80–82% I/O-wait mechanism is not reproduced in these reports. Current healthy disk
samples cannot retroactively exonerate the HDD, but do not support blaming it alone.

## Binder, input and graphics checks

- Cached Chrome PID 20491 was killed at 01:01:19.823 for excessive Binder traffic;
  Contacts had the same cached cleanup at 01:01:18.550. This is not an OOM reason
  and is not proof that the newly launched Chrome main thread exhausted Binder.
- First ANR Binder snapshot: outgoing transaction from **23449:23484 to 727:1734**,
  code 28, elapsed **9 ms**, transaction complete. Second: pending transaction
  **727:4173 to 23782:0**, code 7, **64 bytes**, elapsed **647 ms**. Codes are not
  assigned speculative API names. Chrome's Binder pool stacks show ordinary
  `joinThreadPool` waits, not demonstrated buffer exhaustion or a saturated pool.
- Frozen-process Binder errors -32 at 01:04:06–08 follow the first force-stop;
  the burst at 01:05:02 follows second-process death. Death/cleanup is a stronger
  explanation for those later errors than claiming they caused the earlier ANR.
  Earlier cached cleanup may be related environmental churn, but causality is unproved.
- System_server monitor contention was real: bind/service operations 469–500 ms
  around 01:04:22 and input-method/window update 860 ms at 01:04:59.250. This can
  add latency but no stack establishes it as the blocked main-thread dependency.
- InputDispatcher had concrete Chrome target windows, not a no-focused-window
  timeout. First DOWN event eventually took **5,949 ms**; second focus event
  **7,635 ms**, followed by loss-of-focus **5,405 ms**. Together with main's render
  wait, evidence favors Chrome failing timely input acknowledgement, not failure
  to find/deliver to a target. System contention can still contribute to timing.
- Current input dispatcher enabled/not frozen; Launcher focus; no pending/inbound
  events. Its appended ANR snapshot still lists Chrome unresponsive; that retained
  section is not a currently live Chrome process.
- SurfaceFlinger currently reports **Android Emulator OpenGL ES Translator
  (Google SwiftShader), GLES 3.0**, `vulkan_renderengine=false`. EGL emulation
  libraries and HWUI frame stalls are present in both old launch logs. A failed
  101010-2 format warning alone is not GPU failure. No host renderer switch or
  Windows GPU/driver/audio change was made.

Retained 01:00–05 subset: **8,763 lines**, 0 matched LMKD-kill, OOM,
Binder-buffer/thread-starvation or dex2oat markers. Lowmemorykiller failed-to-open-
already-exited-PID warnings are not proof it killed those processes for memory.
Memory PSI and major faults show pressure, but there is no demonstrated Chrome
OOM/LMKD kill. No numeric historical MemAvailable, full system-server stack or
host graphics-thread trace is available. Permission-restricted trace/binder/zram
paths were not bypassed. No Kinetic interaction or new ANR reproduction occurred.

## Root-cause matrix

Confidence is confidence in the proposed candidate as the PRIMARY cause, not in
whether its cited observations occurred.

| Candidate | Evidence FOR | Evidence AGAINST / limits | Confidence |
|---|---|---|---|
| Chrome main-thread bug/stall | Main futex wait; second main waits for render work | No Chrome-specific bug/Java deadlock identified; graphics is downstream dependency; first stack absent | LOW for Chrome-owned bug |
| Binder saturation | Cached cleanup, pending 647-ms call, system monitor contention | Small buffer, available pool waits, no buffer/starvation markers; main is not in Binder | LOW |
| CPU starvation | CPU PSI ~80%, guest kernel ~80%, long scheduling waits | Does not uniquely explain graphics transport delay; contemporaneous host attribution absent | MEDIUM |
| I/O starvation | First I/O PSI 38.68% some, major faults | Second full PSI 0.55%, iowait 2.4%; later HDD queue 0–1; no filesystem stack dependency | LOW as primary |
| Memory pressure | Memory PSI, swap occupancy, major faults | PSI falls; no LMKD/OOM marker or memory exit reason; later memory/commit headroom | LOW as primary |
| Graphics/compositor stall | Main -> render queue -> EGL/QEMU pipe; multi-second frames; active composer/SF | Only second full stack; host-side call/scheduler bottleneck unresolved; no permanent GPU deadlock proved | MEDIUM |
| system_server/input issue | High CPU and subsecond monitor contention | Known Chrome target; main has render wait; no system ANR; delayed acknowledgements arrive | LOW as primary |
| Chrome state/data issue | Repeated launch failure | No profile corruption/read/initialization stack or package failure evidence; profile not inspected destructively | LOW |
| Multi-factor environment | CPU plus graphics, some memory/I/O and Binder churn | Relative causal weights cannot be isolated; graphics dependency is most specific direct evidence | MEDIUM |

**Exactly one primary classification: F. GRAPHICS / SURFACEFLINGER / GPU STALL —
MEDIUM confidence.** CPU contention is a supported contributor, not a second primary
classification. Diagnosis is completed to the strongest available evidence; exact
host implementation fault and first-process call stack remain unresolved.

No extra reproduction was needed: retained evidence gives a direct dependency
chain and contemporaneous guest pressure. Reproducing cannot reconstruct the
missing original first-process stack or historical host counters. Another launch
would add disruption without being necessary for this bounded classification.

## Kinetic preservation

Full read-only Room dump SHA-256 matched before/after collection:
`0FF2A5731B006AB4D47AD606F179435D99A719666BE2C8CED3CAE420BAEBB30A`.
Room v6, quick_check ok; **15 sessions / 98 messages / 9 memories / 1 summary /
6 approvals / 10 effects**. Provider-preference SHA-256 matched before/after:
`4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
No plaintext credential read/export/decryption or provider call; byte preservation
is not a fresh cloud-functionality test.

Installed and local APK hashes still
`D0438351DB6E9EBA22459C1466256999D4D4E49487E43A888AA8062DC4C0A466`;
fresh apksigner verifies the identical artifact's signer
`42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
Version 0.4.4-astra-stellar, applicationId dev.kinetic.app, first install September 1
11:51:15, last update September 9 11:42:45 unchanged. Permissions remain INTERNET
and app-scoped signature receiver permission. No model installed/downloaded, native
runtime added, or provider selected. No inspected non-generated source/build/
manifest/catalog timestamp is newer than the APK; no Git metadata exists to certify
a Git diff. No source/config edit was made. Only this report and roadmap are edited.

## Exactly one recommended repair / next task — NOT EXECUTED

Authorize **one controlled cold restart of the SAME AVD with its existing launch
arguments and unchanged renderer**, then assess Android/standalone Chrome health
before any Kinetic gate. Preserve all userdata and snapshot files; retain current
`-no-snapshot -feature -QuickbootFileBacked` flags, RAM/cores and configuration.

This resets the long-lived guest/compositor/emulator graphics-pipe state that a
Chrome-only process restart did not recover, without introducing an alternate
renderer, package update or data reset. It is a bounded non-destructive repair
trial, **not a proven cure**; CPU contention may persist. Normal shutdown/termination
verification must precede that future launch. No part of this repair is performed
in this diagnostic task. No other repair or next task is recommended here.

Roadmap is updated last: diagnostic completed; Phase 5 IN PROGRESS, Phase 5A device
gate BLOCKED, Phase 5B NOT STARTED; no engineering pass or owner acceptance inferred.
