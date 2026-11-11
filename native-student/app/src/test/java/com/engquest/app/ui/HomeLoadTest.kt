package com.engquest.app.ui

import com.engquest.app.FakeServer
import com.engquest.app.domain.Grade
import com.engquest.app.domain.Profile
import com.engquest.app.testGraph
import com.engquest.app.ui.home.loadHome
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HomeLoadTest {
    @Test fun loadsEveryPartFromTheFixtures() = runBlocking {
        val fake = FakeServer()
        val graph = testGraph(RuntimeEnvironment.getApplication(), fake.start())
        val d = withTimeout(10_000) { loadHome(graph, Profile(Grade.G10, "XE2", 1, "Student 1")) }
        fake.stop()
        assertEquals(119, d.totalXp)
        assertEquals("38", d.finalScore)
        assertEquals(6, d.tasksDone)
        assertEquals(9, d.tasksTotal)
        assertEquals(0, d.strikes)
        assertEquals(3, d.attendance.size)
    }
}
