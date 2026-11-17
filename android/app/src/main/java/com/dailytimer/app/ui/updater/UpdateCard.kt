package com.dailytimer.app.ui.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailytimer.app.R
import com.dailytimer.app.ui.theme.CoralGradientEnd
import com.dailytimer.app.ui.theme.CoralPrimary
import com.dailytimer.app.ui.theme.EmeraldSuccess
import com.dailytimer.app.updater.AppUpdater
import com.dailytimer.app.updater.UpdateStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Idiomatic, standalone UpdateCard component.
 * Encapsulates its own state, button transitions, progress bar, and installation trigger.
 */
@Composable
fun UpdateCard(
    modifier: Modifier = Modifier,
    endpointUrl: String = "https://raw.githubusercontent.com/shah-ashish/daily-Timer/main/version.json"
) {
    val context = LocalContext.current
    val appUpdater = remember { AppUpdater(context) }
    val scope = rememberCoroutineScope()

    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    var activeDownloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }

    val appName = "Daily Timer"
    val versionName = appUpdater.currentVersionName

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon, App Details, Action Button
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
                        contentDescription = "Daily Timer Logo",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Column {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Dynamic Action Button
                when (val status = updateStatus) {
                    is UpdateStatus.Idle, is UpdateStatus.UpToDate -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    appUpdater.checkUpdateFlow(endpointUrl).collect {
                                        updateStatus = it
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (status is UpdateStatus.UpToDate) "Up to date" else "Check for Update",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is UpdateStatus.Checking -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = CoralPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Checking...", fontSize = 12.sp)
                        }
                    }

                    is UpdateStatus.Available -> {
                        Button(
                            onClick = {
                                activeDownloadJob?.cancel()
                                activeDownloadJob = scope.launch {
                                    appUpdater.downloadApkFlow(status.payload.downloadUrl).collect { downloadState ->
                                        updateStatus = downloadState
                                        if (downloadState is UpdateStatus.ReadyToInstall) {
                                            downloadedApk = downloadState.apkFile
                                        }
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CoralPrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Download v${status.payload.versionName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is UpdateStatus.Downloading -> {
                        Text(
                            text = "Downloading...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CoralPrimary
                        )
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        Button(
                            onClick = {
                                downloadedApk?.let { apk ->
                                    scope.launch {
                                        appUpdater.installApk(apk)
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldSuccess,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Install Now",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is UpdateStatus.Failed -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    appUpdater.checkUpdateFlow(endpointUrl).collect {
                                        updateStatus = it
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(text = "Retry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Optional Release Notes
            AnimatedVisibility(visible = updateStatus is UpdateStatus.Available) {
                val available = updateStatus as? UpdateStatus.Available
                available?.payload?.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CoralPrimary.copy(alpha = 0.08f))
                            .border(1.dp, CoralPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "What's New: $notes",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // Real-time Progress Bar
            AnimatedVisibility(visible = updateStatus is UpdateStatus.Downloading) {
                val downloading = updateStatus as? UpdateStatus.Downloading
                downloading?.let {
                    val progressFloat = it.progress / 100f
                    val mbRead = String.format("%.1f", it.bytesRead / (1024f * 1024f))
                    val mbTotal = String.format("%.1f", it.totalBytes / (1024f * 1024f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading update...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "${it.progress}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { progressFloat },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CoralPrimary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "$mbRead MB / $mbTotal MB",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            // Error display
            AnimatedVisibility(visible = updateStatus is UpdateStatus.Failed) {
                val failed = updateStatus as? UpdateStatus.Failed
                failed?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it.reason,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
