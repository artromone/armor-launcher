package com.armor.launcher

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Device Admin / Device Owner receiver. After `adb shell dpm set-device-owner`,
 * this component holds the privileged DPC role for the app.
 */
class DeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "DeviceAdmin enabled")
        // Register Armor as the persistent HOME at the DPM level immediately,
        // so on the next boot the system picks DisguiseActivity directly and
        // never flashes the OEM launcher before BootReceiver fires.
        runCatching {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
            if (!dpm.isDeviceOwnerApp(context.packageName)) return
            val admin = componentName(context)
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin,
                filter,
                ComponentName(context, DisguiseActivity::class.java)
            )
            Log.i(TAG, "persistent HOME preferred activity registered")
        }.onFailure { Log.w(TAG, "registering HOME failed", it) }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "DeviceAdmin disabled")
    }

    companion object {
        private const val TAG = "ArmorDPC"
        fun componentName(context: Context): ComponentName =
            ComponentName(context, DeviceAdmin::class.java)
    }
}
