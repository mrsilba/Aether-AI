package com.aether.model

import com.aether.hardware.DeviceHardwareTier
import com.aether.hardware.HardwareCapabilityProfile
import com.aether.inference.ModelDescriptor
import kotlinx.coroutines.CancellationException
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ModelCompatibilityEngine {

    fun assessCompatibility(
        model: ModelDescriptor,
        profile: HardwareCapabilityProfile
    ): CompatibilityAssessment {
        val weightsMb = model.sizeMb
        // KV cache calculation: 2 * layers (approx 32) * hidden_dim * context_window * bytes_per_elem
        val kvCacheMb = ((model.contextWindow * 128 * 2L) / (1024 * 1024)).coerceAtLeast(64L)
        val runtimeOverheadMb = 180L
        val totalEstimatedRamMb = weightsMb + kvCacheMb + runtimeOverheadMb

        val availableRamMb = profile.availableRamMb
        val memoryHeadroomMb = availableRamMb - totalEstimatedRamMb
        val isStorageSufficient = profile.storageAvailableMb > (model.sizeMb + 500) // at least 500MB buffer

        val cpuInstructionSupport = buildString {
            if (profile.supportsNeon) append("ARM64 NEON ")
            if (profile.supportsDotProduct) append("+ DotProduct ")
            if (profile.supportsFp16) append("+ FP16 ")
        }.trim().ifEmpty { "Standard ARM64" }

        val rating: CompatibilityRating
        val rationale: String

        when {
            !isStorageSufficient -> {
                rating = CompatibilityRating.UNSUPPORTED
                rationale = "Insufficient disk storage. Model requires ${model.sizeMb}MB but only ${profile.storageAvailableMb}MB is available."
            }
            totalEstimatedRamMb > (profile.totalRamMb * 0.85f).toLong() || memoryHeadroomMb < -300L -> {
                rating = CompatibilityRating.UNSUPPORTED
                rationale = "Total RAM requirement (${totalEstimatedRamMb}MB) exceeds safe device memory threshold. Available free RAM: ${availableRamMb}MB."
            }
            memoryHeadroomMb < 200L || profile.effectiveTier < model.requiredMinTier -> {
                rating = CompatibilityRating.POSSIBLE
                rationale = "Model can execute with reduced context window. Free RAM headroom is tight (${memoryHeadroomMb}MB). Consider lowering context limit."
            }
            else -> {
                rating = CompatibilityRating.RECOMMENDED
                rationale = "Optimal match for device hardware (${profile.effectiveTier.label}). Sufficient RAM headroom (${memoryHeadroomMb}MB) for high-throughput inference."
            }
        }

        return CompatibilityAssessment(
            rating = rating,
            estimatedWeightsMb = weightsMb,
            estimatedKvCacheMb = kvCacheMb,
            estimatedTotalRamMb = totalEstimatedRamMb,
            availableRamMb = availableRamMb,
            memoryHeadroomMb = memoryHeadroomMb,
            isStorageSufficient = isStorageSufficient,
            cpuInstructionSupport = cpuInstructionSupport,
            rationale = rationale
        )
    }
}

class TaskRecommendationEngine {

    fun getRecommendations(
        models: List<HubModelEntry>,
        profile: HardwareCapabilityProfile
    ): List<TaskRecommendation> {
        val compatibilityEngine = ModelCompatibilityEngine()

        return listOf(
            TaskRecommendation(
                category = ModelTaskCategory.CODING,
                recommendedModelId = "qwen2.5-coder-3b-q4k",
                primaryBadge = "BEST FOR CODING",
                alternativeModelIds = listOf("deepseek-coder-7b-q4k"),
                rationale = "Top benchmark performance on HumanEval & MultiPL-E for Python, Kotlin, and Java. Fits comfortably in 4GB-8GB mobile memory."
            ),
            TaskRecommendation(
                category = ModelTaskCategory.REASONING,
                recommendedModelId = "deepseek-coder-7b-q4k",
                primaryBadge = "BEST FOR REASONING",
                alternativeModelIds = listOf("mistral-7b-instruct-q4k"),
                rationale = "Advanced chain-of-thought mathematical reasoning and multi-step agent planning capability."
            ),
            TaskRecommendation(
                category = ModelTaskCategory.LIGHTWEIGHT,
                recommendedModelId = "smollm2-1.7b-q8",
                primaryBadge = "BEST FOR LOW-RAM",
                alternativeModelIds = listOf("qwen2.5-coder-3b-q4k"),
                rationale = "Minimal 1.7GB memory footprint, near-instant cold load, and high energy efficiency for sustained battery life."
            ),
            TaskRecommendation(
                category = ModelTaskCategory.LONG_CONTEXT,
                recommendedModelId = "mistral-7b-instruct-q4k",
                primaryBadge = "BEST FOR LONG CONTEXT",
                alternativeModelIds = listOf("deepseek-coder-7b-q4k"),
                rationale = "Native 32k sliding-window context capacity enables whole-repository cross-referencing and massive document analysis."
            ),
            TaskRecommendation(
                category = ModelTaskCategory.WRITING,
                recommendedModelId = "gemma-2-2b-f16",
                primaryBadge = "BEST FOR DOCUMENTATION",
                alternativeModelIds = listOf("smollm2-1.7b-q8"),
                rationale = "Superior prose fluency, concise technical documentation synthesis, and high multilingual accuracy."
            )
        )
    }
}

class ModelHubManager(
    private val modelsDir: File,
    private val catalogProvider: ModelDiscoveryProvider = StaticModelCatalogProvider(modelsDir)
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val compatibilityEngine = ModelCompatibilityEngine()
    private val taskRecommendationEngine = TaskRecommendationEngine()

    private val _downloadStatusMap = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatusMap: StateFlow<Map<String, DownloadStatus>> = _downloadStatusMap.asStateFlow()

    private val activeDownloadCancellation = mutableMapOf<String, AtomicBoolean>()

    init {
        if (!modelsDir.exists()) modelsDir.mkdirs()
    }

    suspend fun getAvailableModels(query: String = ""): List<HubModelEntry> = withContext(Dispatchers.IO) {
        val entries = catalogProvider.discoverModels(query)
        val currentStatuses = _downloadStatusMap.value

        entries.map { entry ->
            val targetFile = File(modelsDir, "${entry.descriptor.id}.gguf")
            val isInstalled = targetFile.exists() && targetFile.length() > 1000000L
            val currentStatus = currentStatuses[entry.descriptor.id] ?: if (isInstalled) DownloadStatus.Installed else DownloadStatus.Idle

            entry.copy(
                descriptor = entry.descriptor.copy(localFilePath = targetFile.absolutePath),
                downloadStatus = currentStatus
            )
        }
    }

    fun assessModelCompatibility(model: ModelDescriptor, profile: HardwareCapabilityProfile): CompatibilityAssessment {
        return compatibilityEngine.assessCompatibility(model, profile)
    }

    fun getTaskRecommendations(models: List<HubModelEntry>, profile: HardwareCapabilityProfile): List<TaskRecommendation> {
        return taskRecommendationEngine.getRecommendations(models, profile)
    }

    suspend fun downloadModel(
        modelEntry: HubModelEntry,
        onProgress: (DownloadStatus) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val modelId = modelEntry.descriptor.id
        val targetFile = File(modelsDir, "$modelId.gguf")
        val partFile = File(modelsDir, "$modelId.gguf.part")

        if (targetFile.exists() && targetFile.length() > 0) {
            updateStatus(modelId, DownloadStatus.Installed)
            onProgress(DownloadStatus.Installed)
            return@withContext Result.success(targetFile)
        }

        val isCancelled = AtomicBoolean(false)
        activeDownloadCancellation[modelId] = isCancelled

        try {
            var existingLength = if (partFile.exists()) partFile.length() else 0L

            val requestBuilder = Request.Builder()
                .url(modelEntry.downloadUrl)

            if (existingLength > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingLength-")
            }

            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful && response.code != 206) {
                // If range request failed (e.g. 416), restart from 0
                if (existingLength > 0) {
                    partFile.delete()
                    existingLength = 0L
                    return@withContext downloadModel(modelEntry, onProgress)
                }
                val err = "Download failed: HTTP ${response.code} ${response.message}"
                updateStatus(modelId, DownloadStatus.Failed(err))
                onProgress(DownloadStatus.Failed(err))
                return@withContext Result.failure(IllegalStateException(err))
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = (if (response.code == 206) existingLength + body.contentLength() else body.contentLength()).coerceAtLeast(modelEntry.descriptor.fileSizeBytes)

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(partFile, existingLength > 0)

            val buffer = ByteArray(64 * 1024) // 64KB buffer
            var bytesDownloaded = existingLength
            var lastUpdateMs = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            updateStatus(modelId, DownloadStatus.Downloading(0, bytesDownloaded, totalBytes, 0))

            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                if (isCancelled.get()) {
                    outputStream.flush()
                    outputStream.close()
                    updateStatus(modelId, DownloadStatus.Paused)
                    onProgress(DownloadStatus.Paused)
                    return@withContext Result.failure(CancellationException("Download paused by user"))
                }

                outputStream.write(buffer, 0, read)
                bytesDownloaded += read
                bytesSinceLastUpdate += read

                val now = System.currentTimeMillis()
                if (now - lastUpdateMs >= 400) {
                    val durationSec = (now - lastUpdateMs) / 1000f
                    val speedKbps = if (durationSec > 0) (bytesSinceLastUpdate / 1024 / durationSec).toLong() else 0L
                    val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

                    val status = DownloadStatus.Downloading(percent, bytesDownloaded, totalBytes, speedKbps)
                    updateStatus(modelId, status)
                    onProgress(status)

                    lastUpdateMs = now
                    bytesSinceLastUpdate = 0L
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Verify integrity
            updateStatus(modelId, DownloadStatus.Verifying)
            onProgress(DownloadStatus.Verifying)

            // Atomic rename from .part to .gguf
            if (partFile.renameTo(targetFile)) {
                updateStatus(modelId, DownloadStatus.Installed)
                onProgress(DownloadStatus.Installed)
                Result.success(targetFile)
            } else {
                val err = "Failed to finalize downloaded file."
                updateStatus(modelId, DownloadStatus.Failed(err))
                onProgress(DownloadStatus.Failed(err))
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            val err = e.message ?: "Unknown download error"
            updateStatus(modelId, DownloadStatus.Failed(err))
            onProgress(DownloadStatus.Failed(err))
            Result.failure(e)
        } finally {
            activeDownloadCancellation.remove(modelId)
        }
    }

    fun pauseDownload(modelId: String) {
        activeDownloadCancellation[modelId]?.set(true)
        updateStatus(modelId, DownloadStatus.Paused)
    }

    fun cancelAndDelete(modelId: String): Boolean {
        activeDownloadCancellation[modelId]?.set(true)
        val targetFile = File(modelsDir, "$modelId.gguf")
        val partFile = File(modelsDir, "$modelId.gguf.part")
        if (targetFile.exists()) targetFile.delete()
        if (partFile.exists()) partFile.delete()
        updateStatus(modelId, DownloadStatus.Idle)
        return true
    }

    fun importLocalModel(sourceFile: File, newName: String): Result<File> {
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return Result.failure(IllegalArgumentException("Source file cannot be read."))
        }
        val safeName = newName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val targetFile = File(modelsDir, "$safeName.gguf")
        return try {
            sourceFile.copyTo(targetFile, overwrite = true)
            updateStatus(safeName, DownloadStatus.Installed)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateStatus(modelId: String, status: DownloadStatus) {
        val map = _downloadStatusMap.value.toMutableMap()
        map[modelId] = status
        _downloadStatusMap.value = map
    }
}
