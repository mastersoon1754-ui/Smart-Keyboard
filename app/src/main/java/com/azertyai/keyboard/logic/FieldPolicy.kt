package com.azertyai.keyboard.logic

enum class BoardMode { LETTERS, SYMBOLS, SYMBOLS_ALT, EMOJI, NUMBER, PHONE }

enum class EnterBehavior { NEWLINE, ACTION }

data class EnterPlan(
    val behavior: EnterBehavior,
    val actionId: Int,
    val label: String,
)

data class FieldPolicy(
    val board: BoardMode,
    val emailLike: Boolean,
    val canReadText: Boolean,
    val suggestions: Boolean,
    val enter: EnterPlan,
)

fun policyFor(inputType: Int, imeOptions: Int): FieldPolicy {
    val clazz = inputType and TYPE_MASK_CLASS
    val variation = inputType and TYPE_MASK_VARIATION
    val password = isPassword(clazz, variation)
    val noLearning = imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING != 0
    val canRead = !password && !noLearning
    val email = clazz == TYPE_CLASS_TEXT &&
        (variation == TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == TYPE_TEXT_VARIATION_WEB_EMAIL)
    val board = when (clazz) {
        TYPE_CLASS_NUMBER -> BoardMode.NUMBER
        TYPE_CLASS_PHONE -> BoardMode.PHONE
        else -> BoardMode.LETTERS
    }
    return FieldPolicy(
        board = board,
        emailLike = email,
        canReadText = canRead,
        suggestions = canRead && board == BoardMode.LETTERS,
        enter = enterPlan(inputType, imeOptions),
    )
}

fun enterPlan(inputType: Int, imeOptions: Int): EnterPlan {
    val multiline = inputType and TYPE_TEXT_FLAG_MULTI_LINE != 0
    val action = imeOptions and IME_MASK_ACTION
    val noEnterAction = imeOptions and IME_FLAG_NO_ENTER_ACTION != 0
    val explicit = !noEnterAction && action != IME_ACTION_UNSPECIFIED && action != IME_ACTION_NONE
    if (explicit) {
        return EnterPlan(EnterBehavior.ACTION, action, labelForAction(action))
    }
    if (!multiline) return EnterPlan(EnterBehavior.ACTION, IME_ACTION_DONE, "OK")
    return EnterPlan(EnterBehavior.NEWLINE, 0, "↵")
}

private fun isPassword(clazz: Int, variation: Int): Boolean = when (clazz) {
    TYPE_CLASS_NUMBER -> variation == TYPE_NUMBER_VARIATION_PASSWORD
    TYPE_CLASS_TEXT -> variation == TYPE_TEXT_VARIATION_PASSWORD ||
        variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
        variation == TYPE_TEXT_VARIATION_WEB_PASSWORD
    else -> false
}

private fun labelForAction(action: Int): String = when (action) {
    IME_ACTION_GO -> "Aller"
    IME_ACTION_SEARCH -> "Chercher"
    IME_ACTION_SEND -> "Envoyer"
    IME_ACTION_NEXT -> "Suivant"
    IME_ACTION_DONE -> "OK"
    else -> "OK"
}

private const val TYPE_MASK_CLASS = 0x0000000f
private const val TYPE_MASK_VARIATION = 0x00000ff0
private const val TYPE_CLASS_TEXT = 0x00000001
private const val TYPE_CLASS_NUMBER = 0x00000002
private const val TYPE_CLASS_PHONE = 0x00000003
private const val TYPE_TEXT_FLAG_MULTI_LINE = 0x00020000
private const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010
private const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020
private const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
private const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
private const val TYPE_TEXT_VARIATION_WEB_EMAIL = 0x000000d0
private const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
private const val IME_MASK_ACTION = 0x000000ff
private const val IME_ACTION_UNSPECIFIED = 0x00000000
private const val IME_ACTION_NONE = 0x00000001
private const val IME_ACTION_GO = 0x00000002
private const val IME_ACTION_SEARCH = 0x00000003
private const val IME_ACTION_SEND = 0x00000004
private const val IME_ACTION_NEXT = 0x00000005
private const val IME_ACTION_DONE = 0x00000006
private const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x00100000
private const val IME_FLAG_NO_ENTER_ACTION = 0x40000000
