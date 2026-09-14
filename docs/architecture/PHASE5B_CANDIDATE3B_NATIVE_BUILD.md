# Candidate 3B native implementation — 2026-09-14

Status: **IMPLEMENTED — EXPERIMENTAL TEXT-ONLY FOUNDATION; HOST VERIFIED.** All device
integration/acceptance checks **DEFERRED** by owner build-first strategy. No ADB,
phone requirement, emulator start, install or device workload in this task.
See [strategy and final gate](BUILD_FIRST_STRATEGY.md). Candidate3A's prior B result
is preserved; it is not evidence for this new embedded JNI path.

## Implemented design

- `:data:model` owns a thin original C++ JNI bridge, Kotlin adapter and model-data
  import. Core kernel remains pure Kotlin, with no Android/JNI/toolchain dependency.
- Reviewed llama.cpp v0.4.0 commit5266f24da75dc449bd56cbed7addb9c8e4a6a73e is vendored
  as its unmodified official source ZIP under third_party. CMake verifies SHA256
  CF07FFF2E0F5859E633A137466BDEE72B06D75C00B72EE591FF922EAD864EC68, extracts locally
  and builds llama/ggml CPU statically into `libkinetic_local.so`. No floating
  source, prebuilt opaque wrapper, runtime executable download or plugin lookup.
- NDK28.2.13676358, CMake3.30.2, C++17; only arm64-v8a, Android app minAPI26
  unchanged. Static C++ runtime, PIC,16KB linker alignment requested. CPU2threads,
  batch/ubatch64; GPU/OpenMP/KleidiAI/repacking/common/server/tools/MTMD disabled.
  Native build succeeded; host inspection confirms ELF64/AArch64, all five JNI
  exports matching compiled JVM declarations, all PT_LOAD alignment0x4000, and
  only libm.so/libdl.so/libc.so DT_NEEDED. APK inspection is recorded after assembly.
- `System.loadLibrary("kinetic_local")` loads signed-app code, never a downloaded
  path. The model remains data, not executable .so/DEX/JAR. No shell/Termux/PRoot.
- Exactly Qwen3-0.6B-Q4_0.gguf,428970080bytes, publisher revision
  b5f37287796e5be0ea3dab2e7430873fb3f73e49, SHA256
  da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4.
  No weights packaged/committed and no automatic download. Manual Android document
  picker copies selected content into noBackupFilesDir/local-models. Bounded size,
  SHA256, free-space check, private temporary file, fsync and atomic replacement;
  failed/cancelled imports do not replace a valid model. No broad storage permission
  or persistent URI grant. Rehash under a mutex before every native load; verification
  latency is additional and unmeasured on device. Interrupted process death can leave
  a private .part file; bounded orphan cleanup is future hardening, not owner-data deletion.
- Manual `LOCAL_LLAMA` selection is distinct from Fake, simulated-local, compatible
  cloud and OpenAI. No default change, no routing/fallback, no credential lookup for
  local requests. Local summarization explicitly unavailable rather than silently
  returning a simulated or cloud summary. Current real local mode is text only.
- Provider-neutral Started/TextDelta/Completed events; incremental UTF-8 preserves
  split codepoints. No parser turns printed JSON or tool syntax into proposals.
  `toolSupport=Unavailable`, descriptor toolProposals/structuredOutput=false.
- Existing ContextPlanner gets optional provider-owned reserves (64 output/256
  system for this adapter); cloud defaults are unchanged. Local prompt uses only
  selected messages, bounded governed memories/summary, escaped role delimiters and
  explicit untrusted-context labels. Native exact tokenization rejects prompt+64
  above1024; no silent context shift. Estimates cannot guarantee token fit.
- Correct Qwen3 role delimiters and non-thinking assistant prefix; no tools schema.
  Sampling temperature0.7/top-k20/top-p0.8/seed42, cap64. This is not a production
  quality recommendation; prior uppercase/JSON/approval probe failures still apply.
- One native generation per process; background IO worker, bounded stream
  backpressure, cooperative atomic cancellation during model progress and decode,
 120-second native deadline, shared registry ownership (no raw freed JNI handle),
  RAII sampler/context/model cleanup. No warm model retained between turns.
  Atomic worker startup installs cleanup even when cancellation races allocation;
  ensureActive precedes model load. This use of the documented delicate
  [CoroutineStart.ATOMIC](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-start/-a-t-o-m-i-c/)
  performs no lifecycle-bound UI work after cancellation. Cancellation joins worker
  cleanup before next generation. Device latency and
  abort-callback responsiveness must still be validated.
- Preload Android available-memory guard preserves Candidate3A threshold1060000KiB
  and rejects lowMemory. No claim of continuous Android memory/thermal safety or
  immunity to native OOM. Native exceptions expose only static safe codes;
  llama/ggml log callback is suppressed, prompts/paths/credentials not deliberately logged.

## Security and compatibility boundaries

ToolRegistry → Policy → ApprovalGate → EffectLedger → Android coordinator remains
unchanged. Native code receives only an approved model-data path and rendered text;
no registry, permission, policy, approval, credential, Room or Android capability object.
Printed model text cannot change secrets, settings, approvals or execute actions.
Application ID, cloud secrets, Roomv6, manifest and permission surface are unchanged.
An optional local.properties debug-keystore path selects the existing preserved
42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2 signer because
the user-profile default key instead fingerprints8B78A0A989A5E38BE991546F945862073D6E211EF135E90625233F4487B4D3DC.
No key generated/replaced, no secret/password committed; only the local path is set.
No installed-package/data preservation claim is fabricated:
there was no device operation. The new runtime library is ARM64-only; existing UI
dependency libraries retain their other APK ABIs. ABI/process guards reject
unsupported local inference; no x86 emulator or 32-bit model execution is promised.

Root MIT source license and NDK toolchain notices are app assets. Source archive
retains upstream notices. Model is manually imported, not redistributed in APK;
[official Qwen license](https://huggingface.co/Qwen/Qwen3-0.6B/blob/c1899de289a04d12100db370d81485cdf75e47ca/LICENSE)
is Apache2.0. Candidate3A documented publisher conversion provenance, not an exact
reproduced conversion. Final shipping dependency/notice audit remains required if
build targets or runtime distribution change.

## Host verification

Host unit results: **206 passed,0failed,0errors,0skipped**: kernel116, model62,
app16, Android-capability unit12. Thirteen added tests cover text-only proposals,
UTF-8, import digest/size, context plan/reserves/delimiters, availability/failures,
no credential/cloud fallback, and fake-native cancellation/cleanup/reuse (including
cancellation immediately after native-handle allocation). They
do not execute Android JNI. App Kotlin compilation, native build, signed debug
assembly and host device-test safety guard passed. Lint: **0 errors,4 warnings**
(OldTargetApi, newer Compose compiler version available, two UseKtx suggestions);
no unrelated toolchain/SDK upgrade was performed. No instrumentation tests ran.
Early launch issues: JDK17 path needed its actual child
directory; PowerShell split an unquoted -D option; kernel needs existing JDK21;
sandbox denied Kotlin daemon marker under user profile. These are host setup
observations, not Android failures. No device workaround authorized or attempted.
The first JNI compile caught the obsolete use_mmap field in original bridge code;
corrected against pinned llama.h to load_mode=LLAMA_LOAD_MODE_MMAP and explicitly
disabled extra weight-repacking buffers. Upstream source was not modified.
The optional debug-key path Gradle addition required an explicit Properties import
to avoid a DSL name collision; corrected. First successful assembly retained
57,606,720-byte native debug symbols because the app's default NDK could not strip
the library. Both app and model modules now select the same existing NDK28.2;
stripping now produces3,987,072bytes, SHA256
AFD586804342DED5E2557BA4EBF4944349911B7FA9706BDA3EC4970290C6469E,
also verified by hashing the APK entry. No toolchain version download/migration.
The incremental APK retained empty space from the old large entry. That generated
APK was moved to tooling temp (recoverable), then only the missing APK output was
regenerated; no clean, owner-file deletion or native source rebuild required.

Verification commands used existing JDK21, Gradle user home under tooling, Ninja
on PATH, offline dependencies, --no-daemon --max-workers=2 --no-parallel, and
-Pkotlin.compiler.execution.strategy=in-process after the sandbox daemon fallback:

```text
gradlew :core:kernel:test :data:model:testDebugUnitTest :app:compileDebugKotlin
gradlew :app:testDebugUnitTest :data:android-capabilities:testDebugUnitTest
gradlew :data:model:testDebugUnitTest :app:lintDebug :app:assembleDebug verifyDeviceTestSafety
```

Signature verification of the signed output matches the preserved42A087…E446F2
certificate. APK zipalign -c -P16 -v4 passes. Manifest remains dev.kinetic.app,
versionCode1/versionName0.4.4-astra-stellar, min26/target36; only INTERNET plus the
existing generated app-scoped signature permission. No GGUF entries; native
notices present. These are host artifact checks, not an installed-device check.

Final compact APK: `app/build/outputs/apk/debug/app-debug.apk`, **16,980,113bytes**,
SHA256 **5E1A64453D686188865E9E3FA8A9E8D88B17119B351B214879E921076FC9C8F1**.
Signature and16KB ZIP alignment reverified after regenerating the output; native
entry3,987,072bytes, no GGUF entries. Final output regeneration succeeded in36s;
the preceding unit/lint/assemble/safety run succeeded in3m43s. No source changes
after that verification (documentation/roadmap reconciliation only).

## Completeness follow-up — 2026-09-15

The [build-only local-provider completeness record](PHASE5B_LOCAL_PROVIDER_COMPLETENESS.md)
supersedes the initial unsupported-summary/removal/orphan-cleanup limitations below.
It adds real local summarization, typed full-source token limitations, explicit native
end-of-generation verification (six JNI exports), lifecycle states, stale-partial
cleanup and lease-safe model-data deletion. Initial build hashes/counts above remain
historical; follow-up evidence is recorded separately. Device validation stays deferred.

## Initial remaining build work (historical foundation checkpoint)

Local summarization is deliberately unsupported and fails explicitly without fake
or cloud fallback. Further model-management UI (removal/orphan-import recovery),
sustained performance/thermal policy and product polish are not claimed complete.
Candidate3B supplies the requested native text streaming foundation, not a complete
production local-provider product or model-quality acceptance. Phase5B stays IN PROGRESS.
Candidate1 provenance-blocked and Candidate2 unavailable statuses are unchanged;
Candidate3A remains B. Phase6 NOT STARTED. No production default chosen.

## Deferred final real-device gate

Native load/link/ABI/API/16KB behavior, SAF import/cancellation/process-death storage,
real Unicode streaming/quality/performance, 1024-context boundaries, native abort and
reload/leaks, foreground/background/rotation/process death, low-memory/thermal/LMKD,
provider switching/no cloud fallback, Room/credential preservation, all governed
capability approval/exactly-once/no-replay, visual/IME/accessibility and fresh logs.
No embedded inference, speed, safety or owner acceptance result is claimed yet.
The strategy document also schedules later prompt control, voice, secure onboarding,
scoped personalization/context and broader approved Android capabilities; not built here.
