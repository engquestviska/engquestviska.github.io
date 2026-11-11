package com.engquest.app

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.engquest.app.data.Api
import com.engquest.app.data.AppDb
import com.engquest.app.data.ProfileStore
import com.engquest.app.data.Repo
import com.engquest.app.data.SendQueue
import com.engquest.app.data.SendWorker
import java.io.File
import com.engquest.app.domain.Profile
import com.engquest.app.push.NotifyPrefs
import kotlinx.coroutines.launch
import com.engquest.app.domain.Grade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/** Whatever holds the app's one [AppGraph] (the real app, or a test app). */
interface GraphOwner {
    val graph: AppGraph
}

/**
 * The app's objects, made once. [prefs] holds the profile and small screen choices;
 * [legacyPrefs] is the old 1.8 app's "engquest" file (its saved grade preselects the login).
 */
class AppGraph(
    val api: Api,
    val prefs: SharedPreferences,
    private val legacyPrefs: SharedPreferences,
    val db: AppDb,
    /** True while the phone has an internet connection. */
    val online: StateFlow<Boolean>,
    val scope: CoroutineScope,
    /** Where task photos wait until they are sent. */
    sendDir: File,
    /** Starts the background sender; tests pass a no-op and call [SendQueue.drain] themselves. */
    kickSender: () -> Unit,
    /** Gets this phone's push token (Firebase); tests pass one that never answers. */
    private val pushToken: ((String) -> Unit) -> Unit = {},
    /** Subscribes this phone to the first set of Firebase topics and out of the second (class-wide news). */
    private val topics: (Set<String>, Set<String>) -> Unit = { _, _ -> },
    /** Looks for a newer app build; null (tests) means no update check. */
    val updates: com.engquest.app.ui.UpdateChecker? = null,
) {
    val repo = Repo(api, db)
    val profiles = ProfileStore(prefs)
    val sendQueue = SendQueue(db, api, sendDir, kickSender)
    val notify = NotifyPrefs(prefs)

    /**
     * Tells the server this phone's notification switches (best effort, in the background).
     * Without a push token yet (no Firebase, or not registered) there is nothing to tell.
     */
    fun syncNotifyPrefs(p: Profile) {
        val token = notify.token()
        if (token == null) {
            // First time: ask Firebase for this phone's token, then send (never happens without Firebase).
            pushToken { t -> notify.saveToken(t); syncNotifyPrefs(p) }
            return
        }
        val body = mapOf("token" to token, "className" to p.classId, "studentNo" to p.studentNo.toString(), "prefs" to notify.json())
        scope.launch { runCatching { api.write("registerStudentDevice", body) } }
        // Class-wide news (materials, announcements) comes through one topic per grade and kind.
        val want = if (!notify.on) emptySet() else CLASS_KINDS.filter { notify.wants(it) }.map { "eq_g${p.grade.number}_$it" }.toSet()
        topics(want, ALL_TOPICS - want)
    }

    /** Log out: the server stops notifying this phone for that student. */
    fun forgetDevice() {
        val token = notify.token() ?: return
        scope.launch { runCatching { api.write("unregisterStudentDevice", mapOf("token" to token)) } }
        topics(emptySet(), ALL_TOPICS)
        // The next student on this phone is asked about notifications again.
        notify.on = false
        notify.asked = false
    }

    /** The grade the old 1.8 app remembered, used once to preselect the login. */
    @Suppress("unused") fun legacyGrade(): Grade? = Grade.of(legacyPrefs.getString("grade", null))

    /** True when [newestEventId] is newer than what the student last saw in the bell. */
    fun bellUnread(newestEventId: Long): Boolean = newestEventId > prefs.getLong(KEY_BELL_SEEN, 0L)

    fun markBellSeen(newestEventId: Long) {
        if (newestEventId > prefs.getLong(KEY_BELL_SEEN, 0L)) prefs.edit().putLong(KEY_BELL_SEEN, newestEventId).apply()
    }

    companion object {
        const val BASE_URL = "https://engquest-api.engquestviska.workers.dev/exec"
        const val KEY_BELL_SEEN = "bellSeenId"
        /** News sent to a whole grade (the server sends these to topic eq_g<grade>_<kind>). */
        val CLASS_KINDS = listOf("material", "announcement")
        val ALL_TOPICS = listOf("10", "11").flatMap { g -> CLASS_KINDS.map { "eq_g${g}_$it" } }.toSet()

        fun create(context: Context): AppGraph = AppGraph(
            api = Api(BASE_URL, OkHttpClient()),
            prefs = context.getSharedPreferences("student", Context.MODE_PRIVATE),
            legacyPrefs = context.getSharedPreferences("engquest", Context.MODE_PRIVATE),
            db = AppDb.open(context),
            online = networkStatus(context),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            sendDir = File(context.filesDir, "sends"),
            kickSender = { SendWorker.kick(context) },
            pushToken = { cb -> com.engquest.app.push.Notify.token(cb) },
            topics = { on, off -> com.engquest.app.push.Notify.topics(on, off) },
            updates = com.engquest.app.ui.UpdateChecker(
                com.engquest.app.ui.UpdateChecker.VERSION_URL,
                com.engquest.app.ui.UpdateChecker.SITE_BASE,
                BuildConfig.VERSION_CODE,
                OkHttpClient(),
            ),
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
