package com.azertyai.keyboard.keyboard

import android.content.Context
import android.content.res.Configuration
import com.azertyai.keyboard.data.ThemeMode

data class Palette(
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val special: Int,
    val specialPressed: Int,
    val label: Int,
    val hint: Int,
    val accent: Int,
    val accentInk: Int,
    val popup: Int,
    val popupInk: Int,
    val stroke: Int,
    val prompt: Int,
)

fun paletteFor(context: Context, mode: ThemeMode): Palette {
    val night = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            mask == Configuration.UI_MODE_NIGHT_YES
        }
    }
    return if (night) darkPalette() else lightPalette()
}

private fun darkPalette() = Palette(
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

private fun lightPalette() = Palette(
    background = 0xFFE6E2DA.toInt(),
    key = 0xFFFCFBF9.toInt(),
    keyPressed = 0xFFE7E2D8.toInt(),
    special = 0xFFD5D0C6.toInt(),
    specialPressed = 0xFFC8C2B6.toInt(),
    label = 0xFF1C1B19.toInt(),
    hint = 0xFF6F6A62.toInt(),
    accent = 0xFF8C6A32.toInt(),
    accentInk = 0xFFFFF8EC.toInt(),
    popup = 0xFF1C1B19.toInt(),
    popupInk = 0xFFF7F3EB.toInt(),
    stroke = 0xFFD0CBC2.toInt(),
    prompt = 0xFFF4F1EA.toInt(),
)
