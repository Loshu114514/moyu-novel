package com.moyu.novel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyu.novel.ApiEndpoint
import com.moyu.novel.AppSettings
import com.moyu.novel.ChatMode
import com.moyu.novel.ChatViewModel
import com.moyu.novel.DEFAULT_CHAT_PROMPT
import com.moyu.novel.DEFAULT_SYSTEM_PROMPT
import com.moyu.novel.DeviceInfo
import com.moyu.novel.Screen
import com.moyu.novel.formatBytes
import java.util.Locale

@Composable
fun SettingsScreen(vm: ChatViewModel) {
    val settings = vm.settings
    val endpoint = settings.activeEndpoint()

    var baseUrl by remember(endpoint.id) { mutableStateOf(endpoint.baseUrl) }
    var apiKey by remember(endpoint.id) { mutableStateOf(endpoint.apiKey) }
    var model by remember(endpoint.id) { mutableStateOf(endpoint.model) }
    var visionModel by remember(endpoint.id) { mutableStateOf(endpoint.visionModel) }
    var keyVisible by remember(endpoint.id) { mutableStateOf(false) }
    var systemPrompt by remember { mutableStateOf(settings.systemPrompt) }
    var chatPrompt by remember { mutableStateOf(settings.chatPrompt) }
    var confirmClear by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var sourceName by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }

    fun pushEndpoint() {
        vm.updateEndpoint(
            endpoint.copy(
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                model = model.trim(),
                visionModel = visionModel.trim(),
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
    ) {
        item {
            Section("API 服务") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(settings.endpoints, key = { it.id }) { item ->
                        FilterChip(
                            selected = item.id == settings.activeEndpointId,
                            onClick = { vm.selectEndpoint(item.id) },
                            label = { Text(item.name, fontSize = 12.sp) },
                        )
                    }
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; pushEndpoint() },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.deepseek.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; pushEndpoint() },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { keyVisible = !keyVisible }) {
                            Text(if (keyVisible) "隐藏" else "显示", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; pushEndpoint() },
                    label = { Text("对话模型") },
                    placeholder = { Text("deepseek-chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = visionModel,
                    onValueChange = { visionModel = it; pushEndpoint() },
                    label = { Text("视觉模型（识图用，可留空）") },
                    placeholder = { Text("qwen3-vl-plus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { vm.testConnection() },
                        enabled = !vm.testingConnection,
                        colors = ButtonDefaults.buttonColors(containerColor = MoyuBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(if (vm.testingConnection) "测试中…" else "测试连接")
                    }
                    Spacer(Modifier.width(12.dp))
                    if (!endpoint.preset) {
                        TextButton(onClick = { vm.deleteEndpoint(endpoint.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除该接口")
                        }
                    }
                }
                vm.connectionStatus?.let { status ->
                    Text(
                        text = status,
                        color = if (status.startsWith("连接成功")) MoyuGreen else MoyuError,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                TextButton(onClick = { vm.addCustomEndpoint() }) {
                    Text("+ 新增自定义接口（OpenAI 兼容）")
                }
                Text(
                    "Key 只保存在本机应用目录，不会上传到任何第三方。",
                    color = MoyuTextDim.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }

        item {
            Section("模型源") {
                settings.modelSources.forEach { modelSource ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(modelSource.name, fontSize = 13.sp)
                            Text(
                                modelSource.baseUrl,
                                color = MoyuTextDim,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        if (modelSource.preset) {
                            Text("内置", color = MoyuTextDim, fontSize = 10.sp)
                        } else {
                            IconButton(onClick = { vm.deleteModelSource(modelSource.id) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "删除模型源",
                                    tint = MoyuTextDim,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { showSourceDialog = true }) {
                    Text("+ 添加自定义模型源")
                }
                Text(
                    "自定义源需兼容 HuggingFace 的 /api/models 接口（可填任意镜像地址）。魔搭为内置目录源，覆盖 0.5B–235B。",
                    color = MoyuTextDim.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        item {
            Section("生成参数") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("温度", fontSize = 14.sp, modifier = Modifier.width(70.dp))
                    Slider(
                        value = settings.temperature,
                        onValueChange = { value -> vm.updateSettings { it.copy(temperature = value) } },
                        valueRange = 0f..1.5f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        String.format(Locale.US, "%.2f", settings.temperature),
                        color = MoyuTextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.width(44.dp),
                    )
                }
                Text("写作建议 0.7–0.9；越高越发散。", color = MoyuTextDim, fontSize = 11.sp)

                Text("单次最大生成长度", fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(512, 1024, 2048, 4096)) { value ->
                        FilterChip(
                            selected = settings.maxTokens == value,
                            onClick = { vm.updateSettings { it.copy(maxTokens = value) } },
                            label = { Text("$value", fontSize = 12.sp) },
                        )
                    }
                }

                Text("本地模型上下文长度", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(2048, 4096, 8192)) { value ->
                        FilterChip(
                            selected = settings.contextSize == value,
                            onClick = {
                                vm.updateSettings { it.copy(contextSize = value) }
                                vm.toast = if (value >= 8192) "8K 上下文约多占 1GB 内存，8GB 以下手机慎用" else "上下文已设为 $value"
                            },
                            label = { Text("$value", fontSize = 12.sp) },
                        )
                    }
                }
                Text(
                    "上下文长度 = 模型一次能记住多少内容。2048 大约 1500 字，4096 大约 3000 字，8192 大约 6000 字；" +
                        "越大越占内存。写长篇时把关键设定标成“重要”，超出部分也不会被忘掉。",
                    color = MoyuTextDim,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )

                Text("本地推理线程", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(2, 4, 6, 8)) { value ->
                        FilterChip(
                            selected = settings.localThreads == value,
                            onClick = { vm.updateSettings { it.copy(localThreads = value) } },
                            label = { Text("$value", fontSize = 12.sp) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("深度思考", fontSize = 14.sp)
                        Text("对 DeepSeek-Reasoner / Qwen3 等模型生效", color = MoyuTextDim, fontSize = 11.sp)
                    }
                    Switch(
                        checked = settings.deepThinking,
                        onCheckedChange = { value -> vm.updateSettings { it.copy(deepThinking = value) } },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动识别重要对话", fontSize = 14.sp)
                        Text("含设定/人物/记住等内容会自动收藏", color = MoyuTextDim, fontSize = 11.sp)
                    }
                    Switch(
                        checked = settings.autoMarkImportant,
                        onCheckedChange = { value -> vm.updateSettings { it.copy(autoMarkImportant = value) } },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("默认开启联网搜索", fontSize = 14.sp)
                        Text("也可以随时点输入框上方的“联网”", color = MoyuTextDim, fontSize = 11.sp)
                    }
                    Switch(
                        checked = settings.webSearch,
                        onCheckedChange = { value -> vm.updateSettings { it.copy(webSearch = value) } },
                    )
                }

                Text("新对话默认模式", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ChatMode.entries, key = { it.name }) { mode ->
                        FilterChip(
                            selected = settings.defaultMode == mode,
                            onClick = { vm.updateSettings { it.copy(defaultMode = mode) } },
                            label = { Text(mode.label, fontSize = 12.sp) },
                        )
                    }
                }
            }
        }

        item {
            Section("模式提示词") {
                Text("日常聊天", fontSize = 13.sp, color = MoyuTextDim)
                OutlinedTextField(
                    value = chatPrompt,
                    onValueChange = {
                        chatPrompt = it
                        vm.updateSettings { current -> current.copy(chatPrompt = it) }
                    },
                    label = { Text("日常聊天提示词") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    chatPrompt = DEFAULT_CHAT_PROMPT
                    vm.updateSettings { it.copy(chatPrompt = DEFAULT_CHAT_PROMPT) }
                }) {
                    Text("恢复日常聊天默认提示词")
                }

                Text("小说创作", fontSize = 13.sp, color = MoyuTextDim)
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = {
                        systemPrompt = it
                        vm.updateSettings { current -> current.copy(systemPrompt = it) }
                    },
                    label = { Text("小说创作提示词") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    systemPrompt = DEFAULT_SYSTEM_PROMPT
                    vm.updateSettings { it.copy(systemPrompt = DEFAULT_SYSTEM_PROMPT) }
                }) {
                    Text("恢复小说创作默认提示词")
                }
                Text(
                    "智能体模式使用内置工具提示词，不需要在这里改。",
                    color = MoyuTextDim.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
            }
        }

        item {
            Section("本地模型") {
                Text(vm.engineStatus, color = MoyuTextDim, fontSize = 12.sp)
                Text(
                    DeviceInfo.localHint(),
                    color = if (DeviceInfo.isSnapdragon8Gen2OrNewer) MoyuGreen else MoyuTextDim,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
                if (vm.localModels.isEmpty()) {
                    Text(
                        "还没有下载模型。点下面的按钮去模型库挑一个 GGUF（推荐 Qwen3.5 4B Q4，约 2.5GB）。",
                        color = MoyuTextDim,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                vm.localModels.forEach { local ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(local.name, fontSize = 13.sp, lineHeight = 18.sp)
                            Text(formatBytes(local.size), color = MoyuTextDim, fontSize = 11.sp)
                        }
                        if (settings.useLocal && settings.localModelPath == local.path) {
                            Text("使用中", color = MoyuGreen, fontSize = 11.sp)
                            Spacer(Modifier.width(6.dp))
                        } else {
                            TextButton(onClick = { vm.useLocalModel(local.path) }) {
                                Text("使用", fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { vm.deleteLocalModel(local) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除模型",
                                tint = MoyuTextDim,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.goTo(Screen.MODELS) }) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("打开模型库")
                    }
                    TextButton(onClick = { vm.showLocalModelManager = true }) {
                        Text("管理 / 删除 / 导入")
                    }
                }
                Text(
                    "模型来自 hf-mirror.com（HuggingFace 国内镜像），下载后完全离线可用。",
                    color = MoyuTextDim.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        item {
            Section("数据") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.goTo(Screen.IMPORT) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, tint = MoyuTextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("导入聊天记录", fontSize = 14.sp)
                        Text("JSON / TXT / MD / PDF / DOCX", color = MoyuTextDim, fontSize = 11.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { confirmClear = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MoyuError, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("清空所有对话", fontSize = 14.sp, color = MoyuError)
                        Text("本地记录会一起删除", color = MoyuTextDim, fontSize = 11.sp)
                    }
                }
                Text(
                    "本地模型占用 ${formatBytes(vm.localModels.sumOf { it.size })} · 对话 ${vm.conversations.size} 个",
                    color = MoyuTextDim,
                    fontSize = 11.sp,
                )
            }
        }

        item {
            Section("关于") {
                Text("墨语 1.0.3", fontSize = 14.sp)
                Text(
                    "DeepSeek 风格的 AI 小说创作客户端。支持 OpenAI 兼容 API、本地 GGUF 离线推理（llama.cpp）、识图、文件导入、思考过程展示。",
                    color = MoyuTextDim,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    "手机需要 arm64 架构。模型下载地址：hf-mirror.com。",
                    color = MoyuTextDim.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("添加自定义模型源") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = { Text("名称") },
                        placeholder = { Text("例如：公司内网镜像") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it },
                        label = { Text("地址") },
                        placeholder = { Text("https://hf-mirror.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "地址要能访问 /api/models 和 /api/models/{repo}?blobs=true。",
                        color = MoyuTextDim,
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addModelSource(sourceName, sourceUrl)
                    if (sourceName.isNotBlank() && sourceUrl.isNotBlank()) {
                        sourceName = ""
                        sourceUrl = ""
                        showSourceDialog = false
                    }
                }) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("取消") }
            },
            containerColor = MoyuSurface,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有对话？") },
            text = { Text("删除后无法恢复，本地模型不会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllChats()
                    confirmClear = false
                }) {
                    Text("清空", color = MoyuError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
            containerColor = MoyuSurface,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            text = title,
            color = MoyuTextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Surface(color = MoyuSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}
