package com.riffado.meetings.mcp

import java.security.SecureRandom
import java.util.Base64

/** Bearer tokens for the MCP server: `rfd_` + 256 random bits, base64url without padding. */
object McpToken {
    const val PREFIX = "rfd_"

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun looksValid(token: String): Boolean = token.startsWith(PREFIX) && token.length >= PREFIX.length + 40

    /** "rfd_AbCd…wxyz" for display when the token is hidden. */
    fun masked(token: String): String =
        if (token.length <= 12) "••••" else token.take(8) + "••••••••" + token.takeLast(4)
}
