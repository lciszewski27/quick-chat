package dev.lciszewski27.quickchat.data.ai

/** Wire protocol spoken by a provider. */
enum class LlmProtocol {
    /** OpenAI `chat/completions` + SSE (`data:`, `[DONE]`). Spoken by OpenAI,
     * OpenRouter, DeepSeek, Ollama, and Gemini's `v1beta/openai` endpoint. */
    OPENAI,

    /** Anthropic Messages API (`x-api-key`, SSE `content_block_delta`). */
    ANTHROPIC,

    /** On-device Gemini Nano (ML Kit GenAI Prompt API) — no HTTP involved. */
    AICORE
}

/**
 * Everything that differs between providers, in one place.
 * Model catalogs are always fetched live — no hardcoded model ids, because
 * provider lineups change too often to bake in.
 * To add a preset type, append one [ProviderConfig] here and to [presetTypes].
 */
data class ProviderConfig(
    val id: String,
    val displayName: String,
    val protocol: LlmProtocol,
    /** Base URL, e.g. `https://api.openai.com/v1` (no trailing slash). Empty for the custom template. */
    val baseUrl: String,
    val chatPath: String,
    val listPath: String,
    val apiKeyHint: String,
    val helpUrl: String,
    /** False for keyless backends (on-device). */
    val requiresApiKey: Boolean = true,
    val extraHeaders: Map<String, String> = emptyMap(),
    /** Anthropic only: required API version header + max output tokens. */
    val anthropicVersion: String = "2023-06-01",
    val anthropicMaxTokens: Int = 2048,
    /** Gemini only: if the OpenAI-compat catalog fails, retry the native endpoint. */
    val geminiNativeListFallback: Boolean = false
)

/** The built-in catalog. Keys are always bring-your-own, stored per provider. */
object Providers {
    private const val GEMINI_OPENAI_BASE = "https://generativelanguage.googleapis.com/v1beta/openai"
    private const val GEMINI_NATIVE_BASE = "https://generativelanguage.googleapis.com/v1beta"

    val gemini = ProviderConfig(
        id = "gemini",
        displayName = "Google AI Studio",
        protocol = LlmProtocol.OPENAI,
        baseUrl = GEMINI_OPENAI_BASE,
        chatPath = "/chat/completions",
        listPath = "/models",
        apiKeyHint = "AIza…  (aistudio.google.com → Get API key)",
        helpUrl = "https://aistudio.google.com/apikey",
        geminiNativeListFallback = true
    )

    val openai = ProviderConfig(
        id = "openai",
        displayName = "OpenAI",
        protocol = LlmProtocol.OPENAI,
        baseUrl = "https://api.openai.com/v1",
        chatPath = "/chat/completions",
        listPath = "/models",
        apiKeyHint = "sk-…  (platform.openai.com → API keys)",
        helpUrl = "https://platform.openai.com/api-keys"
    )

    val anthropic = ProviderConfig(
        id = "anthropic",
        displayName = "Claude (Anthropic)",
        protocol = LlmProtocol.ANTHROPIC,
        baseUrl = "https://api.anthropic.com/v1",
        chatPath = "/messages",
        listPath = "/models",
        apiKeyHint = "sk-ant-…  (console.anthropic.com → API keys)",
        helpUrl = "https://console.anthropic.com/settings/keys"
    )

    val openrouter = ProviderConfig(
        id = "openrouter",
        displayName = "OpenRouter",
        protocol = LlmProtocol.OPENAI,
        baseUrl = "https://openrouter.ai/api/v1",
        chatPath = "/chat/completions",
        listPath = "/models",
        apiKeyHint = "sk-or-…  (openrouter.ai → Keys)",
        helpUrl = "https://openrouter.ai/keys",
        extraHeaders = mapOf(
            "HTTP-Referer" to "android-app://dev.lciszewski27.quickchat",
            "X-Title" to "QuickChat"
        )
    )

    val deepseek = ProviderConfig(
        id = "deepseek",
        displayName = "DeepSeek",
        protocol = LlmProtocol.OPENAI,
        baseUrl = "https://api.deepseek.com/v1",
        chatPath = "/chat/completions",
        listPath = "/models",
        apiKeyHint = "sk-…  (platform.deepseek.com → API keys)",
        helpUrl = "https://platform.deepseek.com/api_keys"
    )

    /** User-supplied OpenAI-compatible endpoint (Ollama, LM Studio, proxies, …). */
    val custom = ProviderConfig(
        id = "custom",
        displayName = "Custom (OpenAI-compatible)",
        protocol = LlmProtocol.OPENAI,
        baseUrl = "",
        chatPath = "/chat/completions",
        listPath = "/models",
        apiKeyHint = "Key for your endpoint (may be empty for local servers)",
        helpUrl = "https://platform.openai.com/docs/api-reference"
    )

    /** On-device Gemini Nano — no key, no network for inference. */
    val aicore = ProviderConfig(
        id = "aicore",
        displayName = "On-device (Gemini Nano)",
        protocol = LlmProtocol.AICORE,
        baseUrl = "",
        chatPath = "",
        listPath = "",
        apiKeyHint = "",
        helpUrl = "https://developer.android.com/ai/gemini-nano",
        requiresApiKey = false
    )

    /** Preset types offered in the Add-provider picker. */
    fun presetTypes(): List<ProviderConfig> =
        listOf(gemini, openai, anthropic, openrouter, deepseek, aicore, custom)

    fun preset(type: String): ProviderConfig? = presetTypes().find { it.id == type }

    internal fun geminiNativeBase(): String = GEMINI_NATIVE_BASE
}
