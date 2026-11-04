package com.aether.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

sealed class CommandDecision {
    data object Allowed : CommandDecision()
    data class Denied(val reason: String) : CommandDecision()
    data class RequiresConfirmation(val warning: String) : CommandDecision()
}

class CommandGuardrailEngine {
    private val allowedBinaries = setOf(
        "git", "ls", "pwd", "cat", "echo", "grep", "find", "head", "tail", "wc",
        "diff", "mkdir", "rm", "cp", "mv", "touch", "python3", "kotlinc", "javac",
        "gradle", "curl", "tar", "gzip", "unzip", "date", "whoami", "uname", "env"
    )

    private val dangerousPatterns = listOf(
        Regex("""rm\s+(-[a-zA-Z]*r[a-zA-Z]*f|--recursive\s+--force)\s+/\s*$"""),
        Regex("""chmod\s+777\s+/"""),
        Regex("""mkfs"""),
        Regex(""":\(\)\s*\{\s*:\s*\|\s*:\s*&\s*\};\s*:"""), // fork bomb
        Regex("""/system/"""),
        Regex("""/data/data/(?!com\.aistudio|com\.example)"""),
        Regex(""">>\s*/dev/sd"""),
        Regex("""reboot""")
    )

    fun evaluate(commandLine: String): CommandDecision {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return CommandDecision.Denied("Command is empty")

        for (pattern in dangerousPatterns) {
            if (pattern.containsMatchIn(trimmed)) {
                return CommandDecision.Denied("Dangerous command blocked by safety guardrails: contains restricted destructive pattern.")
            }
        }

        val firstToken = trimmed.split(Regex("""\s+""")).firstOrNull() ?: ""
        val binary = File(firstToken).name.lowercase()

        if (binary !in allowedBinaries) {
            return CommandDecision.Denied("Binary '$binary' is not in the development allowlist.")
        }

        if (binary == "rm" || binary == "mv") {
            return CommandDecision.RequiresConfirmation("File modification action: $trimmed")
        }

        return CommandDecision.Allowed
    }
}

object ShellCommandTokenizer {
    /**
     * Splits shell command lines respecting single quotes ('...'), double quotes ("..."),
     * and backslash escapes (\ ).
     */
    fun tokenize(commandLine: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var isEscaped = false

        for (ch in commandLine.trim()) {
            if (isEscaped) {
                current.append(ch)
                isEscaped = false
                continue
            }
            if (ch == '\\') {
                isEscaped = true
                continue
            }
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
                continue
            }
            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
                continue
            }
            if (ch.isWhitespace() && !inSingleQuote && !inDoubleQuote) {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }
}

data class TerminalExecutionResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
)

/**
 * Restricted Terminal Execution Engine.
 * Note: Operates within ordinary Android application UID permissions.
 * Enforces canonical path verification, symlink escape detection,
 * command allowlisting, 30s timeout watchdogs, and output size caps.
 */
class SandboxedTerminalSession(
    private val workspaceRoot: File,
    private val guardrailEngine: CommandGuardrailEngine = CommandGuardrailEngine()
) {
    companion object {
        const val MAX_OUTPUT_BYTES = 256 * 1024 // 256 KB cap per stream
    }

    init {
        if (!workspaceRoot.exists()) {
            workspaceRoot.mkdirs()
        }
    }

    /**
     * Verifies that targetDir resides within the canonical workspace boundary,
     * accounting for symlink targets.
     */
    fun verifyWorkspaceConfinement(targetDir: File): Boolean {
        return try {
            val rootCanon = workspaceRoot.canonicalFile
            val targetCanon = targetDir.canonicalFile
            targetCanon == rootCanon || targetCanon.toPath().startsWith(rootCanon.toPath())
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Detects arguments referencing files or directories escaping the canonical workspace root.
     */
    fun hasEscapedPathArgument(tokens: List<String>, workingDir: File): Boolean {
        val rootCanon = workspaceRoot.canonicalFile
        for (token in tokens.drop(1)) {
            if (token.startsWith("-")) continue
            if (token.contains("/") || token.contains("..")) {
                val candidate = if (File(token).isAbsolute) File(token) else File(workingDir, token)
                try {
                    val canon = candidate.canonicalFile
                    if (candidate.exists() && canon != rootCanon && !canon.toPath().startsWith(rootCanon.toPath())) {
                        return true
                    }
                } catch (_: Exception) {
                    return true
                }
            }
        }
        return false
    }

    suspend fun execute(
        commandLine: String,
        subDir: String = "",
        timeoutSeconds: Long = 30L,
        userConfirmed: Boolean = false,
        onOutputChunk: ((String) -> Unit)? = null
    ): Result<TerminalExecutionResult> = withContext(Dispatchers.IO) {
        val targetDir = if (subDir.isBlank()) workspaceRoot else File(workspaceRoot, subDir)

        if (!verifyWorkspaceConfinement(targetDir)) {
            return@withContext Result.failure(
                SecurityException("Restricted terminal boundary violation: Directory '$targetDir' is outside project workspace root.")
            )
        }

        when (val decision = guardrailEngine.evaluate(commandLine)) {
            is CommandDecision.Denied -> {
                return@withContext Result.failure(SecurityException(decision.reason))
            }
            is CommandDecision.RequiresConfirmation -> {
                if (!userConfirmed) {
                    return@withContext Result.failure(
                        SecurityException("Confirmation Required: ${decision.warning}. Please re-run with confirmation.")
                    )
                }
            }
            is CommandDecision.Allowed -> Unit
        }

        val tokens = ShellCommandTokenizer.tokenize(commandLine)
        if (tokens.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No command tokens parsed."))
        }

        if (hasEscapedPathArgument(tokens, targetDir)) {
            return@withContext Result.failure(
                SecurityException("Security boundary violation: Command argument refers to path outside the workspace root.")
            )
        }

        val startTime = System.currentTimeMillis()
        try {
            val pb = ProcessBuilder(tokens)
                .directory(targetDir)
                .redirectErrorStream(false)

            // Strip secret tokens and passwords from child environment
            pb.environment().keys.removeIf { key ->
                val upper = key.uppercase()
                upper.contains("KEY") || upper.contains("TOKEN") || upper.contains("SECRET") || upper.contains("PASSWORD")
            }
            pb.environment()["WORKSPACE_DIR"] = workspaceRoot.canonicalPath

            val process = pb.start()
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            // Read lines and stream with output cap
            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                if (stdoutBuilder.length < MAX_OUTPUT_BYTES) {
                    val cleanLine = redactSecrets(line ?: "")
                    stdoutBuilder.append(cleanLine).append("\n")
                    onOutputChunk?.invoke(cleanLine)
                }
            }
            while (stderrReader.readLine().also { line = it } != null) {
                if (stderrBuilder.length < MAX_OUTPUT_BYTES) {
                    val cleanLine = redactSecrets(line ?: "")
                    stderrBuilder.append(cleanLine).append("\n")
                    onOutputChunk?.invoke("[stderr] $cleanLine")
                }
            }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext Result.failure(
                    java.util.concurrent.TimeoutException("Execution timed out after ${timeoutSeconds}s.")
                )
            }

            val exitCode = process.exitValue()
            val duration = System.currentTimeMillis() - startTime

            Result.success(
                TerminalExecutionResult(
                    command = redactSecrets(commandLine),
                    exitCode = exitCode,
                    stdout = stdoutBuilder.toString().trimEnd(),
                    stderr = stderrBuilder.toString().trimEnd(),
                    durationMs = duration
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun redactSecrets(input: String): String {
        return input
            .replace(Regex("""(?i)(api[_-]?key|token|bearer|secret)\s*[:=]\s*["']?([A-Za-z0-9_-]{12,})["']?""")) {
                "${it.groupValues[1]}=REDACTED_SECRET"
            }
    }
}

// Alias for accurate documentation
typealias RestrictedTerminalSession = SandboxedTerminalSession
