package dev.lciszewski27.quickchat.data.ai

import dev.lciszewski27.quickchat.data.ai.tools.AiTool
import dev.lciszewski27.quickchat.data.ai.tools.AiTools
import dev.lciszewski27.quickchat.domain.model.ProviderInstance

/**
 * Builds a live [SdkProvider] for a user-added [ProviderInstance].
 * Preset types contribute protocol/paths/hints; the instance contributes
 * identity (instanceId), label, base URL (custom endpoints) and API key.
 * Every provider shares the [tools] registry (built-in, side-effect-free).
 */
class ProviderResolver(
    private val sdk: LlmSdk,
    private val tools: List<AiTool> = AiTools.defaults()
) {

    fun resolve(instance: ProviderInstance): SdkProvider {
        val preset = Providers.preset(instance.type) ?: Providers.custom
        val effective = preset.copy(
            id = instance.instanceId,
            displayName = instance.label.ifBlank { preset.displayName },
            baseUrl = instance.baseUrl.ifBlank { preset.baseUrl }
        )
        return SdkProvider(effective, sdk, tools)
    }

    fun presetFor(type: String): ProviderConfig = Providers.preset(type) ?: Providers.custom
}
