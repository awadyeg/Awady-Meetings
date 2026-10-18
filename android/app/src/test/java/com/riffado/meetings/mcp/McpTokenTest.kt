package com.riffado.meetings.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpTokenTest {
    @Test fun tokensAreRandomUrlSafeAndLongEnough() {
        val a = McpToken.generate(); val b = McpToken.generate()
        assertNotEquals(a, b)
        assertTrue(a.startsWith("rfd_"))
        assertEquals(4 + 43, a.length) // 32 bytes → 43 base64url chars
        assertTrue(a.drop(4).all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertTrue(McpToken.looksValid(a))
        assertFalse(McpToken.looksValid("rfd_short"))
        assertFalse(McpToken.looksValid(""))
    }

    @Test fun maskedHidesTheMiddle() {
        val t = McpToken.generate()
        val m = McpToken.masked(t)
        assertTrue(m.startsWith(t.take(8)) && m.endsWith(t.takeLast(4)))
        assertFalse(m.contains(t.substring(10, 30)))
    }

    @Test fun constantTimeCompare() {
        assertTrue(McpHttpServer.constantTimeEquals("rfd_abc", "rfd_abc"))
        assertFalse(McpHttpServer.constantTimeEquals("rfd_abc", "rfd_abd"))
        assertFalse(McpHttpServer.constantTimeEquals("", "rfd_abc"))
    }

    @Test fun networkHelpers() {
        assertTrue(McpNetwork.isTailscale("100.101.102.103"))
        assertFalse(McpNetwork.isTailscale("100.20.1.1"))
        assertEquals(McpNetwork.Kind.WIFI, McpNetwork.kindOf("wlan0", "192.168.1.5"))
        assertEquals(McpNetwork.Kind.TAILSCALE, McpNetwork.kindOf("tun0", "100.64.0.7"))
        val sorted = McpNetwork.sort(listOf(
            McpNetwork.LanAddress("10.0.0.2", "rmnet0", McpNetwork.Kind.OTHER),
            McpNetwork.LanAddress("100.64.0.7", "tun0", McpNetwork.Kind.TAILSCALE),
            McpNetwork.LanAddress("192.168.1.5", "wlan0", McpNetwork.Kind.WIFI),
        ))
        assertEquals(listOf("192.168.1.5", "100.64.0.7", "10.0.0.2"), sorted.map { it.ip })
        assertEquals("http://192.168.1.5:8765/mcp", McpNetwork.url("192.168.1.5", 8765))
    }
}
