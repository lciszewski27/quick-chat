package dev.lciszewski27.quickchat.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Current date/time on the device. */
class GetTimeTool : AiTool {
    override val name = "get_current_time"
    override val displayName = "Current time"
    override val description =
        "Returns the current date and time on the user's device, including weekday and time zone. " +
            "Use when the user asks about today, now, or needs current-time context."
    override val parametersJson =
        """{"type":"object","properties":{},"additionalProperties":false}"""

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE), HH:mm:ss")

    override suspend fun execute(argsJson: String): String {
        val now = ZonedDateTime.now()
        val zone = now.zone
        val offset = now.offset.toString().let { if (it == "Z") "+00:00" else it }
        return "${now.format(fmt)} $zone (UTC$offset)"
    }
}

/** Safe arithmetic evaluator (no code execution, no network). */
class CalculatorTool : AiTool {
    override val name = "calculate"
    override val displayName = "Calculator"
    override val description =
        "Evaluates a mathematical expression and returns the result. Use for any arithmetic " +
            "the user asks to compute instead of doing the math yourself. " +
            "Supports + - * / % ^, parentheses, and decimal numbers."
    override val parametersJson: String = """
        {"type":"object",
         "properties":{"expression":{"type":"string","description":"The math expression, e.g. (12.5*3+1)/4"}},
         "required":["expression"],"additionalProperties":false}
    """.trimIndent()

    override suspend fun execute(argsJson: String): String {
        val expr = try {
            Json.parseToJsonElement(argsJson).jsonObject["expression"]
                ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return "Error: missing required 'expression' string"
        } catch (e: Exception) {
            return "Error: invalid tool arguments"
        }
        return try {
            val value = ExprEvaluator.eval(expr)
            if (!value.isFinite()) return "Error: result is not finite"
            formatNumber(value)
        } catch (e: IllegalArgumentException) {
            "Error: ${e.message ?: "invalid expression"}"
        } catch (e: Exception) {
            "Error: could not evaluate expression"
        }
    }

    private fun formatNumber(v: Double): String =
        if (v % 1.0 == 0.0 && abs(v) < 1e15) v.toLong().toString() else v.toString()
}

/**
 * Minimal recursive-descent evaluator: expr → term ((+|-) term)*,
 * term → factor ((*|/|%) factor)*, factor → unary (^ factor)?,
 * unary → -unary | primary, primary → number | '(' expr ')'.
 */
object ExprEvaluator {
    fun eval(input: String): Double {
        if (input.length > 500) throw IllegalArgumentException("expression too long")
        return Parser(input).parse()
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expr()
            skipWs()
            if (pos != s.length) throw IllegalArgumentException("unexpected '${s[pos]}'")
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                skipWs()
                v = when {
                    consume('+') -> v + term()
                    consume('-') -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                skipWs()
                v = when {
                    consume('*') -> v * factor()
                    consume('/') -> {
                        val d = factor()
                        if (d == 0.0) throw IllegalArgumentException("division by zero")
                        v / d
                    }
                    consume('%') -> {
                        val d = factor()
                        if (d == 0.0) throw IllegalArgumentException("division by zero")
                        v % d
                    }
                    else -> return v
                }
            }
        }

        private fun factor(): Double {
            val base = unary()
            skipWs()
            return if (consume('^')) {
                val exp = factor() // right-associative
                Math.pow(base, exp)
            } else base
        }

        private fun unary(): Double {
            skipWs()
            if (consume('-')) return -unary()
            if (consume('+')) return unary()
            return primary()
        }

        private fun primary(): Double {
            skipWs()
            if (consume('(')) {
                val v = expr()
                skipWs()
                if (!consume(')')) throw IllegalArgumentException("missing closing parenthesis")
                return v
            }
            return number()
        }

        private fun number(): Double {
            skipWs()
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                while (pos < s.length && s[pos].isDigit()) pos++
            }
            val token = s.substring(start, pos)
            return token.toDoubleOrNull()
                ?: throw IllegalArgumentException(
                    if (start >= s.length) "unexpected end of expression"
                    else "unexpected '${s[start]}'"
                )
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun consume(c: Char): Boolean {
            if (pos < s.length && s[pos] == c) {
                pos++
                return true
            }
            return false
        }
    }
}
