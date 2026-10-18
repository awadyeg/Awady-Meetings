package com.riffado.meetings.export

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotionExportTest {
    private val id = "1a2b3c4d5e6f40718293a4b5c6d7e8f9"
    private val dashed = "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9"

    private fun JsonObject.type() = this["type"]!!.jsonPrimitive.content
    private fun JsonObject.text(): String =
        this[type()]!!.jsonObject["rich_text"]!!.jsonArray.joinToString("") { it.jsonObject["text"]!!.jsonObject["content"]!!.jsonPrimitive.content }

    @Test fun parsesParentPageIds() {
        assertEquals(dashed, NotionExport.parentPageId(id))
        assertEquals(dashed, NotionExport.parentPageId(dashed))
        assertEquals(dashed, NotionExport.parentPageId("https://www.notion.so/acme/Meeting-Notes-$id?v=123"))
        assertEquals(dashed, NotionExport.parentPageId("https://www.notion.so/Cafe-Deadbeef-$id"))  // hex-looking slug
        assertNull(NotionExport.parentPageId("https://www.notion.so/acme/Meeting-Notes"))
        assertNull(NotionExport.parentPageId(""))
    }

    @Test fun richTextChunksAt2000() {
        val rt = NotionExport.richText("x".repeat(4500))
        assertEquals(listOf(2000, 2000, 500), rt.map { it.jsonObject["text"]!!.jsonObject["content"]!!.jsonPrimitive.content.length })
    }

    @Test fun blocksCoverSummaryTasksNotesTranscript() {
        val blocks = NotionExport.blocks(Fixtures.data, ExportLabels(), Fixtures.locale, Fixtures.tz)
        assertEquals("paragraph", blocks[0].type())
        assertEquals("Date: Sun 18 Oct 2026, 14:30 · Duration: 25:20 · People: Mohamed, Sara", blocks[0].text())
        val headings = blocks.filter { it.type().startsWith("heading") }.map { it.text() }
        assertEquals(listOf("Summary", "Action items", "Tasks", "Bookmarks", "Notes", "Transcript"), headings)
        val todos = blocks.filter { it.type() == "to_do" }
        assertEquals(listOf("Release notes — Sara · due Thu 22 Oct 2026", "Book room"), todos.map { it.text() })
        assertEquals(listOf(false, true), todos.map { it["to_do"]!!.jsonObject["checked"]!!.jsonPrimitive.boolean })
        assertTrue(blocks.none { it.text() == "Sara: release notes" })        // summary checklist not duplicated
        assertTrue(blocks.any { it.type() == "paragraph" && it.text() == "We agreed to ship on Thursday." })
        assertTrue(blocks.any { it.type() == "quote" && it.text() == "Mohamed: Ship it Thursday." })
        val sara = blocks.first { it.text() == "Sara: الشاشة الرئيسية خلصت." }
        val parts = sara["paragraph"]!!.jsonObject["rich_text"]!!.jsonArray
        assertEquals(true, parts[0].jsonObject["annotations"]!!.jsonObject["bold"]!!.jsonPrimitive.boolean)  // bold speaker
    }

    @Test fun createAndAppendRespect100BlockLimit() {
        val many = List(250) { NotionExport.paragraph("line $it") }
        val body = NotionExport.createPageBody(dashed, "Weekly sync", many)
        assertEquals(dashed, body["parent"]!!.jsonObject["page_id"]!!.jsonPrimitive.content)
        assertEquals("Weekly sync", body["properties"]!!.jsonObject["title"]!!.jsonObject["title"]!!.jsonArray[0].jsonObject["text"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals(100, (body["children"] as JsonArray).size)
        val batches = NotionExport.appendBatches(many)
        assertEquals(listOf(100, 50), batches.map { (it["children"] as JsonArray).size })
        assertEquals("line 100", (batches[0]["children"] as JsonArray)[0].jsonObject.text())
        assertTrue(NotionExport.appendBatches(many.take(80)).isEmpty())
    }

    @Test fun markdownBlockTypes() {
        val b = NotionExport.markdownBlocks("# H1\n## H2\n- item\n1. first\n- [ ] todo\n- [x] done\nplain **bold**")
        assertEquals(listOf("heading_2", "heading_3", "bulleted_list_item", "numbered_list_item", "to_do", "to_do", "paragraph"), b.map { it.type() })
        assertEquals("plain bold", b.last().text())
    }

    @Test fun errorReasons() {
        assertEquals("UNAUTHORIZED", NotionExport.errorMessage(401, """{"object":"error","code":"unauthorized","message":"API token is invalid."}"""))
        assertEquals("NOT_FOUND", NotionExport.errorMessage(404, """{"code":"object_not_found","message":"Could not find page"}"""))
        assertEquals("RATE_LIMITED", NotionExport.errorMessage(429, ""))
        assertEquals("body.children[3] should be defined", NotionExport.errorMessage(400, """{"code":"validation_error","message":"body.children[3] should be defined"}"""))
        assertEquals("HTTP 502", NotionExport.errorMessage(502, "<html>"))
    }
}
