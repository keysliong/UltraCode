package com.ultracode.app.ai

import com.ultracode.app.data.ProviderConfig
import com.ultracode.app.data.ProviderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed interface Part {
    data class Text(val text: String) : Part
    data class Image(val base64: String, val mime: String = "image/jpeg") : Part
}

data class ToolCall(val id: String, val name: String, val arguments: String)
data class ToolResult(val callId: String, val name: String, val content: String)
data class ToolSpec(val name: String, val description: String, val schema: JsonObject)

data class WireMessage(
    val role: String,
    val parts: List<Part> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
) {
    val text: String get() = parts.filterIsInstance<Part.Text>().joinToString("\n") { it.text }
}

data class TurnResult(val text: String, val toolCalls: List<ToolCall>, val stopReason: String?)

class ApiException(val code: Int, message: String) : Exception(message)

fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

object LlmClient {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    private val JSON = "application/json".toMediaType()
    private val json = com.ultracode.app.data.Storage.json

    suspend fun complete(
        p: ProviderConfig,
        model: String,
        system: String,
        messages: List<WireMessage>,
        tools: List<ToolSpec>,
        onText: (String) -> Unit,
    ): TurnResult = withContext(Dispatchers.IO) {
        when (p.kind) {
            ProviderKind.ANTHROPIC -> anthropic(p, model, system, messages, tools, onText)
            ProviderKind.OPENAI -> openai(p, model, system, messages, tools, onText)
            ProviderKind.CLAUDE_CODE -> throw ApiException(0, "Claude Code работает через мост, а не через API")
        }
    }

    // ---------------------------------------------------------------- OpenAI-compatible

    private suspend fun openai(
        p: ProviderConfig, model: String, system: String,
        messages: List<WireMessage>, tools: List<ToolSpec>, onText: (String) -> Unit,
    ): TurnResult {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonArray("messages") {
                if (system.isNotBlank()) addJsonObject { put("role", "system"); put("content", system) }
                for (m in messages) when {
                    m.toolResults.isNotEmpty() -> m.toolResults.forEach { r ->
                        addJsonObject {
                            put("role", "tool"); put("tool_call_id", r.callId); put("content", r.content)
                        }
                    }
                    m.role == "assistant" -> addJsonObject {
                        put("role", "assistant")
                        if (m.text.isEmpty() && m.toolCalls.isNotEmpty()) put("content", JsonNull) else put("content", m.text)
                        if (m.toolCalls.isNotEmpty()) putJsonArray("tool_calls") {
                            m.toolCalls.forEach { c ->
                                addJsonObject {
                                    put("id", c.id); put("type", "function")
                                    putJsonObject("function") { put("name", c.name); put("arguments", c.arguments.ifBlank { "{}" }) }
                                }
                            }
                        }
                    }
                    else -> addJsonObject {
                        put("role", "user")
                        val imgs = m.parts.filterIsInstance<Part.Image>()
                        if (imgs.isEmpty()) put("content", m.text)
                        else putJsonArray("content") {
                            imgs.forEach { im ->
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", "data:${im.mime};base64,${im.base64}") }
                                }
                            }
                            addJsonObject { put("type", "text"); put("text", m.text.ifBlank { "Что на изображении?" }) }
                        }
                    }
                }
            }
            if (tools.isNotEmpty()) putJsonArray("tools") {
                tools.forEach { t ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", t.name); put("description", t.description); put("parameters", t.schema)
                        }
                    }
                }
            }
        }
        val req = Request.Builder()
            .url(p.baseUrl.trimEnd('/') + "/chat/completions")
            .post(body.toString().toRequestBody(JSON))
            .header("Accept", "text/event-stream")
            .apply {
                if (p.apiKey.isNotBlank()) header("Authorization", "Bearer ${p.apiKey.trim()}")
                if (p.baseUrl.contains("openrouter")) {
                    header("HTTP-Referer", "https://github.com/ultracode-app")
                    header("X-Title", "UltraCode")
                }
            }.build()

        class Acc(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())

        val text = StringBuilder()
        val calls = sortedMapOf<Int, Acc>()
        var stop: String? = null
        sse(req) { data ->
            val o = json.parseToJsonElement(data).jsonObject
            o["error"]?.let { e ->
                val code = ((e as? JsonObject)?.get("code") as? JsonPrimitive)?.intOrNull ?: 0
                throw ApiException(code, errorText(e))
            }
            val choice = (o["choices"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return@sse
            val delta = choice["delta"] as? JsonObject
            delta?.get("content").str()?.let { if (it.isNotEmpty()) { text.append(it); onText(it) } }
            (delta?.get("tool_calls") as? JsonArray)?.forEach { tc ->
                val t = tc.jsonObject
                val idx = (t["index"] as? JsonPrimitive)?.intOrNull ?: calls.size
                val acc = calls.getOrPut(idx) { Acc() }
                t["id"].str()?.let { if (it.isNotEmpty()) acc.id = it }
                (t["function"] as? JsonObject)?.let { f ->
                    f["name"].str()?.let { if (acc.name.isEmpty()) acc.name = it }
                    f["arguments"].str()?.let { acc.args.append(it) }
                }
            }
            choice["finish_reason"].str()?.let { stop = it }
        }
        val toolCalls = calls.values.filter { it.name.isNotEmpty() }.mapIndexed { i, a ->
            ToolCall(a.id.ifEmpty { "call_${System.currentTimeMillis()}_$i" }, a.name, a.args.toString())
        }
        return TurnResult(text.toString(), toolCalls, stop)
    }

    // ---------------------------------------------------------------- Anthropic (Claude)

    private suspend fun anthropic(
        p: ProviderConfig, model: String, system: String,
        messages: List<WireMessage>, tools: List<ToolSpec>, onText: (String) -> Unit,
    ): TurnResult {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 16000)
            put("stream", true)
            if (system.isNotBlank()) put("system", system)
            putJsonArray("messages") {
                for (m in messages) when {
                    m.toolResults.isNotEmpty() -> addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            m.toolResults.forEach { r ->
                                addJsonObject {
                                    put("type", "tool_result"); put("tool_use_id", r.callId); put("content", r.content)
                                }
                            }
                        }
                    }
                    m.role == "assistant" -> addJsonObject {
                        put("role", "assistant")
                        putJsonArray("content") {
                            if (m.text.isNotBlank()) addJsonObject { put("type", "text"); put("text", m.text) }
                            m.toolCalls.forEach { c ->
                                addJsonObject {
                                    put("type", "tool_use"); put("id", c.id); put("name", c.name)
                                    put("input", runCatching { json.parseToJsonElement(c.arguments.ifBlank { "{}" }).jsonObject }
                                        .getOrElse { JsonObject(emptyMap()) })
                                }
                            }
                        }
                    }
                    else -> addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            m.parts.filterIsInstance<Part.Image>().forEach { im ->
                                addJsonObject {
                                    put("type", "image")
                                    putJsonObject("source") {
                                        put("type", "base64"); put("media_type", im.mime); put("data", im.base64)
                                    }
                                }
                            }
                            addJsonObject { put("type", "text"); put("text", m.text.ifBlank { "Что на изображении?" }) }
                        }
                    }
                }
            }
            if (tools.isNotEmpty()) putJsonArray("tools") {
                tools.forEach { t ->
                    addJsonObject { put("name", t.name); put("description", t.description); put("input_schema", t.schema) }
                }
            }
        }
        val req = Request.Builder()
            .url(p.baseUrl.trimEnd('/') + "/messages")
            .post(body.toString().toRequestBody(JSON))
            .header("x-api-key", p.apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
            .build()

        class Acc(val id: String, val name: String, val args: StringBuilder = StringBuilder())

        val text = StringBuilder()
        val calls = sortedMapOf<Int, Acc>()
        var stop: String? = null
        sse(req) { data ->
            val o = json.parseToJsonElement(data).jsonObject
            val idx = (o["index"] as? JsonPrimitive)?.intOrNull ?: 0
            when (o["type"].str()) {
                "error" -> throw ApiException(0, errorText(o["error"] ?: o))
                "content_block_start" -> {
                    val b = o["content_block"]?.jsonObject
                    if (b?.get("type").str() == "tool_use") calls[idx] = Acc(b?.get("id").str() ?: "", b?.get("name").str() ?: "")
                }
                "content_block_delta" -> {
                    val d = o["delta"]?.jsonObject
                    when (d?.get("type").str()) {
                        "text_delta" -> d?.get("text").str()?.let { text.append(it); onText(it) }
                        "input_json_delta" -> d?.get("partial_json").str()?.let { calls[idx]?.args?.append(it) }
                    }
                }
                "message_delta" -> o["delta"]?.jsonObject?.get("stop_reason").str()?.let { stop = it }
            }
        }
        return TurnResult(text.toString(), calls.values.map { ToolCall(it.id, it.name, it.args.toString()) }, stop)
    }

    // ---------------------------------------------------------------- Helpers

    private suspend fun sse(req: Request, onData: (String) -> Unit) = coroutineScope {
        val call = http.newCall(req)
        val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val b = resp.body?.string().orEmpty()
                    throw ApiException(resp.code, "HTTP ${resp.code}: " + errorFromBody(b))
                }
                val src = resp.body!!.source()
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val d = line.removePrefix("data:").trim()
                    if (d == "[DONE]") break
                    if (d.isNotEmpty() && d.startsWith("{")) onData(d)
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    private fun errorFromBody(b: String): String =
        runCatching { errorText(json.parseToJsonElement(b).jsonObject["error"] ?: return b.take(500)) }.getOrElse { b.take(500) }

    private fun errorText(e: JsonElement): String {
        val o = e as? JsonObject ?: return e.str() ?: e.toString().take(500)
        val msg = o["message"].str() ?: o.toString().take(500)
        val raw = (o["metadata"] as? JsonObject)?.get("raw").str()
        return if (raw != null && raw !in msg) "$msg — ${raw.take(300)}" else msg
    }

    /** Lists models of a provider. For OpenRouter returns only free models. */
    suspend fun listModels(p: ProviderConfig): List<String> = if (p.kind == ProviderKind.CLAUDE_CODE) listOf("sonnet", "opus", "haiku") else withContext(Dispatchers.IO) {
        val b = Request.Builder().url(p.baseUrl.trimEnd('/') + "/models")
        if (p.kind == ProviderKind.ANTHROPIC) {
            b.header("x-api-key", p.apiKey.trim()).header("anthropic-version", "2023-06-01")
        } else if (p.apiKey.isNotBlank()) b.header("Authorization", "Bearer ${p.apiKey.trim()}")
        http.newCall(b.build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}: " + errorFromBody(s))
            val data = json.parseToJsonElement(s).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
            val ids = data.mapNotNull { (it as? JsonObject)?.get("id").str() }
            when {
                p.baseUrl.contains("openrouter") -> parseFree(data).map { it.id }
                // Groq also lists speech / moderation models: keep only chat models.
                p.baseUrl.contains("groq.com") -> ids.filter { id ->
                    listOf("whisper", "tts", "guard", "playai", "distil", "orpheus", "compound").none { id.contains(it, true) }
                }.sortedWith(compareBy({ !it.contains("70b") && !it.contains("120b") && !it.contains("kimi") }, { it }))
                else -> ids
            }
        }
    }

    data class FreeModel(val id: String, val tools: Boolean)

    /** Only models whose real price on OpenRouter is $0 for input AND output. Tool-capable models first. */
    private fun parseFree(data: JsonArray): List<FreeModel> = data.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o["id"].str() ?: return@mapNotNull null
        val pr = o["pricing"] as? JsonObject ?: return@mapNotNull null
        fun price(k: String) = pr[k].str()?.toDoubleOrNull()
        val prompt = price("prompt"); val completion = price("completion")
        val request = price("request") ?: 0.0
        if (prompt != 0.0 || completion != 0.0 || request != 0.0) return@mapNotNull null
        if (!id.endsWith(":free")) return@mapNotNull null
        val tools = (o["supported_parameters"] as? JsonArray)?.any { it.str() == "tools" } ?: false
        FreeModel(id, tools)
    }.sortedWith(compareBy({ !it.tools }, { it.id }))

    /** Verified free models on OpenRouter (no key needed). */
    suspend fun freeModels(): List<FreeModel> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("https://openrouter.ai/api/v1/models").build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}")
            parseFree(json.parseToJsonElement(s).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList()))
        }
    }
}
