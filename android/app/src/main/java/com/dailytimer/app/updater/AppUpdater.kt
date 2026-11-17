package com.dailytimer.app.updater

import android.content.Context
import android.content.Intent
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
 * and FileProvider installation.
 */
class AppUpdater(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
            val destinationDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(destinationDir, "DailyTimer-update.apk")

            body.byteStream().use { inputStream ->
                FileOutputStream(apkFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Long = 0
                    var read: Int
                    var lastPercent = -1

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesRead += read

                        val progress = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt() else 0
                        if (progress != lastPercent) {
                            lastPercent = progress
                            emit(UpdateStatus.Downloading(progress, bytesRead, totalBytes))
                        }
                    }
                    outputStream.flush()
                }
            }

            emit(UpdateStatus.ReadyToInstall(apkFile))
        } catch (e: Exception) {
            emit(UpdateStatus.Failed(e.localizedMessage ?: "APK Download interrupted"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Triggers the Android package installer via FileProvider.
     */
    suspend fun installApk(apkFile: File): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
        }
    }
}
