package com.riffado.meetings.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restores call recording state after device boot or app update.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Restart the MCP server if the user left it on (both broadcasts may start foreground services).
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            com.riffado.meetings.mcp.McpService.sync(context)
        }
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Device booted, call recording will be active when accessibility service starts")
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "App updated, call recording state preserved")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
