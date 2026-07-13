package dev.placeholder.llmd

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.runBlocking

object LlmdAndroidBridge {
    private var provider: AndroidLiteRtProvider? = null
    private var selectedModelPath = DEFAULT_MODEL_PATH

    @Synchronized
    fun initialize(context: Context) {
        if (provider == null) {
            provider = AndroidLiteRtProvider(context.applicationContext.cacheDir.absolutePath) {
                android.util.Log.i("llmd", it)
            }
        }
    }

    @Synchronized
    fun close() {
        provider?.close()
        provider = null
    }

    @Synchronized
    fun chatCompletion(requestJson: String): String {
        val request = JSONObject(requestJson)
        val model = request.optString("model", DEFAULT_MODEL)
        require(model == DEFAULT_MODEL) { "Unsupported model: $model" }

        val messages = parseMessages(request.getJSONArray("messages"))
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content ?: ""
        val temperature = when {
            request.isNull("temperature") -> 0.0
            else -> request.optDouble("temperature", 0.0)
        }
        val activeProvider = requireNotNull(provider) { "Android LiteRT bridge is not initialized" }

        return runBlocking {
            activeProvider.generate(
                modelPath = selectedModelPath,
                systemPrompt = systemPrompt,
                messages = messages,
                temperature = temperature,
            )
        }
    }

    private fun parseMessages(array: JSONArray): List<LlmdChatMessage> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LlmdChatMessage(
                role = item.getString("role"),
                content = item.getString("content"),
            )
        }

    private const val DEFAULT_MODEL = "gemma-4-E2B-it"
    private const val DEFAULT_MODEL_PATH = "/data/local/tmp/llmd/gemma-4-E2B-it.litertlm"
}
