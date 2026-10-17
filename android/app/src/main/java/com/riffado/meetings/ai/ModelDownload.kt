package com.riffado.meetings.ai

import java.io.IOException

internal fun modelDownloadOffset(code: Int, partialSize: Long, expectedSize: Long, contentRange: String?): Long =
    when (code) {
        200 -> 0L
        206 -> {
            val range = contentRange?.let { Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(it) }
                ?: throw IOException("Missing or invalid model Content-Range")
            val start = range.groupValues[1].toLongOrNull()
            val end = range.groupValues[2].toLongOrNull()
            val total = range.groupValues[3].toLongOrNull()
            if (start != partialSize || end == null || end < partialSize || end >= expectedSize || total != expectedSize) {
                throw IOException("Unexpected model Content-Range")
            }
            partialSize
        }
        416 -> {
            if (partialSize != expectedSize) throw IOException("Incomplete model download range")
            partialSize
        }
        else -> throw IOException("Model download failed: HTTP $code")
    }
