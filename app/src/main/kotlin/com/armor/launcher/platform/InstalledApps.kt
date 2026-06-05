package com.armor.launcher.platform

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

internal data class InstalledApp(
    val pkg: String,
    val label: String,
    val icon: Drawable?,
)

internal object InstalledApps {
    /** Every launchable third-party + system app, minus Armor itself. */
    fun list(c: Context): List<InstalledApp> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != c.packageName }
            .mapNotNull { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    InstalledApp(
                        pkg = pkg,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
                    )
                } catch (_: Exception) { null }
            }
    }

    fun launch(c: Context, pkg: String): Boolean {
        val intent = c.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { c.startActivity(intent); true } catch (_: Exception) { false }
    }
}
