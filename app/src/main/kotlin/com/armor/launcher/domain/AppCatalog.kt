package com.armor.launcher.domain

/**
 * Static catalog data that survives the move to a dynamic, MRU-sorted app
 * drawer. The curated 3x3 MENU and AppEntry type were retired together with
 * the old MenuActivity; what remains is the Lock Task whitelist baseline.
 *
 * DisguiseActivity merges this baseline with the user's pinned packages
 * before passing to DPM.setLockTaskPackages(), so any pinned app is launchable
 * inside kiosk mode without needing to be listed here.
 */
object AppCatalog {

    /**
     * Packages that must remain launchable from inside Lock Task regardless
     * of what the user has pinned. These are either Armor itself, dev tools
     * needed for in-place updates, or telephony/settings helpers other apps
     * depend on transitively.
     */
    val LOCK_TASK_WHITELIST: Array<String> = arrayOf(
        // ourselves
        "com.armor.launcher",
        // dev tools — keep so APK reinstall works without leaving kiosk
        "ru.zdevs.zarchiver",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        // helpers that other launched apps depend on
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
    )
}
