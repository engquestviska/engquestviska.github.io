package com.engquest.app.ui.chronicle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.app.data.ChronicleStatus
import com.engquest.app.ui.theme.LocalEq

private val Paper = Color(0xFFF4F2ED)
private val Ink = Color(0xFF141414)

/** "Dec 17" from the server's ISO time, in Jakarta time; "soon" if it can't be read. */
fun openDay(iso: String): String = runCatching {
    val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
    val out = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
    out.timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
    out.format(inFmt.parse(iso)!!)
}.getOrDefault("soon")

/** Home's Chronicle card once the semester has ended: same words as the website's banner. Tap opens Chronicle. */
@Composable
fun ChronicleBanner(status: ChronicleStatus, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val eq = LocalEq.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().testTag("chronicle-banner")
            .semantics { role = Role.Button }
            .clip(shape).background(Paper).border(1.5.dp, Ink, shape)
            .clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text("ENGQUEST CHRONICLE · SEMESTER 1", color = Ink.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
        Text(
            if (status.open) "Your semester, wrapped." else "Your semester story is coming.",
            color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            if (status.open) "Your EngQuest Chronicle is ready. Tap to see it."
            else "Opens on ${openDay(status.openAt)}. Make your 6-number PIN now so it's ready.",
            color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            if (status.open) "Open my Chronicle →" else "Make my PIN →",
            color = Paper, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(99.dp)).background(eq.accent).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
