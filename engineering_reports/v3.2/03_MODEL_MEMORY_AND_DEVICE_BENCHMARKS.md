# AETHER AI v3.2 — Model Memory Architecture & Device Benchmark Report

**Audit Date**: 2026-11-04  
**Auditor**: Senior Android Systems Engineer & Machine Learning Specialist  
**Status**: `VERIFIED (Mathematical Calculus & Algorithms) / BLOCKED (Physical Device Silicon)`  

---

## 1. Quantitative Memory Calculus Model

The memory requirements for running quantized LLMs on mobile devices cannot be approximated simply by file size. AETHER AI enforces a strict multi-component memory budget:

$$M_{\text{total}} = M_{\text{weights}} + M_{\text{kv\_cache}} + M_{\text{tensors}} + M_{\text{runtime}}$$

### Component Breakdown:
1. **$M_{\text{weights}}$ (Weights Mapped into RAM)**:
   For GGUF models, weights are either memory-mapped (`mmap`) or buffered into process memory. For SmolLM2 1.7B `Q4_K_M`, this requires $\mathbf{1,025\text{ MB}}$.
2. **$M_{\text{kv\_cache}}$ (Key-Value Attention Cache)**:
   Calculated dynamically from layers $L$, hidden dimension $D$, and context length $C$:
   $$M_{\text{kv\_cache}} = \frac{2 \times L \times D \times C \times 2\text{ bytes}}{1024 \times 1024}$$
   - At $C = 2,048$ tokens: $\approx \mathbf{384\text{ MB}}$
   - At $C = 4,096$ tokens: $\approx \mathbf{768\text{ MB}}$
3. **$M_{\text{tensors}}$ (Scratchpad Activation Tensors)**:
   Temporary buffers required for matrix multiplications and intermediate activations during the forward pass: $\approx \mathbf{64\text{ MB}} - \mathbf{128\text{ MB}}$.
4. **$M_{\text{runtime}}$ (Process & Android Runtime Baseline)**:
   Compose UI, Android ART VM baseline, background threads: $\approx \mathbf{180\text{ MB}}$.

### Total Memory Footprint:
- At 2K Context: $1025 + 384 + 64 + 180 = \mathbf{1,653\text{ MB}}$ ($\approx 1.61\text{ GB}$)
- At 4K Context: $1025 + 768 + 128 + 180 = \mathbf{2,101\text{ MB}}$ ($\approx 2.05\text{ GB}$)

---

## 2. Low Memory Killer (LMK) Protection Policy

1. **Pre-Allocation Inspection**: `DeviceCapabilityManager.kt` checks available physical RAM via `ActivityManager.MemoryInfo.availMem`.
2. **Safety Headroom Threshold**: If $\text{Available RAM} - M_{\text{total}} < 800\text{ MB}$, the model load is refused with an alert: `"Insufficient free RAM. Requires 1,650 MB but only [X] MB available."`
3. **Context Clamping**: Users can configure context size from 512 to 4,096 tokens to adapt to tight memory conditions.

---

## 3. Physical Device Benchmark Execution Procedure

*Rule 2 & 10 Compliance: AETHER AI strictly prohibits fabricating benchmark numbers when physical hardware is unavailable. The following benchmark procedure is fully prepared and executable on physical test devices:*

### Test Procedure:
```bash
# 1. Push model weights to device
adb push smollm2-1.7b-instruct-q4_k_m.gguf /sdcard/Android/data/com.aistudio.aetherai.kqwzrt/files/models/

# 2. Monitor memory, temperature, and CPU clock rates
adb shell "dumpsys meminfo com.aistudio.aetherai.kqwzrt | grep -E 'TOTAL|Native Heap|Dalvik'"
adb shell "cat /sys/class/thermal/thermal_zone*/temp"

# 3. Benchmark Prompts:
# Prompt A (Short - 20 tokens): "Write a Kotlin extension function to capitalize words."
# Prompt B (Medium - 150 tokens): "Implement a thread-safe LRU cache in Java with synchronization."
# Prompt C (Long - 500 tokens): "Audit this Android manifest and list potential permission vulnerabilities."
```

### Measured Status:
- **Physical Device Benchmark Results**: `BLOCKED` (Pending execution on physical hardware device).
- **Algorithmic Calculus & Boundary Guards**: `VERIFIED` and active in source code.
