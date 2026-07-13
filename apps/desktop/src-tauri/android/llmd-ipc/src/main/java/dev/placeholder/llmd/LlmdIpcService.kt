package dev.placeholder.llmd

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dev.placeholder.llmd.ipc.ILlmdService
import org.json.JSONArray
import org.json.JSONObject

class LlmdIpcService : Service() {
    private val binder = object : ILlmdService.Stub() {
        override fun health(): String {
            enforceAllowedCaller()
            return JSONObject()
                .put("status", "ok")
                .put("provider", PROVIDER)
                .put("model", DEFAULT_MODEL)
                .toString()
        }

        override fun listModels(): String {
            enforceAllowedCaller()
            return JSONObject()
                .put("object", "list")
                .put(
                    "data",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", DEFAULT_MODEL)
                                .put("object", "model")
                                .put("created", 0)
                                .put("owned_by", PROVIDER),
                        ),
                )
                .toString()
        }

        override fun chatCompletion(requestJson: String): String {
            enforceAllowedCaller()
            return runCatching {
                LlmdAndroidBridge.initialize(this@LlmdIpcService)
                val request = JSONObject(requestJson)
                val model = request.optString("model", DEFAULT_MODEL)
                val content = LlmdAndroidBridge.chatCompletion(requestJson)
                JSONObject()
                    .put("id", "chatcmpl-android-ipc")
                    .put("object", "chat.completion")
                    .put("created", System.currentTimeMillis() / 1000L)
                    .put("model", model)
                    .put(
                        "choices",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("index", 0)
                                    .put(
                                        "message",
                                        JSONObject()
                                            .put("role", "assistant")
                                            .put("content", content),
                                    )
                                    .put("finish_reason", "stop"),
                            ),
                    )
                    .put(
                        "usage",
                        JSONObject()
                            .put("prompt_tokens", 0)
                            .put("completion_tokens", 0)
                            .put("total_tokens", 0),
                    )
                    .toString()
            }.getOrElse { error ->
                errorResponse(error)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun enforceAllowedCaller() {
        val packages = packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        check(packages.any { it in ALLOWED_CALLER_PACKAGES }) {
            "Caller is not allowed to use llmd IPC"
        }
    }

    private fun errorResponse(error: Throwable): String =
        JSONObject()
            .put(
                "error",
                JSONObject()
                    .put("message", error.message ?: error::class.java.simpleName)
                    .put("type", "llmd_error"),
            )
            .toString()

    private companion object {
        const val DEFAULT_MODEL = "gemma-4-E2B-it"
        const val PROVIDER = "litert-lm-android"
        val ALLOWED_CALLER_PACKAGES = setOf("dev.divedeep.android")
    }
}
