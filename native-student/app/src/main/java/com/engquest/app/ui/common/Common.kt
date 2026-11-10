package com.engquest.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.engquest.app.ui.theme.LocalEq

/**
 * The coloured band at the top of every screen (grade gradient, rounded bottom corners).
 * [extraBottom] leaves room for a card that overlaps the band (the Home level card).
 */
@Composable
fun QuestHeader(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    extraBottom: Dp = 0.dp,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val eq = LocalEq.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(eq.header)
            .statusBarsPadding()
            .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 20.dp + extraBottom),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClickLabel = "Back") { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, style = MaterialTheme.typography.headlineMedium) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                if (eyebrow != null) Text(eyebrow, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
            }
            trailing()
        }
        below()
    }
}

/** A white rounded card. */
@Composable
fun EqCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val eq = LocalEq.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(eq.card)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}

/** A small rounded status label ("Approved · 90"). */
@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = fg, style = MaterialTheme.typography.labelSmall,
        modifier = modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** A rounded square with an emoji, used beside list rows. */
@Composable
fun IconDot(emoji: String, bg: Color, size: Dp = 34.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(emoji, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalEq.current.accent)
    }
}

/** A friendly message with an optional "Try again" button. */
@Composable
fun Message(text: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalEq.current.muted, textAlign = TextAlign.Center)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Try again", style = MaterialTheme.typography.labelLarge) }
    }
}
