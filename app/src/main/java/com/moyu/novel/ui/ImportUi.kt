package com.moyu.novel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyu.novel.ChatViewModel
import com.moyu.novel.Role
import com.moyu.novel.Screen

@Composable
fun ImportScreen(vm: ChatViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadImport(uri)
    }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(color = MoyuSurface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("把旧对话搬进来", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "DeepSeek App 没有官方导出按钮。你可以在聊天里长按全选复制，再粘到这里；" +
                            "也支持 ChatGPT / 其他客户端导出的 JSON、TXT、Markdown，以及 PDF、Word 文档。",
                        color = MoyuTextDim,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MoyuBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件")
                }
                OutlinedButton(
                    onClick = { pasteOpen = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("粘贴文本")
                }
            }
        }

        if (vm.importLoading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MoyuBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("正在解析…", color = MoyuTextDim, fontSize = 12.sp)
                }
            }
        }

        vm.importError?.let { error ->
            item {
                Surface(color = MoyuError.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
                    Text(error, color = MoyuError, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (vm.importPreview.isNotEmpty()) {
            item {
                Text(
                    "识别到 ${vm.importPreview.size} 个对话",
                    color = MoyuTextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(vm.importPreview) { chat ->
                Surface(color = MoyuSurface, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = chat.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        Text(
                            text = "${chat.messages.size} 条消息",
                            color = MoyuTextDim,
                            fontSize = 11.sp,
                        )
                        chat.messages.firstOrNull { it.first == Role.USER }?.second?.let { preview ->
                            Text(
                                preview.take(90),
                                color = MoyuTextDim,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { vm.confirmImport() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MoyuBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入全部对话")
                }
            }
            item {
                TextButton(onClick = { vm.clearImport() }) { Text("清除预览") }
            }
        }
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text("粘贴聊天记录") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 12,
                    placeholder = { Text("用户：……\n助手：……") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pasteOpen = false
                    vm.loadImportText(pasteText)
                }) {
                    Text("解析")
                }
            },
            dismissButton = {
                TextButton(onClick = { pasteOpen = false }) { Text("取消") }
            },
            containerColor = MoyuSurface,
        )
    }
}
