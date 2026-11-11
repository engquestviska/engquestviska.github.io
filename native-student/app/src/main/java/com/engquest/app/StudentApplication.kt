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
        // The channel must exist before the phone shows a notification while the app is closed.
        com.engquest.app.push.Notify.ensureChannel(this)
        // A send cut off when the app last closed goes back in line and is retried.
        graph.scope.launch {
            graph.sendQueue.recover()
            // The app opening is a good moment to try now, not after the rest of a retry pause.
            com.engquest.app.data.SendWorker.kick(this@StudentApplication, now = true)
        }
    }
}
