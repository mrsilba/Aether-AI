# AETHER AI — Architecture Improvement Roadmap

## 1. Overview & Strategy
This roadmap outlines the incremental, low-risk evolution of AETHER AI from its current state into a full-scale AI development workstation on Android. The strategy prioritizes foundation security, followed by the Model Hub, Git/External Integrations, Agent Evolution, and unified UI polish.

---

## 2. Multi-Phase Implementation Plan

```
[Phase 1: Foundation Audit & Technical Debt Registration]
      │ (Completed)
      ▼
[Phase 2: Unified Model Hub & Extensible Provider Adapters]
      ├── Provider Adapters (Hugging Face, GGUF Repositories, Ollama, GitHub Assets)
      ├── Resumable HTTP Range Downloader with SHA-256 Checksum Validation
      └── Dynamic Compatibility Assessment Engine (RAM, KV Cache, Compute Tier)
      │
      ▼
[Phase 3: Task-Based Model Recommendation Engine]
      ├── Task Taxonomies: Coding, Reasoning, Long-Context, Writing, Specialized
      ├── Ranked Candidates (Qwen2.5-Coder, DeepSeek-Coder, Llama-3.2, Mistral, Gemma 2)
      └── Objective Badge Engine (BEST FOR CODING, BEST OVERALL, LOW-RAM)
      │
      ▼
[Phase 4: Local Runtime Orchestration & Multi-Model Switching]
      ├── Local GGUF Engine Lifecycle (Load, Inference Stream, Cancel, Unload)
      └── Intelligent Context Management and Memory Safeguards
      │
      ▼
[Phase 5: Multi-Provider Cloud AI Integration]
      ├── Unified Cloud Provider Interface (Gemini, Claude, OpenAI, DeepSeek, OpenRouter)
      ├── Token Counter, Streaming SSE Parser, Rate Limit Backoff
      └── Secure Keystore AES-256-GCM Credential Vault
      │
      ▼
[Phase 6: GitHub Integration & Git Repository Manager]
      ├── Clone (Public/Private with PAT), Status, Diff, Branch, Commit, Push/Pull
      └── Repository Map & Project Structure Analyzer (Gradle, Node, Python)
      │
      ▼
[Phase 7: Cloud Storage & Unified External File Import]
      ├── Google Drive OAuth / File Browser / Download Stream
      ├── Android Storage Access Framework (SAF)
      └── Safe Archive Unpacker (Zip-Slip Path Traversal Protection)
      │
      ▼
[Phase 8: Agent Supercharging & Tool Registry Expansion]
      ├── Git Tools (`git_status`, `git_commit`, `git_diff`, `inspect_structure`)
      ├── Permission Tiers (Read-Only vs Modification vs Sensitive Execution)
      └── Automated Multi-File Patching and Verification Loop
      │
      ▼
[Phase 9: Minimal Luxury UI Integration & Polish]
      ├── Enhanced Model Hub (Discover, Download Progress, Filter, Compatibility Badges)
      ├── Enhanced Git Screen (Clone Dialog, Branch Switcher, Diff Viewer)
      ├── Enhanced Workspace & Files (Project Map, External Import Dialog)
      └── Enhanced Settings (API Keys, PAT Tokens, Hardware Overrides)
      │
      ▼
[Phase 10: Verification, Testing & Production Release]
      ├── Robolectric & Unit Test Suite (`gradle testDebugUnitTest`)
      ├── AGP Compilation Verification (`compile_applet`)
      └── Final Documentation Reports & Production Release Package
```

---

## 3. Risk Mitigation & Rollback Controls
1. **Zero Breaking Changes**: Retain existing public APIs and ViewModel state contracts; layer new capabilities cleanly into dedicated domain packages (`com.aether.model`, `com.aether.git`, `com.aether.storage`).
2. **Path Confinement**: All file operations (Git clone, zip extract, file edit) are strictly confined to `context.filesDir/aether_workspace` with canonical path verification to prevent path traversal vulnerabilities.
3. **Keystore Integrity**: User API keys and GitHub tokens are encrypted with hardware-backed AES-256-GCM and never logged or exposed in plain text.
