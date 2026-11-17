package com.dailytimer.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dailytimer.app.data.db.TaskDatabaseHelper
import com.dailytimer.app.data.model.Task
import java.text.SimpleDateFormat
import java.util.*

object NotificationScheduler {

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_NAME = "extra_task_name"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_LEAD_MINS = "extra_lead_mins"
    const val EXTRA_DESCRIPTION = "extra_description"

    fun scheduleTaskNotification(context: Context, task: Task, leadMinutes: Int = 10) {
        val triggerTime = computeTriggerTime(task.date, task.startTime, leadMinutes)
        if (triggerTime <= System.currentTimeMillis()) {
            return // Task or reminder has already passed
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Verify exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = TaskNotificationReceiver.ACTION_TRIGGER_NOTIFICATION
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_NAME, task.taskName)
            putExtra(EXTRA_START_TIME, task.startTime)
            putExtra(EXTRA_LEAD_MINS, leadMinutes)
            putExtra(EXTRA_DESCRIPTION, task.description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    fun cancelTaskNotification(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = TaskNotificationReceiver.ACTION_TRIGGER_NOTIFICATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun rescheduleAll(context: Context, leadMinutes: Int = 10) {
        val dbHelper = TaskDatabaseHelper(context)
        val upcoming = dbHelper.getAllUpcomingTasks()
        for (task in upcoming) {
            scheduleTaskNotification(context, task, leadMinutes)
        }
    }

    private fun computeTriggerTime(dateStr: String, timeStr: String, leadMinutes: Int): Long {
        val combined = "$dateStr $timeStr"
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        )

        for (format in formats) {
            try {
                val parsed = format.parse(combined)
                if (parsed != null) {
                    return parsed.time - (leadMinutes * 60 * 1000L)
                }
            } catch (_: Exception) {}
        }
        return 0L
    }
}
