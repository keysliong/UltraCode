@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.ultracode.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ultracode.app.AppViewModel
import com.ultracode.app.data.ChatMessage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(vm: AppViewModel, onSettings: () -> Unit, onRepos: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var showModels by remember { mutableStateOf(false) }
    val chat = vm.current

    LaunchedEffect(vm.toast) {
        vm.toast?.let { snack.showSnackbar(it); vm.toast = null }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setWorkspace(uri)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            HistoryDrawer(
                vm,
                onPick = { vm.openChat(it); scope.launch { drawer.close() } },
                onNew = { vm.newChat(); scope.launch { drawer.close() } },
                onSettings = { scope.launch { drawer.close() }; onSettings() },
                onRepos = { scope.launch { drawer.close() }; onRepos() },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, "Меню") }
                    },
                    title = {
                        Surface(
                            onClick = { showModels = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    shortModel(vm.settings.selectedModel),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp),
                                )
                                Icon(Icons.Outlined.ArrowDropDown, null)
                            }
                        }
                    },
                    actions = {
                        CookieButton(
                            onClick = { vm.newChat() },
                            size = 40.dp,
                            lobes = 6,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Icon(Icons.Filled.Add, "Новый чат") }
                    },
                )
            },
        ) { inner ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .consumeWindowInsets(inner)
                    .imePadding(),
            ) {
                ContextChips(
                    agent = chat?.agent ?: true,
                    folder = vm.workspace().label,
                    repo = chat?.repo,
                    onAgent = { vm.toggleAgent() },
                    onFolder = { folderPicker.launch(null) },
                    onRepo = onRepos,
                )
                if (vm.running) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                Box(Modifier.weight(1f)) {
                    if (chat == null || (chat.messages.isEmpty() && !vm.running)) {
                        EmptyState(onSuggestion = { vm.send(it) })
                    } else {
                        Messages(vm)
                    }
                }
                vm.pending?.let { q -> QuestionCard(q.question, q.options, onAnswer = { vm.answer(it) }) }
                InputBar(vm)
            }
        }
    }

    if (showModels) ModelPicker(vm, onDismiss = { showModels = false }, onSettings = { showModels = false; onSettings() })
}

fun shortModel(m: String): String = m.substringAfter('/').removeSuffix(":free")

@Composable
private fun ContextChips(
    agent: Boolean, folder: String, repo: String?,
    onAgent: () -> Unit, onFolder: () -> Unit, onRepo: () -> Unit,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = agent,
                onClick = onAgent,
                label = { Text(if (agent) "Агент" else "Чат") },
                leadingIcon = { Icon(if (agent) Icons.Filled.SmartToy else Icons.Outlined.ChatBubbleOutline, null, Modifier.size(18.dp)) },
                shape = CircleShape,
            )
        }
        item {
            AssistChip(
                onClick = onFolder,
                label = { Text(folder, maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)) },
                shape = CircleShape,
            )
        }
        item {
            AssistChip(
                onClick = onRepo,
                label = { Text(repo ?: "GitHub", maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.Code, null, Modifier.size(18.dp)) },
                shape = CircleShape,
                colors = if (repo != null) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                else AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CookieBadge(size = 96.dp, lobes = 9, container = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Outlined.Code, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text("UltraCode", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Пиши код, создавай приложения для телефона и ПК, работай с GitHub",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Сделай Android-приложение «Заметки» и собери APK",
                "Создай игру «Змейка» для ПК на Python с EXE",
                "Сделай сайт-портфолио и опубликуй на GitHub Pages",
                "Напиши Telegram-бота на Python",
            ).forEach { s ->
                SuggestionChip(onClick = { onSuggestion(s) }, label = { Text(s) }, shape = RoundedCornerShape(16.dp))
            }
        }
    }
}

@Composable
private fun Messages(vm: AppViewModel) {
    val chat = vm.current ?: return
    val state = rememberLazyListState()
    val count = chat.messages.size + if (vm.running) 1 else 0
    LaunchedEffect(count, vm.streamingText.length / 200, vm.liveTools.size) {
        if (count > 0) state.animateScrollToItem(count - 1)
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(chat.messages, key = { it.id }) { m ->
            if (m.role == "user") UserBubble(m)
            else AssistantMessage(m, isLast = m.id == chat.messages.last().id && !vm.running, onRegenerate = { vm.regenerate() })
        }
        if (vm.running) item(key = "streaming") {
            AssistantMessage(
                ChatMessage(
                    role = "assistant",
                    text = vm.streamingText.ifEmpty { vm.statusText ?: if (vm.liveTools.isEmpty()) "Думаю…" else "" },
                    model = vm.usedModel.ifEmpty { vm.settings.selectedModel },
                    provider = vm.settings.provider()?.name,
                    tools = vm.liveTools.toList(),
                    sources = vm.liveSources.toList(),
                ),
                streaming = true,
            )
        }
    }
}

@Composable
private fun UserBubble(m: ChatMessage) {
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (m.images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                m.images.forEach {
                    AsyncImage(
                        model = File(it), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(120.dp).clip(RoundedCornerShape(20.dp)),
                    )
                }
            }
        }
        if (m.text.isNotBlank()) Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(24.dp))
                .combinedClickable(onClick = {}, onLongClick = { clip.setText(AnnotatedString(m.text)) }),
        ) {
            SelectionContainer {
                Text(m.text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun AssistantMessage(m: ChatMessage, streaming: Boolean = false, isLast: Boolean = false, onRegenerate: () -> Unit = {}) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    var toolsOpen by rememberSaveable(m.id) { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { info = true }) {
            CookieBadge(size = 28.dp, container = if (m.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.size(8.dp))
            Text(shortModel(m.model ?: "ИИ"), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            m.provider?.let {
                Text(" · $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (m.tools.isNotEmpty()) {
            Surface(
                onClick = { toolsOpen = !toolsOpen },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SmartToy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (streaming) "Агент работает: ${m.tools.last()}" else "Действия агента: ${m.tools.size}",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(if (toolsOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    }
                    AnimatedVisibility(toolsOpen) {
                        Column(Modifier.padding(top = 6.dp)) {
                            m.tools.forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
        if (m.sources.isNotEmpty()) SourcesCard(m.sources, streaming)
        if (m.text.isNotBlank()) {
            SelectionContainer(Modifier.padding(top = 8.dp, start = 4.dp)) {
                MarkdownText(m.text)
            }
        }
        if (!streaming) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CookieButton(onClick = { clip.setText(AnnotatedString(m.text)) }, size = 36.dp, lobes = 9, container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Outlined.ContentCopy, "Копировать", Modifier.size(17.dp))
            }
            CookieButton(onClick = {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, m.text), "Поделиться"))
            }, size = 36.dp, lobes = 9, container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Outlined.Share, "Поделиться", Modifier.size(17.dp))
            }
            CookieButton(onClick = { info = true }, size = 36.dp, lobes = 9, container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Outlined.Info, "Какая ИИ ответила", Modifier.size(17.dp))
            }
            if (isLast) CookieButton(onClick = onRegenerate, size = 36.dp, lobes = 9, container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Outlined.Refresh, "Ответить заново", Modifier.size(17.dp))
            }
        }
    }
    if (info) AlertDialog(
        onDismissRequest = { info = false },
        icon = { Icon(Icons.Filled.AutoAwesome, null) },
        title = { Text("Кто ответил") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Модель: ${m.model ?: "—"}")
                Text("Провайдер: ${m.provider ?: "—"}")
                Text("Время: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(m.time)))
                if (m.tools.isNotEmpty()) Text("Действий агента: ${m.tools.size}")
                Text("Символов: ${m.text.length}")
            }
        },
        confirmButton = { TextButton(onClick = { info = false }) { Text("OK") } },
    )
}

@Composable
private fun QuestionCard(question: String, options: List<String>, onAnswer: (String) -> Unit) {
    var text by remember(question) { mutableStateOf("") }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CookieBadge(size = 32.dp, container = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Filled.SmartToy, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text("Агент хочет спросить", style = MaterialTheme.typography.titleMedium)
            }
            Text(question, style = MaterialTheme.typography.bodyLarge)
            if (options.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { o -> SuggestionChip(onClick = { onAnswer(o) }, label = { Text(o) }, shape = RoundedCornerShape(16.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("Ваш ответ…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                CookieButton(onClick = { if (text.isNotBlank()) onAnswer(text) }, size = 48.dp, container = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Ответить")
                }
            }
        }
    }
}

@Composable
private fun InputBar(vm: AppViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris: List<Uri> ->
        uris.forEach { vm.addImage(it) }
    }
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            if (vm.attachments.isNotEmpty()) LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.attachments.toList()) { path ->
                    Box {
                        AsyncImage(
                            model = File(path), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(Cookie12),
                        )
                        Surface(
                            onClick = { vm.attachments.remove(path) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.inverseSurface,
                            modifier = Modifier.size(22.dp).align(Alignment.TopEnd),
                        ) { Icon(Icons.Filled.Close, "Убрать", Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.inverseOnSurface) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(6.dp)) {
                CookieButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    size = 46.dp, lobes = 6,
                    modifier = Modifier.padding(bottom = 9.dp),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                ) { Icon(Icons.Outlined.AddPhotoAlternate, "Фото") }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(if (vm.current?.agent != false) "Опиши задачу для агента…" else "Сообщение…") },
                    maxLines = 6,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                CookieButton(
                    onClick = {
                        if (vm.running) vm.stop()
                        else if (text.isNotBlank() || vm.attachments.isNotEmpty()) { vm.send(text); text = "" }
                    },
                    size = 52.dp, lobes = 9,
                    modifier = Modifier.padding(bottom = 2.dp),
                    container = if (vm.running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ) {
                    Icon(if (vm.running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send, if (vm.running) "Стоп" else "Отправить")
                }
            }
        }
    }
}

@Composable
private fun HistoryDrawer(
    vm: AppViewModel,
    onPick: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onRepos: () -> Unit,
) {
    var toDelete by remember { mutableStateOf<com.ultracode.app.data.Chat?>(null) }
    ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CookieBadge(size = 40.dp, container = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.Code, null, Modifier.size(22.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text("UltraCode", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            CookieButton(onClick = onNew, size = 52.dp, lobes = 9, container = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, "Новый чат")
            }
        }
        Text("История чатов", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            if (vm.chats.none { it.messages.isNotEmpty() }) item {
                Text("Пока пусто", Modifier.padding(horizontal = 28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(vm.chats.filter { it.messages.isNotEmpty() || it.id == vm.currentId }, key = { it.id }) { c ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val model = c.messages.lastOrNull { it.model != null }?.model
                            Text(
                                listOfNotNull(model?.let { shortModel(it) }, c.repo).joinToString(" · ").ifEmpty { "Новый" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            )
                        }
                    },
                    icon = { Icon(if (c.agent) Icons.Filled.SmartToy else Icons.Outlined.ChatBubbleOutline, null) },
                    badge = {
                        IconButton(onClick = { toDelete = c }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Delete, "Удалить", Modifier.size(18.dp))
                        }
                    },
                    selected = c.id == vm.currentId,
                    onClick = { onPick(c.id) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        val drawerCtx = LocalContext.current
        NavigationDrawerItem(
            label = { Text("Claude Code (по подписке)") },
            icon = { Icon(Icons.Filled.AutoAwesome, null) },
            selected = false, onClick = { openClaudeCode(drawerCtx) }, modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text(if (vm.settings.githubLogin.isNotBlank()) "GitHub · ${vm.settings.githubLogin}" else "Подключить GitHub") },
            icon = { Icon(Icons.Outlined.Code, null) },
            selected = false, onClick = onRepos, modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Настройки") },
            icon = { Icon(Icons.Outlined.Settings, null) },
            selected = false, onClick = onSettings, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 12.dp),
        )
    }
    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить чат?") },
            text = { Text("«${c.title}» будет удалён без возможности восстановления.") },
            confirmButton = { TextButton(onClick = { vm.deleteChat(c); toDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ModelPicker(vm: AppViewModel, onDismiss: () -> Unit, onSettings: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var custom by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item {
                Text("Выбор модели", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            vm.settings.providers.forEach { p ->
                item(key = "h_" + p.id) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            if (p.apiKey.isBlank() && !p.keyOptional) Text(
                                "Нужен ключ — добавьте в настройках",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (p.apiKey.isNotBlank() || p.keyOptional || p.id == com.ultracode.app.data.Defaults.OPENROUTER) {
                            IconButton(onClick = { vm.refreshModels(p) }, enabled = !vm.loadingModels) {
                                Icon(Icons.Outlined.Refresh, "Обновить список")
                            }
                        }
                    }
                }
                items(p.models.distinct(), key = { p.id + "/" + it }) { m ->
                    val selected = vm.settings.selectedProvider == p.id && vm.settings.selectedModel == m
                    ListItem(
                        headlineContent = { Text(shortModel(m), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            if (m.endsWith(":free")) Text("free", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        },
                        leadingContent = {
                            CookieBadge(
                                size = 32.dp,
                                container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        ),
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { vm.selectModel(p.id, m); onDismiss() },
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Другая модель для «${vm.settings.provider()?.name}»", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = custom, onValueChange = { custom = it },
                            placeholder = { Text("например deepseek/deepseek-r1:free") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        CookieButton(onClick = {
                            if (custom.isNotBlank()) { vm.selectModel(vm.settings.selectedProvider, custom.trim()); onDismiss() }
                        }, size = 48.dp) { Icon(Icons.Filled.Add, "Выбрать") }
                    }
                    TextButton(onClick = onSettings) { Text("Подключить свою нейросеть…") }
                }
            }
        }
    }
}

/** Opens the official Claude app (Claude Code with your subscription) or claude.ai/code. */
fun openClaudeCode(ctx: android.content.Context) {
    val app = ctx.packageManager.getLaunchIntentForPackage("com.anthropic.claude")
    if (app != null) runCatching { ctx.startActivity(app) }.onSuccess { return }
    openUrl(ctx, "https://claude.ai/code")
}

@Composable
private fun SourcesCard(sources: List<com.ultracode.app.data.WebSource>, streaming: Boolean) {
    val ctx = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val pages = sources.filter { !it.search }
    Surface(
        onClick = { open = !open },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Language, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(6.dp))
                Text(
                    if (streaming) "Смотрит: ${sources.last().url.substringAfter("://").substringBefore('/')}"
                    else "Сайты: ${pages.size}" + if (sources.size > pages.size) " · поисков: ${sources.size - pages.size}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    sources.forEach { src ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { openUrl(ctx, src.url) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                        ) {
                            CookieBadge(size = 26.dp, lobes = 6, container = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Icon(if (src.search) Icons.Outlined.Search else Icons.Outlined.Language, null, Modifier.size(14.dp))
                            }
                            Spacer(Modifier.size(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(src.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(src.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
