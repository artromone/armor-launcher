package com.armor.launcher.platform

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.edit
import com.armor.launcher.LockActivity
import com.armor.launcher.domain.PinManager

/**
 * Coordinates the "screen turned off → next foregrounding must show LockActivity"
 * flow. Two write paths set the needs-lock flag:
 *   1. ACTION_SCREEN_OFF broadcast — covers user pressing power button
 *   2. IdleSleepController.onSleep — covers our own idle-timer sleep
 * Both also call [RealMode.lock] so admin rows disappear from Settings.
 *
 * On resume of any non-lock-screen disguise activity, [maybeRedirectToLock]
 * checks the flag, clears it, and replaces the task with LockActivity if a
 * PIN is set.
 */
internal class LockGate(private val activity: Activity) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = markLocked()
    }

    fun onStart() {
        activity.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    fun onStop() {
        try { activity.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    /** Called from both the broadcast and the idle-sleep path. */
    fun markLocked() {
        RealMode.lock(activity)
        if (PinManager.forPin(activity).isSet()) {
            Prefs.lockState(activity).edit {
                putBoolean(Prefs.KEY_NEEDS_LOCK, true)
            }
        }
    }

    /**
     * Returns true if it kicked a redirect to LockActivity (and finished the
     * owning activity). Owner should early-out of onResume after.
     */
    fun maybeRedirectToLock(): Boolean {
        if (!PinManager.forPin(activity).isSet()) return false
        val prefs = Prefs.lockState(activity)
        if (!prefs.getBoolean(Prefs.KEY_NEEDS_LOCK, false)) return false
        prefs.edit { putBoolean(Prefs.KEY_NEEDS_LOCK, false) }
        activity.startActivity(Intents.clearTopHome(activity, LockActivity::class.java))
        activity.finish()
        return true
    }
}
