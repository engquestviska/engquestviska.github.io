package com.engquest.teacher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val Ink = Color(0xFF0F172A)
internal val Muted = Color(0xFF64748B)
internal val Line = Color(0xFFE2E8F0)
internal val BrandSoft = Color(0xFFEEF2FF)
internal val Bad = Color(0xFFDC2626)
internal val Warn = Color(0xFFD97706)
internal val WarnSoft = Color(0xFFFEF3C7)
internal val BadSoft = Color(0xFFFEE2E2)

/**
 * The pilot's bottom sheet: dimmed backdrop (tap or Back closes it) and a white panel with
 * rounded top corners. Drawn inside the screen, so it needs no extra window.
 */
@Composable
fun BottomSheet(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x730F172A))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .background(Color.White, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                // Taps inside the panel must not reach the backdrop.
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .background(Color(0xFFCBD5E1), RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}
