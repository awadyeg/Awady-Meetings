package com.riffado.meetings.ai

import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.Speakers
import com.riffado.meetings.data.VoiceProfile
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.math.sqrt

data class SpeakerIdentificationResult(
    val names: Map<String, String>,
    val scores: Map<String, Float>,
    val autoNamedLabels: Set<String>,
    val matchedUser: String? = null,
    val failures: Map<String, String> = emptyMap(),
)

class SpeakerIdentifier(
    private val embeddingExtractor: SpeakerEmbeddingExtractor?,
    private val logger: (String) -> Unit = {},
) {
    suspend fun identifySpeakers(
        recording: Recording,
        audioFile: File,
        userProfile: VoiceProfile?,
        callContactName: String? = recording.callContactName,
        threshold: Float,
        existingNames: Map<String, String> = recording.speakerNames,
        callNumber: String? = recording.callNumber,
    ): SpeakerIdentificationResult {
        val turns = recording.speakerTurns.ifEmpty { Speakers.parse(recording.transcript.orEmpty()) }
        val labels = turns.mapNotNull { it.speaker }.distinct()
        val names = existingNames.filterKeys { it !in recording.autoNamedSpeakers }.toMutableMap()
        val auto = mutableSetOf<String>()
        val scores = mutableMapOf<String, Float>()
        val failures = mutableMapOf<String, String>()
        logger("Identifying ${labels.size} speakers; threshold=$threshold")
        labels.filter { it in names }.forEach { logger("Preserving manual name for Speaker $it") }
        if (userProfile != null && embeddingExtractor != null) {
            for (label in labels) {
                try {
                    val embedding = embeddingExtractor.extractSpeakerEmbedding(audioFile, turns.filter { it.speaker == label })
                    scores[label] = cosineSimilarity(userProfile.embedding, embedding)
                    logger("Speaker $label score=${scores[label]} threshold=$threshold")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures[label] = e.message ?: "Embedding failed"
                    logger("Speaker $label identification failed: ${failures[label]}")
                } catch (e: LinkageError) {
                    failures[label] = "Native voice matching unavailable on this device"
                    logger("Speaker $label native identification failed: ${e.message}")
                }
            }
        } else logger("Voice matching skipped: profile or model unavailable")
        val matched = scores.maxByOrNull { it.value }?.takeIf { it.value >= threshold }?.key
        if (matched != null) {
            if (matched !in names) {
                names[matched] = userProfile!!.displayName
                auto += matched
                logger("Matched Speaker $matched to enrolled user")
            } else logger("Matched user is manually named; preserving name")
        } else logger("No match above threshold")
        val contact = callContactName?.takeIf { it.isNotBlank() } ?: callNumber?.takeIf { it.isNotBlank() }
        val unnamed = labels.filter { it !in names && it != matched }
        if (recording.isCall && labels.size == 2 && contact != null && unnamed.size == 1) {
            names[unnamed.single()] = contact
            auto += unnamed.single()
            logger("Named Speaker ${unnamed.single()} from call participant")
        } else logger("Contact naming skipped: requires a two-speaker call with exactly one remaining unnamed speaker")
        return SpeakerIdentificationResult(names, scores, auto, matched, failures)
    }

    companion object {
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            require(a.isNotEmpty() && a.size == b.size) { "Embeddings must have the same nonzero dimension" }
            require(a.all { it.isFinite() } && b.all { it.isFinite() }) { "Embedding contains non-finite values" }
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                dot += a[i].toDouble() * b[i]
                normA += a[i].toDouble() * a[i]
                normB += b[i].toDouble() * b[i]
            }
            val denominator = sqrt(normA * normB)
            return if (denominator == 0.0) 0f else (dot / denominator).toFloat().coerceIn(-1f, 1f)
        }
    }
}

interface SpeakerEmbeddingExtractor {
    /**
     * Extract a speaker embedding from audio segments.
     * 
     * @param audioFile Audio file supported by the platform decoder
     * @param turns Speaker turns to extract audio from
     * @return Model-specific embedding vector
     */
    suspend fun extractSpeakerEmbedding(
        audioFile: File,
        turns: List<Speakers.Turn>,
    ): FloatArray
    
    /**
     * Extract embedding from continuous audio segment.
     * 
     * @param audioFile Audio file
     * @param startMs Start time in milliseconds
     * @param endMs End time in milliseconds
     * @return Model-specific embedding vector
     */
    suspend fun extractEmbedding(
        audioFile: File,
        startMs: Long,
        endMs: Long,
    ): FloatArray
}

/**
 * Stub implementation for testing/development.
 * Returns deterministic but meaningless embeddings.
 * 
 * Only for tests; never used for production identification.
 */
class StubEmbeddingExtractor : SpeakerEmbeddingExtractor {
    override suspend fun extractSpeakerEmbedding(audioFile: File, turns: List<Speakers.Turn>): FloatArray {
        // Generate deterministic embedding based on turns
        val hash = turns.hashCode()
        return FloatArray(192) { i -> ((hash + i) % 256) / 255f }
    }
    
    override suspend fun extractEmbedding(audioFile: File, startMs: Long, endMs: Long): FloatArray {
        // Generate deterministic embedding based on time range
        val hash = (audioFile.path.hashCode() + startMs + endMs).toInt()
        return FloatArray(192) { i -> ((hash + i) % 256) / 255f }
    }
}
