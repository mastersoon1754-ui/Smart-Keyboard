package com.azertyai.keyboard.data

import android.content.Context
import com.azertyai.keyboard.logic.AiClient
import com.azertyai.keyboard.logic.AiConfig
import com.azertyai.keyboard.logic.FrenchDictionary
import com.azertyai.keyboard.logic.loadDictionary
import com.azertyai.keyboard.net.OkHttpTransport

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    val preferences = Preferences(appContext)
    val secure = SecureStore(appContext)
    val client = AiClient(OkHttpTransport())
    val dictionary: FrenchDictionary by lazy {
        val text = appContext.assets.open("fr_words.txt").bufferedReader().use { it.readText() }
        loadDictionary(text)
    }

    fun config(): AiConfig = AiConfig(
        provider = preferences.provider,
        geminiKey = secure.geminiKey(),
        mistralKey = secure.mistralKey(),
        geminiModel = preferences.geminiModel,
        mistralModel = preferences.mistralModel,
    )
}
