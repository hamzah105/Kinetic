# Phase 4B device-test harness safety

## Root cause

An experimental credential probe was added under `app/src/androidTest` and executed with
`:app:connectedDebugAndroidTest`. Android Gradle Plugin 8.13.2 delegated the run to Unified Test
Platform. The generated UTP configuration passed both `app-debug.apk` and
`app-debug-androidTest.apk` to `AndroidTestApkInstallerPlugin` with `uninstall_after_test: true`.
The target application ID was `dev.kinetic.app`, so normal UTP teardown uninstalled the owner's
development package after the test. No project script or test issued an `adb uninstall`,
`pm uninstall`, or `pm clear` command.

The experiment was invalid for an installation whose application-private state needed to survive:
standard app-target instrumentation owns the installed target APK for the duration of its test run.

## Safety boundary

Kinetic retains its Android instrumentation suites in library modules, whose generated test APKs
have disposable package identities. Production-app instrumentation sources under
`app/src/androidTest` are prohibited. A future fixture that needs an application target must be a
separate test-only application module with an application ID distinct from `dev.kinetic.app`.

The root `verifyDeviceTestSafety` Gradle task enforces this boundary before connected/device Android
test tasks. It:

- self-tests its destructive-command matcher;
- rejects Kotlin or Java instrumentation sources under `app/src/androidTest`;
- scans executable build, source, and script files for uninstall or package-clear commands targeting
  `dev.kinetic.app`;
- fails every `:app` connected/device Android test task before UTP execution, even if its source set
  is currently empty;
- leaves ordinary install/update, force-stop, package inspection, JVM tests, and library
  instrumentation available.

Explicit owner-approved device maintenance is intentionally not implemented as a bypass in this
guard. Such maintenance must be a separately reviewed manual operation outside normal automated
test execution.
