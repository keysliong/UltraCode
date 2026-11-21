package com.ultracode.app.ai

import com.ultracode.app.data.Storage
import com.ultracode.app.data.WebSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Parses `claude -p --output-format stream-json` lines (official Claude Code CLI). */
class ClaudeStreamParser(
    private var sessionId: String?,
    private val onText: (String) -> Unit,
    private val onTool: (String, WebSource?) -> Unit,
    private val onSession: (String) -> Unit,
) {
    var streamed = false; private set
    var finalText: String? = null; private set
    var error: String? = null; private set
    val session: String? get() = sessionId

    fun line(raw: String) {
        val d = raw.trim()
        if (!d.startsWith("{")) return
        val o = runCatching { Storage.json.parseToJsonElement(d).jsonObject }.getOrNull() ?: return
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
                            val (label, web) = ClaudeBridge.toolLabel(bo["name"].str() ?: "tool", bo["input"] as? JsonObject)
                            onTool(label, web)
                        }
                        "text" -> if (!streamed) bo["text"].str()?.let { onText(it) }
                    }
                }
            }
            "result" -> {
                finalText = o["result"].str()
                val isError = (o["is_error"] as? JsonPrimitive)?.content == "true"
                if (isError || o["subtype"].str()?.startsWith("error") == true) {
                    error = finalText ?: o["subtype"].str() ?: "Ошибка Claude Code"
                }
            }
            "bridge_error" -> error = o["message"].str() ?: "Ошибка моста"
        }
    }

    fun failIfError(loginHint: String) {
        error?.let { msg ->
            val hint = when {
                msg.contains("region", true) || msg.contains("not available in your country", true) ||
                    msg.contains("unsupported_country", true) || msg.contains("Request not allowed", true) ->
                    "\n\nClaude недоступен из вашей страны — включите VPN на телефоне и повторите."
                msg.contains("login", true) || msg.contains("API key", true) ||
                    msg.contains("auth", true) || msg.contains("token", true) -> "\n\n$loginHint"
                else -> ""
            }
            throw ApiException(0, msg.take(1500) + hint)
        }
    }

    fun result() = ClaudeBridge.Result(if (streamed) "" else finalText.orEmpty(), sessionId)
}
