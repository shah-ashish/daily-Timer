package com.dailytimer.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailytimer.app.R
import com.dailytimer.app.data.db.TaskDatabaseHelper
import com.dailytimer.app.data.model.Task
import com.dailytimer.app.ui.navigation.BottomNavBar
import com.dailytimer.app.ui.navigation.NavTab
import com.dailytimer.app.ui.theme.CoralPrimary
import com.dailytimer.app.ui.theme.EmeraldSuccess
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    selectedTab: NavTab = NavTab.HOME,
    onTabSelected: (NavTab) -> Unit = {},
    onCreateTaskClick: () -> Unit = {},
    logoSize: Dp = 54.dp
) {
    val context = LocalContext.current
    val dbHelper = remember { TaskDatabaseHelper(context) }
    val calendar = remember { Calendar.getInstance() }
    val dateFormatDb = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayDateFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }

    val todayDateStr = remember { dateFormatDb.format(calendar.time) }
    val todayDisplayStr = remember { displayDateFormat.format(calendar.time) }

    var todayTasks by remember { mutableStateOf(listOf<Task>()) }

    LaunchedEffect(Unit) {
        todayTasks = dbHelper.getTasksForDate(todayDateStr)
    }

    fun parseTimeToMillis(dateStr: String, timeStr: String): Long {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US),
            SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        )
        for (f in formats) {
            try {
                f.isLenient = true
                val parsed = f.parse("$dateStr ${timeStr.trim().replace("\u00A0", " ")}")
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return 0L
    }

    fun getRuntimeStatus(task: Task): String {
        if (task.status == "COMPLETED") return "COMPLETED"
        val now = System.currentTimeMillis()
        val startMillis = parseTimeToMillis(task.date, task.startTime)
        val endMillis = if (task.endTime.isNotBlank()) parseTimeToMillis(task.date, task.endTime) else 0L

        if (startMillis == 0L) return task.status

        return when {
            endMillis > startMillis && now in startMillis..endMillis -> "IN_PROGRESS"
            endMillis > 0L && now > endMillis -> "IN_PROGRESS" // Ongoing until user completes
            now >= startMillis && endMillis == 0L -> "IN_PROGRESS"
            else -> "UPCOMING"
        }
    }

    // Determine the hero Focus task:
    // Priority 1: Task currently IN_PROGRESS
    // Priority 2: Next UPCOMING task
    // Priority 3: Fallback to first task if all completed
    val currentFocusTask = remember(todayTasks) {
        todayTasks.firstOrNull { getRuntimeStatus(it) == "IN_PROGRESS" }
            ?: todayTasks.firstOrNull { getRuntimeStatus(it) == "UPCOMING" }
            ?: todayTasks.firstOrNull()
    }

    val otherTasks = remember(todayTasks, currentFocusTask) {
        todayTasks.filter { it != currentFocusTask }
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
            // Header Row: App Logo on left, Today Date/Day on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(logoSize)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Text(
                        text = "Daily Timer",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = todayDisplayStr,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Today's Focus
            Text(
                text = "TODAY'S FOCUS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            if (currentFocusTask != null) {
                val heroStatus = getRuntimeStatus(currentFocusTask)
                val isInProgress = heroStatus == "IN_PROGRESS"
                val isCompleted = heroStatus == "COMPLETED"

                // Hero Task Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isInProgress) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isInProgress) 3.dp else 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isInProgress) "CURRENT FOCUS (LIVE)" else if (isCompleted) "FOCUS COMPLETED" else "NEXT FOCUS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (isInProgress) CoralPrimary else if (isCompleted) EmeraldSuccess else CoralPrimary.copy(alpha = 0.8f)
                            )

                            // Status Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isInProgress -> CoralPrimary
                                            isCompleted -> EmeraldSuccess.copy(alpha = 0.15f)
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isInProgress) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Active",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = when {
                                            isInProgress -> "IN PROGRESS"
                                            isCompleted -> "COMPLETED"
                                            else -> "UPCOMING"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isInProgress -> Color.White
                                            isCompleted -> EmeraldSuccess
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = currentFocusTask.taskName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Time Range display
                        val timeSpan = if (currentFocusTask.endTime.isNotBlank()) {
                            "${currentFocusTask.startTime}  –  ${currentFocusTask.endTime}"
                        } else {
                            currentFocusTask.startTime
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = "Time",
                                tint = if (isInProgress) CoralPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = timeSpan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isInProgress) CoralPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        if (currentFocusTask.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = currentFocusTask.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                // Empty state card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CoralPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "No Tasks",
                                tint = CoralPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "All Caught Up for Today!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No scheduled tasks. Tap Create to plan your day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onCreateTaskClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                        ) {
                            Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Create Task", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Section: Next Up Today
            if (otherTasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "MORE TASKS TODAY",
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
                        otherTasks.forEachIndexed { index, task ->
                            val taskRuntimeStatus = getRuntimeStatus(task)
                            val isItemInProgress = taskRuntimeStatus == "IN_PROGRESS"
                            val isItemCompleted = taskRuntimeStatus == "COMPLETED"

                            val itemTimeSpan = if (task.endTime.isNotBlank()) {
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
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isItemInProgress) CoralPrimary.copy(alpha = 0.15f)
                                                else if (isItemCompleted) EmeraldSuccess.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isItemCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                                            contentDescription = "Time",
                                            tint = if (isItemInProgress) CoralPrimary else if (isItemCompleted) EmeraldSuccess else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = task.taskName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp
                                            ),
                                            color = if (isItemCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = itemTimeSpan,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                isItemInProgress -> CoralPrimary.copy(alpha = 0.15f)
                                                isItemCompleted -> EmeraldSuccess.copy(alpha = 0.15f)
                                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when {
                                            isItemInProgress -> "IN PROGRESS"
                                            isItemCompleted -> "COMPLETED"
                                            else -> "UPCOMING"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isItemInProgress -> CoralPrimary
                                            isItemCompleted -> EmeraldSuccess
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        }
                                    )
                                }
                            }

                            if (index < otherTasks.lastIndex) {
                                Divider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
