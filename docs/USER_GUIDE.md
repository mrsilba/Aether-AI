# AETHER AI v1.0.0 — User & Operator Guide

Welcome to **AETHER AI**, the autonomous spatial developer workstation engineered for Android.

## 1. System Navigation & Architecture

AETHER AI provides 8 dedicated workstation destinations:

1. **Dashboard (Home)**:
   - Primary AI composer input for contextual reasoning.
   - Real-time device telemetry: free RAM, CPU/RAM utilization, and compute tier.
   - Quick launcher modules for rapid access to all workstation hubs.

2. **AI Assistant (ReAct Supervisor Agent)**:
   - Full 9-phase autonomous ReAct (Reasoning + Acting) loop.
   - Transparent tool execution log showing observations, reasoning, and actions.
   - Tool permission classifier (`READ_ONLY`, `CONTROLLED_WRITE`, `SENSITIVE`).

3. **Code Editor**:
   - Multi-tab file editor with syntax rendering.
   - Symbol extraction (functions, classes, variables).
   - Side-by-side / unified diff patch review with atomic file saving.

4. **Models Hub**:
   - Local GGUF discovery and compatibility tier evaluation.
   - Quantization profiles (Q4_K, Q8_0, etc.) evaluated against device RAM.
   - Direct download management and storage directory inspector.

5. **Sandboxed Terminal**:
   - Interactive Linux shell session executing through Android `ProcessBuilder`.
   - Command boundary tokenizer handling quotes, escaped arguments, and symlink checks.
   - Built-in 256KB buffer limits preventing memory starvation.

6. **Git Source Control**:
   - Local working tree status (clean vs modified files).
   - Remote repository cloning with HTTPS access token support.
   - Branch switching, stage-all, and commit operations.

7. **Cloud & Storage Integrations**:
   - Google Drive v3 REST API file browser with atomic downloads into workspace.
   - Storage Access Framework (SAF) system picker for ZIP and document importing.
   - Keystore-backed AES-256-GCM token storage.

8. **Settings & Credential Vault**:
   - Hardware tier override (Standard, Performance, Ultra Performance).
   - Dark mode toggle (Minimal Luxury aesthetic).
   - Cloud AI provider API key configuration (Gemini, Claude, OpenAI, DeepSeek, Groq).

---

## 2. Using the Contextual AI Composer
From the Home screen, enter natural language prompts or terminal directives:
- Type `gradle :app:testDebugUnitTest` or click suggestion chips to run terminal tasks.
- Type `audit workspace` to trigger the static security and dependency auditor.
- Type `fix bug in main.kt` to launch the multi-step ReAct agent.
- Enter general coding questions to invoke active local or cloud AI inference.

---

## 3. Sandboxed Workspace
All project files reside in your application's private internal storage:
`/data/user/0/com.aistudio.aetherai.kqwzrt/files/aether_workspace`
Files cannot escape this boundary, safeguarding system integrity.
