# AETHER AI v3.2 — Initial Source Code & Architectural Audit

**Audit Date**: 2026-11-04  
**Auditor**: Senior Android Systems Engineer, Native AI Runtime Engineer, Application Security Engineer  
**Scope**: Complete repository baseline examination prior to v3.2 gap remediation  
**Repository State**: v3.1 Audited Baseline (Clean working tree)  

---

## 1. Executive Baseline Summary

A forensic review of the repository was conducted across all subsystems: native C++ source files, JNI bridges, local model lifecycle managers, cloud AI providers, Git controllers, storage and archive extractors, terminal sessions, and the ReAct autonomous agent orchestrator.

### Core Metrics:
- **Programming Languages**: Kotlin (100% Compose UI and domain architecture), C++17 (Native GGUF parser & transformer routines).
- **Target SDK**: Android API 36 (Android 15+ compatible).
- **Min SDK**: API 24 (Android 7.0+).
- **Current Verified Automated Tests**: 33 tests across `AetherWorkstationTestSuite.kt`, `ExampleRobolectricTest.kt`, and `ExampleUnitTest.kt` (100% pass rate).
- **Active UI**: Minimal Luxury Design System (`MinimalWorkstationScreen.kt` and `MinimalScreens.kt`). Legacy 3D code completely purged.

---

## 2. Subsystem-by-Subsystem Implementation & Gap Analysis

### 2.1 Local Inference Engine (`LlamaCppNativeBridge.kt`, `aether_llama_jni.cpp`, `InferenceBackend.kt`)
- **Actual Status**: `PARTIALLY VERIFIED`
- **Existing Implementation**:
  - `aether_llama_jni.cpp` (1,068 lines) contains an end-to-end GGUF v3 header and tensor parser supporting types `F32`, `F16`, `Q4_0`, `Q8_0`, `Q4_K`, `Q6_K`, plus an attention forward pass (GQA, RoPE, RMSNorm, SwiGLU).
  - `LlamaCppNativeBridge.kt` binds to native JNI calls (`nativeLoadModel`, `nativeGenerateStream`, `nativeClearKvCache`, `nativeCancel`, `nativeUnloadModel`).
  - `LocalGgufCpuBackend.kt` verifies binary GGUF header magic bytes (`GGUF` = `0x47 0x47 0x55 0x46`).
- **Gaps Identified**:
  - The build environment container does not contain an Android NDK toolchain (`/opt/android-sdk/ndk/*` missing); hence native `.so` binaries (`libaether_llama_jni.so`) are not compiled during cloud CI builds.
  - While `LocalGgufCpuBackend` falls back gracefully to high-performance vector token generation, it lacks the formal 9-state operational lifecycle required by Task A3 (`NOT_DOWNLOADED`, `DOWNLOADING`, `VERIFYING`, `READY`, `LOADING`, `LOADED`, `GENERATING`, `UNLOADING`, `ERROR`).
  - No physical multi-gigabyte GGUF model is bundled in the repository (by design, mobile APKs cannot package 2GB weights).

### 2.2 Google Drive Integration (`CloudStorageManager.kt`)
- **Actual Status**: `PARTIALLY VERIFIED / BLOCKED on GCP Credentials`
- **Existing Implementation**:
  - `CloudStorageManager.kt` implements Android Storage Access Framework (SAF) URI importing and direct HTTP/HTTPS URL streaming with Bearer token authentication.
  - `ZipArchiveExtractor.kt` implements canonical path validation preventing Zip-Slip exploits.
- **Gaps Identified**:
  - No dedicated Google Drive API v3 client (`https://www.googleapis.com/drive/v3/files`).
  - No Google OAuth 2.0 lifecycle manager (account selection, token refresh, scope authorization `https://www.googleapis.com/auth/drive.file`, token revocation).
  - External configuration (Google Cloud Project, OAuth Client ID, SHA-1 fingerprint) is required for live testing.

### 2.3 Terminal & Process Execution (`SandboxedTerminalSession.kt`, `CommandGuardrailEngine.kt`)
- **Actual Status**: `RESTRICTED TERMINAL (Not OS Sandbox)`
- **Existing Implementation**:
  - `CommandGuardrailEngine.kt` uses an allowlist of 27 development binaries and blocks patterns like `rm -rf /`, `:(){ :|:& };:`, and `/system/`.
  - `SandboxedTerminalSession.kt` restricts directory to `workspaceRoot` and enforces a 30s timeout.
- **Gaps Identified**:
  - The command line is naively split using `commandLine.trim().split(Regex("""\s+"""))`, which breaks quoted arguments like `git commit -m "my commit"` or `grep "fun main" .`.
  - Process runs under the application's ordinary Linux UID without cgroups, seccomp filters, or Linux user namespaces (which are unavailable to standard non-root Android apps).
  - Relies on string filtering rather than symlink-aware path inspection.
  - Terminology must be updated from "SANDBOXED" to "RESTRICTED TERMINAL" with transparent documentation of boundaries.

### 2.4 Autonomous ReAct Agent Security (`SupervisorOrchestrator.kt`)
- **Actual Status**: `FUNCTIONAL / SECURITY GAPS DETECTED`
- **Existing Implementation**:
  - Implements 9-step ReAct loop with tools (`read_file`, `write_file`, `execute_terminal`, `audit_project`, `search_code`, `git_status`, `git_diff`).
- **Gaps Identified**:
  - `read_file` and `write_file` directly construct `File(workspaceRoot, relPath)` without verifying that `canonicalPath` remains inside `workspaceRoot`. A malicious agent step could pass `../../../../data` and bypass terminal guardrails!
  - Tools are not formally partitioned into `READ_ONLY`, `CONTROLLED_WRITE`, and `SENSITIVE` categories with execution-boundary permission enforcement.

---

## 3. Immediate v3.2 Remediation Plan

1. **Phase 2 — Real On-Device Local Inference**:
   - Create `ModelExecutionState` tracking all 9 required states.
   - Refine `LlamaCppNativeBridge.kt` and `LocalGgufCpuBackend.kt` with explicit memory budgets, configurable context length, and clean unload/reload cycles.
   - Document the exact reference model: SmolLM2 1.7B Instruct GGUF (`Q4_K_M`).
2. **Phase 3 — Google Drive OAuth & Drive API v3**:
   - Implement `GoogleDriveApiClient.kt` for Drive v3 REST endpoints (list, nested search, pagination, binary download, Google Docs export).
   - Implement `GoogleDriveOAuthManager.kt` managing authentication state, token refresh, and revocation.
   - Author a complete Google Cloud Console setup guide.
3. **Phase 4 — Restricted Terminal & Agent Tool Guardrails**:
   - Implement shell argument tokenizer preserving quoted strings.
   - Implement symlink detection and canonical path verification on all terminal and agent operations.
   - Implement tool permission classification (`READ_ONLY`, `CONTROLLED_WRITE`, `SENSITIVE`) in `SupervisorOrchestrator.kt`.
4. **Phase 5 — Test Suite Expansion**:
   - Add unit tests covering shell argument parsing, symlink traversal blocking, agent tool boundary enforcement, GGUF state machine, and Drive API client URL generation.
5. **Phase 6 — Release Documentation & Verification**:
   - Generate all 9 v3.2 engineering reports and package release archive.
