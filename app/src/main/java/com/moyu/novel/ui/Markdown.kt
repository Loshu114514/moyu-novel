package com.moyu.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Bullet(val text: String, val ordered: Boolean, val number: Int) : MdBlock
    data object Divider : MdBlock
}

private val bulletRegex = Regex("^[-*+]\\s+")
private val orderedRegex = Regex("^\\d+[.)]\\s+")
private val inlineRegex = Regex(
    "(\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__|`[^`\\n]+`|~~[^~\\n]+~~|\\*[^*\\n]+\\*|\\[[^\\]\\n]+\\]\\([^)\\n]+\\))",
)

private fun parseMarkdown(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.replace("\r\n", "\n").split('\n')
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val value = paragraph.toString().trim()
        if (value.isNotEmpty()) blocks += MdBlock.Paragraph(value)
        paragraph.clear()
    }

    var index = 0
    while (index < lines.size) {
        val rawLine = lines[index]
        val line = rawLine.trim()
        when {
            line.startsWith("```") -> {
                flushParagraph()
                val language = line.removePrefix("```").trim()
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code.append(lines[index]).append('\n')
                    index++
                }
                blocks += MdBlock.Code(language, code.toString().trimEnd('\n'))
            }
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                if (level in 1..4 && line.length > level && line[level] == ' ') {
                    flushParagraph()
                    blocks += MdBlock.Heading(level, line.substring(level).trim())
                } else {
                    paragraph.append(rawLine).append('\n')
                }
            }
            line.startsWith(">") -> {
                flushParagraph()
                blocks += MdBlock.Quote(line.removePrefix(">").trim())
            }
            bulletRegex.containsMatchIn(line) -> {
                flushParagraph()
                blocks += MdBlock.Bullet(line.replaceFirst(bulletRegex, ""), ordered = false, number = 0)
            }
            orderedRegex.containsMatchIn(line) -> {
                flushParagraph()
                val number = line.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                blocks += MdBlock.Bullet(line.replaceFirst(orderedRegex, ""), ordered = true, number = number)
            }
            line == "---" || line == "***" || line == "___" -> {
                flushParagraph()
                blocks += MdBlock.Divider
            }
            line.isEmpty() -> flushParagraph()
            else -> paragraph.append(rawLine).append('\n')
        }
        index++
    }
    flushParagraph()
    return blocks
}

private fun inlineMarkdown(text: String, codeColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    inlineRegex.findAll(text).forEach { match ->
        if (match.range.first > last) append(text.substring(last, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.substring(2, token.length - 2))
            }
            token.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.substring(2, token.length - 2))
            }
            token.startsWith("`") -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeColor),
            ) {
                append(token.substring(1, token.length - 1))
            }
            token.startsWith("~~") -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                append(token.substring(2, token.length - 2))
            }
            token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.substring(1, token.length - 1))
            }
            token.startsWith("[") -> {
                val close = token.indexOf("](")
                val label = if (close > 0) token.substring(1, close) else token
                withStyle(SpanStyle(color = MoyuBlue)) { append(label) }
            }
            else -> append(token)
        }
        last = match.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MoyuText,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Paragraph -> Text(
                        text = inlineMarkdown(block.text, MoyuSurfaceHigh),
                        color = color,
                        fontSize = 16.sp,
                        lineHeight = 27.sp,
                    )
                    is MdBlock.Heading -> Text(
                        text = inlineMarkdown(block.text, MoyuSurfaceHigh),
                        color = color,
                        fontSize = when (block.level) {
                            1 -> 22.sp
                            2 -> 20.sp
                            3 -> 18.sp
                            else -> 16.sp
                        },
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 28.sp,
                    )
                    is MdBlock.Code -> CodeBlock(block.language, block.code)
                    is MdBlock.Quote -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MoyuBlue),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = inlineMarkdown(block.text, MoyuSurfaceHigh),
                            color = MoyuTextDim,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                        )
                    }
                    is MdBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (block.ordered) "${block.number}." else "•",
                            color = MoyuBlue,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.width(22.dp),
                        )
                        Text(
                            text = inlineMarkdown(block.text, MoyuSurfaceHigh),
                            color = color,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                        )
                    }
                    MdBlock.Divider -> HorizontalDivider(color = MoyuBorder, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MoyuSurfaceElevated),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MoyuSurfaceHigh)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.ifBlank { "code" },
                color = MoyuTextDim,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    tint = MoyuTextDim,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Text(
            text = code,
            modifier = Modifier.padding(12.dp),
            color = MoyuText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }
}
