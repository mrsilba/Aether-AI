# AETHER AI v3.0 — GitHub & Git Subsystem End-to-End Audit

**Audit Date**: 2026-11-04  
**Subject**: Git CLI Orchestration, Secure PAT Authentication, Differential Parsing, and Confinement  
**Auditor**: Senior DevOps & Platform Engineer  

---

## 1. System Overview

`GitRepositoryManager.kt` connects the AETHER AI development workstation with Git version control. It executes real Git operations inside isolated workspace directories via the `SandboxedTerminalSession`.

Unlike mock developer tools that simulate Git repositories with in-memory JSON state, AETHER AI interacts with genuine `.git` directory trees, tracking real blobs, trees, commit objects, and index refs.

---

## 2. Tested Workflows & Verification

### 2.1 Cloning Repositories
- **Command Structure**: `git clone --depth 1 <url> <targetDir>`
- **Private Repository Support**: When the user provides a GitHub Personal Access Token (PAT), the manager constructs an authenticated URL:
  `https://x-access-token:<PAT>@github.com/<owner>/<repo>.git`
- **Credential Scrubbing Guarantee**: The URL containing the PAT is immediately sanitized by `SecretScanner.redactSecrets()` before any terminal log event or stdout line is dispatched to the UI. The token is never echoed back to the UI.

### 2.2 Branch Management & Commit Operations
- **Branch Listing**: `git branch --all` parsed into local and remote branch descriptors.
- **Branch Creation / Checkout**: `git checkout -b <name>` or `git checkout <name>`.
- **Staging & Commit**:
  1. `git add -A`
  2. `git commit -m "<message>"`
  - Automatically captures author details configured in workstation preferences.

### 2.3 Status & Differential Inspection
- **Working Tree Status**: `git status --porcelain` is parsed into structured status entries:
  - `M`: Modified
  - `A`: Added / Staged
  - `D`: Deleted
  - `??`: Untracked
- **Unified Diff**: `git diff HEAD` outputs standard diff chunks rendered by `CodeEditorController` with colorized additions (`+`), deletions (`-`), and context lines.

### 2.4 Remote Synchronization (Pull & Push)
- **Pull**: `git pull --rebase origin <branch>`
- **Push**: `git push origin <branch>`
- Output streams directly to the terminal panel with exit code telemetry.

---

## 3. Project Structure Intelligence

When a repository is cloned or opened, `analyzeProjectStructure()` automatically inspects configuration files:
- `build.gradle.kts` / `settings.gradle.kts` $\rightarrow$ Android / Kotlin Multiplatform (Gradle Kotlin DSL)
- `pom.xml` $\rightarrow$ Java / Maven
- `package.json` $\rightarrow$ TypeScript / JavaScript (Node.js)
- `requirements.txt` / `pyproject.toml` $\rightarrow$ Python
- `Cargo.toml` $\rightarrow$ Rust (Cargo)
- `CMakeLists.txt` $\rightarrow$ C / C++ (CMake)

This detection was verified under automated test `testProjectStructureAnalyzerDetectsGradleAndroid`.

---

## 4. Execution Boundary & Android Environment Considerations

1. **Host Dependency**:
   - In containerized environments or Android systems equipped with Termux/proot environments, `/usr/bin/git` or `/data/data/.../git` executes natively.
   - On unrooted Android consumer devices where the vendor does not bundle a standalone `/system/bin/git` binary, executing Git CLI commands produces a standard standard error (`git: not found`). In this case, AETHER AI falls back to direct filesystem manipulation, allowing users to import code via ZIP or SAF.
2. **Confinement**:
   - Every Git command has its working directory strictly bound to `workspaceRoot.canonicalPath`. Any attempt to execute commands targeting parent paths (`/system`, `/data`) is blocked by `CommandGuardrailEngine`.

---

## 5. Audit Verdict

- **Git Command Construction & Execution**: `VERIFIED` (Linux container) / `UNIT TESTED ONLY` (Physical Android device without preloaded binary)
- **PAT Token Masking & Redaction**: `VERIFIED`
- **Diff Parsing & Working Tree Tracking**: `VERIFIED`
- **Project Structure AST Detection**: `VERIFIED`
