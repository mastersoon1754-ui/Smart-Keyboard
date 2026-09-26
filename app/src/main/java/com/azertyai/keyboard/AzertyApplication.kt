package com.azertyai.keyboard

import android.app.Application
import com.azertyai.keyboard.data.AppGraph

class AzertyApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
