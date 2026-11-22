package com.ultracode.app.term

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultracode.app.ui.CookieButton

/** Terminal screen (phone and desktop). [status] shows preparation progress (e.g. installing Python). */
@Composable
fun TerminalView(
    session: TerminalSession?,
    title: String,
    status: String?,
    quick: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    var histPos by remember { mutableIntStateOf(-1) }
    val list = rememberLazyListState()
    val lines = session?.lines
    LaunchedEffect(lines?.size, session?.partial) {
        val count = (lines?.size ?: 0) + 1
        if (count > 0) list.scrollToItem(count - 1)
    }
    fun submit() {
        val s = session ?: return
        s.send(input); input = ""; histPos = -1
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Column(Modifier.weight(1f)) {
                Text("Терминал", style = MaterialTheme.typography.titleLarge)
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = { session?.clear() }) { Icon(Icons.Outlined.DeleteSweep, "Очистить") }
            IconButton(onClick = { session?.restart() }) { Icon(Icons.Outlined.RestartAlt, "Перезапустить (остановить программу)") }
        }
        if (status != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Surface(
            color = Color(0xFF111015), contentColor = Color(0xFFE6E1E5), shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            SelectionContainer {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    if (lines != null) items(lines.size) { i ->
                        val l = lines[i]
                        Text(l, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp,
                            color = if (l.startsWith("› ")) Color(0xFFCFBCFF) else Color(0xFFE6E1E5))
                    }
                    item { Text(session?.partial.orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp) }
                }
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(quick) { q -> SuggestionChip(onClick = { input = q }, label = { Text(q, fontFamily = FontFamily.Monospace) }) }
        }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                IconButton(onClick = {
                    val h = session?.history ?: return@IconButton
                    if (h.isNotEmpty()) { histPos = if (histPos <= 0) h.size - 1 else histPos - 1; input = h[histPos] }
                }) { Icon(Icons.Outlined.KeyboardArrowUp, "Предыдущая команда") }
                TextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text("Команда, например: python", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrect = false),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) { submit(); true } else false
                    },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                )
                Spacer(Modifier.size(4.dp))
                CookieButton(onClick = { submit() }, size = 46.dp, container = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Выполнить")
                }
            }
        }
    }
}
