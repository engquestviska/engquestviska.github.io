package com.engquest.teacher.ui.submissions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
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
import com.engquest.teacher.data.ApiError
import com.engquest.teacher.data.Photos
import com.engquest.teacher.domain.ClassInfo
import com.engquest.teacher.domain.Core
import com.engquest.teacher.domain.SubmissionGroup
import com.engquest.teacher.domain.SubmissionRow
import com.engquest.teacher.ui.Bad
import com.engquest.teacher.ui.BadSoft
import com.engquest.teacher.ui.BrandSoft
import com.engquest.teacher.ui.ConfirmDialog
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.LeaveGuard
import com.engquest.teacher.ui.Line
import com.engquest.teacher.ui.Muted
import com.engquest.teacher.ui.Warn
import com.engquest.teacher.ui.WarnSoft
import com.engquest.teacher.ui.attendance.Message
import com.engquest.teacher.ui.theme.Brand

private val Ok = Color(0xFF16A34A)
private val OkSoft = Color(0xFFDCFCE7)
private val OkInk = Color(0xFF166534)
private val BadInk = Color(0xFF991B1B)
private val WarnInk = Color(0xFF92400E)
private val TrackColor = Color(0xFFE2E8F0)
private val ViewerBack = Color(0xFF0B1020)
private val PhotoNote = Color(0xFF94A3B8)

/**
 * The Submissions tab: Waiting / All, one student at a time with their photos (swipe between
 * them, tap one to see it full screen), a score box when the task has a score column, then
 * Approve or Send back, and a strip of the other students below.
 */
@Composable
fun SubmissionsScreen(vm: SubmissionsViewModel, photos: Photos, classInfo: ClassInfo, guard: LeaveGuard) {
    // Typed scores are only saved by Approve: leaving would lose them, so the shell asks first.
    val typed = vm.hasTypedScores
    LaunchedEffect(typed, classInfo.id) {
        guard.unsaved = if (typed) "Scores you typed here are only saved when you tap Approve." else null
    }
    DisposableEffect(vm) {
        onDispose {
            guard.unsaved = null
            vm.forgetTyped()
        }
    }
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        FilterToggle(vm)
        val current = vm.current
        when {
            !vm.loaded && vm.loading -> Message("Loading submissions…", null, null)
            !vm.loaded -> Message(
                "Could not load submissions",
                vm.error ?: "Please check the internet and try again.",
                "Try again" to vm::start,
            )
            current == null && vm.showAll -> Message("No submissions in ${classInfo.label} yet", null, null)
            current == null -> Message("All caught up in ${classInfo.label}", "Nothing waiting to check.", null)
            else -> SubmissionsBody(vm, current, photos)
        }
    }
}

@Composable
private fun FilterToggle(vm: SubmissionsViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).background(TrackColor, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(false to "Waiting", true to "All").forEach { (all, label) ->
            val on = vm.showAll == all
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) Color.White else Color.Transparent, RoundedCornerShape(9.dp))
                    .selectable(selected = on, role = Role.Tab, onClick = { vm.chooseFilter(all) })
                    .padding(vertical = 8.dp)
                    .testTag(if (all) "filter-all" else "filter-waiting"),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Ink else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
    }
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

@Composable
private fun ColumnScope.SubmissionsBody(vm: SubmissionsViewModel, g: SubmissionGroup, photos: Photos) {
    var viewing by remember { mutableStateOf<ImageBitmap?>(null) }
    var askSendBack by remember { mutableStateOf<SubmissionRow?>(null) }
    val item = vm.currentItem ?: return

    // Get the next student's first photo ready while this one is being checked.
    val nextPhoto = vm.groups.getOrNull(vm.position + 1)?.items?.firstOrNull()?.subId
    LaunchedEffect(nextPhoto) {
        if (nextPhoto != null) {
            try {
                photos.load(nextPhoto)
            } catch (e: ApiError) {
                // Shown (with Retry) when the teacher gets there.
            }
        }
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("subScroll")) {
        Text(
            "${plural(vm.waiting, "photo")} waiting · ${plural(vm.waitingStudents, "student")}",
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        StudentHeader(vm, g)
        key(g.studentNo, vm.showAll) {
            PhotoPager(vm, g, photos, onOpen = { viewing = it })
        }
        SlideInfo(vm, g, item)
        if (vm.groups.size > 1) Queue(vm)
        Box(Modifier.height(12.dp))
    }
    vm.note?.let { n ->
        Text(
            n.text,
            color = if (n.isError) Bad else Ok,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("subNote"),
        )
    }
    Actions(vm, g, item, onSendBack = { askSendBack = it })
    Box(Modifier.height(10.dp))

    viewing?.let { PhotoViewer(it, onClose = { viewing = null }) }
    askSendBack?.let { row ->
        ConfirmDialog(
            title = "Send this photo back?",
            text = "The student will see ${Core.taskLabel(row.task).ifBlank { "this task" }} as sent back and can upload it again.",
            confirmLabel = "Send back",
            onConfirm = {
                askSendBack = null
                vm.sendBack(row)
            },
            onDismiss = { askSendBack = null },
        )
    }
}

@Composable
private fun StudentHeader(vm: SubmissionsViewModel, g: SubmissionGroup) {
    val toCheck = g.items.count { it.status == PENDING }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag("subWho")) {
            Text(g.studentName, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "No. ${g.studentNo} · ${plural(g.items.size, "photo")}" + if (toCheck > 0) " · $toCheck to check" else "",
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "${vm.position + 1} / ${vm.groups.size}",
            color = Brand,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            modifier = Modifier.background(BrandSoft, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PhotoPager(vm: SubmissionsViewModel, g: SubmissionGroup, photos: Photos, onOpen: (ImageBitmap) -> Unit) {
    val count = g.items.size
    val pager = rememberPagerState(initialPage = vm.slide.coerceIn(0, count - 1)) { count }
    LaunchedEffect(pager.currentPage) { vm.showSlide(pager.currentPage) }
    // The screen can ask for a photo too (e.g. the one whose score needs fixing).
    LaunchedEffect(vm.slide) { if (vm.slide in 0 until count && vm.slide != pager.currentPage) pager.animateScrollToPage(vm.slide) }
    Column(Modifier.fillMaxWidth().background(ViewerBack, RoundedCornerShape(18.dp))) {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(340.dp), key = { g.items[it].subId }) { page ->
            PhotoPage(g.items[page].subId, photos, onOpen)
        }
        if (count > 1) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            ) {
                repeat(count) { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier
                            .size(width = if (on) 16.dp else 6.dp, height = 6.dp)
                            .background(if (on) Color.White else Color(0xFF475569), RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

private sealed interface PhotoState {
    data object Loading : PhotoState
    data class Shown(val image: ImageBitmap) : PhotoState
    data object CannotShow : PhotoState
    data class Failed(val message: String) : PhotoState
}

@Composable
private fun PhotoPage(fileId: String, photos: Photos, onOpen: (ImageBitmap) -> Unit) {
    var attempt by remember(fileId) { mutableIntStateOf(0) }
    var state by remember(fileId) { mutableStateOf<PhotoState>(PhotoState.Loading) }
    LaunchedEffect(fileId, attempt) {
        state = PhotoState.Loading
        state = try {
            photos.load(fileId)?.let { PhotoState.Shown(it) } ?: PhotoState.CannotShow
        } catch (e: ApiError) {
            PhotoState.Failed(e.userMessage)
        }
    }
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        when (val s = state) {
            PhotoState.Loading -> CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            is PhotoState.Shown -> Image(
                bitmap = s.image,
                contentDescription = "Submitted work. Tap to see it full screen.",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clickable { onOpen(s.image) }.testTag("photo-$fileId"),
            )
            PhotoState.CannotShow -> Text(
                "Photo can't be shown",
                color = PhotoNote,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("photoBad-$fileId"),
            )
            is PhotoState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Could not load photo", color = PhotoNote, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(s.message, color = PhotoNote, fontSize = 12.sp, textAlign = TextAlign.Center)
                TextButton(onClick = { attempt++ }) { Text("Try again", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SlideInfo(vm: SubmissionsViewModel, g: SubmissionGroup, item: SubmissionRow) {
    val label = Core.taskLabel(item.task).ifBlank { "—" }
    val (pillText, pillBack, pillInk) = when (item.status) {
        REVIEWED -> Triple("$label · Approved", OkSoft, OkInk)
        REJECTED -> Triple("$label · Sent back", BadSoft, BadInk)
        else -> Triple(label, WarnSoft, WarnInk)
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Photo ${g.items.indexOf(item) + 1} of ${g.items.size}" + if (item.timestamp.isNotBlank()) " · ${item.timestamp}" else "",
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            pillText,
            color = pillInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.background(pillBack, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
    if (item.status != PENDING) return
    val map = Core.taskToChapterCol(item.task)
    if (map == null) {
        Text(
            "This task code has no score column, so approving only ticks it.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp),
        )
    } else {
        ScoreBox(vm, item, "Chapter ${(map.chapter.toIntOrNull() ?: 4) - 3} ${map.column}")
    }
}

@Composable
private fun ScoreBox(vm: SubmissionsViewModel, item: SubmissionRow, place: String) {
    val focus = LocalFocusManager.current
    val ok = vm.scoreOk(item.subId)
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Score · $place", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Leave empty to approve without a score", color = Muted, fontSize = 12.sp)
        }
        BasicTextField(
            value = vm.typed(item.subId),
            onValueChange = { vm.type(item.subId, it) },
            singleLine = true,
            textStyle = TextStyle(
                color = if (ok) Brand else Bad,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(Brand),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .width(80.dp)
                .height(46.dp)
                .semantics { contentDescription = "Score for ${Core.taskLabel(item.task)}" }
                .testTag("scoreBox"),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (ok) Color.White else BadSoft, shape)
                        .border(BorderStroke(1.5.dp, if (ok) Color(0xFF6366F1) else Bad), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.typed(item.subId).isEmpty()) Text("–", color = PhotoNote, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun Queue(vm: SubmissionsViewModel) {
    val current = vm.current?.studentNo
    LazyRow(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(vm.groups, key = { it.studentNo }) { q ->
            val on = q.studentNo == current
            val n = q.items.count { it.status == PENDING }
            Row(
                Modifier
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.5.dp, if (on) Color(0xFF6366F1) else Line), RoundedCornerShape(12.dp))
                    .selectable(selected = on, role = Role.Tab, onClick = { vm.choose(q.studentNo) })
                    .padding(horizontal = 11.dp, vertical = 7.dp)
                    .testTag("queue-${q.studentNo}"),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(Core.firstName(q.studentName).ifBlank { "No. ${q.studentNo}" }, color = if (on) Brand else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (n > 0) Text("$n", color = Warn, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun Actions(vm: SubmissionsViewModel, g: SubmissionGroup, item: SubmissionRow, onSendBack: (SubmissionRow) -> Unit) {
    val focus = LocalFocusManager.current
    val pend = g.items.count { it.status == PENDING }
    val hasNext = vm.position < vm.groups.size - 1
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pend > 0) {
            if (item.status == PENDING) {
                ActionButton(
                    "Send back",
                    Modifier.weight(1f).testTag("sendBack"),
                    background = Color.White,
                    ink = Bad,
                    border = Color(0xFFFECACA),
                ) { onSendBack(item) }
            } else {
                Box(Modifier.weight(1f))
            }
            val label = "✓ Approve" + (if (pend > 1) " all $pend" else "") + (if (hasNext) " & next" else "")
            ActionButton(label, Modifier.weight(2f).testTag("approve"), background = Ok, ink = Color.White) {
                focus.clearFocus()
                vm.approve()
            }
        } else {
            Box(Modifier.weight(1f))
            ActionButton(
                "Next student →",
                Modifier.weight(2f).testTag("nextStudent"),
                background = Color.White,
                ink = if (hasNext) Ink else PhotoNote,
                border = Line,
                enabled = hasNext,
            ) { vm.next() }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier,
    background: Color,
    ink: Color,
    border: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .background(background, shape)
            .then(if (border != null) Modifier.border(BorderStroke(1.5.dp, border), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
