package com.ultracode.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ultracode.app.agent.FileWorkspace
import com.ultracode.app.agent.Prompts
import com.ultracode.app.agent.ToolBox
import com.ultracode.app.agent.WebTools
import com.ultracode.app.ai.ApiException
import com.ultracode.app.ai.LlmClient
import com.ultracode.app.ai.Part
import com.ultracode.app.ai.ToolResult
import com.ultracode.app.ai.ToolSpec
import com.ultracode.app.ai.TurnResult
import com.ultracode.app.ai.WireMessage
import com.ultracode.app.data.AppSettings
import com.ultracode.app.data.Chat
import com.ultracode.app.data.ChatMessage
import com.ultracode.app.data.Defaults
import com.ultracode.app.data.ProviderConfig
import com.ultracode.app.data.ProviderKind
import com.ultracode.app.data.WebSource
import com.ultracode.app.github.GitHub
import com.ultracode.app.github.GitHubAuth
import com.ultracode.app.github.RepoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.max

class PendingQuestion(val question: String, val options: List<String>, val answer: CompletableDeferred<String>)

/** All app state for the desktop version (same features as the phone ViewModel). */
class DesktopState {
    val store = DesktopStore()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var settings by mutableStateOf(store.loadSettings()); private set
    val chats = mutableStateListOf<Chat>().apply { addAll(store.loadChats()) }
    var currentId by mutableStateOf<String?>(null); private set
    val current: Chat? get() = chats.firstOrNull { it.id == currentId }
    var screen by mutableStateOf("chat")

    var running by mutableStateOf(false); private set
    var streamingText by mutableStateOf(""); private set
    var statusText by mutableStateOf<String?>(null); private set
    var usedModel by mutableStateOf(""); private set
    val liveTools = mutableStateListOf<String>()
    val liveSources = mutableStateListOf<WebSource>()
    var pending by mutableStateOf<PendingQuestion?>(null); private set
    val attachments = mutableStateListOf<String>()
    var draft by mutableStateOf("")
    var toast by mutableStateOf<String?>(null)
    var loadingModels by mutableStateOf(false); private set
    var onQuestion: (String) -> Unit = {}

    var repos by mutableStateOf<List<RepoInfo>>(emptyList()); private set
    var reposLoading by mutableStateOf(false); private set
    var githubDevice by mutableStateOf<GitHubAuth.DeviceCode?>(null); private set
    var claudeVersion by mutableStateOf<String?>(null); private set
    var claudeAuth by mutableStateOf<LocalClaude.Auth?>(null); private set

    private var job: Job? = null
    private var ghJob: Job? = null

    init {
        verifyFreeModels(silent = true)
        settings.provider(Defaults.GROQ)?.takeIf { it.apiKey.isNotBlank() }?.let { refreshModels(it, true) }
        settings.provider(Defaults.DEEPSEEK)?.takeIf { it.apiKey.isNotBlank() }?.let { refreshModels(it, true) }
        checkClaude()
    }

    // ------------------------------------------------------------ settings & models

    fun updateSettings(f: (AppSettings) -> AppSettings) { settings = f(settings); store.saveSettings(settings) }

    fun updateProvider(p: ProviderConfig) = updateSettings { s ->
        val list = s.providers.toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        s.copy(providers = list)
    }

    fun removeProvider(id: String) = updateSettings { s ->
        val list = s.providers.filterNot { it.id == id && !it.builtin }
        if (s.selectedProvider == id) s.copy(providers = list, selectedProvider = Defaults.OPENROUTER, selectedModel = Defaults.freeModels.first())
        else s.copy(providers = list)
    }

    fun selectModel(providerId: String, model: String) = updateSettings { it.copy(selectedProvider = providerId, selectedModel = model) }

    fun addModel(providerId: String, model: String) {
        val m = model.trim().removeSurrounding("\"")
        if (DesktopStore.looksLikeKey(m)) { toast = "Это ключ, а не модель. Вставьте его в поле «Ключ»."; return }
        if (!DesktopStore.validModelId(m)) { toast = "Впишите ID модели латиницей без пробелов, например llama-3.3-70b-versatile"; return }
        if (m in settings.hiddenModels) updateSettings { it.copy(hiddenModels = it.hiddenModels - m) }
        val p = settings.provider(providerId) ?: return
        if (m !in p.models) updateProvider(p.copy(models = listOf(m) + p.models))
        selectModel(providerId, m)
        toast = "Модель ${short(m)} добавлена"
    }

    fun hideModel(providerId: String, model: String) {
        updateSettings { it.copy(hiddenModels = it.hiddenModels + model) }
        val p = settings.provider(providerId) ?: return
        val rest = p.models - model
        updateProvider(p.copy(models = rest, toolModels = p.toolModels - model))
        if (settings.selectedModel == model) rest.firstOrNull()?.let { selectModel(providerId, it) }
        toast = "Модель ${short(model)} убрана из списка"
    }

    fun short(m: String) = m.substringAfter('/').removeSuffix(":free")

    fun verifyFreeModels(silent: Boolean = false) {
        scope.launch {
            loadingModels = true
            try {
                val free = LlmClient.freeModels()
                val p = settings.provider(Defaults.OPENROUTER) ?: return@launch
                val ids = free.map { it.id }.filter { it !in settings.hiddenModels }
                if (ids.isEmpty()) return@launch
                val custom = p.models.filter { !it.endsWith(":free") }
                updateProvider(p.copy(models = custom + ids, toolModels = free.filter { it.tools }.map { it.id }, verifiedAt = System.currentTimeMillis()))
                if (settings.selectedProvider == Defaults.OPENROUTER && settings.selectedModel !in ids + custom) {
                    selectModel(Defaults.OPENROUTER, preferred.firstOrNull { it in ids } ?: ids.first())
                }
                if (!silent) toast = "Бесплатных моделей: ${ids.size}"
            } catch (e: Exception) {
                if (!silent) toast = "Не удалось проверить модели: ${e.message}"
            } finally { loadingModels = false }
        }
    }

    fun refreshModels(p: ProviderConfig, silent: Boolean = false) {
        if (p.id == Defaults.OPENROUTER) { verifyFreeModels(silent); return }
        scope.launch {
            loadingModels = true
            try {
                val models = LlmClient.listModels(p).filter { it !in settings.hiddenModels }
                if (models.isNotEmpty()) {
                    updateProvider((settings.provider(p.id) ?: p).copy(models = models))
                    if (settings.selectedProvider == p.id && settings.selectedModel !in models) selectModel(p.id, models.first())
                    if (!silent) toast = "Загружено моделей: ${models.size}"
                }
            } catch (e: Exception) {
                if (!silent) toast = "Не удалось загрузить модели: ${e.message}"
            } finally { loadingModels = false }
        }
    }

    private val preferred = listOf(
        "qwen/qwen3-coder:free", "z-ai/glm-4.5-air:free", "openai/gpt-oss-120b:free", "moonshotai/kimi-k2:free",
        "meta-llama/llama-3.3-70b-instruct:free", "deepseek/deepseek-chat-v3.1:free",
    )

    private fun isBusy(m: String) = (settings.busyModels[m] ?: 0L) > System.currentTimeMillis() - 30 * 60_000L
    private fun markBusy(m: String) = updateSettings { it.copy(busyModels = it.busyModels + (m to System.currentTimeMillis())) }

    private fun pickFree(tried: Set<String>, needTools: Boolean): String? {
        val p = settings.provider(Defaults.OPENROUTER) ?: return null
        var free = p.models.filter { it.endsWith(":free") && it !in tried }
        if (needTools && p.toolModels.isNotEmpty()) free = free.filter { it in p.toolModels }.ifEmpty { free }
        val ok = free.filter { !isBusy(it) }.ifEmpty { free }.sortedBy { m -> if (listOf("gemma", "ling").any { m.contains(it) }) 1 else 0 }
        return preferred.firstOrNull { it in ok } ?: ok.firstOrNull()
    }

    // ------------------------------------------------------------ workspace & images

    fun workspaceDir(): File = settings.workspaceUri?.let { File(it) }?.takeIf { it.isDirectory } ?: store.projectsDir

    fun setWorkspace(dir: File?) = updateSettings { it.copy(workspaceUri = dir?.absolutePath, workspaceName = dir?.name) }

    fun addImage(file: File) {
        scope.launch {
            try {
                attachments += withContext(Dispatchers.IO) { compress(file) }
            } catch (e: Exception) { toast = "Не удалось добавить фото: ${e.message}" }
        }
    }

    private fun compress(file: File): String {
        val img = ImageIO.read(file) ?: error("это не изображение")
        val scale = minOf(1f, 1568f / max(img.width, img.height))
        val w = (img.width * scale).toInt().coerceAtLeast(1); val h = (img.height * scale).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(img, 0, 0, w, h, java.awt.Color.WHITE, null); dispose()
        }
        val f = File(store.imagesDir, UUID.randomUUID().toString() + ".jpg")
        ImageIO.write(out, "jpg", f)
        return f.absolutePath
    }

    // ------------------------------------------------------------ GitHub

    fun loginGitHub(open: (String) -> Unit) {
        ghJob?.cancel()
        ghJob = scope.launch {
            try {
                val code = GitHubAuth.start()
                githubDevice = code
                open(code.verificationUri)
                val token = GitHubAuth.waitForToken(code)
                githubDevice = null
                connectGitHub(token)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                githubDevice = null; toast = "Вход в GitHub не удался: ${e.message}"
            }
        }
    }

    fun cancelGitHubLogin() { ghJob?.cancel(); githubDevice = null }

    fun connectGitHub(token: String) {
        scope.launch {
            try {
                val login = GitHub(token).me()
                updateSettings { it.copy(githubToken = token.trim(), githubLogin = login) }
                toast = "GitHub подключён: $login"
                loadRepos()
            } catch (e: Exception) { toast = "Не удалось подключить GitHub: ${e.message}" }
        }
    }

    fun disconnectGitHub() { updateSettings { it.copy(githubToken = "", githubLogin = "") }; repos = emptyList() }

    fun loadRepos() {
        if (settings.githubToken.isBlank()) return
        scope.launch {
            reposLoading = true
            try { repos = GitHub(settings.githubToken).repos() } catch (e: Exception) { toast = e.message } finally { reposLoading = false }
        }
    }

    fun attachRepo(name: String?) {
        val c = current ?: newChat()
        save(c.copy(repo = name))
        if (name != null) toast = "Репозиторий $name подключён к чату"
    }

    // ------------------------------------------------------------ Claude Code (local official CLI)

    fun checkClaude() {
        scope.launch {
            claudeVersion = LocalClaude.version()
            claudeAuth = if (claudeVersion != null) LocalClaude.authStatus() else null
        }
    }

    fun installClaude() = runCatching { LocalClaude.openTerminal(LocalClaude.installCommand) }.onFailure { toast = it.message }

    fun loginClaude() {
        val cmd = LocalClaude.findClaude()?.joinToString(" ") { if (it.contains(' ')) "\"$it\"" else it } ?: "claude"
        runCatching { LocalClaude.openTerminal(cmd) }.onFailure { toast = it.message }
        toast = "В терминале введите /login → «Claude account with subscription», затем нажмите «Проверить»"
    }

    // ------------------------------------------------------------ chats

    fun newChat(): Chat {
        current?.takeIf { it.messages.isEmpty() }?.let { return it }
        val c = Chat(agent = current?.agent ?: true)
        chats.add(0, c); currentId = c.id
        return c
    }

    fun openChat(id: String) { if (!running) { currentId = id; screen = "chat" } }

    fun deleteChat(c: Chat) {
        if (running && c.id == currentId) stop()
        chats.removeAll { it.id == c.id }
        if (currentId == c.id) currentId = null
        scope.launch(Dispatchers.IO) { store.deleteChat(c) }
    }

    fun toggleAgent() { val c = current ?: newChat(); save(c.copy(agent = !c.agent)) }

    private fun save(c: Chat) {
        val i = chats.indexOfFirst { it.id == c.id }
        if (i >= 0) chats[i] = c else chats.add(0, c)
        scope.launch(Dispatchers.IO) { store.saveChat(c) }
    }

    // ------------------------------------------------------------ sending

    fun send(text: String) {
        if (running || (text.isBlank() && attachments.isEmpty())) return
        val p = settings.provider() ?: run { toast = "Выберите модель"; return }
        if (p.apiKey.isBlank() && !p.keyOptional && p.kind != ProviderKind.CLAUDE_CODE) {
            toast = "Добавьте ключ для «${p.name}» в настройках"; return
        }
        var chat = current ?: newChat()
        val msg = ChatMessage(role = "user", text = text.trim(), images = attachments.toList())
        attachments.clear()
        chat = chat.copy(
            messages = chat.messages + msg,
            title = if (chat.messages.isEmpty()) text.trim().lineSequence().first().take(48).ifBlank { "Фото" } else chat.title,
            updated = System.currentTimeMillis(),
        )
        save(chat)
        startTurn(chat.id, p, settings.selectedModel)
    }

    fun regenerate() {
        if (running) return
        val c = current ?: return
        val idx = c.messages.indexOfLast { it.role == "user" }
        if (idx < 0) return
        save(c.copy(messages = c.messages.subList(0, idx + 1)))
        startTurn(c.id, settings.provider() ?: return, settings.selectedModel)
    }

    fun stop() { job?.cancel() }

    fun answer(text: String) { pending?.answer?.complete(text) }

    private suspend fun ask(question: String, options: List<String>): String {
        val d = CompletableDeferred<String>()
        pending = PendingQuestion(question, options, d)
        onQuestion(question)
        return try { d.await() } finally { pending = null }
    }

    private fun startTurn(chatId: String, p: ProviderConfig, model: String) {
        job = scope.launch {
            running = true; streamingText = ""; liveTools.clear(); liveSources.clear()
            val sb = StringBuilder()
            var failed = false
            usedModel = model
            var prov = p
            try {
                val chat = chats.first { it.id == chatId }
                if (p.kind == ProviderKind.CLAUDE_CODE) {
                    try {
                        runClaudeCode(chat, model, sb); return@launch
                    } catch (e: ApiException) {
                        val fb = claudeLimitFallback(e) ?: throw e
                        prov = fb.first; usedModel = fb.second
                        toast = "Лимит Claude закончился — диалог продолжает ${short(fb.second)}"
                        if (sb.isNotEmpty()) sb.append("\n\n")
                    }
                }
                val convo = withContext(Dispatchers.IO) { history(chat) }.toMutableList()
                val ws = FileWorkspace(workspaceDir())
                val gh = settings.githubToken.takeIf { it.isNotBlank() }?.let { GitHub(it) }
                val web = if (settings.webAccess) WebTools { src -> scope.launch { if (liveSources.none { it.url == src.url }) liveSources += src } } else null
                val box = ToolBox(ws, gh, chat.repo, ::ask, web)
                var tools = if (chat.agent) box.specs else box.webSpecs
                val system = buildString {
                    append(if (chat.agent) Prompts.agent else Prompts.chat)
                    append("\n\nПользователь работает на компьютере (${System.getProperty("os.name")}).")
                    if (chat.agent) {
                        append(" Рабочая папка: ${workspaceDir().absolutePath}.")
                        append(if (gh != null) " GitHub подключён (${settings.githubLogin})." else " GitHub не подключён.")
                        chat.repo?.let { append(" Подключённый репозиторий: $it.") }
                    }
                    if (settings.systemPrompt.isNotBlank()) append("\n\nИнструкции пользователя:\n").append(settings.systemPrompt)
                }
                val tried = mutableSetOf<String>()
                var steps = 0
                while (true) {
                    if (sb.isNotEmpty() && !sb.endsWith("\n\n")) { sb.append("\n\n"); streamingText = sb.toString() }
                    val onDelta: (String) -> Unit = { d -> statusText = null; sb.append(d); streamingText = sb.toString() }
                    val res = callModel(prov, system, convo, tools, onDelta, tried) { tools = emptyList() }
                    convo += WireMessage("assistant", listOf(Part.Text(res.text)), toolCalls = res.toolCalls)
                    if (res.toolCalls.isEmpty() || tools.isEmpty()) break
                    if (++steps > settings.maxSteps) { sb.append("\n\nДостигнут лимит шагов (${settings.maxSteps}). Напишите «продолжай»."); break }
                    val results = res.toolCalls.map { c ->
                        liveTools += box.label(c.name, c.arguments)
                        ToolResult(c.id, c.name, box.run(c.name, c.arguments))
                    }
                    convo += WireMessage("user", toolResults = results)
                }
            } catch (e: CancellationException) {
                sb.append("\n\nОстановлено"); throw e
            } catch (e: Exception) {
                failed = true; sb.append("\n\nОшибка: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                withContext(NonCancellable) {
                    chats.firstOrNull { it.id == chatId }?.let { c ->
                        val m = ChatMessage(role = "assistant", text = sb.toString().trim().ifEmpty { "(пустой ответ)" },
                            model = usedModel, provider = prov.name, tools = liveTools.toList(), sources = liveSources.toList(), error = failed)
                        save(c.copy(messages = c.messages + m, updated = System.currentTimeMillis()))
                    }
                    running = false; statusText = null; streamingText = ""; liveTools.clear(); liveSources.clear()
                }
            }
        }
    }

    private suspend fun callModel(
        p: ProviderConfig, system: String, convo: List<WireMessage>, toolsIn: List<ToolSpec>,
        onDelta: (String) -> Unit, tried: MutableSet<String>, onToolsUnsupported: () -> Unit,
    ): TurnResult {
        var tools = toolsIn
        var attempt = 0
        var streamed = false
        while (true) {
            try {
                return LlmClient.complete(p, usedModel, system, convo, tools) { streamed = true; onDelta(it) }
            } catch (e: ApiException) {
                if (streamed) throw e
                val msg = e.message.orEmpty()
                val rate = e.code in setOf(429, 502, 503, 529) || msg.contains("rate", true) || msg.contains("overloaded", true)
                if (!rate && tools.isNotEmpty() && msg.contains("tool", true)) { tools = emptyList(); onToolsUnsupported(); continue }
                val unknown = (e.code == 404 || e.code == 400) && (msg.contains("does not exist", true) ||
                    msg.contains("model_not_found", true) || msg.contains("decommissioned", true) || msg.contains("No endpoints", true))
                if (unknown) {
                    val bad = usedModel
                    val next = settings.provider(p.id)?.models?.firstOrNull { it != bad && it !in tried }
                    hideModel(p.id, bad)
                    if (next == null) throw ApiException(e.code, "У «${p.name}» нет модели «$bad». Выберите другую модель.")
                    tried += bad; usedModel = next; toast = "Модели «$bad» нет — отвечает ${short(next)}"; continue
                }
                if (!rate) throw e
                attempt++
                if (attempt <= 2 && !isBusy(usedModel)) {
                    for (s in (if (attempt == 1) 4 else 10) downTo 1) { statusText = "${short(usedModel)} перегружена, повтор через $s с…"; delay(1000) }
                    continue
                }
                markBusy(usedModel); tried += usedModel
                val next = if (p.id == Defaults.OPENROUTER && settings.autoFallback) pickFree(tried, tools.isNotEmpty()) else null
                if (next == null || tried.size >= 5) throw ApiException(429, "Модель ${short(usedModel)} перегружена. Подождите минуту или выберите другую.")
                toast = "${short(usedModel)} перегружена — отвечает ${short(next)}"
                usedModel = next; attempt = 0
            }
        }
    }

    private fun isClaudeLimit(msg: String) = listOf("usage limit", "limit reached", "rate limit", "5-hour", "weekly limit",
        "out of extra usage", "resets at", "429").any { msg.contains(it, true) }

    private fun claudeLimitFallback(e: Exception): Pair<ProviderConfig, String>? {
        if (!isClaudeLimit(e.message.orEmpty()) || !settings.autoFallback) return null
        settings.provider(Defaults.OPENROUTER)?.takeIf { it.apiKey.isNotBlank() }?.let { p -> pickFree(emptySet(), true)?.let { return p to it } }
        settings.provider(Defaults.GROQ)?.takeIf { it.apiKey.isNotBlank() && it.models.isNotEmpty() }?.let { return it to it.models.first() }
        settings.provider(Defaults.DEEPSEEK)?.takeIf { it.apiKey.isNotBlank() && it.models.isNotEmpty() }?.let { return it to it.models.first() }
        return null
    }

    private suspend fun runClaudeCode(chat: Chat, model: String, sb: StringBuilder) {
        val last = chat.messages.lastOrNull { it.role == "user" }
        val before = chat.messages.dropLast(1)
        val unseen = before.drop(chat.ccSeen.coerceAtMost(before.size)).filter { !it.error }
        val transcript = if (unseen.isEmpty()) "" else
            "Контекст: эта переписка шла с другими нейросетями, продолжи её.\n--- начало ---\n" +
                unseen.joinToString("\n\n") { if (it.role == "user") "Пользователь: ${it.text}" else "ИИ (${it.model}): ${it.text}" }.takeLast(20_000) +
                "\n--- конец ---\n\nНовое сообщение пользователя:\n"
        val prompt = transcript + last?.text.orEmpty().ifBlank { "Что на изображении?" }
        val system = Prompts.design + (chat.repo?.let { "\n\nРепозиторий пользователя: https://github.com/$it" } ?: "") +
            (if (settings.systemPrompt.isNotBlank()) "\n\nИнструкции пользователя:\n${settings.systemPrompt}" else "")
        val dir = File(workspaceDir(), chat.repo?.substringAfter('/') ?: settings.ccFolder)
        var needSep = false
        statusText = "Claude Code запускается…"
        val res = LocalClaude.run(
            dir, prompt, chat.ccSession, model, system, settings.ccAllowBash,
            last?.images.orEmpty().map { File(it) }.filter { it.exists() },
            onText = { d ->
                if (needSep && sb.isNotEmpty() && !sb.endsWith("\n\n")) sb.append("\n\n")
                needSep = false; sb.append(d); statusText = null; streamingText = sb.toString()
            },
            onTool = { label, web ->
                needSep = true
                scope.launch { liveTools += label; if (web != null && liveSources.none { it.url == web.url }) liveSources += web }
            },
            onSession = { id -> scope.launch { chats.firstOrNull { it.id == chat.id }?.let { save(it.copy(ccSession = id)) } } },
        )
        if (res.text.isNotBlank() && sb.isEmpty()) { sb.append(res.text); streamingText = sb.toString() }
        chats.firstOrNull { it.id == chat.id }?.let { save(it.copy(ccSeen = it.messages.size + 1)) }
    }

    private fun history(chat: Chat): List<WireMessage> {
        val out = mutableListOf<WireMessage>()
        for (m in chat.messages) {
            if (m.error && m.role == "assistant") continue
            val parts = mutableListOf<Part>()
            m.images.forEach { path -> runCatching { File(path).readBytes() }.getOrNull()?.let { parts += Part.Image(Base64.getEncoder().encodeToString(it)) } }
            var text = m.text
            if (m.role == "assistant" && m.tools.isNotEmpty()) text = "[Выполненные действия: ${m.tools.joinToString("; ")}]\n" + text
            parts += Part.Text(text)
            val last = out.lastOrNull()
            if (last != null && last.role == m.role) out[out.size - 1] = last.copy(parts = last.parts + parts) else out += WireMessage(m.role, parts)
        }
        while (out.firstOrNull()?.role == "assistant") out.removeAt(0)
        return out
    }
}
