# Phase 5B Candidate 2 — Android-managed Nano / AICore preflight

Access and device-check date: **2026-09-13 (PKT)**.

**D. DEVICE UNSUPPORTED / UNAVAILABLE**

**Candidate 2 — EVALUATED — UNSUPPORTED / UNAVAILABLE ON CURRENT PHYSICAL DEVICE.**
**CANDIDATE 2 — AICORE UNAVAILABLE.**

The official Prompt API device list excludes this phone, and ordinary package
inspection found no AICore installation. The owner's Stage 4 stop condition was
applied before SDK integration. **checkStatus(): NOT_RUN**, not a fabricated
`FeatureStatus.UNAVAILABLE` return. This is device-availability evidence, not a
Kinetic failure, inference failure or model-quality result.

## Starting authority and architecture

Read roadmap first: Phase 5 IN PROGRESS; 5A COMPLETE/ACCEPTED; 5B IN PROGRESS;
Candidate 1 BLOCKED — D. INCONCLUSIVE BINARY-SPECIFIC PROVENANCE; Candidate 2 not
previously evaluated; Phase 6 NOT STARTED. Astra/Stellar engineering passed,
owner acceptance pending. Candidate 1 evidence, OpenRouter textual-tool issue and
historical emulator failures are preserved.

Read the Phase5 on-device architecture, Candidate1 backend/preflight report,
binary-provenance report and physical benchmark report. Inspected kernel
`LocalInference.kt`, provider-bounded `ContextPlanning.kt`, runtime wiring,
`:data:model` capability probe and `ConfiguredModelProvider`, and app
`KernelViewModel`/manifest. The existing local port is pure Kotlin, configured
selection returns simulation before cloud credential access, and app wiring keeps
registry/policy/approval/durable ledger/Android coordinator separate from model
generation. No parallel runtime, new module, probe, routing, UI or source change
was necessary or made. Existing simulation is not relabeled as real Nano.

## Official SDK and supported-device evidence

[Current setup guide](https://developers.google.com/ml-kit/genai/prompt/android/get-started)
documents `com.google.mlkit:genai-prompt:1.0.0-beta4`, **minimum API26**.
The [exact Google Maven POM](https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-prompt/1.0.0-beta4/genai-prompt-1.0.0-beta4.pom)
was fetched as text and confirms that version and AAR packaging. No SDK binary
was downloaded or added to Gradle. No floating version or unofficial binding.

[Official Prompt API support list](https://developers.google.com/ml-kit/genai#prompt_api_device_support)
was checked separately from the feature-specific API list. Its Samsung entries are:

| Model generation | Listed Samsung families |
|---|---|
| nano-v2 | Galaxy Z Fold7, Galaxy Z TriFold |
| nano-v3 | Galaxy S26, S26+, S26 Ultra |
| nano-v4 | Galaxy Z Flip8, Z Fold8, Z Fold8 Ultra |

**OFFICIAL DEVICE LIST: NOT SUPPORTED / NOT LISTED** for SM-A065F / Galaxy A06.
[Samsung's exact-model update page](https://doc.samsungmobile.com/SM-A065F/031104241028/eng.html)
identifies SM-A065F as Galaxy A06. No support inferred from Android16, API36,
ARM64, RAM, storage, other Samsung products or generic Gemini marketing.
List access is a dated snapshot, not a permanent prediction of vendor support.

## Fresh physical-device and AICore evidence

ADB enumeration found the authorized physical Samsung and an emulator. All
subsequent ADB calls explicitly selected only the authorized physical serial;
no emulator command or fallback. Serial omitted from this permanent report.

| Read-only query | Observed result |
|---|---|
| ro.kernel.qemu | 0 |
| ro.product.model | SM-A065F |
| ro.build.version.release | 16 |
| ro.build.version.sdk | 36 |
| ro.product.cpu.abilist | arm64-v8a,armeabi-v7a,armeabi |
| ro.product.cpu.abilist64 | arm64-v8a |
| pm list packages --user 0 com.google.android.aicore | No matching package line |
| pm path com.google.android.aicore | No APK path |
| dumpsys package com.google.android.aicore | Unable to find package: com.google.android.aicore |

[Google's Android Enterprise guidance](https://support.google.com/work/android/answer/16268713)
identifies `com.google.android.aicore` as the AICore package. These are ordinary
package-manager checks, not hidden service calls or an SDK status response.
No unrelated package inventory, account data, private files or user content was
collected. No AICore/Play Services modification, reboot, clear-data, permission
change, replacement, preview enrollment, bootloader change or compatibility bypass.
Device actions stopped after the package absence result.

## SDK status semantics — documented, not executed

The [GenerativeModel reference](https://developers.google.com/android/reference/kotlin/com/google/mlkit/genai/prompt/GenerativeModel)
exposes a suspend `checkStatus()` returning a FeatureStatus integer and a separate
`download()` Flow. The setup guide distinguishes:

| Official status | Meaning to retain in a future typed adapter |
|---|---|
| AVAILABLE | Runtime/model currently usable; not a Kinetic integration pass |
| DOWNLOADABLE | Supported, provisioning required; stop for authorization |
| DOWNLOADING | Provisioning underway; initializing, not available |
| UNAVAILABLE | Unsupported or configuration/runtime not currently available |

This phone's actual SDK status, elapsed check time and base-model name are
**NOT_RUN / NOT_OBTAINED**. No client was created. No status-mapping code or tests
were needed because the earlier stop condition applied. No inference, warmup,
download or provisioning was requested. Do not infer that an SDK exception such
as FEATURE_NOT_FOUND was observed here.

## Research-only capability and lifecycle notes

The setup guide documents `generateContentStream()` for incremental output and
`generateContent()` for complete responses. The
[Java-facing GenerativeModel reference](https://developers.google.com/android/reference/com/google/mlkit/genai/prompt/GenerativeModel)
explicitly documents coroutine cancellation for generation and `close()` resource
release. Native cancellation latency, cleanup completion and process-death
behavior remain unmeasured; Flow cancellation is not a tested teardown guarantee.

[Model selection](https://developers.google.com/ml-kit/genai/prompt/android/select-model)
distinguishes default STABLE from PREVIEW. No preview configuration or enrollment
was used. The setup guide says unlocked bootloaders are unsupported; this task did
not inspect or alter bootloader state, since package absence already stopped it.

[GenAI overview](https://developers.google.com/ml-kit/genai) restricts inference
to the top foreground app; a foreground service does not qualify. It documents
short-term BUSY and longer-duration PER_APP_BATTERY_USE_QUOTA_EXCEEDED failures,
without numeric universal quotas. Future Kinetic handling must remain typed and
bounded, with no automatic cloud fallback or background retry loop.

### Structured output versus tool authority

[Structured output guide](https://developers.google.com/ml-kit/genai/prompt/android/structured-output)
documents an alpha, Kotlin-only typed-output surface using `@Generable`, `@Guide`,
KSP2.3.6+ and `genai-schema-compiler:1.0.0-alpha1`. Availability must be checked with
`isStructuredOutputFeatureAvailable()`; minimum API26 alone is insufficient.
Supported structures include strings/enums, numbers/ranges, booleans, lists with
size bounds and nested annotated objects; circular structures are unsupported.
Annotated types may require release keep rules. This is not arbitrary JSON Schema
parity or a measured reliability guarantee. No specific Nano version is promised
by this guide in place of the feature check. No alpha feature integrated.

The inspected public Prompt package/reference and guides did not expose an
identifiable function declaration / function-call result protocol with a supported
proposal-only control. A guessed function-calling guide URL was not retrievable;
absence of that URL alone is not evidence. Cloud Gemini function calling and model
marketing were not substituted for the Prompt SDK contract.

**structuredToolProposals = NOT_SUPPORTED for this evaluated candidate surface.**
This is the conservative Kinetic capability classification based on current public
API evidence, not a claim that a future SDK cannot add it. Typed object output is
data, not authority, and is not automatically advertised as structured tool calling.
An application-specific proposal schema would require separate design/validation;
no textual pseudo-tool parsing or such integration is authorized here.

There is no new automatic-execution risk introduced: no SDK, callback or model
was connected. In any future adapter, ToolRegistry, CapabilityPolicy, ApprovalGate,
EffectLedger and Android Intent authority must remain with Kinetic. A typed or
textual response cannot bypass those boundaries.

### Token and system-instruction boundaries

The setup guide retains **input under4,000 tokens** and discourages output above4K;
these are not a cloud Gemini context window. The Kotlin reference exposes
`countTokens()` for input and `getTokenLimit()` for combined input/output. Future
admission must reserve output and satisfy both the documented input restriction
and the returned total limit. No numeric device total was obtained; no ContextPlanner
integration or tokenizer execution occurred.

[System-instruction guide](https://developers.google.com/ml-kit/genai/prompt/android/system-instructions)
documents `SystemInstruction` for NanoV3+, suggests short instructions and inclusion
in token counts; `isSystemPromptAvailable()` exists in the reference. It advises
against combining system instructions with prefix caching. Model instructions
are behavior guidance, not an execution security boundary.

## SDK terms, managed model and privacy

The exact POM identifies **ML Kit Terms of Service**, not Apache-2.0, as the SDK
license entry (`distribution=repo`). Documentation/sample Apache notices are not
a license for the complete SDK or managed model. The POM declares ordinary app
dependencies including ML Kit common18.11.0/genai-common beta4/genai-schema alpha1,
Play Services, datatransport, Firebase encoders, Kotlin and coroutines. No binaries
or transitive graph were resolved, so no complete redistribution-notice audit or
merged permission inventory is claimed.

[ML Kit terms/privacy](https://developers.google.com/ml-kit/terms) incorporate
Google API terms and treat models as related software. Input and generated output
are processed locally, but updates/configuration and performance/utilization
metrics can involve Google servers. User disclosures are required; “on-device”
must not be described as zero telemetry. No Kinetic prompts or credentials were sent.

[GenAI additional terms](https://developers.google.com/ml-kit/genai-terms) require
adult use and exclude clients directed at or likely accessed by under18s. Ordinary
production use is permitted subject to restrictions; Preview/Experimental Access
is not production authorization. Restrictions include competing services/products,
model extraction/replication, bypassing technical/geographic or safety restrictions,
and clinical/medical uses. Developers retain safety and generated-content
responsibility. These obligations need owner review against actual audience and
product scope before distribution; this preflight does not establish compliance.

[Google APIs terms](https://developers.google.com/terms) address SDK/API use,
privacy policies, branding/notices and restrictions on sublicensing an equivalent
API service. Retain applicable terms and notices; do not redistribute AICore or
extract weights. This candidate would distribute app SDK dependencies while the
device manages AICore/model provisioning. No separate downloadable Nano model
license artifact is invented, and no claim of unrestricted open-source licensing
or legal clearance is made. No concrete SDK/terms obstacle prevented this bounded
read-only preflight; the observed stopping reason is device availability.

## Preservation and verification accounting

No Kotlin/Java/native source, manifest, Gradle/catalog, provider settings, UI,
application ID, signer or Room change. Source schema remainsv6; manifest still
declares INTERNET. No SDK integration means no new permission delta; no new
merged-APK permission audit was necessary. Existing APK remains13,031,195bytes
with September9 timestamp. No install, uninstall, clear-data, credential read,
cloud call or Room/app-data access. Post-update preservation is **NOT_APPLICABLE**,
not a fresh decryption/history test. No app launched or exercised for this gate.

| Gate | This task | Historical accepted baseline, not rerun |
|---|---|---|
| Kernel JVM | NOT_RUN | 116 passed |
| Model JVM | NOT_RUN | 49 passed |
| Capability JVM | NOT_RUN | 12 passed |
| App JVM | NOT_RUN | 16 passed |
| Persistence Android | NOT_RUN | 20 passed |
| Model/security Android | NOT_RUN | 14 passed |
| Capability Android | NOT_RUN | 17 passed |
| New Candidate2 tests | NOT_APPLICABLE: no code | None |
| Total | No fresh automated execution | 244 passed;0 failed;0 errors;0 skipped |
| verifyDeviceTestSafety | NOT_RUN: no instrumentation | Passed |
| Lint | NOT_RUN | 0 errors/5 warnings |
| assembleDebug | NOT_RUN | Passed |
| APK hash/signer/install | NOT_RUN: no update | Existing accepted signer retained |
| Kinetic ANR/crash/log audit | NOT_RUN: no installation/workload | No new clean-log claim |

No app-target instrumentation, model download, inference, protected action or
benchmark was performed. No Candidate3, production default, hybrid router or Phase6.
High confidence in current official-list/package absence findings; no SDK return
value, future support prediction or performance conclusion inferred.

## Final disposition

**D. DEVICE UNSUPPORTED / UNAVAILABLE.** Candidate2 EVALUATED — UNSUPPORTED /
UNAVAILABLE ON CURRENT PHYSICAL DEVICE. Candidate1 remains BLOCKED, not rejected,
with its binary-provenance classification unchanged. Phase5B IN PROGRESS, not
complete; Phase5A ACCEPTED; Phase6 NOT STARTED. Separate gates/issues unchanged.

Exactly one next task, **not begun**:
**OWNER DECISION WHETHER TO PROVIDE A SUPPORTED AICORE DEVICE OR AUTHORIZE
CANDIDATE 3 — SMALL GGUF / LLAMA.CPP EVALUATION.**
