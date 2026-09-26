package com.azertyai.keyboard.logic

sealed interface AiTask {
    data class Question(val question: String) : AiTask
    data class Rewrite(val instruction: String, val source: String) : AiTask
    data class Continue(val source: String) : AiTask
    data class Translate(val source: String, val language: String) : AiTask
    data class Application(val notes: String?) : AiTask
    data class ReplyToText(val message: String) : AiTask
    data class Reply(val messages: List<String>, val instruction: String?) : AiTask
}

fun systemPrompt(): String = """
Tu es l'assistant d'écriture d'un clavier AZERTY français. Tu es bref, naturel et précis.

Règles :
- N'invente aucun fait personnel, diplôme, chiffre ou événement qui n'est pas fourni.
- Si la tâche est une rédaction ou une modification, renvoie uniquement le texte final, sans guillemets, sans titre et sans explication.
- Si la tâche est une question, réponds clairement en quelques phrases. Ce n'est pas un message à insérer.
- Respecte la langue demandée. Sinon, réponds dans la langue de l'utilisateur.
- N'ajoute pas de préambule comme « Bien sûr », « Voici » ou « Texte : ».
""".trim()

fun userPrompt(task: AiTask): String = when (task) {
    is AiTask.Question -> """
Question. Réponds sans proposer un texte à insérer.

${task.question.trim()}
    """.trim()

    is AiTask.Rewrite -> """
Consigne :
${task.instruction.trim()}
Renvoie uniquement le texte final.

Texte :
${task.source.trim()}
    """.trim()

    is AiTask.Continue -> """
Consigne :
Continue le texte naturellement, en quelques phrases au plus. Ne répète pas le texte source. Renvoie uniquement la suite.

Texte :
${task.source.trim()}
    """.trim()

    is AiTask.Translate -> """
Consigne :
Traduis le texte en ${task.language.trim()}. Renvoie uniquement la traduction.

Texte :
${task.source.trim()}
    """.trim()

    is AiTask.Application -> """
Consigne :
Rédige un message de candidature professionnel, concis et crédible, en français. N'invente aucune expérience, aucun diplôme et aucun chiffre absent des notes. S'il manque une information, reste général. Renvoie uniquement le message.

Notes :
${task.notes?.trim().orEmpty().ifBlank { "(aucune note)" }}
    """.trim()

    is AiTask.ReplyToText -> """
Consigne :
Rédige une réponse naturelle, adaptée et concise au message suivant. Renvoie uniquement le message à envoyer.

Message :
${task.message.trim()}
    """.trim()

    is AiTask.Reply -> {
        val lines = task.messages.mapIndexed { index, message ->
            "${index + 1}. ${message.trim()}"
        }.joinToString("\n")
        val extra = task.instruction?.trim()?.takeIf { it.isNotEmpty() }?.let {
            "\n\nPrécision de l'utilisateur :\n$it"
        }.orEmpty()
        """
Consigne :
Rédige une réponse naturelle et appropriée au dernier message de la conversation. Renvoie uniquement le message à envoyer.$extra

Messages, du plus ancien au plus récent :
$lines
        """.trim()
    }
}

fun cleanGenerated(text: String, mode: ResultMode): String {
    var value = text.trim().replace("\r\n", "\n")
    if (value.startsWith("```")) {
        value = value.removePrefix("```").removeSuffix("```").trim()
        val firstBreak = value.indexOf('\n')
        if (firstBreak in 0..16 && value.substring(0, firstBreak).all { it.isLetter() }) {
            value = value.substring(firstBreak + 1).trim()
        }
    }
    if (mode != ResultMode.ANSWER) {
        val quotePairs = listOf('"' to '"', '«' to '»', '“' to '”')
        for ((open, close) in quotePairs) {
            if (value.length >= 2 && value.first() == open && value.last() == close) {
                value = value.substring(1, value.length - 1).trim()
                break
            }
        }
    }
    return value.trim()
}

enum class FreeIntent { QUESTION, EDIT }

fun classifyFreeText(input: String): FreeIntent {
    val text = input.trim().lowercase()
    if (text.isEmpty()) return FreeIntent.QUESTION
    if (SOURCE_HINTS.any { text.contains(it) }) return FreeIntent.EDIT
    if (text.contains("plus professionnel") || text.contains("plus pro")) return FreeIntent.EDIT
    if (START_EDIT.containsMatchIn(text)) return FreeIntent.EDIT
    return FreeIntent.QUESTION
}

private val SOURCE_HINTS = listOf(
    "ce texte", "ce message", "cette phrase", "la sélection", "le champ",
    "this text", "this message", "the selection",
)

private val START_EDIT = Regex(
    """^(corrige|correct|réécris|reecris|rewrite|traduis|traduire|translate|raccourcis|raccourcir|shorten|reformule|améliore|ameliore|résume|resume|continue|poursuis|rends)\b""",
)
