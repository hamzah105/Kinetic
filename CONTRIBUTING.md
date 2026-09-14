# Contributing to Kinetic

Thanks for contributing. Kinetic is an experimental native Android agent framework, so changes that affect execution authority, credentials, permissions, native code, or provider routing need especially careful review.

## Development setup

Current toolchain:

- JDK 21 for Gradle
- Java 17 bytecode
- Android SDK 36
- NDK `28.2.13676358`
- CMake `3.30.2`
- Ninja

The build uses the pinned llama.cpp source archive under `third_party/`. Model weights are not part of the repository.

## Before opening a pull request

Run the host verification gate:

```bash
./gradlew --no-daemon --max-workers=2 --no-parallel \
  -Pkotlin.compiler.execution.strategy=in-process \
  :core:kernel:test \
  :data:model:testDebugUnitTest \
  :data:android-capabilities:testDebugUnitTest \
  :app:testDebugUnitTest \
  verifyDeviceTestSafety \
  lintDebug \
  assembleDebug
```

On Windows use `./gradlew.bat`.

Do not add production-app instrumentation under `app/src/androidTest`. Kinetic's safety guard exists because Android Gradle/UTP teardown can uninstall the target application and destroy valuable app data.

## Architecture rules

Please preserve these boundaries unless a proposal explicitly changes them and receives security review:

1. `:core:kernel` stays pure Kotlin/JVM.
2. Model output is untrusted data, not execution authority.
3. Memory and summaries cannot grant approval, modify policy, or create executable tool calls.
4. Risk-bearing tool execution flows through registry -> validation -> policy -> approval where required -> durable effect -> execution -> durable result.
5. Provider failures do not silently switch to another provider.
6. Native libraries ship with the signed app. Do not introduce runtime-downloaded DEX/JAR/.so/plugin execution.
7. GGUF/model artifacts are data and must be integrity-checked before use.
8. Do not broaden Android permissions or capability surfaces without an explicit requirement and security review.
9. Do not introduce AccessibilityService, broad storage, arbitrary shell execution, or hidden background autonomy as a convenience shortcut.

## Pull requests

Keep pull requests focused. Include:

- what changed and why;
- affected modules and security boundaries;
- test coverage and exact host verification results;
- new permissions, dependencies, native libraries, network endpoints, or persistence changes;
- limitations or device behavior that remains unverified.

Do not describe host fixtures as real-device acceptance.

## Dependencies and third-party code

Prefer small, pinned dependencies. Review the license and provenance of new dependencies before adding them. Do not copy third-party code without retaining required notices and attribution.

## Secrets and private data

Never commit:

- API keys, OAuth tokens, Authorization headers, or passwords;
- `local.properties`, `.env` files, keystores, private certificates, or signing secrets;
- model weights/GGUF files;
- Room databases or app-private state;
- internal `roadmap.md`, `PROMPT.txt`, or private engineering `docs/`.

Use obviously fake values in tests.

## Security reports

Do not put vulnerability details in ordinary issues. Follow [SECURITY.md](SECURITY.md).

## License

By contributing, you agree that your contribution is licensed under the repository's [MIT License](LICENSE).
