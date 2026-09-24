package dev.lciszewski27.quickchat

import dev.lciszewski27.quickchat.data.ai.AiCoreProvider
import dev.lciszewski27.quickchat.domain.model.ChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCoreTranscriptTest {

    @Test
    fun transcript_labelsRolesAndSystem() {
        val out = AiCoreProvider.buildTranscript(
            listOf(ChatTurn("user", "hi"), ChatTurn("model", "hello")),
            "Be brief."
        )
        assertEquals("System: Be brief.\nUser: hi\nAssistant: hello", out)
    }

    @Test
    fun transcript_truncatesOldestFirst() {
        val big = "x".repeat(5000)
        val turns = listOf(
            ChatTurn("user", big),
            ChatTurn("model", big),
            ChatTurn("user", "latest?")
        )
        val out = AiCoreProvider.buildTranscript(turns, null)
        assertTrue(out.length <= AiCoreProvider.MAX_TRANSCRIPT_CHARS)
        assertTrue(out.endsWith("User: latest?"))
    }

    @Test
    fun transcript_keepsSystemAndLatest() {
        val big = "y".repeat(6000)
        val out = AiCoreProvider.buildTranscript(
            listOf(ChatTurn("user", big), ChatTurn("model", big), ChatTurn("user", "q")),
            "sys"
        )
        assertTrue(out.startsWith("System: sys"))
        assertTrue(out.endsWith("User: q"))
        assertTrue(out.length <= AiCoreProvider.MAX_TRANSCRIPT_CHARS)
    }
}
