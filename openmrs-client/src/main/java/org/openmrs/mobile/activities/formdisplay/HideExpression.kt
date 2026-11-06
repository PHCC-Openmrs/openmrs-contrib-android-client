package org.openmrs.mobile.activities.formdisplay

/**
 * Evaluates a form question's `hide.hideWhenExpression`. The web form engine runs real JavaScript;
 * the app only understands the subset such expressions use in practice:
 *
 * - question ids (their current answer: the chosen answer's concept uuid, or the entered text /
 *   number / date; null when unanswered or unknown)
 * - string literals ('..' or ".."), `null`, `undefined`, `true`, `false`
 * - `===`, `!==`, `==`, `!=`, `&&`, `||`, `!` and parentheses
 *
 * Anything else (function calls, arithmetic, ...) makes [evaluate] return null, which callers
 * treat as "don't hide" - an unreadable rule must never make a question unreachable.
 */
object HideExpression {

    /** True/false for the expression, or null when it can't be evaluated. */
    fun evaluate(expression: String, valueOf: (String) -> String?): Boolean? = try {
        val parser = Parser(tokenize(expression), valueOf)
        val result = parser.parseOr()
        if (parser.atEnd()) truthy(result) else null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Question ids [expression] reads - not needed to evaluate, kept for diagnostics and tests. */
    fun identifiers(expression: String): Set<String> = try {
        tokenize(expression).filterIsInstance<Token.Ident>().map { it.name }
            .filter { it !in KEYWORDS }.toSet()
    } catch (e: IllegalArgumentException) {
        emptySet()
    }

    private val KEYWORDS = setOf("null", "undefined", "true", "false")

    private sealed class Token {
        data class Str(val value: String) : Token()
        data class Ident(val name: String) : Token()
        data class Op(val symbol: String) : Token()
    }

    private val OPERATORS = listOf("===", "!==", "==", "!=", "&&", "||", "!", "(", ")")

    private fun tokenize(source: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c.isWhitespace() -> i++
                c == '\'' || c == '"' -> {
                    val end = source.indexOf(c, i + 1)
                    require(end >= 0) { "unterminated string" }
                    tokens += Token.Str(source.substring(i + 1, end))
                    i = end + 1
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    var j = i
                    while (j < source.length && (source[j].isLetterOrDigit() || source[j] == '_' || source[j] == '$')) j++
                    tokens += Token.Ident(source.substring(i, j))
                    i = j
                }
                else -> {
                    val op = OPERATORS.firstOrNull { source.startsWith(it, i) }
                        ?: throw IllegalArgumentException("unsupported character '$c'")
                    tokens += Token.Op(op)
                    i += op.length
                }
            }
        }
        return tokens
    }

    // Values are String? for text/null, Boolean for comparison results.
    private fun truthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is String -> value.isNotEmpty()
        else -> true
    }

    private class Parser(private val tokens: List<Token>, private val valueOf: (String) -> String?) {
        private var pos = 0

        fun atEnd() = pos >= tokens.size

        private fun peekOp(): String? = (tokens.getOrNull(pos) as? Token.Op)?.symbol

        fun parseOr(): Any? {
            var left = parseAnd()
            while (peekOp() == "||") {
                pos++
                val right = parseAnd()
                // JS semantics: the first truthy operand, else the last.
                left = if (truthy(left)) left else right
            }
            return left
        }

        private fun parseAnd(): Any? {
            var left = parseEquality()
            while (peekOp() == "&&") {
                pos++
                val right = parseEquality()
                left = if (!truthy(left)) left else right
            }
            return left
        }

        private fun parseEquality(): Any? {
            var left = parseUnary()
            while (true) {
                val op = peekOp()
                if (op != "===" && op != "!==" && op != "==" && op != "!=") return left
                pos++
                val right = parseUnary()
                val equal = left == right
                left = if (op == "===" || op == "==") equal else !equal
            }
        }

        private fun parseUnary(): Any? {
            if (peekOp() == "!") {
                pos++
                return !truthy(parseUnary())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Any? {
            val token = tokens.getOrNull(pos) ?: throw IllegalArgumentException("unexpected end")
            pos++
            return when (token) {
                is Token.Str -> token.value
                is Token.Ident -> when (token.name) {
                    "null", "undefined" -> null
                    "true" -> true
                    "false" -> false
                    else -> {
                        // A call or member access is beyond the supported subset.
                        require(peekOp() != "(") { "function calls are not supported" }
                        valueOf(token.name)
                    }
                }
                is Token.Op -> {
                    require(token.symbol == "(") { "unexpected '${token.symbol}'" }
                    val inner = parseOr()
                    require(peekOp() == ")") { "missing ')'" }
                    pos++
                    inner
                }
            }
        }
    }
}
