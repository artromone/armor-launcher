package com.armor.launcher

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log

/**
 * Tracks which packages the user has marked as hidden and reflects that
 * state into the OS via DevicePolicyManager.setApplicationHidden().
 *
 * Hidden apps:
 *   - disappear from the launcher / app drawer / recents
 *   - cannot be launched (system rejects the intent)
 *   - stay installed; their data is preserved
 *   - still visible in Settings → Apps as "disabled" (full invisibility
 *     would require also restricting Settings — separate task)
 *
 * Why we keep our own set in prefs instead of asking DPM?
 *   - DPM has no API to enumerate hidden packages.
 *   - We need to re-apply state on boot / after package install.
 *   - We want to remember intent even when not currently DO.
 */
class HiddenAppsManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("armor_hidden", Context.MODE_PRIVATE)

    fun hiddenPackages(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()

    fun isHidden(pkg: String): Boolean = hiddenPackages().contains(pkg)

    /**
     * Toggle a package's hidden state. Returns true on success (or when not
     * Device Owner — we still record intent so it applies once DO is set).
     */
    fun setHidden(pkg: String, hidden: Boolean): Boolean {
        // never hide ourselves
        if (pkg == context.packageName) return false

        val current = hiddenPackages().toMutableSet()
        if (hidden) current.add(pkg) else current.remove(pkg)
        prefs.edit().putStringSet(KEY_HIDDEN, current).apply()

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm?.isDeviceOwnerApp(context.packageName) == true) {
            val admin = DeviceAdmin.componentName(context)
            return try {
                dpm.setApplicationHidden(admin, pkg, hidden)
            } catch (e: Exception) {
                Log.w(TAG, "setApplicationHidden($pkg, $hidden) failed", e)
                false
            }
        }
        return true
    }

    /** Apply stored state to DPM (call on boot and after install changes). */
    fun applyAll() {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val admin = DeviceAdmin.componentName(context)
        val hidden = hiddenPackages()
        for (pkg in hidden) {
            try { dpm.setApplicationHidden(admin, pkg, true) }
            catch (e: Exception) { Log.w(TAG, "apply hide $pkg failed", e) }
        }
    }

    /** Clear all hidden state — useful in panic/disarm flow. */
    fun showAll() {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm?.isDeviceOwnerApp(context.packageName) == true) {
            val admin = DeviceAdmin.componentName(context)
            for (pkg in hiddenPackages()) {
                try { dpm.setApplicationHidden(admin, pkg, false) }
                catch (e: Exception) { Log.w(TAG, "unhide $pkg failed", e) }
            }
        }
        prefs.edit().remove(KEY_HIDDEN).apply()
    }

    /**
     * One-shot: hide every package from RECOMMENDED that's currently installed.
     * Returns the list of packages it actually hid.
     */
    fun hideRecommended(): List<String> {
        val pm = context.packageManager
        val applied = mutableListOf<String>()
        for (pkg in RECOMMENDED_HIDDEN) {
            try {
                pm.getApplicationInfo(pkg, 0)
                if (setHidden(pkg, true)) applied.add(pkg)
            } catch (_: Exception) {
                // package not installed — skip
            }
        }
        return applied
    }

    companion object {
        private const val KEY_HIDDEN = "hidden_pkgs"
        private const val TAG = "ArmorHidden"

        /**
         * Packages that obviously hint at a connected smartphone and should
         * vanish by default for the disguise. The user can toggle individually
         * later via HiddenAppsActivity.
         */
        val RECOMMENDED_HIDDEN: List<String> = listOf(
            "com.android.chrome",                    // browser
            "com.android.vending",                   // Google Play
            "com.happproxy",                         // Happ VPN
            "com.google.android.youtube",            // YouTube
            "com.google.android.apps.tachyon",       // Google Duo / Meet
            "com.google.android.apps.searchlite",    // Search Lite
            "com.google.android.apps.mapslite",      // Maps Lite
            "com.google.android.apps.navlite",       // Nav Lite
            "com.google.android.apps.photosgo",      // Photos Go
            "com.google.android.apps.assistant",     // Assistant
            "com.google.android.calendar",           // Calendar (internet-hint)
            "com.google.android.gm.lite",            // Gmail Go
            "com.android.launcher3",                 // stock launcher
            "com.markusmaribu.cambrianlauncher",     // previous user launcher
            "org.telegram.messenger",                // Telegram (if installed)
            // NOTE: com.google.android.apps.messaging is intentionally NOT here
            //   — we use it for the visible Messages tile in disguise.
        )
    }
}
