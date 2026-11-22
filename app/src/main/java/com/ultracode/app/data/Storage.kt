package com.ultracode.app.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("ultracode", Context.MODE_PRIVATE)
    private val chatsDir = File(context.filesDir, "chats").apply { mkdirs() }
    val imagesDir = File(context.filesDir, "images").apply { mkdirs() }

    fun loadSettings(): AppSettings {
        val raw = prefs.getString("settings", null) ?: return withBuiltins(AppSettings())
        val s = runCatching { json.decodeFromString<AppSettings>(raw) }.getOrElse { AppSettings() }
        return withBuiltins(s)
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit().putString("settings", json.encodeToString(s)).apply()
    }

    fun loadChats(): List<Chat> =
        chatsDir.listFiles()
            ?.mapNotNull { f -> runCatching { json.decodeFromString<Chat>(f.readText()) }.getOrNull() }
            ?.sortedByDescending { it.updated }
            ?: emptyList()

    fun saveChat(chat: Chat) {
        File(chatsDir, "${chat.id}.json").writeText(json.encodeToString(chat))
    }

    fun deleteChat(chat: Chat) {
        File(chatsDir, "${chat.id}.json").delete()
        chat.messages.flatMap { it.images }.forEach { File(it).delete() }
    }

    private fun withBuiltins(s: AppSettings): AppSettings {
        val list = s.providers.toMutableList()
        for (b in Defaults.providers) {
            val i = list.indexOfFirst { it.id == b.id }
            if (i < 0) list.add(list.indexOfLast { it.builtin } + 1, b)
            else list[i] = list[i].copy(
                name = b.name, kind = b.kind, builtin = true, keyUrl = b.keyUrl,
                baseUrl = if (b.kind == ProviderKind.CLAUDE_CODE) list[i].baseUrl.ifBlank { b.baseUrl } else list[i].baseUrl,
                models = if (b.id == Defaults.OPENROUTER && list[i].verifiedAt == 0L) b.models else list[i].models.ifEmpty { b.models },
            )
        }
        // Secret token for the Claude Code bridge (generated once, used in the install command).
        val cc = list.indexOfFirst { it.id == Defaults.CLAUDE_CODE }
        if (cc >= 0 && list[cc].apiKey.isBlank()) {
            val bytes = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            list[cc] = list[cc].copy(apiKey = bytes.joinToString("") { "%02x".format(it) })
        }
        // Drop invalid model ids that were typed in by mistake (spaces / non-latin letters).
        for (k in list.indices) {
            val ok = list[k].models.filter { isValidModelId(it) && it !in s.hiddenModels && !it.startsWith("allam") }
            if (ok.size != list[k].models.size) list[k] = list[k].copy(models = ok)
        }
        val sel = s.selectedModel
        val fixedSel = if (!isValidModelId(sel) || sel in s.hiddenModels || sel.startsWith("allam")) {
            list.firstOrNull { it.id == s.selectedProvider }?.models?.firstOrNull() ?: Defaults.freeModels.first()
        } else sel
        return s.copy(providers = list, selectedModel = fixedSel)
    }

    companion object {
        /** API keys pasted into a "model" field by mistake. */
        fun looksLikeKey(m: String) = listOf("sk-", "gsk_", "ghp_", "gho_", "github_pat_", "AIza", "xai-", "hf_")
            .any { m.startsWith(it) } && m.length > 30

        /** Model ids are latin, without spaces, and not API keys. */
        fun isValidModelId(m: String) = m.isNotBlank() && m.none { it.isWhitespace() } &&
            m.all { it.code in 33..126 } && !looksLikeKey(m)

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}
