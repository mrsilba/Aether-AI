# AETHER AI v1.0.0 — First Usable Release

## Release Overview
- **Application Name**: AETHER AI
- **Release Version**: 1.0.0
- **Version Code**: 100
- **Git Tag**: `v1.0.0`
- **Release Channel**: Public-to-Owner First Release
- **Target Architecture**: Android 14+ (ARM64-v8a / x86_64), Samsung Galaxy S22 Ultra (Exynos/Snapdragon)
- **Design Paradigm**: Minimal Luxury Dark Aesthetic (`#0C0E14` base, `#141722` surface, `#6366F1` accent)

---

## What's New in v1.0.0

### 1. Production UI & Interaction Model (Apple / Cursor Minimalist Aesthetic)
- Complete purge of legacy 3D graphics, spinning geometries, and animated canvas loops.
- Replaced with high-efficiency Jetpack Compose Minimal Luxury components.
- Direct 8-module navigation:
  1. **Home / Dashboard**: Contextual AI composer, active hardware monitor, quick module launch.
  2. **AI Assistant / ReAct Agent**: 9-phase autonomous reasoning loop with structured tool permission classification (`READ_ONLY`, `CONTROLLED_WRITE`, `SENSITIVE`).
  3. **Projects & Code Editor**: Multi-tab code editor with symbol extraction, line diff patch review, and atomic file persistence.
  4. **Models Hub**: GGUF local model discovery and compatibility tiers.
  5. **Sandboxed Terminal**: ProcessBuilder execution with shell tokenization, argument quoting, path boundary confinement, and 256KB output safety buffers.
  6. **Git Source Control**: Remote repository cloning, branch switching, stage-all, and commit operations.
  7. **Cloud & Storage Integrations**: Google Drive v3 REST API browsing and atomic binary downloads, paired with Android Storage Access Framework (SAF).
  8. **Settings & Credential Vault**: Android Keystore hardware-backed AES-256-GCM encryption for cloud AI provider API keys (Gemini, Claude, OpenAI, DeepSeek, Groq) and OAuth tokens.

### 2. Google Drive v3 REST API & Storage Integration
- Zero-mock implementation using official Google Drive v3 REST endpoints.
- Paginated folder listing and search queries (`'root' in parents and trashed = false`).
- Atomic binary streaming download via temporary `.part` files with rename-on-completion.
- Automatic MIME-type translation for Google Docs to Markdown/Plaintext.
- Android Keystore AES-256-GCM token encryption with least-privilege scopes (`drive.file`, `drive.readonly`).

### 3. Truthful Local & Cloud Inference Engine
- Native dual-loader for `aether_llama_jni` and `aether_llama` C++ runtimes.
- Truthful state reporting: reports `activeLoadedModel = null` ("No Model Loaded") when GGUF model files are absent on device storage.
- Adaptive routing between local ARM NEON CPU inference and cloud AI providers.

### 4. Zero Demo & Mock Code
- Purged all hardcoded project names (`Quantum-Dev`), fake conversation streams, simulated metrics, and mock progress bars.
- Live file explorer inspecting the real sandboxed workspace (`filesDir/aether_workspace`).
- Real process execution in terminal session with true exit codes and stdout/stderr capture.

---

## Checksums and Verification
- **Package ID**: `com.aistudio.aetherai.kqwzrt`
- **Minimum SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 35 (Android 15 Vanilla Ice Cream)
- **Release Archive**: `release/AETHER_AI_v1.0.0_RELEASE.zip`
- **CI/CD Pipeline**: `.github/workflows/android-release.yml`
