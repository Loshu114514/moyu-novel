package com.moyu.novel

import org.json.JSONArray
import org.json.JSONObject

object ChatImport {

    private val markerRegex = Regex(
        "^\\s*(?:#{1,4}\\s*)?(?:\\*\\*)?\\s*(用户|user|human|我|问|助手|assistant|ai|bot|chatgpt|答)\\s*(?:\\*\\*)?\\s*[:：]\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )

    private val headingRegex = Regex(
        "^\\s*#{1,4}\\s*(用户|user|human|助手|assistant|ai|bot|chatgpt)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String, fileName: String): List<ImportedChat> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val json = runCatching { parseJson(trimmed) }.getOrNull()
            if (!json.isNullOrEmpty()) return json
        }
        return parsePlain(text, fileName)
    }

    private fun parseJson(text: String): List<ImportedChat> {
        val result = mutableListOf<ImportedChat>()
        if (text.startsWith("[")) {
            val array = JSONArray(text)
            val loose = mutableListOf<Pair<Role, String>>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                when {
                    item.has("mapping") -> chatGptConversation(item)?.let { result += it }
                    item.has("messages") -> genericConversation(item)?.let { result += it }
                    item.has("role") || item.has("from") -> {
                        val parsed = parseMessageObject(item)
                        if (parsed != null) loose += parsed
                    }
                }
            }
            if (result.isEmpty() && loose.isNotEmpty()) {
                result += ImportedChat("导入的对话", loose)
            }
            return result
        }
        val root = JSONObject(text)
        when {
            root.has("conversations") -> {
                val array = root.optJSONArray("conversations") ?: JSONArray()
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { item ->
                        (if (item.has("mapping")) chatGptConversation(item) else genericConversation(item))
                            ?.let { result += it }
                    }
                }
            }
            root.has("mapping") -> chatGptConversation(root)?.let { result += it }
            root.has("messages") -> genericConversation(root)?.let { result += it }
            root.has("data") -> {
                val array = root.optJSONArray("data") ?: JSONArray()
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { item ->
                        (if (item.has("mapping")) chatGptConversation(item) else genericConversation(item))
                            ?.let { result += it }
                    }
                }
            }
            else -> {
                val parsed = parseMessageObject(root)
                if (parsed != null) result += ImportedChat("导入的对话", listOf(parsed))
            }
        }
        return result
    }

    private fun genericConversation(json: JSONObject): ImportedChat? {
        val messages = mutableListOf<Pair<Role, String>>()
        json.optJSONArray("messages")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                parseMessageObject(item)?.let { messages += it }
            }
        }
        if (messages.isEmpty()) return null
        val title = json.optString("title")
            .ifBlank { json.optString("name") }
            .ifBlank { json.optString("conversation_title") }
            .ifBlank { makeTitle(messages.firstOrNull { it.first == Role.USER }?.second ?: "") }
        return ImportedChat(title, messages)
    }

    private fun chatGptConversation(json: JSONObject): ImportedChat? {
        val mapping = json.optJSONObject("mapping") ?: return null
        val collected = mutableListOf<Triple<Double, Role, String>>()
        for (key in mapping.keys()) {
            val node = mapping.optJSONObject(key) ?: continue
            val message = node.optJSONObject("message") ?: continue
            val roleRaw = message.optJSONObject("author")?.optString("role")
                ?: message.optString("role")
            val role = mapRole(roleRaw) ?: continue
            val contentJson = message.opt("content")
            val text = contentText(contentJson).trim()
            if (text.isBlank()) continue
            val time = message.optDouble("create_time", 0.0)
            collected += Triple(time, role, text)
        }
        if (collected.isEmpty()) return null
        val messages = collected.sortedBy { it.first }.map { it.second to it.third }
        val title = json.optString("title").ifBlank { makeTitle(messages.firstOrNull { it.first == Role.USER }?.second ?: "") }
        return ImportedChat(title, messages)
    }

    private fun parseMessageObject(json: JSONObject): Pair<Role, String>? {
        val roleRaw = json.optString("role")
            .ifBlank { json.optString("from") }
            .ifBlank { json.optJSONObject("author")?.optString("role").orEmpty() }
        val role = mapRole(roleRaw) ?: return null
        val raw = when {
            json.has("content") -> json.opt("content")
            json.has("text") -> json.opt("text")
            json.has("message") -> json.opt("message")
            else -> null
        }
        val text = contentText(raw).trim()
        if (text.isBlank()) return null
        return role to text
    }

    private fun contentText(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> value.toTextParts()
        is JSONObject -> {
            val text = value.optString("text")
            if (text.isNotBlank()) {
                text
            } else {
                val parts = value.optJSONArray("parts")
                if (parts != null) parts.toTextParts() else value.optString("content")
            }
        }
        else -> ""
    }

    private fun JSONArray.toTextParts(): String {
        val builder = StringBuilder()
        for (i in 0 until length()) {
            when (val part = opt(i)) {
                is String -> builder.append(part)
                is JSONObject -> {
                    val text = part.optString("text")
                    if (text.isNotBlank()) builder.append(text)
                }
                else -> Unit
            }
        }
        return builder.toString()
    }

    private fun mapRole(raw: String?): Role? = when (raw?.trim()?.lowercase()) {
        "user", "human", "me", "我", "用户" -> Role.USER
        "assistant", "ai", "bot", "gpt", "chatgpt", "助手" -> Role.ASSISTANT
        else -> null
    }

    private fun parsePlain(text: String, fileName: String): List<ImportedChat> {
        val messages = mutableListOf<Pair<Role, String>>()
        var currentRole: Role? = null
        val buffer = StringBuilder()
        var title = ""

        fun flush() {
            val role = currentRole ?: return
            val content = buffer.toString().trim()
            if (content.isNotEmpty()) messages += role to content
            buffer.clear()
        }

        text.lineSequence().forEach { line ->
            val marker = markerRegex.find(line)
            val heading = if (marker == null) headingRegex.find(line) else null
            if (marker != null || heading != null) {
                flush()
                val word = (marker ?: heading)!!.groupValues[1]
                currentRole = mapRole(word)
                if (marker != null) {
                    val inline = marker.groupValues.getOrNull(2).orEmpty()
                    if (inline.isNotBlank()) buffer.append(inline).append('\n')
                }
                if (title.isBlank() && currentRole == Role.USER) {
                    val inline = marker?.groupValues?.getOrNull(2).orEmpty()
                    if (inline.isNotBlank()) title = makeTitle(inline)
                }
            } else if (currentRole != null) {
                buffer.append(line).append('\n')
            } else if (line.isNotBlank() && title.isBlank() && line.length <= 40) {
                title = line.trim()
            }
        }
        flush()

        if (messages.isEmpty()) {
            val fallback = text.trim()
            if (fallback.isBlank()) return emptyList()
            return listOf(ImportedChat(fileName.substringBeforeLast('.').ifBlank { "导入的对话" }, listOf(Role.USER to fallback)))
        }
        return listOf(
            ImportedChat(
                title = title.ifBlank { makeTitle(messages.firstOrNull { it.first == Role.USER }?.second ?: "") },
                messages = messages,
            ),
        )
    }
}
