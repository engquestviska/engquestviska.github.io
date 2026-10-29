package com.engquest.teacher.ui.strikes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.engquest.teacher.domain.Core
import com.engquest.teacher.ui.Bad
import com.engquest.teacher.ui.BottomSheet
import com.engquest.teacher.ui.ConfirmDialog
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.Line
import com.engquest.teacher.ui.Muted
import com.engquest.teacher.ui.Warn
import com.engquest.teacher.ui.WarnSoft
import com.engquest.teacher.ui.attendance.Message
import com.engquest.teacher.ui.home.dayLabel
import com.engquest.teacher.ui.theme.Brand

private val BadSoft = Color(0xFFFEE2E2)
private val Track = Color(0xFFE2E8F0)

/** One strike blocks summative tests; three block class participation (the website's rule). */
internal fun strikeStatus(total: Int): String = when {
    total >= 3 -> "Participation blocked"
    total >= 1 -> "Summative blocked"
    else -> "No strikes"
}

/** Strikes for the selected class: who has how many, and add or remove one with a reason. */
@Composable
fun StrikesScreen(graph: AppGraph, classInfo: ClassInfo) {
    val scope = rememberCoroutineScope()
    val vm = remember(classInfo.id) { StrikesViewModel(graph, classInfo, scope) }
    DisposableEffect(vm) { onDispose { vm.close() } }
    var openNo by rememberSaveable(classInfo.id) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Track, RoundedCornerShape(12.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Toggle("All students", !vm.onlyWithStrikes, Modifier.weight(1f)) { vm.onlyWithStrikes = false }
            Toggle("With strikes", vm.onlyWithStrikes, Modifier.weight(1f)) { vm.onlyWithStrikes = true }
        }
        vm.note?.let { Text(it, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).testTag("strikeNote")) }
        when {
            vm.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            vm.students.isEmpty() && vm.error != null -> Message("Couldn't load strikes", vm.error, "Try again" to { vm.start() })
            else -> {
                val shown = vm.students.filter { !vm.onlyWithStrikes || it.total > 0 }
                if (shown.isEmpty()) {
                    Message(if (vm.onlyWithStrikes) "No strikes in ${classInfo.label}" else "No students in ${classInfo.label}", null, null)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown, key = { it.no }) { s -> StudentRow(s) { openNo = s.no } }
                    item { Box(Modifier.padding(12.dp)) }
                }
            }
        }
    }

    vm.students.firstOrNull { it.no == openNo }?.let { s ->
        StudentSheet(s, onAdd = { vm.add(s.no, it) }, onRemove = { vm.removeLatest(s.no) }, onDismiss = { openNo = null })
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
private fun StudentRow(s: StrikeStudent, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("strikeRow"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(s.no, color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val latest = s.strikes.firstOrNull()
            Text(
                if (latest == null) strikeStatus(0) else "${strikeStatus(s.total)} · ${latest.reason}",
                color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Count(s.total)
    }
}

@Composable
private fun Count(total: Int) {
    val (fg, bg) = when {
        total >= 3 -> Bad to BadSoft
        total >= 1 -> Warn to WarnSoft
        else -> Muted to Track
    }
    Box(Modifier.size(34.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text("$total", color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentSheet(s: StrikeStudent, onAdd: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable(s.no) { mutableStateOf("") }
    var askRemove by remember { mutableStateOf(false) }
    BottomSheet(title = s.name, onDismiss = onDismiss, modifier = Modifier.testTag("strikeSheet")) {
        Text("${s.total} strike${if (s.total == 1) "" else "s"} · ${strikeStatus(s.total)}", color = Muted, fontSize = 13.sp)
        s.strikes.forEach { k ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(k.reason.ifBlank { "(no reason)" }, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(dayLabel(k.date), color = Muted, fontSize = 12.sp)
            }
        }
        Text("Add a strike", color = Ink, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK_REASONS.forEach { q ->
                val on = reason == q
                Text(
                    q, color = if (on) Color.White else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(if (on) Brand else Color.White, RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.dp, if (on) Brand else Line), RoundedCornerShape(10.dp))
                        .clickable { reason = q }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
        OutlinedTextField(
            value = reason, onValueChange = { reason = it }, label = { Text("Reason") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("strikeReason"),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { askRemove = true }, enabled = s.total > 0, modifier = Modifier.weight(1f).testTag("strikeRemove"),
            ) { Text("Remove latest", color = if (s.total > 0) Bad else Muted) }
            Button(
                onClick = { onAdd(reason); reason = "" }, enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Bad),
                modifier = Modifier.weight(1f).testTag("strikeAdd"),
            ) { Text("Add strike", fontWeight = FontWeight.Bold) }
        }
    }
    if (askRemove) {
        val first = Core.firstName(s.name)
        ConfirmDialog(
            title = "Remove a strike?",
            text = "Remove $first's latest strike (${s.strikes.firstOrNull()?.reason.orEmpty()})?",
            confirmLabel = "Remove",
            confirmTag = "confirmRemoveStrike",
            onConfirm = { askRemove = false; onRemove() },
            onDismiss = { askRemove = false },
        )
    }
}
