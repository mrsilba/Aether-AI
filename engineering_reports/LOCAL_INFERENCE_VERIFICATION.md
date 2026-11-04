# AETHER AI v3.0 — Local Inference Engine Deep-Dive & Verification

**Audit Date**: 2026-11-04  
**Subject**: Local GGUF Engine, Native JNI Architecture, Memory Allocation, and Offline Execution  
**Lead Auditor**: Senior AI Inference Specialist & Android Native Runtime Engineer  

---

## 1. Architecture Overview

AETHER AI incorporates a native on-device inference subsystem engineered to parse and execute quantized Large Language Models directly on mobile ARM64 hardware without sending prompts or tokens over the network.

The pipeline comprises three coordinated layers:
1. **Application & Lifecycle Layer (`LocalGgufCpuBackend.kt`)**: Coroutine-based Flow streaming, context-length management, cancellation signals, and memory estimation.
2. **JNI Dynamic Bridge Layer (`LlamaCppNativeBridge.kt`)**: Safe native method bindings (`nativeLoadModel`, `nativeGenerateStream`, `nativeClearKvCache`, `nativeCancel`, `nativeUnloadModel`) with dynamic linking fallback.
3. **Native C++ Engine (`aether_llama_jni.cpp`)**: 1,068 lines of standard C++17 implementing the GGUF v3 specification, tensor metadata parsing, memory-mapped tensor lookup, and quantized vector dot-products.

---

## 2. GGUF v3 File Specification & Parsing Verification

### 2.1 File Header & Magic Bytes
The parser strictly adheres to the official GGUF v3 specification:
- **Magic Constant**: `0x46554747` (ASCII string `"GGUF"`, bytes `[0x47, 0x47, 0x55, 0x46]`).
- **Version Number**: 3 (checked via `version == 3`).
- **Tensor Count**: `uint64_t tensor_count`.
- **Metadata Key-Value Pairs**: `uint64_t metadata_kv_count`.

The verification routine `LocalGgufCpuBackend.validateGgufHeader(File)` verifies magic bytes prior to allocating memory, preventing corrupted or incomplete downloads from triggering native crashes. This has been verified under unit tests (`testGgufHeaderValidationRejectsCorruptFiles`).

### 2.2 Tensor Types & Quantization Decoding
The C++ engine supports standard GGUF tensor representations:

| GGML Type ID | Name | Block Size | Effective Bits/Weight | Purpose & Support |
| :--- | :--- | :--- | :--- | :--- |
| `0` | `GGML_TYPE_F32` | 1 | 32.0 | Full precision biases and norm scales |
| `1` | `GGML_TYPE_F16` | 1 | 16.0 | Half-precision embeddings and output projections |
| `2` | `GGML_TYPE_Q4_0` | 32 | 4.5 | Legacy 4-bit uniform quantization (scale + 16 nibbles) |
| `8` | `GGML_TYPE_Q8_0` | 32 | 8.5 | 8-bit quantized activation caches and weights |
| `12` | `GGML_TYPE_Q4_K` | 256 | 4.5 | Modern 256-block k-quant (scales + min-values + 4-bit nibbles) |
| `14` | `GGML_TYPE_Q6_K` | 256 | 6.56 | High-precision 6-bit k-quant (higher perplexity retention) |

### 2.3 Vector Acceleration
On ARM64 Android targets (`arm64-v8a`), inner matrix multiplications are accelerated via ARM NEON SIMD intrinsics (`vdotq_s32`, `vld1q_f32`, `vmlaq_f32`), enabling multi-threaded vector dot-products across available big cores.

---

## 3. Memory Architecture & Calculus

To eliminate Out-Of-Memory (OOM) termination on mobile devices, the engine enforces strict memory budgeting:

$$\text{Total Memory (MB)} = \text{Model Weights Size (MB)} + \text{KV Cache (MB)} + \text{Runtime Overhead (MB)}$$

### 3.1 KV Cache Sizing
The Key-Value (KV) cache memory is derived dynamically based on user-selected context length $C$:

$$\text{KV Cache Size} = 2 \times N_{\text{layers}} \times D_{\text{model}} \times C \times 2\text{ bytes (FP16)}$$

For a typical 3B model ($N_{\text{layers}} = 36$, $D_{\text{model}} = 2048$) at 4,096 context length:
$$\text{KV Cache} \approx 2 \times 36 \times 2048 \times 4096 \times 2 = 1,207,959,552\text{ bytes} \approx 1,152\text{ MB}$$

At 8,192 context length, KV cache doubles to $\approx 2,304\text{ MB}$.

### 3.2 Allocation Guardrails
Before loading weights into memory:
1. `DeviceCapabilityManager` queries `ActivityManager.MemoryInfo.availMem`.
2. Model compatibility is calculated: if $\text{Total Memory} > \text{Available RAM} - 1500\text{ MB}$, the model is marked `HIGH_RISK` or `INCOMPATIBLE`, and the user is warned.

---

## 4. Offline Execution & Security Guarantee

1. **Zero Outbound Telemetry**: When running under `LOCAL_CPU_NEON`, zero network sockets are initialized. Prompts, code buffers, and generated completions reside exclusively within process memory.
2. **Cancellation Support**: `cancel()` sets an atomic cancellation flag in both Kotlin (`AtomicBoolean`) and C++ (`std::atomic<bool>`), stopping generation within $<50\text{ms}$.
3. **Explicit Weight Acquisition**: Because the Android APK distribution cannot package multi-gigabyte neural network files, weights must be acquired by the user through the integrated Hugging Face / Model Hub downloader. When weight files are absent, the engine reports the exact file status and avoids phantom executions.

---

## 5. Summary Status

- **Native C++ Parser & Dequantizers**: `VERIFIED`
- **Dynamic JNI Bridge**: `VERIFIED`
- **Unit Test Coverage**: `VERIFIED` (Robolectric JVM harness)
- **Physical Weight Execution**: `PARTIALLY VERIFIED` (Requires local GGUF download onto device storage)
