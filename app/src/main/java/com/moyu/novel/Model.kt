package com.moyu.novel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class Role { USER, ASSISTANT, SYSTEM }

enum class AttachKind { IMAGE, TEXT }

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: AttachKind,
    val path: String? = null,
    val text: String? = null,
    val mime: String = "text/plain",
)

class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    content: String = "",
    reasoning: String = "",
) {
    var content by mutableStateOf(content)
    var reasoning by mutableStateOf(reasoning)
    var reasoningStart by mutableStateOf(0L)
    var reasoningMs by mutableStateOf(0L)
    var streaming by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var model by mutableStateOf("")
    val attachments = mutableStateListOf<Attachment>()
    var createdAt: Long = System.currentTimeMillis()
}

class Conversation(
    val id: String = UUID.randomUUID().toString(),
    title: String = "新对话",
) {
    var title by mutableStateOf(title)
    var updatedAt by mutableStateOf(System.currentTimeMillis())
    var systemPrompt: String? = null
    val messages = mutableStateListOf<ChatMessage>()

    fun lastUserMessage(): ChatMessage? = messages.lastOrNull { it.role == Role.USER }
}

data class ApiEndpoint(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val visionModel: String,
    val models: List<String>,
    val apiKey: String = "",
    val thinkingParam: Boolean = false,
    val preset: Boolean = false,
)

data class AppSettings(
    val endpoints: List<ApiEndpoint> = defaultEndpoints(),
    val activeEndpointId: String = "deepseek",
    val temperature: Float = 0.8f,
    val maxTokens: Int = 2048,
    val contextSize: Int = 4096,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val deepThinking: Boolean = true,
    val useLocal: Boolean = false,
    val localModelPath: String? = null,
    val localThreads: Int = 4,
) {
    fun activeEndpoint(): ApiEndpoint =
        endpoints.firstOrNull { it.id == activeEndpointId } ?: endpoints.first()
}

data class LocalModel(
    val path: String,
    val name: String,
    val size: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

data class DownloadRecord(
    val downloadId: Long,
    val repo: String,
    val file: String,
    val path: String,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

data class DownloadProgress(
    val status: Int,
    val bytes: Long,
    val total: Long,
    val reason: Int = 0,
) {
    val running: Boolean get() = status == 2 || status == 1
    val success: Boolean get() = status == 8
    val failed: Boolean get() = status == 16
    val fraction: Float
        get() = if (total > 0) (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

data class HubModel(
    val id: String,
    val downloads: Long,
    val likes: Long,
    val tags: List<String>,
)

data class HubFile(
    val name: String,
    val size: Long,
)

data class ImportedChat(
    val title: String,
    val messages: List<Pair<Role, String>>,
)

enum class Screen { CHAT, MODELS, SETTINGS, IMPORT }

const val DEFAULT_SYSTEM_PROMPT =
    "你是一位资深的中文小说作家，擅长长篇网文的创作与续写。\n" +
        "要求：\n" +
        "1. 直接输出正文，不要解释你的写作过程，也不要使用“好的”“以下是”之类的话。\n" +
        "2. 保持人物、世界观、伏笔与已发生剧情的一致性。\n" +
        "3. 场景具体、节奏明快，多用动作、对话与细节推进剧情，避免空洞总结。\n" +
        "4. 用户给出设定或片段时，先自然衔接再展开；用户要求续写时，从停下的地方接着写。\n" +
        "5. 除非用户明确要求，不要擅自结束故事或写“全文完”。"

val NOVEL_SUGGESTIONS = listOf(
    "帮我写一个玄幻小说的开篇，主角是个被家族抛弃的少年",
    "续写：雨夜，她推开那扇十年没有打开的门……",
    "给我 5 个都市异能小说的爽点设定",
    "把这段剧情改写得更有张力，加入一段对话",
)

fun defaultEndpoints(): List<ApiEndpoint> = listOf(
    ApiEndpoint(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-chat",
        visionModel = "",
        models = listOf("deepseek-chat", "deepseek-reasoner"),
        preset = true,
    ),
    ApiEndpoint(
        id = "qwen",
        name = "通义千问",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        model = "qwen3-max",
        visionModel = "qwen3-vl-plus",
        models = listOf("qwen3-max", "qwen-plus", "qwen3-vl-plus", "qwen3-vl-max"),
        preset = true,
    ),
    ApiEndpoint(
        id = "zhipu",
        name = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        model = "glm-4.6",
        visionModel = "glm-4.5v",
        models = listOf("glm-4.6", "glm-4.5", "glm-4.5v"),
        preset = true,
    ),
    ApiEndpoint(
        id = "kimi",
        name = "Kimi",
        baseUrl = "https://api.moonshot.cn/v1",
        model = "kimi-k2-0905-preview",
        visionModel = "moonshot-v1-32k-vision-preview",
        models = listOf("kimi-k2-0905-preview", "moonshot-v1-32k-vision-preview"),
        preset = true,
    ),
    ApiEndpoint(
        id = "openai",
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        model = "gpt-4o",
        visionModel = "gpt-4o",
        models = listOf("gpt-4o", "gpt-4o-mini"),
        preset = true,
    ),
    ApiEndpoint(
        id = "custom",
        name = "自定义（OpenAI 兼容）",
        baseUrl = "",
        model = "",
        visionModel = "",
        models = emptyList(),
        preset = false,
    ),
)

fun makeTitle(text: String): String {
    val firstLine = text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: "新对话"
    val cleaned = firstLine.removePrefix("#").trim()
    return if (cleaned.length > 18) cleaned.take(18) + "…" else cleaned
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
