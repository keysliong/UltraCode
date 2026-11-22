package com.ultracode.app.term

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.InputStreamReader

/**
 * A simple terminal over a shell process (pipes): shows the output, sends typed commands.
 * `python` without arguments is started as an interactive interpreter (python -i -u), so `>>>` works.
 */
class TerminalSession(
    private val start: () -> Process,
    /** How to stop the shell and everything it started (desktop also kills child processes). */
    private val kill: (Process) -> Unit = { it.destroyForcibly() },
) {
    val lines = mutableStateListOf<String>()
    var partial by mutableStateOf("")
        private set
    var alive by mutableStateOf(false)
        private set
    val history = mutableListOf<String>()
    private var proc: Process? = null

    fun open() {
        if (alive) return
        val p = runCatching { start() }.getOrElse { lines += "Не удалось запустить терминал: ${it.message}"; return }
        proc = p
        alive = true
        Thread { read(p) }.apply { isDaemon = true }.start()
    }

    private fun read(p: Process) {
        val r = InputStreamReader(p.inputStream, Charsets.UTF_8)
        val sb = StringBuilder()
        var cr = false
        var esc = 0 // 0 = text, 1 = after ESC, 2 = inside CSI, 3 = inside OSC
        val buf = CharArray(4096)
        try {
            while (true) {
                val n = r.read(buf)
                if (n < 0) break
                for (i in 0 until n) {
                    val c = buf[i]
                    when (esc) {
                        1 -> { esc = when (c) { '[' -> 2; ']' -> 3; else -> 0 }; continue }
                        2 -> { if (c in '@'..'~') esc = 0; continue }
                        3 -> { if (c == '\u0007') esc = 0; continue }
                    }
                    when {
                        c == '\u001B' -> esc = 1
                        c == '\n' -> { commit(sb.toString()); sb.setLength(0); cr = false }
                        c == '\r' -> cr = true
                        c == '\b' -> if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
                        else -> { if (cr) { sb.setLength(0); cr = false }; if (c >= ' ' || c == '\t') sb.append(c) }
                    }
                }
                partial = sb.toString()
            }
        } catch (_: Exception) {
        }
        if (sb.isNotEmpty()) commit(sb.toString())
        partial = ""
        alive = false
        lines += "[терминал закрыт — нажмите «Перезапустить»]"
    }

    private fun commit(line: String) {
        lines += line
        if (lines.size > 4000) { val keep = lines.takeLast(3000); lines.clear(); lines.addAll(keep) }
    }

    /** Sends a command line. Handles `clear`/`cls` locally. */
    fun send(input: String) {
        val cmd = input.trimEnd()
        if (cmd.isNotBlank() && history.lastOrNull() != cmd) history += cmd
        if (cmd == "clear" || cmd == "cls") { clear(); return }
        if (!alive) open()
        lines += "› $cmd"
        val line = if (Regex("^\\s*(python3?|py)(\\s+-3)?\\s*$").matches(cmd)) "$cmd -i -u" else cmd
        runCatching {
            proc?.outputStream?.let { it.write((line + "\n").toByteArray(Charsets.UTF_8)); it.flush() }
        }.onFailure { lines += "Ошибка ввода: ${it.message}" }
    }

    /** Stops whatever runs (kills the shell) and starts a fresh one. */
    fun restart() {
        stop()
        open()
    }

    fun stop() {
        proc?.let { p -> runCatching { kill(p) } }
        proc = null
        alive = false
    }

    fun clear() { lines.clear(); partial = "" }
}
