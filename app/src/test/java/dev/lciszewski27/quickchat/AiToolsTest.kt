package dev.lciszewski27.quickchat

import dev.lciszewski27.quickchat.data.ai.tools.AiTools
import dev.lciszewski27.quickchat.data.ai.tools.CalculatorTool
import dev.lciszewski27.quickchat.data.ai.tools.ExprEvaluator
import dev.lciszewski27.quickchat.data.ai.tools.GetTimeTool
import dev.lciszewski27.quickchat.data.ai.tools.JsTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolsTest {

    @Test
    fun registry_hasThreeDistinctTools() {
        val tools = AiTools.defaults()
        assertEquals(3, tools.size)
        assertEquals(3, tools.map { it.name }.toSet().size)
    }

    @Test
    fun evaluator_respectsPrecedence() {
        assertEquals(6.0, ExprEvaluator.eval("2+2*2"), 0.0)
        assertEquals(9.0, ExprEvaluator.eval("(1+2)*3"), 0.0)
        assertEquals(2.5, ExprEvaluator.eval("10/4"), 0.0)
        assertEquals(1024.0, ExprEvaluator.eval("2^10"), 0.0)
        assertEquals(-2.0, ExprEvaluator.eval("-5+3"), 0.0)
        assertEquals(1.0, ExprEvaluator.eval("10%3"), 0.0)
        assertEquals(7.0, ExprEvaluator.eval(" 3.5 * 2 "), 0.0)
    }

    @Test
    fun calculator_returnsResultsAndErrors() = runBlocking {
        val calc = CalculatorTool()
        assertEquals("6", calc.execute("""{"expression":"2+2*2"}"""))
        assertEquals("9", calc.execute("""{"expression":"(1+2)*3"}"""))
        assertEquals("2.5", calc.execute("""{"expression":"10/4"}"""))
        assertTrue(calc.execute("""{"expression":"1/0"}""").startsWith("Error"))
        assertTrue(calc.execute("""{"expression":"2+"}""").startsWith("Error"))
        assertTrue(calc.execute("""{}""").startsWith("Error"))
        assertTrue(calc.execute("not json").startsWith("Error"))
    }

    @Test
    fun timeTool_returnsNonBlankTimestamp() = runBlocking {
        val result = GetTimeTool().execute("{}")
        assertTrue(result.isNotBlank())
        assertTrue(result.contains("20")) // sanity: contains a year
    }

    @Test
    fun jsTool_evaluatesExpressions() = runBlocking {
        val js = JsTool()
        assertEquals("3", js.execute("""{"code":"1 + 2"}"""))
        assertEquals("9", js.execute("""{"code":"(1+2)*3"}"""))
        assertEquals("{\"a\":1}", js.execute("""{"code":"JSON.stringify({a: 1})"}"""))
    }

    @Test
    fun jsTool_exposesHelpersAndNoFetch() = runBlocking {
        val js = JsTool()
        assertEquals("hi", js.execute("""{"code":"base64Decode(base64Encode('hi'))"}"""))
        // Sandbox proof: no fetch/XHR in the runtime.
        assertTrue(js.execute("""{"code":"typeof fetch"}""").contains("undefined"))
        assertTrue(js.execute("""{"code":"1+"}""").startsWith("Error"))
        assertTrue(js.execute("""{}""").startsWith("Error"))
    }

    @Test
    fun jsTool_rejectsBadUrlsWithoutNetwork() = runBlocking {
        val js = JsTool()
        assertTrue(js.execute("""{"code":"request('ht!tp://??')"}""").startsWith("Error"))
    }

    @Test
    fun jsTool_timesOutInfiniteLoops() = runBlocking {
        val js = JsTool(timeoutMs = 400)
        assertTrue(js.execute("""{"code":"while(true){}"}""").startsWith("Error"))
    }
}
