package com.dailytimer.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dailytimer.app.updater.AppUpdater
import com.dailytimer.app.updater.UpdateStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val appUpdater by lazy { AppUpdater(applicationContext) }
    private var downloadJob: Job? = null
    private var downloadedApkFile: File? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(UpdaterNativeBridge(), "AndroidUpdater")
            loadUrl("file:///android_asset/web/settings.html")
        }

        setContentView(webView)
    }

    private fun postStatusToWeb(statusJson: JSONObject) {
        runOnUiThread {
            val script = "window.dispatchEvent(new CustomEvent('onUpdateStatus', { detail: $statusJson }));"
            webView.evaluateJavascript(script, null)
        }
    }

    inner class UpdaterNativeBridge {

        @JavascriptInterface
        fun getAppInfo(): String {
            val info = JSONObject().apply {
                put("appName", getString(R.string.app_name))
                put("versionName", appUpdater.currentVersionName)
                put("versionCode", appUpdater.currentVersionCode)
            }
            return info.toString()
        }

        @JavascriptInterface
        fun checkUpdate(endpointUrl: String) {
            lifecycleScope.launch {
                appUpdater.checkUpdateFlow(endpointUrl).collect { status ->
                    val json = JSONObject()
                    when (status) {
                        is UpdateStatus.Checking -> {
                            json.put("state", "CHECKING")
                        }
                        is UpdateStatus.UpToDate -> {
                            json.put("state", "UP_TO_DATE")
                            json.put("versionName", status.currentVersion)
                        }
                        is UpdateStatus.Available -> {
                            json.put("state", "AVAILABLE")
                            json.put("versionName", status.payload.versionName)
                            json.put("versionCode", status.payload.versionCode)
                            json.put("downloadUrl", status.payload.downloadUrl)
                            json.put("releaseNotes", status.payload.releaseNotes)
                        }
                        is UpdateStatus.Failed -> {
                            json.put("state", "ERROR")
                            json.put("message", status.reason)
                        }
                        else -> Unit
                    }
                    postStatusToWeb(json)
                }
            }
        }

        @JavascriptInterface
        fun downloadUpdate(downloadUrl: String) {
            downloadJob?.cancel()
            downloadJob = lifecycleScope.launch {
                appUpdater.downloadApkFlow(downloadUrl).collect { status ->
                    val json = JSONObject()
                    when (status) {
                        is UpdateStatus.Downloading -> {
                            json.put("state", "DOWNLOADING")
                            json.put("progress", status.progress)
                            json.put("bytesRead", status.bytesRead)
                            json.put("totalBytes", status.totalBytes)
                        }
                        is UpdateStatus.ReadyToInstall -> {
                            downloadedApkFile = status.apkFile
                            json.put("state", "READY_TO_INSTALL")
                            json.put("filePath", status.apkFile.absolutePath)
                        }
                        is UpdateStatus.Failed -> {
                            json.put("state", "ERROR")
                            json.put("message", status.reason)
                        }
                        else -> Unit
                    }
                    postStatusToWeb(json)
                }
            }
        }

        @JavascriptInterface
        fun installUpdate() {
            downloadedApkFile?.let { apk ->
                lifecycleScope.launch {
                    val result = appUpdater.installApk(apk)
                    if (result.isFailure) {
                        val json = JSONObject().apply {
                            put("state", "ERROR")
                            put("message", result.exceptionOrNull()?.localizedMessage ?: "Failed to launch installer")
                        }
                        postStatusToWeb(json)
                    }
                }
            }
        }
    }
}
