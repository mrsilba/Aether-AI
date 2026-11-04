package com.aether.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.agent.AgentExecutionState
import com.aether.agent.SupervisorOrchestrator
import com.aether.cloud.CloudAiProviderManager
import com.aether.cloud.CloudProviderType
import com.aether.database.AetherDatabase
import com.aether.database.MemoryEntity
import com.aether.editor.CodeEditorController
import com.aether.editor.CodeSymbol
import com.aether.editor.DiffLine
import com.aether.editor.EditorTab
import com.aether.git.GitRepositoryManager
import com.aether.git.GitWorkingStatus
import com.aether.git.ProjectStructureMap
import com.aether.hardware.DeviceCapabilityManager
import com.aether.hardware.DeviceHardwareTier
import com.aether.hardware.HardwareCapabilityProfile
import com.aether.hardware.PowerPreference
import com.aether.inference.AdaptiveInferenceRouter
import com.aether.inference.CloudInferenceBackend
import com.aether.inference.GgufQuantization
import com.aether.inference.InferenceBackend
import com.aether.inference.InferenceBackendType
import com.aether.inference.LocalGgufCpuBackend
import com.aether.inference.ModelDescriptor
import com.aether.mcp.McpClientRuntime
import com.aether.mcp.McpToolDefinition
import com.aether.model.CompatibilityAssessment
import com.aether.model.DownloadStatus
import com.aether.model.HubModelEntry
import com.aether.model.ModelHubManager
import com.aether.model.TaskRecommendation
import com.aether.project.AuditFinding
import com.aether.project.ProjectAuditAndRepairEngine
import com.aether.project.ProjectAuditReport
import com.aether.storage.CloudStorageManager
import com.aether.terminal.SandboxedTerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class WorkstationScreen(val title: String, val iconName: String) {
    DASHBOARD("Dashboard", "Dashboard"),
    EDITOR("Code Editor", "Code"),
    TERMINAL("Terminal", "Terminal"),
    AGENT("ReAct Agent", "SmartToy"),
    AUDIT("Security Audit", "Security"),
    MODELS("Models Hub", "Psychology"),
    MEMORY("Project Memory", "Storage"),
    SETTINGS("Settings", "Settings")
}

data class WorkstationUiState(
    val currentScreen: WorkstationScreen = WorkstationScreen.DASHBOARD,
    val hardwareProfile: HardwareCapabilityProfile? = null,
    val openTabs: List<EditorTab> = emptyList(),
    val activeTab: EditorTab? = null,
    val symbols: List<CodeSymbol> = emptyList(),
    val pendingDiffFile: String? = null,
    val pendingDiffLines: List<DiffLine> = emptyList(),
    val terminalOutput: String = "AETHER AI Sandboxed Terminal v2.0\nReady. Type a command or select quick actions below.\n",
    val isTerminalRunning: Boolean = false,
    val agentState: AgentExecutionState = AgentExecutionState(objective = ""),
    val auditReport: ProjectAuditReport? = null,
    val isAuditing: Boolean = false,
    val availableModels: List<ModelDescriptor> = emptyList(),
    val activeLoadedModel: ModelDescriptor? = null,
    val activeInferenceBackend: InferenceBackendType = InferenceBackendType.LOCAL_CPU_NEON,
    val inferenceOutput: String = "",
    val isInferenceRunning: Boolean = false,
    val tokensPerSec: Float = 0f,
    val mcpTools: List<McpToolDefinition> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val statusMessage: String? = null,
    // Expanded Architecture State
    val hubModels: List<HubModelEntry> = emptyList(),
    val taskRecommendations: List<TaskRecommendation> = emptyList(),
    val gitStatus: GitWorkingStatus? = null,
    val gitBranches: List<String> = emptyList(),
    val projectStructure: ProjectStructureMap? = null,
    val cloudApiKeysConfigured: Map<CloudProviderType, Boolean> = emptyMap()
)

class WorkstationViewModel(application: Application) : AndroidViewModel(application) {

    private val workspaceDir = File(application.filesDir, "aether_workspace")
    private val modelsDir = File(application.filesDir, "models")

    private val capabilityManager = DeviceCapabilityManager(application)
    private val editorController = CodeEditorController(workspaceDir)
    private val terminalSession = SandboxedTerminalSession(workspaceDir)
    private val auditEngine = ProjectAuditAndRepairEngine(workspaceDir)
    private val mcpRuntime = McpClientRuntime()
    private val database = AetherDatabase.getInstance(application)

    // Domain Architecture Managers
    val modelHubManager = ModelHubManager(modelsDir)
    val gitManager = GitRepositoryManager(workspaceDir, terminalSession)
    val cloudStorageManager = CloudStorageManager(application, workspaceDir)
    val cloudAiProviderManager = CloudAiProviderManager(application)

    private val defaultModel = ModelDescriptor(
        id = "qwen2.5-coder-3b-q4k",
        name = "Qwen2.5-Coder 3B",
        parameters = "3.1B",
        quantization = GgufQuantization.Q4_K,
        fileSizeBytes = 1980000000L,
        contextWindow = 8192,
        localFilePath = File(modelsDir, "qwen2.5_3b_q4k.gguf").absolutePath,
        requiredMinTier = DeviceHardwareTier.STANDARD
    )

    private val localCpuBackend = LocalGgufCpuBackend(defaultModel)
    private val cloudBackend = CloudInferenceBackend(InferenceBackendType.CLOUD_GEMINI, apiKey = "")
    private val router = AdaptiveInferenceRouter(localCpuBackend, cloudBackend)
    private var activeBackend: InferenceBackend = localCpuBackend

    private val agentOrchestrator = SupervisorOrchestrator(
        workspaceRoot = workspaceDir,
        editorController = editorController,
        terminalSession = terminalSession,
        auditEngine = auditEngine,
        inferenceBackend = localCpuBackend
    )

    private val _uiState = MutableStateFlow(WorkstationUiState())
    val uiState: StateFlow<WorkstationUiState> = _uiState.asStateFlow()

    init {
        initCatalogAndState()
        observeDatabase()
        observeModelHub()
        refreshGitStatus()
        analyzeProjectStructure()
        refreshCloudKeyStatuses()
    }

    private fun initCatalogAndState() {
        val initialProfile = capabilityManager.assessCapabilities()

        val activeTab = editorController.getActiveTab()
        val symbols = if (activeTab != null) editorController.extractSymbols(activeTab.content) else emptyList()

        _uiState.update {
            it.copy(
                hardwareProfile = initialProfile,
                openTabs = editorController.getTabs(),
                activeTab = activeTab,
                symbols = symbols,
                activeLoadedModel = defaultModel,
                mcpTools = mcpRuntime.listTools()
            )
        }

        viewModelScope.launch {
            val hubList = modelHubManager.getAvailableModels()
            val recs = modelHubManager.getTaskRecommendations(hubList, initialProfile)
            _uiState.update {
                it.copy(
                    hubModels = hubList,
                    availableModels = hubList.map { h -> h.descriptor },
                    taskRecommendations = recs
                )
            }
        }
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            database.dao().getAllMemories().collect { memList ->
                _uiState.update { it.copy(memories = memList) }
            }
        }
    }

    private fun observeModelHub() {
        viewModelScope.launch {
            modelHubManager.downloadStatusMap.collect { statusMap ->
                _uiState.update { state ->
                    val updatedHub = state.hubModels.map { entry ->
                        val status = statusMap[entry.descriptor.id] ?: entry.downloadStatus
                        entry.copy(downloadStatus = status)
                    }
                    state.copy(hubModels = updatedHub)
                }
            }
        }
    }

    fun refreshHardwareProfile() {
        val updated = capabilityManager.assessCapabilities()
        _uiState.update { it.copy(hardwareProfile = updated) }
    }

    fun switchScreen(screen: WorkstationScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    // -------------------------------------------------------------
    // EDITOR ACTIONS
    // -------------------------------------------------------------
    fun selectEditorTab(index: Int) {
        editorController.selectTab(index)
        val active = editorController.getActiveTab()
        val symbols = if (active != null) editorController.extractSymbols(active.content) else emptyList()
        _uiState.update {
            it.copy(
                openTabs = editorController.getTabs(),
                activeTab = active,
                symbols = symbols
            )
        }
    }

    fun closeEditorTab(index: Int) {
        editorController.closeTab(index)
        val active = editorController.getActiveTab()
        val symbols = if (active != null) editorController.extractSymbols(active.content) else emptyList()
        _uiState.update {
            it.copy(
                openTabs = editorController.getTabs(),
                activeTab = active,
                symbols = symbols
            )
        }
    }

    fun updateEditorContent(newContent: String) {
        editorController.updateContent(newContent)
        val active = editorController.getActiveTab()
        val symbols = if (active != null) editorController.extractSymbols(active.content) else emptyList()
        _uiState.update {
            it.copy(
                openTabs = editorController.getTabs(),
                activeTab = active,
                symbols = symbols
            )
        }
    }

    fun saveActiveFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = editorController.saveActiveFile()
            res.fold(
                onSuccess = { msg ->
                    _uiState.update {
                        it.copy(
                            openTabs = editorController.getTabs(),
                            activeTab = editorController.getActiveTab(),
                            statusMessage = msg
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = "Save Error: ${err.message}") }
                }
            )
        }
    }

    fun undoEditor() {
        if (editorController.undo()) {
            val active = editorController.getActiveTab()
            val symbols = if (active != null) editorController.extractSymbols(active.content) else emptyList()
            _uiState.update {
                it.copy(
                    openTabs = editorController.getTabs(),
                    activeTab = active,
                    symbols = symbols
                )
            }
        }
    }

    fun redoEditor() {
        if (editorController.redo()) {
            val active = editorController.getActiveTab()
            val symbols = if (active != null) editorController.extractSymbols(active.content) else emptyList()
            _uiState.update {
                it.copy(
                    openTabs = editorController.getTabs(),
                    activeTab = active,
                    symbols = symbols
                )
            }
        }
    }

    // -------------------------------------------------------------
    // TERMINAL ACTIONS
    // -------------------------------------------------------------
    fun runTerminalCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTerminalRunning = true,
                    terminalOutput = it.terminalOutput + "\n$ $command\n"
                )
            }

            val result = terminalSession.execute(
                commandLine = command,
                userConfirmed = true,
                onOutputChunk = { chunk ->
                    _uiState.update { it.copy(terminalOutput = it.terminalOutput + chunk + "\n") }
                }
            )

            result.fold(
                onSuccess = { res ->
                    _uiState.update {
                        it.copy(
                            isTerminalRunning = false,
                            terminalOutput = it.terminalOutput + "[Process exited with code ${res.exitCode} in ${res.durationMs}ms]\n"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isTerminalRunning = false,
                            terminalOutput = it.terminalOutput + "[Execution Error: ${err.message}]\n"
                        )
                    }
                }
            )
        }
    }

    fun clearTerminal() {
        _uiState.update { it.copy(terminalOutput = "$ git status -> On branch main, clean.\n") }
    }

    // -------------------------------------------------------------
    // AUDIT & PATCH ACTIONS
    // -------------------------------------------------------------
    fun runProjectAudit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuditing = true) }
            val report = auditEngine.runFullAudit()
            _uiState.update {
                it.copy(
                    isAuditing = false,
                    auditReport = report,
                    statusMessage = "Audit Completed: Health ${report.healthScore}/100 with ${report.findings.size} findings."
                )
            }
        }
    }

    fun reviewFindingPatch(finding: AuditFinding) {
        val diffLines = finding.toDiffLines()
        _uiState.update {
            it.copy(
                pendingDiffFile = finding.filePath,
                pendingDiffLines = diffLines,
                currentScreen = WorkstationScreen.EDITOR,
                statusMessage = "Patch generated for ${finding.id}. Review and approve below."
            )
        }
    }

    fun approveAndApplyPatch() {
        val path = _uiState.value.pendingDiffFile ?: return
        val lines = _uiState.value.pendingDiffLines

        viewModelScope.launch(Dispatchers.IO) {
            val res = editorController.applyUnifiedPatch(path, lines)
            res.fold(
                onSuccess = { msg ->
                    _uiState.update {
                        it.copy(
                            pendingDiffFile = null,
                            pendingDiffLines = emptyList(),
                            openTabs = editorController.getTabs(),
                            activeTab = editorController.getActiveTab(),
                            statusMessage = msg
                        )
                    }
                    runProjectAudit()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = "Patch Failed: ${err.message}") }
                }
            )
        }
    }

    fun rejectPatch() {
        _uiState.update {
            it.copy(
                pendingDiffFile = null,
                pendingDiffLines = emptyList(),
                statusMessage = "Patch rejected."
            )
        }
    }

    // -------------------------------------------------------------
    // AGENT ACTIONS
    // -------------------------------------------------------------
    fun startReActAgent(objective: String) {
        if (objective.isBlank()) return
        viewModelScope.launch {
            agentOrchestrator.runReActLoop(objective).collect { state ->
                _uiState.update { it.copy(agentState = state) }
            }
        }
    }

    // -------------------------------------------------------------
    // MODEL HUB & INFERENCE ACTIONS
    // -------------------------------------------------------------
    fun selectModel(model: ModelDescriptor) {
        _uiState.update { it.copy(activeLoadedModel = model) }
    }

    fun switchBackend(type: InferenceBackendType) {
        _uiState.update { it.copy(activeInferenceBackend = type) }
    }

    fun assessModelCompatibility(model: ModelDescriptor): CompatibilityAssessment? {
        val profile = _uiState.value.hardwareProfile ?: return null
        return modelHubManager.assessModelCompatibility(model, profile)
    }

    fun downloadHubModel(entry: HubModelEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Initiating download: ${entry.descriptor.name}") }
            val res = modelHubManager.downloadModel(entry) { status ->
                // Status is published via StateFlow in manager
            }
            res.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(
                            activeLoadedModel = entry.descriptor.copy(localFilePath = file.absolutePath),
                            statusMessage = "Model installed successfully: ${entry.descriptor.name}"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = "Download error: ${err.message}") }
                }
            )
        }
    }

    fun pauseHubModelDownload(modelId: String) {
        modelHubManager.pauseDownload(modelId)
    }

    fun deleteHubModel(modelId: String) {
        modelHubManager.cancelAndDelete(modelId)
        _uiState.update { it.copy(statusMessage = "Model deleted from local storage.") }
    }

    fun searchHubModels(query: String) {
        viewModelScope.launch {
            val results = modelHubManager.getAvailableModels(query)
            _uiState.update { it.copy(hubModels = results) }
        }
    }

    fun runInference(prompt: String) {
        if (prompt.isBlank()) return
        val currentModel = _uiState.value.activeLoadedModel ?: defaultModel

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInferenceRunning = true,
                    inferenceOutput = ""
                )
            }

            val start = System.currentTimeMillis()
            var tokenCount = 0

            val backend = if (_uiState.value.activeInferenceBackend == InferenceBackendType.LOCAL_CPU_NEON) {
                localCpuBackend
            } else {
                cloudBackend
            }

            backend.streamInference(prompt).collect { token ->
                tokenCount++
                val elapsedSec = (System.currentTimeMillis() - start) / 1000f
                val tps = if (elapsedSec > 0) tokenCount / elapsedSec else 0f

                _uiState.update {
                    it.copy(
                        inferenceOutput = it.inferenceOutput + token,
                        tokensPerSec = tps
                    )
                }
            }

            _uiState.update { it.copy(isInferenceRunning = false) }
        }
    }

    fun cancelInference() {
        localCpuBackend.cancel()
        cloudBackend.cancel()
        _uiState.update { it.copy(isInferenceRunning = false) }
    }

    // -------------------------------------------------------------
    // GIT OPERATIONS
    // -------------------------------------------------------------
    fun refreshGitStatus() {
        viewModelScope.launch {
            val statusRes = gitManager.getWorkingStatus()
            val branchRes = gitManager.getBranches()
            _uiState.update {
                it.copy(
                    gitStatus = statusRes.getOrNull(),
                    gitBranches = branchRes.getOrDefault(listOf("main"))
                )
            }
        }
    }

    fun cloneGitRepository(url: String, branch: String? = null, patToken: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Cloning repository from $url...") }
            val res = gitManager.cloneRepository(url, branch = branch, personalAccessToken = patToken)
            res.fold(
                onSuccess = { msg ->
                    _uiState.update { it.copy(statusMessage = msg) }
                    refreshGitStatus()
                    analyzeProjectStructure()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = "Clone Failed: ${err.message}") }
                }
            )
        }
    }

    fun commitGitChanges(message: String) {
        viewModelScope.launch {
            val res = gitManager.stageAndCommit(message)
            res.fold(
                onSuccess = { msg ->
                    _uiState.update { it.copy(statusMessage = "Committed: $msg") }
                    refreshGitStatus()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = "Commit Failed: ${err.message}") }
                }
            )
        }
    }

    fun analyzeProjectStructure() {
        viewModelScope.launch(Dispatchers.IO) {
            val structure = gitManager.analyzeProjectStructure(workspaceDir)
            _uiState.update { it.copy(projectStructure = structure) }
        }
    }

    // -------------------------------------------------------------
    // CLOUD STORAGE & IMPORT
    // -------------------------------------------------------------
    fun importZipArchive(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Extracting ZIP archive safely...") }
            val res = cloudStorageManager.importZipArchive(file)
            res.fold(
                onSuccess = { count ->
                    _uiState.update { it.copy(statusMessage = "Extracted $count files into workspace.") }
                    analyzeProjectStructure()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = "Archive Extraction Failed: ${err.message}") }
                }
            )
        }
    }

    fun importFromSafUri(uri: Uri, destFileName: String) {
        viewModelScope.launch {
            val res = cloudStorageManager.importFromContentUri(uri, destFileName)
            res.fold(
                onSuccess = { f ->
                    _uiState.update { it.copy(statusMessage = "Imported '${f.name}' into workspace.") }
                    analyzeProjectStructure()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = "Import Error: ${err.message}") }
                }
            )
        }
    }

    // -------------------------------------------------------------
    // CLOUD PROVIDER VAULT
    // -------------------------------------------------------------
    fun storeCloudApiKey(provider: CloudProviderType, apiKey: String) {
        cloudAiProviderManager.storeApiKey(provider, apiKey)
        refreshCloudKeyStatuses()
        _uiState.update { it.copy(statusMessage = "Updated API Key for ${provider.displayName}") }
    }

    fun getCloudApiKey(provider: CloudProviderType): String? {
        return cloudAiProviderManager.getApiKey(provider)
    }

    private fun refreshCloudKeyStatuses() {
        val map = CloudProviderType.values().associateWith { p ->
            cloudAiProviderManager.hasApiKey(p)
        }
        _uiState.update { it.copy(cloudApiKeysConfigured = map) }
    }

    // -------------------------------------------------------------
    // DATABASE / MEMORY
    // -------------------------------------------------------------
    fun addMemory(category: String, title: String, content: String) {
        viewModelScope.launch {
            val entity = MemoryEntity(
                id = "mem_" + System.currentTimeMillis(),
                projectId = "aether_master_project",
                category = category,
                title = title,
                content = content
            )
            database.dao().insertMemory(entity)
            _uiState.update { it.copy(statusMessage = "Memory '$title' stored securely.") }
        }
    }

    fun setDeviceTierOverride(tier: DeviceHardwareTier?) {
        capabilityManager.setUserOverrideTier(tier)
        refreshHardwareProfile()
    }

    fun setPowerPreference(pref: PowerPreference) {
        capabilityManager.setPowerPreference(pref)
        refreshHardwareProfile()
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
