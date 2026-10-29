package com.engquest.teacher

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.engquest.teacher.data.Api
import com.engquest.teacher.data.AppDb
import com.engquest.teacher.data.Outbox
import com.engquest.teacher.data.OutboxWorkerFactory
import com.engquest.teacher.data.Photos
import com.engquest.teacher.data.Repo
import com.engquest.teacher.data.Session
import com.engquest.teacher.push.FirebasePushTokens
import com.engquest.teacher.push.PushTokens
import com.engquest.teacher.ui.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID

/** Whatever holds the app's one [AppGraph] (the real app, or a test app). */
interface GraphOwner {
    val graph: AppGraph
}

/**
 * The app's objects, made once: one [Api], [Session], [Repo], [Outbox] and [Photos].
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
    /** Where submission photos are kept so ones seen before open offline. */
    photoDir: File,
    /** Looks for a newer app build; null (tests) means no update check. */
    val updates: UpdateChecker? = null,
    /** This phone's notification token source; null (most tests) means no notifications. */
    val push: PushTokens? = null,
) {
    val session = Session(sessionPrefs, api)
    val repo = Repo(api, session, db)
    val workerFactory = OutboxWorkerFactory(db, session, api, repo)
    val outbox = Outbox(db, session, workManager(workerFactory), scope)
    val photos = Photos(api, session, photoDir)
    private val registering = Mutex()

    init {
        // Internet back: waiting changes go now, not after the rest of a retry pause.
        scope.launch { online.collect { if (it) outbox.sendNow() } }
    }

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
        // Forgotten so the next login registers this phone again.
        prefs.edit().remove(KEY_DEVICE_TOKEN).apply()
        session.logout()
        // A screen that queued a change in the moment before the logout took hold.
        outbox.clear()
    }

    /** The notification token last registered with the server (null: none, or logged out since). */
    fun deviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)

    /**
     * Tells the server to notify this phone at [token] (through the outbox, so it survives no
     * signal). Skipped while logged out (the next login registers it) and when [token] is the
     * one already registered.
     */
    suspend fun registerDevice(token: String) = registering.withLock {
        if (session.creds() == null || token == deviceToken()) return@withLock
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
        outbox.enqueue("registerDevice", "", mapOf("token" to token))
    }

    companion object {
        const val BASE_URL = "https://engquest-api.engquestviska.workers.dev/exec"
        const val KEY_CLASS = "class"
        const val KEY_TAB = "startTab" // only tests set it; old builds saved "tab", which is now ignored
        const val KEY_DEVICE_TOKEN = "deviceToken"
        /** Set once the teacher answered "Get a notification…?" (never asked again). */
        const val KEY_ASKED_NOTIFICATIONS = "askedNotifications"

        /** The real app's graph: encrypted login, on-disk database, live network status. */
        fun create(context: Context, workManager: (WorkerFactory) -> WorkManager): AppGraph = AppGraph(
            api = Api(BASE_URL, OkHttpClient()),
            sessionPrefs = Session.openSecurePrefs(context),
            prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE),
            db = AppDb.open(context),
            online = networkStatus(context),
            workManager = workManager,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            photoDir = File(context.cacheDir, "photos"),
            updates = UpdateChecker(
                UpdateChecker.VERSION_URL,
                UpdateChecker.SITE_BASE,
                BuildConfig.VERSION_CODE,
                OkHttpClient(),
            ),
            push = FirebasePushTokens,
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
