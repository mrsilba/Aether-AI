# AETHER AI — Functionality Verification Matrix

## 1. System-Wide Verification Matrix
This matrix traces every critical system capability from UI through ViewModel, Domain Logic, Infrastructure, and actual runtime execution.

| Feature Area | UI Screen / Entry Point | ViewModel Property / Function | Domain Engine & Class | Infrastructure / OS Integration | Verification Status |
|---|---|---|---|---|---|
| **AI Prompting** | `MinimalHomeScreen` / `MinimalContextualComposer` | `runInference(prompt)`, `uiState.inferenceOutput` | `AdaptiveInferenceRouter`, `LocalGgufCpuBackend`, `CloudInferenceBackend` | ARM64 NEON vector loops / OkHttp SSE HTTP/2 | **VERIFIED** |
| **Code Editor** | `MinimalEditorScreen` | `selectEditorTab`, `updateEditorContent`, `saveActiveFile` | `CodeEditorController` | Java File I/O under app files dir | **VERIFIED** |
| **Unified Diff Patch** | `MinimalEditorScreen` | `reviewFindingPatch`, `approveAndApplyPatch`, `rejectPatch` | `applyUnifiedPatch()`, `toDiffLines()` | Atomic file write with backup check | **VERIFIED** |
| **Sandboxed Terminal** | `MinimalTerminalScreen` | `runTerminalCommand`, `uiState.terminalOutput` | `SandboxedTerminalSession`, `CommandGuardrailEngine` | Linux `ProcessBuilder` with timeout & confinement | **VERIFIED** |
| **ReAct Supervisor** | `MinimalAgentScreen` | `startReActAgent(objective)`, `uiState.agentState` | `SupervisorOrchestrator`, `WorkstationTool` | Kotlin Coroutines Flow stream | **VERIFIED** |
| **Security Audit** | `MinimalAuditScreen` | `runProjectAudit()`, `uiState.auditReport` | `ProjectAuditAndRepairEngine` | Regex AST AST scanner & patch builder | **VERIFIED** |
| **Models Catalog** | `MinimalModelsScreen` | `selectModel`, `switchBackend`, `uiState.availableModels` | `ModelDescriptor`, `GgufQuantization` | Memory calculation & file check | **VERIFIED (Static) -> Expanding** |
| **Git Management** | `MinimalGitScreen` | `runTerminalCommand("git status")` | `SandboxedTerminalSession` | Linux `git` binary execution | **VERIFIED (Basic) -> Expanding** |
| **Hardware Profiling** | `MinimalHeader` / `MinimalWorkspaceScreen` | `uiState.hardwareProfile`, `setDeviceTierOverride` | `DeviceCapabilityManager` | Android `ActivityManager`, `BatteryManager`, `PowerManager` | **VERIFIED** |
| **Encrypted Database** | `MinimalSettingsScreen` | `addMemory()`, `uiState.memories` | `AetherDatabase`, `MemoryEntity` | Room SQLite on local storage | **VERIFIED** |
| **Cryptography** | `MinimalSettingsScreen` | Keystore diagnostic display | `SecurityEngine` | Android Keystore provider `AES/GCM/NoPadding` | **VERIFIED** |
