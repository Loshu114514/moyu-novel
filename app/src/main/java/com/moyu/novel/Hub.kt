package com.moyu.novel

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RecommendedModel(val repo: String, val note: String)

class ModelHub(private val context: Context, private val store: Store) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val recommended = listOf(
        RecommendedModel("unsloth/Qwen3.5-4B-GGUF", "手机首选 · Qwen3.5 4B · Q4 约 2.5GB"),
        RecommendedModel("Qwen/Qwen3-4B-GGUF", "官方 Qwen3 4B · 稳定通用"),
        RecommendedModel("unsloth/Qwen3-8B-GGUF", "旗舰机可选 · Qwen3 8B · Q4 约 5GB"),
        RecommendedModel("Qwen/Qwen3-8B-GGUF", "官方 Qwen3 8B · 长文更强"),
    )

    suspend fun search(query: String): List<HubModel> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = if (query.isBlank()) {
            "https://hf-mirror.com/api/models?filter=gguf&sort=downloads&direction=-1&limit=30"
        } else {
            "https://hf-mirror.com/api/models?search=$encoded&filter=gguf&sort=downloads&direction=-1&limit=30"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MoyuNovel/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("模型库请求失败：HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val array = runCatching { org.json.JSONArray(body) }.getOrElse {
                throw IOException("模型库返回格式异常")
            }
            val results = mutableListOf<HubModel>()
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val id = json.optString("id")
                if (id.isBlank() || !id.lowercase().contains("gguf")) continue
                val tags = mutableListOf<String>()
                json.optJSONArray("tags")?.let { tagArray ->
                    for (j in 0 until tagArray.length()) tags += tagArray.optString(j)
                }
                results += HubModel(
                    id = id,
                    downloads = json.optLong("downloads"),
                    likes = json.optLong("likes"),
                    tags = tags,
                )
            }
            results
        }
    }

    suspend fun files(repo: String): List<HubFile> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://hf-mirror.com/api/models/$repo?blobs=true")
            .header("User-Agent", "MoyuNovel/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("读取模型文件列表失败：HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw IOException("模型文件列表格式异常")
            }
            val siblings = json.optJSONArray("siblings") ?: return@withContext emptyList()
            val files = mutableListOf<HubFile>()
            for (i in 0 until siblings.length()) {
                val item = siblings.optJSONObject(i) ?: continue
                val name = item.optString("rfilename")
                if (!name.endsWith(".gguf", ignoreCase = true)) continue
                if (name.contains("mmproj", ignoreCase = true)) continue
                val size = item.optLong("size")
                files += HubFile(name = name, size = size)
            }
            files.sortedBy { it.name }
        }
    }

    fun download(repo: String, file: HubFile): DownloadRecord {
        val localName = (repo.replace('/', '_') + "__" + file.name.substringAfterLast('/'))
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val finalFile = File(store.modelsDir, localName)
        val uri = Uri.parse("https://hf-mirror.com/$repo/resolve/main/${encodePath(file.name)}?download=true")
        val request = DownloadManager.Request(uri)
            .setTitle(file.name.substringAfterLast('/'))
            .setDescription("墨语 · 本地模型")
            .setMimeType("application/octet-stream")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "models/$localName.part")
        val id = downloadManager.enqueue(request)
        return DownloadRecord(
            downloadId = id,
            repo = repo,
            file = file.name,
            path = finalFile.absolutePath,
            size = file.size,
        )
    }

    fun progress(id: Long): DownloadProgress? {
        val query = DownloadManager.Query().setFilterById(id)
        return runCatching {
            downloadManager.query(query).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return null
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else 0
                DownloadProgress(status = status, bytes = bytes, total = total, reason = reason)
            }
        }.getOrNull()
    }

    fun cancel(id: Long) {
        runCatching { downloadManager.remove(id) }
    }

    fun downloadedPath(record: DownloadRecord): File = File(record.path)

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
}
