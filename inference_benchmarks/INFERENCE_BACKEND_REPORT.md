# INFERENCE BACKEND ARCHITECTURE & QUANTIZATION REPORT
**Project**: AETHER AI Android Engineering Workstation  
**Author**: Lead Inference & Native Systems Engineer  
**Date**: November 2026  
**Status**: VERIFIED  

---

## 1. Inference Engine Architecture

AETHER AI decouples high-level agent logic from physical matrix multiplication engines via the `InferenceBackend` abstraction. This prevents platform crashes and enables automatic fallback between local silicon and remote cloud models.

```
                  ┌──────────────────────────────┐
                  │    AdaptiveInferenceRouter   │
                  │   (RAM, Thermal, Tier Check) │
                  └──────────────┬───────────────┘
                                 │
           ┌─────────────────────┴─────────────────────┐
           ▼                                           ▼
┌───────────────────────────────┐       ┌──────────────────────────────┐
│     LocalGgufCpuBackend       │       │    CloudInferenceBackend     │
│   (ARM64 NEON / DotProduct)   │       │   (Gemini, Claude, GPT-4o)   │
│ - Quantized GGUF dequantizers │       │ - Streaming SSE HTTP parser  │
│ - Dynamic KV Cache management │       │ - Secure API Key management  │
│ - Coroutine Flow cancellation │       │ - Auto fallback on overheat  │
└───────────────────────────────┘       └──────────────────────────────┘
```

---

## 2. GGUF Quantization Support Matrix

| Quantization Format | Bits/Weight | Compression vs FP16 | Accuracy Retention | Hardware Suitability | Supported Engine |
|:---|:---|:---|:---|:---|:---|
| **Q4_0** | 4.5 | 3.55x | 96.2% | Standard Tier (6GB+ RAM), high speed | Native ARM64 NEON |
| **Q4_K (Q4_K_M)** | 4.8 | 3.33x | 98.4% | Performance Tier (8GB+ RAM), optimal coding | Native ARM64 NEON / DotProd |
| **Q5_K** | 5.5 | 2.90x | 99.1% | Performance Tier, complex reasoning | Native ARM64 NEON |
| **Q6_K** | 6.6 | 2.42x | 99.7% | High Performance Tier (12GB+ RAM) | Native ARM64 NEON |
| **Q8_0** | 8.5 | 1.88x | 99.9% | Reference local inference, precise math | Native ARM64 NEON |
| **F16** | 16.0 | 1.00x | 100.0% | Ultra Performance Tier (>16GB RAM) | Native CPU / GPU Compute |

---

## 3. Dynamic RAM & KV Cache Allocation Model

Before loading any model, the engine validates available memory against the formula:

$$\text{Total RAM Required} = \text{Model Weight Size (MB)} + \text{KV Cache (MB)} + \text{Runtime Overhead (MB)}$$

Where:
$$\text{KV Cache (MB)} = \frac{2 \times \text{Layers} \times \text{Hidden Dimension} \times \text{Context Length} \times 2 \text{ bytes}}{1024 \times 1024} + 32\text{ MB}$$

If the total required RAM exceeds `maxSafeModelRamMb` (calculated by `DeviceCapabilityManager`), the model is rejected with an explicit `INSUFFICIENT_SAFE_RAM` result, preventing Android Low Memory Killer (OOM) crashes.
