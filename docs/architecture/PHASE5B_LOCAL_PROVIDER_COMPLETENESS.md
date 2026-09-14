# Phase 5B local-provider completeness — build-only, 2026-09-15

## Scope and architecture

Extends the existing Candidate3B native text adapter, not a second memory system.
Manually selected LOCAL_LLAMA now implements ConversationSummaryGenerator through
the same verified model lease and packaged llama.cpp engine. ConfiguredModelProvider
does not access credentials or use Cloud/Fake/SIMULATED as a local failure fallback.
ConversationSummaryService remains the sole installer of completed SessionSummary
records: SESSION-bound, MODEL_DERIVED, untrusted, with source digest and coverage.
Original messages are read only; USER memory, governance, tools, policy, approval,
ledger and Android execution receive no new authority or write path. Room remains v6.

The application-owned Qwen summary template explicitly treats source as data and
escapes role-token delimiters. The entire previous summary and new compaction source
are supplied without silent truncation. A 32768-byte preflight bounds JNI input;
the pinned native tokenizer checks the FULL prompt plus 64 reserved output tokens
against 1024 before prompt evaluation. Oversized input returns typed CONTEXT_LIMIT.
This implementation intentionally uses the authorized typed-limitation option, not
chunking; large histories can therefore remain uncompacted on this small model.
Prompt-injection resistance is not a claim of guaranteed model obedience: all output
is inert text regardless of what it says. No textual tool parser is introduced.

Summary collection is private and bounded to 4000 characters. Blank/malformed UTF-8,
secret-shaped, failed, cancelled and output-cap-limited results are rejected. A new
native completed() JNI query distinguishes actual model end-of-generation from the
64-token cap; only end-of-generation can finish a summary. Ordinary conversation
streaming retains its existing cap behavior. Exact-token code is built and inspected,
not executed on a device in this task. Native deadline remains 120 seconds.
Cancellation joins worker teardown before releasing the model lease. The service
checks cancellation again before repository save; no intermediate summary is saved.

## Model lifecycle and storage boundary

Public construction accepts only the pinned Qwen artifact; internal small-fixture
size/hash injection exists for host tests, not arbitrary-model UI support.

- Qwen3-0.6B-Q4_0.gguf, 428970080 bytes.
- SHA256 DA2572F16C06133561CE56ACCAA822216F2391EF4D37FBA427801CD6736417D4.
- Publisher revision b5f37287796e5be0ea3dab2e7430873fb3f73e49.
- llama.cpp v0.4.0, commit 5266f24da75dc449bd56cbed7addb9c8e4a6a73e.
- Existing vendored source and license/NOTICE assets retained; no model downloader.

SAF OpenDocument is the only UI import route. Data goes to the canonical private
noBackupFilesDir/local-models directory, never an executable load path. Storage
preflight requires artifact size plus 256 MiB free, retaining room for an existing
copy. Staging writes cannot exceed the artifact size. Exact size/hash, file fsync,
cancellation check and ATOMIC_MOVE precede publication. Failure retains the old
artifact; reimporting the same valid bytes is safe. No non-atomic fallback exists.

States exposed via read-only StateFlow: NOT_INSTALLED, UNVERIFIED, VERIFYING, READY,
INVALID, ERROR, WAITING_TO_DELETE. READY means data verified, not device inference
accepted. Startup ignores partials and cleans only regular non-symlink files matching
qwen-import-[0-9]+.part in the dedicated directory, without recursion. Other files
and directories are untouched. Startup and every pre-native lease rehash the model.

Explicit UI deletion confirmation waits for the same lease that covers model load,
generation and joined native teardown. Only the fixed GGUF and owned staging files
are targeted. No directory removal, Room access, credential access or Keystore API.
After deletion availability is MODEL_NOT_INSTALLED; provider selection does not
silently change. Provider UI exposes bounded status, runtime, revision, size and hash.
No dangerous permission, application ID, signer or cloud credential implementation
change is required. Single-process application ownership is retained.

## Host verification

**PASSED: 222 tests, 0 failures, 0 errors, 0 skipped.** Kernel116, model78,
app16, Android-capability JVM12. Sixteen tests added (15 completeness fixtures,
one native completion/cleanup fixture); existing manual-selection test also now
checks local summary success and unavailable failure without credential/HTTP access.
Fixtures exercise production Kotlin adapter/service, file lifecycle and native-call
ownership seams; not ARM64 JNI, Android SAF, actual inference or Room on Android.
Existing kernel summary security/provenance/history/approval tests remain green.

Compile passed. Final serialized unit/lint/native/assemble/safety gate passed in
5m11s using existing JDK21, offline dependencies, two workers, no parallel projects,
in-process Kotlin compiler, pinned NDK/CMake and existing Ninja. An initial command
failed because PowerShell split an unquoted -P option; quoting corrected it before
the successful checks. No product defect or device result inferred from that failure.

Lint: **0 errors / 4 warnings** (OldTargetApi, GradleDependency, two UseKtx).
No unrelated toolchain upgrade. verifyDeviceTestSafety and its host guard tests
passed. Lint Android-test source analysis is not instrumentation execution.

Signed APK: app/build/outputs/apk/debug/app-debug.apk, **20,977,567 bytes**,
SHA256 **E6055BDCD526A08389588BF8A65E305F40FF35F0439C1BA626D1CE307ED3420E**.
Preserved signer verified:
**42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2**.
zipalign -c -P16 4 passed, exit0. Application dev.kinetic.app; version remains
0.4.4-astra-stellar. Permission audit: INTERNET and existing app-scoped non-exported
receiver signature permission only. Source manifest and Room6 schema unchanged.

APK native entry lib/arm64-v8a/libkinetic_local.so: **3,987,376 bytes**,
SHA256 **CCB1C6075A919D662D10A3995EED1EF2351FCFDA35DDE0697D1067173CCA18EE**.
Native ELF64/AArch64, all PT_LOAD alignments0x4000, six expected JNI exports;
DT_NEEDED only libm/libdl/libc. Other ABI entries are existing AndroidX UI libraries,
not new local runtimes. No GGUF bundled. Three native-notice assets remain packaged.
Vendored llama ZIP hash reverified as
CF07FFF2E0F5859E633A137466BDEE72B06D75C00B72EE591FF922EAD864EC68.

Workspace has no Git repository, so no fabricated clean-worktree/diff claim. Changes
are scoped to summary failure/cancellation handling, model adapter/native bridge,
verified model lifecycle, configured dispatch, Provider/ViewModel UI, focused tests
and documentation. No signing/build configuration, credential-store implementation,
manifest, Room schema or vendored-source edits in this task.

Classification: **CANDIDATE3B LOCAL PROVIDER IMPLEMENTATION COMPLETE AT HOST LEVEL;
REAL LOCAL TEXT + LOCAL SUMMARIZATION + HARDENED MODEL MANAGEMENT IMPLEMENTED;
HOST VERIFIED; DEVICE VALIDATION DEFERRED TO PHASE10A.** This is not Phase5B
device/owner/production acceptance. Large-source compaction is explicitly limited;
model quality and sustained performance remain unverified in the embedded app.

Exactly one next recommended task, not begun: **PHASE6 — BUILD-ONLY HYBRID MODEL
ROUTER FOUNDATION**, requiring a subsequent owner instruction.

## Deferred to Phase10A

Real tokenizer boundary/inference quality, JNI load/link and completion query,
native cancellation/teardown under Android scheduling, SAF import/process death,
filesystem atomicity/durability on Android, actual low-storage/low-memory/thermal
behavior, native-active deletion, UI/rotation/accessibility, device Room/credential
preservation, approval/exactly-once/no-replay and process log audits remain DEFERRED.
No ADB, emulator, physical test, instrumentation, install, cloud request or real
model request is authorized or performed here. Host fixture data is not owner data.

Candidate1 remains provenance-blocked; Candidate2 unavailable on SM-A065F;
Candidate3A remains B: viable but performance limited. Candidate3 is not a permanent
production default. Phase5B remains in progress; Phase6 has not begun.
