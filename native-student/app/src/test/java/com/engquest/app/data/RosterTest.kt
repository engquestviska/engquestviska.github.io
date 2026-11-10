package com.engquest.app.data

import com.engquest.app.domain.Classmate
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class RosterTest {
    private val me = Profile(Grade.G10, "XE2", 14, "Dimas Pratama")
    private fun roster(vararg s: Pair<Int, String>) = s.map { Classmate(it.first, it.second, "") }

    @Test fun sameNumberAndNameIsSame() =
        assertEquals(RosterResult.Same, checkRoster(me, roster(13 to "Ani", 14 to "dimas pratama ")))

    @Test fun nameAtAnotherNumberMoves() =
        assertEquals(RosterResult.Moved(12, "Dimas Pratama"), checkRoster(me, roster(12 to "Dimas Pratama", 14 to "Budi")))

    @Test fun nameMissingIsGone() =
        assertEquals(RosterResult.Gone, checkRoster(me, roster(14 to "Budi", 15 to "Citra")))

    @Test fun emptyRosterChangesNothing() =
        assertEquals(RosterResult.Same, checkRoster(me, emptyList()))
}
