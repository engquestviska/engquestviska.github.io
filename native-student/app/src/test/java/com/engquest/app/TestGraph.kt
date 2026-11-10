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
): AppGraph = AppGraph(
    api = Api(baseUrl, OkHttpClient()),
    prefs = context.getSharedPreferences("student-test", Context.MODE_PRIVATE),
    legacyPrefs = context.getSharedPreferences("engquest-test", Context.MODE_PRIVATE),
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build(),
    online = online,
    scope = scope,
)
