package com.armor.launcher.domain

import android.content.Context
import androidx.core.content.edit
import com.armor.launcher.platform.Prefs

/**
 * Per-package last-launched timestamp. Used to sort the disguise menu so the
 * apps the user actually opens float to the top.
 *
 * Stored as a flat key/value SharedPreferences file: pkg -> epoch millis.
 * No expiry; entries for uninstalled packages are pruned implicitly when
 * the consumer joins against InstalledApps.list().
 */
class MruTracker(c: Context) {

    private val prefs = c.getSharedPreferences(Prefs.FILE_MRU, Context.MODE_PRIVATE)

    fun recordLaunch(pkg: String) {
        prefs.edit { putLong(pkg, System.currentTimeMillis()) }
    }

    fun lastUsed(pkg: String): Long = prefs.getLong(pkg, 0L)
}
