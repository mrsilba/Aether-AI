# AETHER AI v3.2 — Known Limitations & External Blockers

**Audit Date**: 2026-11-04  
**Auditor**: Senior Systems Architect & Security Engineer  
**Status**: `AUTHENTIC & DOCUMENTED (Zero Simulated Success)`  

---

## 1. Executive Disclosure

In accordance with Non-Negotiable Rules 1, 2, and 10, AETHER AI strictly distinguishes implementation completeness from runtime verification. Where physical device silicon, external credentials, or third-party cloud configurations are required, they are documented below as `BLOCKED` or `PARTIALLY VERIFIED`.

---

## 2. Itemized Limitation & Blocker Register

| ID | Domain | Specific Limitation / Blocker | Current Architecture State | User Action / Prerequisite Required |
| :--- | :--- | :--- | :--- | :--- |
| **BLK-01** | **Physical GGUF Device Benchmark** | Physical device benchmarks (TTFT, tokens/sec, thermals) cannot be measured inside headless build containers. | GGUF v3 parser, quantization dequantizers, memory calculus, and JNI bindings are complete and tested. | Deploy APK to physical Android flagship device (e.g. Snapdragon 8 Gen 2/3) and execute benchmark suite. |
| **BLK-02** | **Android NDK Compilation** | Cloud build environment container omits Android NDK toolchain (`/opt/android-sdk/ndk/*`). | CMake and C++ sources (`aether_llama_jni.cpp`) are complete. Kotlin bridge operates via safe dynamic resolution and vector fallback. | Build locally using Android Studio with NDK `r26+` installed to produce precompiled `.so` binaries. |
| **BLK-03** | **Google Drive Live OAuth** | Live OAuth token exchange against Google Identity servers requires a registered OAuth Client ID. | `GoogleDriveApiClient` (REST v3) and `GoogleDriveOAuthManager` (AES-256-GCM token vault) are fully implemented. | Configure Google Cloud Console project, enable Drive API, register SHA-1 and package name `com.aistudio.aetherai.kqwzrt`. |
| **BLK-04** | **Cloud Provider API Keys** | Live generation via Gemini, Claude, OpenAI, DeepSeek, or OpenRouter requires valid API keys. | Real SSE streaming parsers and cost calculators are implemented; keys are hardware-encrypted. | Enter personal API keys in the Workstation Settings screen. |
| **BLK-05** | **Android Host Git Binary** | Consumer Android OS images (Google Pixel, Samsung OneUI) omit `/system/bin/git`. | `GitRepositoryManager` executes `git` via terminal session. Repositories can also be opened via SAF or imported as ZIP. | Use Termux/proot environment or import projects via Android Storage Access Framework (SAF) / ZIP extractor. |
| **BLK-06** | **OS-Level Process Isolation** | Android does not provide unprivileged access to Linux user namespaces, PID namespaces, or cgroups. | Enforces strict application-level boundary defense (canonical path checks, symlink traversal blocks, allowlists, 30s timeout). | Acknowledge that terminal operates as a **Restricted Terminal**, not a kernel-isolated container. |
