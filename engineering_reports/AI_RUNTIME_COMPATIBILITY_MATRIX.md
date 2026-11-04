# AETHER AI — AI Runtime & Hardware Compatibility Matrix

## 1. Compatibility Formula & Memory Calculus
The `ModelCompatibilityEngine` evaluates whether a model is safe to load and execute without triggering Android Linux out-of-memory (OOM) killer terminations:

$$\text{Estimated Total RAM (MB)} = \text{Model Weight Size (MB)} + \text{KV Cache Memory (MB)} + \text{Runtime Buffer (180 MB)}$$

Where KV Cache is calculated dynamically from context window size:
$$\text{KV Cache (MB)} = \frac{2 \times \text{Layers (32)} \times \text{Hidden Dimension (4096)} \times \text{Context Length} \times 2 \text{ bytes}}{1024 \times 1024}$$

---

## 2. Dynamic Compatibility Matrix by Device Tier

| Model Descriptor | Download Size | Total RAM Required (MB) | Basic Tier (<6GB RAM) | Standard Tier (6-8GB RAM) | Performance Tier (8-12GB RAM) | Ultra Tier (>16GB RAM) |
|---|---|---|---|---|---|---|
| **SmolLM2 1.7B (Q8_0)** | 1.66 GB | ~1,980 MB | **RECOMMENDED** | **RECOMMENDED** | **RECOMMENDED** | **RECOMMENDED** |
| **Qwen2.5-Coder 3B (Q4_K)** | 1.88 GB | ~2,320 MB | **POSSIBLE** (Reduced Context) | **RECOMMENDED** | **RECOMMENDED** | **RECOMMENDED** |
| **DeepSeek-Coder 7B (Q4_K)** | 4.10 GB | ~4,920 MB | **UNSUPPORTED** | **POSSIBLE** | **RECOMMENDED** | **RECOMMENDED** |
| **Mistral 7B (Q4_K, 32k)** | 4.16 GB | ~5,840 MB | **UNSUPPORTED** | **UNSUPPORTED** | **POSSIBLE** | **RECOMMENDED** |
| **Gemma 2 2B (F16)** | 4.95 GB | ~5,420 MB | **UNSUPPORTED** | **POSSIBLE** | **RECOMMENDED** | **RECOMMENDED** |

---

## 3. Dynamic Rating Definitions
- **RECOMMENDED**: Sufficient memory headroom (>500MB free RAM beyond peak model consumption). Inference will operate with maximum tokens/sec without throttling.
- **POSSIBLE**: Tight memory headroom (0-400MB free RAM). The engine automatically caps the active context length to prevent memory starvation.
- **UNSUPPORTED**: Exceeds physical RAM or disk storage. Loading is disallowed by default to prevent application crashes.
