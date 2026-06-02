package com.armor.launcher

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Dev escape hatch:
 *   adb shell am broadcast -a com.armor.launcher.EXIT_KIOSK
 *
 * Hard-disarms the kiosk by **removing Device Owner status**, clearing the
 * lock-task whitelist, and finishing our own tasks. After this, Armor is a
 * regular app — uninstallable from Settings, no special powers. To re-arm
 * the kiosk run:
 *   adb shell dpm set-device-owner com.armor.launcher/.DeviceAdmin
 */
class RescueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXIT) return
        Log.i(TAG, "EXIT_KIOSK — clearing Device Owner")

        KioskState.setEnabled(context, false)

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm?.isDeviceOwnerApp(context.packageName) == true) {
            val admin = DeviceAdmin.componentName(context)
            try {
                dpm.setLockTaskPackages(admin, emptyArray())
            } catch (e: Exception) {
                Log.w(TAG, "clear lockTaskPackages failed", e)
            }
            try {
                @Suppress("DEPRECATION")
                dpm.clearDeviceOwnerApp(context.packageName)
                Log.i(TAG, "Device Owner cleared")
            } catch (e: Exception) {
                Log.w(TAG, "clearDeviceOwnerApp failed", e)
            }
        }
        try {
            val am = context.getSystemService(ActivityManager::class.java)
            am?.appTasks?.forEach { it.finishAndRemoveTask() }
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_EXIT = "com.armor.launcher.EXIT_KIOSK"
        private const val TAG = "ArmorRescue"
    }
}
