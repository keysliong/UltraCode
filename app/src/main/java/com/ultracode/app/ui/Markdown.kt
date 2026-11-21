package com.ultracode.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface Block {
    data class Text(val lines: List<String>) : Block
    data class Code(val lang: String, val code: String) : Block
}

private fun parse(md: String): List<Block> {
    val out = mutableListOf<Block>()
    val buf = mutableListOf<String>()
    var inCode = false
    var lang = ""
    val code = StringBuilder()
    for (line in md.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (!inCode) {
                if (buf.isNotEmpty()) { out += Block.Text(buf.toList()); buf.clear() }
                inCode = true; lang = line.trim().removePrefix("```").trim(); code.clear()
            } else {
                out += Block.Code(lang, code.toString().trimEnd('\n')); inCode = false
            }
            continue
        }
        if (inCode) code.append(line).append('\n') else buf += line
    }
    if (inCode) out += Block.Code(lang, code.toString().trimEnd('\n'))
    if (buf.isNotEmpty()) out += Block.Text(buf)
    return out
}

private val urlEnd = setOf(' ', '\n', '\t', ')', ']', '>', '<', '"', '\'', '`')

private fun androidx.compose.ui.text.AnnotatedString.Builder.link(text: String, url: String, color: Color) {
    withLink(
        LinkAnnotation.Url(
            url,
            TextLinkStyles(style = SpanStyle(color = color, textDecoration = TextDecoration.Underline)),
        ),
    ) { append(text) }
}

/** Plain text with clickable links (for user messages). */
fun linkify(s: String, color: Color): AnnotatedString = inline(s, Color.Transparent, color, markdown = false)

private fun inline(s: String, codeBg: Color, linkColor: Color, markdown: Boolean = true): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            markdown && s[i] == '[' && s.indexOf("](", i) > i && s.indexOf(')', s.indexOf("](", i)) > 0 &&
                s.substring(s.indexOf("](", i) + 2).let { it.startsWith("http") || it.startsWith("mailto:") } -> {
                val mid = s.indexOf("](", i)
                val end = s.indexOf(')', mid)
                link(s.substring(i + 1, mid), s.substring(mid + 2, end).trim(), linkColor)
                i = end + 1
            }
            s.startsWith("https://", i) || s.startsWith("http://", i) -> {
                var end = i
                while (end < s.length && s[end] !in urlEnd) end++
                while (end > i && s[end - 1] in ".,;:!?") end--
                val url = s.substring(i, end)
                link(url, url, linkColor)
                i = end
            }
            !markdown -> { append(s[i]); i++ }
            s.startsWith("**", i) && s.indexOf("**", i + 2) > 0 -> {
                val end = s.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                i = end + 2
            }
            s[i] == '`' && s.indexOf('`', i + 1) > 0 -> {
                val end = s.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 14.sp)) {
                    append(s.substring(i + 1, end))
                }
                i = end + 1
            }
            s[i] == '*' && i + 1 < s.length && s[i + 1] != ' ' && s.indexOf('*', i + 1) > 0 -> {
                val end = s.indexOf('*', i + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                i = end + 1
            }
            else -> { append(s[i]); i++ }
        }
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parse(text) }
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in blocks) when (b) {
            is Block.Code -> CodeBlock(b.lang, b.code)
            is Block.Text -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (raw in b.lines) {
                    val line = raw.trimEnd()
                    when {
                        line.isBlank() -> Spacer(Modifier.size(4.dp))
                        line.startsWith("#") -> {
                            val level = line.takeWhile { it == '#' }.length
                            Text(
                                inline(line.drop(level).trim(), codeBg, linkColor),
                                style = when (level) {
                                    1 -> MaterialTheme.typography.headlineSmall
                                    2 -> MaterialTheme.typography.titleLarge
                                    else -> MaterialTheme.typography.titleMedium
                                },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Regex("^\\s*([-*•]|\\d+\\.)\\s+.*").matches(line) -> {
                            val indent = line.takeWhile { it == ' ' }.length / 2
                            val m = Regex("^\\s*([-*•]|\\d+\\.)\\s+(.*)").find(line)!!
                            val bullet = m.groupValues[1].let { if (it.endsWith(".")) it else "•" }
                            Row(Modifier.padding(start = (indent * 12).dp)) {
                                Text(bullet, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(inline(m.groupValues[2], codeBg, linkColor), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        line.startsWith(">") -> Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(inline(line.removePrefix(">").trim(), codeBg, linkColor), Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> Text(inline(line, codeBg, linkColor), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val clip = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lang.ifBlank { "код" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clip.setText(AnnotatedString(code)) }) {
                    Icon(Icons.Outlined.ContentCopy, "Копировать код", Modifier.size(18.dp))
                }
            }
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }
    }
}
