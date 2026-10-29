package com.engquest.teacher.ui.scores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.AppGraph
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Core
import com.engquest.teacher.ui.Bad
import com.engquest.teacher.ui.BadSoft
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.Line
import com.engquest.teacher.ui.Muted
import com.engquest.teacher.ui.Warn
import com.engquest.teacher.ui.attendance.Message
import com.engquest.teacher.ui.theme.Brand

private val Ok = Color(0xFF16A34A)
private val TrackColor = Color(0xFFE2E8F0)
private val BoxFill = Color(0xFFF8FAFC)

/** The Scores tab: pick a chapter and a column, then type scores down the list. */
@Composable
fun ScoresScreen(graph: AppGraph, classInfo: ClassInfo) {
    val scope = rememberCoroutineScope()
    val vm = remember(classInfo.id) { ScoresViewModel(graph, classInfo, scope) }
    DisposableEffect(vm) { onDispose { vm.close() } }

    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        ChapterChips(vm)
        when {
            !vm.loaded && vm.loading -> Message("Loading scores…", null, null)
            !vm.loaded -> Message(
                "Could not load scores",
                vm.error ?: "Please check the internet and try again.",
                "Try again" to vm::start,
            )
            vm.students.isEmpty() -> Message("No students in ${classInfo.label}", "Pick another class from the top.", null)
            vm.column == null -> Message("Nothing to enter here", "This tab has no score columns.", null)
            else -> ScoresBody(vm)
        }
    }
}

@Composable
private fun ChapterChips(vm: ScoresViewModel) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SCORE_CHAPTERS.forEach { c ->
            val on = c.id == vm.chapter
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) Brand else Color.White, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.5.dp, if (on) Brand else Line), RoundedCornerShape(12.dp))
                    .selectable(selected = on, role = Role.RadioButton, onClick = { vm.chooseChapter(c.id) })
                    .padding(vertical = 9.dp)
                    .testTag("chip-${c.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(c.label, color = if (on) Color.White else Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

private fun shortLabel(col: String): String = when (val l = Core.colLabel(col)) {
    "Formative" -> "Form."
    "Summative" -> "Summ."
    else -> l
}

@Composable
private fun ColumnScope.ScoresBody(vm: ScoresViewModel) {
    val focus = LocalFocusManager.current
    val columns = vm.columns
    if (columns.size > 1) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).background(TrackColor, RoundedCornerShape(12.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            columns.forEach { col ->
                val on = col == vm.column
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (on) Color.White else Color.Transparent, RoundedCornerShape(9.dp))
                        .selectable(selected = on, role = Role.RadioButton, onClick = {
                            focus.clearFocus()
                            vm.chooseColumn(col)
                        })
                        .padding(vertical = 8.dp)
                        .testTag("col-$col"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(shortLabel(col), color = if (on) Ink else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
    Text(vm.heading, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp).testTag("scHeading"))
    val status = vm.status
    Text(
        status.text,
        color = if (status.isError) Bad else Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).testTag("scStatus"),
    )
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val last = vm.students.lastIndex
        vm.students.forEachIndexed { i, s ->
            ScoreRow(vm, s, label = Core.colLabel(vm.column.orEmpty()), isLast = i == last)
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun ScoreRow(vm: ScoresViewModel, s: ScoreStudent, label: String, isLast: Boolean) {
    val focus = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }
    val invalid = vm.isInvalid(s.no)
    val low = vm.isLow(s.no)
    val shape = RoundedCornerShape(11.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(s.no, color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(22.dp))
        Text(s.name, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        BasicTextField(
            value = vm.shown(s.no),
            onValueChange = { vm.type(s.no, it) },
            singleLine = true,
            textStyle = TextStyle(
                color = if (invalid) Bad else if (low) Warn else Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(Brand),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = {
                    vm.commit(s.no)
                    focus.moveFocus(FocusDirection.Down)
                },
                onDone = {
                    vm.commit(s.no)
                    focus.clearFocus()
                },
            ),
            modifier = Modifier
                .width(68.dp)
                .height(42.dp)
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true else if (hadFocus) {
                        hadFocus = false
                        vm.commit(s.no)
                    }
                }
                .semantics { contentDescription = "$label for ${s.name}" }
                .testTag("score-${s.no}"),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (invalid) BadSoft else BoxFill, shape)
                        .border(BorderStroke(1.5.dp, if (invalid) Bad else Line), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.shown(s.no).isEmpty()) Text("–", color = Color(0xFF94A3B8), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    inner()
                }
            },
        )
        Column(Modifier.width(42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (vm.isFinal) "final" else "avg", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                vm.total(s.no).ifEmpty { "–" },
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("avg-${s.no}"),
            )
        }
    }
}
