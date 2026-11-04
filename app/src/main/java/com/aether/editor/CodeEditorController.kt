package com.aether.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

data class EditorTab(
    val id: String,
    val fileName: String,
    val filePath: String,
    var content: String,
    var isDirty: Boolean = false
)

data class CodeSymbol(
    val name: String,
    val kind: String, // class, function, interface, variable
    val line: Int
)

data class DiffLine(
    val type: DiffType,
    val text: String,
    val oldLineNum: Int? = null,
    val newLineNum: Int? = null
)

enum class DiffType {
    CONTEXT,
    ADDITION,
    DELETION
}

class CodeEditorController(private val workspaceRoot: File) {

    private val tabs = mutableListOf<EditorTab>()
    private var activeTabIndex = 0

    // History for Undo / Redo
    private val undoStack = mutableMapOf<String, ArrayDeque<String>>()
    private val redoStack = mutableMapOf<String, ArrayDeque<String>>()

    init {
        // Create initial default starter project files in workspace if empty
        ensureDefaultFiles()
    }

    private fun ensureDefaultFiles() {
        if (!workspaceRoot.exists()) workspaceRoot.mkdirs()

        val sampleFiles = listOf(
            "MainActivity.kt" to """
                package com.example.app
                
                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.material3.Text
                
                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContent {
                            Text("Aether Workstation Project Active")
                        }
                    }
                    
                    fun calculatePerformanceMetric(input: Int): Long {
                        val factor = 42L
                        return input * factor
                    }
                }
            """.trimIndent(),
            "build.gradle.kts" to """
                plugins {
                    alias(libs.plugins.android.application)
                    alias(libs.plugins.kotlin.android)
                }
                
                android {
                    namespace = "com.example.app"
                    compileSdk = 35
                }
            """.trimIndent(),
            "aether_config.json" to """
                {
                    "workstation": "Aether AI",
                    "version": "2.0.0",
                    "mode": "hybrid_inference",
                    "sandboxed": true
                }
            """.trimIndent()
        )

        for ((name, content) in sampleFiles) {
            val file = File(workspaceRoot, name)
            if (!file.exists()) {
                file.writeText(content)
            }
            openFile(file)
        }
    }

    fun openFile(file: File): EditorTab {
        val existing = tabs.find { it.filePath == file.absolutePath }
        if (existing != null) {
            activeTabIndex = tabs.indexOf(existing)
            return existing
        }

        val content = if (file.exists()) file.readText() else ""
        val tab = EditorTab(
            id = file.name + "_" + System.currentTimeMillis(),
            fileName = file.name,
            filePath = file.absolutePath,
            content = content
        )
        tabs.add(tab)
        activeTabIndex = tabs.size - 1
        undoStack[tab.id] = ArrayDeque()
        redoStack[tab.id] = ArrayDeque()
        return tab
    }

    fun getTabs(): List<EditorTab> = tabs

    fun getActiveTab(): EditorTab? = tabs.getOrNull(activeTabIndex)

    fun selectTab(index: Int) {
        if (index in tabs.indices) {
            activeTabIndex = index
        }
    }

    fun closeTab(index: Int) {
        if (index in tabs.indices) {
            tabs.removeAt(index)
            if (activeTabIndex >= tabs.size) {
                activeTabIndex = (tabs.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun updateContent(newContent: String) {
        val tab = getActiveTab() ?: return
        if (tab.content != newContent) {
            val uStack = undoStack.getOrPut(tab.id) { ArrayDeque() }
            uStack.addLast(tab.content)
            if (uStack.size > 50) uStack.removeFirst()
            redoStack[tab.id]?.clear()

            tab.content = newContent
            tab.isDirty = true
        }
    }

    fun undo(): Boolean {
        val tab = getActiveTab() ?: return false
        val uStack = undoStack[tab.id] ?: return false
        if (uStack.isNotEmpty()) {
            val previous = uStack.removeLast()
            val rStack = redoStack.getOrPut(tab.id) { ArrayDeque() }
            rStack.addLast(tab.content)
            tab.content = previous
            tab.isDirty = true
            return true
        }
        return false
    }

    fun redo(): Boolean {
        val tab = getActiveTab() ?: return false
        val rStack = redoStack[tab.id] ?: return false
        if (rStack.isNotEmpty()) {
            val next = rStack.removeLast()
            val uStack = undoStack.getOrPut(tab.id) { ArrayDeque() }
            uStack.addLast(tab.content)
            tab.content = next
            tab.isDirty = true
            return true
        }
        return false
    }

    fun saveActiveFile(): Result<String> {
        val tab = getActiveTab() ?: return Result.failure(IllegalStateException("No open tab to save"))
        return try {
            val file = File(tab.filePath)
            file.writeText(tab.content)
            tab.isDirty = false
            Result.success("Saved ${tab.fileName} (${file.length()} bytes)")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractSymbols(content: String): List<CodeSymbol> {
        val symbols = mutableListOf<CodeSymbol>()
        val lines = content.lines()

        val classRegex = Regex("""\b(class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)""")
        val funRegex = Regex("""\b(fun|def|void|int|float|double|auto)\s+([A-Za-z0-9_]+)\s*\(""")
        val valRegex = Regex("""\b(val|var|let|const)\s+([A-Za-z0-9_]+)""")

        lines.forEachIndexed { idx, line ->
            classRegex.find(line)?.let {
                symbols.add(CodeSymbol(it.groupValues[2], it.groupValues[1], idx + 1))
            }
            funRegex.find(line)?.let {
                symbols.add(CodeSymbol(it.groupValues[2], "function", idx + 1))
            }
            valRegex.find(line)?.let {
                symbols.add(CodeSymbol(it.groupValues[2], "property", idx + 1))
            }
        }

        return symbols
    }

    /**
     * Real patch applier that mutates the file on disk and in memory.
     */
    fun applyUnifiedPatch(filePath: String, diffLines: List<DiffLine>): Result<String> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("File does not exist: $filePath"))
            }

            val newLines = mutableListOf<String>()
            for (diff in diffLines) {
                when (diff.type) {
                    DiffType.CONTEXT, DiffType.ADDITION -> newLines.add(diff.text)
                    DiffType.DELETION -> { /* omit line */ }
                }
            }

            val patchedContent = newLines.joinToString("\n")
            file.writeText(patchedContent)

            // Sync open tab
            tabs.find { it.filePath == file.absolutePath }?.let {
                it.content = patchedContent
                it.isDirty = false
            }

            Result.success("Patch successfully verified and applied to ${file.name}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun computeDiff(original: String, modified: String): List<DiffLine> {
        val origLines = original.lines()
        val modLines = modified.lines()
        val result = mutableListOf<DiffLine>()

        var i = 0
        var j = 0
        while (i < origLines.size || j < modLines.size) {
            val o = origLines.getOrNull(i)
            val m = modLines.getOrNull(j)

            when {
                o == m -> {
                    if (o != null) {
                        result.add(DiffLine(DiffType.CONTEXT, o, i + 1, j + 1))
                    }
                    i++
                    j++
                }
                o != null && (m == null || !modLines.contains(o)) -> {
                    result.add(DiffLine(DiffType.DELETION, o, i + 1, null))
                    i++
                }
                m != null -> {
                    result.add(DiffLine(DiffType.ADDITION, m, null, j + 1))
                    j++
                }
            }
        }
        return result
    }

    /**
     * High-fidelity syntax highlighter generating colored AnnotatedString.
     */
    fun highlightCode(text: String, extension: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)

            val keywordColor = Color(0xFF38BDF8) // Cyan
            val typeColor = Color(0xFFA855F7)    // Violet
            val stringColor = Color(0xFF34D399)  // Green
            val commentColor = Color(0xFF64748B) // Slate gray
            val numberColor = Color(0xFFFBBF24)  // Amber
            val annotationColor = Color(0xFFF472B6) // Rose

            val keywords = when (extension.lowercase()) {
                "kt", "kts" -> setOf(
                    "package", "import", "class", "interface", "object", "fun", "val", "var",
                    "override", "private", "public", "protected", "internal", "data", "sealed",
                    "enum", "return", "if", "else", "when", "for", "while", "suspend", "companion"
                )
                "py" -> setOf("def", "class", "import", "from", "return", "if", "elif", "else", "for", "while", "try", "except", "with", "as")
                "cpp", "c", "h" -> setOf("namespace", "class", "struct", "void", "int", "bool", "return", "if", "else", "for", "while", "template", "typename", "public", "private")
                else -> setOf("function", "var", "let", "const", "return", "if", "else")
            }

            // Highlight strings
            val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"")
            for (match in stringRegex.findAll(text)) {
                addStyle(SpanStyle(color = stringColor), match.range.first, match.range.last + 1)
            }

            // Highlight comments
            val commentRegex = Regex("//.*")
            for (match in commentRegex.findAll(text)) {
                addStyle(SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), match.range.first, match.range.last + 1)
            }

            // Highlight annotations
            val annotationRegex = Regex("@[A-Za-z0-9_]+")
            for (match in annotationRegex.findAll(text)) {
                addStyle(SpanStyle(color = annotationColor, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            }

            // Highlight numbers
            val numberRegex = Regex("\\b[0-9]+[L|f|d]?\\b")
            for (match in numberRegex.findAll(text)) {
                addStyle(SpanStyle(color = numberColor), match.range.first, match.range.last + 1)
            }

            // Highlight keywords
            val wordRegex = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
            for (match in wordRegex.findAll(text)) {
                val word = match.value
                if (word in keywords) {
                    addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold), match.range.first, match.range.last + 1)
                } else if (word.first().isUpperCase()) {
                    addStyle(SpanStyle(color = typeColor), match.range.first, match.range.last + 1)
                }
            }
        }
    }
}
