package com.azertyai.keyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.azertyai.keyboard.logic.DEFAULT_GEMINI_MODEL
import com.azertyai.keyboard.logic.DEFAULT_MISTRAL_MODEL
import com.azertyai.keyboard.logic.Provider
import com.azertyai.keyboard.logic.sanitizeModel

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class KeyHeight { COMPACT, NORMAL, TALL }

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("azerty_prefs", Context.MODE_PRIVATE)
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    var provider: Provider
        get() = if (prefs.getString(KEY_PROVIDER, Provider.GEMINI.name) == Provider.MISTRAL.name) {
            Provider.MISTRAL
        } else {
            Provider.GEMINI
        }
        set(value) = prefs.edit().putString(KEY_PROVIDER, value.name).apply()

    var geminiModel: String
        get() = sanitizeModel(prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL).orEmpty(), DEFAULT_GEMINI_MODEL)
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, sanitizeModel(value, DEFAULT_GEMINI_MODEL)).apply()

    var mistralModel: String
        get() = sanitizeModel(prefs.getString(KEY_MISTRAL_MODEL, DEFAULT_MISTRAL_MODEL).orEmpty(), DEFAULT_MISTRAL_MODEL)
        set(value) = prefs.edit().putString(KEY_MISTRAL_MODEL, sanitizeModel(value, DEFAULT_MISTRAL_MODEL)).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var sound: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var popups: Boolean
        get() = prefs.getBoolean(KEY_POPUPS, true)
        set(value) = prefs.edit().putBoolean(KEY_POPUPS, value).apply()

    var doubleSpace: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_DOUBLE_SPACE, value).apply()

    var autoCap: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAP, value).apply()

    var suggestions: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SUGGESTIONS, value).apply()

    var contextEnabled: Boolean
        get() = prefs.getBoolean(KEY_CONTEXT, false)
        set(value) = prefs.edit().putBoolean(KEY_CONTEXT, value).apply()

    var theme: ThemeMode
        get() = when (prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var height: KeyHeight
        get() = when (prefs.getString(KEY_HEIGHT, KeyHeight.NORMAL.name)) {
            KeyHeight.COMPACT.name -> KeyHeight.COMPACT
            KeyHeight.TALL.name -> KeyHeight.TALL
            else -> KeyHeight.NORMAL
        }
        set(value) = prefs.edit().putString(KEY_HEIGHT, value.name).apply()

    fun listen(block: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> block() }
        listeners += listener
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unlisten(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        listeners.remove(listener)
    }

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_GEMINI_MODEL = "gemini_model"
        const val KEY_MISTRAL_MODEL = "mistral_model"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SOUND = "sound"
        const val KEY_POPUPS = "popups"
        const val KEY_DOUBLE_SPACE = "double_space"
        const val KEY_AUTO_CAP = "auto_cap"
        const val KEY_SUGGESTIONS = "suggestions"
        const val KEY_CONTEXT = "context"
        const val KEY_THEME = "theme"
        const val KEY_HEIGHT = "height"
    }
}
