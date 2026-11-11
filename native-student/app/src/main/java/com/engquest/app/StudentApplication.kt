package com.engquest.app

import android.app.Application
import kotlinx.coroutines.launch

/** Builds the [AppGraph] once, at app start. */
class StudentApplication : Application(), GraphOwner {
    override lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.create(this)
        // A send cut off when the app last closed goes back in line and is retried.
        graph.scope.launch { graph.sendQueue.recover() }
    }
}
