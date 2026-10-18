package com.riffado.meetings.export

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders a long recording through the PDF layout code into bitmaps (Robolectric has no native
 * PdfDocument). Pages are saved to build/test-pdf/ so the Arabic/RTL layout can be checked by eye.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class PdfExporterTest {
    @Test fun rendersMultiplePages() {
        val long = Fixtures.recording.copy(
            transcript = Fixtures.recording.transcript + "\n" + (1..90).joinToString("\n") {
                if (it % 2 == 0) "Speaker 1: Line $it about the release plan and the budget for the Android app."
                else "Speaker 2: السطر رقم $it عن خطة الإصدار والميزانية بتاعة تطبيق Android."
            },
        )
        val dir = File("build/test-pdf").apply { mkdirs() }
        val sink = object : PdfExporter.PageSink {
            var bitmap: Bitmap? = null
            override fun begin(pageNo: Int, width: Int, height: Int): Canvas {
                bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
                return Canvas(bitmap!!).apply { scale(2f, 2f) }
            }
            override fun end(pageNo: Int) {
                File(dir, "page-$pageNo.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val pages = PdfExporter.render(ExportData(long, Fixtures.tasks), sink, ExportLabels(), Fixtures.locale, Fixtures.tz)
        assertTrue("pages=$pages", pages >= 3)
        assertTrue(File(dir, "page-1.png").length() > 10_000)
    }
}
