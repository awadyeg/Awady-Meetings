package com.riffado.meetings.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Extracted tasks for all recordings, persisted as `tasks.json` in app-private storage. */
class TaskStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "tasks.json")
    private val serializer = ListSerializer(TaskItem.serializer())
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    val tasks: StateFlow<List<TaskItem>> = state.asStateFlow()

    /** Replaces a recording's tasks with a fresh extraction, keeping done state for matching titles. */
    suspend fun replaceForRecording(recordingId: String, fresh: List<TaskItem>) = mutex.withLock {
        val existing = state.value.filter { it.recordingId == recordingId }
        val merged = TaskLogic.merge(existing, fresh)
        commit(state.value.filterNot { it.recordingId == recordingId } + merged)
    }

    suspend fun setDone(id: String, done: Boolean) = mutex.withLock {
        commit(state.value.map { if (it.id == id) it.copy(done = done) else it })
    }

    suspend fun deleteForRecording(recordingId: String) = mutex.withLock {
        val next = state.value.filterNot { it.recordingId == recordingId }
        if (next.size != state.value.size) commit(next)
    }

    private fun commit(list: List<TaskItem>) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(serializer, list))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        state.value = list
    }

    private fun load(): List<TaskItem> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }
}
