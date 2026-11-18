package com.engquest.app.ui.chronicle

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.engquest.app.BuildConfig
import com.engquest.app.domain.Profile
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** The end-of-semester story lives on the website; the app shows that same page. */
const val CHRONICLE_URL = "https://engquestviska.github.io/chronicle.html"

private const val INSTAGRAM = "com.instagram.android"

/** What the page reads through `EngQuestApp.profile()` to know whose semester to show. */
fun profileJson(p: Profile): String = buildJsonObject {
    put("grade", p.grade.number)
    put("classId", p.classId)
    put("studentNo", p.studentNo.toString())
    put("name", p.name)
}.toString()

/** Opens Instagram's Story editor with [uri] as a movable sticker over Chronicle's paper/ink colours. */
fun instagramStoryIntent(uri: Uri, appId: String): Intent =
    Intent("com.instagram.share.ADD_TO_STORY")
        .setType("image/png")
        .setPackage(INSTAGRAM)
        .putExtra("source_application", appId)
        .putExtra("interactive_asset_uri", uri)
        .putExtra("top_background_color", "#F4F2ED")
        .putExtra("bottom_background_color", "#141414")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

/** The page's bridge (`window.EngQuestApp`): who the student is, and Android sharing. */
class ChronicleBridge(private val context: Context, private val profile: Profile) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    @JavascriptInterface fun profile(): String = profileJson(profile)

    @JavascriptInterface fun canShareToInstagram(): Boolean =
        BuildConfig.META_APP_ID.isNotBlank() &&
            runCatching { context.packageManager.getPackageInfo(INSTAGRAM, 0) }.isSuccess

    @JavascriptInterface fun shareImage(base64: String) {
        val uri = save(base64, "chronicle.png") ?: return
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        main.post { start(Intent.createChooser(send, "Share your Chronicle")) }
    }

    @JavascriptInterface fun shareToInstagramStory(base64: String) {
        val uri = save(base64, "chronicle-sticker.png") ?: return
        val intent = instagramStoryIntent(uri, BuildConfig.META_APP_ID)
        context.grantUriPermission(INSTAGRAM, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        main.post {
            if (intent.resolveActivity(context.packageManager) != null) start(intent) else shareImage(base64)
        }
    }

    private fun save(base64: String, name: String): Uri? = runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, name).apply { writeBytes(Base64.decode(base64, Base64.DEFAULT)) }
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }.getOrNull()

    private fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "No app on this phone can share this.", Toast.LENGTH_SHORT).show() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChronicleScreen(profile: Profile, url: String = CHRONICLE_URL, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize().testTag("chronicle"),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                addJavascriptInterface(ChronicleBridge(ctx.applicationContext, profile), "EngQuestApp")
                loadUrl(url)
            }
        },
    )
}
