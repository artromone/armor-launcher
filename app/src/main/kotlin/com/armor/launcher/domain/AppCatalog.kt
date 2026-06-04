package com.armor.launcher.domain

import com.armor.launcher.ArmorSettingsActivity
import com.armor.launcher.R

/**
 * Single source of truth for the disguise-mode app catalog.
 *
 * Package names verified on Duoqin Qin F22 stock ROM
 * (see `adb shell dumpsys package packages | grep ^Package`).
 */
data class AppEntry(
    val key: String,
    val label: String,
    val iconRes: Int,
    val launchClass: Class<*>? = null,
    val launchPackage: String? = null,
    val systemSettingsAction: String? = null,
    val isStub: Boolean = false,
)

object AppCatalog {

    /**
     * 3×3 grid (8 cells used, 9th empty).
     * NO calendar / email / browser / play / vpn — those would hint at internet.
     */
    val MENU: List<AppEntry> = listOf(
        AppEntry("phone",      "Phone",      R.drawable.ic_phone,
            launchPackage = "com.android.dialer"),
        AppEntry("contacts",   "Contacts",   R.drawable.ic_contacts,
            launchPackage = "com.google.android.contacts"),
        AppEntry("messages",   "Messages",   R.drawable.ic_messages,
            launchPackage = "com.google.android.apps.messaging"),
        AppEntry("files",      "Files",      R.drawable.ic_files,
            launchPackage = "ru.zdevs.zarchiver"),
        AppEntry("files2",     "Files 2",    R.drawable.ic_files,
            launchPackage = "com.android.documentsui"),  // system Files
        AppEntry("calculator", "Calculator", R.drawable.ic_calculator,
            launchPackage = "com.google.android.calculator"),
        AppEntry("clock",      "Clock",      R.drawable.ic_clock,
            launchPackage = "com.google.android.deskclock"),
        AppEntry("settings",   "Settings",   R.drawable.ic_settings,
            launchClass = ArmorSettingsActivity::class.java),
    )

    /** Lock-task whitelist — these packages can be launched from inside kiosk. */
    val LOCK_TASK_WHITELIST: Array<String> = arrayOf(
        // ourselves
        "com.armor.launcher",
        // dev tools
        "ru.zdevs.zarchiver",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        // disguise apps — verified on Qin F22
        "com.android.dialer",
        "com.google.android.contacts",
        "com.google.android.apps.messaging",
        "com.google.android.calculator",
        "com.google.android.deskclock",
        "com.android.settings",
        // helpers some of these depend on
        "com.android.phone",
        "com.android.server.telecom",
    )
}
