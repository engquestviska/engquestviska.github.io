package com.engquest.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.engquest.app.data.ApiError
import com.engquest.app.data.Repo
import com.engquest.app.domain.Classmate
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.ui.common.EqCard
import com.engquest.app.ui.common.Loading
import com.engquest.app.ui.common.Message
import com.engquest.app.ui.common.QuestHeader
import com.engquest.app.ui.common.SectionTitle
import com.engquest.app.ui.theme.EqTheme
import com.engquest.app.ui.theme.LocalEq
import com.engquest.app.ui.theme.eqColors

/**
 * Pick grade → class → name (no password, same as the website). [preselect] skips the grade step
 * (the old app's saved grade); [note] explains why the student is here again (left the class list).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(repo: Repo, preselect: Grade?, note: String?, onDone: (Profile) -> Unit) {
    var grade by rememberSaveable { mutableStateOf(preselect) }
    var classId by rememberSaveable { mutableStateOf<String?>(null) }

    EqTheme(grade ?: Grade.G10) {
        Column(Modifier.fillMaxSize().background(LocalEq.current.bg)) {
            QuestHeader(
                eyebrow = "Welcome to",
                title = "EngQuest",
                subtitle = when {
                    grade == null -> "Which grade are you in?"
                    classId == null -> "Grade ${grade!!.number}: which class?"
                    else -> "$classId: tap your name"
                },
                onBack = when {
                    classId != null -> ({ classId = null })
                    grade != null -> ({ grade = null })
                    else -> null
                },
            )
            if (note != null) {
                Text(
                    note, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF92400E),
                    modifier = Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFEF3C7)).padding(12.dp),
                )
            }
            val g = grade
            val c = classId
            when {
                g == null -> GradeStep { grade = it }
                c == null -> {
                    SectionTitle("Your class")
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        g.classes.forEach { id ->
                            Text(
                                id, style = MaterialTheme.typography.titleMedium, color = LocalEq.current.accent,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White)
                                    .clickable { classId = id }.padding(horizontal = 22.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
                else -> NameStep(repo, g, c, onDone)
            }
        }
    }
}

@Composable
private fun GradeStep(onPick: (Grade) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Grade.entries.forEach { g ->
            val colors = eqColors(g)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.header)
                    .clickable { onPick(g) }.padding(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Grade ${g.number}", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(g.classes.joinToString(" · "), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                }
                Text("›", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun NameStep(repo: Repo, grade: Grade, classId: String, onDone: (Profile) -> Unit) {
    var list by remember(classId) { mutableStateOf<List<Classmate>?>(null) }
    var error by remember(classId) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by rememberSaveable(classId) { mutableStateOf("") }
    LaunchedEffect(classId, attempt) {
        error = null
        try {
            list = repo.classmates(classId).value.sortedBy { it.no }
        } catch (e: ApiError) {
            error = e.userMessage
        }
    }
    val all = list
    when {
        error != null && all == null -> Message("Couldn't load the names. ${error.orEmpty()}", onRetry = { attempt++ })
        all == null -> Loading()
        else -> Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search your name") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            )
            val shown = all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.no }) { s ->
                    EqCard(onClick = { onDone(Profile(grade, classId, s.no, s.name)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${s.no}", color = LocalEq.current.accent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 14.dp))
                            Text(s.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
