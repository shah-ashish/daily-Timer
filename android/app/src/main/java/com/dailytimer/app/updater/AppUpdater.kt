package com.dailytimer.app.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class VersionPayload(
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int = 26,
    val downloadUrl: String,
    val releaseNotes: String = ""
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val currentVersion: String) : UpdateStatus
    data class Available(val payload: VersionPayload) : UpdateStatus
    data class Downloading(
        val progress: Int,
        val bytesRead: Long,
        val totalBytes: Long
    ) : UpdateStatus
    data class ReadyToInstall(val apkFile: File) : UpdateStatus
    data class Failed(val reason: String) : UpdateStatus
}

/**
 * Idiomatic, standalone AppUpdater handling version check, byte-stream download with progress,
 * integrity verification, and FileProvider installation.
 */
class AppUpdater(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    val currentVersionCode: Long
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }

    val currentVersionName: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"

    /**
     * Checks the remote version endpoint asynchronously.
     */
    fun checkUpdateFlow(endpointUrl: String): Flow<UpdateStatus> = flow {
        emit(UpdateStatus.Checking)
        try {
            val request = Request.Builder().url(endpointUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(UpdateStatus.Failed("HTTP Error ${response.code}: Unable to reach update server"))
                return@flow
            }

            val bodyString = response.body?.string()
                ?: throw IllegalStateException("Empty response from update server")

            val payload = json.decodeFromString<VersionPayload>(bodyString)
            if (payload.versionCode > currentVersionCode) {
                emit(UpdateStatus.Available(payload))
            } else {
                emit(UpdateStatus.UpToDate(currentVersionName))
            }
        } catch (e: Exception) {
            emit(UpdateStatus.Failed(e.localizedMessage ?: "Unknown check update error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Downloads the APK file while emitting reactive byte-stream progress.
     * Uses a temporary .tmp file to prevent incomplete / corrupted APKs from being installed.
     */
    fun downloadApkFlow(downloadUrl: String): Flow<UpdateStatus> = flow {
        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(UpdateStatus.Failed("Failed to download APK: HTTP ${response.code}"))
                return@flow
            }

            val body = response.body ?: throw IllegalStateException("Empty body response")
            val totalBytes = body.contentLength()

            // Save to external files dir if available (accessible to Xiaomi/Samsung PackageInstaller)
            val baseDir = context.getExternalFilesDir(null) ?: context.externalCacheDir ?: context.cacheDir
            val destinationDir = File(baseDir, "updates").apply { mkdirs() }
            val apkFile = File(destinationDir, "DailyTimer-update.apk")
            val tmpFile = File(destinationDir, "DailyTimer-update.apk.tmp")

            // Remove any leftover corrupted files
            if (tmpFile.exists()) tmpFile.delete()
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { inputStream ->
                FileOutputStream(tmpFile).use { outputStream ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Long = 0
                    var read: Int
                    var lastPercent = -1

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesRead += read

                        val progress = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                        if (progress != lastPercent) {
                            lastPercent = progress
                            emit(UpdateStatus.Downloading(progress, bytesRead, totalBytes))
                        }
                    }
                    outputStream.flush()
                }
            }

            // Verify download integrity
            if (!tmpFile.exists() || tmpFile.length() < 1_000_000L) {
                emit(UpdateStatus.Failed("Download incomplete or corrupt. Please try again."))
                return@flow
            }

            if (totalBytes > 0 && tmpFile.length() != totalBytes) {
                emit(UpdateStatus.Failed("Download size mismatch (${tmpFile.length()} vs $totalBytes). Please retry."))
                return@flow
            }

            // Atomically rename to final APK
            if (apkFile.exists()) apkFile.delete()
            val success = tmpFile.renameTo(apkFile)
            val finalApk = if (success) apkFile else tmpFile

            // Make world-readable for PackageInstaller
            finalApk.setReadable(true, false)

            emit(UpdateStatus.ReadyToInstall(finalApk))
        } catch (e: Exception) {
            emit(UpdateStatus.Failed(e.localizedMessage ?: "APK Download interrupted"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Triggers the Android package installer via FileProvider with explicit URI permission grants.
     */
    suspend fun installApk(apkFile: File): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            apkFile.setReadable(true, false)

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            // Grant URI permission to known package installer packages
            val knownInstallers = listOf(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                "com.miui.packageinstaller",
                "com.samsung.android.packageinstaller",
                "com.coloros.packageinstaller",
                "com.vivo.packageinstaller"
            )
            for (pkg in knownInstallers) {
                try {
                    context.grantUriPermission(pkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            // Explicitly grant URI permission to any resolved PackageInstaller activity
            val resolvedActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    installIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(
                    installIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
            }
            for (resolveInfo in resolvedActivities) {
                val pkgName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(pkgName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        }
    }
}
