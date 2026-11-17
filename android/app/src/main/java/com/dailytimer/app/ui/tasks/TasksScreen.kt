package com.dailytimer.app.ui.tasks

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailytimer.app.data.db.TaskDatabaseHelper
import com.dailytimer.app.data.model.Task
import com.dailytimer.app.notification.NotificationScheduler
import com.dailytimer.app.ui.navigation.BottomNavBar
import com.dailytimer.app.ui.navigation.NavTab
import com.dailytimer.app.ui.theme.CoralPrimary
import com.dailytimer.app.ui.theme.EmeraldSuccess
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TasksScreen(
    selectedTab: NavTab = NavTab.TASKS,
    onTabSelected: (NavTab) -> Unit = {},
    onAddTaskForDate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val dbHelper = remember { TaskDatabaseHelper(context) }
    val calendar = remember { Calendar.getInstance() }
    val dateFormatDb = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayDateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }

    var selectedDateDb by remember { mutableStateOf(dateFormatDb.format(calendar.time)) }
    var selectedDateDisplay by remember { mutableStateOf(displayDateFormat.format(calendar.time)) }
    var tasksForDate by remember { mutableStateOf(listOf<Task>()) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    fun refreshTasks() {
        tasksForDate = dbHelper.getTasksForDate(selectedDateDb)
    }

    LaunchedEffect(selectedDateDb) {
        refreshTasks()
    }

    // Native DatePickerDialog
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                selectedDateDb = dateFormatDb.format(chosen.time)
                selectedDateDisplay = displayDateFormat.format(chosen.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // Header
            Text(
                text = "Tasks",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Date Filter Section (Single date selector row)
            Text(
                text = "DATE FILTER",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { datePickerDialog.show() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CoralPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = "Calendar",
                                tint = CoralPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Selected Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = selectedDateDisplay,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = "Change ▾",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CoralPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tasks List Section
            Text(
                text = "SCHEDULE (${tasksForDate.size} ${if (tasksForDate.size == 1) "TASK" else "TASKS"})",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            if (tasksForDate.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No tasks for this day",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap below to add a new task for $selectedDateDb.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        tasksForDate.forEachIndexed { index, task ->
                            val isCompleted = task.status == "COMPLETED"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Status Toggle Checkbox
                                    IconButton(
                                        onClick = {
                                            val newStatus = if (isCompleted) "UPCOMING" else "COMPLETED"
                                            val updated = task.copy(status = newStatus)
                                            dbHelper.updateTask(updated)
                                            if (newStatus == "COMPLETED") {
                                                NotificationScheduler.cancelTaskNotification(context, task.id)
                                            } else {
                                                NotificationScheduler.scheduleTaskNotification(context, updated)
                                            }
                                            refreshTasks()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = "Toggle Complete",
                                            tint = if (isCompleted) EmeraldSuccess else MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Schedule,
                                                contentDescription = "Time",
                                                tint = CoralPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = task.startTime,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = CoralPrimary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = task.taskName,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 16.sp
                                            ),
                                            color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                        )

                                        if (task.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = task.description,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }

                                // Delete task button
                                IconButton(
                                    onClick = { taskToDelete = task },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFE53935).copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (index < tasksForDate.lastIndex) {
                                Divider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Button: Add task to this date
            Button(
                onClick = { onAddTaskForDate(selectedDateDb) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Task to this Date",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Delete Confirmation Dialog
        taskToDelete?.let { task ->
            AlertDialog(
                onDismissRequest = { taskToDelete = null },
                title = {
                    Text(
                        text = "Delete Task",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete \"${task.taskName}\"? This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            NotificationScheduler.cancelTaskNotification(context, task.id)
                            dbHelper.deleteTask(task.id)
                            refreshTasks()
                            taskToDelete = null
                        }
                    ) {
                        Text(
                            text = "Delete",
                            color = Color(0xFFE53935),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskToDelete = null }) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
