package com.moyu.novel

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class Role { USER, ASSISTANT, SYSTEM }

enum class ChatMode(val label: String) {
    CHAT("日常聊天"),
    NOVEL("小说创作"),
    AGENT("本地智能体"),
}

enum class ContextMode(val label: String) {
    ALL("全部对话"),
    IMPORTANT("重要对话"),
}

enum class AttachKind { IMAGE, TEXT }

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: AttachKind,
    val path: String? = null,
    val text: String? = null,
    val mime: String = "text/plain",
    val ocrText: String? = null,
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
    var important by mutableStateOf(false)
    var importantAuto by mutableStateOf(false)
    var trace by mutableStateOf("")
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
    var agentId: String? = null
    var mode by mutableStateOf(ChatMode.CHAT)
    var contextMode by mutableStateOf(ContextMode.ALL)
    val messages = mutableStateListOf<ChatMessage>()

    fun lastUserMessage(): ChatMessage? = messages.lastOrNull { it.role == Role.USER }
}

class AgentMemory(
    val id: String = UUID.randomUUID().toString(),
    val messageId: String = "",
    val role: Role = Role.ASSISTANT,
    content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    var content by mutableStateOf(content)
}

class AgentProfile(
    val id: String = UUID.randomUUID().toString(),
    name: String = "新智能体",
    persona: String = "",
    greeting: String = "",
    avatarPath: String? = null,
    backgroundPath: String? = null,
    contextLength: Int = 4096,
    val conversationId: String = UUID.randomUUID().toString(),
) {
    var name by mutableStateOf(name)
    var persona by mutableStateOf(persona)
    var greeting by mutableStateOf(greeting)
    var avatarPath by mutableStateOf(avatarPath)
    var backgroundPath by mutableStateOf(backgroundPath)
    var contextLength by mutableStateOf(contextLength)
    var updatedAt by mutableStateOf(System.currentTimeMillis())
    val memories = mutableStateListOf<AgentMemory>()
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

enum class ModelSourceKind { HF, MODELSCOPE }

data class ModelSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val kind: ModelSourceKind = ModelSourceKind.HF,
    val preset: Boolean = false,
)

data class CatalogModel(val repo: String, val note: String)

data class AppSettings(
    val endpoints: List<ApiEndpoint> = defaultEndpoints(),
    val activeEndpointId: String = "deepseek",
    val temperature: Float = 0.8f,
    val maxTokens: Int = 2048,
    val contextSize: Int = 4096,
    val modelSources: List<ModelSource> = defaultModelSources(),
    val activeSourceId: String = "hf-mirror",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val chatPrompt: String = DEFAULT_CHAT_PROMPT,
    val deepThinking: Boolean = true,
    val autoMarkImportant: Boolean = true,
    val webSearch: Boolean = false,
    val lastConversationId: String? = null,
    val defaultMode: ChatMode = ChatMode.CHAT,
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
    val note: String = "",
)

data class HubFile(
    val name: String,
    val size: Long,
)

data class ImportedChat(
    val title: String,
    val messages: List<Pair<Role, String>>,
)

enum class Screen { CHAT, MODELS, SETTINGS, IMPORT, AGENTS, AGENT_EDIT, AGENT_CHAT, AGENT_MEMORY }

const val DEFAULT_SYSTEM_PROMPT =
    "你是一位资深的中文小说作家，擅长长篇网文的创作与续写。\n" +
        "要求：\n" +
        "1. 直接输出正文，不要解释你的写作过程，也不要使用“好的”“以下是”之类的话。\n" +
        "2. 保持人物、世界观、伏笔与已发生剧情的一致性。\n" +
        "3. 场景具体、节奏明快，多用动作、对话与细节推进剧情，避免空洞总结。\n" +
        "4. 用户给出设定或片段时，先自然衔接再展开；用户要求续写时，从停下的地方接着写。\n" +
        "5. 除非用户明确要求，不要擅自结束故事或写“全文完”。"

val CHAT_SUGGESTIONS = listOf(
    "帮我把这段话改得更自然",
    "今天适合做点什么？",
    "给我讲一个短一点的冷知识",
    "帮我列一个周末计划",
)

val AGENT_SUGGESTIONS = listOf(
    "帮我算一下 128 × 37",
    "现在几点了？",
    "记住：主角叫林渊，性格冷静",
    "搜索一下今天有哪些科技新闻",
)

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
        thinkingParam = true,
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


fun defaultModelSources(): List<ModelSource> = listOf(
    ModelSource("hf-mirror", "HF 镜像", "https://hf-mirror.com", ModelSourceKind.HF, true),
    ModelSource("huggingface", "Hugging Face", "https://huggingface.co", ModelSourceKind.HF, true),
    ModelSource("modelscope", "魔搭 ModelScope", "https://www.modelscope.cn", ModelSourceKind.MODELSCOPE, true),
)

val MODELSCOPE_CATALOG = listOf(
    CatalogModel("Qwen/Qwen2.5-0.5B-Instruct-GGUF", "0.5B · 极速"),
    CatalogModel("Qwen/Qwen3-0.6B-GGUF", "0.6B · Qwen3"),
    CatalogModel("Qwen/Qwen2.5-1.5B-Instruct-GGUF", "1.5B"),
    CatalogModel("Qwen/Qwen3-1.7B-GGUF", "1.7B"),
    CatalogModel("Qwen/Qwen2.5-3B-Instruct-GGUF", "3B"),
    CatalogModel("Qwen/Qwen3-4B-GGUF", "4B · 手机推荐"),
    CatalogModel("Qwen/Qwen2.5-7B-Instruct-GGUF", "7B"),
    CatalogModel("Qwen/Qwen3-8B-GGUF", "8B"),
    CatalogModel("Qwen/Qwen3-14B-GGUF", "14B"),
    CatalogModel("Qwen/Qwen2.5-14B-Instruct-GGUF", "14B"),
    CatalogModel("Qwen/Qwen3-30B-A3B-GGUF", "30B MoE"),
    CatalogModel("Qwen/Qwen3-32B-GGUF", "32B"),
    CatalogModel("Qwen/Qwen2.5-32B-Instruct-GGUF", "32B"),
    CatalogModel("Qwen/Qwen2.5-72B-Instruct-GGUF", "72B"),
    CatalogModel("Qwen/Qwen3-235B-A22B-GGUF", "235B MoE · 仅收藏"),
)

fun modelBillions(id: String): Double? {
    val matches = Regex("(?i)(\\d+(?:\\.\\d+)?)B(?![a-zA-Z0-9])").findAll(id)
    return matches.mapNotNull { it.groupValues[1].toDoubleOrNull() }.maxOrNull()
}

fun sourceSizeLabel(id: String): String {
    val billions = modelBillions(id) ?: return ""
    return if (billions < 1) "${billions}B" else "${billions.toInt()}B"
}


const val DEFAULT_CHAT_PROMPT =
    "你是一个可靠、直接的中文助手。回答准确、自然，不说客套话；不确定时直说。" +
        "如果用户给了工具结果或搜索结果，优先依据它们回答，并标明信息来源。"

const val AGENT_SYSTEM_PROMPT =
    "你是运行在手机本地的智能体。你可以使用应用提供的工具结果（计算器、时间、记忆、联网搜索）。" +
        "工具结果会以「【工具结果】」开头提供给你。优先依据工具结果回答，不要编造工具没有返回的信息。" +
        "回答简洁，必要时说明你用了哪个工具。"

object DeviceInfo {
    val soc: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }

    val isSnapdragon8Gen2OrNewer: Boolean
        get() {
            val value = soc.lowercase()
            return listOf("sm8550", "sm8650", "sm8750", "sm8850", "qcs8550").any { value.contains(it) }
        }

    fun localHint(): String = if (isSnapdragon8Gen2OrNewer) {
        "当前处理器：$soc（骁龙 8 Gen 2 级别或更新，适合跑本地模型）"
    } else {
        "当前处理器：$soc。推荐骁龙 8 Gen 2 及以上；其他处理器也能跑，但 4B 模型会更慢。"
    }
}
