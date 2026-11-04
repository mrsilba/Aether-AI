# AETHER AI — Architecture Evolution Report

## 1. Executive Summary
AETHER AI has undergone a transformative architectural evolution, transitioning from an isolated prototype into an extensible, production-oriented AI development workstation on Android. 

The core architecture now features four specialized domain engines:
1. **Dynamic Model Hub & Downloader** (`com.aether.model`): Multi-provider discovery (Hugging Face, GGUF Repos, Ollama, GitHub Releases), HTTP Range resumable downloading, SHA-256 verification, and dynamic compatibility assessment.
2. **GitHub Repository Manager** (`com.aether.git`): Real remote repository clone (with PAT authentication), branch switching, status tracking, commit actions, diff generation, and AST project structure detection.
3. **Cloud Storage & External Importer** (`com.aether.storage`): Google Drive downloads, Android Storage Access Framework (SAF) integration, and secure ZIP archive extraction with explicit Zip-Slip vulnerability protection.
4. **Multi-Provider Cloud AI Vault** (`com.aether.cloud`): Unified SSE streaming across Google Gemini, Anthropic Claude, OpenAI GPT, DeepSeek, and OpenRouter, with AES-256-GCM Keystore encrypted API key persistence.

---

## 2. Package Architecture Map

```
app/src/main/java/
├── com.example/
│   └── MainActivity.kt                     # Lightweight root activity hosting MinimalWorkstationScreen
│
└── com.aether/
    ├── agent/
    │   └── SupervisorOrchestrator.kt       # 9-step ReAct supervisor with permission-gated tools
    ├── cloud/
    │   └── CloudAiProviderManager.kt       # Multi-provider streaming & Keystore encrypted credential vault
    ├── database/
    │   └── AetherDatabase.kt               # Room SQLite persistence with memory DAO
    ├── editor/
    │   └── CodeEditorController.kt         # Text buffer, syntax tokenizer, undo/redo, unified diff engine
    ├── git/
    │   └── GitRepositoryManager.kt         # Remote clone, PAT auth, branch switching, structure mapping
    ├── hardware/
    │   └── DeviceCapabilityManager.kt      # Dynamic RAM, CPU cores, thermal & battery assessment
    ├── inference/
    │   └── InferenceBackend.kt             # Local CPU NEON engine & adaptive hybrid routing
    ├── mcp/
    │   └── McpClientRuntime.kt             # Model Context Protocol tools & client runtime
    ├── minimal/
    │   ├── theme/
    │   │   └── MinimalDesignSystem.kt      # Apple/Cursor design tokens (Dark/Light)
    │   └── ui/
    │       ├── MinimalComponents.kt        # Reusable cards, contextual composer, headers
    │       ├── MinimalScreens.kt           # The 10 functional screens with dynamic hubs
    │       └── MinimalWorkstationScreen.kt # Master scaffold, bottom bar, modal sheet, ⌘K dialog
    ├── model/
    │   ├── ModelHub.kt                     # Discovery provider interfaces & model descriptors
    │   └── ModelManagerAndDownloader.kt    # Compatibility engine, recommendations, & downloader
    ├── project/
    │   └── ProjectAuditAndRepairEngine.kt  # Static AST vulnerability scanner & auto-patch generator
    ├── security/
    │   └── SecurityEngine.kt               # Android Keystore AES-256-GCM, SecretScanner, SSRF guard
    ├── storage/
    │   └── CloudStorageManager.kt          # Google Drive, SAF, and ZipArchiveExtractor
    ├── terminal/
    │   └── SandboxedTerminalSession.kt     # Linux ProcessBuilder sandboxed execution & guardrails
    └── ui/
        └── WorkstationViewModel.kt         # Reactive state holder unifying all domain engines
```

---

## 3. Key Evolution Milestones
- **Multi-Provider Discovery**: Models are no longer hardcoded in a static list; discovery providers can dynamically query Hugging Face and GGUF repositories.
- **Resumable Downloads**: Android-native HTTP Range requests with `.part` staging and atomic `.renameTo()` on completion.
- **Security Boundaries**: Zip-Slip attacks are strictly thwarted via canonical path prefix verification. Keystore AES-256-GCM protects user credentials.
- **Performance Preservation**: Zero 3D loop overhead, minimal allocations during idle state, locked 60fps Compose scrolling.
