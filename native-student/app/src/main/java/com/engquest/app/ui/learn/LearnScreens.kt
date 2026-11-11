package com.engquest.app.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.engquest.app.AppGraph
import com.engquest.app.domain.Material
import com.engquest.app.domain.Profile
import com.engquest.app.ui.common.EqCard
import com.engquest.app.ui.common.IconDot
import com.engquest.app.ui.common.Loading
import com.engquest.app.ui.common.Message
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.common.SectionTitle
import com.engquest.app.ui.home.OfflineNote
import com.engquest.app.ui.theme.LocalEq
import com.engquest.app.ui.theme.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** The Learn tab: four places to go. */
@Composable
fun LearnScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(title = "Learn", subtitle = "Lessons, assignments, reading and your words")
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Place("📘", "Materials", "Slides and books for each chapter", Tint.blue) { onOpen("materials") }
            Place("📝", "Assignments", "Work your teacher gave you", Tint.amber) { onOpen("assignments") }
            Place("📖", "Reading", "Texts to read", Tint.green) { onOpen("reading") }
            Place("🔤", "My Vocabulary", "Save new words, up to 30 a week", Tint.violet) { onOpen("vocab") }
        }
    }
}

@Composable
private fun Place(emoji: String, title: String, sub: String, tint: Color, onClick: () -> Unit) {
    EqCard(onClick = onClick) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconDot(emoji, tint, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalEq.current.muted)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = LocalEq.current.muted)
        }
    }
}

/** Materials, Assignments and Reading all come from getMaterials, under different chapter names. */
enum class MaterialKind(val title: String, val chapters: List<String>?) {
    MATERIALS("Materials", listOf("1", "2", "3")),
    ASSIGNMENTS("Assignments", listOf("assign1", "assign2", "assign3")),
    READING("Reading", null),
}

private data class MatData(val groups: List<Pair<String, List<Material>>> = emptyList(), val offlineSince: Long? = null, val error: String? = null, val loaded: Boolean = false)

@Composable
fun MaterialsScreen(graph: AppGraph, profile: Profile, kind: MaterialKind, onBack: () -> Unit, openLink: (String) -> Unit) {
    var data by remember(kind) { mutableStateOf(MatData()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(kind, reload) {
        data = withContext(Dispatchers.IO) {
            coroutineScope {
                val units = async { runCatching { graph.repo.unitNames(profile.grade).value }.getOrDefault(emptyList()) }
                val chapters = kind.chapters ?: listOf("reading")
                val lists = chapters.map { ch -> async { runCatching { graph.repo.materials(profile.grade, ch) }.getOrNull() } }.awaitAll()
                if (lists.all { it == null }) return@coroutineScope MatData(error = "Check your connection.", loaded = true)
                val names = units.await()
                val groups = chapters.mapIndexed { i, ch ->
                    val label = if (kind.chapters == null) "" else "Chapter ${i + 1}" + (names.getOrNull(i)?.let { ": $it" } ?: "")
                    label to lists[i]?.value.orEmpty()
                }
                MatData(groups, lists.filterNotNull().filter { it.fromCache }.minOfOrNull { it.savedAt }, loaded = true)
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(title = kind.title, onBack = onBack)
        when {
            !data.loaded -> Loading()
            data.error != null -> Message("Couldn't load this. ${data.error}", onRetry = { reload++ })
            data.groups.all { it.second.isEmpty() } -> Message("Nothing here yet. Check again after your teacher adds something.")
            else -> {
                data.offlineSince?.let { OfflineNote(it) }
                data.groups.forEach { (label, items) ->
                    if (label.isNotEmpty()) SectionTitle(label)
                    else Spacer(Modifier.height(10.dp))
                    EqCard(Modifier.padding(horizontal = 14.dp)) {
                        if (items.isEmpty()) Text("Nothing yet", style = MaterialTheme.typography.bodySmall, color = LocalEq.current.muted, modifier = Modifier.padding(vertical = 8.dp))
                        items.forEach { m -> MaterialRow(m) { if (m.link.isNotBlank()) openLink(m.link) } }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun MaterialRow(m: Material, onClick: () -> Unit) {
    val (emoji, tint) = when (m.fileType.lowercase()) {
        "pdf" -> "📄" to Tint.red
        "ppt", "pptx", "slides" -> "📊" to Tint.amber
        "video", "youtube" -> "▶️" to Tint.violet
        "doc", "docx" -> "📃" to Tint.blue
        else -> "🔗" to Tint.slate
    }
    EqCard(onClick = onClick, modifier = Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconDot(emoji, tint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(m.fileType.uppercase(), m.addedAt).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = LocalEq.current.muted)
            }
            Text("Open", style = MaterialTheme.typography.labelLarge, color = LocalEq.current.accent)
        }
    }
}
