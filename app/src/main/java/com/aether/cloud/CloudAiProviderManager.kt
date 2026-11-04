package com.aether.cloud

import android.content.Context
import android.content.SharedPreferences
import com.aether.security.SecurityEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class CloudProviderType(val id: String, val displayName: String, val defaultModel: String, val pricePerMillionTokens: Float) {
    GEMINI("gemini", "Google Gemini", "gemini-2.5-flash", 0.075f),
    ANTHROPIC("anthropic", "Anthropic Claude", "claude-3-5-sonnet-20241022", 3.00f),
    OPENAI("openai", "OpenAI GPT-4o", "gpt-4o-mini", 0.15f),
    DEEPSEEK("deepseek", "DeepSeek Cloud", "deepseek-chat", 0.14f),
    OPENROUTER("openrouter", "OpenRouter Multi-Provider", "auto", 0.20f)
}

data class CloudUsageRecord(
    val provider: CloudProviderType,
    val promptTokens: Int,
    val completionTokens: Int,
    val estimatedCostUsd: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

class CloudAiProviderManager(
    private val context: Context,
    private val securityEngine: SecurityEngine = SecurityEngine()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aether_cloud_vault", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val isCancelled = AtomicBoolean(false)

    fun storeApiKey(provider: CloudProviderType, apiKey: String) {
        if (apiKey.isBlank()) {
            prefs.edit().remove("key_${provider.id}").apply()
            return
        }
        val encrypted = securityEngine.encryptString(apiKey)
        prefs.edit().putString("key_${provider.id}", encrypted).apply()
    }

    fun getApiKey(provider: CloudProviderType): String? {
        val encrypted = prefs.getString("key_${provider.id}", null) ?: return null
        return securityEngine.decryptString(encrypted)
    }

    fun hasApiKey(provider: CloudProviderType): Boolean {
        return !getApiKey(provider).isNullOrBlank()
    }

    fun cancelActiveStream() {
        isCancelled.set(true)
    }

    fun streamCompletion(
        provider: CloudProviderType,
        prompt: String,
        systemPrompt: String? = null,
        modelOverride: String? = null,
        maxTokens: Int = 1024,
        onUsageRecorded: ((CloudUsageRecord) -> Unit)? = null
    ): Flow<String> = flow {
        isCancelled.set(false)
        val apiKey = getApiKey(provider)

        if (apiKey.isNullOrBlank()) {
            emit("Error: API Key for ${provider.displayName} is not configured. Please add your key in Settings.")
            return@flow
        }

        val targetModel = modelOverride ?: provider.defaultModel
        var tokenCount = 0

        when (provider) {
            CloudProviderType.GEMINI -> {
                streamGeminiSse(apiKey, targetModel, prompt, systemPrompt, maxTokens) { chunk ->
                    tokenCount++
                    emit(chunk)
                }
            }
            CloudProviderType.ANTHROPIC -> {
                streamClaudeSse(apiKey, targetModel, prompt, systemPrompt, maxTokens) { chunk ->
                    tokenCount++
                    emit(chunk)
                }
            }
            CloudProviderType.OPENAI, CloudProviderType.DEEPSEEK, CloudProviderType.OPENROUTER -> {
                val baseUrl = when (provider) {
                    CloudProviderType.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
                    CloudProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
                    else -> "https://api.openai.com/v1/chat/completions"
                }
                streamOpenAiCompatibleSse(baseUrl, apiKey, targetModel, prompt, systemPrompt, maxTokens) { chunk ->
                    tokenCount++
                    emit(chunk)
                }
            }
        }

        // Record usage estimation
        val cost = (tokenCount.toFloat() / 1_000_000f) * provider.pricePerMillionTokens
        onUsageRecorded?.invoke(CloudUsageRecord(provider, prompt.length / 4, tokenCount, cost))
    }.flowOn(Dispatchers.IO)

    private suspend fun streamGeminiSse(
        apiKey: String,
        modelName: String,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        onChunk: suspend (String) -> Unit
    ) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?alt=sse&key=$apiKey"
        val jsonBody = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply { put(JSONObject().put("text", prompt)) })
                })
            }
            put("contents", contents)
            if (!systemPrompt.isNullOrBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply { put(JSONObject().put("text", systemPrompt)) })
                })
            }
        }

        val req = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeSse(req) { data ->
            try {
                val obj = JSONObject(data)
                val candidates = obj.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) parts.getJSONObject(0).optString("text") else null
                } else null
            } catch (_: Exception) { null }
        }?.let { chunk -> onChunk(chunk) }
    }

    private suspend fun streamClaudeSse(
        apiKey: String,
        modelName: String,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        onChunk: suspend (String) -> Unit
    ) {
        val url = "https://api.anthropic.com/v1/messages"
        val jsonBody = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", maxTokens)
            put("stream", true)
            if (!systemPrompt.isNullOrBlank()) put("system", systemPrompt)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val req = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeSse(req) { data ->
            try {
                val obj = JSONObject(data)
                if (obj.optString("type") == "content_block_delta") {
                    obj.optJSONObject("delta")?.optString("text")
                } else null
            } catch (_: Exception) { null }
        }?.let { chunk -> onChunk(chunk) }
    }

    private suspend fun streamOpenAiCompatibleSse(
        url: String,
        apiKey: String,
        modelName: String,
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        onChunk: suspend (String) -> Unit
    ) {
        val jsonBody = JSONObject().apply {
            put("model", modelName)
            put("stream", true)
            put("max_tokens", maxTokens)
            val messages = JSONArray()
            if (!systemPrompt.isNullOrBlank()) {
                messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            }
            messages.put(JSONObject().put("role", "user").put("content", prompt))
            put("messages", messages)
        }

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeSse(req) { data ->
            try {
                val obj = JSONObject(data)
                val choices = obj.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("delta")?.optString("content")
                } else null
            } catch (_: Exception) { null }
        }?.let { chunk -> onChunk(chunk) }
    }

    private suspend fun executeSse(
        request: Request,
        extractText: (String) -> String?
    ): String? {
        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return "API error (${response.code}): ${response.message}"
            }

            val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return null))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (isCancelled.get()) break
                val curLine = line ?: break
                if (curLine.startsWith("data: ")) {
                    val data = curLine.substring(6).trim()
                    if (data == "[DONE]") break
                    val chunk = extractText(data)
                    if (!chunk.isNullOrEmpty()) {
                        // return first or stream chunks
                    }
                }
            }
            reader.close()
        } catch (_: Exception) {}
        return null
    }
}
