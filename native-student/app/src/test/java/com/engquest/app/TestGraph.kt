package com.engquest.app

import android.content.Context
import androidx.room.Room
import com.engquest.app.data.Api
import com.engquest.app.data.AppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient

/** An [AppGraph] for tests: plain prefs ("student-test"), in-memory database, a network switch the test controls. */
fun testGraph(
    context: Context,
    baseUrl: String,
    online: MutableStateFlow<Boolean> = MutableStateFlow(true),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    /** False shows the one-time "Want a buzz?" question; most tests start past it. */
    buzzAsked: Boolean = true,
    /** Records Firebase topic changes: "+topic" subscribed, "-topic" unsubscribed. */
    topicLog: MutableList<String> = mutableListOf(),
): AppGraph = AppGraph(
    api = Api(baseUrl, OkHttpClient()),
    prefs = context.getSharedPreferences("student-test", Context.MODE_PRIVATE).also {
        it.edit().putBoolean("notifyAsked", buzzAsked).commit()
    },
    legacyPrefs = context.getSharedPreferences("engquest-test", Context.MODE_PRIVATE),
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build(),
    online = online,
    scope = scope,
    sendDir = java.io.File(context.cacheDir, "sends-test"),
    kickSender = {},
    topics = { on, off -> on.sorted().forEach { topicLog += "+$it" }; off.sorted().forEach { topicLog += "-$it" } },
)
