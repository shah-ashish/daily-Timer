package com.dailytimer.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dailytimer.app.MainActivity
import com.dailytimer.app.R
import com.dailytimer.app.data.db.TaskDatabaseHelper

class TaskNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "daily_timer_reminders_channel"
        const val CHANNEL_NAME = "Task Reminders"
        const val ACTION_TRIGGER_NOTIFICATION = "com.dailytimer.app.ACTION_TRIGGER_NOTIFICATION"
        const val ACTION_MARK_COMPLETE = "com.dailytimer.app.ACTION_MARK_COMPLETE"
        const val CORAL_PRIMARY_INT = 0xFFFF5722.toInt()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ACTION_MARK_COMPLETE) {
            val taskId = intent.getLongExtra(NotificationScheduler.EXTRA_TASK_ID, -1L)
            if (taskId != -1L) {
                val dbHelper = TaskDatabaseHelper(context)
                dbHelper.markTaskCompleted(taskId)

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(taskId.toInt())
            }
            return
        }

        if (action == ACTION_TRIGGER_NOTIFICATION) {
            val taskId = intent.getLongExtra(NotificationScheduler.EXTRA_TASK_ID, -1L)
            val taskName = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_NAME) ?: "Upcoming Task"
            val startTime = intent.getStringExtra(NotificationScheduler.EXTRA_START_TIME) ?: ""
            val leadMins = intent.getIntExtra(NotificationScheduler.EXTRA_LEAD_MINS, 10)
            val description = intent.getStringExtra(NotificationScheduler.EXTRA_DESCRIPTION) ?: ""

            showNotification(context, taskId, taskName, startTime, leadMins, description)
        }
    }

    private fun showNotification(
        context: Context,
        taskId: Long,
        taskName: String,
        startTime: String,
        leadMins: Int,
        description: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        // Intent to launch MainActivity when clicking the notification body
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TASK_ID", taskId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mark Complete directly from notification
        val markCompleteIntent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = ACTION_MARK_COMPLETE
            putExtra(NotificationScheduler.EXTRA_TASK_ID, taskId)
        }
        val markCompletePendingIntent = PendingIntent.getBroadcast(
            context,
            (taskId + 10000).toInt(),
            markCompleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val contentText = if (startTime.isNotBlank()) "Starts at $startTime (in $leadMins minutes)" else "Starts in $leadMins minutes"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setColor(CORAL_PRIMARY_INT) // Electric Coral
            .setContentTitle("Upcoming: $taskName")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(openPendingIntent)
            .addAction(0, "Open Task", openPendingIntent)
            .addAction(0, "Mark Complete", markCompletePendingIntent)

        if (description.isNotBlank()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n\nNotes: $description")
                    .setSummaryText("Daily Timer Reminder")
            )
        }

        notificationManager.notify(taskId.toInt(), builder.build())
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Exact reminders for upcoming tasks"
                enableLights(true)
                lightColor = CORAL_PRIMARY_INT
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
