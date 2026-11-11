package com.engquest.app.ui.send

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.engquest.app.AppGraph
import com.engquest.app.data.ApiError
import com.engquest.app.data.MAX_PHOTOS
import com.engquest.app.data.PhotoPicks
import com.engquest.app.data.SendState
import com.engquest.app.data.compressPhoto
import com.engquest.app.domain.MySubmissions
import com.engquest.app.domain.Profile
import com.engquest.app.ui.common.EqCard
import com.engquest.app.ui.common.Pill
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.common.SectionTitle
import com.engquest.app.ui.theme.LocalEq
import com.engquest.app.ui.theme.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val CHAPTERS = 3
private const val TASKS = 3
/** Task scores live in chapters 4–6 of the score book (chapter n -> n + 3), as on the website. */
private const val SCORE_CHAPTER_OFFSET = 3

private data class SendData(
    val units: List<String> = emptyList(),
    val subs: MySubmissions? = null,
    /** chapter (1..3) -> "Task 1" -> score */
    val scores: Map<Int, Map<String, Double?>> = emptyMap(),
    val error: String? = null,
)

private suspend fun loadSend(graph: AppGraph, p: Profile): SendData = coroutineScope {
    val units = async { runCatching { graph.repo.unitNames(p.grade).value }.getOrDefault(emptyList()) }
    val scores = (1..CHAPTERS).map { ch ->
        async { ch to runCatching { graph.repo.chapterScores(p.classId, ch + SCORE_CHAPTER_OFFSET).value.rows.firstOrNull { it.no == p.studentNo }?.scores.orEmpty() }.getOrDefault(emptyMap()) }
    }
    try {
        SendData(units.await(), graph.repo.mySubmissions(p).value, scores.awaitAll().toMap())
    } catch (e: ApiError) {
        SendData(units.await(), null, scores.awaitAll().toMap(), e.userMessage)
    }
}

fun taskLabel(key: String): String {
    val m = Regex("^C(\\d+)T(\\d+)$").find(key) ?: return key
    return "Chapter ${m.groupValues[1]} · Task ${m.groupValues[2]}"
}

private fun statusLook(status: String?): Triple<String, Color, Color>? = when (status) {
    "approved" -> Triple("Approved", Tint.green, Tint.greenInk)
    "pending" -> Triple("Waiting for teacher", Tint.amber, Tint.amberInk)
    "rejected" -> Triple("Sent back · redo it", Tint.red, Tint.redInk)
    else -> null
}

@Composable
fun SendScreen(graph: AppGraph, profile: Profile) {
    val context = LocalContext.current
    val eq = LocalEq.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(SendData()) }
    var reload by remember { mutableIntStateOf(0) }
    var chapter by rememberSaveable { mutableIntStateOf(0) }
    var task by rememberSaveable { mutableIntStateOf(0) }
    val picks = remember { PhotoPicks() }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    val queued by graph.sendQueue.observe().collectAsState(initial = emptyList())

    LaunchedEffect(profile, reload, queued.size) { data = withContext(Dispatchers.IO) { loadSend(graph, profile) } }

    // First open: jump to the first chapter with work still to do.
    LaunchedEffect(data.subs) {
        if (chapter == 0 && data.subs != null) {
            chapter = (1..CHAPTERS).firstOrNull { ch -> (1..TASKS).any { data.subs!!.statuses["C${ch}T$it"] != "approved" } } ?: 1
        }
    }

    fun addPicked(uris: List<Uri>) {
        val left = picks.add(uris.map { it.toString() })
        note = if (left > 0) "Maximum $MAX_PHOTOS photos" else null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { addPicked(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) addPicked(listOf(Uri.parse(u)))
    }

    val key = if (chapter > 0 && task > 0) "C${chapter}T$task" else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(title = "Send a task", subtitle = "Photos of your work go straight to your teacher")

        SectionTitle("Which chapter?")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..CHAPTERS).forEach { ch ->
                val done = (1..TASKS).all { data.subs?.statuses?.get("C${ch}T$it") == "approved" }
                val name = data.units.getOrNull(ch - 1)
                ChoiceChip(
                    text = "Ch $ch" + (if (done) " ✓" else ""), sub = name, selected = chapter == ch,
                    modifier = Modifier.testTag("chapter-$ch"),
                ) { chapter = ch; task = 0; picks.clear(); note = null }
            }
        }

        if (chapter > 0) {
            SectionTitle("Which task?")
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..TASKS).forEach { t ->
                    val k = "C${chapter}T$t"
                    val st = data.subs?.statuses?.get(k)
                    val score = data.scores[chapter]?.get("Task $t")?.takeIf { it != 0.0 }
                    val look = statusLook(st)
                    val sel = task == t
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                            .then(if (sel) Modifier.border(2.dp, eq.accent, RoundedCornerShape(16.dp)) else Modifier)
                            .clickable { task = t; picks.clear(); note = null }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("task-$t"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Task $t", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (score != null) Pill("Score ${fmt(score)}", Tint.blue, Tint.blueInk, Modifier.padding(end = 6.dp))
                        if (look != null) Pill(look.first, look.second, look.third) else Pill("Not sent", Tint.slate, Tint.slateInk)
                    }
                }
            }
        }

        if (key != null) {
            val st = data.subs?.statuses?.get(key)
            if (st == "approved") {
                Text(
                    "Already approved. Sending new photos replaces the approved ones, and your teacher checks again.",
                    style = MaterialTheme.typography.bodySmall, color = Tint.amberInk,
                    modifier = Modifier.padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tint.amber).padding(12.dp),
                )
            }
            SectionTitle("Add photos")
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigPick("📷", "Take photo", Tint.amber, Modifier.weight(1f), enabled = !picks.full && !busy) {
                    val f = File(context.cacheDir, "camera/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
                    val u = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                    cameraUri = u.toString()
                    camera.launch(u)
                }
                BigPick("🖼️", "From gallery", Tint.blue, Modifier.weight(1f), enabled = !picks.full && !busy) {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            if (picks.items.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    picks.items.forEach { u -> Thumb(u, onRemove = { picks.remove(u); note = null }) }
                }
            }
            val count = picks.items.size
            Text(
                note ?: if (count == 0) "No photos yet" else "$count of $MAX_PHOTOS photos ready",
                style = MaterialTheme.typography.bodySmall, color = if (note != null) Tint.redInk else eq.muted,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
            val canSend = count > 0 && !busy
            Box(
                Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (canSend) eq.accent else eq.line)
                    .clickable(enabled = canSend) {
                        busy = true
                        scope.launch {
                            val files = withContext(Dispatchers.IO) {
                                picks.items.mapNotNull { u ->
                                    val out = File(context.cacheDir, "picked/${UUID.randomUUID()}.jpg")
                                    out.takeIf { compressPhoto(context, Uri.parse(u), it) }
                                }
                            }
                            if (files.isEmpty()) note = "Couldn't read those photos. Please pick them again."
                            else {
                                graph.sendQueue.enqueue(profile, key, files)
                                picks.clear()
                                note = null
                                task = 0
                            }
                            busy = false
                        }
                    }
                    .padding(vertical = 15.dp)
                    .testTag("send-button")
                    .semantics { contentDescription = if (canSend) "Send to teacher" else "Send to teacher, add photos first" },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (busy) "Getting photos ready…" else "Send to teacher", color = if (canSend) Color.White else eq.muted, style = MaterialTheme.typography.titleMedium)
            }
        }

        // Sends still on this phone (no signal yet, or refused), then what the teacher has.
        if (queued.isNotEmpty()) {
            SectionTitle("On this phone")
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                queued.forEach { q ->
                    EqCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(taskLabel(q.taskKey), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    when (q.state) {
                                        SendState.FAILED -> "Couldn't send: ${q.error.orEmpty()}"
                                        SendState.SENDING -> "Sending…"
                                        SendState.PENDING -> "Waiting to send · goes when you have signal"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (q.state == SendState.FAILED) Tint.redInk else Tint.amberInk,
                                )
                            }
                            if (q.state == SendState.FAILED) TextButton(onClick = { scope.launch { graph.sendQueue.retry(q.id) } }) { Text("Retry") }
                            if (q.state != SendState.SENDING) TextButton(onClick = { scope.launch { graph.sendQueue.remove(q.id) } }) { Text("Remove") }
                        }
                    }
                }
            }
        }

        val items = data.subs?.items.orEmpty()
        SectionTitle("My sent tasks")
        if (data.error != null && data.subs == null) {
            Text("Couldn't load your tasks. ${data.error}", color = eq.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp))
            TextButton(onClick = { reload++ }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Try again") }
        } else if (data.subs != null && items.isEmpty()) {
            Text("Nothing sent yet.", color = eq.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp))
        } else {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.groupBy { it.task }.toSortedMap().forEach { (task, photos) ->
                    val st = data.subs?.statuses?.get(task) ?: photos.last().status
                    val look = statusLook(st)
                    EqCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(taskLabel(task), style = MaterialTheme.typography.bodyMedium)
                                Text("${photos.size} photo${if (photos.size == 1) "" else "s"} · sent ${photos.last().date.take(10)}", style = MaterialTheme.typography.bodySmall, color = eq.muted)
                            }
                            if (look != null) Pill(look.first, look.second, look.third)
                        }
                        if (st == "pending") {
                            TextButton(onClick = { confirmDelete = photos.last().id }) { Text("Delete this send") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    val del = confirmDelete
    if (del != null) {
        val photos = data.subs?.items.orEmpty().let { all -> val t = all.firstOrNull { it.id == del }?.task; all.filter { it.task == t } }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this send?") },
            text = { Text("Your teacher hasn't checked it yet. The photos will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        for (ph in photos) runCatching {
                            graph.repo.write("deleteMySubmission", mapOf("className" to profile.classId, "studentNo" to profile.studentNo.toString(), "id" to ph.id.toString()))
                        }
                        reload++
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

private fun fmt(d: Double) = if (d == Math.floor(d)) d.toInt().toString() else d.toString()

@Composable
private fun ChoiceChip(text: String, sub: String?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val eq = LocalEq.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) eq.accent else Color.White)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (selected) Color.White else eq.ink)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(alpha = 0.9f) else eq.muted)
    }
}

@Composable
private fun BigPick(emoji: String, label: String, tint: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.5f).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Thumb(uri: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(68.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Tint.slate)) {
            bmp?.let { androidx.compose.foundation.Image(it, contentDescription = "Picked photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(Color(0xFF0F172A))
                .semantics { contentDescription = "Remove photo" }.clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) { Text("×", color = Color.White, style = MaterialTheme.typography.labelLarge) }
    }
}
