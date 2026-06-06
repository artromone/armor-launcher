package com.armor.launcher.platform

import android.app.Activity
import android.content.Context

/**
 * Process-wide flag: is Real mode currently unlocked?
 *
 * Unlocks when the user enters 5×'*' + secret code on DisguiseActivity.
 * Locks when:
 *   - the screen turns off (BaseDisguiseActivity.screenOff / goToSleep),
 *   - the process restarts (reboot, force-stop),
 *   - the user explicitly chooses "Lock Real mode" in Settings.
 *
 * While unlocked, ArmorSettingsActivity reveals admin rows (Secret Code,
 * Hidden Apps, Real Launcher, System Settings). Otherwise the launcher
 * looks like a stock feature phone.
 *
 * Side-effect contract: the [unlock]/[lock] overloads that take a [Context]
 * also flip the DPM status-bar lock and (for unlock) stop Lock Task so the
 * real system status bar + notification shade become available everywhere
 * after entering Real mode. Lock Task is re-armed naturally on the next
 * resume of DisguiseActivity.
 */
object RealMode {
    @Volatile var unlocked: Boolean = false
        private set

    fun unlock() { unlocked = true }
    fun lock() { unlocked = false }

    fun unlock(ctx: Context) {
        unlocked = true
        Dpm.asOwner(ctx, "RealMode.unlock setStatusBarDisabled(false)") { dpm, admin ->
            dpm.setStatusBarDisabled(admin, false)
        }
        if (ctx is Activity) {
            runCatching { ctx.stopLockTask() }
        }
    }

    fun lock(ctx: Context) {
        unlocked = false
        Dpm.asOwner(ctx, "RealMode.lock setStatusBarDisabled(true)") { dpm, admin ->
            dpm.setStatusBarDisabled(admin, true)
        }
    }
}
