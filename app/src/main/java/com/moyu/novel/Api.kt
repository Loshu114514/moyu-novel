package com.moyu.novel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ApiMessage(
    val role: String,
    val content: String,
    val images: List<String> = emptyList(),
)

class ChatApi {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var activeCall: Call? = null

    fun cancel() {
        activeCall?.cancel()
    }

    suspend fun stream(
        messages: List<ApiMessage>,
        baseUrl: String,
        apiKey: String,
        model: String,
        temperature: Float,
        maxTokens: Int,
        deepThinking: Boolean,
        thinkingParam: Boolean,
        onReasoning: (String) -> Unit,
        onContent: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(model.isNotBlank()) { "请先在设置里填写模型名称" }
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { message -> put(messageToJson(message)) }
            })
            put("stream", true)
            put("temperature", temperature.toDouble())
            put("max_tokens", maxTokens)
            if (thinkingParam) put("enable_thinking", deepThinking)
        }

        val request = Request.Builder()
            .url(chatCompletionsUrl(baseUrl))
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .header("Accept", "text/event-stream")
            .apply {
                if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}")
            }
            .build()

        val call = client.newCall(request)
        activeCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = runCatching { response.body?.string() }.getOrNull().orEmpty()
                    throw IOException("请求失败（HTTP ${response.code}）${friendlyHttpError(response.code, detail)}")
                }
                val source = response.body?.source() ?: throw IOException("服务器没有返回内容")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                    val error = json.optJSONObject("error")
                    if (error != null) {
                        throw IOException(error.optString("message", "服务端返回错误"))
                    }
                    val choices = json.optJSONArray("choices") ?: continue
                    val choice = choices.optJSONObject(0) ?: continue
                    val delta = choice.optJSONObject("delta") ?: continue
                    val reasoning = delta.optString("reasoning_content")
                        .ifEmpty { delta.optString("reasoning") }
                    if (reasoning.isNotEmpty()) onReasoning(reasoning)
                    val content = delta.optString("content")
                    if (content.isNotEmpty()) onContent(content)
                    if (choice.optString("finish_reason").isNotEmpty()) break
                }
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    suspend fun listModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(modelsUrl(baseUrl))
            .get()
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}") }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} ${response.body?.string().orEmpty().take(300)}")
            }
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text)
            val data = json.optJSONArray("data") ?: return@use emptyList()
            (0 until data.length()).mapNotNull { index ->
                data.optJSONObject(index)?.optString("id")?.ifBlank { null }
            }
        }
    }

    private fun messageToJson(message: ApiMessage): JSONObject {
        return if (message.images.isEmpty()) {
            JSONObject().put("role", message.role).put("content", message.content)
        } else {
            JSONObject().put("role", message.role).put("content", JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", message.content.ifBlank { "请看这张图片。" }))
                message.images.forEach { dataUrl ->
                    put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", dataUrl)),
                    )
                }
            })
        }
    }

    private fun friendlyHttpError(code: Int, detail: String): String {
        val hint = when (code) {
            401, 403 -> "，API Key 可能无效或没有权限"
            404 -> "，接口地址或模型名可能不对"
            429 -> "，请求太频繁或额度不足"
            500, 502, 503 -> "，服务端暂时不可用"
            else -> ""
        }
        val trimmed = detail.trim()
        if (trimmed.isEmpty()) return hint
        val message = runCatching {
            JSONObject(trimmed).optJSONObject("error")?.optString("message")
                ?: JSONObject(trimmed).optString("message")
        }.getOrNull().orEmpty().ifBlank { trimmed }
        return "$hint\n${message.take(400)}"
    }

    companion object {
        fun chatCompletionsUrl(baseUrl: String): String {
            val base = baseUrl.trim().trimEnd('/')
            require(base.isNotEmpty()) { "请先填写 API 地址" }
            if (base.endsWith("/chat/completions")) return base
            return "$base/chat/completions"
        }

        fun modelsUrl(baseUrl: String): String {
            var base = baseUrl.trim().trimEnd('/')
            require(base.isNotEmpty()) { "请先填写 API 地址" }
            if (base.endsWith("/chat/completions")) base = base.removeSuffix("/chat/completions")
            if (base.endsWith("/models")) return base
            return "$base/models"
        }
    }
}
