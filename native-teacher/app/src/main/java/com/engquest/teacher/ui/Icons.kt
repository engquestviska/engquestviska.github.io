package com.engquest.teacher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The pilot's line icons (24×24 SVG paths), drawn in the current content colour. */
object AppIcons {
    val Back = stroke("Back", "M19 12H5", "m12 19-7-7 7-7")
    val Strikes = stroke("Strikes", "M12 3 2 20h20L12 3z", "M12 10v4M12 17v.5")
    val Tasks = stroke("Tasks", "M9 11l3 3 8-8", "M20 12v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9")
    val Home = stroke("Home", "M3 11 12 3l9 8", "M5 10v10h5v-6h4v6h5V10")
    val Attendance = stroke(
        "Attendance",
        "M6 4h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z",
        "M16 2v4M8 2v4M3 10h18M9 16l2 2 4-4",
    )
    val Submissions = stroke(
        "Submissions",
        "M22 12h-6l-2 3h-4l-2-3H2",
        "M5.5 5h13L22 12v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6z",
    )
    val Xp = stroke("XP", "M13 2 3 14h9l-1 8 10-12h-9z")
    val Scores = stroke("Scores", "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val Logo = stroke("Logo", "M22 10 12 5 2 10l10 5 10-5z", "M6 12v5c3 2 9 2 12 0v-5", width = 2f)
    val ChevronDown = stroke("ChevronDown", "m6 9 6 6 6-6", width = 3f)
    val Warning = stroke("Warning", "M12 3 2 20h20L12 3z", "M12 10v4M12 17v.5")
    val Cloud = stroke("Cloud", "M7 18a5 5 0 1 1 1-9.9A6 6 0 0 1 19 10a4 4 0 0 1 0 8z")
    val MoreVertical = ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f).apply {
        for (y in listOf(5, 12, 19)) {
            addPath(addPathNodes("M12 ${y - 2}a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"), fill = SolidColor(Color.Black))
        }
    }.build()

    private fun stroke(name: String, vararg paths: String, width: Float = 2.2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
