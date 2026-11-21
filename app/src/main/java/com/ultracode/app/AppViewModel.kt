package com.ultracode.app

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultracode.app.agent.FileWorkspace
import com.ultracode.app.agent.Prompts
import com.ultracode.app.agent.SafWorkspace
import com.ultracode.app.agent.ToolBox
import com.ultracode.app.agent.Workspace
import com.ultracode.app.ai.LlmClient
import com.ultracode.app.ai.Part
import com.ultracode.app.ai.ToolResult
import com.ultracode.app.ai.WireMessage
import com.ultracode.app.data.AppSettings
import com.ultracode.app.data.Chat
import com.ultracode.app.data.ChatMessage
import com.ultracode.app.data.Defaults
import com.ultracode.app.data.ProviderConfig
import com.ultracode.app.data.Storage
import com.ultracode.app.github.GitHub
import com.ultracode.app.github.RepoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

class PendingQuestion(val question: String, val options: List<String>, val answer: CompletableDeferred<String>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val storage = Storage(app)

    var settings by mutableStateOf(storage.loadSettings()); private set
    val chats = mutableStateListOf<Chat>()
    var currentId by mutableStateOf<String?>(null); private set
    val current: Chat? get() = chats.firstOrNull { it.id == currentId }

    var running by mutableStateOf(false); private set
    var streamingText by mutableStateOf(""); private set
    val liveTools = mutableStateListOf<String>()
    val liveSources = mutableStateListOf<com.ultracode.app.data.WebSource>()
    var pending by mutableStateOf<PendingQuestion?>(null); private set
    val attachments = mutableStateListOf<String>()
    var toast by mutableStateOf<String?>(null)
    var loadingModels by mutableStateOf(false); private set
    var appVisible = true

    var repos by mutableStateOf<List<RepoInfo>>(emptyList()); private set
    var reposLoading by mutableStateOf(false); private set

    private var job: Job? = null

    init {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { storage.loadChats() }
            chats.addAll(list)
        }
    }

    // ------------------------------------------------------------ settings

    fun updateSettings(f: (AppSettings) -> AppSettings) {
        settings = f(settings)
        storage.saveSettings(settings)
    }

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

    fun selectModel(providerId: String, model: String) =
        updateSettings { it.copy(selectedProvider = providerId, selectedModel = model) }

    fun refreshModels(p: ProviderConfig) {
        viewModelScope.launch {
            loadingModels = true
            try {
                val models = LlmClient.listModels(p)
                if (models.isEmpty()) toast = "Список моделей пуст"
                else { updateProvider(p.copy(models = models)); toast = "Загружено моделей: ${models.size}" }
            } catch (e: Exception) {
                toast = "Не удалось загрузить модели: ${e.message}"
            } finally {
                loadingModels = false
            }
        }
    }

    fun setWorkspace(uri: Uri?) {
        if (uri == null) { updateSettings { it.copy(workspaceUri = null, workspaceName = null) }; return }
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, uri)?.name
        updateSettings { it.copy(workspaceUri = uri.toString(), workspaceName = name) }
        toast = "Рабочая папка: ${name ?: uri}"
    }

    fun workspace(): Workspace = settings.workspaceUri?.let { u ->
        runCatching { SafWorkspace(ctx, Uri.parse(u), settings.workspaceName) }.getOrNull()
    } ?: FileWorkspace(File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "projects"))

    // ------------------------------------------------------------ GitHub

    fun connectGitHub(token: String) {
        viewModelScope.launch {
            try {
                val login = GitHub(token).me()
                updateSettings { it.copy(githubToken = token.trim(), githubLogin = login) }
                toast = "GitHub подключён: $login"
                loadRepos()
            } catch (e: Exception) {
                toast = "Не удалось подключить GitHub: ${e.message}"
            }
        }
    }

    fun disconnectGitHub() {
        updateSettings { it.copy(githubToken = "", githubLogin = "") }
        repos = emptyList()
    }

    fun loadRepos() {
        if (settings.githubToken.isBlank()) return
        viewModelScope.launch {
            reposLoading = true
            try { repos = GitHub(settings.githubToken).repos() }
            catch (e: Exception) { toast = e.message }
            finally { reposLoading = false }
        }
    }

    fun createRepo(name: String, private: Boolean) {
        viewModelScope.launch {
            try {
                val r = GitHub(settings.githubToken).createRepo(name, private, "Создано в UltraCode")
                toast = "Создан ${r.fullName}"
                attachRepo(r.fullName)
                loadRepos()
            } catch (e: Exception) { toast = e.message }
        }
    }

    fun attachRepo(fullName: String?) {
        val c = current ?: newChat()
        save(c.copy(repo = fullName))
        if (fullName != null) toast = "Репозиторий $fullName подключён к чату"
    }

    // ------------------------------------------------------------ chats

    fun newChat(): Chat {
        current?.takeIf { it.messages.isEmpty() }?.let { return it }
        val c = Chat(agent = current?.agent ?: true)
        chats.add(0, c)
        currentId = c.id
        return c
    }

    fun openChat(id: String) { if (!running) currentId = id }

    fun deleteChat(c: Chat) {
        if (running && c.id == currentId) stop()
        chats.removeAll { it.id == c.id }
        if (currentId == c.id) currentId = null
        viewModelScope.launch(Dispatchers.IO) { storage.deleteChat(c) }
    }

    fun toggleAgent() {
        val c = current ?: newChat()
        save(c.copy(agent = !c.agent))
    }

    private fun save(c: Chat) {
        val i = chats.indexOfFirst { it.id == c.id }
        if (i >= 0) chats[i] = c else chats.add(0, c)
        viewModelScope.launch(Dispatchers.IO) { storage.saveChat(c) }
    }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) { compressImage(uri) }
                attachments += path
            } catch (e: Exception) {
                toast = "Не удалось загрузить фото: ${e.message}"
            }
        }
    }

    private fun compressImage(uri: Uri): String {
        val cr = ctx.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / sample > 3000) sample *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("не удалось прочитать изображение")
        val scale = 1568f / max(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val f = File(storage.imagesDir, UUID.randomUUID().toString() + ".jpg")
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return f.absolutePath
    }

    // ------------------------------------------------------------ sending

    fun send(text: String) {
        if (running) return
        if (text.isBlank() && attachments.isEmpty()) return
        val p = settings.provider() ?: run { toast = "Выберите модель"; return }
        if (p.apiKey.isBlank() && !p.keyOptional) {
            toast = "Добавьте ключ для «${p.name}» в настройках"
            return
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

    /** Re-asks the last user message (optionally with another model). */
    fun regenerate() {
        if (running) return
        val c = current ?: return
        val idx = c.messages.indexOfLast { it.role == "user" }
        if (idx < 0) return
        val p = settings.provider() ?: return
        save(c.copy(messages = c.messages.subList(0, idx + 1)))
        startTurn(c.id, p, settings.selectedModel)
    }

    private fun startTurn(chatId: String, p: ProviderConfig, model: String) {
        job = viewModelScope.launch {
            running = true
            streamingText = ""
            liveTools.clear()
            liveSources.clear()
            val sb = StringBuilder()
            var failed = false
            try {
                val chat = chats.first { it.id == chatId }
                val convo = withContext(Dispatchers.IO) { history(chat) }.toMutableList()
                val ws = workspace()
                val gh = settings.githubToken.takeIf { it.isNotBlank() }?.let { GitHub(it) }
                val web = if (settings.webAccess) com.ultracode.app.agent.WebTools { src ->
                    viewModelScope.launch { if (liveSources.none { it.url == src.url }) liveSources += src }
                } else null
                val box = ToolBox(ws, gh, chat.repo, ::ask, web)
                var tools = if (chat.agent) box.specs else box.webSpecs
                val system = buildString {
                    append(if (chat.agent) Prompts.agent else Prompts.chat)
                    if (chat.agent) {
                        append("\n\nРабочая папка: ${ws.label}.")
                        append(if (gh != null) " GitHub подключён (пользователь ${settings.githubLogin})." else " GitHub не подключён.")
                        chat.repo?.let { append(" Подключённый к чату репозиторий: $it.") }
                    }
                    if (settings.systemPrompt.isNotBlank()) append("\n\nИнструкции пользователя:\n").append(settings.systemPrompt)
                }
                var steps = 0
                while (true) {
                    if (sb.isNotEmpty() && !sb.endsWith("\n\n")) { sb.append("\n\n"); streamingText = sb.toString() }
                    val onDelta: (String) -> Unit = { d -> sb.append(d); streamingText = sb.toString() }
                    val res = try {
                        LlmClient.complete(p, model, system, convo, tools, onDelta)
                    } catch (e: com.ultracode.app.ai.ApiException) {
                        // Model without tool support: answer without tools.
                        if (tools.isNotEmpty() && sb.isEmpty() && (e.message ?: "").contains("tool", ignoreCase = true)) {
                            tools = emptyList()
                            if (chat.agent) toast = "Модель $model не поддерживает инструменты — агент и интернет выключены"
                            LlmClient.complete(p, model, system, convo, tools, onDelta)
                        } else throw e
                    }
                    convo += WireMessage("assistant", listOf(Part.Text(res.text)), toolCalls = res.toolCalls)
                    if (res.toolCalls.isEmpty() || tools.isEmpty()) break
                    if (++steps > settings.maxSteps) {
                        sb.append("\n\n⏸️ Достигнут лимит шагов (${settings.maxSteps}). Напишите «продолжай», чтобы агент продолжил.")
                        break
                    }
                    val results = res.toolCalls.map { c ->
                        liveTools += box.label(c.name, c.arguments)
                        ToolResult(c.id, c.name, box.run(c.name, c.arguments))
                    }
                    convo += WireMessage("user", toolResults = results)
                }
            } catch (e: CancellationException) {
                sb.append("\n\n⏹️ Остановлено")
                throw e
            } catch (e: Exception) {
                failed = true
                sb.append("\n\n⚠️ Ошибка: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                withContext(NonCancellable) {
                    val c = chats.firstOrNull { it.id == chatId }
                    if (c != null) {
                        val m = ChatMessage(
                            role = "assistant", text = sb.toString().trim().ifEmpty { "(пустой ответ)" },
                            model = model, provider = p.name, tools = liveTools.toList(), sources = liveSources.toList(), error = failed,
                        )
                        save(c.copy(messages = c.messages + m, updated = System.currentTimeMillis()))
                        if (!appVisible && !failed) Notifier.done(ctx, model, m.text)
                    }
                    running = false
                    streamingText = ""
                    liveTools.clear()
                    liveSources.clear()
                }
            }
        }
    }

    private fun history(chat: Chat): List<WireMessage> {
        val out = mutableListOf<WireMessage>()
        for (m in chat.messages) {
            if (m.error && m.role == "assistant") continue
            val parts = mutableListOf<Part>()
            m.images.forEach { path ->
                runCatching { File(path).readBytes() }.getOrNull()?.let {
                    parts += Part.Image(Base64.encodeToString(it, Base64.NO_WRAP))
                }
            }
            var text = m.text
            if (m.role == "assistant" && m.tools.isNotEmpty()) text = "[Выполненные действия: ${m.tools.joinToString("; ")}]\n" + text
            parts += Part.Text(text)
            val last = out.lastOrNull()
            if (last != null && last.role == m.role) out[out.size - 1] = last.copy(parts = last.parts + parts)
            else out += WireMessage(m.role, parts)
        }
        while (out.firstOrNull()?.role == "assistant") out.removeAt(0)
        return out
    }

    private suspend fun ask(question: String, options: List<String>): String {
        val d = CompletableDeferred<String>()
        pending = PendingQuestion(question, options, d)
        if (settings.notifyQuestions) Notifier.question(ctx, question)
        return try { d.await() } finally {
            pending = null
            Notifier.cancelQuestion(ctx)
        }
    }

    fun answer(text: String) { pending?.answer?.complete(text) }

    fun stop() {
        job?.cancel()
    }
}
