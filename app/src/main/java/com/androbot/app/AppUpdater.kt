package com.androbot.app

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

interface Updater {
    fun checkForUpdate(onResult: (AppUpdater.CheckResult) -> Unit)
    fun downloadAndInstall(metadata: AppUpdater.UpdateMetadata, onStatus: (String) -> Unit)
    fun cleanup()
}

class AppUpdater(private val activity: Activity) : Updater {

    sealed interface CheckResult {
        data class UpdateAvailable(val metadata: UpdateMetadata) : CheckResult
        data object UpToDate : CheckResult
        data class Error(val message: String) : CheckResult
    }

    data class UpdateMetadata(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val sha256: String,
        val minSupportedVersionCode: Long
    )

    private val context = activity.applicationContext
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var activeDownloadId: Long? = null
    private var downloadReceiver: BroadcastReceiver? = null

    override fun checkForUpdate(onResult: (CheckResult) -> Unit) {
        Thread {
            val result = try {
                val metadata = fetchMetadata()
                val current = currentAppVersion()
                when {
                    !isRemoteUpdateAvailable(metadata, current) -> CheckResult.UpToDate
                    current.versionCode < metadata.minSupportedVersionCode ->
                        CheckResult.Error(
                            "Current app version is too old for direct upgrade path."
                        )

                    else -> CheckResult.UpdateAvailable(metadata)
                }
            } catch (e: Exception) {
                Log.i(TAG, "Update check failed: ${e.javaClass.simpleName}")
                CheckResult.Error("Update check failed")
            }
            activity.runOnUiThread { onResult(result) }
        }.start()
    }

    private data class InstalledVersion(
        val versionCode: Long,
        val versionName: String
    )

    private fun isRemoteUpdateAvailable(metadata: UpdateMetadata, current: InstalledVersion): Boolean {
        return when {
            metadata.versionCode > current.versionCode -> true
            metadata.versionCode < current.versionCode -> false
            else -> isVersionNameNewer(metadata.versionName, current.versionName)
        }
    }

    override fun downloadAndInstall(metadata: UpdateMetadata, onStatus: (String) -> Unit) {
        if (activeDownloadId != null) {
            onStatus("Update download is already in progress")
            return
        }

        val request = DownloadManager.Request(Uri.parse(metadata.apkUrl))
            .setTitle("Androbot update ${metadata.versionName}")
            .setDescription("Downloading update package")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "androbot-update-${metadata.versionName}.apk"
            )

        val id = downloadManager.enqueue(request)
        activeDownloadId = id
        onStatus("Downloading update...")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != id) return

                cleanupReceiver()
                activeDownloadId = null

                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)
                cursor.use { c ->
                    if (!c.moveToFirst()) {
                        onStatus("Update download result missing")
                        return
                    }
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        onStatus(downloadStatusMessage(status, reason))
                        return
                    }
                }

                val uri = downloadManager.getUriForDownloadedFile(id)
                if (uri == null) {
                    onStatus("Downloaded update file not found")
                    return
                }

                if (!verifySha256(uri, metadata.sha256)) {
                    onStatus("Update verification failed (checksum mismatch)")
                    return
                }

                val current = currentAppVersion()
                if (!isRemoteUpdateAvailable(metadata, current)) {
                    onStatus(
                        "Downloaded update is not newer than installed app " +
                            "(current ${current.versionName}/${current.versionCode}, " +
                            "download ${metadata.versionName}/${metadata.versionCode})"
                    )
                    return
                }

                if (!canRequestPackageInstalls()) {
                    onStatus(
                        "Install blocked: allow \"Install unknown apps\" for Androbot, then retry"
                    )
                    openUnknownSourcesSettings()
                    return
                }

                if (installApk(uri)) {
                    onStatus("Installer opened. Tap Install to complete update.")
                } else {
                    onStatus(
                        "Unable to open Android installer. " +
                            "Try opening the APK manually from Downloads."
                    )
                }
            }
        }

        downloadReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun cleanup() {
        cleanupReceiver()
        activeDownloadId = null
    }

    private fun cleanupReceiver() {
        val receiver = downloadReceiver ?: return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
            // no-op
        } finally {
            downloadReceiver = null
        }
    }

    private fun installApk(uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "No installer activity available")
            false
        } catch (e: Exception) {
            Log.i(TAG, "Installer launch failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun verifySha256(uri: Uri, expected: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) return false
                updateDigest(stream, digest)
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.equals(expected.trim().lowercase(), ignoreCase = true)
        } catch (e: Exception) {
            Log.i(TAG, "Checksum verification failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun updateDigest(stream: InputStream, digest: MessageDigest) {
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }

    private fun openUnknownSourcesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // no-op
        }
    }

    private fun canRequestPackageInstalls(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    private fun downloadStatusMessage(status: Int, reason: Int): String {
        return when (status) {
            DownloadManager.STATUS_PAUSED ->
                "Update download paused (${downloadReasonLabel(reason)})"
            DownloadManager.STATUS_PENDING ->
                "Update download pending"
            DownloadManager.STATUS_RUNNING ->
                "Update download still running"
            DownloadManager.STATUS_FAILED ->
                "Update download failed (${downloadReasonLabel(reason)})"
            else -> "Update download failed (status=$status reason=$reason)"
        }
    }

    private fun downloadReasonLabel(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "cannot resume"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file already exists"
            DownloadManager.ERROR_FILE_ERROR -> "file error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "network data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "insufficient space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server response error"
            DownloadManager.ERROR_UNKNOWN -> "unknown error"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "waiting for Wi-Fi"
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "waiting for network"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "waiting to retry"
            DownloadManager.PAUSED_UNKNOWN -> "paused"
            else -> "reason=$reason"
        }
    }

    private fun fetchMetadata(): UpdateMetadata {
        val conn = (URL(METADATA_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        conn.inputStream.bufferedReader().use { reader ->
            val json = JSONObject(reader.readText())
            return UpdateMetadata(
                versionCode = json.getLong("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                sha256 = json.getString("sha256"),
                minSupportedVersionCode = json.optLong("minSupportedVersionCode", 1L)
            )
        }
    }

    private fun currentAppVersion(): InstalledVersion {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val versionName = info.versionName ?: ""
        return InstalledVersion(versionCode = versionCode, versionName = versionName)
    }

    companion object {
        private const val TAG = "AppUpdater"
        private const val METADATA_URL =
            "https://github.com/mike-dubman/androbot/releases/latest/download/androbot-update.json"

        internal fun isVersionNameNewer(remote: String, local: String): Boolean {
            val remoteParts = parseVersionParts(remote) ?: return false
            val localParts = parseVersionParts(local) ?: return false
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val remoteValue = remoteParts.getOrElse(i) { 0 }
                val localValue = localParts.getOrElse(i) { 0 }
                if (remoteValue > localValue) return true
                if (remoteValue < localValue) return false
            }
            return false
        }

        private fun parseVersionParts(version: String): List<Int>? {
            val trimmed = version.trim()
            if (!VERSION_NAME_REGEX.matches(trimmed)) return null
            return trimmed.split('.').map { it.toInt() }
        }

        private val VERSION_NAME_REGEX = Regex("^\\d+(?:\\.\\d+)*$")
    }
}
