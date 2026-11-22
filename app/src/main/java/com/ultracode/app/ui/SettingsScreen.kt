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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.material3.OutlinedButton
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

            val cc = s.provider(Defaults.CLAUDE_CODE)
            if (cc != null) ClaudeCodeSection(vm, cc)

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

            val groq = s.provider(Defaults.GROQ)
            if (groq != null) Section(Icons.Outlined.Bolt, "Groq") {
                Text(
                    "Очень быстрые модели: Llama 3.3 70B, GPT-OSS 120B, Kimi K2, Qwen3. Бесплатно с ключом Groq " +
                        "(регистрация за минуту, у бесплатного тарифа есть лимиты в минуту и в день).",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyField("Ключ Groq (gsk_…)", groq.apiKey) { vm.updateProvider(groq.copy(apiKey = it.trim())) }
                // After a key is entered, load the real list of Groq models.
                LaunchedEffect(groq.apiKey) {
                    if (groq.apiKey.startsWith("gsk_") && groq.apiKey.length > 30) {
                        kotlinx.coroutines.delay(1200)
                        vm.refreshModels(groq, silent = true)
                    }
                }
                AddModelRow("Добавить модель Groq", "например llama-3.3-70b-versatile") { vm.addModel(groq.id, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { openUrl(ctx, groq.keyUrl) }) { Text("Получить ключ") }
                    if (groq.apiKey.isNotBlank()) TextButton(onClick = { vm.refreshModels(groq) }, enabled = !vm.loadingModels) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                        Text("Обновить список (${groq.models.size})")
                    }
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
                val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
                Text("UltraCode $ver — ИИ-агент для программирования на телефоне.", style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun ClaudeCodeSection(vm: AppViewModel, cc: ProviderConfig) {
    val ctx = LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val s = vm.settings
    var mode by remember {
        mutableIntStateOf(when { cc.baseUrl == Defaults.EMBEDDED -> 0; cc.baseUrl.contains("127.0.0.1") -> 1; else -> 2 })
    }
    LaunchedEffect(Unit) { vm.refreshEmbedded() }
    Section(Icons.Outlined.Terminal, "Claude Code по подписке") {
        Text(
            "Модели Claude по подписке Pro/Max прямо в чате UltraCode. Работает официальный Claude Code: " +
                "вы входите в свой аккаунт в нём самом, UltraCode ваш вход не видит, а только передаёт задачи и показывает ответы.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(mode == 0, { mode = 0; vm.updateProvider(cc.copy(baseUrl = Defaults.EMBEDDED)) }, SegmentedButtonDefaults.itemShape(0, 3)) { Text("В приложении") }
            SegmentedButton(mode == 1, { mode = 1; vm.updateProvider(cc.copy(baseUrl = "http://127.0.0.1:8765")) }, SegmentedButtonDefaults.itemShape(1, 3)) { Text("Termux") }
            SegmentedButton(mode == 2, { mode = 2; if (cc.baseUrl == Defaults.EMBEDDED || cc.baseUrl.contains("127.0.0.1")) vm.updateProvider(cc.copy(baseUrl = "http://192.168.0.2:8765")) }, SegmentedButtonDefaults.itemShape(2, 3)) { Text("ПК") }
        }
        if (mode == 0) EmbeddedPanel(vm)
        if (mode != 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                vm.bridgeStatus ?: "Статус не проверен",
                style = MaterialTheme.typography.bodySmall,
                color = if (vm.bridgeStatus?.startsWith("Подключено") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.checkBridge() }) { Text("Проверить") }
        }
        if (mode == 1) {
            Step("1", "Установите Termux из F-Droid (версия из Google Play устарела).")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openUrl(ctx, "https://f-droid.org/packages/com.termux/") }) { Text("Скачать Termux") }
            }
            Step("2", "Скопируйте команду установки, откройте Termux, вставьте её и нажмите Enter. Установка займёт 5–10 минут.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    clip.setText(androidx.compose.ui.text.AnnotatedString(com.ultracode.app.ai.ClaudeBridge.termuxInstallCommand(vm.bridgeScript(), cc.apiKey)))
                    vm.toast = "Команда скопирована — вставьте в Termux"
                }) { Text("Скопировать команду") }
                OutlinedButton(onClick = {
                    val i = ctx.packageManager.getLaunchIntentForPackage("com.termux")
                    if (i != null) ctx.startActivity(i) else vm.toast = "Termux не установлен"
                }) { Text("Открыть Termux") }
            }
            Step("3", "В Termux выполните uc-login и войдите в аккаунт Claude (откроется страница claude.ai — скопируйте код и вставьте в Termux).")
            Step("4", "Выполните uc-bridge — мост запущен. Не закрывайте Termux (можно свернуть).")
            Step("5", "В UltraCode выберите модель «Claude Code (подписка)» и пишите как обычно.")
        } else if (mode == 2) {
            Step("1", "На компьютере нужен Node.js (nodejs.org). Скопируйте команду и выполните её в терминале (macOS, Linux, WSL или Git Bash).")
            Button(onClick = {
                clip.setText(androidx.compose.ui.text.AnnotatedString(com.ultracode.app.ai.ClaudeBridge.pcInstallCommand(vm.bridgeScript(), cc.apiKey)))
                vm.toast = "Команда скопирована — отправьте её себе на компьютер"
            }) { Text("Скопировать команду для ПК") }
            Step("2", "Выполните claude и войдите в аккаунт, затем запустите мост командой из подсказки. Он покажет адрес вида http://192.168.x.x:8765.")
            Step("3", "Впишите этот адрес сюда (телефон и компьютер должны быть в одной сети Wi-Fi).")
            OutlinedTextField(
                value = cc.baseUrl, onValueChange = { vm.updateProvider(cc.copy(baseUrl = it.trim())) },
                label = { Text("Адрес моста") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
            )
        }
        OutlinedTextField(
            value = s.ccFolder, onValueChange = { v -> vm.updateSettings { it.copy(ccFolder = v) } },
            label = { Text("Папка проекта (в ~/projects)") }, singleLine = true,
            supportingText = { Text("Если к чату подключён репозиторий — используется папка с его именем") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        )
        SwitchRow("Разрешить команды терминала", "Claude Code сможет запускать команды (сборка, git, npm)", s.ccAllowBash) { v ->
            vm.updateSettings { it.copy(ccAllowBash = v) }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
        CookieBadge(size = 24.dp, container = MaterialTheme.colorScheme.secondaryContainer) {
            Text(n, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmbeddedPanel(vm: AppViewModel) {
    val ctx = LocalContext.current
    if (!vm.embedded.supported) {
        Text("На этом телефоне встроенный режим недоступен (нужен 64-битный процессор arm64). Используйте Termux или ПК.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        return
    }
    val appVer = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    Text(
        "Официальный Claude Code (около 365 МБ) скачивается прямо в UltraCode и работает внутри приложения — без Termux и компьютера. Версия UltraCode: $appVer",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (vm.ccLoggedIn) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text("Подключён", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.logoutEmbedded() }) { Text("Отключить") }
            }
        }
    }
    if (vm.ccInstalled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Claude Code ${vm.ccVersion ?: ""}".trim(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.removeEmbedded() }, enabled = !vm.ccBusy) { Text("Удалить") }
        }
    }
    vm.ccProgress?.let { text ->
        if (vm.ccBusy) {
            val v = vm.ccProgressValue
            if (v != null) androidx.compose.material3.LinearProgressIndicator(progress = { v }, modifier = Modifier.fillMaxWidth())
            else androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = if (text.startsWith("Ошибка")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.installEmbedded(uri)
    }
    if (!vm.ccInstalled) {
        Button(onClick = { vm.installEmbedded() }, enabled = !vm.ccBusy) {
            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
            Text(if (vm.ccBusy) "Устанавливаю…" else "Скачать Claude Code")
        }
        Text("Если загрузка не идёт:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.browserDownloadEmbedded { url -> openUrl(ctx, url) } }, enabled = !vm.ccBusy) {
                Text("Скачать через браузер")
            }
            OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !vm.ccBusy) {
                Text("Выбрать файл")
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!vm.ccInstalled) {
        } else if (!vm.ccLoggedIn) {
            Button(onClick = { vm.loginEmbedded { url -> openUrl(ctx, url) } }, enabled = !vm.ccBusy) {
                Icon(Icons.AutoMirrored.Outlined.Login, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                Text("Войти в Claude")
            }
        }
    }
    if (vm.ccLoggedIn) Text("Выберите модель «Claude Code (подписка)» вверху чата и пишите как обычно.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    vm.ccLoginStatus?.let { status ->
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(32.dp),
            title = { Text("Вход в Claude") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    vm.ccLoginUrl?.let { url ->
                        TextButton(onClick = { openUrl(ctx, url) }) { Text("Открыть страницу ещё раз") }
                        OutlinedTextField(
                            value = code, onValueChange = { code = it }, label = { Text("Код") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        )
                    }
                    if (vm.ccBusy && vm.ccLoginUrl == null) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = vm.ccLoginUrl != null && code.isNotBlank() && vm.ccBusy, onClick = { vm.submitLoginCode(code) }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { vm.closeLogin() }) { Text(if (vm.ccBusy) "Отмена" else "Закрыть") } },
        )
    }
}
