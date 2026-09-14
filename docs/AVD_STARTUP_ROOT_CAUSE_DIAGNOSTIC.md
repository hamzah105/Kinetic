# AVD startup root-cause diagnostic — 2026-09-07

**ENVIRONMENT BLOCKED — ROOT-CAUSE DIAGNOSTIC COMPLETED**

Primary classification: **F. HOST MEMORY / PAGEFILE / DISK PRESSURE**, specifically
disk/I/O starvation, **MEDIUM confidence**. Physical RAM/commit exhaustion was not
observed during this diagnostic. Graphics load is an additional observed contributor,
not a proven sole cause. The precise source of all disk traffic is not isolated.

This is a bounded environment diagnosis, not a successful Kinetic device gate. No
Kinetic source change, build, install, uninstall, data clear, provider request, or
application launch occurred. Phase 4C owner governance acceptance remains pending;
Phase 5 is NOT STARTED. Earlier failures and test results remain historical evidence.

## Host and toolchain

| Item | Observed value |
|---|---|
| OS | Windows 10 Pro, 10.0.19045, build 19045 |
| CPU | Intel Core i5-4690 @ 3.50 GHz; 4 physical / 4 logical processors |
| Installed RAM | 12 GiB: 4 GiB + 8 GiB physical modules |
| OS-visible RAM | 12,241 MiB (12,534,540 KiB) |
| Available RAM | Initial old-emulator sample about 2.4–2.5 GiB; 5,150 MiB after closing it; baseline diagnostic boot 5,218 down to 2,862 MiB |
| Commit | Initial 12,602,527,744 / 19,846,021,120 bytes; approximately 11.74 / 18.48 GiB; diagnostic busy-boot samples 11.46–11.67 GiB committed |
| Pagefiles | C: allocated 4,253 MiB, current use 715 MiB, peak 1,497 MiB; E: allocated 2,432 MiB, current use 418 MiB, peak 744 MiB |
| Pagefile configuration | AutomaticManagedPagefile=False; per-file initial/maximum settings report 0/0. Pagefiles demonstrably exist; these flags do not mean paging is disabled |
| C: | NTFS, about 110.80 GiB total / 5.01 GiB free at baseline; Kingston SV300S37A120G SSD |
| E: | NTFS, about 200 GiB total / 117.06 GiB free at baseline; WDC WD5000AAKX-07U6AA0 HDD shared with D: |
| Tool/data placement | SDK, AVDs and Gradle cache on E:, alongside an E: pagefile |
| GPU | NVIDIA GeForce GT 620; driver 23.21.13.9135 dated 2018-03-23 |
| Emulator | 37.1.11.0, build 15917651 |
| ADB/platform-tools | ADB 1.0.41; platform-tools 37.0.1-15733141 |
| Image | Installed android-36 / google_apis / x86_64, revision 7, extension 17 |

### Virtualization

Exact supported `emulator.exe -accel-check` output:

```text
accel:
0
WHPX(10.0.19045) is installed and usable.
accel
```

The actual QEMU command includes `-enable-whpx`, and runtime reports Windows
Hypervisor Platform operational. This is not software CPU emulation.
HypervisorPresent=True. CPU CIM reports VirtualizationFirmwareEnabled=False and
SLAT/VM-monitor flags False, which cannot establish disabled BIOS virtualization
in the face of the working hypervisor. VBS status=2 was observable; configured/running
security-services arrays were `{0}`. CIM optional-feature InstallState values were
HypervisorPlatform=2, VirtualMachinePlatform=1, Microsoft-Hyper-V-All=2. These do not
reconcile cleanly with usable WHPX. The administrator-only online feature query was
unavailable; no Windows feature or firmware setting was changed.

## Real AVD identity and launch bounds

Same name/path: `Kinetic_API_36`,
`E:\Projects\.tooling\android-avd\Kinetic_API_36.avd\config.ini`.
Pixel 7, x86_64, 2 cores, configured RAM 2G / heap 228M. Runtime raises these to
2,560 MB / 576m. Display 1080x2400, density 420; data 10G, cache 66MB, SD 512MB.
Configured GPU enabled=no / mode=auto; actual renderer is enabled automatically.
Audio input/output remain enabled. Fast Boot is configured; no config.ini edit.

Exactly one ordinary same-AVD diagnostic launch was performed:

```text
emulator.exe -avd Kinetic_API_36 -no-snapshot-load -verbose
```

The preceding emulator was normally closed with `adb emu kill` and termination
confirmed before launch. Baseline start: **12:06:30.199 PKT** (launcher PID 10744,
QEMU 5332). ADB offline observed 12:07:01; online observed **12:07:24.593**.
New boot ID: `59190c2a-5ff6-44b9-bab6-41985e64a190`.
Activity manager ready event: 12:08:21.555 (guest boot-relative 99,943 ms).
Screen-enable event: 12:08:32.551 (110,940 ms).
`sys.boot_completed=1` first observed **12:09:23.413**, approximately 173 seconds
after host launch. These are sampled observation bounds, not precise benchmarks.
Launcher was top-resumed by that observation, but neither usable Launcher nor
responsive System UI was established: System UI had already ANRed.
Normal shutdown succeeded after the final baseline captures around 12:12:35–36.

## New diagnostic-window failures and shared mechanism

The retained baseline event capture has **28 new ANR events**, from
**12:08:42.060 through 12:12:26.370**, across 16 process names. There are 26
startup-completion timeouts and two SIM-state broadcast timeouts. These are events,
not 28 distinct applications. Exact timestamps/reasons are in `baseline-events.log`.

| Process category | New baseline ANRs |
|---|---:|
| Kinetic | 0; zero process-start events, not exercised |
| System UI | 1 |
| Pixel Launcher | 0 |
| system_server / process named system | 0 |
| Chrome | 0 (Android started a background Chrome service; no Kinetic dispatch) |
| Other Android components | 27 |

Other affected processes: phone (1), inputmethod.latin (3), media.module (1),
googlequicksearchbox:interactor (1), android.as (2), messaging:rcs (1),
gms.persistent (2), googlequicksearchbox:search (2), photos (3), gms (2), dialer (5),
adservices.api (1), apps.restore (1), as.oss (1), calendar (1).

Historical September 6 Kinetic startup ANR, September 7 03:16:06 input-dispatch ANR,
previous initialization timeouts, and older system/Launcher/Chrome failures are
not included in these new counts. The earlier 04:40 restart is a separate window.
No log buffer was cleared.

### Early startup: CPU/composer saturation

System UI PID 1040 started 12:08:25.019 and failed startup at **12:08:42.060**.
Its report records load 9.95/3.94/1.46; PSI avg10 CPU some=74.65%, memory
some=0.05% / full=0.01%, I/O some=76.49% / full=8.08%.
For 12:08:19.595–12:08:42.074, graphics composer ranchu consumed 91% CPU, all
reported kernel time; its binder:525_1 thread reached 93% in a short sample.
Bootanimation=20%, system_server=18%, SurfaceFlinger=4.2%; guest total reports
76% kernel and 12% I/O wait. Host CPU was 89.5–100%, with QEMU later sampled at
313% across four logical CPUs. This supports substantial graphics/emulator work,
not a Kinetic-owned blocked thread or proof of a renderer deadlock.

### Later startup: sustained I/O stalls

At 12:11:57 the adservices startup-ANR report records guest **80% I/O wait**,
I/O PSI avg10 some=98.60% / full=58.65%; memory some=0.97% / full=0.78%.
At 12:12:10 the restore startup-ANR report records **82% I/O wait**, I/O PSI
some=98.37% / full=73.15%, while memory some=0.33% / full=0.29%.
The composer had fallen to 1.3–2.7% CPU. Therefore graphics alone does not
explain the continuing timeout cascade. System-server and zygote continued
starting/restarting unrelated processes; zygote-return operations took up to
1,278 ms in the sampled log. ANR dumping/restarts themselves add work, so later
pressure includes consequences as well as causes.

Host baseline samples at 12:08:38 show disk idle 1.9%, 25 ms transfer latency,
queue 3, with 2,984 MiB available and 11.65 GiB committed. Pages/sec ranged
194–2,812 during the busy interval; that metric alone does not identify pagefile
traffic or prove memory exhaustion. Guest memory at baseline end: available
1,151,076 KiB, swap total 1,899,220 KiB / free 1,760,168 KiB. Direct guest PSI
and swaps paths were permission-restricted; ANR reports supplied PSI without
rooting. No complete new-window lmkd/native-stack trace was retained, so absence
of LMK activity or a particular Java blocked stack is not claimed.

System_server PID 769 eventually answered Activity queries and SurfaceFlinger
answered its renderer query. Screen/Launcher bookkeeping progressing is not
proof of input responsiveness. Package/window checks did not establish a healthy
interactive gate. No new process-system ANR is required for widespread startup
starvation to exist. No Kinetic source defect was demonstrated.

## One temporary renderer comparison

Start **12:13:27.925**, same real AVD, command-line-only addition `-gpu host` with
the same `-no-snapshot-load -verbose`. No configuration or driver change.
Default auto had selected GLES SwiftShader / Vulkan Lavapipe after host Vulkan
capability detection failed with VK_ERROR_INITIALIZATION_FAILED (-3).
SurfaceFlinger identified SwiftShader; vulkan_renderengine=false.

Host comparison output stopped around Vulkan library initialization; no ADB
device or guest boot was reached during more than two minutes. QEMU PID 20916
remained at 0.703125 CPU seconds, about 78 MB working set, with no main window.
Normal CloseMainWindow returned False; ADB/console was unavailable. Only this
verified diagnostic QEMU process was terminated, after matching path/AVD/renderer
arguments. The wrapper exited; termination was confirmed before control launch.
This is a failed comparison, NOT zero-ANR success. Its last output does not prove
the exact blocked native function. Default console “Crash dump message” metadata
is not evidence of an actual crash. No second renderer experiment was run.

## Audio and snapshots

No owner response confirmed simultaneous Realtek jack popups during this run;
the explicitly conditional no-audio experiment was therefore not performed.
Audio causation remains untested. No Realtek/Windows audio/device settings changed.

Both baseline and control used cold boots with no snapshot restoration. Old
snapshot corruption is not demonstrated. The important distinction is that
`-no-snapshot-load` still allowed **file-backed guest RAM** and normal snapshot
saving. Both actual commands include `-mem-path ...\snapshots\default_boot\ram.img`
and `-mem-file-shared` on E:'s HDD. Real RAM image length=2,684,420,096 bytes.
Snapshot metadata was read only by us; normal emulator runtime/shutdown updated
snapshot metadata and the renderer attempt left its dirty marker. No snapshot,
userdata, or application file was manually deleted/rewritten.

## One isolated control AVD

`Kinetic_Diagnostic_Control_20260907`, separate directory under the same AVD root,
same installed API 36 image and Pixel 7 defaults (same 2 cores, 2G/228M config,
runtime 2,560MB/576m, auto software graphics, 1080x2400). No Kinetic installation,
account setup, or copying of real-AVD data. avdmanager warned that the image's
devices.xml could not be loaded but created the selected built-in Pixel 7 profile.

Launch **12:16:17.716**; ADB offline visible **12:16:47.074**. At
**12:21:45.753**, after **328 seconds**, it remained offline; boot completion,
System UI/Launcher responsiveness and guest ANR counts could not be measured.
One offline-transport reconnect did not make it online. Console identity confirmed
the control name. Normal console shutdown was requested successfully; control
files are retained, not deleted. This is **NO HEALTHY CONTROL ESTABLISHED**, not
a claim of measured control ANRs or proof of system-image corruption. First boot
has additional initialization work and is not a matched steady-state benchmark.

Control host evidence is nevertheless significant: RAM available 5,556–5,604 MiB
early, then 4,358–4,594 MiB; committed about 10.7 GiB. At 12:18:44 and 12:18:53,
HDD idle=0%, queue=101/102, latency=262/316 ms. At 12:20:02, idle=0%, queue=99,
latency=203 ms; at 12:20:29, idle=0%, queue=55, latency=415 ms. Host CPU samples
were roughly 28–57%, not globally saturated. This supports host/storage starvation
independent of Kinetic data, without proving the real AVD has no separate issue.
A control shutdown log also recorded UpdateLayeredWindowIndirect failure; that
window-composition error is retained, not treated as an Android ANR.

Cleanup: after the console shutdown acknowledgement, the control remained stuck
for more than three minutes. Its exact QEMU identity was checked before termination;
at 12:25:29 PID 8844 reported HasExited=True / zero handles. The remaining control
wrapper was stopped separately. The real AVD was not running during this cleanup.
Control files remain available. At that late sample HDD idle was still 0%, queue
86, with 5,329 MiB RAM available: pressure did not immediately disappear with guest
termination. No host reboot, disk repair, or unrelated-process shutdown was attempted.

## Preservation and limitations

Before and after the real baseline boot, Room file hash was unchanged:
`424997FC54F4501AE2A6FD271F900B2486ABF4B86252E8E770BA9F3771111D73`.
WAL remained zero bytes. Provider-preference hash remained:
`4D33EEDD841830A9A17096D14EA630D8E3FF5EBFDD7DB0CA61358A1D5A55C505`.
Only hashes/file sizes were printed, not credentials. Decryption/use was not
retested. The later renderer comparison never reached guest boot; the control
has entirely separate userdata. No extra real-AVD launch was performed to claim
a post-comparison application verification.

Existing built APK still hashes to
`B4EB0D7019DDE57914C4CB2F9195D8DD79E4529BAD633126E593DC8C784DADE5`.
The unchanged previously verified installed build is 0.4.4-astra-stellar, signer
`42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
No package mutation or signing operation occurred.
An additional read-only apksigner verification of the previously pulled installed
APK stalled under host I/O pressure and was stopped. It produced no certificate
result, so the signer above is the preceding successful verification, not a new
successful verification from that stalled command.
Real config/locator SHA-256 at final read:
`D64BD0D0EACB3F2869E16D73C89BABC2FABFDFC8326D5EFD18A922E575CDDF4C` /
`F1640E33E6F0F0E0ECFD2D95E3FB9F55F95AC83AC5FD7866A1594AD3A77D7BC6`.

No Kinetic source/config file had a post-08:00 modification in the pre-report
workspace scan. No Git metadata was available for a Git diff. Diagnostic changes
are this report, the engineering-checkpoint/roadmap updates, generated local logs,
one isolated control AVD, and normal emulator-managed state. Prior 219 passed /
0 failed/errors/skipped, lint 0 errors/5 warnings and successful build remain
historical, not rerun or newly asserted as a live gate.

## Exactly one recommended repair — NOT EXECUTED

Authorize a **single temporary same-AVD cold launch without file-backed Quick Boot
RAM**, preserving default renderer, guest RAM/cores, all userdata and existing
snapshot files:

```text
emulator.exe -avd Kinetic_API_36 -no-snapshot -feature -QuickbootFileBacked -verbose
```

This removes one evidenced HDD-backed memory path without moving/deleting the AVD,
changing Windows settings, replacing a GPU driver or reinstalling anything. It is
a reversible repair trial, not a proven cure: verify the resulting QEMU command
has no `-mem-path`, then assess fresh Android health before touching Kinetic.
The file-backed path's causal contribution remains an inference until that trial.
Do not execute it without owner authorization. No other repair is recommended here.

## Evidence and interpretation references

Local raw console/events/ActivityManager captures:
`E:\Projects\.tooling\temp\kinetic-avd-diagnostic-20260907`.
Host counter samples are recorded in the diagnostic tool transcript and summarized
above; no full host ETW or exhaustive guest trace was collected.

- [Android acceleration](https://developer.android.com/studio/run/emulator-acceleration): CPU acceleration and rendering are separate; supported renderer modes.
- [Android command-line options](https://developer.android.com/studio/run/emulator-commandline): cold boot versus disabling snapshot load/save.
- [Android file-backed RAM documentation](https://developer.android.com/studio/releases/emulator#28-0-16): mapped guest RAM behavior and the QuickbootFileBacked feature.
- [Emulator implementation](https://android.googlesource.com/platform/external/qemu/+/emu-master-dev/android-qemu2-glue/main.cpp): feature-gated RAM mapping; our installed console independently confirms the active path.
- [Linux PSI documentation](https://docs.kernel.org/accounting/psi.html): some/full measure stall time, not RAM occupancy; system-wide CPU full=0 is not proof of no CPU contention.
- [Android troubleshooting](https://developer.android.com/studio/run/emulator-troubleshooting): Windows commit accounting differs from available physical RAM.
