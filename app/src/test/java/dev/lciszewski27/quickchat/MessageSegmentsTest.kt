package dev.lciszewski27.quickchat

import dev.lciszewski27.quickchat.ui.chat.MessageSegment
import dev.lciszewski27.quickchat.ui.chat.splitThoughts
import dev.lciszewski27.quickchat.ui.chat.stripThoughts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSegmentsTest {

    @Test
    fun plainText_staysSingleSegment() {
        val out = splitThoughts("Hello **world**")
        assertEquals(1, out.size)
        assertEquals(MessageSegment.Text("Hello **world**"), out[0])
    }

    @Test
    fun thoughtBlock_splitsAround() {
        val out = splitThoughts("Answer.\n<thought>reasoning here</thought>\nDone.")
        assertEquals(3, out.size)
        assertEquals(MessageSegment.Text("Answer.\n"), out[0])
        assertEquals(MessageSegment.Thought("reasoning here"), out[1])
        assertEquals(MessageSegment.Text("\nDone."), out[2])
    }

    @Test
    fun thinkVariant_isRecognized() {
        val out = splitThoughts("<think>hmm</think>Hi")
        assertEquals(2, out.size)
        assertEquals(MessageSegment.Thought("hmm"), out[0])
        assertEquals(MessageSegment.Text("Hi"), out[1])
    }

    @Test
    fun unclosedTag_staysVisible() {
        val raw = "Hi <thought>oops, never closed"
        val out = splitThoughts(raw)
        assertEquals(1, out.size)
        assertEquals(MessageSegment.Text(raw), out[0])
    }

    @Test
    fun multipleBlocks_allSplit() {
        val out = splitThoughts("a<thought>1</thought>b<thought>2</thought>c")
        assertEquals(5, out.size)
        assertTrue(out[1] is MessageSegment.Thought)
        assertTrue(out[3] is MessageSegment.Thought)
    }

    @Test
    fun strip_removesBlocksForCopy() {
        assertEquals(
            "Answer.\n\nDone.",
            stripThoughts("Answer.\n<thought>reasoning</thought>\nDone.")
        )
        assertEquals("plain", stripThoughts("plain"))
    }
}
