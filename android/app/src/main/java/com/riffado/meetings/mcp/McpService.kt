package com.riffado.meetings.mcp

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.riffado.meetings.MainActivity
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.R
import java.net.BindException

/**
 * Foreground service hosting the MCP server. Runs only while the user has it enabled in Settings;
 * the ongoing notification shows the address and offers Stop.
 */
class McpService : Service() {
    private var server: McpHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as MeetingsApp
        if (intent?.action == ACTION_STOP) {
            app.mcpSettings.setEnabled(false)
            shutdown()
            return START_NOT_STICKY
        }
        val settings = app.mcpSettings.settings.value
        if (!settings.enabled) {
            shutdown()
            return START_NOT_STICKY
        }
        McpRuntime.set(McpServerState.Starting)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(settings.port),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        val running = server
        if (intent?.action != ACTION_RESTART && running != null && running.isRunning && running.boundPort == settings.port) {
            McpRuntime.set(McpServerState.Running(running.boundPort, System.currentTimeMillis(), running.requestCount))
            return START_STICKY
        }
        running?.stop()
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1"
        val fresh = McpHttpServer(
            protocol = McpProtocol(McpTools.forApp(app), serverVersion = version),
            token = { app.mcpSettings.settings.value.token },
            port = settings.port,
            onRequest = { _, _ ->
                McpRuntime.recordRequest()
                refreshWakeLock()
            },
        )
        try {
            fresh.start()
        } catch (e: Exception) {
            Log.w(TAG, "MCP server failed to start", e)
            val message = if (e is BindException) getString(R.string.mcp_err_port_in_use, settings.port) else (e.message ?: e.javaClass.simpleName)
            McpRuntime.set(McpServerState.Failed(message))
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        server = fresh
        acquireLocks(settings.keepAwake)
        McpRuntime.set(McpServerState.Running(fresh.boundPort, System.currentTimeMillis()))
        Log.i(TAG, "MCP server listening on :${fresh.boundPort}")
        return START_STICKY
    }

    override fun onDestroy() {
        shutdown(stopService = false)
        super.onDestroy()
    }

    private fun shutdown(stopService: Boolean = true) {
        server?.stop()
        server = null
        releaseLocks()
        if (McpRuntime.state.value !is McpServerState.Failed) McpRuntime.set(McpServerState.Stopped)
        if (stopService) {
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopForegroundCompat() = ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

    private fun acquireLocks(keepAwake: Boolean) {
        releaseLocks()
        if (!keepAwake) return
        wakeLock = getSystemService<PowerManager>()?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "riffado:mcp")?.apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_IDLE_MS)
        }
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = applicationContext.getSystemService<WifiManager>()?.createWifiLock(mode, "riffado:mcp")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    /** Each request extends the wake lock; after [WAKE_LOCK_IDLE_MS] without requests the phone may sleep again. */
    private fun refreshWakeLock() {
        runCatching { wakeLock?.acquire(WAKE_LOCK_IDLE_MS) }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun notification(port: Int) = NotificationCompat.Builder(this, MeetingsApp.CHANNEL_MCP)
        .setSmallIcon(R.drawable.ic_stat_agents)
        .setContentTitle(getString(R.string.mcp_notif_title))
        .setContentText(McpNetwork.addresses().firstOrNull()?.let { McpNetwork.url(it.ip, port) } ?: getString(R.string.mcp_notif_no_network, port))
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0, getString(R.string.mcp_notif_stop),
            PendingIntent.getService(this, 1, Intent(this, McpService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    companion object {
        private const val TAG = "McpService"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_STOP = "com.riffado.meetings.mcp.STOP"
        private const val ACTION_RESTART = "com.riffado.meetings.mcp.RESTART"
        private const val WAKE_LOCK_IDLE_MS = 6 * 60 * 60 * 1000L

        /** Starts or stops the service to match the saved settings (call after any MCP setting changes). */
        fun sync(context: Context) {
            val app = context.applicationContext as MeetingsApp
            val intent = Intent(context, McpService::class.java)
            if (app.mcpSettings.settings.value.enabled) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
                    .onFailure { McpRuntime.set(McpServerState.Failed(it.message ?: it.javaClass.simpleName)) }
            } else {
                context.stopService(intent)
                McpRuntime.set(McpServerState.Stopped)
            }
        }

        /** Restarts a running server (port or wake-lock preference changed). */
        fun restart(context: Context) {
            val app = context.applicationContext as MeetingsApp
            if (!app.mcpSettings.settings.value.enabled) return sync(context)
            runCatching { ContextCompat.startForegroundService(context, Intent(context, McpService::class.java).setAction(ACTION_RESTART)) }
                .onFailure { McpRuntime.set(McpServerState.Failed(it.message ?: it.javaClass.simpleName)) }
        }
    }
}
