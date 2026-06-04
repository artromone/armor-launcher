package com.armor.launcher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Thin DevicePolicyManager facade. Every call site previously had its own
 * try/catch + isDeviceOwnerApp() pattern. This centralizes both.
 */
internal object Dpm {
    private const val TAG = "ArmorDpm"

    fun service(c: Context): DevicePolicyManager? =
        c.getSystemService(DevicePolicyManager::class.java)

    fun isOurDeviceOwner(c: Context): Boolean =
        service(c)?.isDeviceOwnerApp(c.packageName) == true

    fun admin(c: Context): ComponentName = DeviceAdmin.componentName(c)

    /**
     * Run [block] with (dpm, admin) iff we are Device Owner. Catches and logs
     * any exception so calls don't need their own try/catch.
     * Returns null if not DO or on failure; otherwise the block's result.
     */
    inline fun <R> asOwner(
        c: Context,
        tag: String,
        block: (DevicePolicyManager, ComponentName) -> R,
    ): R? {
        val dpm = service(c) ?: return null
        if (!dpm.isDeviceOwnerApp(c.packageName)) return null
        return try {
            block(dpm, admin(c))
        } catch (e: Exception) {
            Log.w(TAG, "$tag failed", e)
            null
        }
    }

    /** Try lockNow() — used by IdleSleepController. Active-admin is enough. */
    fun lockNow(c: Context) {
        val dpm = service(c) ?: return
        try {
            if (dpm.isAdminActive(admin(c))) dpm.lockNow()
        } catch (e: Exception) {
            Log.w(TAG, "lockNow failed", e)
        }
    }
}
