package com.riffado.meetings.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.riffado.meetings.R
import java.io.File

enum class ExportKind { PDF, MARKDOWN, WHATSAPP, NOTION }

/** Android side of exports: localised labels, files in cache/exports, share intents. */
object ExportActions {
    fun labels(context: Context) = ExportLabels(
        date = context.getString(R.string.export_date),
        duration = context.getString(R.string.export_duration),
        people = context.getString(R.string.export_people),
        summary = context.getString(R.string.tab_summary),
        tasks = context.getString(R.string.tab_tasks),
        bookmarks = context.getString(R.string.detail_bookmarks),
        notes = context.getString(R.string.export_notes),
        transcript = context.getString(R.string.tab_transcript),
        due = context.getString(R.string.export_due),
        exportedBy = context.getString(R.string.export_footer),
    )

    private fun exportDir(context: Context) = File(context.cacheDir, "exports").apply {
        mkdirs()
        // Keep the folder small: drop exports older than a day.
        listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3_600_000L }?.forEach { it.delete() }
    }

    fun pdfFile(context: Context, data: ExportData): File =
        PdfExporter.write(data, File(exportDir(context), RecordingExport.fileName(data.recording, "pdf")), labels(context))

    fun markdownFile(context: Context, data: ExportData): File =
        File(exportDir(context), RecordingExport.fileName(data.recording, "md")).apply {
            writeText(RecordingExport.markdown(data, labels(context)))
        }

    fun shareFileIntent(context: Context, file: File, mime: String, title: String, text: String? = null): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".exports", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Sends [text] to WhatsApp (or WhatsApp Business) if installed, else opens the share sheet. */
    fun shareToWhatsApp(context: Context, text: String, title: String) {
        val base = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                context.startActivity(Intent(base).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // not installed; try the next one
            }
        }
        context.startActivity(Intent.createChooser(base.putExtra(Intent.EXTRA_SUBJECT, title), title))
    }

    /** User-facing message for a Notion failure. */
    fun notionError(context: Context, e: Throwable): String = when ((e as? NotionException)?.reason) {
        "UNAUTHORIZED" -> context.getString(R.string.notion_err_token)
        "NOT_FOUND", "FORBIDDEN" -> context.getString(R.string.notion_err_page)
        "RATE_LIMITED" -> context.getString(R.string.notion_err_rate)
        null -> context.getString(R.string.notion_err_network, e.message ?: e.javaClass.simpleName)
        else -> context.getString(R.string.notion_err_other, (e as NotionException).reason)
    }
}
