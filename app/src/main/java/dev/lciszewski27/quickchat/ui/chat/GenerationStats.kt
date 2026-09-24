package dev.lciszewski27.quickchat.ui.chat

/**
 * Generation-speed stats. Providers don't report per-chunk token usage, so
 * tokens are estimated (~4 chars/token for typical text) — standard practice
 * for live TPS displays. Stated as estimates nowhere in UI copy is needed
 * since values are inherently approximate; keep them to one decimal max.
 */
internal const val CHARS_PER_TOKEN = 4

internal fun estimateTokens(chars: Int): Int =
    if (chars <= 0) 0 else (chars / CHARS_PER_TOKEN).coerceAtLeast(1)

/** Tokens/sec from totals, or null when there isn't enough signal yet. */
internal fun computeTps(tokens: Int, elapsedMs: Long): Float? {
    if (tokens <= 0 || elapsedMs < 300) return null
    return tokens / (elapsedMs / 1000f)
}

internal fun formatTps(tps: Float): String =
    if (tps >= 10) "${tps.toInt()} tok/s" else "${"%.1f".format(tps)} tok/s"
