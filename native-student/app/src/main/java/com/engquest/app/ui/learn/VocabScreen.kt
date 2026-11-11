package com.engquest.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.engquest.app.AppGraph
import com.engquest.app.data.ApiError
import com.engquest.app.domain.Profile
import com.engquest.app.domain.Vocabulary
import com.engquest.app.ui.common.EqCard
import com.engquest.app.ui.common.Loading
import com.engquest.app.ui.common.Message
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.common.SectionTitle
import com.engquest.app.ui.theme.LocalEq
import com.engquest.app.ui.theme.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val WEEK_MS = 7L * 24 * 3600 * 1000
private val TYPES = listOf("noun" to "Noun", "verb" to "Verb", "adjective" to "Adj.", "adverb" to "Adv.", "other" to "Other")
private val SECTIONS = listOf("noun" to "📗 Nouns", "verb" to "📘 Verbs", "adjective" to "📙 Adjectives", "adverb" to "📕 Adverbs", "other" to "📒 Other", "" to "📔 Not typed yet")

private fun dayMs(d: String): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(d.take(10))!!.time
}.getOrNull()

/**
 * When the next word slot opens (same rule as the website): a week after the oldest word saved
 * in the last 7 days; otherwise the server's nextSlotAt; 0 when unknown.
 */
fun wordSlotOpensAt(wordDates: List<String>, nextSlotAt: Long, now: Long = System.currentTimeMillis()): Long {
    val first = wordDates.mapNotNull(::dayMs).filter { it >= now - WEEK_MS }.minOrNull()
    return first?.plus(WEEK_MS) ?: nextSlotAt
}

private fun countdown(ms: Long): String {
    val m = (ms / 60000).coerceAtLeast(0)
    val d = m / (60 * 24); val h = (m / 60) % 24; val min = m % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${min}m"
        else -> "${min}m"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabScreen(graph: AppGraph, profile: Profile, onBack: () -> Unit) {
    val eq = LocalEq.current
    val scope = rememberCoroutineScope()
    var vocab by remember { mutableStateOf<Vocabulary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var english by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text, ok?
    var saving by remember { mutableStateOf(false) }
    var removeWord by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val me = mapOf("className" to profile.classId, "studentNo" to profile.studentNo.toString())

    LaunchedEffect(reload) {
        error = null
        try { vocab = withContext(Dispatchers.IO) { graph.repo.vocabulary(profile).value } } catch (e: ApiError) { error = e.userMessage }
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(title = "My Vocabulary", subtitle = "Each new word = +2 XP · up to 30 a week", onBack = onBack)
        val v = vocab
        when {
            v == null && error != null -> Message("Couldn't load your words. $error", onRetry = { reload++ })
            v == null -> Loading()
            else -> {
                val full = v.weeklyRemaining <= 0
                val opens = if (full) wordSlotOpensAt(v.words.map { it.at }, v.nextSlotAt, now) else 0L
                Text(
                    if (!full) "${v.weeklyRemaining} word${if (v.weeklyRemaining == 1) "" else "s"} left this week"
                    else "Full for this week" + if (opens > now) " · next word in ${countdown(opens - now)}" else "",
                    style = MaterialTheme.typography.labelLarge, color = if (full) Tint.amberInk else Tint.greenInk,
                    modifier = Modifier.padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (full) Tint.amber else Tint.green).padding(12.dp),
                )
                EqCard(Modifier.padding(horizontal = 14.dp).alpha(if (full) 0.6f else 1f)) {
                    Text("Add a new word", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    OutlinedTextField(english, { english = it }, enabled = !full, singleLine = true, label = { Text("English word") },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("in-english"), shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(meaning, { meaning = it }, enabled = !full, singleLine = true, label = { Text("Indonesian meaning") },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("in-meaning"), shape = RoundedCornerShape(14.dp))
                    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TYPES.forEach { (k, label) ->
                            val on = type == k
                            Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else eq.ink,
                                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) eq.accent else eq.bg)
                                    .clickable(enabled = !full) { type = if (on) "" else k }.padding(horizontal = 12.dp, vertical = 7.dp).testTag("type-$k"))
                        }
                    }
                    val canAdd = !full && !saving && english.isNotBlank() && meaning.isNotBlank()
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (canAdd) eq.accent else eq.line)
                            .clickable(enabled = canAdd) {
                                saving = true
                                msg = null
                                scope.launch {
                                    val words = JsonArray(listOf(JsonObject(mapOf("english" to JsonPrimitive(english.trim()), "indonesian" to JsonPrimitive(meaning.trim()), "type" to JsonPrimitive(type)))))
                                    try {
                                        val res = withContext(Dispatchers.IO) { graph.repo.write("addVocabulary", me + ("words" to words.toString())) }
                                        val added = res["added"]?.jsonPrimitive?.intOrNull ?: 0
                                        val reason = (res["rejected"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.get("reason")?.jsonPrimitive?.contentOrNull }
                                        msg = if (added > 0) "✅ +${res["xp"]?.jsonPrimitive?.intOrNull ?: 2} XP for \"${english.trim()}\"!" to true
                                        else (if (reason == "duplicate") "You added this word before." else if (reason == "weekly-cap") "Full for this week." else "Word not added.") to false
                                        if (added > 0) { english = ""; meaning = ""; type = "" }
                                        reload++
                                    } catch (e: ApiError) {
                                        msg = "Couldn't save. ${e.userMessage}" to false
                                    }
                                    saving = false
                                }
                            }
                            .semantics { if (!canAdd) disabled() }
                            .padding(vertical = 13.dp).testTag("add-word"),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (saving) "Saving…" else "Add word (+2 XP)", color = if (canAdd) Color.White else eq.muted, style = MaterialTheme.typography.titleMedium) }
                    msg?.let { (t, ok) -> Text(t, style = MaterialTheme.typography.bodySmall, color = if (ok) Tint.greenInk else Tint.redInk, modifier = Modifier.padding(vertical = 8.dp)) }
                }

                SectionTitle("My words (${v.words.size})")
                if (v.words.isEmpty()) Message("No words yet. Read something in English and add the new words here.")
                SECTIONS.forEach { (key, title) ->
                    val words = v.words.filter { (it.type.lowercase().takeIf { t -> TYPES.any { it.first == t } } ?: "") == key }
                    if (words.isNotEmpty()) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = eq.muted, modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 4.dp))
                        EqCard(Modifier.padding(horizontal = 14.dp)) {
                            words.forEach { w ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(w.english, style = MaterialTheme.typography.bodyLarge)
                                        Text(w.indonesian, style = MaterialTheme.typography.bodySmall, color = eq.muted)
                                    }
                                    TextButton(onClick = { removeWord = w.english }) { Text("Remove", color = Tint.redInk) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    removeWord?.let { w ->
        AlertDialog(
            onDismissRequest = { removeWord = null },
            title = { Text("Remove \"$w\"?") },
            text = { Text("Its 2 XP is taken back too.") },
            confirmButton = {
                TextButton(onClick = {
                    removeWord = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { graph.repo.write("removeVocabulary", me + ("english" to w)) } }
                            .onFailure { msg = "Couldn't remove it. Check your connection." to false }
                        reload++
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeWord = null }) { Text("Keep") } },
        )
    }
}
