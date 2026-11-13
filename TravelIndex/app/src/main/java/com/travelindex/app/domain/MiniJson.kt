package com.travelindex.app.domain

/**
 * JSON mínimo, sin dependencias de Android (así se puede probar en JVM puro).
 * Tipos: Map<String, Any?>, List<Any?>, String, Long, Double, Boolean, null.
 */
object MiniJson {
    private const val MAX_DEPTH = 40

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value(0)
        p.skipWs()
        require(p.atEnd()) { "JSON: texto sobrante en la posición ${p.pos}" }
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        (parse(text) as? Map<String, Any?>) ?: throw IllegalArgumentException("JSON: se esperaba un objeto")

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v.toString())
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> sb.append(if (v.isNaN() || v.isInfinite()) "null" else v.toString())
            is Float -> sb.append(if (v.isNaN() || v.isInfinite()) "null" else v.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString()); sb.append(':'); write(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) { if (!first) sb.append(','); first = false; write(sb, x) }
                sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        var pos = 0
        fun atEnd() = pos >= s.length

        fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }

        fun value(depth: Int): Any? {
            require(depth <= MAX_DEPTH) { "JSON: demasiado anidado" }
            require(!atEnd()) { "JSON: fin inesperado" }
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("JSON: carácter inesperado '$c' en $pos")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            require(s.startsWith(word, pos)) { "JSON: valor no válido en $pos" }
            pos += word.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return m }
            while (true) {
                skipWs()
                require(pos < s.length && s[pos] == '"') { "JSON: se esperaba una clave en $pos" }
                val k = str()
                skipWs()
                require(pos < s.length && s[pos] == ':') { "JSON: falta ':' en $pos" }
                pos++
                skipWs()
                m[k] = value(depth + 1)
                skipWs()
                require(pos < s.length) { "JSON: objeto sin cerrar" }
                if (s[pos] == ',') { pos++; continue }
                require(s[pos] == '}') { "JSON: se esperaba ',' o '}' en $pos" }
                pos++
                return m
            }
        }

        private fun arr(depth: Int): List<Any?> {
            val l = ArrayList<Any?>()
            pos++ // [
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return l }
            while (true) {
                skipWs()
                l += value(depth + 1)
                skipWs()
                require(pos < s.length) { "JSON: lista sin cerrar" }
                if (s[pos] == ',') { pos++; continue }
                require(s[pos] == ']') { "JSON: se esperaba ',' o ']' en $pos" }
                pos++
                return l
            }
        }

        private fun str(): String {
            val sb = StringBuilder()
            pos++ // "
            while (true) {
                require(pos < s.length) { "JSON: cadena sin cerrar" }
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(pos < s.length) { "JSON: escape incompleto" }
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                require(pos + 4 <= s.length) { "JSON: \\u incompleto" }
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar()); pos += 4
                            }
                            else -> throw IllegalArgumentException("JSON: escape no válido '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val t = s.substring(start, pos)
            return if (t.any { it == '.' || it == 'e' || it == 'E' }) t.toDouble() else (t.toLongOrNull() ?: t.toDouble())
        }
    }
}
