# Phase 5B Candidate 1 — LiteRT-LM 0.17.0 binary license provenance

Audit window: September 12–13, 2026 (PKT). Supply-chain analysis only.

**D. INCONCLUSIVE — BINARY-SPECIFIC PROVENANCE CANNOT BE ESTABLISHED**

Candidate 1 remains **BLOCKED**. High confidence that the evidence collected does
not satisfy the required component-to-binary attribution gate; no confidence claim
about whether unidentified GPL-covered implementation is actually present. This
is an engineering evidence classification, not legal advice or a finding of
incompatibility. Neither a GPL notice nor an Apache project license settles linkage.

## 1. Starting state and preservation

Roadmap read first: Phase 4 COMPLETE; 4C ACCEPTED; Phase 5 IN PROGRESS; Phase 5A
COMPLETE/ACCEPTED; Phase 5B IN PROGRESS, Candidate 1 blocked at preflight for runtime
third-party provenance. Phase 6 NOT STARTED. Separate Astra/Stellar engineering
passed/owner pending, OpenRouter inert textual-tool issue and historical emulator
failures remain unchanged.

No production code, Gradle, catalog, manifest, Room, provider or UI was changed.
Production build-file search found no LiteRT-LM integration. Room remains version
6. The existing APK was not rebuilt (13,031,195 bytes, September 9 timestamp).
No device command, app-storage inspection, credential access, build, install,
native execution/loading, model download or inference occurred in this audit.
The prior preflight records no source change, model provisioning or APK update;
absence of models in private phone storage is retained from that record, NOT a
new independent phone inspection. There is no Git baseline here for proving every
historical byte unchanged. Prior 244 passed/0 failed/0 errors/0 skipped, lint
0 errors/5 warnings and successful build are historical, not rerun.

Only documentation and temporary analysis material were written. The cached AAR
was not repackaged or altered. No notices were removed or suppressed.

## 2. Exact artifact and complete archive inventory

Coordinate: `com.google.ai.edge.litertlm:litertlm-android:0.17.0`.
[Official Google Maven AAR](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.17.0/litertlm-android-0.17.0.aar).

- Length: **20,492,644 bytes**.
- SHA-256: `28AA6BC43EFCEE35B31795F9E5CA633C3DEC06D9F3FB85ECB6A753FA360E2134`.
- Reused and rehashed the already-inspected official copy at
  `E:\Projects\.tooling\temp\litertlm-android-0.17.0.aar`.

All eight ZIP entries, including the empty directory:

| Path | Uncompressed bytes | SHA-256 |
|---|---:|---|
| AndroidManifest.xml | 243 | `5C007DE9F6123FD372CED98E7C9C6FEA1B1510F2DD4379B91AB078ECD5311625` |
| classes.jar | 160701 | `47C0DE5858BE5E068F4784BEE22C523E8ACBD8AA00033154F78DC09138453AC5` |
| res/ | 0 | `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855` |
| R.txt | 10701 | `C4881650E648C6B17EAB8606D8399BA21A62D96D6AB9C1D53BDC5AEAF9C3D1DC` |
| jni/arm64-v8a/liblitertlm_jni.so | 21802960 | `3F50949F9E08A74F5AE8C32A9217C744CA2D9F32BB10C4D58B60F634F0A62FD2` |
| jni/x86_64/liblitertlm_jni.so | 25968008 | `A75A8C1210F64F8CE5DCC464AA9DF53C0F9C28607769A4CF2C681617A0764E5C` |
| LICENSE | 11357 | `C71D239DF91726FC519C6EB72D318EC65820627232B2F796219E87DCF35D0AB4` |
| THIRD_PARTY_NOTICE.txt | 2146747 | `67D807A83A6E4F9457365CA61FF3FA1DB95B135A17FEEEFADEFF8E9F02123243` |

No `libs/`, AAR-level `META-INF/`, consumer rules, prefab metadata, SBOM or
populated resource files. Manifest declares minSDK 24, namespace
`com.google.ai.edge.litertlm`, no permission or feature declarations.

## 3. Exact disputed notice

`THIRD_PARTY_NOTICE.txt`, line 6884, heading **Google Runtime Environment:**;
18,042 characters through the boundary immediately before **Gson:**.

- Product label: Google Runtime Environment. Exact implementation/component ID:
  **not supplied**. Do not substitute OpenJDK, GRTE or a header package by guess.
- License text: GNU General Public License, Version 2, June 1991.
- Component-specific `GPL-2.0-only` versus `GPL-2.0-or-later` grant:
  **NOT ESTABLISHED**. The standard license's illustrative application boilerplate
  is not a component-specific grant of the “or later” option.
- Explicit Classpath, linking or GCC Runtime Library Exception in this section:
  **NONE FOUND**. It is not an identified GPLv3/GCC-exception or LGPL grant.
- The Free Software Foundation 1989/1991 copyright identifies the license text;
  it does not establish the software component's copyright holder.
- Component copyright holder, version and source/project URL: **not stated**.
- Exceptions in unrelated notice sections cannot be transferred to this entry.

## 4. Published Maven dependency declarations

[Exact POM](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.17.0/litertlm-android-0.17.0.pom)
declares Apache-2.0 and these direct compile dependencies (therefore relevant to
runtime consumption as well, not `runtime`-scope declarations):

| Group | Artifact | Version | Scope |
|---|---|---|---|
| com.google.code.gson | gson | 2.14.0 | compile |
| org.jetbrains.kotlin | kotlin-reflect | 2.4.0 | compile |
| org.jetbrains.kotlinx | kotlinx-coroutines-android | 1.11.0 | compile |

Official transitive POMs were inspected on
[Maven Central](https://repo.maven.apache.org/maven2/), without installing dependencies:

| Parent | Dependency declaration | Scope |
|---|---|---|
| gson:2.14.0 | com.google.errorprone:error_prone_annotations:2.48.0 | compile (default) |
| kotlin-reflect:2.4.0 | org.jetbrains.kotlin:kotlin-stdlib:2.4.0 | compile |
| kotlinx-coroutines-android:1.11.0 | org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0 | compile |
| kotlinx-coroutines-android:1.11.0 | org.jetbrains.kotlin:kotlin-stdlib:2.2.20 | compile |
| kotlinx-coroutines-core-jvm:1.11.0 | org.jetbrains:annotations:23.0.0 | compile |
| kotlinx-coroutines-core-jvm:1.11.0 | org.jetbrains.kotlin:kotlin-stdlib:2.2.20 | compile |
| kotlin-stdlib:2.4.0 and :2.2.20 | org.jetbrains:annotations:13.0 | compile |

The inspected annotations 13.0/23.0.0 POMs add no dependencies; Error Prone
annotations adds only a test dependency. Gson's JUnit/Truth/Guava test dependencies
are excluded from this consumer-runtime inventory. No declared GRE coordinate
was found. These are metadata declarations, **not a Gradle-resolved application
graph**; stdlib/annotations version conflicts were not resolved or integrated.
No transitive JAR byte inventory or full transitive-license clearance is claimed.

The exact `litertlm-android-0.17.0.module` Google Maven URL returned 404.
[Maven metadata](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml)
reported latest/release 0.17.0, lastUpdated `20260904223025` when checked.

## 5. Java/Kotlin and native byte inventory

`classes.jar` has 86 entries including metadata/directories. All class paths are
under `com/google/ai/edge/litertlm/`. Major types include Engine, Conversation,
Session, Backend/configurations, NativeLibraryLoader, LiteRtLmJni, ToolManager,
ReflectionTool, OpenApiTool, InternalJsonTool, messages, benchmark and embedding.
It contains a manifest and the Kotlin module metadata path
`META-INF/third_party.odml.litert_lm.kotlin.java.com.google.ai.edge.litertlm_litertlm-android.kotlin_module`.
This is a build-path clue, not a release attestation.

Read-only `jdeps --ignore-missing-deps -verbose:package` identifies standard Java,
Gson, Kotlin/reflection and kotlinx.coroutines references. Gson/Kotlin or a Java
runtime are not embedded under their usual namespaces. No obvious GRE namespace
was identified. Unresolved classpath entries in this isolated inspection are not
an application dependency failure. Package naming cannot exclude shaded/copied code.

| ABI | ELF / SONAME | Build ID |
|---|---|---|
| arm64-v8a | ELF64 little-endian AArch64 DYN / liblitertlm_jni.so | `dde9e6a84ccdcade1e718c966a2e9775` |
| x86_64 | ELF64 / liblitertlm_jni.so | `b1254eddb53dacfa54296d6285ea6bd1` |

Both libraries declare the same nine `DT_NEEDED` entries:
`libandroid.so`, `libz.so`, `libGLESv2.so`, `libEGL.so`, `libGLESv3.so`,
`liblog.so`, `libdl.so`, `libm.so`, `libc.so`.

These are Android system library names, not a named GRE/JVM dynamic dependency.
ARM64 exports LiteRtLmJni JNI entry points. Its Android note records API 26,
NDK r29/build 14206865, distinct from manifest minSDK24. Kinetic's minSDK26 is
not below that native build level; this is not a compatibility or runtime pass.
Prior ELF checks observed 16KB PT_LOAD alignment for both ABIs.

ARM64 has dynamic symbols but no full `.symtab`/debug inventory observed.
Bounded strings/symbol searches for Google Runtime Environment, OpenJDK, Classpath,
libjvm/libjava/libjli and JDK paths yielded no attribution. **Weak negative evidence
only**: stripped/static/inlined/header code cannot be excluded. `DT_NEEDED` does
not enumerate runtime `dlopen` targets or statically incorporated dependencies.

## 6. Exact upstream source mapping and its limits

Official tag [v0.17.0](https://github.com/google-ai-edge/LiteRT-LM/tree/v0.17.0),
commit **`e9fd8c53ff968071774206163027dd84bedfe925`**.

- [Root LICENSE](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.0/LICENSE):
  Apache-2.0, not a per-byte third-party manifest.
- [Kotlin BUILD](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.0/kotlin/java/com/google/ai/edge/litertlm/BUILD):
  `kt_android_library(name="litertlm-android")`, Kotlin source glob, Kotlin
  reflection/Gson/coroutines dependencies. The Android block does not itself
  provide the complete published JNI/AAR packaging recipe.
- [JNI BUILD](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.0/kotlin/java/com/google/ai/edge/litertlm/jni/BUILD):
  shared `cc_binary(name="litertlm_jni")` from `litertlm.cc`; dependencies include
  Abseil, JSON, LiteRT utilities, C engine/capabilities, conversation/engine/executor,
  protobuf/schema, embedding and image preprocessing. Android selects an empty
  extra JNI-toolchain dependency; other platforms select `@rules_java//toolchains:jni`.
  This supports a platform-specific build distinction, **not proof GRE is build-only**.
- [WORKSPACE](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.0/WORKSPACE):
  LiteRT pin `9fe5be45564c868408e6514c8aabb83e211a0911`, TensorFlow pin
  `d9a8da74b4c3de28a39ab34ad007838d6bc30c67`; public Maven declarations use Gson
  2.13.2/coroutines1.9.0, unlike published POM2.14.0/1.11.0. Public source
  declarations alone therefore do not reproduce the exact publication configuration.
- [Android CI](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.0/.github/workflows/ml-ci-build-android.yml)
  uses host OpenJDK17 and NDKr28b for Android x86_64 builds/CLI artifacts, not an
  identified Maven0.17.0 publication attestation; the inspected native note saysr29.
- [CMake orchestrator](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.0/CMakeLists.txt),
  `cmake/packages/litert_lm/CMakeLists.txt`, `cmake/packages/packages.cmake`,
  `cmake/modules/collect_dependencies.cmake` and Android toolchain script distinguish
  host protoc/flatc prebuild from target native/Rust builds, with LiteRT/TFLite,
  tokenizers, SentencePiece, RE2, FlatBuffers, protobuf and Abseil declarations.
  They do not establish which GRE source, if any, contributed to these exact AAR bytes.

The named Android library/JNI targets are identifiable; the exact Maven publication
recipe, toolchain/source closure, static link map and notice-generation inputs
bound to this AAR hash **were not established**. No rebuild was attempted.

## 7. SBOM and upstream issue evidence

The Android AAR itself provides a binary-associated `THIRD_PARTY_NOTICE.txt`, but
not a component/version/source/byte mapping. No Android0.17.0 hash-bound SPDX,
CycloneDX or equivalent SBOM was found in the AAR, checked Maven metadata, bounded
official tag/tree searches or release asset listing. The checked release assets
were Apple XCFramework/macOS artifacts. This is a bounded availability finding,
not proof that no internal or unindexed SBOM exists.

| Official discussion | Observed status / FACT | CLAIM or limitation | Android0.17.0 relevance |
|---|---|---|---|
| [#3194](https://github.com/google-ai-edge/LiteRT-LM/issues/3194) | Open; collaborator replies acknowledge request and later say Swift SBOM/notices were supplied for0.15/0.16 | Requester disputes completeness and GRE attribution; unrelated legal allegations are not adopted | Apple releases, not this AAR |
| [#3556](https://github.com/google-ai-edge/LiteRT-LM/issues/3556) | Open; collaborator says investigation will follow | Requester reports GRE entry lacks version/source in iOS0.16 SBOM | No Android mapping or resolution |
| [PR#3320](https://github.com/google-ai-edge/LiteRT-LM/pull/3320) | Merged; merge commit `56a9ac80e3c5b0cd97d7092b9ea0ab59f7601f5b`; fetched diff changes whitespace in swift/BUILD | Title mentions SBOM but the inspected diff does not establish Android SBOM generation | No Android0.17.0 attribution |
| [#3236](https://github.com/google-ai-edge/LiteRT-LM/issues/3236) | Fresh API returns410/deleted; comments404 | Cached search excerpt is not a verifiable live maintainer response | Not used as substantive proof |

Specific collaborator replies:
[#3194 acknowledgement](https://github.com/google-ai-edge/LiteRT-LM/issues/3194#issuecomment-5255015677),
[#3194 Swift packaging response](https://github.com/google-ai-edge/LiteRT-LM/issues/3194#issuecomment-5375720693),
[#3556 investigation response](https://github.com/google-ai-edge/LiteRT-LM/issues/3556#issuecomment-5631113630).
Requester mentions of `grte_linux_header_extensions` in other-platform discussion
are not a confirmed identity for this Android notice. No issue was posted or
upstream contact initiated.

## 8. Gemma provenance is separate

The [official Gemma4 E2B card](https://huggingface.co/google/gemma-4-E2B-it)
declares Apache-2.0 and links the
[Gemma4 license](https://ai.google.dev/gemma/docs/gemma_4_license).
The [pinned LiteRT community card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/README.md)
also declares Apache-2.0 and identifies that base model. Retained candidate:
`gemma-4-E2B-it.litertlm`, revision `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1`,
published2,588,147,712bytes, published SHA-256
`181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
[Exact prospective artifact](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm).
Only card/metadata text was fetched. No weights downloaded or hash-verified; no
model license conclusion cures the runtime blocker. Intended use remains text-only,
not a claim the CPU-capable bundle contains only text weights.

## 9. Required classification matrix

Confidence describes the stated observation or limitation, not guessed linkage.

| Question | Evidence | Source | Confidence | Result |
|---|---|---|---|---|
| 1. GPL-related notice present? | Full GPLv2 text in GRE section | Exact AAR notice, §3 | High | YES |
| 2. Exact component? | Product label only; no implementation ID | Notice, §3 | High | Identity incomplete |
| 3. Exact license variant? | GPLv2 text, no component-specific only/or-later grant | Notice, §3 | High | GPLv2 text; variant unresolved |
| 4. Exception stated? | No section-specific exception found | Notice, §3 | High | NONE FOUND |
| 5. Corresponding classes.jar code? | LiteRT-LM namespace; no obvious GRE classes | JAR/jdeps, §5 | Limited negative | Not established |
| 6. Corresponding native code? | Two JNI libraries; no attributable GRE symbols | ELF/build inspection, §§5–6 | Limited negative | Not established |
| 7. Maven runtime dependency? | No GRE coordinate in inspected POM closure | Official POMs, §4 | High for declarations | No named dependency; implementation attribution unresolved |
| 8. Build-time only? | Host/target distinction exists, no GRE mapping | Pinned build rules, §6 | High for limitation | NOT PROVEN |
| 9. Statically linked? | No static link map/full symbols/SBOM | ELF/source, §§5–7 | High for limitation | UNKNOWN |
| 10. Dynamically linked? | Nine system DT_NEEDED names, no GRE/JVM | Both ELF files, §5 | High for names | No named GRE dependency; indirect/loading attribution unknown |
| 11. Binary-specific SBOM? | Not in AAR or checked publication sources | §7 | Bounded search | NOT FOUND |
| 12. Redistribution obligations identifiable? | Root Apache and bundled notice available; GRE identity/grant unresolved | §§3,7 | High for limitation | Complete obligations NOT ESTABLISHED |
| 13. Shipped third-party set reproducible? | Archive bytes known, source/publication mapping incomplete | §§2,4–7 | High | NO, not to required standard |

## 10. Disposition, obligations and reproducibility

**D. INCONCLUSIVE — BINARY-SPECIFIC PROVENANCE CANNOT BE ESTABLISHED.**
Candidate1 remains BLOCKED at preflight; Phase5B remains IN PROGRESS. No runtime
integration is authorized by this result. Phase5A remains ACCEPTED; Phase6 NOT STARTED.

The Apache declaration and supplied license/notice material identify material to
retain for any later authorized distribution. They do not supply a complete GRE
redistribution basis. No complete attribution manifest or redistribution clearance
is claimed. Do not strip notices, suppress dependencies or rename binaries to
bypass this result. To resolve it would require authoritative component/source/
version mapping and shipped-versus-build-only attribution bound to this AAR/hash,
plus the applicable grant/exception and distribution obligations if shipped.

Read-only reproduction uses .NET ZIP inventory/SHA-256, JDK21 `jdeps`, and existing
NDK28.2 `llvm-readelf -h -d -n`, `llvm-readelf -S`, `llvm-nm -D --demangle`,
and `llvm-strings`. No target binary is executed. Temporary helper:
`E:\Projects\.tooling\temp\kinetic-litert-license-audit.ps1`.
Extracted copies:
`E:\Projects\.tooling\temp\litert-license-3e9f482b03c943408afb589494f7a3e4`.
Remote checks used official release/raw-source/GitHub API and Maven POM endpoints.
Negative findings are bounded by the sources and stripped metadata inspected;
there is no exhaustive reverse engineering, full transitive binary audit,
reproducible native rebuild or signed publication attestation.

Exactly one next task, **not begun**:
**OWNER REVIEW OF CANDIDATE 1 BLOCKER AND DECISION WHETHER TO SEEK UPSTREAM
CLARIFICATION OR AUTHORIZE CANDIDATE 2 EVALUATION.**
