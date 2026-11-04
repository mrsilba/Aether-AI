# AETHER AI v3.0 — Terminal & Execution Security Audit

**Audit Date**: 2026-11-04  
**Subject**: Sandboxed Process Execution, Command Guardrails, Boundary Defense, and Secret Masking  
**Auditor**: Senior Application Security Engineer & Systems Auditor  

---

## 1. Threat Model & Confinement Architecture

Allowing arbitrary terminal command execution within an Android application creates severe attack vectors if unmitigated:
1. **Host Traversal & Data Exfiltration**: Attempting to read files in other apps' sandboxes (`/data/data/`) or system partitions (`/system`, `/vendor`).
2. **Denial of Service (DoS)**: Spawning fork bombs, recursive infinite loops, or unkillable background daemon processes.
3. **Destructive System Operations**: Deleting critical partitions or executing privilege escalation exploits.
4. **Credential Leakage**: Echoing API tokens, passwords, or private keys to terminal logs.

To mitigate these threats, AETHER AI implements a multi-layer defense-in-depth security boundary:

```
User Command / Agent Invocation
               │
               ▼
┌──────────────────────────────┐
│  CommandGuardrailEngine      │ ──► Denies fork bombs, root deletion, system paths
└──────────────┬───────────────┘
               │ (Allowed)
               ▼
┌──────────────────────────────┐
│  Canonical Path Verifier     │ ──► Verifies target directory is within workspaceRoot
└──────────────┬───────────────┘
               │ (Confined)
               ▼
┌──────────────────────────────┐
│  ProcessBuilder Execution    │ ──► Enforces 30s timeout, non-interactive env
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  SecretScanner Redactor      │ ──► Masks ghp_*, AIzaSy*, sk-*, Bearer tokens
└──────────────┬───────────────┘
               │
               ▼
Terminal UI / Log Output
```

---

## 2. Guardrail Rules & Test Verification

`CommandGuardrailEngine.kt` evaluates commands against an explicit policy matrix:

### 2.1 Destructive Pattern Defense
The following patterns are categorically intercepted and blocked before process instantiation:
- **Root / Partition Deletion**: `rm -rf /`, `rm -rf /*`, `rm -rf /system`, `rm -rf /data`
- **Fork Bombs & Resource Exhaustion**: `:(){ :|:& };:`, `perl -e "fork while fork"`
- **System Partition Modifications**: `chmod 777 /`, `mount -o remount,rw /system`
- **Sensitive Kernel & System File Probing**: `cat /system/build.prop`, `cat /proc/kallsyms`, `cat /data/system/users/0/accounts.db`

### 2.2 Allowed Development Commands
Legitimate software engineering workflows are permitted within the workspace root:
- `git` (status, diff, clone, commit, branch, log)
- Directory inspection: `ls`, `pwd`, `tree`, `find`
- File inspection & text processing: `cat`, `grep`, `head`, `tail`, `wc`, `sed`, `awk`
- Project builds & runners: `gradle`, `python3`, `node` (when present in host environment)

### 2.3 Automated Test Evidence
Verified in test suite `AetherWorkstationTestSuite.kt`:
- `testCommandGuardrailAllowsSafeDevCommands`: `PASSED`
- `testCommandGuardrailBlocksDestructiveOperations`: `PASSED`
- `testTerminalWorkspaceConfinementPreventsPathTraversal`: `PASSED`

---

## 3. Boundary Confinement & Path Sanitization

The working directory of every spawned process is constrained via `ProcessBuilder.directory(workspaceRoot)`.

Before any relative path argument is expanded:
```kotlin
fun verifyWorkspaceConfinement(target: File): Boolean {
    val canonicalRoot = workspaceRoot.canonicalPath
    val canonicalTarget = target.canonicalPath
    return canonicalTarget == canonicalRoot || canonicalTarget.startsWith(canonicalRoot + File.separator)
}
```
If `verifyWorkspaceConfinement` returns `false`, execution is aborted with an access violation error.

---

## 4. Execution Timeout & Cancellation

1. **30-Second Execution Watchdog**:
   - Long-running commands are terminated after 30,000 ms if they fail to produce an exit code.
2. **Forcible Process Termination**:
   - When the user taps the Stop button or cancels the coroutine, `process.destroyForcibly()` is invoked, ensuring no orphaned processes drain battery or CPU cycles in the background.

---

## 5. Secret Scrubbing & Data Sanitation

All combined stdout and stderr streams pass through `SecretScanner.redactSecrets()`.
Patterns matching GitHub PATs (`ghp_[a-zA-Z0-9]{36}`), Google Cloud Keys (`AIzaSy[a-zA-Z0-9_-]{33}`), and OpenAI Keys (`sk-[a-zA-Z0-9]{20,}`) are replaced with `[REDACTED]`.

Verified by unit test `testSecretScannerRedaction` (`PASSED`).

---

## 6. Audit Verdict

- **Confinement & Boundary Defense**: `VERIFIED`
- **Guardrail Engine**: `VERIFIED`
- **Secret Redaction**: `VERIFIED`
- **Execution Watchdog & Cleanup**: `VERIFIED`
