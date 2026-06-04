package com.armor.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Brings Armor to the foreground after device boot. Without Armor being the
 * default HOME app, the system would otherwise show launcher3 first; we shove
 * DisguiseActivity on top immediately so the user lands inside the disguise
 * (and Lock Task re-arms via DisguiseActivity.onCreate).
 *
 * Listens to several variants to cover OEM quirks (QUICKBOOT, USER_UNLOCKED).
 * directBootAware so it can run before user PIN unlock — but we only START
 * the activity after the user is unlocked (USER_UNLOCKED or BOOT_COMPLETED).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                // Small delay — some OEM ROMs are not ready to start Activities
                // the millisecond BOOT_COMPLETED fires, system_server is still
                // assembling its services.
                Handler(Looper.getMainLooper()).postDelayed({
                    launchDisguise(context)
                }, LAUNCH_DELAY_MS)
            }
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                // Device storage is not yet unlocked; we just log. The actual
                // launch happens on BOOT_COMPLETED / USER_UNLOCKED above.
                Log.i(TAG, "LOCKED_BOOT_COMPLETED — waiting for user unlock")
            }
        }
    }

    private fun launchDisguise(context: Context) {
        try {
            val target = if (PinManager.forPin(context).isSet()) {
                LockActivity::class.java
            } else {
                DisguiseActivity::class.java
            }
            // Boot uses CLEAR_TOP (not CLEAR_TASK like Intents.clearTopHome) so
            // we don't wipe a freshly-staged task from a quick reboot loop.
            val launch = Intent(context, target).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            context.startActivity(launch)
            Log.i(TAG, "Launched ${target.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch from BootReceiver", e)
        }
    }

    companion object {
        private const val TAG = "ArmorBoot"
        private const val LAUNCH_DELAY_MS = 0L
    }
}
