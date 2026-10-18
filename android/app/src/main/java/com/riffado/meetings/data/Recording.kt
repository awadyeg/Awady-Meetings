package com.riffado.meetings.data

import kotlinx.serialization.Serializable

@Serializable
enum class RecordingStatus {
    RECORDING,
    RECORDED,
    QUEUED,
    TRANSCRIBING,
    SUMMARIZING,
    DONE,
    FAILED,
    INTERRUPTED,
}

/** A meeting recording. Audio may be split across several segment files to stay under provider upload limits. */
@Serializable
data class Recording(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long = 0,
    val segments: List<String> = emptyList(),
    val status: RecordingStatus = RecordingStatus.RECORDING,
    val transcript: String? = null,
    val summary: String? = null,
    val error: String? = null,
    val titleIsDefault: Boolean = true,
    /** Display names keyed by speaker number ("1", "2", ...) from a diarized transcript. */
    val speakerNames: Map<String, String> = emptyMap(),
    val autoNamedSpeakers: Set<String> = emptySet(),
    val speakerTurns: List<Speakers.Turn> = emptyList(),
    /** Tags for categorization (e.g., "call" for call recordings). */
    val tags: Set<String> = emptySet(),
    /** Phone number for call recordings (null for meetings). */
    val callNumber: String? = null,
    /** Contact name for call recordings (null if no contact match). */
    val callContactName: String? = null,
    /** ID of provider that transcribed this recording (null if not yet transcribed). */
    val transcribedBy: String? = null,
    /** Bookmarks added while recording, as elapsed milliseconds from the start of the recording. */
    val bookmarks: List<Long> = emptyList(),
    /** When tasks were last extracted from this recording's minutes (null = never). */
    val tasksExtractedAt: Long? = null,
) {
    val isCall: Boolean get() = "call" in tags
    
    /** Returns the best display name for a call: contact name, phone number, or generic fallback. */
    val displayName: String
        get() = when {
            !isCall -> title
            !callContactName.isNullOrBlank() -> callContactName
            !callNumber.isNullOrBlank() -> callNumber
            else -> title
        }
}
