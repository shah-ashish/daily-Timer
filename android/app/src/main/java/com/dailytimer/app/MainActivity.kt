package com.dailytimer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dailytimer.app.data.db.TaskDatabaseHelper
import com.dailytimer.app.data.preferences.SettingsPreferences
import com.dailytimer.app.notification.NotificationScheduler
import com.dailytimer.app.notification.TaskNotificationReceiver
import com.dailytimer.app.ui.create.CreateTaskScreen
import com.dailytimer.app.ui.home.HomeScreen
import com.dailytimer.app.ui.navigation.NavTab
import com.dailytimer.app.ui.settings.SettingsScreen
import com.dailytimer.app.ui.tasks.TasksScreen
import com.dailytimer.app.ui.theme.CoralPrimary
import com.dailytimer.app.ui.theme.DailyTimerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestedTaskId = mutableLongStateOf(NO_TASK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pre-register notification channel so system knows it immediately
        TaskNotificationReceiver.createNotificationChannel(applicationContext)

        // Reschedule active alarms on launch in background
        lifecycleScope.launch(Dispatchers.IO) {
            NotificationScheduler.rescheduleAll(applicationContext)
        }

        consumeLaunchIntent(intent)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkOverride by remember { mutableStateOf(SettingsPreferences.getDarkModeOverride(applicationContext)) }
            val isDarkMode = darkOverride ?: systemDark
            var currentTab by rememberSaveable { mutableStateOf(NavTab.HOME) }
            var tasksDate by rememberSaveable { mutableStateOf<String?>(null) }
            var createDate by rememberSaveable { mutableStateOf<String?>(null) }
            var showExactAlarmDialog by remember { mutableStateOf(false) }

            // Handle incoming deep navigation from notification
            LaunchedEffect(requestedTaskId.longValue) {
                val id = requestedTaskId.longValue
                if (id == NO_TASK) return@LaunchedEffect
                val task = withContext(Dispatchers.IO) {
                    TaskDatabaseHelper.getInstance(applicationContext).getTaskById(id)
                }
                if (task != null) {
                    tasksDate = task.date
                    currentTab = NavTab.TASKS
                }
                requestedTaskId.longValue = NO_TASK
            }

            fun navigate(tab: NavTab) {
                createDate = null
                currentTab = tab
            }

            // Request Notification Permission on Android 13+ (API 33+)
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val isGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!isGranted) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!NotificationScheduler.canScheduleExactAlarms(this@MainActivity)) {
                        showExactAlarmDialog = true
                    }
                }
            }

            DailyTimerTheme(darkTheme = isDarkMode) {
                if (showExactAlarmDialog) {
                    AlertDialog(
                        onDismissRequest = { showExactAlarmDialog = false },
                        title = {
                            Text(
                                text = "Enable Exact Reminders",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Text(
                                text = "To ensure reminders trigger at the exact minute even when your screen is off or in sleep mode, please allow 'Alarms & reminders' for Daily Timer in system settings.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExactAlarmDialog = false
                                    NotificationScheduler.openExactAlarmSettings(this@MainActivity)
                                }
                            ) {
                                Text("Open Settings", fontWeight = FontWeight.Bold, color = CoralPrimary)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExactAlarmDialog = false }) {
                                Text("Not Now", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                when (currentTab) {
                    NavTab.HOME -> {
                        HomeScreen(
                            selectedTab = currentTab,
                            onTabSelected = ::navigate,
                            onCreateTaskClick = { navigate(NavTab.CREATE) }
                        )
                    }
                    NavTab.TASKS -> {
                        TasksScreen(
                            selectedTab = currentTab,
                            onTabSelected = ::navigate,
                            initialDate = tasksDate,
                            onAddTaskForDate = { date ->
                                createDate = date
                                currentTab = NavTab.CREATE
                            }
                        )
                    }
                    NavTab.CREATE -> {
                        CreateTaskScreen(
                            initialDate = createDate,
                            selectedTab = currentTab,
                            onTabSelected = ::navigate,
                            onTaskCreated = { date ->
                                tasksDate = date
                                createDate = null
                                currentTab = NavTab.TASKS
                            }
                        )
                    }
                    NavTab.SETTINGS -> {
                        SettingsScreen(
                            isDarkMode = isDarkMode,
                            onThemeToggle = { dark ->
                                darkOverride = dark
                                SettingsPreferences.setDarkModeOverride(applicationContext, dark)
                            },
                            selectedTab = currentTab,
                            onTabSelected = ::navigate
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        requestedTaskId.longValue = intent?.getLongExtra(EXTRA_OPEN_TASK_ID, NO_TASK) ?: NO_TASK
        intent?.removeExtra(EXTRA_OPEN_TASK_ID) // Avoid repeating navigation on recreation
    }

    companion object {
        const val EXTRA_OPEN_TASK_ID = "com.dailytimer.app.extra.OPEN_TASK_ID"
        private const val NO_TASK = -1L
    }
}
