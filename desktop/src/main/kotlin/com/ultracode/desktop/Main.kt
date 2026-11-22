@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package com.ultracode.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ultracode.app.data.ChatMessage
import com.ultracode.app.data.Defaults
import com.ultracode.app.data.ProviderConfig
import com.ultracode.app.data.ProviderKind
import com.ultracode.app.data.WebSource
import com.ultracode.app.ui.CookieBadge
import com.ultracode.app.ui.CookieButton
import com.ultracode.app.ui.MarkdownText
import java.awt.Desktop
import java.awt.FileDialog
import java.io.File
import java.net.URI
import java.util.UUID
import javax.imageio.ImageIO
import javax.swing.JFileChooser

fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure {
        runCatching { ProcessBuilder(if (LocalClaude.isMac) "open" else "xdg-open", url).start() }
    }
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF65558F), onPrimary = Color.White, primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF21005D), secondaryContainer = Color(0xFFE8DEF8), tertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFFFEF7FF), surface = Color(0xFFFEF7FF),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFCFBCFF), onPrimary = Color(0xFF381E72), primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFE9DDFF), secondaryContainer = Color(0xFF4A4458), tertiaryContainer = Color(0xFF633B48),
    background = Color(0xFF141218), surface = Color(0xFF141218),
)

fun main() = application {
    val state = remember { DesktopState() }
    val winState = rememberWindowState(width = 1240.dp, height = 820.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "UltraCode",
        state = winState,
        icon = androidx.compose.ui.res.painterResource("icon.png"),
    ) {
        state.onQuestion = { window.toFront(); window.requestFocus() }
        val dark = when (state.settings.themeMode) { 1 -> false; 2 -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
        // Native title bar in the theme colors (Windows), on macOS a transparent title bar over the app background.
        LaunchedEffect(dark) {
            val scheme = if (dark) DarkScheme else LightScheme
            TitleBar.apply(window, dark, scheme.surfaceContainerLow, scheme.onSurface)
        }
        if (LocalClaude.isMac) LaunchedEffect(Unit) {
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        }
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                App(state, window)
            }
        }
    }
}

@Composable
fun App(state: DesktopState, frame: java.awt.Frame) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) { state.toast?.let { snack.showSnackbar(it); state.toast = null } }
    Box(Modifier.fillMaxSize().padding(top = if (LocalClaude.isMac) 28.dp else 0.dp)) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(state)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (state.screen) {
                    "settings" -> SettingsPane(state)
                    "repos" -> ReposPane(state)
                    else -> ChatPane(state, frame)
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
    state.pending?.let { q -> QuestionDialog(q.question, q.options) { state.answer(it) } }
}

// ------------------------------------------------------------------ sidebar

@Composable
fun Sidebar(state: DesktopState) {
    var toDelete by remember { mutableStateOf<com.ultracode.app.data.Chat?>(null) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(290.dp).fillMaxHeight()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                CookieBadge(size = 40.dp, container = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.Code, null, Modifier.size(22.dp)) }
                Spacer(Modifier.size(12.dp))
                Text("UltraCode", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                CookieButton(onClick = { state.newChat(); state.screen = "chat" }, size = 46.dp, container = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, "Новый чат")
                }
            }
            Text("История чатов", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(state.chats.filter { it.messages.isNotEmpty() || it.id == state.currentId }, key = { it.id }) { c ->
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(c.messages.lastOrNull { it.model != null }?.model?.let { state.short(it) }, c.repo).joinToString(" · ").ifEmpty { "Новый" },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        },
                        icon = { Icon(if (c.agent) Icons.Filled.SmartToy else Icons.Outlined.ChatBubbleOutline, null) },
                        badge = { IconButton(onClick = { toDelete = c }, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.Delete, "Удалить", Modifier.size(18.dp)) } },
                        selected = c.id == state.currentId && state.screen == "chat",
                        onClick = { state.openChat(c.id) },
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            NavigationDrawerItem(
                label = { Text(if (state.settings.githubLogin.isNotBlank()) "GitHub · ${state.settings.githubLogin}" else "Подключить GitHub") },
                icon = { Icon(Icons.Outlined.Code, null) }, selected = state.screen == "repos",
                onClick = { state.screen = "repos"; state.loadRepos() }, modifier = Modifier.padding(horizontal = 10.dp),
            )
            NavigationDrawerItem(
                label = { Text("Настройки") }, icon = { Icon(Icons.Outlined.Settings, null) }, selected = state.screen == "settings",
                onClick = { state.screen = "settings" }, modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 12.dp),
            )
        }
    }
    toDelete?.let { c ->
        AlertDialog(onDismissRequest = { toDelete = null }, title = { Text("Удалить чат?") }, text = { Text("«${c.title}» будет удалён.") },
            confirmButton = { TextButton(onClick = { state.deleteChat(c); toDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } })
    }
}

// ------------------------------------------------------------------ chat

@Composable
fun ChatPane(state: DesktopState, frame: java.awt.Frame) {
    var picker by remember { mutableStateOf(false) }
    val chat = state.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = { picker = true }, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.short(state.settings.selectedModel)} · ${state.settings.provider()?.name ?: ""}", style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 380.dp))
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
            }
            Spacer(Modifier.size(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                item {
                    FilterChip(selected = chat?.agent ?: true, onClick = { state.toggleAgent() }, shape = CircleShape,
                        label = { Text(if (chat?.agent != false) "Агент" else "Чат") },
                        leadingIcon = { Icon(if (chat?.agent != false) Icons.Filled.SmartToy else Icons.Outlined.ChatBubbleOutline, null, Modifier.size(18.dp)) })
                }
                item {
                    AssistChip(onClick = {
                        NativeDialogs.pickFolder(frame, state.workspaceDir())?.let { state.setWorkspace(it) }
                    }, shape = CircleShape, label = { Text(state.workspaceDir().name, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)) })
                }
                item {
                    AssistChip(onClick = { state.screen = "repos"; state.loadRepos() }, shape = CircleShape, label = { Text(chat?.repo ?: "GitHub", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Code, null, Modifier.size(18.dp)) },
                        colors = if (chat?.repo != null) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else AssistChipDefaults.assistChipColors())
                }
            }
        }
        if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (chat == null || (chat.messages.isEmpty() && !state.running)) EmptyState { state.send(it) } else Messages(state)
        }
        InputBar(state, frame)
    }
    if (picker) ModelPickerDialog(state) { picker = false }
}

@Composable
fun EmptyState(onSuggestion: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CookieBadge(size = 96.dp, container = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.Code, null, Modifier.size(44.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("UltraCode", style = MaterialTheme.typography.headlineMedium)
        Text("Спрашивай что угодно: советы, тексты, учёба, код и приложения", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 720.dp)) {
            listOf("Сделай Android-приложение «Заметки» и собери APK", "Создай игру «Змейка» на Python с EXE",
                "Сделай сайт-портфолио и опубликуй на GitHub Pages", "Объясни код в этой папке").forEach { s ->
                SuggestionChip(onClick = { onSuggestion(s) }, label = { Text(s) }, shape = RoundedCornerShape(16.dp))
            }
        }
    }
}

@Composable
fun Messages(state: DesktopState) {
    val chat = state.current ?: return
    val list = rememberLazyListState()
    val count = chat.messages.size + if (state.running) 1 else 0
    LaunchedEffect(count, state.streamingText.length / 300, state.liveTools.size) { if (count > 0) list.animateScrollToItem(count - 1) }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(chat.messages, key = { it.id }) { m ->
            if (m.role == "user") UserBubble(m) else AssistantMessage(state, m, isLast = m.id == chat.messages.last().id && !state.running)
        }
        if (state.running) item(key = "live") {
            AssistantMessage(state, ChatMessage(role = "assistant",
                text = state.streamingText.ifEmpty { state.statusText ?: if (state.liveTools.isEmpty()) "Думаю…" else "" },
                model = state.usedModel, provider = state.settings.provider()?.name, tools = state.liveTools.toList(), sources = state.liveSources.toList()),
                streaming = true)
        }
    }
}

@Composable
fun UserBubble(m: ChatMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (m.images.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            m.images.forEach { p -> FileImage(p, Modifier.size(140.dp).clip(RoundedCornerShape(20.dp))) }
        }
        if (m.text.isNotBlank()) Surface(color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp), modifier = Modifier.widthIn(max = 640.dp)) {
            SelectionContainer { Text(m.text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
fun FileImage(path: String, modifier: Modifier) {
    val bmp = remember(path) { runCatching { ImageIO.read(File(path))?.toComposeImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, null, modifier, contentScale = ContentScale.Crop)
}

@Composable
fun AssistantMessage(state: DesktopState, m: ChatMessage, streaming: Boolean = false, isLast: Boolean = false) {
    val clip = LocalClipboardManager.current
    var toolsOpen by remember(m.id) { mutableStateOf(false) }
    var sitesOpen by remember(m.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CookieBadge(size = 28.dp, container = if (m.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(15.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(state.short(m.model ?: "ИИ"), style = MaterialTheme.typography.labelLarge)
            m.provider?.let { Text(" · $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (m.tools.isNotEmpty()) Surface(onClick = { toolsOpen = !toolsOpen }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SmartToy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.size(6.dp))
                    Text(if (streaming) "Агент работает: ${m.tools.last()}" else "Действия агента: ${m.tools.size}", style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(if (toolsOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
                AnimatedVisibility(toolsOpen) { Column(Modifier.padding(top = 6.dp)) { m.tools.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
            }
        }
        if (m.sources.isNotEmpty()) Surface(onClick = { sitesOpen = !sitesOpen }, shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.size(6.dp))
                    Text("Сайты: ${m.sources.size}", style = MaterialTheme.typography.labelLarge)
                    Icon(if (sitesOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
                AnimatedVisibility(sitesOpen) {
                    Column(Modifier.padding(top = 6.dp)) {
                        m.sources.forEach { s: WebSource ->
                            Text("${s.title} — ${s.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { openUrl(s.url) }.padding(vertical = 3.dp))
                        }
                    }
                }
            }
        }
        if (m.text.isNotBlank()) SelectionContainer(Modifier.padding(top = 8.dp, start = 4.dp)) { MarkdownText(m.text) }
        if (!streaming) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CookieButton(onClick = { clip.setText(AnnotatedString(m.text)) }, size = 34.dp, container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Outlined.ContentCopy, "Копировать", Modifier.size(16.dp))
            }
            if (isLast) CookieButton(onClick = { state.regenerate() }, size = 34.dp, container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Outlined.Refresh, "Ответить заново", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun InputBar(state: DesktopState, frame: java.awt.Frame) {
    fun submit() {
        if (state.running) return
        if (state.draft.isNotBlank() || state.attachments.isNotEmpty()) { state.send(state.draft); state.draft = "" }
    }
    Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Column {
            if (state.attachments.isNotEmpty()) LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.attachments.toList()) { p ->
                    Box {
                        FileImage(p, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                        Surface(onClick = { state.attachments.remove(p) }, shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface,
                            modifier = Modifier.size(22.dp).align(Alignment.TopEnd)) {
                            Icon(Icons.Filled.Close, "Убрать", Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                CookieButton(onClick = {
                    NativeDialogs.pickImages(frame).forEach { state.addImage(it) }
                }, size = 44.dp, lobes = 6, container = MaterialTheme.colorScheme.secondaryContainer) { Icon(Icons.Outlined.AddPhotoAlternate, "Фото") }
                TextField(
                    value = state.draft, onValueChange = { state.draft = it },
                    placeholder = { Text(if (state.current?.agent != false) "Спроси что угодно или опиши задачу… (Enter — отправить, Shift+Enter — новая строка)" else "Сообщение…") },
                    maxLines = 8,
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) { submit(); true } else false
                    },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                )
                CookieButton(onClick = { if (state.running) state.stop() else submit() }, size = 50.dp,
                    container = if (state.running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) {
                    Icon(if (state.running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send, if (state.running) "Стоп" else "Отправить")
                }
            }
        }
    }
}

@Composable
fun QuestionDialog(question: String, options: List<String>, onAnswer: (String) -> Unit) {
    var text by remember(question) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.SmartToy, null) },
        title = { Text("Агент хочет спросить") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(question)
                if (options.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { o -> SuggestionChip(onClick = { onAnswer(o) }, label = { Text(o) }) }
                }
                OutlinedTextField(text, { text = it }, placeholder = { Text("Ваш ответ…") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onAnswer(text) }) { Text("Ответить") } },
    )
}

@Composable
fun ModelPickerDialog(state: DesktopState, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(state.settings.selectedProvider) }
    var toHide by remember { mutableStateOf<Pair<String, String>?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбор модели") },
        text = {
            LazyColumn(Modifier.widthIn(min = 520.dp).height(560.dp)) {
                item {
                    Text("Правый клик или долгое нажатие на модель — убрать её из списка", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        OutlinedTextField(custom, { custom = it }, placeholder = { Text("Своя модель: провайдер/модель") }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = { if (custom.isNotBlank()) { state.addModel(target, custom); onDismiss() } }) { Text("Добавить") }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.settings.providers, key = { "t" + it.id }) { p ->
                            FilterChip(selected = target == p.id, onClick = { target = p.id }, label = { Text(p.name) })
                        }
                    }
                }
                state.settings.providers.forEach { p ->
                    item(key = "h" + p.id) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                if (p.apiKey.isBlank() && !p.keyOptional && p.kind != ProviderKind.CLAUDE_CODE)
                                    Text("Нужен ключ — добавьте в настройках", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (p.apiKey.isNotBlank() || p.id == Defaults.OPENROUTER) IconButton(onClick = { state.refreshModels(p) }) { Icon(Icons.Outlined.Refresh, "Обновить") }
                        }
                    }
                    items(p.models.distinct(), key = { p.id + "/" + it }) { m ->
                        val sel = state.settings.selectedProvider == p.id && state.settings.selectedModel == m
                        Surface(color = if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { state.selectModel(p.id, m); onDismiss() }, onLongClick = { toHide = p.id to m })
                                .onPreviewKeyEvent { false }) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(state.short(m), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (m.endsWith(":free")) Text("free", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                                if (m in p.toolModels) Text("  агент", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { toHide = p.id to m }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "Убрать", Modifier.size(14.dp)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
    toHide?.let { (pid, m) ->
        AlertDialog(onDismissRequest = { toHide = null }, title = { Text("Убрать модель?") },
            text = { Text("«${state.short(m)}» пропадёт из списка. Вернуть можно через «Своя модель».") },
            confirmButton = { TextButton(onClick = { state.hideModel(pid, m); toHide = null }) { Text("Убрать") } },
            dismissButton = { TextButton(onClick = { toHide = null }) { Text("Отмена") } })
    }
}

// ------------------------------------------------------------------ settings

@Composable
fun Section(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CookieBadge(size = 36.dp, container = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.size(20.dp)) }
                Spacer(Modifier.size(12.dp)); Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
fun KeyField(label: String, value: String, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp))

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun KeyProvider(state: DesktopState, p: ProviderConfig, title: String, about: String, keyLabel: String) {
    Section(Icons.Filled.AutoAwesome, title) {
        Text(about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KeyField(keyLabel, p.apiKey) { state.updateProvider(p.copy(apiKey = it.trim())) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.keyUrl.isNotBlank()) TextButton(onClick = { openUrl(p.keyUrl) }) { Text("Получить ключ") }
            if (p.apiKey.isNotBlank() || p.id == Defaults.OPENROUTER) TextButton(onClick = { state.refreshModels(p) }, enabled = !state.loadingModels) {
                Text("Обновить список моделей (${p.models.size})")
            }
        }
    }
}

@Composable
fun SettingsPane(state: DesktopState) {
    val s = state.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.screen = "chat" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }
        Section(Icons.Outlined.Settings, "Внешний вид") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Системная", "Светлая", "Тёмная").forEachIndexed { i, l ->
                    SegmentedButton(s.themeMode == i, { state.updateSettings { it.copy(themeMode = i) } }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                }
            }
        }
        Section(Icons.Outlined.Code, "Claude Code по подписке") {
            Text("Работает официальный Claude Code, установленный на этом компьютере. Вы входите в свой аккаунт Claude в нём самом — " +
                "UltraCode ваш вход не видит, а только передаёт задачи и показывает ответы.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.claudeVersion?.let { "Установлен: $it" } ?: "Claude Code не найден", style = MaterialTheme.typography.titleSmall,
                color = if (state.claudeVersion != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            state.claudeAuth?.let { a ->
                when {
                    !a.loggedIn -> Text("Вход не выполнен", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    a.apiBilling -> Text(
                        "Вход выполнен, но через API-ключ (${a.method}): запросы оплачиваются отдельно, а не по подписке Pro/Max. " +
                            "Чтобы работать по подписке: «Сменить вход» → в Claude Code введите /login → выберите «Claude account with subscription». " +
                            "Если на компьютере задана переменная среды ANTHROPIC_API_KEY, удалите её — она заставляет Claude Code платить по API.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    else -> Text("Вход выполнен (${a.method}) — работает по вашей подписке", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.claudeVersion == null) Button(onClick = { state.installClaude() }) { Text("Установить Claude Code") }
                OutlinedButton(onClick = { state.loginClaude() }) {
                    Text(if (state.claudeAuth?.loggedIn == true) "Сменить вход" else "Войти в Claude")
                }
                TextButton(onClick = { state.checkClaude() }) { Text("Проверить") }
            }
            Text("1) «Установить» откроет терминал с официальным установщиком. 2) «Войти в Claude» откроет Claude Code — войдите командой /login. " +
                "3) Выберите модель «Claude Code (подписка)» в чате.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(s.ccFolder, { v -> state.updateSettings { it.copy(ccFolder = v) } }, label = { Text("Папка проекта (внутри рабочей папки)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp))
            SwitchRow("Разрешить команды терминала", "Claude Code сможет запускать команды (сборка, git, npm)", s.ccAllowBash) { v -> state.updateSettings { it.copy(ccAllowBash = v) } }
        }
        s.provider(Defaults.OPENROUTER)?.let {
            KeyProvider(state, it, "Бесплатные модели (OpenRouter)", "DeepSeek, GLM, Qwen Coder, Kimi и другие бесплатно. Нужен бесплатный ключ OpenRouter.", "Ключ OpenRouter (sk-or-…)")
            SwitchRow("Автозамена занятой модели", "Если модель перегружена — ответит другая бесплатная", s.autoFallback) { v -> state.updateSettings { it.copy(autoFallback = v) } }
        }
        s.provider(Defaults.GROQ)?.let { KeyProvider(state, it, "Groq", "Очень быстрые модели: Llama 3.3 70B, GPT-OSS, Kimi K2, Qwen3. Бесплатный ключ.", "Ключ Groq (gsk_…)") }
        s.provider(Defaults.DEEPSEEK)?.let { KeyProvider(state, it, "DeepSeek API", "deepseek-chat и deepseek-reasoner. Платный, но дешёвый.", "Ключ DeepSeek (sk-…)") }
        s.provider(Defaults.ANTHROPIC)?.let { KeyProvider(state, it, "Claude API", "Claude по API-ключу из console.anthropic.com.", "API-ключ Claude (sk-ant-…)") }
        CustomProviders(state)
        Section(Icons.Filled.SmartToy, "Агент") {
            Text("Рабочая папка: ${state.workspaceDir().absolutePath}", style = MaterialTheme.typography.bodyMedium)
            SwitchRow("Доступ в интернет", "ИИ может искать и читать сайты — вы видите, какие", s.webAccess) { v -> state.updateSettings { it.copy(webAccess = v) } }
            Text("Максимум шагов агента: ${s.maxSteps}", style = MaterialTheme.typography.labelLarge)
            Slider(s.maxSteps.toFloat(), { v -> state.updateSettings { it.copy(maxSteps = v.toInt()) } }, valueRange = 5f..100f)
            OutlinedTextField(s.systemPrompt, { v -> state.updateSettings { it.copy(systemPrompt = v) } }, label = { Text("Свои инструкции для ИИ") },
                minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp))
        }
        Text("UltraCode для компьютера · настройки и чаты хранятся в ${state.store.dir.absolutePath}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CustomProviders(state: DesktopState) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var models by remember { mutableStateOf("") }
    Section(Icons.Filled.Add, "Свои нейросети") {
        Text("Любой OpenAI-совместимый API: Ollama, LM Studio, Gemini, Mistral, GitHub Models…", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.settings.providers.filter { !it.builtin }.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.name} · ${p.baseUrl}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { state.removeProvider(p.id) }) { Icon(Icons.Outlined.Delete, "Удалить") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Defaults.presets.forEach { pr ->
                SuggestionChip(onClick = { name = pr.name; url = pr.url; models = pr.models }, label = { Text(pr.name) })
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        KeyField("API-ключ (если нужен)", key) { key = it }
        OutlinedTextField(models, { models = it }, label = { Text("Модели через запятую") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = name.isNotBlank() && url.isNotBlank(), onClick = {
            state.updateProvider(ProviderConfig(id = UUID.randomUUID().toString(), name = name.trim(), kind = ProviderKind.OPENAI, baseUrl = url.trim(),
                apiKey = key.trim(), models = models.split(',').map { it.trim() }.filter { DesktopStore.validModelId(it) }, keyOptional = key.isBlank()))
            name = ""; url = ""; key = ""; models = ""
            state.toast = "Нейросеть добавлена"
        }) { Text("Добавить нейросеть") }
    }
}

// ------------------------------------------------------------------ GitHub

@Composable
fun ReposPane(state: DesktopState) {
    val clip = LocalClipboardManager.current
    var token by remember { mutableStateOf("") }
    val connected = state.settings.githubToken.isNotBlank()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.screen = "chat" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(if (connected) "GitHub · ${state.settings.githubLogin}" else "GitHub", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (connected) {
                IconButton(onClick = { state.loadRepos() }) { Icon(Icons.Outlined.Refresh, "Обновить") }
                TextButton(onClick = { state.disconnectGitHub() }) { Text("Выйти") }
            }
        }
        if (!connected) {
            val d = state.githubDevice
            Section(Icons.Outlined.Code, "Подключите GitHub") {
                Text("Агент сможет читать и создавать репозитории, делать коммиты, собирать приложения через GitHub Actions и публиковать сайты.")
                if (d == null) Button(onClick = { state.loginGitHub { openUrl(it) } }) { Text("Войти через GitHub") }
                else {
                    Text("Введите этот код на странице GitHub:")
                    Surface(onClick = { clip.setText(AnnotatedString(d.userCode)) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(d.userCode, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(16.dp))
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clip.setText(AnnotatedString(d.userCode)); openUrl(d.verificationUri) }) { Text("Открыть GitHub") }
                        TextButton(onClick = { state.cancelGitHubLogin() }) { Text("Отмена") }
                    }
                }
                Text("Или по токену (права repo, workflow):", style = MaterialTheme.typography.labelLarge)
                KeyField("Токен GitHub (ghp_…)", token) { token = it }
                Button(onClick = { state.connectGitHub(token) }, enabled = token.isNotBlank()) { Text("Подключить по токену") }
            }
        } else {
            if (state.reposLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Нажмите на репозиторий, чтобы подключить его к текущему чату", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.repos, key = { it.fullName }) { r ->
                    val sel = r.fullName == state.current?.repo
                    Surface(onClick = { state.attachRepo(r.fullName); state.screen = "chat" }, shape = RoundedCornerShape(18.dp),
                        color = if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(r.fullName + if (r.private) "  · приватный" else "", style = MaterialTheme.typography.titleSmall)
                            val sub = listOf(r.language, r.description).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
