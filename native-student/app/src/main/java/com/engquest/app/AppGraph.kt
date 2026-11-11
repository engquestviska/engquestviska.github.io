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
) {
    val repo = Repo(api, db)
    val profiles = ProfileStore(prefs)
    val sendQueue = SendQueue(db, api, sendDir, kickSender)

    /** The grade the old 1.8 app remembered, used once to preselect the login. */
    fun legacyGrade(): Grade? = Grade.of(legacyPrefs.getString("grade", null))

    /** True when [newestEventId] is newer than what the student last saw in the bell. */
    fun bellUnread(newestEventId: Long): Boolean = newestEventId > prefs.getLong(KEY_BELL_SEEN, 0L)

    fun markBellSeen(newestEventId: Long) {
        if (newestEventId > prefs.getLong(KEY_BELL_SEEN, 0L)) prefs.edit().putLong(KEY_BELL_SEEN, newestEventId).apply()
    }

    companion object {
        const val BASE_URL = "https://engquest-api.engquestviska.workers.dev/exec"
        const val KEY_BELL_SEEN = "bellSeenId"

        fun create(context: Context): AppGraph = AppGraph(
            api = Api(BASE_URL, OkHttpClient()),
            prefs = context.getSharedPreferences("student", Context.MODE_PRIVATE),
            legacyPrefs = context.getSharedPreferences("engquest", Context.MODE_PRIVATE),
            db = AppDb.open(context),
            online = networkStatus(context),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            sendDir = File(context.filesDir, "sends"),
            kickSender = { SendWorker.kick(context) },
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
