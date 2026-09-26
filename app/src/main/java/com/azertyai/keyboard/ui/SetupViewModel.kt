package com.azertyai.keyboard.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azertyai.keyboard.AzertyApplication
import com.azertyai.keyboard.data.KeyHeight
import com.azertyai.keyboard.data.ThemeMode
import com.azertyai.keyboard.logic.AiOutcome
import com.azertyai.keyboard.logic.AiTask
import com.azertyai.keyboard.logic.Provider
import com.azertyai.keyboard.logic.ResultMode
import com.azertyai.keyboard.system.accessibilityEnabled
import com.azertyai.keyboard.system.keyboardEnabled
import com.azertyai.keyboard.system.keyboardSelected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Route { Home, Keys, Privacy, Preferences }

data class SettingsSnapshot(
    val provider: Provider = Provider.GEMINI,
    val hasGemini: Boolean = false,
    val hasMistral: Boolean = false,
    val geminiModel: String = "",
    val mistralModel: String = "",
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val popups: Boolean = true,
    val doubleSpace: Boolean = true,
    val autoCap: Boolean = true,
    val suggestions: Boolean = true,
    val contextEnabled: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val height: KeyHeight = KeyHeight.NORMAL,
    val keyboardEnabled: Boolean = false,
    val keyboardSelected: Boolean = false,
    val accessibilityOn: Boolean = false,
)

class SetupViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as AzertyApplication).graph
    var route by mutableStateOf(Route.Home)
    var settings by mutableStateOf(SettingsSnapshot())
    var geminiDraft by mutableStateOf("")
    var mistralDraft by mutableStateOf("")
    var geminiModelDraft by mutableStateOf("")
    var mistralModelDraft by mutableStateOf("")
    var revealGemini by mutableStateOf(false)
    var revealMistral by mutableStateOf(false)
    var testing by mutableStateOf(false)
    var testMessage by mutableStateOf<String?>(null)

    init {
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        val prefs = graph.preferences
        settings = SettingsSnapshot(
            provider = prefs.provider,
            hasGemini = graph.secure.hasGeminiKey(),
            hasMistral = graph.secure.hasMistralKey(),
            geminiModel = prefs.geminiModel,
            mistralModel = prefs.mistralModel,
            haptics = prefs.haptics,
            sound = prefs.sound,
            popups = prefs.popups,
            doubleSpace = prefs.doubleSpace,
            autoCap = prefs.autoCap,
            suggestions = prefs.suggestions,
            contextEnabled = prefs.contextEnabled,
            theme = prefs.theme,
            height = prefs.height,
            keyboardEnabled = keyboardEnabled(context),
            keyboardSelected = keyboardSelected(context),
            accessibilityOn = accessibilityEnabled(context),
        )
        if (geminiModelDraft.isEmpty()) geminiModelDraft = prefs.geminiModel
        if (mistralModelDraft.isEmpty()) mistralModelDraft = prefs.mistralModel
    }

    fun open(next: Route) {
        if (route == Route.Keys && next != Route.Keys) {
            saveModels()
            forgetDrafts()
        }
        route = next
        if (next == Route.Keys) testMessage = null
    }

    fun selectProvider(provider: Provider) {
        graph.preferences.provider = provider
        refresh()
    }

    fun saveGemini() {
        if (geminiDraft.isBlank()) return
        graph.secure.setGeminiKey(geminiDraft)
        geminiDraft = ""
        revealGemini = false
        refresh()
    }

    fun saveMistral() {
        if (mistralDraft.isBlank()) return
        graph.secure.setMistralKey(mistralDraft)
        mistralDraft = ""
        revealMistral = false
        refresh()
    }

    fun clearGemini() {
        graph.secure.setGeminiKey("")
        geminiDraft = ""
        revealGemini = false
        refresh()
    }

    fun clearMistral() {
        graph.secure.setMistralKey("")
        mistralDraft = ""
        revealMistral = false
        refresh()
    }

    fun toggleGeminiReveal() {
        if (!revealGemini && geminiDraft.isEmpty()) geminiDraft = graph.secure.geminiKey()
        revealGemini = !revealGemini
    }

    fun toggleMistralReveal() {
        if (!revealMistral && mistralDraft.isEmpty()) mistralDraft = graph.secure.mistralKey()
        revealMistral = !revealMistral
    }

    fun saveModels() {
        graph.preferences.geminiModel = geminiModelDraft
        graph.preferences.mistralModel = mistralModelDraft
        refresh()
        geminiModelDraft = graph.preferences.geminiModel
        mistralModelDraft = graph.preferences.mistralModel
    }

    fun testConnection() {
        if (testing) return
        saveModels()
        val provider = settings.provider
        if (provider == Provider.GEMINI && geminiDraft.isNotBlank()) saveGemini()
        if (provider == Provider.MISTRAL && mistralDraft.isNotBlank()) saveMistral()
        val config = graph.config()
        testing = true
        testMessage = null
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                graph.client.complete(
                    config,
                    AiTask.Question("Réponds uniquement par OK."),
                    ResultMode.ANSWER,
                )
            }
            testMessage = when (outcome) {
                is AiOutcome.Ok -> "Connexion réussie."
                is AiOutcome.Err -> outcome.message
            }
            testing = false
            refresh()
        }
    }

    fun setHaptics(value: Boolean) = update { graph.preferences.haptics = value }
    fun setSound(value: Boolean) = update { graph.preferences.sound = value }
    fun setPopups(value: Boolean) = update { graph.preferences.popups = value }
    fun setDoubleSpace(value: Boolean) = update { graph.preferences.doubleSpace = value }
    fun setAutoCap(value: Boolean) = update { graph.preferences.autoCap = value }
    fun setSuggestions(value: Boolean) = update { graph.preferences.suggestions = value }
    fun setContext(value: Boolean) = update { graph.preferences.contextEnabled = value }
    fun setTheme(value: ThemeMode) = update { graph.preferences.theme = value }
    fun setHeight(value: KeyHeight) = update { graph.preferences.height = value }

    private fun update(block: () -> Unit) {
        block()
        refresh()
    }

    private fun forgetDrafts() {
        geminiDraft = ""
        mistralDraft = ""
        revealGemini = false
        revealMistral = false
    }
}
