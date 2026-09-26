package com.azertyai.keyboard.logic

import java.util.Locale

enum class ShiftMode { OFF, ONCE, LOCK }

class ShiftController {
    var mode: ShiftMode = ShiftMode.OFF
        private set
    private var heldByUser: Boolean = false
    private var userOverride: Boolean = false
    private var lastShiftTap: Long = 0L

    fun reset() {
        mode = ShiftMode.OFF
        heldByUser = false
        userOverride = false
        lastShiftTap = 0L
    }

    fun onShiftTap(now: Long): ShiftMode {
        if (mode == ShiftMode.LOCK) {
            mode = ShiftMode.OFF
            heldByUser = false
            userOverride = true
            lastShiftTap = now
            return mode
        }
        if (mode == ShiftMode.ONCE && heldByUser && now - lastShiftTap in 0..349) {
            mode = ShiftMode.LOCK
            heldByUser = true
            userOverride = false
            lastShiftTap = now
            return mode
        }
        if (mode == ShiftMode.ONCE) {
            mode = ShiftMode.OFF
            heldByUser = false
            userOverride = true
            lastShiftTap = now
            return mode
        }
        mode = ShiftMode.ONCE
        heldByUser = true
        userOverride = false
        lastShiftTap = now
        return mode
    }

    fun onShiftLongPress(): ShiftMode {
        mode = ShiftMode.LOCK
        heldByUser = true
        return mode
    }

    fun onLetterCommitted(textBeforeCursor: String, autoCap: Boolean) {
        if (mode == ShiftMode.LOCK) return
        heldByUser = false
        userOverride = false
        mode = if (autoCap && shouldAutoCapitalize(textBeforeCursor)) ShiftMode.ONCE else ShiftMode.OFF
    }

    fun sync(textBeforeCursor: String, autoCap: Boolean) {
        if (mode == ShiftMode.LOCK || heldByUser || userOverride) return
        mode = if (autoCap && shouldAutoCapitalize(textBeforeCursor)) ShiftMode.ONCE else ShiftMode.OFF
    }
}

fun shouldAutoCapitalize(before: String): Boolean {
    if (before.isEmpty() || before.last() == '\n') return true
    val trimmed = before.dropLastWhile { it == ' ' || it == '\n' || it == '\t' || it == '\u00A0' }
    if (trimmed.isEmpty()) return true
    return trimmed.last() in ".!?…"
}

fun applyCase(raw: String, shift: ShiftMode): String {
    if (shift == ShiftMode.OFF) return raw
    if (raw.any { it.isLetter() } && raw.all { it.isLetter() || it == '-' || it == '\'' || it == '’' }) {
        return raw.uppercase(Locale.FRENCH)
    }
    return raw
}

fun shouldConvertDoubleSpace(before: String): Boolean {
    if (before.length < 2 || !before.endsWith(' ')) return false
    val previous = before[before.length - 2]
    if (previous.isWhitespace() || previous in ".!?,;:") return false
    return previous.isLetterOrDigit() || previous in "»\"'’"
}

fun currentPrefix(before: String): String {
    val match = PREFIX.find(before) ?: return ""
    return match.value
}

fun suggestionEdit(before: String, suggestion: String): Pair<Int, String> {
    val prefix = currentPrefix(before)
    return prefix.length to (suggestion + " ")
}

fun dropLastCodePoint(value: String): String {
    if (value.isEmpty()) return value
    if (value.length >= 2 && Character.isSurrogatePair(value[value.length - 2], value.last())) {
        return value.dropLast(2)
    }
    return value.dropLast(1)
}

private val PREFIX = Regex("""[\p{L}\p{M}'’-]{1,40}$""")
