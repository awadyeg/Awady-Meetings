package com.riffado.meetings.ai

import com.riffado.meetings.data.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AskAiTest {
    private val budget = Recording("b", "Budget review", 3_000, summary = "## Summary\nThe marketing budget was approved at 50k.",
        transcript = "Speaker 1: Can we approve the budget?\nSpeaker 2: Yes, fifty thousand for marketing.", speakerNames = mapOf("1" to "Mohamed"))
    private val call = Recording("c", "أحمد حسن", 2_000, tags = setOf("call"), callContactName = "أحمد حسن",
        summary = "## الملخص\nاتفقنا على تسليم التطبيق يوم الخميس.", transcript = "Speaker 1: هنسلم التطبيق الخميس إن شاء الله")
    private val other = Recording("o", "Standup", 1_000, summary = "## Summary\nDaily standup, nothing special.")
    private val all = listOf(budget, call, other)

    @Test fun tokenizeNormalisesArabicAndDropsStopwords() {
        assertEquals(listOf("اجتماع", "عميل"), AskAi.tokenize("في الاجتماع والعميل"))
        assertEquals(listOf("budget", "approved"), AskAi.tokenize("Was the BUDGET approved?"))
        assertEquals(listOf("تسليم"), AskAi.tokenize("التسليم امتى؟"))
    }

    @Test fun ranksRelevantRecordingFirst() {
        val ranked = AskAi.rank(all, "how much was the marketing budget?")
        assertEquals("b", ranked.first().first.recordingId)
        val arabic = AskAi.rank(all, "إمتى تسليم التطبيق؟")
        assertEquals("c", arabic.first().first.recordingId)
    }

    @Test fun contextCitesNumberedSourcesAndAppliesSpeakerNames() {
        val ctx = AskAi.buildContext(all, "marketing budget", locale = Locale.US)
        assertEquals("b", ctx.sources.first().recordingId)
        assertEquals(1, ctx.sources.first().number)
        assertTrue(ctx.prompt.startsWith("[1] \"Budget review\""))
        assertTrue(ctx.prompt.contains("Mohamed: Can we approve the budget?"))
    }

    @Test fun fallsBackToRecentSummariesWhenNothingMatches() {
        val ctx = AskAi.buildContext(all, "zzz qqq", locale = Locale.US)
        assertEquals(listOf("b", "c", "o"), ctx.sources.map { it.recordingId })
    }

    @Test fun scopedContextOnlyUsesThatRecording() {
        val ctx = AskAi.buildContext(all, "anything new?", scopedTo = "c", locale = Locale.US)
        assertEquals(listOf("c"), ctx.sources.map { it.recordingId })
        assertTrue(ctx.prompt.contains("الخميس"))
    }

    @Test fun respectsBudgetWithLongTranscripts() {
        val long = Recording("l", "Long call", 5_000, transcript = (1..400).joinToString("\n") { "Speaker ${it % 2 + 1}: budget item number $it is discussed here" })
        val ctx = AskAi.buildContext(listOf(long), "budget", tokenBudget = 2_000, scopedTo = "l", locale = Locale.US)
        val maxChars = ((2_000 - TranscriptChunker.estimateTokens(AskAi.SYSTEM_PROMPT) - TranscriptChunker.estimateTokens("budget") - 600) * 2.5).toInt()
        assertTrue("used ${ctx.usedChars} > $maxChars", ctx.usedChars <= maxChars)
        assertTrue(AskAi.passages(long).size > 3)
        assertTrue(AskAi.passages(long).all { it.text.length <= AskAi.PASSAGE_CHARS + 200 })
    }

    @Test fun citations() {
        val sources = listOf(AskAi.Source(1, "b", "B", 0), AskAi.Source(2, "c", "C", 0))
        assertEquals(listOf("c"), AskAi.citedSources("Delivery is Thursday [2].", sources).map { it.recordingId })
        assertEquals(2, AskAi.citedSources("No citation here", sources).size)
    }

    @Test fun messagesKeepHistoryAndQuestionLast() {
        val ctx = AskAi.buildContext(all, "budget", locale = Locale.US)
        val msgs = AskAi.messages(ctx, "budget?", listOf("user" to "hi", "assistant" to "hello"), "Sunday")
        assertEquals(listOf("system", "user", "assistant", "user"), msgs.map { it.first })
        assertTrue(msgs.last().second.endsWith("Question: budget?"))
    }

    @Test fun tooLargeDetection() {
        assertTrue(AskAi.isTooLarge(ApiException(413, "HTTP 413")))
        assertTrue(AskAi.isTooLarge(ApiException(429, "HTTP 429: Rate limit reached on tokens per minute (TPM)")))
        assertTrue(!AskAi.isTooLarge(ApiException(429, "HTTP 429: requests per day")))
    }
}
