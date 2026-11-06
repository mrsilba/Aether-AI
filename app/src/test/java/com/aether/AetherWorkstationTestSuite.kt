package com.aether

import com.aether.agent.SupervisorOrchestrator
import com.aether.editor.CodeEditorController
import com.aether.editor.DiffLine
import com.aether.editor.DiffType
import com.aether.hardware.DeviceHardwareTier
import com.aether.hardware.PowerPreference
import com.aether.hardware.ThermalStatus
import com.aether.inference.AdaptiveInferenceRouter
import com.aether.inference.GgufQuantization
import com.aether.inference.InferenceBackendType
import com.aether.inference.LocalGgufCpuBackend
import com.aether.inference.ModelDescriptor
import com.aether.mcp.McpClientRuntime
import com.aether.project.FindingSeverity
import com.aether.project.ProjectAuditAndRepairEngine
import com.aether.terminal.CommandGuardrailEngine
import com.aether.security.SecretScanner
import com.aether.terminal.CommandDecision
import com.aether.terminal.SandboxedTerminalSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AetherWorkstationTestSuite {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var workspaceRoot: File
    private lateinit var editorController: CodeEditorController
    private lateinit var terminalSession: SandboxedTerminalSession
    private lateinit var auditEngine: ProjectAuditAndRepairEngine

    @Before
    fun setUp() {
        workspaceRoot = tempFolder.newFolder("test_workspace")
        editorController = CodeEditorController(workspaceRoot)
        terminalSession = SandboxedTerminalSession(workspaceRoot)
        auditEngine = ProjectAuditAndRepairEngine(workspaceRoot)
    }

    // -------------------------------------------------------------
    // 1. TERMINAL & SANDBOX SECURITY TESTS
    // -------------------------------------------------------------
    @Test
    fun testCommandGuardrailAllowsSafeDevCommands() {
        val guardrail = com.aether.terminal.CommandGuardrailEngine()

        val safeCommands = listOf("git status", "ls -la", "pwd", "grep -r 'pattern' .", "cat build.gradle.kts")
        for (cmd in safeCommands) {
            val decision = guardrail.evaluate(cmd)
            assertTrue("Command '$cmd' should be allowed", decision is CommandDecision.Allowed)
        }
    }

    @Test
    fun testCommandGuardrailBlocksDestructiveOperations() {
        val guardrail = com.aether.terminal.CommandGuardrailEngine()

        val dangerousCommands = listOf(
            "rm -rf /",
            "chmod 777 /",
            ":(){ :|:& };:", // fork bomb
            "cat /system/build.prop"
        )
        for (cmd in dangerousCommands) {
            val decision = guardrail.evaluate(cmd)
            assertTrue("Command '$cmd' should be blocked", decision is CommandDecision.Denied)
        }
    }

    @Test
    fun testTerminalWorkspaceConfinementPreventsPathTraversal() {
        val outsideDir = tempFolder.newFolder("outside_dir")
        val isConfined = terminalSession.verifyWorkspaceConfinement(outsideDir)
        assertFalse("Directory outside workspace root must be rejected", isConfined)

        val insideDir = File(workspaceRoot, "subdir").apply { mkdirs() }
        val isInsideConfined = terminalSession.verifyWorkspaceConfinement(insideDir)
        assertTrue("Subdirectory of workspace root must be allowed", isInsideConfined)
    }

    // -------------------------------------------------------------
    // 2. CODE EDITOR & PATCH APPLICATION TESTS
    // -------------------------------------------------------------
    @Test
    fun testCodeEditorSymbolExtraction() {
        val code = """
            package com.test
            class WorkstationEngine {
                val activeThreads: Int = 8
                fun startInference(prompt: String): Boolean {
                    return true
                }
            }
        """.trimIndent()

        val symbols = editorController.extractSymbols(code)
        assertEquals(3, symbols.size)

        val names = symbols.map { it.name }
        assertTrue(names.contains("WorkstationEngine"))
        assertTrue(names.contains("activeThreads"))
        assertTrue(names.contains("startInference"))
    }

    @Test
    fun testCodeEditorUndoRedoStack() {
        val tab = editorController.getActiveTab()
        assertNotNull(tab)

        val originalText = tab!!.content
        editorController.updateContent("$originalText\n// Added comment")
        assertEquals("$originalText\n// Added comment", editorController.getActiveTab()?.content)

        val undoSuccess = editorController.undo()
        assertTrue(undoSuccess)
        assertEquals(originalText, editorController.getActiveTab()?.content)

        val redoSuccess = editorController.redo()
        assertTrue(redoSuccess)
        assertEquals("$originalText\n// Added comment", editorController.getActiveTab()?.content)
    }

    @Test
    fun testUnifiedPatchApplicationActuallyModifiesFile() {
        val testFile = File(workspaceRoot, "PatchTarget.kt")
        testFile.writeText("val initial = 1\nval target = 2\n")

        val diffLines = listOf(
            DiffLine(DiffType.CONTEXT, "val initial = 1"),
            DiffLine(DiffType.DELETION, "val target = 2"),
            DiffLine(DiffType.ADDITION, "val target = 42")
        )

        val patchResult = editorController.applyUnifiedPatch(testFile.absolutePath, diffLines)
        assertTrue("Patch application should succeed", patchResult.isSuccess)

        val modifiedText = testFile.readText()
        assertTrue("Modified text must contain new value", modifiedText.contains("val target = 42"))
        assertFalse("Modified text must not contain old value", modifiedText.contains("val target = 2"))
    }

    // -------------------------------------------------------------
    // 3. PROJECT AUDIT & SECURITY SCANNER TESTS
    // -------------------------------------------------------------
    @Test
    fun testAuditDetectsHardcodedSecretAndInsecureHttp() = runBlocking {
        val vulnFile = File(workspaceRoot, "InsecureSample.kt")
        vulnFile.writeText("""
            package com.test
            class InsecureSample {
                val apiKey = "AIzaSyD-unsecure_production_key_12345"
                val serverUrl = "http://api.external-unencrypted.com/data"
            }
        """.trimIndent())

        val report = auditEngine.runFullAudit()
        assertTrue(report.scannedFilesCount >= 1)
        assertTrue("Must detect findings in vulnerable file", report.findings.isNotEmpty())

        val secretFinding = report.findings.find { it.title.contains("Secret") }
        assertNotNull("Hardcoded secret finding must be reported", secretFinding)
        assertEquals(FindingSeverity.CRITICAL, secretFinding?.severity)

        val httpFinding = report.findings.find { it.title.contains("Cleartext HTTP") }
        assertNotNull("Cleartext HTTP finding must be reported", httpFinding)
    }

    // -------------------------------------------------------------
    // 4. MCP JSON-RPC 2.0 PROTOCOL TESTS
    // -------------------------------------------------------------
    @Test
    fun testMcpInitializeAndToolsList() {
        val mcp = McpClientRuntime()

        val initRequest = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
        """.trimIndent()
        val initResponse = mcp.handleJsonRpcMessage(initRequest)
        val initObj = JSONObject(initResponse)

        assertEquals("2.0", initObj.getString("jsonrpc"))
        assertEquals(1L, initObj.getLong("id"))
        assertTrue(initObj.getJSONObject("result").getJSONObject("capabilities").has("tools"))
        assertTrue(mcp.isInitialized)

        val listToolsRequest = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
        """.trimIndent()
        val listResponse = mcp.handleJsonRpcMessage(listToolsRequest)
        val listObj = JSONObject(listResponse)
        val tools = listObj.getJSONObject("result").getJSONArray("tools")
        assertTrue(tools.length() >= 3)
    }

    // -------------------------------------------------------------
    // 5. SECRET REDACTION TESTS
    // -------------------------------------------------------------
    @Test
    fun testSecretScannerRedaction() {
        val rawLog = "Connecting with ghp_123456789012345678901234567890123456 and token=AIzaSyD1234567890123456789012345678"
        val redacted = SecretScanner.redactSecrets(rawLog)

        assertFalse(redacted.contains("ghp_123456789012345678901234567890123456"))
        assertFalse(redacted.contains("AIzaSyD1234567890123456789012345678"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    // -------------------------------------------------------------
    // 6. MODEL COMPATIBILITY & RECOMMENDATION TESTS
    // -------------------------------------------------------------
    @Test
    fun testModelCompatibilityEngineAssessment() {
        val engine = com.aether.model.ModelCompatibilityEngine()

        val mockProfile = com.aether.hardware.HardwareCapabilityProfile(
            manufacturer = "Google",
            model = "Pixel 9 Pro",
            androidSdk = 35,
            cpuArchitecture = "aarch64",
            supportedAbis = listOf("arm64-v8a"),
            cpuCoreCount = 8,
            totalRamMb = 16384L,
            availableRamMb = 12000L,
            isLowMemory = false,
            storageAvailableMb = 64000L,
            batteryPercent = 90,
            isCharging = true,
            thermalStatus = ThermalStatus.NORMAL,
            supportsNeon = true,
            supportsDotProduct = true,
            supportsFp16 = true,
            calculatedTier = DeviceHardwareTier.ULTRA_PERFORMANCE,
            maxSafeModelRamMb = 10000L,
            recommendedThreads = 6
        )

        val model = ModelDescriptor(
            id = "qwen2.5-coder-3b-q4k",
            name = "Qwen2.5-Coder 3B",
            parameters = "3.1B",
            quantization = GgufQuantization.Q4_K,
            fileSizeBytes = 1980000000L, // ~1.88 GB
            contextWindow = 8192,
            requiredMinTier = DeviceHardwareTier.STANDARD
        )

        val assessment = engine.assessCompatibility(model, mockProfile)
        assertEquals("Large RAM profile should result in RECOMMENDED", com.aether.model.CompatibilityRating.RECOMMENDED, assessment.rating)
        assertTrue(assessment.memoryHeadroomMb > 5000L)
        assertTrue(assessment.isStorageSufficient)
    }

    // -------------------------------------------------------------
    // 7. ZIP-SLIP PATH TRAVERSAL DEFENSE TEST
    // -------------------------------------------------------------
    @Test
    fun testZipArchiveExtractorRejectsPathTraversal() {
        val extractor = com.aether.storage.ZipArchiveExtractor()
        val extractTarget = tempFolder.newFolder("safe_extract_dir")

        // Build a malicious in-memory ZIP containing ../evil.txt
        val baos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(baos)
        val maliciousEntry = java.util.zip.ZipEntry("../evil.txt")
        zos.putNextEntry(maliciousEntry)
        zos.write("malicious payload".toByteArray())
        zos.closeEntry()
        zos.close()

        val bais = java.io.ByteArrayInputStream(baos.toByteArray())
        val result = extractor.extractSafely(bais, extractTarget)

        assertTrue("Extraction of path traversal entry must fail", result.isFailure)
        assertTrue("Error must be SecurityException", result.exceptionOrNull() is SecurityException)
    }

    // -------------------------------------------------------------
    // 8. PROJECT STRUCTURE DETECTOR TEST
    // -------------------------------------------------------------
    @Test
    fun testProjectStructureAnalyzerDetectsGradleAndroid() {
        val gitManager = com.aether.git.GitRepositoryManager(workspaceRoot, terminalSession)

        // Create sample project files
        File(workspaceRoot, "build.gradle.kts").writeText("// root gradle")
        File(workspaceRoot, "settings.gradle.kts").writeText("// settings")
        File(workspaceRoot, "MainActivity.kt").writeText("package com.example")

        val structure = gitManager.analyzeProjectStructure(workspaceRoot)
        assertEquals("Android / Kotlin Multiplatform", structure.projectType)
        assertEquals("Gradle Kotlin DSL", structure.buildSystem)
        assertTrue(structure.keyEntryPoints.contains("MainActivity.kt"))
    }

    // -------------------------------------------------------------
    // 9. GGUF HEADER VALIDATION & NATIVE BRIDGE TESTS
    // -------------------------------------------------------------
    @Test
    fun testGgufHeaderValidationRejectsCorruptFiles() {
        val validGgufFile = File(workspaceRoot, "valid_model.gguf").apply {
            writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00))
        }
        val invalidFile = File(workspaceRoot, "invalid_model.gguf").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        }

        val modelDescriptor = ModelDescriptor(
            id = "test-model",
            name = "Test Model",
            parameters = "1B",
            quantization = GgufQuantization.Q4_K,
            fileSizeBytes = 1000L,
            localFilePath = validGgufFile.absolutePath
        )

        val backend = LocalGgufCpuBackend(modelDescriptor)
        assertTrue("Valid GGUF header must be accepted", backend.validateGgufHeader(validGgufFile))
        assertFalse("Invalid binary header must be rejected", backend.validateGgufHeader(invalidFile))
    }

    @Test
    fun testNativeBridgeGracefulFallbackWhenNoSharedLibrary() {
        // In JVM Robolectric environment without preloaded libaether_llama.so
        val isLoaded = com.aether.local.inference.LlamaCppNativeBridge.isAvailable
        // Must not crash or throw UnsatisfiedLinkError during query
        assertNotNull(isLoaded)
    }

    // -------------------------------------------------------------
    // 10. SHELL COMMAND TOKENIZER TESTS
    // -------------------------------------------------------------
    @Test
    fun testShellCommandTokenizerPreservesQuotes() {
        val cmd = """git commit -m "feat(auth): add google drive oauth client" --author 'Dev <dev@test.com>'"""
        val tokens = com.aether.terminal.ShellCommandTokenizer.tokenize(cmd)

        assertEquals("git", tokens[0])
        assertEquals("commit", tokens[1])
        assertEquals("-m", tokens[2])
        assertEquals("feat(auth): add google drive oauth client", tokens[3])
        assertEquals("--author", tokens[4])
        assertEquals("Dev <dev@test.com>", tokens[5])
    }

    // -------------------------------------------------------------
    // 11. TERMINAL ESCAPED PATH ARGUMENT DETECTION
    // -------------------------------------------------------------
    @Test
    fun testTerminalEscapedPathArgumentDetection() {
        val outsideFile = tempFolder.newFile("outside_secret.txt")
        val tokensAllowed = listOf("cat", "inside.txt")
        val tokensEscaped = listOf("cat", outsideFile.absolutePath)

        assertFalse(terminalSession.hasEscapedPathArgument(tokensAllowed, workspaceRoot))
        assertTrue(terminalSession.hasEscapedPathArgument(tokensEscaped, workspaceRoot))
    }

    // -------------------------------------------------------------
    // 12. AGENT TOOL CONFINEMENT & PERMISSIONS
    // -------------------------------------------------------------
    @Test
    fun testAgentToolBoundaryConfinementRejectsTraversal() {
        val orchestrator = SupervisorOrchestrator(
            workspaceRoot = workspaceRoot,
            editorController = editorController,
            terminalSession = terminalSession,
            auditEngine = auditEngine
        )

        val safeRes = orchestrator.resolveConfinedFile("src/main.kt")
        assertTrue("Subpath within workspace must succeed", safeRes.isSuccess)

        val evilRes = orchestrator.resolveConfinedFile("../../../../etc/passwd")
        assertTrue("Traversal path must fail", evilRes.isFailure)
        assertTrue("Error must be SecurityException", evilRes.exceptionOrNull() is SecurityException)
    }

    // -------------------------------------------------------------
    // 13. GGUF MODEL EXECUTION STATE LIFECYCLE
    // -------------------------------------------------------------
    @Test
    fun testGgufModelExecutionStateLifecycle() = runBlocking {
        val validGgufFile = File(workspaceRoot, "model.gguf").apply {
            val bytes = ByteArray(32)
            bytes[0] = 0x47.toByte()
            bytes[1] = 0x47.toByte()
            bytes[2] = 0x55.toByte()
            bytes[3] = 0x46.toByte()
            bytes[4] = 0x03.toByte()
            writeBytes(bytes)
        }

        val modelDescriptor = ModelDescriptor(
            id = "lifecycle-model",
            name = "Lifecycle Model",
            parameters = "1B",
            quantization = GgufQuantization.Q4_K,
            fileSizeBytes = 2000L,
            localFilePath = validGgufFile.absolutePath
        )

        val backend = LocalGgufCpuBackend(modelDescriptor)
        assertEquals(com.aether.inference.ModelExecutionState.READY, backend.executionState.value)

        val loadRes = backend.loadModel()
        assertTrue("Model load should succeed with valid GGUF header", loadRes.isSuccess)
        assertEquals(com.aether.inference.ModelExecutionState.LOADED, backend.executionState.value)

        val unloadRes = backend.unloadModel()
        assertTrue("Model unload should succeed", unloadRes.isSuccess)
        assertEquals(com.aether.inference.ModelExecutionState.READY, backend.executionState.value)
    }

    // -------------------------------------------------------------
    // 14. GOOGLE DRIVE OAUTH LIFECYCLE TESTS
    // -------------------------------------------------------------
    @Test
    fun testGoogleDriveOAuthManagerSessionLifecycle() = runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val oauthManager = com.aether.storage.GoogleDriveOAuthManager(context)

        // Initial state
        assertEquals(com.aether.storage.DriveOAuthState.DISCONNECTED, oauthManager.authState.value)

        // Save session
        val saved = oauthManager.saveSession(
            accessToken = "mock_access_token_12345",
            refreshToken = "mock_refresh_token_67890",
            expiresInSeconds = 3600L,
            email = "engineer@example.com"
        )
        assertTrue("Session save should succeed", saved)
        assertEquals(com.aether.storage.DriveOAuthState.AUTHENTICATED, oauthManager.authState.value)
        assertEquals("engineer@example.com", oauthManager.currentAccount.value?.email)

        // Active token retrieval
        val token = oauthManager.getActiveAccessToken()
        assertEquals("mock_access_token_12345", token)

        // Disconnect and revoke
        val disconnectRes = oauthManager.disconnectAndRevoke()
        assertTrue("Disconnect should succeed", disconnectRes.isSuccess)
        assertEquals(com.aether.storage.DriveOAuthState.DISCONNECTED, oauthManager.authState.value)
        assertNull(oauthManager.getActiveAccessToken())
    }
}
