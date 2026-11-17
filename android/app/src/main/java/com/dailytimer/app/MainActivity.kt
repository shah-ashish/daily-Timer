package com.dailytimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.dailytimer.app.ui.create.CreateTaskScreen
import com.dailytimer.app.ui.navigation.NavTab
import com.dailytimer.app.ui.settings.SettingsScreen
import com.dailytimer.app.ui.theme.DailyTimerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }
            var currentTab by remember { mutableStateOf(NavTab.SETTINGS) }

            DailyTimerTheme(darkTheme = isDarkMode) {
                when (currentTab) {
                    NavTab.CREATE -> {
                        CreateTaskScreen(
                            selectedTab = currentTab,
                            onTabSelected = { currentTab = it },
                            onTaskCreated = {
                                // Task saved successfully into SQLite
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
                    else -> {
                        // For Home / Tasks tabs, fallback gracefully to Settings for now
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
