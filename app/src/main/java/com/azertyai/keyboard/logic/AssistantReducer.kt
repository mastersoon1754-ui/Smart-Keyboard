package com.azertyai.keyboard.logic

enum class Screen { HOME, TRANSLATE, CONTEXT, APPLICATION, RESULT }

enum class ResultMode { ANSWER, INSERT, REPLACE }

sealed interface Note {
    data object Hint : Note
    data object EmptyField : Note
    data object Sensitive : Note
    data object ContextDisabled : Note
    data object NeedAccessibility : Note
    data object CheckPreview : Note
    data object NoMessages : Note
    data object Cancelled : Note
    data class Info(val text: String) : Note
}

data class AiPanelState(
    val visible: Boolean = false,
    val screen: Screen = Screen.HOME,
    val prompt: String = "",
    val toPrompt: Boolean = false,
    val busy: Boolean = false,
    val capturing: Boolean = false,
    val note: Note = Note.Hint,
    val result: String? = null,
    val resultMode: ResultMode? = null,
    val canReplace: Boolean = false,
    val replaceSelectionOnly: Boolean = false,
    val preview: List<String> = emptyList(),
    val requestId: Int = 0,
)

data class Env(
    val field: FieldSnapshot = FieldSnapshot("", "", null),
    val canRead: Boolean = true,
    val contextEnabled: Boolean = false,
    val accessibilityOn: Boolean = false,
)

sealed interface Event {
    data object Open : Event
    data object Close : Event
    data object FocusPrompt : Event
    data object TargetField : Event
    data class Type(val text: String) : Event
    data object Backspace : Event
    data object Professional : Event
    data object Correct : Event
    data object Shorten : Event
    data object Continue : Event
    data object OpenTranslate : Event
    data class ChooseLanguage(val language: String) : Event
    data object OpenApplication : Event
    data object UseFieldNotes : Event
    data object DraftApplication : Event
    data object OpenContext : Event
    data class ChooseContext(val count: Int) : Event
    data class Captured(val messages: List<String>) : Event
    data object RequestPaste : Event
    data class Pasted(val text: String) : Event
    data object ReplyToField : Event
    data object ConfirmPreview : Event
    data object ClearPreview : Event
    data object SendPrompt : Event
    data class Completed(
        val requestId: Int,
        val text: String,
        val mode: ResultMode,
        val canReplace: Boolean,
    ) : Event
    data class Failed(val requestId: Int, val message: String) : Event
    data object CancelBusy : Event
    data object BackToHome : Event
    data class Mark(val message: String) : Event
    data object InsertResult : Event
    data object ReplaceResult : Event
    data object CopyResult : Event
}

sealed interface Effect {
    data class Complete(val requestId: Int, val task: AiTask, val mode: ResultMode) : Effect
    data class Capture(val count: Int) : Effect
    data object Paste : Effect
}

private val allowedContextCounts = setOf(5, 10, 30)

fun reduce(state: AiPanelState, event: Event, env: Env = Env()): Pair<AiPanelState, Effect?> {
    if (state.busy && event !is Event.Completed && event !is Event.Failed &&
        event !is Event.CancelBusy && event != Event.Close
    ) {
        return state to null
    }
    return when (event) {
        Event.Open -> cleared().copy(visible = true, requestId = state.requestId + 1) to null
        Event.Close -> cleared().copy(requestId = state.requestId + 1) to null
        Event.FocusPrompt -> state.copy(toPrompt = true) to null
        Event.TargetField -> state.copy(toPrompt = false) to null
        is Event.Type -> {
            if (!state.visible || !state.toPrompt) state to null
            else state.copy(prompt = state.prompt + event.text, toPrompt = true) to null
        }
        Event.Backspace -> {
            if (!state.toPrompt) state to null
            else state.copy(prompt = dropLastCodePoint(state.prompt)) to null
        }
        Event.Professional -> rewrite(
            state,
            env,
            "Réécris ce texte dans un registre professionnel, clair et naturel. Garde le sens.",
        )
        Event.Correct -> rewrite(
            state,
            env,
            "Corrige l'orthographe, la grammaire et la ponctuation. Ne change ni le sens ni le ton.",
        )
        Event.Shorten -> rewrite(
            state,
            env,
            "Raccourcis ce texte en conservant l'essentiel.",
        )
        Event.Continue -> {
            val source = readableSource(env) ?: return blocked(state, env)
            val id = state.requestId + 1
            state.copy(
                busy = true,
                requestId = id,
                screen = Screen.RESULT,
                result = null,
                note = Note.Hint,
                replaceSelectionOnly = env.field.selected.isNullOrBlank().not(),
            ) to Effect.Complete(id, AiTask.Continue(source), ResultMode.INSERT)
        }
        Event.OpenTranslate -> state.copy(screen = Screen.TRANSLATE, note = Note.Hint, toPrompt = false) to null
        is Event.ChooseLanguage -> {
            val source = readableSource(env) ?: return blocked(state, env)
            val id = state.requestId + 1
            state.copy(
                busy = true,
                requestId = id,
                screen = Screen.RESULT,
                result = null,
                replaceSelectionOnly = env.field.selected.isNullOrBlank().not(),
            ) to Effect.Complete(id, AiTask.Translate(source, event.language), ResultMode.REPLACE)
        }
        Event.OpenApplication -> state.copy(
            screen = Screen.APPLICATION,
            toPrompt = true,
            note = Note.Hint,
        ) to null
        Event.UseFieldNotes -> {
            if (!env.canRead) return state.copy(note = Note.Sensitive) to null
            val source = env.field.sourceForEdit.trim()
            if (source.isEmpty()) return state.copy(note = Note.EmptyField) to null
            state.copy(prompt = source, toPrompt = true, screen = Screen.APPLICATION, note = Note.Hint) to null
        }
        Event.DraftApplication -> {
            val notes = state.prompt.trim().ifBlank { null }
            val id = state.requestId + 1
            state.copy(busy = true, requestId = id, screen = Screen.RESULT, result = null) to
                Effect.Complete(id, AiTask.Application(notes), ResultMode.INSERT)
        }
        Event.OpenContext -> state.copy(screen = Screen.CONTEXT, note = Note.Hint, capturing = false) to null
        is Event.ChooseContext -> chooseContext(state, env, event.count)
        is Event.Captured -> {
            if (!state.capturing) return state to null
            if (event.messages.isEmpty()) {
                state.copy(capturing = false, preview = emptyList(), note = Note.NoMessages) to null
            } else {
                state.copy(capturing = false, preview = event.messages, note = Note.CheckPreview) to null
            }
        }
        Event.RequestPaste -> state.copy(capturing = false) to Effect.Paste
        is Event.Pasted -> {
            if (!state.visible) return state to null
            val text = event.text.trim()
            if (text.isEmpty()) {
                state.copy(note = Note.NoMessages, preview = emptyList()) to null
            } else {
                state.copy(
                    screen = Screen.CONTEXT,
                    preview = listOf(text.take(4000)),
                    note = Note.CheckPreview,
                    capturing = false,
                ) to null
            }
        }
        Event.ReplyToField -> {
            val source = readableSource(env) ?: return blocked(state, env)
            val id = state.requestId + 1
            state.copy(busy = true, requestId = id, screen = Screen.RESULT, result = null) to
                Effect.Complete(id, AiTask.ReplyToText(source), ResultMode.INSERT)
        }
        Event.ConfirmPreview -> confirmPreview(state)
        Event.ClearPreview -> state.copy(preview = emptyList(), note = Note.Hint, capturing = false) to null
        Event.SendPrompt -> sendPrompt(state, env)
        is Event.Completed -> complete(state, event)
        is Event.Failed -> {
            if (event.requestId != state.requestId) state to null
            else state.copy(busy = false, capturing = false, note = Note.Info(event.message), result = null) to null
        }
        Event.CancelBusy -> state.copy(
            busy = false,
            capturing = false,
            requestId = state.requestId + 1,
            note = Note.Cancelled,
        ) to null
        Event.BackToHome -> state.copy(
            screen = Screen.HOME,
            busy = false,
            capturing = false,
            result = null,
            resultMode = null,
            note = Note.Hint,
            toPrompt = false,
        ) to null
        is Event.Mark -> state.copy(note = Note.Info(event.message)) to null
        Event.InsertResult, Event.ReplaceResult, Event.CopyResult -> state to null
    }
}

private fun chooseContext(state: AiPanelState, env: Env, count: Int): Pair<AiPanelState, Effect?> {
    if (count !in allowedContextCounts) return state to null
    if (!env.contextEnabled) {
        return state.copy(screen = Screen.CONTEXT, capturing = false, note = Note.ContextDisabled) to null
    }
    if (!env.accessibilityOn) {
        return state.copy(screen = Screen.CONTEXT, capturing = false, note = Note.NeedAccessibility) to null
    }
    return state.copy(
        screen = Screen.CONTEXT,
        capturing = true,
        preview = emptyList(),
        note = Note.Hint,
    ) to Effect.Capture(count)
}

private fun confirmPreview(state: AiPanelState): Pair<AiPanelState, Effect?> {
    if (state.preview.isEmpty()) return state.copy(note = Note.NoMessages) to null
    val id = state.requestId + 1
    val instruction = state.prompt.trim().ifBlank { null }
    return state.copy(busy = true, requestId = id, screen = Screen.RESULT, capturing = false, result = null) to
        Effect.Complete(id, AiTask.Reply(state.preview, instruction), ResultMode.INSERT)
}

private fun sendPrompt(state: AiPanelState, env: Env): Pair<AiPanelState, Effect?> {
    val prompt = state.prompt.trim()
    if (prompt.isEmpty()) return state to null
    if (state.screen == Screen.APPLICATION) {
        return reduce(state, Event.DraftApplication, env)
    }
    return when (classifyFreeText(prompt)) {
        FreeIntent.QUESTION -> {
            val id = state.requestId + 1
            state.copy(busy = true, requestId = id, screen = Screen.RESULT, result = null) to
                Effect.Complete(id, AiTask.Question(prompt), ResultMode.ANSWER)
        }
        FreeIntent.EDIT -> {
            val source = readableSource(env) ?: return blocked(state, env)
            val id = state.requestId + 1
            state.copy(
                busy = true,
                requestId = id,
                screen = Screen.RESULT,
                result = null,
                replaceSelectionOnly = env.field.selected.isNullOrBlank().not(),
            ) to Effect.Complete(id, AiTask.Rewrite(prompt, source), ResultMode.REPLACE)
        }
    }
}

private fun complete(state: AiPanelState, event: Event.Completed): Pair<AiPanelState, Effect?> {
    if (event.requestId != state.requestId) return state to null
    val text = event.text.trim()
    if (text.isEmpty()) {
        return state.copy(busy = false, note = Note.Info("Réponse vide."), result = null) to null
    }
    return state.copy(
        busy = false,
        capturing = false,
        screen = Screen.RESULT,
        result = text,
        resultMode = event.mode,
        canReplace = event.mode != ResultMode.ANSWER && event.canReplace,
        replaceSelectionOnly = state.replaceSelectionOnly && event.mode != ResultMode.ANSWER,
        preview = emptyList(),
        note = Note.Hint,
    ) to null
}

private fun rewrite(state: AiPanelState, env: Env, instruction: String): Pair<AiPanelState, Effect?> {
    val source = readableSource(env) ?: return blocked(state, env)
    val id = state.requestId + 1
    return state.copy(
        busy = true,
        requestId = id,
        screen = Screen.RESULT,
        result = null,
        note = Note.Hint,
        replaceSelectionOnly = env.field.selected.isNullOrBlank().not(),
    ) to Effect.Complete(id, AiTask.Rewrite(instruction, source), ResultMode.REPLACE)
}

private fun readableSource(env: Env): String? {
    if (!env.canRead) return null
    return env.field.sourceForEdit.trim().ifBlank { null }
}

private fun blocked(state: AiPanelState, env: Env): Pair<AiPanelState, Effect?> {
    val note = if (!env.canRead) Note.Sensitive else Note.EmptyField
    return state.copy(note = note, busy = false) to null
}

private fun cleared(): AiPanelState = AiPanelState()
