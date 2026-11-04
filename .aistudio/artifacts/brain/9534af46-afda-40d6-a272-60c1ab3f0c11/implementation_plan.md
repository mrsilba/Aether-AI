# AETHER AI — Master Engineering Directive & Implementation Plan

AETHER AI is an Android-native AI Development Workstation engineered to run across modern flagship and upper-tier Android hardware (Snapdragon, Google Tensor, Samsung Exynos, MediaTek Dimensity). It transforms mobile devices into full-fledged software engineering workstations capable of code editing, recursive repository auditing, automated patch generation, sandboxed terminal execution, local GGUF/LiteRT inference, and autonomous ReAct agent orchestration.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following core product and architectural choices were confirmed during the initial interview phase and govern the workstation implementation:

- **Confirmed: Inference Strategy**: Adaptive hybrid model routing that prioritizes local quantized GGUF on-device execution (ARM64 NEON/dotprod accelerated) with seamless automatic fallback to cloud AI providers (Gemini, Claude, GPT) when model size, RAM, or thermal constraints dictate.
- **Confirmed: Hardware Adaptation Policy**: Dynamic runtime hardware capability profiling (`DeviceCapabilityManager`) assessing CPU topology, RAM pressure, GPU Vulkan/GLES, and thermal throttling status, with user-facing manual override controls.
- **Confirmed: Workspace & Execution Confinement**: Strict in-app workspace sandboxing with canonical path verification, command allowlisting, timeout enforcement, and least-privilege boundary defense against path traversal or unauthorized file mutation.

---

## 1. Overview & Core Concept

- **What It Does**: A complete mobile software engineering IDE and agentic workstation featuring an M3 cybernetic interface. It provides project management, code editing with syntax highlighting and diff viewers, sandboxed terminal execution, local model management (Hugging Face GGUF downloader and llama.cpp/LiteRT backend), multi-provider cloud routing, real repository static security analysis, and a model-driven ReAct agent loop communicating with local and remote tools via Model Context Protocol (MCP).
- **Target Audience / Persona**: Mobile software engineers, security auditors, AI researchers, and Android power users who require a desktop-grade development environment on high-performance Android devices.
- **Key Value**: Delivers genuine, verifiable development capabilities on mobile hardware without simulated stubs, hardcoded metrics, or fake terminal outputs.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Workstation Dashboard**: Real-time hardware telemetry (RAM, CPU cores, thermal status, battery rate, active inference backend), active project overview, model load status, and quick launcher.
2. **AI Workspace & Code Editor**: Multi-tab code editor with line numbers, syntax highlighting (Kotlin, Java, Python, C++, JSON), search & replace, undo/redo, real-time symbol indexing, and side-by-side unified diff review.
3. **Sandboxed Terminal**: Real command execution in isolated project workspace with live stdout/stderr streaming, process cancellation, execution timers, and exit code telemetry.
4. **Autonomous Agent & MCP**: Interactive ReAct reasoning loop showing objective breakdown, thought logs, tool selection (file read/write, bash, grep, audit), tool output observation, and final synthesis.
5. **Repository Audit & Patch Review**: Recursive indexing of real project files, AST/regex vulnerability scanning, build configuration inspection, and an interactive patch approval modal that actually modifies workspace files upon confirmation.
6. **Local Model Hub & Cloud Providers**: Model catalog (TinyCoder, Qwen2.5-Coder, DeepSeek-Coder, Gemma), RAM-suitability estimation, resumable downloading, and cloud API key configuration (stored securely in Keystore/DataStore).

### Visual Identity & Theme
- **Aesthetic Direction**: High-tech cybernetic engineering studio. Deep space dark posture with tactile contrast, razor-sharp information density, and functional color coding.
- **Color Palette (M3 Semantic Tokens)**:
  - Deep Space Canvas: `#080C14`
  - Elevated Container / Cards: `#121826`
  - Border & Dividers: `#1F293D`
  - Primary Accent (Cyan/Teal): `#00E5FF`
  - Secondary Accent (Neon Violet): `#A855F7`
  - Success Indicator (Emerald): `#10B981`
  - Warning Indicator (Amber): `#F59E0B`
  - Error Indicator (Coral Red): `#EF4444`
- **Typography & Hierarchy**:
  - Code & Telemetry: Monospace font family (`FontFamily.Monospace`) for terminal output, hex keys, file paths, diffs, and editor buffers.
  - Headings & Labels: Technical sans-serif typography with distinct font weight hierarchy (Medium 500, SemiBold 600, Bold 700).
- **Layout & Density**:
  - Full-bleed edge-to-edge layout honoring notch and navigation bar safe insets (`WindowInsets.safeDrawing`, `navigationBarsPadding`).
  - Minimum 48dp interactive touch targets on all buttons, tabs, and action icons.
  - Adaptive two-pane layouts on foldable/tablet orientations (Navigation rail + Split pane).

---

## 3. Key Product Decisions & Trade-Offs

- **Modular Architecture Refactoring**:
  - *Chosen Approach*: Replace the monolithic 25-dependency `WorkstationViewModel` with scoped, domain-specific view models and use-cases (`DashboardViewModel`, `EditorViewModel`, `TerminalViewModel`, `AgentViewModel`, `AuditViewModel`, `ModelManagerViewModel`).
  - *Why*: Eliminates state thrashing, ensures clean dependency injection, simplifies Robolectric and JVM unit testing, and prevents memory leaks during heavy inference sessions.
- **Hardware Capability Manager**:
  - *Chosen Approach*: Query Android system APIs (`ActivityManager`, `BatteryManager`, `PowerManager`, OpenGL/Vulkan runtime, `Os.sysconf`) to dynamically assign hardware profiles: `BASIC`, `STANDARD`, `PERFORMANCE`, `HIGH_PERFORMANCE`, `ULTRA_PERFORMANCE`.
  - *Why*: Supports multi-vendor SoCs (Snapdragon 8 Gen 1/2/3, Tensor G2/G3/G4, Exynos 2200/2400, Dimensity 9000/9300) without vendor lock-in or fragile string matching.
- **Inference Backend Abstraction**:
  - *Chosen Approach*: Clean `InferenceBackend` interface with implementations for Native CPU (ARM64 NEON), Vulkan GPU compute, and Cloud API fallback.
  - *Why*: Ensures graceful degradation when GPU acceleration is unavailable or memory pressure increases, preventing application crashes.
- **Database & Cryptography**:
  - *Chosen Approach*: Room database backed by SQLCipher with 256-bit passphrase generated via Android Keystore (`KeyGenParameterSpec` AES-GCM wrapping).
  - *Why*: Provides zero-trust encrypted local persistence for project files, memory records, audit histories, and credentials without storing plaintext keys.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        AETHER AI UI Layer                              │
│  MainActivity ─ Dashboard ─ Editor ─ Terminal ─ Agent ─ Audit ─ Models │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────┴─────────────────────────────────────┐
│                       Feature ViewModels Layer                         │
│  [DashboardVM]  [EditorVM]  [TerminalVM]  [AgentVM]  [AuditVM] [ModelVM]│
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────┴─────────────────────────────────────┐
│                          Domain Use Cases                              │
│  ExecuteCommandUseCase │ RunAgentLoopUseCase │ AuditRepositoryUseCase  │
│  LoadModelUseCase      │ ApplyPatchUseCase   │ EncryptDataUseCase      │
└──────────┬───────────────────────┬──────────────────────────┬──────────┘
           │                       │                          │
┌──────────┴────────────┐ ┌────────┴───────────┐ ┌────────────┴──────────┐
│  Inference Subsystem  │ │ Sandboxed Toolchain│ │ Storage & Security    │
│  - DeviceCapability   │ │ - Process Isolation│ │ - Room + SQLCipher    │
│  - GGUF Native / NEON │ │ - Command Guardrail│ │ - Android Keystore    │
│  - MultiProvider Cloud│ │ - Stdio/HTTP MCP   │ │ - Secret Scanner      │
└───────────────────────┘ └────────────────────┘ └───────────────────────┘
```

### Core Execution Modules
1. **Core Database & Security**:
   - `AetherDatabase` (Room + SQLCipher) with entities for `ProjectEntity`, `MemoryEntity`, `AuditFindingEntity`, `ToolExecutionLog`.
   - `SqlCipherKeyManager` utilizing Android Keystore for key wrapping.
   - `CommandGuardrailEngine` preventing dangerous commands (`rm -rf /`, fork bombs, unauthorized system path writes).
2. **Device & Hardware Engine**:
   - `DeviceCapabilityManager`: Measures available memory, core counts, thermal state, and ABI support.
   - `InferenceBackendFactory`: Dynamically selects between local native engine and cloud router based on hardware tier and model requirements.
3. **Autonomous Agent & MCP Engine**:
   - `SupervisorOrchestrator`: Implements the 9-step ReAct loop (Objective -> Context -> Tool Selection -> Execution -> Observation -> Decision -> Stop).
   - `McpClientRuntime`: Implements JSON-RPC 2.0 protocol over stdio and HTTP transports for tool discovery and execution.
4. **Editor & Terminal Workstation**:
   - `CodeEditorController`: Manages file buffers, undo/redo stacks, syntax rules, and unified diff generator.
   - `SandboxedTerminalSession`: Executes commands within the project root directory, streams stdout/stderr, and supports cancellation via coroutines.

---

## 5. Verification & Acceptance Plan

- **Compilation & Syntax**: Fix confirmed syntax defects in `DeveloperLibraryAndToolRegistry.kt` (line 264 unescaped quote) and all related Kotlin files.
- **Compilation Check**: Execute `compile_applet` to verify zero compile errors across the application module.
- **Unit & Robolectric Tests**: Run local JVM tests verifying hardware capability assessment, SQLCipher key derivation, ReAct agent state transitions, command guardrail filtering, and diff patch calculations.
- **Audit Deliverable**: Generate `engineering_audit/INDEPENDENT_FORENSIC_AUDIT.md` detailing all verified defects, resolved blockers, and architectural remediation steps.
