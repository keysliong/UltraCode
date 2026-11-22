package com.ultracode.desktop

import com.ultracode.app.data.AppSettings
import com.ultracode.app.data.Chat
import com.ultracode.app.data.Defaults
import com.ultracode.app.data.JsonConfig
import com.ultracode.app.data.ProviderKind
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.SecureRandom

/** Settings and chats in ~/.ultracode (desktop). */
class DesktopStore {
    val dir = File(System.getProperty("user.home"), ".ultracode").apply { mkdirs() }
    private val chatsDir = File(dir, "chats").apply { mkdirs() }
    val imagesDir = File(dir, "images").apply { mkdirs() }
    val projectsDir = File(System.getProperty("user.home"), "UltraCode").apply { mkdirs() }
    private val settingsFile = File(dir, "settings.json")
    private val json = JsonConfig.json

    fun loadSettings(): AppSettings {
        val s = runCatching { json.decodeFromString<AppSettings>(settingsFile.readText()) }.getOrElse { AppSettings() }
        return withBuiltins(s)
    }

    fun saveSettings(s: AppSettings) {
        runCatching { settingsFile.writeText(json.encodeToString(s)) }
    }

    fun loadChats(): List<Chat> =
        chatsDir.listFiles()?.mapNotNull { runCatching { json.decodeFromString<Chat>(it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.updated } ?: emptyList()

    fun saveChat(c: Chat) = runCatching { File(chatsDir, "${c.id}.json").writeText(json.encodeToString(c)) }

    fun deleteChat(c: Chat) {
        File(chatsDir, "${c.id}.json").delete()
        c.messages.flatMap { it.images }.forEach { File(it).delete() }
    }

    private fun withBuiltins(s: AppSettings): AppSettings {
        val list = s.providers.toMutableList()
        for (b in Defaults.providers) {
            val i = list.indexOfFirst { it.id == b.id }
            // On a computer Claude Code runs locally (the official CLI), not inside the app.
            val base = if (b.kind == ProviderKind.CLAUDE_CODE) b.copy(baseUrl = LOCAL) else b
            if (i < 0) list.add(list.indexOfLast { it.builtin } + 1, base)
            else list[i] = list[i].copy(
                name = b.name, kind = b.kind, builtin = true, keyUrl = b.keyUrl,
                baseUrl = if (b.kind == ProviderKind.CLAUDE_CODE) list[i].baseUrl.takeIf { it.startsWith("http") } ?: LOCAL else list[i].baseUrl,
                models = list[i].models.ifEmpty { b.models },
            )
        }
        val cc = list.indexOfFirst { it.id == Defaults.CLAUDE_CODE }
        if (cc >= 0 && list[cc].apiKey.isBlank()) {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            list[cc] = list[cc].copy(apiKey = bytes.joinToString("") { "%02x".format(it) })
        }
        for (k in list.indices) {
            val ok = list[k].models.filter { validModelId(it) && it !in s.hiddenModels && !it.startsWith("allam") }
            if (ok.size != list[k].models.size) list[k] = list[k].copy(models = ok)
        }
        return s.copy(providers = list)
    }

    companion object {
        const val LOCAL = "local"

        fun looksLikeKey(m: String) = listOf("sk-", "gsk_", "ghp_", "gho_", "github_pat_", "AIza", "xai-", "hf_")
            .any { m.startsWith(it) } && m.length > 30

        fun validModelId(m: String) = m.isNotBlank() && m.none { it.isWhitespace() } &&
            m.all { it.code in 33..126 } && !looksLikeKey(m)
    }
}
