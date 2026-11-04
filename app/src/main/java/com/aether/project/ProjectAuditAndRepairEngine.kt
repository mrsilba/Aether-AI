package com.aether.project

import com.aether.editor.DiffLine
import com.aether.editor.DiffType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class FindingSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

data class AuditFinding(
    val id: String,
    val filePath: String,
    val lineNumber: Int,
    val title: String,
    val description: String,
    val severity: FindingSeverity,
    val originalSnippet: String,
    val suggestedSnippet: String
) {
    fun toDiffLines(): List<DiffLine> {
        val origList = originalSnippet.lines().map { DiffLine(DiffType.DELETION, it) }
        val suggList = suggestedSnippet.lines().map { DiffLine(DiffType.ADDITION, it) }
        return origList + suggList
    }
}

data class ProjectAuditReport(
    val scannedFilesCount: Int,
    val findings: List<AuditFinding>,
    val scanDurationMs: Long,
    val healthScore: Int
)

class ProjectAuditAndRepairEngine(private val workspaceRoot: File) {

    suspend fun runFullAudit(): ProjectAuditReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val findings = mutableListOf<AuditFinding>()
        var scannedFiles = 0

        if (workspaceRoot.exists()) {
            workspaceRoot.walkTopDown()
                .filter { it.isFile && isAuditable(it) }
                .forEach { file ->
                    scannedFiles++
                    auditFile(file, findings)
                }
        }

        val duration = System.currentTimeMillis() - startTime
        val criticals = findings.count { it.severity == FindingSeverity.CRITICAL }
        val highs = findings.count { it.severity == FindingSeverity.HIGH }
        val mediums = findings.count { it.severity == FindingSeverity.MEDIUM }

        val deductions = (criticals * 30) + (highs * 15) + (mediums * 5)
        val healthScore = (100 - deductions).coerceIn(0, 100)

        ProjectAuditReport(
            scannedFilesCount = scannedFiles,
            findings = findings,
            scanDurationMs = duration,
            healthScore = healthScore
        )
    }

    private fun isAuditable(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in setOf("kt", "java", "kts", "xml", "json", "properties", "cpp", "py", "sh")
    }

    private fun auditFile(file: File, findings: MutableList<AuditFinding>) {
        val lines = try { file.readLines() } catch (_: Exception) { return }
        val relativePath = file.relativeTo(workspaceRoot).path

        lines.forEachIndexed { index, line ->
            val lineNum = index + 1

            // 1. Hardcoded Secret Detection
            val secretMatch = Regex("""(?i)(api_?key|password|token|secret)\s*[:=]\s*["']([^"'\s]{8,})["']""").find(line)
            if (secretMatch != null && !line.contains("BuildConfig") && !line.contains("System.getenv")) {
                val keyName = secretMatch.groupValues[1]
                findings.add(
                    AuditFinding(
                        id = "SEC-SECRET-${file.name}-$lineNum",
                        filePath = file.absolutePath,
                        lineNumber = lineNum,
                        title = "Hardcoded Secret / API Key Detected",
                        description = "Key identifier '$keyName' is stored in plain source code. Inject via BuildConfig or Android Keystore instead.",
                        severity = FindingSeverity.CRITICAL,
                        originalSnippet = line,
                        suggestedSnippet = line.replace(secretMatch.groupValues[0], "$keyName = BuildConfig.API_KEY")
                    )
                )
            }

            // 2. Cleartext Insecure HTTP Detection
            val httpMatch = Regex("""http://(?!localhost|127\.0\.0\.1)[a-zA-Z0-9.-]+""").find(line)
            if (httpMatch != null) {
                findings.add(
                    AuditFinding(
                        id = "SEC-HTTP-${file.name}-$lineNum",
                        filePath = file.absolutePath,
                        lineNumber = lineNum,
                        title = "Cleartext HTTP Connection",
                        description = "Insecure cleartext transmission is vulnerable to man-in-the-middle attacks.",
                        severity = FindingSeverity.HIGH,
                        originalSnippet = line,
                        suggestedSnippet = line.replace("http://", "https://")
                    )
                )
            }

            // 3. Kotlin/Java Unchecked Println / Logging in Production
            if (line.contains("println(") && (file.extension == "kt" || file.extension == "java")) {
                findings.add(
                    AuditFinding(
                        id = "QUAL-PRINTLN-${file.name}-$lineNum",
                        filePath = file.absolutePath,
                        lineNumber = lineNum,
                        title = "Standard Output Print Statement",
                        description = "Replace standard println with structured Log.d/Log.i or Timber to avoid performance leaks.",
                        severity = FindingSeverity.LOW,
                        originalSnippet = line,
                        suggestedSnippet = line.replace("println(", "android.util.Log.d(\"Aether\", ")
                    )
                )
            }

            // 4. Insecure Raw SQL Concatenation
            if (line.contains("rawQuery(") && line.contains("+")) {
                findings.add(
                    AuditFinding(
                        id = "SEC-SQLI-${file.name}-$lineNum",
                        filePath = file.absolutePath,
                        lineNumber = lineNum,
                        title = "Potential SQL Injection Vulnerability",
                        description = "Dynamic string concatenation in raw SQL query. Use parameterized arguments instead.",
                        severity = FindingSeverity.HIGH,
                        originalSnippet = line,
                        suggestedSnippet = "// Use parameterized query with Room or selectionArgs"
                    )
                )
            }

            // 5. Unescaped Nested Quotes Syntax Defect (Fixing DeveloperLibraryAndToolRegistry pattern)
            if (line.contains("Cipher.getInstance(\"AES/GCM/NoPadding\")") && line.contains("codeExample = \"Cipher")) {
                findings.add(
                    AuditFinding(
                        id = "SYNTAX-QUOTE-${file.name}-$lineNum",
                        filePath = file.absolutePath,
                        lineNumber = lineNum,
                        title = "Malformed Nested String Quotes",
                        description = "Unescaped quotes inside string literal cause immediate Kotlin syntax compilation failure.",
                        severity = FindingSeverity.CRITICAL,
                        originalSnippet = line,
                        suggestedSnippet = line.replace("\"AES/GCM/NoPadding\"", "\\\"AES/GCM/NoPadding\\\"")
                    )
                )
            }
        }
    }
}
