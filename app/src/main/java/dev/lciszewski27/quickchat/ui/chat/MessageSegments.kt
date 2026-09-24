package dev.lciszewski27.quickchat.ui.chat

/**
 * Splits a model reply into visible text and `<thought>`/`<think>` blocks.
 * Thought blocks render collapsed (expandable); everything else renders
 * as normal markdown. Unclosed tags are left visible as plain text so
 * malformed model output stays transparent instead of silently vanishing.
 */
internal sealed interface MessageSegment {
    data class Text(val text: String) : MessageSegment
    data class Thought(val text: String) : MessageSegment
}

private val thoughtRegex = Regex(
    "<(thought|think)>(.*?)</\\1>",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)

internal fun splitThoughts(source: String): List<MessageSegment> {
    if (source.isEmpty()) return listOf(MessageSegment.Text(source))
    val out = mutableListOf<MessageSegment>()
    var pos = 0
    thoughtRegex.findAll(source).forEach { m ->
        if (m.range.first > pos) {
            out += MessageSegment.Text(source.substring(pos, m.range.first))
        }
        out += MessageSegment.Thought(m.groupValues[2])
        pos = m.range.last + 1
    }
    if (pos < source.length) out += MessageSegment.Text(source.substring(pos))
    return out.ifEmpty { listOf(MessageSegment.Text(source)) }
}

/** Reply text with thought blocks removed (used for copy). */
internal fun stripThoughts(source: String): String =
    thoughtRegex.replace(source, "").replace(Regex("\n{3,}"), "\n\n").trim()
