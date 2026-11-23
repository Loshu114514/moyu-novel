package com.moyu.novel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyu.novel.ChatMode
import com.moyu.novel.ChatViewModel
import com.moyu.novel.ContextMode
import com.moyu.novel.DeviceInfo
import com.moyu.novel.LocalModel
import com.moyu.novel.Role
import com.moyu.novel.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(vm: ChatViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.showHistorySheet = false },
        sheetState = sheetState,
        containerColor = MoyuSurface,
        contentColor = MoyuText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text("历史对话", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "点任意一条继续聊；消息会一直保存在本机。",
                color = MoyuTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            vm.conversations.forEach { conversation ->
                val last = conversation.messages.lastOrNull { it.content.isNotBlank() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.selectChat(conversation.id)
                            vm.showHistorySheet = false
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            conversation.title,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${conversation.mode.label} · ${conversation.messages.count { it.role == Role.USER }} 条 · " +
                                SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(conversation.updatedAt)),
                            color = MoyuTextDim,
                            fontSize = 10.sp,
                        )
                        if (last != null) {
                            Text(
                                last.content.take(80),
                                color = MoyuTextDim.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { vm.deleteChat(conversation.id) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除对话",
                            tint = MoyuTextDim,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                HorizontalDivider(color = MoyuBorder)
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySheet(vm: ChatViewModel) {
    val conversation = vm.activeConversation ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val important = conversation.messages.filter { it.important }

    ModalBottomSheet(
        onDismissRequest = { vm.showMemorySheet = false },
        sheetState = sheetState,
        containerColor = MoyuSurface,
        contentColor = MoyuText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text("上下文记忆", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "生成前会先回看这里的内容。重要对话会自动识别，也可以手动收藏，随时删除。",
                color = MoyuTextDim,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )

            Text("每次生成回看：", color = MoyuTextDim, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                ContextMode.entries.forEach { mode ->
                    FilterChip(
                        selected = conversation.contextMode == mode,
                        onClick = { vm.setContextMode(mode) },
                        label = { Text(mode.label, fontSize = 12.sp) },
                    )
                }
            }
            Text(
                if (conversation.contextMode == ContextMode.ALL) {
                    "全部对话：每次把整段聊天都发给模型，适合短对话；本地模型超出上下文会自动截断。"
                } else {
                    "重要对话：只回看收藏的重要内容和最近 4 条消息，适合长篇创作，省上下文。"
                },
                color = MoyuTextDim.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动识别重要对话", fontSize = 14.sp)
                    Text("含“设定/人物/记住”等关键词或较长内容会自动收藏", color = MoyuTextDim, fontSize = 11.sp)
                }
                Switch(
                    checked = vm.settings.autoMarkImportant,
                    onCheckedChange = { value ->
                        vm.updateSettings { it.copy(autoMarkImportant = value) }
                    },
                )
            }

            HorizontalDivider(color = MoyuBorder, modifier = Modifier.padding(vertical = 10.dp))
            Text("已收藏（${important.size}）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (important.isEmpty()) {
                Text(
                    "还没有重要对话。在消息右下角点书签图标即可收藏，或在输入框里说“记住：……”。",
                    color = MoyuTextDim,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            important.forEach { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (message.role == Role.USER) "用户" else "助手") +
                                if (message.importantAuto) " · 自动" else " · 手动",
                            color = MoyuBlue,
                            fontSize = 10.sp,
                        )
                        Text(
                            message.content.take(180),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { vm.toggleImportant(message) }) {
                        Icon(
                            Icons.Outlined.Bookmark,
                            contentDescription = "取消收藏",
                            tint = MoyuBlue,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    IconButton(onClick = { vm.deleteMemoryMessage(message) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除这条记忆",
                            tint = MoyuTextDim,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                HorizontalDivider(color = MoyuBorder)
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelManagerSheet(vm: ChatViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDelete by remember { mutableStateOf<LocalModel?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importLocalModel(uri)
    }

    ModalBottomSheet(
        onDismissRequest = { vm.showLocalModelManager = false },
        sheetState = sheetState,
        containerColor = MoyuSurface,
        contentColor = MoyuText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text("本地模型管理", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                DeviceInfo.localHint(),
                color = if (DeviceInfo.isSnapdragon8Gen2OrNewer) MoyuGreen else MoyuTextDim,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "占用 ${formatBytes(vm.localModels.sumOf { it.size })} · ${vm.localModels.size} 个文件",
                color = MoyuTextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = MoyuBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入 GGUF", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        vm.showLocalModelManager = false
                        vm.goTo(com.moyu.novel.Screen.MODELS)
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("去模型库", fontSize = 12.sp)
                }
            }
            Text(
                "导入的文件会复制到应用私有目录，卸载 App 时一起删除；不需要访问整个手机存储。",
                color = MoyuTextDim.copy(alpha = 0.75f),
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )

            HorizontalDivider(color = MoyuBorder, modifier = Modifier.padding(vertical = 10.dp))
            if (vm.localModels.isEmpty()) {
                Text(
                    "还没有本地模型。可以从模型库下载，或点“导入 GGUF”选手机里的 .gguf 文件。",
                    color = MoyuTextDim,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            vm.localModels.forEach { model ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = MoyuTextDim,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(model.name, fontSize = 12.sp, lineHeight = 17.sp)
                        Text(formatBytes(model.size), color = MoyuTextDim, fontSize = 10.sp)
                    }
                    IconButton(onClick = { pendingDelete = model }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除模型",
                            tint = MoyuTextDim,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                HorizontalDivider(color = MoyuBorder)
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除模型？") },
            text = { Text("${model.name}\n\n只删除手机里的模型文件，不影响对话记录。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLocalModel(model)
                    pendingDelete = null
                }) {
                    Text("删除", color = MoyuError)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
            containerColor = MoyuSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(vm: ChatViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val descriptions = mapOf(
        ChatMode.CHAT to "日常问答、写作润色、翻译、生活助手",
        ChatMode.NOVEL to "网文开篇、续写、人物设定、章节大纲",
        ChatMode.AGENT to "本地计算、时间、记忆、联网搜索，全程在手机上",
    )
    ModalBottomSheet(
        onDismissRequest = { vm.showModeSheet = false },
        sheetState = sheetState,
        containerColor = MoyuSurface,
        contentColor = MoyuText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text("选择模式", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "模式只影响提示词和工具，随时可以切换。",
                color = MoyuTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            ChatMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.setMode(mode) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = when (mode) {
                            ChatMode.CHAT -> Icons.Outlined.History
                            ChatMode.NOVEL -> Icons.Outlined.Bookmark
                            ChatMode.AGENT -> Icons.Outlined.SmartToy
                        },
                        contentDescription = null,
                        tint = MoyuBlue,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(descriptions[mode].orEmpty(), color = MoyuTextDim, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = MoyuBorder)
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}
