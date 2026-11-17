package com.dailytimer.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.dailytimer.app.data.db.TaskDatabaseHelper
import com.dailytimer.app.data.model.Task
import com.dailytimer.app.data.preferences.SettingsPreferences
import java.text.SimpleDateFormat
import java.util.*

object NotificationScheduler {

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_NAME = "extra_task_name"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_LEAD_MINS = "extra_lead_mins"
    const val EXTRA_DESCRIPTION = "extra_description"

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
        }
    }

    fun scheduleTaskNotification(
        context: Context,
        task: Task,
        leadMinutes: Int = SettingsPreferences.getReminderLeadMinutes(context)
    ) {
        val now = System.currentTimeMillis()
        val taskStartTime = parseTaskTime(task.date, task.startTime)

        if (taskStartTime == 0L || taskStartTime <= now) {
            // Task has already ended or passed
            return
        }

        // Calculate reminder trigger time based on leadMinutes
        val leadMillis = leadMinutes * 60 * 1000L
        val calculatedTriggerTime = taskStartTime - leadMillis

        // If reminder time has already passed but task itself hasn't started yet,
        // trigger reminder right away (1 second delay)
        val triggerTime = if (calculatedTriggerTime > now) {
            calculatedTriggerTime
        } else {
            now + 1000L
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Safe fallback for Android 12+ when exact alarm permission isn't granted:
                    // Still wakes up device from Doze mode without throwing SecurityException
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // If OEM security policy throws on setExactAndAllowWhileIdle, fallback gracefully
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (_: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
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

    fun rescheduleAll(
        context: Context,
        leadMinutes: Int = SettingsPreferences.getReminderLeadMinutes(context)
    ) {
        val dbHelper = TaskDatabaseHelper(context)
        val upcoming = dbHelper.getAllUpcomingTasks()
        for (task in upcoming) {
            scheduleTaskNotification(context, task, leadMinutes)
        }
    }

    private fun parseTaskTime(dateStr: String, timeStr: String): Long {
        val cleanTime = timeStr.trim().replace("\u00A0", " ")
        val combined = "${dateStr.trim()} $cleanTime"

        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US),
            SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.US),
            SimpleDateFormat("yyyy-MM-dd H:mm", Locale.US),
            SimpleDateFormat("yyyy-MM-dd hh:mma", Locale.US)
        )

        for (format in formats) {
            try {
                format.isLenient = true
                val parsed = format.parse(combined)
                if (parsed != null) {
                    return parsed.time
                }
            } catch (_: Exception) {}
        }
        return 0L
    }
}
