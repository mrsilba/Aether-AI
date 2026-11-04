# AETHER AI v3.2 — Terminal Execution Security & Agent Boundary Audit

**Audit Date**: 2026-11-04  
**Auditor**: Senior Application Security Engineer & Systems Auditor  
**Status**: `VERIFIED & HARDENED (Restricted Terminal Policy & Agent Boundaries)`  

---

## 1. Operating System Reality Check & Security Labeling

In standard Android mobile operating environments, non-root applications cannot instantiate Linux user namespaces, PID namespaces, network namespaces, or mount cgroups. Any process spawned via `ProcessBuilder` or `/system/bin/sh` executes under the **same Linux UID** and security context as the host Android application.

Therefore, claiming that commands run in an "OS Sandbox" is technically inaccurate. AETHER AI v3.2 explicitly re-designates this subsystem as a **RESTRICTED TERMINAL** and documents the precise defensive controls applied.

---

## 2. Threat Model & Evaluated Attack Vectors

| Attack Vector | Evaluated Scenario | Defensive Mechanism | Test Status |
| :--- | :--- | :--- | :--- |
| **Path Traversal via Argument** | `cat ../../../data/data/...` | `hasEscapedPathArgument` detects canonical target escaping `workspaceRoot` | `VERIFIED` |
| **Symlink Escape** | Symlink inside workspace pointing to `/system` or `/data` | `verifyWorkspaceConfinement` inspects `canonicalFile.toPath()` | `VERIFIED` |
| **Quoted Command Misparsing** | `git commit -m "feat: login"` splitting on space | `ShellCommandTokenizer` parses quotes (`'...'`, `"..."`) and escapes | `VERIFIED` |
| **Fork Bombs & Resource Exhaustion** | `:(){ :|:& };:` or runaway fork loops | `CommandGuardrailEngine` pattern matcher blocks fork bomb before execution | `VERIFIED` |
| **Destructive Root Operations** | `rm -rf /` or `chmod 777 /` | Denied by guardrail pattern rules | `VERIFIED` |
| **Environment Variable Leakage** | Child process inspecting parent environment | `pb.environment()` stripped of all `KEY`, `TOKEN`, `SECRET`, `PASSWORD` | `VERIFIED` |
| **Infinite Output DoS** | `yes` or runaway streaming output | Truncation cap enforced at `256 KB` per stdout/stderr stream | `VERIFIED` |
| **Runaway Process Hang** | Hanging network sockets or unkillable sleep | 30-second watchdog invokes `process.destroyForcibly()` | `VERIFIED` |
| **Credential Echoing in Terminal Logs** | Exposing API keys or PATs in stdout | `SecretScanner.redactSecrets` masks `ghp_...`, `AIzaSy...`, `sk-...` | `VERIFIED` |

---

## 3. Agent Tool Permission Boundaries

To ensure that the autonomous ReAct agent cannot bypass terminal restrictions through alternative tools, `SupervisorOrchestrator.kt` establishes three strict tool security categories:

### 3.1 Tool Categorization:
1. **`READ_ONLY`**:
   - Tools: `read_file`, `search_code`, `git_status`, `git_diff`, `audit_project`.
   - Cannot mutate filesystem or state; auto-approved.
2. **`CONTROLLED_WRITE`**:
   - Tools: `write_file`, `create_file`, `edit_file`.
   - Modifies workspace files; requires canonical boundary verification.
3. **`SENSITIVE`**:
   - Tools: `execute_terminal`, `delete_file`.
   - High-impact operations; requires explicit confirmation and audit logging.

### 3.2 Canonical Boundary Enforcement on Tool Execution:
Every tool accessing the filesystem must resolve paths via:
```kotlin
fun resolveConfinedFile(relPath: String): Result<File> {
    val file = File(workspaceRoot, relPath)
    val rootCanon = workspaceRoot.canonicalFile
    val fileCanon = file.canonicalFile
    if (fileCanon == rootCanon || fileCanon.toPath().startsWith(rootCanon.toPath())) {
        return Result.success(file)
    }
    return Result.failure(SecurityException("Tool boundary violation: path '$relPath' escapes workspace root."))
}
```
Automated test `testAgentToolBoundaryConfinementRejectsTraversal` confirms that attempts to pass `../../../../etc/passwd` or outside paths are intercepted and throw `SecurityException`.

---

## 4. Audit Conclusion

The terminal execution engine and agent toolchain now provide verified defense-in-depth within standard Android capabilities, with no false security claims.
