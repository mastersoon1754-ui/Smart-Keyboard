package com.azertyai.keyboard.logic

enum class ActionId {
    INSERT,
    REPLACE,
    COPY,
    CONFIRM_CONTEXT,
    CLEAR_PREVIEW,
    DRAFT,
    USE_FIELD,
    CANCEL,
    BACK,
    REPLY_FIELD,
}

enum class ChipId { ACTION, LANGUAGE, COUNT, PASTE }

data class Chip(
    val id: ChipId,
    val label: String,
    val value: String,
)

val translateLanguages = listOf(
    "anglais",
    "espagnol",
    "allemand",
    "italien",
    "portugais",
    "français",
)

fun panelChips(state: AiPanelState): List<Chip> {
    if (state.busy || state.capturing) return emptyList()
    return when (state.screen) {
    Screen.HOME -> listOf(
        Chip(ChipId.ACTION, "Candidature", "application"),
        Chip(ChipId.ACTION, "Plus professionnel", "professional"),
        Chip(ChipId.ACTION, "Traduire", "translate"),
        Chip(ChipId.ACTION, "Répondre", "reply"),
        Chip(ChipId.ACTION, "Corriger", "correct"),
        Chip(ChipId.ACTION, "Raccourcir", "shorten"),
        Chip(ChipId.ACTION, "Continuer", "continue"),
        Chip(ChipId.ACTION, "Contexte", "context"),
    )
    Screen.TRANSLATE -> translateLanguages.map { language ->
        Chip(ChipId.LANGUAGE, language.replaceFirstChar { it.titlecase() }, language)
    }
    Screen.CONTEXT -> listOf(
        Chip(ChipId.COUNT, "5 messages", "5"),
        Chip(ChipId.COUNT, "10 messages", "10"),
        Chip(ChipId.COUNT, "30 messages", "30"),
        Chip(ChipId.PASTE, "Coller un extrait", "paste"),
    )
        Screen.APPLICATION, Screen.RESULT -> emptyList()
    }
}

fun panelActions(state: AiPanelState, env: Env = Env()): List<ActionId> {
    if (!state.visible) return emptyList()
    if (state.busy) return listOf(ActionId.CANCEL)
    return when (state.screen) {
        Screen.HOME -> emptyList()
        Screen.TRANSLATE -> listOf(ActionId.BACK)
        Screen.APPLICATION -> buildList {
            add(ActionId.DRAFT)
            if (env.canRead && env.field.sourceForEdit.isNotBlank()) add(ActionId.USE_FIELD)
            add(ActionId.BACK)
        }
        Screen.CONTEXT -> buildList {
            if (state.preview.isNotEmpty()) add(ActionId.CONFIRM_CONTEXT)
            if (env.canRead && env.field.sourceForEdit.isNotBlank()) add(ActionId.REPLY_FIELD)
            if (state.preview.isNotEmpty()) add(ActionId.CLEAR_PREVIEW)
            add(ActionId.BACK)
        }
        Screen.RESULT -> when (state.resultMode) {
            ResultMode.ANSWER -> listOf(ActionId.COPY, ActionId.BACK)
            ResultMode.INSERT, ResultMode.REPLACE -> buildList {
                if (state.resultMode == ResultMode.REPLACE && state.canReplace) add(ActionId.REPLACE)
                add(ActionId.INSERT)
                if (state.resultMode == ResultMode.INSERT && state.canReplace) add(ActionId.REPLACE)
                add(ActionId.COPY)
                add(ActionId.BACK)
            }
            null -> listOf(ActionId.BACK)
        }
    }
}

fun promptBarVisible(state: AiPanelState): Boolean {
    return state.visible && state.screen in setOf(Screen.HOME, Screen.APPLICATION, Screen.RESULT)
}

object PanelCopy {
    fun body(state: AiPanelState): String = when {
        state.busy -> "Rédaction…"
        state.capturing -> "Lecture de l'écran…"
        state.screen == Screen.RESULT && !state.result.isNullOrBlank() -> state.result
        state.screen == Screen.CONTEXT && state.preview.isNotEmpty() -> formatPreview(state.preview)
        state.screen == Screen.TRANSLATE ->
            "Choisissez une langue. Le texte du champ sera traduit. Rien n'est modifié tant que vous ne remplacez pas."
        state.screen == Screen.APPLICATION ->
            "Décrivez le poste, l'entreprise et deux atouts dans la ligne du bas, puis rédigez."
        else -> noteText(state.note)
    }

    fun status(state: AiPanelState): String? = when {
        state.screen == Screen.RESULT && state.note is Note.Info -> state.note.text
        state.screen == Screen.CONTEXT && state.preview.isNotEmpty() ->
            "Rien n'a été envoyé. Vérifiez, puis confirmez."
        state.toPrompt && promptBarVisible(state) -> "Saisie pour l'assistant"
        else -> null
    }
}

private fun formatPreview(messages: List<String>): String {
    return messages.mapIndexed { index, message -> "${index + 1}. $message" }.joinToString("\n\n")
}

private fun noteText(note: Note): String = when (note) {
    Note.Hint -> "Posez une question, ou agissez sur le texte déjà écrit. Rien n'est envoyé sans votre action."
    Note.EmptyField -> "Écrivez d'abord dans le champ, ou sélectionnez le passage à modifier."
    Note.Sensitive -> "Ce champ est masqué. L'assistant peut répondre à une question, pas lire son contenu."
    Note.ContextDisabled ->
        "L'analyse de contexte est désactivée. Activez-la dans l'application, puis choisissez 5, 10 ou 30 messages."
    Note.NeedAccessibility ->
        "Pour lire la conversation affichée, activez le service d'accessibilité AZERTY. Vous pourrez aussi coller un extrait."
    Note.CheckPreview -> "Rien n'a été envoyé. Vérifiez, puis confirmez."
    Note.NoMessages -> "Aucun message lisible. Faites défiler la conversation, ou collez un extrait."
    Note.Cancelled -> "Annulé. Aucun texte n'a été modifié."
    is Note.Info -> note.text
}
