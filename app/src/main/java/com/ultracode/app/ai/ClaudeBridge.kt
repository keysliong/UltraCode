package com.ultracode.app.ai

import com.ultracode.app.data.Storage
import com.ultracode.app.data.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Talks to the UltraCode bridge, which runs the OFFICIAL Claude Code CLI on the user's
 * device (Termux) or computer, signed in with the user's own Claude account.
 * UltraCode never sees the Claude login — it only sends tasks and shows the results.
 */
object ClaudeBridge {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .build()
    private val json = Storage.json

    data class Result(val text: String, val session: String?)

    private fun friendly(e: IOException, url: String) = ApiException(0,
        if (url.contains("127.0.0.1") || url.contains("localhost"))
            "Мост Claude Code не запущен. Откройте Termux и выполните команду uc-bridge (подробнее: Настройки → Claude Code по подписке)."
        else "Не удаётся подключиться к мосту Claude Code по адресу $url. Проверьте, что на компьютере запущен мост и телефон в той же сети Wi-Fi.")

    suspend fun health(url: String, token: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url.trimEnd('/') + "/health").header("x-token", token).build()
        try {
            http.newCall(req).execute().use { r ->
                val s = r.body?.string().orEmpty()
                if (r.code == 401) throw ApiException(401, "Неверный токен моста. Скопируйте команду установки заново.")
                if (!r.isSuccessful) throw ApiException(r.code, "Мост ответил HTTP ${r.code}")
                val o = json.parseToJsonElement(s).jsonObject
                o["version"].str() ?: throw ApiException(0, "Мост работает, но Claude Code не найден. Установите его (команда установки в настройках).")
            }
        } catch (e: IOException) { throw friendly(e, url) }
    }

    fun toolLabel(name: String, input: JsonObject?): Pair<String, WebSource?> {
        val d = input?.let {
            it["command"].str() ?: it["file_path"].str() ?: it["notebook_path"].str() ?: it["path"].str()
                ?: it["pattern"].str() ?: it["url"].str() ?: it["query"].str() ?: it["description"].str()
        }.orEmpty().lineSequence().firstOrNull().orEmpty().take(70)
        val icon = when (name) {
            "Bash" -> "⌨️"; "Read" -> "📖"; "Edit", "MultiEdit", "Write", "NotebookEdit" -> "✏️"
            "Glob", "Grep", "LS" -> "📂"; "WebFetch" -> "🌐"; "WebSearch" -> "🔎"; "TodoWrite" -> "📝"
            else -> "🔧"
        }
        val src = when (name) {
            "WebFetch" -> input?.get("url").str()?.let { WebSource(it.substringAfter("://").substringBefore('/'), it) }
            "WebSearch" -> input?.get("query").str()?.let {
                WebSource("Поиск: $it", "https://duckduckgo.com/?q=" + URLEncoder.encode(it, "UTF-8"), search = true)
            }
            else -> null
        }
        return "$icon $name $d".trim() to src
    }

    suspend fun run(
        url: String,
        token: String,
        prompt: String,
        session: String?,
        model: String,
        folder: String,
        system: String,
        allowBash: Boolean,
        onText: (String) -> Unit,
        onTool: (String, WebSource?) -> Unit,
        onSession: (String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("prompt", prompt)
            if (!session.isNullOrBlank()) put("session", session)
            put("model", model)
            put("folder", folder)
            put("system", system)
            put("allowBash", allowBash)
        }
        val req = Request.Builder()
            .url(url.trimEnd('/') + "/chat")
            .header("x-token", token)
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        var sessionId: String? = session
        var finalText: String? = null
        var streamed = false
        var error: String? = null
        coroutineScope {
            val call = http.newCall(req)
            val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
            try {
                call.execute().use { r ->
                    if (r.code == 401) throw ApiException(401, "Неверный токен моста. Скопируйте команду установки заново и перезапустите uc-bridge.")
                    if (!r.isSuccessful) throw ApiException(r.code, "Мост ответил HTTP ${r.code}: ${r.body?.string().orEmpty().take(300)}")
                    val src = r.body!!.source()
                    while (true) {
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val d = line.removePrefix("data:").trim()
                        if (!d.startsWith("{")) continue
                        val o = runCatching { json.parseToJsonElement(d).jsonObject }.getOrNull() ?: continue
                        o["session_id"].str()?.let { if (it != sessionId) { sessionId = it; onSession(it) } }
                        when (o["type"].str()) {
                            "stream_event" -> {
                                val ev = o["event"] as? JsonObject
                                if (ev?.get("type").str() == "content_block_delta") {
                                    val delta = ev?.get("delta") as? JsonObject
                                    if (delta?.get("type").str() == "text_delta") delta?.get("text").str()?.let { streamed = true; onText(it) }
                                }
                            }
                            "assistant" -> {
                                val content = (o["message"] as? JsonObject)?.get("content") as? JsonArray
                                content?.forEach { b ->
                                    val bo = b as? JsonObject ?: return@forEach
                                    when (bo["type"].str()) {
                                        "tool_use" -> {
                                            val (label, web) = toolLabel(bo["name"].str() ?: "tool", bo["input"] as? JsonObject)
                                            onTool(label, web)
                                        }
                                        // Older CLI without partial messages: show whole text blocks.
                                        "text" -> if (!streamed) bo["text"].str()?.let { onText(it) }
                                    }
                                }
                            }
                            "result" -> {
                                finalText = o["result"].str()
                                val isError = (o["is_error"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
                                if (isError || o["subtype"].str()?.startsWith("error") == true) {
                                    error = finalText ?: o["subtype"].str() ?: "Ошибка Claude Code"
                                }
                            }
                            "bridge_error" -> error = o["message"].str() ?: "Ошибка моста"
                        }
                    }
                }
            } catch (e: IOException) {
                if (watcher.isActive) throw friendly(e, url) else throw e
            } finally {
                watcher.cancel()
            }
        }
        error?.let { msg ->
            val hint = if (msg.contains("login", true) || msg.contains("API key", true) || msg.contains("auth", true))
                "\n\nВойдите в аккаунт Claude: в Termux выполните uc-login (на ПК — команда claude), затем снова uc-bridge."
            else ""
            throw ApiException(0, msg.take(1500) + hint)
        }
        Result(if (streamed) "" else finalText.orEmpty(), sessionId)
    }

    /** One command for Termux: Debian (proot) + official Claude Code + the bridge + uc-login / uc-bridge commands. */
    fun termuxInstallCommand(bridgeJs: String, token: String): String {
        val b64 = android.util.Base64.encodeToString(bridgeJs.toByteArray(), android.util.Base64.NO_WRAP)
        val d = "$"
        return listOf(
            "pkg update -y && pkg install -y proot-distro",
            "(proot-distro install debian || true)",
            "proot-distro login debian -- bash -c 'apt-get update && apt-get install -y nodejs curl git ca-certificates && (test -x /root/.local/bin/claude || curl -fsSL https://claude.ai/install.sh | bash)'",
            "R=${d}PREFIX/var/lib/proot-distro/installed-rootfs/debian/root",
            "echo '$b64' | base64 -d > ${d}R/ultracode-bridge.js",
            "printf '#!/data/data/com.termux/files/usr/bin/sh\\nproot-distro login debian -- /root/.local/bin/claude \"${d}@\"\\n' > ${d}PREFIX/bin/uc-login",
            "printf '#!/data/data/com.termux/files/usr/bin/sh\\ntermux-wake-lock 2>/dev/null\\nproot-distro login debian -- env CLAUDE_BIN=/root/.local/bin/claude node /root/ultracode-bridge.js --token $token \"${d}@\"\\n' > ${d}PREFIX/bin/uc-bridge",
            "chmod +x ${d}PREFIX/bin/uc-login ${d}PREFIX/bin/uc-bridge",
            "echo && echo 'Готово! 1) uc-login — войдите в аккаунт Claude   2) uc-bridge — запустите мост и вернитесь в UltraCode'",
        ).joinToString(" && ")
    }

    /** Command for a computer (macOS / Linux / WSL / Git Bash) with Node.js. */
    fun pcInstallCommand(bridgeJs: String, token: String): String {
        val b64 = android.util.Base64.encodeToString(bridgeJs.toByteArray(), android.util.Base64.NO_WRAP)
        return "npm install -g @anthropic-ai/claude-code && echo '$b64' | base64 -d > ultracode-bridge.js && " +
            "echo 'Готово! 1) claude — войдите в аккаунт  2) node ultracode-bridge.js --lan --token $token'"
    }
}
