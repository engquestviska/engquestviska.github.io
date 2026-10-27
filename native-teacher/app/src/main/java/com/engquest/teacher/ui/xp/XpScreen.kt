package com.engquest.teacher.ui.xp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.engquest.teacher.ui.Bad
import com.engquest.teacher.ui.BadSoft
import com.engquest.teacher.ui.BrandSoft
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.Line
import com.engquest.teacher.ui.Muted
import com.engquest.teacher.ui.attendance.Message
import com.engquest.teacher.ui.theme.Brand
import kotlinx.coroutines.delay

private val Ok = Color(0xFF16A34A)
private val ModeTrack = Color(0xFFE2E8F0)
private val TileTakeBackBorder = Color(0xFFFECACA)
private val TileTakeBackFill = Color(0xFFFFF7F7)
private val AvatarColors = listOf(
    0xFF6366F1, 0xFF0EA5E9, 0xFFF59E0B, 0xFF10B981, 0xFFEC4899, 0xFF8B5CF6,
    0xFF14B8A6, 0xFFF97316, 0xFF3B82F6, 0xFF22C55E, 0xFFE11D48, 0xFFA855F7,
).map { Color(it) }
private const val MINUS = "−"

/** The Give XP tab: pick a reason once, then tap each student who earned it. */
@Composable
fun XpScreen(graph: AppGraph, classInfo: ClassInfo) {
    val scope = rememberCoroutineScope()
    val vm = remember(classInfo.id) { XpViewModel(graph, classInfo, scope) }
    DisposableEffect(vm) { onDispose { vm.close() } }

    when {
        !vm.loaded && vm.loading -> Message("Loading ${classInfo.label}…", null, null)
        !vm.loaded -> Message(
            "Could not load the class",
            vm.error ?: "Please check the internet and try again.",
            "Try again" to vm::start,
        )
        vm.students.isEmpty() -> Message("No students in ${classInfo.label}", "Pick another class from the top.", null)
        else -> XpBody(vm)
    }
}

@Composable
private fun XpBody(vm: XpViewModel) {
    val r = vm.reason
    val sign = if (vm.takeBack) MINUS else "+"
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            ModeToggle(vm.takeBack, vm::chooseTakeBack)
            Text(
                if (vm.takeBack) "Tap a student to take back ${r.xp} XP for ${r.label}" else "Tap a student to give +${r.xp} XP for ${r.label}",
                color = Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp).testTag("xpHint"),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                XpReasons.ALL.forEach { x -> ReasonChip(x, x.key == r.key, sign) { vm.pickReason(x) } }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.students, key = { it.no }) { s ->
                    Tile(s, vm.takeBack, vm.pops[s.no]) { vm.tap(s.no) }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
            vm.toast?.let { Toast(it, onGone = { vm.clearToast(it) }) }
        }
    }
}

@Composable
private fun ModeToggle(takeBack: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).background(ModeTrack, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeButton("+ Give", on = !takeBack, onColor = Brand, fill = Color.White, tag = "mode-give", modifier = Modifier.weight(1f)) { onChange(false) }
        ModeButton("$MINUS Take back", on = takeBack, onColor = Color.White, fill = Bad, tag = "mode-minus", modifier = Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun ModeButton(label: String, on: Boolean, onColor: Color, fill: Color, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (on) fill else Color.Transparent, RoundedCornerShape(9.dp))
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) onColor else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
private fun ReasonChip(reason: Reason, on: Boolean, sign: String, onClick: () -> Unit) {
    Row(
        Modifier
            .background(if (on) Brand else Color.White, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.5.dp, if (on) Brand else Line), RoundedCornerShape(12.dp))
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("reason-${reason.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(reason.label, color = if (on) Color.White else Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        Text(
            "$sign${reason.xp}",
            color = if (on) Color(0xFFC7D2FE) else Brand,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun Tile(s: XpStudent, takeBack: Boolean, pop: XpPop?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (takeBack) TileTakeBackFill else Color.White, shape)
            .border(BorderStroke(1.dp, if (takeBack) TileTakeBackBorder else Line), shape)
            .clickable(onClick = onClick)
            .padding(top = 12.dp, bottom = 10.dp, start = 6.dp, end = 6.dp)
            .testTag("tile-${s.no}"),
    ) {
        Text(s.no, color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(42.dp).background(AvatarColors[(s.no.toIntOrNull() ?: 0) % AvatarColors.size], CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(Core.initials(s.name), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
            Text(
                Core.firstName(s.name),
                color = Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text("${s.total} XP", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.testTag("total-${s.no}"))
        }
        if (pop != null) {
            Text(
                pop.text,
                color = if (pop.negative) Bad else Ok,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.TopCenter).testTag("pop-${s.no}"),
            )
        }
    }
}

@Composable
private fun Toast(toast: XpToast, onGone: () -> Unit) {
    LaunchedEffect(toast) {
        delay(if (toast.undo != null) 5_000 else 3_000)
        onGone()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (toast.isError) Bad else Ink, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            toast.text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp).testTag("toast"),
        )
        toast.undo?.let { undo ->
            Text(
                "Undo",
                color = Color(0xFFC7D2FE),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(onClick = undo).padding(horizontal = 12.dp, vertical = 10.dp).testTag("toastUndo"),
            )
        }
    }
}
