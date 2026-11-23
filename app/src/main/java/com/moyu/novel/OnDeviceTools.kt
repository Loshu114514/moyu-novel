package com.moyu.novel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ToolResult(
    val context: String,
    val trace: String,
)

class OnDeviceTools(
    private val context: Context,
    private val webSearch: WebSearch,
) {

    suspend fun prepare(
        text: String,
        memories: List<String>,
        webEnabled: Boolean,
    ): ToolResult? {
        val parts = mutableListOf<String>()
        val traces = mutableListOf<String>()

        calculate(text)?.let {
            parts += "【工具结果 · 计算器】\n$it"
            traces += "计算器：$it"
        }

        if (Regex("现在几点|什么时间|今天几号|日期|星期几|今天是").containsMatchIn(text)) {
            val now = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINA).format(Date())
            parts += "【工具结果 · 当前时间】\n$now"
            traces += "时间：$now"
        }

        if (text.contains("电量") || text.contains("电池")) {
            runCatching { batteryStatus() }.getOrNull()?.let {
                parts += "【工具结果 · 设备电量】\n$it"
                traces += "电量：$it"
            }
        }

        if (text.trimStart().startsWith("记住")) {
            val fact = text.trimStart().removePrefix("记住").trimStart(':', '：', ' ')
            if (fact.isNotBlank()) {
                parts += "【工具结果 · 记忆】\n已把这条记为重要记忆：${fact.take(300)}"
                traces += "记忆：已记录"
            }
        } else if (Regex("回忆|之前说|记得什么|记忆里有|重要记忆").containsMatchIn(text) && memories.isNotEmpty()) {
            val block = memories.takeLast(8).joinToString("\n") { "- ${it.take(200)}" }
            parts += "【工具结果 · 记忆】\n$block"
            traces += "记忆：读取 ${memories.size} 条"
        }

        val needSearch = webEnabled && (
            Regex("搜索|搜一下|查一下|查查|最新|新闻|联网|天气|价格|是谁|百科").containsMatchIn(text) ||
                text.contains("搜 ")
            )
        if (needSearch) {
            val query = buildQuery(text)
            val results = runCatching { webSearch.search(query) }.getOrNull().orEmpty()
            if (results.isEmpty()) {
                parts += "【工具结果 · 联网搜索】\n没有检索到结果。"
                traces += "联网搜索：无结果"
            } else {
                val block = results.joinToString("\n\n") { result ->
                    "${result.title}\n${result.url}\n${result.snippet}"
                }
                parts += "【工具结果 · 联网搜索】\n$block"
                traces += "联网搜索：$query（${results.size} 条）\n" +
                    results.joinToString("\n") { "· ${it.title} — ${it.url}" }
            }
        }

        if (parts.isEmpty()) return null
        return ToolResult(parts.joinToString("\n\n"), traces.joinToString("\n"))
    }

    fun calculate(text: String): String? {
        val candidate = Regex("(?:计算|算一下|算算|等于)?\\s*([-+*/%^().\\d\\s×÷]{3,})")
            .find(text)?.groupValues?.get(1)?.trim().orEmpty()
        if (candidate.length < 3) return null
        if (!Regex("[0-9]").containsMatchIn(candidate)) return null
        if (!Regex("[+\\-*/%^×÷]").containsMatchIn(candidate)) return null
        val normalized = candidate
            .replace("×", "*")
            .replace("÷", "/")
            .replace("（", "(")
            .replace("）", ")")
            .replace(" ", "")
        val value = Calculator.eval(normalized) ?: return null
        val formatted = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
        return "$candidate = $formatted"
    }

    private fun buildQuery(text: String): String =
        text.replace(Regex("^(帮我|请|麻烦)?\\s*(搜索|搜一下|查一下|查查|联网搜)\\s*"), "")
            .replace(Regex("[。！？!?].*$"), "")
            .trim()
            .take(80)
            .ifBlank { text.take(80) }

    private fun batteryStatus(): String? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val percent = level * 100 / scale
        val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            else -> "未知"
        }
        return "$percent% · $status"
    }
}

object Calculator {

    fun eval(expression: String): Double? {
        if (expression.length > 200) return null
        return runCatching { Parser(expression).parse() }.getOrNull()?.takeIf { it.isFinite() }
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): Double {
            val value = parseExpression()
            skipSpaces()
            if (pos != text.length) throw IllegalArgumentException("unexpected")
            return value
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                when {
                    consume('+') -> value += parseTerm()
                    consume('-') -> value -= parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parsePower()
            while (true) {
                skipSpaces()
                when {
                    consume('*') -> value *= parsePower()
                    consume('/') -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) throw IllegalArgumentException("div0")
                        value /= divisor
                    }
                    consume('%') -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) throw IllegalArgumentException("div0")
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            skipSpaces()
            if (consume('^')) {
                return Math.pow(base, parsePower())
            }
            return base
        }

        private fun parseUnary(): Double {
            skipSpaces()
            return when {
                consume('+') -> parseUnary()
                consume('-') -> -parseUnary()
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Double {
            skipSpaces()
            if (consume('(')) {
                val value = parseExpression()
                if (!consume(')')) throw IllegalArgumentException("paren")
                return value
            }
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) {
                pos++
            }
            if (start == pos) throw IllegalArgumentException("number")
            return text.substring(start, pos).toDouble()
        }

        private fun consume(char: Char): Boolean {
            skipSpaces()
            if (pos < text.length && text[pos] == char) {
                pos++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
