package com.storytellerf.llmd

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

object LlmdAndroidBridge {
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val providerMutex = Mutex()
    private var provider: AndroidLiteRtProvider? = null
    private var selectedModelPath = DEFAULT_MODEL_PATH

    suspend fun initialize(context: Context) = providerMutex.withLock {
        if (provider == null) {
            provider = AndroidLiteRtProvider(context.applicationContext.cacheDir.absolutePath) {
                android.util.Log.i("llmd", it)
            }
        }
    }

    suspend fun close() = providerMutex.withLock {
        provider?.close()
        provider = null
    }

    suspend fun listModels(): List<String> = providerMutex.withLock {
        when {
            File(selectedModelPath).isUsableModelFile() -> listOf(DEFAULT_MODEL)
            else -> emptyList()
        }
    }

    fun chatCompletionAsync(requestId: Long, requestJson: String) {
        bridgeScope.launch {
            val result = runCatching { chatCompletion(requestJson) }
            LlmdNativeServer.completeChatCompletion(
                requestId,
                result.getOrNull(),
                result.exceptionOrNull()?.message,
            )
        }
    }

    suspend fun chatCompletion(requestJson: String): String = providerMutex.withLock {
        val request = JSONObject(requestJson)
        val model = request.optString("model", DEFAULT_MODEL)
        require(model == DEFAULT_MODEL) { "Unsupported model: $model" }
        require(File(selectedModelPath).isUsableModelFile()) {
            "Model file does not exist: $selectedModelPath"
        }

        val messages = parseMessages(request.getJSONArray("messages"))
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content ?: ""
        val temperature = when {
            request.isNull("temperature") -> 0.0
            else -> request.optDouble("temperature", 0.0)
        }
        val activeProvider = requireNotNull(provider) { "Android LiteRT bridge is not initialized" }

        activeProvider.generate(
            modelPath = selectedModelPath,
            systemPrompt = systemPrompt,
            messages = messages,
            temperature = temperature,
        )
    }

    private fun parseMessages(array: JSONArray): List<LlmdChatMessage> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LlmdChatMessage(
                role = item.getString("role"),
                content = item.getString("content"),
            )
        }

    private fun File.isUsableModelFile(): Boolean = exists() && isFile && length() > 0L

    private const val DEFAULT_MODEL = "gemma-4-E2B-it"
    private const val DEFAULT_MODEL_PATH = "/data/local/tmp/llmd/gemma-4-E2B-it.litertlm"
}
