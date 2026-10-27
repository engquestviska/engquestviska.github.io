package com.engquest.teacher.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.engquest.teacher.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** A newer build of the app: its name, what changed, and where to download it. */
data class UpdateInfo(val versionName: String, val notes: String, val apkUrl: String)

/**
 * Looks at the published version file. [versionUrl] and [siteBase] are injectable so tests
 * can point them at a local server; [currentVersion] is this build's version code.
 */
class UpdateChecker(
    private val versionUrl: String,
    private val siteBase: String,
    private val currentVersion: Int,
    private val client: OkHttpClient,
) {
    /** The newer build, or null when this one is current, the phone is offline, or the file is unreadable. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(versionUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val obj = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                if (obj.getValue("versionCode").jsonPrimitive.int <= currentVersion) return@use null
                UpdateInfo(
                    versionName = obj.getValue("versionName").jsonPrimitive.content,
                    notes = obj["notes"]?.jsonPrimitive?.content.orEmpty(),
                    apkUrl = siteBase + obj.getValue("apk").jsonPrimitive.content,
                )
            }
        }.getOrNull()
    }

    companion object {
        const val VERSION_URL = "https://engquestviska.github.io/teacher-app-version.json"
        const val SITE_BASE = "https://engquestviska.github.io/"
    }
}

/**
 * Downloads the new APK to one fixed file (so there are never duplicates) and, when the
 * download finishes, opens the system installer on it.
 */
class UpdateInstaller(private val context: Context) {
    fun start(apkUrl: String) {
        val file = File(context.getExternalFilesDir(null), APK_NAME)
        file.delete()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("EngQuest Teacher update")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, null, APK_NAME),
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != id) return
                runCatching { context.unregisterReceiver(this) }
                install(file)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun install(file: File) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        const val APK_NAME = "EngQuestTeacher-update.apk"
    }
}

/** A one-line banner when a newer build exists. Shows nothing otherwise, including on any error. */
@Composable
fun UpdateBanner(checker: UpdateChecker, onUpdate: (apkUrl: String) -> Unit) {
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(checker) { info = checker.check() }
    val found = info ?: return
    var started by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp)
            .background(BrandSoft, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .testTag("updateBanner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "New version ${found.versionName} — ${found.notes}",
            color = Brand,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                started = true
                onUpdate(found.apkUrl)
            },
            enabled = !started,
        ) { Text(if (started) "Downloading…" else "Update", color = Brand, fontWeight = FontWeight.ExtraBold) }
    }
}
