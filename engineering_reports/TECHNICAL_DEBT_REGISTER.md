# AETHER AI — Technical Debt & Architectural Risk Register

## 1. Identified Technical Debt & Mitigation Tracker

| ID | Component Area | Issue Description | Architectural Risk | Remediation Action | Status |
|---|---|---|---|---|---|
| **TD-01** | `InferenceBackend.kt` | Hardcoded list of 4 model descriptors; lacks discovery from public repositories. | Users cannot discover or download new GGUF models. | Implement `ModelDiscoveryProvider` with HuggingFace, Ollama, and GGUF adapters. | **IN PROGRESS** |
| **TD-02** | `WorkstationViewModel.kt` | Monolithic responsibilities: manages UI state, database observation, terminal, editor, and models in a single 460-line class. | Maintainability risk and high coupling. | Introduce dedicated domain managers: `ModelHubManager`, `GitRepositoryManager`, `CloudStorageManager`. | **IN PROGRESS** |
| **TD-03** | `MinimalGitScreen.kt` | Git operations only invoke raw `git status`; no UI for remote repository clone, branch management, or commits. | Restricts workstation to local-only files; cannot clone GitHub projects. | Implement `GitRepositoryManager` with remote clone, branch list, commit actions, and diff viewers. | **IN PROGRESS** |
| **TD-04** | Project Importer | No support for Google Drive or local ZIP archives. | User cannot import external codebases without manually placing files in app storage. | Implement `CloudStorageManager` supporting Google Drive API, SAF, and secure ZIP extraction with Zip-Slip defense. | **IN PROGRESS** |
| **TD-05** | Agent Tooling | `SupervisorOrchestrator` lacks Git tools, structural analysis tools, and multi-file patching tools. | Agent cannot autonomously inspect repo structures or stage Git commits. | Register `git_status`, `git_commit`, `git_diff`, `inspect_structure`, and `patch_file` tools. | **IN PROGRESS** |
| **TD-06** | Key Storage | Cloud API keys (Gemini, Claude, OpenAI) were passed as in-memory constructor strings. | Plaintext key leakage risk in memory dumps. | Store API keys and GitHub PATs using Android Keystore AES-256-GCM authenticated encryption. | **IN PROGRESS** |

---

## 2. Security & Guardrail Debt Analysis
- **Path Traversal / Zip-Slip**: Any archive extraction must validate that target entries resolve strictly within the canonical workspace boundary:
  ```kotlin
  val destFile = File(workspaceRoot, entry.name)
  if (!destFile.canonicalPath.startsWith(workspaceRoot.canonicalPath + File.separator)) {
      throw SecurityException("Zip-Slip path traversal attempt detected: ${entry.name}")
  }
  ```
- **Shell Command Sanitization**: All commands passed to `SandboxedTerminalSession` must continue to pass through `CommandGuardrailEngine` allowlists and dangerous pattern regex checks.
