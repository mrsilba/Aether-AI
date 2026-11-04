# MULTI-DEVICE ANDROID HARDWARE COMPATIBILITY REPORT
**Project**: AETHER AI Android Engineering Workstation  
**Author**: Principal Android Architect  
**Specification**: Universal Android Hardware Adaptive Engine  
**Date**: November 2026  
**Status**: VERIFIED  

---

## 1. Architectural Philosophy: Capability-Driven Adaptation

AETHER AI rejects device-name hardcoding and marketing label heuristics (e.g., "Ultra", "Pro", "Max"). In production, devices sharing identical marketing names often diverge significantly in available RAM, thermal design power (TDP), kernel scheduling, and background governor restrictions.

Instead, AETHER AI implements a runtime **capability-driven architecture** centered on `DeviceCapabilityManager`.

### Measured Runtime Metrics
1. **Physical & Available RAM**: Queried directly via `ActivityManager.getMemoryInfo()` to evaluate real-time available headroom and `lowMemory` warning flags.
2. **CPU Architecture & Core Count**: Dynamic core count via `Runtime.getRuntime().availableProcessors()` and ABI list via `Build.SUPPORTED_ABIS`.
3. **Vector Acceleration Flags**: Inspects hardware vector capabilities (`asimddp` for dot-product operations, `fphp` for FP16 half-precision, and `asimd` / `neon`).
4. **Thermal Throttling Feedback**: Subscribed to Android's `PowerManager.OnThermalStatusChangedListener` (API 29+), categorizing conditions into `NORMAL`, `LIGHT`, `MODERATE`, `SEVERE`, and `CRITICAL`.
5. **Battery State**: Evaluates remaining battery percentage and charging state to adjust background inference thread counts and batch sizes.

---

## 2. Hardware Capability Tiers

| Tier | Minimum RAM | CPU Topology | Acceleration | Optimal Workload |
|:---|:---|:---|:---|:---|
| **BASIC** | < 6 GB | 4–6 Cores | Software fallback | Cloud API (Gemini / Claude / GPT) |
| **STANDARD** | 6–8 GB | 8 Cores (Big.Little) | ARM64 NEON | Small local GGUF models (< 2B, Q4_0) |
| **PERFORMANCE** | 8–12 GB | 8 Cores (1+3+4 or 2+2+4) | ARM64 NEON / DotProd | Quantized 3B models (Q4_K, Q5_K) |
| **HIGH_PERFORMANCE** | 12–16 GB | 8–10 Cores | NEON DotProd + Vulkan | 7B GGUF models (Q4_K_M) |
| **ULTRA_PERFORMANCE** | > 16 GB | 8+ Prime/Performance | Full NPU/GPU Compute | 7B–14B models with extended context (8k+) |

---

## 3. Graceful Thermal Degradation Policy

When high-intensity local inference causes thermal elevation, AETHER AI protects device stability:
- **MODERATE Throttling**: Reduces inference worker threads from 4+ down to 3, and applies a 15% reduction to safe context allocations.
- **SEVERE Throttling**: Restricts inference to 2 efficiency/mid cores, halts non-essential background indexing, and notifies the user in the top telemetry bar.
- **CRITICAL / EMERGENCY**: Immediately suspends local matrix multiplications and automatically transitions active tasks to the Cloud API fallback router (`AdaptiveInferenceRouter`).
