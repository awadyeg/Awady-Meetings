package com.riffado.meetings.callrecorder

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic logger for call recording events.
 * Stores last 50 events to help users debug recording failures.
 */
object CallRecorderDiagnostics {
    private const val MAX_EVENTS = 50
    private const val LOG_FILE = "call_recorder_diagnostics.txt"
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    @Synchronized
    fun log(context: Context, event: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val entry = "[$timestamp] $event\n"
            
            val file = File(context.filesDir, LOG_FILE)
            val existing = if (file.exists()) file.readLines() else emptyList()
            val updated = (existing + entry).takeLast(MAX_EVENTS)
            
            file.writeText(updated.joinToString(""))
            android.util.Log.d("CallRecorderDiag", event)
        } catch (e: Exception) {
            android.util.Log.e("CallRecorderDiag", "Failed to write diagnostic log", e)
        }
    }
    
    @Synchronized
    fun read(context: Context): String {
        return try {
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists()) file.readText() else "No diagnostic logs yet."
        } catch (e: Exception) {
            "Error reading diagnostics: ${e.message}"
        }
    }
    
    @Synchronized
    fun clear(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            android.util.Log.e("CallRecorderDiag", "Failed to clear diagnostics", e)
        }
    }
}
