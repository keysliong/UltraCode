package com.ultracode.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ProviderKind { ANTHROPIC, OPENAI, CLAUDE_CODE }

/** Any AI provider: Claude, OpenRouter (free models) or a user's own network. */
@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<String> = emptyList(),
    val builtin: Boolean = false,
    val keyOptional: Boolean = false,
    val keyUrl: String = "",
    /** Models that support tools (agent mode). Filled from OpenRouter. */
    val toolModels: List<String> = emptyList(),
    /** When the free list was last verified. */
    val verifiedAt: Long = 0,
)

@Serializable
data class WebSource(val title: String, val url: String, val search: Boolean = false)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" | "assistant"
    val text: String,
    val images: List<String> = emptyList(),
    val model: String? = null,
    val provider: String? = null,
    val tools: List<String> = emptyList(),
    val sources: List<WebSource> = emptyList(),
    val error: Boolean = false,
    val time: Long = System.currentTimeMillis(),
)

@Serializable
data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Новый чат",
    val messages: List<ChatMessage> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val repo: String? = null,
    val agent: Boolean = true,
    /** Claude Code session id (to continue the same conversation in the official CLI). */
    val ccSession: String? = null,
    /** How many chat messages Claude Code has already seen (the rest is sent to it as a transcript). */
    val ccSeen: Int = 0,
)

@Serializable
data class AppSettings(
    val providers: List<ProviderConfig> = Defaults.providers,
    val selectedProvider: String = Defaults.OPENROUTER,
    val selectedModel: String = Defaults.freeModels.first(),
    val githubToken: String = "",
    val githubLogin: String = "",
    val dynamicColor: Boolean = true,
    val themeMode: Int = 0, // 0 = system, 1 = light, 2 = dark
    val systemPrompt: String = "",
    val workspaceUri: String? = null,
    val workspaceName: String? = null,
    val maxSteps: Int = 30,
    val notifyQuestions: Boolean = true,
    val webAccess: Boolean = true,
    val autoFallback: Boolean = true,
    /** model id -> time it was last overloaded (HTTP 429). Used to skip busy free models. */
    val busyModels: Map<String, Long> = emptyMap(),
    /** Models the user removed from the list (they stay hidden after list refreshes). */
    val hiddenModels: Set<String> = emptySet(),
    /** Project folder for Claude Code (inside ~/projects on the bridge). */
    val ccFolder: String = "ultracode",
    val ccAllowBash: Boolean = true,
) {
    fun provider(id: String = selectedProvider) = providers.firstOrNull { it.id == id }
}

object Defaults {
    const val ANTHROPIC = "anthropic"
    const val OPENROUTER = "openrouter"
    const val CLAUDE_CODE = "claude_code"
    const val GROQ = "groq"

    /** Groq: very fast, free tier with a key from console.groq.com. Refreshable from the app. */
    val groqModels = listOf(
        "llama-3.3-70b-versatile",
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "moonshotai/kimi-k2-instruct",
        "qwen/qwen3-32b",
        "meta-llama/llama-4-maverick-17b-128e-instruct",
        "meta-llama/llama-4-scout-17b-16e-instruct",
        "llama-3.1-8b-instant",
    )
    /** Claude Code runs inside UltraCode (no Termux / computer). */
    const val EMBEDDED = "embedded"

    val claudeModels = listOf(
        "claude-opus-5-5",
        "claude-sonnet-5-5",
        "claude-haiku-5-5",
    )

    /** Free models on OpenRouter. The list can be refreshed from the app. */
    val freeModels = listOf(
        // DeepSeek
        "deepseek/deepseek-chat-v3-0324:free",
        "deepseek/deepseek-chat-v3.1:free",
        "deepseek/deepseek-r1-0528:free",
        "deepseek/deepseek-r1:free",
        "deepseek/deepseek-r1-distill-llama-70b:free",
        "tngtech/deepseek-r1t2-chimera:free",
        "microsoft/mai-ds-r1:free",
        // GLM
        "z-ai/glm-4.5-air:free",
        // Qwen
        "qwen/qwen3-coder:free",
        "qwen/qwen3-235b-a22b:free",
        "qwen/qwen3-30b-a3b:free",
        "qwen/qwen-2.5-coder-32b-instruct:free",
        // Kimi
        "moonshotai/kimi-k2:free",
        // OpenAI open models
        "openai/gpt-oss-120b:free",
        "openai/gpt-oss-20b:free",
        // Mistral
        "mistralai/devstral-small-2505:free",
        "mistralai/mistral-small-3.2-24b-instruct:free",
        // Meta
        "meta-llama/llama-4-maverick:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        // Google
        "google/gemini-2.0-flash-exp:free",
        "google/gemma-3-27b-it:free",
        // NVIDIA
        "nvidia/nemotron-nano-9b-v2:free",
    )

    val providers = listOf(
        ProviderConfig(
            id = CLAUDE_CODE,
            name = "Claude Code (подписка)",
            kind = ProviderKind.CLAUDE_CODE,
            baseUrl = EMBEDDED,
            models = listOf("sonnet", "opus", "haiku"),
            builtin = true,
        ),
        ProviderConfig(
            id = ANTHROPIC,
            name = "Claude",
            kind = ProviderKind.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            models = claudeModels,
            builtin = true,
            keyUrl = "https://console.anthropic.com/settings/keys",
        ),
        ProviderConfig(
            id = OPENROUTER,
            name = "Бесплатные модели",
            kind = ProviderKind.OPENAI,
            baseUrl = "https://openrouter.ai/api/v1",
            models = freeModels,
            builtin = true,
            keyUrl = "https://openrouter.ai/settings/keys",
        ),
        ProviderConfig(
            id = GROQ,
            name = "Groq",
            kind = ProviderKind.OPENAI,
            baseUrl = "https://api.groq.com/openai/v1",
            models = groqModels,
            builtin = true,
            keyUrl = "https://console.groq.com/keys",
        ),
    )

    /** Presets for "connect your own neural network". */
    data class Preset(val name: String, val kind: ProviderKind, val url: String, val models: String, val keyOptional: Boolean = false, val keyUrl: String = "")

    val presets = listOf(
        Preset("DeepSeek", ProviderKind.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat, deepseek-reasoner", keyUrl = "https://platform.deepseek.com/api_keys"),
        Preset("GLM (Zhipu)", ProviderKind.OPENAI, "https://open.bigmodel.cn/api/paas/v4", "glm-4.5-flash, glm-4.6", keyUrl = "https://open.bigmodel.cn/usercenter/apikeys"),
        Preset("Gemini", ProviderKind.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash, gemini-2.5-pro", keyUrl = "https://aistudio.google.com/apikey"),
        Preset("Mistral", ProviderKind.OPENAI, "https://api.mistral.ai/v1", "codestral-latest, mistral-large-latest", keyUrl = "https://console.mistral.ai/api-keys"),
        Preset("GitHub Models (бесплатный лимит)", ProviderKind.OPENAI, "https://models.github.ai/inference", "openai/gpt-4.1, openai/gpt-4o, deepseek/DeepSeek-V3-0324, deepseek/DeepSeek-R1, meta/Llama-4-Maverick-17B-128E-Instruct-FP8, xai/grok-3-mini, mistral-ai/Codestral-2501", keyUrl = "https://github.com/settings/tokens"),
        Preset("Cerebras (бесплатный лимит)", ProviderKind.OPENAI, "https://api.cerebras.ai/v1", "qwen-3-coder-480b, gpt-oss-120b, llama-3.3-70b", keyUrl = "https://cloud.cerebras.ai"),
        Preset("SambaNova (бесплатный лимит)", ProviderKind.OPENAI, "https://api.sambanova.ai/v1", "DeepSeek-V3-0324, DeepSeek-R1, Meta-Llama-3.3-70B-Instruct", keyUrl = "https://cloud.sambanova.ai/apis"),
        Preset("Pollinations (без ключа)", ProviderKind.OPENAI, "https://text.pollinations.ai/openai", "openai, mistral, qwen-coder", keyOptional = true),
        Preset("Ollama (свой ПК)", ProviderKind.OPENAI, "http://192.168.0.2:11434/v1", "qwen2.5-coder, llama3.1", keyOptional = true),
        Preset("LM Studio", ProviderKind.OPENAI, "http://192.168.0.2:1234/v1", "local-model", keyOptional = true),
        Preset("Anthropic-совместимый", ProviderKind.ANTHROPIC, "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
    )
}
