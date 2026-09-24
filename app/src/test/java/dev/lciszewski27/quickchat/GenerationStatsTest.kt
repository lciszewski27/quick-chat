package dev.lciszewski27.quickchat

import dev.lciszewski27.quickchat.ui.chat.computeTps
import dev.lciszewski27.quickchat.ui.chat.estimateTokens
import dev.lciszewski27.quickchat.ui.chat.formatTps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationStatsTest {

    @Test
    fun tokens_estimatedAtFourCharsEach() {
        assertEquals(0, estimateTokens(0))
        assertEquals(1, estimateTokens(3))
        assertEquals(10, estimateTokens(40))
        assertEquals(250, estimateTokens(1000))
    }

    @Test
    fun tps_needsSignal() {
        assertNull(computeTps(0, 1000))
        assertNull(computeTps(10, 299))
        assertEquals(10f, computeTps(10, 1000)!!, 0.01f)
        assertEquals(40f, computeTps(20, 500)!!, 0.01f)
    }

    @Test
    fun tps_formatsCompactly() {
        assertEquals("12 tok/s", formatTps(12.34f))
        assertEquals("4.5 tok/s", formatTps(4.47f))
    }
}
