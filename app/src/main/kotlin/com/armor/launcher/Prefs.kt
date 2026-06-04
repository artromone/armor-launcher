package com.armor.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * Single registry of every SharedPreferences file & key the app uses.
 * Anything that touches disk-backed prefs should go through here so the
 * names are discoverable in one place and renames are mechanical.
 */
internal object Prefs {
    // File names
    const val FILE_LOCK_STATE = "armor_lock_state"
    const val FILE_KIOSK_STATE = "armor_state"
    const val FILE_HIDDEN = "armor_hidden"
    const val FILE_POWER = "armor_power"
    const val FILE_PIN = "armor_pin"
    const val FILE_SECRET = "armor_secret"

    // Keys (scoped by file via the constant name)
    const val KEY_NEEDS_LOCK = "needs_lock"
    const val KEY_KIOSK_ENABLED = "kiosk_enabled"
    const val KEY_HIDDEN_PKGS = "hidden_pkgs"
    const val KEY_OFF_AFTER_MS = "off_after_ms"

    fun lockState(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE_LOCK_STATE, Context.MODE_PRIVATE)

    fun kioskState(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE_KIOSK_STATE, Context.MODE_PRIVATE)

    fun hidden(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE_HIDDEN, Context.MODE_PRIVATE)

    fun power(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE_POWER, Context.MODE_PRIVATE)
}
