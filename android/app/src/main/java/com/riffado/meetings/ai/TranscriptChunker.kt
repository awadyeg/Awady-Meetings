package com.riffado.meetings.ai

import com.riffado.meetings.data.Speakers

/**
 * Chunks long transcripts for map-reduce summarization.
 * Splits on speaker-turn boundaries to preserve context.
 */
object TranscriptChunker {
    
    
    // Rough approximation: 1 token ≈ 4 characters for English, ≈ 2 for Arabic/mixed
    private const val CHARS_PER_TOKEN_CONSERVATIVE = 2.5
    
    /**
     * Split transcript into chunks that fit within token budget.
     * Splits on speaker-turn boundaries to preserve conversation flow.
     * 
     * @param transcript Full transcript with speaker turns
     * @param promptTemplate The prompt template to be used (for overhead calculation)
     * @param tokenBudget Target tokens per chunk (default 5000)
     * @return List of transcript chunks
     */
    fun chunkTranscript(
        transcript: String,
        promptTemplate: String,
        tokenBudget: Int = 5000,
        logger: (String) -> Unit = {},
    ): List<String> {
        // Estimate prompt overhead in tokens
        val promptOverhead = estimateTokens(promptTemplate)
        val availableTokensForContent = tokenBudget - promptOverhead
        
        if (availableTokensForContent <= 0) {
            logger("Token budget too small for prompt overhead")
            return listOf(transcript)
        }
        
        val targetChars = (availableTokensForContent * CHARS_PER_TOKEN_CONSERVATIVE).toInt()
        
        // Check if transcript fits in single chunk
        if (transcript.length <= targetChars) {
            return listOf(transcript)
        }
        
        logger("Chunking transcript: ${transcript.length} chars, target $targetChars chars per chunk")
        
        // Parse into speaker turns
        val turns = Speakers.parse(transcript)
        
        if (turns.none { it.speaker != null }) {
            // No speaker structure, just split by character count
            return splitBySize(transcript, targetChars)
        }
        
        // Group turns into chunks that fit the budget
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        var currentSize = 0
        
        for (turn in turns) {
            val turnText = if (turn.speaker != null) {
                "Speaker ${turn.speaker}: ${turn.text}\n\n"
            } else {
                "${turn.text}\n\n"
            }
            
            val turnSize = turnText.length
            
            // If single turn exceeds budget, split it
            if (turnSize > targetChars) {
                // Flush current chunk if any
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk.clear()
                    currentSize = 0
                }
                
                // Split this oversized turn
                val splitTurns = splitBySize(turnText, targetChars)
                chunks.addAll(splitTurns)
                continue
            }
            
            // If adding this turn exceeds budget, start new chunk
            if (currentSize + turnSize > targetChars && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk.clear()
                currentSize = 0
            }
            
            currentChunk.append(turnText)
            currentSize += turnSize
        }
        
        // Add final chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        
        logger("Split into ${chunks.size} chunks")
        return chunks
    }
    
    /**
     * Estimate tokens from text length.
     */
    fun estimateTokens(text: String): Int {
        return (text.length / CHARS_PER_TOKEN_CONSERVATIVE).toInt()
    }
    
    /**
     * Split text by character count (fallback when no speaker structure).
     */
    private fun splitBySize(text: String, targetSize: Int): List<String> {
        if (text.length <= targetSize) return listOf(text)
        
        val chunks = mutableListOf<String>()
        var start = 0
        
        while (start < text.length) {
            var end = (start + targetSize).coerceAtMost(text.length)
            
            // Try to break on sentence boundary
            if (end < text.length) {
                val lastPeriod = text.lastIndexOf('.', end)
                val lastNewline = text.lastIndexOf('\n', end)
                val breakPoint = maxOf(lastPeriod, lastNewline)
                
                if (breakPoint > start + targetSize / 2) {
                    end = breakPoint + 1
                }
            }
            
            chunks.add(text.substring(start, end))
            start = end
        }
        
        return chunks
    }
}
