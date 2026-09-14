# Phase 5B Candidate 1 — physical preflight, benchmark NOT RUN

September 13 provenance follow-up: **D. INCONCLUSIVE — BINARY-SPECIFIC PROVENANCE
CANNOT BE ESTABLISHED; Candidate 1 remains BLOCKED.** See the
[binary-specific audit and current next task](architecture/PHASE5B_LITERTLM_0170_LICENSE_PROVENANCE.md).
No device was used for that follow-up; all benchmark gates remain NOT RUN.
The September 12 evidence below is preserved as history.

Date:2026-09-12. **BLOCKED — RUNTIME THIRD-PARTY LICENSE PROVENANCE UNRESOLVED.**
Candidate1 is evaluated only at preflight; not an inference failure or device rejection.
See [exact runtime/model audit](architecture/PHASE5B_REAL_LOCAL_BACKEND.md).

## Starting state and fresh device facts

Phase4 COMPLETE;4C ACCEPTED;5 IN PROGRESS;5A COMPLETE/ACCEPTED;5B NOT STARTED at entry,
explicitly authorized by the owner to begin. Separate Astra/Stellar owner gate pending.
ADB enumeration found the authorized Samsung SM-A065F and the emulator. Only the
physical phone was queried; no emulator command, fallback or benchmark occurred.
Operational serial is omitted from permanent documentation.

At approximately02:27 PKT, ordinary read-only probes reported:

| Fact | Observation |
|---|---|
| Physical device | samsung SM-A065F; ro.kernel.qemu=0 |
| OS/ABI | Android16/API36; primary arm64-v8a; list arm64-v8a,armeabi-v7a,armeabi |
| SoC | MT6769V/CZ |
| MemTotal | 3,720,468KiB =3,809,759,232bytes |
| MemAvailable | 911,808KiB, snapshot only |
| Heap properties | growthlimit256m / heapsize512m; not a fresh ActivityManager largeMemoryClass measurement |
| Android lowMemory flag | NOT_MEASURABLE by the bounded read-only query used; no false claim of low-memory clearance |
| /data free | 71,640,596KiB =73,359,970,304bytes |
| Battery |31%, USB charging,34C; later02:33 sample35C |
| Thermal | Thermal Status0 at preflight |
| Host storage | C:17,721,507,840bytes; E:121,923,596,288bytes at subsequent storage sample |

CPU is the intended first backend, NOT_RUN. AAR min24 and packagedARM64 support the
basic ABI/API preconditions; safe model initialization and practical memory fit are
unverified. Current available RAM is limited, but no official hard RAM exclusion was
established. No stress test, app termination or owner setting change was performed.

Prospective CPU artifact size M=2,588,147,712bytes. Conservative storage requirement:
**staging M + final M +2GiB reserve =7,323,779,072bytes**. Phone free space exceeds
this; E: also exceeds one host artifact plus2GiB reserve. Storage arithmetic passes
at the sample, but this is not permission to bypass the unresolved license gate.
No owner files/apps deleted and no model bytes transferred/downloaded.

## Stop reason

Official LiteRT-LM0.17.0 AAR root/POM declares Apache-2.0, while its bundled notice
contains a Google Runtime Environment GPLv2 section without a section-specific
linking exception. Whether this describes a build-time tool or shipped component
has not been established. This is **license provenance unclear**, not proven GPL
linkage or a legal incompatibility conclusion. The owner's explicit stop condition
was applied before integration/model provisioning. Exact hashes/URLs in architecture
report make the finding reproducible. No alternative runtime/model was tried.

## Benchmark and engineering result matrix

| Check | Result |
|---|---|
| Model hash/bytes | Published metadata pinned; downloaded host/app verification NOT_RUN |
| Native supply chain | Official AAR inspected, minSDK/ABI/ELF16KB alignment observed; third-party licensing BLOCKED; signed APK packaging NOT_RUN |
| Local REAL initialization/greeting/context | NOT_RUN |
| Genuine streaming/tokenizer/envelope admission | NOT_RUN on device |
| Automatic tool execution | No runtime integrated/enabled; proposal-only upstream API identified, Kinetic adapter unverified |
| Pseudo-tool non-execution regression | No new test; unchanged prior boundaries, no claim of new regression pass |
| Provider real-local pinning / switching | NOT_RUN; existing simulated tests retained |
| Offline inference | NOT_RUN, no owner network changes requested |
| Cold load / warm TTFT / throughput | NOT_RUN:0 trials, no fabricated latency or tokens/sec |
| Model PSS/RSS / peak/steady memory | NOT_RUN; preflight MemAvailable is not model memory |
| Load/prefill/decode cancellation latency | NOT_RUN |
| Battery drain | NOT_MEASURABLE in charging preflight; no workload drain measurement |
| Thermal/throttling under inference | NOT_RUN; baseline thermal status is not a workload result |
| Ten-fixture corpus / JSON / structured-tool / planning scores | All NOT_RUN; do not substitute zero quality scores or NOT_SUPPORTED |
| Process death / recovery / replay | NOT_RUN for Candidate1; Phase5A evidence preserved historically |
| Provider credentials / Room history | No app-data access, update or mutation; no fresh post-update preservation/decryption claim |
| Permissions / schema | No source/dependency changes; existing INTERNET + signature-receiver boundary and Roomv6 retained |
| Current APK size/hash/signer | No build/update; prior accepted APK/signer retained, not freshly reverified |
| Kinetic ANR/crash/OOM/native errors | No Candidate1 workload or log audit; NOT_RUN, not a fabricated zero-error gate |

## Test/build accounting

No production source, Gradle, manifest or Room edit. No install, uninstall, clear-data,
Keystore/provider change, real cloud request, model load, app-target instrumentation,
dependency integration or alternative-candidate work. Official AAR audit cache only,
outside project source; never executed. Documentation is the only project change.

Historical accepted suites, not rerun: kernelJVM116, modelJVM49, capabilityJVM12,
appJVM16, persistenceAndroid20, model/securityAndroid14, capabilityAndroid17;
**244 passed,0 failed,0 errors,0 skipped**. Historical verifyDeviceTestSafety passed;
historical lint0errors/5unchangedwarnings and assembleDebug passed. All fresh Phase5B
automated/lint/build gates are NOT_RUN because implementation stopped at preflight.

## Final status

Phase5 IN PROGRESS;5A ACCEPTED;5B IN PROGRESS, Candidate1 evaluated at preflight,
BLOCKED — runtime third-party license provenance unresolved. No production winner,
no Candidate2, no Phase6. Emulator failures and the known OpenRouter textual-tool
issue remain historical/separate and unfixed. Astra/Stellar owner acceptance pending.

Owner acceptance flow is not issued: Candidate1 engineering has not passed.
Exactly one recommended next task, not begun: resolve the pinned runtime's third-party
license provenance before resuming the same Candidate1 implementation/benchmark gate.
Roadmap is updated last.
