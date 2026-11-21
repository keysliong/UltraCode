@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ultracode.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ultracode.app.AppViewModel
import com.ultracode.app.data.Defaults
import com.ultracode.app.data.ProviderConfig
import com.ultracode.app.data.ProviderKind
import java.util.UUID

fun openUrl(ctx: android.content.Context, url: String) =
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onClaude: () -> Unit = {}) {
    val ctx = LocalContext.current
    val s = vm.settings
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snack = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) vm.setWorkspace(it) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                scrollBehavior = scroll,
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(Icons.Outlined.Palette, "Внешний вид") {
                SwitchRow("Material You", "Цвета из обоев (Android 12+)", s.dynamicColor) { v -> vm.updateSettings { it.copy(dynamicColor = v) } }
                Text("Тема", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Системная", "Светлая", "Тёмная").forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = s.themeMode == i,
                            onClick = { vm.updateSettings { it.copy(themeMode = i) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text(label) }
                    }
                }
            }

            val claude = s.provider(Defaults.ANTHROPIC)
            if (claude != null) Section(Icons.Outlined.AutoAwesome, "Аккаунт Claude") {
                Text(
                    "Нажмите «Войти в аккаунт Claude» — откроется claude.ai поверх UltraCode. Войдите (можно через Google), и Claude Code будет работать по вашей подписке. " +
                        "API-ключ для работы внутри UltraCode хранится только на этом телефоне.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onClaude, modifier = Modifier.fillMaxWidth()) {
                    Text("Войти в аккаунт Claude")
                }
                Text(
                    "Для чата UltraCode с Claude-моделями можно также указать API-ключ:",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyField("API-ключ Claude (sk-ant-…)", claude.apiKey) { vm.updateProvider(claude.copy(apiKey = it)) }
                AddModelRow("Добавить модель Claude", "например claude-opus-5-5") { vm.addModel(claude.id, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { openUrl(ctx, claude.keyUrl) }) { Text("Войти и получить ключ") }
                    if (claude.apiKey.isNotBlank()) TextButton(onClick = { vm.refreshModels(claude) }) { Text("Обновить модели") }
                }
            }

            val free = s.provider(Defaults.OPENROUTER)
            if (free != null) Section(Icons.Outlined.Hub, "Бесплатные модели") {
                Text(
                    "DeepSeek, GLM, Qwen Coder, Kimi, Llama, Gemma и другие бесплатно через OpenRouter. " +
                        "Список проверяется по реальным ценам OpenRouter при каждом запуске: платные модели убираются автоматически. Нужен бесплатный ключ.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyField("Ключ OpenRouter (sk-or-…)", free.apiKey) { vm.updateProvider(free.copy(apiKey = it)) }
                AddModelRow("Добавить модель OpenRouter", "например deepseek/deepseek-r1:free") { vm.addModel(free.id, it) }
                SwitchRow("Автозамена занятой модели", "Если модель перегружена (ошибка 429) — ответит другая бесплатная", s.autoFallback) { v ->
                    vm.updateSettings { it.copy(autoFallback = v) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { openUrl(ctx, free.keyUrl) }) { Text("Получить ключ") }
                    TextButton(onClick = { vm.refreshModels(free) }, enabled = !vm.loadingModels) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                        Text("Проверить бесплатные (${free.models.size})")
                    }
                }
            }

            Section(Icons.Outlined.Key, "Свои нейросети") {
                Text(
                    "Подключите любую нейросеть с OpenAI- или Anthropic-совместимым API: DeepSeek, GLM, Groq, Gemini, Mistral, Ollama на ПК и т.д.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.providers.filter { !it.builtin }.forEach { p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${p.models.size} мод. · ${p.baseUrl}", maxLines = 1) },
                        leadingContent = {
                            CookieBadge(size = 36.dp, lobes = 6, container = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(p.name.take(1), style = MaterialTheme.typography.titleMedium)
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = p }) { Icon(Icons.Outlined.Edit, "Изменить") }
                                IconButton(onClick = { vm.removeProvider(p.id) }) { Icon(Icons.Outlined.Delete, "Удалить") }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    CookieButton(onClick = {
                        editing = ProviderConfig(id = UUID.randomUUID().toString(), name = "", kind = ProviderKind.OPENAI, baseUrl = "")
                    }, size = 52.dp, container = MaterialTheme.colorScheme.primary) { Icon(Icons.Filled.Add, "Добавить") }
                    Spacer(Modifier.size(12.dp))
                    Text("Добавить нейросеть", style = MaterialTheme.typography.titleSmall)
                }
            }

            Section(Icons.Outlined.SmartToy, "Агент") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, null)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Папка проектов", style = MaterialTheme.typography.titleSmall)
                        Text(vm.workspace().label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { folderPicker.launch(null) }) { Text("Выбрать") }
                    if (s.workspaceUri != null) TextButton(onClick = { vm.setWorkspace(null) }) { Text("Сброс") }
                }
                SwitchRow("Доступ в интернет", "ИИ может искать и читать сайты — вы видите, какие", s.webAccess) { v ->
                    vm.updateSettings { it.copy(webAccess = v) }
                }
                SwitchRow("Уведомления «Агент хочет спросить»", "Когда ИИ нужен ваш ответ", s.notifyQuestions) { v ->
                    vm.updateSettings { it.copy(notifyQuestions = v) }
                }
                Text("Максимум шагов агента: ${s.maxSteps}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = s.maxSteps.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(maxSteps = v.toInt()) } },
                    valueRange = 5f..100f,
                )
                OutlinedTextField(
                    value = s.systemPrompt,
                    onValueChange = { v -> vm.updateSettings { it.copy(systemPrompt = v) } },
                    label = { Text("Свои инструкции для ИИ") },
                    placeholder = { Text("Например: пиши комментарии на русском, используй Material 3…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                )
            }

            Section(Icons.Outlined.AutoAwesome, "О приложении") {
                Text("UltraCode 1.0 — ИИ-агент для программирования на телефоне.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Создавайте приложения для Android, Windows, macOS, Linux и веба: агент пишет код, отправляет его на GitHub, а GitHub Actions собирает APK/EXE.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    editing?.let { p -> ProviderDialog(p, onDismiss = { editing = null }, onSave = { vm.updateProvider(it); editing = null }) }
}

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CookieBadge(size = 36.dp, container = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.size(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun ProviderDialog(initial: ProviderConfig, onDismiss: () -> Unit, onSave: (ProviderConfig) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.baseUrl) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var models by remember { mutableStateOf(initial.models.joinToString(", ")) }
    var kind by remember { mutableStateOf(initial.kind) }
    var keyOptional by remember { mutableStateOf(initial.keyOptional) }
    var keyUrl by remember { mutableStateOf(initial.keyUrl) }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        title = { Text(if (initial.name.isEmpty()) "Своя нейросеть" else "Изменить «${initial.name}»") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.name.isEmpty()) {
                    Text("Шаблоны", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Defaults.presets.forEach { pr ->
                            FilterChip(
                                selected = name == pr.name,
                                onClick = {
                                    name = pr.name; url = pr.url; models = pr.models; kind = pr.kind
                                    keyOptional = pr.keyOptional; keyUrl = pr.keyUrl
                                },
                                label = { Text(pr.name) },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(kind == ProviderKind.OPENAI, { kind = ProviderKind.OPENAI }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("OpenAI API") }
                    SegmentedButton(kind == ProviderKind.ANTHROPIC, { kind = ProviderKind.ANTHROPIC }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Anthropic API") }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    key, { key = it }, label = { Text(if (keyOptional) "API-ключ (необязательно)" else "API-ключ") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                if (keyUrl.isNotBlank()) TextButton(onClick = { openUrl(ctx, keyUrl) }) { Text("Получить ключ") }
                OutlinedTextField(models, { models = it }, label = { Text("Модели через запятую") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(), baseUrl = url.trim(), apiKey = key.trim(), kind = kind,
                            models = models.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() },
                            keyOptional = keyOptional || key.isBlank() && (url.startsWith("http://")),
                            keyUrl = keyUrl,
                        ),
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun AddModelRow(label: String, hint: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text(label) }, placeholder = { Text(hint) },
            singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        CookieButton(onClick = { if (text.isNotBlank()) { onAdd(text); text = "" } }, size = 48.dp, container = MaterialTheme.colorScheme.primary) {
            Icon(Icons.Filled.Add, "Добавить")
        }
    }
}
