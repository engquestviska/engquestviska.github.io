package com.engquest.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engquest.app.domain.Grade

/** The Quest look's colours for one grade. Header = gradient band; act = the orange "do it" colour. */
data class EqColors(
    val headerStart: Color,
    val headerMid: Color,
    val headerEnd: Color,
    val accent: Color,
    val accentSoft: Color,
    val act1: Color = Color(0xFFF59E0B),
    val act2: Color = Color(0xFFEA580C),
    /** Text on the orange act colour's pale tint, e.g. the "Send" tab label. */
    val actText: Color = Color(0xFFC2410C),
    val bg: Color = Color(0xFFEEF2FF),
    val card: Color = Color.White,
    val ink: Color = Color(0xFF0F172A),
    val muted: Color = Color(0xFF64748B),
    val line: Color = Color(0xFFE0E7FF),
) {
    val header: Brush get() = Brush.linearGradient(listOf(headerStart, headerMid, headerEnd))
    val act: Brush get() = Brush.linearGradient(listOf(act1, act2))
}

fun eqColors(grade: Grade): EqColors = when (grade) {
    Grade.G10 -> EqColors(
        headerStart = Color(0xFF1D4ED8), headerMid = Color(0xFF1E40AF), headerEnd = Color(0xFF3730A3),
        accent = Color(0xFF1D4ED8), accentSoft = Color(0xFFDBEAFE),
    )
    Grade.G11 -> EqColors(
        headerStart = Color(0xFF0F766E), headerMid = Color(0xFF115E59), headerEnd = Color(0xFF134E4A),
        accent = Color(0xFF0F766E), accentSoft = Color(0xFFCCFBF1),
    )
}

/** Soft fills for status chips and event icons (same in both grades). */
object Tint {
    val green = Color(0xFFDCFCE7); val greenInk = Color(0xFF15803D)
    val red = Color(0xFFFEE2E2); val redInk = Color(0xFFB91C1C)
    val amber = Color(0xFFFEF3C7); val amberInk = Color(0xFF92400E)
    val blue = Color(0xFFDBEAFE); val blueInk = Color(0xFF1D4ED8)
    val violet = Color(0xFFEDE9FE); val violetInk = Color(0xFF6D28D9)
    val slate = Color(0xFFF1F5F9); val slateInk = Color(0xFF334155)
}

val LocalEq = staticCompositionLocalOf { eqColors(Grade.G10) }

/**
 * The app font: Nunito (SIL Open Font License, see native-student/NUNITO-OFL.txt), one variable-weight
 * file. Android 8+ draws each weight; older phones show the regular weight.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun nunito(w: Int) = androidx.compose.ui.text.font.Font(
    com.engquest.app.R.font.nunito,
    weight = FontWeight(w),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)),
)
val AppFont: FontFamily = FontFamily(nunito(400), nunito(600), nunito(700), nunito(800), nunito(900))

private fun style(size: Int, weight: FontWeight, line: Int = size + 6) =
    TextStyle(fontFamily = AppFont, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)

private val AppType = Typography(
    headlineMedium = style(24, FontWeight.Black, 30),
    titleLarge = style(18, FontWeight.ExtraBold),
    titleMedium = style(15, FontWeight.ExtraBold),
    bodyLarge = style(15, FontWeight.SemiBold),
    bodyMedium = style(14, FontWeight.SemiBold),
    bodySmall = style(12, FontWeight.SemiBold),
    labelLarge = style(14, FontWeight.ExtraBold),
    labelMedium = style(12, FontWeight.Bold),
    labelSmall = style(11, FontWeight.ExtraBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun EqTheme(grade: Grade, content: @Composable () -> Unit) {
    val c = eqColors(grade)
    val scheme = lightColorScheme(
        primary = c.accent, onPrimary = Color.White,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.ink,
        secondary = c.act2, onSecondary = Color.White,
        background = c.bg, onBackground = c.ink,
        surface = c.card, onSurface = c.ink, onSurfaceVariant = c.muted,
        outline = c.line,
    )
    CompositionLocalProvider(LocalEq provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppType, shapes = AppShapes, content = content)
    }
}
