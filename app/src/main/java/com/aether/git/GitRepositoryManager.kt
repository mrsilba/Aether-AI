package com.aether.git

import com.aether.terminal.SandboxedTerminalSession
import com.aether.terminal.TerminalExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class GitRemoteRepository(
    val url: String,
    val owner: String,
    val name: String,
    val defaultBranch: String = "main",
    val isPrivate: Boolean = false
)

data class GitWorkingStatus(
    val currentBranch: String,
    val isClean: Boolean,
    val stagedFiles: List<String>,
    val modifiedFiles: List<String>,
    val untrackedFiles: List<String>,
    val remoteSyncStatus: String // "Synchronized", "Ahead by 1", "Behind by 2"
)

data class ProjectStructureMap(
    val projectType: String, // "Android / Gradle", "Node.js / TypeScript", "Python / FastAPI", "Generic"
    val primaryLanguage: String,
    val buildSystem: String,
    val rootFiles: List<String>,
    val keyEntryPoints: List<String>,
    val dependenciesSummary: String,
    val totalFilesCount: Int
)

class GitRepositoryManager(
    private val workspaceRoot: File,
    private val terminalSession: SandboxedTerminalSession
) {

    suspend fun cloneRepository(
        repoUrl: String,
        targetSubDir: String = "",
        branch: String? = null,
        personalAccessToken: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetDir = if (targetSubDir.isBlank()) workspaceRoot else File(workspaceRoot, targetSubDir)
        if (!targetDir.exists()) targetDir.mkdirs()

        // Authenticate via token if provided: https://x-access-token:<PAT>@github.com/...
        val authenticatedUrl = if (!personalAccessToken.isNullOrBlank() && repoUrl.startsWith("https://github.com/")) {
            val stripped = repoUrl.removePrefix("https://github.com/")
            "https://x-access-token:$personalAccessToken@github.com/$stripped"
        } else {
            repoUrl
        }

        val branchArg = if (!branch.isNullOrBlank()) "-b $branch" else ""
        val cloneTarget = if (targetSubDir.isBlank()) "." else targetSubDir
        val cmd = "git clone $branchArg $authenticatedUrl $cloneTarget".trim()

        val result = terminalSession.execute(
            commandLine = cmd,
            subDir = "",
            timeoutSeconds = 90L,
            userConfirmed = true
        )

        result.fold(
            onSuccess = { res ->
                if (res.exitCode == 0) {
                    Result.success("Repository cloned successfully into '${targetDir.name}'")
                } else {
                    Result.failure(IllegalStateException("Git clone failed (exit ${res.exitCode}): ${res.stderr}"))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun getWorkingStatus(subDir: String = ""): Result<GitWorkingStatus> = withContext(Dispatchers.IO) {
        val result = terminalSession.execute(
            commandLine = "git status -s -b",
            subDir = subDir,
            timeoutSeconds = 15L,
            userConfirmed = true
        )

        result.fold(
            onSuccess = { res ->
                val lines = res.stdout.lines().filter { it.isNotBlank() }
                var currentBranch = "main"
                var syncStatus = "Synchronized"
                val staged = mutableListOf<String>()
                val modified = mutableListOf<String>()
                val untracked = mutableListOf<String>()

                for (line in lines) {
                    if (line.startsWith("##")) {
                        // Header: ## main...origin/main [ahead 1]
                        val branchInfo = line.removePrefix("##").trim()
                        val parts = branchInfo.split("...")
                        currentBranch = parts.firstOrNull()?.trim() ?: "main"
                        syncStatus = when {
                            branchInfo.contains("ahead") -> "Ahead of remote"
                            branchInfo.contains("behind") -> "Behind remote"
                            branchInfo.contains("diverged") -> "Diverged"
                            else -> "Synchronized with remote"
                        }
                    } else if (line.length >= 3) {
                        val indexState = line[0]
                        val workTreeState = line[1]
                        val path = line.substring(3).trim()

                        if (indexState != ' ' && indexState != '?') staged.add(path)
                        if (workTreeState == 'M' || workTreeState == 'D') modified.add(path)
                        if (indexState == '?' && workTreeState == '?') untracked.add(path)
                    }
                }

                val isClean = staged.isEmpty() && modified.isEmpty() && untracked.isEmpty()

                Result.success(
                    GitWorkingStatus(
                        currentBranch = currentBranch,
                        isClean = isClean,
                        stagedFiles = staged,
                        modifiedFiles = modified,
                        untrackedFiles = untracked,
                        remoteSyncStatus = syncStatus
                    )
                )
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun getBranches(subDir: String = ""): Result<List<String>> = withContext(Dispatchers.IO) {
        val result = terminalSession.execute(
            commandLine = "git branch -a",
            subDir = subDir,
            timeoutSeconds = 10L,
            userConfirmed = true
        )

        result.fold(
            onSuccess = { res ->
                val branches = res.stdout.lines()
                    .map { it.replace("*", "").trim() }
                    .filter { it.isNotBlank() }
                Result.success(branches)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun switchBranch(branchName: String, createNew: Boolean = false, subDir: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val createFlag = if (createNew) "-b" else ""
        val cmd = "git checkout $createFlag $branchName".trim()

        val result = terminalSession.execute(cmd, subDir = subDir, userConfirmed = true)
        result.fold(
            onSuccess = { res ->
                if (res.exitCode == 0) Result.success("Switched to branch '$branchName'")
                else Result.failure(IllegalStateException(res.stderr))
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun stageAndCommit(commitMessage: String, subDir: String = ""): Result<String> = withContext(Dispatchers.IO) {
        if (commitMessage.isBlank()) return@withContext Result.failure(IllegalArgumentException("Commit message cannot be empty"))

        val safeMsg = commitMessage.replace("\"", "\\\"")
        val cmd = "git add -A && git commit -m \"$safeMsg\""

        val result = terminalSession.execute(cmd, subDir = subDir, userConfirmed = true)
        result.fold(
            onSuccess = { res ->
                if (res.exitCode == 0) Result.success(res.stdout.trim())
                else Result.failure(IllegalStateException(res.stderr.ifBlank { res.stdout }))
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun getDiff(subDir: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val result = terminalSession.execute("git diff", subDir = subDir, userConfirmed = true)
        result.fold(
            onSuccess = { Result.success(it.stdout) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun pull(remote: String = "origin", branch: String = "main", subDir: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val result = terminalSession.execute("git pull $remote $branch", subDir = subDir, timeoutSeconds = 60L, userConfirmed = true)
        result.fold(
            onSuccess = { res ->
                if (res.exitCode == 0) Result.success("Git pull successful: ${res.stdout.trim()}")
                else Result.failure(IllegalStateException(res.stderr))
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun push(remote: String = "origin", branch: String = "main", subDir: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val result = terminalSession.execute("git push $remote $branch", subDir = subDir, timeoutSeconds = 60L, userConfirmed = true)
        result.fold(
            onSuccess = { res ->
                if (res.exitCode == 0) Result.success("Git push successful: ${res.stdout.trim()}")
                else Result.failure(IllegalStateException(res.stderr))
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * Inspects the project directory structure to detect project type, build system, and entry points.
     */
    fun analyzeProjectStructure(targetDir: File = workspaceRoot): ProjectStructureMap {
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return ProjectStructureMap("Empty", "None", "None", emptyList(), emptyList(), "No files", 0)
        }

        val allFiles = targetDir.walkTopDown().maxDepth(4).filter { it.isFile }.toList()
        val rootFiles = targetDir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()

        val hasGradle = rootFiles.any { it.contains("gradle") }
        val hasPackageJson = rootFiles.contains("package.json")
        val hasCargo = rootFiles.contains("Cargo.toml")
        val hasPython = rootFiles.any { it == "requirements.txt" || it == "setup.py" || it == "pyproject.toml" }
        val hasMaven = rootFiles.contains("pom.xml")

        val projectType: String
        val buildSystem: String
        val primaryLanguage: String

        when {
            hasGradle -> {
                projectType = "Android / Kotlin Multiplatform"
                buildSystem = "Gradle Kotlin DSL"
                primaryLanguage = "Kotlin / Java"
            }
            hasPackageJson -> {
                projectType = "Node.js / Web Application"
                buildSystem = "npm / yarn"
                primaryLanguage = "TypeScript / JavaScript"
            }
            hasPython -> {
                projectType = "Python Service / AI Agent"
                buildSystem = "pip / poetry"
                primaryLanguage = "Python"
            }
            hasCargo -> {
                projectType = "Rust Native Binary"
                buildSystem = "Cargo"
                primaryLanguage = "Rust"
            }
            hasMaven -> {
                projectType = "Java Enterprise"
                buildSystem = "Apache Maven"
                primaryLanguage = "Java"
            }
            else -> {
                projectType = "Generic Software Workspace"
                buildSystem = "Custom / Shell"
                primaryLanguage = "Multi-Language"
            }
        }

        val entryPoints = allFiles.filter {
            val n = it.name.lowercase()
            n == "mainactivity.kt" || n == "main.kt" || n == "index.ts" || n == "index.js" || n == "app.py" || n == "main.py" || n == "main.rs"
        }.map { it.relativeTo(targetDir).path }

        val depSummary = buildString {
            if (hasGradle) append("Dependencies: Gradle Version Catalog / build.gradle.kts\n")
            if (hasPackageJson) append("Dependencies: Node modules in package.json\n")
            if (hasPython) append("Dependencies: Python requirements.txt\n")
        }.trim().ifEmpty { "Standard source tree" }

        return ProjectStructureMap(
            projectType = projectType,
            primaryLanguage = primaryLanguage,
            buildSystem = buildSystem,
            rootFiles = rootFiles.take(12),
            keyEntryPoints = entryPoints,
            dependenciesSummary = depSummary,
            totalFilesCount = allFiles.size
        )
    }
}
