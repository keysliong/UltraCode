package com.ultracode.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Runs Python scripts with the Python installed on this computer. */
object PythonRunner {
    private fun works(cmd: List<String>) = runCatching {
        val p = ProcessBuilder(cmd + "--version").redirectErrorStream(true).start()
        p.outputStream.close()
        p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)

    /** python / python3 / py -3, whichever exists. */
    val command: List<String>? by lazy {
        val candidates = if (LocalClaude.isWindows) listOf(listOf("py", "-3"), listOf("python"), listOf("python3"))
        else listOf(listOf("python3"), listOf("python"))
        candidates.firstOrNull { works(it) }
    }

    const val NOT_INSTALLED = "Python не установлен на компьютере. Установите его с python.org (при установке отметьте «Add python.exe to PATH») и перезапустите UltraCode."

    /** Returns the combined stdout/stderr (max 20 000 chars) and the exit code. */
    suspend fun run(code: String, workDir: File, timeoutSec: Long = 120): Pair<Int, String> = withContext(Dispatchers.IO) {
        val py = command ?: return@withContext -1 to NOT_INSTALLED
        val dir = File(workDir, ".ultracode-run").apply { mkdirs() }
        val script = File(dir, "script_${System.currentTimeMillis()}.py").apply { writeText(code) }
        try {
            val pb = ProcessBuilder(py + listOf("-X", "utf8", script.absolutePath)).directory(workDir).redirectErrorStream(true)
            pb.environment()["PYTHONIOENCODING"] = "utf-8"
            pb.environment()["PYTHONUNBUFFERED"] = "1"
            val p = pb.start()
            p.outputStream.close()
            val out = StringBuilder()
            val reader = Thread { runCatching { p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { synchronized(out) { if (out.length < 20_000) out.appendLine(it) } } } }
            reader.start()
            val done = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!done) p.destroyForcibly()
            reader.join(2000)
            val text = synchronized(out) { out.toString() }.ifBlank { "(скрипт ничего не вывел)" }
            if (done) p.exitValue() to text else -2 to "$text\n⏹ Остановлено: скрипт работал дольше $timeoutSec с"
        } finally {
            script.delete()
        }
    }
}
