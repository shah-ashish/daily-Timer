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
import com.dailytimer.app.data.model.TaskStatus
import com.dailytimer.app.domain.TaskPhase
import com.dailytimer.app.domain.TaskPhaseResolver
import com.dailytimer.app.domain.TaskTime
import com.dailytimer.app.notification.NotificationScheduler
import com.dailytimer.app.ui.navigation.BottomNavBar
import com.dailytimer.app.ui.navigation.NavTab
import com.dailytimer.app.ui.theme.CoralPrimary
import com.dailytimer.app.ui.theme.EmeraldSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun rememberNowMillis(refreshIntervalMs: Long = 30_000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(refreshIntervalMs)
            value = System.currentTimeMillis()
        }
    }

@Composable
fun TasksScreen(
    selectedTab: NavTab = NavTab.TASKS,
    onTabSelected: (NavTab) -> Unit = {},
    initialDate: String? = null,
    onAddTaskForDate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val dbHelper = remember { TaskDatabaseHelper.getInstance(context) }
    val nowMillis by rememberNowMillis()

    var selectedDate by remember(initialDate) {
        mutableStateOf(TaskTime.parseDate(initialDate.orEmpty()) ?: LocalDate.now())
    }
    val selectedDateDb = TaskTime.formatDate(selectedDate)
    val selectedDateDisplay = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault()))
    }

    var tasksForDate by remember { mutableStateOf(listOf<Task>()) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(selectedDateDb) {
        dbHelper.changes.onStart { emit(Unit) }.collectLatest {
            tasksForDate = withContext(Dispatchers.IO) { dbHelper.getTasksForDate(selectedDateDb) }
        }
    }

    // Native DatePickerDialog keyed on selectedDate
    val datePickerDialog = remember(selectedDate) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        )
    }

    // Delete Confirmation Dialog
    if (taskToDelete != null) {
        val target = taskToDelete!!
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = {
                Text(
                    text = "Delete Task",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${target.taskName}\"? This action cannot be undone.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        dbHelper.deleteTask(target.id)
                        NotificationScheduler.cancelTaskNotification(context, target.id)
                        taskToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { taskToDelete = null }
                ) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
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
            // Header: iOS Large Title
            Text(
                text = "Tasks",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Single Date Selector Card
            Text(
                text = "SELECT DATE",
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CoralPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = "Date",
                                tint = CoralPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = selectedDateDisplay,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${tasksForDate.size} scheduled tasks",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CoralPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tasks List
            if (tasksForDate.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CoralPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Empty",
                                tint = CoralPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
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
                            val isCompleted = task.status == TaskStatus.COMPLETED
                            val phase = TaskPhaseResolver.resolve(task, nowMillis)
                            val isInProgress = phase == TaskPhase.IN_PROGRESS
                            val isOverdue = phase == TaskPhase.OVERDUE

                            val timeSpan = if (task.endTime.isNotBlank()) {
                                "${task.startTime} – ${task.endTime}"
                            } else {
                                task.startTime
                            }

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
                                            val newStatus = if (isCompleted) TaskStatus.UPCOMING else TaskStatus.COMPLETED
                                            val updated = task.copy(status = newStatus)
                                            dbHelper.updateTask(updated)
                                            if (newStatus == TaskStatus.COMPLETED) {
                                                NotificationScheduler.cancelTaskNotification(context, task.id)
                                            } else {
                                                NotificationScheduler.scheduleTaskNotification(context, updated)
                                            }
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
                                                tint = if (isInProgress) CoralPrimary else CoralPrimary.copy(alpha = 0.8f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = timeSpan,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isInProgress) CoralPrimary else CoralPrimary.copy(alpha = 0.8f)
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

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Status chip
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when {
                                                    isCompleted -> EmeraldSuccess.copy(alpha = 0.15f)
                                                    isInProgress -> CoralPrimary.copy(alpha = 0.15f)
                                                    isOverdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                                }
                                            )
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isCompleted -> "COMPLETED"
                                                isInProgress -> "IN PROGRESS"
                                                isOverdue -> "OVERDUE"
                                                else -> "UPCOMING"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isCompleted -> EmeraldSuccess
                                                isInProgress -> CoralPrimary
                                                isOverdue -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            }
                                        )
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
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Task",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Task for This Date",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
