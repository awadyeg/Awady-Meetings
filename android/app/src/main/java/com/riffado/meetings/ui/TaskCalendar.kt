package com.riffado.meetings.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.riffado.meetings.R
import com.riffado.meetings.data.TaskItem
import java.util.TimeZone

/** "Add to calendar" via the CalendarContract insert intent: opens the user's calendar app, needs no permission. */
object TaskCalendar {
    private const val HALF_HOUR = 30 * 60_000L
    private const val DAY = 24 * 60 * 60_000L

    fun insertIntent(task: TaskItem, sourceTitle: String?, context: Context): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, task.title)
            val description = buildList {
                task.assignee?.let { add(context.getString(R.string.tasks_cal_assignee, it)) }
                task.dueText?.let { add(context.getString(R.string.tasks_cal_due, it)) }
                sourceTitle?.let { add(context.getString(R.string.tasks_cal_source, it)) }
            }.joinToString("\n")
            if (description.isNotEmpty()) putExtra(CalendarContract.Events.DESCRIPTION, description)
            task.dueAt?.let { due ->
                if (task.allDay) {
                    // All-day events are stored in UTC; shift local midnight to UTC midnight of the same date.
                    val utcMidnight = due + TimeZone.getDefault().getOffset(due)
                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, utcMidnight)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, utcMidnight + DAY)
                } else {
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, due)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, due + HALF_HOUR)
                }
            }
        }

    fun add(context: Context, task: TaskItem, sourceTitle: String?) = launch(context, listOf(insertIntent(task, sourceTitle, context)))

    /** Opens one insert screen per task (stacked; the user saves each). */
    fun addAll(context: Context, tasks: List<Pair<TaskItem, String?>>) =
        launch(context, tasks.map { (t, s) -> insertIntent(t, s, context) })

    private fun launch(context: Context, intents: List<Intent>) {
        if (intents.isEmpty()) return
        try {
            if (intents.size == 1) context.startActivity(intents[0]) else context.startActivities(intents.toTypedArray())
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.tasks_no_calendar, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.tasks_no_calendar, Toast.LENGTH_LONG).show()
        }
    }
}
