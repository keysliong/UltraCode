package com.ultracode.desktop

import com.ultracode.app.ai.ApiException
import com.ultracode.app.ai.ClaudeBridge
import com.ultracode.app.ai.ClaudeStreamParser
import com.ultracode.app.data.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Official Claude Code CLI installed on this computer (signed in with your own Claude account).
 * UltraCode only sends tasks to it and shows the stream — it never touches your login.
 */
object LocalClaude {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val isMac = System.getProperty("os.name").lowercase().contains("mac")

    /** Finds the `claude` executable: PATH, then the official installer's ~/.local/bin. */
    fun findClaude(): List<String>? {
        val names = if (isWindows) listOf("claude.exe", "claude.cmd", "claude.bat") else listOf("claude")
        val dirs = (System.getenv("PATH") ?: "").split(File.pathSeparator).filter { it.isNotBlank() }.map { File(it) } +
            listOf(File(System.getProperty("user.home"), ".local/bin"), File(System.getProperty("user.home"), ".claude/local"),
                File("/usr/local/bin"), File("/opt/homebrew/bin"))
        for (d in dirs) for (n in names) {
            val f = File(d, n)
            if (f.isFile && f.canExecute() || (isWindows && f.isFile)) {
                return if (f.name.endsWith(".cmd") || f.name.endsWith(".bat")) listOf("cmd", "/c", f.absolutePath) else listOf(f.absolutePath)
            }
        }
        return null
    }

    suspend fun version(): String? = withContext(Dispatchers.IO) {
        val cmd = findClaude() ?: return@withContext null
        runCatching {
            val p = ProcessBuilder(cmd + "--version").redirectErrorStream(true).start()
            p.outputStream.close()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(60, TimeUnit.SECONDS)) { p.destroyForcibly(); null }
            else out.lines().firstOrNull { it.contains("Claude Code") }?.trim() ?: out.lines().firstOrNull { it.isNotBlank() }?.trim()
        }.getOrNull()
    }

    data class Auth(val loggedIn: Boolean, val method: String) {
        /** Signed in with an API key / Console account: billed per request, not the Pro/Max subscription. */
        val apiBilling: Boolean get() = method.contains("api", true) || method.contains("console", true)
    }

    /** `claude auth status --json` (official command). */
    suspend fun authStatus(): Auth? = withContext(Dispatchers.IO) {
        val cmd = findClaude() ?: return@withContext null
        runCatching {
            val p = ProcessBuilder(cmd + listOf("auth", "status", "--json")).redirectErrorStream(true).start()
            p.outputStream.close()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(60, TimeUnit.SECONDS)) { p.destroyForcibly(); return@runCatching null }
            val o = com.ultracode.app.data.JsonConfig.json.parseToJsonElement(out.substring(out.indexOf('{'))).let { it as kotlinx.serialization.json.JsonObject }
            Auth(
                loggedIn = (o["loggedIn"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true",
                method = (o["authMethod"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "",
            )
        }.getOrNull()
    }

    /** Opens a terminal window running [command] (for the official installer and sign-in). */
    fun openTerminal(command: String) {
        runCatching {
            when {
                isWindows -> ProcessBuilder("cmd", "/c", "start", "UltraCode", "powershell", "-NoExit", "-Command", command).start()
                isMac -> ProcessBuilder("osascript", "-e", "tell application \"Terminal\" to do script \"${command.replace("\"", "\\\"")}\"",
                    "-e", "tell application \"Terminal\" to activate").start()
                else -> {
                    val terms = listOf(
                        listOf("x-terminal-emulator", "-e", "bash", "-lc", "$command; exec bash"),
                        listOf("gnome-terminal", "--", "bash", "-lc", "$command; exec bash"),
                        listOf("konsole", "-e", "bash", "-lc", "$command; exec bash"),
                        listOf("xfce4-terminal", "-e", "bash -lc '$command; exec bash'"),
                        listOf("xterm", "-e", "bash", "-lc", "$command; exec bash"),
                    )
                    terms.firstNotNullOfOrNull { t -> runCatching { ProcessBuilder(t).start() }.getOrNull() }
                        ?: throw IllegalStateException("Не найден терминал")
                }
            }
        }.getOrElse { throw ApiException(0, "Не удалось открыть терминал: ${it.message}. Выполните вручную: $command") }
    }

    val installCommand: String
        get() = if (isWindows) "irm https://claude.ai/install.ps1 | iex" else "curl -fsSL https://claude.ai/install.sh | bash"

    suspend fun run(
        projectDir: File, prompt: String, session: String?, model: String, system: String, allowBash: Boolean,
        images: List<File>,
        onText: (String) -> Unit, onTool: (String, WebSource?) -> Unit, onSession: (String) -> Unit,
    ): ClaudeBridge.Result = withContext(Dispatchers.IO) {
        val claude = findClaude() ?: throw ApiException(0,
            "Claude Code не найден на компьютере. Настройки → Claude Code → «Установить», затем «Войти в Claude».")
        projectDir.mkdirs()
        val imgPaths = images.mapIndexed { i, f ->
            val dir = File(projectDir, ".ultracode-images").apply { mkdirs() }
            val dst = File(dir, "img_${System.currentTimeMillis()}_$i.${f.extension.ifBlank { "jpg" }}")
            f.copyTo(dst, overwrite = true)
            dst.absolutePath
        }
        // Through Windows cmd, characters like < > " in arguments break: send the instructions inside the message then.
        val viaCmd = claude.firstOrNull() == "cmd"
        val fullPrompt = (if (viaCmd && system.isNotBlank()) "Инструкции для работы:\n$system\n\n---\n\n" else "") +
            ClaudeBridge.promptWithImages(prompt, imgPaths)
        val tools = mutableListOf("Read", "Edit", "Write", "MultiEdit", "Glob", "Grep", "LS", "TodoWrite", "WebFetch", "WebSearch", "NotebookEdit")
        if (allowBash) tools += "Bash"
        val args = mutableListOf("-p", "--output-format", "stream-json", "--verbose", "--include-partial-messages", "--model", model)
        if (!session.isNullOrBlank()) args += listOf("--resume", session)
        if (system.isNotBlank() && !viaCmd) args += listOf("--append-system-prompt", system)
        args += listOf("--permission-mode", "acceptEdits", "--allowedTools") + tools
        val pb = ProcessBuilder(claude + args).directory(projectDir)
        pb.environment()["DISABLE_AUTOUPDATER"] = "1"
        val p = pb.start()
        val parser = ClaudeStreamParser(session, onText, onTool, onSession)
        val stderr = StringBuilder()
        val errThread = Thread {
            runCatching { p.errorStream.bufferedReader().forEachLine { synchronized(stderr) { stderr.appendLine(it); if (stderr.length > 8000) stderr.delete(0, 4000) } } }
        }.apply { start() }
        coroutineScope {
            val watcher = launch { try { awaitCancellation() } finally { p.destroy() } }
            try {
                p.outputStream.use { it.write(fullPrompt.toByteArray()) }
                p.inputStream.bufferedReader().forEachLine { parser.line(it) }
                p.waitFor()
            } finally {
                watcher.cancel()
            }
        }
        errThread.join(2000)
        if (p.exitValue() != 0 && parser.error == null && !parser.streamed && parser.finalText.isNullOrBlank()) {
            throw ApiException(0, "Claude Code завершился с ошибкой (код ${p.exitValue()}):\n" + synchronized(stderr) { stderr.toString().takeLast(1200) })
        }
        parser.failIfError("Войдите в аккаунт Claude: Настройки → Claude Code → «Войти в Claude».")
        parser.result()
    }
}
