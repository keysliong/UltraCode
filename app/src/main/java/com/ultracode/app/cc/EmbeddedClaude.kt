package com.ultracode.app.cc

import android.content.Context
import android.os.Build
import android.system.Os
import com.ultracode.app.ai.ApiException
import com.ultracode.app.ai.ClaudeBridge
import com.ultracode.app.ai.ClaudeStreamParser
import com.ultracode.app.ai.LlmClient
import com.ultracode.app.ai.str
import com.ultracode.app.data.Storage
import com.ultracode.app.data.WebSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Runs the OFFICIAL Claude Code (linux-arm64-musl build from npm) inside UltraCode,
 * in a small Alpine Linux environment started with proot. No Termux, no computer.
 * You sign in inside Claude Code itself; the token is kept in Claude Code's own folder.
 */
class EmbeddedClaude(private val ctx: Context) {
    private val base = File(ctx.filesDir, "cc")
    val root = File(base, "alpine")
    private val nativeDir = File(ctx.applicationInfo.nativeLibraryDir)
    private val proot = File(nativeDir, "libproot.so")
    private val loader = File(nativeDir, "libproot-loader.so")
    private val claudeBin = File(root, "usr/local/bin/claude")
    private val tokenFile = File(root, "root/.ultracode/token")

    val supported: Boolean get() = Build.SUPPORTED_ABIS.contains("arm64-v8a") && proot.exists()
    val installed: Boolean get() = claudeBin.exists() && File(root, "bin/bash").exists()
    val loggedIn: Boolean get() = tokenFile.exists() && tokenFile.length() > 20

    private fun env(extra: Map<String, String> = emptyMap()): Map<String, String> = mapOf(
        "PROOT_LOADER" to loader.absolutePath,
        "PROOT_TMP_DIR" to File(ctx.cacheDir, "proot").apply { mkdirs() }.absolutePath,
        "LD_LIBRARY_PATH" to nativeDir.absolutePath,
    ) + extra

    /** Command line that runs [cmd] inside Alpine as fake root. */
    private fun prootCmd(cmd: List<String>, cwd: String = "/root", guestEnv: Map<String, String> = emptyMap()): List<String> {
        val fake = File(root, ".fake")
        val binds = mutableListOf(
            "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", "${root.absolutePath}/tmp:/dev/shm",
            "-b", "/proc/self/fd:/dev/fd", "-b", "/proc/self/fd/0:/dev/stdin",
            "-b", "/proc/self/fd/1:/dev/stdout", "-b", "/proc/self/fd/2:/dev/stderr",
            "-b", "/dev/urandom:/dev/random",
        )
        for (n in listOf("stat", "loadavg", "uptime", "vmstat", "version")) {
            val f = File(fake, n)
            if (f.exists()) binds += listOf("-b", "${f.absolutePath}:/proc/$n")
        }
        val genv = mapOf(
            "HOME" to "/root", "USER" to "root", "LANG" to "C.UTF-8", "TERM" to "xterm-256color",
            "PATH" to "/usr/local/bin:/usr/local/sbin:/usr/bin:/usr/sbin:/bin:/sbin",
            "SHELL" to "/bin/bash", "TMPDIR" to "/tmp",
            "DISABLE_AUTOUPDATER" to "1", "USE_BUILTIN_RIPGREP" to "0",
            "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC" to "1",
        ) + guestEnv
        return listOf(proot.absolutePath, "--kill-on-exit", "--link2symlink", "-L", "--sysvipc",
            "--kernel-release=6.2.1-ultracode", "-0", "-r", root.absolutePath) + binds +
            listOf("-w", cwd, "/usr/bin/env", "-i") + genv.map { "${it.key}=${it.value}" } + cmd
    }

    private fun start(cmd: List<String>, cwd: String = "/root", guestEnv: Map<String, String> = emptyMap()): Process {
        val pb = ProcessBuilder(prootCmd(cmd, cwd, guestEnv))
        pb.environment().putAll(env())
        pb.redirectErrorStream(true)
        return pb.start()
    }

    /** Runs a command to completion and returns (exitCode, output). */
    private suspend fun exec(cmd: List<String>, onLine: (String) -> Unit = {}): Pair<Int, String> = withContext(Dispatchers.IO) {
        val p = start(cmd)
        p.outputStream.close()
        val out = StringBuilder()
        p.inputStream.bufferedReader().forEachLine { out.appendLine(it); onLine(it) }
        p.waitFor() to out.toString()
    }

    private fun writeFakeProc() {
        val fake = File(root, ".fake").apply { mkdirs() }
        File(fake, "stat").writeText("cpu  1957 0 2877 93280 262 342 254 87 0 0\ncpu0 31 0 226 12027 82 10 4 9 0 0\nintr 0\nctxt 0\nbtime 0\nprocesses 0\nprocs_running 1\nprocs_blocked 0\n")
        File(fake, "loadavg").writeText("0.12 0.07 0.02 2/165 765\n")
        File(fake, "uptime").writeText("124.08 932.80\n")
        File(fake, "vmstat").writeText("nr_free_pages 1743136\n")
        File(fake, "version").writeText("Linux version 6.1.0-ultracode (ultracode@localhost) #1 SMP PREEMPT\n")
    }

    /**
     * Installs everything: Alpine base (from the APK), bash/git/ripgrep (apk), official Claude Code (npm).
     * [progress] gets a status text and an optional 0..1 value.
     */
    suspend fun install(progress: (String, Float?) -> Unit) = withContext(Dispatchers.IO) {
        if (!supported) throw ApiException(0, "Встроенный Claude Code работает только на 64-битных телефонах (arm64).")
        base.mkdirs()
        if (!File(root, "bin/busybox").exists()) {
            progress("Готовлю Linux-среду…", null)
            root.deleteRecursively(); root.mkdirs()
            // Stored as .bin: the Android build un-gzips/renames assets ending in .gz.
            ctx.assets.open("alpine-rootfs.bin").use { Tar.extract(GZIPInputStream(it, 1 shl 16), root) }
        }
        File(root, "etc/resolv.conf").apply { delete(); writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n") }
        File(root, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
        File(root, "tmp").apply { mkdirs(); runCatching { Os.chmod(absolutePath, 0x3ff) } }
        File(root, "root/projects").mkdirs()
        writeFakeProc()

        val needed = listOf("bin/bash", "usr/bin/script", "usr/bin/rg", "usr/bin/git")
        if (needed.any { !File(root, it).exists() }) {
            // Main CDN first, then mirrors (the CDN often drops downloads in some countries).
            val mirrors = listOf("https://dl-cdn.alpinelinux.org/alpine", "https://mirror.yandex.ru/mirrors/alpine",
                "https://mirrors.aliyun.com/alpine", "https://dl-cdn.alpinelinux.org/alpine")
            var lastOut = ""
            for ((i, mirror) in mirrors.withIndex()) {
                File(root, "etc/apk/repositories").writeText("$mirror/v3.20/main\n$mirror/v3.20/community\n")
                progress("Устанавливаю пакеты Linux (попытка ${i + 1}, ${mirror.substringAfter("//").substringBefore('/')})…", null)
                val (code, out) = exec(listOf("/sbin/apk", "add", "--no-cache", "--update-cache",
                    "bash", "git", "ripgrep", "ca-certificates", "util-linux-misc", "libgcc", "libstdc++")) {
                    progress("Пакеты: ${it.take(60)}", null)
                }
                lastOut = out
                if (code == 0 && needed.all { File(root, it).exists() }) break
            }
            if (needed.any { !File(root, it).exists() }) {
                throw ApiException(0, "Не удалось скачать пакеты Linux — проверьте интернет и нажмите «Скачать» ещё раз.\n" +
                    lastOut.lines().filter { it.contains("ERROR") }.distinct().takeLast(4).joinToString("\n"))
            }
        }

        if (!claudeBin.exists()) {
            val free = android.os.StatFs(ctx.filesDir.absolutePath).availableBytes
            if (free < 450L * 1024 * 1024) {
                throw ApiException(0, "Мало места: нужно около 450 МБ свободной памяти, сейчас ${free / 1024 / 1024} МБ.")
            }
            claudeBin.parentFile?.mkdirs()
            val tmp = File(claudeBin.parentFile, "claude.part")
            var lastErr: Exception? = null
            // 1) Official Claude Code storage (the same place the official installer downloads from).
            try {
                downloadOfficial(tmp, progress)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastErr = e
                tmp.delete()
                // 2) npm package (and its mirror).
                for (registry in listOf("https://registry.npmjs.org", "https://registry.npmmirror.com")) {
                    try {
                        progress("Пробую другой источник: ${registry.substringAfter("//")}…", null)
                        downloadNpm(registry, tmp, progress)
                        lastErr = null
                        break
                    } catch (e2: kotlinx.coroutines.CancellationException) {
                        throw e2
                    } catch (e2: Exception) {
                        lastErr = e2
                        tmp.delete()
                    }
                }
            }
            if (lastErr != null) throw ApiException(0, "Не удалось скачать Claude Code: ${lastErr.message}")
            if (!tmp.exists() || tmp.length() < 1_000_000) throw ApiException(0, "Файл Claude Code повреждён, попробуйте ещё раз")
            Os.chmod(tmp.absolutePath, 0x1ed)
            if (!tmp.renameTo(claudeBin)) throw ApiException(0, "Не удалось сохранить Claude Code")
        }
        progress("Проверяю Claude Code…", null)
        val v = version() ?: throw ApiException(0, "Claude Code установлен, но не запускается. Пришлите скриншот разработчику.")
        progress("Готово: $v", 1f)
    }

    private val gcs = "https://storage.googleapis.com/claude-code-dist-86c565f3-f756-42ad-8dfa-d59b1c096819/claude-code-releases"

    private fun getText(url: String): String = dl.newCall(Request.Builder().url(url).build()).execute().use {
        if (!it.isSuccessful) throw ApiException(it.code, "HTTP ${it.code} ($url)")
        it.body!!.string()
    }

    /** Official build linux-arm64-musl + SHA-256 check against the release manifest. */
    private suspend fun downloadOfficial(dest: File, progress: (String, Float?) -> Unit) = withContext(Dispatchers.IO) {
        progress("Узнаю последнюю версию Claude Code…", null)
        val version = getText("$gcs/latest").trim()
        val manifest = Storage.json.parseToJsonElement(getText("$gcs/$version/manifest.json")).jsonObject
        val plat = manifest["platforms"]?.jsonObject?.get("linux-arm64-musl")?.jsonObject
        val sum = plat?.get("checksum").str()
        download("$gcs/$version/linux-arm64-musl/claude", dest, progress)
        if (sum != null) {
            progress("Проверяю целостность файла…", null)
            val md = java.security.MessageDigest.getInstance("SHA-256")
            dest.inputStream().buffered(1 shl 16).use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (!got.equals(sum, true)) { dest.delete(); throw ApiException(0, "Файл скачался с ошибкой (не совпала контрольная сумма)") }
        }
    }

    /** npm package @anthropic-ai/claude-code-linux-arm64-musl (tgz) from [registry]. */
    private suspend fun downloadNpm(registry: String, dest: File, progress: (String, Float?) -> Unit) = withContext(Dispatchers.IO) {
        val meta = Storage.json.parseToJsonElement(getText("$registry/@anthropic-ai/claude-code-linux-arm64-musl/latest")).jsonObject
        val url = meta["dist"]?.jsonObject?.get("tarball").str() ?: throw ApiException(0, "npm: нет файла")
        val tgz = File(base, "claude-code.tgz")
        download(url, tgz, progress)
        progress("Распаковываю Claude Code…", null)
        try {
            tgz.inputStream().buffered(1 shl 16).use { Tar.extract(GZIPInputStream(it, 1 shl 16), root, only = "package/claude", onlyTarget = dest) }
        } finally {
            tgz.delete()
        }
    }

    /** HTTP/1.1 only: long HTTP/2 downloads from the npm CDN get "stream was reset" on some networks. */
    private val dl = okhttp3.OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Resumable download: continues from where it stopped after a dropped connection. */
    private suspend fun download(url: String, dest: File, progress: (String, Float?) -> Unit) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        var total = -1L
        repeat(8) { attempt ->
            ensureActive()
            val have = if (dest.exists()) dest.length() else 0L
            if (total > 0 && have >= total) return@withContext
            try {
                val req = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
                dl.newCall(req).execute().use { r ->
                    if (r.code == 416 && have > 0) return@withContext // already complete
                    if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}")
                    val append = r.code == 206
                    val len = r.body!!.contentLength()
                    val start = if (append) have else 0L
                    if (len > 0) total = start + len
                    var done = start
                    var lastPct = -1
                    java.io.FileOutputStream(dest, append).use { out ->
                        val src = r.body!!.byteStream()
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = src.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val t = if (total > 0) total else 365_000_000L
                            val pct = (done * 100 / t).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                progress("Скачиваю Claude Code: $pct%" + if (attempt > 0) " (повтор ${attempt})" else "", done.toFloat() / t)
                            }
                        }
                    }
                    if (total <= 0 || done >= total) return@withContext
                    throw java.io.IOException("Соединение оборвалось")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                progress("Связь прервалась, продолжаю загрузку…", null)
                kotlinx.coroutines.delay(2000L * (attempt + 1))
            }
        }
        throw ApiException(0, "Не удалось скачать Claude Code: ${lastError?.message}")
    }

    suspend fun version(): String? = runCatching {
        val (code, out) = exec(listOf("/usr/local/bin/claude", "--version"))
        if (code == 0) out.lines().firstOrNull { it.isNotBlank() }?.trim() else null
    }.getOrNull()

    fun remove() {
        root.deleteRecursively()
    }

    fun logout() {
        tokenFile.delete()
    }

    /**
     * Sign-in through the official `claude setup-token` flow:
     * Claude Code prints a claude.ai link -> the user signs in and copies the code -> we type it into Claude Code.
     * The resulting long-lived token is stored in Claude Code's folder inside the Linux environment.
     */
    suspend fun login(onUrl: (String) -> Unit, code: CompletableDeferred<String>, status: (String) -> Unit) = withContext(Dispatchers.IO) {
        val p = start(listOf("/usr/bin/script", "-qfec", "stty cols 4000 rows 50 2>/dev/null; /usr/local/bin/claude setup-token", "/dev/null"))
        val ansi = Regex("\\u001B\\[[0-9;?]*[ -/]*[@-~]|\\u001B\\][^\\u0007]*\\u0007|\\u001B[()][0-9A-B]")
        val urlRe = Regex("https://[a-z.]*claude\\.(ai|com)/[^\\s\\u001B\\u0007]*oauth/authorize[^\\s\\u001B\\u0007]+")
        val osc8 = Regex("\\u001B]8;[^;\\u0007]*;(https://[a-z.]*claude\\.(?:ai|com)/[^\\u0007\\u001B]*oauth/authorize[^\\u0007\\u001B]*)")
        val tokenRe = Regex("sk-ant-oat[0-9]{2}-[A-Za-z0-9_\\-]{20,}")
        val buf = StringBuilder()
        var urlSent = false
        var codeSent = false
        var sender: kotlinx.coroutines.Job? = null
        try {
            coroutineScope {
                val watcher = launch { try { awaitCancellation() } finally { p.destroy() } }
                val input: InputStream = p.inputStream
                val chunk = ByteArray(8192)
                status("Запускаю Claude Code…")
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.append(String(chunk, 0, n, Charsets.UTF_8))
                    val raw = buf.toString()
                    // Claude Code prints the link as a terminal hyperlink (OSC 8) and lays out text with
                    // cursor moves instead of spaces, so read the link from the hyperlink sequence.
                    val link = osc8.find(raw)?.groupValues?.get(1)
                        ?: raw.replace("\r", "").lines().firstNotNullOfOrNull { l -> urlRe.find(l.replace(ansi, " "))?.value }
                    if (!urlSent && link != null) {
                        urlSent = true
                        onUrl(link.trimEnd('.', ')', ']', '"', '\''))
                        status("Войдите на открывшейся странице, скопируйте код и вставьте сюда.")
                        sender = launch(Dispatchers.IO) {
                            val c = code.await().trim()
                            codeSent = true
                            p.outputStream.write((c + "\r").toByteArray())
                            p.outputStream.flush()
                            status("Проверяю код…")
                        }
                    }
                    raw.replace("\r", "").lines().firstNotNullOfOrNull { l -> tokenRe.find(l.replace(ansi, " ")) }?.let { m ->
                        tokenFile.parentFile?.mkdirs()
                        tokenFile.writeText(m.value)
                        runCatching { Os.chmod(tokenFile.absolutePath, 0x180) }
                        watcher.cancel()
                        sender?.cancel()
                        p.destroy()
                        return@coroutineScope
                    }
                    if (buf.length > 200_000) buf.delete(0, 100_000)
                }
                sender?.cancel()
                watcher.cancel()
            }
        } finally {
            p.destroy()
        }
        if (!loggedIn) {
            val tail = buf.toString().replace(ansi, "").replace("\r", "").lines().filter { it.isNotBlank() }.takeLast(6).joinToString("\n")
            throw ApiException(0, if (codeSent) "Вход не удался. Проверьте код и попробуйте ещё раз.\n$tail" else "Claude Code не выдал ссылку для входа.\n$tail")
        }
    }

    /** Runs one turn: `claude -p` with stream-json output, continuing [session] if given. */
    suspend fun run(
        prompt: String, session: String?, model: String, folder: String, system: String, allowBash: Boolean,
        onText: (String) -> Unit, onTool: (String, WebSource?) -> Unit, onSession: (String) -> Unit,
    ): ClaudeBridge.Result = withContext(Dispatchers.IO) {
        if (!installed) throw ApiException(0, "Claude Code не установлен. Настройки → Claude Code по подписке → Скачать Claude Code.")
        if (!loggedIn) throw ApiException(0, "Войдите в аккаунт Claude: Настройки → Claude Code по подписке → Войти в Claude.")
        val clean = folder.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." }.joinToString("/").ifEmpty { "ultracode" }
        File(root, "root/projects/$clean").mkdirs()
        val tools = mutableListOf("Read", "Edit", "Write", "MultiEdit", "Glob", "Grep", "LS", "TodoWrite", "WebFetch", "WebSearch", "NotebookEdit")
        if (allowBash) tools += "Bash"
        val args = mutableListOf("-p", "--output-format", "stream-json", "--verbose", "--include-partial-messages", "--model", model)
        if (!session.isNullOrBlank()) args += listOf("--resume", session)
        if (system.isNotBlank()) args += listOf("--append-system-prompt", system)
        args += listOf("--permission-mode", "acceptEdits", "--allowedTools") + tools
        // The token is read by the shell inside Linux and passed only to Claude Code.
        val cmd = listOf("/bin/sh", "-c", "export CLAUDE_CODE_OAUTH_TOKEN=\"\$(cat /root/.ultracode/token)\"; exec /usr/local/bin/claude \"\$@\"", "claude") + args
        val pb = ProcessBuilder(prootCmd(cmd, cwd = "/root/projects/$clean"))
        pb.environment().putAll(env())
        val p = pb.start()
        val parser = ClaudeStreamParser(session, onText, onTool, onSession)
        val stderr = StringBuilder()
        val errThread = Thread { runCatching { p.errorStream.bufferedReader().forEachLine { synchronized(stderr) { stderr.appendLine(it); if (stderr.length > 8000) stderr.delete(0, 4000) } } } }
        errThread.start()
        coroutineScope {
            val watcher = launch { try { awaitCancellation() } finally { p.destroy() } }
            try {
                p.outputStream.use { it.write(prompt.toByteArray()) }
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
        parser.failIfError("Войдите заново: Настройки → Claude Code по подписке → Войти в Claude.")
        parser.result()
    }
}
