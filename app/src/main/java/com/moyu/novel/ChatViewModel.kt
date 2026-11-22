package com.moyu.novel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moyu.novel.local.LocalEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val store = Store(app)
    private val api = ChatApi()
    private val hub = ModelHub(app, store)
    private val engine = LocalEngine(app)

    val recommendedModels: List<RecommendedModel> get() = hub.recommended

    val activeSource: ModelSource
        get() = settings.modelSources.firstOrNull { it.id == settings.activeSourceId }
            ?: settings.modelSources.first()

    var settings by mutableStateOf(store.loadSettings())
        private set

    val conversations = mutableStateListOf<Conversation>()
    var activeId by mutableStateOf("")
        private set

    val activeConversation: Conversation?
        get() = conversations.firstOrNull { it.id == activeId }

    var screen by mutableStateOf(Screen.CHAT)
    var showModelPicker by mutableStateOf(false)
    val pendingAttachments = mutableStateListOf<Attachment>()
    val localModels = mutableStateListOf<LocalModel>()
    val downloadProgress = mutableStateMapOf<Long, DownloadProgress>()

    var downloads by mutableStateOf<List<DownloadRecord>>(emptyList())
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var engineStatus by mutableStateOf("未加载本地模型")
        private set
    var toast by mutableStateOf<String?>(null)
    var importPreview by mutableStateOf<List<ImportedChat>>(emptyList())
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var importLoading by mutableStateOf(false)
        private set
    var connectionStatus by mutableStateOf<String?>(null)
        private set
    var testingConnection by mutableStateOf(false)
        private set
    var hubModels by mutableStateOf<List<HubModel>>(emptyList())
        private set
    var hubLoading by mutableStateOf(false)
        private set
    var hubError by mutableStateOf<String?>(null)
        private set
    val hubFiles = mutableStateMapOf<String, List<HubFile>>()
    val hubLoadingFiles = mutableStateMapOf<String, Boolean>()

    private var genJob: Job? = null
    private var persistJob: Job? = null
    private var settingsJob: Job? = null
    private var stopRequested = false
    private var localSession: LocalSession? = null
    private var lastStreamSave = 0L

    init {
        val saved = store.loadConversations().sortedByDescending { it.updatedAt }
        conversations.addAll(saved)
        if (conversations.isEmpty()) {
            createConversation()
        } else {
            activeId = conversations.first().id
        }
        refreshLocalModels()
        downloads = store.loadDownloads().filter { record ->
            val target = File(record.path)
            val part = File(record.path + ".part")
            !target.exists() && part.exists()
        }
        startDownloadWatcher()
    }

    fun activeModelLabel(): String {
        if (settings.useLocal) {
            val path = settings.localModelPath
            return if (path != null) File(path).name.substringBeforeLast(".gguf") else "选择本地模型"
        }
        val endpoint = settings.activeEndpoint()
        return endpoint.model.ifBlank { endpoint.name }
    }

    fun goTo(target: Screen) {
        screen = target
    }

    // ---------------------------------------------------------------- conversations

    private fun createConversation(): Conversation {
        val conversation = Conversation()
        conversations.add(0, conversation)
        activeId = conversation.id
        return conversation
    }

    fun newChat() {
        if (isGenerating) stop()
        val current = activeConversation
        if (current != null && current.messages.isEmpty()) {
            screen = Screen.CHAT
            return
        }
        createConversation()
        localSession = null
        screen = Screen.CHAT
        persistSoon()
    }

    fun selectChat(id: String) {
        if (activeId == id) {
            screen = Screen.CHAT
            return
        }
        if (isGenerating) stop()
        activeId = id
        localSession = null
        screen = Screen.CHAT
    }

    fun deleteChat(id: String) {
        val index = conversations.indexOfFirst { it.id == id }
        if (index < 0) return
        if (isGenerating && id == activeId) stop()
        conversations.removeAt(index)
        if (activeId == id) {
            val next = conversations.getOrNull(index) ?: conversations.getOrNull(index - 1)
            if (next != null) {
                activeId = next.id
            } else {
                createConversation()
            }
            localSession = null
        }
        persistSoon()
    }

    fun renameChat(id: String, title: String) {
        val conversation = conversations.firstOrNull { it.id == id } ?: return
        conversation.title = title.ifBlank { "新对话" }
        conversation.updatedAt = System.currentTimeMillis()
        persistSoon()
    }

    fun clearAllChats() {
        if (isGenerating) stop()
        conversations.clear()
        createConversation()
        localSession = null
        persistSoon()
        toast = "已清空所有对话"
    }

    // ---------------------------------------------------------------- sending

    fun send(text: String) {
        if (isGenerating) return
        val conversation = activeConversation ?: return
        val content = text.trim()
        val attachments = pendingAttachments.toList()
        if (content.isEmpty() && attachments.isEmpty()) return
        pendingAttachments.clear()

        val userMessage = ChatMessage(role = Role.USER, content = content)
        userMessage.attachments.addAll(attachments)
        conversation.messages.add(userMessage)
        if (conversation.messages.count { it.role == Role.USER } == 1) {
            conversation.title = makeTitle(content.ifBlank { attachments.firstOrNull()?.name ?: "图片对话" })
        }
        conversation.updatedAt = System.currentTimeMillis()
        persistSoon()
        startReply(conversation)
    }

    fun regenerate() {
        if (isGenerating) return
        val conversation = activeConversation ?: return
        val last = conversation.messages.lastOrNull() ?: return
        if (last.role != Role.ASSISTANT) return
        conversation.messages.removeAt(conversation.messages.size - 1)
        localSession = null
        startReply(conversation)
    }

    private fun startReply(conversation: Conversation) {
        val assistant = ChatMessage(role = Role.ASSISTANT)
        assistant.streaming = true
        assistant.model = activeModelLabel()
        conversation.messages.add(assistant)
        conversation.updatedAt = System.currentTimeMillis()
        isGenerating = true
        stopRequested = false
        lastStreamSave = System.currentTimeMillis()

        genJob = viewModelScope.launch {
            try {
                if (settings.useLocal) {
                    generateLocal(conversation, assistant)
                } else {
                    generateApi(conversation, assistant)
                }
            } catch (cancelled: CancellationException) {
                // user pressed stop
            } catch (throwable: Throwable) {
                if (!stopRequested) {
                    assistant.error = throwable.message?.takeIf { it.isNotBlank() }
                        ?: "生成失败：${throwable.javaClass.simpleName}"
                }
            } finally {
                assistant.streaming = false
                if (assistant.reasoningStart > 0L && assistant.reasoningMs == 0L) {
                    assistant.reasoningMs = System.currentTimeMillis() - assistant.reasoningStart
                }
                if (!stopRequested && assistant.content.isBlank() && assistant.reasoning.isBlank() && assistant.error == null) {
                    assistant.error = "模型没有返回内容，请检查接口配置"
                }
                isGenerating = false
                stopRequested = false
                conversation.updatedAt = System.currentTimeMillis()
                persistSoon()
            }
        }
    }

    fun stop() {
        if (!isGenerating) return
        stopRequested = true
        api.cancel()
        engine.cancel()
        genJob?.cancel()
    }

    private suspend fun generateApi(conversation: Conversation, assistant: ChatMessage) {
        val endpoint = settings.activeEndpoint()
        if (endpoint.baseUrl.isBlank()) throw IllegalStateException("还没有配置 API 地址：设置 → API 服务")
        if (endpoint.apiKey.isBlank()) throw IllegalStateException("还没有填写 API Key：设置 → API 服务")
        val history = conversation.messages.dropLast(1).toList()
        val hasImages = history.any { message -> message.attachments.any { it.kind == AttachKind.IMAGE } }
        val model = if (hasImages && endpoint.visionModel.isNotBlank()) endpoint.visionModel else endpoint.model
        if (model.isBlank()) throw IllegalStateException("还没有填写模型名称：设置 → API 服务")
        assistant.model = model

        val messages = withContext(Dispatchers.IO) { buildApiMessages(settings.systemPrompt, history) }
        var reasoningStarted = false

        api.stream(
            messages = messages,
            baseUrl = endpoint.baseUrl,
            apiKey = endpoint.apiKey,
            model = model,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            deepThinking = settings.deepThinking,
            thinkingParam = endpoint.thinkingParam,
            onReasoning = { piece ->
                if (!reasoningStarted) {
                    reasoningStarted = true
                    assistant.reasoningStart = System.currentTimeMillis()
                }
                assistant.reasoning += piece
            },
            onContent = { piece ->
                if (reasoningStarted && assistant.reasoningMs == 0L) {
                    assistant.reasoningMs = System.currentTimeMillis() - assistant.reasoningStart
                }
                assistant.content += piece
                maybeStreamSave()
            },
        )
        if (assistant.content.isBlank() && assistant.reasoning.isBlank()) {
            throw IOException("模型没有返回内容，请检查模型名称和接口地址")
        }
    }

    private fun buildApiMessages(system: String, history: List<ChatMessage>): List<ApiMessage> {
        val result = mutableListOf<ApiMessage>()
        if (system.isNotBlank()) result += ApiMessage("system", system)
        for (message in history) {
            when (message.role) {
                Role.USER -> {
                    val images = message.attachments
                        .filter { it.kind == AttachKind.IMAGE }
                        .mapNotNull { attachment -> attachment.path?.let { AttachTools.imageDataUrl(it) } }
                    val text = localPromptText(message)
                    result += ApiMessage(
                        role = "user",
                        content = text.ifBlank { if (images.isNotEmpty()) "请看这张图片。" else "" },
                        images = images,
                    )
                }
                Role.ASSISTANT -> {
                    val text = message.content.trim()
                    if (text.isNotEmpty()) result += ApiMessage("assistant", text)
                }
                Role.SYSTEM -> Unit
            }
        }
        return result
    }

    private suspend fun generateLocal(conversation: Conversation, assistant: ChatMessage) = withContext(Dispatchers.Default) {
        val path = settings.localModelPath ?: localModels.firstOrNull()?.path
            ?: throw IllegalStateException("还没有本地模型：模型 → 模型库下载")
        if (!File(path).exists()) throw IllegalStateException("本地模型文件不见了，请重新下载")
        if (settings.localModelPath != path) {
            settings = settings.copy(localModelPath = path, useLocal = true)
            saveSettingsSoon()
        }
        if (engine.loadedModelPath() != path) {
            engineStatus = "正在加载模型…（首次加载可能要几十秒）"
            val ok = engine.load(path, settings.contextSize, settings.localThreads)
            if (!ok) {
                engineStatus = "模型加载失败"
                throw IllegalStateException("本地模型加载失败：内存不足或文件损坏（试试更小的量化或调小上下文）")
            }
            engineStatus = "已加载 · ${engine.description()}"
            localSession = null
        }

        val system = settings.systemPrompt
        val contextSize = settings.contextSize
        val history = conversation.messages.dropLast(2).toList()
        val userMessage = conversation.messages.getOrNull(conversation.messages.size - 2)
            ?: throw IllegalStateException("消息状态异常，请重新发送")
        val userText = localPromptText(userMessage)
        val userTokens = estimateTokens(userText)
        val nearFull = localSession != null && engine.usedTokens() + userTokens + 256 > contextSize
        val session = localSession
        val historyIds = history.map { it.id }
        val idsMatch = session != null &&
            session.ids.isNotEmpty() &&
            historyIds.size >= session.ids.size &&
            historyIds.takeLast(session.ids.size) == session.ids
        val needsReplay = session == null ||
            session.conversationId != conversation.id ||
            session.systemPrompt != system ||
            !idsMatch ||
            nearFull

        if (needsReplay) {
            val trimmed = trimHistory(history, system, contextSize, userTokens)
            val truncated = trimmed.size != history.size
            engineStatus = if (truncated) "上下文较长，已自动截断较早对话" else "正在准备上下文…"
            if (!engine.startChat(system)) {
                throw IllegalStateException("本地模型上下文初始化失败：请把上下文调大或新建对话")
            }
            for (message in trimmed) {
                when (message.role) {
                    Role.USER -> {
                        if (message.content.isNotBlank() || message.attachments.isNotEmpty()) {
                            if (!engine.appendUser(localPromptText(message))) {
                                throw IllegalStateException("上下文超出模型窗口：请调大上下文或新建对话")
                            }
                        }
                    }
                    Role.ASSISTANT -> {
                        if (message.content.isNotBlank()) {
                            engine.appendAssistantHistory(message.content, message.reasoning)
                        }
                    }
                    Role.SYSTEM -> Unit
                }
            }
            localSession = LocalSession(conversation.id, system, trimmed.map { it.id }.toMutableList())
            if (truncated) toast = "对话太长，已截断较早内容"
        }

        if (!engine.appendUser(userText)) {
            throw IllegalStateException("上下文超出模型窗口：请调大上下文或新建对话")
        }
        localSession?.ids?.add(userMessage.id)
        engineStatus = "生成中…"

        val raw = StringBuilder()
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var state = 0
        var thinkStart = 0L

        try {
            val maxNew = minOf(settings.maxTokens, (contextSize * 0.45).toInt()).coerceAtLeast(128)
            engine.generate(
                maxTokens = maxNew,
                temperature = settings.temperature,
            ) { piece ->
                raw.append(piece)
                if (state == 0) {
                    val current = raw.toString()
                    if (current.length < 7 && " thinking".startsWith(current)) {
                        return@generate
                    }
                    if (current.startsWith(" thinking")) {
                        state = 1
                    } else {
                        state = 2
                        content.append(current)
                        assistant.content = content.toString()
                        return@generate
                    }
                }
                if (state == 1) {
                    val current = raw.toString()
                    if (thinkStart == 0L) {
                        thinkStart = System.currentTimeMillis()
                        assistant.reasoningStart = thinkStart
                    }
                    val end = current.indexOf("</think>")
                    if (end >= 0) {
                        reasoning.clear()
                        reasoning.append(current.substring(7, end).trim())
                        state = 2
                        val rest = current.substring(end + 8).trimStart('\n', ' ')
                        if (rest.isNotEmpty()) content.append(rest)
                        assistant.reasoning = reasoning.toString()
                        assistant.reasoningMs = System.currentTimeMillis() - thinkStart
                    } else {
                        reasoning.clear()
                        reasoning.append(current.substring(7))
                        assistant.reasoning = reasoning.toString()
                    }
                } else if (state == 2) {
                    content.append(piece)
                    assistant.content = content.toString()
                }
                maybeStreamSave()
            }
        } finally {
            if (state == 0) {
                content.append(raw)
                assistant.content = content.toString()
            }
            val finalContent = content.toString().trim()
            val finalReasoning = reasoning.toString().trim()
            if (finalContent.isNotEmpty() || finalReasoning.isNotEmpty()) {
                withContext(NonCancellable) {
                    engine.finishAssistant(finalContent, finalReasoning)
                }
                localSession?.ids?.add(assistant.id)
            }
            assistant.content = finalContent
            assistant.reasoning = finalReasoning
            engineStatus = "已加载 · ${engine.description()}"
        }
    }

    private fun maybeStreamSave() {
        val now = System.currentTimeMillis()
        if (now - lastStreamSave > 5000) {
            lastStreamSave = now
            persistSoon()
        }
    }

    private fun localPromptText(message: ChatMessage): String {
        val builder = StringBuilder(message.content.trim())
        val blocks = message.attachments
            .filter { it.kind == AttachKind.TEXT }
            .map { "【文件：${it.name}】\n${it.text.orEmpty().take(30_000)}\n【文件结束】" }
        if (blocks.isNotEmpty()) {
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append(blocks.joinToString("\n\n").take(60_000))
        }
        return builder.toString().trim()
    }

    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        val counted = engine.tokenCount(text)
        return if (counted > 0) counted else (text.length / 2).coerceAtLeast(1)
    }

    private fun trimHistory(
        history: List<ChatMessage>,
        system: String,
        contextSize: Int,
        newUserTokens: Int,
    ): List<ChatMessage> {
        val generation = minOf(settings.maxTokens, (contextSize * 0.45).toInt()).coerceAtLeast(128)
        val budget = (contextSize - generation - 128).coerceAtLeast(256)
        var used = estimateTokens(system) + newUserTokens + 32
        val deque = ArrayDeque<ChatMessage>()
        for (message in history.asReversed()) {
            val cost = estimateTokens(localPromptText(message)) + 16
            if (used + cost > budget) break
            deque.addFirst(message)
            used += cost
        }
        while (deque.isNotEmpty() && deque.first().role == Role.ASSISTANT) {
            deque.removeFirst()
        }
        return deque.toList()
    }

    // ---------------------------------------------------------------- models & settings

    fun refreshLocalModels() {
        val models = store.localModels()
        localModels.clear()
        localModels.addAll(models)
    }

    fun selectModelSource(id: String) {
        if (settings.activeSourceId == id) return
        settings = settings.copy(activeSourceId = id)
        hubModels = emptyList()
        hubError = null
        hubFiles.clear()
        hubLoadingFiles.clear()
        saveSettingsNow()
    }

    fun addModelSource(name: String, baseUrl: String) {
        val cleanName = name.trim()
        var cleanUrl = baseUrl.trim().trimEnd('/')
        if (cleanName.isEmpty() || cleanUrl.isEmpty()) {
            toast = "名称和地址都要填"
            return
        }
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        val source = ModelSource(
            id = "custom-" + System.currentTimeMillis(),
            name = cleanName,
            baseUrl = cleanUrl,
            kind = ModelSourceKind.HF,
            preset = false,
        )
        settings = settings.copy(
            modelSources = settings.modelSources + source,
            activeSourceId = source.id,
        )
        hubModels = emptyList()
        hubError = null
        hubFiles.clear()
        hubLoadingFiles.clear()
        saveSettingsNow()
        toast = "已添加模型源：$cleanName"
    }

    fun deleteModelSource(id: String) {
        val source = settings.modelSources.firstOrNull { it.id == id } ?: return
        if (source.preset) {
            toast = "内置模型源不能删除"
            return
        }
        val remaining = settings.modelSources.filterNot { it.id == id }
        if (remaining.isEmpty()) return
        val nextId = if (settings.activeSourceId == id) remaining.first().id else settings.activeSourceId
        settings = settings.copy(modelSources = remaining, activeSourceId = nextId)
        hubModels = emptyList()
        hubError = null
        hubFiles.clear()
        hubLoadingFiles.clear()
        saveSettingsNow()
        toast = "已删除模型源"
    }

    fun selectEndpoint(id: String) {
        settings = settings.copy(activeEndpointId = id, useLocal = false)
        connectionStatus = null
        localSession = null
        saveSettingsSoon()
    }

    fun useLocalModel(path: String) {
        settings = settings.copy(useLocal = true, localModelPath = path)
        localSession = null
        saveSettingsNow()
        toast = "已选择本地模型"
    }

    fun selectApiModel(endpointId: String, model: String) {
        settings = settings.copy(
            useLocal = false,
            activeEndpointId = endpointId,
            endpoints = settings.endpoints.map {
                if (it.id == endpointId) it.copy(model = model) else it
            },
        )
        localSession = null
        saveSettingsNow()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        saveSettingsSoon()
    }

    fun updateEndpoint(endpoint: ApiEndpoint) {
        settings = settings.copy(
            endpoints = settings.endpoints.map { if (it.id == endpoint.id) endpoint else it },
        )
        saveSettingsSoon()
    }

    fun addCustomEndpoint() {
        val endpoint = ApiEndpoint(
            id = "custom-" + System.currentTimeMillis(),
            name = "自定义接口",
            baseUrl = "",
            model = "",
            visionModel = "",
            models = emptyList(),
        )
        settings = settings.copy(
            endpoints = settings.endpoints + endpoint,
            activeEndpointId = endpoint.id,
            useLocal = false,
        )
        saveSettingsSoon()
    }

    fun deleteEndpoint(id: String) {
        val endpoint = settings.endpoints.firstOrNull { it.id == id } ?: return
        if (endpoint.preset) {
            toast = "内置服务商不能删除，可以改 Key 或加自定义接口"
            return
        }
        val remaining = settings.endpoints.filterNot { it.id == id }
        if (remaining.isEmpty()) return
        settings = settings.copy(
            endpoints = remaining,
            activeEndpointId = if (settings.activeEndpointId == id) remaining.first().id else settings.activeEndpointId,
        )
        saveSettingsSoon()
    }

    fun testConnection() {
        val endpoint = settings.activeEndpoint()
        if (endpoint.baseUrl.isBlank()) {
            connectionStatus = "先填写 API 地址"
            return
        }
        testingConnection = true
        connectionStatus = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.listModels(endpoint.baseUrl, endpoint.apiKey) }
            }
            connectionStatus = result.fold(
                onSuccess = { models -> "连接成功，检测到 ${models.size} 个模型" },
                onFailure = { "连接失败：${it.message}" },
            )
            testingConnection = false
        }
    }

    fun downloadModel(repo: String, file: HubFile) {
        if (downloads.any { it.repo == repo && it.file == file.name }) {
            toast = "这个模型已经在下载队列里了"
            return
        }
        val source = activeSource
        runCatching { hub.download(source, repo, file) }
            .onSuccess { record ->
                downloads = downloads + record
                store.saveDownloads(downloads)
                toast = "开始下载 ${file.name.substringAfterLast('/')}"
            }
            .onFailure { toast = "无法开始下载：${it.message}" }
    }

    fun cancelDownload(downloadId: Long) {
        val record = downloads.firstOrNull { it.downloadId == downloadId }
        hub.cancel(downloadId)
        downloads = downloads.filterNot { it.downloadId == downloadId }
        downloadProgress.remove(downloadId)
        store.saveDownloads(downloads)
        if (record != null) File(record.path + ".part").delete()
        toast = "已取消下载"
    }

    fun deleteLocalModel(model: LocalModel) {
        if (engine.loadedModelPath() == model.path) {
            viewModelScope.launch { engine.release() }
        }
        if (store.deleteModel(model.path)) {
            refreshLocalModels()
            if (settings.localModelPath == model.path) {
                settings = settings.copy(localModelPath = null, useLocal = false)
                localSession = null
                saveSettingsNow()
            }
            toast = "已删除 ${model.name}"
        } else {
            toast = "删除失败"
        }
    }

    fun searchHub(query: String) {
        hubLoading = true
        hubError = null
        val source = activeSource
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { hub.search(source, query) }
            }
            result.onSuccess {
                hubModels = it
                hubLoading = false
            }.onFailure {
                hubError = it.message ?: "搜索失败"
                hubLoading = false
            }
        }
    }

    fun loadHubFiles(repo: String) {
        if (hubFiles.containsKey(repo) || hubLoadingFiles[repo] == true) return
        hubLoadingFiles[repo] = true
        val source = activeSource
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { hub.files(source, repo) } }
            result.onSuccess {
                hubFiles[repo] = it
                hubLoadingFiles[repo] = false
            }.onFailure {
                toast = it.message ?: "读取文件列表失败"
                hubLoadingFiles[repo] = false
            }
        }
    }

    // ---------------------------------------------------------------- attachments

    fun attachFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AttachTools.attachmentFromUri(app, uri) }
            }
            result.onSuccess { pendingAttachments += it }
                .onFailure { toast = it.message ?: "读取附件失败" }
        }
    }

    fun attachCamera(file: File) {
        pendingAttachments += AttachTools.cameraAttachment(app, file)
    }

    fun removeAttachment(id: String) {
        pendingAttachments.removeAll { it.id == id }
    }

    fun newCameraFile(): File = AttachTools.newCameraFile(app)

    // ---------------------------------------------------------------- import

    fun loadImport(uri: Uri) {
        importLoading = true
        importError = null
        importPreview = emptyList()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = AttachTools.displayName(app, uri)
                    val text = AttachTools.readDocumentText(app, uri, name)
                        ?: throw IOException("读不出这个文件，换 JSON / TXT / MD / PDF / DOCX 试试")
                    ChatImport.parse(text, name)
                }
            }
            result.onSuccess { chats ->
                importPreview = chats
                if (chats.isEmpty()) importError = "没有识别到聊天记录"
                importLoading = false
            }.onFailure {
                importError = it.message ?: "导入失败"
                importLoading = false
            }
        }
    }

    fun loadImportText(text: String) {
        importError = null
        importPreview = emptyList()
        if (text.isBlank()) {
            importError = "先粘贴聊天记录"
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ChatImport.parse(text, "粘贴的聊天记录") }
            }
            result.onSuccess {
                importPreview = it
                if (it.isEmpty()) importError = "没有识别到聊天记录"
            }.onFailure { importError = it.message ?: "解析失败" }
        }
    }

    fun confirmImport() {
        val chats = importPreview
        if (chats.isEmpty()) return
        var firstId: String? = null
        chats.forEachIndexed { index, chat ->
            val conversation = Conversation(title = chat.title.ifBlank { "导入的对话" })
            chat.messages.forEach { (role, content) ->
                conversation.messages += ChatMessage(role = role, content = content)
            }
            conversation.updatedAt = System.currentTimeMillis()
            conversations.add(index, conversation)
            if (index == 0) firstId = conversation.id
        }
        firstId?.let { activeId = it }
        importPreview = emptyList()
        importError = null
        localSession = null
        screen = Screen.CHAT
        persistSoon()
        toast = "已导入 ${chats.size} 个对话"
    }

    fun clearImport() {
        importPreview = emptyList()
        importError = null
    }

    fun clearToast() {
        toast = null
    }

    // ---------------------------------------------------------------- persistence

    private fun persistSoon() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(400)
            val payload = store.buildConversationsJson(conversations.toList())
            withContext(Dispatchers.IO) { store.writeConversationsJson(payload) }
        }
    }

    private fun saveSettingsSoon() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            delay(400)
            val snapshot = settings
            withContext(Dispatchers.IO) { store.saveSettings(snapshot) }
        }
    }

    private fun saveSettingsNow() {
        settingsJob?.cancel()
        runCatching { store.saveSettings(settings) }
    }

    fun flush() {
        runCatching {
            store.writeConversationsJson(store.buildConversationsJson(conversations.toList()))
            store.saveSettings(settings)
            store.saveDownloads(downloads)
        }
    }

    private fun startDownloadWatcher() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val snapshot = downloads
                for (record in snapshot) {
                    val progress = hub.progress(record.downloadId) ?: continue
                    downloadProgress[record.downloadId] = progress
                    if (progress.success) {
                        val part = File(record.path + ".part")
                        val target = File(record.path)
                        if (part.exists()) {
                            if (!part.renameTo(target)) {
                                part.delete()
                            }
                        }
                        downloads = downloads.filterNot { it.downloadId == record.downloadId }
                        downloadProgress.remove(record.downloadId)
                        store.saveDownloads(downloads)
                        refreshLocalModels()
                        toast = "模型下载完成：${target.name}"
                    } else if (progress.failed) {
                        File(record.path + ".part").delete()
                        downloads = downloads.filterNot { it.downloadId == record.downloadId }
                        downloadProgress.remove(record.downloadId)
                        store.saveDownloads(downloads)
                        toast = "模型下载失败，请检查网络后重试"
                    }
                }
                delay(1200)
            }
        }
    }

    private class LocalSession(
        val conversationId: String,
        val systemPrompt: String,
        val ids: MutableList<String>,
    )
}
