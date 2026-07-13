package dev.placeholder.llmd

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

data class LlmdChatMessage(
    val role: String,
    val content: String,
)

class LlmdAndroidServer(
    context: Context,
    private val port: Int = 11435,
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newCachedThreadPool()
    private val logs = Collections.synchronizedList(mutableListOf<String>())
    private val provider = AndroidLiteRtProvider(appContext.cacheDir.absolutePath, ::log)
    private var serverSocket: ServerSocket? = null
    private var selectedModelPath = DEFAULT_MODEL_PATH

    fun start() {
        if (serverSocket != null) return
        executor.execute {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                log("OpenAI-compatible API listening on 127.0.0.1:$port")
                while (!Thread.currentThread().isInterrupted) {
                    val socket = serverSocket?.accept() ?: break
                    executor.execute { handle(socket) }
                }
            } catch (error: Exception) {
                log("API server stopped: ${error.message}")
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        serverSocket = null
        provider.close()
        log("API server stopped")
    }

    fun snapshot(): JSONObject = JSONObject()
        .put("status", "ok")
        .put("provider", "litert-lm-android")
        .put("model", DEFAULT_MODEL)
        .put("model_path", selectedModelPath)
        .put("model_available", File(selectedModelPath).isFile)
        .put("api_base_url", "http://127.0.0.1:$port")

    private fun handle(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(it.getOutputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeJson(writer, 400, JSONObject().put("error", "bad request"))
                return
            }

            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val splitAt = line.indexOf(':')
                if (splitAt > 0 && line.substring(0, splitAt).equals("content-length", ignoreCase = true)) {
                    contentLength = line.substring(splitAt + 1).trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0) {
                CharArray(contentLength).also { reader.read(it, 0, contentLength) }.concatToString()
            } else {
                ""
            }

            val method = parts[0]
            val path = parts[1].substringBefore("?")
            try {
                route(method, path, body, writer)
            } catch (error: Exception) {
                log("Request failed $method $path: ${error.message}")
                writeJson(
                    writer,
                    500,
                    JSONObject().put(
                        "error",
                        JSONObject()
                            .put("message", error.message ?: "Android LiteRT-LM request failed")
                            .put("type", "llmd_android_error"),
                    ),
                )
            }
        }
    }

    private fun route(method: String, path: String, body: String, writer: BufferedWriter) {
        when {
            method == "GET" && path == "/health" -> writeJson(writer, 200, snapshot())
            method == "GET" && path == "/logs" -> writeJson(writer, 200, JSONObject().put("data", JSONArray(logs.toList())))
            method == "GET" && path == "/v1/models" -> writeJson(writer, 200, modelsResponse())
            method == "GET" && path == "/v1/models/$DEFAULT_MODEL" -> writeJson(writer, 200, modelObject())
            method == "POST" && path == "/v1/models/select" -> {
                val json = JSONObject(body)
                selectedModelPath = json.optString("model_path", selectedModelPath)
                log("Selected model path: $selectedModelPath")
                writeJson(writer, 200, snapshot())
            }
            method == "POST" && path == "/v1/chat/completions" -> writeJson(writer, 200, chatCompletions(JSONObject(body)))
            else -> writeJson(writer, 404, JSONObject().put("error", "not found"))
        }
    }

    private fun modelsResponse(): JSONObject =
        JSONObject()
            .put("object", "list")
            .put("data", JSONArray().put(modelObject()))

    private fun modelObject(): JSONObject =
        JSONObject()
            .put("id", DEFAULT_MODEL)
            .put("object", "model")
            .put("created", 0)
            .put("owned_by", "litert-lm-android")

    private fun chatCompletions(request: JSONObject): JSONObject {
        val model = request.optString("model", DEFAULT_MODEL)
        require(model == DEFAULT_MODEL) { "Unsupported model: $model" }
        require(!request.optBoolean("stream", false)) { "Streaming is not implemented on Android yet" }

        val messages = parseMessages(request.getJSONArray("messages"))
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content ?: ""
        val temperature = request.optDouble("temperature", 0.0)
        val content = runBlocking {
            provider.generate(
                modelPath = selectedModelPath,
                systemPrompt = systemPrompt,
                messages = messages,
                temperature = temperature,
            )
        }

        return JSONObject()
            .put("id", "chatcmpl-${UUID.randomUUID()}")
            .put("object", "chat.completion")
            .put("created", Instant.now().epochSecond)
            .put("model", model)
            .put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("index", 0)
                        .put("message", JSONObject().put("role", "assistant").put("content", content))
                        .put("finish_reason", "stop"),
                ),
            )
            .put("usage", JSONObject().put("prompt_tokens", 0).put("completion_tokens", 0).put("total_tokens", 0))
    }

    private fun parseMessages(array: JSONArray): List<LlmdChatMessage> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LlmdChatMessage(
                role = item.getString("role"),
                content = contentToString(item.get("content")),
            )
        }

    private fun contentToString(value: Any): String = when (value) {
        is String -> value
        is JSONArray -> (0 until value.length()).joinToString("\n") { index ->
            value.getJSONObject(index).optString("text")
        }
        else -> value.toString()
    }

    private fun writeJson(writer: BufferedWriter, status: Int, body: JSONObject) {
        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        writer.write("HTTP/1.1 $status ${statusText(status)}\r\n")
        writer.write("Content-Type: application/json; charset=utf-8\r\n")
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.write(String(bytes, StandardCharsets.UTF_8))
        writer.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        else -> "Internal Server Error"
    }

    private fun log(message: String) {
        val entry = "${Instant.now()} $message"
        logs.add(entry)
        android.util.Log.i("llmd", entry)
    }

    companion object {
        const val DEFAULT_MODEL = "gemma-4-E2B-it"
        const val DEFAULT_MODEL_PATH = "/data/local/tmp/llmd/gemma-4-E2B-it.litertlm"
    }
}
