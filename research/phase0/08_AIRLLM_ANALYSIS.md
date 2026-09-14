# AirLLM forensic architecture and Android feasibility audit

## Audit identity and recommendation

| Item | Exact local evidence | Finding |
|---|---|---|
| Checkout | `Eco_reference/airllm` | Intact, clean Git checkout for the files inspected. |
| Remote | repository metadata | `origin = https://github.com/hamzah105/airllm` |
| Branch / revision | repository metadata | `main` at `64a4e4fc3749aa7dc9bba4788f560ed0d7e74bd2` |
| Latest local commit | repository metadata | `2026-07-28T20:05:48-05:00`, `release: 3.1.0` |
| Release/build | `air_llm/setup.py` | Python package `airllm` 3.1.0; PyTorch, Transformers, Accelerate, SafeTensors, Hugging Face Hub, SciPy, SentencePiece. |
| Primary target | `air_llm/airllm/airllm_base.py::AirLLMBaseModel`; `air_llm/airllm/airllm_llama_mlx.py::AirLLMLlamaMlx` | CUDA/PyTorch desktop or server; separate Darwin/Apple-Silicon MLX implementation. This is not an Android runtime. |
| Required audit recommendation | implementation evidence below | **RESEARCH_ONLY** for Kinetic's local-model decision. Adapt the residency/prefetch idea only after Android-native benchmarks; do not directly port this Python/PyTorch implementation. |

AirLLM solves a precise problem: make accelerator residency depend on one layer (or selected MoE experts), not total parameter count. It does **not** make the checkpoint small, avoid reading weights, provide a mobile inference kernel, or make autoregressive decoding fast. For a dense transformer, its forward hooks reload the full sequence of weight modules on every forward; Transformers generation performs a forward for the prompt and then forwards for generated tokens. The dominant resource moves from VRAM capacity to repeated disk read plus host-to-accelerator transfer. That trade can be rational on a workstation with a huge model and fast storage; it is a poor default for an always-available Android agent, where latency, flash capacity/endurance, bandwidth, battery, thermal headroom, and process lifecycle matter together.

## License and provenance

- The root `LICENSE` and `air_llm/LICENSE` are byte-identical Apache License 2.0 texts. No `NOTICE` file is present. The template appendix still contains `Copyright [yyyy] [name of copyright owner]`; repository ownership/attribution is not filled into the license file itself.
- `air_llm/setup.py` classifies the package as Apache Software License and identifies author Gavin Li / upstream URL `https://github.com/lyogavin/airllm`. Candidate core files generally have no file header.
- `air_llm/airllm/tokenization_baichuan.py` contains explicit Baichuan 2023 and EleutherAI/Hugging Face 2022 notices plus Apache-2.0 terms. Attribution must be preserved if that file is redistributed.
- `anima_100k/modeling_flash_llama.py` likewise carries EleutherAI/Hugging Face copyright and Apache-2.0 text.
- `training/qlora.py`, `rlhf/qlora_dpo.py`, and `anima_100k/longer_training.py` each claim an MIT license in the root `LICENSE`, but the actual root license is Apache-2.0. That is a real file-level/repository-level provenance inconsistency. These auxiliary training files are not Kinetic candidates and should not be reused without tracing their origin and license.
- Model code and weights downloaded from Hugging Face have their own licenses. `AutoModel.from_pretrained` does not transfer the AirLLM repository license to a model, tokenizer, remote Python module, or checkpoint. Kinetic's `ModelDescriptor` must preserve model/card license, acceptable-use terms, source revision, digest, signer/publisher, and attribution separately from engine licensing.
- Dependencies such as PyTorch, Transformers, Accelerate, SafeTensors, bitsandbytes, compressed-tensors, MLX, and model-specific remote code require a separate dependency/provenance review if chosen. Apache-2.0 at repository root is not a blanket license for them.

Reuse decision: the high-level streaming/residency algorithm is permissively referenceable, but Kinetic should independently express and benchmark it against whichever Android-native engine is selected. Copying Python machinery would import the wrong runtime and dependency assumptions.

## Build and dependency integrity

`air_llm/setup.py` is the release declaration: `torch>=2.4`, `transformers>=4.49,<5.13`, `accelerate>=1.0`, SafeTensors, Hugging Face Hub, SciPy, SentencePiece, and `tqdm`; bitsandbytes and compressed-tensors are optional. Root `requirements.txt` is materially stale/different: bitsandbytes 0.39.0, Git-head Transformers, old pinned PEFT/Accelerate, and training/evaluation packages. A consumer cannot treat both files as one reproducible environment.

No lockfile, model manifest with digest, Android build, NDK build, mobile artifact, or reproducible benchmark environment is present. `find_or_create_local_splitted_path` accepts a local directory or Hugging Face repo ID and downloads mutable repository content through `huggingface_hub.snapshot_download`; no explicit revision parameter is exposed by `AirLLMBaseModel.__init__`.

## Entry point and model selection

`air_llm/airllm/__init__.py` exports `AutoModel` and `AirLLMBaseModel` on non-Darwin platforms; on Darwin it exports `AirLLMLlamaMlx`. It defensively imports dedicated subclasses such as ChatGLM, QWen, Baichuan, InternLM, Mistral, Mixtral, and Kimi K3.

`air_llm/airllm/auto_model.py::AutoModel.from_pretrained` is the public factory:

1. On macOS, it always returns `AirLLMLlamaMlx`.
2. Else `get_module_class` loads `AutoConfig` with `trust_remote_code=True`, examines the first architecture, uses `ARCH_OVERRIDES` for a few non-standard layouts, and otherwise selects `AirLLMBaseModel`.
3. It dynamically imports the chosen class.

The generic base deliberately lets Transformers own model forward/generation semantics. This increases architecture coverage, but compatibility is only as stable as Transformers and any model-supplied code. The current `tests/test_automodel.py` expects older dedicated class mappings (including `AirLLMLlama2`, Mistral, and Mixtral) that are no longer present in `ARCH_OVERRIDES`; it appears stale relative to the implementation and triggers network/config loading for every case.

## Layer streaming implementation

### 1. Split or locate layer shards

`air_llm/airllm/utils.py::find_or_create_local_splitted_path` recognizes local PyTorch/SafeTensors checkpoint forms or downloads model metadata and weights from Hugging Face. It calls `split_and_save_layers` with architecture-specific module names.

`split_and_save_layers`:

- reads a PyTorch/SafeTensors weight map, or synthesizes one for a single weight file;
- derives embeddings, decoder layers, final norm, head, and optional resident modules;
- writes one persisted file per module through `persist/ModelPersister`;
- for SafeTensors shards already containing exactly one module, uses `link_or_copy_file` to prefer a hard link, then symlink, then copy, avoiding checkpoint duplication;
- otherwise reads source shards into CPU state, extracts the module, optionally compresses, persists it, deletes released tensors, and calls `clean_memory`;
- creates `.done` markers used by `SafetensorModelPersister.model_persist_exist`;
- can delete original checkpoint shards while splitting.

This is storage re-layout, not reduced total model information. Without passthrough links or `delete_original`, the original checkpoint and per-layer copy coexist and may approach double storage. `check_space` tries to preflight capacity, while the README itself warns that splitting is disk-intensive. Deleting originals makes recovery/download integrity more consequential; interrupted transformations rely on per-file marker checks rather than a signed transactional model manifest.

### 2. Construct a weightless executable graph

`AirLLMBaseModel.__init__` defaults to `device="cuda:0"`. It resolves shards, loads model configuration/tokenizer, chooses native dtype (often bfloat16; fallback float16), creates a single-worker prefetch executor, then calls `init_model`.

`init_model` uses Accelerate `init_empty_weights(include_buffers=False)` to construct a real `AutoModelForCausalLM` on the PyTorch `meta` device. It prefers SDPA attention and falls back to eager. It preprocesses declared Hugging Face quantizers, retains materialized buffers on the running device, and patches the model's reported device/dtype; `_patch_device_property` can mix `GenerationMixin` back into remote/older model classes. `set_layers_from_layer_names` records the ordered executable module objects.

Configuration loading first tries `trust_remote_code=False`, then falls back to `True`; tokenizers always use `trust_remote_code=True`. This can execute model-repository Python. On a trusted research workstation that may be an accepted trade; downloadable executable model code is not an acceptable default for a Play-distributed Android runtime.

### 3. Load just in time and evict immediately

`_install_streaming_hooks` registers `_pre_hook` and `_post_hook` on embeddings, every decoder layer, norm, and head, except special handling for tied embeddings. `_pre_hook` obtains the layer from a finished prefetch future or `load_layer_to_cpu`, moves each parameter to the running device through `move_layer_to_device`, and schedules the next module on the one-worker executor. `_post_hook` moves placed parameters back to `meta` (or calls `module.to('meta')`), invokes `clean_memory`, and returns the activation.

`load_layer_to_cpu` can pin CPU tensors before transfer only when their total size is at most `max_pinned_layer_bytes = 2 GiB`; larger layers remain pageable. Compression disables prefetching. `clean_memory` runs Python GC, tries glibc `malloc_trim`, and calls `torch.cuda.empty_cache`, showing the desktop/CUDA memory-management assumption.

The underlying Transformers model retains activations and KV cache needed for generation. AirLLM removes weight residency; it does not eliminate context-dependent KV-cache/activation memory. `max_seq_len` is stored on the object but is not consulted anywhere else in the implementation, so it is not an enforced allocation or input bound.

## Quantization and compression assumptions

The code has two distinct quantization paths.

1. AirLLM's optional split-time `compression='4bit'|'8bit'` is implemented in `utils.py::compress_layer_state_dict` with bitsandbytes CUDA operations: NF4 with block size 64 or block-wise 8-bit with block size 2048. `uncompress_layer_state_dict` reconstructs quantization state and calls `.cuda()`/bitsandbytes dequantization each time a shard loads. This is weight-storage compression intended to reduce the disk bottleneck, not a mobile integer-kernel pipeline. Prefetching is explicitly disabled when compression is enabled.
2. `AirLLMBaseModel.init_model` detects a checkpoint's `quantization_config` and uses Transformers `AutoHfQuantizer`. `_should_load_verbatim` preserves packed integer, FP8, scale, zero-point, and related tensors rather than casting them. `_decompress_state_dict` contains special handling for compressed-tensors-style modules. These paths depend on PyTorch/Transformers quantizer implementations and supported accelerator kernels.

`tests/test_compression.py` allocates tensors directly on CUDA and checks reconstructed RMSE under 0.1. It does not test model quality, representative tasks, CPU/mobile kernels, thermal behavior, prefetch interaction, model compatibility, or power. The README's claim that storage compression speeds inference is consistent with a disk-bound implementation, but cannot be generalized to Android without device-specific measurements.

For Kinetic, model format/quantization and execution engine must be declared in `ModelDescriptor` (for example architecture, tokenizer, tensor format, quantization scheme, context limit, required RAM/storage/accelerator, license/digest) and validated by an engine-specific loader. Do not expose a generic `compression="4bit"` switch that hides incompatible quantization formats or kernels.

## Sparse MoE per-expert streaming

`air_llm/airllm/airllm_kimi_k3.py::AirLLMKimiK3.set_layer_names_dict` identifies a nested language model, `block_sparse_moe.experts`, and resident vision/projector/residual modules. `_setup_expert_streaming` in `airllm_base.py` lists SafeTensors keys, groups them by expert index, installs hooks on every expert module, and separates non-expert tensors. `_expert_pre_hook` calls `load_layer_subset` for only the selected expert's keys; `_expert_post_hook` returns them to `meta`.

This is the most interesting algorithmic extension: sparse routing means only selected experts need weight I/O. It still depends on an architecture where dispatch invokes identifiable expert submodules, seekable SafeTensors, correct per-expert key mapping, quantizer support, and storage capable of the access pattern. It also keeps declared multimodal modules resident. Kinetic should study expert-granular demand loading only for a specific model/runtime pair; it is not a generic mobile engine abstraction.

`tests/test_kimi_k3_split.py` is the strongest automated asset in the checkout. It creates a miniature SafeTensors checkpoint and verifies:

- all standard and resident modules are produced;
- one-module-per-shard files are hard-linked, not duplicated;
- shared shards are materialized separately without tensor leakage;
- values and dtypes round-trip exactly;
- packed 4-bit payload remains `uint8`;
- ordinary multi-module shards continue to split into real files.

Those storage invariants are adaptable to any future Kinetic model conversion tool, although Android deployment would need a signed atomic manifest, resumable download/conversion, storage quotas, crash consistency, and model-revision migration tests.

## MLX path is not an Android path

`air_llm/airllm/airllm_llama_mlx.py::AirLLMLlamaMlx` is selected only when `sys.platform == "darwin"`. It defines its own Llama-like attention/MLP stack in Apple MLX. During prompt evaluation and each token step, `model_generate` constructs and loads embeddings, each transformer block, norm, and output head from `MlxModelPersister`, evaluates, and deletes modules while retaining per-layer KV cache.

`persist/mlx_model_persister.py` converts tensors to float16 NumPy archives and maps PyTorch naming to MLX. This demonstrates the same algorithm in a second accelerator ecosystem, but MLX is Apple-specific and the implementation is architecture-specific. It provides no Android GPU, NNAPI, LiteRT, vendor NPU, Vulkan, or CPU SIMD integration.

## Disk bandwidth, latency, battery, and storage implications

The dense-model lower bound follows directly from the hooks:

```text
bytes read per generated token ~= sum(bytes of every streamed module)
minimum I/O time per token >= bytes read / sustained storage throughput
```

Transformers may retain a KV cache so attention does not recompute earlier token projections, but `_pre_hook` still reloads every layer's weights for every model forward. A dense 70B model at 16 bits contains roughly 140 GB of weights before metadata; even an ideal 5 GB/s sustained device would spend about 28 seconds per full weight pass before compute and transfers. This numeric example is an inference from parameter count and the audited access pattern, not a measured AirLLM/Android benchmark. Four-bit storage reduces bytes but does not remove repeated full-model traffic and may add dequantization cost.

On Android the important barriers are cumulative:

- **Capacity:** checkpoints can exceed ordinary free app storage by orders of magnitude. Splitting may transiently duplicate them. App uninstall/clear-data, scoped storage, low-storage events, and model updates need transactional handling.
- **Sustained throughput:** phone flash performance varies with temperature, free space, file layout, background I/O, and power state. Peak sequential specifications are not token-generation guarantees; per-expert reads may be less sequential.
- **Energy and thermal:** repeatedly reading and transferring most weights for each token exercises storage, memory buses, CPU/GPU/NPU, and dequantization continuously. It is antagonistic to a responsive background assistant and likely to throttle.
- **Flash endurance:** total bytes read do not directly consume NAND program/erase cycles like writes, but splitting/downloading/caching enormous checkpoints does write heavily, and constant high-volume I/O has system/battery cost. Endurance and thermal claims require measurement, not assumption.
- **Latency/cancellation:** hooks are synchronous at each layer. The one-thread prefetch overlaps one next read but there is no priority, deadline, Android lifecycle cancellation, suspend/resume checkpoint, or interruption-safe generation state.
- **Memory not captured by "one layer":** resident modules, layer activations, tokenizer/config/model graph, prefetched CPU layer, current accelerator layer, outputs, and growing KV cache all contribute. For very large individual layers, even the one-layer working set can exceed a phone.

AirLLM's claim is therefore about **capacity feasibility**, not acceptable interactive performance. The project's `LayeredProfiler` records disk/compression buckets and CUDA free memory, while `tests/test_streaming_gpu.py` reports peak VRAM and elapsed time; neither measures disk bytes, energy, thermals, Android jank, battery, or flash/storage behavior.

## Android feasibility barriers

| Barrier | Exact evidence | Consequence |
|---|---|---|
| Runtime | `setup.py`; imports in `airllm_base.py` | Python + PyTorch + Transformers + Accelerate + Hugging Face dynamic model graph is not an Android-native inference stack. |
| Accelerator | default `device="cuda:0"`; `.cuda()` in examples/tests/compression; `torch.cuda.empty_cache` | CUDA/bitsandbytes assumptions do not map to ordinary Android GPUs/NPUs. CPU selection may work in desktop PyTorch paths, but that is not evidence of viable Android CPU inference. |
| Alternate backend | `AirLLMLlamaMlx`, `MlxModelPersister` | Darwin/MLX only; no Android support. |
| Model code | `AutoModel.get_module_class`; base config/tokenizer fallbacks | `trust_remote_code=True` can execute downloaded Python and is incompatible with a constrained Play-safe model loader. |
| Storage | `split_and_save_layers`, `snapshot_download`, optional `delete_original` | Desktop filesystem/HF cache assumptions; no scoped-storage/low-storage/resumable signed model lifecycle. |
| Lifecycle | synchronous `generate`, forward hooks, worker thread | No coroutine cancellation contract, WorkManager/service integration, process-death resume, memory-pressure callback, foreground visibility, or resource arbitration. |
| Native engines | repository-wide implementation inventory | No LiteRT/TFLite, MediaPipe, llama.cpp, ONNX Runtime, ExecuTorch, MLC, NNAPI, QNN, Vulkan, or Android NDK/JNI module. |

`DIRECTLY_PORT` is therefore rejected. `ADAPT_ALGORITHM` is too strong as the project-level recommendation because no evidence shows the repeated-I/O approach meets the target product's latency/energy envelope. **`RESEARCH_ONLY`** correctly reserves its most useful hypothesis - explicit weight residency, prefetch, and expert-granular loading - for a benchmark spike against conventional fully resident small models and Android-native runtimes.

## Kinetic local-model abstraction proposal (interfaces only)

AirLLM should sit behind, not define, the eventual engine boundary. The following contract roles are supported by the gaps observed here; this is not production Kotlin or an architecture freeze.

```text
LocalModelEngine
  engineId / supported ModelDescriptor predicates
  prepareModel(storage, policy, progress, cancellation)
  openSession(descriptor, SessionOptions) -> InferenceSession
  health/capacity estimate / close

ModelDescriptor
  modelId, immutable revision, content digests, source/license/attribution
  architecture, tokenizer, tensor format, quantization, context limit
  modalities, structured-output support
  storage/RAM/accelerator estimates, engine compatibility, min app/device requirements

InferenceSession
  generate(request, streaming sink, cancellation)
  checkpoint/resume capability declaration
  current resource use / close

StructuredGeneration
  grammar/schema/tool-call constraints and validation outcome
  never inferred merely from free-form JSON text

EmbeddingEngine
  separate descriptor/session/batching/dimension/normalization contract

ModelStorage
  quota, signed/resumable download, atomic activation, verification
  revision pinning, eviction, migration, corruption recovery, low-storage response
```

Capabilities must be queried, not assumed. For example, an engine can declare `supportsWeightStreaming`, `supportsProcessResume`, supported accelerators, grammar constraints, image/audio inputs, and whether cancellation is immediate or only at token/layer boundaries. Provider routing should compare predicted latency, quality, privacy, energy/thermal budget, connectivity, and task requirements; "fits in memory" is insufficient.

If a weight-streaming spike is pursued, expose a research-only `ResidencyPlan` behind the engine:

- module/expert partition and order;
- bounded CPU and accelerator resident sets;
- prefetch distance and pinned-memory budget;
- deterministic eviction and cancellation point;
- expected bytes per prompt/token and measured throughput;
- KV-cache and resident-module budget;
- thermal/battery guard and fallback to cloud/smaller model.

The experiment should compare at least: a fully resident small quantized model, a partially cached model, dense layer streaming, and sparse expert streaming on real target devices. Measure time-to-first-token, tokens/s, total bytes read/written, peak PSS/RSS and accelerator memory, battery energy, surface/SoC temperature, throttling curve, app jank, cancellation latency, low-memory behavior, and cold/warm resume.

## Security, privacy, and Play considerations

- `trust_remote_code=True` is the largest software security concern: model/tokenizer repositories can supply executable Python with ambient file/network/process privileges. Kinetic Play must use a closed set of engine/model formats and never execute downloaded model code.
- `hf_token` is passed into Hugging Face loading functions, but AirLLM defines no encrypted secret store or log-redaction contract. Kinetic should use an opaque credential broker and avoid needing a model-hub token during inference.
- `snapshot_download` does not expose an immutable revision through the public constructor. Kinetic needs pinned digests, atomic verification, provenance, license acceptance, and revocation before activation.
- Model inputs/outputs remain local in this implementation, and there is no telemetry code in the audited inference package. That is a privacy advantage, but third-party remote code could change the boundary; engine isolation and network policy are still required.
- Downloadable **data-only model artifacts** used by fixed app code are conceptually `PLAY_SAFE_OR_LIKELY`, subject to size, disclosure, licensing, device-resource behavior, and policy review. Downloadable Python/native/dex/JAR executable code is a different, high-risk category and should not be required.
- A long foreground inference operation needs user visibility and Android lifecycle compliance. AirLLM offers no evidence that a foreground service can overcome process death, thermal throttling, battery restrictions, or Play policy; Kinetic must design for interruption and explicit resumption rather than pretending it is a daemon.

## Test and benchmark assets

Five tracked Python files are test-shaped, while six notebook test artifacts are exploratory/manual:

| Asset | What it actually validates | Limitation / Kinetic use |
|---|---|---|
| `air_llm/tests/test_kimi_k3_split.py` | CPU/tempfile structural split, hard-link/copy behavior, exact tensor/dtype round trip, packed payload | Strongest reusable test idea; add crash/atomicity/digest/quota/Android filesystem cases. |
| `air_llm/tests/test_compression.py` | CUDA bitsandbytes 4/8-bit tensor reconstruction RMSE | No end-task quality, model coverage, mobile kernel, or energy measurement. |
| `air_llm/tests/test_streaming_gpu.py` | Manual CLI: deterministic output comparison to fully resident Transformers, peak CUDA VRAM, elapsed time, optional VRAM cap | Not an automated unit test; requires model download/GPU and lacks I/O/power/thermal metrics. Its reference-output equivalence pattern is valuable. |
| `air_llm/tests/test_automodel.py` | Expected model-to-class routing | Network dependent and stale against current generic `ARCH_OVERRIDES`; demonstrates why factory contracts need offline config fixtures. |
| `scripts/test_cn_dataset_lenghts.py` | Downloads a tokenizer/dataset and prints length quantiles | Manual data analysis, not an inference correctness test. |
| `air_llm/tests/test_notebooks/*.ipynb` | Historical interactive scenarios | Not a deterministic CI/conformance suite. |

No audited test covers cancellation, corrupted/partial download, process death, out-of-storage, low-memory callback, remote-code isolation, quantized model task quality, context-limit enforcement, thermal throttling, battery/runtime impact, or cloud/local failover. Tests were not run during this forensic pass because the GPU/network/model prerequisites would not add reliable evidence about Android feasibility and could download or transform large external models.

## Synthesis-ready conclusions

1. AirLLM reduces **weight residency**, not model size, KV-cache size, or total computation. This distinction must survive cross-repository synthesis.
2. Dense autoregressive generation re-reads essentially all streamed weights per model forward/token. Storage bandwidth becomes a hard latency/energy lower bound.
3. One-layer VRAM feasibility does not imply an acceptable Android experience. A single layer, next prefetched CPU layer, activations, resident modules, graph, and KV cache still need memory.
4. The cleanest reusable concept is an explicit, measured residency plan with prefetch/eviction. It belongs behind a `LocalModelEngine`, not in the agent kernel.
5. Per-expert SafeTensors loading is a worthwhile research idea for sparse MoE, but only with a chosen architecture/runtime and real device data.
6. Split/checkpoint storage invariants and reference-output equivalence are the strongest test assets to adapt.
7. PyTorch/Transformers/Accelerate/CUDA/bitsandbytes and Darwin MLX are feasibility barriers, not Android components. No native Android inference engine exists here.
8. `trust_remote_code` and unpinned model repository content are incompatible with a fixed, Play-safe executable boundary.
9. License provenance must be tracked per engine, model, tokenizer, and remote code. Auxiliary files claiming a nonexistent root MIT license are specifically ambiguous.
10. Final recommendation: **RESEARCH_ONLY**. Benchmark the algorithm against fully resident small quantized models; do not select or port AirLLM as Kinetic's local runtime on repository evidence alone.

## Material implementation ledger

Material artifacts read or section-read: **28**. Repository-wide inventories, Git metadata, and targeted searches are additional and not counted.

1. Root/build/license/docs (5): `LICENSE`; `air_llm/LICENSE`; `README.md`; `requirements.txt`; `air_llm/setup.py`.
2. Primary engine and selection (8): `air_llm/airllm/__init__.py`; `auto_model.py`; `airllm_base.py`; `airllm.py`; `airllm_kimi_k3.py`; `airllm_llama_mlx.py`; `utils.py`; `profiler.py`.
3. Persistence/specialized model files (6): `air_llm/airllm/persist/model_persister.py`; `safetensor_model_persister.py`; `mlx_model_persister.py`; `airllm_qwen2.py`; `airllm_mixtral.py`; `tokenization_baichuan.py`.
4. Focused tests/manual test script (5): `air_llm/tests/test_streaming_gpu.py`; `test_compression.py`; `test_automodel.py`; `test_kimi_k3_split.py`; `scripts/test_cn_dataset_lenghts.py`.
5. File-level provenance samples (4): `training/qlora.py`; `rlhf/qlora_dpo.py`; `anima_100k/longer_training.py`; `anima_100k/modeling_flash_llama.py`.

No model was downloaded, transformed, or executed, and no reference source was modified. This report contributes algorithmic and interface-level research only.
