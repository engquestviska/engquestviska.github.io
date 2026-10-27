package com.engquest.teacher

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.engquest.teacher.data.Api
import com.engquest.teacher.data.AppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.io.File

/**
 * An [AppGraph] for tests: plain prefs, in-memory database, a test WorkManager (its jobs wait
 * for a network the test never grants) and a network switch the test controls.
 */
fun testGraph(
    context: Context,
    baseUrl: String,
    online: MutableStateFlow<Boolean> = MutableStateFlow(true),
    /** Tests of thread safety pass a real background scope, as the app has. */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
): AppGraph = AppGraph(
    api = Api(baseUrl, OkHttpClient()),
    sessionPrefs = context.getSharedPreferences("session-test", Context.MODE_PRIVATE),
    prefs = context.getSharedPreferences("ui-test", Context.MODE_PRIVATE),
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build(),
    online = online,
    workManager = { factory ->
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build(),
        )
        WorkManager.getInstance(context)
    },
    scope = scope,
    photoDir = File(context.cacheDir, "photos-test").apply { deleteRecursively() },
)

/** Stands in for [TeacherApplication] in activity tests (no Android Keystore under Robolectric). */
class TestTeacherApp : Application(), GraphOwner {
    override val graph: AppGraph by lazy { testGraph(this, "http://localhost/exec") }
}
