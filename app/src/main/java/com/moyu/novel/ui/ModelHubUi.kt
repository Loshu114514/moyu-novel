package com.moyu.novel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.moyu.novel.ChatViewModel
import com.moyu.novel.HubFile
import com.moyu.novel.LocalModel
import com.moyu.novel.ModelSourceKind
import com.moyu.novel.Screen
import com.moyu.novel.formatBytes
import com.moyu.novel.modelBillions
import com.moyu.novel.sourceSizeLabel

private val sizeFilters = listOf("全部", "≤3B", "4-8B", "14-32B", "≥70B")

private fun matchesSize(id: String, filter: String): Boolean {
    if (filter == "全部") return true
    val billions = modelBillions(id) ?: return false
    return when (filter) {
        "≤3B" -> billions <= 3.0
        "4-8B" -> billions in 4.0..8.0
        "14-32B" -> billions in 14.0..32.0
        "≥70B" -> billions >= 70.0
        else -> true
    }
}

@Composable
fun ModelHubScreen(vm: ChatViewModel) {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var manualRepo by remember { mutableStateOf<String?>(null) }
    var repoInput by remember { mutableStateOf("") }
    var sizeFilter by remember { mutableStateOf("全部") }

    LaunchedEffect(vm.settings.activeSourceId) {
        query = ""
        searched = false
        expanded = null
        manualRepo = null
        sizeFilter = "全部"
        vm.searchHub("")
    }
    LaunchedEffect(expanded) {
        expanded?.let { vm.loadHubFiles(it) }
    }

    val source = vm.activeSource
    val hubList = vm.hubModels.filter { matchesSize(it.id, sizeFilter) }
    val recList = vm.recommendedModels.filter { matchesSize(it.repo, sizeFilter) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.settings.modelSources, key = { it.id }) { modelSource ->
                    FilterChip(
                        selected = modelSource.id == vm.settings.activeSourceId,
                        onClick = { vm.selectModelSource(modelSource.id) },
                        label = { Text(modelSource.name, fontSize = 12.sp) },
                    )
                }
                item {
                    TextButton(onClick = { vm.goTo(Screen.SETTINGS) }) {
                        Text("管理源", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Surface(color = MoyuSurface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("本地模型库", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "当前源：${source.name} · ${source.baseUrl}",
                        color = MoyuTextDim,
                        fontSize = 11.sp,
                    )
                    Text(
                        "从 0.5B 到 235B 都能搜。4B Q4 约 2.5GB，8B 约 5GB；" +
                            "70B 以上只建议电脑/大内存设备。下载走系统 DownloadManager，后台也会继续。",
                        color = MoyuTextDim,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    if (source.kind == ModelSourceKind.MODELSCOPE) {
                        Text(
                            "魔搭源使用内置目录（0.5B–235B），也可以直接粘贴任意仓库 ID 打开。",
                            color = MoyuText.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索 GGUF，例如 Qwen3 32B", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        searched = true
                        vm.searchHub(query)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MoyuBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("搜索")
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sizeFilters, key = { it }) { filter ->
                    FilterChip(
                        selected = sizeFilter == filter,
                        onClick = { sizeFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) },
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    placeholder = { Text("直接打开仓库，如 Qwen/Qwen3-32B-GGUF", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val repo = repoInput.trim()
                    if (repo.isNotBlank()) {
                        manualRepo = repo
                        expanded = repo
                        searched = true
                    }
                }) {
                    Text("打开")
                }
            }
        }

        if (vm.downloads.isNotEmpty()) {
            item { SectionTitle("下载任务") }
            items(vm.downloads, key = { "dl-" + it.downloadId }) { record ->
                val progressState = vm.downloadProgress[record.downloadId]
                Surface(color = MoyuSurface, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(record.file.substringAfterLast('/'), fontSize = 12.sp, lineHeight = 17.sp)
                            Text(record.repo, color = MoyuTextDim, fontSize = 10.sp, lineHeight = 15.sp)
                            Spacer(Modifier.size(4.dp))
                            if (progressState != null) {
                                LinearProgressIndicator(
                                    progress = { progressState.fraction },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MoyuBlue,
                                    trackColor = MoyuSurfaceHigh,
                                )
                                Text(
                                    "${formatBytes(progressState.bytes)} / ${formatBytes(progressState.total)}",
                                    color = MoyuTextDim,
                                    fontSize = 10.sp,
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MoyuBlue,
                                    trackColor = MoyuSurfaceHigh,
                                )
                            }
                        }
                        IconButton(onClick = { vm.cancelDownload(record.downloadId) }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "取消下载",
                                tint = MoyuTextDim,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }

        if (vm.localModels.isNotEmpty()) {
            item { SectionTitle("已下载（${vm.localModels.size}）") }
            items(vm.localModels, key = { "lm-" + it.path }) { local ->
                LocalModelRow(
                    model = local,
                    selected = vm.settings.useLocal && vm.settings.localModelPath == local.path,
                    onUse = { vm.useLocalModel(local.path) },
                    onDelete = { vm.deleteLocalModel(local) },
                )
            }
        }

        if (manualRepo != null &&
            hubList.none { it.id == manualRepo } &&
            recList.none { it.repo == manualRepo }
        ) {
            item { SectionTitle("手动打开") }
            item {
                RepoCard(
                    vm = vm,
                    id = manualRepo!!,
                    note = "按仓库 ID 打开",
                    expanded = expanded == manualRepo,
                    onToggle = {
                        expanded = if (expanded == manualRepo) null else manualRepo
                    },
                )
            }
        }

        item {
            SectionTitle(
                when {
                    searched -> "搜索结果"
                    vm.hubModels.isNotEmpty() -> "热门 GGUF"
                    else -> "推荐模型"
                },
            )
        }

        if (vm.hubLoading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MoyuBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("正在读取模型库…", color = MoyuTextDim, fontSize = 12.sp)
                }
            }
        }
        vm.hubError?.let { error ->
            item {
                Surface(color = MoyuError.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
                    Text(error, color = MoyuError, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (hubList.isNotEmpty()) {
            items(hubList, key = { "hub-" + it.id }) { model ->
                val meta = if (model.note.isNotBlank()) {
                    model.note
                } else {
                    "${model.downloads} 次下载 · ${model.likes} 赞"
                }
                RepoCard(
                    vm = vm,
                    id = model.id,
                    note = meta,
                    expanded = expanded == model.id,
                    onToggle = { expanded = if (expanded == model.id) null else model.id },
                )
            }
        } else if (!vm.hubLoading) {
            if (source.kind == ModelSourceKind.HF && !searched && recList.isNotEmpty()) {
                items(recList, key = { "rec-" + it.repo }) { recommended ->
                    RepoCard(
                        vm = vm,
                        id = recommended.repo,
                        note = recommended.note,
                        expanded = expanded == recommended.repo,
                        onToggle = {
                            expanded = if (expanded == recommended.repo) null else recommended.repo
                        },
                    )
                }
            } else {
                item {
                    Text(
                        "没有符合大小筛选的模型，换个筛选条件或搜索词试试。",
                        color = MoyuTextDim,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item {
            Text(
                "提示：模型名可以完整显示；点仓库展开文件，选 Q4_K_M 最均衡。下载完成后在“已下载”里点“使用”。",
                color = MoyuTextDim.copy(alpha = 0.7f),
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = MoyuTextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun RepoCard(
    vm: ChatViewModel,
    id: String,
    note: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val sizeLabel = sourceSizeLabel(id)
    Surface(color = MoyuSurface, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(id, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = listOf(sizeLabel, note).filter { it.isNotBlank() }.joinToString(" · "),
                        color = MoyuTextDim,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MoyuTextDim,
                )
            }
            if (expanded) {
                Spacer(Modifier.size(8.dp))
                FilesOfRepo(vm, id)
            }
        }
    }
}

@Composable
private fun FilesOfRepo(vm: ChatViewModel, repo: String) {
    val loading = vm.hubLoadingFiles[repo] == true
    val files = vm.hubFiles[repo]
    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MoyuBlue)
            Spacer(Modifier.width(8.dp))
            Text("读取文件列表…", color = MoyuTextDim, fontSize = 11.sp)
        }
        return
    }
    if (files == null || files.isEmpty()) {
        Text("这个仓库没有可用的 GGUF 文件，或需要换个模型源。", color = MoyuTextDim, fontSize = 11.sp)
        return
    }
    files.forEach { file ->
        HubFileRow(repo = repo, file = file, vm = vm)
    }
}

@Composable
private fun HubFileRow(repo: String, file: HubFile, vm: ChatViewModel) {
    val shortName = file.name.substringAfterLast('/')
    val downloaded = vm.localModels.any { it.name.endsWith(shortName) }
    val record = vm.downloads.firstOrNull { it.repo == repo && it.file == file.name }
    val progressState = record?.let { vm.downloadProgress[it.downloadId] }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(shortName, fontSize = 12.sp, lineHeight = 17.sp)
            Text(
                formatBytes(file.size) + if (downloaded) " · 已下载" else "",
                color = if (downloaded) MoyuGreen else MoyuTextDim,
                fontSize = 10.sp,
            )
            if (progressState != null) {
                LinearProgressIndicator(
                    progress = { progressState.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = MoyuBlue,
                    trackColor = MoyuSurfaceHigh,
                )
            }
        }
        when {
            downloaded -> Icon(
                Icons.Outlined.Check,
                contentDescription = "已下载",
                tint = MoyuGreen,
                modifier = Modifier.size(18.dp),
            )
            record != null -> IconButton(onClick = { vm.cancelDownload(record.downloadId) }) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "取消下载",
                    tint = MoyuTextDim,
                    modifier = Modifier.size(17.dp),
                )
            }
            else -> IconButton(onClick = { vm.downloadModel(repo, file) }) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = "下载",
                    tint = MoyuBlue,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalModelRow(
    model: LocalModel,
    selected: Boolean,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MoyuSurface, shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(model.name, fontSize = 12.sp, lineHeight = 17.sp)
                Text(formatBytes(model.size), color = MoyuTextDim, fontSize = 10.sp)
            }
            if (selected) {
                Text("使用中", color = MoyuGreen, fontSize = 11.sp)
            } else {
                TextButton(onClick = onUse) { Text("使用", fontSize = 12.sp) }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除模型",
                    tint = MoyuTextDim,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
