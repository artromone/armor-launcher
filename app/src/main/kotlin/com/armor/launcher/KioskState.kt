package com.armor.launcher

import android.content.Context
import androidx.core.content.edit

object KioskState {
    fun isEnabled(ctx: Context): Boolean =
        Prefs.kioskState(ctx).getBoolean(Prefs.KEY_KIOSK_ENABLED, true) // default ON

    fun setEnabled(ctx: Context, enabled: Boolean) {
        Prefs.kioskState(ctx).edit { putBoolean(Prefs.KEY_KIOSK_ENABLED, enabled) }
    }
}
