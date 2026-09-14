# Phase 5B Candidate 3A — pinned llama.cpp / Qwen3 feasibility

Date: 2026-09-13. **PHYSICAL ARM64 FEASIBILITY PASSED — B. VIABLE BUT PERFORMANCE LIMITED.**
Pre-integration only; real Kinetic integration NOT STARTED.

Roadmap read first. Starting state: Phase5/5B IN PROGRESS; Phase5A ACCEPTED;
Candidate1 BLOCKED (binary-specific license provenance inconclusive), Candidate2
evaluated/unavailable on current phone; Phase6 NOT STARTED. Separate Astra/Stellar
owner gate, OpenRouter textual-tool issue and historical emulator failures unchanged.
The Phase5 architecture, prior candidate reports, Phase2B structured-tool contract
and Phase4B test-harness safety document were inspected. No Kinetic JNI, production
source/dependency/UI/Room/provider integration is authorized or implemented here.

## Exact runtime and build

Primary sources accessed September13:
[official repository](https://github.com/ggml-org/llama.cpp),
[non-prerelease v0.4.0](https://github.com/ggml-org/llama.cpp/releases/tag/v0.4.0),
[pinned Android instructions](https://github.com/ggml-org/llama.cpp/blob/v0.4.0/docs/android.md),
[build guidance](https://github.com/ggml-org/llama.cpp/blob/v0.4.0/docs/build.md).

- Latest non-prerelease reported by official release API: **v0.4.0**, published
  September4; exact tag commit **5266f24da75dc449bd56cbed7addb9c8e4a6a73e**.
- Source ZIP: official codeload URL bound to that commit;39,291,134bytes;
  SHA256 `CF07FFF2E0F5859E633A137466BDEE72B06D75C00B72EE591FF922EAD864EC68`.
- Bundled ggml reports0.23.0. No floating master, alternative runtime or wrapper.
- Existing NDK **28.2.13676358/r28c**, Clang19.0.1; existing CMake3.30.2 and Ninja1.12.1.
- Official CLI cross-build recipe uses arm64-v8a/API28 and disables host-native
  tuning, OpenMP, llamafile and OpenSSL. It does not mandate a newer NDK version;
  this NDK successfully configured and built the selected target.
- Built target **llama-completion**, the bounded completion tool. Current
  llama-cli requires server implementation targets; the latter were deliberately
  not built for this CPU-only local-file benchmark.
- Source path: `E:\Projects\.tooling\temp\kc3src\llama.cpp-5266f24da75dc449bd56cbed7addb9c8e4a6a73e`.
  Build path: `E:\Projects\.tooling\temp\kc3build`.
- PowerShell ZIP extraction failed and rolled back its own temporary output;
  existing7-Zip extracted the same verified archive successfully. No source patch.
- Archive has no Git directory, so generated version metadata says0.4.0-dev/unknown
  commit. Exact source identity comes from the pinned archive/hash, not that banner.

Exact configuration (paths abbreviated only in this documentation):

```text
cmake -S <pinned-source> -B <kc3build> -G Ninja
  -DCMAKE_MAKE_PROGRAM=D:/Espressif/tools/ninja/1.12.1/ninja.exe
  -DCMAKE_TOOLCHAIN_FILE=E:/Projects/.tooling/android-sdk/ndk/28.2.13676358/build/cmake/android.toolchain.cmake
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28
  -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
  -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF
  -DGGML_LLAMAFILE=OFF -DGGML_CPU_KLEIDIAI=OFF -DGGML_CPU_REPACK=OFF
  -DGGML_VULKAN=OFF -DGGML_OPENCL=OFF -DGGML_RPC=OFF
  -DLLAMA_OPENSSL=OFF -DLLAMA_SUBPROCESS=OFF -DLLAMA_LLGUIDANCE=OFF
  -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_APP=OFF -DLLAMA_BUILD_TESTS=OFF
  -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=ON
  -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
cmake --build <kc3build> --target llama-completion --config Release -j 2
```

No global `-march` increase. Optional KleidiAI is documented upstream with runtime
CPU dispatch, but disabled here, along with repacking to avoid an extra weight
buffer. No GPU/NPU/OpenMP/Termux/PRoot/server/UI/phone Python or Node.

Build succeeded; a subsequent incremental check reported no work to do.
Original executable128,748,432bytes, SHA256
`44A6E3701C94AB412B615BD2FCF44480ED36083F031E292ED86DD8ADE98F3261`.
NDK `llvm-strip --strip-debug` generated the9,718,832byte transfer copy, SHA256
`5252F1ED34771CFF7C5502BDAC57D94ADC3024F1DC8C5045FC0EAE36F11744D5`.
ELF64/AArch64 PIE uses `/system/bin/linker64`,16KB PT_LOAD alignment, and only
`libm.so`, `libdl.so`, `libc.so` in DT_NEEDED. C++/llama/ggml are linked statically.
This is not a Kinetic signed-APK or Android execution pass by itself.

## Runtime licensing and future obligations

[Root license](https://github.com/ggml-org/llama.cpp/blob/v0.4.0/LICENSE): MIT,
ggml authors2023–2026. Pinned common/vendor/build rules were inspected rather than
assuming every file shares that license. Relevant bundled material includes
nlohmann/json MIT; cpp-httplib MIT; rotate-bits MIT; xxHash BSD2-Clause;
SHA1/SHA256 public-domain notices; sheredom subprocess public-domain/Unlicense
(execution disabled). Miniaudio permits public-domain/MIT-0 and stb permits
public-domain/MIT; these are source-tree items, not a claim that unused audio/image
objects are linked into this selected completion executable. Jinja implementation
is in the pinned common source. No separate unresolved copyleft issue was found in
the inspected CPU/common dependency path. Optional LLGuidance/KleidiAI are off.

Preserve applicable copyright, license and BSD disclaimers and any model NOTICE
material in future distribution. NDK static libc++/compiler-runtime attribution
must be included as applicable (LLVM/Android toolchain terms are separate from
root MIT). No blanket binary redistribution clearance or legal advice is claimed;
final embedded dependency/link/notice inventory remains an integration obligation.

## Exact single model

[Publisher repository](https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF), pinned revision
**b5f37287796e5be0ea3dab2e7430873fb3f73e49**.
[Exact artifact](https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/b5f37287796e5be0ea3dab2e7430873fb3f73e49/Qwen3-0.6B-Q4_0.gguf).

- Filename **Qwen3-0.6B-Q4_0.gguf**, remote/local **428,970,080bytes**.
- Published LFS and independently computed host SHA256 match:
  **DA2572F16C06133561CE56ACCAA822216F2391EF4D37FBA427801CD6736417D4**.
- Host-only staging: `E:\Projects\.tooling\temp\kinetic-candidate3-20260913`.
- GGUFv3, architecture `qwen3`, file_type2/Q4_0, quantization_version2;
  311tensors:198 Q4_0 and113 F32. No other quantization or model downloaded.
- 28layers, embedding1024, FFN3072,16query heads/8KV heads, key/value length128,
  vocabulary151936, context metadata40960.
- Embedded Qwen3 Jinja chat-template SHA256:
  `87a2728cb8dc9fe424d624542f6060ec05a1d285ebbec578bb078900e33396b5`.
  It includes role delimiters, optional tool schema and an `enable_thinking=false`
  branch. This probe materializes only its no-tools/single-user/non-thinking branch.
- [Pinned card](https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/blob/b5f37287796e5be0ea3dab2e7430873fb3f73e49/README.md)
  identifies source `Qwen/Qwen3-0.6B` and automatic conversion via
  [ggml-org/convert](https://github.com/ggml-org/convert). Publisher, immutable artifact
  identity and conversion route are established; exact converter commit/build log
  and original source revision used by that conversion are not supplied.
- Fresh source-model reference revision: **c1899de289a04d12100db370d81485cdf75e47ca**;
  this is a research reference, NOT asserted as the conversion's source commit.
  [Official source](https://huggingface.co/Qwen/Qwen3-0.6B) is post-trained/chat,
  Apache2.0. Source config and GGUF architecture agree. GGUF Base-model ancestry
  points to Qwen3-0.6B-Base, as does the source chat model's own card metadata;
  this is not evidence that the downloaded artifact is the pretrained-only model.
- Publisher card, source LICENSE and GGUF license metadata identify Apache2.0.
  Model license and runtime MIT remain separate. No full conversion reproduction.

## Resource estimate and operating bounds

Initial fresh SM-A065F evidence: physical (`ro.kernel.qemu=0`), Android16/API36,
primary/64-bit ABI arm64-v8a; full list arm64-v8a,armeabi-v7a,armeabi.
Serial is excluded from permanent documentation.

MemTotal3,720,468KiB; MemAvailable1,142,188KiB at the bounded initial sample.
Phone/data free71,740,128KiB; hostE free121,824,034,816bytes. Battery18%, USB charging,
35C; Android thermal status0. These are preflight snapshots, not inference results.

Storage budget: host one429MB artifact +4GiB temporary source/build allowance +
2GiB reserve; phone oneartifact + executable +2GiB reserve. Both fit observed free
space. No owner files deleted; source/model bytes remain outside Kinetic.

Memory estimate at context1024, FP16 K/V:
`28 layers * 1024 tokens * 8 KV heads * 128 head dimension * 2 (K,V) * 2 bytes`
=117,440,512bytes =112MiB. Full weight-file mapping409.10MiB plus estimated
128–256MiB runtime/tokenizer/compute buffers gives about649–777MiB, with an
additional256MiB system headroom reserve. This is an estimate, not a fit proof.
Use batch/ubatch64, CPU2threads, no repack. Recheck MemAvailable before execution;
the host runner requires at least1,060,000KiB and thermal status below3, and stops
a running probe if observed available memory falls below256MiB or thermal reaches3.
This threshold is an engineering guard, not a vendor guarantee against LMKD.

Initial operating context **1024**, output cap **64**, temperature0.7, top-k20,
top-p0.8, min-p0, seed42, repeat penalty1. No4096/40K run. Upstream source card and
GGUF advertise larger windows; neither is a safe-device operating budget.

## External-harness and proposal boundaries

Executed isolated device path: `/data/local/tmp/kinetic-phase5b-candidate3/`;
the two transferred files and generated PID file were removed after verification,
then the empty directory was removed and its absence confirmed.
Only the host-built executable and verified GGUF may be transferred. Generated PID
state is local harness bookkeeping. No APK, Kinetic preferences/Room/API keys.
This owner-authorized external CLI exception is NOT a Kinetic shell capability or
permission for runtime-downloaded executable code in production.

The selected completion executable contains common HTTP utility code, but the
invocation selects a local model path and `--offline`; no server, remote URL,
model lookup, cloud request or transport is part of its intended inference path.
Do not claim the whole executable has no networking code. No owner network setting
changed. Local-file inference succeeded with this invocation. Network independence
is architectural/path evidence, not an airplane-mode or packet-capture experiment.

[Pinned function-calling documentation](https://github.com/ggml-org/llama.cpp/blob/v0.4.0/docs/function-calling.md)
describes server-side native/generic template handlers exposing structured
proposals. The GGUF embeds tool-role syntax. Neither proves this0.6B quantization's
tool reliability. The completion-only harness returns **TEXTUAL ONLY** output;
no JSON/regex-to-ToolCall parsing or Android tool execution. Future provider-native
proposal adaptation would need explicit schema/identity/exposure validation before
existing registry, policy, approval and durable ledger authority.

See [physical benchmark record](../PHASE5B_CANDIDATE3_PHYSICAL_BENCHMARK_20260913.md)
for the completed device result, current resource snapshots, exact trials, raw
quality failures and safety limitations. No Kinetic integration or production default.
