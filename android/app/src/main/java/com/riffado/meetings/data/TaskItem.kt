package com.riffado.meetings.data

import kotlinx.serialization.Serializable

/** An action item / deadline extracted from a recording's minutes. */
@Serializable
data class TaskItem(
    /** Stable id: `<recordingId>:<hash of normalised title>` so re-extraction keeps done state. */
    val id: String,
    val recordingId: String,
    val title: String,
    val assignee: String? = null,
    /** Due time in epoch millis; for [allDay] tasks this is local midnight of the due date. */
    val dueAt: Long? = null,
    val allDay: Boolean = true,
    /** Original wording of the deadline ("by Thursday", "قبل آخر الشهر"). */
    val dueText: String? = null,
    val done: Boolean = false,
    val createdAt: Long = 0,
)
