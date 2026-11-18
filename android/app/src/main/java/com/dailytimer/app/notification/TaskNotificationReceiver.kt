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
import com.dailytimer.app.data.model.Task
import com.dailytimer.app.data.model.TaskStatus
import com.dailytimer.app.domain.TaskTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TaskNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Database access (and possible migration) must not block the main thread.
        val pendingResult = goAsync()
        ioExecutor.execute {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(NotificationScheduler.EXTRA_TASK_ID, NO_TASK)
        if (taskId == NO_TASK) return
        val db = TaskDatabaseHelper.getInstance(context)

        when (intent.action) {
            ACTION_MARK_COMPLETE -> {
                db.markTaskCompleted(taskId)
                context.getSystemService(NotificationManager::class.java).cancel(taskId.toInt())
            }
            ACTION_TRIGGER_NOTIFICATION -> {
                val task = db.getTaskById(taskId) ?: return                 // deleted since scheduling
                if (task.status == TaskStatus.COMPLETED) return
                val startMillis = TaskTime.toEpochMillis(task.date, task.startTime) ?: return
                // Round up so "9m 10s left" reads as 10 min, never 9.
                val msLeft = startMillis - System.currentTimeMillis()
                val minutesLeft = TimeUnit.MILLISECONDS.toMinutes(msLeft + TimeUnit.MINUTES.toMillis(1) - 1)
                showNotification(context, task, minutesLeft.toInt())
            }
        }
    }

    private fun showNotification(context: Context, task: Task, minutesLeft: Int) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        createNotificationChannel(context)
        val notificationId = task.id.toInt()
        val immutableUpdate = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TASK_ID, task.id)
        }
        val openPendingIntent = PendingIntent.getActivity(context, notificationId, openAppIntent, immutableUpdate)

        val markCompleteIntent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = ACTION_MARK_COMPLETE
            putExtra(NotificationScheduler.EXTRA_TASK_ID, task.id)
        }
        val markCompletePendingIntent = PendingIntent.getBroadcast(
            context, (task.id + MARK_COMPLETE_REQUEST_OFFSET).toInt(), markCompleteIntent, immutableUpdate,
        )

        val whenText = if (minutesLeft <= 0) "starting now" else "in $minutesLeft min"
        val contentText = "Starts at ${task.startTime} ($whenText)"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(DARK_ORANGE_INT)
            .setColorized(true)
            .setContentTitle("Upcoming: ${task.taskName}")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(VIBRATION_PATTERN)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Open Task", openPendingIntent)
            .addAction(0, "Mark Complete", markCompletePendingIntent)

        if (task.description.isNotBlank()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n\nNotes: ${task.description}")
                    .setSummaryText("Daily Timer Reminder"),
            )
        }
        notificationManager.notify(notificationId, builder.build())
    }

    companion object {
        const val CHANNEL_ID = "daily_timer_reminders_channel"
        const val CHANNEL_NAME = "Task Reminders"
        const val ACTION_TRIGGER_NOTIFICATION = "com.dailytimer.app.ACTION_TRIGGER_NOTIFICATION"
        const val ACTION_MARK_COMPLETE = "com.dailytimer.app.ACTION_MARK_COMPLETE"
        const val DARK_ORANGE_INT = 0xFFE65100.toInt()

        private const val NO_TASK = -1L
        private const val MARK_COMPLETE_REQUEST_OFFSET = 1_000_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 300, 200, 300)
        private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Exact reminders for upcoming tasks"
                enableLights(true)
                lightColor = DARK_ORANGE_INT
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }
}
