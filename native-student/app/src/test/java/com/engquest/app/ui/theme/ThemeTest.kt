package com.engquest.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.engquest.app.domain.Grade
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test fun grade10IsBlue() {
        val c = eqColors(Grade.G10)
        assertEquals(Color(0xFF1D4ED8), c.headerStart)
        assertEquals(Color(0xFF1E40AF), c.headerMid)
        assertEquals(Color(0xFF3730A3), c.headerEnd)
        assertEquals(Color(0xFF1D4ED8), c.accent)
    }

    @Test fun grade11IsTeal() {
        val c = eqColors(Grade.G11)
        assertEquals(Color(0xFF0F766E), c.headerStart)
        assertEquals(Color(0xFF115E59), c.headerMid)
        assertEquals(Color(0xFF134E4A), c.headerEnd)
        assertEquals(Color(0xFF0F766E), c.accent)
    }

    @Test fun actColourIsOrangeForBothGrades() {
        for (g in Grade.entries) {
            assertEquals(Color(0xFFF59E0B), eqColors(g).act1)
            assertEquals(Color(0xFFEA580C), eqColors(g).act2)
            assertEquals(Color(0xFFEEF2FF), eqColors(g).bg)
        }
    }
}
