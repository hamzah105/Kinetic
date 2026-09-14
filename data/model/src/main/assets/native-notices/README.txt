Kinetic Candidate 3B native runtime

llama.cpp v0.4.0
Source commit: 5266f24da75dc449bd56cbed7addb9c8e4a6a73e
https://github.com/ggml-org/llama.cpp
Unmodified upstream source, statically linked llama/ggml CPU into kinetic_local.
Kinetic's JNI bridge is separate original integration code.
llama-MIT.txt retains the upstream copyright and permission notice.
NDK-toolchain.txt retains toolchain notices, including static C++ runtime terms.
No common HTTP library, server, GPU backend, subprocess or multimodal runtime is
linked by this integration's selected target. No runtime plugin loading.

The model is not included in this APK. Explicit manual data import accepts only:
ggml-org/Qwen3-0.6B-GGUF revision b5f37287796e5be0ea3dab2e7430873fb3f73e49
Qwen3-0.6B-Q4_0.gguf, 428970080 bytes
SHA256 da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4
Source model: Qwen/Qwen3-0.6B, Apache-2.0, Copyright 2024 Alibaba Cloud.
Source license: https://huggingface.co/Qwen/Qwen3-0.6B/blob/c1899de289a04d12100db370d81485cdf75e47ca/LICENSE
Publisher conversion route: ggml-org/convert; exact conversion revision not supplied.
This is an experimental implementation path, not a permanent production model.
