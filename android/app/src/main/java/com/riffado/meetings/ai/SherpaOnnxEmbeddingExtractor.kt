package com.riffado.meetings.ai

import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor as NativeSpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.riffado.meetings.data.Speakers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SherpaOnnxEmbeddingExtractor(private val modelFile: File) : SpeakerEmbeddingExtractor, AutoCloseable {
    private var native: NativeSpeakerEmbeddingExtractor? = null
    private val lock = Any()
    private val decoder = AudioDecoder()

    suspend fun embed(samples: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        require(samples.size >= 24000) { "At least 1.5 seconds of speech is required" }
        synchronized(lock) {
            val extractor = native ?: NativeSpeakerEmbeddingExtractor(
                assetManager = null,
                config = SpeakerEmbeddingExtractorConfig(modelFile.absolutePath, numThreads = 2, provider = "cpu"),
            ).also { native = it }
            val stream = extractor.createStream()
            try {
                stream.acceptWaveform(samples, 16000)
                stream.inputFinished()
                check(extractor.isReady(stream)) { "Not enough speech to identify this speaker" }
                extractor.compute(stream).also { embedding ->
                    check(embedding.size == extractor.dim() && embedding.all { it.isFinite() } && embedding.any { it != 0f }) { "Invalid speaker embedding" }
                }
            } finally {
                stream.release()
            }
        }
    }

    override suspend fun extractSpeakerEmbedding(audioFile: File, turns: List<Speakers.Turn>): FloatArray = withContext(Dispatchers.Default) {
        val timed = turns.filter { it.startMs != null && it.endMs != null && it.endMs > it.startMs }
            .sortedByDescending { it.endMs!! - it.startMs!! >= 500 }
        require(timed.isNotEmpty()) { "No speaker timestamps. Re-transcribe with Munsit or ElevenLabs first." }
        val chunks = mutableListOf<FloatArray>()
        var size = 0
        for (turn in timed) {
            if (size >= 480000) break
            val file = turn.audioSegment?.let { File(audioFile.parentFile, it) } ?: audioFile
            val end = minOf(turn.endMs!!, turn.startMs!! + (480000 - size) / 16)
            if (end <= turn.startMs) continue
            val decoded = decoder.decode(file, turn.startMs, end)
            val samples = if (decoded.size > 480000 - size) decoded.copyOf(480000 - size) else decoded
            chunks += samples
            size += samples.size
        }
        require(size >= 24000) { "At least 1.5 seconds of timed speech is required for this speaker" }
        val samples = FloatArray(size)
        var offset = 0
        chunks.forEach { it.copyInto(samples, offset); offset += it.size }
        embed(samples)
    }

    override suspend fun extractEmbedding(audioFile: File, startMs: Long, endMs: Long): FloatArray =
        embed(decoder.decode(audioFile, startMs, minOf(endMs, startMs + 30000)))

    override fun close() = synchronized(lock) {
        native?.release()
        native = null
    }
}
