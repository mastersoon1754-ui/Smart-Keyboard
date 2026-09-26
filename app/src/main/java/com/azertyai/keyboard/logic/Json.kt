package com.azertyai.keyboard.logic

sealed interface JsonValue {
    data class Obj(val map: Map<String, JsonValue>) : JsonValue
    data class Arr(val list: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val raw: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

operator fun JsonValue.get(key: String): JsonValue? = (this as? JsonValue.Obj)?.map[key]

fun JsonValue.asString(): String? = (this as? JsonValue.Str)?.value

fun JsonValue.asArray(): List<JsonValue>? = (this as? JsonValue.Arr)?.list

fun toJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> jsonString(value)
    is Boolean -> if (value) "true" else "false"
    is Int, is Long -> value.toString()
    is Double -> value.toString()
    is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, child) ->
        jsonString(key.toString()) + ":" + toJson(child)
    }
    is List<*> -> value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
    else -> error("Type JSON non pris en charge: ${value::class.java.simpleName}")
}

fun jsonString(value: String): String {
    val out = StringBuilder(value.length + 2)
    out.append('"')
    for (ch in value) {
        when (ch) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (ch.code < 0x20) {
                out.append("\\u")
                out.append(ch.code.toString(16).padStart(4, '0'))
            } else {
                out.append(ch)
            }
        }
    }
    out.append('"')
    return out.toString()
}

fun parseJson(source: String): JsonValue = JsonParser(source).parse()

private class JsonParser(private val source: String) {
    private var index = 0

    fun parse(): JsonValue {
        val value = readValue()
        skip()
        if (index != source.length) fail("caractères en trop")
        return value
    }

    private fun readValue(): JsonValue {
        skip()
        if (index >= source.length) fail("valeur manquante")
        return when (val ch = source[index]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> JsonValue.Str(readString())
            't' -> literal("true", JsonValue.Bool(true))
            'f' -> literal("false", JsonValue.Bool(false))
            'n' -> literal("null", JsonValue.Null)
            '-', in '0'..'9' -> readNumber()
            else -> fail("caractère inattendu $ch")
        }
    }

    private fun readObject(): JsonValue.Obj {
        expect('{')
        val map = linkedMapOf<String, JsonValue>()
        skip()
        if (peek('}')) {
            index++
            return JsonValue.Obj(map)
        }
        while (true) {
            skip()
            val key = readString()
            skip()
            expect(':')
            map[key] = readValue()
            skip()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    break
                }
                else -> fail("objet mal formé")
            }
        }
        return JsonValue.Obj(map)
    }

    private fun readArray(): JsonValue.Arr {
        expect('[')
        val list = mutableListOf<JsonValue>()
        skip()
        if (peek(']')) {
            index++
            return JsonValue.Arr(list)
        }
        while (true) {
            list += readValue()
            skip()
            when {
                peek(',') -> index++
                peek(']') -> {
                    index++
                    break
                }
                else -> fail("tableau mal formé")
            }
        }
        return JsonValue.Arr(list)
    }

    private fun readString(): String {
        expect('"')
        val out = StringBuilder()
        while (index < source.length) {
            val ch = source[index++]
            when (ch) {
                '"' -> return out.toString()
                '\\' -> out.append(readEscape())
                else -> out.append(ch)
            }
        }
        fail("chaîne non terminée")
    }

    private fun readEscape(): Char {
        if (index >= source.length) fail("échappement tronqué")
        return when (val ch = source[index++]) {
            '"', '\\', '/' -> ch
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (index + 4 > source.length) fail("unicode tronqué")
                val hex = source.substring(index, index + 4)
                index += 4
                hex.toInt(16).toChar()
            }
            else -> fail("échappement inconnu")
        }
    }

    private fun readNumber(): JsonValue.Num {
        val start = index
        if (peek('-')) index++
        while (index < source.length && source[index].isDigit()) index++
        if (peek('.')) {
            index++
            while (index < source.length && source[index].isDigit()) index++
        }
        if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
            index++
            if (peek('+') || peek('-')) index++
            while (index < source.length && source[index].isDigit()) index++
        }
        if (start == index) fail("nombre invalide")
        return JsonValue.Num(source.substring(start, index))
    }

    private fun literal(text: String, value: JsonValue): JsonValue {
        if (!source.regionMatches(index, text, 0, text.length)) fail("littéral $text")
        index += text.length
        return value
    }

    private fun skip() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    private fun expect(ch: Char) {
        skip()
        if (index >= source.length || source[index] != ch) fail("« $ch » attendu")
        index++
    }

    private fun peek(ch: Char): Boolean = index < source.length && source[index] == ch

    private fun fail(message: String): Nothing {
        throw IllegalArgumentException("JSON: $message à l'index $index")
    }
}
