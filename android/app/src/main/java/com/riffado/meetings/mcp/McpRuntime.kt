package com.riffado.meetings.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live state of the embedded MCP server, observed by Settings. */
sealed interface McpServerState {
    data object Stopped : McpServerState
    data object Starting : McpServerState
    data class Running(val port: Int, val startedAt: Long, val requests: Long = 0, val lastRequestAt: Long? = null) : McpServerState
    data class Failed(val message: String) : McpServerState
}

object McpRuntime {
    private val mutable = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    val state: StateFlow<McpServerState> = mutable.asStateFlow()

    fun set(value: McpServerState) {
        mutable.value = value
    }

    fun recordRequest(now: Long = System.currentTimeMillis()) {
        val current = mutable.value
        if (current is McpServerState.Running) mutable.value = current.copy(requests = current.requests + 1, lastRequestAt = now)
    }
}
