package com.armor.launcher.domain

import android.content.Context
import androidx.core.content.edit
import com.armor.launcher.platform.Prefs

object KioskState {
    fun isEnabled(ctx: Context): Boolean =
        Prefs.kioskState(ctx).getBoolean(Prefs.KEY_KIOSK_ENABLED, true) // default ON

    fun setEnabled(ctx: Context, enabled: Boolean) {
        Prefs.kioskState(ctx).edit { putBoolean(Prefs.KEY_KIOSK_ENABLED, enabled) }
    }
}
