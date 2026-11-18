package com.dailytimer.app.ui.create

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
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
import com.dailytimer.app.domain.TaskTime
import com.dailytimer.app.notification.LateReminderPolicy
import com.dailytimer.app.notification.NotificationScheduler
import com.dailytimer.app.ui.navigation.BottomNavBar
import com.dailytimer.app.ui.navigation.NavTab
import com.dailytimer.app.ui.theme.CoralPrimary
import com.dailytimer.app.ui.theme.EmeraldSuccess
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

enum class CreateMode {
    NEW_TASK,
    ADD_TO_EXISTING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    initialDate: String? = null,
    onTaskCreated: (date: String) -> Unit = {},
    selectedTab: NavTab = NavTab.CREATE,
    onTabSelected: (NavTab) -> Unit = {}
) {
    val context = LocalContext.current
    val dbHelper = remember { TaskDatabaseHelper.getInstance(context) }

    var selectedMode by remember { mutableStateOf(CreateMode.NEW_TASK) }
    var taskDate by remember(initialDate) {
        mutableStateOf(initialDate ?: TaskTime.formatDate(LocalDate.now()))
    }
    var startTime by remember { mutableStateOf("09:30 AM") }
    var endTime by remember { mutableStateOf("10:30 AM") }
    var taskName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Existing dates for Mode 2
    var existingDates by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
    var selectedExistingDate by remember { mutableStateOf("") }
    var existingTasksPreview by remember { mutableStateOf(listOf<Task>()) }

    fun refreshExistingDates() {
        val dates = dbHelper.getDistinctDatesWithCount()
        existingDates = dates
        if (dates.isNotEmpty() && (selectedExistingDate.isBlank() || dates.none { it.first == selectedExistingDate })) {
            selectedExistingDate = dates.first().first
            existingTasksPreview = dbHelper.getTasksForDate(dates.first().first)
        } else if (dates.isEmpty()) {
            selectedExistingDate = ""
            existingTasksPreview = emptyList()
        }
    }

    LaunchedEffect(selectedMode) {
        if (selectedMode == CreateMode.ADD_TO_EXISTING) {
            refreshExistingDates()
        }
    }

    // Date picker dialog keyed on taskDate
    val datePickerDialog = remember(taskDate) {
        val initial = TaskTime.parseDate(taskDate) ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                taskDate = TaskTime.formatDate(LocalDate.of(year, month + 1, day))
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        )
    }

    // Start time picker dialog
    val startTimePickerDialog = remember(startTime, endTime) {
        val currentStart = TaskTime.parseTime(startTime) ?: LocalTime.of(9, 30)
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val chosenTime = LocalTime.of(hourOfDay, minute)
                startTime = TaskTime.formatTime(chosenTime)
                errorMessage = null

                val end = TaskTime.parseTime(endTime)
                if (end != null && !end.isAfter(chosenTime)) {
                    val autoEnd = if (chosenTime.hour == 23) LocalTime.of(23, 59) else chosenTime.plusHours(1)
                    endTime = TaskTime.formatTime(autoEnd)
                }
            },
            currentStart.hour,
            currentStart.minute,
            false
        )
    }

    // End time picker dialog
    val endTimePickerDialog = remember(startTime, endTime) {
        val currentEnd = TaskTime.parseTime(endTime) ?: LocalTime.of(10, 30)
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val chosenTime = LocalTime.of(hourOfDay, minute)
                endTime = TaskTime.formatTime(chosenTime)

                val start = TaskTime.parseTime(startTime)
                if (start != null && !chosenTime.isAfter(start)) {
                    errorMessage = "End time cannot be before or equal to start time"
                } else {
                    errorMessage = null
                }
            },
            currentEnd.hour,
            currentEnd.minute,
            false
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
                text = "New Task",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Mode Selector: Segmented Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selectedMode == CreateMode.NEW_TASK) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedMode = CreateMode.NEW_TASK }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "New Schedule",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedMode == CreateMode.NEW_TASK) CoralPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selectedMode == CreateMode.ADD_TO_EXISTING) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedMode = CreateMode.ADD_TO_EXISTING }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add to Existing Date",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedMode == CreateMode.ADD_TO_EXISTING) CoralPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Mode 1: New Task Schedule
            if (selectedMode == CreateMode.NEW_TASK) {
                Text(
                    text = "SCHEDULE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { datePickerDialog.show() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Date",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = taskDate,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CoralPrimary
                                )
                                Icon(
                                    imageVector = Icons.Rounded.CalendarToday,
                                    contentDescription = "Pick Date",
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { startTimePickerDialog.show() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Start Time",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = startTime,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CoralPrimary
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = "Pick Start Time",
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { endTimePickerDialog.show() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "End Time",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = endTime,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CoralPrimary
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = "Pick End Time",
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Mode 2: Add to Existing Date
                Text(
                    text = "TARGET EXISTING DATE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                if (existingDates.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No existing dates yet. Use 'New Schedule' to create your first task.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = "Select Date to Append:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                existingDates.forEach { (dateStr, count) ->
                                    val isSelected = dateStr == selectedExistingDate
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) CoralPrimary.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
                                            )
                                            .clickable {
                                                selectedExistingDate = dateStr
                                                existingTasksPreview = dbHelper.getTasksForDate(dateStr)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = dateStr,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) CoralPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelected) CoralPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$count tasks",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (existingTasksPreview.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "EXISTING TIMELINE PREVIEW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                existingTasksPreview.forEach { existing ->
                                    val timeSpan = if (existing.endTime.isNotBlank()) "${existing.startTime} - ${existing.endTime}" else existing.startTime
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• ${existing.taskName}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = timeSpan,
                                            fontSize = 11.sp,
                                            color = CoralPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Start & End time pickers for append
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { startTimePickerDialog.show() }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "New Slot Start Time",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = startTime,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CoralPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = "Pick Start Time",
                                        tint = CoralPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { endTimePickerDialog.show() }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "New Slot End Time",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = endTime,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CoralPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = "Pick End Time",
                                        tint = CoralPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Details Section
            Text(
                text = "TASK DETAILS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = taskName,
                        onValueChange = {
                            taskName = it
                            errorMessage = null
                        },
                        label = { Text("Task Name") },
                        placeholder = { Text("e.g. Mathematics Revision") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        placeholder = { Text("Add specific notes, chapters, or goals...") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Message Alerts
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = Color(0xFFE53935), // Pure Red
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }

            AnimatedVisibility(visible = successMessage != null) {
                successMessage?.let {
                    Text(
                        text = it,
                        color = EmeraldSuccess, // Pure Green
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = {
                    if (taskName.isBlank()) {
                        errorMessage = "Please enter a task name"
                        return@Button
                    }

                    val finalDate = if (selectedMode == CreateMode.NEW_TASK) {
                        taskDate
                    } else {
                        if (selectedExistingDate.isBlank()) {
                            errorMessage = "Please choose an existing date"
                            return@Button
                        }
                        selectedExistingDate
                    }

                    val start = TaskTime.parseTime(startTime)
                    val end = TaskTime.parseTime(endTime)
                    if (start != null && end != null && !end.isAfter(start)) {
                        errorMessage = "End time must be after start time"
                        return@Button
                    }

                    val newTask = Task(
                        date = finalDate,
                        startTime = startTime,
                        endTime = endTime,
                        taskName = taskName.trim(),
                        description = description.trim(),
                        status = TaskStatus.UPCOMING
                    )

                    val rowId = dbHelper.insertTask(newTask)
                    if (rowId != -1L) {
                        val created = newTask.copy(id = rowId)
                        NotificationScheduler.scheduleTaskNotification(
                            context = context,
                            task = created,
                            latePolicy = LateReminderPolicy.FIRE_NOW
                        )
                        successMessage = "Task created successfully for $finalDate!"
                        taskName = ""
                        description = ""
                        if (selectedMode == CreateMode.ADD_TO_EXISTING) {
                            refreshExistingDates()
                        }
                        onTaskCreated(finalDate)
                    } else {
                        errorMessage = "Failed to save task into database"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
            ) {
                Text(
                    text = if (selectedMode == CreateMode.NEW_TASK) "Create Task" else "Append Task",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
