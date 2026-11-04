# Local GGUF Model Setup & Inference Guide

AETHER AI features an on-device local inference engine supporting **GGUF** quantized models powered by ARM NEON SIMD acceleration and `llama.cpp` native bindings.

---

## 1. Truthful State Architecture
In AETHER AI v1.0.0, the application strictly adheres to **truthful reporting**:
- The status bar displays **"No Model Loaded"** until a valid GGUF file has been verified and loaded into memory.
- No dummy memory allocations or simulated token streams are performed.

---

## 2. Recommended Models for Samsung Galaxy S22 Ultra (8GB / 12GB RAM)

| Model | Parameters | Quantization | File Size | Minimum RAM | Recommended Tier |
|---|---|---|---|---|---|
| **SmolLM2 360M** | 360M | Q4_K | ~240 MB | 2 GB | Standard |
| **Qwen 2.5 Coder 1.5B** | 1.54B | Q4_K | ~980 MB | 4 GB | Standard |
| **Qwen 2.5 Coder 3B** | 3.1B | Q4_K | ~1.98 GB | 6 GB | Performance |
| **Llama 3.2 3B** | 3.21B | Q4_K | ~2.05 GB | 6 GB | Performance |
| **DeepSeek R1 Distill 1.5B** | 1.78B | Q4_K | ~1.12 GB | 4 GB | Performance |

---

## 3. How to Sideload GGUF Models

Models can be placed directly into the app's internal models directory via ADB or imported through the Models Hub:

### Via ADB (Device connected via USB):
```bash
# Push model file directly into AETHER AI models directory:
adb push qwen2.5_3b_q4k.gguf /sdcard/Download/
adb shell run-as com.aistudio.aetherai.kqwzrt cp /sdcard/Download/qwen2.5_3b_q4k.gguf /data/user/0/com.aistudio.aetherai.kqwzrt/files/models/
```

### Via Models Hub:
1. Open **Models Hub** from the drawer or Home screen.
2. Browse recommended entries and tap **Download Model**.
3. Once downloaded, tap **Load Model** to initialize the ARM NEON native CPU runtime.
