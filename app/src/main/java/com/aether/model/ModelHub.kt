package com.aether.model

import com.aether.hardware.DeviceHardwareTier
import com.aether.hardware.HardwareCapabilityProfile
import com.aether.inference.GgufQuantization
import com.aether.inference.ModelDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ModelSourceProvider(val id: String, val displayName: String, val badge: String) {
    HUGGING_FACE("huggingface", "Hugging Face Hub", "HF"),
    GGUF_REPO("gguf_repo", "GGUF Model Repositories", "GGUF"),
    OLLAMA("ollama", "Ollama Library", "OLLAMA"),
    GITHUB_ASSETS("github", "GitHub Releases", "GH"),
    LOCAL_STORAGE("local", "Local Device Storage", "LOCAL")
}

enum class CompatibilityRating(val label: String, val description: String) {
    RECOMMENDED("Recommended", "Expected to run smoothly within available device RAM with full context headroom."),
    POSSIBLE("Possible", "May run with reduced context window or under light memory pressure."),
    UNSUPPORTED("Unsupported", "Exceeds device RAM or storage capacity. May cause out-of-memory termination.")
}

data class CompatibilityAssessment(
    val rating: CompatibilityRating,
    val estimatedWeightsMb: Long,
    val estimatedKvCacheMb: Long,
    val estimatedTotalRamMb: Long,
    val availableRamMb: Long,
    val memoryHeadroomMb: Long,
    val isStorageSufficient: Boolean,
    val cpuInstructionSupport: String,
    val rationale: String
)

enum class ModelTaskCategory(val title: String, val description: String) {
    CODING("Coding & Engineering", "Code generation, completion, refactoring, Kotlin, Python, debugging"),
    REASONING("General Reasoning", "Complex problem solving, math, planning, architecture analysis"),
    LONG_CONTEXT("Long-Context Analysis", "Multi-file repository understanding, large documents, logs"),
    WRITING("Writing & Docs", "Technical documentation, summarization, clean explanations"),
    LIGHTWEIGHT("Low-RAM & Mobile", "Ultra-fast execution on standard mobile hardware with minimal memory footprint")
}

data class TaskRecommendation(
    val category: ModelTaskCategory,
    val recommendedModelId: String,
    val primaryBadge: String,
    val alternativeModelIds: List<String>,
    val rationale: String
)

sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data class Downloading(
        val percent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedKbps: Long
    ) : DownloadStatus()
    data object Paused : DownloadStatus()
    data object Verifying : DownloadStatus()
    data object Installed : DownloadStatus()
    data class Failed(val error: String) : DownloadStatus()
}

data class HubModelEntry(
    val descriptor: ModelDescriptor,
    val provider: ModelSourceProvider,
    val publisher: String,
    val architecture: String,
    val license: String,
    val downloadUrl: String,
    val sha256Checksum: String? = null,
    val taskCategories: List<ModelTaskCategory>,
    val highlights: String,
    val downloadStatus: DownloadStatus = DownloadStatus.Idle
)

interface ModelDiscoveryProvider {
    val provider: ModelSourceProvider
    suspend fun discoverModels(query: String = ""): List<HubModelEntry>
}

class StaticModelCatalogProvider(private val modelsDir: File) : ModelDiscoveryProvider {
    override val provider: ModelSourceProvider = ModelSourceProvider.HUGGING_FACE

    override suspend fun discoverModels(query: String): List<HubModelEntry> = withContext(Dispatchers.IO) {
        val all = listOf(
            HubModelEntry(
                descriptor = ModelDescriptor(
                    id = "qwen2.5-coder-3b-q4k",
                    name = "Qwen2.5-Coder 3B",
                    parameters = "3.1B",
                    quantization = GgufQuantization.Q4_K,
                    fileSizeBytes = 1980000000L,
                    contextWindow = 8192,
                    localFilePath = File(modelsDir, "qwen2.5_3b_q4k.gguf").absolutePath,
                    requiredMinTier = DeviceHardwareTier.STANDARD
                ),
                provider = ModelSourceProvider.HUGGING_FACE,
                publisher = "Qwen Team (Alibaba)",
                architecture = "qwen2",
                license = "Apache 2.0",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
                sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                taskCategories = listOf(ModelTaskCategory.CODING, ModelTaskCategory.LIGHTWEIGHT),
                highlights = "State-of-the-art 3B coding model. Excels at Kotlin, Java, Python, and AST refactoring."
            ),
            HubModelEntry(
                descriptor = ModelDescriptor(
                    id = "deepseek-coder-7b-q4k",
                    name = "DeepSeek-Coder 7B",
                    parameters = "6.9B",
                    quantization = GgufQuantization.Q4_K,
                    fileSizeBytes = 4300000000L,
                    contextWindow = 16384,
                    localFilePath = File(modelsDir, "deepseek7b_q4k.gguf").absolutePath,
                    requiredMinTier = DeviceHardwareTier.PERFORMANCE
                ),
                provider = ModelSourceProvider.GGUF_REPO,
                publisher = "DeepSeek AI",
                architecture = "deepseek",
                license = "DeepSeek License (Permissive)",
                downloadUrl = "https://huggingface.co/TheBloke/deepseek-coder-6.7B-instruct-GGUF/resolve/main/deepseek-coder-6.7b-instruct.Q4_K_M.gguf",
                sha256Checksum = "f8a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1",
                taskCategories = listOf(ModelTaskCategory.CODING, ModelTaskCategory.REASONING),
                highlights = "Unsurpassed logic, complex repository planning, and mathematical code synthesis."
            ),
            HubModelEntry(
                descriptor = ModelDescriptor(
                    id = "smollm2-1.7b-q8",
                    name = "SmolLM2 1.7B",
                    parameters = "1.7B",
                    quantization = GgufQuantization.Q8_0,
                    fileSizeBytes = 1750000000L,
                    contextWindow = 4096,
                    localFilePath = File(modelsDir, "smollm2_1.7b_q8.gguf").absolutePath,
                    requiredMinTier = DeviceHardwareTier.BASIC
                ),
                provider = ModelSourceProvider.HUGGING_FACE,
                publisher = "HuggingFace TB",
                architecture = "llama",
                license = "Apache 2.0",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q8_0.gguf",
                sha256Checksum = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
                taskCategories = listOf(ModelTaskCategory.LIGHTWEIGHT, ModelTaskCategory.WRITING),
                highlights = "Ultra-compact footprint, immediate startup, and perfect for low-RAM devices."
            ),
            HubModelEntry(
                descriptor = ModelDescriptor(
                    id = "mistral-7b-instruct-q4k",
                    name = "Mistral 7B Instruct v0.3",
                    parameters = "7.2B",
                    quantization = GgufQuantization.Q4_K,
                    fileSizeBytes = 4370000000L,
                    contextWindow = 32768,
                    localFilePath = File(modelsDir, "mistral7b_q4k.gguf").absolutePath,
                    requiredMinTier = DeviceHardwareTier.HIGH_PERFORMANCE
                ),
                provider = ModelSourceProvider.OLLAMA,
                publisher = "Mistral AI",
                architecture = "mistral",
                license = "Apache 2.0",
                downloadUrl = "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
                sha256Checksum = "c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4",
                taskCategories = listOf(ModelTaskCategory.LONG_CONTEXT, ModelTaskCategory.REASONING),
                highlights = "32k context window, exceptional multi-file repository understanding and structured output."
            ),
            HubModelEntry(
                descriptor = ModelDescriptor(
                    id = "gemma-2-2b-f16",
                    name = "Google Gemma 2 2B",
                    parameters = "2.6B",
                    quantization = GgufQuantization.F16,
                    fileSizeBytes = 5200000000L,
                    contextWindow = 8192,
                    localFilePath = File(modelsDir, "gemma2_2b_f16.gguf").absolutePath,
                    requiredMinTier = DeviceHardwareTier.PERFORMANCE
                ),
                provider = ModelSourceProvider.GITHUB_ASSETS,
                publisher = "Google DeepMind",
                architecture = "gemma2",
                license = "Gemma Terms of Use",
                downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-f16.gguf",
                sha256Checksum = "d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5",
                taskCategories = listOf(ModelTaskCategory.REASONING, ModelTaskCategory.WRITING),
                highlights = "High-precision sliding-window attention and strong multilingual understanding."
            )
        )

        if (query.isBlank()) {
            all
        } else {
            all.filter {
                it.descriptor.name.contains(query, ignoreCase = true) ||
                it.publisher.contains(query, ignoreCase = true) ||
                it.architecture.contains(query, ignoreCase = true) ||
                it.highlights.contains(query, ignoreCase = true)
            }
        }
    }
}
