package com.engquest.teacher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.engquest.teacher.ui.Page
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.AppGraph
import com.engquest.teacher.domain.Core
import com.engquest.teacher.ui.Bad
import com.engquest.teacher.ui.Ink
import com.engquest.teacher.ui.Muted
import com.engquest.teacher.ui.Warn
import com.engquest.teacher.ui.WarnSoft
import com.engquest.teacher.ui.attendance.Message
import com.engquest.teacher.ui.submissions.PENDING
import com.engquest.teacher.ui.submissions.REJECTED
import com.engquest.teacher.ui.theme.Brand
import java.util.Calendar

private val Good = Color(0xFF059669)
private val GoodSoft = Color(0xFFD1FAE5)
private val BadSoft = Color(0xFFFEE2E2)
private val MedalColors = listOf(Color(0xFFF59E0B), Color(0xFF94A3B8), Color(0xFFB45309))
private val DAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/**
 * The first screen: a welcome, the top students of the semester and the latest submissions
 * from every class. Tapping a submission opens it ([onOpen] gets the class and student).
 */
@Composable
fun HomeScreen(
    graph: AppGraph,
    onOpen: (className: String, studentNo: String) -> Unit,
    onOpenPage: (Page) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val vm = remember { HomeViewModel(graph, scope) }
    LaunchedEffect(vm) { vm.start() }
    val name = graph.session.savedUsername().orEmpty()

    LazyColumn(Modifier.fillMaxSize().testTag("home"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Welcome(name, vm.waiting) }
        item { QuickAccess(onOpenPage) }
        if (vm.champions.isNotEmpty()) {
            item { Heading("🏆 Champions of the semester") }
            item { Champions(vm.champions) }
        }
        item { Heading("Recent activity") }
        when {
            vm.loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            vm.activity.isEmpty() && vm.error != null -> item {
                Message("Couldn't load recent activity", vm.error, "Try again" to { vm.start() })
            }
            vm.activity.isEmpty() -> item { Message("No submissions yet", "New student submissions will show up here.", null) }
            else -> items(vm.activity, key = { "${it.className}|${it.studentNo}|${it.task}|${it.day}" }) { a ->
                ActivityRow(a) { onOpen(a.className, a.studentNo) }
            }
        }
        item { Box(Modifier.padding(8.dp)) }
    }
}

@Composable
private fun Welcome(name: String, waiting: Int) {
    val now = Calendar.getInstance()
    val date = "${DAYS[now.get(Calendar.DAY_OF_WEEK) - 1]}, ${now.get(Calendar.DAY_OF_MONTH)} ${MONTHS[now.get(Calendar.MONTH)]}"
    Column(
        Modifier
            .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            .fillMaxWidth()
            .background(Brand, RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Text(date, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        Text(
            "Welcome back${if (name.isNotBlank()) ", $name" else ""} 👋",
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            when (waiting) {
                0 -> "No submissions waiting for you."
                1 -> "1 photo is waiting for you to check."
                else -> "$waiting photos are waiting for you to check."
            },
            color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp).testTag("homeWaiting"),
        )
    }
}

/** Shortcuts to the screens that don't fit in the bottom bar. */
@Composable
private fun QuickAccess(onOpenPage: (Page) -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Page.entries.forEach { p ->
            Row(
                Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .clickable { onOpenPage(p) }
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .testTag("quick-${p.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(p.icon, contentDescription = null, tint = Brand, modifier = Modifier.size(22.dp))
                Text(p.title, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(), color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp),
    )
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** The top three side by side: medal, first name, class and XP. */
@Composable
private fun Champions(list: List<Champion>) {
    Card {
        list.take(3).forEachIndexed { place, c ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(28.dp).background(MedalColors.getOrElse(place) { Muted }, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${place + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(
                    Core.firstName(c.name), color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(classLabel(c.className), color = Muted, fontSize = 12.sp)
                Text("${c.xp} XP", color = Brand, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ActivityRow(a: Activity, onClick: () -> Unit) {
    Card(Modifier.clickable(onClick = onClick).testTag("activity")) {
        Box(Modifier.size(38.dp).background(Brand.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Text(Core.initials(a.studentName), color = Brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(a.studentName, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val photos = if (a.photos == 1) "1 photo" else "${a.photos} photos"
            Text(
                "${classLabel(a.className)} · sent ${Core.taskLabel(a.task)} · $photos",
                color = Muted, fontSize = 13.sp, maxLines = 2,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(dayLabel(a.day), color = Muted, fontSize = 12.sp)
            val (label, fg, bg) = when (a.status) {
                PENDING -> Triple("Waiting", Warn, WarnSoft)
                REJECTED -> Triple("Sent back", Bad, BadSoft)
                else -> Triple("Approved", Good, GoodSoft)
            }
            Text(
                label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp).background(bg, RoundedCornerShape(99.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
