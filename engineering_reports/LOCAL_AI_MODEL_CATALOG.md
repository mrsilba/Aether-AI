# AETHER AI — Local AI Model Catalog & Task Taxonomy

## 1. Overview
The AETHER AI Model Hub provides a curated, verified catalog of open-weight LLMs available in quantized GGUF format for local execution on ARM64 Android smartphones. Each model is categorized by task and hardware compatibility tier.

---

## 2. Model Catalog Matrix

| Model Identifier | Publisher | Parameters | Quantization | Download Size | Context Window | Min Hardware Tier | Primary Task |
|---|---|---|---|---|---|---|---|
| **`qwen2.5-coder-3b-q4k`** | Qwen Team (Alibaba) | 3.1B | Q4_K_M | 1.88 GB | 8,192 | STANDARD (6-8 GB RAM) | **Coding & Engineering** |
| **`deepseek-coder-7b-q4k`** | DeepSeek AI | 6.9B | Q4_K_M | 4.10 GB | 16,384 | PERFORMANCE (8-12 GB RAM) | **Reasoning & Planning** |
| **`smollm2-1.7b-q8`** | Hugging Face TB | 1.7B | Q8_0 | 1.66 GB | 4,096 | BASIC (<6 GB RAM) | **Low-RAM & Mobile** |
| **`mistral-7b-instruct-q4k`** | Mistral AI | 7.2B | Q4_K_M | 4.16 GB | 32,768 | HIGH_PERFORMANCE (12-16 GB) | **Long-Context Analysis** |
| **`gemma-2-2b-f16`** | Google DeepMind | 2.6B | F16 | 4.95 GB | 8,192 | PERFORMANCE (8-12 GB RAM) | **Writing & Documentation** |

---

## 3. Task Category Recommendations & Objective Badges

### A. Coding & Software Engineering
- **Recommended**: `qwen2.5-coder-3b-q4k` (`BEST FOR CODING`)
- **Strengths**: Top HumanEval score for Kotlin, Java, and Python. Specialized Fill-in-the-Middle (FIM) code completion and AST refactoring.
- **Alternative**: `deepseek-coder-7b-q4k` for complex architectural design and mathematical logic.

### B. General Reasoning & Agent Problem-Solving
- **Recommended**: `deepseek-coder-7b-q4k` (`BEST FOR REASONING`)
- **Strengths**: Excels at multi-step chain-of-thought, algorithmic problem decomposition, and unit test generation.
- **Alternative**: `mistral-7b-instruct-q4k`.

### C. Low-RAM & Energy-Constrained Devices
- **Recommended**: `smollm2-1.7b-q8` (`BEST FOR LOW-RAM`)
- **Strengths**: 1.7GB memory footprint, rapid cold-load time (<200ms), low thermal signature, ideal for continuous background execution.
- **Alternative**: `qwen2.5-coder-3b-q4k`.

### D. Long-Context & Repository-Wide Analysis
- **Recommended**: `mistral-7b-instruct-q4k` (`BEST FOR LONG CONTEXT`)
- **Strengths**: Native 32k sliding-window context capacity enables cross-referencing multi-file codebases and extensive API documentation.
- **Alternative**: `deepseek-coder-7b-q4k`.

### E. Technical Writing & Documentation
- **Recommended**: `gemma-2-2b-f16` (`BEST FOR DOCUMENTATION`)
- **Strengths**: Highly articulate explanations, concise commit messages, and structured markdown synthesis.
- **Alternative**: `smollm2-1.7b-q8`.
