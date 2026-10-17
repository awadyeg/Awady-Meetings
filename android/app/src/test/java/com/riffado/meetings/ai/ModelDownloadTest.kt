package com.riffado.meetings.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ModelDownloadTest {
    @Test fun `server ignoring range restarts from zero`() {
        assertEquals(0L, modelDownloadOffset(200, 40, 100, null))
    }

    @Test fun `partial response appends at validated offset`() {
        assertEquals(40L, modelDownloadOffset(206, 40, 100, "bytes 40-99/100"))
    }

    @Test fun `invalid partial responses never append`() {
        for (range in listOf(null, "bytes 0-99/100", "bytes 40-99/200", "bytes 40-100/100", "bytes 40-39/100")) {
            assertThrows(IOException::class.java) { modelDownloadOffset(206, 40, 100, range) }
        }
    }

    @Test fun `range not satisfiable only accepts complete file`() {
        assertEquals(100L, modelDownloadOffset(416, 100, 100, null))
        assertThrows(IOException::class.java) { modelDownloadOffset(416, 40, 100, null) }
        assertThrows(IOException::class.java) { modelDownloadOffset(416, 101, 100, null) }
    }

    @Test fun `http errors fail without accepting model`() {
        for (code in listOf(404, 429, 500)) {
            assertThrows(IOException::class.java) { modelDownloadOffset(code, 40, 100, null) }
        }
    }
}
