package com.azertyai.keyboard.ime

import android.content.ClipData
import android.content.ClipDescription
import android.media.AudioManager
import android.os.Build
import android.os.PersistableBundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.content.SharedPreferences
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.azertyai.keyboard.AzertyApplication
import com.azertyai.keyboard.context.ChatAccessibilityService
import com.azertyai.keyboard.data.AppGraph
import com.azertyai.keyboard.data.KeyHeight
import com.azertyai.keyboard.databinding.InputRootBinding
import com.azertyai.keyboard.keyboard.KeyboardView
import com.azertyai.keyboard.keyboard.paletteFor
import com.azertyai.keyboard.logic.AiOutcome
import com.azertyai.keyboard.logic.AiPanelState
import com.azertyai.keyboard.logic.Effect
import com.azertyai.keyboard.logic.EnterBehavior
import com.azertyai.keyboard.logic.Env
import com.azertyai.keyboard.logic.Event
import com.azertyai.keyboard.logic.FieldSnapshot
import com.azertyai.keyboard.logic.ResultMode
import com.azertyai.keyboard.logic.ShiftController
import com.azertyai.keyboard.logic.applyCase
import com.azertyai.keyboard.logic.currentPrefix
import com.azertyai.keyboard.logic.extractMessages
import com.azertyai.keyboard.logic.policyFor
import com.azertyai.keyboard.logic.reduce
import com.azertyai.keyboard.logic.shouldConvertDoubleSpace
import com.azertyai.keyboard.logic.suggestionEdit
import com.azertyai.keyboard.system.accessibilityEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.inputmethodservice.InputMethodService
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.CancellationException

class AzertyInputMethodService : InputMethodService(), KeyboardView.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val shift = ShiftController()
    private var panel = AiPanelState()
    private var policy = policyFor(EditorInfo.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NONE)
    private var running: Job? = null
    private lateinit var graph: AppGraph
    private var binding: InputRootBinding? = null
    private var keyboard: KeyboardView? = null
    private var binder: AiPanelBinder? = null
    private var backRegistered = false
    private var backCallback: Any? = null
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate() {
        super.onCreate()
        graph = (application as AzertyApplication).graph
        prefListener = graph.preferences.listen { if (binding != null) render() }
    }

    override fun onDestroy() {
        running?.cancel()
        prefListener?.let { graph.preferences.unlisten(it) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onUpdateExtractingVisibility(ei: EditorInfo?) {
        isExtractViewShown = false
    }

    override fun onCreateInputView(): View {
        val root = InputRootBinding.inflate(layoutInflater)
        binding = root
        val keys = KeyboardView(this).also { it.listener = this }
        keyboard = keys
        root.keyboardSlot.addView(keys)
        binder = AiPanelBinder(root, ::onPanel)
        ViewCompat.setOnApplyWindowInsetsListener(root.root) { _, insets ->
            keys.bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            insets
        }
        ViewCompat.requestApplyInsets(root.root)
        render()
        return root.root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        attribute?.let { policy = policyFor(it.inputType, it.imeOptions) }
        if (!restarting) {
            shift.reset()
            keyboard?.setBoard(policy.board, policy.emailLike)
            onPanel(Event.Close)
        }
        refreshShiftAndSuggestions()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        info?.let { policy = policyFor(it.inputType, it.imeOptions) }
        render()
        refreshShiftAndSuggestions()
    }

    override fun onFinishInput() {
        running?.cancel()
        onPanel(Event.Close)
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!panel.toPrompt) refreshShiftAndSuggestions()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && panel.visible) {
            onPanel(Event.Close)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onText(raw: String) {
        val text = applyCase(raw, shift.mode)
        if (panel.visible && panel.toPrompt) {
            if (text == " " && graph.preferences.doubleSpace && shouldConvertDoubleSpace(panel.prompt)) {
                onPanel(Event.Backspace)
                onPanel(Event.Type(". "))
            } else {
                onPanel(Event.Type(text))
            }
            afterBufferCommit(text, panel.prompt)
            return
        }
        val input = currentInputConnection ?: return
        if (text == " " && graph.preferences.doubleSpace) {
            val before = input.getTextBeforeCursor(80, 0)?.toString().orEmpty()
            if (shouldConvertDoubleSpace(before)) {
                input.beginBatchEdit()
                input.deleteSurroundingText(1, 0)
                input.commitText(". ", 1)
                input.endBatchEdit()
                afterBufferCommit(". ", before.dropLast(1) + ". ")
                return
            }
        }
        input.commitText(text, 1)
        val before = input.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        afterBufferCommit(text, before)
    }

    override fun onShift() {
        shift.onShiftTap(System.currentTimeMillis())
        keyboard?.shift = shift.mode
    }

    override fun onShiftLock() {
        shift.onShiftLongPress()
        keyboard?.shift = shift.mode
    }

    override fun onBackspace() {
        if (panel.visible && panel.toPrompt) {
            onPanel(Event.Backspace)
            refreshShiftAndSuggestions()
            return
        }
        val input = currentInputConnection ?: return
        val selected = input.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            input.commitText("", 1)
        } else {
            val before = input.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.isNotEmpty()) {
                val count = if (
                    before.length >= 2 &&
                    Character.isSurrogatePair(before[before.length - 2], before[before.length - 1])
                ) {
                    2
                } else {
                    1
                }
                input.deleteSurroundingText(count, 0)
            }
        }
        refreshShiftAndSuggestions()
    }

    override fun onEnter() {
        if (panel.visible && panel.toPrompt && panel.prompt.isNotBlank()) {
            onPanel(Event.SendPrompt)
            return
        }
        val input = currentInputConnection ?: return
        if (policy.enter.behavior == EnterBehavior.NEWLINE) {
            onText("\n")
        } else {
            input.performEditorAction(policy.enter.actionId)
        }
    }

    override fun onSuggestion(word: String) {
        if (panel.visible && panel.toPrompt) {
            val (remove, replacement) = suggestionEdit(panel.prompt, word)
            repeat(remove) { onPanel(Event.Backspace) }
            onPanel(Event.Type(replacement))
            afterBufferCommit(replacement, panel.prompt)
            return
        }
        val input = currentInputConnection ?: return
        val before = input.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val (remove, replacement) = suggestionEdit(before, word)
        input.beginBatchEdit()
        if (remove > 0) input.deleteSurroundingText(remove, 0)
        input.commitText(replacement, 1)
        input.endBatchEdit()
        afterBufferCommit(replacement, before.dropLast(remove.coerceAtMost(before.length)) + replacement)
    }

    override fun onAi() {
        onPanel(if (panel.visible) Event.Close else Event.Open)
    }

    override fun onGlobe(longPress: Boolean) {
        if (longPress || !switchToNext()) {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }

    override fun onCursorMove(delta: Int) {
        if (panel.visible && panel.toPrompt) return
        val input = currentInputConnection ?: return
        val extracted = input.getExtractedText(request(), 0) ?: return
        val text = extracted.text ?: return
        var next = (extracted.selectionEnd + delta).coerceIn(0, text.length)
        if (next in 1 until text.length && Character.isSurrogatePair(text[next - 1], text[next])) {
            next += if (delta > 0) 1 else -1
        }
        input.setSelection(next.coerceIn(0, text.length), next.coerceIn(0, text.length))
    }

    override fun onFeedback() {
        val keys = keyboard ?: return
        if (graph.preferences.haptics) {
            keys.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (graph.preferences.sound) {
            getSystemService(AudioManager::class.java)?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.35f)
        }
    }

    private fun onPanel(event: Event) {
        when (event) {
            Event.InsertResult -> insertResult()
            Event.ReplaceResult -> replaceResult()
            Event.CopyResult -> copyResult()
            Event.CancelBusy, Event.Close -> {
                running?.cancel()
                dispatch(event)
            }
            else -> dispatch(event)
        }
    }

    private fun dispatch(event: Event) {
        val (next, effect) = reduce(panel, event, environment())
        panel = next
        render()
        when (effect) {
            is Effect.Complete -> launch(effect)
            is Effect.Capture -> capture(effect.count)
            Effect.Paste -> paste()
            null -> Unit
        }
    }

    private fun launch(effect: Effect.Complete) {
        running?.cancel()
        val requestId = effect.requestId
        val mode = effect.mode
        val canReplace = policy.canReadText && readField().sourceForEdit.isNotBlank()
        val config = graph.config()
        running = scope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    graph.client.complete(config, effect.task, mode)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AiOutcome.Err("Connexion impossible.")
            }
            if (requestId != panel.requestId) return@launch
            when (outcome) {
                is AiOutcome.Ok -> dispatch(Event.Completed(requestId, outcome.text, mode, canReplace))
                is AiOutcome.Err -> dispatch(Event.Failed(requestId, outcome.message))
            }
        }
    }

    private fun capture(count: Int) {
        keyboard?.post {
            if (!panel.capturing) return@post
            val draft = if (policy.canReadText) readField().sourceForEdit else null
            val messages = extractMessages(ChatAccessibilityService.snapshot(), count, draft)
            dispatch(Event.Captured(messages))
        }
    }

    private fun paste() {
        val text = try {
            val clip = getSystemService(android.content.ClipboardManager::class.java)?.primaryClip
            clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        } catch (_: Exception) {
            ""
        }
        dispatch(Event.Pasted(text))
    }

    private fun insertResult() {
        val text = panel.result ?: return
        if (panel.resultMode == ResultMode.ANSWER) return
        val input = currentInputConnection ?: return
        input.beginBatchEdit()
        try {
            val extracted = input.getExtractedText(request(), 0)
            if (extracted != null && extracted.selectionStart != extracted.selectionEnd) {
                input.setSelection(extracted.selectionEnd, extracted.selectionEnd)
            }
            input.commitText(text, 1)
        } finally {
            input.endBatchEdit()
        }
        dispatch(Event.Mark("Inséré dans le champ."))
    }

    private fun replaceResult() {
        val text = panel.result ?: return
        if (!panel.canReplace || panel.resultMode == ResultMode.ANSWER) return
        val input = currentInputConnection ?: return
        input.beginBatchEdit()
        try {
            val extracted = input.getExtractedText(request(), 0)
            if (panel.replaceSelectionOnly && extracted != null && extracted.selectionStart != extracted.selectionEnd) {
                input.commitText(text, 1)
            } else if (extracted?.text != null) {
                val start = extracted.startOffset
                input.setSelection(start, start + extracted.text.length)
                input.commitText(text, 1)
            } else {
                input.deleteSurroundingText(50_000, 50_000)
                input.commitText(text, 1)
            }
        } finally {
            input.endBatchEdit()
        }
        dispatch(Event.Mark("Texte remplacé."))
    }

    private fun copyResult() {
        val text = panel.result ?: return
        val clip = ClipData.newPlainText("assistant", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip)
        dispatch(Event.Mark("Copié."))
    }

    private fun environment(): Env = Env(
        field = readField(),
        canRead = policy.canReadText,
        contextEnabled = graph.preferences.contextEnabled,
        accessibilityOn = accessibilityEnabled(this),
    )

    private fun readField(): FieldSnapshot {
        val input = currentInputConnection ?: return FieldSnapshot("", "", null)
        return FieldSnapshot(
            before = input.getTextBeforeCursor(20_000, 0)?.toString().orEmpty(),
            after = input.getTextAfterCursor(20_000, 0)?.toString().orEmpty(),
            selected = input.getSelectedText(0)?.toString(),
        )
    }

    private fun refreshShiftAndSuggestions() {
        val buffer = if (panel.visible && panel.toPrompt) panel.prompt else readField().before
        shift.sync(buffer, graph.preferences.autoCap)
        keyboard?.shift = shift.mode
        val prefix = when {
            panel.visible && panel.toPrompt -> currentPrefix(panel.prompt)
            policy.suggestions && graph.preferences.suggestions -> currentPrefix(readField().before)
            else -> ""
        }
        val values = if (prefix.isEmpty()) {
            emptyList()
        } else {
            try {
                graph.dictionary.suggest(prefix, 3)
            } catch (_: Exception) {
                emptyList()
            }
        }
        keyboard?.setSuggestions(values)
    }

    private fun afterBufferCommit(committed: String, buffer: String) {
        if (committed.any { it.isLetter() || it in ".!?…" || it == '\n' }) {
            shift.onLetterCommitted(buffer, graph.preferences.autoCap)
        }
        keyboard?.shift = shift.mode
        refreshShiftAndSuggestions()
    }

    private fun render() {
        val palette = paletteFor(this, graph.preferences.theme)
        binding?.root?.setBackgroundColor(palette.background)
        keyboard?.apply {
            this.palette = palette
            enterLabel = policy.enter.label
            showPopups = graph.preferences.popups
            rowHeightDp = when (graph.preferences.height) {
                KeyHeight.COMPACT -> 48f
                KeyHeight.NORMAL -> 54f
                KeyHeight.TALL -> 60f
            }
            shift = this@AzertyInputMethodService.shift.mode
        }
        binder?.render(panel, environment(), palette)
        window.window?.navigationBarColor = palette.background
        syncBack()
    }

    private fun syncBack() {
        setBackDisposition(if (panel.visible) BACK_DISPOSITION_WILL_NOT_DISMISS else BACK_DISPOSITION_DEFAULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) syncPredictiveBack()
    }

    private fun syncPredictiveBack() {
        val dispatcher = window.window?.onBackInvokedDispatcher ?: return
        if (panel.visible && !backRegistered) {
            val callback = android.window.OnBackInvokedCallback { onPanel(Event.Close) }
            backCallback = callback
            dispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            backRegistered = true
        } else if (!panel.visible && backRegistered) {
            val callback = backCallback as? android.window.OnBackInvokedCallback
            if (callback != null) dispatcher.unregisterOnBackInvokedCallback(callback)
            backCallback = null
            backRegistered = false
        }
    }

    private fun switchToNext(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return switchToNextInputMethod(false)
    }

    private fun request() = ExtractedTextRequest().apply {
        hintMaxChars = 100_000
        hintMaxLines = 10_000
    }
}
