# Kinetic development environment

Verified on 2026-08-28 for the Phase 2A implementation. The live cloud-provider acceptance remains pending because no real API key was supplied to the development run.

## Host and toolchain

| Item | Verified value |
|---|---|
| Host | Windows 10 build 19045, x86_64 |
| Android Studio | 2026.1, build `AI-261.26222.65.2613.16025427` |
| Build JDK | Eclipse Temurin 21.0.12.1 LTS at `E:\Projects\.tooling\jdk21\jdk-21.0.12.1+1` |
| Gradle | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin plugins | 2.3.21 |
| Android SDK | `E:\Projects\.tooling\android-sdk` |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| AVD | `Kinetic_API_36`, Android 36 Google APIs x86_64 |
| Application ID | `dev.kinetic.app` |
| Version | `0.2.0-phase2a` |

The project intentionally remains on the already-working toolchain. Phase 2A did not upgrade Android Studio, SDK, Gradle, AGP, Kotlin, JDK, or NDK. The command-line-tools/AGP SDK XML version warning remains non-blocking.

The Windows system drive reached capacity during validation. Final commands used the existing workspace-local Gradle installation, cache, and temporary directory on `E:`. The regenerable Gradle 8.13 transform cache under the user profile was removed to restore enough host space; no project source or user/application data was deleted.

## Project configuration

| Module | Purpose |
|---|---|
| `:app` | Compose conversation/settings UI and lifecycle wiring |
| `:core:kernel` | Pure Kotlin/JVM agent kernel and provider-neutral contracts |
| `:data:persistence` | Room sessions, ordered messages, journal, and run ledger |
| `:data:model` | Android secure provider settings and OpenAI-compatible OkHttp transport |

The build uses Kotlin DSL and the version catalog. The new model module uses OkHttp 5.3.0 and kotlinx.serialization JSON 1.9.0. Network tests use MockWebServer with a generated local TLS certificate; they do not call a public endpoint.

Room is schema version 2. Both schemas are exported under `data/persistence/schemas`, and the production builder registers explicit `MIGRATION_1_2`. No destructive migration fallback is enabled.

## Verification commands

Final JVM, lint, and assembly verification used the local toolchain/cache in offline mode:

```powershell
$env:GRADLE_USER_HOME='E:\Projects\.tooling\gradle-user-home'
$env:TEMP='E:\Projects\.tooling\temp'
$env:TMP='E:\Projects\.tooling\temp'
& 'E:\Projects\.tooling\gradle-8.13\bin\gradle.bat' --no-daemon --offline '-Pkotlin.compiler.execution.strategy=in-process' :core:kernel:test :data:model:testDebugUnitTest :app:testDebugUnitTest lintDebug assembleDebug
```

Connected tests are run on `Kinetic_API_36` only after the production-package safety guard:

```powershell
.\gradlew.bat --no-daemon verifyDeviceTestSafety :data:persistence:connectedDebugAndroidTest :data:model:connectedDebugAndroidTest :data:android-capabilities:connectedDebugAndroidTest
```

Do not run app-target instrumentation from `:app`. AGP 8.13.2 delegates connected tests to Unified Test Platform's `AndroidTestApkInstallerPlugin`, which sets `uninstall_after_test: true` for the target APK as well as the test APK. When `:app:connectedDebugAndroidTest` targeted `dev.kinetic.app`, its teardown removed the owner's installed application and all application-private data. Kinetic therefore keeps instrumentation in library-owned disposable packages; `verifyDeviceTestSafety` rejects `app/src/androidTest` sources and destructive package commands targeting `dev.kinetic.app`, and every app-target connected/device Android test task fails before UTP execution.

The first combined connected run completed the two persistence tests but the model instrumentation process failed to attach and ran zero tests after a transient emulator shell timeout. Re-running `:data:model:connectedDebugAndroidTest` alone completed both model instrumentation tests successfully. This was an emulator/host test-runner failure, not a test assertion error.

## Test results

| Suite | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| `:core:kernel` JVM | 28 | 0 | 0 | 0 |
| `:data:model` JVM | 9 | 0 | 0 | 0 |
| `:app` JVM | 5 | 0 | 0 | 0 |
| `:data:persistence` Android | 2 | 0 | 0 | 0 |
| `:data:model` Android | 2 | 0 | 0 | 0 |
| **Total** | **46** | **0** | **0** | **0** |

Coverage includes all previous Phase 1/1.1 tests plus provider configuration, missing-key failure, non-stream mapping, one-byte-fragmented UTF-8 SSE, provider completion/malformed closure, typed HTTP/network/timeout failures, bounded ordered context, Fake/Cloud switching, turn-scoped streaming, cancellation, tool-payload fail-closed behavior, recovery without resend, session isolation, Room reopen durability, message sequence ordering, Keystore save/replace/clear, and key exclusion from preferences/Room/database bytes.

## Lint and build

- Lint: 0 errors, 13 warnings across all modules (app 11, model 1, persistence 1).
- The warnings are non-blocking version/target drift, the existing Room kapt-to-KSP suggestion, and one deliberate synchronous preferences-removal warning used when invalid encrypted secret data is reconciled.
- `assembleDebug`: `BUILD SUCCESSFUL`.
- APK: `E:\Projects\Kinetic\app\build\outputs\apk\debug\app-debug.apk`
- APK SHA-256: `7CC76BEF8B0D1A368DAF9B37E8B07F23B9C42C2A4916196D91AC31FEA9270AAE`

## Emulator verification

The emulator initially contained `0.1.0-phase1`. Installing the Phase 2A APK with `adb install -r` retained application data, and the first cold launch successfully opened the existing Room database through schema migration 1→2. `MainActivity` became top-resumed and the process remained alive.

The provider panel opened and exposed display name, HTTPS base URL, model ID, masked API-key input/status, Save, Clear API key, and Fake/Cloud selection. Fake mode accepted `hello` and displayed `Fake response: hello`. After force-stop and cold relaunch, both messages were restored in order. This verifies the production app's provider UI, Fake-provider regression, database migration, active-session restoration, and durable history on the device.

The final log scan found no Kinetic fatal exception, ANR, Room/SQLite migration error, API-key marker, bearer header, or authorization header. No real provider request was made.

## Permission and network audit

The source manifest declares only:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

The installed package reports `android.permission.INTERNET` plus AndroidX's generated app-signature `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. It has no dangerous permission, Accessibility service, camera, microphone, location, contacts, SMS, call-log, broad package query, external-storage management, MediaProjection, notification listener, battery-optimization exemption, or shell capability. `usesCleartextTraffic` is `false`, no TLS verification bypass exists, and application backup is disabled with an explicit provider-settings exclusion.

## Remaining gate

Automated and Fake-provider validation is complete. A user must supply their own OpenAI-compatible endpoint, model ID, and API key and report the single live streaming/context/cancellation acceptance result before Phase 2A is considered fully accepted. Phase 2B is not started.
