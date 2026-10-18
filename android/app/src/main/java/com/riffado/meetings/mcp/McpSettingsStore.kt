package com.riffado.meetings.mcp

import android.content.Context
import androidx.core.content.edit
import com.riffado.meetings.data.KeystoreCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class McpSettings(
    val enabled: Boolean = false,
    val port: Int = McpHttpServer.DEFAULT_PORT,
    val token: String = "",
    /** Hold a partial wake lock + Wi-Fi lock while running so agents can connect with the screen off. */
    val keepAwake: Boolean = true,
)

/** MCP server settings; the bearer token is encrypted at rest with [KeystoreCipher]. */
class McpSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("mcp", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val settings: StateFlow<McpSettings> = mutable.asStateFlow()

    init {
        if (!McpToken.looksValid(mutable.value.token)) regenerateToken()
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun setKeepAwake(keep: Boolean) = update { it.copy(keepAwake = keep) }

    /** Returns false (and keeps the old port) when [port] is outside 1024–65535. */
    fun setPort(port: Int): Boolean {
        if (!validPort(port)) return false
        update { it.copy(port = port) }
        return true
    }

    fun regenerateToken(): String {
        val token = McpToken.generate()
        update { it.copy(token = token) }
        return token
    }

    private fun update(transform: (McpSettings) -> McpSettings) {
        val next = transform(mutable.value)
        prefs.edit {
            putBoolean(K_ENABLED, next.enabled)
            putInt(K_PORT, next.port)
            putString(K_TOKEN, KeystoreCipher.encrypt(next.token))
            putBoolean(K_AWAKE, next.keepAwake)
        }
        mutable.value = next
    }

    private fun read() = McpSettings(
        enabled = prefs.getBoolean(K_ENABLED, false),
        port = prefs.getInt(K_PORT, McpHttpServer.DEFAULT_PORT),
        token = KeystoreCipher.decrypt(prefs.getString(K_TOKEN, "") ?: ""),
        keepAwake = prefs.getBoolean(K_AWAKE, true),
    )

    companion object {
        fun validPort(port: Int) = port in 1024..65535
        private const val K_ENABLED = "enabled"
        private const val K_PORT = "port"
        private const val K_TOKEN = "token"
        private const val K_AWAKE = "keep_awake"
    }
}
