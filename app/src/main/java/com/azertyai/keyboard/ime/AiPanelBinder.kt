package com.azertyai.keyboard.ime

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.azertyai.keyboard.databinding.InputRootBinding
import com.azertyai.keyboard.keyboard.Palette
import com.azertyai.keyboard.logic.ActionId
import com.azertyai.keyboard.logic.AiPanelState
import com.azertyai.keyboard.logic.Chip
import com.azertyai.keyboard.logic.Env
import com.azertyai.keyboard.logic.Event
import com.azertyai.keyboard.logic.PanelCopy
import com.azertyai.keyboard.logic.Screen
import com.azertyai.keyboard.logic.panelActions
import com.azertyai.keyboard.logic.panelChips
import com.azertyai.keyboard.logic.promptBarVisible

class AiPanelBinder(
    private val binding: InputRootBinding,
    private val onEvent: (Event) -> Unit,
) {
    private var chipSignature = ""
    private var actionSignature = ""
    private var state = AiPanelState()

    init {
        binding.aiClose.setOnClickListener { onEvent(Event.Close) }
        binding.aiSend.setOnClickListener { onEvent(Event.SendPrompt) }
        binding.aiPrompt.setOnClickListener { onEvent(Event.FocusPrompt) }
    }

    fun render(next: AiPanelState, env: Env, palette: Palette) {
        state = next
        binding.aiPanel.visibility = if (next.visible) View.VISIBLE else View.GONE
        binding.aiDivider.visibility = if (next.visible) View.VISIBLE else View.GONE
        if (!next.visible) return

        binding.aiPanel.setBackgroundColor(palette.background)
        binding.aiDivider.setBackgroundColor(palette.stroke)
        binding.aiTitle.setTextColor(palette.label)
        binding.aiClose.setTextColor(palette.hint)
        binding.aiBody.setTextColor(palette.label)
        binding.aiStatus.setTextColor(palette.hint)

        val target = if (next.toPrompt) "Assistant" else "Champ"
        binding.aiTarget.text = target
        binding.aiTarget.setTextColor(if (next.toPrompt) palette.accentInk else palette.label)
        binding.aiTarget.background = pill(if (next.toPrompt) palette.accent else palette.key, dp(12))
        binding.aiTarget.setOnClickListener {
            onEvent(if (state.toPrompt) Event.TargetField else Event.FocusPrompt)
        }

        val chips = panelChips(next)
        val chipKey = chips.joinToString("|") { it.label } + palette.background
        if (chipKey != chipSignature) {
            chipSignature = chipKey
            binding.aiChips.removeAllViews()
            chips.forEach { chip -> binding.aiChips.addView(chipView(chip, palette)) }
        }
        binding.aiChips.visibility = if (chips.isEmpty()) View.GONE else View.VISIBLE

        val body = PanelCopy.body(next)
        if (binding.aiBody.text.toString() != body) binding.aiBody.text = body
        val status = PanelCopy.status(next)
        binding.aiStatus.visibility = if (status.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.aiStatus.text = status.orEmpty()

        val actions = panelActions(next, env)
        val actionKey = actions.joinToString("|") + palette.background
        if (actionKey != actionSignature) {
            actionSignature = actionKey
            binding.aiActions.removeAllViews()
            actions.forEachIndexed { index, action ->
                val emphasize = index == 0 && action != ActionId.BACK && action != ActionId.CANCEL
                binding.aiActions.addView(actionView(action, palette, emphasize))
            }
        }

        val promptVisible = promptBarVisible(next)
        binding.aiPromptRow.visibility = if (promptVisible) View.VISIBLE else View.GONE
        val placeholder = if (next.screen == Screen.APPLICATION) {
            "Poste, entreprise, atouts…"
        } else {
            "Question ou instruction…"
        }
        val typed = next.prompt.isNotEmpty()
        binding.aiPrompt.text = if (typed) next.prompt else placeholder
        binding.aiPrompt.setTextColor(if (typed) palette.label else palette.hint)
        binding.aiPrompt.background = pill(palette.prompt, dp(12)).apply {
            setStroke(dp(1), if (next.toPrompt) palette.accent else palette.stroke)
        }
        val canSend = next.prompt.isNotBlank() && !next.busy
        binding.aiSend.isEnabled = canSend
        binding.aiSend.alpha = if (canSend) 1f else 0.35f
        binding.aiSend.setTextColor(palette.accent)
    }

    private fun chipView(chip: Chip, palette: Palette): TextView {
        return TextView(binding.root.context).apply {
            text = chip.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(palette.label)
            background = pill(palette.key, dp(14))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onEvent(eventFor(chip)) }
            layoutParams = marginEnd()
        }
    }

    private fun actionView(action: ActionId, palette: Palette, emphasize: Boolean): TextView {
        return TextView(binding.root.context).apply {
            text = labelFor(action)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (emphasize) palette.accentInk else palette.label)
            background = pill(if (emphasize) palette.accent else palette.key, dp(14))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onEvent(eventFor(action)) }
            layoutParams = marginEnd()
        }
    }

    private fun eventFor(chip: Chip): Event = when (chip.value) {
        "application" -> Event.OpenApplication
        "professional" -> Event.Professional
        "translate" -> Event.OpenTranslate
        "reply" -> Event.OpenContext
        "correct" -> Event.Correct
        "shorten" -> Event.Shorten
        "continue" -> Event.Continue
        "context" -> Event.OpenContext
        "paste" -> Event.RequestPaste
        "5", "10", "30" -> Event.ChooseContext(chip.value.toInt())
        else -> Event.ChooseLanguage(chip.value)
    }

    private fun eventFor(action: ActionId): Event = when (action) {
        ActionId.INSERT -> Event.InsertResult
        ActionId.REPLACE -> Event.ReplaceResult
        ActionId.COPY -> Event.CopyResult
        ActionId.CONFIRM_CONTEXT -> Event.ConfirmPreview
        ActionId.CLEAR_PREVIEW -> Event.ClearPreview
        ActionId.DRAFT -> Event.DraftApplication
        ActionId.USE_FIELD -> Event.UseFieldNotes
        ActionId.CANCEL -> Event.CancelBusy
        ActionId.BACK -> Event.BackToHome
        ActionId.REPLY_FIELD -> Event.ReplyToField
    }

    private fun labelFor(action: ActionId): String = when (action) {
        ActionId.INSERT -> "Insérer"
        ActionId.REPLACE -> "Remplacer"
        ActionId.COPY -> "Copier"
        ActionId.CONFIRM_CONTEXT -> "Envoyer à l'assistant"
        ActionId.CLEAR_PREVIEW -> "Effacer"
        ActionId.DRAFT -> "Rédiger"
        ActionId.USE_FIELD -> "Utiliser le champ"
        ActionId.CANCEL -> "Annuler"
        ActionId.BACK -> "Retour"
        ActionId.REPLY_FIELD -> "Répondre au champ"
    }

    private fun pill(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun marginEnd() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { marginEnd = dp(6) }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        binding.root.resources.displayMetrics,
    ).toInt()
}
