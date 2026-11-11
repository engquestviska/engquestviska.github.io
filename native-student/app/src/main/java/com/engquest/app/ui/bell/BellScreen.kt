package com.engquest.app.ui.bell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.engquest.app.AppGraph
import com.engquest.app.domain.Event
import com.engquest.app.domain.Profile
import com.engquest.app.ui.common.EqCard
import com.engquest.app.ui.common.Loading
import com.engquest.app.ui.common.Message
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.common.SectionTitle
import com.engquest.app.ui.home.EventRow
import com.engquest.app.ui.home.OfflineNote
import com.engquest.app.ui.home.prettyDate
import com.engquest.app.ui.theme.LocalEq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE = 30

/** Everything that happened, newest first, grouped by day. Opening it marks everything seen. */
@Composable
fun BellScreen(graph: AppGraph, profile: Profile, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var events by remember { mutableStateOf<List<Event>?>(null) }
    var more by remember { mutableStateOf(false) }
    var offlineSince by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(attempt) {
        error = null
        runCatching { withContext(Dispatchers.IO) { graph.repo.myEvents(profile, 0) } }
            .onSuccess { f ->
                events = f.value
                more = f.value.size >= PAGE
                offlineSince = f.savedAt.takeIf { f.fromCache }
                f.value.firstOrNull()?.let { graph.markBellSeen(it.id) }
            }
            .onFailure { error = "Check your connection." }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        QuestHeader(title = "What's new", subtitle = "Approved tasks, XP, attendance and news", onBack = onBack)
        val list = events
        when {
            list == null && error != null -> Message("Couldn't load your news. $error", onRetry = { attempt++ })
            list == null -> Loading()
            list.isEmpty() -> Message("Nothing yet. When your teacher approves a task or gives you XP, it shows up here.")
            else -> {
                offlineSince?.let { OfflineNote(it) }
                list.groupBy { it.at.take(10) }.forEach { (day, items) ->
                    SectionTitle(prettyDate(day))
                    EqCard(Modifier.padding(horizontal = 14.dp)) {
                        items.forEach { e -> EventRow(e) { onOpen(e.link.ifBlank { "home" }) } }
                    }
                }
                if (more) {
                    TextButton(onClick = {
                        scope.launch {
                            val last = list.last().id
                            runCatching { withContext(Dispatchers.IO) { graph.repo.myEvents(profile, last) } }.onSuccess { f ->
                                events = list + f.value
                                more = f.value.size >= PAGE
                            }
                        }
                    }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Show older", style = MaterialTheme.typography.labelLarge, color = LocalEq.current.accent) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
