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
import com.dailytimer.app.data.model.TaskStatus
import com.dailytimer.app.data.preferences.SettingsPreferences
import com.dailytimer.app.domain.TaskTime
import java.util.concurrent.TimeUnit

enum class LateReminderPolicy {
    /** The lead window already started: do nothing (used when re-arming existing tasks). */
    SKIP,
    /** The lead window already started: remind almost immediately (used right after creating a task). */
    FIRE_NOW,
}

object NotificationScheduler {

    const val EXTRA_TASK_ID = "extra_task_id"
    private const val IMMEDIATE_DELAY_MS = 1_000L

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
        leadMinutes: Int = SettingsPreferences.getReminderLeadMinutes(context),
        latePolicy: LateReminderPolicy = LateReminderPolicy.SKIP,
    ) {
        if (task.status == TaskStatus.COMPLETED) return
        val now = System.currentTimeMillis()
        val startMillis = TaskTime.toEpochMillis(task.date, task.startTime) ?: return
        if (startMillis <= now) return

        val reminderAt = startMillis - TimeUnit.MINUTES.toMillis(leadMinutes.toLong())
        val triggerAt = when {
            reminderAt > now -> reminderAt
            latePolicy == LateReminderPolicy.FIRE_NOW -> now + IMMEDIATE_DELAY_MS
            else -> return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = TaskNotificationReceiver.ACTION_TRIGGER_NOTIFICATION
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(alarmManager, triggerAt, pendingIntent)
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, operation: PendingIntent) {
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canUseExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
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
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
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
        TaskDatabaseHelper.getInstance(context).getAllUpcomingTasks()
            .forEach { scheduleTaskNotification(context, it, leadMinutes, LateReminderPolicy.SKIP) }
    }
}
