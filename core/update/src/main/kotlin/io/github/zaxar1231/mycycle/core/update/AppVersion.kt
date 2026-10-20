package io.github.zaxar1231.mycycle.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** versionName of the installed app; the library module has no access to the app's BuildConfig. */
fun Context.appVersionName(): String {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return info.versionName ?: "0"
}
