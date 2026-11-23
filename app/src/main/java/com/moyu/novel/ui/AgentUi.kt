package com.moyu.novel.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyu.novel.AgentMemory
import com.moyu.novel.AgentProfile
import com.moyu.novel.ChatMessage
import com.moyu.novel.ChatViewModel
import com.moyu.novel.Conversation
import com.moyu.novel.Role
import com.moyu.novel.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AgentAvatar(agent: AgentProfile, size: Dp = 48.dp) {
    val bitmap by produceState<Bitmap?>(initialValue = null, agent.avatarPath) {
        value = agent.avatarPath?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(MoyuBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = agent.name.take(1).ifBlank { "智" },
                color = MoyuBlue,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun AgentListScreen(vm: ChatViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(color = MoyuSurface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("我的智能体", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "自己创建角色：人设、头像、背景、上下文长度和记忆池都由你定。聊天记录就是角色记忆的来源，撤回的消息不会进记忆池。",
                        color = MoyuTextDim,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Button(
                        onClick = { vm.createAgent() },
                        colors = ButtonDefaults.buttonColors(containerColor = MoyuBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("创建智能体")
                    }
                }
            }
        }

        if (vm.agents.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = MoyuTextDim,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("还没有智能体", color = MoyuTextDim, fontSize = 13.sp)
                    Text("点上面的按钮创建第一个角色", color = MoyuTextDim, fontSize = 11.sp)
                }
            }
        }

        items(vm.agents, key = { it.id }) { agent ->
            val conversation = vm.conversations.firstOrNull { it.id == agent.conversationId }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { vm.openAgent(agent.id) },
                color = MoyuSurface,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AgentAvatar(agent, 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(agent.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            agent.persona.ifBlank { "还没有填写人设" },
                            color = MoyuTextDim,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${conversation?.messages?.count { it.role == Role.USER } ?: 0} 轮对话 · " +
                                "${agent.memories.size} 条记忆 · 上下文 ${agent.contextLength}",
                            color = MoyuTextDim.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                        )
                    }
                    IconButton(onClick = { vm.editAgent(agent.id) }) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "编辑智能体",
                            tint = MoyuTextDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgentEditScreen(vm: ChatViewModel) {
    val agent = vm.editingAgent ?: return
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setAgentAvatar(agent, uri)
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setAgentBackground(agent, uri)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(color = MoyuSurface, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AgentAvatar(agent, 64.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("头像", fontSize = 12.sp, color = MoyuTextDim)
                        TextButton(onClick = { avatarPicker.launch("image/*") }) { Text("更换头像") }
                    }
                }
                TextButton(onClick = { backgroundPicker.launch("image/*") }) {
                    Text(if (agent.backgroundPath == null) "上传聊天背景图" else "更换聊天背景图")
                }
                if (agent.backgroundPath != null) {
                    AgentBackgroundPreview(agent.backgroundPath!!, Modifier.fillMaxWidth().height(110.dp))
                }
            }
        }

        OutlinedTextField(
            value = agent.name,
            onValueChange = { agent.name = it },
            label = { Text("名字") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = agent.persona,
            onValueChange = { agent.persona = it },
            label = { Text("人设 / 性格 / 说话方式") },
            minLines = 6,
            maxLines = 14,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = agent.greeting,
            onValueChange = { agent.greeting = it },
            label = { Text("开场白（可选）") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("上下文长度", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2048, 4096, 8192).forEach { value ->
                FilterChip(
                    selected = agent.contextLength == value,
                    onClick = { agent.contextLength = value },
                    label = { Text("$value", fontSize = 12.sp) },
                )
            }
        }
        Text(
            "上下文长度就是一次能记住多少字：2048 约 1500 字，4096 约 3000 字，8192 约 6000 字。越大越占内存；角色聊天建议 4096。",
            color = MoyuTextDim,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.saveAgent(agent) },
                colors = ButtonDefaults.buttonColors(containerColor = MoyuBlue, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("保存")
            }
            TextButton(onClick = {
                vm.openAgentMemory(agent.id)
            }) {
                Text("记忆池（${agent.memories.size}）")
            }
        }
        TextButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MoyuError, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("删除这个智能体", color = MoyuError)
        }
        Spacer(Modifier.height(20.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除智能体？") },
            text = { Text("会同时删除它的聊天记录和记忆池。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAgent(agent.id)
                    confirmDelete = false
                }) {
                    Text("删除", color = MoyuError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
            containerColor = MoyuSurface,
        )
    }
}

@Composable
private fun AgentBackgroundPreview(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatTopBar(vm: ChatViewModel) {
    val agent = vm.activeAgent
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (agent != null) {
                    AgentAvatar(agent, 34.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(agent.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "记忆 ${agent.memories.size} 条 · 上下文 ${agent.contextLength}",
                            color = MoyuTextDim,
                            fontSize = 10.sp,
                        )
                    }
                } else {
                    Text("智能体", fontSize = 16.sp)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = { vm.goTo(Screen.AGENTS) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            IconButton(onClick = { vm.goTo(Screen.AGENT_MEMORY) }) {
                Icon(Icons.Outlined.Bookmark, contentDescription = "记忆池")
            }
            IconButton(onClick = { agent?.let { vm.editAgent(it.id) } }) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑智能体")
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

@Composable
fun AgentChatScreen(vm: ChatViewModel, conversation: Conversation) {
    val agent = vm.activeAgent ?: return
    val listState = rememberLazyListState()
    val messages = conversation.messages
    val last = messages.lastOrNull()
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(conversation.id) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    LaunchedEffect(last?.id, last?.content?.length, last?.reasoning?.length) {
        if (messages.isNotEmpty() && last?.streaming == true) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val backgroundPath = agent.backgroundPath
        if (backgroundPath != null) {
            val bitmap by produceState<Bitmap?>(initialValue = null, backgroundPath) {
                value = withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(backgroundPath) }.getOrNull()
                }
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.28f),
                )
            }
            Box(Modifier.fillMaxSize().background(MoyuBg.copy(alpha = 0.45f)))
        }

        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AgentAvatar(agent, 78.dp)
                Spacer(Modifier.height(12.dp))
                Text(agent.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (agent.persona.isNotBlank()) {
                    Text(
                        agent.persona,
                        color = MoyuTextDim,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    "发一句开场，角色会自动记住之后的对话",
                    color = MoyuTextDim.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(messages, key = { _, message -> message.id }) { _, message ->
                    AgentMessageRow(
                        agent = agent,
                        message = message,
                        onMore = { actionMessage = message },
                    )
                }
            }
        }
    }

    actionMessage?.let { message ->
        AgentMessageActions(
            vm = vm,
            conversation = conversation,
            message = message,
            onDismiss = { actionMessage = null },
            onEdit = {
                editingMessage = message
                editText = message.content
                actionMessage = null
            },
        )
    }

    editingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("修改这条消息") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 12,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.editMessage(conversation, message, editText)
                    editingMessage = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("取消") }
            },
            containerColor = MoyuSurface,
        )
    }
}

@Composable
private fun AgentMessageRow(
    agent: AgentProfile,
    message: ChatMessage,
    onMore: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    if (message.role == Role.USER) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Surface(
                color = MoyuBlue,
                shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
            ) {
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!message.streaming) {
                    IconButton(onClick = onMore, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "消息操作",
                            tint = MoyuTextDim,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AgentAvatar(agent, 34.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(agent.name, color = MoyuTextDim, fontSize = 10.sp)
                if (message.reasoning.isNotBlank()) {
                    AgentReasoning(message)
                }
                if (message.content.isNotBlank()) {
                    MarkdownText(message.content)
                }
                if (message.streaming && message.content.isBlank() && message.reasoning.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MoyuBlue)
                        Spacer(Modifier.width(6.dp))
                        Text("${agent.name} 正在想…", color = MoyuTextDim, fontSize = 12.sp)
                    }
                }
                if (message.trace.isNotBlank() && message.trace != "正在准备上下文…") {
                    Text(message.trace, color = MoyuTextDim.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp)
                }
                message.error?.let {
                    Text(it, color = MoyuError, fontSize = 11.sp, lineHeight = 16.sp)
                }
                if (!message.streaming && (message.content.isNotBlank() || message.error != null)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "复制", tint = MoyuTextDim, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onMore, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "消息操作", tint = MoyuTextDim, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentReasoning(message: ChatMessage) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MoyuSurface.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (message.streaming && message.content.isBlank()) "正在思考…" else "思考过程",
                color = MoyuTextDim,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MoyuTextDim,
                modifier = Modifier.size(15.dp),
            )
        }
        AnimatedVisibility(expanded || (message.streaming && message.content.isBlank())) {
            Text(message.reasoning, color = MoyuTextDim, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentMessageActions(
    vm: ChatViewModel,
    conversation: Conversation,
    message: ChatMessage,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MoyuSurface,
        contentColor = MoyuText,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text("消息操作", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            AgentActionRow(Icons.Outlined.ContentCopy, "复制") {
                clipboard.setText(AnnotatedString(message.content))
                onDismiss()
            }
            AgentActionRow(Icons.Outlined.Edit, "修改") { onEdit() }
            AgentActionRow(Icons.Outlined.Refresh, "重新生成") {
                vm.regenerateFrom(conversation, message)
                onDismiss()
            }
            AgentActionRow(Icons.Outlined.Delete, "撤回（不计入记忆池）") {
                vm.withdrawMessage(conversation, message)
                onDismiss()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AgentActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MoyuTextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp)
    }
}

@Composable
fun AgentMemoryScreen(vm: ChatViewModel) {
    val agent = vm.activeAgent
    if (agent == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有选中的智能体", color = MoyuTextDim)
        }
        return
    }
    var newMemory by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${agent.name} 的记忆池", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "没撤回的消息会自动记录在这里；撤回时会同步删除。下次生成会把这些内容带进上下文。",
            color = MoyuTextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newMemory,
                onValueChange = { newMemory = it },
                placeholder = { Text("手动加一条记忆…", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = {
                vm.addAgentMemory(newMemory)
                newMemory = ""
            }) { Text("添加") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("共 ${agent.memories.size} 条", color = MoyuTextDim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { confirmClear = true }) { Text("清空", color = MoyuError, fontSize = 12.sp) }
        }
        HorizontalDivider(color = MoyuBorder)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(agent.memories, key = { it.id }) { memory ->
                AgentMemoryRow(memory) { vm.deleteAgentMemory(memory) }
            }
            if (agent.memories.isEmpty()) {
                item {
                    Text("记忆池还是空的，先聊几句吧。", color = MoyuTextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空记忆池？") },
            text = { Text("只清空记忆，不删除聊天记录。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAgentMemories()
                    confirmClear = false
                }) { Text("清空", color = MoyuError) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
            containerColor = MoyuSurface,
        )
    }
}

@Composable
private fun AgentMemoryRow(memory: AgentMemory, onDelete: () -> Unit) {
    Surface(color = MoyuSurface, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (memory.role == Role.USER) "用户" else "角色",
                    color = MoyuBlue,
                    fontSize = 9.sp,
                )
                Text(memory.content, fontSize = 12.sp, lineHeight = 17.sp)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除记忆", tint = MoyuTextDim, modifier = Modifier.size(15.dp))
            }
        }
    }
}
