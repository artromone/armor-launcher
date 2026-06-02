package com.armor.launcher

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Admin / Device Owner receiver. After `adb shell dpm set-device-owner`,
 * this component holds the privileged DPC role for the app.
 */
class DeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "DeviceAdmin enabled")
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
