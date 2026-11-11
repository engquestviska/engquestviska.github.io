package com.engquest.app

import android.app.Application

/** Builds the [AppGraph] once, at app start. */
class StudentApplication : Application(), GraphOwner {
    override lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.create(this)
    }
}
