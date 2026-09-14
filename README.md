# Project Kinetic

Kinetic is a Kotlin-first native Android foundation for autonomous and semi-autonomous agents.

## Project status

- Phase 0: Complete
- Phase 1 / 1.1: Complete and owner accepted
- Phase 2A: Implemented and owner accepted
- Phase 2B and Phase 3A/B/C: Accepted
- Phase 4A/B: Accepted
- Phase 4C: Owner accepted; Phase 4 complete (explicit SQLite supersession/recall acceptance).
- Unnumbered Astra / Stellar gate: engineering device gate passed; separate owner acceptance pending; see [verification checkpoint](docs/ASTRA_ENGINEERING_VERIFICATION.md) and [authoritative state](roadmap.md).
- Phase 5A: Accepted, including the later physical ARM64 engineering and owner gate. Earlier emulator failures remain historical evidence.
- Phase 5B: Candidate1 provenance-blocked; Candidate2 unavailable on SM-A065F; Candidate3A physical feasibility passed (B, performance limited). Candidate3B native implementation is tracked in [native build state](docs/architecture/PHASE5B_CANDIDATE3B_NATIVE_BUILD.md); it is not a production default or device acceptance claim.
- Phase6: opt-in deterministic Hybrid router foundation; MANUAL remains default. See [routing policy and validation limits](docs/architecture/PHASE6_HYBRID_MODEL_ROUTER.md). Phase7 not started.
- Build-first strategy: no routine ADB/device/emulator gate. Device acceptance is deferred to the documented [final integration and real-device gate](docs/architecture/BUILD_FIRST_STRATEGY.md).

Kinetic supports durable streaming conversation, governed memory, bounded context and a controlled structured-tool loop. Fake, OpenAI-compatible and direct OpenAI Responses adapters remain behind the pure Kotlin provider port. Models propose only existing allowlisted functions; Kinetic validates, authorizes, records and executes. Structured tools default off for cloud models. Ordinary prose is never executable.

## Modules

- `app`: Android/Compose Stellar conversation UI and manual dependency assembly.
- `core/kernel`: pure Kotlin agent, model, tool, policy, session, and logging contracts.
- `data/model`: Cloud adapters, isolated Keystore credentials, and the experimental app-packaged llama.cpp text provider with verified manual GGUF import.
- `data/persistence`: Room-backed conversations, journal, approvals, turns, and effect identity.
- `data/android-capabilities`: Foreground, user-approved HTTPS/share/Settings/clipboard/dialer/email handoffs.
- `research/phase0`: approved ecosystem and policy research retained unchanged.

No arbitrary device control, dynamic execution, shell runtime, Accessibility service, MCP, background autonomy, or dangerous permission is included.

Candidate3B also implements local session summarization and hardened model-data
management; see [completeness and validation limits](docs/architecture/PHASE5B_LOCAL_PROVIDER_COMPLETENESS.md).
The full summary source must fit 1024 tokens including the output reserve, otherwise
compaction returns a typed limitation without truncation or fallback. Model import,
integrity state, provenance and explicit model-only deletion are in Provider settings.
Real JNI/device validation remains deferred to Phase10A.

## Build and test

Candidate3B uses existing NDK28.2.13676358 and CMake3.30.2. Point local.properties
`cmake.dir` at the installed CMake if it is outside the Android SDK; Ninja must be
available on PATH. Use JDK21 for the kernel toolchain. Pinned native source is local
under third_party and verified by CMake; no model download is part of building.
Use local.properties `kinetic.debug.keystore` to select the existing continuity
key when the user-profile default differs; never generate a replacement or commit
keystores/passwords. This workspace points to its preserved tooling debug key.
Device commands below are reference only for a later explicitly authorized gate,
not prerequisites for ongoing implementation.

The verified workspace toolchain uses JDK 21 for Gradle, Java 17 bytecode, and Android SDK 36. See [environment setup](docs/development-environment.md).

```text
./gradlew --max-workers=1 :core:kernel:test :data:model:testDebugUnitTest :data:android-capabilities:testDebugUnitTest :app:testDebugUnitTest
./gradlew --max-workers=1 verifyDeviceTestSafety lintDebug assembleDebug
```

On Windows PowerShell, use `./gradlew.bat`. Verify the installed and built APK signing certificates match before updating. Never uninstall or clear valuable app data to fix signing. Update and launch with:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.kinetic.app/.MainActivity
```

Fake-provider prompts include `echo: hello`, `protected demo`, `current app time`, `failing demo`, `unknown tool`, and `model failure`.

Provider settings also offer **Use Local · SIMULATED / TEST**. This is a network-independent,
scripted development backend, not a real LLM. Try `hello`, `echo: hello`, `protected demo`,
or `open https://example.com`. Actions still require normal validation and approval.
`local unavailable` and `local malformed` exercise safe failure handling. Debug displays
ordinary device facts and explicitly unavailable real benchmark measurements. Selecting
Local does not erase cloud configuration or credentials. Return to Use Cloud or Use OpenAI
explicitly; manual mode never switches providers or falls back automatically.

Hybrid routing is a separate explicit choice in Provider settings. Its Router inspector
shows fixed metadata/reasons, not prompts or secrets. Network state is explicitly
user supplied and defaults UNKNOWN; Hybrid excludes cloud until AVAILABLE is supplied.
Private/local-only and structured-tool requirements constrain selection. A selected
provider stays pinned through the turn; failures never trigger another provider.

See [Phase 1 architecture](docs/architecture/PHASE1_AGENT_KERNEL.md), [Phase 2A conversation architecture](docs/architecture/PHASE2A_STREAMING_CONVERSATION.md), and [Phase 2B structured-tool architecture](docs/architecture/PHASE2B_STRUCTURED_TOOL_CALLING.md).

## Security and behavior

The source manifest adds only normal `android.permission.INTERNET` and disables cleartext traffic. API keys are AES-GCM encrypted with a non-exportable Android Keystore key; credentials, authorization headers, raw provider bodies, and stack traces do not cross safe error or journal boundaries.

The Compose shell provides durable user/assistant/tool history, progressive proposal and continuation output, requested-tool and safe-argument status, exact approval controls, provider/model status, safe errors, and a per-model structured-tool switch. Debug journal access remains available. Configuration is disabled during an active turn.

Provider failures become typed stable kernel errors for configuration, authentication, network, timeout, rate limit, server, malformed response, and unexpected failure. User cancellation remains distinct.

Automated network tests use local TLS `MockWebServer`; no test uses a real API key or paid public endpoint. They cover fragmented UTF-8 structured proposals, strict arguments, identity, continuation ordering, bounded context, errors, disconnects, switching, and secret exclusion. Runtime and Room tests cover policy/approval, prose bypass, replay resistance, completed-effect durability, recovery without resend, session isolation, reopen durability, and ordering.

Device tests run only from library-owned, disposable instrumentation packages. Do not add instrumentation sources under `app/src/androidTest`: AGP/UTP installs and then uninstalls the target application APK, which would remove the owner's `dev.kinetic.app` data. `verifyDeviceTestSafety` enforces this boundary and rejects destructive package commands targeting the production application ID.

The unnumbered gate is documented in [Astra architecture](docs/architecture/ASTRA_INTEGRATION_GATE.md), [Stellar design/backlog](docs/architecture/KINETIC_STELLAR_PRODUCT_GATE.md) and [owner acceptance](docs/ASTRA_OWNER_ACCEPTANCE.md). Real OpenAI credentials are entered only inside Kinetic. Engineering tests do not constitute owner acceptance or authorize Phase 5.
