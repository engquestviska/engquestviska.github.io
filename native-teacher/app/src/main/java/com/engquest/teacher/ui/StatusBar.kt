package com.engquest.teacher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.teacher.data.OutboxItem
import com.engquest.teacher.domain.Classes
import com.engquest.teacher.ui.theme.Brand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Under the top bar: how many changes are still on their way, and how many the server refused.
 * Shows nothing when everything is sent.
 */
@Composable
fun StatusBar(pending: Int, failed: Int, online: Boolean, onOpenFailed: () -> Unit) {
    if (pending == 0 && failed == 0) return
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pending > 0) {
            val changes = if (pending == 1) "1 change" else "$pending changes"
            StatusChip(
                text = if (online) "$changes waiting to send" else "You're offline — $changes waiting to send",
                icon = AppIcons.Cloud,
                color = if (online) Brand else Warn,
                background = if (online) BrandSoft else WarnSoft,
            )
        }
        if (failed > 0) {
            StatusChip("$failed not saved — tap to see", AppIcons.Warning, Bad, BadSoft, onOpenFailed)
        }
    }
}

@Composable
private fun StatusChip(text: String, icon: ImageVector, color: Color, background: Color, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(background, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Lists the changes the server refused, each with what it was, why, and Retry / Discard. */
@Composable
fun FailedSheet(items: List<OutboxItem>, onRetry: (OutboxItem) -> Unit, onDiscard: (OutboxItem) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Changes not saved", onDismiss) {
        Text(
            "The server didn't accept these. Retry sends one again; Discard drops it.",
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (items.isEmpty()) {
            Text("Nothing left here.", color = Muted, fontSize = 14.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.5.dp, Line), RoundedCornerShape(14.dp))
                        .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                ) {
                    Text(describeChange(item), color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(plainServerError(item.error), color = Bad, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onDiscard(item) }) { Text("Discard", color = Muted) }
                        OutlinedButton(onClick = { onRetry(item) }, modifier = Modifier.padding(start = 4.dp)) {
                            Text("Retry", color = Brand)
                        }
                    }
                }
            }
        }
    }
}

/** What a queued change was, in plain words, e.g. "Attendance for X E-2 (2026-10-27)". */
fun describeChange(item: OutboxItem): String {
    val p = runCatching { Json.parseToJsonElement(item.paramsJson) as JsonObject }.getOrNull()
        ?.mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() }
        .orEmpty()
    val cls = Classes.ALL.firstOrNull { it.id == item.className }?.label ?: item.className
    val student = p["studentNo"]?.takeIf { it.isNotBlank() }?.let { "student no. $it, " }.orEmpty()
    val date = p["date"]?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    return when (item.action) {
        "saveAttendance" -> "Attendance for $cls$date"
        "removeAttendanceMeeting" -> "Removing a meeting from $cls$date"
        "incrementActiveness" -> {
            val taking = p["delta"]?.startsWith("-") == true
            val reason = p["column"]?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            (if (taking) "Taking back XP$reason" else "XP$reason") + " for $student$cls"
        }
        "saveChapterScore", "saveFinalScore" -> "Score for $student$cls"
        "reviewTaskSubmission" -> "Reviewing a submission for $student$cls"
        "registerDevice" -> "Turning on notifications for this phone"
        else -> if (cls.isNotBlank()) "A change for $cls" else "A change"
    }
}

private val HTTP_CODE = Regex("""^Server error (\d{3})\.?$""")

/** The server's reason for refusing a change, in words a teacher can read. */
fun plainServerError(message: String?): String {
    val text = message?.trim().orEmpty()
    HTTP_CODE.matchEntire(text)?.let { return "The server couldn't save this (${it.groupValues[1]})." }
    return when {
        text.isEmpty() -> "The server couldn't save this."
        // Never show raw data or code to the teacher.
        text.startsWith("{") || text.startsWith("[") || text.contains("Exception") || text.length > 160 ->
            "The server couldn't save this."
        else -> text
    }
}
