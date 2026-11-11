package com.engquest.app.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.engquest.app.ui.theme.LocalEq

private data class TabItem(val route: String, val label: String, val icon: DrawScope.(Color) -> Unit)

private val ITEMS = listOf(
    TabItem("home", "Home") { c -> homeIcon(c) },
    TabItem("scores", "Scores") { c -> scoresIcon(c) },
    TabItem("send", "Send") { c -> cameraIcon(c) },
    TabItem("learn", "Learn") { c -> bookIcon(c) },
    TabItem("more", "More") { c -> moreIcon(c) },
)

/**
 * Home · Scores · Send · Learn · More. Send is a small orange rounded square (44 dp, radius 14 dp)
 * lifted 12 dp above the bar: easy to find, but no bigger than the other tabs need.
 */
@Composable
fun BottomBar(current: String, onTab: (String) -> Unit, modifier: Modifier = Modifier) {
    val eq = LocalEq.current
    Column(modifier.fillMaxWidth().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(eq.line))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Bottom) {
            ITEMS.forEach { item ->
                val on = item.route == current
                val send = item.route == "send"
                val tint = when {
                    send -> eq.actText
                    on -> eq.accent
                    else -> eq.muted
                }
                Column(
                    Modifier.testTag("tab-${item.label}")
                        .semantics { role = Role.Tab; selected = on }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTab(item.route) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (send) {
                        // In line with the other tabs (owner found the lifted button odd): a small orange
                        // tile the same height as the icons, label on the same line as the rest.
                        Box(
                            Modifier.size(width = 40.dp, height = 24.dp).clip(RoundedCornerShape(8.dp)).background(eq.act),
                            contentAlignment = Alignment.Center,
                        ) { Canvas(Modifier.size(18.dp)) { item.icon(this, Color.White) } }
                        Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint)
                    } else {
                        Canvas(Modifier.size(24.dp)) { item.icon(this, tint) }
                        Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                }
            }
        }
    }
}

// Simple line icons drawn on a 24-unit grid (so no icon library is needed).
private fun DrawScope.u(x: Float, y: Float) = Offset(x * size.width / 24f, y * size.height / 24f)
private fun DrawScope.stroke() = Stroke(width = 2.2f * size.width / 24f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private fun DrawScope.poly(c: Color, vararg pts: Pair<Float, Float>, close: Boolean = false) {
    val path = Path().apply {
        pts.forEachIndexed { i, (x, y) -> val o = u(x, y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        if (close) close()
    }
    drawPath(path, c, style = stroke())
}

private fun DrawScope.homeIcon(c: Color) = poly(c, 3f to 10.5f, 12f to 3f, 21f to 10.5f, 21f to 21f, 15f to 21f, 15f to 15f, 9f to 15f, 9f to 21f, 3f to 21f, close = true)
private fun DrawScope.scoresIcon(c: Color) {
    poly(c, 4f to 20f, 4f to 11f); poly(c, 10f to 20f, 10f to 4f); poly(c, 16f to 20f, 16f to 13f); poly(c, 2f to 21f, 22f to 21f)
}
private fun DrawScope.cameraIcon(c: Color) {
    poly(c, 4f to 8f, 7f to 8f, 9f to 5f, 15f to 5f, 17f to 8f, 20f to 8f, 20f to 19f, 4f to 19f, close = true)
    drawCircle(c, radius = 3.5f * size.width / 24f, center = u(12f, 13f), style = stroke())
}
private fun DrawScope.bookIcon(c: Color) {
    poly(c, 12f to 6f, 12f to 20f); poly(c, 12f to 6f, 4f to 4f, 4f to 18f, 12f to 20f, 20f to 18f, 20f to 4f, 12f to 6f)
}
private fun DrawScope.moreIcon(c: Color) {
    for (x in listOf(5f, 12f, 19f)) drawCircle(c, radius = 2f * size.width / 24f, center = u(x, 12f))
}
