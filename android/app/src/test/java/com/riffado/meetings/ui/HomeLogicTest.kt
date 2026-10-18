package com.riffado.meetings.ui

import com.riffado.meetings.data.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HomeLogicTest {
    private val zone = TimeZone.getTimeZone("Asia/Dubai")
    private val now = Calendar.getInstance(zone).apply { set(2026, Calendar.OCTOBER, 18, 18, 0, 0) }.timeInMillis
    private fun at(day: Int, hour: Int) = Calendar.getInstance(zone).apply { set(2026, Calendar.OCTOBER, day, hour, 0, 0) }.timeInMillis
    private fun rec(id: String, t: Long) = Recording(id = id, title = id, createdAt = t)

    @Test fun sections() {
        assertEquals(HomeLogic.Section.TODAY, HomeLogic.sectionOf(at(18, 0) + 1000, now, zone))
        assertEquals(HomeLogic.Section.YESTERDAY, HomeLogic.sectionOf(at(17, 23), now, zone))
        assertEquals(HomeLogic.Section.YESTERDAY, HomeLogic.sectionOf(at(17, 0) + 1000, now, zone))
        assertEquals(HomeLogic.Section.THIS_WEEK, HomeLogic.sectionOf(at(12, 9), now, zone))
        assertEquals(HomeLogic.Section.OLDER, HomeLogic.sectionOf(at(11, 9), now, zone))
    }

    @Test fun groupsOrderedAndNewestFirst() {
        val groups = HomeLogic.group(
            listOf(rec("old", at(1, 9)), rec("t1", at(18, 9)), rec("t2", at(18, 12)), rec("y", at(17, 9))), now, zone,
        )
        assertEquals(listOf(HomeLogic.Section.TODAY, HomeLogic.Section.YESTERDAY, HomeLogic.Section.OLDER), groups.map { it.section })
        assertEquals(listOf("t2", "t1"), groups[0].items.map { it.id })
    }

    @Test fun snippetSkipsHeadingsAndMarkers() {
        val md = "# Weekly sync\n\n## Summary\nWe agreed on **the launch** date.\n\n## Action items\n- [ ] Ahmed sends deck"
        assertEquals("We agreed on the launch date. Ahmed sends deck", HomeLogic.summarySnippet(md))
        assertNull(HomeLogic.summarySnippet("# Title only"))
        assertEquals("ملخص المكالمة مع أحمد", HomeLogic.summarySnippet("## الملخص\nملخص المكالمة مع أحمد"))
    }

    @Test fun snippetTruncates() {
        val s = HomeLogic.summarySnippet("a".repeat(300), maxChars = 10)!!
        assertEquals(11, s.length)
    }

    @Test fun initials() {
        assertEquals("AH", HomeLogic.initials("Ahmed Hassan"))
        assertEquals("M", HomeLogic.initials("mohamed"))
        assertEquals("مع", HomeLogic.initials("محمد علي"))
        assertNull(HomeLogic.initials("+20 100 123 4567"))
        assertNull(HomeLogic.initials(""))
    }

    @Test fun actionItems() {
        val md = "## Action items\n- [ ] Send invoice (Sara)\n- [x] Done thing\n- [ ] -\n- plain"
        assertEquals(listOf("Send invoice (Sara)"), HomeLogic.actionItems(md))
    }
}
