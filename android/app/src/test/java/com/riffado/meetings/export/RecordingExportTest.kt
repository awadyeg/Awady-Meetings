package com.riffado.meetings.export

import com.riffado.meetings.data.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingExportTest {
    private val md = RecordingExport.markdown(Fixtures.data, ExportLabels(), Fixtures.locale, Fixtures.tz)

    @Test fun markdownHasAllSectionsInOrder() {
        val order = listOf("# Weekly sync", "**Date:** Sun 18 Oct 2026, 14:30", "**Duration:** 25:20", "**People:** Mohamed, Sara",
            "## Summary", "### Action items", "## Tasks", "## Bookmarks", "## Notes", "## Transcript", "Exported from Awady meetings")
        var at = -1
        order.forEach { part ->
            val i = md.indexOf(part)
            assertTrue("missing or out of order: $part\n$md", i > at)
            at = i
        }
    }

    @Test fun markdownDetails() {
        assertTrue(md.contains("- [ ] Release notes — Sara · due Thu 22 Oct 2026"))
        assertTrue(md.contains("- [x] Book room"))
        assertTrue(md.contains("- 01:05"))                                                 // bookmark
        assertTrue(md.contains("- [00:09] > Mohamed: Ship it Thursday.\n  Confirm with QA")) // note with speaker + time
        assertTrue(md.contains("**Sara:** الشاشة الرئيسية خلصت."))                       // names + Arabic verbatim
        assertTrue(md.contains("**Mohamed:** Ship it Thursday.\n> 📝 Confirm with QA"))    // note inline under its line
        assertFalse(md.contains("### Summary"))                                           // no duplicated heading
        assertEquals(1, Regex("^# Weekly sync$", RegexOption.MULTILINE).findAll(md).count())
    }

    @Test fun localisedLabels() {
        val ar = RecordingExport.markdown(Fixtures.data, ExportLabels(summary = "الملخص", tasks = "المهام"), Fixtures.locale, Fixtures.tz)
        assertTrue(ar.contains("## الملخص") && ar.contains("## المهام"))
    }

    @Test fun shareTextIsCompactWithoutMarkdown() {
        val text = RecordingExport.shareText(Fixtures.data, ExportLabels(), Fixtures.locale, Fixtures.tz)
        assertTrue(text, text.startsWith("*Weekly sync*\nSun 18 Oct 2026, 14:30 · Mohamed, Sara\n\nWe agreed to *ship* on Thursday."))
        assertTrue(text.contains("*Action items*"))
        assertFalse(text.contains("Sara: release notes"))                                 // summary checklist replaced by tasks
        assertTrue(text.contains("*Tasks*\n☐ Release notes — Sara · due Thu 22 Oct 2026\n✅ Book room"))
        assertFalse(text.contains("Transcript") || text.contains("**") || text.contains("#"))
    }

    @Test fun tasksFallBackToMinutesWhenNeverExtracted() {
        val r = Fixtures.recording.copy(tasksExtractedAt = null)
        assertEquals(listOf("Sara: release notes"), RecordingExport.tasksFor(r, emptyList()).map { it.title })
        assertTrue(RecordingExport.tasksFor(Fixtures.recording, emptyList()).isEmpty())   // extracted, none found
        assertEquals(listOf("t1", "t2"), RecordingExport.tasksFor(Fixtures.recording, Fixtures.tasks.reversed()).map { it.id })
    }

    @Test fun peopleForCalls() {
        val call = Recording(id = "c", title = "+971", createdAt = 0, tags = setOf("call"), callNumber = "+971 50 1", speakerNames = mapOf("1" to "Speaker 1"))
        assertEquals(listOf("+971 50 1"), RecordingExport.people(call))
        assertEquals(listOf("Ahmed"), RecordingExport.people(call.copy(callContactName = "Ahmed")))
    }

    @Test fun safeFileName() {
        assertEquals("a b c.pdf", RecordingExport.fileName(Fixtures.recording.copy(title = "a/b:c"), "pdf"))
        assertEquals("recording.md", RecordingExport.fileName(Fixtures.recording.copy(title = "  "), "md"))
    }
}
