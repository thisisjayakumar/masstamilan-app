package com.masstamilan.app.core.util

/**
 * Tiny dependency-free JSON reader/writer.
 *
 * Replaces org.json (an Android stub that throws "not mocked" in local JVM unit
 * tests) and is the single JSON entry point for scraping + library import/export.
 *
 * Values map to: [Map], [List], [String], [Double], [Boolean], null.
 */
object Json {

    fun parse(text: String): Any? = Reader(text.trim()).run {
        ws()
        val v = value()
        ws()
        if (i < s.length) throw IllegalArgumentException("Trailing data at $i")
        v
    }

    /** Array of flat objects with stringified scalars — the site's scrape shape. */
    @Suppress("UNCHECKED_CAST")
    fun parseArrayOfObjects(text: String): List<Map<String, String>> {
        val arr = parse(text) as? List<*> ?: throw IllegalArgumentException("Expected JSON array")
        return arr.map { item ->
            (item as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to scalar(v) }
                ?: throw IllegalArgumentException("Expected JSON objects")
        }
    }

    fun write(value: Any?): String = StringBuilder().also { out(value, it) }.toString()

    private fun scalar(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        is Map<*, *>, is List<*> -> ""
        else -> value.toString()
    }

    private fun out(value: Any?, sb: StringBuilder) {
        when (value) {
            null -> sb.append("null")
            is String, is Number, is Boolean -> sb.append(
                if (value is String) quote(value) else scalar(value)
            )
            is Map<*, *> -> {
                sb.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(',')
                    sb.append(quote(k.toString())).append(':')
                    out(v, sb)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    out(v, sb)
                }
                sb.append(']')
            }
            is Array<*> -> out(value.toList(), sb)
            else -> sb.append(quote(value.toString()))
        }
    }

    private fun quote(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private class Reader(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            if (i >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        fun obj(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            ws()
            if (consume('}')) return map
            while (true) {
                ws()
                val key = str()
                ws()
                expect(':')
                ws()
                map[key] = value()
                ws()
                when {
                    consume(',') -> continue
                    consume('}') -> break
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $i")
                }
            }
            return map
        }

        fun arr(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            ws()
            if (consume(']')) return list
            while (true) {
                ws()
                list.add(value())
                ws()
                when {
                    consume(',') -> continue
                    consume(']') -> break
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $i")
                }
            }
            return list
        }

        fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("Bad literal at $i")
            i += word.length
            return result
        }

        fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
            if (start == i) throw IllegalArgumentException("Bad value at $i")
            return s.substring(start, i).toDoubleOrNull()
                ?: throw IllegalArgumentException("Bad number at $start")
        }

        fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("Unterminated string")
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw IllegalArgumentException("Bad escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("Bad \\u escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun consume(c: Char): Boolean = if (i < s.length && s[i] == c) {
            i++
            true
        } else {
            false
        }

        fun expect(c: Char) {
            if (!consume(c)) throw IllegalArgumentException("Expected '$c' at $i")
        }
    }
}
