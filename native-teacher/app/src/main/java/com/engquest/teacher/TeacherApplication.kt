package com.engquest.teacher

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.engquest.teacher.push.SubmissionNotifications

/**
 * Builds the [AppGraph] once, at app start. WorkManager's default start-up is turned off in the
 * manifest so it uses this configuration, whose factory builds the outbox worker with the graph's objects.
 */
class TeacherApplication : Application(), GraphOwner, Configuration.Provider {
    private var workerFactory: WorkerFactory? = null

    override lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.create(this) { factory ->
            workerFactory = factory
            WorkManager.getInstance(this)
        }
        // The channel must exist before the phone shows a notification while the app is closed.
        SubmissionNotifications.ensureChannel(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(checkNotNull(workerFactory) { "WorkManager asked before the app started" })
            .build()
}
