package com.armor.launcher.platform

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
 */
object RealMode {
    @Volatile var unlocked: Boolean = false
        private set

    fun unlock() { unlocked = true }
    fun lock() { unlocked = false }
}
