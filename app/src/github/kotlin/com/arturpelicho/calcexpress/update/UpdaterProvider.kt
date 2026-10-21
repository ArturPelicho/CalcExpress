package com.arturpelicho.calcexpress.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.arturpelicho.calcexpress.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdaterProvider {
    fun create(context: Context): Updater = GitHubUpdater(context.applicationContext)
}

/**
 * Checks https://api.github.com/repos/<repo>/releases/latest. Release tags are
 * "v<versionName>" and the last number of the version name is the versionCode
 * (e.g. v0.1.42 → 42), so comparing is a single integer comparison.
 */
private class GitHubUpdater(private val context: Context) : Updater {
    override val supported = true

    override suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "CalcExpress/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode == 404) return@withContext null // no release yet
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name").removePrefix("v")
            val code = versionCodeOf(tag) ?: return@withContext null
            if (code <= BuildConfig.VERSION_CODE) return@withContext null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.getString("name")
                if (name.endsWith(".apk")) {
                    return@withContext UpdateInfo(
                        versionName = tag,
                        versionCode = code,
                        apkUrl = a.getString("browser_download_url"),
                        notes = json.optString("body", ""),
                        sizeBytes = a.optLong("size", 0),
                    )
                }
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun versionCodeOf(versionName: String): Int? =
        versionName.substringBefore('-').split('.').lastOrNull()?.toIntOrNull()

    private fun updatesDir(): File {
        // The system installer of Android 6 cannot read content:// URIs, so use a file:// in external cache there.
        val base = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) context.externalCacheDir ?: context.cacheDir else context.cacheDir
        return File(base, "updates").apply { mkdirs() }
    }

    override suspend fun download(info: UpdateInfo, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = updatesDir()
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "CalcExpress-${info.versionName}.apk")
        var url = URL(info.apkUrl)
        var conn: HttpURLConnection
        // Follow redirects manually (GitHub → objects.githubusercontent.com).
        var hops = 0
        while (true) {
            conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "CalcExpress/${BuildConfig.VERSION_NAME}")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) {
                url = URL(url, conn.getHeaderField("Location"))
                conn.disconnect()
                hops++
                continue
            }
            if (code != 200) error("HTTP $code")
            break
        }
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; withContext(Dispatchers.Main) { progress(pct) } }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        target
    }

    override fun install(activity: Activity, apk: File): InstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
            )
            return InstallResult.NEEDS_PERMISSION
        }
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        } else {
            Uri.fromFile(apk)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        return InstallResult.STARTED
    }
}
