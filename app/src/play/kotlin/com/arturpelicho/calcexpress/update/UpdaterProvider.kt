package com.arturpelicho.calcexpress.update

import android.app.Activity
import android.content.Context
import java.io.File

/** Google Play build: updates come from Play, so there is no self-updater. */
object UpdaterProvider {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): Updater = object : Updater {
        override val supported = false
        override suspend fun check(): UpdateInfo? = null
        override suspend fun download(info: UpdateInfo, progress: (Int) -> Unit): File = error("unsupported")
        override fun install(activity: Activity, apk: File) = error("unsupported")
    }
}
