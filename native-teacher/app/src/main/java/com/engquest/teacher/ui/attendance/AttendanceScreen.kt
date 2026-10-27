package com.engquest.teacher.ui.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.AppGraph
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Core
import com.engquest.teacher.ui.AppIcons
import com.engquest.teacher.ui.Bad
import com.engquest.teacher.ui.ConfirmDialog
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.LeaveGuard
import com.engquest.teacher.ui.Line
import com.engquest.teacher.ui.Muted
import com.engquest.teacher.ui.Warn
import com.engquest.teacher.ui.theme.Brand
import java.util.Calendar
import java.util.TimeZone

private val Ok = Color(0xFF16A34A)
private val Info = Color(0xFF2563EB)
private val DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val LETTER = mapOf("Present" to "P", "Absent" to "A", "Sick" to "S", "Permit" to "I")

private fun statusColor(status: String): Color = when (status) {
    "Present" -> Ok
    "Absent" -> Bad
    "Sick" -> Warn
    else -> Info
}

private fun isoParts(iso: String): Triple<Int, Int, Int>? {
    val p = iso.split('-').mapNotNull { it.toIntOrNull() }
    return if (p.size == 3) Triple(p[0], p[1], p[2]) else null
}

/** "Tue, 27 Oct (today)" for a yyyy-MM-dd date. */
internal fun niceDate(iso: String, today: String = todayIso()): String {
    val (y, m, d) = isoParts(iso) ?: return iso
    val day = Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.get(Calendar.DAY_OF_WEEK) - 1
    return "${DAYS[day]}, $d ${MONTHS[m - 1]}" + if (iso == today) " (today)" else ""
}

private fun utcMillis(iso: String): Long? {
    val (y, m, d) = isoParts(iso) ?: return null
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m - 1, d) }.timeInMillis
}

private fun isoFromUtcMillis(ms: Long): String {
    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/** The Attendance tab: pick a day, tap P / A / S / I for each student, then Save. */
@Composable
fun AttendanceScreen(graph: AppGraph, classInfo: ClassInfo, guard: LeaveGuard) {
    val scope = rememberCoroutineScope()
    val vm = remember(classInfo.id) { AttendanceViewModel(graph, classInfo, scope) }
    DisposableEffect(vm) { onDispose { vm.close() } }

    // Tell the shell while there are marks that would be lost by leaving.
    val unsaved = vm.dirty
    LaunchedEffect(unsaved, classInfo.id) {
        guard.unsaved = if (unsaved) "Your attendance changes for ${classInfo.label} are not saved yet." else null
    }
    DisposableEffect(vm) { onDispose { guard.unsaved = null } }

    when {
        !vm.loaded && vm.loading -> Message("Loading ${classInfo.label}…", null, null)
        !vm.loaded -> Message(
            "Could not load the class",
            vm.error ?: "Please check the internet and try again.",
            "Try again" to vm::start,
        )
        vm.students.isEmpty() -> Message("No students in ${classInfo.label}", "Pick another class from the top.", null)
        else -> AttendanceBody(vm)
    }
}

@Composable
internal fun Message(title: String, detail: String?, action: Pair<String, () -> Unit>?) {
    Column(
        Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Ink, textAlign = TextAlign.Center)
        if (detail != null) Text(detail, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
        if (action != null) {
            TextButton(onClick = action.second, modifier = Modifier.testTag("retry")) {
                Text(action.first, color = Brand, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AttendanceBody(vm: AttendanceViewModel) {
    var pickDate by remember { mutableStateOf(false) }
    var askRemove by remember { mutableStateOf(false) }
    var switchTo by remember { mutableStateOf<String?>(null) }
    val today = remember { todayIso() }

    fun goToDate(iso: String) {
        if (iso == vm.date) return
        if (vm.dirty) switchTo = iso else vm.showDate(iso)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("attendanceList"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { DateRow(vm, today, onPick = { pickDate = true }, onAllPresent = vm::markEveryonePresent, onRemove = { askRemove = true }) }
            item { Summary(vm.counts) }
            item {
                Text(
                    "P Present · A Absent · S Sick · I Permit",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                )
            }
            items(vm.students, key = { it.no }) { s ->
                StudentRow(s.no, s.nickname.ifBlank { s.name }, vm.marks[s.no] ?: "Present", vm.saving) { vm.mark(s.no, it) }
            }
        }
        SaveBar(vm, Modifier.align(Alignment.BottomCenter))
    }

    if (pickDate) {
        DatePickerSheet(
            current = vm.date,
            today = today,
            onPick = { pickDate = false; goToDate(it) },
            onDismiss = { pickDate = false },
        )
    }
    if (askRemove) {
        ConfirmDialog(
            title = "Remove this meeting?",
            text = "This deletes everyone's marks for ${niceDate(vm.date, today)} in ${vm.classInfo.label}. It cannot be undone.",
            confirmLabel = "Remove",
            confirmTag = "confirmRemove",
            onConfirm = { askRemove = false; vm.removeMeeting() },
            onDismiss = { askRemove = false },
        )
    }
    switchTo?.let { iso ->
        ConfirmDialog(
            title = "Leave without saving?",
            text = "Your attendance changes for ${vm.classInfo.label} are not saved yet.",
            confirmLabel = "Leave",
            confirmTag = "confirmSwitchDate",
            onConfirm = { switchTo = null; vm.showDate(iso) },
            onDismiss = { switchTo = null },
        )
    }
}

@Composable
private fun DateRow(
    vm: AttendanceViewModel,
    today: String,
    onPick: () -> Unit,
    onAllPresent: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onPick)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("dateRow"),
        ) {
            Text(niceDate(vm.date, today), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink)
            Text(
                (vm.meeting?.let { "Meeting ${it.number}" } ?: "New meeting") + " · tap to change the day",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.testTag("meetingLabel"),
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("attendanceMenu")) {
                Icon(AppIcons.MoreVertical, contentDescription = "More for this meeting", tint = Muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Mark everyone Present") },
                    onClick = { menu = false; onAllPresent() },
                )
                if (vm.meeting != null) {
                    DropdownMenuItem(
                        text = { Text("Remove this meeting", color = Bad) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Summary(counts: Map<String, Int>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Core.STATUSES.forEach { status ->
            Column(
                Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Line), RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp)
                    .testTag("chip-$status"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${counts[status] ?: 0}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = statusColor(status), modifier = Modifier.testTag("count-$status"))
                Text(status.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StudentRow(no: String, name: String, mark: String, locked: Boolean, onMark: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 7.dp, top = 7.dp, bottom = 7.dp)
            .testTag("row-$no"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(no, color = Muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.End)
        Text(name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Core.STATUSES.forEach { status ->
                val on = mark == status
                val color = statusColor(status)
                Box(
                    Modifier
                        .size(38.dp)
                        .background(if (on) color else Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.dp, if (on) color else Line), RoundedCornerShape(10.dp))
                        .selectable(selected = on, enabled = !locked, role = Role.RadioButton, onClick = { onMark(status) })
                        .testTag("mark-$no-$status"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(LETTER.getValue(status), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (on) Color.White else Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
private fun SaveBar(vm: AttendanceViewModel, modifier: Modifier) {
    val n = vm.changes
    val title: String
    val sub: String
    when {
        vm.saving -> { title = "Saving…"; sub = "${vm.students.size} students" }
        vm.meeting == null -> {
            title = "Not saved yet"
            sub = if (n > 0) "$n marked not present" else "Everyone is Present"
        }
        n > 0 -> { title = "Not saved yet"; sub = "$n change" + if (n > 1) "s" else "" }
        else -> { title = "Saved ✓"; sub = "Meeting ${vm.meeting?.number} · ${vm.counts["Present"] ?: 0} present" }
    }
    Row(
        modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .fillMaxWidth()
            .background(Ink, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            .testTag("saveBar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.testTag("saveTitle"))
            Text(sub, color = Color(0xFFCBD5E1), fontSize = 12.sp)
        }
        TextButton(
            onClick = vm::save,
            enabled = vm.canSave,
            modifier = Modifier
                .background(if (vm.canSave) Brand else Color(0xFF475569), RoundedCornerShape(12.dp))
                .testTag("saveButton"),
        ) {
            Text("Save", color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(current: String, today: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val latest = remember(today) { utcMillis(today) ?: Long.MAX_VALUE }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = utcMillis(current),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latest
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onPick(state.selectedDateMillis?.let(::isoFromUtcMillis) ?: current) },
                modifier = Modifier.testTag("showDate"),
            ) { Text("Show", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(today) }, modifier = Modifier.testTag("todayDate")) { Text("Today") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        DatePicker(state = state)
    }
}
