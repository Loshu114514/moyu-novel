package com.moyu.novel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DrawerState
import com.moyu.novel.AttachKind
import com.moyu.novel.AttachTools
import com.moyu.novel.Attachment
import com.moyu.novel.ChatMessage
import com.moyu.novel.ChatViewModel
import com.moyu.novel.Conversation
import com.moyu.novel.NOVEL_SUGGESTIONS
import com.moyu.novel.Role
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(vm: ChatViewModel, onMenu: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = vm.activeConversation?.title ?: "墨语",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Outlined.Menu, contentDescription = "菜单")
            }
        },
        actions = {
            IconButton(onClick = { vm.newChat() }) {
                Icon(Icons.Outlined.Add, contentDescription = "新建对话")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MoyuBg,
            titleContentColor = MoyuText,
            navigationIconContentColor = MoyuText,
            actionIconContentColor = MoyuText,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MoyuBg,
            titleContentColor = MoyuText,
            navigationIconContentColor = MoyuText,
        ),
    )
}

@Composable
fun DrawerContent(vm: ChatViewModel, drawerState: DrawerState) {
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch { drawerState.close() }
    }

    ModalDrawerSheet(
        drawerContainerColor = MoyuSurface,
        drawerContentColor = MoyuText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MoyuBlue.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("墨", color = MoyuBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("墨语", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("AI 小说创作", color = MoyuTextDim, fontSize = 11.sp)
                }
                IconButton(onClick = { close() }) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = MoyuTextDim)
                }
            }

            Button(
                onClick = {
                    vm.newChat()
                    close()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MoyuBlue, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建对话")
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "对话",
                color = MoyuTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(vm.conversations, key = { it.id }) { conversation ->
                    DrawerConversationRow(
                        conversation = conversation,
                        selected = conversation.id == vm.activeId,
                        onClick = {
                            vm.selectChat(conversation.id)
                            close()
                        },
                        onDelete = { vm.deleteChat(conversation.id) },
                    )
                }
            }

            HorizontalDivider(color = MoyuBorder)
            DrawerActionRow(Icons.Outlined.Memory, "模型库", "本地 GGUF 下载") {
                vm.goTo(com.moyu.novel.Screen.MODELS)
                close()
            }
            DrawerActionRow(Icons.Outlined.Download, "导入聊天记录", "JSON / TXT / PDF") {
                vm.goTo(com.moyu.novel.Screen.IMPORT)
                close()
            }
            DrawerActionRow(Icons.Outlined.Settings, "设置", "API · 参数 · 创作设定") {
                vm.goTo(com.moyu.novel.Screen.SETTINGS)
                close()
            }
            Text(
                text = "本地模型 ${vm.localModels.size} 个 · ${vm.activeModelLabel()}",
                color = MoyuTextDim.copy(alpha = 0.75f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun DrawerConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) MoyuSurfaceElevated else Color.Transparent)
            .padding(start = 20.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = if (selected) MoyuBlue else MoyuTextDim,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = conversation.title,
            color = if (selected) MoyuText else MoyuText.copy(alpha = 0.85f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "删除对话",
                tint = MoyuTextDim,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun DrawerActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MoyuTextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 14.sp)
            Text(subtitle, color = MoyuTextDim, fontSize = 11.sp)
        }
    }
}

@Composable
fun ChatBody(vm: ChatViewModel) {
    val conversation = vm.activeConversation
    if (conversation == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    if (conversation.messages.isEmpty()) {
        HomeContent(vm)
    } else {
        MessageList(vm, conversation)
    }
}

@Composable
private fun HomeContent(vm: ChatViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MoyuBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("墨", fontSize = 36.sp, color = MoyuBlue, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text("你好，我是墨语", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "本地模型 · 云端 API · 为写小说而生",
            color = MoyuTextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(34.dp))

        NOVEL_SUGGESTIONS.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { suggestion ->
                    SuggestionCard(
                        text = suggestion,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.send(suggestion) },
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Memory,
                contentDescription = null,
                tint = MoyuTextDim.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "当前模型：${vm.activeModelLabel()}",
                color = MoyuTextDim.copy(alpha = 0.8f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SuggestionCard(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 78.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MoyuSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MoyuBorder),
    ) {
        Text(
            text = text,
            color = MoyuText.copy(alpha = 0.92f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun MessageList(vm: ChatViewModel, conversation: Conversation) {
    val listState = rememberLazyListState()
    val messages = conversation.messages
    val last = messages.lastOrNull()

    LaunchedEffect(conversation.id) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    LaunchedEffect(last?.id, last?.content?.length, last?.reasoning?.length) {
        if (messages.isNotEmpty() && last?.streaming == true) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            MessageItem(vm, message, isLast = index == messages.lastIndex)
        }
    }
}

@Composable
private fun MessageItem(vm: ChatViewModel, message: ChatMessage, isLast: Boolean) {
    if (message.role == Role.USER) {
        UserMessage(message)
    } else {
        AssistantMessage(vm, message, isLast)
    }
}

@Composable
private fun UserMessage(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.attachments.forEach { attachment ->
            AttachmentBadge(attachment)
        }
        if (message.content.isNotBlank()) {
            Surface(
                color = MoyuBlue,
                shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        color = Color.White,
                        fontSize = 16.sp,
                        lineHeight = 25.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(vm: ChatViewModel, message: ChatMessage, isLast: Boolean) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (message.reasoning.isNotBlank() || (message.streaming && message.content.isBlank())) {
            ThinkingBlock(message)
        }
        if (message.streaming && message.content.isBlank() && message.reasoning.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MoyuBlue,
                )
                Spacer(Modifier.width(8.dp))
                Text("正在思考…", color = MoyuTextDim, fontSize = 13.sp)
            }
        }
        if (message.content.isNotBlank()) {
            MarkdownText(message.content)
        }
        if (message.streaming && (message.content.isNotBlank() || message.reasoning.isNotBlank())) {
            Text("▍", color = MoyuBlue, fontSize = 14.sp)
        }
        message.error?.let { error ->
            Surface(
                color = MoyuError.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error,
                    color = MoyuError,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        if (!message.streaming && (message.content.isNotBlank() || message.error != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SmallIconButton(Icons.Outlined.ContentCopy, "复制") {
                    clipboard.setText(AnnotatedString(message.content))
                }
                if (isLast) {
                    SmallIconButton(Icons.Outlined.Refresh, "重新生成") { vm.regenerate() }
                }
                Spacer(Modifier.weight(1f))
                if (message.model.isNotBlank()) {
                    Text(
                        text = message.model,
                        color = MoyuTextDim.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MoyuTextDim,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun ThinkingBlock(message: ChatMessage) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    val thinkingNow = message.streaming && message.content.isBlank()
    val seconds = (message.reasoningMs / 1000L).coerceAtLeast(0L)
    val title = when {
        thinkingNow -> "深度思考中…"
        seconds > 0 -> "已深度思考（用时 ${seconds} 秒）"
        else -> "已深度思考"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MoyuSurface.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MoyuTextDim,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(title, color = MoyuTextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MoyuTextDim,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = expanded || thinkingNow) {
            Text(
                text = message.reasoning,
                color = MoyuTextDim,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AttachmentBadge(attachment: Attachment, onRemove: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (attachment.kind == AttachKind.IMAGE) MoyuSurfaceElevated else MoyuSurfaceElevated)
            .padding(start = 9.dp, end = if (onRemove != null) 2.dp else 9.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (attachment.kind == AttachKind.IMAGE) Icons.Outlined.Image else Icons.Outlined.Description,
            contentDescription = null,
            tint = MoyuTextDim,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = attachment.name,
            color = MoyuText.copy(alpha = 0.9f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "移除附件",
                    tint = MoyuTextDim,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
fun Composer(vm: ChatViewModel) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachFromUri(uri)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.attachFromUri(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraFile
        if (success && file != null) vm.attachCamera(file)
        cameraFile = null
    }

    fun openCamera() {
        val file = vm.newCameraFile()
        cameraFile = file
        runCatching {
            cameraLauncher.launch(AttachTools.uriForFile(context, file))
        }.onFailure {
            vm.toast = "无法打开相机"
        }
    }

    Surface(color = MoyuBg) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                AssistChip(
                    onClick = { vm.showModelPicker = true },
                    label = {
                        Text(
                            text = vm.activeModelLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            modifier = Modifier.widthIn(max = 150.dp),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
                Spacer(Modifier.width(8.dp))
                if (!vm.settings.useLocal) {
                    FilterChip(
                        selected = vm.settings.deepThinking,
                        onClick = {
                            vm.updateSettings { it.copy(deepThinking = !it.deepThinking) }
                        },
                        label = { Text("深度思考", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (vm.settings.deepThinking) Icons.Outlined.Check else Icons.Outlined.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (vm.isGenerating) {
                    Text("生成中…", color = MoyuTextDim, fontSize = 11.sp)
                }
            }

            if (vm.pendingAttachments.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    items(vm.pendingAttachments, key = { it.id }) { attachment ->
                        AttachmentBadge(attachment) { vm.removeAttachment(attachment.id) }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "添加附件", tint = MoyuTextDim)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("图片") },
                            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                imagePicker.launch("image/*")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("文件") },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                documentPicker.launch(arrayOf("*/*"))
                            },
                        )
                    }
                }
                IconButton(onClick = { openCamera() }) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = "拍照", tint = MoyuTextDim)
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = MoyuSurfaceElevated,
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                        if (input.isEmpty()) {
                            Text("给墨语发消息…", color = MoyuTextDim, fontSize = 15.sp)
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(color = MoyuText, fontSize = 15.sp, lineHeight = 22.sp),
                            cursorBrush = SolidColor(MoyuBlue),
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                val canSend = input.isNotBlank() || vm.pendingAttachments.isNotEmpty()
                FilledIconButton(
                    onClick = {
                        if (vm.isGenerating) {
                            vm.stop()
                        } else if (canSend) {
                            vm.send(input)
                            input = ""
                        }
                    },
                    enabled = vm.isGenerating || canSend,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MoyuBlue,
                        contentColor = Color.White,
                        disabledContainerColor = MoyuSurfaceHigh,
                        disabledContentColor = MoyuTextDim,
                    ),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (vm.isGenerating) Icons.Outlined.Stop else Icons.Outlined.Send,
                        contentDescription = if (vm.isGenerating) "停止生成" else "发送",
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(vm: ChatViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.showModelPicker = false },
        sheetState = sheetState,
        containerColor = MoyuSurface,
        contentColor = MoyuText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Text("选择模型", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            vm.settings.endpoints.forEach { endpoint ->
                Text(
                    text = endpoint.name,
                    color = MoyuTextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                val models = endpoint.models.ifEmpty {
                    listOf(endpoint.model).filter { it.isNotBlank() }
                }
                if (models.isEmpty()) {
                    Text(
                        "还没有填写模型名称（设置 → API 服务）",
                        color = MoyuTextDim.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                models.forEach { model ->
                    ModelRow(
                        title = model,
                        subtitle = if (endpoint.preset) endpoint.name else "自定义接口",
                        selected = !vm.settings.useLocal &&
                            vm.settings.activeEndpointId == endpoint.id &&
                            endpoint.model == model,
                        onClick = {
                            vm.selectApiModel(endpoint.id, model)
                            vm.showModelPicker = false
                        },
                    )
                }
            }

            Text(
                text = "本地模型",
                color = MoyuTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
            )
            if (vm.localModels.isEmpty()) {
                Text(
                    "还没有下载本地模型，下载后可以完全离线跑小说。",
                    color = MoyuTextDim.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            vm.localModels.forEach { model ->
                ModelRow(
                    title = model.name,
                    subtitle = com.moyu.novel.formatBytes(model.size) + " · 本地推理",
                    selected = vm.settings.useLocal && vm.settings.localModelPath == model.path,
                    onClick = {
                        vm.useLocalModel(model.path)
                        vm.showModelPicker = false
                    },
                )
            }

            TextButton(
                onClick = {
                    vm.showModelPicker = false
                    vm.goTo(com.moyu.novel.Screen.MODELS)
                },
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("下载 / 管理本地模型")
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ModelRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MoyuText else MoyuText.copy(alpha = 0.9f),
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MoyuTextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
