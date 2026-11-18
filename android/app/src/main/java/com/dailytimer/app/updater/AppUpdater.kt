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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable
data class VersionPayload(
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int = 26,
    val downloadUrl: String,
    val sha256: String? = null,
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
 * cryptographic hash verification, and FileProvider installation.
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
        emit(fetchStatus(endpointUrl))
    }.flowOn(Dispatchers.IO)

    private fun fetchStatus(endpointUrl: String): UpdateStatus = try {
        httpClient.newCall(Request.Builder().url(endpointUrl).build()).execute().use { response ->
            if (!response.isSuccessful) {
                return@use UpdateStatus.Failed("HTTP Error ${response.code}: Unable to reach update server")
            }

            val bodyString = response.body?.string()
                ?: return@use UpdateStatus.Failed("Empty response from update server")

            val payload = json.decodeFromString<VersionPayload>(bodyString)
            when {
                payload.versionCode <= currentVersionCode -> UpdateStatus.UpToDate(currentVersionName)
                payload.minSdk > Build.VERSION.SDK_INT -> UpdateStatus.Failed("Update requires Android API ${payload.minSdk}")
                else -> UpdateStatus.Available(payload)
            }
        }
    } catch (e: IOException) {
        UpdateStatus.Failed(e.localizedMessage ?: "Network error while checking for updates")
    } catch (e: IllegalArgumentException) {
        UpdateStatus.Failed("Malformed update manifest")
    } catch (e: Exception) {
        UpdateStatus.Failed(e.localizedMessage ?: "Unknown check update error")
    }

    /**
     * Downloads the APK file while emitting reactive byte-stream progress.
     * Computes running SHA-256 and verifies checksum before marking ready to install.
     * Uses a temporary .tmp file to prevent incomplete / corrupted APKs from being installed.
     */
    fun downloadApkFlow(payload: VersionPayload): Flow<UpdateStatus> = flow {
        val url = payload.downloadUrl.toHttpUrlOrNull()
        val expectedSha = payload.sha256?.lowercase()
        if (url == null || !url.isHttps) {
            emit(UpdateStatus.Failed("Update URL must use HTTPS"))
            return@flow
        }
        if (expectedSha == null || !SHA256_HEX.matches(expectedSha)) {
            emit(UpdateStatus.Failed("Update manifest has no valid SHA-256 checksum"))
            return@flow
        }

        val baseDir = context.getExternalFilesDir(null) ?: context.externalCacheDir ?: context.cacheDir
        val destinationDir = File(baseDir, "updates").apply { mkdirs() }
        val apkFile = File(destinationDir, "DailyTimer-update.apk")
        val tmpFile = File(destinationDir, "DailyTimer-update.apk.tmp")

        // Clean up previous artifacts
        if (tmpFile.exists()) tmpFile.delete()
        if (apkFile.exists()) apkFile.delete()

        try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    emit(UpdateStatus.Failed("Failed to download APK: HTTP ${response.code}"))
                    return@flow
                }
                val totalBytes = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")

                body.byteStream().use { inputStream ->
                    FileOutputStream(tmpFile).use { outputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Long = 0
                        var read: Int
                        var lastPercent = -1

                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
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

                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }

                when {
                    !tmpFile.exists() || tmpFile.length() < 1_000_000L ->
                        emit(UpdateStatus.Failed("Download incomplete or corrupt. Please try again."))
                    totalBytes > 0 && tmpFile.length() != totalBytes ->
                        emit(UpdateStatus.Failed("Download size mismatch (${tmpFile.length()} vs $totalBytes). Please retry."))
                    actualSha != expectedSha ->
                        emit(UpdateStatus.Failed("Checksum mismatch: the file is corrupted or was tampered with"))
                    !tmpFile.renameTo(apkFile) ->
                        emit(UpdateStatus.Failed("Could not finalize the downloaded APK"))
                    else -> {
                        apkFile.setReadable(true, false)
                        emit(UpdateStatus.ReadyToInstall(apkFile))
                    }
                }
                if (tmpFile.exists()) tmpFile.delete()
            }
        } catch (e: IOException) {
            if (tmpFile.exists()) tmpFile.delete()
            emit(UpdateStatus.Failed(e.localizedMessage ?: "APK download interrupted"))
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

    companion object {
        private const val BUFFER_SIZE = 16 * 1024
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}
