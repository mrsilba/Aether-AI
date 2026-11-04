# SECURITY AUDIT & THREAT MODEL REPORT
**Project**: AETHER AI Android Engineering Workstation  
**Author**: Lead Application Security Engineer  
**Standard**: OWASP Mobile Top 10 & Android Security Hardening  
**Date**: November 2026  
**Status**: AUDITED & SECURED  

---

## 1. Threat Model & Security Posture

AETHER AI executes local terminal commands, indexes project files, manages API credentials, and coordinates autonomous agent tool invocation. It operates under a **Zero-Trust Input Model**: project files, model outputs, terminal outputs, and external HTTP payloads are treated as untrusted data.

---

## 2. Security Subsystems & Controls

### 2.1 Workspace Confinement & Directory Traversal Protection
- **Mechanism**: `SandboxedTerminalSession.verifyWorkspaceConfinement(targetDir)`
- **Implementation**: Normalizes `workingDirectory` and `workspaceRoot` to their canonical paths via `.canonicalPath`.
- **Defense**: Prevents path traversal attacks (e.g., `../../../../system/bin` or symbolic link poisoning) outside the allocated project directory.

### 2.2 Shell Command Guardrail Engine
- **Mechanism**: `CommandGuardrailEngine.evaluate(commandLine)`
- **Policy**:
  - **Allowlisted Development Binaries**: `git`, `ls`, `pwd`, `cat`, `echo`, `grep`, `find`, `head`, `tail`, `wc`, `diff`, `mkdir`, `rm`, `cp`, `mv`, `touch`, `python3`, `kotlinc`, `javac`, `gradle`, `curl`, `tar`, `gzip`, `unzip`.
  - **Hard-Blocked Patterns**: `rm -rf /`, `chmod 777 /`, `mkfs`, fork bombs (`:(){ :|:& };:`), `/system/`, `/data/data/` access outside own package.
  - **Explicit User Confirmation**: File deletion (`rm`) and move (`mv`) trigger mandatory user confirmation gates.

### 2.3 Process Environment Scrubbing
- **Mechanism**: Child process sanitization before `ProcessBuilder.start()`
- **Policy**: Scans `ProcessBuilder.environment()` and removes all keys matching `*KEY*`, `*TOKEN*`, `*SECRET*`, `*PASSWORD*`. Child shell processes cannot inspect Android host app environment secrets.

### 2.4 Server-Side Request Forgery (SSRF) & DNS Rebinding Defense
- **Mechanism**: `NetworkSecurityValidator.validateUrl(url)`
- **Policy**:
  - Enforces HTTPS only; cleartext HTTP is strictly rejected.
  - Resolves hostnames via `InetAddress.getByName` and verifies that the destination is neither loopback (`127.0.0.1`, `::1`), site-local (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), nor link-local (`169.254.0.0/16`).

### 2.5 Hardware-Backed Cryptography & Key Management
- **Mechanism**: `KeystoreEncryptionManager`
- **Implementation**: Generates AES-256 keys inside `AndroidKeyStore` with `KeyGenParameterSpec` (`PURPOSE_ENCRYPT | PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`, `PADDING_NONE`).
- **Policy**: Raw database encryption keys are never stored in plaintext on disk or in shared preferences. Key material is backed by TEE (Trusted Execution Environment) or StrongBox Keymaster where available.

### 2.6 Live Secret Redaction
- **Mechanism**: `SecretScanner.redactSecrets(input)`
- **Coverage**: GitHub tokens (`ghp_*`), Google API keys (`AIza*`), OpenAI keys (`sk-*`), Bearer tokens, and generic password assignments in logs and terminal outputs are automatically masked with `...[REDACTED]`.
