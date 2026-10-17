package com.riffado.meetings.ai

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

object AudioSamples {
    fun downmix(samples: FloatArray, channels: Int): FloatArray {
        require(channels > 0 && samples.size % channels == 0)
        return FloatArray(samples.size / channels) { frame ->
            var sum = 0.0
            repeat(channels) { sum += samples[frame * channels + it] }
            (sum / channels).toFloat().coerceIn(-1f, 1f)
        }
    }

    fun resample(samples: FloatArray, sourceRate: Int, targetRate: Int = 16000): FloatArray {
        require(sourceRate > 0 && targetRate > 0)
        if (samples.isEmpty() || sourceRate == targetRate) return samples.copyOf()
        val size = (samples.size.toLong() * targetRate / sourceRate).toInt()
        return FloatArray(size) { i ->
            val position = i.toDouble() * sourceRate / targetRate
            val left = position.toInt().coerceAtMost(samples.lastIndex)
            val right = (left + 1).coerceAtMost(samples.lastIndex)
            (samples[left] + (samples[right] - samples[left]) * (position - left)).toFloat().coerceIn(-1f, 1f)
        }
    }

    fun trimWindow(samples: FloatArray, sampleRate: Int, presentationTimeUs: Long, startUs: Long, endUs: Long): FloatArray {
        require(sampleRate > 0 && endUs >= startUs)
        val from = ceil((startUs - presentationTimeUs).toDouble() * sampleRate / 1_000_000).toLong().coerceIn(0, samples.size.toLong()).toInt()
        val to = ceil((endUs - presentationTimeUs).toDouble() * sampleRate / 1_000_000).toLong().coerceIn(from.toLong(), samples.size.toLong()).toInt()
        return samples.copyOfRange(from, to)
    }
}

class AudioDecoder {
    suspend fun decode(file: File, startMs: Long = 0, endMs: Long = Long.MAX_VALUE / 1000): FloatArray = withContext(Dispatchers.Default) {
        require(startMs >= 0 && endMs > startMs) { "Invalid audio window" }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No supported audio track in ${file.name}")
            extractor.selectTrack(track)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val inputFormat = extractor.getTrackFormat(track)
            if (inputFormat.getString(MediaFormat.KEY_MIME) == "audio/raw") {
                return@withContext decodeRaw(extractor, inputFormat, startMs * 1000, endMs * 1000)
            }
            val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            var outputFormat = inputFormat
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            val chunks = mutableListOf<FloatArray>()
            var total = 0
            var idleSince = System.nanoTime()
            while (!outputDone) {
                coroutineContext.ensureActive()
                check(System.nanoTime() - idleSince < 30_000_000_000L) { "Audio decoder stalled" }
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        val time = extractor.sampleTime
                        if (size < 0 || time >= endMs * 1000) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, time, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val index = decoder.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = decoder.outputFormat
                    else -> if (index >= 0) {
                        idleSince = System.nanoTime()
                        try {
                            if (info.size > 0) {
                                val buffer = decoder.getOutputBuffer(index)!!.duplicate().order(ByteOrder.nativeOrder())
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val samples = pcmSamples(buffer, outputFormat)
                                val rate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                val mono = AudioSamples.downmix(samples, outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                                val trimmed = AudioSamples.trimWindow(mono, rate, info.presentationTimeUs, startMs * 1000, endMs * 1000)
                                val converted = AudioSamples.resample(trimmed, rate)
                                chunks += converted
                                total += converted.size
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || info.presentationTimeUs >= endMs * 1000
                        } finally {
                            decoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
            FloatArray(total).also { result ->
                var offset = 0
                chunks.forEach { it.copyInto(result, offset); offset += it.size }
            }
        } finally {
            codec?.release()
            extractor.release()
        }
    }

    private fun pcmSamples(buffer: ByteBuffer, format: MediaFormat): FloatArray {
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
        require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) { "Unsupported PCM encoding: $encoding" }
        return if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            FloatArray(buffer.remaining() / 4) { buffer.float.let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f } }
        } else FloatArray(buffer.remaining() / 2) { buffer.short / 32768f }
    }

    private suspend fun decodeRaw(extractor: MediaExtractor, format: MediaFormat, startUs: Long, endUs: Long): FloatArray {
        val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
        val buffer = ByteBuffer.allocateDirect(maxOf(bufferSize, 1024 * 1024)).order(ByteOrder.nativeOrder())
        val chunks = mutableListOf<FloatArray>()
        var total = 0
        while (extractor.sampleTime >= 0 && extractor.sampleTime < endUs) {
            coroutineContext.ensureActive()
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            buffer.position(0)
            buffer.limit(size)
            val mono = AudioSamples.downmix(pcmSamples(buffer, format), channels)
            val trimmed = AudioSamples.trimWindow(mono, rate, extractor.sampleTime, startUs, endUs)
            chunks += trimmed
            total += trimmed.size
            extractor.advance()
        }
        val samples = FloatArray(total)
        var offset = 0
        chunks.forEach { it.copyInto(samples, offset); offset += it.size }
        return AudioSamples.resample(samples, rate)
    }

}
