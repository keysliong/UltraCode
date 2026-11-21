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
        val raw = prefs.getString("settings", null) ?: return AppSettings()
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
            if (i < 0) list.add(0, b)
            else list[i] = list[i].copy(
                name = b.name, kind = b.kind, builtin = true, keyUrl = b.keyUrl,
                models = list[i].models.ifEmpty { b.models },
            )
        }
        return s.copy(providers = list)
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}
