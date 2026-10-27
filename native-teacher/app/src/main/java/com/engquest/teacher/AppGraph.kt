package com.engquest.teacher

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.room.Room
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.engquest.teacher.data.Api
import com.engquest.teacher.data.AppDb
import com.engquest.teacher.data.Outbox
import com.engquest.teacher.data.OutboxWorkerFactory
import com.engquest.teacher.data.Repo
import com.engquest.teacher.data.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID

/** Whatever holds the app's one [AppGraph] (the real app, or a test app). */
interface GraphOwner {
    val graph: AppGraph
}

/**
 * The app's objects, made once: one [Api], [Session], [Repo] and [Outbox].
 * [workManager] gets the worker factory so WorkManager can build [com.engquest.teacher.data.OutboxWorker]
 * with these same objects. [prefs] holds plain screen choices (selected class and tab).
 * Making the [Outbox] here, at app start, lets it re-schedule unsent items after a restart or re-login.
 */
class AppGraph(
    val api: Api,
    sessionPrefs: SharedPreferences,
    val prefs: SharedPreferences,
    val db: AppDb,
    /** True while the phone has an internet connection. */
    val online: StateFlow<Boolean>,
    workManager: (WorkerFactory) -> WorkManager,
    val scope: CoroutineScope,
) {
    val session = Session(sessionPrefs, api)
    val repo = Repo(api, session, db)
    val workerFactory = OutboxWorkerFactory(db, session, api, repo)
    val outbox = Outbox(db, session, workManager(workerFactory), scope)

    /**
     * Logs out: tells the server to stop notifying this phone (best effort, in the background),
     * drops unsent changes, and forgets the login.
     */
    suspend fun logOut() {
        val creds = session.creds()
        val token = deviceToken()
        if (creds != null && token != null) {
            scope.launch {
                runCatching {
                    api.write(
                        "unregisterDevice",
                        mapOf(
                            "username" to creds.username,
                            "password" to creds.password,
                            "token" to token,
                            "rid" to UUID.randomUUID().toString(),
                        ),
                    )
                }
            }
        }
        outbox.clear()
        session.logout()
    }

    /**
     * This phone's notification token. Nothing stores one yet: Task 13 (notifications) saves it
     * under [KEY_DEVICE_TOKEN]; until then log out skips `unregisterDevice`.
     */
    fun deviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)

    companion object {
        const val BASE_URL = "https://engquest-api.engquestviska.workers.dev/exec"
        const val KEY_CLASS = "class"
        const val KEY_TAB = "tab"
        const val KEY_DEVICE_TOKEN = "deviceToken"

        /** The real app's graph: encrypted login, on-disk database, live network status. */
        fun create(context: Context, workManager: (WorkerFactory) -> WorkManager): AppGraph = AppGraph(
            api = Api(BASE_URL, OkHttpClient()),
            sessionPrefs = Session.openSecurePrefs(context),
            prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE),
            db = Room.databaseBuilder(context, AppDb::class.java, "engquest-teacher.db").build(),
            online = networkStatus(context),
            workManager = workManager,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

        private fun networkStatus(context: Context): StateFlow<Boolean> {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val state = MutableStateFlow(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    state.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }

                override fun onLost(network: Network) {
                    state.value = false
                }
            })
            return state
        }
    }
}
