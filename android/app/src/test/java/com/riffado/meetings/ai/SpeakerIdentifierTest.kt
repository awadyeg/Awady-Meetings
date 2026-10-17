package com.riffado.meetings.ai

import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.Speakers
import com.riffado.meetings.data.VoiceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SpeakerIdentifierTest {
    private val profile = VoiceProfile(embedding = floatArrayOf(1f, 0f), audioSource = VoiceProfile.AudioSource.Enrollment(30000))
    private val recording = Recording("id", "Title", 0, transcript = "Speaker 1: Hello\nSpeaker 2: Hi")
    private class Fake(private val vectors: Map<String, FloatArray>) : SpeakerEmbeddingExtractor {
        override suspend fun extractSpeakerEmbedding(audioFile: File, turns: List<Speakers.Turn>) = vectors.getValue(turns.first().speaker!!)
        override suspend fun extractEmbedding(audioFile: File, startMs: Long, endMs: Long) = error("Not used")
    }
    private fun identify(rec: Recording = recording, vectors: Map<String, FloatArray> = mapOf("1" to floatArrayOf(1f, 0f), "2" to floatArrayOf(0f, 1f)), user: VoiceProfile? = profile) = runBlocking {
        SpeakerIdentifier(Fake(vectors)).identifySpeakers(rec, File("audio"), user, threshold = 0.7f)
    }

    @Test fun `best matching user above threshold is named`() {
        val result = identify(vectors = mapOf("1" to floatArrayOf(0.8f, 0.6f), "2" to floatArrayOf(1f, 0f)))
        assertEquals(mapOf("2" to "Me"), result.names)
        assertEquals(setOf("2"), result.autoNamedLabels)
        assertEquals(0.8f, result.scores.getValue("1"), 0.001f)
    }

    @Test fun `below threshold is not named`() {
        val result = identify(vectors = mapOf("1" to floatArrayOf(0f, 1f), "2" to floatArrayOf(-1f, 0f)))
        assertTrue(result.names.isEmpty())
        assertNull(result.matchedUser)
    }

    @Test fun `manual names are never overwritten even when matched`() {
        val result = identify(recording.copy(speakerNames = mapOf("1" to "Manual")))
        assertEquals("Manual", result.names["1"])
        assertTrue(result.autoNamedLabels.isEmpty())
    }

    @Test fun `previous auto names are replaced`() {
        val result = identify(recording.copy(speakerNames = mapOf("1" to "Old", "2" to "Old contact"), autoNamedSpeakers = setOf("1", "2")))
        assertEquals(mapOf("1" to "Me"), result.names)
    }

    @Test fun `two speaker call names the other speaker from contact`() {
        val result = identify(recording.copy(tags = setOf("call"), callContactName = "Alice"))
        assertEquals(mapOf("1" to "Me", "2" to "Alice"), result.names)
    }

    @Test fun `call falls back to phone number`() {
        val result = identify(recording.copy(tags = setOf("call"), callContactName = " ", callNumber = "+123"))
        assertEquals("+123", result.names["2"])
    }

    @Test fun `three speakers never receive contact naming`() {
        val result = identify(recording.copy(tags = setOf("call"), callContactName = "Alice",
            transcript = recording.transcript + "\nSpeaker 3: Hello", speakerNames = mapOf("3" to "Manual")))
        assertFalse(result.names.containsValue("Alice"))
    }

    @Test fun `contact naming without profile or model respects manual name`() = runBlocking {
        val rec = recording.copy(tags = setOf("call"), callNumber = "123", speakerNames = mapOf("1" to "Me"))
        val result = SpeakerIdentifier(null).identifySpeakers(rec, File("audio"), null, threshold = 0.7f)
        assertEquals(mapOf("1" to "Me", "2" to "123"), result.names)
    }

    @Test fun `two unknown speakers cannot be assigned a contact by guessing`() {
        assertTrue(identify(recording.copy(tags = setOf("call"), callContactName = "Alice"), user = null).names.isEmpty())
    }

    @Test fun `non call never receives contact naming`() {
        assertFalse(identify(recording.copy(callContactName = "Alice")).names.containsValue("Alice"))
    }

    @Test fun `embedding failures are reported and do not abort identification`() {
        val result = identify(vectors = mapOf("1" to floatArrayOf(1f, 0f)))
        assertEquals("Me", result.names["1"])
        assertTrue("2" in result.failures)
    }

    @Test fun `coroutine cancellation propagates`() {
        val extractor = object : SpeakerEmbeddingExtractor {
            override suspend fun extractSpeakerEmbedding(audioFile: File, turns: List<Speakers.Turn>): FloatArray = throw CancellationException("cancelled")
            override suspend fun extractEmbedding(audioFile: File, startMs: Long, endMs: Long): FloatArray = error("Not used")
        }
        try {
            runBlocking { SpeakerIdentifier(extractor).identifySpeakers(recording, File("audio"), profile, threshold = 0.7f) }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    @Test fun `cosine math covers orthogonal opposite zero and scaled vectors`() {
        assertEquals(1f, SpeakerIdentifier.cosineSimilarity(floatArrayOf(2f, 0f), floatArrayOf(8f, 0f)), 0.0001f)
        assertEquals(0f, SpeakerIdentifier.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001f)
        assertEquals(-1f, SpeakerIdentifier.cosineSimilarity(floatArrayOf(1f), floatArrayOf(-1f)), 0.0001f)
        assertEquals(0f, SpeakerIdentifier.cosineSimilarity(floatArrayOf(0f), floatArrayOf(1f)), 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched dimensions fail clearly`() { SpeakerIdentifier.cosineSimilarity(floatArrayOf(1f), floatArrayOf(1f, 0f)) }

    @Test fun `timed and legacy transcripts render without timestamps`() {
        val text = "Speaker 1: [1250-3500ms] Hello\nSpeaker 2: Hi\nplain text"
        val turns = Speakers.parse(text)
        assertEquals(1250L, turns[0].startMs)
        assertEquals(3500L, turns[0].endMs)
        assertNull(turns[1].startMs)
        assertEquals("Me: Hello\nSpeaker 2: Hi\nplain text", Speakers.applyNames(text, mapOf("1" to "Me")))
    }
}
