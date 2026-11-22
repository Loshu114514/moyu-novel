package com.moyu.novel

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(private val context: Context) {

    private val dir: File = File(context.filesDir, "moyu").apply { mkdirs() }
    private val conversationsFile = File(dir, "conversations.json")
    private val settingsFile = File(dir, "settings.json")
    private val downloadsFile = File(dir, "downloads.json")

    val modelsDir: File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
        "models",
    ).apply { mkdirs() }

    fun loadConversations(): List<Conversation> {
        if (!conversationsFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(conversationsFile.readText())
            (0 until array.length()).mapNotNull { index ->
                parseConversation(array.optJSONObject(index))
            }
        }.getOrElse { emptyList() }
    }

    fun saveConversations(conversations: List<Conversation>) {
        writeAtomic(conversationsFile, buildConversationsJson(conversations))
    }

    fun buildConversationsJson(conversations: List<Conversation>): String {
        val array = JSONArray()
        conversations.forEach { array.put(conversationJson(it)) }
        return array.toString()
    }

    fun writeConversationsJson(payload: String) {
        writeAtomic(conversationsFile, payload)
    }

    fun loadSettings(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return runCatching {
            val json = JSONObject(settingsFile.readText())
            val loaded = mutableListOf<ApiEndpoint>()
            json.optJSONArray("endpoints")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { loaded += parseEndpoint(it) }
                }
            }
            val merged = mergeEndpoints(loaded)
            val loadedSources = mutableListOf<ModelSource>()
            json.optJSONArray("modelSources")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val kind = runCatching {
                        ModelSourceKind.valueOf(item.optString("kind", "HF"))
                    }.getOrDefault(ModelSourceKind.HF)
                    loadedSources += ModelSource(
                        id = item.optString("id"),
                        name = item.optString("name", "自定义源"),
                        baseUrl = item.optString("baseUrl"),
                        kind = kind,
                        preset = item.optBoolean("preset", false),
                    ).let { if (it.id.isBlank()) it.copy(id = "custom-" + System.currentTimeMillis()) else it }
                }
            }
            val mergedSources = mergeSources(loadedSources)
            val requestedSource = json.optString("activeSourceId", "hf-mirror")
            AppSettings(
                endpoints = merged,
                activeEndpointId = json.optString("activeEndpointId", "deepseek"),
                modelSources = mergedSources,
                activeSourceId = if (mergedSources.any { it.id == requestedSource }) requestedSource else mergedSources.first().id,
                temperature = json.optDouble("temperature", 0.8).toFloat(),
                maxTokens = json.optInt("maxTokens", 2048),
                contextSize = json.optInt("contextSize", 4096),
                systemPrompt = json.optString("systemPrompt", DEFAULT_SYSTEM_PROMPT),
                deepThinking = json.optBoolean("deepThinking", true),
                useLocal = json.optBoolean("useLocal", false),
                localModelPath = json.optString("localModelPath", "").ifBlank { null },
                localThreads = json.optInt("localThreads", 4),
            )
        }.getOrElse { AppSettings() }
    }

    private fun mergeSources(loaded: List<ModelSource>): List<ModelSource> {
        if (loaded.isEmpty()) return defaultModelSources()
        val byId = loaded.associateBy { it.id }.toMutableMap()
        defaultModelSources().forEach { preset ->
            val existing = byId[preset.id]
            if (existing == null) {
                byId[preset.id] = preset
            } else if (existing.preset) {
                byId[preset.id] = existing.copy(
                    name = existing.name.ifBlank { preset.name },
                    baseUrl = existing.baseUrl.ifBlank { preset.baseUrl },
                    kind = preset.kind,
                    preset = true,
                )
            }
        }
        return byId.values.toList()
    }

    fun saveSettings(settings: AppSettings) {
        writeAtomic(
            settingsFile,
            JSONObject().apply {
                put("endpoints", JSONArray().apply {
                    settings.endpoints.forEach { endpoint ->
                        put(JSONObject().apply {
                            put("id", endpoint.id)
                            put("name", endpoint.name)
                            put("baseUrl", endpoint.baseUrl)
                            put("model", endpoint.model)
                            put("visionModel", endpoint.visionModel)
                            put("models", JSONArray(endpoint.models))
                            put("apiKey", endpoint.apiKey)
                            put("thinkingParam", endpoint.thinkingParam)
                            put("preset", endpoint.preset)
                        })
                    }
                })
                put("activeEndpointId", settings.activeEndpointId)
                put("modelSources", JSONArray().apply {
                    settings.modelSources.forEach { source ->
                        put(JSONObject().apply {
                            put("id", source.id)
                            put("name", source.name)
                            put("baseUrl", source.baseUrl)
                            put("kind", source.kind.name)
                            put("preset", source.preset)
                        })
                    }
                })
                put("activeSourceId", settings.activeSourceId)
                put("temperature", settings.temperature.toDouble())
                put("maxTokens", settings.maxTokens)
                put("contextSize", settings.contextSize)
                put("systemPrompt", settings.systemPrompt)
                put("deepThinking", settings.deepThinking)
                put("useLocal", settings.useLocal)
                put("localModelPath", settings.localModelPath ?: "")
                put("localThreads", settings.localThreads)
            }.toString(),
        )
    }

    fun loadDownloads(): List<DownloadRecord> {
        if (!downloadsFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(downloadsFile.readText())
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                DownloadRecord(
                    downloadId = json.optLong("downloadId"),
                    repo = json.optString("repo"),
                    file = json.optString("file"),
                    path = json.optString("path"),
                    size = json.optLong("size"),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveDownloads(records: List<DownloadRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject().apply {
                put("downloadId", record.downloadId)
                put("repo", record.repo)
                put("file", record.file)
                put("path", record.path)
                put("size", record.size)
                put("createdAt", record.createdAt)
            })
        }
        writeAtomic(downloadsFile, array.toString())
    }

    fun localModels(): List<LocalModel> {
        val files = modelsDir.listFiles { file -> file.isFile && file.name.endsWith(".gguf", true) }
            ?: return emptyList()
        return files.map { file ->
            LocalModel(path = file.absolutePath, name = file.name, size = file.length(), addedAt = file.lastModified())
        }.sortedBy { it.name }
    }

    fun deleteModel(path: String): Boolean {
        val file = File(path)
        val inside = file.canonicalPath.startsWith(modelsDir.canonicalPath)
        return inside && file.delete()
    }

    private fun mergeEndpoints(loaded: List<ApiEndpoint>): List<ApiEndpoint> {
        if (loaded.isEmpty()) return defaultEndpoints()
        val byId = loaded.associateBy { it.id }.toMutableMap()
        defaultEndpoints().forEach { preset ->
            val existing = byId[preset.id]
            if (existing == null) {
                byId[preset.id] = preset
            } else if (existing.preset) {
                // Preserve the user's key and model choice, refresh the catalogue.
                byId[preset.id] = existing.copy(
                    models = (preset.models + existing.models).distinct(),
                    baseUrl = existing.baseUrl.ifBlank { preset.baseUrl },
                    visionModel = existing.visionModel.ifBlank { preset.visionModel },
                )
            }
        }
        return byId.values.toList()
    }

    private fun parseEndpoint(json: JSONObject): ApiEndpoint {
        val models = mutableListOf<String>()
        json.optJSONArray("models")?.let { array ->
            for (i in 0 until array.length()) {
                val model = array.optString(i)
                if (model.isNotBlank()) models += model
            }
        }
        return ApiEndpoint(
            id = json.optString("id", "custom-" + System.currentTimeMillis()),
            name = json.optString("name", "自定义"),
            baseUrl = json.optString("baseUrl"),
            model = json.optString("model"),
            visionModel = json.optString("visionModel"),
            models = models,
            apiKey = json.optString("apiKey"),
            thinkingParam = json.optBoolean("thinkingParam", false),
            preset = json.optBoolean("preset", false),
        )
    }

    private fun parseConversation(json: JSONObject?): Conversation? {
        if (json == null) return null
        val conversation = Conversation(
            id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            title = json.optString("title", "新对话"),
        )
        conversation.updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        conversation.systemPrompt = json.optString("systemPrompt", "").ifBlank { null }
        json.optJSONArray("messages")?.let { messages ->
            for (i in 0 until messages.length()) {
                val messageJson = messages.optJSONObject(i) ?: continue
                val role = runCatching {
                    Role.valueOf(messageJson.optString("role", "USER"))
                }.getOrDefault(Role.USER)
                val message = ChatMessage(
                    id = messageJson.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    role = role,
                    content = messageJson.optString("content"),
                    reasoning = messageJson.optString("reasoning"),
                )
                message.reasoningMs = messageJson.optLong("reasoningMs")
                message.error = messageJson.optString("error", "").ifBlank { null }
                message.model = messageJson.optString("model")
                message.createdAt = messageJson.optLong("createdAt", System.currentTimeMillis())
                messageJson.optJSONArray("attachments")?.let { attachments ->
                    for (j in 0 until attachments.length()) {
                        val attachmentJson = attachments.optJSONObject(j) ?: continue
                        val kind = runCatching {
                            AttachKind.valueOf(attachmentJson.optString("kind", "TEXT"))
                        }.getOrDefault(AttachKind.TEXT)
                        message.attachments += Attachment(
                            id = attachmentJson.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                            name = attachmentJson.optString("name", "附件"),
                            kind = kind,
                            path = attachmentJson.optString("path", "").ifBlank { null },
                            text = attachmentJson.optString("text", "").ifBlank { null },
                            mime = attachmentJson.optString("mime", "text/plain"),
                        )
                    }
                }
                conversation.messages += message
            }
        }
        return conversation
    }

    private fun conversationJson(conversation: Conversation): JSONObject = JSONObject().apply {
        put("id", conversation.id)
        put("title", conversation.title)
        put("updatedAt", conversation.updatedAt)
        put("systemPrompt", conversation.systemPrompt ?: "")
        put("messages", JSONArray().apply {
            conversation.messages.forEach { message ->
                put(JSONObject().apply {
                    put("id", message.id)
                    put("role", message.role.name)
                    put("content", message.content)
                    put("reasoning", message.reasoning)
                    put("reasoningMs", message.reasoningMs)
                    put("error", message.error ?: "")
                    put("model", message.model)
                    put("createdAt", message.createdAt)
                    put("attachments", JSONArray().apply {
                        message.attachments.forEach { attachment ->
                            put(JSONObject().apply {
                                put("id", attachment.id)
                                put("name", attachment.name)
                                put("kind", attachment.kind.name)
                                put("path", attachment.path ?: "")
                                put("text", attachment.text ?: "")
                                put("mime", attachment.mime)
                            })
                        }
                    })
                })
            }
        })
    }

    private fun writeAtomic(file: File, text: String) {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(text)
            if (file.exists()) file.delete()
            temp.renameTo(file)
        }
    }
}
