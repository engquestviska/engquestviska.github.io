package com.engquest.teacher.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.AppGraph
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.Line
import com.engquest.teacher.ui.Muted
import com.engquest.teacher.ui.attendance.Message
import com.engquest.teacher.ui.theme.Brand

private val Good = Color(0xFF059669)
private val GoodSoft = Color(0xFFD1FAE5)
private val Track = Color(0xFFE2E8F0)

/** Which tasks each student of the class has finished; tap a task to tick or untick it. */
@Composable
fun TasksScreen(graph: AppGraph, classInfo: ClassInfo) {
    val scope = rememberCoroutineScope()
    val vm = remember(classInfo.id) { TasksViewModel(graph, classInfo, scope) }
    DisposableEffect(vm) { onDispose { vm.close() } }

    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { ch ->
                val on = vm.chapter == ch
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (on) Brand else Color.White, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, if (on) Brand else Line), RoundedCornerShape(12.dp))
                        .selectable(selected = on, role = Role.RadioButton) { vm.pickChapter(ch) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Ch $ch", color = if (on) Color.White else Ink, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Track, RoundedCornerShape(12.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Toggle("All students", !vm.onlyMissing, Modifier.weight(1f)) { vm.onlyMissing = false }
            Toggle("Not finished", vm.onlyMissing, Modifier.weight(1f)) { vm.onlyMissing = true }
        }
        vm.note?.let { Text(it, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) }
        when {
            vm.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            vm.students.isEmpty() && vm.error != null -> Message("Couldn't load tasks", vm.error, "Try again" to { vm.start() })
            else -> {
                val keys = vm.keys
                val shown = vm.students.filter { s -> !vm.onlyMissing || keys.any { s.done[it] != true } }
                if (shown.isEmpty()) {
                    Message(if (vm.onlyMissing) "Everyone has finished Chapter ${vm.chapter}" else "No students in ${classInfo.label}", null, null)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown, key = { it.no }) { s -> StudentRow(s, keys) { key -> vm.toggle(s.no, key) } }
                    item { Box(Modifier.padding(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (on) Color.White else Color.Transparent, RoundedCornerShape(9.dp))
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) Brand else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
}

@Composable
private fun StudentRow(s: TaskStudent, keys: List<String>, onToggle: (String) -> Unit) {
    val done = keys.count { s.done[it] == true }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("taskRow"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.no, color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(s.name, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$done/${keys.size}", color = if (done == keys.size) Good else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            keys.forEach { key ->
                val on = s.done[key] == true
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (on) GoodSoft else Color.White, RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.dp, if (on) Good else Line), RoundedCornerShape(10.dp))
                        .clickable { onToggle(key) }
                        .padding(vertical = 8.dp)
                        .testTag("task-${s.no}-$key"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (if (on) "✓ " else "") + "Task ${key.substringAfter('T')}",
                        color = if (on) Good else Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
