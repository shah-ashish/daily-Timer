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
import com.dailytimer.app.data.model.TaskStatus
import com.dailytimer.app.domain.TaskPhase
import com.dailytimer.app.domain.TaskPhaseResolver
import com.dailytimer.app.domain.TaskTime
import com.dailytimer.app.ui.navigation.BottomNavBar
import com.dailytimer.app.ui.navigation.NavTab
import com.dailytimer.app.ui.theme.CoralPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TaskUi(val task: Task, val phase: TaskPhase)

@Composable
private fun rememberNowMillis(refreshIntervalMs: Long = 30_000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(refreshIntervalMs)
            value = System.currentTimeMillis()
        }
    }

@Composable
fun HomeScreen(
    selectedTab: NavTab = NavTab.HOME,
    onTabSelected: (NavTab) -> Unit = {},
    onCreateTaskClick: () -> Unit = {},
    logoSize: Dp = 54.dp
) {
    val context = LocalContext.current
    val dbHelper = remember { TaskDatabaseHelper.getInstance(context) }

    val nowMillis by rememberNowMillis()
    val today = TaskTime.localDateOf(nowMillis)
    val todayDb = TaskTime.formatDate(today)
    val todayDisplay = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    }

    var tasksToday by remember { mutableStateOf<List<Task>?>(null) } // null = loading
    LaunchedEffect(todayDb) {
        dbHelper.changes
            .onStart { emit(Unit) }
            .collectLatest { tasksToday = withContext(Dispatchers.IO) { dbHelper.getTasksForDate(todayDb) } }
    }

    val loadedTasks = tasksToday
    val pending = remember(loadedTasks, nowMillis) {
        loadedTasks.orEmpty()
            .map { TaskUi(it, TaskPhaseResolver.resolve(it, nowMillis)) }
            .filter { it.phase.isPending }
    }

    val focus = remember(pending) {
        pending.firstOrNull { it.phase == TaskPhase.IN_PROGRESS } ?: pending.firstOrNull()
    }
    val others = remember(pending, focus) {
        pending.filter { it.task.id != focus?.task?.id }
    }
    val completedToday = remember(loadedTasks) {
        loadedTasks.orEmpty().count { it.status == TaskStatus.COMPLETED }
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
                    text = todayDisplay,
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

            if (loadedTasks != null) {
                if (focus != null) {
                    val currentFocusTask = focus.task
                    val isInProgress = focus.phase == TaskPhase.IN_PROGRESS

                    // Hero Task Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
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
                                    text = if (isInProgress) "CURRENT FOCUS (LIVE)" else "NEXT FOCUS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = if (isInProgress) CoralPrimary else CoralPrimary.copy(alpha = 0.8f)
                                )

                                // Status Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isInProgress) CoralPrimary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
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
                                            text = if (isInProgress) "IN PROGRESS" else "UPCOMING",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isInProgress) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
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
                                text = if (completedToday > 0) "All Caught Up for Today!" else "Nothing Scheduled Today",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (completedToday > 0) "You finished $completedToday task(s). Nice work." else "Tap Create to plan your day.",
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
                if (others.isNotEmpty()) {
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
                            others.forEachIndexed { index, item ->
                                val task = item.task
                                val isItemInProgress = item.phase == TaskPhase.IN_PROGRESS

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
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                            ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Schedule,
                                                contentDescription = "Time",
                                                tint = if (isItemInProgress) CoralPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                                                color = MaterialTheme.colorScheme.onSurface
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
                                                if (isItemInProgress) CoralPrimary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isItemInProgress) "IN PROGRESS" else "UPCOMING",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isItemInProgress) CoralPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                if (index < others.lastIndex) {
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
}
