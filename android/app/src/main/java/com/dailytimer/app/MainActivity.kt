package com.dailytimer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.dailytimer.app.notification.NotificationScheduler
import com.dailytimer.app.ui.create.CreateTaskScreen
import com.dailytimer.app.ui.home.HomeScreen
import com.dailytimer.app.ui.navigation.NavTab
import com.dailytimer.app.ui.settings.SettingsScreen
import com.dailytimer.app.ui.tasks.TasksScreen
import com.dailytimer.app.ui.theme.DailyTimerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Reschedule active alarms on launch
        NotificationScheduler.rescheduleAll(applicationContext)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }
            var currentTab by remember { mutableStateOf(NavTab.HOME) }

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
            }

            DailyTimerTheme(darkTheme = isDarkMode) {
                when (currentTab) {
                    NavTab.HOME -> {
                        HomeScreen(
                            selectedTab = currentTab,
                            onTabSelected = { currentTab = it },
                            onCreateTaskClick = { currentTab = NavTab.CREATE }
                        )
                    }
                    NavTab.TASKS -> {
                        TasksScreen(
                            selectedTab = currentTab,
                            onTabSelected = { currentTab = it },
                            onAddTaskForDate = { _ ->
                                currentTab = NavTab.CREATE
                            }
                        )
                    }
                    NavTab.CREATE -> {
                        CreateTaskScreen(
                            selectedTab = currentTab,
                            onTabSelected = { currentTab = it },
                            onTaskCreated = {
                                currentTab = NavTab.TASKS
                            }
                        )
                    }
                    NavTab.SETTINGS -> {
                        SettingsScreen(
                            isDarkMode = isDarkMode,
                            onThemeToggle = { isDarkMode = it },
                            selectedTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }
                }
            }
        }
    }
}
