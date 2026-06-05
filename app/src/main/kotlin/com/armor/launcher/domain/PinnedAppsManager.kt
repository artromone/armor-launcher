package com.armor.launcher.domain

import android.content.Context
import androidx.core.content.edit
import com.armor.launcher.platform.Prefs

/**
 * Ordered list of packages the user has pinned to the disguise home screen.
 *
 * Persistence: a single CSV string in SharedPreferences (StringSet would lose
 * order, which matters for the home-screen layout). Order = insertion.
 *
 * Limit is intentionally low: the disguise home is a Nokia-shaped 480×640
 * portrait surface and the pinned list shares it with status bar, tasks line,
 * and soft keys. More than ~5 visible rows feels cramped.
 */
class PinnedAppsManager(c: Context) {

    private val prefs = c.getSharedPreferences(Prefs.FILE_PINNED, Context.MODE_PRIVATE)

    fun list(): List<String> =
        prefs.getString(Prefs.KEY_PINNED_CSV, "")
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun isPinned(pkg: String): Boolean = list().contains(pkg)

    /**
     * Toggle a package's pinned state. Returns a [ToggleResult] so callers can
     * surface user feedback (pinned / unpinned / limit reached).
     */
    fun toggle(pkg: String): ToggleResult {
        val current = list().toMutableList()
        return if (current.remove(pkg)) {
            save(current); ToggleResult.UNPINNED
        } else if (current.size >= LIMIT) {
            ToggleResult.LIMIT_REACHED
        } else {
            current.add(pkg); save(current); ToggleResult.PINNED
        }
    }

    /** Drop entries that no longer correspond to installed packages. */
    fun prune(installed: Set<String>) {
        val filtered = list().filter { it in installed }
        if (filtered.size != list().size) save(filtered)
    }

    private fun save(items: List<String>) {
        prefs.edit { putString(Prefs.KEY_PINNED_CSV, items.joinToString(",")) }
    }

    enum class ToggleResult { PINNED, UNPINNED, LIMIT_REACHED }

    companion object {
        const val LIMIT = 5
    }
}
