package com.ultracode.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ProviderKind { ANTHROPIC, OPENAI }

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
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" | "assistant"
    val text: String,
    val images: List<String> = emptyList(),
    val model: String? = null,
    val provider: String? = null,
    val tools: List<String> = emptyList(),
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
) {
    fun provider(id: String = selectedProvider) = providers.firstOrNull { it.id == id }
}

object Defaults {
    const val ANTHROPIC = "anthropic"
    const val OPENROUTER = "openrouter"

    val claudeModels = listOf(
        "claude-opus-5-5",
        "claude-sonnet-5-5",
        "claude-haiku-5-5",
    )

    /** Free models on OpenRouter. The list can be refreshed from the app. */
    val freeModels = listOf(
        "deepseek/deepseek-chat-v3-0324:free",
        "deepseek/deepseek-r1-0528:free",
        "z-ai/glm-4.5-air:free",
        "qwen/qwen3-coder:free",
        "moonshotai/kimi-k2:free",
        "openai/gpt-oss-20b:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "google/gemma-3-27b-it:free",
    )

    val providers = listOf(
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
    )

    /** Presets for "connect your own neural network". */
    data class Preset(val name: String, val kind: ProviderKind, val url: String, val models: String, val keyOptional: Boolean = false, val keyUrl: String = "")

    val presets = listOf(
        Preset("DeepSeek", ProviderKind.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat, deepseek-reasoner", keyUrl = "https://platform.deepseek.com/api_keys"),
        Preset("GLM (Zhipu)", ProviderKind.OPENAI, "https://open.bigmodel.cn/api/paas/v4", "glm-4.5-flash, glm-4.6", keyUrl = "https://open.bigmodel.cn/usercenter/apikeys"),
        Preset("Groq", ProviderKind.OPENAI, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile, qwen/qwen3-32b", keyUrl = "https://console.groq.com/keys"),
        Preset("Gemini", ProviderKind.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash, gemini-2.5-pro", keyUrl = "https://aistudio.google.com/apikey"),
        Preset("Mistral", ProviderKind.OPENAI, "https://api.mistral.ai/v1", "codestral-latest, mistral-large-latest", keyUrl = "https://console.mistral.ai/api-keys"),
        Preset("Pollinations (без ключа)", ProviderKind.OPENAI, "https://text.pollinations.ai/openai", "openai, mistral, qwen-coder", keyOptional = true),
        Preset("Ollama (свой ПК)", ProviderKind.OPENAI, "http://192.168.0.2:11434/v1", "qwen2.5-coder, llama3.1", keyOptional = true),
        Preset("LM Studio", ProviderKind.OPENAI, "http://192.168.0.2:1234/v1", "local-model", keyOptional = true),
        Preset("Anthropic-совместимый", ProviderKind.ANTHROPIC, "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
    )
}
