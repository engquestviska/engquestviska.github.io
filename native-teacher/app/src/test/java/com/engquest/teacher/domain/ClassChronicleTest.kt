package com.engquest.teacher.domain

import com.engquest.teacher.ui.home.parseClassChronicle
import com.engquest.teacher.ui.home.presentText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassChronicleTest {
    @Test fun parsesTheServerReply() {
        val j = Json.parseToJsonElement(
            """{"success":true,"meetings":34,"tasks":9,"students":[
               {"no":1,"name":"Adel","rank":1,"final":92,"present":32,"done":9,"words":48,"pin":true},
               {"no":5,"name":"Eko","rank":null,"final":"","present":0,"done":0,"words":0,"pin":false}]}""",
        ).jsonObject
        val c = parseClassChronicle(j)
        assertEquals(34, c.meetings)
        assertEquals(9, c.tasks)
        assertEquals("92", c.rows[0].final)
        assertEquals(true, c.rows[0].pin)
        assertEquals(null, c.rows[1].rank)
        assertEquals("", c.rows[1].final)
    }

    @Test fun presentIsAPercentOfMeetings() {
        assertEquals("94%", presentText(32, 34))
        assertEquals("–", presentText(0, 0))
    }
}
