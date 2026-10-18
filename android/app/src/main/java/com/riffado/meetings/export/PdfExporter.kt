package com.riffado.meetings.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.riffado.meetings.data.Comments
import com.riffado.meetings.data.Speakers
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * Renders a recording to an A4 PDF with [PdfDocument] + [StaticLayout]. StaticLayout shapes Arabic
 * and resolves bidi per paragraph (first strong character), so Arabic paragraphs are right-aligned
 * and mixed Arabic/English lines read correctly.
 */
object PdfExporter {
    private const val PAGE_W = 595 // A4 at 72 dpi
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()
    private const val NOTE_BAR = 0xFFFF9500.toInt()

    /** Where pages are drawn: a [PdfDocument] on device, bitmaps in tests. */
    interface PageSink {
        fun begin(pageNo: Int, width: Int, height: Int): Canvas
        fun end(pageNo: Int)
    }

    private class PdfSink(private val doc: PdfDocument) : PageSink {
        private var page: PdfDocument.Page? = null
        override fun begin(pageNo: Int, width: Int, height: Int): Canvas =
            doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create()).also { page = it }.canvas
        override fun end(pageNo: Int) { page?.let(doc::finishPage); page = null }
    }

    private class Writer(private val sink: PageSink, private val footer: String) {
        private var pageNo = 0
        val pages: Int get() = pageNo
        private var open = false
        private lateinit var canvas: Canvas
        var y = 0f
        private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.GRAY }
        private val barPaint = Paint().apply { color = NOTE_BAR }

        fun newPage() {
            finishPage()
            pageNo++
            canvas = sink.begin(pageNo, PAGE_W, PAGE_H)
            canvas.drawColor(Color.WHITE)
            open = true
            y = MARGIN
        }

        fun finishPage() {
            if (!open) return
            canvas.drawText("$footer · $pageNo", MARGIN, PAGE_H - 24f, footerPaint)
            sink.end(pageNo)
            open = false
        }

        /** Draws [text] as a paragraph, splitting across pages line by line. */
        fun paragraph(text: CharSequence, paint: TextPaint, spacingAfter: Float = 6f, indent: Float = 0f, bar: Boolean = false) {
            if (text.isEmpty()) return
            val width = (CONTENT_W - indent).toInt()
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(2f, 1f)
                .setIncludePad(false)
                .build()
            for (i in 0 until layout.lineCount) {
                val top = layout.getLineTop(i).toFloat()
                val h = layout.getLineBottom(i) - top
                if (!open || y + h > PAGE_H - MARGIN) newPage()
                canvas.save()
                canvas.clipRect(MARGIN + indent - 6f, y, MARGIN + indent + width + 2f, y + h)
                canvas.translate(MARGIN + indent, y - top)
                layout.draw(canvas)
                canvas.restore()
                if (bar) canvas.drawRect(MARGIN + indent - 8f, y, MARGIN + indent - 6f, y + h, barPaint)
                y += h
            }
            y += spacingAfter
        }

        fun space(px: Float) {
            if (!open) newPage()
            y += px
            if (y > PAGE_H - MARGIN) newPage()
        }

        fun rule() {
            if (!open || y + 12 > PAGE_H - MARGIN) newPage()
            canvas.drawLine(MARGIN, y + 4, PAGE_W - MARGIN, y + 4, Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.7f })
            y += 12
        }

        /** Starts a new page unless [minFollow] px fit (keeps headings with their content). */
        fun ensure(minFollow: Float) {
            if (!open || y + minFollow > PAGE_H - MARGIN) newPage()
        }
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    /** Writes the PDF to [out] and returns it. */
    fun write(data: ExportData, out: File, labels: ExportLabels = ExportLabels(), locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): File {
        val doc = PdfDocument()
        try {
            render(data, PdfSink(doc), labels, locale, tz)
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return out
    }

    /** Lays out and draws all pages into [sink]; returns the page count. */
    fun render(data: ExportData, sink: PageSink, labels: ExportLabels = ExportLabels(), locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): Int {
        val r = data.recording
        val title = paint(22f, bold = true)
        val h2 = paint(14f, bold = true, color = 0xFF007AFF.toInt())
        val h3 = paint(11.5f, bold = true)
        val body = paint(10.5f)
        val meta = paint(9.5f, color = 0xFF6B6B70.toInt())
        val speaker = paint(10f, bold = true, color = 0xFF3A3A3C.toInt())
        val note = paint(10f, color = 0xFF8A5A00.toInt())

        val w = Writer(sink, labels.exportedBy)
        w.newPage()
        w.paragraph(r.title, title, 4f)
        val metaLine = buildList {
            add(RecordingExport.formatDate(r.createdAt, locale, tz))
            if (r.durationMs > 0) add(RecordingExport.formatDuration(r.durationMs))
            if (r.isCall && !r.callNumber.isNullOrBlank()) add("\u2066${r.callNumber}\u2069")
        }.joinToString("  ·  ")
        w.paragraph(metaLine, meta, 2f)
        if (data.people.isNotEmpty()) w.paragraph("${labels.people}: ${data.people.joinToString(", ")}", meta, 2f)
        w.rule()

        fun section(name: String) { w.ensure(60f); w.space(6f); w.paragraph(name, h2, 6f) }

        RecordingExport.summaryBody(r, labels.summary)?.let { md ->
            section(labels.summary)
            md.lines().forEach { raw ->
                val line = raw.trim().replace("**", "")
                when {
                    line.isEmpty() -> w.space(3f)
                    line.startsWith("#") -> { w.ensure(40f); w.paragraph(line.trimStart('#').trim(), h3, 3f) }
                    line.startsWith("- [ ]") || line.startsWith("* [ ]") -> w.paragraph("☐  " + line.drop(5).trim(), body, 3f, indent = 10f)
                    line.startsWith("- [x]", true) -> w.paragraph("☑  " + line.drop(5).trim(), body, 3f, indent = 10f)
                    line.startsWith("- ") || line.startsWith("* ") -> w.paragraph("•  " + line.drop(2), body, 3f, indent = 10f)
                    else -> w.paragraph(line, body, 4f)
                }
            }
        }
        if (data.tasks.isNotEmpty()) {
            section(labels.tasks)
            data.tasks.forEach { t -> w.paragraph((if (t.done) "☑  " else "☐  ") + RecordingExport.taskLine(t, labels, locale, tz), body, 4f, indent = 10f) }
        }
        if (r.bookmarks.isNotEmpty()) {
            section(labels.bookmarks)
            w.paragraph(r.bookmarks.joinToString("   ") { "🔖 " + RecordingExport.formatDuration(it) }, body, 4f)
        }
        if (r.comments.isNotEmpty()) {
            section(labels.notes)
            r.comments.forEach { c ->
                val who = c.speaker?.let { Speakers.label(it, r.speakerNames) + ": " }.orEmpty()
                val at = c.startMs?.let { "\u2066[${RecordingExport.formatDuration(it)}]\u2069 " }.orEmpty()
                w.paragraph("$at$who“${c.quote}”", meta, 2f, indent = 10f, bar = true)
                w.paragraph(c.text, note, 8f, indent = 10f, bar = true)
            }
        }
        val lines = data.lines
        if (lines.isNotEmpty()) {
            section(labels.transcript)
            val anchored = Comments.anchor(r.comments, lines.map { it.text })
            var previous: String? = null
            lines.forEachIndexed { i, t ->
                if (t.speaker != null && t.speaker != previous) {
                    w.ensure(30f)
                    w.paragraph(Speakers.label(t.speaker, r.speakerNames), speaker, 1f)
                }
                previous = t.speaker
                w.paragraph(t.text.trim(), body, 5f)
                anchored.byLine[i]?.let { w.paragraph("📝 " + it.text, note, 6f, indent = 10f, bar = true) }
            }
        }
        w.finishPage()
        return w.pages
    }
}
