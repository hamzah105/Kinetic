# Phase 5A final emulator bottleneck attribution — 2026-09-11

## Outcome

**FINAL EMULATOR BOTTLENECK ATTRIBUTION COMPLETED — WITH MATERIAL OBSERVABILITY LIMITS.** Primary classification **G: inconclusive** for the exact dominant boot-time thread/file bottleneck. **F: mixed graphics + CPU + disk** remains the strongest historical explanation, at medium confidence, not a newly proven per-thread/per-file attribution.

The existing emulator had settled by this task. No restart, stimulus, app test, configuration change or repair was performed to recreate the failure. Windows denied the privileged traces. The task's bounded diagnostic attempt is complete; the requested exact heavy-file ranking and symbolic boot-thread attribution could not be established. Phase 5A remains DEVICE GATE BLOCKED, not passed or owner accepted.

Read roadmap first. Starting state: Phase4 COMPLETE; Phase5 IN PROGRESS; Phase5A IMPLEMENTED / AUTOMATED GATE PASSED / DEVICE GATE BLOCKED; Phase5B NOT STARTED. Prior244 passed,0 failures/errors/skips remains historical, not rerun.

## Window, instance and tracing limitations

Read-only active diagnostic began **03:15:35 PKT**, ended approximately **03:23:07** (under8minutes, below20-minute cap); later work only summarized evidence, checked reference documentation and wrote documentation. Host sampler ran about90seconds, with13 timestamped observations spanning **03:18:31.487–03:19:57.196** (85.709seconds first-to-last). Ten guest samples began **03:18:39.118–03:19:36.828**. Collectors completed and no trace session remained active.

- Same emulator PID4508, QEMU **2316**, boot ID `c1c4a69c-6b11-4df9-a3b7-b284c00470e7`; no start/restart necessary.
- Current command: `E:\Projects\.tooling\android-sdk\emulator\qemu\windows-x86_64\qemu-system-x86_64.exe -avd Kinetic_API_36 -no-snapshot -feature -QuickbootFileBacked -verbose`.
- Installed WPR reported no existing recording. CPU.light + DiskIO.light + FileIO.light startup failed with **0xc5585011: Failed to enable the policy to profile system performance**. Counter-only fallback repeated this check and continued without policy changes.
- A separate32MB circular `Microsoft-Windows-Kernel-File` provider attempt using logman failed **Access is denied; run as administrator**. Elevated sandbox execution did not provide an administrator Windows token. No UAC bypass, privilege-policy change or tool installation was attempted.
- Therefore there is **no successful ETW CPU-stack/file-event capture**, no complete boot-time CPU symbols, and no valid per-file byte/latency ranking. An open handle establishes a file relationship, not I/O activity.
- Thread descriptions were queried read-only using GetThreadDescription; the API returned no usable description. Raw CPU/state counters remained available.
- Evidence files outside app source: `E:\Projects\.tooling\temp\kinetic-attribution-20260911.ps1`, matching `.jsonl`, `kinetic-attribution-guest-20260911.jsonl`, `kinetic-memory-map-20260911.ps1` and matching `.jsonl`. Memory inspection collected region metadata and filenames only, not guest RAM contents.

## QEMU CPU and thread attribution

QEMU used **4.625 CPU seconds in85.709 wall seconds**: **5.396% of one core /1.349% of the four-logical-core host**, with sampled interval range1.539–9.226% one-core (0.385–2.306% host). This is not the earlier287–364% boot load. Cumulative process CPU at the final observation was897.5seconds; lifetime totals cannot retrospectively isolate boot activity.

| TID | CPU delta seconds | User / kernel delta seconds | Final state / wait | Purpose supported by evidence |
|---|---:|---|---|---|
| 1040 | 1.750000 | 1.671875 /0.078125 | Wait /Unknown | Unnamed; vCPU versus graphics not resolved |
| 12848 | 1.640625 | 1.578125 /0.062500 | Wait /Unknown | Unnamed; vCPU versus graphics not resolved |
| 6208 | 0.484375 | 0.421875 /0.062500 | Wait /UserRequest | Unnamed; main/I/O role not proved |
| 3532 | 0.156250 | 0.156250 /0 | Wait /UserRequest | Unnamed worker |
| 15256 | 0.156250 | 0.156250 /0 | Wait /UserRequest | Unnamed worker |
| 8680 | 0.125000 | 0.125000 /0 | Wait /UserRequest | Unnamed worker |
| 2504 | 0.125000 | 0.125000 /0 | Wait /UserRequest | Unnamed worker |

The two leading threads account for73.3% of measured process CPU, but **two leading threads does not prove two vCPUs**. Their names/stacks were unavailable. Threads created/exited between observations prevent a perfectly exhaustive delta sum. All listed threads were waiting at the last snapshot, not necessarily throughout the interval. Current guest data shows little work in either graphics or ordinary computation, so it cannot disambiguate the earlier host spike.

## Actual graphics and host GPU

Current SurfaceFlinger reports:

`GLES: Google (Google Inc.), Android Emulator OpenGL ES Translator (Google SwiftShader), OpenGL ES 3.0 (OpenGL ES 3.0 SwiftShader 4.0.0.1)`.

`vulkan_renderengine: false`. Current `hardware-qemu.ini`, written01:46:18 in this boot, reports `hw.gpu.enabled=true`, `hw.gpu.mode=lavapipe`, resolution1080x2400, density420, two CPUs, **2560MiB RAM**. These runtime facts supersede any older recollection of2048MiB; nothing was changed during this task.

QEMU has `libgfxstream_backend.dll`, `gles_swiftshader\libGLESv2.dll`, `libEGL.dll`, `libGLES_CM.dll`, and Vulkan `libvulkan_lvp.dll` loaded. Thus **gfxstream with SwiftShader GLES and a lavapipe Vulkan module/configuration** is evidenced; loading Vulkan does not prove a current Vulkan workload. WinHvPlatform/WinHvEmulation modules are loaded, supporting the existing WHPX setup but not identifying individual vCPU threads.

Host GPU is NVIDIA GeForce GT620, driver23.21.13.9135. All13 samples of QEMU's seven exposed GPU engines were **0%**, while other processes had measurable GPU activity. No separate wrapper GPU engine was observed. Crucially, **QEMU CPU was also low in this interval**; do not combine current GPU0% with historical boot CPU364% as if measured simultaneously.

Old `kinetic-emulator.stdout.log` is dated August28. It mentions gfxstream/SwiftShader/llvmpipe/WHPX, but is **historical**, not a current initialization log. No new host-GPU initialization failure was established. Software rendering is directly confirmed, but substantial current software-rendering CPU was not demonstrated. Android's documentation identifies SwiftShader and lavapipe as software backends and warns that unsupported hardware modes can fail: [official acceleration documentation](https://developer.android.com/studio/run/emulator-acceleration).

## File and physical-drive attribution

Read-only QEMU handle inventory confirms the following exact files are open. **Read/write columns describe intended roles, not measured operations. Every per-file activity/rank is unavailable because tracing was denied.**

| Path | Physical location | Expected role/direction; measured activity |
|---|---|---|
| `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd\userdata-qemu.img` | Disk1, E: | Guest userdata backing image; read role, bytes unknown |
| `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd\userdata-qemu.img.qcow2` | Disk1, E: | Writable guest userdata overlay; read/write bytes unknown |
| `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd\cache.img` | Disk1, E: | Cache backing; bytes unknown |
| `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd\cache.img.qcow2` | Disk1, E: | Cache overlay; read/write bytes unknown |
| `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd\encryptionkey.img` and `.qcow2` | Disk1, E: | Encryption metadata backing/overlay; contents not read, activity unknown |
| `E:\Projects\.tooling\android-sdk\system-images\android-36\google_apis\x86_64\system.img` | Disk1, E: | Guest system image; expected reads, bytes unknown |
| `E:\Projects\.tooling\android-sdk\system-images\android-36\google_apis\x86_64\vendor.img` | Disk1, E: | Guest vendor image; expected reads, bytes unknown |
| `E:\Projects\.tooling\android-avd\Kinetic_API_36.avd\hardware-qemu.ini.lock\pid` and `multiinstance.lock` | Disk1, E: | Instance state/locks; no evidence of significant traffic |
| Windows locale/font/resource mappings under `C:\Windows` | Disk0, C: | Host UI/runtime resources, not guest-RAM backing; activity unknown |

Disk1 is **WDC WD5000AAKX-07U6AA0**, shared by D: and E:. Windows partition mapping proves these AVD and system-image paths reside on that device. Get-PhysicalDisk returned MediaType Unspecified for it; Western Digital's original model datasheet establishes the WD5000AAKX as a7200RPM HDD: [manufacturer datasheet, hosted copy](https://www.datasheets.com/western-digital/wd5000aakx/datasheet.pdf). Disk0/C: is **KINGSTON SV300S37A120G**, reported SSD by Get-PhysicalDisk.

In the current counter samples, D/E disk queue was0, physical reads0B/s, write peak40,983B/s, interval-average write latency peak1.340ms. C disk queue0, read latency peak0.288ms, write6.257ms. One later QEMU process-I/O counter reading showed read0/write0B/s, other I/O12,089B/s. Those are sampled readings, not a guarantee of no inter-sample or cached I/O. Diagnostic JSONL writes also share E: and can contribute to its small current write total.

Prior boot disk queue4/read186ms/write328ms and guest wait98% remain valid historical observations. The current inventory proves QEMU uses HDD-resident AVD/system files, **not which of them caused that prior latency**. No exact hottest-read or hottest-write file can responsibly be named. File timestamps are not substituted for event tracing.

## File-backed memory finding

No `-mem-path` appears in the live command line or current saved launch arguments. `-feature -QuickbootFileBacked` and `-no-snapshot` are present, but the conclusion is not based on flags alone.

VirtualQueryEx metadata found **4,222,275,584 bytes committed MEM_PRIVATE**, including one **2,684,354,560-byte (2560MiB) private region**, exactly matching runtime guest RAM size. Total committed MEM_MAPPED was only26,497,024bytes; named mappings were small Windows locale/font resources on C:, plus about4MB without a returned filename. No large AVD/snapshot/temporary RAM mapping or corresponding open backing file was observed.

Conclusion: **guest RAM appears anonymous/private host allocation, high confidence**, rather than quickboot/snapshot-file-backed. The2560MiB region is a strong size/shape inference, not an allocation stack proving ownership. Normal Windows pagefile backing of private memory is distinct from QEMU explicitly using a RAM file; OS paging is not ruled out. No guest memory was read. Memory-region semantics reference: [Microsoft VirtualQueryEx documentation](https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-virtualqueryex).

## Current guest pressure versus historical boot

- Current guest MemAvailable **1,242,164–1,247,352KiB**; host available RAM **5305–5567MiB**.
- All ten one-second vmstat intervals: **0% I/O wait**,98–99% idle, swap-in/out0. The first vmstat line is lifetime average and was not treated as a current interval.
- Guest swap is now active: total1,899,220KiB, about741,548–741,804KiB used. This differs legitimately from early boot's zero SwapTotal. No current swap churn was sampled; detailed zram access remains restricted.
- Top's short samples show composer/system_server/SurfaceFlinger each around3.7–3.8% of one guest core when present; diagnostic `top` itself sometimes led. system_server RES294MiB, SurfaceFlinger19MiB, composer7.6MiB. No dex2oat/installd/package optimization dominance was observed in the top samples; this does not prove absence throughout boot.
- Current CPU/I/O PSI: **unavailable, permission denied**. Historical boot ANR avg10 maxima CPU87.59%, I/O94.36/13.93% are not current readings.
- No03:xx ANR entry was returned by the bounded event-log audit. No Kinetic process start was found. Android had autonomously started Chrome for a broadcast at01:52:36, before this task; this is historical and was not a user/agent Chrome handoff. No Chrome test was performed.
- Host total CPU sampled3.81–89.69%, while QEMU remained low; the isolated host peak cannot be attributed to QEMU. Diagnostics and unrelated host work were not isolated. An idle sample/no fresh ANR does not pass the outstanding device gate.

## Root-cause matrix

| Candidate | Evidence for | Evidence against / missing | Confidence |
|---|---|---|---|
| Software rendering | Current SwiftShader GLES/lavapipe configuration; QEMU GPU0%; historical guest composer93–94% mostly kernel | No boot host-thread stacks; current QEMU low CPU; guest graphics CPU is not automatically host rasterizer CPU | High backend identification; medium contributor hypothesis |
| Virtual CPU saturation | Two configured guest CPUs; historical CPU PSI87.59%, host QEMU287–364%; WHPX modules loaded | Top host TIDs unnamed; no vCPU-versus-render stack split; current guest98–99% idle | Medium possible contributor, low exclusive attribution |
| AVD HDD random I/O | Open AVD/system files on proven Disk1 HDD; historical queue/latency and guest stalls | No per-file events, access offsets or random/sequential classification; current disk idle | Medium storage-stall hypothesis; low exact file/random-I/O attribution |
| File-backed guest RAM | Generic OS paging remains possible | Live2560MiB MEM_PRIVATE region; no large RAM-file mapping/handle; no mem-path | Low candidate; high confidence against explicit RAM-file mode |
| Package optimization/startup | Historical broad startup timeouts/resource-loading stacks | No identified dominant dex2oat/installd workload; current sample idle | Low for package optimization specifically; medium general startup contention |
| Host RAM/pagefile | Some historical paging and background working sets | Prior boot retained>3GiB available; current>5GiB; no observed current swap churn; commit headroom | Low as primary explanation |
| Combined bottleneck | Independent historical graphics/kernel CPU and disk-pressure evidence | Cannot assign exact causal shares or hottest files/threads without successful boot trace | Medium historical F hypothesis; final exact attribution G |

## One configuration recommendation, not applied

**Reduce only the AVD display resolution from1080x2400 to720x1600, retaining the current graphics backend and other settings.** This is one reversible display configuration experiment:55.6% fewer output pixels targets the confirmed software-rendered display and historical graphics-path pressure without relocating valuable data or relying on unverified GT620 driver compatibility. Benefit is an inference; confidence in resolving ANRs is low-to-medium. It does not establish or fix HDD causality, and its smaller logical viewport must not be mistaken for completing the existing large/adaptive-layout gate. No resolution, density, renderer, CPU, RAM or other setting was changed.

**Physical-device acceptance is now preferable**, assuming a suitable owner-provided ARM64 Android device is available. Repeated environment failures plus absent boot trace privileges make further speculative emulator tuning lower-value than obtaining genuine Phase5A device evidence. This is analysis only, not Phase5B benchmarking or authorization to set up/install on a device.

## Preservation, roadmap and next task

No Kinetic source/build/install/uninstall/data clear, Room/provider mutation, model download, Local/Cloud request, approval or capability test. Signer and appId were not changed; expected signer remains `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2` (no fresh certificate extraction in this no-install task).

Read-only preservation checks match baseline: Room dump SHA256 `0FF2A5731B006AB4D47AD606F179435D99A719666BE2C8CED3CAE420BAEBB30A`; encrypted provider prefs `4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`; AVD config `D64BD0D0EACB3F2869E16D73C89BABC2FABFDFC8326D5EFD18A922E575CDDF4C`. No plaintext credential was exposed. Hash continuity is not a fresh decryption/provider-readiness test.

Roadmap is updated last: FINAL EMULATOR BOTTLENECK ATTRIBUTION COMPLETED, with limits; Phase5 IN PROGRESS; Phase5A DEVICE GATE BLOCKED; Phase5B NOT STARTED. No acceptance granted.

Exactly one next task, **not begun**: owner-authorized Phase5A device acceptance on a suitable physical ARM64 Android device, preserving the existing emulator installation and treating any new-device installation/provider setup as a separately scoped workflow.
