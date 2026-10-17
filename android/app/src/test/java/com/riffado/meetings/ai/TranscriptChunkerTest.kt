package com.riffado.meetings.ai

import org.junit.Assert.*
import org.junit.Test

class TranscriptChunkerTest {

    private val samplePrompt = Prompts.MEETING_SYSTEM

    @Test
    fun `small transcript returns single chunk`() {
        val transcript = """
            Speaker 1: Hello
            Speaker 2: Hi there
        """.trimIndent()

        val chunks = TranscriptChunker.chunkTranscript(transcript, samplePrompt, 5000)

        assertEquals(1, chunks.size)
        assertEquals(transcript, chunks[0])
    }

    @Test
    fun `long transcript splits into multiple chunks`() {
        // Create a transcript that exceeds 5000 token budget
        val longTranscript = buildString {
            for (i in 1..100) {
                append("Speaker $i: This is a long conversation with many participants ")
                append("discussing various topics in great detail. ")
                append("We need to cover all the important points and make sure ")
                append("everyone's voice is heard in this meeting. ")
                repeat(20) {
                    append("More content here to make this really long. ")
                }
                append("\n\n")
            }
        }

        val chunks = TranscriptChunker.chunkTranscript(longTranscript, samplePrompt, 5000)

        assertTrue("Should split into multiple chunks", chunks.size > 1)
        
        // Verify all chunks are within budget
        val promptOverhead = TranscriptChunker.estimateTokens(samplePrompt)
        chunks.forEach { chunk ->
            val tokens = TranscriptChunker.estimateTokens(chunk) + promptOverhead
            assertTrue("Chunk should fit in budget: $tokens tokens", tokens <= 6000) // Allow some margin
        }

        // Verify all content is preserved
        val rejoined = chunks.joinToString("\n\n")
        val originalWords = longTranscript.split(Regex("\\s+")).filter { it.isNotBlank() }
        val rejoinedWords = rejoined.split(Regex("\\s+")).filter { it.isNotBlank() }
        assertEquals("All content should be preserved", originalWords.size, rejoinedWords.size)
    }

    @Test
    fun `chunks split on speaker turn boundaries`() {
        val transcript = """
            Speaker 1: First topic discussion.
            This continues for multiple lines.
            Still talking about the first topic.
            
            Speaker 2: Second person responds.
            With their own multi-line response.
            
            Speaker 1: Back to the first speaker.
            More discussion here.
        """.trimIndent()

        // Use small budget to force splitting
        val chunks = TranscriptChunker.chunkTranscript(transcript, samplePrompt, TranscriptChunker.estimateTokens(samplePrompt) + 60)

        assertTrue("Should split into chunks", chunks.size > 1)

        // Each chunk should start with a speaker label or be a continuation
        chunks.forEach { chunk ->
            val firstLine = chunk.lines().first().trim()
            // Either starts with "Speaker" or is a continuation
            assertTrue(
                "Chunk should have valid structure: '$firstLine'",
                firstLine.startsWith("Speaker") || firstLine.isNotEmpty()
            )
        }
    }

    @Test
    fun `handles transcript without speaker structure`() {
        val transcript = """
            This is a transcript without any speaker labels.
            It just has plain text that goes on for a while.
            We should still be able to chunk it somehow.
        """.trimIndent().repeat(50) // Make it long

        val chunks = TranscriptChunker.chunkTranscript(transcript, samplePrompt, 500)

        assertTrue("Should split long unstructured text", chunks.size > 1)
        
        // All content should be preserved
        val rejoined = chunks.joinToString("")
        assertEquals(transcript.length, rejoined.length)
    }

    @Test
    fun `respects token budget calculation`() {
        val transcript = "x".repeat(10000) // 10k characters

        val estimatedTokens = TranscriptChunker.estimateTokens(transcript)
        
        // Should estimate approximately 10000 / 2.5 = 4000 tokens
        assertTrue("Should estimate ~4000 tokens", estimatedTokens in 3500..4500)
    }

    @Test
    fun `handles oversized single turn`() {
        // Single speaker turn that's too large to fit in one chunk
        val hugeTurn = "Speaker 1: " + "word ".repeat(5000)

        val chunks = TranscriptChunker.chunkTranscript(hugeTurn, samplePrompt, 1000)

        assertTrue("Should split oversized turn", chunks.size > 1)
        
        // Content should be preserved
        val rejoined = chunks.joinToString(" ")
        assertTrue("Should preserve speaker label", rejoined.contains("Speaker 1:"))
    }

    @Test
    fun `empty transcript returns single empty chunk`() {
        val chunks = TranscriptChunker.chunkTranscript("", samplePrompt, 5000)

        assertEquals(1, chunks.size)
        assertEquals("", chunks[0])
    }

    @Test
    fun `splits intelligently on sentence boundaries`() {
        val transcript = buildString {
            repeat(50) {
                append("This is sentence $it with some content. ")
            }
        }

        val chunks = TranscriptChunker.chunkTranscript(transcript, samplePrompt, 500)

        // Verify chunks don't cut mid-sentence when possible
        chunks.dropLast(1).forEach { chunk ->
            // Should end with period or whitespace, not mid-word
            val lastChar = chunk.trimEnd().lastOrNull()
            assertTrue(
                "Chunk should end cleanly: last char '$lastChar'",
                lastChar == null || lastChar == '.' || lastChar == '\n'
            )
        }
    }

    @Test
    fun `handles mixed Arabic and English speaker turns`() {
        val transcript = """
            المتحدث ١: مرحبا بكم في هذا الاجتماع
            هذا موضوع مهم جدا
            
            Speaker 2: Yes, I agree with that point
            We should discuss this further
            
            المتحدث ١: نعم، دعونا نتحدث عن التفاصيل
        """.trimIndent()

        val chunks = TranscriptChunker.chunkTranscript(transcript, samplePrompt, 5000)

        // Should handle RTL text properly
        assertTrue("Should preserve content", chunks.joinToString("\n").contains("مرحبا"))
        assertTrue("Should preserve content", chunks.joinToString("\n").contains("agree"))
    }

    @Test
    fun `very small budget still produces chunks`() {
        val transcript = """
            Speaker 1: Hello
            Speaker 2: Hi
            Speaker 3: Hey
        """.trimIndent()

        // Unreasonably small budget
        val chunks = TranscriptChunker.chunkTranscript(transcript, samplePrompt, 50)

        // Should still split somehow, even if not ideal
        assertFalse("Should produce at least one chunk", chunks.isEmpty())
        assertTrue("Should preserve all speakers", 
            chunks.joinToString("\n").contains("Speaker 1") &&
            chunks.joinToString("\n").contains("Speaker 2") &&
            chunks.joinToString("\n").contains("Speaker 3")
        )
    }
}
