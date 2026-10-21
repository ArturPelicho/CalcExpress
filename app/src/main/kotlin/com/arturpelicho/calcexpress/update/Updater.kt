package com.arturpelicho.calcexpress.update

import android.app.Activity
import java.io.File

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String,
    val sizeBytes: Long,
)

enum class InstallResult { STARTED, NEEDS_PERMISSION }

/** Self-update from GitHub Releases. Only the "github" flavour has a real implementation. */
interface Updater {
    val supported: Boolean

    /** Returns the newer release, or null when up to date. Throws on network errors. */
    suspend fun check(): UpdateInfo?

    suspend fun download(info: UpdateInfo, progress: (Int) -> Unit): File

    fun install(activity: Activity, apk: File): InstallResult
}

sealed class UpdateStatus {
    data object Idle : UpdateStatus()
    data object Checking : UpdateStatus()
    data object UpToDate : UpdateStatus()
    data class Available(val info: UpdateInfo) : UpdateStatus()
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateStatus()
    data class NeedsPermission(val info: UpdateInfo) : UpdateStatus()
    data object Error : UpdateStatus()
}
