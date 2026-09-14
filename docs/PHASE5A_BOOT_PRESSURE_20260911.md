# Phase 5A same-AVD boot-pressure diagnostic — 2026-09-11

## Decision and scope

**ENVIRONMENT BLOCKED — RESOURCE PRESSURE REPRODUCED.** A single authorized reopen of the existing `Kinetic_API_36` produced multiple Android startup ANRs before any Kinetic or Chrome exercise. The trial stopped at this condition; subsequent work only collected evidence. No repair or device-acceptance retry was attempted.

Starting roadmap: Phase 4 COMPLETE; Phase 5 IN PROGRESS; Phase 5A IMPLEMENTED / AUTOMATED GATE PASSED / DEVICE GATE BLOCKED; Phase 5B NOT STARTED. Prior automated baseline remains 244 passed, zero failed/errors/skips; not rerun. Separate Astra/Stellar engineering pass and pending owner acceptance remain unchanged.

All times below are September 11, PKT (UTC+05:00). The counted ANR window ends **01:49:35.445**, not at report-writing time. Later retained entries are outside this report's count. Logs were not cleared.

## Launch and sampling

- Before launch: no `emulator.exe`, QEMU process, or adb emulator instance. No stale process required termination. The adb daemon was started normally.
- Same locator: `E:\Projects\.tooling\android-avd\Kinetic_API_36.ini` points to `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd`, target android-36.
- Same config SHA-256 before/after: `D64BD0D0EACB3F2869E16D73C89BABC2FABFDFC8326D5EFD18A922E575CDDF4C`.
- Environment: `ANDROID_AVD_HOME=E:\Projects\.tooling\android-avd`; `ANDROID_SDK_ROOT=E:\Projects\.tooling\android-sdk`.
- Exact executable/arguments: `E:\Projects\.tooling\android-sdk\emulator\emulator.exe -avd Kinetic_API_36 -no-snapshot -feature -QuickbootFileBacked -verbose`.
- Start **01:46:06.412**, emulator PID **4508**, QEMU PID **2316**. Neither RAM, cores, renderer, image, SDK/AVD location nor launch mitigation changed. No wipe, reset, snapshot deletion or userdata deletion.
- adb first observed online **01:47:07.152** (previous poll 01:47:02.113 offline). New boot ID `c1c4a69c-6b11-4df9-a3b7-b284c00470e7`.
- `sys.boot_completed=1` first directly observed **01:48:56**; still blank in the sample begun 01:48:52.354. This is an observation bound, not an exact property-transition timestamp.
- System UI failed startup (PID1160). Launcher PID1309 existed, but interactive responsiveness was **not certified**, because the multiple-ANR stop condition took precedence. No healthy System UI/Launcher timestamp is claimed. system_server PID809 answered diagnostic commands but suffered CPU contention; that does not establish overall system health.
- Host: 74 valid JSONL rows, 01:43:21.704–01:49:35.445; 41 after launch, approximately 5 seconds apart. Guest: 15 rows, 01:47:07.152–01:48:52.354, approximately 7–9 seconds including command overhead. Guest collector stopped automatically on the second ANR. Host/QEMU collectors were then explicitly stopped after bounded evidence capture.
- Initial wildcard process counters did not reliably include newly created QEMU. A separate read-only CIM sampler collected 24 emulator/QEMU rows beginning about 01:47:20; there is a **launch-to-first-QEMU-sample gap**. Invalid counter statuses were discarded. Top-10 lists for original host processes must be read together with QEMU's separate series, not misrepresented as a complete all-process ranking after launch.
- Raw non-secret telemetry and diagnostic scripts: `E:\Projects\.tooling\temp\kinetic-boot-{host,guest,qemu}-20260911.{ps1,jsonl}`; analysis script `kinetic-boot-analysis-20260911.ps1`. These are local diagnostic artifacts, not app source.

## Host baseline and observed extrema

Initial host baseline at 01:42:35: available physical RAM **4,935,636 KiB** of **12,534,540 KiB**. RAM subsequently rose without this agent closing unrelated processes. The closest prelaunch sample (01:46:04.278) is the relevant immediate baseline: **6198 MiB available**, **8.501% total CPU**, **8,102,023,168 bytes committed / 21,559,521,280-byte limit** (7.55/20.08 GiB). Four host logical processors.

Pagefiles initially: C allocated5888/current23/peak33 MiB; E allocated2432/current38/peak39 MiB. Final read-only check during report preparation: C current171/peak172 MiB; E current61/peak62 MiB, allocations unchanged. Free disk baseline: C **21,202,771,968 bytes**; E **121,945,878,528 bytes**.

Immediate prelaunch largest working sets: Code PID8848 **403.9 MiB**, Memory Compression PID1632 **374.4 MiB**, host Chrome PID10412 **337.1 MiB**, Code PID12700 **322.8 MiB**, Defender PID3836 **282.1 MiB**. Largest process CPU counters: Code10780 **8.663%**, Chrome10412 **5.569%**, Codex12828 **4.331%**, Defender3836 and Code12700 **3.713%** each. Here process CPU100%=one core; divide by4 for host-capacity share. Prelaunch process I/O leaders: Codex12828 2.21MB/s; Code12700/10780 about1.48MB/s each; Chrome10412 0.367MB/s. Process I/O includes non-disk traffic.

| Measurement | Observed result after launch |
|---|---|
| Available host RAM minimum | **3098 MiB** at 01:49:35.445; no reproduction of prior 665MiB nadir |
| Host CPU peak | **99.708%**, 01:49:03.364 |
| Commit maximum | **12,741,586,944 bytes** (11.87GiB), below20.08GiB limit |
| QEMU working set/private maximum | **3,302,690,816 / 3,218,890,752 bytes** (3149.7/3069.8MiB) |
| QEMU CPU | **287–364%** in its sampled interval (71.75–91% of four-core host capacity) |
| Emulator wrapper | <=9,269,248-byte working set, <=1,605,632-byte private WS; sampled CPU0% |
| E-containing physical disk queue | Peak **4**; this physical disk also contains D:, so not exclusive E-volume attribution |
| E-containing disk read/write latency peaks | **186ms** at01:46:09.365 / **328ms** at01:47:05.090; interval-average latency peaks, not individual-operation maxima |
| E-containing disk throughput peaks | Read18,899,613B/s (**18.02MiB/s**); write1,516,104B/s (**1.45MiB/s**) |
| C-containing disk queue | Sampled peak0; interval activity still occurred |
| Memory Pages/sec peak | 5787.422; includes file-backed paging, not proof of pagefile-only traffic |

## Guest pressure and consumers

Minimum sampled `MemAvailable` **1,700,208 KiB** (1660.4MiB); this is only through the guest collector cutoff. Guest MemTotal2,532,300KiB. All collected `SwapTotal`, `SwapFree`, vmstat swap-in/out and swap-used values were zero. `/proc/swaps` and zram `mm_stat` were permission denied; no privileged bypass attempted. Thus no active swap was reported, but zram internal allocation was not inspectable.

Direct PSI access was denied. The following are **maximum observed avg10 values in the eleven in-window ANR reports**, not continuous boot maxima: CPU some/full **87.59/0%**, memory **0.80/0.37%**, I/O **94.36/13.93%**. Earlier boot PSI was unavailable. Highest one-second vmstat I/O wait **98%**, at01:47:07 and01:47:16. Top's aggregate percentages use a200% two-core scale and are not substituted for normalized vmstat.

The first System UI ANR's 20-second CPU report measured composer PID518 **93%** (92% kernel), system_server PID809 **20%**, SurfaceFlinger PID552 **4.1%**; total97% with71% kernel and20% I/O wait. Phone's next report measured composer94% (93% kernel), system_server23%, SurfaceFlinger5.8%; total99%,90% kernel,2.3% I/O wait. This is sustained graphics-path/kernel activity, not a Kinetic workload.

Short top samples also observed composer107%, SurfaceFlinger113%, bootanimation37%, Launcher56.6%, system_server53.1%, SystemUI23.3% (100%=one guest core; noisy short snapshots, not simultaneous maxima). Representative resident sizes: composer15MiB, SurfaceFlinger32MiB, bootanimation46MiB, system_server373MiB, Launcher150MiB, SystemUI187MiB. Early boot showed numerous init/media processes in D state while I/O wait was98%. Later Messaging's main thread was D-state while loading APK resources. ActivityManager recorded its lmkd connection; no specific lmkd kill was established by this bounded capture. No claim of exhaustive lmkd coverage.

## New ANRs and timestamp correlation

**11 new ANR events** through01:49:35.445: SystemUI1; Phone1; keyboard1; Wellbeing1; PlayServices persistent2; Google interactor1; Android System Intelligence1; Messaging RCS1; Google search1; PlayServices main1. **Kinetic0, Chrome0, Launcher0, system_server/process-system0** in this window. No Kinetic or Chrome process-start event was found in the new boot. Historical September9/10 entries were excluded by timestamp/boot identity, not erased.

All first nine reasons: **failed to complete startup**. Last two: **SIM_STATE_CHANGED broadcast timeout**. Host values below use the nearest recorded sample, not exact instantaneous measurements. PSI CPU is some; memory/I/O are some/full. Report I/O wait is the ANR CPU-accounting interval, not an instantaneous reading.

| Event time / process / PID | Main state / location | Host sample time; freeMiB / CPU% | E disk queue; read/write ms | CPU PSI; memory PSI; I/O PSI | Report I/O wait% |
|---|---|---|---|---|---|
| 01:48:40.741 SystemUI1160 | R, Dagger component initialization | 01:48:42.285;3620/94.999 | 2;11/64 | 71.56;0.54/0.37;66.39/13.93 | 20 |
| 01:48:52.117 Phone1290 | S, Binder registerContentObserver during onCreate | 01:48:52.713;3458/97.728 | 1;4/4 | 85.70;0.26/0.11;66.00/5.10 | 2.3 |
| 01:49:11.393 keyboard1584 | D, Application attachBaseContext | 01:49:08.864;3311/96.733 | 2;14/178 | 83.15;0.80/0.01;88.07/4.91 | 5.5 |
| 01:49:14.747 Wellbeing1612 | R, Application constructor | 01:49:14.735;3253/99.401 | 3;6/58 | 83.84;0.65/0.01;90.23/4.75 | 5.5 |
| 01:49:15.261 GMS persistent1631 | R, ART dump checkpoint captured | 01:49:14.735;3253/99.401 | 3;6/58 | 85.32;0.53/0;91.82/4.79 | 6.2 |
| 01:49:17.342 Google interactor1728 | R, ART dump checkpoint captured | 01:49:19.808;3232/97.458 | 3;5/40 | 86.35;0.43/0;92.57/4.46 | 6.2 |
| 01:49:17.906 Android Intelligence1730 | R, class initialization | 01:49:19.808;3232/97.458 | 3;5/40 | 86.35;0.43/0;92.57/4.46 | 6.2 |
| 01:49:19.152 Messaging RCS1775 | D, LoadedArsc/ApkAssets resource loading | 01:49:19.808;3232/97.458 | 3;5/40 | 86.47;0.35/0;89.03/4.56 | 6.2 |
| 01:49:21.032 Google search1876 | R, class/Application initialization | 01:49:19.808;3232/97.458 | 3;5/40 | 86.47;0.35/0;89.03/4.56 | 7.7 |
| 01:49:31.031 GMS2144 | R, GMS initialization frame fkmv.b | 01:49:30.234;3142/98.123 | 0;11/126 | 86.84;0.13/0;93.55/5.17 | 7.7 |
| 01:49:32.544 GMS persistent2108 | R, GMS initialization frame fkmv.b | 01:49:30.234;3142/98.123 | 0;11/126 | 87.59;0.10/0;94.36/4.78 | 7.7 |

The System UI main thread had0.177s execution versus1.330s cumulative runqueue wait; Phone0.398s execution versus3.871s wait. These are thread lifetime counters, not ANR-window totals. Stack samples and contention establish delays, not a proven app deadlock. GMS/interactor dump-checkpoint frames are observer artifacts and do not identify their precise original blocking instruction.

## Ranked measured resource contributors

Rank reflects likely contribution to this failure, not a claim that CPU, RAM and I/O have one interchangeable ordering. Host-family RAM below sums **private** working sets, avoiding shared-page double counting. Peaks need not be simultaneous. Process I/O is not equivalent to physical-disk bytes; no file-level trace was captured.

| Rank / consumer | Host/guest | CPU | RAM | I/O | Timing | Likely contribution |
|---|---|---|---|---|---|---|
| 1 QEMU2316 | Host | peak364%=91% host | peak3069.8MiB private | peak24.13MiB/s process I/O | sustained sampled boot | Dominant measured host CPU and incremental RAM consumer |
| 2 graphics composer518 / SurfaceFlinger552 / bootanimation718 | Guest | composer93–94% sustained; SF4.1–5.8% in ANR intervals, transient113% | representative15/32/46MiB RES | kernel-heavy graphics path; disk attribution unavailable | before first two ANRs | Strongest guest CPU contributor; supports graphics/emulator contention, exact host-thread cause unresolved |
| 3 E-containing storage path | Host/guest dependency | not a process | not applicable | queue4; read186ms/write328ms; guest wait98% | early startup; I/O PSI rises during ANRs | Material storage stalls; cannot separate AVD, D-volume traffic, file paging or scanning by counters alone |
| 4 system_server809 | Guest |20–23% in first ANR intervals | observed373MiB RES |81 then184 major faults in those intervals | app startup/broadcasts | Shared startup/Binder bottleneck; Phone waits on its content service |
| 5 Defender3836 | Host | peak60.865%=15.22% host | peak227.1MiB private | peak32.04MiB/s process I/O | CPU/I/O peak01:46:14.421, before ANRs | Secondary early pressure; no proof that scanned files were AVD files |
| 6 Code family | Host | peak30.279%=7.57% host | peak930.4MiB private | peak2.83MiB/s process I/O | present before and during boot | Background memory/CPU overhead, far below QEMU CPU |
| 7 host Chrome family | Host | peak25.344%=6.34% host | peak664.6MiB private | peak1.06MiB/s process I/O | throughout | Background overhead; distinct from Android Chrome, which was not started |
| 8 Launcher/SystemUI and background Google startup | Guest | Launcher snapshot56.6%, SystemUI23.3%; GMS not established as dominant CPU | Launcher150/SystemUI187MiB representative RES | resource-loading D states / major faults | late boot | Concurrent startup load and victims; no evidence to rank PlayStore/dex optimization as dominant |
| 9 Codex family | Host | peak8.039%=2.01% host | peak89.5MiB private | peak1.99MiB/s process I/O | throughout | Smaller background/diagnostic overhead |
| 10 Edge / Ollama / MongoDB | Host | peaks0.918/0/1.855% one-core | private193.9/45.6/16.8MiB | <=0.0074/0/0.0158MiB/s | throughout | Present but minor measured contributors |

No Java/Gradle, Qoder or Cursor process was observed in scope. Memory Compression already occupied374.4MiB WS before launch; paging activity exists, but the commit headroom and >3GiB available RAM argue against **demonstrated host RAM exhaustion** as the immediate cause. This does not prove memory reclaim had zero cost. No unrelated process, antivirus policy, audio setting or host resource configuration was changed.

## Preservation and final state

Kinetic was not launched, tested, built, installed, force-stopped, cleared or uninstalled. No source, Gradle, manifest, Room, provider, applicationId or signer change. No model download, request, approval, capability or Chrome handoff. Existing install/signing baseline remains expected signer `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`; no fresh certificate extraction was needed for this no-install diagnostic.

Read-only `run-as` hashes match the prior baseline:

- Room read-only SQLite dump: `0FF2A5731B006AB4D47AD606F179435D99A719666BE2C8CED3CAE420BAEBB30A`.
- Encrypted provider preferences: `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.

No plaintext provider content was printed or backed up. Hash preservation is not a fresh credential-decryption/UI-readiness test. The same emulator remains open; it is **not certified healthy**. Only diagnostic documentation and tooling were added. `roadmap.md` is updated after all other workspace modifications.

Final roadmap: **Phase5A DEVICE GATE BLOCKED**; environment **RESOURCE PRESSURE REPRODUCED**; Phase5B **NOT STARTED**; no owner acceptance granted.

Exactly one recommended next task, **not begun**: a bounded read-only host QEMU thread-CPU and file-I/O attribution trace on the existing running AVD, to identify the graphics/storage bottleneck before proposing any configuration change. No additional restart, patch or repair is implied.
