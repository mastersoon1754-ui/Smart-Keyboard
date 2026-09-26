package com.azertyai.keyboard.logic

enum class KeyAction {
    SHIFT,
    BACKSPACE,
    ENTER,
    SYMBOLS,
    LETTERS,
    EMOJI,
    SPACE,
    SYMBOLS_ALT,
}

enum class KeyStyle { LETTER, SPECIAL, SPACE, ACTION }

data class SoftKey(
    val text: String? = null,
    val action: KeyAction? = null,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.LETTER,
)

fun boardRows(mode: BoardMode, email: Boolean = false): List<List<SoftKey>> = when (mode) {
    BoardMode.LETTERS -> letters(email)
    BoardMode.SYMBOLS -> symbols()
    BoardMode.SYMBOLS_ALT -> symbolsAlt()
    BoardMode.EMOJI -> emoji()
    BoardMode.NUMBER -> numberPad()
    BoardMode.PHONE -> phonePad()
}

fun alternates(label: String): List<String> = ALTERNATES[label.lowercase()] ?: emptyList()

private fun letters(email: Boolean): List<List<SoftKey>> = listOf(
    row("a z e r t y u i o p"),
    row("q s d f g h j k l m"),
    listOf(special(KeyAction.SHIFT, "⇧", 1.5f)) + row("w x c v b n '") +
        listOf(special(KeyAction.BACKSPACE, "⌫", 1.5f)),
    bottom(KeyAction.SYMBOLS, "?123", if (email) key("@") else key(",")),
)

private fun symbols(): List<List<SoftKey>> = listOf(
    row("1 2 3 4 5 6 7 8 9 0"),
    row("@ # € _ & - + ( ) /"),
    listOf(special(KeyAction.SYMBOLS_ALT, "#+=", 1.5f)) + row("* \" ' : ; ! ?") +
        listOf(special(KeyAction.BACKSPACE, "⌫", 1.5f)),
    bottom(KeyAction.LETTERS, "ABC", key(",")),
)

private fun symbolsAlt(): List<List<SoftKey>> = listOf(
    row("~ ` | • √ π ÷ × ¶ ∆"),
    row("£ ¥ $ ¢ ^ ° = { } \\"),
    listOf(special(KeyAction.SYMBOLS, "123", 1.5f)) + row("% © ® ™ ✓ [ ]") +
        listOf(special(KeyAction.BACKSPACE, "⌫", 1.5f)),
    bottom(KeyAction.LETTERS, "ABC", key(",")),
)

private fun emoji(): List<List<SoftKey>> = listOf(
    row("😀 😂 😊 😉 😍 😘 😎 🤔"),
    row("😅 😢 😡 👍 👎 🙏 👏 🔥"),
    row("❤️ 💙 ✨ 🎉 ✅ ❌ 💬 👋"),
    row("🇫🇷 💼 📧 📅 ⏰ 🌟 🤝 💡"),
    bottom(KeyAction.LETTERS, "ABC", key(",")),
)

private fun numberPad(): List<List<SoftKey>> = listOf(
    listOf(key("1"), key("2"), key("3"), special(KeyAction.BACKSPACE, "⌫", 1.3f)),
    listOf(key("4"), key("5"), key("6"), key("-", 1.3f)),
    listOf(key("7"), key("8"), key("9"), key(".", 1.3f)),
    listOf(
        special(KeyAction.LETTERS, "ABC", 1.3f),
        key("0"),
        key(","),
        special(KeyAction.ENTER, "↵", 1.3f, KeyStyle.ACTION),
    ),
)

private fun phonePad(): List<List<SoftKey>> = listOf(
    listOf(key("1"), key("2"), key("3")),
    listOf(key("4"), key("5"), key("6")),
    listOf(key("7"), key("8"), key("9")),
    listOf(key("*"), key("0"), key("#")),
    listOf(
        special(KeyAction.LETTERS, "ABC", 1.2f),
        key("+", 1.2f),
        special(KeyAction.BACKSPACE, "⌫", 1.2f),
        special(KeyAction.ENTER, "↵", 1.4f, KeyStyle.ACTION),
    ),
)

private fun bottom(modeAction: KeyAction, modeLabel: String, middle: SoftKey): List<SoftKey> = listOf(
    special(modeAction, modeLabel, 1.45f),
    special(KeyAction.EMOJI, "☺", 1.15f),
    middle,
    special(KeyAction.SPACE, "", 4.3f, KeyStyle.SPACE),
    key("."),
    special(KeyAction.ENTER, "↵", 1.45f, KeyStyle.ACTION),
)

private fun row(spec: String): List<SoftKey> = spec.split(' ').map { key(it) }

private fun key(text: String, weight: Float = 1f) = SoftKey(text = text, weight = weight)

private fun special(
    action: KeyAction,
    label: String,
    weight: Float,
    style: KeyStyle = KeyStyle.SPECIAL,
) = SoftKey(text = label, action = action, weight = weight, style = style)

private val ALTERNATES = mapOf(
    "a" to listOf("à", "â", "ä", "æ", "á", "ã"),
    "e" to listOf("é", "è", "ê", "ë", "€"),
    "i" to listOf("î", "ï", "í", "ì"),
    "o" to listOf("ô", "ö", "œ", "ó", "ò"),
    "u" to listOf("ù", "û", "ü", "ú"),
    "y" to listOf("ÿ"),
    "c" to listOf("ç"),
    "n" to listOf("ñ"),
    "'" to listOf("’", "‘", "«", "»"),
    "." to listOf(",", "?", "!", "…", ".fr", ".com"),
    "," to listOf(";", ":", "«", "»"),
    "?" to listOf("¿"),
    "!" to listOf("¡"),
    "-" to listOf("–", "—", "_"),
    "/" to listOf("\\"),
    "$" to listOf("€", "£", "¥", "¢"),
    "0" to listOf("°"),
    "@" to listOf(".com", ".fr"),
)
