package com.azertyai.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.azertyai.keyboard.logic.BoardMode
import com.azertyai.keyboard.logic.KeyAction
import com.azertyai.keyboard.logic.KeyStyle
import com.azertyai.keyboard.logic.ShiftMode
import com.azertyai.keyboard.logic.SoftKey
import com.azertyai.keyboard.logic.alternates
import com.azertyai.keyboard.logic.applyCase
import com.azertyai.keyboard.logic.boardRows
import kotlin.math.abs
import kotlin.math.hypot

class KeyboardView(context: Context) : View(context) {
    interface Listener {
        fun onText(raw: String)
        fun onShift()
        fun onShiftLock()
        fun onBackspace()
        fun onEnter()
        fun onSuggestion(word: String)
        fun onAi()
        fun onGlobe(longPress: Boolean)
        fun onCursorMove(delta: Int)
        fun onFeedback()
    }

    var listener: Listener? = null
    var palette: Palette = darkFallback()
        set(value) {
            field = value
            invalidate()
        }
    var shift: ShiftMode = ShiftMode.OFF
        set(value) {
            field = value
            invalidate()
        }
    var enterLabel: String = "↵"
        set(value) {
            field = value
            invalidate()
        }
    var showPopups: Boolean = true
    var bottomInset: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }
    var rowHeightDp: Float = 54f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private var mode: BoardMode = BoardMode.LETTERS
    private var emailLike: Boolean = false
    private var suggestions: List<String> = emptyList()

    private val hits = ArrayList<Hit>(48)
    private val suggestionHits = ArrayList<Pair<RectF, String>>(3)
    private val accentRects = ArrayList<RectF>(8)
    private val aiRect = RectF()
    private val globeRect = RectF()
    private val popupRect = RectF()
    private val iconPath = Path()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var pointerId = -1
    private var downIndex = -1
    private var downSuggestion: String? = null
    private var downAi = false
    private var downGlobe = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var accent: List<String>? = null
    private var accentIndex = 0
    private var spaceSwipe = false
    private var spaceAccum = 0f
    private var repeating = false
    private var longPressed = false
    private var repeatTicks = 0
    private var laidOutWidth = -1
    private var laidOutHeight = -1

    private val longPress = Runnable { onLongPress() }
    private val repeat = object : Runnable {
        override fun run() {
            if (pointerId == -1 || downKey()?.action != KeyAction.BACKSPACE) return
            repeating = true
            repeatTicks += 1
            listener?.onBackspace()
            postDelayed(this, if (repeatTicks > 12) 30L else 46L)
        }
    }

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isSoundEffectsEnabled = false
        contentDescription = "Clavier AZERTY"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setBoard(next: BoardMode, email: Boolean = emailLike) {
        val changed = next != mode || email != emailLike
        mode = next
        emailLike = email
        if (changed) {
            laidOutWidth = -1
            requestLayout()
            invalidate()
        }
    }

    fun setSuggestions(values: List<String>) {
        if (values == suggestions) return
        suggestions = values
        laidOutWidth = -1
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = boardRows(mode, emailLike).size
        val height = dp(48f).toInt() + (rows * dp(rowHeightDp)).toInt() + bottomInset
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (laidOutWidth != width || laidOutHeight != height) layoutKeys()
        fill.color = palette.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.color = palette.stroke
        canvas.drawRect(0f, 0f, width.toFloat(), dp(1f), fill)
        drawToolbar(canvas)
        for ((index, hit) in hits.withIndex()) {
            drawKey(canvas, hit.rect, hit.key, pressed = index == downIndex && accent == null)
        }
        if (accent != null) drawAccents(canvas)
        else if (showPopups) drawPreview(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerId != -1) return true
                val index = event.actionIndex
                pointerId = event.getPointerId(index)
                down(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) move(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) {
                    up(
                        event.getX(index),
                        event.getY(index),
                        event.actionMasked == MotionEvent.ACTION_CANCEL,
                    )
                    pointerId = -1
                }
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        removeCallbacks(repeat)
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean = super.performClick()

    private fun down(x: Float, y: Float) {
        removeCallbacks(longPress)
        removeCallbacks(repeat)
        repeating = false
        longPressed = false
        accent = null
        spaceSwipe = false
        spaceAccum = 0f
        repeatTicks = 0
        downX = x
        downY = y
        lastX = x
        downIndex = -1
        downSuggestion = null
        downAi = false
        downGlobe = false
        if (y < dp(48f)) {
            when {
                aiRect.contains(x, y) -> downAi = true
                globeRect.contains(x, y) -> {
                    downGlobe = true
                    postDelayed(longPress, 360)
                }
                else -> downSuggestion = suggestionHits.firstOrNull { it.first.contains(x, y) }?.second
            }
        } else {
            downIndex = hitIndex(x, y)
            when (downKey()?.action) {
                KeyAction.BACKSPACE -> {
                    listener?.onBackspace()
                    postDelayed(repeat, 340)
                }
                KeyAction.SHIFT -> postDelayed(longPress, 360)
                null -> if (downKey()?.text != null) postDelayed(longPress, 280)
                else -> Unit
            }
        }
        listener?.onFeedback()
        invalidate()
    }

    private fun move(x: Float, y: Float) {
        val options = accent
        if (options != null) {
            accentIndex = accentIndexAt(x)
            invalidate()
            return
        }
        if (hypot(x - downX, y - downY) > dp(14f)) removeCallbacks(longPress)
        val key = downKey()
        if (key?.action == KeyAction.SPACE && abs(x - downX) > dp(18f)) {
            spaceSwipe = true
            spaceAccum += x - lastX
            val step = dp(22f)
            while (spaceAccum > step) {
                listener?.onCursorMove(1)
                spaceAccum -= step
            }
            while (spaceAccum < -step) {
                listener?.onCursorMove(-1)
                spaceAccum += step
            }
        }
        lastX = x
        invalidate()
    }

    private fun up(x: Float, y: Float, cancel: Boolean) {
        removeCallbacks(longPress)
        removeCallbacks(repeat)
        val options = accent
        if (!cancel) {
            when {
                downAi && aiRect.contains(x, y) -> listener?.onAi()
                downGlobe && !longPressed && globeRect.contains(x, y) -> listener?.onGlobe(false)
                downSuggestion != null && suggestionHits.any { it.second == downSuggestion && it.first.contains(x, y) } ->
                    listener?.onSuggestion(downSuggestion!!)
                options != null -> listener?.onText(options[accentIndex.coerceIn(options.indices)])
                spaceSwipe || repeating -> Unit
                longPressed && downKey()?.action == KeyAction.SHIFT -> Unit
                downKey()?.action == KeyAction.BACKSPACE -> Unit
                else -> dispatch(hitKey(x, y) ?: downKey())
            }
        }
        downIndex = -1
        downAi = false
        downGlobe = false
        downSuggestion = null
        accent = null
        spaceSwipe = false
        invalidate()
        performClick()
    }

    private fun dispatch(key: SoftKey?) {
        when (key?.action) {
            KeyAction.SHIFT -> listener?.onShift()
            KeyAction.BACKSPACE -> Unit
            KeyAction.ENTER -> listener?.onEnter()
            KeyAction.SPACE -> listener?.onText(" ")
            KeyAction.SYMBOLS -> setBoard(BoardMode.SYMBOLS)
            KeyAction.SYMBOLS_ALT -> setBoard(BoardMode.SYMBOLS_ALT)
            KeyAction.LETTERS -> setBoard(BoardMode.LETTERS)
            KeyAction.EMOJI -> setBoard(if (mode == BoardMode.EMOJI) BoardMode.LETTERS else BoardMode.EMOJI)
            null -> key?.text?.let { listener?.onText(it) }
        }
    }

    private fun onLongPress() {
        longPressed = true
        when {
            downGlobe -> listener?.onGlobe(true)
            downKey()?.action == KeyAction.SHIFT -> listener?.onShiftLock()
            downKey()?.action == null && downKey()?.text != null -> {
                val options = alternates(downKey()?.text.orEmpty())
                if (options.isEmpty()) {
                    longPressed = false
                } else {
                    openAccent(options)
                }
            }
            else -> longPressed = false
        }
        listener?.onFeedback()
        invalidate()
    }

    private fun openAccent(options: List<String>) {
        val keyRect = hits.getOrNull(downIndex)?.rect ?: return
        accent = options
        accentIndex = 0
        accentRects.clear()
        val cell = dp(44f)
        val height = dp(48f)
        val total = cell * options.size
        val maxLeft = (width - total - dp(6f)).coerceAtLeast(dp(6f))
        val minLeft = dp(6f).coerceAtMost(maxLeft)
        var left = (keyRect.centerX() - total / 2f).coerceIn(minLeft, maxLeft)
        var top = keyRect.top - height - dp(8f)
        if (top < dp(4f)) top = (keyRect.bottom + dp(6f)).coerceAtMost(this.height - height - dp(4f))
        options.indices.forEach { index ->
            accentRects += RectF(left, top + dp(2f), left + cell - dp(4f), top + height - dp(2f))
            left += cell
        }
    }

    private fun layoutKeys() {
        hits.clear()
        suggestionHits.clear()
        val toolbar = dp(48f)
        val gap = dp(5f)
        val side = dp(4f)
        aiRect.set(side, dp(6f), side + dp(36f), dp(42f))
        globeRect.set(width - side - dp(36f), dp(6f), width - side, dp(42f))
        val rows = boardRows(mode, emailLike)
        val usable = (height - bottomInset - toolbar).coerceAtLeast(dp(40f))
        val rowHeight = usable / rows.size.coerceAtLeast(1)
        var top = toolbar
        for (row in rows) {
            val weight = row.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
            val available = width - side * 2 - gap * (row.size - 1)
            var x = side
            for (key in row) {
                val keyWidth = available * (key.weight / weight)
                hits += Hit(RectF(x, top + gap / 2f, x + keyWidth, top + rowHeight - gap / 2f), key)
                x += keyWidth + gap
            }
            top += rowHeight
        }
        layoutSuggestions(toolbar)
        laidOutWidth = width
        laidOutHeight = height
    }

    private fun layoutSuggestions(toolbar: Float) {
        suggestionHits.clear()
        if (suggestions.isEmpty()) return
        val left = aiRect.right + dp(8f)
        val right = globeRect.left - dp(8f)
        if (right - left < dp(40f)) return
        val slot = (right - left) / suggestions.size
        labelPaint.textSize = sp(15f)
        suggestions.forEachIndexed { index, word ->
            val rect = RectF(left + slot * index, dp(4f), left + slot * (index + 1), toolbar - dp(4f))
            suggestionHits += rect to ellipsize(word, rect.width() - dp(8f))
        }
    }

    private fun drawToolbar(canvas: Canvas) {
        drawAiButton(canvas, aiRect, downAi)
        drawGlobe(canvas, globeRect, downGlobe)
        labelPaint.color = palette.label
        labelPaint.textSize = sp(15f)
        for ((rect, word) in suggestionHits) {
            canvas.drawText(word, rect.centerX(), baseline(rect, labelPaint), labelPaint)
        }
    }

    private fun drawKey(canvas: Canvas, rect: RectF, key: SoftKey, pressed: Boolean) {
        val enter = key.action == KeyAction.ENTER
        fill.color = when {
            enter -> palette.accent
            pressed && key.style == KeyStyle.SPECIAL -> palette.specialPressed
            pressed -> palette.keyPressed
            key.style == KeyStyle.SPECIAL || key.style == KeyStyle.ACTION -> palette.special
            else -> palette.key
        }
        canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
        val shown = labelOf(key)
        if (key.action == KeyAction.SHIFT) {
            drawShift(canvas, rect, shift)
            return
        }
        if (key.action == KeyAction.BACKSPACE) {
            drawBackspace(canvas, rect)
            return
        }
        if (key.action == KeyAction.EMOJI && shown == "☺") {
            drawFace(canvas, rect)
            return
        }
        labelPaint.color = if (enter) palette.accentInk else palette.label
        labelPaint.textSize = when {
            shown.length >= 7 -> sp(12f)
            key.action != null -> sp(13f)
            mode == BoardMode.EMOJI -> sp(22f)
            else -> sp(20f)
        }
        canvas.drawText(shown, rect.centerX(), baseline(rect, labelPaint), labelPaint)
    }

    private fun drawPreview(canvas: Canvas) {
        val key = downKey() ?: return
        if (key.action != null || key.text.isNullOrEmpty() || spaceSwipe) return
        val rect = hits.getOrNull(downIndex)?.rect ?: return
        val width = dp(52f)
        val height = dp(58f)
        popupRect.set(
            (rect.centerX() - width / 2f).coerceIn(dp(4f), this.width - width - dp(4f)),
            (rect.top - height - dp(8f)).coerceAtLeast(dp(2f)),
            0f,
            0f,
        )
        popupRect.right = popupRect.left + width
        popupRect.bottom = popupRect.top + height
        fill.color = palette.popup
        canvas.drawRoundRect(popupRect, dp(10f), dp(10f), fill)
        labelPaint.color = palette.popupInk
        labelPaint.textSize = sp(26f)
        canvas.drawText(labelOf(key), popupRect.centerX(), baseline(popupRect, labelPaint), labelPaint)
    }

    private fun drawAccents(canvas: Canvas) {
        val options = accent ?: return
        options.forEachIndexed { index, text ->
            val rect = accentRects.getOrNull(index) ?: return@forEachIndexed
            val selected = index == accentIndex
            fill.color = if (selected) palette.accent else palette.popup
            canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
            labelPaint.color = if (selected) palette.accentInk else palette.popupInk
            labelPaint.textSize = if (text.length > 2) sp(13f) else sp(20f)
            val shown = applyCase(text, shift)
            canvas.drawText(shown, rect.centerX(), baseline(rect, labelPaint), labelPaint)
        }
    }

    private fun drawAiButton(canvas: Canvas, rect: RectF, pressed: Boolean) {
        fill.color = if (pressed) palette.specialPressed else palette.accent
        canvas.drawRoundRect(rect, dp(12f), dp(12f), fill)
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = palette.accentInk
        drawSpark(canvas, rect.centerX(), rect.centerY(), dp(7f))
        iconPaint.style = Paint.Style.STROKE
    }

    private fun drawSpark(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        iconPath.rewind()
        iconPath.moveTo(cx, cy - radius)
        iconPath.quadTo(cx + radius * 0.18f, cy - radius * 0.18f, cx + radius, cy)
        iconPath.quadTo(cx + radius * 0.18f, cy + radius * 0.18f, cx, cy + radius)
        iconPath.quadTo(cx - radius * 0.18f, cy + radius * 0.18f, cx - radius, cy)
        iconPath.quadTo(cx - radius * 0.18f, cy - radius * 0.18f, cx, cy - radius)
        iconPath.close()
        canvas.drawPath(iconPath, iconPaint)
    }

    private fun drawGlobe(canvas: Canvas, rect: RectF, pressed: Boolean) {
        fill.color = if (pressed) palette.specialPressed else palette.special
        canvas.drawRoundRect(rect, dp(12f), dp(12f), fill)
        iconPaint.color = palette.label
        iconPaint.strokeWidth = dp(1.4f)
        val radius = dp(8f)
        canvas.drawCircle(rect.centerX(), rect.centerY(), radius, iconPaint)
        canvas.drawLine(rect.centerX() - radius, rect.centerY(), rect.centerX() + radius, rect.centerY(), iconPaint)
        popupRect.set(rect.centerX() - radius * 0.55f, rect.centerY() - radius, rect.centerX() + radius * 0.55f, rect.centerY() + radius)
        canvas.drawOval(popupRect, iconPaint)
    }

    private fun drawShift(canvas: Canvas, rect: RectF, mode: ShiftMode) {
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = if (mode == ShiftMode.OFF) palette.hint else palette.label
        val cx = rect.centerX()
        val cy = rect.centerY()
        iconPath.rewind()
        iconPath.moveTo(cx, cy - dp(9f))
        iconPath.lineTo(cx + dp(8f), cy + dp(1f))
        iconPath.lineTo(cx + dp(3.5f), cy + dp(1f))
        iconPath.lineTo(cx + dp(3.5f), cy + dp(8f))
        iconPath.lineTo(cx - dp(3.5f), cy + dp(8f))
        iconPath.lineTo(cx - dp(3.5f), cy + dp(1f))
        iconPath.lineTo(cx - dp(8f), cy + dp(1f))
        iconPath.close()
        if (mode == ShiftMode.OFF) {
            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeWidth = dp(1.6f)
        }
        canvas.drawPath(iconPath, iconPaint)
        if (mode == ShiftMode.LOCK) {
            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeWidth = dp(1.7f)
            canvas.drawLine(cx - dp(6f), cy + dp(11f), cx + dp(6f), cy + dp(11f), iconPaint)
        }
        iconPaint.style = Paint.Style.STROKE
    }

    private fun drawBackspace(canvas: Canvas, rect: RectF) {
        iconPaint.style = Paint.Style.STROKE
        iconPaint.color = palette.label
        iconPaint.strokeWidth = dp(1.6f)
        val cx = rect.centerX()
        val cy = rect.centerY()
        iconPath.rewind()
        iconPath.moveTo(cx + dp(8f), cy - dp(7f))
        iconPath.lineTo(cx - dp(2f), cy - dp(7f))
        iconPath.lineTo(cx - dp(9f), cy)
        iconPath.lineTo(cx - dp(2f), cy + dp(7f))
        iconPath.lineTo(cx + dp(8f), cy + dp(7f))
        iconPath.close()
        canvas.drawPath(iconPath, iconPaint)
        canvas.drawLine(cx - dp(1f), cy - dp(3.5f), cx + dp(5f), cy + dp(3.5f), iconPaint)
        canvas.drawLine(cx + dp(5f), cy - dp(3.5f), cx - dp(1f), cy + dp(3.5f), iconPaint)
    }

    private fun drawFace(canvas: Canvas, rect: RectF) {
        iconPaint.style = Paint.Style.STROKE
        iconPaint.color = palette.label
        iconPaint.strokeWidth = dp(1.5f)
        val cx = rect.centerX()
        val cy = rect.centerY()
        canvas.drawCircle(cx, cy, dp(9f), iconPaint)
        iconPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx - dp(3f), cy - dp(2f), dp(1.2f), iconPaint)
        canvas.drawCircle(cx + dp(3f), cy - dp(2f), dp(1.2f), iconPaint)
        iconPaint.style = Paint.Style.STROKE
        iconPath.rewind()
        iconPath.moveTo(cx - dp(4f), cy + dp(2f))
        iconPath.quadTo(cx, cy + dp(6f), cx + dp(4f), cy + dp(2f))
        canvas.drawPath(iconPath, iconPaint)
    }

    private fun labelOf(key: SoftKey): String {
        if (key.action == KeyAction.ENTER) return enterLabel
        if (key.action == KeyAction.SPACE) return ""
        val raw = key.text.orEmpty()
        if (key.action != null) return raw
        return applyCase(raw, shift)
    }

    private fun downKey(): SoftKey? = hits.getOrNull(downIndex)?.key

    private fun hitIndex(x: Float, y: Float): Int =
        hits.indexOfFirst { it.rect.contains(x, y) }

    private fun hitKey(x: Float, y: Float): SoftKey? = hits.getOrNull(hitIndex(x, y))?.key

    private fun accentIndexAt(x: Float): Int {
        accentRects.forEachIndexed { index, rect -> if (x <= rect.right) return index }
        return accentRects.lastIndex.coerceAtLeast(0)
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (labelPaint.measureText(text) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && labelPaint.measureText("$value…") > maxWidth) {
            value = value.dropLast(1)
        }
        return if (value.isEmpty()) "…" else "$value…"
    }

    private fun baseline(rect: RectF, paint: Paint): Float {
        val metrics = paint.fontMetrics
        return rect.centerY() - (metrics.ascent + metrics.descent) / 2f
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private data class Hit(val rect: RectF, val key: SoftKey)
}

private fun darkFallback() = Palette(
    background = 0xFF12110F.toInt(),
    key = 0xFF2C2A27.toInt(),
    keyPressed = 0xFF3E3B37.toInt(),
    special = 0xFF1C1B19.toInt(),
    specialPressed = 0xFF34322E.toInt(),
    label = 0xFFF4F0E8.toInt(),
    hint = 0xFFA39E94.toInt(),
    accent = 0xFFC6A15B.toInt(),
    accentInk = 0xFF1A1408.toInt(),
    popup = 0xFFF7F3EB.toInt(),
    popupInk = 0xFF1A1814.toInt(),
    stroke = 0xFF3A3834.toInt(),
    prompt = 0xFF1C1B19.toInt(),
)
