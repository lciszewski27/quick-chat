package dev.lciszewski27.quickchat.data.ai.tools

/**
 * A tool the assistant can call mid-reply (agentic loop lives in SdkProvider).
 * All built-ins are side-effect-free (no permissions, network, or storage).
 *
 * To add a tool: implement this interface and append it to [AiTools.defaults].
 * No provider, database, or UI changes needed — both wire protocols
 * (OpenAI-protocol + Anthropic) are generated from this definition.
 */
interface AiTool {
    /** Stable identifier sent to the model, e.g. "calculate". */
    val name: String

    /** Short human label for timeline cards, e.g. "Calculator". */
    val displayName: String

    /** What the tool does — the model only sees this text. */
    val description: String

    /** JSON Schema object for the arguments, e.g. {"type":"object",...}. */
    val parametersJson: String

    /**
     * Runs the tool. Must not throw for bad input — return
     * "Error: ..." text instead so the model can recover.
     */
    suspend fun execute(argsJson: String): String
}

object AiTools {
    fun defaults(): List<AiTool> = listOf(
        GetTimeTool(),
        CalculatorTool(),
        JsTool()
    )
}
